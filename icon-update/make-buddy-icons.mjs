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
async function text(value, size, color, bold = false, extra = 0) {
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
  // Quicksand stops at 700, so `extra` thickens strokes further with a same-colour outline (in pixels).
  const pad = 2 + extra, outline = extra ? ` stroke="${color}" stroke-width="${extra * 2 / scale}" stroke-linejoin="round"` : '';
  return sharp(svg(Math.ceil(cursor * scale) + pad * 2, Math.ceil((font.ascent - font.descent) * scale) + pad * 2,
    `<g fill="${color}"${outline} transform="translate(${pad} ${pad + font.ascent * scale}) scale(${scale} ${-scale})">${paths}</g>`)).png().toBuffer();
}

// A square launcher icon with space around the original mark.
const icon = await sharp(svg(384, 384, '<rect x="1" y="1" width="382" height="382" rx="80" fill="#fffaf5" stroke="#ffedd5" stroke-width="2"/>'))
  .composite([{input: await mark(288, 64), left: 48, top: 48}])
  .png().toBuffer();
await sharp(icon).resize(192, 192).png().toFile(resolve(target, 'duobuddy_app_icon.png'));

// A full-bleed tile like Netflix or Spotify: mark and wordmark centred on white, no tagline.
// Compose at twice the TV banner size to keep the wordmark crisp at 320 × 180.
const name = await text('Buddy', 128, '#000000', true, 1.2);
const {width: nameWidth, height: nameHeight} = await sharp(name).metadata();
const markSize = 140, gap = 26, left = Math.round((640 - markSize - gap - nameWidth) / 2);
const banner = await sharp(svg(640, 360, '<rect width="640" height="360" fill="#ffffff"/>'))
  .composite([
    {input: await mark(markSize, 32), left, top: Math.round((360 - markSize) / 2)},
    {input: name, left: left + markSize + gap, top: Math.round((360 - nameHeight) / 2) + 2},
  ])
  .png().toBuffer();
await sharp(banner).resize(320, 180).png().toFile(resolve(target, 'duobuddy_tv_banner.png'));

for (const name of ['duobuddy_app_icon.png', 'duobuddy_tv_banner.png']) await copyFile(resolve(target, name), resolve(preview, name));
console.log('Created Buddy launcher icon and TV banner.');
