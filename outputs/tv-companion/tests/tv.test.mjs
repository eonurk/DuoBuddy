import test from 'node:test';
import assert from 'node:assert/strict';
import {TvController, parseFocus, parseVolume, parseSessions, describeSnapshot} from '../tv.mjs';

const dump = `MEDIA SESSION SERVICE (dumpsys media_session)
Sessions Stack - have 3 sessions:
    spotify-android-tv-media-session com.spotify.tv.android/spotify-android-tv-media-session (userId=0)
      ownerPid=4288, ownerUid=10064, userId=0
      package=com.spotify.tv.android
      active=false
      state=PlaybackState {state=0, position=166487, buffered position=0, speed=1.0, updated=31342195, actions=2368383, custom actions=[], active item id=-1, error=null}
      metadata:size=8, description=Let's Get Loud, Jennifer Lopez, On The 6
    NetflixMediaSession com.netflix.ninja/NetflixMediaSession (userId=0)
      package=com.netflix.ninja
      state=PlaybackState {state=3, position=1200, buffered position=0, speed=1.0, updated=5, actions=1, custom actions=[], active item id=-1, error=null}
      metadata:size=3, description=Wednesday, S1:E2, null
    YtSession com.google.android.youtube.tv/YtSession (userId=0)
      package=com.google.android.youtube.tv
      state=PlaybackState {state=2, position=-1, buffered position=0, speed=0.0, updated=7, actions=0, custom actions=[], active item id=-1, error=null}
`;
test('only playing or paused media sessions are reported, most recent first', () => {
  const s = parseSessions(dump);
  assert.deepEqual(s.map(x => [x.pkg, x.state]), [['com.netflix.ninja', 'playing'], ['com.google.android.youtube.tv', 'paused']]);
  assert.equal(s[0].title, 'Wednesday, S1:E2');
  assert.deepEqual(parseSessions(''), []);
});
test('foreground app and volume are parsed from dumpsys and media output', () => {
  assert.deepEqual(parseFocus('  mCurrentFocus=Window{435b135 u0 com.google.android.youtube.tv/com.google.android.apps.youtube.tv.activity.MainActivity}'), {pkg: 'com.google.android.youtube.tv', activity: 'com.google.android.apps.youtube.tv.activity.MainActivity'});
  assert.equal(parseFocus('mCurrentFocus=null'), null);
  assert.deepEqual(parseVolume('[v] will get volume\n[v] volume is 14 in range [0..15]'), {level: 14, max: 15});
  assert.equal(parseVolume('error'), null);
});
test('the status summary is readable and survives an unreachable TV', () => {
  const text = describeSnapshot({foreground: {label: 'YouTube'}, playing: [{app: 'Netflix', state: 'playing', title: 'Wednesday'}], volume: {level: 7, max: 15}});
  assert.match(text, /YouTube/); assert.match(text, /Netflix playing — Wednesday/); assert.match(text, /47%/);
  assert.match(describeSnapshot(null), /did not answer/);
});

test('a real Mi Box dump with spaced session tags and a null state is attributed correctly', async () => {
  const {readFile} = await import('node:fs/promises');
  const real = await readFile(new URL('./fixture-media-session.txt', import.meta.url), 'utf8');
  assert.deepEqual(parseSessions(real), [{pkg: 'com.spotify.tv.android', state: 'playing', title: 'Instant Crush (feat. Julian Casablancas), Daft Punk, Random Access Memories'}]);
  assert.equal(parseSessions('    Netflix media session com.netflix.ninja/Netflix media session (userId=0)\n      package=com.netflix.ninja\n      state=PlaybackState {state=2, position=1}\n      metadata:size=1, description=Wednesday, null, null\n')[0].title, 'Wednesday');
});

test('screenshot compression never loses the picture: bad input falls back to the original bytes', async () => {
  const {compressScreenshot} = await import('../tv.mjs');
  const notAnImage = Buffer.from('definitely not a png');
  const result = await compressScreenshot(notAnImage);
  assert.equal(result.mime, 'image/png'); assert.deepEqual(result.data, notAnImage);
});

test('chat can use recent background context without running another TV command, but stale context expires', async () => {
  const tv = new TvController({adb: 'unused', device: 'first'});
  let calls = 0;
  tv.shell = async () => {calls++; return 'mCurrentFocus=null\n@@\n\n@@\nvolume is 3 in range [0..15]';};
  tv.refreshApps = async () => {};
  assert.equal(tv.recentSnapshot().snapshot, null);
  const fresh = await tv.snapshot();
  assert.deepEqual(tv.recentSnapshot().snapshot, fresh); assert.equal(calls, 1);
  assert.equal(tv.recentSnapshot(10000, tv.snapshotCache.at + 10001).snapshot, null);
  tv.setDevice('second'); assert.equal(tv.recentSnapshot().snapshot, null);
});

test('a snapshot finishing after a TV switch cannot populate the new TV context', async () => {
  const tv = new TvController({adb: 'unused', device: 'first'});
  let finish;
  tv.shell = () => new Promise(resolve => {finish = resolve;});
  tv.refreshApps = async () => {};
  const pending = tv.snapshot(); tv.setDevice('second');
  finish(''); await pending;
  assert.equal(tv.recentSnapshot().snapshot, null);
});
