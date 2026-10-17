import {spawn,execFile} from 'node:child_process';
import {promisify} from 'node:util';
import {createInterface} from 'node:readline';
const exec=promisify(execFile);

// Remotes report their microphone or assistant button as one of these keys: the Mi Box
// remote sends KEY_VOICECOMMAND, Google TV and Shield remotes KEY_ASSISTANT or KEY_SEARCH.
export const VOICE_KEYS=Object.freeze({KEY_VOICECOMMAND:'0246',KEY_ASSISTANT:'0247',KEY_SEARCH:'00d9'});
const DEDICATED=['KEY_VOICECOMMAND','KEY_ASSISTANT'];
const REMOTE_NAME=/remote|\brc\b|rcu|controller|ble|voice|atv|bluetooth|xiaomi|nvidia|shield|chromecast|g20|onn/i;
const IGNORED=/virtual|uinput|gpio|hdmi[_ -]?cec|power|touch/i;

// Every input device that can send a voice key, best match first: the name chosen on the
// setup page, then devices with a dedicated microphone key, then remote-like names.
export function remoteDevices(description,preferred=''){
  const found=[];
  for(const block of String(description).split(/add device \d+:\s*/)){
    const path=block.match(/^\/dev\/input\/event\d+/)?.[0];
    if(!path)continue;
    const name=block.match(/name:\s+"([^"]*)"/)?.[1]||'';
    const keys=Object.keys(VOICE_KEYS).filter(k=>new RegExp('\\b(?:'+k+'|'+VOICE_KEYS[k]+')\\b','i').test(block));
    const chosen=!!preferred&&name.toLowerCase()===preferred.toLowerCase();
    if(preferred&&!chosen)continue;
    if(!chosen&&(!keys.length||IGNORED.test(name)))continue;
    const score=(chosen?100:0)+(keys.some(k=>DEDICATED.includes(k))?10:0)+(REMOTE_NAME.test(name)?5:0);
    // A keyboard's search key alone is not taken for a remote's microphone button.
    if(score<5)continue;
    found.push({path,name,keys,score});
  }
  found.sort((a,b)=>b.score-a.score);
  // A remote named on the setup page is used on its own, never swapped for another.
  if(preferred&&!(found[0]?.score>=100))found.length=0;
  if(!found.length)throw Error(preferred?`No input device called "${preferred}" is connected.`:'No remote with a microphone or assistant button is connected.');
  return found;
}
export function remoteDevicePath(description,preferred){return remoteDevices(description,preferred)[0].path;}
const KEY_RELEASE=new RegExp('\\bEV_KEY\\s+('+Object.keys(VOICE_KEYS).join('|')+')\\s+UP\\s*$');
const RAW_RELEASE=new RegExp('\\b0001\\s+('+Object.values(VOICE_KEYS).join('|')+')\\s+00000000\\s*$');
export function isMicrophoneRelease(line){return KEY_RELEASE.test(line)||RAW_RELEASE.test(line);}

export class RemoteButtonMonitor {
  constructor({adb,device,name='',onPress,onState}){Object.assign(this,{adb,device,name,onPress,onState});this.closed=true;this.children=[];this.timer=null;this.handling=false;this.lastPress=0;this.generation=0;}
  start(){if(!this.closed)return;this.closed=false;void this.connect();}
  async connect(){
    if(this.closed)return;
    const generation=this.generation;
    try{
      await exec(this.adb,['connect',this.device],{timeout:8000});
      const {stdout}=await exec(this.adb,['-s',this.device,'shell','getevent','-pl'],{timeout:8000,maxBuffer:1024*1024});
      const all=remoteDevices(stdout,this.name);
      // The chosen remote alone, or every remote that matches equally well (a TV may have two).
      const remotes=all.filter(r=>r.score===all[0].score);
      if(this.closed||generation!==this.generation)return;
      let finished=false;
      const disconnected=()=>{if(finished||this.closed||generation!==this.generation)return;finished=true;for(const c of this.children)c.kill('SIGTERM');this.children=[];this.retry('Remote button connection interrupted.');};
      for(const remote of remotes){
        // -tt gives getevent a terminal. Without one, Android 9 block-buffers its output
        // and a button press would not reach the controller until much later, if ever.
        // stdin stays open (never written) because adb ends the session when it closes.
        const child=spawn(this.adb,['-s',this.device,'shell','-tt','getevent','-lt',remote.path],{stdio:['pipe','pipe','pipe']});
        this.children.push(child);
        const lines=createInterface({input:child.stdout});
        lines.on('line',line=>{if(generation===this.generation&&isMicrophoneRelease(line))void this.press();});
        // Other key events are discarded immediately; no input is saved or logged.
        child.stderr.on('data',()=>{});
        child.once('error',disconnected);child.once('exit',()=>{lines.close();disconnected();});
      }
      this.onState({ready:true,error:'',remote:remotes.map(r=>r.name||r.path).join(', ')});
    }catch(e){if(generation===this.generation)this.retry(e.message);}
  }
  retry(error){if(this.closed)return;this.onState({ready:false,error,remote:''});clearTimeout(this.timer);this.timer=setTimeout(()=>void this.connect(),5000);}
  async press(){
    if(this.closed||this.handling||Date.now()-this.lastPress<1200)return;
    this.handling=true;this.lastPress=Date.now();
    try{await this.onPress();}catch(e){this.onState({ready:true,error:e.message});}finally{this.handling=false;}
  }
  close(){this.closed=true;this.generation++;clearTimeout(this.timer);for(const c of this.children)c.kill('SIGTERM');this.children=[];}
}
