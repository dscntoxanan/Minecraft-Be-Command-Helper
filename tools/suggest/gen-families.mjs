// 由官方 families.csv 生成中文名并写出 Kotlin 片段
//
// 中文名的来源优先级：
//   1. 手工表 FAMILY_CN（有些族的英文名与实体 ID 不同，机器推不出来）
//   2. 官方实体词表里的中文名（族名与实体 ID 同名时，如 `creeper` → 苦力怕）
//   3. 没有中文名的就保留英文 ID —— **不编造翻译**
//
// 用法: node tools/suggest/gen-families.mjs

import { readFileSync, writeFileSync } from 'node:fs';

const DIR = 'D:\\Agent\\Deepseek\\CommandAssistant\\tools\\suggest';
const SUGGEST = 'D:\\Agent\\Deepseek\\CommandAssistant\\app\\src\\main\\java\\com\\xanan\\cmdhelper\\json\\Suggest.kt';
const OUT = 'D:\\Agent\\Deepseek\\CommandAssistant\\app\\src\\main\\java\\com\\xanan\\cmdhelper\\json\\EntityFamilies.kt';

/** 手工中文名：键是官方族名。只写有把握的，剩下交给实体词表或保留英文。 */
const FAMILY_CN = {
  // 大类
  mob: '生物（所有生物）',
  monster: '怪物',
  undead: '亡灵',
  inanimate: '非生物（方块/物品类）',
  animal: '动物',
  aquatic: '水生生物',
  arthropod: '节肢动物',
  fish: '鱼类',
  illager: '灾厄村民',
  projectile: '弹射物',
  actor: '实体（总类）',
  pacified: '被安抚',

  // 具体生物
  creeper: '苦力怕 / 爬行者',
  zombie: '僵尸',
  skeleton: '骷髅',
  cavespider: '洞穴蜘蛛',
  spider: '蜘蛛',
  enderman: '末影人',
  endermite: '末影螨',
  silverfish: '蠹虫',
  slime: '史莱姆',
  magmacube: '岩浆怪',
  blaze: '烈焰人',
  ghast: '恶魂',
  happy_ghast: '善念恶魂',
  wither: '凋灵',
  dragon: '末影龙',
  shulker: '潜影贝',
  guardian: '守卫者',
  guardian_elder: '远古守卫者',
  warden: '监守者',
  breeze: '旋风人',
  creaking: '嘎枝',
  sulfur_cube: '硫方怪',
  armadillo: '犰狳',
  bogged: '沼骸',
  parched: '焦骷髅',
  camel_husk: '骆驼尸傀',
  sniffer: '嗅探兽',
  allay: '悦灵',
  copper_golem: '铜傀儡',
  irongolem: '铁傀儡',
  snowgolem: '雪傀儡',
  vex: '恼鬼',
  evocation_illager: '唤魔者',
  vindicator: '卫道士',
  pillager: '掠夺者',
  ravager: '劫掠兽',
  witch: '女巫',
  piglin: '猪灵',
  piglin_brute: '猪灵蛮兵',
  zoglin: '僵尸疣猪兽',
  zoglin_adult: '成年僵尸疣猪兽',
  hoglin: '疣猪兽',
  hoglin_baby: '幼年疣猪兽',
  zombie_villager: '僵尸村民',
  drowned: '溺尸',
  husk: '尸壳',
  stray: '流浪者',
  phantom: '幻翼',
  strider: '炽足兽',
  strider_baby: '幼年炽足兽',
  frog: '青蛙',
  tadpole: '蝌蚪',
  axolotl: '美西螈',
  goat: '山羊',
  panda: '熊猫',
  panda_aggressive: '熊猫（攻击性）',
  polarbear: '北极熊',
  fox: '狐狸',
  wolf: '狼',
  cat: '猫',
  ocelot: '豹猫',
  rabbit: '兔子',
  chicken: '鸡',
  cow: '牛',
  mushroomcow: '哞菇',
  pig: '猪',
  sheep: '羊',
  horse: '马',
  skeletonhorse: '骷髅马',
  zombiehorse: '僵尸马',
  donkey: '驴',
  mule: '骡',
  llama: '羊驼',
  trader_llama: '行商羊驼',
  turtle: '海龟',
  baby_turtle: '幼年海龟',
  bat: '蝙蝠',
  bee: '蜜蜂',
  parrot_tame: '已驯服鹦鹉',
  dolphin: '海豚',
  squid: '鱿鱼',
  nautilus: '鹦鹉螺',
  zombie_nautilus: '僵尸鹦鹉螺',
  cod: '鳕鱼',
  salmon: '鲑鱼',
  tropicalfish: '热带鱼',
  pufferfish: '河豚',
  villager: '村民',
  wandering_trader: '流浪商人',
  npc: 'NPC',
  player: '玩家',
  armor_stand: '盔甲架',
  tripodcamera: '三脚架摄像机',
  cushion: '坐垫',
  boat: '船',
  minecart: '矿车',
  tnt: 'TNT',
  xp_bottle: '附魔之瓶',
  egg: '鸡蛋',
  snowball: '雪球',
  ender_pearl: '末影珍珠',
  lightning: '闪电',
  fireball: '火球',
  small_fireball: '小火球',
  dragon_fireball: '末影龙火球',
  wither_skull: '凋灵之首',
  wither_skull_dangerous: '凋灵之首（危险）',
  shulker_bullet: '潜影贝子弹',
  llama_spit: '羊驼口水',
  splash_potion: '喷溅药水',
  lingering_potion: '滞留药水',
  thrown_trident: '三叉戟',
  fishing_hook: '鱼钩',
  fireworks_rocket: '焰火火箭',
  wind_charge: '风弹',
  wind_charge_projectile: '风弹（投射物）',
  breeze_wind_charge_projectile: '旋风人风弹',

  // 职业 / 变体
  armorer: '盔甲匠',
  cartographer: '制图师',
  baby_piglin: '幼年猪灵',
  adult_piglin: '成年猪灵',
  baby_undead: '幼年亡灵',
  baby_zombie_pigman: '幼年僵尸猪灵',
};

function readCsv(file) {
  const lines = readFileSync(file, 'utf8').split(/\r?\n/).filter(Boolean);
  const head = lines[0].split(',');
  const out = [];
  for (let i = 1; i < lines.length; i++) {
    const cells = [];
    let cur = '';
    let q = false;
    for (let j = 0; j < lines[i].length; j++) {
      const c = lines[i][j];
      if (q) {
        if (c === '"' && lines[i][j + 1] === '"') { cur += '"'; j++; }
        else if (c === '"') q = false;
        else cur += c;
      } else if (c === '"') q = true;
      else if (c === ',') { cells.push(cur); cur = ''; }
      else cur += c;
    }
    cells.push(cur);
    const row = {};
    head.forEach((h, k) => { row[h] = cells[k]; });
    out.push(row);
  }
  return out;
}

// 官方实体词表：族名与实体 ID 同名时可以直接借它的中文名
const suggestSrc = readFileSync(SUGGEST, 'utf8');
const entityNames = new Map();
for (const m of suggestSrc.matchAll(/Term\("([^"]+)",\s*"([^"]*)"\)/g)) {
  if (!entityNames.has(m[1])) entityNames.set(m[1], m[2]);
}

const rows = readCsv(`${DIR}\\families.csv`);
const items = rows.map((r) => {
  const id = r.family;
  const cn = FAMILY_CN[id] ?? entityNames.get(id) ?? '';
  return { id, cn, count: r.entityCount ?? '', sample: r.sampleEntities ?? '' };
});

const withCn = items.filter((i) => i.cn).length;
console.log(`族 ${items.length} 个，有中文名 ${withCn}，无中文名 ${items.length - withCn}`);
const missing = items.filter((i) => !i.cn);
if (missing.length) console.log('无中文名（保留英文）：', missing.map((i) => i.id).join(', '));

const kotlinString = (s) => `"${String(s ?? '').replace(/\\/g, '\\\\').replace(/"/g, '\\"')}"`;

// 生成 Kotlin：族名 + 中文名 + 示例实体（示例放注释里，方便人工核对）
const body = items.map((i, idx) => {
  const comment = i.sample ? ` // ${i.count} 个实体，如 ${i.sample.split('|').slice(0, 3).join('/')}` : '';
  const name = i.cn || i.id;
  return `        ${kotlinString(i.id)} to ${kotlinString(name)},${comment}`;
}).join('\n');

writeFileSync(
  OUT,
  '// 由 tools/suggest/gen-families.mjs 生成，不要手改\n'
    // ⚠️ 注释里**不能出现 `/*`** —— Kotlin 的块注释支持嵌套，
    // `behavior_pack/entities/*.json` 这种 glob 会开启一个永远不闭合的嵌套注释，
    // 报错是 "Unclosed comment"（踩过）。这里一律写成 `entities/…json`。
    + '// 数据来源：Mojang 官方 behavior_pack/entities 下的实体定义\n'
    + `// 共 ${items.length} 个族（有中文名 ${withCn} 个）\n`
    + '\n'
    + 'package com.xanan.cmdhelper.json\n'
    + '\n'
    + '/**\n'
    + ' * 实体族清单（`@e[family=…]` 的合法值）。\n'
    + ' *\n'
    + ' * ⚠️ **本文件由工具生成，不要手改**。数据来自 Mojang 官方行为包里\n'
    + ' * 每个实体的 `minecraft:type_family` 组件 —— 这是唯一的权威来源\n'
    + ' * （语言文件里没有族名，Wiki 也只举了几个例子）。\n'
    + ' *\n'
    + ' * 生成链：`fetch-families.mjs`（联网抓官方包）→ `gen-families.mjs`（配中文名）。\n'
    + ' */\n'
    + 'internal object EntityFamilies {\n'
    + '    /** 族名 → 中文名；没有官方译名的保留英文 ID，不编造翻译。 */\n'
    + `    val all: List<Pair<String, String>> = listOf(\n${body}\n    )\n`
    + '}\n',
  'utf8',
);
console.log('已写', OUT);
