import {readFile, mkdir, copyFile} from 'node:fs/promises';
import {fileURLToPath} from 'node:url';
import {resolve} from 'node:path';
import sharp from '/Users/onur-lumc/Desktop/Github/Booked.ly/language-book-app/node_modules/sharp/lib/index.js';
import fontkit from '/Users/onur-lumc/Desktop/Github/Booked.ly/language-book-app/node_modules/fontkit/dist/main.cjs';

const root = fileURLToPath(new URL('../', import.meta.url));
const source = process.argv[2] ? resolve(process.argv[2]) : resolve(root, 'outputs/tv-companion/art/duobook-logo.png');
const target = process.argv[3] ? resolve(process.argv[3]) : resolve(root, 'outputs/tv-companion/android/res/drawable-nodpi');
const preview = resolve(root, 'icon-update/preview');
const fontfile = resolve(root, 'outputs/tv-companion/android/assets/fonts/Quicksand.ttf');
const typeface = fontkit.openSync(fontfile);
await mkdir(target, {recursive: true});
await mkdir(preview, {recursive: true});
const logo = await readFile(source);
const svg = (width, height, content) => Buffer.from(`<svg width="${width}" height="${height}" xmlns="http://www.w3.org/2000/svg">${content}</svg>`);
async function mark(size, radius) {
  const mask = svg(size, size, `<rect width="${size}" height="${size}" rx="${radius}" fill="white"/>`);
  return sharp(logo).resize(size, size, {fit: 'contain'}).composite([{input: mask, blend: 'dest-in'}]).png().toBuffer();
}
async function text(value, size, color, bold = false) {
  // Outline the bundled Quicksand glyphs so no system-font fallback can change the wordmark.
  const font = typeface.getVariation({wght: bold ? 700 : 500});
  const run = font.layout(value), scale = size / font.unitsPerEm;
  let cursor = 0;
  const paths = run.glyphs.map((glyph, i) => {
    const position = run.positions[i];
    const path = `<path d="${glyph.path.toSVG()}" transform="translate(${cursor + position.xOffset} ${position.yOffset})"/>`;
    cursor += position.xAdvance;
    return path;
  }).join('');
  return sharp(svg(Math.ceil(cursor * scale) + 4, Math.ceil((font.ascent - font.descent) * scale) + 4,
    `<g fill="${color}" transform="translate(2 ${2 + font.ascent * scale}) scale(${scale} ${-scale})">${paths}</g>`)).png().toBuffer();
}

// A square launcher icon with space around the original mark.
const icon = await sharp(svg(384, 384, '<rect x="1" y="1" width="382" height="382" rx="80" fill="#fffaf5" stroke="#ffedd5" stroke-width="2"/>'))
  .composite([{input: await mark(288, 64), left: 48, top: 48}])
  .png().toBuffer();
await sharp(icon).resize(192, 192).png().toFile(resolve(target, 'duobuddy_app_icon.png'));

// Compose at twice the TV banner size to keep the wordmark crisp at 320 × 180.
const name = await text('DuoBuddy', 60, '#1e293b', true);
const subtitle = await text('Your little TV companion', 25, '#64748b');
const banner = await sharp(svg(640, 360, '<rect width="640" height="360" fill="#fffaf5"/><rect x="1" y="1" width="638" height="358" rx="22" fill="none" stroke="#ffedd5" stroke-width="2"/>'))
  .composite([{input: await mark(176, 40), left: 40, top: 92}, {input: name, left: 248, top: 132}, {input: subtitle, left: 250, top: 214}])
  .png().toBuffer();
await sharp(banner).resize(320, 180).png().toFile(resolve(target, 'duobuddy_tv_banner.png'));

for (const name of ['duobuddy_app_icon.png', 'duobuddy_tv_banner.png']) await copyFile(resolve(target, name), resolve(preview, name));
console.log('Created DuoBuddy launcher icon and named TV banner.');
