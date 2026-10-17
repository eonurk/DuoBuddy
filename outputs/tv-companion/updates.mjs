import {readFile} from 'node:fs/promises';
import {join} from 'node:path';

const REPOSITORY = 'https://github.com/eonurk/DuoBuddy';
const API = 'https://api.github.com/repos/eonurk/DuoBuddy/releases/latest';
function version(value) {
  const match = String(value).match(/^v?(\d+)\.(\d+)\.(\d+)(?:-([0-9A-Za-z.-]+))?$/);
  return match ? {numbers: match.slice(1, 4).map(Number), pre: match[4] || ''} : null;
}
export function newerVersion(candidate, current) {
  const a = version(candidate), b = version(current);
  if (!a || !b) return false;
  for (let i = 0; i < 3; i++) if (a.numbers[i] !== b.numbers[i]) return a.numbers[i] > b.numbers[i];
  if (!a.pre || !b.pre) return !!b.pre && !a.pre;
  const left = a.pre.split('.'), right = b.pre.split('.');
  for (let i = 0; i < Math.max(left.length, right.length); i++) {
    if (left[i] === right[i]) continue;
    if (left[i] === undefined || right[i] === undefined) return left[i] !== undefined;
    const an = /^\d+$/.test(left[i]), bn = /^\d+$/.test(right[i]);
    return an && bn ? Number(left[i]) > Number(right[i]) : an !== bn ? !an : left[i] > right[i];
  }
  return false;
}

// Check only when requested. The user downloads a replacement app; no downloaded
// executable is run or silently installed by the controller.
export async function checkForUpdate(root, request = fetch) {
  const metadata = JSON.parse(await readFile(join(root, 'release.json'), 'utf8'));
  const response = await request(API, {headers: {Accept: 'application/vnd.github+json', 'User-Agent': 'DuoBuddy'}, signal: AbortSignal.timeout(10000), redirect: 'error'});
  if (response.status === 404) return {current: metadata.version, available: false, message: 'No public Mac release is available yet.'};
  if (!response.ok) throw Error('Could not check updates. Try again later.');
  const release = await response.json();
  const asset = release.assets?.find(a => /^DuoBuddy-[0-9A-Za-z.-]+-mac-universal\.zip$/.test(a.name) && String(a.browser_download_url).startsWith(REPOSITORY + '/releases/download/'));
  if (!release.draft && !release.prerelease && asset && newerVersion(release.tag_name, metadata.version)) {
    return {current: metadata.version, latest: release.tag_name, available: true, download: asset.browser_download_url, message: 'Download the new Mac app, quit DuoBuddy from the menu bar, and replace it in Applications. Then open it and select Install / update DuoBuddy on this TV. Your saved settings are kept.'};
  }
  return {current: metadata.version, available: false, message: 'You have the latest available Mac app.'};
}
