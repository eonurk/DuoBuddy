import test from 'node:test';
import assert from 'node:assert/strict';
import http from 'node:http';
import {spawn} from 'node:child_process';
import {once} from 'node:events';
import {mkdtemp, mkdir, readdir, copyFile, writeFile, readFile, rm} from 'node:fs/promises';
import {join} from 'node:path';
import {tmpdir} from 'node:os';

async function unusedPort() {
  const server = http.createServer();
  await new Promise((resolve, reject) => {server.once('error', reject); server.listen(0, '127.0.0.1', resolve);});
  const port = server.address().port;
  await new Promise(resolve => server.close(resolve));
  return port;
}

test('setup connects, pairs, installs, saves and restores a TV without contacting real devices or voice services', {timeout: 20000}, async t => {
  const dir = await mkdtemp(join(tmpdir(), 'buddy-setup-test-'));
  let child;
  const stop = async () => {if (child && child.exitCode === null) {const exited = once(child, 'exit'); process.kill(-child.pid, 'SIGTERM'); await exited;}};
  t.after(async () => {await stop(); await rm(dir, {recursive: true, force: true, maxRetries: 5, retryDelay: 100});});
  const root = join(dir, 'outputs/tv-companion'), runtime = join(dir, 'work/tv-runtime');
  await mkdir(join(root, 'android'), {recursive: true}); await mkdir(runtime, {recursive: true});
  const source = new URL('../', import.meta.url);
  for (const name of await readdir(source)) if (name.endsWith('.mjs') || name === 'setup.html') await copyFile(new URL(name, source), join(root, name));
  await copyFile(new URL('android/AndroidManifest.xml', source), join(root, 'android/AndroidManifest.xml'));
  await writeFile(join(root, 'DuoBuddy-TV.apk'), 'fixture APK');
  // Replace external voice adapters only in this disposable copy of the app.
  await writeFile(join(root, 'piper-client.mjs'), 'export class PiperClient {async ensure(){throw Error("offline fixture");} close(){}}');
  await writeFile(join(root, 'mac-voice.mjs'), 'export async function macSpeak(){return {audio:Buffer.from("RIFF0000WAVE"),voice:"fixture",duration:1};}');
  const token = 'a'.repeat(64), adminToken = 'b'.repeat(64);
  await writeFile(join(runtime, 'config.json'), JSON.stringify({token, adminToken, tvDevice: '192.168.1.20:40123', remoteName: 'G10 Remote', remoteButtonEnabled: false, reactions: false}));
  const adb = join(dir, 'fake-adb.mjs');
  await writeFile(adb, `#!${process.execPath}
import {appendFileSync} from 'node:fs';
const args=process.argv.slice(2);appendFileSync(new URL('./calls.jsonl',import.meta.url),JSON.stringify(args)+'\\n');
if(args[0]==='connect'){console.log(args[1].startsWith('bad')?'failed to connect':'connected');process.exit(0);}
if(args[0]==='pair'){console.log('Successfully paired');process.exit(0);}
if(args[2]==='install'){console.log('Success');process.exit(0);}
const command=args[3]||'';
if(command.startsWith('getprop'))console.log('Google\\nGoogle TV Streamer\\n14\\n34\\npackage:/launcher.apk');
else if(command.startsWith('dumpsys package'))console.log('versionCode=3');
else if(command.startsWith('cmd package'))console.log('com.example.tv/.Main');
else console.log('');
`, {mode: 0o700});
  const adminPort = await unusedPort(), port = await unusedPort();
  let output = '';
  const launch = () => {
    const env = {...process.env, TV_PORT: String(port), TV_ADMIN_PORT: String(adminPort), TV_ADB: adb};
    delete env.TV_DEVICE; delete env.TV_REMOTE_NAME;
    child = spawn(process.execPath, [join(root, 'server.mjs')], {env, detached: true, stdio: ['ignore', 'pipe', 'pipe']});
    child.stdout.on('data', data => {output += data;}); child.stderr.on('data', data => {output += data;});
  };
  const call = async (path, body, setup = adminToken) => {
    const response = await fetch(`http://127.0.0.1:${adminPort}${path}`, {method: body === undefined ? 'GET' : 'POST', headers: {'X-Setup-Token': setup, 'Content-Type': 'application/json'}, body: body === undefined ? undefined : JSON.stringify(body)});
    return {status: response.status, data: response.headers.get('content-type')?.includes('application/json') ? await response.json() : await response.text()};
  };
  const ready = async () => {
    for (let attempt = 0; attempt < 60; attempt++) {try {return await call('/admin/state');} catch {await new Promise(resolve => setTimeout(resolve, 50));}}
    throw Error('Fixture server did not start: ' + output);
  };
  launch();
  assert.equal((await ready()).data.device, '192.168.1.20:40123');
  assert.equal((await call('/admin/state', undefined, 'wrong')).status, 403);
  const page = await call('/');
  assert.equal(page.status, 200); assert.match(page.data, /id="tv-config"/);
  const connected = await call('/admin/tv', {address: '192.168.1.30:40234', remoteName: 'NVIDIA Remote', pairAddress: '192.168.1.30:37123', pairCode: '123456'});
  assert.equal(connected.status, 200, JSON.stringify(connected.data));
  const bundled = Number((await readFile(new URL('../android/AndroidManifest.xml', import.meta.url), 'utf8')).match(/android:versionCode="(\d+)"/)[1]);
  assert.equal(connected.data.bundled, bundled); assert.equal(connected.data.installed, 3);
  assert.match(connected.data.tv, /Google TV Streamer/);
  assert.equal((await call('/admin/tv/install', {})).status, 200);
  assert.equal((await call('/admin/tv/screensaver', {})).status, 200);
  const pairing = JSON.parse(await readFile(join(runtime, 'pairing.json')));
  assert.match(pairing.server, /^http:\/\/[\d.]+:\d+$/); assert.equal(pairing.token, token);
  assert.equal((await call('/admin/tv', {address: '192.168.1.40', pairAddress: '192.168.1.40', pairCode: '123456'})).status, 400);
  assert.equal((await call('/admin/tv', {address: 'bad.local'})).status, 400);
  assert.equal((await call('/admin/state')).data.device, '192.168.1.30:40234');
  const calls = (await readFile(join(dir, 'calls.jsonl'), 'utf8')).trim().split('\n').map(JSON.parse);
  assert.deepEqual(calls.find(a => a[0] === 'pair'), ['pair', '192.168.1.30:37123', '123456']);
  assert.equal(calls.filter(a => a[0] === 'pair').length, 1);
  assert.ok(calls.filter(a => a[2] === 'install' || /^(pm grant|appops|am start|settings put)/.test(a[3] || '')).every(a => a[1] === '192.168.1.30:40234'));
  await stop();
  launch();
  const restored = (await ready()).data;
  assert.equal(restored.device, '192.168.1.30:40234'); assert.equal(restored.remoteName, 'NVIDIA Remote');
});
