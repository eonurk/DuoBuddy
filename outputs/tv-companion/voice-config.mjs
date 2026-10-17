export const VOICES = Object.freeze([
  {id:'en_US-hfc_female-medium',label:'HFC female · American English',description:'A different feminine voice to try first.'},
  {id:'en_US-lessac-high',label:'Lessac · higher quality',description:'The original feminine speaker with a larger voice model.'},
  {id:'en_US-lessac-medium',label:'Lessac · original',description:'The voice you heard before.'}
]);
export const DEFAULT_VOICE = VOICES[0].id;
export function validateVoice(id){
  if(!VOICES.some(voice=>voice.id===id))throw Error('Choose one of the available voices.');
  return id;
}
