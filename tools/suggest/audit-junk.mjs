// 判定 JUNK 过滤掉的条目里，哪些其实是**合法内容**（该收进词表）
//
// 判据（全部来自官方数据，不靠印象）：
//   1. 官方资源包 textures/items|blocks/<id>.png 存在  → 这个 ID 是真实存在的方块/物品
//   2. zh_CN.lang 有官方中文译名
//   3. 它是不是"能用在 /give 或 /setblock 里的 ID"（而不是纯 UI/变体字符串）
//
// 用法: node audit-junk.mjs
import { readFileSync, existsSync } from 'node:fs';
import path from 'node:path';

const here = path.dirname(new URL(import.meta.url).pathname.replace(/^\/([A-Za-z]:)/, '$1'));
const pack = JSON.parse(readFileSync(path.join(here, 'pack-ids.json'), 'utf8'));
const itemTex = new Set(pack['items-textures']);
const blockTex = new Set(pack['blocks-textures']);
const itemDef = new Set(pack['items-definitions']);

const LANG = path.join(here, 'zh_CN.lang');
const lang = new Map();
for (const line of readFileSync(LANG, 'utf8').replace(/^\uFEFF/, '').split(/\r?\n/)) {
  if (!line || line.startsWith('#')) continue;
  const i = line.indexOf('=');
  if (i > 0) lang.set(line.slice(0, i).trim(), line.slice(i + 1).trim());
}
const langIds = (prefix) => {
  const out = new Map();
  const re = new RegExp(`^${prefix}\\.(.+)\\.name$`);
  for (const [k, v] of lang) {
    const m = re.exec(k);
    if (!m) continue;
    const id = m[1];
    if (id.includes('.') || /[A-Z]/.test(id) || !v) continue;
    out.set(id, v);
  }
  return out;
};

/** extract.ps1 里那条 JUNK 规则，逐条拆开，便于看出"是哪一条命中的"。 */
const JUNK_RULES = [
  ['spawn_egg', /spawn_egg/],
  ['_egg$', /_egg$/],
  ['^boat$', /^boat$/],
  ['_boat$', /_boat$/],
  ['_raft$', /_raft$/],
  ['^bed$', /^bed$/],
  ['_bed$', /_bed$/],
  ['_sign$', /_sign$/],
  ['_hanging_sign$', /_hanging_sign$/],
  ['banner_pattern', /banner_pattern/],
  ['_banner$', /_banner$/],
  ['_trim', /_trim/],
  ['_smithing_template', /_smithing_template/],
  ['_pottery_sherd', /_pottery_sherd/],
  ['^potion$', /^potion$/],
  ['_potion$', /_potion$/],
  ['^tipped_arrow$', /^tipped_arrow$/],
  ['record_', /record_/],
  ['_music_disc', /_music_disc/],
  ['axolotl', /axolotl/],
  ['^pale_', /^pale_/],
  ['^light_block', /^light_block/],
  ['_bundle$', /_bundle$/],
  ['^bundle', /^bundle/],
];

const ourCsv = (f, isSoundLike = false) => {
  const p = path.join(here, f);
  if (!existsSync(p)) return new Map();
  const lines = readFileSync(p, 'utf8').replace(/^\uFEFF/, '').split(/\r?\n/).filter(Boolean);
  const out = new Map();
  for (const line of lines.slice(1)) {
    if (isSoundLike) {
      const c = line.indexOf(',');
      if (c > 0) out.set(line.slice(0, c), line.slice(c + 1, line.indexOf(',', c + 1)));
    } else {
      const m = /^"((?:[^"]|"")*)","((?:[^"]|"")*)"/.exec(line);
      if (m) out.set(m[1].replace(/""/g, '"'), m[2].replace(/""/g, '"'));
    }
  }
  return out;
};

const categories = [
  { label: 'items', lang: langIds('item'), ours: ourCsv('items.csv'), tex: itemTex },
  { label: 'blocks', lang: langIds('tile'), ours: ourCsv('blocks.csv'), tex: blockTex },
];

for (const cat of categories) {
  const missing = [...cat.lang.keys()].filter((k) => !cat.ours.has(k));
  console.log(`\n${'='.repeat(70)}\n${cat.label}：被过滤 ${missing.length} 条\n${'='.repeat(70)}`);

  // 按"命中了哪条规则"分组
  const byRule = new Map();
  const noRule = [];
  for (const id of missing) {
    const hit = JUNK_RULES.filter(([, re]) => re.test(id)).map(([n]) => n);
    if (hit.length === 0) { noRule.push(id); continue; }
    const key = hit.join(' + ');
    if (!byRule.has(key)) byRule.set(key, []);
    byRule.get(key).push(id);
  }

  for (const [rule, ids] of [...byRule.entries()].sort((a, b) => b[1].length - a[1].length)) {
    const inTex = ids.filter((i) => cat.tex.has(i));
    const notInTex = ids.filter((i) => !cat.tex.has(i));
    console.log(`\n--- 命中规则「${rule}」共 ${ids.length} 条`);
    console.log(`    官方贴图里**存在**（=> 是真 ID，应当收录）：${inTex.length} 条`);
    if (inTex.length) console.log(`      ${inTex.join(', ')}`);
    if (notInTex.length) {
      console.log(`    官方贴图里**没有**（=> 可能不是可直接用的 ID）：${notInTex.length} 条`);
      console.log(`      ${notInTex.join(', ')}`);
    }
  }
  if (noRule.length) {
    console.log(`\n--- 没命中任何规则（不该被过滤！）${noRule.length} 条：${noRule.join(', ')}`);
  }
}
