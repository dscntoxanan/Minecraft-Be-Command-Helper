// 从官方 zh_CN.lang **重建** items.csv / blocks.csv
//
// ── 为什么要有这个脚本 ──────────────────────────────────────────────────────
// 老的 `extract.ps1` 只认 `^item\.<id>\.name$` 且显式排除含点的 id，
// 于是语言文件里**点分键那一大批整族没进词表**：
//
//     tile.wool.black.name     = 黑色羊毛    →  black_wool
//     tile.planks.acacia.name  = 金合欢木板  →  acacia_planks
//     item.boat.oak.name       = 橡木船      →  oak_boat
//     tile.log.acacia.name     = 金合欢原木  →  acacia_log
//
// 实测这些全都不在词表里（`white_wool`、`oak_log`、`acacia_planks`、所有船）——
// 玩家搜「羊毛」「原木」一条都搜不到。这不是取舍，是覆盖度上的洞。
//
// ── 为什么是"合并"而不是"替换" ──────────────────────────────────────────────
// 映射器只覆盖**有译名键**的 ID；语言文件里还有一批**平直键**或压根没有键的真方块
// （`air`、`bell`、`cake`、`flower_pot`、各色门…，它们的名字来自别处）。
// 直接替换会把这 37 条丢掉，所以策略是：
//
//     新表 = 映射器产出（点分键那批的修复）
//          ∪ 现有 CSV 里还没被覆盖的条目（保住平直键与手工补录）
//
// 两边都来自官方数据，取并集只增不减，可反复运行（幂等）。
//
// 用法: node build-terms.mjs [--dry]
import { readFileSync, writeFileSync, existsSync } from 'node:fs';
import path from 'node:path';
import { mapKeys } from './lang-key-map.mjs';

const here = path.dirname(new URL(import.meta.url).pathname.replace(/^\/([A-Za-z]:)/, '$1'));
const dry = process.argv.includes('--dry');

// ── 读语言文件 ────────────────────────────────────────────────────────────────
const entries = [];
for (const line of readFileSync(path.join(here, 'zh_CN.lang'), 'utf8').replace(/^\uFEFF/, '').split(/\r?\n/)) {
  if (!line || line.startsWith('#')) continue;
  const i = line.indexOf('=');
  if (i > 0) entries.push([line.slice(0, i).trim(), line.slice(i + 1).trim()]);
}

const { mapped, unmapped } = mapKeys(entries);
const fromLang = { blocks: new Map(), items: new Map() };
for (const [id, v] of mapped) {
  (v.kind === 'block' ? fromLang.blocks : fromLang.items).set(id, v.name);
}

// ── 读现有 CSV（作为"平直键与手工补录"的来源）────────────────────────────────
function readCsv(file) {
  const p = path.join(here, file);
  if (!existsSync(p)) return new Map();
  const out = new Map();
  for (const line of readFileSync(p, 'utf8').replace(/^\uFEFF/, '').split(/\r?\n/).slice(1)) {
    if (!line) continue;
    const m = /^"((?:[^"]|"")*)","((?:[^"]|"")*)"/.exec(line);
    if (m) out.set(m[1].replace(/""/g, '"'), m[2].replace(/""/g, '"'));
  }
  return out;
}
const prev = { items: readCsv('items.csv'), blocks: readCsv('blocks.csv') };

// ── 不收录的 ──────────────────────────────────────────────────────────────────
//
// 只有"**这个 ID 不是 /give、/setblock 能用的实体 ID**"才排除。
// 老 JUNK 规则排得太多，实测误杀了一批合法内容，所以逐条收紧：
//
//   老规则              问题                                  处置
//   ------------------  ------------------------------------  ----------------------
//   ^pale_              本意排苍白垂藤，却把**整个苍白橡木族** 改成精确匹配
//                       （门/树苗/树叶/木板/台阶…）全排掉了     pale_hanging_moss
//   _egg$               本意排刷怪蛋，把 dragon_egg /          改成只排 spawn_egg
//                       turtle_egg / sniffer_egg 也排了
//   *_sign / *_banner   告示牌与旗帜是能 /give 的物品          不再排除
//   *_pottery_sherd     陶片是能 /give 的物品                  不再排除
//   *_smithing_template 盔甲纹饰是能 /give 的物品              不再排除
//   *_boat / _raft      船是能 /give 的物品                    不再排除
//   record_ / 唱片      唱片是能 /give 的物品                  不再排除
//
// 仍然排除的：
const EXCLUDE = new RegExp([
  // 教育版化学（要教育版才能用）
  '^element_', '^chemical', '^compound$', '^bleach$', '^hard_glass', '^hard_stained',
  // 纯 UI / 内部占位
  '^allow$', 'placeholder', '^client_request',
  '^board$', '^chalkboard$', '^camera$', '^glow_stick$', '^balloon$', '^sparkler$', '^ice_bomb$',
  // 已被现代 ID 取代的旧 ID（在现在的游戏里 setblock 它们没有意义）
  '^hardened_clay$', '^stained_hardened_clay$', '^wooden_door$', '^leaves$', '^reeds$',
].join('|'));

// ⚠️ **刷怪蛋不排除**。一开始想排掉（94 个，看着像噪音），但它们
//    * 是官方 Items.md 认可的 ID（`zombie_spawn_egg` 等），能 /give；
//    * 带官方中文名（"僵尸生成蛋"），玩家搜「僵尸」本来也该看到它。
//    排序上的噪音靠"按输入过滤"解决，不该在数据层砍掉。

const csvCell = (s) => '"' + String(s).replace(/"/g, '""') + '"';

function build(label, langMap, prevMap) {
  const merged = new Map(langMap);
  let carried = 0;
  for (const [id, name] of prevMap) {
    if (merged.has(id)) continue;
    if (EXCLUDE.test(id)) continue;
    merged.set(id, name);
    carried++;
  }
  const dropped = [];
  for (const id of [...merged.keys()]) {
    if (EXCLUDE.test(id)) { merged.delete(id); dropped.push(id); }
  }
  console.log(
    `  ${label}: 语言映射 ${langMap.size} + 沿用旧表 ${carried} − 排除 ${dropped.length}` +
    ` = ${merged.size}`,
  );
  if (dropped.length) {
    console.log(`      排除：${dropped.slice(0, 8).join(', ')}${dropped.length > 8 ? ' …' : ''}`);
  }
  return merged;
}

console.log('构建词表（语言文件映射 ∪ 现有 CSV）：');
const items = build('items ', fromLang.items, prev.items);
const blocks = build('blocks', fromLang.blocks, prev.blocks);
console.log(`\n未映射的语言键 ${unmapped.length} 个（合成键/变体，不需要收录）`);

writeCsv('items.csv', items);
writeCsv('blocks.csv', blocks);
console.log('\n完成');

function writeCsv(file, map) {
  const out = ['"id","name","inPack"'];
  for (const id of [...map.keys()].sort()) {
    out.push([csvCell(id), csvCell(map.get(id)), 'False'].join(','));
  }
  if (dry) {
    console.log(`  [dry] ${file} 将写入 ${map.size} 条`);
  } else {
    writeFileSync(path.join(here, file), out.join('\n') + '\n', 'utf8');
    console.log(`  已写 ${file}（${map.size} 条）`);
  }
}
