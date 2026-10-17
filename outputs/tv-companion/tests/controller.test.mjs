import test from 'node:test';
import assert from 'node:assert/strict';
import {validateCall, legacyToCall, TOOLS} from '../tools.mjs';
import {quote, KEYS} from '../tv.mjs';
import {searchTarget, validateUrl} from '../apps.mjs';

test('shell data remains a single quoted argument', () => {
  assert.equal(quote("a'b;$(id)"), "'a'\\''b;$(id)'");
});
test('a search phrase cannot escape its deep-link query', () => {
  const title = "Paddington & $(touch /tmp/unwanted); O'Brien";
  const target = searchTarget({key: 'youtube', pkg: 'com.google.android.youtube.tv'}, title);
  assert.equal(new URL(target.uri).searchParams.get('search_query'), title);
  assert.ok(!target.uri.includes(' '));
  assert.throws(() => searchTarget({key: 'youtube'}, ''));
  assert.throws(() => searchTarget({key: 'youtube'}, 'x'.repeat(201)));
  assert.equal(searchTarget({key: 'vlc'}, 'anything'), null);
  assert.deepEqual(searchTarget({key: 'stremio'}, 'Paddington'), {stremio: true});
});
test('arbitrary shell, package and scheme injection is rejected', () => {
  for (const [name, args] of [['shell', {value: 'reboot'}], ['remote', {key: 'HOME;reboot', times: 1}], ['remote', {key: 'volume_up', times: 1}], ['media', {command: 'reboot'}], ['volume', {action: 'set', amount: 101}], ['open_url', {url: 'file:///etc/passwd'}], ['open_url', {url: 'https://a.example/ b'}], ['open_url', {url: 'intent://x#Intent;end'}]]) {
    assert.throws(() => validateCall(name, args), undefined, name);
  }
  assert.throws(() => validateUrl('http://insecure.example'));
  assert.equal(validateUrl('https://www.youtube.com/watch?v=abc'), 'https://www.youtube.com/watch?v=abc');
});
test('screen taps must fall inside the captured screen', () => {
  const size = {width: 1920, height: 1080};
  for (const a of [{x: -1, y: 20}, {x: 20, y: Infinity}, {x: 10.5, y: 30}, {x: 1920, y: 20}, {x: 20, y: 1080}]) assert.throws(() => validateCall('tap', a, {size}));
  assert.doesNotThrow(() => validateCall('tap', {x: 1830, y: 1040}, {size}));
});
test('text input rejects control characters and excessive length', () => {
  assert.throws(() => validateCall('type_text', {text: 'hello\nreboot', submit: false}));
  assert.throws(() => validateCall('type_text', {text: 'a'.repeat(201), submit: false}));
  assert.doesNotThrow(() => validateCall('type_text', {text: "O'Brien", submit: true}));
});
test('remote keys repeat within limits and include sleep and paging', () => {
  assert.equal(KEYS.select, 23); assert.equal(KEYS.back, 4); assert.equal(KEYS.sleep, 223);
  assert.doesNotThrow(() => validateCall('remote', {key: 'down', times: 25}));
  assert.throws(() => validateCall('remote', {key: 'down', times: 26}));
  assert.throws(() => validateCall('remote', {key: 'down', times: 0}));
});
test('volume, timer and memory arguments are validated', () => {
  assert.doesNotThrow(() => validateCall('volume', {action: 'set', amount: 40}));
  assert.throws(() => validateCall('volume', {action: 'set', amount: null}));
  assert.doesNotThrow(() => validateCall('volume', {action: 'up', amount: null}));
  assert.throws(() => validateCall('timer', {action: 'set', seconds: 90000, label: null, id: null}));
  assert.throws(() => validateCall('memory', {action: 'remember', note: ''}));
  assert.doesNotThrow(() => validateCall('memory', {action: 'list', note: null}));
});
test('legacy action shapes from older builds still map to tools', () => {
  assert.deepEqual(legacyToCall({action: 'open_app', value: 'stremio'}), {name: 'open_app', args: {app: 'stremio'}});
  assert.deepEqual(legacyToCall({action: 'key', value: 'home'}), {name: 'remote', args: {key: 'home', times: 1}});
  assert.equal(legacyToCall({action: 'search_stremio', value: 'Paddington'}).args.app, 'stremio');
  assert.throws(() => legacyToCall({action: 'shell', value: 'reboot'}));
});
test('every strict tool schema lists all of its properties as required', () => {
  for (const t of TOOLS) {
    assert.equal(t.strict, true);
    assert.deepEqual([...t.parameters.required].sort(), Object.keys(t.parameters.properties).sort(), t.name);
    assert.equal(t.parameters.additionalProperties, false);
  }
});
