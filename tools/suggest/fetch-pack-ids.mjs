// 从官方 bedrock-samples 资源包/行为包取"真实存在的 ID 清单"，用于交叉验证语言文件里的 ID
//   资源包 textures/items/**.png 与 textures/blocks/**.png 的文件名
//   行为包 entities/*.json 的文件名（实体 ID）
// 用法: node fetch-pack-ids.mjs
import { writeFileSync, mkdirSync } from 'node:fs';
import path from 'node:path';

const here = path.dirname(new URL(import.meta.url).pathname.replace(/^\/([A-Za-z]:)/, '$1'));
const API = 'https://api.github.com/repos/Mojang/bedrock-samples/git/trees/main?recursive=1';

async function fetchJson(url, tries = 4) {
  let last;
  for (let i = 1; i <= tries; i++) {
    try {
      const r = await fetch(url, { headers: { 'User-Agent': 'cmdhelper-audit', Accept: 'application/vnd.github+json' } });
      if (!r.ok) throw new Error(`HTTP ${r.status}`);
      return await r.json();
    } catch (e) {
      last = e;
      console.log(`  第 ${i} 次失败：${e.message}${i < tries ? '，重试…' : ''}`);
      if (i < tries) await new Promise((r) => setTimeout(r, 900 * i));
    }
  }
  throw last;
}

console.log('拉取仓库文件树（一次拿到全部路径）…');
const tree = await fetchJson(API);
if (!tree.tree) throw new Error('返回里没有 tree 字段：' + JSON.stringify(tree).slice(0, 200));
console.log(`  共 ${tree.tree.length} 个路径`);

const paths = tree.tree.map((t) => t.path);

/** 从路径集合里按正则抽出文件名（去扩展名）。 */
function collect(paths, re, group = 1) {
  const out = new Map(); // name -> 第一个匹配到的完整路径
  for (const p of paths) {
    const m = re.exec(p);
    if (!m) continue;
    const name = m[group];
    if (!out.has(name)) out.set(name, p);
  }
  return out;
}

// 只取**顶层** png（子目录里是动画帧/变体，不是独立 ID）
const itemTex = collect(paths, /^resource_pack\/textures\/items\/([^/]+)\.png$/, 1);
const blockTex = collect(paths, /^resource_pack\/textures\/blocks\/([^/]+)\.png$/, 1);
// 实体：行为包 definitions 里每个实体一个 json
const entDef = collect(paths, /^behavior_pack\/entities\/([^/]+)\.json$/, 1);
// 物品"定义"（1.21+ 的 item 组件定义，比贴图更权威）
const itemDef = collect(paths, /^behavior_pack\/items\/([^/]+)\.json$/, 1);

console.log(`资源包贴图：items=${itemTex.size}  blocks=${blockTex.size}`);
console.log(`行为包定义：entities=${entDef.size}  items=${itemDef.size}`);

const out = {
  'items-textures': [...itemTex.keys()].sort(),
  'blocks-textures': [...blockTex.keys()].sort(),
  'entities-definitions': [...entDef.keys()].sort(),
  'items-definitions': [...itemDef.keys()].sort(),
};
const dst = path.join(here, 'pack-ids.json');
writeFileSync(dst, JSON.stringify(out, null, 1), 'utf8');
console.log(`已写入 ${dst}`);

// 顺便看看我们关心的新内容在不在
const probe = ['pale_oak_planks', 'pale_oak_log', 'poplar_planks', 'poplar_log', 'straw_bed', 'resin_clump', 'resin_block'];
console.log('\n=== 重点 ID 是否在资源包/行为包里 ===');
for (const id of probe) {
  console.log(
    `  ${id.padEnd(20)} 贴图=${itemTex.has(id) || blockTex.has(id) ? '有' : '—'}` +
    `  物品定义=${itemDef.has(id) ? '有' : '—'}`,
  );
}
