// 统计官方 zh_CN.lang 里各类前缀的键数量，判断"我们的词表相对官方少了什么"
// 用法: node audit-lang.mjs <zh_CN.lang 路径>
import { readFileSync, existsSync } from 'node:fs';

const file = process.argv[2];
if (!file || !existsSync(file)) {
  console.error('用法: node audit-lang.mjs <zh_CN.lang>');
  process.exit(1);
}

const text = readFileSync(file, 'utf8').replace(/^\uFEFF/, '');
const kv = new Map();
for (const line of text.split(/\r?\n/)) {
  if (!line || line.startsWith('#')) continue;
  const i = line.indexOf('=');
  if (i <= 0) continue;
  kv.set(line.slice(0, i).trim(), line.slice(i + 1).trim());
}
console.log(`zh_CN.lang 共 ${kv.size} 个键\n`);

// 统计各前缀
const prefixCount = new Map();
for (const k of kv.keys()) {
  const p = k.includes('.') ? k.slice(0, k.indexOf('.')) : '(无点)';
  prefixCount.set(p, (prefixCount.get(p) ?? 0) + 1);
}
console.log('=== 各前缀键数（只列 >= 20 的）===');
[...prefixCount.entries()].filter(([, n]) => n >= 20).sort((a, b) => b[1] - a[1])
  .forEach(([p, n]) => console.log(`  ${String(n).padStart(6)}  ${p}.*`));

// 重点看这几类
const FOCUS = ['item', 'tile', 'entity', 'particle', 'sound', 'subtitles', 'effect', 'biome', 'enchantment'];
console.log('\n=== 重点前缀明细 ===');
for (const f of FOCUS) {
  const keys = [...kv.keys()].filter((k) => k.startsWith(f + '.'));
  console.log(`\n--- ${f}.*  共 ${keys.length} 个`);
  // 前缀的下一级词（item. 后面那一段的统计）
  const sub = new Map();
  for (const k of keys) {
    const rest = k.slice(f.length + 1);
    const seg = rest.includes('.') ? rest.slice(0, rest.indexOf('.')) : '(直接结尾)';
    sub.set(seg, (sub.get(seg) ?? 0) + 1);
  }
  [...sub.entries()].sort((a, b) => b[1] - a[1]).slice(0, 12)
    .forEach(([s, n]) => console.log(`      ${String(n).padStart(5)}  ${f}.${s}`));
  console.log('      样例:');
  keys.slice(0, 5).forEach((k) => console.log(`        ${k} = ${kv.get(k)}`));
}
