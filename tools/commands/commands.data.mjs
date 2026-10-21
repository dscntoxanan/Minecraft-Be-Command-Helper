// 基岩版命令的数据表 —— 由 gen-commands.mjs 编译成 CommandLibrary.kt
//
// 每条命令在这里是**一条数据**，只有生成逻辑特殊的才写 variants。
// 参数简写：{ k:键, n:标签, t:类型, r:必填, d:默认值, c:固定候选, h:提示, s:词表候选 }
// 类型 t: text | long | number | bool | choice | json | selector（默认 text）
// 词表候选 s: items | blocks | entities | effects | particles | sounds | gameModes
//            | gamerules | blockStates | giveComponents
// 中文名 cn: 让玩家能打「/粒子」而不是「/particle」来找命令，见 COMMAND_CN。

export const CAT = {
  text: '文本显示',
  item: '物品',
  score: '计分板',
  world: '世界与方块',
  entity: '实体与效果',
  fx: '特效与音效',
  player: '玩家与游戏规则',
};

/**
 * 命令的**中文名 / 别名**表。
 *
 * 为什么要有这张表：玩家在输入框打 `/粒子` 想找 `particle`，但补全一直只按
 * 英文关键字匹配，于是「一条都没有」——用户实测报过这个问题。
 * 这里给每条命令加中文名（以及玩家更可能打的口语名），
 * **两者都能搜到**；插入的仍然永远是英文关键字。
 *
 * 多个别名用 `/` 分隔。命名尽量贴官方或玩家常用说法。
 */
export const COMMAND_CN = {
  titleraw: '标题 / 大字 / 屏幕文字',
  tellraw: '聊天消息 / 发消息',
  title: '标题文字',
  say: '广播 / 喊话',
  tell: '私聊 / 悄悄话',
  me: '动作发言',

  give: '给予 / 给物品',
  replaceitem: '替换物品',
  clear: '清空物品 / 清除物品',

  'scoreboard objectives add': '计分板目标 新建',
  'scoreboard objectives remove': '计分板目标 删除',
  'scoreboard objectives list': '计分板目标 列表',
  'scoreboard objectives setdisplay': '计分板目标 显示',
  'scoreboard players set': '计分板 设置分数',
  'scoreboard players add': '计分板 加分',
  'scoreboard players remove': '计分板 减分',
  'scoreboard players reset': '计分板 重置分数',
  'scoreboard players test': '计分板 检测分数',
  'scoreboard players operation': '计分板 运算',
  'scoreboard players random': '计分板 随机分数',
  'scoreboard players list': '计分板 列出分数',

  setblock: '放置方块 / 设置方块',
  fill: '填充方块 / 区域填充',
  clone: '复制区域 / 克隆',
  'time set': '设置时间',
  'time add': '推进时间',
  weather: '天气',
  difficulty: '难度',
  locate: '查找结构 / 定位',
  setworldspawn: '设置出生点',
  gamerule: '游戏规则',
  tickingarea: '常加载区域',
  'structure save': '结构 保存',
  'structure load': '结构 加载',
  'structure delete': '结构 删除',
  'time query': '查询时间',
  'weather query': '查询天气',

  effect: '状态效果 / 药水效果',
  summon: '生成生物 / 刷怪',
  kill: '清除实体 / 杀死',
  damage: '造成伤害',
  tag: '标签',
  testfor: '检测实体',
  ride: '骑乘',
  tp: '传送',
  // `teleport` 与 `tp` 是同一条命令的两个名字，别名不能完全一样 ——
  // 否则 `/传送` 该匹配谁就成了平局（实测比出来的是 teleport）。
  // 这里让简称独占"传送"，全名用更长的说法。
  teleport: '完整传送',

  particle: '粒子 / 特效粒子',
  playsound: '音效 / 播放声音',
  stopsound: '停止音效',
  camera: '镜头 / 运镜',
  'camerashake add': '镜头抖动',
  'camerashake stop': '停止镜头抖动',
  fog: '迷雾',
  'music play': '播放音乐',
  'music stop': '停止音乐',
  'music volume': '音乐音量',
  'music queue': '音乐 排队',

  gamemode: '游戏模式',
  xp: '经验',
  execute: '执行 / 条件执行',
  spreadplayers: '随机散布',
  inputpermission: '输入权限',
  'dialogue open': '对话 打开',
  'dialogue change': '对话 切换',
  reload: '重载',
  function: '函数',
  'schedule on_area_loaded add': '计划任务 区域加载',
  'schedule on_area_loaded clear': '计划任务 取消区域加载',
  'schedule delay add': '计划任务 延迟',
  'schedule delay clear': '计划任务 取消延迟',
  'schedule clear': '计划任务 清除',
  'gametest run': '游戏测试',
  'gametest runthis': '游戏测试 最近',
  'gametest runthese': '游戏测试 范围内',
  'gametest runset': '游戏测试 测试类',
  'gametest runsetuntilfail': '游戏测试 失败即停',
  'gametest clearall': '游戏测试 清理',
  'gametest create': '游戏测试 创建',
  'gametest pos': '游戏测试 定位',
  'gametest stopall': '游戏测试 停止全部',

  // ── 第二轮按维基补的基岩版命令 ────────────────────────────────────────────
  // 这些在维基的 "Available in Bedrock Edition and Minecraft Education" 表里
  // BE 列都是 yes（含 BE & Edu 共有那一组）。别名给玩家更可能打的说法。
  help: '帮助 / 命令帮助',
  ability: '玩家能力 / 飞行隐身',
  allowlist: '服务器白名单 / 允许名单',
  aimassist: '瞄准辅助',
  alwaysday: '锁定昼夜 / 永昼',
  daylock: '锁定昼夜 / 永昼',
  changesetting: '服务器设置',
  clearspawnpoint: '清除出生点',
  connect: '连接服务器 / WebSocket',
  wsserver: '连接 WebSocket',
  controlscheme: '控制方案',
  dedicatedwsserver: '专用服务器连接',
  deop: '撤销管理员 / 取消OP',
  'editor-allowlist': '编辑器白名单',
  enchant: '附魔 / 给手持物品附魔',
  event: '实体事件',
  gametips: '游戏提示',
  hud: '界面元素 / HUD',
  immutableworld: '世界不可变 / 锁世界',
  kick: '踢出玩家 / 踢人',
  list: '玩家列表 / 在线玩家',
  loot: '战利品 / 掉落物品',
  mobevent: '生物事件',
  msg: '私聊 / 发消息',
  op: '授予管理员 / 给OP',
  permission: '权限重载',
  party: '队伍聊天',
  place: '放置结构 / 放地物',
  playanimation: '播放动画',
  project: '编辑器项目',
  recipe: '配方 / 解锁配方',
  reloadconfig: '重载配置',
  reloadpacketlimitconfig: '重载包限制配置',
  save: '存档备份',
  script: '脚本调试',
  scriptevent: '脚本事件',
  sendshowstoreoffer: '商店优惠',
  set_movement_authority: '移动校验',
  setmaxplayers: '最大玩家数',
  spawnpoint: '个人出生点 / 设置重生点',
  stop: '停止服务器',
  testforblock: '检测方块',
  testforblocks: '检测区域方块',
  toggledownfall: '切换天气',
  transfer: '转移服务器',
  worldbuilder: '世界建造者 / 编辑受限方块',
};

const commands = [];
const tpl = (keyword, description, category, params, template, opts = {}) =>
  commands.push({ keyword, description, category, params, template, ...opts });
const variant = (keyword, description, category, params, variants, opts = {}) =>
  commands.push({ keyword, description, category, params, variants, ...opts });

// ═════════════════════════════════════════════════════════════════════════════
// 文本显示
// ═════════════════════════════════════════════════════════════════════════════
// ⚠️ 基岩版的 titleraw **没有 set 这个关键字**：
//      titleraw <目标> <位置> <JSON>       ← 位置后面直接跟 JSON
//      titleraw <目标> times <淡入> <停留> <淡出>
//      titleraw <目标> <位置> clear|reset
//    早先照 Java 版的 `/title <目标> title <文本>` 写成了 `... set ...`，
//    生成的命令在游戏里是语法错误。不要再加回来。
// 命令族：`title` 与 `titleraw` 是同一件事的两种写法 ——
//   title    纯文本，不能上色
//   titleraw JSON，能上色、能加点击事件
// 名字只差三个字母，玩家手打 `/title` 时多半其实想要 titleraw。
// 所以在 title 上声明 alternatives，补全时先让用户选一次（见 CommandSpec.alternatives）。
variant('titleraw', '在屏幕上显示 JSON 文本（标题 / 副标题 / 动作栏）', CAT.text, [
  { k: 'target', n: '目标', t: 'selector', d: '@a' },
  { k: 'position', n: '显示位置', t: 'choice', d: 'title', c: ['title', 'subtitle', 'actionbar'] },
  { k: 'action', n: '操作', t: 'choice', d: 'show', c: ['show', 'times', 'clear', 'reset'], h: 'show = 直接显示后面的 JSON；times/clear/reset 见各自写法' },
  { k: 'json', n: 'JSON 组件', t: 'json', r: false, h: '显示模式下必填，如 {"rawtext":[{"text":"你好"}]}' },
  { k: 'fadeIn', n: '淡入（tick）', t: 'number', r: false, d: '10' },
  { k: 'stay', n: '停留（tick）', t: 'number', r: false, d: '70' },
  { k: 'fadeOut', n: '淡出（tick）', t: 'number', r: false, d: '20' },
], [
  // ⚠️ clear / reset **不带位置参数**。维基 BE 语法是 `titleraw <目标> <clear|reset>`，
  //    写成 `titleraw @a title clear` 是语法错误（位置参数只用于 show）。
  { require: { action: 'clear' }, text: 'titleraw {target} clear' },
  { require: { action: 'reset' }, text: 'titleraw {target} reset' },
  // times 不接受位置参数，直接跟三个数字
  { require: { action: 'times' }, text: 'titleraw {target} times {fadeIn|10} {stay|70} {fadeOut|20}' },
  // 显示：位置后面直接跟 JSON
  { require: ['json'], text: 'titleraw {target} {position} {json}' },
  // 没有 JSON 就不生成命令 —— `titleraw @a title` 单独一条是无效命令
  { text: '' },
], { variantNote: 'JSON（可上色）' });

tpl('tellraw', '给玩家发送一条 JSON 消息（聊天栏）', CAT.text, [
  { k: 'target', n: '目标', t: 'selector', d: '@a' },
  { k: 'json', n: 'JSON 组件', t: 'json' },
], 'tellraw {target} {json}', { variantNote: 'JSON（可上色）' });

tpl('title', '显示普通文本标题（不支持样式，要颜色请用 titleraw）', CAT.text, [
  { k: 'target', n: '目标', t: 'selector', d: '@a' },
  { k: 'position', n: '显示位置', t: 'choice', d: 'title', c: ['title', 'subtitle', 'actionbar'] },
  { k: 'text', n: '文本', t: 'long' },
], 'title {target} {position} {text}', {
  alternatives: ['titleraw'],
  variantNote: '纯文本',
});

tpl('say', '以系统身份向所有人发一条消息', CAT.text, [
  { k: 'message', n: '消息内容', t: 'long' },
], 'say {message}');

// `tell` / `tellraw` 是同一族：前者纯文本、后者 JSON（能上色）。
// 与 title / titleraw 同一套机制，见上面 title 的说明。
tpl('tell', '给指定玩家发私聊消息', CAT.text, [
  { k: 'target', n: '目标', t: 'selector', d: '@a' },
  { k: 'message', n: '消息内容', t: 'long' },
], 'tell {target} {message}', {
  alternatives: ['tellraw'],
  variantNote: '纯文本',
});

tpl('me', '以动作形式发言（* 玩家名 …）', CAT.text, [
  { k: 'action', n: '动作描述', t: 'long' },
], 'me {action}');

// ═════════════════════════════════════════════════════════════════════════════
// 物品
// ═════════════════════════════════════════════════════════════════════════════
variant('give', '给予物品，可附带自定义名称、附魔、属性等组件', CAT.item, [
  { k: 'target', n: '目标', t: 'selector', d: '@s' },
  { k: 'item', n: '物品 ID', h: '如 diamond_sword，可带命名空间 minecraft:', s: 'items' },
  { k: 'amount', n: '数量', t: 'number', r: false, d: '1' },
  { k: 'data', n: '数据值', t: 'number', r: false, d: '0' },
  { k: 'components', n: '组件 JSON', t: 'json', r: false, h: '如 {"item_name":"宝剑"}', s: 'giveComponents' },
], [
  // 组件 JSON 前面必须先有 [数量] [数据值] 占位，否则游戏会把组件当数据值解析。
  { require: ['components'], text: 'give {target} {item} {amount|1} {data|0} {components}' },
  { require: ['item'], text: 'give {target} {item} {amount|1}' },
  // 两样都没给时不生成命令：`give @s` 本身是无效命令，给了反而误导。
  { text: '' },
]);

variant('replaceitem', '替换容器或实体身上的物品', CAT.item, [
  { k: 'kind', n: '类型', t: 'choice', d: 'entity', c: ['entity', 'block'], h: 'entity = 换实体身上的；block = 换容器方块里的' },
  // entity 分支用 target（选择器），block 分支用坐标。两个都留着，按 kind 生成。
  { k: 'target', n: '目标实体', t: 'selector', r: false, d: '@s' },
  { k: 'x', n: 'X', r: false, d: '~' }, { k: 'y', n: 'Y', r: false, d: '~' }, { k: 'z', n: 'Z', r: false, d: '~' },
  { k: 'slotType', n: '槽位类型', r: false, d: 'slot.weapon.mainhand', h: '如 slot.container、slot.armor.head、slot.hotbar' },
  { k: 'slotId', n: '槽位序号', t: 'number', r: false, d: '0' },
  { k: 'item', n: '物品 ID', s: 'items' },
  { k: 'amount', n: '数量', t: 'number', r: false, d: '1' },
  { k: 'data', n: '数据值', t: 'number', r: false, d: '0' },
  { k: 'components', n: '组件 JSON', t: 'json', r: false },
], [
  // ⚠️ `entity` / `block` 这个判别词是**必需**的，老写法整个漏了它。
  //    维基 BE 两种形式：
  //      replaceitem block <position> slot.container <slotId> <itemName> [amount] [data] [components]
  //      replaceitem entity <target> <slotType> <slotId> <itemName> [amount] [data] [components]
  { require: { kind: 'block' }, text: 'replaceitem block {x} {y} {z} slot.container {slotId|0} {item} {{amount}} {{data}} {{components}}' },
  { require: ['target', 'slotType', 'item'], text: 'replaceitem entity {target} {slotType} {slotId|0} {item} {{amount}} {{data}} {{components}}' },
  { text: '' },
]);

variant('clear', '从玩家物品栏里清除物品', CAT.item, [
  { k: 'target', n: '目标', t: 'selector', d: '@s' },
  { k: 'item', n: '物品 ID', r: false, s: 'items', h: '留空 = 清空全部物品' },
  { k: 'data', n: '数据值', t: 'number', r: false, d: '0' },
  // ⚠️ maxCount 的语义容易反：维基 BE 原文 —— `maxCount: int` 为 **0** 表示
  //    **只检测并返回数量、不清除**；要清除全部必须写 **-1**。
  //    老写法默认 0，会生成 `clear @s stone 0 0` 这种"只查询"的命令，
  //    玩家以为在清物品其实什么都没清。
  { k: 'maxCount', n: '最多清除数量', t: 'number', r: false, d: '-1', h: '填 -1 清除全部；填 0 只统计数量不清除' },
], [
  { require: ['item'], text: 'clear {target} {item} {data|0} {maxCount|-1}' },
  { text: 'clear {target}' },
]);

// ═════════════════════════════════════════════════════════════════════════════
// 计分板
// ═════════════════════════════════════════════════════════════════════════════
tpl('scoreboard objectives add', '新建一个计分板目标', CAT.score, [
  { k: 'objective', n: '目标名' },
  // ⚠️ 基岩版**只支持 dummy**。维基参数说明原文：
  //    "In Bedrock Edition, 'dummy' is the only criterion currently supported"。
  //    曾经把 Java 的 trigger / deathCount / playerKillCount / totalKillCount / health
  //    也列进候选，玩家选了会在基岩版报错。
  { k: 'criteria', n: '准则', t: 'choice', d: 'dummy', c: ['dummy'] },
  { k: 'display', n: '显示名', r: false, h: '支持 JSON 组件' },
], 'scoreboard objectives add {objective} {criteria} {{display}}');

tpl('scoreboard objectives remove', '删除一个计分板目标', CAT.score, [
  { k: 'objective', n: '目标名' },
], 'scoreboard objectives remove {objective}');

tpl('scoreboard objectives list', '列出所有计分板目标', CAT.score, [], 'scoreboard objectives list');

variant('scoreboard objectives setdisplay', '把计分板显示在侧边栏或玩家列表', CAT.score, [
  { k: 'slot', n: '显示位置', t: 'choice', d: 'sidebar', c: ['sidebar', 'list', 'belowname'] },
  { k: 'objective', n: '目标名' },
  { k: 'order', n: '排序', t: 'choice', r: false, d: 'ascending', c: ['ascending', 'descending'] },
], [
  // 维基 BE：`setdisplay <list|sidebar> [objective] [ascending|descending]`、
  //          `setdisplay belowname [objective]`
  // 也就是 **list 和 sidebar 都接受排序参数**，只有 belowname 不接受。
  // 老写法只在 sidebar 时输出 order，把 list 的排序能力漏掉了。
  { require: { slot: 'belowname' }, text: 'scoreboard objectives setdisplay belowname {objective}' },
  { require: ['objective'], text: 'scoreboard objectives setdisplay {slot} {objective} {order|ascending}' },
  { text: 'scoreboard objectives setdisplay {slot}' },
]);

tpl('scoreboard players set', '设置某个玩家（或假玩家）的分数', CAT.score, [
  { k: 'player', n: '玩家 / 假玩家名', d: '@s' },
  { k: 'objective', n: '目标名' },
  { k: 'score', n: '分数', t: 'number', d: '0' },
], 'scoreboard players set {player} {objective} {score}');

tpl('scoreboard players add', '给分数加上一个值（可为负数）', CAT.score, [
  { k: 'player', n: '玩家 / 假玩家名', d: '@s' },
  { k: 'objective', n: '目标名' },
  { k: 'score', n: '增加量', t: 'number', d: '1' },
], 'scoreboard players add {player} {objective} {score}');

tpl('scoreboard players remove', '从分数里减去一个值', CAT.score, [
  { k: 'player', n: '玩家 / 假玩家名', d: '@s' },
  { k: 'objective', n: '目标名' },
  { k: 'score', n: '减少量', t: 'number', d: '1' },
], 'scoreboard players remove {player} {objective} {score}');

tpl('scoreboard players reset', '重置分数（清空某个目标上的记录）', CAT.score, [
  { k: 'player', n: '玩家 / 假玩家名', d: '@s' },
  { k: 'objective', n: '目标名', r: false, h: '留空 = 清空该玩家所有目标上的分' },
], 'scoreboard players reset {player} {{objective}}');

tpl('scoreboard players test', '判断分数是否落在指定区间，成立时输出结果（配合命令方块做条件）', CAT.score, [
  { k: 'player', n: '对象 / 实体', d: '@p' },
  { k: 'objective', n: '记分项' },
  { k: 'min', n: '最小值', t: 'number', d: '1' },
  { k: 'max', n: '最大值', r: false, h: '填 * 表示不设上限' },
], 'scoreboard players test {player} {objective} {min} {max|*}');

tpl('scoreboard players operation', '对两个分数做运算（赋值 / 加减乘除取余 / 取最大最小）', CAT.score, [
  { k: 'target', n: '被修改的对象', d: '@s' },
  { k: 'targetObjective', n: '被修改的记分项' },
  { k: 'operator', n: '运算符', t: 'choice', d: '=', c: ['=', '+=', '-=', '*=', '/=', '%=', '><', '<', '>'] },
  { k: 'source', n: '来源对象', d: '@s' },
  { k: 'sourceObjective', n: '来源记分项' },
], 'scoreboard players operation {target} {targetObjective} {operator} {source} {sourceObjective}');

tpl('scoreboard players random', '给分数随机赋一个区间内的值', CAT.score, [
  { k: 'player', n: '玩家 / 假玩家名', d: '@s' },
  { k: 'objective', n: '目标名' },
  { k: 'min', n: '最小值', t: 'number', d: '1' },
  { k: 'max', n: '最大值', t: 'number', d: '6' },
], 'scoreboard players random {player} {objective} {min} {max}');

tpl('scoreboard players list', '列出玩家记录的全部记分项', CAT.score, [
  { k: 'player', n: '玩家名', r: false },
], 'scoreboard players list {{player}}');

// ═════════════════════════════════════════════════════════════════════════════
// 世界与方块
// ═════════════════════════════════════════════════════════════════════════════
variant('setblock', '在指定坐标放置方块', CAT.world, [
  { k: 'x', n: 'X', d: '~' }, { k: 'y', n: 'Y', d: '~' }, { k: 'z', n: 'Z', d: '~' },
  { k: 'block', n: '方块 ID', s: 'blocks' },
  { k: 'states', n: '方块状态', r: false, h: '如 ["facing_direction"=1]', s: 'blockStates' },
  { k: 'mode', n: '处理方式', t: 'choice', r: false, d: 'replace', c: ['replace', 'destroy', 'keep'] },
], [
  // ⚠️ `{block} {states}` 之间**必须有空格**。
  //    维基把 `<tileName: Block>` 与 `<blockStates: block states>` 列为两个独立参数，
  //    示例：`setblock ~ ~ ~ chest ["minecraft:cardinal_direction"="east"]`。
  //    老写法 `{block}{states}` 会拼成 `chest["minecraft:cardinal_direction"="east"]`，
  //    方块名与状态粘在一起，游戏解析不了。
  { require: ['block', 'states'], text: 'setblock {x} {y} {z} {block} {states} {{mode}}' },
  { require: ['block'], text: 'setblock {x} {y} {z} {block} {{mode}}' },
  { text: 'setblock {x} {y} {z}' },
]);

variant('fill', '用方块填充一个区域', CAT.world, [
  { k: 'x1', n: '起点 X', d: '~' }, { k: 'y1', n: '起点 Y', d: '~' }, { k: 'z1', n: '起点 Z', d: '~' },
  { k: 'x2', n: '终点 X', d: '~' }, { k: 'y2', n: '终点 Y', d: '~' }, { k: 'z2', n: '终点 Z', d: '~' },
  { k: 'block', n: '方块 ID', s: 'blocks' },
  { k: 'states', n: '方块状态', r: false, h: '如 ["facing_direction"=1]', s: 'blockStates' },
  // ⚠️ `blockData`（数字数据值）在基岩版 1.19.70 已**移除**，别再输出。
  //    维基历史原文："1.19.70 Removed tileData: int and replaceDataValue: int arguments"。
  { k: 'mode', n: '填充方式', t: 'choice', r: false, d: 'replace', c: ['replace', 'destroy', 'hollow', 'keep', 'outline', 'strict'] },
], [
  { require: ['block', 'states'], text: 'fill {x1} {y1} {z1} {x2} {y2} {z2} {block} {states} {{mode}}' },
  { require: ['block'], text: 'fill {x1} {y1} {z1} {x2} {y2} {z2} {block} {{mode}}' },
  { text: 'fill {x1} {y1} {z1} {x2} {y2} {z2}' },
]);

tpl('clone', '复制一块区域到另一处', CAT.world, [
  { k: 'x1', n: '起点 X', d: '~' }, { k: 'y1', n: '起点 Y', d: '~' }, { k: 'z1', n: '起点 Z', d: '~' },
  { k: 'x2', n: '终点 X', d: '~' }, { k: 'y2', n: '终点 Y', d: '~' }, { k: 'z2', n: '终点 Z', d: '~' },
  { k: 'dx', n: '目标 X', d: '~' }, { k: 'dy', n: '目标 Y', d: '~' }, { k: 'dz', n: '目标 Z', d: '~' },
  { k: 'maskMode', n: '过滤模式', t: 'choice', r: false, d: 'replace', c: ['replace', 'masked'] },
  { k: 'cloneMode', n: '复制方式', t: 'choice', r: false, d: 'normal', c: ['normal', 'force', 'move'] },
], 'clone {x1} {y1} {z1} {x2} {y2} {z2} {dx} {dy} {dz} {maskMode|replace} {cloneMode|normal}');

tpl('time set', '设置世界时间', CAT.world, [
  { k: 'value', n: '时间', t: 'choice', d: 'day', c: ['day', 'noon', 'sunset', 'night', 'midnight', 'sunrise'] },
], 'time set {value}');

tpl('time add', '把世界时间往前推一段', CAT.world, [
  { k: 'amount', n: '推进量', t: 'number', d: '1000' },
], 'time add {amount}');

// 维基 BE：`time query <daytime|gametime|day>` —— 原来只有 set / add，
// 漏了查询能力（写计分板定时器时很常用）。
tpl('time query', '查询当前时间是白天还是夜晚、或已过多少刻', CAT.world, [
  { k: 'value', n: '查询内容', t: 'choice', d: 'daytime', c: ['daytime', 'gametime', 'day'] },
], 'time query {value}');

// ⚠️ duration 的单位是 **game tick**，不是秒。
//    维基：BE 1.18.30 起 `duration: int` 单位为 game ticks（20 tick = 1 秒）。
//    老写法标签写"持续秒数"、默认 600，玩家会以为 600 秒，实际只有 30 秒。
variant('weather', '切换天气', CAT.world, [
  { k: 'type', n: '天气', t: 'choice', d: 'clear', c: ['clear', 'rain', 'thunder'] },
  { k: 'duration', n: '持续（游戏刻）', t: 'number', r: false, h: '20 刻 = 1 秒；不填由游戏决定' },
], [
  { require: ['duration'], text: 'weather {type} {duration}' },
  { text: 'weather {type}' },
]);

// 维基 BE：`weather query` —— 查询当前天气（原来只有切换，漏了查询）。
tpl('weather query', '查询当前天气', CAT.world, [], 'weather query');

tpl('difficulty', '设置游戏难度', CAT.world, [
  { k: 'value', n: '难度', t: 'choice', d: 'normal', c: ['peaceful', 'easy', 'normal', 'hard'] },
], 'difficulty {value}');

variant('locate', '查找最近的生物群系或结构并给出坐标', CAT.world, [
  { k: 'feature', n: '类型', t: 'choice', d: 'biome', c: ['biome', 'structure'] },
  { k: 'name', n: '名称', h: '如 village、stronghold、cherry_grove' },
], [
  { require: ['name'], text: 'locate {feature} {name}' },
  { text: 'locate {feature}' },
]);

// ⚠️ 坐标是**三坐标一体**的：维基 BE `setworldspawn [spawnPoint: x y z]`，
//    要么三个都给，要么整个省略（省略 = 用命令执行位置）。
//    老写法把 y 单独设为可选且无默认值，会生成只有两个坐标的 `setworldspawn ~ ~`，
//    基岩版解析不了。
variant('setworldspawn', '设置世界出生点', CAT.world, [
  { k: 'x', n: 'X', r: false, d: '~' }, { k: 'y', n: 'Y', r: false, d: '~' }, { k: 'z', n: 'Z', r: false, d: '~' },
], [
  { require: ['x', 'y', 'z'], text: 'setworldspawn {x} {y} {z}' },
  { text: 'setworldspawn' },
]);

tpl('gamerule', '修改游戏规则', CAT.world, [
  { k: 'rule', n: '规则名', t: 'choice', choices: 'gamerules', h: '可先打字母筛选' },
  { k: 'value', n: '值', t: 'choice', r: false, d: 'true', c: ['true', 'false'] },
], 'gamerule {rule} {{value}}');

variant('tickingarea', '创建常加载区域（让远处区块保持运行）', CAT.world, [
  { k: 'mode', n: '操作', t: 'choice', d: 'add', c: ['add', 'remove', 'remove_all', 'list', 'preload'] },
  { k: 'from', n: '起始坐标', r: false, h: '如 ~ ~ ~ 或 0 0 0' },
  { k: 'to', n: '结束坐标', r: false },
  { k: 'name', n: '区域名', r: false, h: '可选，方便以后按名字移除' },
], [
  // ⚠️ 参数顺序按维基：坐标在前、区域名在后。
  //    老写法 `{{name}} {{from}} {{to}}` 把名字放前面，游戏会当成坐标解析失败。
  // ⚠️ `remove_all` 是**带下划线**的（维基原文 `tickingarea remove_all`）。
  { require: { mode: 'list' }, text: 'tickingarea list' },
  { require: { mode: 'remove_all' }, text: 'tickingarea remove_all' },
  { require: ['from', 'to'], text: 'tickingarea {mode} {from} {to} {{name}}' },
  { text: 'tickingarea {mode}' },
]);

// ═════════════════════════════════════════════════════════════════════════════
// 实体与效果
// ═════════════════════════════════════════════════════════════════════════════
variant('effect', '给予或清除状态效果', CAT.entity, [
  { k: 'target', n: '目标', t: 'selector', d: '@s' },
  // ⚠️ 这里用**两个键**：`mode` 决定是给效果还是清除，`effect` 才是效果 ID。
  //    早先把它们合成一个键（填 clear 就表示清除），结果生成 `effect @s clear clear`
  //    —— clear 被当成效果名又输出了一遍（单元测试抓到过）。
  { k: 'mode', n: '操作', t: 'choice', d: 'give', c: ['give', 'clear'], h: 'give = 给予效果；clear = 清除效果' },
  { k: 'effect', n: '效果 ID', r: false, s: 'effects', h: 'give 必填；clear 时可留空表示清除全部效果' },
  { k: 'duration', n: '持续秒数', t: 'number', r: false, d: '30', h: '也可填 infinite 表示永久' },
  { k: 'amplifier', n: '等级（0 起）', t: 'number', r: false, d: '0' },
  { k: 'hideParticles', n: '隐藏粒子', t: 'bool', r: false, d: 'false' },
], [
  // ⚠️ 用了两个变体而不是 `{{effect}}`：可选段在生成器里会先补一个空格，
  //    结果 clear 指定效果时输出 `effect @a clear  speed`（两个空格）。
  //    拆成"带效果 / 不带效果"两条就不会有多余空格。
  { require: { mode: 'clear', effect: '' }, text: 'effect {target} clear' },
  { require: { mode: 'clear' }, text: 'effect {target} clear {effect}' },
  // 永久效果：`effect <player> <effect> infinite [amplifier] [hideParticles]`
  { require: { duration: 'infinite' }, text: 'effect {target} {effect} infinite {amplifier|0} {hideParticles|false}' },
  { require: ['effect'], text: 'effect {target} {effect} {duration|30} {amplifier|0} {hideParticles|false}' },
  // 只选了 give 却没给效果 → 不生成（`effect @s` 在基岩版无法解析）
  { text: '' },
]);

variant('summon', '生成一个实体', CAT.entity, [
  { k: 'entity', n: '实体 ID', s: 'entities' },
  { k: 'x', n: 'X', d: '~' }, { k: 'y', n: 'Y', d: '~' }, { k: 'z', n: 'Z', d: '~' },
  { k: 'yRot', n: '水平朝向', r: false, h: '如 90 或 ~' },
  { k: 'xRot', n: '垂直朝向', r: false },
  { k: 'spawnEvent', n: '生成事件', r: false, h: '如 minecraft:entity_born' },
  // ⚠️ nameTag 是**纯字符串**，不是 JSON。
  //    维基 BE：`summon <entityType> [spawnPos] [yRot] [xRot] [spawnEvent: string] [nameTag: string]`，
  //    nameTag 类型 basic_string，示例 `summon dolphin §6Dolphin ~ ~ ~`。
  //    老写法输出 `{"nameTag":"..."}`，名字会带上花括号字面量（实测会这样）。
  { k: 'nameTag', n: '名称', r: false, h: '直接写名字，支持 § 颜色代码；不要写 JSON' },
], [
  { require: ['entity', 'spawnEvent', 'nameTag'], text: 'summon {entity} {x} {y} {z} {{yRot}} {{xRot}} {spawnEvent} {nameTag}' },
  { require: ['entity', 'nameTag'], text: 'summon {entity} {x} {y} {z} {{yRot}} {{xRot}} {nameTag}' },
  { require: ['entity', 'spawnEvent'], text: 'summon {entity} {x} {y} {z} {{yRot}} {{xRot}} {spawnEvent}' },
  { require: ['entity'], text: 'summon {entity} {x} {y} {z} {{yRot}} {{xRot}}' },
  { text: '' },
]);

tpl('kill', '清除实体（对自己用等于自杀）', CAT.entity, [
  { k: 'target', n: '目标', t: 'selector', d: '@s' },
], 'kill {target}');

tpl('damage', '对实体造成指定类型与数值的伤害', CAT.entity, [
  { k: 'target', n: '目标', t: 'selector', d: '@s' },
  { k: 'amount', n: '伤害值', t: 'number', d: '1' },
  { k: 'cause', n: '伤害类型', r: false, d: 'none', h: '如 none / fall / fire / drowning / magic' },
], 'damage {target} {amount} {{cause}}');

variant('tag', '给实体加标签 / 移除标签（做条件判断的基础）', CAT.entity, [
  { k: 'target', n: '目标', t: 'selector', d: '@s' },
  { k: 'action', n: '操作', t: 'choice', d: 'add', c: ['add', 'remove', 'list'] },
  { k: 'name', n: '标签名', r: false, h: 'list 时不用填' },
], [
  { require: { action: 'list' }, text: 'tag {target} list' },
  { require: ['target', 'name'], text: 'tag {target} {action} {name}' },
  { text: 'tag {target} {action}' },
]);

tpl('testfor', '检测是否存在符合条件的实体（命令方块条件用）', CAT.entity, [
  { k: 'target', n: '目标', t: 'selector', d: '@a' },
], 'testfor {target}');

// ⚠️ 基岩版 ride 的语法与 Java **完全不同**（Java 是 `ride <target> mount <vehicle>`）。
//    老写法 `ride {riders} {target}` 两版都不是合法命令。
//    维基 BE 原文：
//      ride <riders: target> start_riding <ride: target> [teleportRules] [howToFill]
//      ride <riders: target> stop_riding
//      ride <rides: target> evict_riders
//      ride <rides: target> summon_rider <entityType> [spawnEvent] [nameTag]
//      ride <riders: target> summon_ride <entityType> [rideRules] [spawnEvent] [nameTag]
variant('ride', '让实体骑乘 / 下来 / 召唤骑手与坐骑', CAT.entity, [
  { k: 'riders', n: '骑乘者 / 被骑者', t: 'selector', d: '@s', h: 'start_riding / stop_riding / summon_ride 填骑乘者；evict_riders / summon_rider 填被骑的实体' },
  { k: 'mode', n: '操作', t: 'choice', d: 'start_riding', c: ['start_riding', 'stop_riding', 'evict_riders', 'summon_rider', 'summon_ride'] },
  { k: 'ride', n: '坐骑', t: 'selector', r: false, d: '@e[type=armor_stand]', h: 'start_riding 时填' },
  { k: 'entityType', n: '召唤的实体', s: 'entities', r: false, h: 'summon_rider / summon_ride 时填' },
  { k: 'teleportRules', n: '传送规则', t: 'choice', r: false, d: 'teleport_rider', c: ['teleport_rider', 'teleport_ride'] },
  { k: 'howToFill', n: '填充方式', t: 'choice', r: false, d: 'until_full', c: ['until_full', 'if_group_fits'] },
  { k: 'rideRules', n: '召唤规则', t: 'choice', r: false, d: 'reassign_rides', c: ['reassign_rides', 'no_ride_change', 'skip_riders'] },
  { k: 'spawnEvent', n: '生成事件', r: false },
  { k: 'nameTag', n: '名称', r: false },
], [
  { require: { mode: 'stop_riding' }, text: 'ride {riders} stop_riding' },
  { require: { mode: 'evict_riders' }, text: 'ride {riders} evict_riders' },
  { require: { mode: 'summon_rider' }, text: 'ride {riders} summon_rider {entityType} {{spawnEvent}} {{nameTag}}' },
  { require: { mode: 'summon_ride' }, text: 'ride {riders} summon_ride {entityType} {{rideRules}} {{spawnEvent}} {{nameTag}}' },
  { require: ['ride'], text: 'ride {riders} start_riding {ride} {{teleportRules}} {{howToFill}}' },
  { text: '' },
]);

// 注意：可选段之间**不要**再手写空格 —— {{...}} 已经会自己补一个。
// 写成 `{z} {{yRot}} {{xRot}}`，不要写成 `{z} {{yRot}}  {{xRot}}`。
tpl('tp', '传送实体', CAT.entity, [
  { k: 'target', n: '被传送者', t: 'selector', d: '@s' },
  { k: 'x', n: 'X', d: '~' }, { k: 'y', n: 'Y', d: '~' }, { k: 'z', n: 'Z', d: '~' },
  { k: 'yRot', n: '水平朝向', r: false, h: '如 90 或 ~' },
  { k: 'xRot', n: '垂直朝向', r: false },
], 'tp {target} {x} {y} {z}{{yRot}}{{xRot}}');

// ⚠️ teleport 的 BE 语法有**两种互斥的目的地**：坐标 或 目标实体。
//    老写法把 `destination`(目标实体) 与 `y`/`z`(坐标分量) 并列成可选参数，
//    会生成 `teleport @s @p 0 0` —— 坐标里混了个选择器，两版都解析不了。
//
//    维基 BE 形式：
//      teleport <destination: target>
//      teleport <victim: target> <destination: target> [checkForBlocks]
//      teleport <destination: x y z> [yRot] [xRot] [checkForBlocks]
//      teleport <victim: target> <destination: x y z> [yRot] [xRot] [checkForBlocks]
//    这里把"传自己 / 传别人"分成两条，坐标作为一个整体段出现，不再拆散。
variant('teleport', '传送实体（tp 的完整写法，两者等价）', CAT.entity, [
  // 被传送者。留空 = 传自己（BE 的 `teleport <目的地>` 形式）。
  { k: 'victim', n: '被传送者', t: 'selector', r: false, h: '留空 = 传送自己' },
  // 目的地二选一：实体 或 坐标。用 require 把两条路径分开，不并列成可选参数。
  { k: 'destination', n: '目的地实体', t: 'selector', r: false, h: '传送到某实体身上；与坐标二选一' },
  { k: 'x', n: 'X', r: false, d: '~' }, { k: 'y', n: 'Y', r: false, d: '~' }, { k: 'z', n: 'Z', r: false, d: '~' },
  { k: 'yRot', n: '水平朝向', r: false, h: '如 90 或 ~' },
  { k: 'xRot', n: '垂直朝向', r: false },
  { k: 'checkForBlocks', n: '检查阻挡', t: 'bool', r: false, h: 'true = 目标位置被挡住就不传送' },
], [
  // 传给别人 + 目的地是实体
  { require: ['victim', 'destination'], text: 'teleport {victim} {destination} {{checkForBlocks}}' },
  // 传给别人 + 目的地是坐标（带朝向）
  { require: ['victim', 'yRot'], text: 'teleport {victim} {x|~} {y|~} {z|~} {yRot} {xRot|0} {{checkForBlocks}}' },
  // 传给别人 + 目的地是坐标
  { require: ['victim'], text: 'teleport {victim} {x|~} {y|~} {z|~} {{checkForBlocks}}' },
  // 传自己 + 目的地是实体
  { require: ['destination'], text: 'teleport {destination}' },
  // 传自己 + 目的地是坐标（带朝向）
  { require: ['yRot'], text: 'teleport {x|~} {y|~} {z|~} {yRot} {xRot|0} {{checkForBlocks}}' },
  // 传自己 + 目的地是坐标
  { require: ['x'], text: 'teleport {x} {y|~} {z|~} {{checkForBlocks}}' },
  { text: 'teleport' },
]);

// ═════════════════════════════════════════════════════════════════════════════
// 特效与音效
// ═════════════════════════════════════════════════════════════════════════════
tpl('particle', '在指定位置播放粒子效果', CAT.fx, [
  { k: 'effect', n: '粒子名称', s: 'particles' },
  { k: 'x', n: 'X', d: '~' }, { k: 'y', n: 'Y', d: '~' }, { k: 'z', n: 'Z', d: '~' },
], 'particle {effect} {x} {y} {z}');

variant('playsound', '给玩家播放音效', CAT.fx, [
  // 音效 ID 与粒子都接词表：1671 个音效全部带中文（来自官方字幕译文），
  // 玩家搜「升级」「爆炸」就能找到，不用去背 random.levelup 这种名字。
  { k: 'sound', n: '音效 ID', s: 'sounds', h: '如 random.levelup、mob.zombie.say' },
  // ⚠️ **不要**在这里放 `source`（播放频道 master/player/block…）。
  //    维基 playsound 页把 `<source>` 明确标为 `{{el|je|short=1}}`（**仅 Java**）；
  //    基岩版第二个参数直接就是 `player: target`。
  //    老写法会生成 `playsound random.orb master @a` —— 在基岩版是语法错误，
  //    玩家点了补全直接报错（这是本报告里最严重的一项）。
  { k: 'target', n: '目标', t: 'selector', d: '@a' },
  { k: 'x', n: 'X', r: false, d: '~' }, { k: 'y', n: 'Y', r: false, d: '~' }, { k: 'z', n: 'Z', r: false, d: '~' },
  { k: 'volume', n: '音量', t: 'number', r: false, d: '1' },
  { k: 'pitch', n: '音调', t: 'number', r: false, d: '1' },
  // 基岩版支持 minimumVolume（越小传播越远），Java 没有 —— 这条是 BE 独有的。
  { k: 'minimumVolume', n: '最小音量', t: 'number', r: false, h: '可选，越小传得越远' },
], [
  { require: ['sound', 'minimumVolume'], text: 'playsound {sound} {target} {x} {y} {z} {volume|1} {pitch|1} {minimumVolume}' },
  { require: ['sound'], text: 'playsound {sound} {target} {x} {y} {z} {volume|1} {pitch|1}' },
  { text: 'playsound' },
]);

// ⚠️ 维基 BE camera 共 11 个子命令，老写法错了好几个地方：
//      (a) mode 候选里有 `add` —— BE **没有**这个子命令；
//      (b) `camera <player> fade <easeTime> <easeType>` 形式不存在；
//          BE 的 fade 只接受 `color r g b` 或 `time <in> <hold> <out> [color r g b]`；
//      (c) `set` 必须跟 `<preset: string>`，老写法漏了；
//      (d) easeType 用的是 Easing 枚举（linear / in_quad / out_circ / in_out_cubic… 共 31 个），
//          不是 linear/in/out/inout 四个。
//    维基原文（关键几条）：
//      camera <players> clear
//      camera <players> attach_to_entity <entity>   /  detach_from_entity
//      camera <players> fade color <r> <g> <b>
//      camera <players> fade time <fadeIn> <hold> <fadeOut> [color <r> <g> <b>]
//      camera <players> fov_set <fov_value> [easeTime] [easeType]   /  fov_clear [...]
//      camera <players> set <preset> ...
//      camera <players> play_spline <name>   /  remove_target
variant('camera', '控制玩家镜头（运镜 / 淡入淡出 / 视角绑定，基岩版特有，做剧情演出用）', CAT.fx, [
  { k: 'player', n: '玩家', t: 'selector', d: '@a' },
  { k: 'mode', n: '子命令', t: 'choice', d: 'clear', c: ['clear', 'attach_to_entity', 'detach_from_entity', 'fade', 'fov_set', 'fov_clear', 'set', 'play_spline', 'remove_target'] },
  { k: 'fadeMode', n: 'fade 方式', t: 'choice', r: false, d: 'time', c: ['time', 'color'], h: 'fade 专用' },
  { k: 'fadeIn', n: '淡入秒数', t: 'number', r: false, d: '1' },
  { k: 'hold', n: '保持秒数', t: 'number', r: false, d: '1' },
  { k: 'fadeOut', n: '淡出秒数', t: 'number', r: false, d: '1' },
  { k: 'red', n: '红 0-255', t: 'number', r: false, d: '0' },
  { k: 'green', n: '绿 0-255', t: 'number', r: false, d: '0' },
  { k: 'blue', n: '蓝 0-255', t: 'number', r: false, d: '0' },
  { k: 'preset', n: '预设名', r: false, h: 'set 专用，如 minecraft:free' },
  { k: 'fov', n: '视野值', t: 'number', r: false, d: '70' },
  { k: 'easeTime', n: '过渡秒数', t: 'number', r: false, d: '1' },
  { k: 'easeType', n: '过渡曲线', r: false, d: 'linear', h: 'Easing 枚举：linear / in_quad / out_circ / in_out_cubic…（共 31 个）' },
  { k: 'entity', n: '绑定实体', t: 'selector', r: false, h: 'attach_to_entity 专用' },
  { k: 'spline', n: '样条名', r: false, h: 'play_spline 专用' },
], [
  { require: { mode: 'attach_to_entity' }, text: 'camera {player} attach_to_entity {entity}' },
  { require: { mode: 'detach_from_entity' }, text: 'camera {player} detach_from_entity' },
  { require: { mode: 'remove_target' }, text: 'camera {player} remove_target' },
  { require: { mode: 'fov_clear' }, text: 'camera {player} fov_clear {{easeTime}} {{easeType}}' },
  { require: { mode: 'fov_set' }, text: 'camera {player} fov_set {fov|70} {{easeTime}} {{easeType}}' },
  { require: { mode: 'play_spline' }, text: 'camera {player} play_spline {spline}' },
  { require: { mode: 'set' }, text: 'camera {player} set {preset}' },
  { require: { mode: 'fade', fadeMode: 'color' }, text: 'camera {player} fade color {red|0} {green|0} {blue|0}' },
  { require: { mode: 'fade' }, text: 'camera {player} fade time {fadeIn|1} {hold|1} {fadeOut|1}' },
  { require: { mode: 'clear' }, text: 'camera {player} clear' },
  { text: '' },
]);

// ⚠️ 维基 BE fog 只有两种形式，**都需要两个 id**：
//      fog <victim: target> push <fogID: string> <userProvidedId: string>
//      fog <victim: target> <mode: delete> <userProvidedId: string>
//    老写法只建模了一个 id，`fog @a remove` 缺少 userProvidedId，是语法错误。
//    另外 BE 的"操作"字面量是 `delete`（一个词涵盖 pop / remove 两种说法）；
//    `push` 是把雾压入栈、`delete` 是按 userProvidedId 弹出。
variant('fog', '给玩家套用 / 移除迷雾（做气氛）', CAT.fx, [
  { k: 'target', n: '目标', t: 'selector', d: '@a' },
  { k: 'mode', n: '操作', t: 'choice', d: 'push', c: ['push', 'delete'], h: 'push = 套用迷雾；delete = 按标识移除' },
  { k: 'fogId', n: '迷雾 ID', r: false, h: '如 minecraft:fog_crimson_forest；push 时必填' },
  { k: 'userId', n: '自定义标识', h: '自己起的名字，移除时按它来找；两端都要填' },
], [
  { require: { mode: 'delete' }, text: 'fog {target} delete {userId}' },
  { require: ['fogId'], text: 'fog {target} push {fogId} {userId}' },
  { text: '' },
]);

tpl('camerashake add', '给玩家加镜头抖动（爆炸 / 地震气氛）', CAT.fx, [
  { k: 'player', n: '玩家', t: 'selector', d: '@a' },
  { k: 'intensity', n: '强度', t: 'number', r: false, d: '0.1' },
  { k: 'seconds', n: '持续秒数', t: 'number', r: false, d: '1' },
  { k: 'shakeType', n: '抖动类型', t: 'choice', r: false, d: 'positional', c: ['positional', 'rotational'] },
], 'camerashake add {player} {intensity} {seconds} {shakeType}');

tpl('camerashake stop', '停止镜头抖动', CAT.fx, [
  { k: 'player', n: '玩家', t: 'selector', d: '@a' },
], 'camerashake stop {player}');

tpl('stopsound', '停止正在播放的音效', CAT.fx, [
  { k: 'player', n: '玩家', t: 'selector', d: '@a' },
], 'stopsound {player}');

tpl('music play', '播放背景音乐', CAT.fx, [
  { k: 'track', n: '曲目', h: '如 music.game、music.overworld' },
  { k: 'volume', n: '音量', t: 'number', r: false, d: '1' },
  { k: 'fadeSeconds', n: '淡入秒数', t: 'number', r: false, d: '1' },
  { k: 'repeatMode', n: '循环方式', t: 'choice', r: false, d: 'play_once', c: ['play_once', 'loop'] },
], 'music play {track} {volume} {fadeSeconds} {repeatMode}');

tpl('music stop', '停止背景音乐', CAT.fx, [
  { k: 'fadeSeconds', n: '淡出秒数', t: 'number', r: false, d: '1' },
], 'music stop {{fadeSeconds}}');

tpl('music volume', '调整音乐音量', CAT.fx, [
  { k: 'volume', n: '音量', t: 'number', d: '1' },
], 'music volume {volume}');

// 维基 BE：`music queue <trackName> [volume] [fadeSeconds] [repeatMode]`
tpl('music queue', '把一首曲目排进播放队列', CAT.fx, [
  { k: 'track', n: '曲目', d: 'music.game', h: '如 music.game、music.overworld' },
  { k: 'volume', n: '音量', t: 'number', r: false, d: '1' },
  { k: 'fadeSeconds', n: '淡入秒数', t: 'number', r: false, d: '1' },
  { k: 'repeatMode', n: '循环方式', t: 'choice', r: false, d: 'play_once', c: ['play_once', 'loop'] },
], 'music queue {track} {{volume}} {{fadeSeconds}} {{repeatMode}}');

// ═════════════════════════════════════════════════════════════════════════════
// 玩家与游戏规则
// ═════════════════════════════════════════════════════════════════════════════
tpl('gamemode', '切换游戏模式', CAT.player, [
  { k: 'mode', n: '模式', d: 'creative', h: 'survival / creative / adventure / spectator', s: 'gameModes' },
  { k: 'target', n: '目标', t: 'selector', r: false, d: '@s' },
], 'gamemode {mode} {{target}}');

tpl('xp', '给玩家增加或扣除经验', CAT.player, [
  { k: 'amount', n: '数量', t: 'number', d: '10' },
  { k: 'target', n: '目标', t: 'selector', r: false, d: '@s' },
], 'xp {amount} {{target}}');

// ⚠️ 基岩版 execute 的条件只有四种，且**没有 `store` 子命令**（那些都是 Java 的）：
//      if|unless block <坐标> <方块> [方块状态]
//      if|unless blocks <起点> <终点> <目标位置> <检测方式>
//      if|unless entity <选择器>
//      if|unless score <对象> <记分项> <运算符> <来源对象> <来源记分项>
//    Java 才有的 data / biome / dimension / predicate / loaded / function 条件，
//    以及 `execute store ...`，填进去基岩版会解析失败。
//    condition 是自由文本（允许玩家拼 `if block ~ ~-1 ~ stone` 这类组合），
//    所以这里只能靠提示与说明约束，不做语法校验。
variant('execute', '以指定身份 / 条件执行命令（新版子命令语法）', CAT.player, [
  { k: 'target', n: '执行者', t: 'selector', r: false, h: '留空则不改变执行者' },
  { k: 'condition', n: '条件', r: false, h: '仅支持：if/unless block | blocks | entity | score。如 if entity @s[tag=队长]' },
  { k: 'command', n: '要执行的命令' },
], [
  { require: ['target', 'condition', 'command'], text: 'execute as {target} at @s {condition} run {command}' },
  { require: ['target', 'command'], text: 'execute as {target} at @s run {command}' },
  { require: ['condition', 'command'], text: 'execute {condition} run {command}' },
  { require: ['command'], text: 'execute run {command}' },
  { text: 'execute' },
]);

tpl('spreadplayers', '把实体随机散布在一片区域里（做小游戏开局很好用）', CAT.player, [
  { k: 'x', n: '中心 X', d: '~' }, { k: 'z', n: '中心 Z', d: '~' },
  { k: 'spreadDistance', n: '最小间距', t: 'number', d: '10' },
  { k: 'maxRange', n: '散布范围', t: 'number', d: '50' },
  { k: 'target', n: '目标', t: 'selector', d: '@a' },
], 'spreadplayers {x} {z} {spreadDistance} {maxRange} {target}');

tpl('inputpermission', '开关玩家的移动 / 视角输入（做剧情锁操作）', CAT.player, [
  { k: 'target', n: '目标', t: 'selector', d: '@a' },
  { k: 'permission', n: '权限项', t: 'choice', d: 'movement', c: ['movement', 'camera'] },
  { k: 'state', n: '状态', t: 'choice', d: 'disabled', c: ['enabled', 'disabled'] },
], 'inputpermission set {target} {permission} {state}');

// ⚠️ `team` 是 **Java 版专有**（维基 Commands/team 页首标 {{exclusive|java}}），
//     基岩版没有这条命令。曾经照 Java 写过 team add/join/remove/list/empty/modify 六条，
//     生成的命令在基岩版里全是"未知命令"，已整组删除。
//     同理 `forceload` 也是 Java 专有，基岩版的对应物是 `tickingarea`（见上）。

// ⚠️ 可选参数顺序与默认值按维基 BE 校正：
//      structure save <name> <from> <to> [includeEntities: Boolean] [saveMode] [includeBlocks: Boolean]
//    老写法把 saveMode 放在 includeEntities **前面**（顺序错），
//    且 saveMode 默认写 disk（维基：未指定时默认 memory）。
variant('structure save', '把一块区域保存成结构（做地标 / 建筑复制）', CAT.world, [
  { k: 'name', n: '结构名' },
  { k: 'from', n: '起始坐标', d: '~ ~ ~' },
  { k: 'to', n: '结束坐标', d: '~ ~ ~' },
  { k: 'includeEntities', n: '包含实体', t: 'bool', r: false, d: 'false' },
  { k: 'saveMode', n: '保存方式', t: 'choice', r: false, h: '留空 = memory（内存，重启就没了）；disk = 存到磁盘' },
  { k: 'includeBlocks', n: '包含方块', t: 'bool', r: false, d: 'true' },
], [
  { require: ['includeBlocks'], text: 'structure save {name} {from} {to} {{includeEntities}} {{saveMode}} {includeBlocks}' },
  { require: ['includeEntities'], text: 'structure save {name} {from} {to} {includeEntities} {{saveMode}}' },
  { require: ['saveMode'], text: 'structure save {name} {from} {to} {{includeEntities}} {saveMode}' },
  { text: 'structure save {name} {from} {to}' },
]);

tpl('structure load', '把保存过的结构加载到指定位置', CAT.world, [
  { k: 'name', n: '结构名' },
  { k: 'x', n: 'X', d: '~' }, { k: 'y', n: 'Y', d: '~' }, { k: 'z', n: 'Z', d: '~' },
  { k: 'rotation', n: '旋转', t: 'choice', r: false, d: '0_degrees', c: ['0_degrees', '90_degrees', '180_degrees', '270_degrees'] },
  { k: 'mirror', n: '镜像', t: 'choice', r: false, d: 'none', c: ['none', 'x', 'z', 'xz'] },
  { k: 'animationMode', n: '生成动画', t: 'choice', r: false, d: 'block_by_block', c: ['block_by_block', 'layer_by_layer'] },
  { k: 'animationSeconds', n: '动画秒数', t: 'number', r: false, d: '0' },
  { k: 'includeEntities', n: '包含实体', t: 'bool', r: false, d: 'false' },
  { k: 'includeBlocks', n: '包含方块', t: 'bool', r: false, d: 'true' },
], 'structure load {name} {x} {y} {z} {{rotation}} {{mirror}} {{animationMode}} {{animationSeconds}} {{includeEntities}} {{includeBlocks}}');

tpl('structure delete', '删除已保存的结构', CAT.world, [
  { k: 'name', n: '结构名' },
], 'structure delete {name}');

tpl('dialogue open', '打开对话（NPC 剧情用）', CAT.player, [
  { k: 'npc', n: 'NPC', t: 'selector', d: '@e[type=npc]' },
  { k: 'player', n: '玩家', t: 'selector', d: '@s' },
  { k: 'sceneTag', n: '场景标签', r: false, h: '留空则用 NPC 自己的对话' },
], 'dialogue open {npc} {player} {{sceneTag}}');

tpl('dialogue change', '切换某个 NPC 的对话场景', CAT.player, [
  { k: 'npc', n: 'NPC', t: 'selector', d: '@e[type=npc]' },
  { k: 'sceneTag', n: '场景标签' },
  { k: 'player', n: '玩家', t: 'selector', r: false, d: '@s' },
], 'dialogue change {npc} {sceneTag} {{player}}');

tpl('reload', '重新加载行为包与函数（改完数据包不用重启）', CAT.player, [], 'reload');

tpl('function', '运行函数文件里的一组命令', CAT.player, [
  { k: 'name', n: '函数路径', h: '如 mypack:start' },
], 'function {name}');

tpl('schedule on_area_loaded add', '区域加载时延后执行函数', CAT.player, [
  { k: 'circle', n: '区域', h: '如 circle 0 0 0 20' },
  { k: 'name', n: '函数路径' },
], 'schedule on_area_loaded add {circle} {name}');

tpl('gametest run', '运行游戏内测试（开发者用）', CAT.player, [
  { k: 'testName', n: '测试名' },
], 'gametest run {testName}');

// 维基 BE gametest 的子命令（原来只有 run 一条）：
//   runthis / runthese / runset / runsetuntilfail / clearall / create / pos / stopall
variant('gametest runthis', '运行距离最近的测试', CAT.player, [
  { k: 'testName', n: '测试名', r: false },
], [
  { require: ['testName'], text: 'gametest runthis {testName}' },
  { text: 'gametest runthis' },
]);

tpl('gametest runthese', '运行一定范围内的所有测试', CAT.player, [
  { k: 'radius', n: '半径', t: 'number', r: false, d: '10' },
], 'gametest runthese {{radius}}');

tpl('gametest runset', '运行一整个测试类', CAT.player, [
  { k: 'tag', n: '测试类标签', r: false, h: '留空 = 全部' },
], 'gametest runset {{tag}}');

tpl('gametest runsetuntilfail', '运行测试类，遇到第一个失败就停', CAT.player, [
  { k: 'tag', n: '测试类标签', r: false },
], 'gametest runsetuntilfail {{tag}}');

tpl('gametest clearall', '清理测试区域', CAT.player, [
  { k: 'radius', n: '半径', t: 'number', r: false, d: '10' },
], 'gametest clearall {{radius}}');

tpl('gametest create', '在指定位置创建测试结构', CAT.player, [
  { k: 'name', n: '测试名' },
  { k: 'x', n: 'X', r: false, d: '~' }, { k: 'y', n: 'Y', r: false, d: '~' }, { k: 'z', n: 'Z', r: false, d: '~' },
], 'gametest create {name} {{x}} {{y}} {{z}}');

tpl('gametest pos', '定位测试结构的位置', CAT.player, [], 'gametest pos');

tpl('gametest stopall', '停止所有正在运行的测试', CAT.player, [], 'gametest stopall');

// 维基 BE schedule 的其他重载（原来只有 on_area_loaded add）：
//   delay add <function> <delay> [append|replace]  /  delay clear <function>
//   on_area_loaded clear function <function>  /  on_area_loaded clear tickingarea <name> [chunkX chunkZ]
tpl('schedule delay add', '延迟若干刻之后执行函数', CAT.player, [
  { k: 'name', n: '函数路径', d: 'mypack:start' },
  { k: 'delay', n: '延迟（刻）', t: 'number', d: '20', h: '20 刻 = 1 秒' },
  { k: 'mode', n: '重复时', t: 'choice', r: false, d: 'replace', c: ['replace', 'append'] },
], 'schedule delay add {name} {delay} {{mode}}');

tpl('schedule delay clear', '取消延迟执行的函数', CAT.player, [
  { k: 'name', n: '函数路径', d: 'mypack:start' },
], 'schedule delay clear {name}');

tpl('schedule clear', '清除某个函数的全部计划任务', CAT.player, [
  { k: 'name', n: '函数路径', d: 'mypack:start' },
], 'schedule clear {name}');

variant('schedule on_area_loaded clear', '取消区域加载时的计划任务', CAT.player, [
  { k: 'kind', n: '按什么取消', t: 'choice', d: 'function', c: ['function', 'tickingarea'] },
  { k: 'name', n: '函数路径 / 区域名', d: 'mypack:start' },
  { k: 'chunkX', n: '区块 X', r: false, h: 'tickingarea 时可指定区块' },
  { k: 'chunkZ', n: '区块 Z', r: false },
], [
  { require: { kind: 'tickingarea' }, text: 'schedule on_area_loaded clear tickingarea {name} {{chunkX}} {{chunkZ}}' },
  { require: ['name'], text: 'schedule on_area_loaded clear function {name}' },
  { text: '' },
]);

// ═════════════════════════════════════════════════════════════════════════════
// 第二轮按维基补的基岩版命令
//
// 这 45 条来自维基 Commands 页的
// "Available in Bedrock Edition and Minecraft Education" 表（BE 列 = yes），
// 每条语法都取自各自的 Commands/<name> 页面原文，没有凭记忆写。
// 详见 Chat_Room/命令助手-命令库维基核对报告.md。
//
// 其中服务器/编辑器专用的（allowlist、stop、project、sendshowstoreoffer…）
// 也一并收录 —— 站长要求"补全原版所有命令"，而且这些在单人开作弊的世界、
// 以及自建 BDS 服务器里都能用。
// ═════════════════════════════════════════════════════════════════════════════

// ── 帮助 ──────────────────────────────────────────────────────────────────
// 维基 BE：`help <command>` 看某条命令的用法；`help [page]` 列出全部命令。
variant('help', '查看命令用法或列出全部命令', CAT.text, [
  { k: 'command', n: '命令名', r: false, h: '填了就看这条命令的用法；不填则列出全部' },
  { k: 'page', n: '页码', t: 'number', r: false, d: '1' },
], [
  { require: ['command'], text: 'help {command}' },
  { text: 'help {page|1}' },
]);

// ── 玩家能力 ──────────────────────────────────────────────────────────────
// 维基 BE：`ability <player> <ability> [value]` / `ability <player> [ability]`
tpl('ability', '授予或撤销玩家能力（飞行、隐身、世界建造者等）', CAT.player, [
  { k: 'player', n: '玩家', t: 'selector', d: '@s' },
  { k: 'ability', n: '能力', r: false, h: '如 mayfly、mute、worldbuilder、invulnerable' },
  { k: 'value', n: '开关', t: 'bool', r: false, d: 'true' },
], 'ability {player} {{ability}} {{value}}');

// ── 服务器白名单 ──────────────────────────────────────────────────────────
// 维基 whitelist 页：BE 用 `allowlist`（whitelist 是它的别名），
// 且 BE **没有** on / off（未实现），只有 add / remove / list / reload。
variant('allowlist', '管理服务器白名单（allowlist 是基岩版的写法）', CAT.player, [
  { k: 'mode', n: '操作', t: 'choice', d: 'list', c: ['add', 'remove', 'list', 'reload'] },
  { k: 'name', n: '玩家名', r: false, h: 'add / remove 时填；不需要玩家在线' },
], [
  { require: { mode: 'list' }, text: 'allowlist list' },
  { require: { mode: 'reload' }, text: 'allowlist reload' },
  { require: ['name'], text: 'allowlist {mode} {name}' },
  { text: '' },
]);

// ── 瞄准辅助 ──────────────────────────────────────────────────────────────
// 维基 BE：`aimassist <players> clear` / `aimassist <players> set [参数…]`
tpl('aimassist', '设置或清除玩家的瞄准辅助（基岩版特有的辅助瞄准）', CAT.player, [
  { k: 'players', n: '玩家', t: 'selector', d: '@s' },
  { k: 'mode', n: '操作', t: 'choice', d: 'clear', c: ['clear', 'set'] },
  { k: 'angleX', n: '水平角度', t: 'number', r: false, h: 'set 时可选' },
  { k: 'angleY', n: '垂直角度', t: 'number', r: false },
  { k: 'distance', n: '最大距离', t: 'number', r: false },
  { k: 'targetMode', n: '目标模式', r: false, h: 'AimAssistTargetMode 枚举' },
  { k: 'presetId', n: '预设 ID', r: false },
], 'aimassist {players} {mode} {{angleX}} {{angleY}} {{distance}} {{targetMode}} {{presetId}}');

// ── 锁定昼夜 ──────────────────────────────────────────────────────────────
// 维基 daylock 页：`daylock [lock]`，alwaysday 是别名。只接受 true / false。
tpl('daylock', '锁定或解锁永昼', CAT.world, [
  { k: 'lock', n: '锁定', t: 'bool', r: false, d: 'true' },
], 'daylock {{lock}}');

// ── 服务器设置（运行时） ────────────────────────────────────────────────────
// 维基 BE：`changesetting allow-cheats <bool>` / `changesetting difficulty <难度>`
variant('changesetting', '在专用服务器运行时修改设置', CAT.player, [
  { k: 'setting', n: '设置项', t: 'choice', d: 'allow-cheats', c: ['allow-cheats', 'difficulty'] },
  { k: 'value', n: '值', d: 'true', h: 'allow-cheats 用 true/false；difficulty 用 peaceful/easy/normal/hard' },
], [
  { require: { setting: 'allow-cheats' }, text: 'changesetting allow-cheats {value|true}' },
  { require: ['value'], text: 'changesetting difficulty {value}' },
  { text: '' },
]);

// ── 清除出生点 ────────────────────────────────────────────────────────────
// 维基 BE：`clearspawnpoint [player]`
tpl('clearspawnpoint', '清除玩家的个人出生点', CAT.world, [
  { k: 'player', n: '玩家', t: 'selector', r: false, d: '@s' },
], 'clearspawnpoint {{player}}');

// ── 连接 WebSocket ────────────────────────────────────────────────────────
// 维基 wsserver 页：`wsserver <serverUri>`，connect 是别名。
tpl('wsserver', '连接 WebSocket 服务器（教育版/脚本调试用）', CAT.player, [
  { k: 'uri', n: '服务器地址', d: 'ws://localhost:19131', h: '如 ws://localhost:19131' },
], 'wsserver {uri}');

// ── 控制方案 ──────────────────────────────────────────────────────────────
tpl('controlscheme', '切换创作者相机的控制方案', CAT.player, [
  { k: 'players', n: '玩家', t: 'selector', d: '@s' },
  { k: 'mode', n: '操作', t: 'choice', d: 'clear', c: ['clear', 'set'] },
  { k: 'scheme', n: '控制方案', r: false, h: 'set 时必填，见 controlscheme 枚举' },
], 'controlscheme {players} {mode} {{scheme}}');

// ── 专用服务器连接 ─────────────────────────────────────────────────────────
tpl('dedicatedwsserver', '连接专用服务器的 WebSocket 地址', CAT.player, [
  { k: 'address', n: '服务器地址', d: 'localhost' },
  { k: 'port', n: '端口', t: 'number', d: '19131' },
], 'dedicatedwsserver {address} {port}');

// ── 管理员 ────────────────────────────────────────────────────────────────
tpl('op', '把玩家设为管理员', CAT.player, [
  { k: 'player', n: '玩家', t: 'selector', d: '@s' },
], 'op {player}');

tpl('deop', '撤销玩家的管理员身份', CAT.player, [
  { k: 'player', n: '玩家', t: 'selector', d: '@s' },
], 'deop {player}');

// ── 权限重载 ──────────────────────────────────────────────────────────────
// 维基 permission 页：list / reload / set <player> [等级]；ops 是别名。
variant('permission', '查看或重载服务器权限列表（ops 是别名）', CAT.player, [
  { k: 'mode', n: '操作', t: 'choice', d: 'list', c: ['list', 'reload', 'set'] },
  { k: 'player', n: '玩家', t: 'selector', r: false, d: '@s' },
  { k: 'level', n: '权限等级', r: false, h: '如 member / operator / visitor' },
], [
  { require: { mode: 'list' }, text: 'permission list' },
  { require: { mode: 'reload' }, text: 'permission reload' },
  { require: ['player'], text: 'permission set {player} {{level}}' },
  { text: '' },
]);

// ── 编辑器白名单 ──────────────────────────────────────────────────────────
// 维基把它列在 whitelist 页的 editor-allowlist 小节，操作与 allowlist 同构。
variant('editor-allowlist', '管理 Minecraft Editor 服务器的白名单', CAT.player, [
  { k: 'mode', n: '操作', t: 'choice', d: 'list', c: ['add', 'remove', 'list'] },
  { k: 'name', n: '玩家名', r: false, h: 'add / remove 时填' },
], [
  { require: { mode: 'list' }, text: 'editor-allowlist list' },
  { require: ['name'], text: 'editor-allowlist {mode} {name}' },
  { text: '' },
]);

// ── 附魔 ──────────────────────────────────────────────────────────────────
// 维基 BE 两条形式：`enchant <player> <enchantmentId: int> [level]`
//              与 `enchant <player> <enchantmentName: Enchant> [level]`
tpl('enchant', '给玩家手持的物品附魔（不消耗经验）', CAT.item, [
  { k: 'player', n: '玩家', t: 'selector', d: '@s' },
  { k: 'enchantment', n: '附魔', d: 'sharpness', h: '附魔名（如 sharpness）或数字 ID' },
  { k: 'level', n: '等级', t: 'number', r: false, d: '1' },
], 'enchant {player} {enchantment} {{level}}');

// ── 实体事件 ──────────────────────────────────────────────────────────────
tpl('event', '对实体触发一个行为包事件', CAT.entity, [
  { k: 'target', n: '目标', t: 'selector', d: '@e' },
  { k: 'eventName', n: '事件名', d: 'minecraft:entity_born', h: '如 minecraft:entity_born' },
], 'event entity {target} {eventName}');

// ── 游戏提示 ──────────────────────────────────────────────────────────────
tpl('gametips', '开关本机的游戏提示', CAT.player, [
  { k: 'status', n: '状态', t: 'choice', d: 'on', c: ['on', 'off'] },
], 'gametips {status}');

// ── HUD 元素 ──────────────────────────────────────────────────────────────
tpl('hud', '显示或隐藏某个 HUD 元素', CAT.player, [
  { k: 'target', n: '目标', t: 'selector', d: '@s' },
  { k: 'visible', n: '可见性', t: 'choice', d: 'hide', c: ['hide', 'show'] },
  { k: 'element', n: 'HUD 元素', r: false, h: '如 paper_doll、armor、tooltips、crosshair' },
], 'hud {target} {visible} {{element}}');

// ── 世界不可变 ────────────────────────────────────────────────────────────
tpl('immutableworld', '锁定世界，禁止玩家破坏或放置方块', CAT.world, [
  { k: 'value', n: '锁定', t: 'bool', r: false, d: 'true' },
], 'immutableworld {{value}}');

// ── 踢人 ──────────────────────────────────────────────────────────────────
tpl('kick', '把玩家踢出服务器', CAT.player, [
  { k: 'target', n: '玩家', t: 'selector', d: '@s' },
  { k: 'reason', n: '原因', t: 'long', r: false, h: '会显示给被踢的玩家' },
], 'kick {target} {{reason}}');

// ── 在线列表 ──────────────────────────────────────────────────────────────
tpl('list', '列出服务器上在线的玩家', CAT.player, [], 'list');

// ── 战利品 ────────────────────────────────────────────────────────────────
// 维基 BE 的 TARGET 有 give / insert / spawn / replace block|entity；
// SOURCE 有 kill / loot / mine。这里给最常用的几种组合，不堆满笛卡尔积。
variant('loot', '把战利品表的内容给玩家 / 塞进容器 / 掉到地上', CAT.item, [
  { k: 'targetMode', n: '给到哪', t: 'choice', d: 'give', c: ['give', 'insert', 'spawn'] },
  { k: 'player', n: '玩家', t: 'selector', r: false, d: '@s', h: 'give 时用' },
  { k: 'x', n: 'X', r: false, d: '~' }, { k: 'y', n: 'Y', r: false, d: '~' }, { k: 'z', n: 'Z', r: false, d: '~' },
  { k: 'sourceMode', n: '来源', t: 'choice', d: 'loot', c: ['loot', 'kill', 'mine'] },
  { k: 'table', n: '战利品表', r: false, d: 'minecraft:chests/simple_dungeon', h: '如 minecraft:chests/simple_dungeon' },
  { k: 'entity', n: '实体', t: 'selector', r: false, d: '@e', h: 'source 选 kill 时填' },
], [
  { require: { sourceMode: 'kill' }, text: 'loot {targetMode} {player} kill {entity}' },
  { require: { sourceMode: 'mine' }, text: 'loot {targetMode} {player} mine {x} {y} {z}' },
  { require: { targetMode: 'give' }, text: 'loot give {player} loot {table}' },
  { require: ['x'], text: 'loot {targetMode} {x} {y} {z} loot {table}' },
  { text: '' },
]);

// ── 生物事件 ──────────────────────────────────────────────────────────────
tpl('mobevent', '启用或禁用指定的生物事件', CAT.entity, [
  { k: 'event', n: '生物事件', d: 'minecraft:pillager_patrols_event', h: '如 minecraft:pillager_patrols_event' },
  { k: 'value', n: '开关', t: 'bool', r: false, d: 'true' },
], 'mobevent {event} {{value}}');

// ── 私聊（别名） ───────────────────────────────────────────────────────────
// 维基：msg / w / tell 是同一条命令的三个名字，语法都是 <target> <message>。
tpl('msg', '给指定玩家发私聊消息（tell / w 是别名）', CAT.text, [
  { k: 'target', n: '目标', t: 'selector', d: '@a' },
  { k: 'message', n: '消息内容', t: 'long' },
], 'msg {target} {message}');

// ── 队伍聊天 ──────────────────────────────────────────────────────────────
tpl('party', '向当前队伍聊天频道发一条消息', CAT.text, [
  { k: 'message', n: '消息内容', t: 'long' },
], 'party {message}');

// ── 放置结构 / 地物 ────────────────────────────────────────────────────────
// 维基 BE：place feature / featurerule / jigsaw / structure。
variant('place', '在指定位置放置地物、拼图或结构', CAT.world, [
  { k: 'kind', n: '类型', t: 'choice', d: 'feature', c: ['feature', 'featurerule', 'jigsaw', 'structure'] },
  { k: 'name', n: '名称', d: 'minecraft:oak', h: 'feature 填地物名（如 minecraft:oak）；structure 填结构名' },
  { k: 'x', n: 'X', r: false, d: '~' }, { k: 'y', n: 'Y', r: false, d: '~' }, { k: 'z', n: 'Z', r: false, d: '~' },
  { k: 'pool', n: '拼图池', r: false, h: 'jigsaw 专用，如 minecraft:village/plains/houses' },
  { k: 'jigsawTarget', n: '起始拼图', r: false, h: 'jigsaw 专用' },
  { k: 'maxDepth', n: '最大深度', t: 'number', r: false, d: '7', h: 'jigsaw 专用' },
], [
  { require: { kind: 'jigsaw' }, text: 'place jigsaw {pool} {jigsawTarget} {maxDepth|7} {{x}} {{y}} {{z}}' },
  { require: ['name'], text: 'place {kind} {name} {{x}} {{y}} {{z}}' },
  { text: '' },
]);

// ── 播放动画 ──────────────────────────────────────────────────────────────
tpl('playanimation', '让实体播放一次动画（行为包定义过的）', CAT.entity, [
  { k: 'entity', n: '实体', t: 'selector', d: '@e' },
  { k: 'animation', n: '动画名', d: 'animation.armor_stand.attack_rot', h: '行为包里的动画名' },
  { k: 'nextState', n: '下一状态', r: false },
  { k: 'blendOutTime', n: '淡出秒数', t: 'number', r: false },
  { k: 'stopExpression', n: '停止表达式', r: false },
  { k: 'controller', n: '控制器', r: false },
], 'playanimation {entity} {animation} {{nextState}} {{blendOutTime}} {{stopExpression}} {{controller}}');

// ── 编辑器项目 ────────────────────────────────────────────────────────────
tpl('project', 'Minecraft Editor 的项目工具', CAT.player, [
  { k: 'exportType', n: '导出类型', d: 'minecraft:project', h: 'ExportTypes 枚举' },
], 'project export {exportType}');

// ── 配方 ──────────────────────────────────────────────────────────────────
// 维基 BE：`recipe give <player> <recipe|*>` / `recipe take ...`。
// `*` 要加引号才不会在部分版本被当通配符吃掉。
variant('recipe', '解锁或锁定玩家的配方', CAT.item, [
  { k: 'mode', n: '操作', t: 'choice', d: 'give', c: ['give', 'take'] },
  { k: 'player', n: '玩家', t: 'selector', d: '@s' },
  { k: 'recipe', n: '配方', r: false, d: '"*"', h: '填 "*" 表示全部配方；也可填具体配方名' },
], [
  { require: ['recipe'], text: 'recipe {mode} {player} {recipe}' },
  { text: 'recipe {mode} {player} "*"' },
]);

// ── 重载配置 ──────────────────────────────────────────────────────────────
tpl('reloadconfig', '重新加载变量、权限等配置文件', CAT.player, [], 'reloadconfig');

tpl('reloadpacketlimitconfig', '重新加载 packetlimitconfig.json', CAT.player, [], 'reloadpacketlimitconfig');

// ── 存档备份 ──────────────────────────────────────────────────────────────
// 维基 BE 只有 `save` 一条（1.6.1 加入），子命令用于备份流程。
// ⚠️ 维基没有列出子命令细节，这里按 BDS 的公开用法给 hold / query / resume；
//    如果以后维基补全了，以维基为准。
variant('save', '准备备份、查询备份状态或恢复存档', CAT.world, [
  { k: 'mode', n: '操作', t: 'choice', r: false, h: '留空 = 开始保存；hold = 挂起等待复制；query = 查询；resume = 恢复' },
], [
  { require: ['mode'], text: 'save {mode}' },
  { text: 'save' },
]);

// ── 脚本调试 ──────────────────────────────────────────────────────────────
variant('script', 'GameTest 框架的脚本调试工具', CAT.player, [
  { k: 'group', n: '类别', t: 'choice', d: 'debugger', c: ['debugger', 'profiler', 'watchdog'] },
  { k: 'action', n: '操作', d: 'close', h: 'debugger: close/connect/listen；profiler: start/stop；watchdog: exportstats' },
  { k: 'host', n: '主机', r: false },
  { k: 'port', n: '端口', t: 'number', r: false },
], [
  { require: { group: 'debugger' }, text: 'script debugger {action|close} {{host}} {{port}}' },
  { require: { group: 'profiler' }, text: 'script profiler {action|start}' },
  { require: { group: 'watchdog' }, text: 'script watchdog exportstats' },
  { text: '' },
]);

// ── 脚本事件 ──────────────────────────────────────────────────────────────
tpl('scriptevent', '用一个 ID 和消息触发脚本事件', CAT.player, [
  { k: 'messageId', n: '事件 ID', d: 'minecraft:example', h: '如 mypack:hello' },
  { k: 'message', n: '消息内容', t: 'long', r: false },
], 'scriptevent {messageId} {{message}}');

// ── 商店优惠 ──────────────────────────────────────────────────────────────
variant('sendshowstoreoffer', '请求向玩家显示商店优惠', CAT.player, [
  { k: 'player', n: '玩家', t: 'selector', d: '@s' },
  { k: 'mode', n: '方式', t: 'choice', d: 'server', c: ['server', 'offer'] },
  { k: 'redirectType', n: '跳转类型', r: false, h: 'offer 时填，RedirectLocation 枚举' },
  { k: 'offerId', n: '优惠 ID', r: false, h: 'offer 时填' },
], [
  { require: { mode: 'server' }, text: 'sendshowstoreoffer {player} server' },
  { require: ['offerId'], text: 'sendshowstoreoffer {player} {redirectType} {offerId}' },
  { text: '' },
]);

// ── 移动校验 ──────────────────────────────────────────────────────────────
tpl('set_movement_authority', '设置玩家移动由客户端还是服务器校验', CAT.player, [
  { k: 'authority', n: '校验方', t: 'choice', d: 'server', c: ['server', 'client'] },
], 'set_movement_authority {authority}');

// ── 最大玩家数 ────────────────────────────────────────────────────────────
tpl('setmaxplayers', '设置服务器允许加入的最大玩家数', CAT.player, [
  { k: 'maxPlayers', n: '最大人数', t: 'number', d: '10' },
], 'setmaxplayers {maxPlayers}');

// ── 个人出生点 ────────────────────────────────────────────────────────────
// 维基 BE：`spawnpoint [player] [spawnPos: x y z]`
variant('spawnpoint', '设置玩家的个人出生点（重生点）', CAT.world, [
  { k: 'player', n: '玩家', t: 'selector', r: false, d: '@s' },
  { k: 'x', n: 'X', r: false, d: '~' }, { k: 'y', n: 'Y', r: false, d: '~' }, { k: 'z', n: 'Z', r: false, d: '~' },
], [
  { require: ['player', 'x', 'y', 'z'], text: 'spawnpoint {player} {x} {y} {z}' },
  { require: ['player'], text: 'spawnpoint {player}' },
  { require: ['x', 'y', 'z'], text: 'spawnpoint {x} {y} {z}' },
  { text: 'spawnpoint' },
]);

// ── 停止服务器 ────────────────────────────────────────────────────────────
tpl('stop', '停止服务器', CAT.player, [], 'stop');

// ── 检测方块 ──────────────────────────────────────────────────────────────
// ⚠️ 这条在**新版基岩版仍可用**（维基 BE 命令表列着），
//    但官方推荐用 `execute if block` 代替。这里收录是为了"命令库完整"，
//    描述里会说明它属于旧式检测命令。
tpl('testforblock', '检测指定位置是不是某个方块（旧式写法，建议改用 execute if block）', CAT.world, [
  { k: 'x', n: 'X', d: '~' }, { k: 'y', n: 'Y', d: '~' }, { k: 'z', n: 'Z', d: '~' },
  { k: 'block', n: '方块 ID', s: 'blocks' },
  { k: 'states', n: '方块状态', r: false, s: 'blockStates' },
], 'testforblock {x} {y} {z} {block} {{states}}');

tpl('testforblocks', '检测两个区域的方块是否相同（旧式写法）', CAT.world, [
  { k: 'x1', n: '起点 X', d: '~' }, { k: 'y1', n: '起点 Y', d: '~' }, { k: 'z1', n: '起点 Z', d: '~' },
  { k: 'x2', n: '终点 X', d: '~' }, { k: 'y2', n: '终点 Y', d: '~' }, { k: 'z2', n: '终点 Z', d: '~' },
  { k: 'dx', n: '目标 X', d: '~' }, { k: 'dy', n: '目标 Y', d: '~' }, { k: 'dz', n: '目标 Z', d: '~' },
  { k: 'maskMode', n: '比较方式', t: 'choice', r: false, d: 'all', c: ['all', 'masked'] },
], 'testforblocks {x1} {y1} {z1} {x2} {y2} {z2} {dx} {dy} {dz} {{maskMode}}');

// ── 切换天气 ──────────────────────────────────────────────────────────────
tpl('toggledownfall', '在晴天与下雨之间切换', CAT.world, [], 'toggledownfall');

// ── 转移服务器 ────────────────────────────────────────────────────────────
// 维基 BE：`transfer <pfidOrMSA: string> <server: string> <port: int>`
tpl('transfer', '把玩家转移到另一台服务器', CAT.player, [
  { k: 'player', n: '玩家标识', d: '@s', h: 'BE 写 pfid 或 MSA 标识' },
  { k: 'server', n: '服务器地址', d: 'localhost' },
  { k: 'port', n: '端口', t: 'number', d: '19132' },
], 'transfer {player} {server} {port}');

// ── 世界建造者 ────────────────────────────────────────────────────────────
tpl('worldbuilder', '切换世界建造者模式（可编辑受限方块）', CAT.world, [], 'worldbuilder');

// 把中文名挂到每条命令上。
// 放在最后统一做，而不是在 72 处定义里各写一遍 —— 那样漏一条都不会被发现，
// 而这里可以对"表里有命令名却没配中文名"直接报错。
for (const c of commands) {
  const cn = COMMAND_CN[c.keyword];
  if (cn) c.aliases = cn.split('/').map((s) => s.trim()).filter(Boolean);
}

// 自检：任何一条命令缺中文名就直接失败，避免以后新增命令忘了配。
{
  const missing = commands.filter((c) => !c.aliases || c.aliases.length === 0).map((c) => c.keyword);
  if (missing.length) {
    throw new Error(`这些命令没有配中文名（请在 COMMAND_CN 里补）：${missing.join(', ')}`);
  }
}

export { commands };
