import test from 'node:test';
import assert from 'node:assert/strict';
import http from 'node:http';
import {PiperClient} from '../piper-client.mjs';
import {DEFAULT_VOICE,VOICES,validateVoice} from '../voice-config.mjs';

test('voice choices reject arbitrary model paths and unknown voices',()=>{
  assert.equal(DEFAULT_VOICE,'en_US-hfc_female-medium');
  for(const voice of VOICES)assert.equal(validateVoice(voice.id),voice.id);
  for(const voice of ['../../other.onnx','unknown','',null,{}])assert.throws(()=>validateVoice(voice));
});

test('a selected voice is forwarded to the worker and verified in its WAV response',async()=>{
  const requests=[];
  const server=http.createServer(async(req,res)=>{
    if(req.url==='/health'){res.setHeader('Content-Type','application/json');res.end(JSON.stringify({service:'duobook-piper-v1',ready:true,voiceSelection:true}));return;}
    let input='';for await(const chunk of req)input+=chunk;
    requests.push(JSON.parse(input));
    res.writeHead(200,{'Content-Type':'audio/wav','X-Piper-Voice':requests.at(-1).voice,'X-Synthesis-Ms':'123','X-Audio-Duration':'1.2'});
    res.end(Buffer.from('RIFF0000WAVE'));
  });
  await new Promise(resolve=>server.listen(0,'127.0.0.1',resolve));
  try{
    const client=new PiperClient({port:server.address().port});
    const result=await client.synthesize('Hello Buddy',{voice:'en_US-lessac-high'});
    assert.deepEqual(requests,[{text:'Hello Buddy',voice:'en_US-lessac-high'}]);
    assert.equal(result.voice,'en_US-lessac-high');assert.equal(result.duration,1.2);
    await assert.rejects(client.synthesize('Hello',{voice:'../../escape'}));
    assert.equal(requests.length,1);
  }finally{await new Promise(resolve=>server.close(resolve));}
});
