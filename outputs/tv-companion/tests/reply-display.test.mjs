import test from 'node:test';
import assert from 'node:assert/strict';
import {mkdtemp, rm, access} from 'node:fs/promises';
import {tmpdir} from 'node:os';
import {join} from 'node:path';
import {fileURLToPath} from 'node:url';
import {execFile} from 'node:child_process';
import {promisify} from 'node:util';
const exec=promisify(execFile);

test('TV replies expire without restoring stale conversations',async t=>{
  const jdk=new URL('../../../work/build-tools/jdk-17.0.20.1+1/Contents/Home/bin/',import.meta.url);
  const javac=fileURLToPath(new URL('javac',jdk));
  try{await access(javac);}catch{t.skip('Local TV build JDK is not installed');return;}
  const dir=await mkdtemp(join(tmpdir(),'duobook-reply-display-'));
  t.after(()=>rm(dir,{recursive:true,force:true}));
  await exec(javac,['-d',dir,fileURLToPath(new URL('../android/src/family/tv/companion/ReplyDisplay.java',import.meta.url)),fileURLToPath(new URL('./ReplyDisplayTest.java',import.meta.url))]);
  const {stdout}=await exec(fileURLToPath(new URL('java',jdk)),['-cp',dir,'family.tv.companion.ReplyDisplayTest']);
  assert.match(stdout,/regression checks passed/);
});
