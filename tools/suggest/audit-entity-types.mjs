// 核对 type= 的候选：把官方客户端实体清单与现有实体词表比对，找出缺的
//
// 用法: node tools/suggest/audit-entity-types.mjs

import { readFileSync } from 'node:fs';

const DIR = 'D:\\Agent\\Deepseek\\CommandAssistant\\tools\\suggest';
const SUGGEST = 'D:\\Agent\\Deepseek\\CommandAssistant\\app\\src\\main\\java\\com\\xanan\\cmdhelper\\json\\Suggest.kt';

const client = await fetch('https://api.github.com/repos/Mojang/bedrock-samples/contents/resource_pack/entity', {
  headers: { 'User-Agent': 'dsh-command-assistant' },
}).then((r) => r.json());

// 归一化：去掉 `.v1.0` 这类版本后缀与 `_v1`/`_v2` 变体后缀，去重
function normalize(name) {
  let id = name.replace(/\.json$/, '').replace(/\.entity$/, '');
  id = id.replace(/\.v\d+(\.\d+)*$/, '');   // armor_stand.v1.0
  id = id.replace(/_v\d+$/, '');            // donkey_v1 / horse_v2
  return id;
}

/** 这些是渲染/内部定义，不是能写进 type= 的实体 ID。 */
const NOT_ENTITY = new Set([
  'bed', 'decorated_pot', 'skull', 'armor_stand', 'player', '',
]);

const clientIds = new Set();
for (const f of client) {
  if (!f.name.endsWith('.json')) continue;
  const id = normalize(f.name);
  if (!NOT_ENTITY.has(id)) clientIds.add(id);
}
console.log('官方客户端实体（归一化后）=', clientIds.size);

// 现有词表
const src = readFileSync(SUGGEST, 'utf8');
const m = src.match(/val entities:[\s\S]*?\n    \)/);
const have = new Set([...m[0].matchAll(/Term\("([^"]+)"/g)].map((x) => x[1]));
console.log('现有实体词表 =', have.size);

const missing = [...clientIds].filter((id) => !have.has(id)).sort();
console.log('\n=== 官方有、词表里缺的', missing.length, '个 ===');
missing.forEach((id) => console.log('  ' + id));

const extra = [...have].filter((id) => !clientIds.has(id)).sort();
console.log('\n=== 词表有、官方客户端清单里没有的', extra.length, '个 ===');
extra.forEach((id) => console.log('  ' + id));
