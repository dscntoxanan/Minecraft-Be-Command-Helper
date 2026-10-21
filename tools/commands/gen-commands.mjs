// 生成 CommandLibrary.kt
//
// 把 tools/commands/commands.data.mjs 里的命令数据编译成 Kotlin。
//
// 为什么用生成器：基岩版有 70+ 条命令，每条都要声明参数、类型、候选、生成逻辑。
// 手写又慢又容易错（本项目已经踩过 lambda 里 `return@CommandSpec` 的语法坑、
// 多词命令匹配的边界问题、以及组件 JSON 的占位顺序）。生成出来的 Kotlin
// 依然是**可读的、带中文注释的普通代码**，不是黑箱。
//
// 用法: node tools/commands/gen-commands.mjs

import { writeFileSync } from 'node:fs';
import { commands } from './commands.data.mjs';

const OUT = 'D:\\Agent\\Deepseek\\CommandAssistant\\app\\src\\main\\java\\com\\xanan\\cmdhelper\\json\\CommandLibrary.kt';

const TYPE = {
  text: 'ParamType.TEXT',
  long: 'ParamType.LONG_TEXT',
  number: 'ParamType.NUMBER',
  bool: 'ParamType.BOOLEAN',
  choice: 'ParamType.CHOICE',
  json: 'ParamType.JSON',
  selector: 'ParamType.SELECTOR',
};

/**
 * 词表候选：值取自 `Suggest.<name>.id`，显示名取自 `.name`。
 *
 * ⚠️ `items` 指向 **`Suggest.giveableItems`**（物品 ∪ 方块），不是 `Suggest.items`。
 *    原因：基岩版里**方块也是合法的 /give 目标**
 *    （`/give @s oak_log`、`/give @s white_wool`、`/give @s stone` 都成立），
 *    而"纯物品"归 `items`、方块归 `blocks`。
 *    早先这里映射到 `Suggest.items`，于是 `/give @s oak_log` 会得到
 *    "没有匹配的项" + 一整屏按字母序排的物品 —— 玩家看到的就是"搜不到原木"
 *    （真机上复现过，见 Chat_Room/命令助手-词表核对与补全报告.md）。
 *
 *    这也让 `suggestionsFor` 第 1 步（参数自带候选）与第 3 步的回退
 *    （`textSuggestions` 里 `give`/`replaceitem`）**保持一致**，
 *    否则两条路径会给不同的表。
 */
const SUGGEST = {
  items: 'Suggest.giveableItems',
  blocks: 'Suggest.blocks',
  entities: 'Suggest.entities',
  effects: 'Suggest.effects',
  particles: 'Suggest.particles',
  sounds: 'Suggest.sounds',
  gameModes: 'Suggest.gameModes',
  gamerules: 'Suggest.gamerules',
};

/** 内联候选（与语言文件无关的固定值）。 */
const INLINE_SUGGEST = {
  blockStates: ['["facing_direction"=1]', '["facing_direction"=0]', '["powered_bit"=true]', '["open_bit"=true]'],
  giveComponents: [
    '{"item_name":"名刀·村正"}',
    '{"item_name":"宝剑","enchantments":[{"id":"sharpness","lvl":5}]}',
    '{"minecraft:can_destroy":{"blocks":["dirt"]}}',
  ],
};

/** 粒子名要带 `minecraft:` 前缀，词表里已经带好了，这里直接取。 */
const SUGGEST_RAW = {
  particles: 'Suggest.particles',
  sounds: 'Suggest.sounds',
};

const kstr = (s) => `"${String(s).replace(/\\/g, '\\\\').replace(/\$/g, '\\$').replace(/"/g, '\\"')}"`;

// ─────────────────────────────────────────────────────────────────────────────
// 参数 → Kotlin
// ─────────────────────────────────────────────────────────────────────────────
function emitParam(p, command) {
  const parts = [`key = ${kstr(p.k)}`, `label = ${kstr(p.n)}`];
  if (p.t && p.t !== 'text') parts.push(`type = ${TYPE[p.t]}`);

  // 默认**必填**：只有显式写 `r: false` 的才算可选。
  // ⚠️ 这里不能用"有没有默认值"来判断必填 —— 有默认值只说明界面预填什么，
  // 例如 give 的 item（无默认值但必填）、target（有默认值 @s 且可选）。
  // 早先写反过：有默认值的都被当成可选，于是 syntaxTemplate() 把它们全滤掉了，
  // 生成出来的模板只剩 `<JSON 组件:JSON?>` 这种残缺内容（实测踩过）。
  const required = p.r !== false;
  if (!required) parts.push('required = false');
  if (p.d !== undefined) parts.push(`defaultValue = ${kstr(p.d)}`);
  if (p.c) parts.push(`choices = listOf(${p.c.map(kstr).join(', ')})`);
  if (p.h) parts.push(`hint = ${kstr(p.h)}`);

  // 候选：choices（词表）/ s（词表或内联）
  const table = p.choices ?? p.s;
  if (table) {
    if (SUGGEST[table]) {
      parts.push(`suggestions = ${SUGGEST[table]}.map { it.id }`);
      parts.push(`suggestionLabels = ${SUGGEST[table]}.map { it.name }`);
    } else if (INLINE_SUGGEST[table]) {
      parts.push(`suggestions = listOf(${INLINE_SUGGEST[table].map(kstr).join(', ')})`);
    } else {
      throw new Error(`${command}: 未知候选片段 ${table}`);
    }
  }
  return `                CommandParam(\n                    ${parts.join(',\n                    ')}\n                ),`;
}

// ─────────────────────────────────────────────────────────────────────────────
// 模板 → Kotlin
//
// 模板语法（刻意做得很小）：
//   {key}        取值；为空则整条命令不生成
//   {key|默认}   取值，为空时用默认值
//   {{...}}      可选段：段内占位符全为空时整段省略，否则以"空格 + 内容"追加
// ─────────────────────────────────────────────────────────────────────────────
function splitTemplate(text, command) {
  const segs = [];
  let i = 0;
  while (i < text.length) {
    if (text.startsWith('{{', i)) {
      const end = text.indexOf('}}', i + 2);
      if (end < 0) throw new Error(`${command}: 模板括号不匹配 -> ${text}`);
      segs.push({ optional: true, raw: text.slice(i + 2, end) });
      i = end + 2;
    } else {
      const next = text.indexOf('{{', i);
      const stop = next < 0 ? text.length : next;
      segs.push({ optional: false, raw: text.slice(i, stop) });
      i = stop;
    }
  }
  // 段内拆成 字面量 / 占位符
  //
  // 占位符有三种写法，注意第二种：`{{key}}` 里那个 key **本身没有花括号**
  // （外层 `{{ }}` 已经把它包住了），所以正则必须同时接受"裸 key" ——
  // 只认 `{key}` 的话，`{{slotId}}` 会被当成字面量 "slotId"，
  // 生成出 `append("slotId")` 和 `if ()` 这种废代码（实测踩过）。
  //   {key} / {key|默认}   普通取值
  //   key                  裸 key（只在可选段里出现）
  //   {@json:key}          取值并做 JSON 字符串转义
  return segs.map((seg) => {
    const parts = [];
    const re = seg.optional
      ? /\{(@json:)?([A-Za-z0-9_]+)(?:\|([^}]*))?\}|([A-Za-z0-9_]+)/g
      : /\{(@json:)?([A-Za-z0-9_]+)(?:\|([^}]*))?\}/g;
    let last = 0;
    let m;
    while ((m = re.exec(seg.raw)) !== null) {
      if (m.index > last) parts.push({ kind: 'lit', text: seg.raw.slice(last, m.index) });
      const key = m[2] ?? m[4];
      parts.push({
        kind: 'key',
        json: Boolean(m[1]),
        key,
        def: m[3],
        optional: seg.optional,
      });
      last = m.index + m[0].length;
    }
    if (last < seg.raw.length) parts.push({ kind: 'lit', text: seg.raw.slice(last) });
    return { optional: seg.optional, parts };
  });
}

/** 生成渲染一条模板的 Kotlin 语句块。 */
function emitTemplate(text, command, indent, keyword) {
  const pad = ' '.repeat(indent);
  // 空模板 = 这个分支明确"不生成命令"（例如 titleraw 的 set 模式却没给 JSON）。
  if (text === '') {
    return `${pad}return@CommandSpec null`;
  }
  const segs = splitTemplate(text, command);
  const out = [];

  // 首段是否"以关键字开头"——决定后面要补一个空格。
  // 注意要在**剥离关键字之前**判断，因为剥离后首个 part 往往已经变成 key 了。
  const headStartsWithKeyword =
    segs.length > 0 && !segs[0].optional &&
    segs[0].parts[0]?.kind === 'lit' &&
    segs[0].parts[0].text.startsWith(keyword);

  // 关键字已经写进 StringBuilder(keyword)，模板里不能再含关键字，
  // 否则会输出 `titlerawtitleraw @a`（实测踩过）；关键字后面自带的那个空格也要
  // 一起剥掉 —— 空格统一由后面的 append(" ") 负责，否则会拼出双空格。
  if (headStartsWithKeyword) {
    const head = segs[0].parts[0].text.slice(keyword.length).replace(/^ +/, '');
    segs[0].parts[0] = { ...segs[0].parts[0], text: head };
    if (segs[0].parts[0].text === '') segs[0].parts.shift();
  }

  // 必填占位符（非可选段、且没有默认值）为空时直接不生成
  const required = [];
  for (const seg of segs) {
    if (seg.optional) continue;
    for (const part of seg.parts) {
      if (part.kind === 'key' && part.def === undefined && !required.includes(part.key)) {
        required.push(part.key);
      }
    }
  }
  if (required.length > 0) {
    const cond = required.map((k) => `v.v("${k}").isNotEmpty()`).join(' && ');
    out.push(`${pad}if (!(${cond})) return@CommandSpec null`);
  }

  out.push(`${pad}val sb = StringBuilder(${kstr(keyword)})`);
  // 模板是以关键字开头的 → 第一个拼接项前必须先补一个空格。
  let pendingSpace = headStartsWithKeyword;
  for (const seg of segs) {
    const pieces = seg.parts.map((part) => {
      if (part.kind === 'lit') return kstr(part.text);
      // {@json:key}：取值后必须做 JSON 字符串转义，否则带空格/中文/引号的名字
      // 会把命令切碎（summon 的 nameTag 就是这种情况）。
      if (part.json) {
        return `org.json.JSONObject.quote(v.v("${part.key}"))`;
      }
      // 可选段里的占位符：段已经被"非空才追加"的条件保护住了，这里直接取值。
      if (part.optional) return `v.v("${part.key}")`;
      return part.def !== undefined
        ? `v.vOr("${part.key}", ${kstr(part.def)})`
        : `v.v("${part.key}")`;
    });
    const inner = pieces.filter((p) => p !== kstr(''));
    if (inner.length === 0) continue;

    if (!seg.optional) {
      if (pendingSpace) {
        out.push(`${pad}sb.append(" ").append(${inner.join(' + ')})`);
        pendingSpace = false;
      } else {
        out.push(`${pad}sb.append(${inner.join(' + ')})`);
      }
    } else {
      const keys = seg.parts.filter((p) => p.kind === 'key').map((p) => p.key);
      const cond = keys.map((k) => `v.v("${k}").isNotEmpty()`).join(' || ');
      out.push(`${pad}if (${cond}) sb.append(" ").append(${inner.join(' + ')})`);
      pendingSpace = false;
    }
  }
  out.push(`${pad}return@CommandSpec sb.toString()`);
  return out.join('\n');
}

/** variants → 一串 "命中就 return" 的 if 块 + 最后的兜底。 */
function emitVariants(cmd) {
  const pad = ' '.repeat(12);
  const out = [];
  const conditional = cmd.variants.filter((v) => v.require);
  const fallback = cmd.variants.find((v) => !v.require);
  if (!fallback) throw new Error(`${cmd.keyword}: variants 必须有一条不带 require 的兜底`);

  for (const v of conditional) {
    let cond;
    if (Array.isArray(v.require)) {
      cond = v.require.map((k) => `v.v("${k}").isNotEmpty()`).join(' && ');
    } else {
      cond = Object.entries(v.require)
        .map(([k, val]) => `v.vOr("${k}", "") == ${kstr(val)}`)
        .join(' && ');
    }
    out.push(`${pad}if (${cond}) {`);
    out.push(emitTemplate(v.text, cmd.keyword, 16, cmd.keyword));
    out.push(`${pad}}`);
  }
  // 兜底
  out.push(emitTemplate(fallback.text, cmd.keyword, 12, cmd.keyword));
  return out.join('\n');
}

// ─────────────────────────────────────────────────────────────────────────────
// 一条命令 → Kotlin
// ─────────────────────────────────────────────────────────────────────────────
function emitCommand(cmd) {
  const usesJson = cmd.usesJson || cmd.params.some((p) => p.t === 'json');
  const params = cmd.params.map((p) => emitParam(p, cmd.keyword)).join('\n');
  const paramBlock = params ? `listOf(\n${params}\n            )` : 'emptyList()';

  let build;
  if (cmd.variants) {
    build = `        ) { v ->\n${emitVariants(cmd)}\n        }`;
  } else if (cmd.template !== undefined) {
    build = `        ) { v ->\n${emitTemplate(cmd.template, cmd.keyword, 12, cmd.keyword)}\n        }`;
  } else {
    throw new Error(`${cmd.keyword}: 缺少 template 或 variants`);
  }

  return `
        CommandSpec(
            keyword = ${kstr(cmd.keyword)},
            description = ${kstr(cmd.description)},
            category = ${kstr(cmd.category)},
            aliases = listOf(${(cmd.aliases ?? []).map(kstr).join(', ')}),${usesJson ? '\n            usesJson = true,' : ''}${
              cmd.alternatives?.length
                ? `\n            alternatives = listOf(${cmd.alternatives.map(kstr).join(', ')}),`
                : ''
            }${cmd.variantNote ? `\n            variantNote = ${kstr(cmd.variantNote)},` : ''}
            params = ${paramBlock}
${build},
`;
}

// ─────────────────────────────────────────────────────────────────────────────
// 唯一性检查
// ─────────────────────────────────────────────────────────────────────────────
{
  const seen = new Set();
  for (const c of commands) {
    if (seen.has(c.keyword)) throw new Error(`命令关键字重复: ${c.keyword}`);
    seen.add(c.keyword);
  }
  // 参数键不能重复
  for (const c of commands) {
    const keys = new Set();
    for (const p of c.params) {
      if (keys.has(p.k)) throw new Error(`${c.keyword}: 参数键重复 ${p.k}`);
      keys.add(p.k);
    }
  }
}

const body = commands.map(emitCommand).join('');

const HEADER = `package com.xanan.cmdhelper.json

/**
 * 参数类型 —— 同时驱动"界面生成什么控件"和"生成命令时怎么输出"。
 */
enum class ParamType {
    /** 单行文本，生成时原样输出（如玩家名、物品 ID）。 */
    TEXT,

    /** 可能含空格或中文的文本，界面给更大一点的输入框。 */
    LONG_TEXT,

    /** 数字（整数）：坐标偏移、数量、等级。 */
    NUMBER,

    /** 布尔开关，生成 true / false。 */
    BOOLEAN,

    /** 多选一，界面用下拉框。 */
    CHOICE,

    /** JSON 文本块，界面给代码编辑器。 */
    JSON,

    /** 目标选择器，给常用值快捷按钮（@a/@p/@s/@e/@r）。 */
    SELECTOR
}

/**
 * 一个参数的声明。
 *
 * @param key              取值用的键，也是批量模板里占位符的名字
 * @param label            界面上显示的中文名
 * @param type             控件类型
 * @param required         是否必填（必填为空时不生成命令，并给出提示）
 * @param defaultValue     默认值
 * @param choices          当 type 为 CHOICE 时的候选值
 * @param hint             输入框的提示文字，通常填取值范围
 * @param suggestions      **自动补全**的候选值（点击即插入的是它们）
 * @param suggestionLabels 与 [suggestions] 一一对应的显示名（通常是中文名），
 *                         界面显示中文、插入英文 ID
 */
data class CommandParam(
    val key: String,
    val label: String,
    val type: ParamType = ParamType.TEXT,
    val required: Boolean = true,
    val defaultValue: String = "",
    val choices: List<String> = emptyList(),
    val hint: String = "",
    val suggestions: List<String> = emptyList(),
    val suggestionLabels: List<String> = emptyList()
)

/**
 * 一条命令的"元数据 + 生成规则"。
 *
 * [build] 是纯函数：给一组参数值，产出可以直接粘进游戏的一整条命令。
 * 返回 null 表示参数不合法（例如必填项没填），由界面统一提示。
 */
data class CommandSpec(
    /** 命令关键字，如 \`give\`、\`titleraw\`。 */
    val keyword: String,

    /** 中文说明，用于列表展示与搜索。 */
    val description: String,

    /** 分组名，界面上按组排列。 */
    val category: String,

    /**
     * 命令的**中文名 / 别名**（可多个）。
     *
     * 玩家会在输入框里打 \`/粒子\` 想找 \`particle\`，所以命令名也要能按中文匹配。
     * 注意：**插入的永远是 [keyword]**，中文名只用于搜索与展示 ——
     * 把中文名当命令发出去游戏是不认的。
     */
    val aliases: List<String> = emptyList(),

    /** 参数声明，顺序即界面顺序。 */
    val params: List<CommandParam>,

    /** 这条命令是否涉及 JSON 参数（界面上用来打标记）。 */
    val usesJson: Boolean = false,

    /**
     * 同一族命令里**其他**能做同一件事、但写法不同的关键字。
     *
     * 存在的理由：有些命令成对出现，名字只差几个字母、用途却不同 ——
     * 典型是 title（纯文本）/ titleraw（JSON，支持颜色）、
     * tell（纯文本私聊）/ tellraw（JSON 私聊）。
     * 玩家手打 /title 时如果直接进入 title 的参数阶段，等于**替他做了决定**：
     * 他多半想要的是能上色的 titleraw，却发现填完参数生成的命令没有颜色。
     *
     * 所以在补全里，只要输入**正好是这条命令的关键字**且本字段非空，
     * 就先让用户选一遍（见 CommandCompletion 的命令族分支）。
     *
     * 只需写在"基准"那条上（title / tell），不需要两边互指。
     */
    val alternatives: List<String> = emptyList(),

    /**
     * 命令族选项里的**一句话差异说明**，例如"纯文本""JSON（可上色）"。
     * 只在这一族被展示给用户选择时用到；为空时只显示关键字本身。
     */
    val variantNote: String = "",

    /** 生成命令。返回 null 表示参数不合法。 */
    val build: (Map<String, String>) -> String?
) {

    /** 需要用户必须填写的参数键。 */
    val requiredKeys: List<CommandParam> get() = params.filter { it.required }

    /** 收集缺失的必填参数，供界面一次性提示。 */
    fun missingRequired(values: Map<String, String>): List<CommandParam> =
        requiredKeys.filter { values[it.key].isNullOrBlank() }

    /** 用默认值初始化一份参数表。 */
    fun defaultValues(): Map<String, String> =
        params.associate { it.key to it.defaultValue }

    /**
     * 生成这条命令的**语法模板**，形如：
     * \`\`\`
     * scoreboard players test <对象:实体> <记分项> <最小值:整数> [*]
     * \`\`\`
     *
     * 从参数声明自动生成，所以新增命令时帮助面板自动就有，不会出现
     * "加了命令忘了写帮助"。必填写成 \`<标签:类型>\`，可选写成 \`<标签:类型?>\`。
     */
    fun syntaxTemplate(): String {
        val sb = StringBuilder(keyword)
        params.forEach { param ->
            if (!param.required && param.defaultValue.isNotEmpty()) return@forEach
            val typeName = when (param.type) {
                ParamType.TEXT, ParamType.LONG_TEXT -> "文本"
                ParamType.NUMBER -> "整数"
                ParamType.BOOLEAN -> "true/false"
                ParamType.CHOICE -> param.choices.joinToString("/")
                ParamType.JSON -> "JSON"
                ParamType.SELECTOR -> "实体"
            }
            sb.append(' ').append('<').append(param.label).append(':').append(typeName)
                .append(if (param.required) "" else "?").append('>')
        }
        return sb.toString()
    }

    /** 参数说明列表（给帮助面板用）：标签 -> 说明。 */
    fun paramDoc(): List<Pair<String, String>> = params.map { param ->
        val typeName = when (param.type) {
            ParamType.TEXT, ParamType.LONG_TEXT -> "文本"
            ParamType.NUMBER -> "整数"
            ParamType.BOOLEAN -> "true / false"
            ParamType.CHOICE -> param.choices.joinToString(" / ")
            ParamType.JSON -> "JSON 组件"
            ParamType.SELECTOR -> "目标选择器"
        }
        val required = if (param.required) "必填" else "可选"
        val hint = param.hint.takeIf { it.isNotBlank() }?.let { "\\u3000$it" } ?: ""
        param.label to "$required · $typeName$hint"
    }
}

/**
 * 取值工具：去空白，空串按"未填"处理。
 *
 * 独立成顶层函数而不是 [CommandSpec] 的成员扩展，是因为 [CommandSpec] 的 build
 * lambda 是外部传入的，成员扩展在 lambda 里解析不到（Kotlin 的作用域规则）。
 */
internal fun Map<String, String>.v(key: String): String = this[key]?.trim().orEmpty()

/** 取值，空时用 [fallback]。 */
internal fun Map<String, String>.vOr(key: String, fallback: String): String =
    v(key).ifEmpty { fallback }

/**
 * 全部内置命令规格。
 *
 * ⚠️ **本文件由工具生成，不要手改**（改完下次生成会被覆盖）。
 *    数据与生成器：\`tools/commands/\`（\`commands.data.mjs\` + \`gen-commands.mjs\`）。
 *
 * 语法按基岩版**新版**整理：\`titleraw\` 的四个位置参数、\`give\` 的
 * \`[数量][数据值][组件JSON]\` 顺序、\`execute\` 的子命令式新语法（\`as\`/\`at\`/\`if\`/\`run\`）。
 */
object CommandLibrary {

    /** 全部分组名，按展示顺序排列。 */
    val categories: List<String> = listOf(
        "文本显示",
        "物品",
        "计分板",
        "世界与方块",
        "实体与效果",
        "特效与音效",
        "玩家与游戏规则"
    )

    /** 所有命令。 */
    val all: List<CommandSpec> = listOf(
${body}    )

    /** 按关键字找一条命令规格。 */
    fun find(keyword: String): CommandSpec? = all.firstOrNull { it.keyword == keyword }

    /**
     * 把用户输入的文本匹配到一条命令规格（**宽松版，供展示用**）。
     *
     * 难点在于命令关键字**有长有短**：
     *
     *  * \`scoreboard objectives add\` 与 \`scoreboard objectives setdisplay\` 共享前两个词，
     *    只按"前缀相同"匹配会两个都命中，结果取决于谁在列表里靠前 —— 典型"有时对有时错"；
     *  * \`scoreboard players\` 这种"多词命令打了一半"的输入也必须匹配上，
     *    否则玩家刚打完第一个词就什么提示都没有（实测踩过）；
     *  * \`give\` 这种单词关键字后面直接跟参数。
     *
     * 规则是"**按词边界双向匹配，取最长**"。
     * ⚠️ 本函数只回答"像哪条命令"，**不区分"命令名打完没有"**；
     * 需要那个判断请用 [CommandCompletion]（它按 token 逐个比对，更严格）。
     */
    fun match(input: String): CommandSpec? = match(input, input)

    /**
     * 同上，但把"用来匹配命令名的文本"与"原始输入"分开。
     *
     * 补全时要传**只有命令名那一段**的文本：玩家打到 \`@e[type=zombie\` 时，
     * 原始输入里那个 token 是 \`@e[type=zombie\` 而不是 \`@e\`，
     * 直接拿去匹配英文关键字会一条都命中不了。
     */
    fun match(input: String, nameText: String): CommandSpec? {
        val text = nameText.trim().removePrefix("/").trim()
        if (text.isEmpty()) return null

        // 第一优先：输入**已经包含**一条完整命令名。
        // 要求"关键字后面是空格"，不能只是字符串前缀 —— 否则
        // \`scoreboard objectives\` 会被 \`scoreboard objectives add\` 抢走，
        // 把"还没打完的命令名"误判成"已经打完的命令 + 参数"。
        all.filter { spec ->
            text == spec.keyword || text.startsWith(spec.keyword + " ")
        }.maxByOrNull { it.keyword.length }?.let { return it }

        // 第一·五优先：**中文命令名**。
        // 玩家会打 \`/粒子\` 而不是 \`/particle\`，这条必须有（用户实测报过打中文没反应）。
        // 只按"别名本身"或"别名 + 空格"匹配，不做前缀 ——
        // 中文没有词边界，前缀匹配会让 \`/队伍\` 与 \`/队伍 创建\` 互相抢。
        // 同分别名（\`tp\` 与 \`teleport\` 都是"传送"）时取**更短的关键字**：
        // 玩家基本都用短名。按长度升序配 maxWith，取到的就是最短的那个。
        all.filter { spec -> spec.aliases.any { text == it || text.startsWith(it + " ") } }
            .maxWithOrNull(compareBy({ spec -> spec.aliases.maxOf { it.length } }, { spec -> spec.keyword.length }))
            ?.let { return it }

        // 第二优先：单个词的前缀，用于"还没打完就出提示"（\`giv\` → \`give\`）。
        if (!text.contains(' ')) {
            return all
                .filter { !it.keyword.contains(' ') && it.keyword.startsWith(text) }
                .maxByOrNull { it.keyword.length }
        }
        return null
    }

    /**
     * 按中文别名找命令（宽松版，用于"命令名还没打完"时的候选）。
     *
     * 中文没有词边界，所以这里用**包含**匹配：\`/粒子\` 能命中 \`particle\`，
     * \`/计分板\` 能命中所有计分板子命令。返回按别名长度升序 ——
     * 越短的别名越可能是玩家的意图（\`/队伍\` 应该先给"队伍 创建"这类短名）。
     */
    fun matchByAlias(query: String): List<CommandSpec> {
        val q = query.trim().removePrefix("/").trim()
        if (q.isEmpty()) return emptyList()
        return all.filter { spec -> spec.aliases.any { it.contains(q) } }
            .sortedBy { spec -> spec.aliases.minOf { it.length } }
    }

    /** 按分组取命令。 */
    fun byCategory(category: String): List<CommandSpec> = all.filter { it.category == category }

    /** 模糊搜索：关键字、中文别名或说明里包含关键词就命中。 */
    fun search(query: String): List<CommandSpec> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return all
        return all.filter {
            it.keyword.lowercase().contains(q) ||
                it.description.lowercase().contains(q) ||
                it.aliases.any { alias -> alias.lowercase().contains(q) }
        }
    }

    /** 所有需要 JSON 参数的命令，界面上用来做筛选标签。 */
    fun jsonCommands(): List<CommandSpec> = all.filter { it.usesJson }
}
`;

writeFileSync(OUT, HEADER, 'utf8');
console.log(`written: ${OUT}`);
console.log(`commands = ${commands.length}`);
