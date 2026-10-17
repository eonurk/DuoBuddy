import test from 'node:test';
import assert from 'node:assert/strict';
import {EventEmitter} from 'node:events';
import {setTimeout as delay} from 'node:timers/promises';
import {StateDelivery} from '../state-delivery.mjs';

function fixture(options = {}) {
  const state = {session: 'first-start', revision: 1, reply: ''};
  const sent = [];
  const delivery = new StateDelivery({read: () => ({...state}), send: (res, status, value) => sent.push({res, status, value}), ...options});
  return {state, sent, delivery};
}

test('a held state request receives a new reply immediately and unregisters itself', () => {
  const {state, sent, delivery} = fixture();
  const res = new EventEmitter();
  delivery.wait(res, 1, state.session);
  assert.equal(sent.length, 0);
  state.revision++; state.reply = 'Ready.';
  delivery.publish();
  assert.equal(sent[0].value.reply, 'Ready.');
  assert.equal(sent[0].value.revision, 2);
  assert.equal(delivery.clients.size, 0);
  assert.equal(res.listenerCount('close'), 0);
  delivery.publish(); assert.equal(sent.length, 1);
});

test('reconnecting after missed updates or a controller restart does not wait', () => {
  const {state, sent, delivery} = fixture();
  delivery.wait(new EventEmitter(), 0, state.session);
  delivery.wait(new EventEmitter(), 1, 'previous-start');
  assert.equal(sent.length, 2); assert.equal(delivery.clients.size, 0);
});

test('disconnected requests are removed and heartbeat timeouts release idle connections', async () => {
  const {state, sent, delivery} = fixture({timeoutMs: 25});
  const gone = new EventEmitter();
  delivery.wait(gone, 1, state.session); gone.destroyed = true; gone.emit('close');
  assert.equal(delivery.clients.size, 0);
  const live = new EventEmitter(); delivery.wait(live, 1, state.session);
  await delay(50);
  assert.equal(sent.length, 1); assert.equal(sent[0].res, live);
  assert.equal(delivery.clients.size, 0);
});

test('connection limits trigger retry rather than an immediate-success reconnect loop', () => {
  const {state, sent, delivery} = fixture({maxClients: 1});
  const live = new EventEmitter(); delivery.wait(live, 1, state.session);
  delivery.wait(new EventEmitter(), 1, state.session);
  assert.equal(sent[0].status, 503);
  live.emit('close'); assert.equal(delivery.clients.size, 0);
});
