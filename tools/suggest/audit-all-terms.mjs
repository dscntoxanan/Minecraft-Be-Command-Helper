// 词表综合审计：用**三份官方数据源**交叉核对，产出可信的「缺失清单」
//
// 三个源：
//   A. Mojang 官方清单（Microsoft Learn / minecraft-creator 仓库）
//        VanillaListingsReference/Items.md     —— 官方认可的物品 ID
//        VanillaListingsReference/Blocks.md    —— 官方认可的方块 ID
//        VanillaListingsReference/Entities.md  —— 官方认可的实体 ID
//   B. bedrock-samples 的 zh_CN.lang：`item.<id>.name` / `tile.<id>.name` / `entity.<id>.name`
//        —— 官方中文译名（这是**译名的唯一权威来源**）
//   C. bedrock-samples 资源包贴图文件名（可选，只作辅助佐证）
//
// 为什么必须三源：
//   * 只用 A：A 的清单里有些条目 zh_CN.lang 没有译名（新内容），也有 A 不收但我们能用的 ID；
//   * 只用 B：B 里混着合成键（banner/spawn_egg 变体），且无法判断 ID 是否真能用；
//   * 只用 C：材质包可能被精简过（本项目的 Be原版材质包就是精简版），会漏。
//   取 **A ∩ B**（两边都有）当"铁定该收录"，A 或 B 单边有的另外列出来人工判断。
//
// 用法: node audit-all-terms.mjs
import { readFileSync, writeFileSync, existsSync } from 'node:fs';
import path from 'node:path';

const here = path.dirname(new URL(import.meta.url).pathname.replace(/^\/([A-Za-z]:)/, '$1'));
const RAW = 'https://raw.githubusercontent.com/MicrosoftDocs/minecraft-creator/main/creator/Reference/Content/VanillaListingsReference';

async function fetchText(url, tries = 4) {
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

/** 官方清单是 `| minecraft:<id> | states |` 的表格。 */
function parseListing(text, label) {
  const ids = new Set();
  for (const line of text.split(/\r?\n/)) {
    if (!line.startsWith('|')) continue;
    const m = /^\|\s*(?:minecraft:)?([a-z0-9_]+)\s*\|/.exec(line);
    if (!m) continue;
    const id = m[1];
    if (id === 'Name' || id.startsWith('-')) continue;
    ids.add(id);
  }
  console.log(`  官方 ${label} 清单：${ids.size} 个 ID`);
  return ids;
}

// ── 语言文件 ──────────────────────────────────────────────────────────────────
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

// ── 主流程 ────────────────────────────────────────────────────────────────────
console.log('拉取官方清单 …');
const [itemsMd, blocksMd, entitiesMd] = await Promise.all([
  fetchText(`${RAW}/Items.md`),
  fetchText(`${RAW}/Blocks.md`),
  fetchText(`${RAW}/Entities.md`),
]);
const officialItems = parseListing(itemsMd, 'Items');
const officialBlocks = parseListing(blocksMd, 'Blocks');
const officialEntities = parseListing(entitiesMd, 'Entities');

const lang = parseLang(path.join(here, 'zh_CN.lang'));
const langItems = langIds(lang, 'item');
const langBlocks = langIds(lang, 'tile');
const langEntities = langIds(lang, 'entity');
console.log(`  语言文件：item=${langItems.size} tile=${langBlocks.size} entity=${langEntities.size}`);

const ours = {
  items: readCsv(path.join(here, 'items.csv')),
  blocks: readCsv(path.join(here, 'blocks.csv')),
  entities: readCsv(path.join(here, 'entities.csv')),
};
console.log(`  我们的 CSV：item=${ours.items.size} block=${ours.blocks.size} entity=${ours.entities.size}`);

const lines = [];
const say = (s) => { console.log(s); lines.push(s); };

const CASES = [
  { label: 'items', official: officialItems, lang: langItems, our: ours.items },
  { label: 'blocks', official: officialBlocks, lang: langBlocks, our: ours.blocks },
  { label: 'entities', official: officialEntities, lang: langEntities, our: ours.entities },
];

const summary = [];
for (const c of CASES) {
  const both = [...c.official].filter((id) => c.lang.has(id));        // A∩B：铁定该收录
  const onlyOfficial = [...c.official].filter((id) => !c.lang.has(id)); // 只有官方清单有
  const onlyLang = [...c.lang.keys()].filter((id) => !c.official.has(id)); // 只有译名有

  const missingBoth = both.filter((id) => !c.our.has(id));
  const missingOnlyOfficial = onlyOfficial.filter((id) => !c.our.has(id));
  const missingOnlyLang = onlyLang.filter((id) => !c.our.has(id));

  summary.push({ label: c.label, both: both.length, onlyOfficial: onlyOfficial.length, onlyLang: onlyLang.length, missingBoth: missingBoth.length });

  say(`\n${'='.repeat(72)}`);
  say(`${c.label}：官方清单 ${c.official.size}  译名 ${c.lang.size}  我们有 ${c.our.size}`);
  say(`  A∩B（两边都有，铁定该收录）${both.length}  仅官方清单 ${onlyOfficial.length}  仅译名 ${onlyLang.length}`);
  say(`  → 缺失：A∩B 里缺 ${missingBoth.length} / 仅官方缺 ${missingOnlyOfficial.length} / 仅译名缺 ${missingOnlyLang.length}`);
  say(`${'='.repeat(72)}`);

  if (missingBoth.length) {
    say(`\n【必补】A∩B 但缺失（${missingBoth.length}）—— 官方认可 + 有官方译名，没有任何理由不收：`);
    missingBoth.forEach((id) => say(`    ${id} = ${c.lang.get(id)}`));
  }
  if (missingOnlyOfficial.length) {
    say(`\n【待判断】只有官方清单、zh_CN.lang 没有译名的（${missingOnlyOfficial.length}）—— 多半是新内容，需要补译名：`);
    missingOnlyOfficial.forEach((id) => say(`    ${id}`));
  }
  if (missingOnlyLang.length) {
    say(`\n【待判断】只有译名、官方清单没列的（${missingOnlyLang.length}）—— 可能是变体键或已废弃，逐条看：`);
    missingOnlyLang.slice(0, 80).forEach((id) => say(`    ${id} = ${c.lang.get(id)}`));
    if (missingOnlyLang.length > 80) say(`    …还有 ${missingOnlyLang.length - 80} 条`);
  }
}

say(`\n\n${'#'.repeat(72)}\n# 汇总\n${'#'.repeat(72)}`);
summary.forEach((s) => say(`  ${s.label.padEnd(10)} A∩B ${String(s.both).padStart(5)}  仅官方 ${String(s.onlyOfficial).padStart(5)}  仅译名 ${String(s.onlyLang).padStart(5)}  必补 ${s.missingBoth}`));

const dst = path.join(here, 'audit-all-terms-report.txt');
writeFileSync(dst, lines.join('\n'), 'utf8');
console.log(`\n报告已写入 ${dst}`);
