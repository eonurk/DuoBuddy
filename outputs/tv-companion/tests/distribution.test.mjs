import test from 'node:test';
import assert from 'node:assert/strict';
import http from 'node:http';
import {spawn} from 'node:child_process';
import {once} from 'node:events';
import {mkdtemp, mkdir, readdir, copyFile, writeFile, readFile, rm, stat} from 'node:fs/promises';
import {join} from 'node:path';
import {tmpdir} from 'node:os';
import {fileURLToPath} from 'node:url';
import {runtimeConfig} from '../runtime-config.mjs';
import {checkForUpdate, newerVersion} from '../updates.mjs';
import {TvController} from '../tv.mjs';

test('distribution state is outside the bundle and source settings retain their paths', () => {
  const shipped = runtimeConfig('/Applications/Buddy.app/Contents/Resources/controller', {TV_DISTRIBUTION: '1'});
  assert.equal(shipped.defaultDevice, ''); assert.equal(shipped.piperEnabled, false);
  assert.match(shipped.runtime, /Library\/Application Support\/DuoBuddy$/);
  const source = runtimeConfig('/repo/outputs/tv-companion', {});
  assert.equal(source.runtime, '/repo/work/tv-runtime'); assert.equal(source.piperEnabled, true);
  assert.equal(runtimeConfig('/app', {TV_DISTRIBUTION: '1', TV_RUNTIME_DIR: '/state', TV_ADB: '/adb'}).runtime, '/state');
});

test('no TV commands run before selection', async () => {
  const tv = new TvController({adb: '/not-a-real-executable', device: ''});
  await assert.rejects(tv.adb(['install', '-r', 'buddy.apk']), /Connect your TV/);
});

test('update checks handle prereleases, missing public releases and untrusted downloads', async () => {
  assert.equal(newerVersion('v0.4.0', '0.4.0-beta.1'), true);
  assert.equal(newerVersion('v0.4.0-beta.10', '0.4.0-beta.2'), true);
  assert.equal(newerVersion('v0.4.0-beta.1', '0.4.0'), false);
  assert.equal(newerVersion('v0.3.9', '0.4.0-beta.1'), false);
  assert.equal(newerVersion('invalid', '0.4.0'), false);
  const root = fileURLToPath(new URL('../', import.meta.url));
  const response = data => async () => ({ok: true, json: async () => data});
  const release = {tag_name: 'v0.5.0', assets: [{name: 'DuoBuddy-0.5.0-mac-universal.zip', browser_download_url: 'https://github.com/eonurk/DuoBuddy/releases/download/v0.5.0/DuoBuddy-0.5.0-mac-universal.zip'}]};
  assert.equal((await checkForUpdate(root, response(release))).available, true);
  assert.equal((await checkForUpdate(root, response({...release, prerelease: true}))).available, false);
  assert.equal((await checkForUpdate(root, response({...release, assets: [{...release.assets[0], browser_download_url: 'https://example.com/malware.zip'}]}))).available, false);
  assert.match((await checkForUpdate(root, async () => ({status: 404}))).message, /No public/);
  await assert.rejects(checkForUpdate(root, async () => ({status: 403, ok: false})), /Try again later/);
});

async function unusedPort() {
  const server = http.createServer();
  await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
  const port = server.address().port;
  await new Promise(resolve => server.close(resolve));
  return port;
}

test('fresh distributed controller generates private settings, uses local voice and never probes a developer TV or Piper', {timeout: 15000}, async t => {
  const dir = await mkdtemp(join(tmpdir(), 'buddy-distribution-test-'));
  let child;
  t.after(async () => {
    if (child?.exitCode === null) {const exited = once(child, 'exit'); child.kill('SIGTERM'); await exited;}
    await rm(dir, {recursive: true, force: true, maxRetries: 5, retryDelay: 100});
  });
  const root = join(dir, 'read-only-app/controller'), runtime = join(dir, 'user-profile');
  await mkdir(join(root, 'android'), {recursive: true});
  const source = new URL('../', import.meta.url);
  for (const name of await readdir(source)) if (name.endsWith('.mjs') || ['setup.html', 'release.json'].includes(name)) await copyFile(new URL(name, source), join(root, name));
  await copyFile(new URL('android/AndroidManifest.xml', source), join(root, 'android/AndroidManifest.xml'));
  await writeFile(join(root, 'mac-voice.mjs'), 'export async function macSpeak(){return {audio:Buffer.from("RIFF0000WAVE"),voice:"local fixture",duration:1};}');
  const attempted = join(dir, 'external-service-attempt');
  await writeFile(join(root, 'piper-client.mjs'), `import {writeFileSync} from 'node:fs'; export class PiperClient {async ensure(){writeFileSync(${JSON.stringify(attempted)},'Piper attempted');throw Error('Should never connect');}close(){}}`);
  const adb = join(dir, 'fake-adb');
  await writeFile(adb, `#!${process.execPath}\nimport {writeFileSync} from 'node:fs';writeFileSync(${JSON.stringify(attempted)},'ADB attempted');process.exit(1);`, {mode: 0o700});
  const admin = await unusedPort(), port = await unusedPort(), ready = join(runtime, 'ready.json');
  const env = {...process.env, TV_DISTRIBUTION: '1', TV_RUNTIME_DIR: runtime, TV_ADB: adb, TV_PORT: String(port), TV_ADMIN_PORT: String(admin), TV_READY_FILE: ready};
  delete env.TV_DEVICE; delete env.TV_REMOTE_NAME; delete env.TV_VOICE_ENGINE;
  child = spawn(process.execPath, [join(root, 'server.mjs')], {env, stdio: ['ignore', 'pipe', 'pipe']});
  let output = ''; child.stdout.on('data', d => {output += d;}); child.stderr.on('data', d => {output += d;});
  let readyData;
  for (let i = 0; i < 100; i++) {
    try {readyData = JSON.parse(await readFile(ready)); break;} catch {await new Promise(resolve => setTimeout(resolve, 30));}
  }
  assert.ok(readyData, output); assert.equal(readyData.pid, child.pid);
  const config = JSON.parse(await readFile(join(runtime, 'config.json')));
  assert.match(config.token, /^[a-f0-9]{64}$/); assert.match(config.adminToken, /^[a-f0-9]{64}$/);
  assert.notEqual(config.token, config.adminToken);
  assert.equal((await stat(runtime)).mode & 0o777, 0o700);
  assert.equal((await stat(join(runtime, 'config.json'))).mode & 0o777, 0o600);
  const headers = {'X-Setup-Token': config.adminToken, 'Content-Type': 'application/json'};
  const call = async (path, body) => {
    const r = await fetch(`http://127.0.0.1:${admin}${path}`, {headers, method: body === undefined ? 'GET' : 'POST', body: body === undefined ? undefined : JSON.stringify(body)});
    return {status: r.status, data: await r.json()};
  };
  const state = (await call('/admin/state')).data;
  assert.equal(state.device, ''); assert.equal(state.configured, false);
  assert.equal(state.piperEnabled, false); assert.equal(state.voiceEngine, 'mac'); assert.equal(state.voiceReady, true);
  assert.deepEqual(state.voices, []);
  assert.match((await call('/admin/context')).data.summary, /Connect your TV/);
  assert.equal((await call('/admin/tv/install', {})).status, 400);
  assert.equal((await call('/admin/voice', {preview: true})).status, 200);
  await new Promise(resolve => setTimeout(resolve, 100));
  assert.equal((await call('/admin/state')).data.voiceName, 'local fixture');
  // The same controller must skip Mac synthesis once an embedded-voice TV checks in.
  const tvHeaders={Authorization:'Bearer '+config.token,'X-Buddy-Version':'7','X-Buddy-Voice':'en_US-lessac-medium'};
  const local=await (await fetch(`http://127.0.0.1:${port}/api/state`,{headers:tvHeaders})).json();
  assert.equal(local.localVoice,true);assert.equal(local.voiceEngine,'piper-tv');assert.equal(local.voiceHost,'This TV');assert.equal(local.voiceName,'Lessac Medium · on this TV');
  assert.equal((await call('/admin/voice',{preview:true})).status,200);
  const reply=(await call('/admin/state')).data;
  assert.match(reply.speechText,/DuoBuddy/);assert.equal(reply.audioPath,'');assert.equal(reply.phase,'idle');
  await call('/admin/stop',{});
  const stopped=(await call('/admin/state')).data;
  assert.equal(stopped.speechText,'');assert.equal(stopped.reply,'');
  // An older TV still receives a playable legacy clip after switching devices/builds.
  await fetch(`http://127.0.0.1:${port}/api/state`,{headers:{...tvHeaders,'X-Buddy-Version':'6'}});
  await call('/admin/voice',{preview:true});
  await new Promise(resolve=>setTimeout(resolve,100));
  assert.match((await call('/admin/state')).data.audioPath,/^\/api\/audio\/[a-f0-9]{32}\.wav$/);
  await assert.rejects(stat(attempted), {code: 'ENOENT'});
  await assert.rejects(stat(join(runtime, 'credentials.json')), {code: 'ENOENT'});
  await assert.rejects(stat(join(dir, 'work')), {code: 'ENOENT'});
});
