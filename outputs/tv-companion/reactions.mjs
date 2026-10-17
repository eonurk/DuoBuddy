// Buddy reacts to what's on: a short pop-in when a new title starts (headphones and a
// groove for music, peeking from under a blanket for horror, a scarf and a cheer for a
// match) and live goal alerts for football. It only reads the media session's title, which
// the TV already reports, and polls a public scoreboard while a match is on, so there are
// no screenshots and at most one small model call per new title.

export const VIBES = ['sports', 'scary', 'music', 'comedy', 'kids', 'romance', 'other'];
const MUSIC_APPS = new Set(['com.spotify.tv.android', 'com.google.android.youtube.tvmusic', 'tunein.player', 'com.appgeneration.itunerfree']);
const SPORT_WORDS = /\b(live|highlights|match|matchday|derby|football|soccer|voetbal|eredivisie|premier league|la ?liga|serie a|bundesliga|ligue 1|champions league|europa league|süper lig|super lig|world cup|euro 20\d\d|nba|nfl|f1|grand prix)\b/i;
const VERSUS = /([\p{L}][\p{L}0-9.'& ]{1,28}?)\s+(?:vs\.?|v\.?|–|-|x)\s+([\p{L}][\p{L}0-9.'& ]{1,28})/iu;
const SCARY = /\b(horror|haunt(ed|ing)?|scream|conjuring|exorcis\w*|nightmare|zombie|paranormal|insidious|the ring|it follows|halloween|annabelle|evil dead|hereditary)\b/i;
const KIDS = /\b(paddington|peppa|bluey|paw patrol|cocomelon|frozen|toy story|cartoon|pixar|disney junior|kids)\b/i;

const clean = s => String(s || '').replace(/\s+/g, ' ').trim();

/** Cheap first pass; null means "ask the model". */
export function classifyHeuristic({pkg, title}) {
  const t = clean(title);
  if (MUSIC_APPS.has(pkg)) return {vibe: 'music'};
  if (!t) return {vibe: 'other'};
  const versus = t.match(VERSUS);
  if (versus && SPORT_WORDS.test(t)) return {vibe: 'sports', teams: [clean(versus[1]), clean(versus[2]).replace(/\s*[|(\[].*$/, '')]};
  if (SCARY.test(t)) return {vibe: 'scary'};
  if (KIDS.test(t)) return {vibe: 'kids'};
  return null;
}

const norm = s => clean(s).normalize('NFD').replace(/\p{M}/gu, '').toLowerCase().replace(/[^a-z0-9 ]/g, ' ').replace(/\s+/g, ' ').trim();
const names = team => [team.displayName, team.shortDisplayName, team.name, team.location].filter(Boolean).map(norm).filter(n => n.length >= 3);
/** True when a team from the scoreboard is named in a piece of text. */
export function mentionsTeam(text, team) {
  const t = ' ' + norm(text) + ' ';
  return names(team).some(n => t.includes(' ' + n + ' ')) || (team.abbreviation && new RegExp('\\b' + team.abbreviation + '\\b').test(clean(text)));
}
/** Finds the scoreboard event a title is about (both teams named, or one plus parsed teams). */
export function findMatch(events, title, teams = []) {
  const text = [title, ...teams].join(' ');
  const scored = (events || []).map(e => {
    const sides = e.competitions?.[0]?.competitors || [];
    const hits = sides.filter(s => mentionsTeam(text, s.team)).length;
    return {e, hits, live: e.status?.type?.state === 'in'};
  }).filter(x => x.hits >= (teams.length ? 1 : 2));
  scored.sort((a, b) => b.hits - a.hits || b.live - a.live);
  return scored[0]?.e || null;
}
export function matchSummary(e) {
  const sides = e.competitions[0].competitors;
  const home = sides.find(s => s.homeAway === 'home') || sides[0], away = sides.find(s => s !== home);
  const side = s => ({id: s.team.id, name: s.team.shortDisplayName || s.team.displayName, full: s.team.displayName, abbreviation: s.team.abbreviation, color: s.team.color, alt: s.team.alternateColor, score: Number(s.score) || 0, team: s.team});
  return {id: e.id, state: e.status?.type?.state, detail: e.status?.type?.shortDetail || '', home: side(home), away: side(away)};
}
const line = m => `${m.home.name} ${m.home.score}–${m.away.score} ${m.away.name}`;
const hex = c => /^[0-9a-f]{6}$/i.test(c || '') ? '#' + c : null;
const scarf = s => ({primary: hex(s.color) || '#e11d48', secondary: hex(s.alt) && hex(s.alt).toLowerCase() !== hex(s.color)?.toLowerCase() ? hex(s.alt) : '#ffffff'});

const REACTIONS = {
  scary: {mood: 'scared', caption: 'Eek… I’ll watch from under my blanket.'},
  music: {mood: 'groove', caption: '♪ Ooh, I like this one.', outfit: {eyewear: 'headphones'}},
  comedy: {mood: 'laugh', caption: 'Hehe, this is a funny one.'},
  kids: {mood: 'cheer', caption: 'Ooh, one of my favourites!'},
  romance: {mood: 'aww', caption: 'Aww.', outfit: {eyewear: 'hearts'}}
};

export class ReactionWatcher {
  /**
   * snapshot(): the TV's media state; classify({pkg,title}): model fallback → {vibe, teams};
   * fetchJson(url); emit(reaction); notes(): saved notes, used to find the family's team.
   */
  constructor({snapshot, classify = null, fetchJson, emit, notes = () => [], enabled = () => true, idle = () => true, now = () => Date.now()}) {
    Object.assign(this, {snapshotFn: snapshot, classifyFn: classify, fetchJson, emit, notes, enabled, idle, now});
    this.cache = new Map(); this.title = ''; this.quietSince = 0; this.lastVibeAt = {}; this.match = null; this.scoreAt = 0; this.timer = null; this.id = 0;
  }
  start(ms = 15000) {if (!this.timer) {this.timer = setInterval(() => void this.tick().catch(e => console.log('Reactions: ' + e.message)), ms); this.timer.unref?.();}}
  stop() {clearInterval(this.timer); this.timer = null;}

  async classify(session) {
    const key = session.pkg + '|' + session.title;
    if (this.cache.has(key)) return this.cache.get(key);
    let result = classifyHeuristic(session);
    if (!result && this.classifyFn) try {result = await this.classifyFn(session);} catch (e) {console.log('Reaction classifier: ' + e.message);}
    result ||= {vibe: 'other'};
    this.cache.set(key, result); if (this.cache.size > 300) this.cache.delete(this.cache.keys().next().value);
    return result;
  }
  react(r) {this.emit({id: ++this.id, ms: 4500, ...r});}

  async tick() {
    if (!this.enabled() || !this.idle()) return;
    const snap = await this.snapshotFn();
    const playing = snap?.playing?.find(p => p.state === 'playing' && p.title);
    const now = this.now();
    if (!playing) {
      // After a few quiet minutes the same title counts as new again.
      if (!this.quietSince) this.quietSince = now;
      if (now - this.quietSince > 3 * 60 * 1000) {this.title = ''; this.match = null;}
      return;
    }
    this.quietSince = 0;
    if (playing.title !== this.title) {
      this.title = playing.title; this.match = null;
      const {vibe, teams = []} = await this.classify(playing);
      if (vibe === 'sports') return this.startMatch(playing.title, teams);
      const reaction = REACTIONS[vibe];
      // The same kind of reaction at most every 20 minutes, so a playlist doesn't nag.
      if (reaction && now - (this.lastVibeAt[vibe] || 0) > 20 * 60 * 1000) {this.lastVibeAt[vibe] = now; this.react({vibe, ...reaction});}
      return;
    }
    if (this.match && now - this.scoreAt > 60000) await this.checkScore();
  }

  async scoreboard() {return (await this.fetchJson('https://site.api.espn.com/apis/site/v2/sports/soccer/all/scoreboard')).events || [];}
  favourite(m) {const text = this.notes().join(' '); return [m.home, m.away].find(s => mentionsTeam(text, s.team)) || null;}

  async startMatch(title, teams) {
    let event = null;
    try {event = findMatch(await this.scoreboard(), title, teams);} catch (e) {console.log('Scoreboard unavailable: ' + e.message);}
    this.scoreAt = this.now();
    if (!event) {this.react({vibe: 'sports', mood: 'cheer', caption: 'Match time! Go go go!', outfit: {neck: 'teamscarf'}, scarf: {primary: '#e11d48', secondary: '#ffffff'}}); return;}
    const m = matchSummary(event), fav = this.favourite(m);
    this.match = {id: m.id, state: m.state, home: m.home.score, away: m.away.score, fav: fav?.id || ''};
    const caption = m.state === 'in' ? `${fav ? 'Come on, ' + fav.name + '! ' : ''}${line(m)} · ${m.detail}` : fav ? `Come on, ${fav.name}!` : `${m.home.name} v ${m.away.name}, here we go!`;
    this.react({vibe: 'sports', mood: 'cheer', caption, outfit: {neck: 'teamscarf'}, scarf: scarf(fav || m.home)});
  }
  async checkScore() {
    this.scoreAt = this.now();
    let event;
    try {event = (await this.scoreboard()).find(e => e.id === this.match.id);} catch (e) {console.log('Scoreboard unavailable: ' + e.message); return;}
    if (!event) return;
    const m = matchSummary(event), before = this.match;
    const scorer = m.home.score > before.home ? m.home : m.away.score > before.away ? m.away : null;
    this.match = {...before, home: m.home.score, away: m.away.score};
    this.match.state = m.state;
    const fav = [m.home, m.away].find(s => s.id === before.fav), rival = fav && (fav === m.home ? m.away : m.home);
    if (m.state === 'post' && before.state !== 'post') {this.react({vibe: 'sports', mood: fav && fav.score < rival.score ? 'sad' : 'cheer', caption: 'Full time: ' + line(m), outfit: {neck: 'teamscarf'}, scarf: scarf(fav || m.home)}); return;}
    if (!scorer) return;
    // With a known favourite, Buddy is glum when the other side scores.
    const glum = fav && scorer.id !== fav.id;
    this.react({vibe: 'goal', mood: glum ? 'sad' : 'cheer', caption: (glum ? 'Oh no… ' : 'GOAL! ') + line(m), outfit: {neck: 'teamscarf'}, scarf: scarf(glum ? fav : scorer), ms: 6000});
  }
}
