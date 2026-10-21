// 官方语言文件的**键模式 → 官方 ID** 映射
//
// ── 为什么需要这一层 ────────────────────────────────────────────────────────
// zh_CN.lang 的键名与命令里的 ID **不是一一对应**，而是按"族"组织的：
//
//     tile.wool.black.name         = 黑色羊毛      →  ID 是 black_wool
//     tile.planks.acacia.name      = 金合欢木板    →  ID 是 acacia_planks
//     item.boat.oak.name           = 橡木船        →  ID 是 oak_boat
//     tile.log.acacia.name         = 金合欢原木    →  ID 是 acacia_log
//     tile.concretePowder.red.name = 红色混凝土粉末 →  ID 是 red_concrete_powder
//
// 原来的 `extract.ps1` 只认 `^item\.(.+)\.name$` 且**排除含点的 id**，
// 于是上面这些整族**一条都没进词表**（实测：`white_wool`、`oak_log`、
// `acacia_planks`、所有船都不在词表里）。这是个覆盖度上的大洞，
// 不是"要不要收录"的取舍，所以这里用**确定性映射**补齐。
//
// ── 设计原则 ────────────────────────────────────────────────────────────────
//  1. 只处理**能确定映射规则**的模式；拿不准的宁可不映射（记进 unmapped，人工看）；
//  2. 颜色名要转成 ID 里的写法：`lightBlue` → `light_blue`、`silver` → `light_gray`
//     （官方 ID 用 light_gray，语言文件用 silver —— 这个坑必须显式处理）；
//  3. 输出 `id → 中文名`，与 `item.<id>.name` 那批**合并后**一起用。

/** 语言文件里的颜色段 → 官方 ID 前缀。silver 是 `light_gray` 的历史叫法。 */
const COLOR_TO_ID = {
  white: 'white', orange: 'orange', magenta: 'magenta', lightBlue: 'light_blue',
  light_blue: 'light_blue', yellow: 'yellow', lime: 'lime', pink: 'pink',
  gray: 'gray', silver: 'light_gray', lightGray: 'light_gray', cyan: 'cyan',
  purple: 'purple', blue: 'blue', brown: 'brown', green: 'green',
  red: 'red', black: 'black',
};

/** 木种段 → 官方 ID 里的写法。darkoak 与 dark_oak 两种都出现过。 */
const WOOD_TO_ID = {
  oak: 'oak', spruce: 'spruce', birch: 'birch', jungle: 'jungle',
  acacia: 'acacia', dark_oak: 'dark_oak', darkoak: 'dark_oak',
  // ⚠️ 语言文件里深色橡木还有第三种写法 `big_oak`（`item.boat.big_oak`）。
  big_oak: 'dark_oak',
  mangrove: 'mangrove', cherry: 'cherry', bamboo: 'bamboo',
  crimson: 'crimson', warped: 'warped', pale_oak: 'pale_oak', poplar: 'poplar',
};

/**
 * 珊瑚的"颜色"段其实是**种类**（`tile.coral.blue = 管珊瑚`，不是"蓝色珊瑚"）。
 * 对照来自官方语言文件本身 + 官方 ID 清单。
 * 失活珊瑚的键名是 `tile.coral_fan_dead.<色>` 与 `tile.coral_block_dead.<色>`。
 */
const CORAL_TO_ID = {
  blue: 'tube', pink: 'brain', purple: 'bubble', red: 'fire', yellow: 'horn',
};

/** 桶类：语言文件的键名与官方 ID 差别较大，逐条列出（都能在官方 Items.md 里查到）。 */
const BUCKET_TO_ID = {
  bucketWater: 'water_bucket',
  bucketLava: 'lava_bucket',
  bucketFish: 'cod_bucket',
  bucketSalmon: 'salmon_bucket',
  bucketTropical: 'tropical_fish_bucket',
  bucketPuffer: 'pufferfish_bucket',
  bucketAxolotl: 'axolotl_bucket',
  bucketTadpole: 'tadpole_bucket',
};

/**
 * 映射规则表。
 *
 * 每条：`键前缀` → 一个函数，把"剩下的段"拼成官方 ID。
 * `kind` 只用于报表分类。返回 null 表示这条映射不了（会进 unmapped）。
 */
export const PATTERNS = [
  // ── 颜色族 ────────────────────────────────────────────────────────────────
  { re: /^tile\.wool\.(.+)\.name$/, kind: 'block', id: (c) => `${COLOR_TO_ID[c]}_wool` },
  { re: /^tile\.carpet\.(.+)\.name$/, kind: 'block', id: (c) => `${COLOR_TO_ID[c]}_carpet` },
  { re: /^tile\.concrete\.(.+)\.name$/, kind: 'block', id: (c) => `${COLOR_TO_ID[c]}_concrete` },
  { re: /^tile\.concretePowder\.(.+)\.name$/, kind: 'block', id: (c) => `${COLOR_TO_ID[c]}_concrete_powder` },
  { re: /^tile\.stained_glass\.(.+)\.name$/, kind: 'block', id: (c) => `${COLOR_TO_ID[c]}_stained_glass` },
  { re: /^tile\.stained_glass_pane\.(.+)\.name$/, kind: 'block', id: (c) => `${COLOR_TO_ID[c]}_stained_glass_pane` },
  { re: /^tile\.stained_hardened_clay\.(.+)\.name$/, kind: 'block', id: (c) => `${COLOR_TO_ID[c]}_terracotta` },
  { re: /^tile\.glazedTerracotta\.(.+)\.name$/, kind: 'block', id: (c) => `${COLOR_TO_ID[c]}_glazed_terracotta` },
  { re: /^tile\.shulkerBox\.(.+)\.name$/, kind: 'block', id: (c) => `${COLOR_TO_ID[c]}_shulker_box` },
  { re: /^item\.shulker_shell\.(.+)\.name$/, kind: 'item', id: (c) => `${COLOR_TO_ID[c]}_shulker_box` },
  { re: /^item\.bed\.(.+)\.name$/, kind: 'item', id: (c) => `${COLOR_TO_ID[c]}_bed` },
  { re: /^tile\.bed\.(.+)\.name$/, kind: 'block', id: (c) => `${COLOR_TO_ID[c]}_bed` },
  { re: /^item\.banner\.(.+)\.name$/, kind: 'item', id: (c) => `${COLOR_TO_ID[c]}_banner` },
  { re: /^tile\.standing_banner\.(.+)\.name$/, kind: 'block', id: (c) => `${COLOR_TO_ID[c]}_banner` },
  { re: /^item\.cushion\.(.+)\.name$/, kind: 'item', id: (c) => `${COLOR_TO_ID[c]}_cushion` },
  { re: /^item\.harness\.(.+)\.name$/, kind: 'item', id: (c) => `${COLOR_TO_ID[c]}_harness` },
  { re: /^item\.dye\.(.+)_new\.name$/, kind: 'item', id: (c) => `${COLOR_TO_ID[c]}_dye` },
  // ⚠️ 染料有两种键写法：新的 `<色>_new`（`item.dye.black_new.name` = 黑色染料）
  //    与老的裸色名（`item.dye.blue.name` = 青金石 —— 那是**旧名字**）。
  //    这里两种都映射到 `<色>_dye`，谁先到用谁；缺了这条 `red_dye` 这类就会漏。
  { re: /^item\.dye\.(.+)\.name$/, kind: 'item', id: (c) => `${COLOR_TO_ID[c]}_dye` },

  // ── 无前缀的"单色"方块：语言文件把它们单独写了一条（没有颜色段）──────
  //     `tile.hardened_clay.name = 陶瓦` 其实就是"白色陶瓦"，
  //     官方 ID 是 `white_terracotta`。这类键不能按 id 直接取（会得到 `hardened_clay`，
  //     那个 ID 在基岩版不存在），必须映射到带颜色的官方 ID。
  { re: /^tile\.hardened_clay\.name$/, kind: 'block', id: () => 'white_terracotta' },
  { re: /^tile\.stained_hardened_clay\.name$/, kind: 'block', id: () => 'white_terracotta' },
  { re: /^tile\.glazedTerracotta\.name$/, kind: 'block', id: () => 'white_glazed_terracotta' },

  // ── 深色橡木的特殊键 ──────────────────────────────────────────────────────
  //     `tile.wood.dark_oak.name` 之类已被通用规则覆盖，这里只补
  //     `item.boat.big_oak` 那类靠 WOOD_TO_ID 里的 big_oak 处理。

  // ── 木种族 ────────────────────────────────────────────────────────────────
  { re: /^tile\.planks\.(.+)\.name$/, kind: 'block', id: (w) => `${WOOD_TO_ID[w]}_planks` },
  { re: /^tile\.log\.(.+)\.name$/, kind: 'block', id: (w) => `${WOOD_TO_ID[w]}_log` },
  { re: /^tile\.log2\.(.+)\.name$/, kind: 'block', id: (w) => `${WOOD_TO_ID[w]}_log` },
  { re: /^tile\.wood\.(.+)\.name$/, kind: 'block', id: (w) => `${WOOD_TO_ID[w]}_wood` },
  { re: /^tile\.wood\.stripped\.(.+)\.name$/, kind: 'block', id: (w) => `stripped_${WOOD_TO_ID[w]}_wood` },
  { re: /^tile\.sapling\.(.+)\.name$/, kind: 'block', id: (w) => `${WOOD_TO_ID[w]}_sapling` },
  { re: /^tile\.leaves\.(.+)\.name$/, kind: 'block', id: (w) => `${WOOD_TO_ID[w]}_leaves` },
  { re: /^tile\.wooden_slab\.(.+)\.name$/, kind: 'block', id: (w) => `${WOOD_TO_ID[w]}_slab` },
  { re: /^tile\.double_wooden_slab\.(.+)\.name$/, kind: 'block', id: (w) => `${WOOD_TO_ID[w]}_double_slab` },

  // ── 船 / 运输船（item 与 tile 两种键都出现过）────────────────────────────
  { re: /^item\.boat\.(.+)\.name$/, kind: 'item', id: (w) => `${WOOD_TO_ID[w]}_boat` },
  { re: /^item\.chest_boat\.(.+)\.name$/, kind: 'item', id: (w) => `${WOOD_TO_ID[w]}_chest_boat` },
  { re: /^item\.raft\.(.+)\.name$/, kind: 'item', id: (w) => `${WOOD_TO_ID[w]}_raft` },

  // ── 珊瑚（颜色段在这里其实是**珊瑚种类**，不是颜色）──────────────────────
  //     语言文件的键名有三套写法混用，必须都覆盖（实测查过原文）：
  //
  //       tile.coral.blue.name              = 管珊瑚          → tube_coral
  //       tile.coral.blue_dead.name         = 失活的管珊瑚    → dead_tube_coral
  //       tile.coral_block.blue.name        = 管珊瑚块        → tube_coral_block
  //       tile.coral_block.blue_dead.name   = 失活的管珊瑚块  → dead_tube_coral_block
  //       tile.coral_fan.blue_fan.name      = 管珊瑚扇        → tube_coral_fan
  //       tile.coral_fan_dead.blue_fan.name = 失活的管珊瑚扇  → dead_tube_coral_fan
  //
  //     ⚠️ 失活的两种写法**不一样**：coral / coral_block 用**后缀** `_dead`，
  //        coral_fan 用**前缀** `coral_fan_dead`。一开始只按前缀写，漏掉了
  //        dead_tube_coral_block 这一族（官方清单里有，实测对不上）。
  { re: /^tile\.coral\.(.+)_dead\.name$/, kind: 'block', id: (c) => `dead_${CORAL_TO_ID[c]}_coral` },
  { re: /^tile\.coral_block\.(.+)_dead\.name$/, kind: 'block', id: (c) => `dead_${CORAL_TO_ID[c]}_coral_block` },
  { re: /^tile\.coral\.(.+)\.name$/, kind: 'block', id: (c) => `${CORAL_TO_ID[c]}_coral` },
  { re: /^tile\.coral_block\.(.+)\.name$/, kind: 'block', id: (c) => `${CORAL_TO_ID[c]}_coral_block` },
  // 珊瑚扇：段值带 `_fan` 后缀（blue_fan），先剥掉
  { re: /^tile\.coral_fan_dead\.(.+?)(?:_fan)?\.name$/, kind: 'block', id: (c) => `dead_${CORAL_TO_ID[c]}_coral_fan` },
  { re: /^tile\.coral_fan\.(.+?)(?:_fan)?\.name$/, kind: 'block', id: (c) => `${CORAL_TO_ID[c]}_coral_fan` },

  // ── 无前缀告示牌：`item.sign.name` 就是**橡木**告示牌（官方 ID 是 oak_sign）──
  //     直接按 id 取会得到 `sign`，那个 ID 在基岩版不存在。
  { re: /^item\.sign\.name$/, kind: 'item', id: () => 'oak_sign' },
  { re: /^item\.hanging_sign\.name$/, kind: 'item', id: () => 'oak_hanging_sign' },

  // ── 红树"树苗"的官方 ID 叫 `mangrove_propagule`（红树胎生苗）──────────────
  //     `tile.mangrove_sapling.name` 按 id 直接取会得到 `mangrove_sapling`，
  //     而基岩版的真实 ID 是 `mangrove_propagule`。
  { re: /^tile\.mangrove_sapling\.name$/, kind: 'block', id: () => 'mangrove_propagule' },

  // ── 桶（键名与 ID 差别大，用显式表）──────────────────────────────────────
  {
    re: /^item\.(bucket[A-Za-z]+)\.name$/,
    kind: 'item',
    id: (k) => BUCKET_TO_ID[k] ?? `__unknown_bucket_${k}`,
  },

  // ── 刷怪蛋：`item.spawn_egg.entity.<实体>.name` → `<实体>_spawn_egg` ──────
  //    官方 Items.md 里确实有 `zombie_spawn_egg` 这类 ID，且能 /give。
  { re: /^item\.spawn_egg\.entity\.(.+)\.name$/, kind: 'item', id: (e) => `${e}_spawn_egg` },

  // ── 命名空间桶（新版本把桶改成 `<鱼>_bucket`，键名是 bucketXxx 形式已覆盖）
  { re: /^item\.bucket\.(.+)\.name$/, kind: 'item', id: (f) => `${f}_bucket` },
];

/**
 * 把语言文件的整个键值表映射成 `id → 中文名`。
 *
 * @param entries `[键, 译名]` 数组
 * @param prefixes 只处理这些前缀（`item` / `tile`）
 * @returns `{ mapped: Map<id, {name, kind, key}>, unmapped: Array<{key, name, prefix}> }`
 */
export function mapKeys(entries, prefixes = ['item', 'tile']) {
  const mapped = new Map();
  const unmapped = [];

  for (const [key, name] of entries) {
    if (!name) continue;
    if (!prefixes.some((p) => key.startsWith(p + '.'))) continue;
    if (!key.endsWith('.name')) continue;

    let hit = false;
    for (const p of PATTERNS) {
      const m = p.re.exec(key);
      if (!m) continue;
      const id = p.id(m[1]);
      // 段值不认识（例如新木种）或映射成了占位符 → 交给 unmapped，不猜
      if (!id || id.includes('undefined') || id.startsWith('__unknown')) break;
      if (!mapped.has(id)) mapped.set(id, { name, kind: p.kind, key });
      hit = true;
      break;
    }
    if (hit) continue;

    // 没命中映射规则的：只有"键去掉前缀后不含点"的才能直接当 ID
    const mid = key.slice(key.indexOf('.') + 1, -'.name'.length);
    if (!mid.includes('.') && !/[A-Z]/.test(mid)) {
      const kind = key.startsWith('tile.') ? 'block' : 'item';
      if (!mapped.has(mid)) mapped.set(mid, { name, kind, key });
      continue;
    }
    // 其余（含点但没规则、或含大写）都算 unmapped，人工看
    unmapped.push({ key, name, prefix: key.slice(0, key.indexOf('.')) });
  }

  return { mapped, unmapped };
}
