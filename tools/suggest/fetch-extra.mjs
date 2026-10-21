// 拉取官方数据，生成 tools/suggest/sounds.csv 与 particles.csv
//
// 数据来源（都是 Mojang 官方 bedrock-samples 仓库）：
//   * resource_pack/particles/*.json          → 粒子 ID 清单
//   * resource_pack/sounds/sound_definitions.json → 音效 ID + subtitle 键
//   * resource_pack/texts/zh_CN.lang          → 中文（subtitle 键在这里有官方译文）
//
// 中文名的取法：音效定义里带 `subtitle`，那是**字幕键**，zh_CN.lang 有它的官方中文。
// 例：`random.levelup` 的 subtitle 是 `subtitles.entity.player.levelup` → "升级了"。
// 没有 subtitle 的（循环环境音等）就没有官方中文，回退成我们自己写的说明 ——
// 这类会标注 `src=manual`，跟官方译文区分开，不冒充官方。
//
// 用法: node tools/suggest/fetch-extra.mjs

import { writeFileSync, readFileSync, existsSync } from 'node:fs';

const DIR = 'D:\\Agent\\Deepseek\\CommandAssistant\\tools\\suggest';
const LANG_PATH = `${DIR}\\zh_CN.lang`;
const RAW = 'https://raw.githubusercontent.com/Mojang/bedrock-samples/main/resource_pack';
const API = 'https://api.github.com/repos/Mojang/bedrock-samples/contents/resource_pack';

async function getText(url) {
  const res = await fetch(url, { headers: { 'User-Agent': 'dsh-command-assistant' } });
  if (!res.ok) throw new Error(`${res.status} ${url}`);
  return res.text();
}

// ── 中文语言文件 ──────────────────────────────────────────────────────────
let langText;
if (existsSync(LANG_PATH)) {
  langText = readFileSync(LANG_PATH, 'utf8');
  console.log('语言文件：用本地存档');
} else {
  langText = await getText(`${RAW}/texts/zh_CN.lang`);
  writeFileSync(LANG_PATH, langText, 'utf8');
  console.log('语言文件：已下载');
}
const lang = new Map();
for (const line of langText.split(/\r?\n/)) {
  const i = line.indexOf('=');
  if (i > 0) lang.set(line.slice(0, i), line.slice(i + 1));
}

// ── 粒子清单 ──────────────────────────────────────────────────────────────
const particleFiles = JSON.parse(await getText(`${API}/particles`));
const particleIds = particleFiles
  .filter((f) => f.name.endsWith('.json'))
  .map((f) => f.name.replace(/\.json$/, ''))
  .sort();
console.log('粒子文件数 =', particleIds.length);

// 粒子没有官方中文（zh_CN.lang 里没有 particle.* 键），中文名由下面这张表提供。
// 只覆盖玩家真正会用的那些，其余保持英文 ID 显示（不硬编瞎翻译）。
const PARTICLE_CN = {
  basic_flame: '基础火焰',
  basic_smoke: '基础烟雾',
  basic_bubble: '基础气泡',
  basic_bubble_manual: '基础气泡（手动）',
  basic_crit: '基础暴击',
  basic_portal: '基础传送门',
  ballon_gas: '气球气体',
  balloon_gas: '气球气体',
  bleach: '漂白',
  block_destruct: '方块破坏',
  block_slide: '方块滑落',
  blue_flame: '蓝色火焰',
  breaking_item_icon: '物品损坏（图标）',
  breaking_item_terrain: '物品损坏（方块）',
  bubble_column_bubble: '气泡柱气泡',
  bubble_column_down: '气泡柱（下沉）',
  bubble_column_up: '气泡柱（上浮）',
  campfire_smoke: '营火烟雾',
  campfire_smoke_tall: '营火烟雾（高）',
  candle_flame: '蜡烛火焰',
  cauldron_bubble: '炼药锅气泡',
  cauldron_spell: '炼药锅药水',
  conduit_block: '潮涌核心方块',
  conduit_block_active: '潮涌核心方块（激活）',
  crit: '暴击',
  crop_growth: '作物生长',
  dragon_breath: '龙息',
  dragon_breath_trail: '龙息轨迹',
  dragon_death_explosion_emitter: '末影龙死亡爆炸',
  dragon_fireball: '末影龙火球',
  dust_plume: '尘土飞扬',
  electric_spark: '电火花',
  enchantment_table: '附魔台',
  end_chest: '末影箱',
  evaporation: '蒸发',
  explosion: '爆炸',
  explosion_manual: '爆炸（手动）',
  eye_of_ender: '末影之眼',
  falling_border_dust: '边界尘埃',
  falling_dust: '下落尘埃',
  fireworks_rocket: '焰火火箭',
  fireworks_starter: '焰火起始',
  fish_hook: '鱼钩',
  glow: '发光',
  green_flame: '绿色火焰',
  guardian_attack: '守卫者攻击',
  guardian_water_move: '守卫者水流',
  heart: '爱心',
  honey_drip: '蜂蜜滴落',
  huge_explosion_lab_misc: '巨型爆炸（杂项）',
  huge_explosion_level: '巨型爆炸',
  ice_evaporation: '冰蒸发',
  ink: '墨汁',
  knockback_roar: '击退怒吼',
  lava_drip: '岩浆滴落',
  lava_particle: '岩浆粒子',
  llama_spit: '羊驼口水',
  magic_critical_hit: '魔法暴击',
  mob_block: '生物方块',
  mob_flame: '生物火焰',
  mob_portal: '生物传送门',
  mob_spell: '生物药水',
  mushroom_spore: '蘑菇孢子',
  nautilus: '鹦鹉螺',
  note: '音符',
  obsidian_glow_dust: '黑曜石辉光尘埃',
  pointed_dripstone_drip: '钟乳石滴落',
  portal_directional: '传送门（定向）',
  redstone_dust: '红石尘埃',
  rising_border_dust: '边界尘埃（上升）',
  sculk_charge: '幽匿充能',
  sculk_charge_pop: '幽匿充能爆裂',
  sculk_sensor_redstone_particle: '幽匿感测体红石',
  sculk_soul: '幽匿灵魂',
  shriek: '尖啸',
  silverfish_grow: '蠹虫生长',
  smoke: '烟雾',
  sneeze: '喷嚏',
  snowflake: '雪花',
  soul: '灵魂',
  soul_particle: '灵魂粒子',
  sparkler: '手持烟花',
  spell: '药水效果',
  spit: '唾液',
  squib: '爆炸火花',
  storm_explosion: '风暴爆炸',
  totem: '不死图腾',
  trail: '轨迹',
  villager_happy: '村民高兴',
  villager_unhappy: '村民不高兴',
  water_drip: '水滴落',
  water_evaporation: '水蒸发',
  water_splash: '水花',
  water_splash_manual: '水花（手动）',
  water_wake: '水面波纹',
  wax: '蜡',
  wax_particle: '蜡粒子',
  wind_explosion_emitter: '风爆',
  wither_boss_dust: '凋灵尘埃',
  breeze_ground_particle: '旋风人地面',
  breeze_wind_explosion: '旋风人风爆',
  redstone_wire_dust: '红石线尘埃',
  lightning: '闪电',
  wax_particle_2: '蜡粒子 2',
};

const particleRows = [['id', 'name', 'src']];
for (const id of particleIds) {
  const cn = PARTICLE_CN[id];
  particleRows.push([id, cn ?? id, cn ? 'manual' : 'none']);
}
writeFileSync(`${DIR}\\particles.csv`, particleRows.map((r) => r.map(csvCell).join(',')).join('\n'), 'utf8');
console.log('已写 particles.csv，行数 =', particleRows.length - 1,
  '（有中文名 =', particleRows.filter((r) => r[2] === 'manual').length, '）');

// ── 音效 ──────────────────────────────────────────────────────────────────
const defs = JSON.parse(await getText(`${RAW}/sounds/sound_definitions.json`));
const all = Object.entries(defs.sound_definitions ?? {});
console.log('音效定义总数 =', all.length);

// 过滤：循环/环境/UI 内部音不算"玩家会主动播放的音效"，
// 单独播放它们没有意义（很多是 stream 的长音频）。
const INTERNAL = /(\.loop$|\.mood$|\.additions$|^ambient\.|^music\.|^ui\.|^internal\.|^note\.|\.ambient$|^record\.|^item\.record)/;
const isStream = (v) => JSON.stringify(v?.sounds ?? '').includes('"stream":true');

const soundRows = [['id', 'name', 'src', 'category']];
let withCn = 0;
for (const [id, def] of all) {
  if (INTERNAL.test(id)) continue;
  if (isStream(def)) continue;

  // 官方中文：definition 里的 subtitle 键 → zh_CN.lang
  const subtitleKey = def.subtitle;
  const cn = subtitleKey ? lang.get(subtitleKey) : undefined;
  if (cn && cn.trim()) withCn++;
  // 没有官方译文的先留空，稍后由 gen-suggest 里的手工表补，或直接用 ID
  soundRows.push([id, cn && cn.trim() ? cn.trim() : '', cn && cn.trim() ? 'official' : 'none', def.category ?? '']);
}

writeFileSync(`${DIR}\\sounds.csv`, soundRows.map((r) => r.map(csvCell).join(',')).join('\n'), 'utf8');
console.log('已写 sounds.csv，行数 =', soundRows.length - 1, '（有官方中文 =', withCn, '）');

function csvCell(v) {
  const s = String(v ?? '');
  return /[",\n]/.test(s) ? '"' + s.replace(/"/g, '""') + '"' : s;
}
