// 核对：type= 的合法值（实体类型 ID）到底有哪些
//
// 来源：官方 behavior_pack/entities 的文件名 + 语言文件里的 entity.*.name 键。
// 用法: node tools/suggest/fetch-entity-ids.mjs

import { readFileSync, writeFileSync } from 'node:fs';

const DIR = 'D:\\Agent\\Deepseek\\CommandAssistant\\tools\\suggest';
const LANG = `${DIR}\\zh_CN.lang`;

const files = await fetch('https://api.github.com/repos/Mojang/bedrock-samples/contents/behavior_pack/entities', {
  headers: { 'User-Agent': 'dsh-command-assistant' },
}).then((r) => r.json());
const ids = files.filter((f) => f.name.endsWith('.json')).map((f) => f.name.replace(/\.json$/, '')).sort();
console.log('官方实体定义文件 =', ids.length);

// 语言文件里的 entity.<id>.name
const lang = readFileSync(LANG, 'utf8').split(/\r?\n/);
const langIds = new Map();
for (const l of lang) {
  const m = l.match(/^entity\.([a-z0-9_]+)\.name=(.+)$/);
  if (m) langIds.set(m[1], m[2]);
}
console.log('语言文件 entity.*.name =', langIds.size);

const missing = ids.filter((id) => !langIds.has(id));
console.log('\n=== 官方有定义、语言文件没有 entity.*.name 的', missing.length, '个 ===');
missing.forEach((id) => console.log('  ' + id));

const extra = [...langIds.keys()].filter((id) => !ids.includes(id));
console.log('\n=== 语言文件有、官方没定义文件的', extra.length, '个 ===');
extra.forEach((id) => console.log(`  ${id} = ${langIds.get(id)}`));

writeFileSync(`${DIR}\\entity_ids.csv`,
  ['id', 'name'].concat([]).join(',') + '\n' +
  ids.map((id) => `${id},${(langIds.get(id) ?? '').replace(/,/g, '，')}`).join('\n'), 'utf8');
console.log('\n已写 entity_ids.csv（官方定义清单）');
