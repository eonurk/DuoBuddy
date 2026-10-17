import test from 'node:test';
import assert from 'node:assert/strict';
import http from 'node:http';
import {spawn} from 'node:child_process';
import {once} from 'node:events';
import {mkdtemp, mkdir, readdir, copyFile, writeFile, readFile, rm, stat} from 'node:fs/promises';
import {join} from 'node:path';
import {tmpdir} from 'node:os';
import {setTimeout as delay} from 'node:timers/promises';

async function port() {
  const server = http.createServer(); await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
  const value = server.address().port; await new Promise(resolve => server.close(resolve)); return value;
}
test('model picker switches providers, retains keys, completes Gemini tool calls and survives restart', {timeout: 15000}, async t => {
  const dir = await mkdtemp(join(tmpdir(), 'buddy-model-picker-'));
  let child;
  const stop = async () => {if (child?.exitCode === null) {const exited = once(child, 'exit'); child.kill('SIGTERM'); await exited;}};
  t.after(async () => {await stop(); await rm(dir, {recursive: true, force: true, maxRetries: 5, retryDelay: 100});});
  const root = join(dir, 'controller'), runtime = join(dir, 'runtime');
  await mkdir(join(root, 'android'), {recursive: true}); await mkdir(runtime);
  const source = new URL('../', import.meta.url);
  for (const name of await readdir(source)) if (name.endsWith('.mjs') || name === 'setup.html') await copyFile(new URL(name, source), join(root, name));
  await copyFile(new URL('android/AndroidManifest.xml', source), join(root, 'android/AndroidManifest.xml'));
  await writeFile(join(root, 'mac-voice.mjs'), 'export async function macSpeak(){return {audio:Buffer.from("RIFF0000WAVE"),voice:"fixture"};}');
  const token = 'a'.repeat(64), adminToken = 'b'.repeat(64), openaiKey = 'sk-' + 'c'.repeat(30), geminiKey = 'AIza' + 'd'.repeat(30);
  await writeFile(join(runtime, 'config.json'), JSON.stringify({token, adminToken, remoteButtonEnabled: false, reactions: false, seasonal: false}));
  await writeFile(join(runtime, 'credentials.json'), JSON.stringify({apiKey: openaiKey, model: 'gpt-6-luna'}));
  const preload = join(dir, 'api-fixture.mjs');
  await writeFile(preload, `const original=fetch;
globalThis.fetch=async (url,options)=>{
 const address=String(url),json=(data,status=200)=>new Response(JSON.stringify(data),{status,headers:{'Content-Type':'application/json'}});
 if(address.startsWith('https://api.openai.com/v1/models/'))return json({id:'gpt-6-luna'});
 if(address.startsWith('https://generativelanguage.googleapis.com/v1beta/models/'))return options.headers['x-goog-api-key']==='${geminiKey}'?json({supportedGenerationMethods:['generateContent']}):json({error:{message:'Invalid Gemini key'}},403);
 if(address==='https://generativelanguage.googleapis.com/v1beta/openai/chat/completions'){
   if(options.headers.Authorization!=='Bearer ${geminiKey}')return json({error:{message:'Wrong provider credential'}},401);
   const body=JSON.parse(options.body),prior=body.messages.find(m=>m.tool_calls?.length);
   if(body.tools.some(t=>t.type!=='function'))return json({error:{message:'Unsupported tool'}},400);
   if(prior){
     if(prior.tool_calls[0].extra_content.google.thought_signature!=='signed-fixture')return json({error:{message:'Missing signature'}},400);
     return json({choices:[{finish_reason:'stop',message:{role:'assistant',content:'Gemini tool result received.'}}]});
   }
   return json({choices:[{finish_reason:'tool_calls',message:{role:'assistant',content:null,tool_calls:[{id:'call-1',type:'function',function:{name:'tv_status',arguments:'{}'},extra_content:{google:{thought_signature:'signed-fixture'}}}]}}]});
 }
 return original(url,options);
};`);
  const admin = await port(), tvPort = await port(), ready = join(runtime, 'ready.json');
  let output = '';
  const launch = async () => {
    child = spawn(process.execPath, ['--import', preload, join(root, 'server.mjs')], {env: {...process.env, TV_DISTRIBUTION: '1', TV_DEVICE: '', TV_RUNTIME_DIR: runtime, TV_VOICE_ENGINE: 'mac', TV_PORT: String(tvPort), TV_ADMIN_PORT: String(admin), TV_READY_FILE: ready}, stdio: ['ignore', 'pipe', 'pipe']});
    child.stdout.on('data', data => {output += data;}); child.stderr.on('data', data => {output += data;});
    for (let i = 0; i < 100; i++) {try {const data = JSON.parse(await readFile(ready)); if (data.pid === child.pid) return;} catch {} await delay(20);}
    assert.fail(output);
  };
  const call = async (path, data) => {const res = await fetch('http://127.0.0.1:' + admin + path, {method: data === undefined ? 'GET' : 'POST', headers: {'X-Setup-Token': adminToken, 'Content-Type': 'application/json'}, body: data === undefined ? undefined : JSON.stringify(data)}); return {status: res.status, data: await res.json()};};
  await launch();
  const choices = (await call('/admin/models')).data;
  assert.equal(choices.provider, 'openai'); assert.equal(choices.providers[0].savedKey, true);
  assert.equal(JSON.stringify(choices).includes(openaiKey), false);
  assert.equal((await call('/admin/configure', {provider: 'gemini', model: 'gemini-3.1-flash-lite', apiKey: geminiKey})).status, 200);
  assert.equal((await call('/admin/state')).data.provider, 'gemini');
  assert.equal((await call('/admin/state')).data.webSearchAvailable, false);
  await fetch('http://127.0.0.1:' + tvPort + '/api/state', {headers: {Authorization: 'Bearer ' + token, 'X-Buddy-Version': '9'}});
  await call('/admin/chat', {text: 'Check the TV'});
  let state;
  for (let i = 0; i < 100; i++) {state = (await call('/admin/state')).data; if (!state.busy && state.speechText) break; await delay(20);}
  assert.equal(state.speechText, 'Gemini tool result received.', output);
  const saved = await readFile(join(runtime, 'credentials.json'), 'utf8');
  assert.equal((await call('/admin/configure', {provider: 'gemini', model: 'gemini-3.1-flash-lite', apiKey: 'wrong'.repeat(8)})).status, 400);
  assert.equal(await readFile(join(runtime, 'credentials.json'), 'utf8'), saved);
  assert.equal((await call('/admin/configure', {provider: 'openai', model: 'gpt-6-luna', apiKey: ''})).status, 200);
  assert.equal((await call('/admin/configure', {provider: 'gemini', model: 'gemini-3.1-flash-lite', apiKey: ''})).status, 200);
  assert.equal((await stat(join(runtime, 'credentials.json'))).mode & 0o777, 0o600);
  const publicData = JSON.stringify([(await call('/admin/state')).data, (await call('/admin/models')).data]);
  assert.equal(publicData.includes(openaiKey) || publicData.includes(geminiKey), false);
  await stop(); await launch();
  assert.equal((await call('/admin/state')).data.provider, 'gemini');
  assert.ok((await call('/admin/models')).data.providers.every(info => info.savedKey));
});
