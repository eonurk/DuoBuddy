import {readFile, writeFile} from 'node:fs/promises';
import {existsSync} from 'node:fs';

const MAX_NOTES = 40, MAX_PEOPLE = 8;
// A profile sticks for a while, then Buddy goes back to the household, because the
// next person on the sofa is often someone else.
export const PROFILE_IDLE_MS = 45 * 60 * 1000;

const clean = v => String(v || '').replace(/[\x00-\x1F\x7F]+/g, ' ').replace(/\s+/g, ' ').trim();
export function personName(value) {
  const name = clean(value).replace(/^(it'?s|this is|i'?m|i am)\s+/i, '');
  if (!/^\p{L}[\p{L}' .-]{0,29}$/u.test(name)) throw Error('A name must be 1–30 letters.');
  return name.charAt(0).toLocaleUpperCase() + name.slice(1);
}
export function birthday(value) {
  const m = String(value || '').trim().match(/^(\d{1,2})-(\d{1,2})$/);
  const month = m && Number(m[1]), day = m && Number(m[2]);
  if (!m || month < 1 || month > 12 || day < 1 || day > new Date(Date.UTC(2024, month, 0)).getUTCDate()) throw Error('A birthday is month-day, e.g. 03-14.');
  return String(month).padStart(2, '0') + '-' + String(day).padStart(2, '0');
}

// Short notes the family asked Buddy to remember: household notes everyone shares, plus
// each person's own notes and birthday. One person can be "at the TV" at a time.
export class Memory {
  constructor(file) {this.file = file; this.notes = []; this.people = {}; this.active = ''; this.activeAt = 0;}
  async load() {
    if (!existsSync(this.file)) return;
    try {
      const data = JSON.parse(await readFile(this.file, 'utf8'));
      // Earlier builds stored a plain list of household notes.
      const household = Array.isArray(data) ? data : data.household;
      this.notes = (household || []).filter(n => typeof n === 'string').slice(0, MAX_NOTES);
      this.people = {};
      for (const [name, p] of Object.entries(Array.isArray(data) ? {} : data.people || {})) {
        try {this.people[personName(name)] = {notes: (p.notes || []).filter(n => typeof n === 'string').slice(0, MAX_NOTES), birthday: p.birthday ? birthday(p.birthday) : ''};} catch {}
      }
    } catch {this.notes = []; this.people = {};}
  }
  async save() {await writeFile(this.file, JSON.stringify({household: this.notes, people: this.people}), {mode: 0o600});}

  /** The person Buddy is talking to, or '' for the whole household. */
  current(now = Date.now()) {
    if (this.active && now - this.activeAt > PROFILE_IDLE_MS) this.active = '';
    return this.active;
  }
  touch(now = Date.now()) {if (this.current(now)) this.activeAt = now;}
  find(name) {const n = String(name || '').trim().toLowerCase(); return Object.keys(this.people).find(p => p.toLowerCase() === n) || '';}
  async ensure(name) {
    const who = personName(name), known = this.find(who);
    if (known) return {name: known, isNew: false};
    if (Object.keys(this.people).length >= MAX_PEOPLE) throw Error('There are already eight people. Remove one on the setup page first.');
    this.people[who] = {notes: [], birthday: ''}; await this.save();
    return {name: who, isNew: true};
  }
  async switchTo(name, now = Date.now()) {
    const person = await this.ensure(name);
    this.active = person.name; this.activeAt = now;
    return person;
  }
  leave() {this.active = '';}
  async removePerson(name) {
    const who = this.find(name);
    if (!who) throw Error('No one by that name.');
    delete this.people[who]; if (this.active === who) this.active = '';
    await this.save();
  }
  async setBirthday(name, value) {
    const who = name ? (await this.ensure(name)).name : this.current();
    if (!who) throw Error('Say whose birthday it is.');
    this.people[who].birthday = value ? birthday(value) : '';
    await this.save();
    return who;
  }
  birthdays() {return Object.entries(this.people).filter(([, p]) => p.birthday).map(([name, p]) => ({name, birthday: p.birthday}));}

  /** Notes for a scope: 'person' (the active person) or 'household'. */
  list(scope) {return scope === 'person' ? this.people[this.current()]?.notes || [] : this.notes;}
  scopeFor(requested) {
    if (requested === 'person' && !this.current()) throw Error('No one has said who they are yet. Ask their name first.');
    return requested || (this.current() ? 'person' : 'household');
  }
  async add(note, scope = 'household') {
    const text = clean(note);
    if (!text || text.length > 200) throw Error('A note must be 1–200 characters.');
    const notes = this.list(this.scopeFor(scope));
    if (notes.includes(text)) return text;
    if (notes.length >= MAX_NOTES) throw Error('Memory is full. Ask me to forget something first.');
    notes.push(text); await this.save();
    return text;
  }
  async forget(query, scope) {
    const q = String(query || '').toLowerCase().trim();
    if (!q) throw Error('Say which note to forget.');
    // Without a scope, forget from both the person's notes and the household's.
    const scopes = scope ? [this.scopeFor(scope)] : this.current() ? ['person', 'household'] : ['household'];
    let removed = 0;
    for (const s of scopes) {
      const notes = this.list(s), keep = notes.filter(n => !n.toLowerCase().includes(q));
      removed += notes.length - keep.length; notes.splice(0, notes.length, ...keep);
    }
    if (!removed) throw Error('No saved note matches that.');
    await this.save();
    return removed;
  }
  async clear(name) {
    if (name) {const who = this.find(name); if (!who) throw Error('No one by that name.'); this.people[who].notes = [];}
    else this.notes = [];
    await this.save();
  }
}
