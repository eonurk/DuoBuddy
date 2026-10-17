import test from 'node:test';
import assert from 'node:assert/strict';
import {mkdtemp, writeFile, readFile, rm} from 'node:fs/promises';
import {tmpdir} from 'node:os';
import {join} from 'node:path';
import {execFile} from 'node:child_process';
import {promisify} from 'node:util';
import {TvController, parseDeviceInfo, describeDevice, parseFocus} from '../tv.mjs';
import {tvAddress, tvSelection, lanAddress} from '../server.mjs';
import {remoteDevices} from '../remote-button.mjs';
import {buildInstructions} from '../persona.mjs';

test('network debugging addresses validate hosts and ports; pairing requires an explicit port', () => {
  assert.equal(tvAddress(' 192.168.1.20 '), '192.168.1.20:5555');
  assert.equal(tvAddress('living-room.local:40123'), 'living-room.local:40123');
  assert.equal(tvAddress('192.168.1.20:37123', 0), '192.168.1.20:37123');
  for (const value of ['', 'http://192.168.1.20', '192.168.1.20;reboot', '-server', '192.168.1.20:0', 'tv:65536', '999.1.1.1', 'tv..local', 'tv:abc']) assert.throws(() => tvAddress(value), undefined, value);
  assert.throws(() => tvAddress('192.168.1.20', 0), /port/);
});

test('restart restores the chosen TV and remote, with environment overrides', () => {
  const saved = {tvDevice: '192.168.1.20:40123', remoteName: 'G10 Remote'};
  assert.deepEqual(tvSelection(saved, {}), {device: saved.tvDevice, remoteName: saved.remoteName});
  assert.deepEqual(tvSelection(saved, {TV_DEVICE: 'bedroom.local', TV_REMOTE_NAME: 'NVIDIA Remote'}), {device: 'bedroom.local:5555', remoteName: 'NVIDIA Remote'});
  assert.equal(tvSelection({}, {}).device, '192.168.178.87:5555');
});

test('the callback address follows the TV network instead of the original home network', () => {
  const interfaces = {lo: [{family: 'IPv4', internal: true, address: '127.0.0.1'}], vpn: [{family: 'IPv4', address: '10.8.0.2'}], wifi: [{family: 'IPv4', address: '192.168.1.4'}]};
  assert.equal(lanAddress('192.168.1.20:40123', interfaces), '192.168.1.4');
  assert.equal(lanAddress('10.8.0.20:5555', interfaces), '10.8.0.2');
  assert.equal(lanAddress('192.168.2.20:5555', {...interfaces, wifi: [{family: 'IPv4', address: '192.168.1.4', netmask: '255.255.0.0'}]}), '192.168.1.4');
});

test('Mi Box and Google TV identities and newer focused activities are understood', () => {
  assert.equal(describeDevice(parseDeviceInfo('Xiaomi\nMIBOX4\n9\n28\n')), 'an Android TV (Xiaomi MIBOX4, Android 9)');
  const google = parseDeviceInfo('Google\nGoogle TV Streamer\n14\n34\npackage:/product/launcher.apk\n');
  assert.equal(google.name, 'Google TV Streamer');
  assert.equal(google.googleTv, true);
  assert.deepEqual(parseFocus('mCurrentFocus=null\n mFocusedApp=ActivityRecord{abc u0 com.example.tv/.Main t31}'), {pkg: 'com.example.tv', activity: '.Main'});
  const prompt = buildInstructions({device: google, catalog: {describe: () => '', searchable: () => []}, snapshot: null});
  assert.match(prompt, /Google TV Streamer, Android 14/);
  assert.doesNotMatch(prompt, /Xiaomi Mi Box 4/);
});

test('an explicitly named remote excludes every other candidate', () => {
  const inputs = 'add device 1: /dev/input/event1\n name: "Xiaomi RC"\n KEY_VOICECOMMAND\nadd device 2: /dev/input/event2\n name: "NVIDIA Remote"\n KEY_ASSISTANT\n';
  assert.deepEqual(remoteDevices(inputs, 'xiaomi rc').map(r => r.name), ['Xiaomi RC']);
  assert.throws(() => remoteDevices(inputs, 'Disconnected Remote'), /Disconnected Remote/);
});

async function fakeAdb(t) {
  const dir = await mkdtemp(join(tmpdir(), 'buddy-adb-test-'));
  t.after(() => rm(dir, {recursive: true, force: true}));
  const path = join(dir, 'adb');
  await writeFile(path, `#!${process.execPath}
import {appendFileSync} from 'node:fs';
const args=process.argv.slice(2);
appendFileSync(new URL('./calls.jsonl',import.meta.url),JSON.stringify(args)+'\\n');
if(args[0]==='pair') {console.log(args[2]==='000000'?'Failed: wrong code':'Successfully paired to '+args[1]);process.exit(0);}
if(args[0]==='connect') {if(args[1].startsWith('bad'))process.stderr.write('failed to authenticate');else console.log('connected to '+args[1]);process.exit(0);}
const command=args[3]||'';
if(args[2]==='install')console.log(args[1].startsWith('bad')?'Failure [INSTALL_FAILED]':'Success');
else if(command.startsWith('getprop'))console.log('Google\\nGoogle TV Streamer\\n14\\n34\\npackage:/launcher.apk');
else if(command.startsWith('dumpsys package'))console.log('versionCode=3 minSdk=26 targetSdk=28');
else if(args[1].startsWith('denied'))console.log('SecurityException: Permission denial');
else console.log('');
`, {mode: 0o700});
  // .mjs ensures the stub is ESM on every supported Node release.
  const esmPath = path + '.mjs';
  await writeFile(esmPath, await readFile(path), {mode: 0o700});
  return {tv: new TvController({adb: esmPath, device: '192.168.1.20:40123'}), calls: async () => (await readFile(join(dir, 'calls.jsonl'), 'utf8')).trim().split('\n').map(JSON.parse), dir};
}

test('ADB pairs once with its separate port and surfaces pairing and authentication failures', async t => {
  const {tv, calls} = await fakeAdb(t);
  await tv.pair('192.168.1.20:37123', '123456');
  await assert.rejects(tv.pair('192.168.1.20:37123', '000000'), /Pairing failed/);
  assert.equal((await tv.connect()).googleTv, true);
  tv.setDevice('bad.local:5555');
  await assert.rejects(tv.connect(), /authenticate/);
  assert.deepEqual((await calls())[0], ['pair', '192.168.1.20:37123', '123456']);
});

test('install targets only the selected TV, grants permissions, and sends the callback and token', async t => {
  const {tv, calls} = await fakeAdb(t);
  await tv.installCompanion({apk: '/tmp/Buddy with spaces.apk', server: 'http://192.168.1.4:8765', token: 'a'.repeat(64)});
  const invoked = await calls();
  assert.ok(invoked.every(a => a[0] === '-s' && a[1] === tv.device));
  assert.deepEqual(invoked[0].slice(2), ['install', '-r', '/tmp/Buddy with spaces.apk']);
  assert.match(invoked[1][3], /RECORD_AUDIO/);
  assert.match(invoked[2][3], /SYSTEM_ALERT_WINDOW allow/);
  assert.match(invoked[3][3], /--es server 'http:\/\/192.168.1.4:8765' --es token 'a{64}'/);
  tv.setDevice('bad.local:5555');
  await assert.rejects(tv.installCompanion({apk: '/tmp/Buddy.apk'}), /Install failed/);
  tv.setDevice('denied.local:5555');
  await assert.rejects(tv.installCompanion({apk: '/tmp/Buddy.apk'}), /Permission denial/);
});

test('screensaver setup reports rejected settings and requires an installed companion', async t => {
  const {tv} = await fakeAdb(t);
  await tv.useBuddyScreensaver();
  tv.setDevice('denied.local:5555');
  await assert.rejects(tv.useBuddyScreensaver(), /Permission denial/);
  tv.companionVersion = async () => 0;
  await assert.rejects(tv.useBuddyScreensaver(), /Install DuoBuddy/);
});

test('changing TVs discards the previous catalogue, identity, volume and screen dimensions', () => {
  const tv = new TvController({device: 'old'});
  tv.catalog.load(new Map([['old.app', 'old.app/.Main']]));
  tv.info = {name: 'old'}; tv.mutedFrom = 12; tv.size = {width: 3840, height: 2160};
  tv.setDevice('new');
  assert.equal(tv.info, null); assert.equal(tv.mutedFrom, null);
  assert.deepEqual(tv.catalog.apps, []); assert.deepEqual(tv.size, {width: 1920, height: 1080});
});

test('volume reads and writes fall back to cmd media_session when legacy media is absent', async t => {
  const {tv, dir} = await fakeAdb(t);
  await writeFile(join(dir, 'media'), '#!/bin/sh\nexit 127\n', {mode: 0o700});
  await writeFile(join(dir, 'cmd'), '#!/bin/sh\n[ "$1" = media_session ] || exit 1\necho "volume is 45 in range [0..100]"\n', {mode: 0o700});
  const exec = promisify(execFile);
  tv.shell = async command => (await exec('/bin/sh', ['-c', command], {env: {...process.env, PATH: dir}})).stdout;
  assert.deepEqual(await tv.volume(), {level: 45, max: 100});
  assert.deepEqual(await tv.setVolume(80), {level: 45, max: 100});
});

test('late replies from the old TV cannot populate the new TV catalogue or identity', async () => {
  const tv = new TvController({device: 'old'});
  const replies = [];
  tv.shell = () => new Promise(resolve => replies.push(resolve));
  const apps = tv.refreshApps(), info = tv.deviceInfo();
  tv.setDevice('new');
  replies[0]('old.app/.Main'); replies[1]('Old TV\nOld model\n9\n28\n');
  await Promise.all([apps, info]);
  assert.deepEqual(tv.catalog.apps, []); assert.equal(tv.info, null);
});
