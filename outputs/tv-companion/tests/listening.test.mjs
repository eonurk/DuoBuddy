import test from 'node:test';
import assert from 'node:assert/strict';
import {mkdtemp, rm, access} from 'node:fs/promises';
import {tmpdir} from 'node:os';
import {join} from 'node:path';
import {fileURLToPath} from 'node:url';
import {execFile} from 'node:child_process';
import {promisify} from 'node:util';
const exec=promisify(execFile);

test('TV listening tolerates duplicate triggers and early endpoints, with bounded retries',async t=>{
  const jdk=new URL('../../../work/build-tools/jdk-17.0.20.1+1/Contents/Home/bin/',import.meta.url);
  const javac=fileURLToPath(new URL('javac',jdk));
  try{await access(javac);}catch{t.skip('Local TV build JDK is not installed');return;}
  const dir=await mkdtemp(join(tmpdir(),'duobook-listening-'));
  t.after(()=>rm(dir,{recursive:true,force:true}));
  await exec(javac,['-d',dir,fileURLToPath(new URL('../android/src/family/tv/companion/ListeningSession.java',import.meta.url)),fileURLToPath(new URL('./ListeningSessionTest.java',import.meta.url))]);
  const {stdout}=await exec(fileURLToPath(new URL('java',jdk)),['-cp',dir,'family.tv.companion.ListeningSessionTest']);
  assert.match(stdout,/regression checks passed/);
});
