// 把官方 zh_CN.lang 里所有 `item.*.name` / `tile.*.name` 的键模式列出来，
// 用来找出"官方 ID → 译名键"的命名规律
// 用法: node peek-patterns.mjs [item|tile]
import { readFileSync } from 'node:fs';
import path from 'node:path';

const here = path.dirname(new URL(import.meta.url).pathname.replace(/^\/([A-Za-z]:)/, '$1'));
const prefix = process.argv[2] ?? 'item';

const kv = new Map();
for (const line of readFileSync(path.join(here, 'zh_CN.lang'), 'utf8').replace(/^\uFEFF/, '').split(/\r?\n/)) {
  if (!line || line.startsWith('#')) continue;
  const i = line.indexOf('=');
  if (i > 0) kv.set(line.slice(0, i).trim(), line.slice(i + 1).trim());
}

const keys = [...kv.keys()].filter((k) => k.startsWith(prefix + '.') && k.endsWith('.name'));
console.log(`${prefix}.*.name 共 ${keys.length} 个键\n`);

/** 把键名里的"具体词"换成占位符，抽出模式。
 *  具体词 = 颜色名、木种名（这些是枚举值，不是 ID 的一部分）。 */
const COLORS = new Set(['white','orange','magenta','light_blue','yellow','lime','pink','gray','silver','cyan','purple','blue','brown','green','red','black','light_gray']);
const WOODS = new Set(['oak','spruce','birch','jungle','acacia','dark_oak','darkoak','mangrove','cherry','bamboo','crimson','warped','pale_oak','poplar']);

const patterns = new Map();
for (const k of keys) {
  const mid = k.slice(prefix.length + 1, -'.name'.length);
  const segs = mid.split('.');
  const pat = segs
    .map((s) => (COLORS.has(s) ? '<色>' : WOODS.has(s) ? '<木>' : s))
    .join('.');
  if (!patterns.has(pat)) patterns.set(pat, []);
  patterns.get(pat).push(k);
}

console.log('=== 键模式（段数 >1 的才是"ID 与译名键不一致"的嫌疑）===');
[...patterns.entries()].sort((a, b) => b[1].length - a[1].length).forEach(([pat, list]) => {
  const dots = (pat.match(/\./g) ?? []).length;
  const mark = dots > 0 ? '  ← 多段，需映射' : '';
  console.log(`  ${String(list.length).padStart(4)}  ${prefix}.${pat}.name${mark}`);
  if (dots > 0) console.log(`          例: ${list[0]} = ${kv.get(list[0])}`);
});
