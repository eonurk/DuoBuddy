import {execFile} from 'node:child_process';
import {promisify} from 'node:util';
import {mkdtemp, writeFile, readFile, rm} from 'node:fs/promises';
import {tmpdir} from 'node:os';
import {join} from 'node:path';
import {AppCatalog, parseLaunchables} from './apps.mjs';

const exec = promisify(execFile);
const pause = ms => new Promise(r => setTimeout(r, ms));

export function quote(value) {return "'" + String(value).replaceAll("'", "'\\''") + "'";}

export const KEYS = Object.freeze({
  up: 19, down: 20, left: 21, right: 22, select: 23, enter: 66, back: 4, home: 3,
  menu: 82, search: 84, info: 165, guide: 172, delete: 67, page_up: 92, page_down: 93,
  channel_up: 166, channel_down: 167, play_pause: 85, volume_up: 24, volume_down: 25, mute: 164,
  sleep: 223, wake: 224
});
export const MEDIA_KEYS = Object.freeze({play: 126, pause: 127, play_pause: 85, next: 87, previous: 88, stop: 86, rewind: 89, fast_forward: 90});

const PLAYBACK = {2: 'paused', 3: 'playing', 4: 'fast-forwarding', 5: 'rewinding', 6: 'buffering'};

// The focused window, or else the focused activity (newer Android may report only that one,
// e.g. while an overlay holds focus).
export function parseFocus(out) {
  const m = String(out).match(/Window\{\S+ u\d+ ([A-Za-z0-9_.]+)\/(\S+?)\}/) || String(out).match(/ActivityRecord\{\S+ u\d+ ([A-Za-z0-9_.]+)\/(\S+?)[\s}]/);
  return m ? {pkg: m[1], activity: m[2]} : null;
}
// Android 9 lists the focus under "dumpsys window windows"; Android 10 and later only in the full dump.
const FOCUS = "dumpsys window | grep -E 'mCurrentFocus|mFocusedApp'";
// The `media` command was replaced by `cmd media_session` on newer Android releases.
const volumeCommand = args => `(media volume --stream 3 ${args} || cmd media_session volume --stream 3 ${args}) 2>&1`;
export const COMPANION = 'family.tv.companion';

// "Xiaomi MIBOX4 · Android 9" from getprop; brand and model are often the same word.
export function parseDeviceInfo(out) {
  const [manufacturer = '', model = '', release = '', sdk = '', launcher = ''] = String(out).split('\n').map(s => s.trim());
  const name = model.toLowerCase().startsWith(manufacturer.toLowerCase()) ? model : [manufacturer, model].filter(Boolean).join(' ');
  return {manufacturer, model, name: name || 'Android TV', android: release, sdk: Number(sdk) || 0, googleTv: /^package:/.test(launcher)};
}
export function describeDevice(info) {
  if (!info) return 'an Android TV';
  return `${info.googleTv ? 'a Google TV' : 'an Android TV'} (${info.name}${info.android ? ', Android ' + info.android : ''})`;
}
export function parseVolume(out) {
  const m = String(out).match(/volume is (\d+) in range \[(\d+)\.\.(\d+)\]/);
  return m ? {level: Number(m[1]), max: Number(m[3])} : null;
}
// Active and paused media sessions, most recent first. Idle sessions (state 0) are dropped.
export function parseSessions(dump) {
  const sessions = [];
  let current = null;
  for (const line of String(dump).split('\n')) {
    // Session tags may contain spaces ("Netflix media session com.netflix.ninja/..."), so match the whole header line.
    if (/^ {4}\S.*\/.* \(userId=\d+\)\s*$/.test(line)) {current = {}; sessions.push(current); continue;}
    if (!current) continue;
    const pkg = line.match(/^\s+package=([A-Za-z0-9_.]+)/);
    if (pkg) current.pkg = pkg[1];
    const state = line.match(/state=PlaybackState \{state=(\d+)/);
    if (state) current.state = Number(state[1]);
    const meta = line.match(/^\s+metadata:size=\d+, description=(.*)$/);
    if (meta) current.title = meta[1].split(', ').filter(p => p.trim() && p.trim() !== 'null').join(', ');
  }
  return sessions.filter(s => s.pkg && PLAYBACK[s.state]).map(s => ({pkg: s.pkg, state: PLAYBACK[s.state], title: s.title || ''}));
}

// A 1080p PNG is several megabytes; as JPEG at the same size it is a fraction of that,
// so every look at the screen uploads and returns much faster. Pixel size is unchanged,
// so tap coordinates stay valid. Falls back to the PNG if the conversion is unavailable.
export async function compressScreenshot(png) {
  let dir;
  try {
    dir = await mkdtemp(join(tmpdir(), 'buddy-shot-'));
    await writeFile(join(dir, 'in.png'), png);
    await exec('/usr/bin/sips', ['-s', 'format', 'jpeg', '-s', 'formatOptions', '70', join(dir, 'in.png'), '--out', join(dir, 'out.jpg')], {timeout: 8000});
    const jpg = await readFile(join(dir, 'out.jpg'));
    if (jpg.length > 1000 && jpg.length < png.length) return {data: jpg, mime: 'image/jpeg'};
  } catch {} finally {if (dir) await rm(dir, {recursive: true, force: true}).catch(() => {});}
  return {data: png, mime: 'image/png'};
}

export class TvController {
  constructor({adb, device}) {
    this.adbPath = adb; this.device = device;
    this.catalog = new AppCatalog();
    this.size = {width: 1920, height: 1080};
    this.mutedFrom = null;
    this.info = null;
    this.snapshotCache = null; this.deviceGeneration = 0;
  }
  // Switching TVs forgets everything learned about the previous one.
  setDevice(device) {this.device = device; this.deviceGeneration++; this.snapshotCache = null; this.info = null; this.catalog = new AppCatalog(); this.size = {width: 1920, height: 1080}; this.mutedFrom = null;}
  async connect() {
    const {stdout, stderr} = await exec(this.adbPath, ['connect', this.device], {timeout: 10000});
    if (/failed|unable|refused|cannot|authenticate/i.test(stdout + stderr)) throw Error((stdout + stderr).trim().slice(0, 300));
    return this.deviceInfo(true);
  }
  // Newer Google TV builds only allow wireless debugging after a one-time pairing code.
  async pair(address, code) {
    const {stdout, stderr} = await exec(this.adbPath, ['pair', address, code], {timeout: 20000}).catch(e => ({stdout: String(e.stdout || ''), stderr: String(e.stderr || e.message)}));
    if (!/Successfully paired/i.test(stdout)) throw Error(('Pairing failed: ' + (stdout + ' ' + stderr).trim()).slice(0, 300));
  }
  async deviceInfo(force = false) {
    if (this.info && !force) return this.info;
    const device = this.device;
    const out = await this.shell('getprop ro.product.manufacturer; getprop ro.product.model; getprop ro.build.version.release; getprop ro.build.version.sdk; pm path com.google.android.apps.tv.launcherx 2>/dev/null; true', {timeout: 8000});
    const info = parseDeviceInfo(out);
    if (this.device === device) this.info = info;
    return info;
  }
  async companionVersion() {
    const m = (await this.shell('dumpsys package ' + COMPANION + ' | grep versionCode')).match(/versionCode=(\d+)/);
    return m ? Number(m[1]) : 0;
  }
  // Installs or updates DuoBuddy, allows its microphone and overlay captions, pairs it with this
  // controller and opens it. Everything here is something the user would otherwise do by hand.
  async installCompanion({apk, server, token}) {
    const out = await this.adb(['install', '-r', apk], {timeout: 180000});
    if (!/Success/.test(out)) throw Error('Install failed: ' + out.trim().slice(-300));
    await this.checkedShell(`pm grant ${COMPANION} android.permission.RECORD_AUDIO`);
    await this.checkedShell(`appops set ${COMPANION} SYSTEM_ALERT_WINDOW allow`);
    await this.checkedShell(`am start -n ${COMPANION}/.MainActivity --es server ${quote(server)} --es token ${quote(token)}`);
  }
  // Google TV hides third-party screensavers from its settings, so this sets it directly.
  async useBuddyScreensaver() {
    if (!(await this.companionVersion())) throw Error('Install DuoBuddy on this TV first.');
    await this.checkedShell(`settings put secure screensaver_components ${COMPANION}/.BuddyDream`);
    await this.checkedShell('settings put secure screensaver_enabled 1');
  }
  async adb(args, {binary = false, timeout = 25000} = {}) {
    if (!this.device) throw Error('Connect your TV on the Mac setup page first.');
    const options = {timeout, maxBuffer: 12 * 1024 * 1024, encoding: binary ? 'buffer' : 'utf8'};
    try {return (await exec(this.adbPath, ['-s', this.device, ...args], options)).stdout;}
    catch (e) {
      if (/offline|not found|no devices/.test(String(e.stderr || e.message))) {
        await exec(this.adbPath, ['connect', this.device], {timeout: 7000});
        return (await exec(this.adbPath, ['-s', this.device, ...args], options)).stdout;
      }
      throw Error(String(e.stderr || e.message).slice(0, 700));
    }
  }
  shell(command, options) {return this.adb(['shell', command], options);}
  async checkedShell(command) {
    const out = await this.shell(command);
    if (/Error:|Exception|Permission denial|Unknown (?:command|package|component)|does not exist/i.test(out)) throw Error(out.trim().slice(-400));
    return out;
  }

  async screen() {
    // A screenshot works even when animated TV interfaces never become UIAutomator-idle.
    const png = await this.adb(['exec-out', 'screencap', '-p'], {binary: true});
    if (png.length < 24 || png.toString('hex', 0, 8) !== '89504e470d0a1a0a') throw Error('Could not capture the TV screen.');
    const width = png.readUInt32BE(16), height = png.readUInt32BE(20);
    this.size = {width, height};
    const focus = parseFocus(await this.shell(FOCUS).catch(() => ''));
    const shot = await compressScreenshot(png);
    return {width, height, focused: focus ? this.catalog.label(focus.pkg) + ' (' + focus.pkg + ')' : 'unknown app', image: shot.data.toString('base64'), mime: shot.mime};
  }

  async refreshApps(force = false) {
    if (!force && this.catalog.apps.length && Date.now() - this.catalog.loadedAt < 5 * 60 * 1000) return this.catalog.apps;
    const catalog = this.catalog;
    const out = await this.shell('cmd package query-activities --brief -a android.intent.action.MAIN -c android.intent.category.LEANBACK_LAUNCHER; cmd package query-activities --brief -a android.intent.action.MAIN -c android.intent.category.LAUNCHER');
    if (catalog !== this.catalog) return this.catalog.apps;
    const found = parseLaunchables(out);
    if (found.size) this.catalog.load(found);
    return this.catalog.apps;
  }
  async launch(appName) {
    await this.refreshApps();
    const app = this.catalog.resolve(appName);
    const out = await this.shell('am start -a android.intent.action.MAIN -c android.intent.category.LEANBACK_LAUNCHER -n ' + quote(app.component));
    if (/Error|Exception/i.test(out)) {
      const fallback = await this.shell('monkey -p ' + quote(app.pkg) + ' -c android.intent.category.LAUNCHER 1');
      if (/No activities found|Error/i.test(fallback)) throw Error('Could not start ' + app.label + '.');
    }
    return app;
  }
  async openUri(uri, pkg) {
    const out = await this.shell('am start -a android.intent.action.VIEW -d ' + quote(uri) + (pkg ? ' -p ' + quote(pkg) : ''));
    if (/Error:|unable to resolve|Exception/i.test(out)) throw Error(out.trim().split('\n').slice(-2).join(' ').slice(0, 300));
  }
  async waitForForeground(pkg, timeoutMs = 8000) {
    const until = Date.now() + timeoutMs;
    while (Date.now() < until) {
      const focus = parseFocus(await this.shell(FOCUS).catch(() => ''));
      if (focus?.pkg === pkg) return true;
      await pause(500);
    }
    return false;
  }
  async keys(codes, times = 1) {
    const list = [].concat(codes);
    await this.shell('input keyevent ' + Array.from({length: times}, () => list.join(' ')).join(' '));
  }
  async pasteText(text) {
    await this.shell('am start -n family.tv.companion/.ClipboardActivity --es text ' + quote(text));
    await pause(350);
    await this.keys(279);
  }
  async screenSize() {
    const dims = [...(await this.shell('wm size')).matchAll(/size:\s*(\d+)x(\d+)/g)].pop();
    if (dims) this.size = {width: Number(dims[1]), height: Number(dims[2])};
    return this.size;
  }
  async stremioSearch(title) {
    // Stremio 1.11.2 search links only pre-fill the title (upstream #2833), so use
    // the normal sidebar search, which was verified on a Mi Box.
    await this.openUri('stremio:///board', 'com.stremio.one');
    await pause(1000);
    const {width, height} = await this.screenSize();
    await this.shell('input tap ' + Math.round(width * 84 / 1920) + ' ' + Math.round(height * 266 / 1080));
    await pause(650);
    await this.keys(123); await this.keys(67, 200);
    await this.pasteText(title.trim());
    await this.keys(66);
  }

  // Exported only to holders of android.permission.DUMP, which adb shell has.
  async showAmbient() {
    const out = await this.shell('am start -n family.tv.companion/.AmbientActivity');
    if (/Error|Exception/i.test(out)) throw Error('Update DuoBuddy on the TV to use the screensaver.');
  }

  async volume() {return parseVolume(await this.shell(volumeCommand('--get')));}
  async setVolume(level) {
    // TVs differ in volume steps (15 on the Mi Box, 100 on some sets), so clamp to this one's range.
    const max = (await this.volume().catch(() => null))?.max || 15;
    await this.shell(volumeCommand('--set ' + Math.max(0, Math.min(max, Math.round(level)))));
    return this.volume();
  }

  // One round trip: foreground app, media sessions and volume.
  recentSnapshot(maxAgeMs = 10000, now = Date.now()) {
    const ageMs = this.snapshotCache ? now - this.snapshotCache.at : Infinity;
    return ageMs >= 0 && ageMs <= maxAgeMs ? {snapshot: this.snapshotCache.value, ageMs} : {snapshot: null, ageMs: null};
  }
  async snapshot() {
    const generation = this.deviceGeneration, capturedAt = Date.now();
    const out = await this.shell(FOCUS + '; echo @@; dumpsys media_session; echo @@; ' + volumeCommand('--get'), {timeout: 8000});
    const [focus = '', sessions = '', volume = ''] = out.split('@@\n');
    await this.refreshApps().catch(() => {});
    const fg = parseFocus(focus);
    const value = {
      foreground: fg ? {pkg: fg.pkg, label: this.catalog.label(fg.pkg)} : null,
      playing: parseSessions(sessions).map(s => ({...s, app: this.catalog.label(s.pkg)})),
      volume: parseVolume(volume)
    };
    if (generation === this.deviceGeneration && (!this.snapshotCache || capturedAt >= this.snapshotCache.at)) this.snapshotCache = {value, at: capturedAt};
    return value;
  }
}

export function describeSnapshot(s) {
  if (!s) return 'The TV did not answer; it may be asleep or offline.';
  const lines = [];
  lines.push('On screen: ' + (s.foreground ? s.foreground.label : 'unknown') + '.');
  lines.push(s.playing.length ? 'Media: ' + s.playing.map(p => `${p.app} ${p.state}${p.title ? ' — ' + p.title : ''}`).join('; ') + '.' : 'Media: nothing is playing.');
  if (s.volume) lines.push(`Volume: ${Math.round(s.volume.level / s.volume.max * 100)}% (${s.volume.level}/${s.volume.max}).`);
  return lines.join(' ');
}
