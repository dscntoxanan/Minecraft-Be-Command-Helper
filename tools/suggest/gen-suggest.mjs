// 由官方 zh_CN.lang + 俗名表生成 Suggest.kt
//
// 用 Node 而不是 PowerShell：PS 的对象/数组是引用语义，配 CSV 导入时
// 很容易出现"改了 A 条目的名字，B 条目也变了"这种串味（踩过两次）。
// Node 里全是值语义，生成结果确定、可复现。
//
// 用法: node gen-suggest.mjs <langDir> <outFile>

import { readFileSync, writeFileSync, existsSync as fsExists } from 'node:fs';
import * as fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

/** 本脚本所在目录（tools/suggest）—— 数据与输出都相对它定位，换机器不用改路径。 */
const here = path.dirname(fileURLToPath(import.meta.url));

// 默认路径指向 tools/suggest（本项目存档的位置）。
// 早期版本写的是个人临时目录 D:\Agent\Deepseek\.tmp\mc-lang，换机器就找不到数据了。
const langDir = process.argv[2] ?? here;
const outFile = process.argv[3]
  ?? path.join(here, '..', '..', 'app', 'src', 'main', 'java', 'com', 'xanan', 'cmdhelper', 'json', 'Suggest.kt');

/** 极简 CSV 解析（本项目的数据里没有内嵌逗号或引号，只需剥掉 Export-Csv 加的外层引号）。 */
function unquote(cell) {
  const s = (cell ?? '').trim();
  return s.startsWith('"') && s.endsWith('"') ? s.slice(1, -1) : s;
}

function readCsv(file) {
  const text = readFileSync(file, 'utf8').replace(/^\uFEFF/, '');
  const lines = text.split(/\r?\n/).filter((l) => l.trim() !== '');
  // PowerShell 的 Export-Csv 会给每个表头加引号，所以先剥掉再找列。
  const header = lines[0].split(',').map(unquote);
  const idIdx = header.indexOf('id');
  const nameIdx = header.indexOf('name');
  if (idIdx < 0 || nameIdx < 0) {
    throw new Error(`CSV 缺少 id/name 列: ${file} (header=${header.join('|')})`);
  }
  return lines.slice(1).map((line) => {
    const cells = line.split(',');
    return { id: unquote(cells[idIdx]), name: unquote(cells[nameIdx]) };
  }).filter((r) => r.id && r.name);
}

// ── 俗名表 ──────────────────────────────────────────────────────────────────
// 官方译名已经在词表里，这里只**补充**玩家日常会打的叫法。
// 如果某个俗名和官方译名相同，会在合并时自动去重（爬行者的官方简体译名就是"苦力怕"）。
const ALIASES = {
  // 生物
  creeper: '爬行者',
  zombie: '丧尸',
  skeleton: '骨骸',
  warden: '循声守卫',
  allay: '小精灵',
  wandering_trader: '流浪商人',
  iron_golem: '铁巨人',
  snow_golem: '雪人',
  shulker: '潜影盒怪',
  guardian: '海守卫',
  elder_guardian: '远古海守卫',
  piglin: '猪灵蛮兵',
  hoglin: '疣猪',
  // `entity.lightning_bolt.name` 官方译名就是"闪电"（没有"束"）。
  // 但玩家/旧译里有叫"闪电束"的，两写都能搜到。
  lightning_bolt: '闪电束',
  // 盔甲：玩家习惯说"裤""甲"
  leather_leggings: '皮裤',
  chainmail_leggings: '锁子裤',
  iron_leggings: '铁裤',
  golden_leggings: '金裤',
  diamond_leggings: '钻石裤',
  netherite_leggings: '下界合金裤',
  leather_chestplate: '皮甲',
  chainmail_chestplate: '锁子甲',
  iron_chestplate: '铁甲',
  golden_chestplate: '金甲',
  diamond_chestplate: '钻石甲',
  netherite_chestplate: '下界合金甲',
  leather_helmet: '皮帽',
  iron_helmet: '铁盔',
  golden_helmet: '金盔',
  diamond_helmet: '钻石盔',
  netherite_helmet: '下界合金盔',
  // 常见物品 / 方块的俗称
  grass_block: '草地',
  command_block: '指令方块',
  structure_block: '结构方块',
  repeating_command_block: '循环指令方块',
  chain_command_block: '连锁指令方块',
  obsidian: '黑曜石',
  elytra: '翅膀',
  trident: '海神三叉戟',
  shield: '盾',
  bow: '弓箭',
  crossbow: '十字弓',
  golden_apple: '金苹果',
  enchanted_golden_apple: '附魔金苹果',
  ender_pearl: '末影珍珠',
  blaze_rod: '烈焰棒',
};

const SEP = ' / ';

// ── 语言文件里没有、但 `type=` 能用的实体 ──────────────────────────────────
// 来源：官方 `resource_pack/entity` 的客户端实体清单（180 个文件，去掉版本后缀
// 与渲染用定义后 126 个）。与语言文件的 `entity.*.name`（138 个）比对后，
// **真正多出来的只有下面这几个**。
//
// 为什么不干脆改用客户端清单：它只有 126 个，比语言文件少 ——
// 语言文件里那 138 个（含 item / tnt / armor_stand / painting / player 等）
// 才是更全的超集。所以这里只在语言文件的基础上**补差集**。
const EXTRA_ENTITIES = {
  trial_spawner: '试炼刷怪笼',
  // 下面两个是同一实体的另一种官方写法，语言文件用的是
  // `entity.eye_of_ender_signal` / `entity.xp_bottle`。
  // 玩家可能按客户端定义名来搜，两种都留着，中文名沿用官方译名。
  ender_eye: '末影之眼 / 末影之眼信号',
  experience_bottle: '附魔之瓶 / 经验瓶',
  experience_orb: '经验球',
};

// ── 官方语言文件里缺失、但命令里极常用的方块 ────────────────────────────────
// zh_CN.lang 里没有 tile.stone.name / tile.command_block.name 这类键
// （标题栏英文名走的是另一套数据），结果恰恰是最常用的方块漏了，这里按需补录。
const EXTRA_BLOCKS = {
  stone: '石头',
  cobblestone: '圆石',
  mossy_cobblestone: '苔石',
  stone_bricks: '石砖',
  smooth_stone: '平滑石头',
  granite: '花岗岩',
  diorite: '闪长岩',
  andesite: '安山岩',
  deepslate: '深板岩',
  cobbled_deepslate: '深板岩圆石',
  dirt: '泥土',
  coarse_dirt: '砂土',
  rooted_dirt: '缠根泥土',
  grass_block: '草方块 / 草地',
  podzol: '灰化土',
  mycelium: '菌丝体',
  farmland: '耕地',
  sand: '沙子',
  red_sand: '红沙',
  sandstone: '砂岩',
  gravel: '砂砾',
  clay: '黏土',
  terracotta: '陶瓦',
  bricks: '砖块',
  glass: '玻璃',
  glass_pane: '玻璃板',
  stained_glass: '染色玻璃',
  oak_planks: '橡木木板',
  spruce_planks: '云杉木板',
  birch_planks: '白桦木板',
  jungle_planks: '丛林木板',
  acacia_planks: '金合欢木板',
  dark_oak_planks: '深色橡木木板',
  oak_log: '橡木原木',
  spruce_log: '云杉原木',
  birch_log: '白桦原木',
  oak_leaves: '橡木树叶',
  oak_fence: '橡木栅栏',
  oak_stairs: '橡木楼梯',
  oak_slab: '橡木台阶',
  oak_door: '橡木门',
  oak_trapdoor: '橡木活板门',
  bookshelf: '书架',
  crafting_table: '工作台',
  furnace: '熔炉',
  blast_furnace: '高炉',
  smoker: '烟熏炉',
  chest: '箱子',
  trapped_chest: '陷阱箱',
  ender_chest: '末影箱',
  barrel: '木桶',
  anvil: '铁砧',
  enchanting_table: '附魔台',
  brewing_stand: '酿造台',
  cauldron: '炼药锅',
  beacon: '信标',
  hopper: '漏斗',
  dropper: '投掷器',
  dispenser: '发射器',
  observer: '侦测器',
  piston: '活塞',
  sticky_piston: '黏性活塞',
  tnt: 'TNT',
  redstone_block: '红石块',
  redstone_lamp: '红石灯',
  redstone_torch: '红石火把',
  lever: '拉杆',
  torch: '火把',
  soul_torch: '灵魂火把',
  lantern: '灯笼',
  soul_lantern: '灵魂灯笼',
  glowstone: '荧石',
  sea_lantern: '海晶灯',
  shroomlight: '菌光体',
  command_block: '命令方块 / 指令方块',
  repeating_command_block: '循环命令方块',
  chain_command_block: '连锁命令方块',
  structure_block: '结构方块',
  structure_void: '结构空位',
  barrier: '屏障',
  bedrock: '基岩',
  obsidian: '黑曜石',
  crying_obsidian: '哭泣的黑曜石',
  netherrack: '下界岩',
  soul_sand: '灵魂沙',
  soul_soil: '灵魂土',
  end_stone: '末地石',
  purpur_block: '紫珀块',
  prismarine: '海晶石',
  sponge: '海绵',
  ice: '冰',
  packed_ice: '浮冰',
  blue_ice: '蓝冰',
  snow: '雪块',
  water: '水',
  lava: '岩浆',
  air: '空气（用来清除方块）',
  iron_block: '铁块',
  gold_block: '金块',
  diamond_block: '钻石块',
  emerald_block: '绿宝石块',
  netherite_block: '下界合金块',
  lapis_block: '青金石块',
  coal_block: '煤炭块',
  copper_block: '铜块',
  amethyst_block: '紫水晶块',
  hay_block: '干草捆',
  slime_block: '黏液块',
  honey_block: '蜂蜜块',
  target: '标靶',
  note_block: '音符盒',
  jukebox: '唱片机',
  lectern: '讲台',
  composter: '堆肥桶',
  loom: '织布机',
  smithing_table: '锻造台',
  stonecutter_block: '切石机',
  grindstone: '砂轮',
  cartography_table: '制图台',
  fletching_table: '制箭台',
  scaffolding: '脚手架',
  ladder: '梯子',
  rail: '铁轨',
  powered_rail: '充能铁轨',
  detector_rail: '探测铁轨',
  activator_rail: '激活铁轨',
  chain: '锁链',
  iron_bars: '铁栏杆',
  cobweb: '蜘蛛网',
  bed: '床',
  banner: '旗帜',
  sign: '告示牌',
  item_frame: '物品展示框',
  armour_stand_block: '盔甲架',
};

function withAliases(rows) {
  return rows.map((row) => {
    const extra = ALIASES[row.id];
    if (!extra) return { id: row.id, name: row.name };
    const parts = row.name.split(SEP);
    for (const name of extra.split(SEP)) {
      if (!parts.includes(name)) parts.push(name);
    }
    return { id: row.id, name: parts.join(SEP) };
  });
}

/**
 * 把语言文件里没有、但 `type=` 能用的实体补进来。
 *
 * 已有的（含 `xp_orb` 这类语言文件写法）保持不动，只补差集。
 */
function mergeExtraEntities(rows) {
  const known = new Set(rows.map((r) => r.id));
  const merged = rows.map((r) => {
    const extra = EXTRA_ENTITIES[r.id];
    if (!extra) return r;
    const parts = r.name.split(SEP);
    for (const name of extra.split(SEP)) {
      if (!parts.includes(name)) parts.push(name);
    }
    return { id: r.id, name: parts.join(SEP) };
  });
  for (const [id, name] of Object.entries(EXTRA_ENTITIES)) {
    if (!known.has(id)) merged.push({ id, name });
  }
  return merged.sort((a, b) => a.id.localeCompare(b.id));
}

/** 把补录的方块并进词表：已存在的（按官方名）保留官方名，缺的补上。 */
function mergeExtraBlocks(rows) {
  const known = new Set(rows.map((r) => r.id));
  const merged = rows.map((r) => {
    const extra = EXTRA_BLOCKS[r.id];
    if (!extra) return r;
    // 官方译名可能与补录名不同（grass_block 官方是空/草方块），两者都留。
    const parts = r.name.split(SEP);
    for (const name of extra.split(SEP)) {
      if (!parts.includes(name)) parts.push(name);
    }
    return { id: r.id, name: parts.join(SEP) };
  });
  for (const [id, name] of Object.entries(EXTRA_BLOCKS)) {
    if (!known.has(id)) merged.push({ id, name });
  }
  return merged.sort((a, b) => a.id.localeCompare(b.id));
}

// ── 读数据 ──────────────────────────────────────────────────────────────────
const items = withAliases(readCsv(path.join(langDir, 'items.csv')));
const blocks = mergeExtraBlocks(withAliases(readCsv(path.join(langDir, 'blocks.csv'))));
const entities = mergeExtraEntities(withAliases(readCsv(path.join(langDir, 'entities.csv'))));

// 音效与粒子的词表由 gen-extra-terms.mjs 生成到 extra-terms.kt，
// 这里读进来内联。分两个脚本是因为它们的数据要**联网**拉官方仓库，
// 而本脚本要能离线重复运行（改了俗称/别名就重跑，不该每次都联网）。
const EXTRA_FILE = path.join(here, 'extra-terms.kt');
let extraBlock = '';
if (fsExists(EXTRA_FILE)) {
  // ⚠️ **不要过滤掉 `//` 开头的行** —— 表的分隔标记就是注释
  //    （`// >>> SUGGEST-TABLE:sounds`），滤掉之后标记就找不到了，
  //    音效/粒子会静默内联成空表（踩过）。
  //    文件头的说明注释由下面的 extractTable 按标记范围裁掉，不影响输出。
  extraBlock = fs.readFileSync(EXTRA_FILE, 'utf8').replace(/\r\n/g, '\n');
} else {
  console.warn('⚠️ 没有 extra-terms.kt，音效/粒子词表将为空。先运行：node tools/suggest/gen-extra-terms.mjs');
}

// 调试：确认没有串味、补录生效
const sample = items.find((i) => i.id === 'diamond_chestplate');
console.log('sanity diamond_chestplate =', sample?.name);
console.log('sanity creeper =', entities.find((e) => e.id === 'creeper')?.name);
console.log('sanity stone =', blocks.find((b) => b.id === 'stone')?.name);
console.log('sanity command_block =', blocks.find((b) => b.id === 'command_block')?.name);
console.log(`counts items=${items.length} blocks=${blocks.length} entities=${entities.length}`);

// ── 生成 Kotlin ─────────────────────────────────────────────────────────────
const kotlinString = (s) => `"${String(s ?? '').replace(/\\/g, '\\\\').replace(/"/g, '\\"')}"`;

/**
 * 把一张大表拆成多个顶层 `val`，再合成一个只读入口。
 *
 * ── 为什么必须拆 ────────────────────────────────────────────────────────────
 * Kotlin 里 `val x = listOf(3000 个 Term(...))` 会把**全部构造代码塞进一个方法**
 * （`Suggest.<clinit>`）。JVM 对单个方法有 **64KB 字节码**硬限制，超了直接编译失败：
 *
 * ```
 * e: Error generating class file .../Suggest.class:
 *    Method too large: com/xanan/cmdhelper/json/Suggest.<clinit> ()V
 * ```
 *
 * ⚠️ **只在同一个文件里拆成多个 val 是不够的**：Kotlin 会把**同一文件所有顶层
 * 属性的初始化都塞进那一个 `<clinit>`**，总量没变、照样超限（实测踩过）。
 * 所以这里把大表**整段生成到独立文件**（见 splitTables），每个文件有自己的
 * `<clinit>`，单方法自然就降下来了。
 *
 * @param varName  对外暴露的名字（如 `items`）
 * @param rows     词条
 * @param comment  生成到 Kotlin 里的注释
 * @param chunk    每块多少条（同一文件内再分块，纯粹为了让代码可读）
 * @param qualify  `Term` 是否需要写成 `Suggest.Term`。
 *                 **独立文件里必须写限定名** —— `Term` 是嵌在 `Suggest` 对象里的
 *                 嵌套类，另一个文件的顶层属性初始化里写裸 `Term` 会
 *                 "Unresolved reference: Term"（实测踩过）。
 */
function terms(varName, rows, comment, chunk = 400, qualify = false) {
  const T = qualify ? 'Suggest.Term' : 'Term';
  if (rows.length <= chunk) {
    const body = rows
      .map((r, i) => `        ${T}(${kotlinString(r.id)}, ${kotlinString(r.name)})${i < rows.length - 1 ? ',' : ''}`)
      .join('\n');
    return `    /** ${comment} */\n    val ${varName}: List<Term> = listOf(\n${body}\n    )\n`;
  }

  const parts = [];
  for (let i = 0; i < rows.length; i += chunk) parts.push(rows.slice(i, i + chunk));

  const decls = parts.map((part, idx) => {
    const body = part
      .map((r, i) => `        ${T}(${kotlinString(r.id)}, ${kotlinString(r.name)})${i < part.length - 1 ? ',' : ''}`)
      .join('\n');
    return (
      `    /** ${comment}（第 ${idx + 1}/${parts.length} 块）*/\n` +
      `    private val ${varName}Part${idx}: List<Term> = listOf(\n${body}\n    )`
    );
  });

  // ⚠️ 块变量名用 `Part0/Part1…` 而不是 `$0/$1` —— Kotlin 标识符里允许 `$`，
  //    但它与协程的"名字改写"（`foo$default`、`foo$suspendImpl`）冲突，容易混淆。
  const refs = parts.map((_, idx) => `        ${varName}Part${idx}`).join(',\n');
  decls.push(
    `    /** ${comment} */\n` +
      `    val ${varName}: List<Term> get() = ${varName}All`,
  );
  decls.push(
    `    private val ${varName}All: List<Term> = listOf(\n${refs}\n    ).flatten()`,
  );
  return decls.join('\n\n') + '\n';
}

const HEADER = `package com.xanan.cmdhelper.json

/**
 * 常用取值库 —— 自动补全的"词表"，每个值都带中文名。
 *
 * ⚠️ **本文件由工具生成，不要手改**（改完下次生成会被覆盖）。
 *    生成脚本：\`.tmp/mc-lang/gen-suggest.mjs\`（数据来自下面两个来源）。
 *
 * ── 数据来源与依据 ──────────────────────────────────────────────────────────
 *  1. **官方中文译名**：中国版/Mojang 官方资源包的 \`texts/zh_CN.lang\`
 *     （https://github.com/Mojang/bedrock-samples）。
 *  2. **ID 以命令为准**：贴图文件名和命令里的 ID 经常不一致
 *     （\`apple_golden.png\` 的 ID 是 \`golden_apple\`、\`tile.grass.name\` 的 ID 是
 *     \`grass_block\`），所以只要两者冲突，一律**以官方语言文件里的命令 ID 为准**。
 *     \`WSX/Be原版材质包.zip\` 只用来核对"这个 ID 是否真的存在贴图"。
 *  3. **俗名**：官方译名往往不是玩家嘴里的说法，所以同一行会写成
 *     \`官方译名 / 俗名\`（例：\`钻石护腿 / 钻石裤\`），搜索时两种叫法都能命中。
 *
 * ── 一条重要约定 ────────────────────────────────────────────────────────────
 * 中文名**只用于显示**；真正插进命令里的永远是 [Term.id] 这个英文 ID。
 * 拿中文名去拼命令会直接生成无效命令，\`CommandCompletionTest\` 里有测试钉住这条。
 */
object Suggest {

    /**
     * 一个候选词。
     *
     * @param id   真正插入到命令里的英文 ID
     * @param name 界面显示名（官方译名，可能带 \` / \` 分隔的俗名）
     */
    data class Term(val id: String, val name: String)

    /** 目标选择器：符号本身不好记，中文名就是它的用途。 */
    val selectors: List<Pair<String, String>> = listOf(
        "@s" to "自己",
        "@a" to "所有玩家",
        "@p" to "最近的玩家",
        "@r" to "随机玩家",
        "@e" to "所有实体",
        "@n" to "最近的实体",
        "@initiator" to "点 NPC 对话按钮的玩家"
    )
`;

// 注意：这里**刻意不预置** `@e[type=zombie]`、`@a[r=10]` 这类"带完整方括号参数"的候选。
// 玩家明确要求过：选择器补全只写 `@e`，不要连方括号参数一起塞进来。
// 方括号里能写什么，由「选择器参数补全」在玩家自己打出 `[` 之后来教
// （见 SelectorLibrary / SelectorCompletion），比预置几条死例子更有用。

const EFFECTS = `    /** 常用状态效果。 */
    val effects: List<Term> = listOf(
        Term("speed", "速度提升 / 速度"),
        Term("slowness", "缓慢"),
        Term("haste", "急迫"),
        Term("mining_fatigue", "挖掘疲劳"),
        Term("strength", "力量"),
        Term("instant_health", "瞬间治疗"),
        Term("instant_damage", "瞬间伤害"),
        Term("jump_boost", "跳跃提升"),
        Term("nausea", "反胃"),
        Term("regeneration", "生命恢复 / 回血"),
        Term("resistance", "抗性提升"),
        Term("fire_resistance", "抗火"),
        Term("water_breathing", "水下呼吸"),
        Term("invisibility", "隐身"),
        Term("blindness", "失明"),
        Term("night_vision", "夜视"),
        Term("hunger", "饥饿"),
        Term("weakness", "虚弱"),
        Term("poison", "中毒"),
        Term("wither", "凋零"),
        Term("health_boost", "生命提升"),
        Term("absorption", "伤害吸收 / 吸收"),
        Term("saturation", "饱和"),
        Term("levitation", "漂浮"),
        Term("slow_falling", "缓降"),
        Term("conduit_power", "潮涌能量"),
        Term("village_hero", "村庄英雄"),
        Term("darkness", "黑暗"),
        Term("bad_omen", "不祥之兆"),
        Term("clear", "清除所有效果")
    )
`;

const PARTICLES_LEGACY_UNUSED = null;
void PARTICLES_LEGACY_UNUSED;

// 音效与粒子两张表体量很大（1671 + 192 条，且各自已按 400 条拆块），
// 由 gen-extra-terms.mjs 生成到 extra-terms.kt，这里**按显式标记整段内联**。
//
// ⚠️ 不要退回"按 `\n    /**` 切分再找含 `val sounds:` 的段"那种写法：
//    一张表拆成多块后，那样只会拿到第一块，表直接少一大半（已踩过）。
const extractTable = (text, name) => {
  const begin = `// >>> SUGGEST-TABLE:${name}`;
  const end = `// <<< SUGGEST-TABLE:${name}`;
  const i = text.indexOf(begin);
  const j = text.indexOf(end);
  if (i < 0 || j < 0) return null;
  return text.slice(i + begin.length, j).trim();
};

const PARTICLES = extractTable(extraBlock, 'particles') ?? '    val particles: List<Term> = emptyList()';
const SOUNDS = extractTable(extraBlock, 'sounds') ?? '    val sounds: List<Term> = emptyList()';
if (!extraBlock.includes('SUGGEST-TABLE:sounds')) {
  throw new Error(
    'extra-terms.kt 里没有 SUGGEST-TABLE 标记 —— 音效/粒子会内联成空表。\n' +
    '请先运行：node tools/suggest/gen-extra-terms.mjs',
  );
}

const GAMERULES = `    /** 游戏规则。 */
    val gamerules: List<Term> = listOf(
        Term("keepInventory", "保留物品栏 / 死亡不掉落"),
        Term("mobGriefing", "生物破坏方块"),
        Term("doDaylightCycle", "昼夜更替"),
        Term("doWeatherCycle", "天气更替"),
        Term("showcoordinates", "显示坐标"),
        Term("commandBlockOutput", "命令方块输出"),
        Term("sendCommandFeedback", "命令执行反馈"),
        Term("doFireTick", "火焰蔓延"),
        Term("doMobSpawning", "生物生成"),
        Term("pvp", "友军伤害 / PVP"),
        Term("naturalRegeneration", "自然生命恢复 / 自然回血"),
        Term("doMobLoot", "生物战利品 / 生物掉落"),
        Term("doTileDrops", "方块掉落"),
        Term("keepCommandBlock", "保留命令方块"),
        Term("showtags", "显示实体标签"),
        Term("doImmediateRespawn", "立即重生"),
        Term("fallDamage", "摔落伤害"),
        Term("fireDamage", "火焰伤害"),
        Term("drowningDamage", "溺水伤害"),
        Term("freezeDamage", "冰冻伤害")
    )
`;

const GAMEMODES = `    /** 游戏模式。 */
    val gameModes: List<Term> = listOf(
        Term("survival", "生存"),
        Term("creative", "创造"),
        Term("adventure", "冒险"),
        Term("spectator", "旁观")
    )
`;

const ENCHANTS = `    /** 附魔 ID。 */
    val enchantments: List<Term> = listOf(
        Term("sharpness", "锋利"),
        Term("smite", "亡灵杀手"),
        Term("bane_of_arthropods", "节肢杀手"),
        Term("efficiency", "效率"),
        Term("silk_touch", "精准采集"),
        Term("fortune", "时运"),
        Term("unbreaking", "耐久"),
        Term("mending", "经验修补"),
        Term("protection", "保护"),
        Term("fire_protection", "火焰保护"),
        Term("feather_falling", "摔落保护"),
        Term("blast_protection", "爆炸保护"),
        Term("projectile_protection", "弹射物保护"),
        Term("thorns", "荆棘"),
        Term("respiration", "水下呼吸"),
        Term("aqua_affinity", "水下速掘"),
        Term("depth_strider", "深海探索者"),
        Term("frost_walker", "冰霜行者"),
        Term("soul_speed", "灵魂疾行"),
        Term("knockback", "击退"),
        Term("fire_aspect", "火焰附加"),
        Term("looting", "抢夺"),
        Term("power", "力量（弓）"),
        Term("punch", "冲击（弓）"),
        Term("flame", "火矢"),
        Term("infinity", "无限"),
        Term("multishot", "多重射击"),
        Term("quick_charge", "快速装填"),
        Term("piercing", "穿透"),
        Term("loyalty", "忠诚"),
        Term("impaling", "穿刺"),
        Term("riptide", "激流"),
        Term("channeling", "引雷")
    )
`;

const JSON_TEMPLATES = `    /**
     * JSON 组件骨架。
     *
     * 手写 JSON 是这套命令里最容易出错的部分（引号、括号、中英混排），
     * 所以这里给的是**完整可直接用**的骨架，而不是需要玩家自己补全的半成品。
     */
    val jsonTemplates: List<Pair<String, String>> = listOf(
        "{\\"rawtext\\":[{\\"text\\":\\"你好\\"}]}" to "纯文本",
        "{\\"rawtext\\":[{\\"text\\":\\"第 \\"},{\\"score\\":{\\"name\\":\\"@s\\",\\"objective\\":\\"分数\\"}},{\\"text\\":\\" 分\\"}]}" to "文本 + 计分板分数",
        "{\\"rawtext\\":[{\\"selector\\":\\"@s\\"},{\\"text\\":\\"，欢迎！\\"}]}" to "玩家名 + 文本",
        "{\\"rawtext\\":[{\\"translate\\":\\"item.diamond_sword\\"}]}" to "翻译键（随语言变化）",
        "{\\"text\\":\\"金色粗体\\",\\"color\\":\\"gold\\",\\"bold\\":true}" to "带样式（直接用在 text 字段）",
        "{\\"item_name\\":\\"名刀·村正\\"}" to "give 的自定义名称",
        "{\\"item_name\\":\\"宝剑\\",\\"enchantments\\":[{\\"id\\":\\"sharpness\\",\\"lvl\\":5}]}" to "give 的名称 + 附魔",
        "{\\"minecraft:can_destroy\\":{\\"blocks\\":[\\"dirt\\"]}}" to "give 的可破坏方块"
    )
}
`;

// ── 组装输出 ────────────────────────────────────────────────────────────────
//
// 大表（items / blocks / sounds / particles）各自生成到**独立文件**，理由是
// JVM 的 64KB 单方法限制（详见 terms() 的注释）：
// Kotlin 会把同一文件所有顶层属性的初始化塞进一个 `<clinit>`，
// 所以"同一个文件里拆块"没用，必须拆文件。
//
// 每个大表用一个独立的 object（`SuggestItems` / `SuggestBlocks` / `SuggestSounds`
// / `SuggestParticles`），`Suggest` 里只留一句转发，调用方完全无感。
const SPLIT_TABLES = [
  { varName: 'items', rows: items, comment: '物品（用于 give / replaceitem）。', object: 'SuggestItems' },
  { varName: 'blocks', rows: blocks, comment: '方块（用于 setblock / fill）。', object: 'SuggestBlocks' },
];

/** 生成一个独立文件：`object <Name> { <分块表> }`。 */
function tableFile(objectName, varName, rows, comment) {
  // ⚠️ 表体里的 `Term(...)` 与类型注解 `List<Term>` 都要写成 `Suggest.Term` ——
  //    `Term` 是 `Suggest` 的嵌套类，独立文件里写裸名字编译不过（实测踩过）。
  const body = terms(varName, rows, comment, 400, true)
    .replace(/List<Term>/g, 'List<Suggest.Term>');
  return (
    `package com.xanan.cmdhelper.json\n\n` +
    `/**\n` +
    ` * ${comment}\n` +
    ` *\n` +
    ` * ⚠️ **由 tools/suggest/gen-suggest.mjs 生成，不要手改。**\n` +
    ` *\n` +
    ` * 单独一个文件是**必须的**，不是风格问题：Kotlin 会把同一文件所有顶层属性的\n` +
    ` * 初始化编译进一个 \`<clinit>\` 方法，而 JVM 对单个方法有 64KB 字节码上限，\n` +
    ` * 这张表有 ${rows.length} 条，和别的表挤在一个文件里会直接编译失败：\n` +
    ` * \`Method too large: com/xanan/cmdhelper/json/Suggest.<clinit> ()V\`。\n` +
    ` *\n` +
    ` * 通过 [Suggest.${varName}] 访问，调用方不用关心这里。\n` +
    ` */\n` +
    `internal object ${objectName} {\n\n` +
    body +
    `}\n`
  );
}

const splitFiles = [];
for (const t of SPLIT_TABLES) {
  splitFiles.push({ object: t.object, text: tableFile(t.object, t.varName, t.rows, t.comment) });
}

// sounds / particles 的表体由 gen-extra-terms.mjs 生成（带 SUGGEST-TABLE 标记），
// 这里按标记取出后同样放进独立文件。
const extraTables = [
  { name: 'sounds', object: 'SuggestSounds', varName: 'sounds' },
  { name: 'particles', object: 'SuggestParticles', varName: 'particles' },
];
for (const t of extraTables) {
  const body = extractTable(extraBlock, t.name);
  if (!body) continue;
  splitFiles.push({
    object: t.object,
    text:
      `package com.xanan.cmdhelper.json\n\n` +
      `/**\n` +
      ` * ${t.name} 词表。\n` +
      ` *\n` +
      ` * ⚠️ **由 tools/suggest/gen-extra-terms.mjs 生成，不要手改。**\n` +
      ` * 单独一个文件的原因同 [SuggestItems]：JVM 的 64KB 单方法上限。\n` +
      ` */\n` +
      `internal object ${t.object} {\n\n` +
      body.replace(/List<Term>/g, 'List<Suggest.Term>') +
      `\n}\n`,
  });
}

// 各表的"归属"：大表放在独立 object 里，小表直接留在 Suggest。
const ownerOf = {
  items: 'SuggestItems', blocks: 'SuggestBlocks',
  sounds: 'SuggestSounds', particles: 'SuggestParticles',
};
const ref = (varName) => {
  const owner = ownerOf[varName];
  return owner ? `${owner}.${varName}` : varName;
};

// JSON_TEMPLATES 的末尾自带 `}`（object 的闭合括号）。大表转发要插在它**之前**，
// 所以先把闭合括号摘掉，拼完再补回去 —— 比事后按位置切字符串清楚。
const JSON_TEMPLATES_BODY = JSON_TEMPLATES.replace(/\n}\n?$/, '\n');

const out = [
  HEADER,
  terms('entities', entities, '实体（用于 summon 与 @e[type=…]）。'),
  EFFECTS,
  GAMERULES,
  GAMEMODES,
  ENCHANTS,
  JSON_TEMPLATES_BODY,
  // ── 大表转发 ─────────────────────────────────────────────────────────────
  `    // ── 大表转发 ────────────────────────────────────────────────────────────\n` +
    `    // items / blocks / sounds / particles 四张表各自在独立文件里（见 SuggestItems 等），\n` +
    `    // 原因见 SuggestItems 的注释（JVM 单方法 64KB 上限）。\n` +
    `    // 这里只做转发，调用方仍然写 \`Suggest.items\`，不受影响。\n` +
    [
      ['items', '物品（用于 give / replaceitem）。'],
      ['blocks', '方块（用于 setblock / fill）。'],
      ['particles', '粒子（对应 /particle 的第一个参数）。'],
      ['sounds', '音效（对应 /playsound 的第一个参数）。'],
    ]
      .map(([n, c]) => `    /** ${c} */\n    val ${n}: List<Term> get() = ${ref(n)}`)
      .join('\n\n') +
    '\n\n' +
    // ── 能 /give 的 ID 合集：物品 ∪ 方块 ─────────────────────────────────────
    //
    // ⚠️ 这是个**真实踩过的坑**：基岩版里**方块也能 /give**
    //    （`/give @s oak_log`、`/give @s white_wool`、`/give @s stone` 都成立），
    //    而 `items` 表只收"纯物品"（方块归 `blocks`）。
    //    早先 `give` 的物品参数只查 `items`，于是玩家打 `/give @s oak_log`
    //    会被告知"没有匹配的项"，然后看到一整屏按字母序排的物品 ——
    //    看起来就是"搜不到原木"（实测在真机上复现过）。
    //
    // 合并时**以 blocks 的名字为准**：方块条目常带额外提示
    //    （`空气（用来清除方块）`、`石头 / 圆石`），信息比物品条目多。
    //    两边同名的有 33 条，distinctBy 去重后不会有重复候选。
    `    /**\n` +
    `     * **能 /give 的全部 ID** = 物品 ∪ 方块。\n` +
    `     *\n` +
    `     * 基岩版里方块也是合法的 /give 目标，所以补全必须用这张合集，\n` +
    `     * 不能只用 [items]。详见生成器里对应的注释。\n` +
    `     */\n` +
    `    val giveableItems: List<Term> = (blocks + items).distinctBy { it.id }\n`,
  '}\n',
].join('\n');

writeFileSync(outFile, out, 'utf8');
console.log('written:', outFile);

const dir = path.dirname(outFile);
for (const f of splitFiles) {
  const target = path.join(dir, `${f.object}.kt`);
  writeFileSync(target, f.text, 'utf8');
  console.log('written:', target);
}
