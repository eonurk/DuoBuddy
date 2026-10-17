import test from 'node:test';
import assert from 'node:assert/strict';
import {mkdtemp, rm, access, readFile} from 'node:fs/promises';
import {createHash} from 'node:crypto';
import {tmpdir} from 'node:os';
import {join} from 'node:path';
import {fileURLToPath} from 'node:url';
import {execFile} from 'node:child_process';
import {promisify} from 'node:util';
import {usesEmbeddedVoice,tvVoiceName} from '../server.mjs';
const exec=promisify(execFile);

test('only TV builds containing Piper receive text instead of remote audio',()=>{
  for(const value of [undefined,null,0,6,'6',NaN,'bad',7.5])assert.equal(usesEmbeddedVoice(value),false,String(value));
  for(const value of [7,'7',8])assert.equal(usesEmbeddedVoice(value),true);
  assert.equal(tvVoiceName('en_US-lessac-medium'),'Lessac Medium');
  assert.equal(tvVoiceName('../../escape'),'Amy Low');
  assert.equal(tvVoiceName('__proto__'),'Amy Low');
});

test('voice downloads reject corruption, incomplete bodies and cancellation without replacing a working voice',async t=>{
  const jdk=new URL('../../../work/build-tools/jdk-17.0.20.1+1/Contents/Home/bin/',import.meta.url);
  try{await access(new URL('javac',jdk));}catch{t.skip('Local TV build JDK is not installed');return;}
  const dir=await mkdtemp(join(tmpdir(),'buddy-voice-download-'));t.after(()=>rm(dir,{recursive:true,force:true}));
  await exec(fileURLToPath(new URL('javac',jdk)),['-d',dir,fileURLToPath(new URL('../android/src/family/tv/companion/VoiceFile.java',import.meta.url)),fileURLToPath(new URL('VoiceFileTest.java',import.meta.url))]);
  const {stdout}=await exec(fileURLToPath(new URL('java',jdk)),['-cp',dir,'family.tv.companion.VoiceFileTest',dir]);
  assert.match(stdout,/regression checks passed/);
});

test('optional voices use pinned HTTPS models, matching sample rates and verified bundled metadata',async()=>{
  const base=new URL('../android/assets/voices/',import.meta.url);
  const catalog=JSON.parse(await readFile(new URL('catalog.json',base)));
  assert.deepEqual(catalog.map(v=>v.id),['en_US-lessac-medium','en_US-amy-medium','en_US-lessac-high']);
  for(const voice of catalog){
    assert.match(voice.sha256,/^[a-f0-9]{64}$/);assert.match(voice.revision,/^[a-f0-9]{40}$/);
    const url=new URL(voice.url);assert.equal(url.protocol,'https:');assert.equal(url.hostname,'huggingface.co');assert.ok(url.pathname.includes('/resolve/'+voice.revision+'/'));
    for(const [name,sha] of Object.entries(voice.metadata))assert.equal(createHash('sha256').update(await readFile(new URL(voice.id+'/'+name,base))).digest('hex'),sha);
    const config=JSON.parse(await readFile(new URL(voice.id+'/'+voice.id+'.onnx.json',base)));assert.equal(config.audio.sample_rate,voice.sampleRate);
  }
});

test('stopped and superseded native speech cannot play or reopen the microphone',async t=>{
  const jdk=new URL('../../../work/build-tools/jdk-17.0.20.1+1/Contents/Home/bin/',import.meta.url);
  try{await access(new URL('javac',jdk));}catch{t.skip('Local TV build JDK is not installed');return;}
  const dir=await mkdtemp(join(tmpdir(),'buddy-speech-'));
  t.after(()=>rm(dir,{recursive:true,force:true}));
  const kotlin=fileURLToPath(new URL('../../../work/piper-runtime/kotlin-stdlib-2.0.21.jar',import.meta.url));
  try{await access(kotlin);}catch{t.skip('Build the TV app once to prepare its Kotlin compile dependency');return;}
  await exec(fileURLToPath(new URL('javac',jdk)),['-cp',kotlin,'-d',dir,fileURLToPath(new URL('../android/src/family/tv/companion/PiperCallback.java',import.meta.url)),fileURLToPath(new URL('../android/src/family/tv/companion/SpeechTurn.java',import.meta.url)),fileURLToPath(new URL('SpeechTurnTest.java',import.meta.url))]);
  const {stdout}=await exec(fileURLToPath(new URL('java',jdk)),['-cp',dir+':'+kotlin,'family.tv.companion.SpeechTurnTest']);
  assert.match(stdout,/regression checks passed/);
});
