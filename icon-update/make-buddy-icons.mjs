import sharp from '/Users/onur-lumc/Desktop/Github/Booked.ly/language-book-app/node_modules/sharp/lib/index.js';
import {readFile, mkdir} from 'node:fs/promises';
import {fileURLToPath} from 'node:url';
import {resolve} from 'node:path';

const root = fileURLToPath(new URL('../', import.meta.url));
const source = process.argv[2] ? resolve(process.argv[2]) : resolve(root, 'outputs/tv-companion/art/duobook-logo.png');
const target = process.argv[3] ? resolve(process.argv[3]) : resolve(root, 'outputs/tv-companion/android/res/drawable-nodpi');
await mkdir(target, {recursive: true});
const logo = await readFile(source);
await sharp(logo).resize(192, 192).png().toFile(resolve(target, 'buddy_icon.png'));
const bannerLogo = await sharp(logo).resize(180, 180).png().toBuffer();
await sharp({create: {width: 320, height: 180, channels: 4, background: '#f97316'}})
  .composite([{input: bannerLogo, left: 70, top: 0}])
  .png().toFile(resolve(target, 'buddy_banner.png'));
console.log('Created launcher icon and TV banner directly from the DuoBook logo.');
