import {KEYS, MEDIA_KEYS, describeSnapshot} from './tv.mjs';
import {searchTarget, validateUrl} from './apps.mjs';
import {spokenDuration} from './timers.mjs';

const pause = ms => new Promise(r => setTimeout(r, ms));
const nullable = (type, extra = {}) => ({type: [type, 'null'], ...extra});

// Strict function schemas require every property, so optional ones are nullable.
function fn(name, description, properties = {}) {
  return {type: 'function', name, description, strict: true, parameters: {type: 'object', properties, required: Object.keys(properties), additionalProperties: false}};
}

export const TOOLS = [
  fn('open_app', 'Open any installed TV app by name (e.g. YouTube, Netflix, Spotify, Plex, VLC, Prime Video, Stremio, a browser). Use the installed-app list in your context.',
    {app: {type: 'string', description: 'App name or alias.'}}),
  fn('search_app', 'Search inside an app that supports it: YouTube, Netflix, Spotify, Prime Video, Stremio, Play Store. Opens the app on the search results. Verify with read_screen.',
    {app: {type: 'string'}, query: {type: 'string', description: 'Title, artist, topic or phrase to search for.'}}),
  fn('open_url', 'Open an https link; Android picks the app that handles it (e.g. a YouTube link opens in YouTube).',
    {url: {type: 'string'}}),
  fn('remote', 'Press a remote-control button, optionally several times in a row (menus, scrolling, selecting). Also sleep/wake the TV.',
    {key: {type: 'string', enum: Object.keys(KEYS).filter(k => !k.startsWith('volume') && k !== 'mute')}, times: nullable('integer', {description: 'Repeat count 1–25. Null for once.'})}),
  fn('media', 'Control whatever is playing: play, pause, toggle, next/previous track or episode, stop, rewind or fast-forward.',
    {command: {type: 'string', enum: Object.keys(MEDIA_KEYS)}}),
  fn('volume', 'Change TV volume. up/down move by steps; set takes a percentage 0–100; mute/unmute remember the old level.',
    {action: {type: 'string', enum: ['up', 'down', 'set', 'mute', 'unmute']}, amount: nullable('integer', {description: 'Percent for set (0–100); steps for up/down (1–8, default 2). Null otherwise.'})}),
  fn('read_screen', 'Take a screenshot of the TV. Use it to verify results, find items to select, or read what an app shows.'),
  fn('tap', 'Tap a point using pixel coordinates from the latest read_screen.', {x: {type: 'integer'}, y: {type: 'integer'}}),
  fn('type_text', 'Type into the focused text field, optionally pressing Enter afterwards.',
    {text: {type: 'string'}, submit: {type: 'boolean'}}),
  fn('wait', 'Wait for an app to load before looking again.', {seconds: {type: 'integer', description: '1–8'}}),
  fn('tv_status', 'Refresh what is on screen, what is playing and the volume. Your context already has this at the start of the turn; call it after changes.'),
  fn('timer', 'Set, list or cancel timers and reminders. When a timer fires, DuoBuddy speaks the label.',
    {action: {type: 'string', enum: ['set', 'list', 'cancel']}, seconds: nullable('integer'), label: nullable('string'), id: nullable('integer')}),
  fn('memory', 'Remember, list or forget short notes. Only when the user explicitly asks you to remember or forget something. Notes go to the person at the TV when one has said who they are, otherwise to the household.',
    {action: {type: 'string', enum: ['remember', 'list', 'forget']}, note: nullable('string', {description: 'Note to save, or text matching notes to forget.'}),
      scope: nullable('string', {enum: ['person', 'household', null], description: 'household for things about everyone ("we", "the kids"), person for the speaker\'s own preferences. Null to decide automatically.'})}),
  fn('profile', 'Who is at the TV. switch when someone says who they are ("it\'s Mum", "this is Onur"); leave when they say they are going or hand over; list to name everyone DuoBuddy knows; birthday to save someone\'s birthday.',
    {action: {type: 'string', enum: ['switch', 'leave', 'list', 'birthday']}, name: nullable('string', {description: 'First name or family title, e.g. Mum.'}), birthday: nullable('string', {description: 'MM-DD for the birthday action.'})}),
  fn('screensaver', 'Show DuoBuddy\'s calm screensaver dashboard (clock, weather, word of the day, notes) now. Any remote button closes it.')
];

export const WEB_SEARCH_TOOL = {type: 'web_search'};

// Actions whose result the model does not need to read back: when it already said its
// reply alongside one of these and the action succeeded, the turn ends without another call.
export function isOneShot(name, a) {
  switch (name) {
    case 'media': case 'volume': case 'screensaver': return true;
    case 'remote': return a.key === 'sleep' || a.key === 'wake';
    case 'timer': return a.action === 'set' || a.action === 'cancel';
    case 'memory': return a.action === 'remember';
    case 'profile': return a.action === 'switch' || a.action === 'leave' || a.action === 'birthday';
    default: return false;
  }
}

const int = (v, lo, hi, what) => {
  if (!Number.isInteger(v) || v < lo || v > hi) throw Error(`${what} must be a whole number from ${lo} to ${hi}.`);
  return v;
};
const text = (v, what, max = 200) => {
  if (typeof v !== 'string' || !v.trim() || v.length > max || /[\x00-\x1F\x7F]/.test(v)) throw Error(`${what} must be 1–${max} printable characters.`);
  return v;
};

// Maps the old {action,value} shape used by earlier TV builds and the setup page.
export function legacyToCall(a) {
  if (!a || typeof a !== 'object') throw Error('Missing action.');
  const map = {
    open_app: () => ['open_app', {app: a.value}],
    key: () => ['remote', {key: a.value, times: 1}],
    search_stremio: () => ['search_app', {app: 'stremio', query: a.value}],
    tap: () => ['tap', {x: a.x, y: a.y}],
    type_text: () => ['type_text', {text: a.value, submit: false}],
    read_screen: () => ['read_screen', {}]
  };
  if (!Object.hasOwn(map, a.action)) throw Error('Unsupported action.');
  const [name, args] = map[a.action]();
  return {name, args};
}

export function validateCall(name, a, {size = {width: 1920, height: 1080}} = {}) {
  if (!a || typeof a !== 'object') throw Error('Missing arguments.');
  switch (name) {
    case 'open_app': text(a.app, 'App name', 80); break;
    case 'search_app': text(a.app, 'App name', 80); searchTarget({key: ''}, text(a.query, 'Search phrase')); break;
    case 'open_url': validateUrl(a.url); break;
    case 'remote':
      if (!Object.hasOwn(KEYS, a.key) || /^volume|^mute$/.test(a.key)) throw Error('Unknown remote button.');
      if (a.times != null) int(a.times, 1, 25, 'times');
      break;
    case 'media': if (!Object.hasOwn(MEDIA_KEYS, a.command)) throw Error('Unknown media command.'); break;
    case 'volume':
      if (!['up', 'down', 'set', 'mute', 'unmute'].includes(a.action)) throw Error('Unknown volume action.');
      if (a.action === 'set') int(a.amount, 0, 100, 'Volume percent');
      else if (['up', 'down'].includes(a.action) && a.amount != null) int(a.amount, 1, 8, 'Steps');
      break;
    case 'tap':
      int(a.x, 0, size.width - 1, 'x'); int(a.y, 0, size.height - 1, 'y'); break;
    case 'type_text': text(a.text, 'Text'); break;
    case 'wait': int(a.seconds, 1, 8, 'seconds'); break;
    case 'read_screen': case 'tv_status': break;
    case 'timer':
      if (!['set', 'list', 'cancel'].includes(a.action)) throw Error('Unknown timer action.');
      if (a.action === 'set') int(a.seconds, 1, 86400, 'seconds');
      if (a.action === 'cancel') int(a.id, 1, 1e6, 'id');
      break;
    case 'memory':
      if (!['remember', 'list', 'forget'].includes(a.action)) throw Error('Unknown memory action.');
      if (a.action !== 'list') text(a.note, 'Note');
      if (a.scope != null && !['person', 'household'].includes(a.scope)) throw Error('Unknown memory scope.');
      break;
    case 'profile':
      if (!['switch', 'leave', 'list', 'birthday'].includes(a.action)) throw Error('Unknown profile action.');
      if (a.action === 'switch') text(a.name, 'Name', 30);
      if (a.action === 'birthday') text(a.birthday, 'Birthday', 5);
      break;
    case 'screensaver': break;
    default: throw Error('Unknown tool.');
  }
  return a;
}

export function createToolRunner({tv, timers, memory, status = () => {}, onProfile = () => {}}) {
  const percent = v => v ? Math.round(v.level / v.max * 100) : null;
  return async function run(name, args) {
    validateCall(name, args, {size: tv.size});
    status(name.replace('_', ' '));
    switch (name) {
      case 'open_app': {
        const app = await tv.launch(args.app);
        await pause(900);
        return {message: 'Opened ' + app.label + '.'};
      }
      case 'search_app': {
        await tv.refreshApps();
        const app = tv.catalog.resolve(args.app);
        const target = searchTarget(app, args.query);
        if (!target) throw Error(`${app.label} has no search link. Searchable apps: ${tv.catalog.searchable().join(', ')}. Open ${app.label} and navigate with remote, type_text and read_screen instead.`);
        if (target.stremio) await tv.stremioSearch(args.query); else await tv.openUri(target.uri, target.pkg);
        if (target.submit) {
          // The link only fills the search box. Wait for the app, give its UI a moment, then press Enter.
          await tv.waitForForeground(target.pkg); await pause(3000); await tv.keys(KEYS.enter);
          // Playback takes a few seconds to register, so look for it instead of reporting too early.
          for (let i = 0; i < 5; i++) {
            await pause(i ? 1000 : 2000);
            const now = await tv.snapshot().catch(() => null);
            const live = now?.playing.find(p => p.pkg === target.pkg && ['playing', 'buffering'].includes(p.state));
            if (live) return {message: `Searched ${app.label} for "${args.query.trim()}" and pressed Enter. ${app.label} is now ${live.state}${live.title ? ': ' + live.title : ''}.`};
          }
          return {message: `Searched ${app.label} for "${args.query.trim()}" and pressed Enter, which ${target.submit}, but no playback has started yet. Check with tv_status, then read_screen if it is still silent.`};
        }
        await pause(2000);
        return {message: `Opened a ${app.label} search for "${args.query.trim()}". Use read_screen to verify the results.`};
      }
      case 'open_url': await tv.openUri(args.url); await pause(1500); return {message: 'Opened the link.'};
      case 'remote': await tv.keys(KEYS[args.key], args.times ?? 1); await pause(450); return {message: `Pressed ${args.key}${args.times > 1 ? ' ×' + args.times : ''}.`};
      case 'media': await tv.keys(MEDIA_KEYS[args.command]); await pause(500); return {message: 'Sent ' + args.command + '.'};
      case 'volume': {
        const now = await tv.volume();
        if (args.action === 'set') {const v = await tv.setVolume(args.amount / 100 * (now?.max ?? 15)); return {message: `Volume set to ${percent(v)}%.`};}
        if (args.action === 'mute') {if (now?.level) tv.mutedFrom = now.level; await tv.setVolume(0); return {message: 'Muted.'};}
        if (args.action === 'unmute') {const v = await tv.setVolume(tv.mutedFrom || 6); return {message: `Unmuted at ${percent(v)}%.`};}
        await tv.keys(args.action === 'up' ? KEYS.volume_up : KEYS.volume_down, args.amount ?? 2);
        await pause(300);
        const v = await tv.volume();
        return {message: `Volume is now ${percent(v)}%.`};
      }
      case 'read_screen': {
        const s = await tv.screen();
        return {message: `TV screen ${s.width}×${s.height}, showing ${s.focused}. Coordinates use these dimensions.`, image: s.image, mime: s.mime};
      }
      case 'tap': await tv.shell(`input tap ${args.x} ${args.y}`); await pause(650); return {message: `Tapped ${args.x}, ${args.y}.`};
      case 'type_text': await tv.pasteText(args.text); if (args.submit) await tv.keys(KEYS.enter); return {message: 'Typed into the focused field' + (args.submit ? ' and pressed Enter.' : '.')};
      case 'wait': await pause(args.seconds * 1000); return {message: `Waited ${args.seconds}s.`};
      case 'tv_status': return {message: describeSnapshot(await tv.snapshot().catch(() => null))};
      case 'timer': {
        if (args.action === 'set') {const t = timers.set(args.seconds, args.label); return {message: `Timer ${t.id} set for ${spokenDuration(args.seconds)}${t.label ? ' (' + t.label + ')' : ''}.`};}
        if (args.action === 'cancel') {const t = timers.cancel(args.id); return {message: `Cancelled timer ${t.id}.`};}
        const list = timers.list();
        return {message: list.length ? list.map(t => `Timer ${t.id}${t.label ? ' (' + t.label + ')' : ''}: ${spokenDuration(t.secondsLeft)} left`).join('; ') + '.' : 'No timers are running.'};
      }
      case 'memory': {
        const scope = args.scope ?? undefined;
        if (args.action === 'remember') {const s = memory.scopeFor(scope); return {message: `Saved for ${s === 'person' ? memory.current() : 'the household'}: ` + await memory.add(args.note, s)};}
        if (args.action === 'forget') return {message: `Forgot ${await memory.forget(args.note, scope)} note(s).`};
        const who = memory.current(), mine = who ? memory.list('person') : [];
        const lines = [...mine.map(n => `${who}: ${n}`), ...memory.notes.map(n => `Household: ${n}`)];
        return {message: lines.length ? lines.map((n, i) => `${i + 1}. ${n}`).join('\n') : 'Nothing saved yet.'};
      }
      case 'profile': {
        if (args.action === 'switch') {const p = await memory.switchTo(args.name); onProfile(p.name); return {message: p.isNew ? `New profile for ${p.name}; they are now at the TV.` : `${p.name} is now at the TV.`};}
        if (args.action === 'leave') {const was = memory.current(); memory.leave(); onProfile(''); return {message: was ? `${was} left; back to the household.` : 'Nobody was signed in.'};}
        if (args.action === 'birthday') {const who = await memory.setBirthday(args.name, args.birthday); return {message: `Saved ${who}'s birthday (${memory.people[who].birthday}).`};}
        const people = Object.entries(memory.people).map(([n, p]) => n + (p.birthday ? ` (birthday ${p.birthday})` : ''));
        return {message: people.length ? 'Known people: ' + people.join(', ') + '. At the TV: ' + (memory.current() || 'the household') + '.' : 'No one has introduced themselves yet.'};
      }
      case 'screensaver': await tv.showAmbient(); return {message: 'Showing the screensaver. Any button closes it.'};
    }
  };
}
