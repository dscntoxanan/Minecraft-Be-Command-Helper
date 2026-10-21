// 用「官方清单 ∩ 译名映射」算出最终该收录的 ID 全集，并与现有 CSV 对比
// 用法: node diff-mapped.mjs
import { readFileSync, existsSync, writeFileSync } from 'node:fs';
import path from 'node:path';
import { mapKeys } from './lang-key-map.mjs';

const here = path.dirname(new URL(import.meta.url).pathname.replace(/^\/([A-Za-z]:)/, '$1'));
const RAW = 'https://raw.githubusercontent.com/MicrosoftDocs/minecraft-creator/main/creator/Reference/Content/VanillaListingsReference';

async function fetchText(url, tries = 5) {
  let last;
  for (let i = 1; i <= tries; i++) {
    try {
      const r = await fetch(url, { headers: { 'User-Agent': 'cmdhelper-audit' } });
      if (!r.ok) throw new Error(`HTTP ${r.status}`);
      return await r.text();
    } catch (e) { last = e; if (i < tries) await new Promise((x) => setTimeout(x, 700 * i)); }
  }
  throw last;
}
function parseListing(text) {
  const ids = new Set();
  for (const line of text.split(/\r?\n/)) {
    if (!line.startsWith('|')) continue;
    const m = /^\|\s*(?:minecraft:)?([a-z0-9_]+)\s*\|/.exec(line);
    if (!m || m[1] === 'Name' || m[1].startsWith('-')) continue;
    ids.add(m[1]);
  }
  return ids;
}
function readCsv(file) {
  if (!existsSync(file)) return new Map();
  const lines = readFileSync(file, 'utf8').replace(/^\uFEFF/, '').split(/\r?\n/).filter(Boolean);
  const out = new Map();
  for (const line of lines.slice(1)) {
    const m = /^"((?:[^"]|"")*)","((?:[^"]|"")*)"/.exec(line);
    if (m) out.set(m[1].replace(/""/g, '"'), m[2].replace(/""/g, '"'));
  }
  return out;
}

// ── 语言文件 → 键值对 ─────────────────────────────────────────────────────────
const langEntries = [];
for (const line of readFileSync(path.join(here, 'zh_CN.lang'), 'utf8').replace(/^\uFEFF/, '').split(/\r?\n/)) {
  if (!line || line.startsWith('#')) continue;
  const i = line.indexOf('=');
  if (i > 0) langEntries.push([line.slice(0, i).trim(), line.slice(i + 1).trim()]);
}

const { mapped, unmapped } = mapKeys(langEntries);
console.log(`语言文件映射出 ${mapped.size} 个 ID（其中方块 ${[...mapped.values()].filter((v) => v.kind === 'block').length}，物品 ${[...mapped.values()].filter((v) => v.kind === 'item').length}）`);
console.log(`未能映射的键 ${unmapped.length} 个`);

console.log('\n拉取官方清单 …');
const [itemsMd, blocksMd] = await Promise.all([fetchText(`${RAW}/Items.md`), fetchText(`${RAW}/Blocks.md`)]);
const officialAllItems = parseListing(itemsMd);
const officialBlocks = parseListing(blocksMd);
const officialItems = new Set([...officialAllItems].filter((id) => !officialBlocks.has(id)));

const ours = { items: readCsv(path.join(here, 'items.csv')), blocks: readCsv(path.join(here, 'blocks.csv')) };

const out = [];
const say = (s) => { console.log(s); out.push(s); };

// ── 方块：官方清单 ∩ 映射译名 = 该收录，减去已有 ────────────────────────────────
const blockIds = [...mapped.entries()].filter(([, v]) => v.kind === 'block').map(([id]) => id);
const blockMust = blockIds.filter((id) => officialBlocks.has(id) && !ours.blocks.has(id)).sort();
const blockNotOfficial = blockIds.filter((id) => !officialBlocks.has(id) && !ours.blocks.has(id)).sort();

// ── 物品：官方真物品 ∩ 映射译名 ────────────────────────────────────────────────
const itemIds = [...mapped.entries()].filter(([, v]) => v.kind === 'item').map(([id]) => id);
const itemMust = itemIds.filter((id) => officialItems.has(id) && !ours.items.has(id)).sort();
const itemNotOfficial = itemIds.filter((id) => !officialItems.has(id) && !ours.items.has(id)).sort();

say(`\n=== 与官方清单交叉后 ===`);
say(`blocks：映射出 ${blockIds.length} 个，官方清单认可且我们缺 ${blockMust.length}，官方清单不认 ${blockNotOfficial.length}`);
say(`items ：映射出 ${itemIds.length} 个，官方清单认可且我们缺 ${itemMust.length}，官方清单不认 ${itemNotOfficial.length}`);

say(`\n=== blocks 必补（${blockMust.length}）===`);
blockMust.forEach((id) => say(`  ${id} = ${mapped.get(id).name}`));
say(`\n=== items 必补（${itemMust.length}）===`);
itemMust.forEach((id) => say(`  ${id} = ${mapped.get(id).name}`));

if (blockNotOfficial.length) {
  say(`\n=== blocks：映射到了但官方 Blocks.md 没有（${blockNotOfficial.length}）—— 需人工判断 ===`);
  blockNotOfficial.forEach((id) => say(`  ${id} = ${mapped.get(id).name}   [键 ${mapped.get(id).key}]`));
}
if (itemNotOfficial.length) {
  say(`\n=== items：映射到了但官方 Items.md 没有（${itemNotOfficial.length}）===`);
  itemNotOfficial.forEach((id) => say(`  ${id} = ${mapped.get(id).name}   [键 ${mapped.get(id).key}]`));
}
if (unmapped.length) {
  say(`\n=== 未能映射的键（${unmapped.length}）===`);
  unmapped.forEach((u) => say(`  ${u.key} = ${u.name}`));
}

const plan = {
  blocksMust: blockMust.map((id) => [id, mapped.get(id).name]),
  itemsMust: itemMust.map((id) => [id, mapped.get(id).name]),
  blocksNotOfficial: blockNotOfficial.map((id) => [id, mapped.get(id).name]),
  itemsNotOfficial: itemNotOfficial.map((id) => [id, mapped.get(id).name]),
  unmapped,
};
writeFileSync(path.join(here, 'mapped-plan.json'), JSON.stringify(plan, null, 1), 'utf8');
writeFileSync(path.join(here, 'mapped-plan-report.txt'), out.join('\n'), 'utf8');
console.log('\n计划写入 mapped-plan.json / mapped-plan-report.txt');
