import test from 'node:test';
import assert from 'node:assert/strict';
import {AppCatalog, parseLaunchables, KNOWN_APPS} from '../apps.mjs';

const sample = `29 activities found:
  Activity #0:
    priority=0 preferredOrder=0 match=0x108000 specificIndex=-1 isDefault=true
    com.android.vending/com.google.android.finsky.tvmainactivity.TvMainActivity
  Activity #1:
    com.google.android.youtube.tv/com.google.android.apps.youtube.tv.activity.ShellActivity
  Activity #2:
    com.spotify.tv.android/.SpotifyTVActivity
  Activity #3:
    com.esaba.downloader/.ui.main.MainActivity
  Activity #4:
    com.dsmart.blu.android/com.foxxum.webapp.MainActivity
  Activity #5:
    com.stremio.one/.MainActivity
  Activity #6:
    com.stremio.one/.MainActivity
`;
test('launcher output yields one component per package', () => {
  const found = parseLaunchables(sample);
  assert.equal(found.size, 6);
  assert.equal(found.get('com.spotify.tv.android'), 'com.spotify.tv.android/.SpotifyTVActivity');
  assert.equal(parseLaunchables('com.spotify.tv.android/.SpotifyTVActivity\n').get('com.spotify.tv.android'), 'com.spotify.tv.android/.SpotifyTVActivity');
});
test('apps resolve by key, label, alias and package; sideloaders stay off limits', () => {
  const catalog = new AppCatalog(); catalog.load(parseLaunchables(sample));
  assert.equal(catalog.resolve('YouTube').pkg, 'com.google.android.youtube.tv');
  assert.equal(catalog.resolve('yt').key, 'youtube');
  assert.equal(catalog.resolve('google play').pkg, 'com.android.vending');
  assert.equal(catalog.resolve('com.dsmart.blu.android').pkg, 'com.dsmart.blu.android');
  assert.ok(catalog.apps.every(a => a.pkg !== 'com.esaba.downloader'));
  assert.throws(() => catalog.resolve('Downloader'), /not installed/);
  assert.throws(() => catalog.resolve('rm -rf'), /not installed/);
  assert.deepEqual(catalog.searchable().sort(), ['Play Store', 'Spotify', 'Stremio', 'YouTube']);
});
test('every known app has a package and unique aliases', () => {
  const seen = new Set();
  for (const [key, app] of Object.entries(KNOWN_APPS)) {
    assert.match(app.pkg, /^[a-z0-9_.]+$/i, key);
    for (const a of [key, ...app.aliases]) {assert.ok(!seen.has(a), a); seen.add(a);}
  }
});
