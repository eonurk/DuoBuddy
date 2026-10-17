import {randomUUID} from 'node:crypto';
import {DEFAULT_MODEL} from './model-config.mjs';

export const PROVIDERS = Object.freeze({
  openai: {label: 'OpenAI', defaultModel: DEFAULT_MODEL, models: [{id: DEFAULT_MODEL, label: 'GPT-6 Luna · economical'}], webSearch: true},
  gemini: {label: 'Google Gemini', defaultModel: 'gemini-3.1-flash-lite', models: [{id: 'gemini-3.1-flash-lite', label: 'Gemini 3.1 Flash-Lite · economical'}], webSearch: false}
});
export function providerInfo(provider) {
  if (!Object.hasOwn(PROVIDERS, provider)) throw Error('Choose OpenAI or Google Gemini.');
  return PROVIDERS[provider];
}
export function credentialState(saved = {}) {
  const provider = Object.hasOwn(PROVIDERS, saved.provider) ? saved.provider : 'openai';
  const accounts = {};
  for (const id of Object.keys(PROVIDERS)) if (Object.hasOwn(saved.accounts || {}, id)) accounts[id] = {...saved.accounts[id]};
  if (saved.apiKey) accounts[provider] = {apiKey: saved.apiKey, model: saved.model || PROVIDERS[provider].defaultModel};
  return {provider, apiKey: accounts[provider]?.apiKey || '', model: accounts[provider]?.model || PROVIDERS[provider].defaultModel, accounts};
}
export function catalog(provider, model, accounts) {
  return {provider, model, providers: Object.entries(PROVIDERS).map(([id, info]) => ({id, ...info, savedKey: !!accounts[id]?.apiKey, savedModel: accounts[id]?.model || info.defaultModel}))};
}
export async function validateCredentials(provider, model, key, fetcher = fetch) {
  const info = providerInfo(provider);
  if (!/^[A-Za-z0-9._-]{1,100}$/.test(model)) throw Error('Invalid model name.');
  if (!(provider === 'openai' ? /^sk-[A-Za-z0-9_-]{15,}$/ : /^[A-Za-z0-9_-]{20,200}$/).test(key)) throw Error('Enter a valid ' + info.label + ' API key.');
  const url = provider === 'openai' ? 'https://api.openai.com/v1/models/' + encodeURIComponent(model) : 'https://generativelanguage.googleapis.com/v1beta/models/' + encodeURIComponent(model);
  const headers = provider === 'openai' ? {Authorization: 'Bearer ' + key} : {'x-goog-api-key': key};
  const response = await fetcher(url, {headers, signal: AbortSignal.timeout(15000)});
  const data = await response.json();
  if (!response.ok) throw Error(data.error?.message || info.label + ' key or model could not be validated.');
  if (provider === 'gemini' && !data.supportedGenerationMethods?.includes('generateContent')) throw Error('Choose a Gemini model that supports text generation.');
}

// Preserve Gemini's complete assistant message, including tool-call thought signatures.
function geminiMessages(input) {
  if (typeof input === 'string') return [{role: 'user', content: input}];
  const messages = [];
  for (const item of input || []) {
    if (item.type === 'provider_message' && item.provider === 'gemini') {messages.push(item.message); continue;}
    if (item.providerProjection) continue;
    if (item.type === 'function_call_output') {messages.push({role: 'tool', tool_call_id: item.call_id, content: item.output}); continue;}
    if (!item.role) continue;
    const content = typeof item.content === 'string' ? item.content : (item.content || []).map(part => {
      if (part.type === 'input_image') return {type: 'image_url', image_url: {url: part.image_url}};
      return {type: 'text', text: part.text || ''};
    });
    messages.push({role: item.role, content});
  }
  return messages;
}
export function providerRequest(provider, key, body) {
  providerInfo(provider);
  const headers = {Authorization: 'Bearer ' + key, 'Content-Type': 'application/json'};
  if (provider === 'openai') return {url: 'https://api.openai.com/v1/responses', headers, body};
  const request = {model: body.model, messages: [{role: 'system', content: body.instructions || ''}, ...geminiMessages(body.input)], max_tokens: body.max_output_tokens || 2000};
  if (body.model === 'gemini-3.1-flash-lite') request.reasoning_effort = 'minimal';
  const tools = (body.tools || []).filter(tool => tool.type === 'function').map(({name, description, parameters}) => ({type: 'function', function: {name, description, parameters}}));
  if (tools.length) {request.tools = tools; request.tool_choice = 'auto';}
  if (body.text?.format?.type === 'json_schema') {
    const {type, ...json_schema} = body.text.format;
    request.response_format = {type, json_schema};
  }
  return {url: 'https://generativelanguage.googleapis.com/v1beta/openai/chat/completions', headers, body: request};
}
export function providerResponse(provider, data) {
  if (provider === 'openai') return data;
  const choice = data.choices?.[0], message = choice?.message;
  if (!message || ['content_filter', 'SAFETY'].includes(choice.finish_reason) || message.refusal) throw Error('Gemini could not answer this request.');
  const output = [{type: 'provider_message', provider: 'gemini', message}];
  if (message.content) output.push({type: 'message', providerProjection: true, content: [{type: 'output_text', text: message.content}]});
  for (const call of message.tool_calls || []) {
    call.id ||= randomUUID();
    output.push({type: 'function_call', providerProjection: true, call_id: call.id, name: call.function.name, arguments: call.function.arguments});
  }
  return {status: choice.finish_reason === 'length' ? 'incomplete' : 'completed', output, usage: {input_tokens: data.usage?.prompt_tokens || 0, output_tokens: data.usage?.completion_tokens || 0, input_tokens_details: {cached_tokens: data.usage?.prompt_tokens_details?.cached_tokens || 0}}};
}
