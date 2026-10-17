import test from 'node:test';
import assert from 'node:assert/strict';
import {responseRequest} from '../model-config.mjs';
import {credentialState, catalog, validateCredentials, providerRequest, providerResponse} from '../providers.mjs';

test('legacy credentials retain OpenAI and provider catalogs never contain saved keys', () => {
  const saved = credentialState({apiKey: 'private-example', model: 'gpt-6-luna'});
  assert.equal(saved.provider, 'openai'); assert.equal(saved.accounts.openai.apiKey, 'private-example');
  const list = catalog(saved.provider, saved.model, saved.accounts);
  assert.equal(list.providers[0].savedKey, true);
  assert.equal(JSON.stringify(list).includes('private-example'), false);
  assert.throws(() => providerRequest('__proto__', '', {}), /Choose/);
});

test('OpenAI requests retain the existing Responses configuration', () => {
  const body = responseRequest({instructions: 'TV', input: [], tools: []});
  const request = providerRequest('openai', 'key', body);
  assert.equal(request.url, 'https://api.openai.com/v1/responses');
  assert.equal(request.body, body); assert.equal(request.headers.Authorization, 'Bearer key');
});

test('Gemini tool results preserve complete signed assistant calls and image inputs', () => {
  const signature = {google: {thought_signature: 'signed-thought'}};
  const message = {role: 'assistant', content: null, tool_calls: [{id: 'call-1', type: 'function', function: {name: 'read_screen', arguments: '{}'}, extra_content: signature}]};
  const reply = providerResponse('gemini', {choices: [{message, finish_reason: 'tool_calls'}], usage: {prompt_tokens: 12, completion_tokens: 4}});
  const body = responseRequest({model: 'gemini-3.1-flash-lite', instructions: 'TV', input: [
    {role: 'user', content: 'Look at the TV'}, ...reply.output,
    {type: 'function_call_output', call_id: 'call-1', output: '{"message":"screen"}'},
    {role: 'user', content: [{type: 'input_image', image_url: 'data:image/jpeg;base64,screen'}]}
  ], tools: [{type: 'function', name: 'read_screen', description: 'Read TV', parameters: {type: 'object', properties: {}}}, {type: 'web_search'}]});
  const request = providerRequest('gemini', 'google-key', body);
  assert.equal(request.body.reasoning_effort, 'minimal');
  assert.deepEqual(request.body.messages[2], message);
  assert.equal(request.body.messages.filter(m => m.role === 'assistant').length, 1);
  assert.equal(request.body.messages[3].tool_call_id, 'call-1');
  assert.equal(request.body.messages[4].content[0].image_url.url, 'data:image/jpeg;base64,screen');
  assert.equal(request.body.tools.length, 1); assert.equal(request.body.tools[0].function.name, 'read_screen');
  assert.equal(reply.usage.input_tokens, 12);
  assert.equal(providerResponse('gemini', {choices: [{message: {content: 'Hello'}, finish_reason: 'length'}]}).status, 'incomplete');
  assert.throws(() => providerResponse('gemini', {choices: []}), /could not answer/);
});

test('background JSON output is mapped for Gemini without OpenAI-specific options', () => {
  const request = providerRequest('gemini', 'key', {model: 'gemini-3.1-flash-lite', instructions: 'JSON', input: 'question', max_output_tokens: 400, text: {format: {type: 'json_schema', name: 'result', strict: true, schema: {type: 'object'}}}});
  assert.equal(request.body.response_format.json_schema.name, 'result');
  assert.equal(request.body.messages[1].content, 'question');
  assert.equal(request.body.max_tokens, 400); assert.equal('store' in request.body, false);
});

test('provider keys are validated only against their own fixed HTTPS hosts', async () => {
  const calls = [];
  const fetcher = async (url, options) => {calls.push({url, options}); return {ok: true, json: async () => ({supportedGenerationMethods: ['generateContent']})};};
  await validateCredentials('openai', 'gpt-6-luna', 'sk-' + 'a'.repeat(30), fetcher);
  await validateCredentials('gemini', 'gemini-3.1-flash-lite', 'AIza' + 'b'.repeat(30), fetcher);
  assert.equal(new URL(calls[0].url).hostname, 'api.openai.com');
  assert.equal(new URL(calls[1].url).hostname, 'generativelanguage.googleapis.com');
  assert.ok(calls[1].options.headers['x-goog-api-key']); assert.equal(new URL(calls[1].url).search, '');
  await assert.rejects(validateCredentials('openai', '../escape', 'sk-' + 'a'.repeat(30), fetcher), /model name/);
  await assert.rejects(validateCredentials('gemini', 'gemini-3.1-flash-lite', 'AIza' + 'b'.repeat(30), async () => ({ok: false, json: async () => ({error: {message: 'Invalid key'}})})), /Invalid key/);
});
