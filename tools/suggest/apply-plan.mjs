// 把「官方清单 ∩ 译名映射」里我们缺的条目，补录进 items.csv / blocks.csv
//
// 这是**数据修正**，不是"多收一点"：
//   zh_CN.lang 里点分键那批（羊毛/地毯/混凝土/原木/木板/树叶/树苗/台阶/船/床/
//   染料/旗帜/潜石板/刷怪蛋…）原本**整族没被提取**，因为老脚本只认 `item.<id>.name`
//   且显式排除了含点的 id。实测 `white_wool`、`oak_log`、`acacia_planks`、所有船
//   都不在词表里 —— 玩家搜「羊毛」「原木」一条都搜不到。
//
// 用法: node apply-plan.mjs          # 写回 CSV
//       node apply-plan.mjs --dry    # 只看会加什么
import { readFileSync, writeFileSync, existsSync } from 'node:fs';
import path from 'node:path';

const here = path.dirname(new URL(import.meta.url).pathname.replace(/^\/([A-Za-z]:)/, '$1'));
const dry = process.argv.includes('--dry');

const plan = JSON.parse(readFileSync(path.join(here, 'mapped-plan.json'), 'utf8'));

function readCsv(file) {
  const p = path.join(here, file);
  const lines = readFileSync(p, 'utf8').replace(/^\uFEFF/, '').split(/\r?\n/).filter(Boolean);
  const header = lines[0];
  const rows = new Map();
  for (const line of lines.slice(1)) {
    const m = /^"((?:[^"]|"")*)","((?:[^"]|"")*)","?([^",]*)"?$/.exec(line);
    if (m) rows.set(m[1].replace(/""/g, '"'), { name: m[2].replace(/""/g, '"'), inPack: m[3] === 'True' });
  }
  return { header, rows };
}

const csvCell = (s) => '"' + String(s).replace(/"/g, '""') + '"';

function writeCsv(file, header, rows) {
  const out = [header];
  for (const id of [...rows.keys()].sort()) {
    const r = rows.get(id);
    out.push([csvCell(id), csvCell(r.name), r.inPack ? 'True' : 'False'].join(','));
  }
  writeFileSync(path.join(here, file), out.join('\n') + '\n', 'utf8');
}

for (const [file, key] of [['items.csv', 'itemsMust'], ['blocks.csv', 'blocksMust']]) {
  const { header, rows } = readCsv(file);
  const before = rows.size;
  const added = [];
  for (const [id, name] of plan[key]) {
    if (rows.has(id)) continue;
    rows.set(id, { name, inPack: false });
    added.push([id, name]);
  }

  // ── 还要补"映射到了、但官方清单没认"的那批 ────────────────────────────────
  // 官方清单（Microsoft Learn 的 Default Listings）里有**旧 ID**没更新，
  // 实测至少三例：
  //   * 它写 `silver_glazed_terracotta`，而现代游戏 ID 是 `light_gray_glazed_terracotta`
  //     （同一批的 `silver_glazed_terracotta` 与 `light_blue_glazed_terracotta` 并存，
  //      说明文档是几次更新叠加的、没统一）；
  //   * 它写 `hardened_clay`（扁平化前的名字），语言文件用的是 `terracotta` 系列；
  //   * `light_block`（光源方块）与 `monster_egg`（被虫蚀的方块）它压根没列，
  //     但两者都是能 /setblock 的真方块。
  // zh_CN.lang 才跟着游戏版本走，所以这类**以语言文件为准**收进来。
  const notOfficialKey = key === 'itemsMust' ? 'itemsNotOfficial' : 'blocksNotOfficial';
  for (const [id, name] of plan[notOfficialKey] ?? []) {
    if (rows.has(id)) continue;
    rows.set(id, { name, inPack: false });
    added.push([id, name]);
  }

  console.log(`${file}: ${before} → ${rows.size}（新增 ${added.length}）`);
  if (dry) {
    added.slice(0, 30).forEach(([id, name]) => console.log(`    + ${id} = ${name}`));
    if (added.length > 30) console.log(`    …还有 ${added.length - 30} 条`);
  } else {
    writeCsv(file, header, rows);
    console.log(`    已写回 ${file}`);
  }
}
