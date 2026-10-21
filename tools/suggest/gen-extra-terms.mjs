// 生成 Suggest.kt 里的 sounds / particles 两张词表，追加到 gen-suggest.mjs 的输出
//
// 数据来源（由 tools/suggest/fetch-extra.mjs 拉取）：
//   * particles.csv  —— 官方 resource_pack/particles/*.json 的全部粒子
//   * sounds.csv     —— 官方 sound_definitions.json 的音效，中文取自 subtitle 键的官方译文
//
// 音效中文名为什么用"字幕"：官方没有给音效起中文名，但给**字幕**起了
// （`subtitles.entity.player.levelup` = "升级了"），而音效定义里正好带 `subtitle` 字段。
// 这是唯一能拿到官方中文的路径。少数音效没有 subtitle，就用手工说明表补，
// 并在报告里如实说明这部分不是官方译文。
//
// 用法: node tools/suggest/gen-extra-terms.mjs

import { readFileSync, writeFileSync } from 'node:fs';

const DIR = 'D:\\Agent\\Deepseek\\CommandAssistant\\tools\\suggest';
const OUT = `${DIR}\\extra-terms.kt`;

function readCsv(name) {
  const lines = readFileSync(`${DIR}\\${name}`, 'utf8').split(/\r?\n/).filter(Boolean);
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

// ── 粒子中文名补充表（官方没有粒子译名，这部分是我们的说明）───────────────
const PARTICLE_CN = {
  arrowspell: '箭矢法术',
  biome_tinted_leaves_particle: '生物群系树叶',
  camera_shoot_explosion: '相机拍摄爆炸',
  cauldron_splash: '炼药锅水花',
  cauldronspell: '炼药锅药水',
  cherry_leaves_particle: '樱花树叶',
  colored_flame: '彩色火焰',
  conduit: '潮涌核心',
  conduit_absorb: '潮涌核心吸收',
  conduit_attack: '潮涌核心攻击',
  creaking_crumble_body: '嘎枝崩解（身体）',
  creaking_crumble_head: '嘎枝崩解（头）',
  creaking_heart_trail: '嘎枝之心轨迹',
  critical_hit: '暴击',
  crop_growth_area: '作物生长（区域）',
  dolphin_move_particle: '海豚游动',
  dragon_breath_fire: '龙息火焰',
  dragon_breath_lingering: '龙息残留',
  dragon_death_explosion: '末影龙死亡爆炸',
  dragon_destroy_block: '末影龙破坏方块',
  dragon_dying_explosion: '末影龙垂死爆炸',
  dripstone_lava_drip: '钟乳石岩浆滴落',
  dripstone_water_drip: '钟乳石水滴落',
  enchanting_table_particle: '附魔台',
  endrod: '末地烛光',
  evaporation_elephant_toothpaste: '蒸发（特效）',
  evocation_fang: '唤魔者尖牙',
  evoker_spell: '唤魔者法术',
  explosion_cauldron: '爆炸（炼药锅）',
  explosion_death: '爆炸（死亡）',
  explosion_egg_destroy: '爆炸（蛋破碎）',
  explosion_eyeofender_death: '爆炸（末影之眼）',
  explosion_labtable_fire: '爆炸（实验台火焰）',
  explosion_level: '爆炸',
  eye_of_ender_bubble: '末影之眼气泡',
  eyeblossom_close: '眼眸花闭合',
  eyeblossom_open: '眼眸花开放',
  falling_dust_concrete_powder: '下落尘埃（混凝土粉末）',
  falling_dust_dragon_egg: '下落尘埃（龙蛋）',
  falling_dust_gravel: '下落尘埃（沙砾）',
  falling_dust_red_sand: '下落尘埃（红沙）',
  falling_dust_sand: '下落尘埃（沙子）',
  falling_dust_scaffolding: '下落尘埃（脚手架）',
  falling_dust_top_snow: '下落尘埃（顶层雪）',
  firefly_particle: '萤火虫',
  fish_pos: '鱼群定位',
  'geyser_base.particle': '间歇泉底部',
  'geyser_plume.particle': '间歇泉喷柱',
  'geyser_poof.particle': '间歇泉喷发',
  infested_ambient: '感染环境',
  infested_emitter: '感染发射',
  lab_table_heatblock_dust: '实验台热块尘埃',
  lab_table_misc_mystical: '实验台神秘粒子',
  large_explosion_level: '大爆炸',
  magnesium_salts: '镁盐',
  mob_block_spawn: '生物方块生成',
  mobflame: '生物火焰',
  mobflame_single: '生物火焰（单个）',
  mobspell: '生物药水',
  mobspell_ambient: '生物药水（环境）',
  mobspell_lingering: '生物药水（残留）',
  mycelium_dust: '菌丝尘埃',
  nautilus_bubbles: '鹦鹉螺气泡',
  nectar_drip: '花蜜滴落',
  noxious_gas_particle: '毒气',
  obsidian_tear: '黑曜石泪',
  ominous_spawning_particle: '不祥生成',
  oozing_ambient: '渗浆环境',
  oozing_emitter: '渗浆发射',
  orange_poplar_leaves_particle: '橙杨树叶',
  pale_oak_leaves_particle: '苍白橡树叶',
  pause_mob_growth: '生物生长暂停',
  phantom_trail: '幻翼轨迹',
  pointing_and_laughing: '指向与大笑',
  portal_directional_manual: '传送门（定向，手动）',
  powder_snow_dust: '细雪尘埃',
  rain_splash: '雨滴水花',
  reset_mob_growth: '生物生长重置',
  sculk_charge_pop_manual: '幽匿充能爆裂（手动）',
  shulker_bullet: '潜影贝子弹',
  shulker_box: '潜影盒',
  shulker_peek: '潜影贝窥视',
  sculk_sensor_redstone: '幽匿感测体红石',
  small_explosion_level: '小爆炸',
  soul_manual: '灵魂（手动）',
  spit_manual: '唾液（手动）',
  sulfur_cube_landing: '硫方怪落地',
  sulfur_cube_small_landing: '小硫方怪落地',
  sulfur_cube_landing_manual: '硫方怪落地（手动）',
  sulfur_water_evaporation: '硫水蒸发',
  trial_spawner_detection: '试炼刷怪笼侦测',
  trial_spawner_detection_ominous: '试炼刷怪笼侦测（不祥）',
  trial_spawner_spawning: '试炼刷怪笼生成',
  trial_spawner_spawning_ominous: '试炼刷怪笼生成（不祥）',
  turtle_egg_particle: '海龟蛋',
  villager_happy_manual: '村民高兴（手动）',
  villager_unhappy_manual: '村民不高兴（手动）',
  wax_particle_manual: '蜡粒子（手动）',
  wind_explosion: '风爆',
  wind_explosion_manual: '风爆（手动）',
  wolf_armor_crack: '狼铠破损',
  lightning_bolt: '闪电',
  lightning_bolt_manual: '闪电（手动）',
  note_manual: '音符（手动）',
  blue_flame_particle: '蓝色火焰',
  colored_flame_particle: '彩色火焰',
  dragon_breath: '龙息',
};

// ── 音效中文名补充表（官方无字幕的那 64 个，说明性翻译，不冒充官方）──────
const SOUND_CN = {
  'block.false_permissions': '权限方块（拒绝）',
  'block.loom.use': '织布机使用',
  'block.potent_sulfur.geyser_continuous_eruption_active': '强效硫磺间歇泉持续喷发',
  'block.potent_sulfur.geyser_eruption_active': '强效硫磺间歇泉喷发',
  'block.shelf_mushroom.land': '架生菇落地',
  'block.stonecutter.use': '切石机使用',
  'block.straw_bed.jump': '草床跳跃',
  'block.straw_bed.land': '草床落地',
  'bubble.down': '气泡下沉',
  'camera.take_picture': '相机拍照',
  'component.jump_to_block': '跳转方块（提示）',
  'conduit.short': '潮涌核心（短）',
  'fall.egg': '鸡蛋摔落',
  'mob.baby_nautilus.swim': '幼年鹦鹉螺游动',
  'mob.big_pig.death': '大猪死亡',
  'mob.big_pig.eat': '大猪进食',
  'mob.big_pig.hurt': '大猪受伤',
  'mob.big_pig.say': '大猪叫声',
  'mob.copper_golem.chest_interaction.put_item': '铜傀儡放物品进箱子',
  'mob.copper_golem.chest_interaction.put_item_fail': '铜傀儡放物品失败',
  'mob.copper_golem.chest_interaction.take_item': '铜傀儡从箱子取物品',
  'mob.copper_golem.chest_interaction.take_item_fail': '铜傀儡取物品失败',
  'mob.fish.step': '鱼的脚步声',
  'mob.ghast.affectionate_scream': '恶魂亲昵尖叫',
  'mob.happy_ghast.ride': '善念恶魂骑乘',
  'mob.hoglin.howl': '疣猪兽嚎叫',
  'mob.imitate.wolf': '模仿狼叫',
  'mob.irongolem.crack': '铁傀儡开裂',
  'mob.mini_pig.death': '迷你猪死亡',
  'mob.mini_pig.eat': '迷你猪进食',
  'mob.mini_pig.hurt': '迷你猪受伤',
  'mob.mini_pig.say': '迷你猪叫声',
  'mob.moody_cow.death': '阴沉牛死亡',
  'mob.moody_cow.hurt': '阴沉牛受伤',
  'mob.moody_cow.say': '阴沉牛叫声',
  'mob.nautilus.nautilus_saddle': '鹦鹉螺鞍具',
  'mob.nautilus.ride': '鹦鹉螺骑乘',
  'mob.parrot.step': '鹦鹉脚步声',
  'mob.pause_growth': '生物生长暂停',
  'mob.picky_chicken.death': '挑食鸡死亡',
  'mob.picky_chicken.hurt': '挑食鸡受伤',
  'mob.picky_chicken.say': '挑食鸡叫声',
  'mob.pig.boost': '猪加速',
  'mob.pig.eat': '猪进食',
  'mob.reset_growth': '生物生长重置',
  'mob.royal_cat.death': '皇家猫死亡',
  'mob.royal_cat.hurt': '皇家猫受伤',
  'mob.royal_cat.purreow': '皇家猫呼噜喵',
  'mob.strider.panic': '炽足兽惊慌',
  'mob.sulfur_cube.landing': '硫方怪落地',
  'mob.sulfur_cube.small.landing': '小硫方怪落地',
  'mob.turtle_baby.born': '幼年海龟孵化',
  'mob.unsaddle': '卸下鞍具',
  'mob.zombiepig.zpig': '僵尸猪灵叫声',
  'mob.zombiepig.zpigangry': '僵尸猪灵愤怒',
  'mob.zombiepig.zpigdeath': '僵尸猪灵死亡',
  'mob.zombiepig.zpighurt': '僵尸猪灵受伤',
  'random.screenshot': '截图',
  'random.toast': '提示弹窗',
  'random.toast_recipe_unlocking_in': '配方解锁弹窗（进入）',
  'random.toast_recipe_unlocking_out': '配方解锁弹窗（离开）',
  'random.totem': '不死图腾',
  'step.web': '蜘蛛网脚步声',
  'vr.stutterturn': 'VR 转身卡顿',
};

// ── 音效的"可搜索别名"────────────────────────────────────────────────────
// 音效中文名取自官方**字幕**，而字幕文案经常完全看不出是什么音效：
// `random.levelup` 的字幕是"玩家叮当作响"，玩家永远不会去搜这个词。
// 所以给最常用的那些补上玩家真正会打的叫法，追加在官方名后面（用 / 分隔），
// 显示与搜索都能用。**官方名在前，不改动官方译名。**
//
// 只加有把握的：必须是我能确定对应关系的 ID，宁缺毋滥。
const SOUND_ALIASES = {
  'random.levelup': '升级音效',
  'random.orb': '经验球',
  'random.pop': '捡起物品',
  'random.click': '按钮点击',
  'random.explode': '爆炸',
  'random.fizz': '嘶嘶声',
  'random.glass': '玻璃碎裂',
  'random.break': '物品损坏',
  'random.bow': '射箭',
  'random.hurt': '受伤',
  'random.splash': '水花',
  'random.swim': '游泳',
  'random.totem': '不死图腾',
  'random.toast': '提示弹窗',
  'random.anvil_use': '铁砧使用',
  'random.anvil_land': '铁砧落地',
  'random.anvil_break': '铁砧损坏',
  'random.chestopen': '打开箱子',
  'random.chestclosed': '关闭箱子',
  'random.door_open': '开门',
  'random.door_close': '关门',
  'random.eat': '进食',
  'random.burp': '打嗝',
  'random.drink': '喝药水',
  'random.fizz': '嘶嘶声',
  'random.hurt': '受伤',
  'note.harp': '音符盒（竖琴）',
  'note.bass': '音符盒（贝斯）',
  'note.bd': '音符盒（大鼓）',
  'note.snare': '音符盒（小军鼓）',
  'note.hat': '音符盒（鼓边）',
  'note.pling': '音符盒（电钢琴）',
  'note.bell': '音符盒（钟琴）',
  'note.flute': '音符盒（长笛）',
  'note.chime': '音符盒（钟）',
  'note.guitar': '音符盒（吉他）',
  'note.xylobone': '音符盒（木琴）',
  'note.iron_xylophone': '音符盒（铁木琴）',
  'note.cow_bell': '音符盒（牛铃）',
  'note.didgeridoo': '音符盒（迪吉里杜管）',
  'note.bit': '音符盒（方波）',
  'note.banjo': '音符盒（班卓琴）',
};

/**
 * 音效名的清洗：官方字幕里有不少是 `方块：损坏` 这种"类别 + 冒号 + 动作"，
 * 直接当名字太长；但也有信息量（说明是方块音），所以保留、只做去重与截断。
 */
function cleanSoundName(id, raw) {
  let name = (raw ?? '').trim();
  if (name) {
    // 全角/半角冒号统一，去掉首尾多余空格
    name = name.replace(/\s+/g, ' ').replace(/^[:：\s]+/, '');
  } else {
    name = SOUND_CN[id] ?? id;
  }
  // 追加可搜索别名（去重：别名与官方名相同时不加）
  const alias = SOUND_ALIASES[id];
  if (alias && !name.includes(alias)) name = `${name} / ${alias}`;
  return name;
}

const kotlinString = (s) => `"${String(s ?? '').replace(/\\/g, '\\\\').replace(/"/g, '\\"')}"`;

/**
 * 生成一张词表；**超过 400 条就拆成多个顶层 val**。
 *
 * ⚠️ 拆分不是"优化"，是必须的：Kotlin 的 `val x = listOf(1600 个 Term(...))`
 * 会把全部构造代码编进一个 `Suggest.<clinit>` 方法，而 **JVM 对单个方法有 64KB
 * 字节码硬限制**，超了直接编译失败：
 * ```
 * e: Method too large: com/xanan/cmdhelper/json/Suggest.<clinit> ()V
 * ```
 * 音效有 1671 条，早就该拆了 —— 之前没炸是因为 `Suggest` 里其它表的构造
 * 恰好还没把总量推过线（补录方块/物品之后就过了）。
 *
 * 每个块有自己的初始化方法，单方法远低于上限；对外仍是一个 `List<Term>`。
 *
 * @param qualify `Term` 写成 `Suggest.Term` —— 这张表最终被放进**独立文件**
 *                （SuggestSounds.kt / SuggestParticles.kt），而 `Term` 是嵌在
 *                `Suggest` 对象里的嵌套类，另一个文件写裸 `Term` 会编译失败。
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

// ── 粒子 ──────────────────────────────────────────────────────────────────
const particlesRaw = readCsv('particles.csv');
const particles = particlesRaw.map((r) => ({
  id: `minecraft:${r.id}`,
  name: PARTICLE_CN[r.id] ?? r.name ?? r.id,
}));
// 少数几个"手动/发射器"变体是给命令方块用的内部名，保留但排在后面
particles.sort((a, b) => {
  const am = a.id.includes('manual') ? 1 : 0;
  const bm = b.id.includes('manual') ? 1 : 0;
  return am - bm || a.id.localeCompare(b.id);
});

// ── 音效 ──────────────────────────────────────────────────────────────────
const soundsRaw = readCsv('sounds.csv');
const sounds = soundsRaw.map((r) => ({ id: r.id, name: cleanSoundName(r.id, r.name) }));
const manualCount = soundsRaw.filter((r) => r.src !== 'official').length;
console.log(`音效 ${sounds.length} 个（官方字幕译文 ${sounds.length - manualCount}，手工说明 ${manualCount}）`);
console.log(`粒子 ${particles.length} 个（有中文名 ${particles.filter((p) => p.name !== p.id).length}）`);

// 没中文名的粒子列出来提醒
const stillId = particles.filter((p) => p.name === p.id);
if (stillId.length) {
  console.log('⚠️ 仍无中文名的粒子：', stillId.map((p) => p.id).join(', '));
}
const stillSound = sounds.filter((s) => s.name === s.id);
if (stillSound.length) {
  console.log('⚠️ 仍无中文名的音效：', stillSound.map((s) => s.id).join(', '));
}

// ⚠️ 用**显式标记**把每张表包起来，供 gen-suggest.mjs 原样内联。
//
// 早先是"按 `\n    /**` 切分再找含 `val sounds:` 的那一段"，但一张表拆成多块之后，
// 那段文本里既有 `private val soundsPart0` 又有 `val sounds: List<Term> get() = ...`，
// 按注释切就会把一张表切成十几段、只内联到第一段（表直接少一大半）。
// 标记是确定性的，拆分与不拆分都成立。
const wrap = (name, text) =>
  `// >>> SUGGEST-TABLE:${name}\n${text.trimEnd()}\n// <<< SUGGEST-TABLE:${name}\n`;

writeFileSync(
  OUT,
  '// 由 tools/suggest/gen-extra-terms.mjs 生成，不要手改（内容会被 gen-suggest.mjs 内联进 Suggest.kt）\n\n'
    + wrap('sounds', terms('sounds', sounds, '音效（对应 /playsound 的第一个参数）。中文名取自官方字幕译文，没有字幕的手工说明。', 400, true))
    + '\n'
    + wrap('particles', terms('particles', particles, '粒子（对应 /particle 的第一个参数）。官方没有粒子译名，中文名是说明性的。', 400, true)),
  'utf8',
);
console.log('已写', OUT);
