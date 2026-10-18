import {writeFileSync} from 'node:fs';
import {TOOLS} from './tools.mjs';
writeFileSync(new URL('./android/assets/native-tools.json', import.meta.url), JSON.stringify(TOOLS));
