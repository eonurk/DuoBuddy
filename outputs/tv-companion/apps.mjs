// App registry for Android TV and Google TV. Known apps get friendly names, aliases and search
// deep links; every other launchable app on the TV is discovered at runtime, so
// Buddy can open anything installed without a code change.
const enc = encodeURIComponent;

export const KNOWN_APPS = Object.freeze({
  stremio: {pkg: 'com.stremio.one', label: 'Stremio', aliases: [], search: 'stremio'},
  youtube: {pkg: 'com.google.android.youtube.tv', label: 'YouTube', aliases: ['yt'], search: q => ({uri: 'https://www.youtube.com/results?search_query=' + enc(q)})},
  youtube_music: {pkg: 'com.google.android.youtube.tvmusic', label: 'YouTube Music', aliases: ['yt music']},
  netflix: {pkg: 'com.netflix.ninja', label: 'Netflix', aliases: [], search: q => ({uri: 'nflx://www.netflix.com/search?q=' + enc(q)})},
  prime: {pkg: 'com.amazon.amazonvideo.livingroom', label: 'Prime Video', aliases: ['amazon', 'amazon prime', 'prime video'], search: q => ({uri: 'https://app.primevideo.com/search?phrase=' + enc(q)})},
  // On the TV app the link only fills the search box; pressing Enter then plays the best match.
  spotify: {pkg: 'com.spotify.tv.android', label: 'Spotify', aliases: [], submit: 'plays the best match', search: q => ({uri: 'spotify:search:' + enc(q)})},
  plex: {pkg: 'com.plexapp.android', label: 'Plex', aliases: []},
  vlc: {pkg: 'org.videolan.vlc', label: 'VLC', aliases: []},
  mx_player: {pkg: 'com.mxtech.videoplayer.ad', label: 'MX Player', aliases: ['mx']},
  tunein: {pkg: 'tunein.player', label: 'TuneIn Radio', aliases: []},
  radio: {pkg: 'com.appgeneration.itunerfree', label: 'myTuner Radio', aliases: ['mytuner']},
  play_store: {pkg: 'com.android.vending', label: 'Play Store', aliases: ['google play', 'store'], search: q => ({uri: 'market://search?q=' + enc(q) + '&c=apps'})},
  play_movies: {pkg: 'com.google.android.videos', label: 'Google TV Movies', aliases: ['google tv', 'play movies']},
  play_games: {pkg: 'com.google.android.play.games', label: 'Play Games', aliases: ['games']},
  browser: {pkg: 'com.cloudmosa.puffinTV', label: 'Puffin Browser', aliases: ['puffin', 'web browser', 'internet']},
  files: {pkg: 'com.lonelycatgames.Xplore', label: 'X-plore Files', aliases: ['file manager', 'xplore']},
  weather: {pkg: 'com.macropinch.swan', label: 'Weather', aliases: []},
  fitness: {pkg: 'com.gotokeep.keep.intl', label: 'Keep Fitness', aliases: ['keep', 'workout']},
  redbull: {pkg: 'com.nousguide.android.rbtv', label: 'Red Bull TV', aliases: ['red bull']},
  nordvpn: {pkg: 'com.nordvpn.android', label: 'NordVPN', aliases: ['vpn', 'nord']},
  live_tv: {pkg: 'com.google.android.tv', label: 'Live TV', aliases: ['tv', 'live']},
  mi_home: {pkg: 'com.mitv.tvhome.atv', label: 'Mi Home', aliases: ['patchwall', 'launcher']},
  companion: {pkg: 'family.tv.companion', label: 'DuoBuddy', aliases: ['duobuddy', 'duobook', 'buddy']}
});

// Apps that exist to install or sideload other software: Buddy may not drive them
// on its own, because installing apps needs the user's explicit action.
const OFF_LIMITS = new Set(['com.esaba.downloader', 'cm.aptoide.pt']);

export function parseLaunchables(output) {
  const found = new Map();
  for (const line of String(output).split('\n')) {
    const m = line.match(/^\s*([A-Za-z0-9_.]+)\/(\S+)\s*$/);
    if (m && !found.has(m[1])) found.set(m[1], m[1] + '/' + m[2]);
  }
  return found;
}

function titleFromPackage(pkg) {
  const parts = pkg.split('.').filter(p => !['com', 'org', 'net', 'android', 'tv', 'app', 'apps', 'atv', 'ninja', 'player', 'mobile', 'intl'].includes(p));
  const word = (parts.at(-1) || pkg).replace(/[_-]+/g, ' ');
  return word.charAt(0).toUpperCase() + word.slice(1);
}

const norm = s => String(s ?? '').toLowerCase().replace(/[^a-z0-9]+/g, ' ').trim();

export class AppCatalog {
  constructor() {this.apps = []; this.byPackage = new Map(); this.loadedAt = 0;}
  load(launchables) {
    const apps = [];
    for (const [pkg, component] of launchables) {
      if (OFF_LIMITS.has(pkg)) continue;
      const known = Object.entries(KNOWN_APPS).find(([, a]) => a.pkg === pkg);
      apps.push({key: known ? known[0] : pkg, pkg, component, label: known ? known[1].label : titleFromPackage(pkg), known: !!known});
    }
    apps.sort((a, b) => Number(b.known) - Number(a.known) || a.label.localeCompare(b.label));
    this.apps = apps; this.byPackage = new Map(apps.map(a => [a.pkg, a])); this.loadedAt = Date.now();
    return apps;
  }
  resolve(name) {
    const wanted = norm(name);
    if (!wanted) throw Error('Name an app to open.');
    const hit = this.apps.find(a => norm(a.key) === wanted || norm(a.label) === wanted || a.pkg === name
      || (KNOWN_APPS[a.key]?.aliases || []).some(x => norm(x) === wanted))
      || this.apps.find(a => norm(a.label).includes(wanted) || wanted.includes(norm(a.label)));
    if (!hit) throw Error(`"${name}" is not installed. Installed apps: ${this.apps.map(a => a.label).join(', ')}.`);
    return hit;
  }
  label(pkg) {return this.byPackage.get(pkg)?.label || (pkg ? titleFromPackage(pkg) : '');}
  searchable() {return this.apps.filter(a => KNOWN_APPS[a.key]?.search).map(a => a.label);}
  describe() {return this.apps.map(a => a.label).join(', ');}
}

// Returns {stremio:true} for Stremio's special flow, {uri,pkg} for a deep link, or null.
export function searchTarget(app, query) {
  if (typeof query !== 'string' || !query.trim() || query.length > 200) throw Error('Use a search phrase of 1–200 characters.');
  const search = KNOWN_APPS[app.key]?.search;
  if (!search) return null;
  if (search === 'stremio') return {stremio: true};
  return {...search(query.trim()), pkg: app.pkg, submit: KNOWN_APPS[app.key].submit || ''};
}

export function validateUrl(url) {
  if (typeof url !== 'string' || url.length > 500 || !/^https:\/\/[^\s]+$/i.test(url)) throw Error('Only https links up to 500 characters can be opened.');
  return url;
}
