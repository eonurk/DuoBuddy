import test from 'node:test';
import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';
import {remoteDevices,remoteDevicePath,isMicrophoneRelease,RemoteButtonMonitor} from '../remote-button.mjs';

const device=(n,path,name,keys)=>`add device ${n}: ${path}\n  name:     "${name}"\n  events:\n    KEY (0001): ${keys}\n`;
test('remote discovery follows its name even when the input-device number changes',()=>{
  assert.equal(remoteDevicePath(device(1,'/dev/input/event0','keyboard','KEY_A  KEY_ENTER')+device(2,'/dev/input/event7','Xiaomi RC','KEY_UP  KEY_VOICECOMMAND')),'/dev/input/event7');
  assert.throws(()=>remoteDevicePath(device(1,'/dev/input/event0','keyboard','KEY_A  KEY_ENTER')));
});
test('the real Mi Box picks its Bluetooth remote over the IR keypad and virtual devices',async()=>{
  const found=remoteDevices(await readFile(new URL('./fixture-getevent-mibox.txt',import.meta.url),'utf8'));
  assert.equal(found[0].name,'Xiaomi RC');assert.equal(found[0].path,'/dev/input/event3');
  assert.ok(found[0].score>found[1].score);
  assert.ok(!found.some(r=>r.name==='virtual-search'));
});
test('other Android TV remotes are found by their voice or assistant key',()=>{
  // Chromecast with Google TV, Shield and a generic TV-box remote.
  assert.equal(remoteDevicePath(device(1,'/dev/input/event2','gpio-keys','KEY_POWER')+device(2,'/dev/input/event5','G10 Remote','KEY_UP  KEY_ASSISTANT')),'/dev/input/event5');
  assert.equal(remoteDevicePath(device(1,'/dev/input/event4','NVIDIA Remote','KEY_BACK  KEY_SEARCH')),'/dev/input/event4');
  assert.equal(remoteDevicePath(device(1,'/dev/input/event1','HID 0b05:4500','KEY_HOMEPAGE  KEY_VOICECOMMAND')),'/dev/input/event1');
  assert.equal(remoteDevicePath(device(1,'/dev/input/event1','HID Remote','KEY_UP  0247')),'/dev/input/event1');
  // A plain keyboard's search key is not mistaken for a microphone button.
  assert.throws(()=>remoteDevicePath(device(1,'/dev/input/event1','USB Keyboard','KEY_A  KEY_SEARCH')));
});
test('a remote named on the setup page wins over automatic detection',()=>{
  const list=device(1,'/dev/input/event1','Xiaomi RC','KEY_VOICECOMMAND')+device(2,'/dev/input/event2','My Keyboard','KEY_A');
  assert.equal(remoteDevicePath(list,'my keyboard'),'/dev/input/event2');
  assert.throws(()=>remoteDevicePath(list,'Shield Remote'),/Shield Remote/);
});
test('only a released microphone key activates Buddy',()=>{
  assert.equal(isMicrophoneRelease('[ 123.456] EV_KEY KEY_VOICECOMMAND UP'),true);
  assert.equal(isMicrophoneRelease('[ 123.456] 0001 0246 00000000'),true);
  assert.equal(isMicrophoneRelease('[ 123.456] EV_KEY KEY_ASSISTANT UP'),true);
  assert.equal(isMicrophoneRelease('[ 123.456] EV_KEY KEY_SEARCH UP'),true);
  assert.equal(isMicrophoneRelease('[ 123.456] 0001 0247 00000000'),true);
  for(const line of ['EV_KEY KEY_VOICECOMMAND DOWN','EV_KEY KEY_VOICECOMMAND REPEAT','EV_KEY KEY_ENTER UP','EV_KEY KEY_VOLUMEUP UP','0001 0246 00000001','EV_SYN SYN_REPORT 00000000'])assert.equal(isMicrophoneRelease(line),false);
});
test('repeated release events cannot launch overlapping microphone sessions',async()=>{
  let calls=0,finish;
  const monitor=new RemoteButtonMonitor({onPress:()=>{calls++;return new Promise(resolve=>{finish=resolve;});},onState:()=>{}});
  monitor.closed=false;
  const first=monitor.press();await monitor.press();assert.equal(calls,1);
  finish();await first;await monitor.press();assert.equal(calls,1);
  monitor.close();
});

test('terminal-style lines from a real Mi Box remote are recognised', () => {
  assert.equal(isMicrophoneRelease('[    5771.548106] EV_KEY       KEY_VOICECOMMAND     UP                  \r'), true);
  assert.equal(isMicrophoneRelease('[    5771.475905] EV_KEY       KEY_VOICECOMMAND     DOWN                \r'), false);
  assert.equal(isMicrophoneRelease('[    5771.548106] EV_SYN       SYN_REPORT           00000000            \r'), false);
});
