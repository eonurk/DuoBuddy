import {spawn} from 'node:child_process';
import {DEFAULT_VOICE,validateVoice} from './voice-config.mjs';

export class PiperClient {
  constructor({host=process.env.TV_PIPER_HOST||'m1-2',port=18771,remotePort=18770}={}){
    this.host=host;this.port=port;this.remotePort=remotePort;this.child=null;this.connecting=null;this.ready=false;this.lastError='';
    this.base='http://127.0.0.1:'+port;
  }
  async health(){
    const r=await fetch(this.base+'/health',{signal:AbortSignal.timeout(2000)});
    if(!r.ok)throw Error('Piper health check failed.');
    const data=await r.json();
    if(data.service!=='duobook-piper-v1'||!data.ready)throw Error('Piper is still loading.');
    if(!data.voiceSelection)throw Error('The Piper worker needs its voice-selection update.');
    this.ready=true;return data;
  }
  async ensure(){
    try{return await this.health();}catch{this.ready=false;}
    if(this.connecting)return this.connecting;
    this.connecting=this.connect();
    try{return await this.connecting;}finally{this.connecting=null;}
  }
  async connect(){
    if(!this.child||this.child.exitCode!==null||this.child.killed){
      const root='/Users/eonurk/Documents/Codex/tv-companion-piper';
      // Override the SSH profile's IPv4-only setting so a reachable IPv6 address can be used.
      this.child=spawn('/usr/bin/ssh',['-T','-o','AddressFamily=any','-o','BatchMode=yes','-o','ConnectTimeout=25','-o','ExitOnForwardFailure=yes','-o','ServerAliveInterval=15','-o','ServerAliveCountMax=3','-L',`127.0.0.1:${this.port}:127.0.0.1:${this.remotePort}`,this.host,'/usr/bin/caffeinate','-i',root+'/.venv/bin/python',root+'/piper_worker.py','--model',root+'/voices/en_US-lessac-medium.onnx','--port',String(this.remotePort)],{stdio:['ignore','pipe','pipe']});
      this.child.on('error',e=>{this.lastError=e.message;this.ready=false;});
      this.child.on('exit',()=>{this.ready=false;});
      this.child.stdout.on('data',()=>{});
      this.child.stderr.on('data',d=>{this.lastError=d.toString().trim().slice(-500);});
    }
    for(let attempt=0;attempt<45;attempt++){
      try{return await this.health();}catch{}
      if(this.child.exitCode!==null)throw Error('Cannot start Piper on '+this.host+': '+this.lastError);
      await new Promise(resolve=>setTimeout(resolve,350));
    }
    throw Error('Piper on '+this.host+' did not become ready: '+this.lastError);
  }
  async synthesize(text,{voice=DEFAULT_VOICE}={}){
    if(typeof text!=='string'||!text.trim()||text.length>8000)throw Error('Speech text must contain 1–8000 characters.');
    validateVoice(voice);
    await this.ensure();
    const r=await fetch(this.base+'/synthesize',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({text,voice}),signal:AbortSignal.timeout(60000)});
    if(!r.ok)throw Error('Piper synthesis failed ('+r.status+').');
    const audio=Buffer.from(await r.arrayBuffer());
    if(audio.toString('ascii',0,4)!=='RIFF'||audio.toString('ascii',8,12)!=='WAVE')throw Error('Piper did not return a WAV file.');
    return {audio,voice:r.headers.get('x-piper-voice'),synthesisMs:Number(r.headers.get('x-synthesis-ms')),duration:Number(r.headers.get('x-audio-duration'))};
  }
  close(){this.child?.kill('SIGTERM');this.ready=false;}
}
