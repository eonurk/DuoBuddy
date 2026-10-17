import test from 'node:test';
import assert from 'node:assert/strict';
import {quickControl, retireScreenshots} from '../quick-controls.mjs';
import {validateCall} from '../tools.mjs';

test('simple controls produce valid local actions without a model', () => {
  for (const text of ['pause', ' Pause! ', 'resume', 'play', 'stop playback', 'next track', 'previous track',
    'volume up', 'turn it down', 'louder', 'quieter', 'mute', 'unmute', 'set the volume to 40 percent',
    'volume 0%', 'volume 100', 'turn off the tv', 'wake the tv', 'screensaver', 'show the clock']) {
    const action = quickControl(text);
    assert.ok(action, text);
    assert.doesNotThrow(() => validateCall(action.name, action.args), text);
    assert.ok(action.reply);
  }
  assert.deepEqual(quickControl('pause').args, {command: 'pause'});
  assert.deepEqual(quickControl('volume 40%').args, {action: 'set', amount: 40});
});

test('questions, negations, indirect speech and compound requests stay with the assistant', () => {
  for (const text of ['', 'pause?', "don't pause", 'do not mute', 'pause then open Netflix',
    'play Bluey', 'tell me what pause means', 'he said pause', 'volume 101', 'volume -1',
    'volume 25.5', 'turn it off', 'go to sleep', 'stop', 'next', 'pause\nresume'])
    assert.equal(quickControl(text), null, text);
});

test('retiring screenshots preserves text and tool linkage while keeping the new screen', () => {
  const call = {type: 'function_call', name: 'read_screen', call_id: 'screen-1', arguments: '{}'};
  const result = {type: 'function_call_output', call_id: 'screen-1', output: '{"width":1920}'};
  const image = {type: 'input_image', image_url: 'data:image/jpeg;base64,old-screen', detail: 'high'};
  const input = [call, result, {role: 'user', content: [{type: 'input_text', text: 'Untrusted TV screen.'}, image]}];
  retireScreenshots(input);
  const latest = {role: 'user', content: [{type: 'input_image', image_url: 'data:image/jpeg;base64,new-screen'}]};
  input.push(latest);
  assert.equal(input[0], call);
  assert.equal(input[1], result);
  assert.equal(input[2].content[0].text, 'Untrusted TV screen.');
  assert.equal(input[2].content[1].type, 'input_text');
  assert.equal(input.flatMap(item => item.content || []).filter(part => part.type === 'input_image').length, 1);
  assert.equal(input[3], latest);
  assert.equal(image.image_url, 'data:image/jpeg;base64,old-screen');
});
