import test from 'node:test';
import assert from 'node:assert/strict';
import http from 'node:http';
import {spawn} from 'node:child_process';
import {once} from 'node:events';
import {mkdtemp, mkdir, readdir, copyFile, writeFile, readFile, rm} from 'node:fs/promises';
import {join} from 'node:path';
import {tmpdir} from 'node:os';
import {setTimeout as delay} from 'node:timers/promises';

async function unusedPort() {
  const server = http.createServer();
  await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
  const port = server.address().port;
  await new Promise(resolve => server.close(resolve)); return port;
}

test('authenticated TV replies arrive without waiting for slow status reads, and Stop cancels follow-up', {timeout: 20000}, async t => {
  const dir = await mkdtemp(join(tmpdir(), 'buddy-latency-'));
  let child;
  t.after(async () => {
    if (child?.exitCode === null) {const exited = once(child, 'exit'); process.kill(-child.pid, 'SIGTERM'); await exited;}
    await rm(dir, {recursive: true, force: true, maxRetries: 5, retryDelay: 100});
  });
  const root = join(dir, 'controller'), runtime = join(dir, 'runtime');
  await mkdir(join(root, 'android'), {recursive: true}); await mkdir(runtime);
  const source = new URL('../', import.meta.url);
  for (const name of await readdir(source)) if (name.endsWith('.mjs')) await copyFile(new URL(name, source), join(root, name));
  await copyFile(new URL('android/AndroidManifest.xml', source), join(root, 'android/AndroidManifest.xml'));
  const token = 'a'.repeat(64), adminToken = 'b'.repeat(64);
  await writeFile(join(runtime, 'config.json'), JSON.stringify({token, adminToken, tvDevice: 'fixture:5555', remoteButtonEnabled: false, reactions: false, seasonal: false}));
  await writeFile(join(runtime, 'credentials.json'), JSON.stringify({apiKey: 'fixture-key', model: 'fixture-model'}));
  await writeFile(join(root, 'piper-client.mjs'), 'export class PiperClient {close(){}}');
  await writeFile(join(root, 'mac-voice.mjs'), 'export async function macSpeak(){return {audio:Buffer.from("RIFF0000WAVE"),voice:"fixture"};}');
  const snapshotStarted = join(dir, 'snapshot-started');
  const playback = join(dir, 'playback'); await writeFile(playback, 'paused');
  const adb = join(dir, 'adb.mjs');
  await writeFile(adb, `#!${process.execPath}
import {writeFileSync,readFileSync} from 'node:fs';
const command=process.argv[5]||'';
if(command.startsWith('dumpsys window')){
  writeFileSync(${JSON.stringify(snapshotStarted)},'started');
  setTimeout(()=>console.log('mCurrentFocus=null\\n@@\\n    session com.example.player/session (userId=0)\\n      package=com.example.player\\n      state=PlaybackState {state='+ (readFileSync(${JSON.stringify(playback)},'utf8')==='playing'?3:2) +', position=0}\\n@@\\nvolume is 3 in range [0..15]'),2200);
}else if(command.startsWith('dumpsys package'))console.log('versionCode=8');
else console.log('');
`, {mode: 0o700});
  // Only this disposable controller intercepts model calls; the test never contacts OpenAI.
  const preload = join(dir, 'model-fixture.mjs');
  await writeFile(preload, `const original=globalThis.fetch; globalThis.fetch=(url,options)=>String(url).startsWith('https://api.openai.com/')?Promise.resolve(new Response(JSON.stringify({output:[{type:'message',content:[{type:'output_text',text:'I am ready. What would you like to watch?'}]}],usage:{input_tokens:1,output_tokens:1}}),{headers:{'Content-Type':'application/json'}})):original(url,options);`);
  const port = await unusedPort(), admin = await unusedPort(), ready = join(runtime, 'ready.json');
  child = spawn(process.execPath, ['--import', preload, join(root, 'server.mjs')], {detached: true, env: {...process.env, TV_RUNTIME_DIR: runtime, TV_ADB: adb, TV_DEVICE: 'fixture:5555', TV_VOICE_ENGINE: 'mac', TV_PORT: String(port), TV_ADMIN_PORT: String(admin), TV_READY_FILE: ready}, stdio: ['ignore', 'pipe', 'pipe']});
  let output = ''; child.stdout.on('data', data => {output += data;}); child.stderr.on('data', data => {output += data;});
  for (let i = 0; i < 150; i++) {try {await readFile(ready); await readFile(snapshotStarted); break;} catch {await delay(20);}}
  await readFile(ready).catch(() => assert.fail(output)); await readFile(snapshotStarted);
  const headers = {Authorization: 'Bearer ' + token, 'X-Buddy-Version': '8', 'X-Buddy-Voice': 'en_US-amy-low', 'Content-Type': 'application/json'};
  const request = (path, data, extra = {}) => fetch(`http://127.0.0.1:${port}${path}`, {headers, method: data === undefined ? 'GET' : 'POST', body: data === undefined ? undefined : JSON.stringify(data), ...extra});
  assert.equal((await request('/api/state?since=1&session=bad', undefined, {headers: {Authorization: 'Bearer wrong'}})).status, 401);
  let state = await (await request('/api/state')).json(); assert.equal(state.stateWait, true);
  let delivered = false;
  const held = request(`/api/state?since=${state.revision}&session=${state.session}`).then(async res => {delivered = true; return res.json();});
  await delay(50); assert.equal(delivered, false);
  const began = performance.now();
  assert.equal((await request('/api/chat', {text: 'Hello there'})).status, 202);
  state = await held;
  while (!state.speechText) state = await (await request(`/api/state?since=${state.revision}&session=${state.session}`)).json();
  assert.match(state.speechText, /I am ready/); assert.equal(state.followUp, true);
  assert.ok(performance.now() - began < 1500, 'Reply must not wait for the 2.2-second TV status command');
  // Starting another request must invalidate a delayed permission check before it finishes.
  const permission = request('/api/follow-up?replyId=' + state.replyId).then(res => res.json());
  await delay(50); await request('/api/stop', {});
  assert.equal((await permission).allowed, false);
  state = await (await request('/api/state')).json();
  assert.equal(state.speechText, ''); assert.equal(state.followUp, false);
  // A new controller session responds immediately even if revision numbers coincide.
  const restarted = await (await request(`/api/state?since=${state.revision}&session=previous-start`)).json();
  assert.equal(restarted.session, state.session);
  await writeFile(playback, 'playing');
  await request('/api/chat', {text: 'Hello again'});
  do {state = await (await request(`/api/state?since=${state.revision}&session=${state.session}`)).json();} while (!state.speechText || state.busy);
  assert.equal((await (await request('/api/follow-up?replyId=' + state.replyId)).json()).allowed, false);
});
