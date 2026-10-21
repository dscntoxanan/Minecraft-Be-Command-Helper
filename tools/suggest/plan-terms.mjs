// 用"官方清单 ∩ 官方译名"算出**精确缺失清单**，并生成可直接并入 CSV 的行
//
// 甄别规则（依据官方两份清单的关系，实测得出）：
//   * 官方 Items.md 里**同时**出现在 Blocks.md 的 ID 是方块（1361 个），不算物品；
//   * 只有 Items.md 有、Blocks.md 没有的才是**真物品**（530 个）；
//   * 官方 Entities.md 只有 15 个"实体类别"（不是实体 ID），所以实体以 zh_CN.lang 的
//     `entity.<id>.name` 为准 —— 这也是当初补实体类型时的结论。
//
// 用法: node plan-terms.mjs
// 输出: terms-plan.json（每类的"必补"行，可直接写回 CSV）
import { readFileSync, writeFileSync, existsSync } from 'node:fs';
import path from 'node:path';

const here = path.dirname(new URL(import.meta.url).pathname.replace(/^\/([A-Za-z]:)/, '$1'));
const RAW = 'https://raw.githubusercontent.com/MicrosoftDocs/minecraft-creator/main/creator/Reference/Content/VanillaListingsReference';

async function fetchText(url, tries = 5) {
  let last;
  for (let i = 1; i <= tries; i++) {
    try {
      const r = await fetch(url, { headers: { 'User-Agent': 'cmdhelper-audit' } });
      if (!r.ok) throw new Error(`HTTP ${r.status}`);
      return await r.text();
    } catch (e) {
      last = e;
      if (i < tries) await new Promise((r) => setTimeout(r, 700 * i));
    }
  }
  throw last;
}

function parseListing(text) {
  const ids = new Set();
  for (const line of text.split(/\r?\n/)) {
    if (!line.startsWith('|')) continue;
    const m = /^\|\s*(?:minecraft:)?([a-z0-9_]+)\s*\|/.exec(line);
    if (!m) continue;
    const id = m[1];
    if (id === 'Name' || id.startsWith('-')) continue;
    ids.add(id);
  }
  return ids;
}

function parseLang(file) {
  const m = new Map();
  for (const line of readFileSync(file, 'utf8').replace(/^\uFEFF/, '').split(/\r?\n/)) {
    if (!line || line.startsWith('#')) continue;
    const i = line.indexOf('=');
    if (i > 0) m.set(line.slice(0, i).trim(), line.slice(i + 1).trim());
  }
  return m;
}
const langIds = (lang, prefix) => {
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

console.log('拉取官方清单 …');
const [itemsMd, blocksMd] = await Promise.all([
  fetchText(`${RAW}/Items.md`),
  fetchText(`${RAW}/Blocks.md`),
]);
const officialItemsAll = parseListing(itemsMd);
const officialBlocks = parseListing(blocksMd);
// 真物品 = Items.md 有、Blocks.md 没有
const officialItems = new Set([...officialItemsAll].filter((id) => !officialBlocks.has(id)));
console.log(`  Items.md=${officialItemsAll.size}  Blocks.md=${officialBlocks.size}  → 真物品=${officialItems.size}`);

const lang = parseLang(path.join(here, 'zh_CN.lang'));
const langItems = langIds(lang, 'item');
const langBlocks = langIds(lang, 'tile');
const langEntities = langIds(lang, 'entity');

const ours = {
  items: readCsv(path.join(here, 'items.csv')),
  blocks: readCsv(path.join(here, 'blocks.csv')),
  entities: readCsv(path.join(here, 'entities.csv')),
};

const plan = {};
const report = [];
const say = (s) => { console.log(s); report.push(s); };

// ── items ─────────────────────────────────────────────────────────────────────
{
  // 官方认可 + 有官方译名 = 必补
  const must = [...officialItems].filter((id) => langItems.has(id) && !ours.items.has(id)).sort();
  // 只有译名、官方清单没有：可能是变体或我们已有的其它叫法
  const langOnly = [...langItems.keys()].filter((id) => !officialItems.has(id) && !ours.items.has(id)).sort();
  // 只有官方清单、没有译名：新内容，需要人工译名
  const noName = [...officialItems].filter((id) => !langItems.has(id) && !ours.items.has(id)).sort();

  plan.items = { must: must.map((id) => [id, langItems.get(id)]), langOnly, noName };
  say(`\n=== items：必补 ${must.length} / 仅译名 ${langOnly.length} / 无译名 ${noName.length}`);
  say('\n【必补】官方清单 ∩ 官方译名，但我们的 CSV 里没有：');
  must.forEach((id) => say(`    ${id} = ${langItems.get(id)}`));
  if (langOnly.length) {
    say(`\n【仅译名有】${langOnly.length} 条（官方 Items.md 没列，逐条判断是否需要）：`);
    langOnly.forEach((id) => say(`    ${id} = ${langItems.get(id)}`));
  }
  if (noName.length) {
    say(`\n【无官方译名】${noName.length} 条（官方清单有、译名文件没有；多半是刷怪蛋/变体，也可能是新内容）：`);
    noName.forEach((id) => say(`    ${id}`));
  }
}

// ── blocks ────────────────────────────────────────────────────────────────────
{
  const must = [...officialBlocks].filter((id) => langBlocks.has(id) && !ours.blocks.has(id)).sort();
  const langOnly = [...langBlocks.keys()].filter((id) => !officialBlocks.has(id) && !ours.blocks.has(id)).sort();
  const noName = [...officialBlocks].filter((id) => !langBlocks.has(id) && !ours.blocks.has(id)).sort();

  plan.blocks = { must: must.map((id) => [id, langBlocks.get(id)]), langOnly, noName };
  say(`\n=== blocks：必补 ${must.length} / 仅译名 ${langOnly.length} / 无译名 ${noName.length}`);
  say('\n【必补】官方清单 ∩ 官方译名，但我们的 CSV 里没有：');
  must.forEach((id) => say(`    ${id} = ${langBlocks.get(id)}`));
  if (langOnly.length) {
    say(`\n【仅译名有】${langOnly.length} 条：`);
    langOnly.forEach((id) => say(`    ${id} = ${langBlocks.get(id)}`));
  }
  if (noName.length) {
    say(`\n【无官方译名】${noName.length} 条：`);
    noName.forEach((id) => say(`    ${id}`));
  }
}

// ── entities（以译名为准，官方 Entities.md 不是实体 ID 清单）────────────────────
{
  const must = [...langEntities.keys()].filter((id) => !ours.entities.has(id)).sort();
  plan.entities = { must: must.map((id) => [id, langEntities.get(id)]) };
  say(`\n=== entities：以 zh_CN.lang 为准，缺失 ${must.length}`);
  must.forEach((id) => say(`    ${id} = ${langEntities.get(id)}`));
}

writeFileSync(path.join(here, 'terms-plan.json'), JSON.stringify(plan, null, 1), 'utf8');
const dst = path.join(here, 'terms-plan-report.txt');
writeFileSync(dst, report.join('\n'), 'utf8');
console.log(`\n计划已写入 terms-plan.json / ${path.basename(dst)}`);
