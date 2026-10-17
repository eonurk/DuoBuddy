import {readFile, writeFile} from 'node:fs/promises';
import {existsSync} from 'node:fs';

// Data for Buddy's calm screensaver: weather from Open-Meteo (free, no key; only the
// saved coordinates are sent) and a word of the day in the language the family learns.

const WMO = [
  [[0], 'Clear', 'sun'], [[1], 'Mostly clear', 'sun'], [[2], 'Partly cloudy', 'partly'], [[3], 'Overcast', 'cloud'],
  [[45, 48], 'Fog', 'fog'], [[51, 53, 55, 56, 57], 'Drizzle', 'rain'], [[61, 63, 66, 80, 81], 'Rain', 'rain'],
  [[65, 67, 82], 'Heavy rain', 'rain'], [[71, 73, 75, 77, 85, 86], 'Snow', 'snow'], [[95, 96, 99], 'Thunderstorms', 'storm']
];
export function describeWeatherCode(code) {
  const hit = WMO.find(([codes]) => codes.includes(Number(code)));
  return hit ? {text: hit[1], icon: hit[2]} : {text: 'Weather', icon: 'cloud'};
}
export function parseForecast(data, place) {
  const c = data?.current, d = data?.daily;
  if (!c || !d) throw Error('Unexpected weather data.');
  const now = describeWeatherCode(c.weather_code), tomorrow = describeWeatherCode(d.weather_code?.[1]);
  const round = v => Number.isFinite(v) ? Math.round(v) : null;
  return {
    place, temp: round(c.temperature_2m), text: now.text, icon: now.icon, isDay: c.is_day !== 0,
    high: round(d.temperature_2m_max?.[0]), low: round(d.temperature_2m_min?.[0]), rain: round(d.precipitation_probability_max?.[0]),
    tomorrow: {text: tomorrow.text, icon: tomorrow.icon, high: round(d.temperature_2m_max?.[1]), low: round(d.temperature_2m_min?.[1])}
  };
}

// A small built-in list keeps the word of the day working without an API key.
const FALLBACK_WORDS = {
  Dutch: [['gezellig', 'cosy, warm and sociable', 'Wat een gezellige avond!'], ['uitwaaien', 'to clear your head in the wind', 'We gaan lekker uitwaaien op het strand.'], ['lekker', 'nice, tasty, pleasant', 'Lekker weertje vandaag.'], ['boterham', 'slice of bread', 'Ik eet een boterham met kaas.'], ['fiets', 'bicycle', 'Mijn fiets staat buiten.'], ['regenboog', 'rainbow', 'Kijk, een regenboog!'], ['knuffel', 'hug; cuddly toy', 'Geef me een knuffel.']],
  Turkish: [['merhaba', 'hello', 'Merhaba, nasılsın?'], ['kitap', 'book', 'Bu kitap çok güzel.'], ['çay', 'tea', 'Bir çay alır mısın?'], ['gökkuşağı', 'rainbow', 'Bak, gökkuşağı!'], ['afiyet olsun', 'enjoy your meal', 'Afiyet olsun!']],
  Spanish: [['sobremesa', 'chatting at the table after a meal', 'La sobremesa duró dos horas.'], ['estrella', 'star', 'Mira esa estrella.'], ['abrazo', 'hug', 'Te mando un abrazo.']],
  English: [['cosy', 'warm and comfortable', 'What a cosy evening!'], ['rainbow', 'arc of colours in the sky', 'Look, a rainbow!']]
};
export const WORD_LANGUAGES = ['Dutch', 'Turkish', 'Spanish', 'French', 'German', 'Italian', 'Portuguese', 'English', 'Japanese', 'Arabic'];
export function fallbackWord(language, date) {
  const list = FALLBACK_WORDS[language] || FALLBACK_WORDS.English;
  const day = Math.floor(Date.UTC(date.getFullYear(), date.getMonth(), date.getDate()) / 86400000);
  const [word, meaning, example] = list[day % list.length];
  return {language: FALLBACK_WORDS[language] ? language : 'English', word, meaning, example};
}
const localDay = d => d.getFullYear() + '-' + String(d.getMonth() + 1).padStart(2, '0') + '-' + String(d.getDate()).padStart(2, '0');

export class Ambient {
  /** ask(prompt, schema) returns parsed JSON from the model, or throws. */
  constructor({file, fetchImpl = fetch, ask = null}) {this.file = file; this.fetch = fetchImpl; this.ask = ask; this.weather = null; this.weatherAt = 0; this.weatherKey = ''; this.word = null;}
  async load() {if (existsSync(this.file)) try {this.word = JSON.parse(await readFile(this.file, 'utf8')).word || null;} catch {}}

  async geocode(name) {
    const q = String(name || '').trim();
    if (!q || q.length > 80) throw Error('Enter a town or city.');
    const r = await this.fetch('https://geocoding-api.open-meteo.com/v1/search?count=1&format=json&name=' + encodeURIComponent(q), {signal: AbortSignal.timeout(8000)});
    const hit = (await r.json()).results?.[0];
    if (!hit) throw Error('No place called ' + q + ' was found.');
    return {name: hit.name + (hit.country_code ? ', ' + hit.country_code : ''), latitude: hit.latitude, longitude: hit.longitude};
  }
  async forecast(location, now = Date.now()) {
    if (!location) return null;
    const key = location.latitude + ',' + location.longitude;
    if (this.weather && this.weatherKey === key && now - this.weatherAt < 20 * 60 * 1000) return this.weather;
    try {
      const url = 'https://api.open-meteo.com/v1/forecast?latitude=' + location.latitude + '&longitude=' + location.longitude +
        '&current=temperature_2m,weather_code,is_day&daily=weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max&forecast_days=2&timezone=auto';
      const r = await this.fetch(url, {signal: AbortSignal.timeout(8000)});
      if (!r.ok) throw Error('Weather service answered ' + r.status);
      this.weather = parseForecast(await r.json(), location.name); this.weatherAt = now; this.weatherKey = key;
    } catch (e) {console.log('Weather unavailable: ' + e.message); if (this.weatherKey !== key) this.weather = null;}
    return this.weather;
  }
  async wordOfDay(language, date = new Date()) {
    const day = localDay(date);
    if (this.word?.day === day && this.word.language === language) return this.word;
    let word = null;
    if (this.ask) try {
      const recent = this.word?.language === language ? [this.word.word] : [];
      const w = await this.ask(`Pick a friendly, useful ${language} word or short phrase of the day for a family learning ${language} (date ${day}). Prefer everyday, cheerful, seasonal words${recent.length ? '; not ' + recent.join(', ') : ''}. Give its English meaning in a few words and one short, simple example sentence in ${language}.`,
        {type: 'object', properties: {word: {type: 'string'}, meaning: {type: 'string'}, example: {type: 'string'}}, required: ['word', 'meaning', 'example'], additionalProperties: false});
      if ([w.word, w.meaning, w.example].every(v => typeof v === 'string' && v.trim() && v.length < 160)) word = {language, word: w.word.trim(), meaning: w.meaning.trim(), example: w.example.trim()};
    } catch (e) {console.log('Word of the day falls back to the built-in list: ' + e.message);}
    this.word = {...(word || fallbackWord(language, date)), day};
    await writeFile(this.file, JSON.stringify({word: this.word}), {mode: 0o600}).catch(() => {});
    return this.word;
  }
}
