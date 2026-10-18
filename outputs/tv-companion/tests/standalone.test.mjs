import test from 'node:test';
import assert from 'node:assert/strict';
import {execFile} from 'node:child_process';
import {promisify} from 'node:util';
import {mkdtemp,rm,access} from 'node:fs/promises';
import {tmpdir} from 'node:os';
import {join} from 'node:path';
import {fileURLToPath} from 'node:url';
const exec=promisify(execFile);
test('native provider loop preserves signed Gemini calls and rejects unsafe selections',async t=>{
 const root=fileURLToPath(new URL('../../../',import.meta.url));
 const jdk=join(root,'work/build-tools/jdk-17.0.20.1+1/Contents/Home/bin');
 try{await access(join(jdk,'javac'));}catch{t.skip('Local TV JDK required');return;}
 await exec('python3',[fileURLToPath(new URL('../prepare-native.py',import.meta.url)),'--tests']);
 const dir=await mkdtemp(join(tmpdir(),'duobuddy-native-'));t.after(()=>rm(dir,{recursive:true,force:true}));
 const json=join(root,'work/build-tools/native/json-20240303.jar');
 const source=fileURLToPath(new URL('../android/src/family/tv/companion/',import.meta.url));
 await exec(join(jdk,'javac'),['-cp',json,'-d',dir,join(source,'AiProtocol.java'),join(source,'TvJson.java'),fileURLToPath(new URL('AiProtocolTest.java',import.meta.url))]);
 const {stdout}=await exec(join(jdk,'java'),['-cp',dir+':'+json,'family.tv.companion.AiProtocolTest']);assert.match(stdout,/regression checks passed/);
});
