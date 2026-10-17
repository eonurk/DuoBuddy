import {execFile} from 'node:child_process';
import {promisify} from 'node:util';
import {mkdtemp, readFile, rm} from 'node:fs/promises';
import {tmpdir} from 'node:os';
import {join} from 'node:path';

const exec = promisify(execFile);
const PREFERRED = ['Samantha', 'Ava', 'Allison', 'Karen', 'Daniel'];
let chosen;

// The Mac's built-in voice, used whenever the Piper machine cannot be reached, so
// Buddy is never mute. Prefers a pleasant English voice and falls back to the system default.
async function pickVoice() {
  if (chosen !== undefined) return chosen;
  try {
    const {stdout} = await exec('/usr/bin/say', ['-v', '?'], {timeout: 5000});
    const installed = new Set(stdout.split('\n').map(l => l.split(/\s{2,}/)[0].trim()));
    chosen = PREFERRED.find(v => installed.has(v)) || null;
  } catch {chosen = null;}
  return chosen;
}

export function wavDuration(wav) {
  if (wav.length < 44 || wav.toString('ascii', 0, 4) !== 'RIFF' || wav.toString('ascii', 8, 12) !== 'WAVE') return 0;
  let byteRate = 0, offset = 12;
  while (offset + 8 <= wav.length) {
    const id = wav.toString('ascii', offset, offset + 4), size = wav.readUInt32LE(offset + 4);
    if (id === 'fmt ') byteRate = wav.readUInt32LE(offset + 16);
    if (id === 'data') return byteRate ? Math.round(Math.min(size, wav.length - offset - 8) / byteRate * 1000) / 1000 : 0;
    offset += 8 + size + (size % 2);
  }
  return 0;
}

export async function macSpeak(text) {
  if (typeof text !== 'string' || !text.trim() || text.length > 4000) throw Error('Speech text must contain 1–4000 characters.');
  const dir = await mkdtemp(join(tmpdir(), 'buddy-say-'));
  try {
    const file = join(dir, 'speech.wav'), voice = await pickVoice();
    const started = Date.now();
    // "--" keeps text that begins with a dash from being read as an option.
    await exec('/usr/bin/say', ['-o', file, '--data-format=LEI16@22050', ...(voice ? ['-v', voice] : []), '--', text], {timeout: 30000});
    const audio = await readFile(file);
    if (audio.toString('ascii', 0, 4) !== 'RIFF' || audio.toString('ascii', 8, 12) !== 'WAVE') throw Error('The Mac voice did not produce a WAV file.');
    return {audio, voice: voice ? 'macOS ' + voice : 'macOS voice', synthesisMs: Date.now() - started, duration: wavDuration(audio)};
  } finally {await rm(dir, {recursive: true, force: true}).catch(() => {});}
}
