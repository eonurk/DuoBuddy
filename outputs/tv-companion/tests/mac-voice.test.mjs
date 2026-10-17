import test from 'node:test';
import assert from 'node:assert/strict';
import {macSpeak, wavDuration} from '../mac-voice.mjs';

const onMac = process.platform === 'darwin';

test('the Mac fallback voice produces a playable WAV, even for text that starts with a dash', {skip: !onMac}, async () => {
  const speech = await macSpeak('-Hello there. This is Buddy speaking with the Mac voice.');
  assert.equal(speech.audio.toString('ascii', 0, 4), 'RIFF'); assert.equal(speech.audio.toString('ascii', 8, 12), 'WAVE');
  assert.ok(speech.duration > 1 && speech.duration < 10, 'duration ' + speech.duration);
  assert.match(speech.voice, /^macOS/);
});
test('the fallback voice rejects empty and oversized text', async () => {
  await assert.rejects(macSpeak('   ')); await assert.rejects(macSpeak('x'.repeat(4001)));
});
test('WAV duration is read from the header, skipping extra chunks', () => {
  const data = Buffer.alloc(44100 * 2); // one second of 22.05 kHz... here 44.1 kHz mono 16-bit
  const header = Buffer.alloc(44); header.write('RIFF', 0); header.write('WAVE', 8); header.write('fmt ', 12); header.writeUInt32LE(16, 16); header.writeUInt32LE(44100 * 2, 28); header.write('data', 36); header.writeUInt32LE(data.length, 40);
  assert.equal(wavDuration(Buffer.concat([header, data])), 1);
  assert.equal(wavDuration(Buffer.from('not a wav')), 0);
});
