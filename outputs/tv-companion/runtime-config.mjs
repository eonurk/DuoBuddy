import {join, resolve} from 'node:path';
import {homedir} from 'node:os';

// Distribution builds use a fresh, per-user home outside the read-only app bundle.
// The source checkout keeps its existing settings and Piper configuration.
export function runtimeConfig(root, env = process.env) {
  const distributed = env.TV_DISTRIBUTION === '1';
  const base = resolve(root, '../..');
  return {
    distributed,
    runtime: env.TV_RUNTIME_DIR || (distributed ? join(homedir(), 'Library/Application Support/DuoBuddy') : join(base, 'work/tv-runtime')),
    adb: env.TV_ADB || join(base, 'work/android-tools/platform-tools/adb'),
    defaultDevice: distributed ? '' : '192.168.178.87:5555',
    piperEnabled: env.TV_VOICE_ENGINE ? env.TV_VOICE_ENGINE === 'piper' : !distributed
  };
}
