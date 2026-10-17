// In-memory timers and reminders. They live as long as the controller runs.
export class Timers {
  constructor(onFire) {this.onFire = onFire; this.items = new Map(); this.next = 1;}
  set(seconds, label = '') {
    if (!Number.isInteger(seconds) || seconds < 1 || seconds > 86400) throw Error('Timers run from 1 second to 24 hours.');
    if (this.items.size >= 20) throw Error('Too many timers are running.');
    const id = this.next++;
    const text = String(label || '').trim().slice(0, 80);
    const handle = setTimeout(() => {this.items.delete(id); this.onFire({id, label: text});}, seconds * 1000);
    handle.unref?.();
    this.items.set(id, {id, label: text, dueAt: Date.now() + seconds * 1000, handle});
    return {id, label: text, seconds};
  }
  list() {return [...this.items.values()].map(t => ({id: t.id, label: t.label, secondsLeft: Math.max(0, Math.round((t.dueAt - Date.now()) / 1000))}));}
  cancel(id) {
    const t = this.items.get(id);
    if (!t) throw Error('No timer with that number.');
    clearTimeout(t.handle); this.items.delete(id);
    return {id, label: t.label};
  }
  clear() {for (const id of [...this.items.keys()]) this.cancel(id);}
}

export function spokenDuration(seconds) {
  const h = Math.floor(seconds / 3600), m = Math.floor(seconds % 3600 / 60), s = seconds % 60;
  const parts = [];
  if (h) parts.push(h + (h === 1 ? ' hour' : ' hours'));
  if (m) parts.push(m + (m === 1 ? ' minute' : ' minutes'));
  if (s && !h) parts.push(s + (s === 1 ? ' second' : ' seconds'));
  return parts.join(' and ') || '0 seconds';
}
