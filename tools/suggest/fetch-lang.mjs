// 拉取官方最新 zh_CN.lang，与本地存档对比：
//   - 新增了哪些 item./tile./entity. 的 .name 键（= 官方新加的物品/方块/实体）
//   - 哪些键的译名改了
// 用法: node fetch-lang.mjs [输出目录]
import { writeFileSync, readFileSync, existsSync, mkdirSync } from 'node:fs';
import path from 'node:path';

const here = path.dirname(new URL(import.meta.url).pathname.replace(/^\/([A-Za-z]:)/, '$1'));
const outDir = process.argv[2] ?? here;
const remote = 'https://raw.githubusercontent.com/Mojang/bedrock-samples/main/resource_pack/texts/zh_CN.lang';
const local = path.join(here, 'zh_CN.lang');

console.log('拉取官方 zh_CN.lang …');

/**
 * 带退避重试的拉取。
 *
 * 这个文件有 1.2MB，直连 raw.githubusercontent 时**经常被重置**
 * （`read ECONNRESET`，实测 3 次里坏 1 次）。重试几次基本都能成功，
 * 所以不要因为一次失败就判定"拉不到"。
 */
async function fetchWithRetry(url, tries = 5) {
  let lastErr;
  for (let i = 1; i <= tries; i++) {
    try {
      const r = await fetch(url, { headers: { 'User-Agent': 'cmdhelper-audit' } });
      if (!r.ok) throw new Error(`HTTP ${r.status}`);
      const t = await r.text();
      if (t.length < 10000) throw new Error(`内容太短（${t.length} 字节），可能是错误页`);
      return t;
    } catch (e) {
      lastErr = e;
      console.log(`  第 ${i} 次失败：${e.message}${i < tries ? '，重试…' : ''}`);
      if (i < tries) await new Promise((r) => setTimeout(r, 800 * i));
    }
  }
  throw lastErr;
}

const fresh = await fetchWithRetry(remote);
console.log(`  拿到 ${fresh.length} 字节`);

const parse = (t) => {
  const m = new Map();
  for (const line of t.replace(/^\uFEFF/, '').split(/\r?\n/)) {
    if (!line || line.startsWith('#')) continue;
    const i = line.indexOf('=');
    if (i <= 0) continue;
    m.set(line.slice(0, i).trim(), line.slice(i + 1).trim());
  }
  return m;
};

/** 只取能当 ID 用的那些键：^<prefix>\.(.+)\.name$，且 id 里不含点、不含大写。 */
const idsOf = (m, prefix) => {
  const out = new Map();
  const re = new RegExp(`^${prefix}\\.(.+)\\.name$`);
  for (const [k, v] of m) {
    const mt = re.exec(k);
    if (!mt) continue;
    const id = mt[1];
    if (id.includes('.')) continue;
    if (/[A-Z]/.test(id)) continue;
    if (!v) continue;
    out.set(id, v);
  }
  return out;
};

const freshMap = parse(fresh);
const newItems = idsOf(freshMap, 'item');
const newTiles = idsOf(freshMap, 'tile');
const newEnts = idsOf(freshMap, 'entity');
console.log(`官方最新：item=${newItems.size} tile=${newTiles.size} entity=${newEnts.size}`);

if (existsSync(local)) {
  const oldMap = parse(readFileSync(local, 'utf8'));
  const oldItems = idsOf(oldMap, 'item');
  const oldTiles = idsOf(oldMap, 'tile');
  const oldEnts = idsOf(oldMap, 'entity');
  console.log(`本地存档：item=${oldItems.size} tile=${oldTiles.size} entity=${oldEnts.size}`);

  const diff = (a, b, label) => {
    const added = [...a.keys()].filter((k) => !b.has(k));
    const removed = [...b.keys()].filter((k) => !a.has(k));
    const renamed = [...a.keys()].filter((k) => b.has(k) && b.get(k) !== a.get(k));
    console.log(`\n=== ${label}：新增 ${added.length} / 消失 ${removed.length} / 译名改动 ${renamed.length}`);
    if (added.length) console.log('  新增: ' + added.map((k) => `${k}=${a.get(k)}`).join(', '));
    if (removed.length) console.log('  消失: ' + removed.join(', '));
    if (renamed.length) {
      renamed.slice(0, 40).forEach((k) => console.log(`  译名改: ${k}  「${b.get(k)}」 -> 「${a.get(k)}」`));
      if (renamed.length > 40) console.log(`  …还有 ${renamed.length - 40} 条译名改动`);
    }
  };
  diff(newItems, oldItems, 'item');
  diff(newTiles, oldTiles, 'tile');
  diff(newEnts, oldEnts, 'entity');
} else {
  console.log('本地没有存档，跳过对比');
}

mkdirSync(outDir, { recursive: true });
const dst = path.join(outDir, 'zh_CN.official.lang');
writeFileSync(dst, fresh, 'utf8');
console.log(`\n已写入 ${dst}`);
