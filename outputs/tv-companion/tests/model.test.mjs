import test from 'node:test';
import assert from 'node:assert/strict';
import {DEFAULT_MODEL,responseRequest} from '../model-config.mjs';
test('the requested GPT-6 Luna model is the default for vision and tool calls',()=>{
  assert.equal(DEFAULT_MODEL,'gpt-6-luna');
  const input=[{role:'user',content:[{type:'input_image',image_url:'data:image/png;base64,example'}]}];
  const tools=[{type:'function',name:'tv_action'}];
  const request=responseRequest({input,tools,instructions:'TV companion'});
  assert.equal(request.model,'gpt-6-luna');assert.deepEqual(request.reasoning,{effort:'none'});
  assert.deepEqual(request.include,['reasoning.encrypted_content']);assert.equal(request.input,input);assert.equal(request.tools,tools);
  assert.equal(request.store,true);assert.equal(request.parallel_tool_calls,false);
  assert.ok(!('temperature' in request));
});
test('an explicit alternative model receives no GPT-6-only options',()=>{
  const request=responseRequest({model:'explicit-user-model',input:[],tools:[],instructions:''});
  assert.equal(request.model,'explicit-user-model');assert.ok(!('reasoning' in request));assert.ok(!('include' in request));
});
test('every request shares one prompt cache key',()=>{
  assert.equal(responseRequest({input:[],tools:[],instructions:''}).prompt_cache_key,'duobook-buddy');
});
test('only actions that need no read-back finish without a second call',async()=>{
  const {isOneShot}=await import('../tools.mjs');
  assert.ok(isOneShot('media',{command:'pause'}));assert.ok(isOneShot('volume',{action:'up',amount:null}));
  assert.ok(isOneShot('remote',{key:'sleep',times:null}));assert.ok(!isOneShot('remote',{key:'down',times:3}));
  assert.ok(isOneShot('timer',{action:'set',seconds:60,label:null,id:null}));assert.ok(!isOneShot('timer',{action:'list',seconds:null,label:null,id:null}));
  assert.ok(!isOneShot('memory',{action:'list',note:null,scope:null}));assert.ok(!isOneShot('search_app',{app:'spotify',query:'x'}));
  assert.ok(!isOneShot('read_screen',{}));assert.ok(!isOneShot('tv_status',{}));
});
