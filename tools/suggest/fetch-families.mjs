// 从官方 bedrock-samples 的实体定义里提取全部 type_family 值
//
// 为什么需要：`family=` 的合法值是实体 `minecraft:type_family` 组件里声明的族，
// 不在任何语言文件里，Wiki 也只举了几个例子。唯一权威来源就是官方行为包。
//
// 用法: node tools/suggest/fetch-families.mjs

import { writeFileSync } from 'node:fs';

const API = 'https://api.github.com/repos/Mojang/bedrock-samples/contents/behavior_pack/entities';
const RAW = 'https://raw.githubusercontent.com/Mojang/bedrock-samples/main/behavior_pack/entities';
const OUT = 'D:\\Agent\\Deepseek\\CommandAssistant\\tools\\suggest\\families.csv';

async function getJson(url) {
  const r = await fetch(url, { headers: { 'User-Agent': 'dsh-command-assistant' } });
  if (!r.ok) throw new Error(`${r.status} ${url}`);
  return r.json();
}

const files = (await getJson(API)).filter((f) => f.name.endsWith('.json'));
console.log('实体文件 =', files.length);

/** family → 用到它的实体列表（用来给中文说明提供线索）。 */
const familyToEntities = new Map();

/**
 * 带重试的抓取。
 *
 * raw.githubusercontent.com 在并发稍高时会直接 ConnectTimeout（实测过），
 * 所以这里降并发 + 退避重试，并且**失败不抛**，交给调用方跳过 ——
 * 少解析几个实体不影响"族名清单"的完整性判断（漏掉的只是计数）。
 */
async function fetchWithRetry(url, tries = 3) {
  for (let i = 0; i < tries; i++) {
    try {
      const r = await fetch(url, {
        headers: { 'User-Agent': 'dsh-command-assistant' },
        signal: AbortSignal.timeout(20000),
      });
      if (r.ok) return await r.text();
    } catch {
      // 退避后重试
    }
    await new Promise((res) => setTimeout(res, 600 * (i + 1)));
  }
  return '';
}

const CONCURRENCY = 3;
let cursor = 0;
let okCount = 0;
let failCount = 0;
async function worker() {
  while (cursor < files.length) {
    const file = files[cursor++];
    const text = await fetchWithRetry(`${RAW}/${file.name}`);
    if (!text) {
      failCount++;
      continue;
    }
    okCount++;

    // type_family 形如： "minecraft:type_family": { "family": [ "zombie", "undead" ] }
    // 用正则而不是 JSON.parse：这些文件里带注释（// ...），严格解析会失败。
    const block = text.match(/"minecraft:type_family"\s*:\s*\{[\s\S]*?"family"\s*:\s*\[([\s\S]*?)\]/);
    if (!block) continue;
    const ids = [...block[1].matchAll(/"([^"]+)"/g)].map((m) => m[1]);
    const entityId = file.name.replace(/\.json$/, '');
    for (const id of ids) {
      if (!familyToEntities.has(id)) familyToEntities.set(id, []);
      familyToEntities.get(id).push(entityId);
    }
  }
}

await Promise.all(Array.from({ length: CONCURRENCY }, worker));
console.log(`抓取成功 ${okCount} / 失败 ${failCount}`);

const families = [...familyToEntities.keys()].sort();
console.log('提取到族 =', families.length);

const rows = [['family', 'entityCount', 'sampleEntities']];
for (const f of families) {
  const list = familyToEntities.get(f);
  rows.push([f, String(list.length), list.slice(0, 5).join('|')]);
}
writeFileSync(
  OUT,
  rows.map((r) => r.map((c) => (/[",]/.test(c) ? '"' + c.replace(/"/g, '""') + '"' : c)).join(',')).join('\n'),
  'utf8',
);
console.log('已写', OUT);
console.log('\n全部族名：');
console.log(families.join(', '));
