// 看官方语言文件里 item.* / tile.* / entity.* 键的**命名形态**，
// 判断哪些能当命令 ID 用（这是甄别 corpus 的第一步）
// 用法: node peek-lang.mjs <zh_CN.lang> <前缀> [每类样例数]
import { readFileSync } from 'node:fs';

const [, , file, prefix = 'item', per = '8'] = process.argv;
const text = readFileSync(file, 'utf8').replace(/^\uFEFF/, '');
const kv = new Map();
for (const line of text.split(/\r?\n/)) {
  if (!line || line.startsWith('#')) continue;
  const i = line.indexOf('=');
  if (i <= 0) continue;
  kv.set(line.slice(0, i).trim(), line.slice(i + 1).trim());
}

const keys = [...kv.keys()].filter((k) => k.startsWith(prefix + '.'));
console.log(`${prefix}.* 共 ${keys.length} 个\n`);

// 按"去掉前缀后的第一段"分组
const groups = new Map();
for (const k of keys) {
  const rest = k.slice(prefix.length + 1);
  const seg = rest.includes('.') ? rest.slice(0, rest.indexOf('.')) : '(无点，整段就是名称)';
  if (!groups.has(seg)) groups.set(seg, []);
  groups.get(seg).push(k);
}

console.log('=== 分组（按第一段）===');
[...groups.entries()].sort((a, b) => b[1].length - a[1].length).forEach(([seg, list]) => {
  if (list.length < 2) return;
  console.log(`\n--- ${prefix}.${seg}.*  (${list.length} 个)`);
  list.slice(0, +per).forEach((k) => console.log(`      ${k} = ${kv.get(k)}`));
});

// 单段键（最可能是可直接当 ID 的）
const flat = keys.filter((k) => !k.slice(prefix.length + 1).includes('.'));
console.log(`\n=== 单段键（${prefix}.<name>，共 ${flat.length} 个）===`);
flat.slice(0, 40).forEach((k) => console.log(`      ${k} = ${kv.get(k)}`));
