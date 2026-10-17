// Buddy dresses up on its own for the time of year and for family birthdays. The TV
// layers these over the outfit picked in Style, slot by slot, while the setting is on.
const SEASONS = [
  // [from MM-DD, to MM-DD, label, outfit] — the first match wins.
  ['10-31', '10-31', 'Halloween', {hat: 'witchhat', neck: 'spookyscarf'}],
  ['10-15', '10-30', 'Halloween', {hat: 'pumpkin', neck: 'spookyscarf'}],
  ['12-31', '12-31', 'New Year’s Eve', {hat: 'party'}],
  ['01-01', '01-01', 'New Year’s Day', {hat: 'party'}],
  ['12-01', '12-26', 'Christmas', {hat: 'santa', neck: 'festivescarf'}],
  ['02-14', '02-14', 'Valentine’s Day', {eyewear: 'hearts'}],
  ['03-20', '03-21', 'the first days of spring', {hat: 'sunrisecrown'}]
];

const monthDay = d => String(d.getMonth() + 1).padStart(2, '0') + '-' + String(d.getDate()).padStart(2, '0');

/** Today's automatic outfit, or null. birthdays: [{name, birthday: 'MM-DD'}]. */
export function seasonalLook(now = new Date(), birthdays = []) {
  const today = monthDay(now);
  const party = birthdays.filter(b => b.birthday === today).map(b => b.name);
  if (party.length) return {label: party.join(' and ') + '’s birthday', hat: 'party', neck: 'medal', birthday: party};
  for (const [from, to, label, outfit] of SEASONS) if (today >= from && today <= to) return {label, ...outfit};
  return null;
}
