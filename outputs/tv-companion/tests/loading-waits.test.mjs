import test from 'node:test';
import assert from 'node:assert/strict';
import {createToolRunner, createTurnToolRunner, TOOLS} from '../tools.mjs';

test('legacy five-second loading waits return within two seconds', async () => {
  const run = createToolRunner({tv: {size: {width: 1920, height: 1080}}});
  const started = performance.now();
  const result = await run('wait', {seconds: 5});
  assert.ok(performance.now() - started < 2000, 'A five-second request must be shortened');
  assert.match(result.message, /Waited 1s/);
});

test('observing a result cannot re-enable repeated waits; a new action can', async () => {
  const calls = [];
  const turn = createTurnToolRunner(async (name, args) => {
    calls.push(name);
    if (args.fail) throw Error('Action failed');
    return {message: 'ok'};
  });
  const hasWait = () => turn.tools(TOOLS).some(tool => tool.name === 'wait');
  assert.equal(hasWait(), true);
  await turn.run('wait', {seconds: 5});
  assert.equal(hasWait(), false);
  await turn.run('read_screen', {});
  await turn.run('tv_status', {});
  await assert.rejects(turn.run('wait', {seconds: 5}), /Already waited/);
  await assert.rejects(turn.run('remote', {fail: true}), /Action failed/);
  assert.equal(hasWait(), false);
  await turn.run('remote', {key: 'select'});
  assert.equal(hasWait(), true);
  await turn.run('wait', {seconds: 1});
  assert.equal(hasWait(), false);
  assert.equal(calls.filter(name => name === 'wait').length, 2);
  assert.equal(createTurnToolRunner(async () => ({})).tools(TOOLS), TOOLS, 'A new turn has its own wait budget');
});
