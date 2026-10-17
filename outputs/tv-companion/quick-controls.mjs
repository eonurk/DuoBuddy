// Deliberately match complete, unambiguous commands only. Questions, negations,
// compound requests and unfamiliar wording still go through the assistant.
export function quickControl(text) {
  const command = String(text).trim().toLowerCase().replace(/[.!]+$/, '').trim();
  const media = {
    'pause': ['pause', 'Paused.'], 'pause playback': ['pause', 'Paused.'],
    'play': ['play', 'Resuming.'], 'resume': ['play', 'Resuming.'],
    'resume playback': ['play', 'Resuming.'], 'stop playback': ['stop', 'Stopped.'],
    'next track': ['next', 'Skipping to the next track.'],
    'previous track': ['previous', 'Going to the previous track.']
  };
  if (Object.hasOwn(media, command)) {
    const [action, reply] = media[command];
    return {name: 'media', args: {command: action}, reply};
  }
  const volumes = {
    'volume up': ['up', 'Turning it up.'], 'turn it up': ['up', 'Turning it up.'],
    'louder': ['up', 'Turning it up.'], 'volume down': ['down', 'Turning it down.'],
    'turn it down': ['down', 'Turning it down.'], 'quieter': ['down', 'Turning it down.'],
    'mute': ['mute', 'Muted.'], 'unmute': ['unmute', 'Unmuted.']
  };
  if (Object.hasOwn(volumes, command)) {
    const [action, reply] = volumes[command];
    return {name: 'volume', args: {action, amount: null}, reply};
  }
  const level = command.match(/^(?:set (?:the )?volume to|volume) (\d{1,3})(?:\s*(?:%|percent))?$/);
  if (level && Number(level[1]) <= 100) {
    const amount = Number(level[1]);
    return {name: 'volume', args: {action: 'set', amount}, reply: `Volume set to ${amount} percent.`};
  }
  if (command === 'turn off the tv' || command === 'turn the tv off')
    return {name: 'remote', args: {key: 'sleep', times: 1}, reply: 'Putting the TV to sleep.'};
  if (command === 'wake the tv' || command === 'turn on the tv')
    return {name: 'remote', args: {key: 'wake', times: 1}, reply: 'Waking the TV.'};
  if (command === 'screensaver' || command === 'show the clock')
    return {name: 'screensaver', args: {}, reply: 'Showing the clock.'};
  return null;
}

// Keep the latest screen for reading and tap coordinates, but replace superseded
// image payloads with text. Tool calls/results and their IDs remain intact.
export function retireScreenshots(input) {
  for (let i = 0; i < input.length; i++) {
    const item = input[i];
    if (!Array.isArray(item.content) || !item.content.some(part => part.type === 'input_image')) continue;
    input[i] = {...item, content: item.content.map(part => part.type === 'input_image'
      ? {type: 'input_text', text: '[Earlier TV screenshot omitted; use the latest screen for navigation.]'}
      : part)};
  }
}
