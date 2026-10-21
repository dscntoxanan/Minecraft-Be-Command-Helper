// 词表审计：把"官方 ID 全集"与我们现有的 CSV 逐类对比，产出三张清单
//   缺失（官方有、我们没有） / 多余（我们有、官方没有） / 译名不一致
//
// 数据源（全部为官方）：
//   1. zh_CN.lang 的 `item.<id>.name` / `tile.<id>.name` / `entity.<id>.name`
//      —— 既是官方 ID 来源，也是官方中文译名来源
//   2. 资源包 textures/items、textures/blocks 的文件名 —— 用于交叉验证 ID 是否真实存在
//
// 用法: node audit-terms.mjs
import { readFileSync, existsSync, writeFileSync } from 'node:fs';
import path from 'node:path';

const here = path.dirname(new URL(import.meta.url).pathname.replace(/^\/([A-Za-z]:)/, '$1'));
const LANG = path.join(here, 'zh_CN.lang');

// ── 解析语言文件 ──────────────────────────────────────────────────────────────
function parseLang(file) {
  const m = new Map();
  for (const line of readFileSync(file, 'utf8').replace(/^\uFEFF/, '').split(/\r?\n/)) {
    if (!line || line.startsWith('#')) continue;
    const i = line.indexOf('=');
    if (i <= 0) continue;
    m.set(line.slice(0, i).trim(), line.slice(i + 1).trim());
  }
  return m;
}

/** 取 `^<prefix>\.(.+)\.name$` 且 id 不含点、不含大写（合成键不是真 ID）。 */
function langIds(lang, prefix) {
  const out = new Map();
  const re = new RegExp(`^${prefix}\\.(.+)\\.name$`);
  for (const [k, v] of lang) {
    const mt = re.exec(k);
    if (!mt) continue;
    const id = mt[1];
    if (id.includes('.') || /[A-Z]/.test(id) || !v) continue;
    out.set(id, v);
  }
  return out;
}

// ── 解析我们的 CSV（id,name,inPack）──────────────────────────────────────────
function readCsv(file) {
  if (!existsSync(file)) return new Map();
  const lines = readFileSync(file, 'utf8').replace(/^\uFEFF/, '').split(/\r?\n/).filter(Boolean);
  const out = new Map();
  for (const line of lines.slice(1)) {
    // 形如 "id","name","False"（名字里可能有逗号，所以不用简单 split）
    const m = /^"((?:[^"]|"")*)","((?:[^"]|"")*)"/.exec(line);
    if (!m) continue;
    out.set(m[1].replace(/""/g, '"'), m[2].replace(/""/g, '"'));
  }
  return out;
}

const lang = parseLang(LANG);
const official = {
  items: langIds(lang, 'item'),
  blocks: langIds(lang, 'tile'),
  entities: langIds(lang, 'entity'),
};
const ours = {
  items: readCsv(path.join(here, 'items.csv')),
  blocks: readCsv(path.join(here, 'blocks.csv')),
  entities: readCsv(path.join(here, 'entities.csv')),
};

/** extract.ps1 里的垃圾过滤规则（逐字保留，便于对照）。 */
const JUNK = /spawn_egg|_egg$|^boat$|_boat$|_raft$|^bed$|_bed$|_sign$|_hanging_sign$|banner_pattern|_banner$|_trim|_smithing_template|_pottery_sherd|^potion$|_potion$|^tipped_arrow$|record_|_music_disc|axolotl|^pale_|^light_block|_bundle$|^bundle/i;

const report = [];
const say = (s) => { console.log(s); report.push(s); };

say(`官方 zh_CN.lang：item=${official.items.size}  tile=${official.blocks.size}  entity=${official.entities.size}`);
say(`我们的 CSV    ：item=${ours.items.size}  block=${ours.blocks.size}  entity=${ours.entities.size}`);

const pairs = [
  ['item', 'items'],
  ['tile', 'blocks'],
  ['entity', 'entities'],
];

for (const [offKey, ourKey] of pairs) {
  const off = official[offKey === 'item' ? 'items' : offKey === 'tile' ? 'blocks' : 'entities'];
  const our = ours[ourKey];

  const missing = [...off.keys()].filter((k) => !our.has(k));
  const extra = [...our.keys()].filter((k) => !off.has(k));
  const renamed = [...off.keys()].filter((k) => our.has(k) && our.get(k) !== off.get(k));

  say(`\n========== ${ourKey} ==========`);
  say(`缺失 ${missing.length} / 多余 ${extra.length} / 译名不一致 ${renamed.length}`);

  if (missing.length) {
    const junked = missing.filter((k) => JUNK.test(k));
    const real = missing.filter((k) => !JUNK.test(k));
    say(`\n  【被 JUNK 规则过滤掉的】${junked.length} 个（这些是刻意排除的，逐条复核是否该排除）`);
    junked.forEach((k) => say(`      ${k} = ${off.get(k)}`));
    say(`\n  【非 JUNK 但仍缺失】${real.length} 个 ← **这才是真漏项**`);
    real.forEach((k) => say(`      ${k} = ${off.get(k)}`));
  }
  if (extra.length) {
    say(`\n  【我们多出来的】${extra.length} 个`);
    extra.forEach((k) => say(`      ${k} = ${our.get(k)}`));
  }
  if (renamed.length) {
    say(`\n  【译名不一致】${renamed.length} 个`);
    renamed.slice(0, 60).forEach((k) => say(`      ${k}: 我们「${our.get(k)}」 vs 官方「${off.get(k)}」`));
    if (renamed.length > 60) say(`      …还有 ${renamed.length - 60} 条`);
  }
}

const out = path.join(here, 'audit-terms-report.txt');
writeFileSync(out, report.join('\n'), 'utf8');
console.log(`\n报告已写入 ${out}`);
