package com.xanan.cmdhelper.json

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
    /** 命令关键字，如 `give`、`titleraw`。 */
    val keyword: String,

    /** 中文说明，用于列表展示与搜索。 */
    val description: String,

    /** 分组名，界面上按组排列。 */
    val category: String,

    /**
     * 命令的**中文名 / 别名**（可多个）。
     *
     * 玩家会在输入框里打 `/粒子` 想找 `particle`，所以命令名也要能按中文匹配。
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
     * ```
     * scoreboard players test <对象:实体> <记分项> <最小值:整数> [*]
     * ```
     *
     * 从参数声明自动生成，所以新增命令时帮助面板自动就有，不会出现
     * "加了命令忘了写帮助"。必填写成 `<标签:类型>`，可选写成 `<标签:类型?>`。
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
        val hint = param.hint.takeIf { it.isNotBlank() }?.let { "\u3000$it" } ?: ""
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
 *    数据与生成器：`tools/commands/`（`commands.data.mjs` + `gen-commands.mjs`）。
 *
 * 语法按基岩版**新版**整理：`titleraw` 的四个位置参数、`give` 的
 * `[数量][数据值][组件JSON]` 顺序、`execute` 的子命令式新语法（`as`/`at`/`if`/`run`）。
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

        CommandSpec(
            keyword = "titleraw",
            description = "在屏幕上显示 JSON 文本（标题 / 副标题 / 动作栏）",
            category = "文本显示",
            aliases = listOf("标题", "大字", "屏幕文字"),
            usesJson = true,
            variantNote = "JSON（可上色）",
            params = listOf(
                CommandParam(
                    key = "target",
                    label = "目标",
                    type = ParamType.SELECTOR,
                    defaultValue = "@a"
                ),
                CommandParam(
                    key = "position",
                    label = "显示位置",
                    type = ParamType.CHOICE,
                    defaultValue = "title",
                    choices = listOf("title", "subtitle", "actionbar")
                ),
                CommandParam(
                    key = "action",
                    label = "操作",
                    type = ParamType.CHOICE,
                    defaultValue = "show",
                    choices = listOf("show", "times", "clear", "reset"),
                    hint = "show = 直接显示后面的 JSON；times/clear/reset 见各自写法"
                ),
                CommandParam(
                    key = "json",
                    label = "JSON 组件",
                    type = ParamType.JSON,
                    required = false,
                    hint = "显示模式下必填，如 {\"rawtext\":[{\"text\":\"你好\"}]}"
                ),
                CommandParam(
                    key = "fadeIn",
                    label = "淡入（tick）",
                    type = ParamType.NUMBER,
                    required = false,
                    defaultValue = "10"
                ),
                CommandParam(
                    key = "stay",
                    label = "停留（tick）",
                    type = ParamType.NUMBER,
                    required = false,
                    defaultValue = "70"
                ),
                CommandParam(
                    key = "fadeOut",
                    label = "淡出（tick）",
                    type = ParamType.NUMBER,
                    required = false,
                    defaultValue = "20"
                ),
            )
        ) { v ->
            if (v.vOr("action", "") == "clear") {
                if (!(v.v("target").isNotEmpty())) return@CommandSpec null
                val sb = StringBuilder("titleraw")
                sb.append(" ").append(v.v("target") + " clear")
                return@CommandSpec sb.toString()
            }
            if (v.vOr("action", "") == "reset") {
                if (!(v.v("target").isNotEmpty())) return@CommandSpec null
                val sb = StringBuilder("titleraw")
                sb.append(" ").append(v.v("target") + " reset")
                return@CommandSpec sb.toString()
            }
            if (v.vOr("action", "") == "times") {
                if (!(v.v("target").isNotEmpty())) return@CommandSpec null
                val sb = StringBuilder("titleraw")
                sb.append(" ").append(v.v("target") + " times " + v.vOr("fadeIn", "10") + " " + v.vOr("stay", "70") + " " + v.vOr("fadeOut", "20"))
                return@CommandSpec sb.toString()
            }
            if (v.v("json").isNotEmpty()) {
                if (!(v.v("target").isNotEmpty() && v.v("position").isNotEmpty() && v.v("json").isNotEmpty())) return@CommandSpec null
                val sb = StringBuilder("titleraw")
                sb.append(" ").append(v.v("target") + " " + v.v("position") + " " + v.v("json"))
                return@CommandSpec sb.toString()
            }
            return@CommandSpec null
        },

        CommandSpec(
            keyword = "tellraw",
            description = "给玩家发送一条 JSON 消息（聊天栏）",
            category = "文本显示",
            aliases = listOf("聊天消息", "发消息"),
            usesJson = true,
            variantNote = "JSON（可上色）",
            params = listOf(
                CommandParam(
                    key = "target",
                    label = "目标",
                    type = ParamType.SELECTOR,
                    defaultValue = "@a"
                ),
                CommandParam(
                    key = "json",
                    label = "JSON 组件",
                    type = ParamType.JSON
                ),
            )
        ) { v ->
            if (!(v.v("target").isNotEmpty() && v.v("json").isNotEmpty())) return@CommandSpec null
            val sb = StringBuilder("tellraw")
            sb.append(" ").append(v.v("target") + " " + v.v("json"))
            return@CommandSpec sb.toString()
        },

        CommandSpec(
            keyword = "title",
            description = "显示普通文本标题（不支持样式，要颜色请用 titleraw）",
            category = "文本显示",
            aliases = listOf("标题文字"),
            alternatives = listOf("titleraw"),
            variantNote = "纯文本",
            params = listOf(
                CommandParam(
                    key = "target",
                    label = "目标",
                    type = ParamType.SELECTOR,
                    defaultValue = "@a"
                ),
                CommandParam(
                    key = "position",
                    label = "显示位置",
                    type = ParamType.CHOICE,
                    defaultValue = "title",
                    choices = listOf("title", "subtitle", "actionbar")
                ),
                CommandParam(
                    key = "text",
                    label = "文本",
                    type = ParamType.LONG_TEXT
                ),
            )
        ) { v ->
            if (!(v.v("target").isNotEmpty() && v.v("position").isNotEmpty() && v.v("text").isNotEmpty())) return@CommandSpec null
            val sb = StringBuilder("title")
            sb.append(" ").append(v.v("target") + " " + v.v("position") + " " + v.v("text"))
            return@CommandSpec sb.toString()
        },

        CommandSpec(
            keyword = "say",
            description = "以系统身份向所有人发一条消息",
            category = "文本显示",
            aliases = listOf("广播", "喊话"),
            params = listOf(
                CommandParam(
                    key = "message",
                    label = "消息内容",
                    type = ParamType.LONG_TEXT
                ),
            )
        ) { v ->
            if (!(v.v("message").isNotEmpty())) return@CommandSpec null
            val sb = StringBuilder("say")
            sb.append(" ").append(v.v("message"))
            return@CommandSpec sb.toString()
        },

        CommandSpec(
            keyword = "tell",
            description = "给指定玩家发私聊消息",
            category = "文本显示",
            aliases = listOf("私聊", "悄悄话"),
            alternatives = listOf("tellraw"),
            variantNote = "纯文本",
            params = listOf(
                CommandParam(
                    key = "target",
                    label = "目标",
                    type = ParamType.SELECTOR,
                    defaultValue = "@a"
                ),
                CommandParam(
                    key = "message",
                    label = "消息内容",
                    type = ParamType.LONG_TEXT
                ),
            )
        ) { v ->
            if (!(v.v("target").isNotEmpty() && v.v("message").isNotEmpty())) return@CommandSpec null
            val sb = StringBuilder("tell")
            sb.append(" ").append(v.v("target") + " " + v.v("message"))
            return@CommandSpec sb.toString()
        },

        CommandSpec(
            keyword = "me",
            description = "以动作形式发言（* 玩家名 …）",
            category = "文本显示",
            aliases = listOf("动作发言"),
            params = listOf(
                CommandParam(
                    key = "action",
                    label = "动作描述",
                    type = ParamType.LONG_TEXT
                ),
            )
        ) { v ->
            if (!(v.v("action").isNotEmpty())) return@CommandSpec null
            val sb = StringBuilder("me")
            sb.append(" ").append(v.v("action"))
            return@CommandSpec sb.toString()
        },

        CommandSpec(
            keyword = "give",
            description = "给予物品，可附带自定义名称、附魔、属性等组件",
            category = "物品",
            aliases = listOf("给予", "给物品"),
            usesJson = true,
            params = listOf(
                CommandParam(
                    key = "target",
                    label = "目标",
                    type = ParamType.SELECTOR,
                    defaultValue = "@s"
                ),
                CommandParam(
                    key = "item",
                    label = "物品 ID",
                    hint = "如 diamond_sword，可带命名空间 minecraft:",
                    suggestions = Suggest.giveableItems.map { it.id },
                    suggestionLabels = Suggest.giveableItems.map { it.name }
                ),
                CommandParam(
                    key = "amount",
                    label = "数量",
                    type = ParamType.NUMBER,
                    required = false,
                    defaultValue = "1"
                ),
                CommandParam(
                    key = "data",
                    label = "数据值",
                    type = ParamType.NUMBER,
                    required = false,
                    defaultValue = "0"
                ),
                CommandParam(
                    key = "components",
                    label = "组件 JSON",
                    type = ParamType.JSON,
                    required = false,
                    hint = "如 {\"item_name\":\"宝剑\"}",
                    suggestions = listOf("{\"item_name\":\"名刀·村正\"}", "{\"item_name\":\"宝剑\",\"enchantments\":[{\"id\":\"sharpness\",\"lvl\":5}]}", "{\"minecraft:can_destroy\":{\"blocks\":[\"dirt\"]}}")
                ),
            )
        ) { v ->
            if (v.v("components").isNotEmpty()) {
                if (!(v.v("target").isNotEmpty() && v.v("item").isNotEmpty() && v.v("components").isNotEmpty())) return@CommandSpec null
                val sb = StringBuilder("give")
                sb.append(" ").append(v.v("target") + " " + v.v("item") + " " + v.vOr("amount", "1") + " " + v.vOr("data", "0") + " " + v.v("components"))
                return@CommandSpec sb.toString()
            }
            if (v.v("item").isNotEmpty()) {
                if (!(v.v("target").isNotEmpty() && v.v("item").isNotEmpty())) return@CommandSpec null
                val sb = StringBuilder("give")
                sb.append(" ").append(v.v("target") + " " + v.v("item") + " " + v.vOr("amount", "1"))
                return@CommandSpec sb.toString()
            }
            return@CommandSpec null
        },

        CommandSpec(
            keyword = "replaceitem",
            description = "替换容器或实体身上的物品",
            category = "物品",
            aliases = listOf("替换物品"),
            usesJson = true,
            params = listOf(
                CommandParam(
                    key = "kind",
                    label = "类型",
                    type = ParamType.CHOICE,
                    defaultValue = "entity",
                    choices = listOf("entity", "block"),
                    hint = "entity = 换实体身上的；block = 换容器方块里的"
                ),
                CommandParam(
                    key = "target",
                    label = "目标实体",
                    type = ParamType.SELECTOR,
                    required = false,
                    defaultValue = "@s"
                ),
                CommandParam(
                    key = "x",
                    label = "X",
                    required = false,
                    defaultValue = "~"
                ),
                CommandParam(
                    key = "y",
                    label = "Y",
                    required = false,
                    defaultValue = "~"
                ),
                CommandParam(
                    key = "z",
                    label = "Z",
                    required = false,
                    defaultValue = "~"
                ),
                CommandParam(
                    key = "slotType",
                    label = "槽位类型",
                    required = false,
                    defaultValue = "slot.weapon.mainhand",
                    hint = "如 slot.container、slot.armor.head、slot.hotbar"
                ),
                CommandParam(
                    key = "slotId",
                    label = "槽位序号",
                    type = ParamType.NUMBER,
                    required = false,
                    defaultValue = "0"
                ),
                CommandParam(
                    key = "item",
                    label = "物品 ID",
                    suggestions = Suggest.giveableItems.map { it.id },
                    suggestionLabels = Suggest.giveableItems.map { it.name }
                ),
                CommandParam(
                    key = "amount",
                    label = "数量",
                    type = ParamType.NUMBER,
                    required = false,
                    defaultValue = "1"
                ),
                CommandParam(
                    key = "data",
                    label = "数据值",
                    type = ParamType.NUMBER,
                    required = false,
                    defaultValue = "0"
                ),
                CommandParam(
                    key = "components",
                    label = "组件 JSON",
                    type = ParamType.JSON,
                    required = false
                ),
            )
        ) { v ->
            if (v.vOr("kind", "") == "block") {
                if (!(v.v("x").isNotEmpty() && v.v("y").isNotEmpty() && v.v("z").isNotEmpty() && v.v("item").isNotEmpty())) return@CommandSpec null
                val sb = StringBuilder("replaceitem")
                sb.append(" ").append("block " + v.v("x") + " " + v.v("y") + " " + v.v("z") + " slot.container " + v.vOr("slotId", "0") + " " + v.v("item") + " ")
                if (v.v("amount").isNotEmpty()) sb.append(" ").append(v.v("amount"))
                sb.append(" ")
                if (v.v("data").isNotEmpty()) sb.append(" ").append(v.v("data"))
                sb.append(" ")
                if (v.v("components").isNotEmpty()) sb.append(" ").append(v.v("components"))
                return@CommandSpec sb.toString()
            }
            if (v.v("target").isNotEmpty() && v.v("slotType").isNotEmpty() && v.v("item").isNotEmpty()) {
                if (!(v.v("target").isNotEmpty() && v.v("slotType").isNotEmpty() && v.v("item").isNotEmpty())) return@CommandSpec null
                val sb = StringBuilder("replaceitem")
                sb.append(" ").append("entity " + v.v("target") + " " + v.v("slotType") + " " + v.vOr("slotId", "0") + " " + v.v("item") + " ")
                if (v.v("amount").isNotEmpty()) sb.append(" ").append(v.v("amount"))
                sb.append(" ")
                if (v.v("data").isNotEmpty()) sb.append(" ").append(v.v("data"))
                sb.append(" ")
                if (v.v("components").isNotEmpty()) sb.append(" ").append(v.v("components"))
                return@CommandSpec sb.toString()
            }
            return@CommandSpec null
        },

        CommandSpec(
            keyword = "clear",
            description = "从玩家物品栏里清除物品",
            category = "物品",
            aliases = listOf("清空物品", "清除物品"),
            params = listOf(
                CommandParam(
                    key = "target",
                    label = "目标",
                    type = ParamType.SELECTOR,
                    defaultValue = "@s"
                ),
                CommandParam(
                    key = "item",
                    label = "物品 ID",
                    required = false,
                    hint = "留空 = 清空全部物品",
                    suggestions = Suggest.giveableItems.map { it.id },
                    suggestionLabels = Suggest.giveableItems.map { it.name }
                ),
                CommandParam(
                    key = "data",
                    label = "数据值",
                    type = ParamType.NUMBER,
                    required = false,
                    defaultValue = "0"
                ),
                CommandParam(
                    key = "maxCount",
                    label = "最多清除数量",
                    type = ParamType.NUMBER,
                    required = false,
                    defaultValue = "-1",
                    hint = "填 -1 清除全部；填 0 只统计数量不清除"
                ),
            )
        ) { v ->
            if (v.v("item").isNotEmpty()) {
                if (!(v.v("target").isNotEmpty() && v.v("item").isNotEmpty())) return@CommandSpec null
                val sb = StringBuilder("clear")
                sb.append(" ").append(v.v("target") + " " + v.v("item") + " " + v.vOr("data", "0") + " " + v.vOr("maxCount", "-1"))
                return@CommandSpec sb.toString()
            }
            if (!(v.v("target").isNotEmpty())) return@CommandSpec null
            val sb = StringBuilder("clear")
            sb.append(" ").append(v.v("target"))
            return@CommandSpec sb.toString()
        },

        CommandSpec(
            keyword = "scoreboard objectives add",
            description = "新建一个计分板目标",
            category = "计分板",
            aliases = listOf("计分板目标 新建"),
            params = listOf(
                CommandParam(
                    key = "objective",
                    label = "目标名"
                ),
                CommandParam(
                    key = "criteria",
                    label = "准则",
                    type = ParamType.CHOICE,
                    defaultValue = "dummy",
                    choices = listOf("dummy")
                ),
                CommandParam(
                    key = "display",
                    label = "显示名",
                    required = false,
                    hint = "支持 JSON 组件"
                ),
            )
        ) { v ->
            if (!(v.v("objective").isNotEmpty() && v.v("criteria").isNotEmpty())) return@CommandSpec null
            val sb = StringBuilder("scoreboard objectives add")
            sb.append(" ").append(v.v("objective") + " " + v.v("criteria") + " ")
            if (v.v("display").isNotEmpty()) sb.append(" ").append(v.v("display"))
            return@CommandSpec sb.toString()
        },

        CommandSpec(
            keyword = "scoreboard objectives remove",
            description = "删除一个计分板目标",
            category = "计分板",
            aliases = listOf("计分板目标 删除"),
            params = listOf(
                CommandParam(
                    key = "objective",
                    label = "目标名"
                ),
            )
        ) { v ->
            if (!(v.v("objective").isNotEmpty())) return@CommandSpec null
            val sb = StringBuilder("scoreboard objectives remove")
            sb.append(" ").append(v.v("objective"))
            return@CommandSpec sb.toString()
        },

        CommandSpec(
            keyword = "scoreboard objectives list",
            description = "列出所有计分板目标",
            category = "计分板",
            aliases = listOf("计分板目标 列表"),
            params = emptyList()
        ) { v ->
            val sb = StringBuilder("scoreboard objectives list")
            return@CommandSpec sb.toString()
        },

        CommandSpec(
            keyword = "scoreboard objectives setdisplay",
            description = "把计分板显示在侧边栏或玩家列表",
            category = "计分板",
            aliases = listOf("计分板目标 显示"),
            params = listOf(
                CommandParam(
                    key = "slot",
                    label = "显示位置",
                    type = ParamType.CHOICE,
                    defaultValue = "sidebar",
                    choices = listOf("sidebar", "list", "belowname")
                ),
                CommandParam(
                    key = "objective",
                    label = "目标名"
                ),
                CommandParam(
                    key = "order",
                    label = "排序",
                    type = ParamType.CHOICE,
                    required = false,
                    defaultValue = "ascending",
                    choices = listOf("ascending", "descending")
                ),
            )
        ) { v ->
            if (v.vOr("slot", "") == "belowname") {
                if (!(v.v("objective").isNotEmpty())) return@CommandSpec null
                val sb = StringBuilder("scoreboard objectives setdisplay")
                sb.append(" ").append("belowname " + v.v("objective"))
                return@CommandSpec sb.toString()
            }
            if (v.v("objective").isNotEmpty()) {
                if (!(v.v("slot").isNotEmpty() && v.v("objective").isNotEmpty())) return@CommandSpec null
                val sb = StringBuilder("scoreboard objectives setdisplay")
                sb.append(" ").append(v.v("slot") + " " + v.v("objective") + " " + v.vOr("order", "ascending"))
                return@CommandSpec sb.toString()
            }
            if (!(v.v("slot").isNotEmpty())) return@CommandSpec null
            val sb = StringBuilder("scoreboard objectives setdisplay")
            sb.append(" ").append(v.v("slot"))
            return@CommandSpec sb.toString()
        },

        CommandSpec(
            keyword = "scoreboard players set",
            description = "设置某个玩家（或假玩家）的分数",
            category = "计分板",
            aliases = listOf("计分板 设置分数"),
            params = listOf(
                CommandParam(
                    key = "player",
                    label = "玩家 / 假玩家名",
                    defaultValue = "@s"
                ),
                CommandParam(
                    key = "objective",
                    label = "目标名"
                ),
                CommandParam(
                    key = "score",
                    label = "分数",
                    type = ParamType.NUMBER,
                    defaultValue = "0"
                ),
            )
        ) { v ->
            if (!(v.v("player").isNotEmpty() && v.v("objective").isNotEmpty() && v.v("score").isNotEmpty())) return@CommandSpec null
            val sb = StringBuilder("scoreboard players set")
            sb.append(" ").append(v.v("player") + " " + v.v("objective") + " " + v.v("score"))
            return@CommandSpec sb.toString()
        },

        CommandSpec(
            keyword = "scoreboard players add",
            description = "给分数加上一个值（可为负数）",
            category = "计分板",
            aliases = listOf("计分板 加分"),
            params = listOf(
                CommandParam(
                    key = "player",
                    label = "玩家 / 假玩家名",
                    defaultValue = "@s"
                ),
                CommandParam(
                    key = "objective",
                    label = "目标名"
                ),
                CommandParam(
                    key = "score",
                    label = "增加量",
                    type = ParamType.NUMBER,
                    defaultValue = "1"
                ),
            )
        ) { v ->
            if (!(v.v("player").isNotEmpty() && v.v("objective").isNotEmpty() && v.v("score").isNotEmpty())) return@CommandSpec null
            val sb = StringBuilder("scoreboard players add")
            sb.append(" ").append(v.v("player") + " " + v.v("objective") + " " + v.v("score"))
            return@CommandSpec sb.toString()
        },

        CommandSpec(
            keyword = "scoreboard players remove",
            description = "从分数里减去一个值",
            category = "计分板",
            aliases = listOf("计分板 减分"),
            params = listOf(
                CommandParam(
                    key = "player",
                    label = "玩家 / 假玩家名",
                    defaultValue = "@s"
                ),
                CommandParam(
                    key = "objective",
                    label = "目标名"
                ),
                CommandParam(
                    key = "score",
                    label = "减少量",
                    type = ParamType.NUMBER,
                    defaultValue = "1"
                ),
            )
        ) { v ->
            if (!(v.v("player").isNotEmpty() && v.v("objective").isNotEmpty() && v.v("score").isNotEmpty())) return@CommandSpec null
            val sb = StringBuilder("scoreboard players remove")
            sb.append(" ").append(v.v("player") + " " + v.v("objective") + " " + v.v("score"))
            return@CommandSpec sb.toString()
        },

        CommandSpec(
            keyword = "scoreboard players reset",
            description = "重置分数（清空某个目标上的记录）",
            category = "计分板",
            aliases = listOf("计分板 重置分数"),
            params = listOf(
                CommandParam(
                    key = "player",
                    label = "玩家 / 假玩家名",
                    defaultValue = "@s"
                ),
                CommandParam(
                    key = "objective",
                    label = "目标名",
                    required = false,
                    hint = "留空 = 清空该玩家所有目标上的分"
                ),
            )
        ) { v ->
            if (!(v.v("player").isNotEmpty())) return@CommandSpec null
            val sb = StringBuilder("scoreboard players reset")
            sb.append(" ").append(v.v("player") + " ")
            if (v.v("objective").isNotEmpty()) sb.append(" ").append(v.v("objective"))
            return@CommandSpec sb.toString()
        },

        CommandSpec(
            keyword = "scoreboard players test",
            description = "判断分数是否落在指定区间，成立时输出结果（配合命令方块做条件）",
            category = "计分板",
            aliases = listOf("计分板 检测分数"),
            params = listOf(
                CommandParam(
                    key = "player",
                    label = "对象 / 实体",
                    defaultValue = "@p"
                ),
                CommandParam(
                    key = "objective",
                    label = "记分项"
                ),
                CommandParam(
                    key = "min",
                    label = "最小值",
                    type = ParamType.NUMBER,
                    defaultValue = "1"
                ),
                CommandParam(
                    key = "max",
                    label = "最大值",
                    required = false,
                    hint = "填 * 表示不设上限"
                ),
            )
        ) { v ->
            if (!(v.v("player").isNotEmpty() && v.v("objective").isNotEmpty() && v.v("min").isNotEmpty())) return@CommandSpec null
            val sb = StringBuilder("scoreboard players test")
            sb.append(" ").append(v.v("player") + " " + v.v("objective") + " " + v.v("min") + " " + v.vOr("max", "*"))
            return@CommandSpec sb.toString()
        },

        CommandSpec(
            keyword = "scoreboard players operation",
            description = "对两个分数做运算（赋值 / 加减乘除取余 / 取最大最小）",
            category = "计分板",
            aliases = listOf("计分板 运算"),
            params = listOf(
                CommandParam(
                    key = "target",
                    label = "被修改的对象",
                    defaultValue = "@s"
                ),
                CommandParam(
                    key = "targetObjective",
                    label = "被修改的记分项"
                ),
                CommandParam(
                    key = "operator",
                    label = "运算符",
                    type = ParamType.CHOICE,
                    defaultValue = "=",
                    choices = listOf("=", "+=", "-=", "*=", "/=", "%=", "><", "<", ">")
                ),
                CommandParam(
                    key = "source",
                    label = "来源对象",
                    defaultValue = "@s"
                ),
                CommandParam(
                    key = "sourceObjective",
                    label = "来源记分项"
                ),
            )
        ) { v ->
            if (!(v.v("target").isNotEmpty() && v.v("targetObjective").isNotEmpty() && v.v("operator").isNotEmpty() && v.v("source").isNotEmpty() && v.v("sourceObjective").isNotEmpty())) return@CommandSpec null
            val sb = StringBuilder("scoreboard players operation")
            sb.append(" ").append(v.v("target") + " " + v.v("targetObjective") + " " + v.v("operator") + " " + v.v("source") + " " + v.v("sourceObjective"))
            return@CommandSpec sb.toString()
        },

        CommandSpec(
            keyword = "scoreboard players random",
            description = "给分数随机赋一个区间内的值",
            category = "计分板",
            aliases = listOf("计分板 随机分数"),
            params = listOf(
                CommandParam(
                    key = "player",
                    label = "玩家 / 假玩家名",
                    defaultValue = "@s"
                ),
                CommandParam(
                    key = "objective",
                    label = "目标名"
                ),
                CommandParam(
                    key = "min",
                    label = "最小值",
                    type = ParamType.NUMBER,
                    defaultValue = "1"
                ),
                CommandParam(
                    key = "max",
                    label = "最大值",
                    type = ParamType.NUMBER,
                    defaultValue = "6"
                ),
            )
        ) { v ->
            if (!(v.v("player").isNotEmpty() && v.v("objective").isNotEmpty() && v.v("min").isNotEmpty() && v.v("max").isNotEmpty())) return@CommandSpec null
            val sb = StringBuilder("scoreboard players random")
            sb.append(" ").append(v.v("player") + " " + v.v("objective") + " " + v.v("min") + " " + v.v("max"))
            return@CommandSpec sb.toString()
        },

        CommandSpec(
            keyword = "scoreboard players list",
            description = "列出玩家记录的全部记分项",
            category = "计分板",
            aliases = listOf("计分板 列出分数"),
            params = listOf(
                CommandParam(
                    key = "player",
                    label = "玩家名",
                    required = false
                ),
            )
        ) { v ->
            val sb = StringBuilder("scoreboard players list")
            if (v.v("player").isNotEmpty()) sb.append(" ").append(v.v("player"))
            return@CommandSpec sb.toString()
        },

        CommandSpec(
            keyword = "setblock",
            description = "在指定坐标放置方块",
            category = "世界与方块",
            aliases = listOf("放置方块", "设置方块"),
            params = listOf(
                CommandParam(
                    key = "x",
                    label = "X",
                    defaultValue = "~"
                ),
                CommandParam(
                    key = "y",
                    label = "Y",
                    defaultValue = "~"
                ),
                CommandParam(
                    key = "z",
                    label = "Z",
                    defaultValue = "~"
                ),
                CommandParam(
                    key = "block",
                    label = "方块 ID",
                    suggestions = Suggest.blocks.map { it.id },
                    suggestionLabels = Suggest.blocks.map { it.name }
                ),
                CommandParam(
                    key = "states",
                    label = "方块状态",
                    required = false,
                    hint = "如 [\"facing_direction\"=1]",
                    suggestions = listOf("[\"facing_direction\"=1]", "[\"facing_direction\"=0]", "[\"powered_bit\"=true]", "[\"open_bit\"=true]")
                ),
                CommandParam(
                    key = "mode",
                    label = "处理方式",
                    type = ParamType.CHOICE,
                    required = false,
                    defaultValue = "replace",
                    choices = listOf("replace", "destroy", "keep")
                ),
            )
        ) { v ->
            if (v.v("block").isNotEmpty() && v.v("states").isNotEmpty()) {
                if (!(v.v("x").isNotEmpty() && v.v("y").isNotEmpty() && v.v("z").isNotEmpty() && v.v("block").isNotEmpty() && v.v("states").isNotEmpty())) return@CommandSpec null
                val sb = StringBuilder("setblock")
                sb.append(" ").append(v.v("x") + " " + v.v("y") + " " + v.v("z") + " " + v.v("block") + " " + v.v("states") + " ")
                if (v.v("mode").isNotEmpty()) sb.append(" ").append(v.v("mode"))
                return@CommandSpec sb.toString()
            }
            if (v.v("block").isNotEmpty()) {
                if (!(v.v("x").isNotEmpty() && v.v("y").isNotEmpty() && v.v("z").isNotEmpty() && v.v("block").isNotEmpty())) return@CommandSpec null
                val sb = StringBuilder("setblock")
                sb.append(" ").append(v.v("x") + " " + v.v("y") + " " + v.v("z") + " " + v.v("block") + " ")
                if (v.v("mode").isNotEmpty()) sb.append(" ").append(v.v("mode"))
                return@CommandSpec sb.toString()
            }
            if (!(v.v("x").isNotEmpty() && v.v("y").isNotEmpty() && v.v("z").isNotEmpty())) return@CommandSpec null
            val sb = StringBuilder("setblock")
            sb.append(" ").append(v.v("x") + " " + v.v("y") + " " + v.v("z"))
            return@CommandSpec sb.toString()
        },

        CommandSpec(
            keyword = "fill",
            description = "用方块填充一个区域",
            category = "世界与方块",
            aliases = listOf("填充方块", "区域填充"),
            params = listOf(
                CommandParam(
                    key = "x1",
                    label = "起点 X",
                    defaultValue = "~"
                ),
                CommandParam(
                    key = "y1",
                    label = "起点 Y",
                    defaultValue = "~"
                ),
                CommandParam(
                    key = "z1",
                    label = "起点 Z",
                    defaultValue = "~"
                ),
                CommandParam(
                    key = "x2",
                    label = "终点 X",
                    defaultValue = "~"
                ),
                CommandParam(
                    key = "y2",
                    label = "终点 Y",
                    defaultValue = "~"
                ),
                CommandParam(
                    key = "z2",
                    label = "终点 Z",
                    defaultValue = "~"
                ),
                CommandParam(
                    key = "block",
                    label = "方块 ID",
                    suggestions = Suggest.blocks.map { it.id },
                    suggestionLabels = Suggest.blocks.map { it.name }
                ),
                CommandParam(
                    key = "states",
                    label = "方块状态",
                    required = false,
                    hint = "如 [\"facing_direction\"=1]",
                    suggestions = listOf("[\"facing_direction\"=1]", "[\"facing_direction\"=0]", "[\"powered_bit\"=true]", "[\"open_bit\"=true]")
                ),
                CommandParam(
                    key = "mode",
                    label = "填充方式",
                    type = ParamType.CHOICE,
                    required = false,
                    defaultValue = "replace",
                    choices = listOf("replace", "destroy", "hollow", "keep", "outline", "strict")
                ),
            )
        ) { v ->
            if (v.v("block").isNotEmpty() && v.v("states").isNotEmpty()) {
                if (!(v.v("x1").isNotEmpty() && v.v("y1").isNotEmpty() && v.v("z1").isNotEmpty() && v.v("x2").isNotEmpty() && v.v("y2").isNotEmpty() && v.v("z2").isNotEmpty() && v.v("block").isNotEmpty() && v.v("states").isNotEmpty())) return@CommandSpec null
                val sb = StringBuilder("fill")
                sb.append(" ").append(v.v("x1") + " " + v.v("y1") + " " + v.v("z1") + " " + v.v("x2") + " " + v.v("y2") + " " + v.v("z2") + " " + v.v("block") + " " + v.v("states") + " ")
                if (v.v("mode").isNotEmpty()) sb.append(" ").append(v.v("mode"))
                return@CommandSpec sb.toString()
            }
            if (v.v("block").isNotEmpty()) {
                if (!(v.v("x1").isNotEmpty() && v.v("y1").isNotEmpty() && v.v("z1").isNotEmpty() && v.v("x2").isNotEmpty() && v.v("y2").isNotEmpty() && v.v("z2").isNotEmpty() && v.v("block").isNotEmpty())) return@CommandSpec null
                val sb = StringBuilder("fill")
                sb.append(" ").append(v.v("x1") + " " + v.v("y1") + " " + v.v("z1") + " " + v.v("x2") + " " + v.v("y2") + " " + v.v("z2") + " " + v.v("block") + " ")
                if (v.v("mode").isNotEmpty()) sb.append(" ").append(v.v("mode"))
                return@CommandSpec sb.toString()
            }
            if (!(v.v("x1").isNotEmpty() && v.v("y1").isNotEmpty() && v.v("z1").isNotEmpty() && v.v("x2").isNotEmpty() && v.v("y2").isNotEmpty() && v.v("z2").isNotEmpty())) return@CommandSpec null
            val sb = StringBuilder("fill")
            sb.append(" ").append(v.v("x1") + " " + v.v("y1") + " " + v.v("z1") + " " + v.v("x2") + " " + v.v("y2") + " " + v.v("z2"))
            return@CommandSpec sb.toString()
        },

        CommandSpec(
            keyword = "clone",
            description = "复制一块区域到另一处",
            category = "世界与方块",
            aliases = listOf("复制区域", "克隆"),
            params = listOf(
                CommandParam(
                    key = "x1",
                    label = "起点 X",
                    defaultValue = "~"
                ),
                CommandParam(
                    key = "y1",
                    label = "起点 Y",
                    defaultValue = "~"
                ),
                CommandParam(
                    key = "z1",
                    label = "起点 Z",
                    defaultValue = "~"
                ),
                CommandParam(
                    key = "x2",
                    label = "终点 X",
                    defaultValue = "~"
                ),
                CommandParam(
                    key = "y2",
                    label = "终点 Y",
                    defaultValue = "~"
                ),
                CommandParam(
                    key = "z2",
                    label = "终点 Z",
                    defaultValue = "~"
                ),
                CommandParam(
                    key = "dx",
                    label = "目标 X",
                    defaultValue = "~"
                ),
                CommandParam(
                    key = "dy",
                    label = "目标 Y",
                    defaultValue = "~"
                ),
                CommandParam(
                    key = "dz",
                    label = "目标 Z",
                    defaultValue = "~"
                ),
                CommandParam(
                    key = "maskMode",
                    label = "过滤模式",
                    type = ParamType.CHOICE,
                    required = false,
                    defaultValue = "replace",
                    choices = listOf("replace", "masked")
                ),
                CommandParam(
                    key = "cloneMode",
                    label = "复制方式",
                    type = ParamType.CHOICE,
                    required = false,
                    defaultValue = "normal",
                    choices = listOf("normal", "force", "move")
                ),
            )
        ) { v ->
            if (!(v.v("x1").isNotEmpty() && v.v("y1").isNotEmpty() && v.v("z1").isNotEmpty() && v.v("x2").isNotEmpty() && v.v("y2").isNotEmpty() && v.v("z2").isNotEmpty() && v.v("dx").isNotEmpty() && v.v("dy").isNotEmpty() && v.v("dz").isNotEmpty())) return@CommandSpec null
            val sb = StringBuilder("clone")
            sb.append(" ").append(v.v("x1") + " " + v.v("y1") + " " + v.v("z1") + " " + v.v("x2") + " " + v.v("y2") + " " + v.v("z2") + " " + v.v("dx") + " " + v.v("dy") + " " + v.v("dz") + " " + v.vOr("maskMode", "replace") + " " + v.vOr("cloneMode", "normal"))
            return@CommandSpec sb.toString()
        },

        CommandSpec(
            keyword = "time set",
            description = "设置世界时间",
            category = "世界与方块",
            aliases = listOf("设置时间"),
            params = listOf(
                CommandParam(
                    key = "value",
                    label = "时间",
                    type = ParamType.CHOICE,
                    defaultValue = "day",
                    choices = listOf("day", "noon", "sunset", "night", "midnight", "sunrise")
                ),
            )
        ) { v ->
            if (!(v.v("value").isNotEmpty())) return@CommandSpec null
            val sb = StringBuilder("time set")
            sb.append(" ").append(v.v("value"))
            return@CommandSpec sb.toString()
        },

        CommandSpec(
            keyword = "time add",
            description = "把世界时间往前推一段",
            category = "世界与方块",
            aliases = listOf("推进时间"),
            params = listOf(
                CommandParam(
                    key = "amount",
                    label = "推进量",
                    type = ParamType.NUMBER,
                    defaultValue = "1000"
                ),
            )
        ) { v ->
            if (!(v.v("amount").isNotEmpty())) return@CommandSpec null
            val sb = StringBuilder("time add")
            sb.append(" ").append(v.v("amount"))
            return@CommandSpec sb.toString()
        },

        CommandSpec(
            keyword = "time query",
            description = "查询当前时间是白天还是夜晚、或已过多少刻",
            category = "世界与方块",
            aliases = listOf("查询时间"),
            params = listOf(
                CommandParam(
                    key = "value",
                    label = "查询内容",
                    type = ParamType.CHOICE,
                    defaultValue = "daytime",
                    choices = listOf("daytime", "gametime", "day")
                ),
            )
        ) { v ->
            if (!(v.v("value").isNotEmpty())) return@CommandSpec null
            val sb = StringBuilder("time query")
            sb.append(" ").append(v.v("value"))
            return@CommandSpec sb.toString()
        },

        CommandSpec(
            keyword = "weather",
            description = "切换天气",
            category = "世界与方块",
            aliases = listOf("天气"),
            params = listOf(
                CommandParam(
                    key = "type",
                    label = "天气",
                    type = ParamType.CHOICE,
                    defaultValue = "clear",
                    choices = listOf("clear", "rain", "thunder")
                ),
                CommandParam(
                    key = "duration",
                    label = "持续（游戏刻）",
                    type = ParamType.NUMBER,
                    required = false,
                    hint = "20 刻 = 1 秒；不填由游戏决定"
                ),
            )
        ) { v ->
            if (v.v("duration").isNotEmpty()) {
                if (!(v.v("type").isNotEmpty() && v.v("duration").isNotEmpty())) return@CommandSpec null
                val sb = StringBuilder("weather")
                sb.append(" ").append(v.v("type") + " " + v.v("duration"))
                return@CommandSpec sb.toString()
            }
            if (!(v.v("type").isNotEmpty())) return@CommandSpec null
            val sb = StringBuilder("weather")
            sb.append(" ").append(v.v("type"))
            return@CommandSpec sb.toString()
        },

        CommandSpec(
            keyword = "weather query",
            description = "查询当前天气",
            category = "世界与方块",
            aliases = listOf("查询天气"),
            params = emptyList()
        ) { v ->
            val sb = StringBuilder("weather query")
            return@CommandSpec sb.toString()
        },

        CommandSpec(
            keyword = "difficulty",
            description = "设置游戏难度",
            category = "世界与方块",
            aliases = listOf("难度"),
            params = listOf(
                CommandParam(
                    key = "value",
                    label = "难度",
                    type = ParamType.CHOICE,
                    defaultValue = "normal",
                    choices = listOf("peaceful", "easy", "normal", "hard")
                ),
            )
        ) { v ->
            if (!(v.v("value").isNotEmpty())) return@CommandSpec null
            val sb = StringBuilder("difficulty")
            sb.append(" ").append(v.v("value"))
            return@CommandSpec sb.toString()
        },

        CommandSpec(
            keyword = "locate",
            description = "查找最近的生物群系或结构并给出坐标",
            category = "世界与方块",
            aliases = listOf("查找结构", "定位"),
            params = listOf(
                CommandParam(
                    key = "feature",
                    label = "类型",
                    type = ParamType.CHOICE,
                    defaultValue = "biome",
                    choices = listOf("biome", "structure")
                ),
                CommandParam(
                    key = "name",
                    label = "名称",
                    hint = "如 village、stronghold、cherry_grove"
                ),
            )
        ) { v ->
            if (v.v("name").isNotEmpty()) {
                if (!(v.v("feature").isNotEmpty() && v.v("name").isNotEmpty())) return@CommandSpec null
                val sb = StringBuilder("locate")
                sb.append(" ").append(v.v("feature") + " " + v.v("name"))
                return@CommandSpec sb.toString()
            }
            if (!(v.v("feature").isNotEmpty())) return@CommandSpec null
            val sb = StringBuilder("locate")
            sb.append(" ").append(v.v("feature"))
            return@CommandSpec sb.toString()
        },

        CommandSpec(
            keyword = "setworldspawn",
            description = "设置世界出生点",
            category = "世界与方块",
            aliases = listOf("设置出生点"),
            params = listOf(
                CommandParam(
                    key = "x",
                    label = "X",
                    required = false,
                    defaultValue = "~"
                ),
                CommandParam(
                    key = "y",
                    label = "Y",
                    required = false,
                    defaultValue = "~"
                ),
                CommandParam(
                    key = "z",
                    label = "Z",
                    required = false,
                    defaultValue = "~"
                ),
            )
        ) { v ->
            if (v.v("x").isNotEmpty() && v.v("y").isNotEmpty() && v.v("z").isNotEmpty()) {
                if (!(v.v("x").isNotEmpty() && v.v("y").isNotEmpty() && v.v("z").isNotEmpty())) return@CommandSpec null
                val sb = StringBuilder("setworldspawn")
                sb.append(" ").append(v.v("x") + " " + v.v("y") + " " + v.v("z"))
                return@CommandSpec sb.toString()
            }
            val sb = StringBuilder("setworldspawn")
            return@CommandSpec sb.toString()
        },

        CommandSpec(
            keyword = "gamerule",
            description = "修改游戏规则",
            category = "世界与方块",
            aliases = listOf("游戏规则"),
            params = listOf(
                CommandParam(
                    key = "rule",
                    label = "规则名",
                    type = ParamType.CHOICE,
                    hint = "可先打字母筛选",
                    suggestions = Suggest.gamerules.map { it.id },
                    suggestionLabels = Suggest.gamerules.map { it.name }
                ),
                CommandParam(
                    key = "value",
                    label = "值",
                    type = ParamType.CHOICE,
                    required = false,
                    defaultValue = "true",
                    choices = listOf("true", "false")
                ),
            )
        ) { v ->
            if (!(v.v("rule").isNotEmpty())) return@CommandSpec null
            val sb = StringBuilder("gamerule")
            sb.append(" ").append(v.v("rule") + " ")
            if (v.v("value").isNotEmpty()) sb.append(" ").append(v.v("value"))
            return@CommandSpec sb.toString()
        },

        CommandSpec(
            keyword = "tickingarea",
            description = "创建常加载区域（让远处区块保持运行）",
            category = "世界与方块",
            aliases = listOf("常加载区域"),
            params = listOf(
                CommandParam(
                    key = "mode",
                    label = "操作",
                    type = ParamType.CHOICE,
                    defaultValue = "add",
                    choices = listOf("add", "remove", "remove_all", "list", "preload")
                ),
                CommandParam(
                    key = "from",
                    label = "起始坐标",
                    required = false,
                    hint = "如 ~ ~ ~ 或 0 0 0"
                ),
                CommandParam(
                    key = "to",
                    label = "结束坐标",
                    required = false
                ),
                CommandParam(
                    key = "name",
                    label = "区域名",
                    required = false,
                    hint = "可选，方便以后按名字移除"
                ),
            )
        ) { v ->
            if (v.vOr("mode", "") == "list") {
                val sb = StringBuilder("tickingarea")
                sb.append(" ").append("list")
                return@CommandSpec sb.toString()
            }
            if (v.vOr("mode", "") == "remove_all") {
                val sb = StringBuilder("tickingarea")
                sb.append(" ").append("remove_all")
                return@CommandSpec sb.toString()
            }
            if (v.v("from").isNotEmpty() && v.v("to").isNotEmpty()) {
                if (!(v.v("mode").isNotEmpty() && v.v("from").isNotEmpty() && v.v("to").isNotEmpty())) return@CommandSpec null
                val sb = StringBuilder("tickingarea")
                sb.append(" ").append(v.v("mode") + " " + v.v("from") + " " + v.v("to") + " ")
                if (v.v("name").isNotEmpty()) sb.append(" ").append(v.v("name"))
                return@CommandSpec sb.toString()
            }
            if (!(v.v("mode").isNotEmpty())) return@CommandSpec null
            val sb = StringBuilder("tickingarea")
            sb.append(" ").append(v.v("mode"))
            return@CommandSpec sb.toString()
        },

        CommandSpec(
            keyword = "effect",
            description = "给予或清除状态效果",
            category = "实体与效果",
            aliases = listOf("状态效果", "药水效果"),
            params = listOf(
                CommandParam(
                    key = "target",
                    label = "目标",
                    type = ParamType.SELECTOR,
                    defaultValue = "@s"
                ),
                CommandParam(
                    key = "mode",
                    label = "操作",
                    type = ParamType.CHOICE,
                    defaultValue = "give",
                    choices = listOf("give", "clear"),
                    hint = "give = 给予效果；clear = 清除效果"
                ),
                CommandParam(
                    key = "effect",
                    label = "效果 ID",
                    required = false,
                    hint = "give 必填；clear 时可留空表示清除全部效果",
                    suggestions = Suggest.effects.map { it.id },
                    suggestionLabels = Suggest.effects.map { it.name }
                ),
                CommandParam(
                    key = "duration",
                    label = "持续秒数",
                    type = ParamType.NUMBER,
                    required = false,
                    defaultValue = "30",
                    hint = "也可填 infinite 表示永久"
                ),
                CommandParam(
                    key = "amplifier",
                    label = "等级（0 起）",
                    type = ParamType.NUMBER,
                    required = false,
                    defaultValue = "0"
                ),
                CommandParam(
                    key = "hideParticles",
                    label = "隐藏粒子",
                    type = ParamType.BOOLEAN,
                    required = false,
                    defaultValue = "false"
                ),
            )
        ) { v ->
            if (v.vOr("mode", "") == "clear" && v.vOr("effect", "") == "") {
                if (!(v.v("target").isNotEmpty())) return@CommandSpec null
                val sb = StringBuilder("effect")
                sb.append(" ").append(v.v("target") + " clear")
                return@CommandSpec sb.toString()
            }
            if (v.vOr("mode", "") == "clear") {
                if (!(v.v("target").isNotEmpty() && v.v("effect").isNotEmpty())) return@CommandSpec null
                val sb = StringBuilder("effect")
                sb.append(" ").append(v.v("target") + " clear " + v.v("effect"))
                return@CommandSpec sb.toString()
            }
            if (v.vOr("duration", "") == "infinite") {
                if (!(v.v("target").isNotEmpty() && v.v("effect").isNotEmpty())) return@CommandSpec null
                val sb = StringBuilder("effect")
                sb.append(" ").append(v.v("target") + " " + v.v("effect") + " infinite " + v.vOr("amplifier", "0") + " " + v.vOr("hideParticles", "false"))
                return@CommandSpec sb.toString()
            }
            if (v.v("effect").isNotEmpty()) {
                if (!(v.v("target").isNotEmpty() && v.v("effect").isNotEmpty())) return@CommandSpec null
                val sb = StringBuilder("effect")
                sb.append(" ").append(v.v("target") + " " + v.v("effect") + " " + v.vOr("duration", "30") + " " + v.vOr("amplifier", "0") + " " + v.vOr("hideParticles", "false"))
                return@CommandSpec sb.toString()
            }
            return@CommandSpec null
        },

        CommandSpec(
            keyword = "summon",
            description = "生成一个实体",
            category = "实体与效果",
            aliases = listOf("生成生物", "刷怪"),
            params = listOf(
                CommandParam(
                    key = "entity",
                    label = "实体 ID",
                    suggestions = Suggest.entities.map { it.id },
                    suggestionLabels = Suggest.entities.map { it.name }
                ),
                CommandParam(
                    key = "x",
                    label = "X",
                    defaultValue = "~"
                ),
                CommandParam(
                    key = "y",
                    label = "Y",
                    defaultValue = "~"
                ),
                CommandParam(
                    key = "z",
                    label = "Z",
                    defaultValue = "~"
                ),
                CommandParam(
                    key = "yRot",
                    label = "水平朝向",
                    required = false,
                    hint = "如 90 或 ~"
                ),
                CommandParam(
                    key = "xRot",
                    label = "垂直朝向",
                    required = false
                ),
                CommandParam(
                    key = "spawnEvent",
                    label = "生成事件",
                    required = false,
                    hint = "如 minecraft:entity_born"
                ),
                CommandParam(
                    key = "nameTag",
                    label = "名称",
                    required = false,
                    hint = "直接写名字，支持 § 颜色代码；不要写 JSON"
                ),
            )
        ) { v ->
            if (v.v("entity").isNotEmpty() && v.v("spawnEvent").isNotEmpty() && v.v("nameTag").isNotEmpty()) {
                if (!(v.v("entity").isNotEmpty() && v.v("x").isNotEmpty() && v.v("y").isNotEmpty() && v.v("z").isNotEmpty() && v.v("spawnEvent").isNotEmpty() && v.v("nameTag").isNotEmpty())) return@CommandSpec null
                val sb = StringBuilder("summon")
                sb.append(" ").append(v.v("entity") + " " + v.v("x") + " " + v.v("y") + " " + v.v("z") + " ")
                if (v.v("yRot").isNotEmpty()) sb.append(" ").append(v.v("yRot"))
                sb.append(" ")
                if (v.v("xRot").isNotEmpty()) sb.append(" ").append(v.v("xRot"))
                sb.append(" " + v.v("spawnEvent") + " " + v.v("nameTag"))
                return@CommandSpec sb.toString()
            }
            if (v.v("entity").isNotEmpty() && v.v("nameTag").isNotEmpty()) {
                if (!(v.v("entity").isNotEmpty() && v.v("x").isNotEmpty() && v.v("y").isNotEmpty() && v.v("z").isNotEmpty() && v.v("nameTag").isNotEmpty())) return@CommandSpec null
                val sb = StringBuilder("summon")
                sb.append(" ").append(v.v("entity") + " " + v.v("x") + " " + v.v("y") + " " + v.v("z") + " ")
                if (v.v("yRot").isNotEmpty()) sb.append(" ").append(v.v("yRot"))
                sb.append(" ")
                if (v.v("xRot").isNotEmpty()) sb.append(" ").append(v.v("xRot"))
                sb.append(" " + v.v("nameTag"))
                return@CommandSpec sb.toString()
            }
            if (v.v("entity").isNotEmpty() && v.v("spawnEvent").isNotEmpty()) {
                if (!(v.v("entity").isNotEmpty() && v.v("x").isNotEmpty() && v.v("y").isNotEmpty() && v.v("z").isNotEmpty() && v.v("spawnEvent").isNotEmpty())) return@CommandSpec null
                val sb = StringBuilder("summon")
                sb.append(" ").append(v.v("entity") + " " + v.v("x") + " " + v.v("y") + " " + v.v("z") + " ")
                if (v.v("yRot").isNotEmpty()) sb.append(" ").append(v.v("yRot"))
                sb.append(" ")
                if (v.v("xRot").isNotEmpty()) sb.append(" ").append(v.v("xRot"))
                sb.append(" " + v.v("spawnEvent"))
                return@CommandSpec sb.toString()
            }
            if (v.v("entity").isNotEmpty()) {
                if (!(v.v("entity").isNotEmpty() && v.v("x").isNotEmpty() && v.v("y").isNotEmpty() && v.v("z").isNotEmpty())) return@CommandSpec null
                val sb = StringBuilder("summon")
                sb.append(" ").append(v.v("entity") + " " + v.v("x") + " " + v.v("y") + " " + v.v("z") + " ")
                if (v.v("yRot").isNotEmpty()) sb.append(" ").append(v.v("yRot"))
                sb.append(" ")
                if (v.v("xRot").isNotEmpty()) sb.append(" ").append(v.v("xRot"))
                return@CommandSpec sb.toString()
            }
            return@CommandSpec null
        },

        CommandSpec(
            keyword = "kill",
            description = "清除实体（对自己用等于自杀）",
            category = "实体与效果",
            aliases = listOf("清除实体", "杀死"),
            params = listOf(
                CommandParam(
                    key = "target",
                    label = "目标",
                    type = ParamType.SELECTOR,
                    defaultValue = "@s"
                ),
            )
        ) { v ->
            if (!(v.v("target").isNotEmpty())) return@CommandSpec null
            val sb = StringBuilder("kill")
            sb.append(" ").append(v.v("target"))
            return@CommandSpec sb.toString()
        },

        CommandSpec(
            keyword = "damage",
            description = "对实体造成指定类型与数值的伤害",
            category = "实体与效果",
            aliases = listOf("造成伤害"),
            params = listOf(
                CommandParam(
                    key = "target",
                    label = "目标",
                    type = ParamType.SELECTOR,
                    defaultValue = "@s"
                ),
                CommandParam(
                    key = "amount",
                    label = "伤害值",
                    type = ParamType.NUMBER,
                    defaultValue = "1"
                ),
                CommandParam(
                    key = "cause",
                    label = "伤害类型",
                    required = false,
                    defaultValue = "none",
                    hint = "如 none / fall / fire / drowning / magic"
                ),
            )
        ) { v ->
            if (!(v.v("target").isNotEmpty() && v.v("amount").isNotEmpty())) return@CommandSpec null
            val sb = StringBuilder("damage")
            sb.append(" ").append(v.v("target") + " " + v.v("amount") + " ")
            if (v.v("cause").isNotEmpty()) sb.append(" ").append(v.v("cause"))
            return@CommandSpec sb.toString()
        },

        CommandSpec(
            keyword = "tag",
            description = "给实体加标签 / 移除标签（做条件判断的基础）",
            category = "实体与效果",
            aliases = listOf("标签"),
            params = listOf(
                CommandParam(
                    key = "target",
                    label = "目标",
                    type = ParamType.SELECTOR,
                    defaultValue = "@s"
                ),
                CommandParam(
                    key = "action",
                    label = "操作",
                    type = ParamType.CHOICE,
                    defaultValue = "add",
                    choices = listOf("add", "remove", "list")
                ),
                CommandParam(
                    key = "name",
                    label = "标签名",
                    required = false,
                    hint = "list 时不用填"
                ),
            )
        ) { v ->
            if (v.vOr("action", "") == "list") {
                if (!(v.v("target").isNotEmpty())) return@CommandSpec null
                val sb = StringBuilder("tag")
                sb.append(" ").append(v.v("target") + " list")
                return@CommandSpec sb.toString()
            }
            if (v.v("target").isNotEmpty() && v.v("name").isNotEmpty()) {
                if (!(v.v("target").isNotEmpty() && v.v("action").isNotEmpty() && v.v("name").isNotEmpty())) return@CommandSpec null
                val sb = StringBuilder("tag")
                sb.append(" ").append(v.v("target") + " " + v.v("action") + " " + v.v("name"))
                return@CommandSpec sb.toString()
            }
            if (!(v.v("target").isNotEmpty() && v.v("action").isNotEmpty())) return@CommandSpec null
            val sb = StringBuilder("tag")
            sb.append(" ").append(v.v("target") + " " + v.v("action"))
            return@CommandSpec sb.toString()
        },

        CommandSpec(
            keyword = "testfor",
            description = "检测是否存在符合条件的实体（命令方块条件用）",
            category = "实体与效果",
            aliases = listOf("检测实体"),
            params = listOf(
                CommandParam(
                    key = "target",
                    label = "目标",
                    type = ParamType.SELECTOR,
                    defaultValue = "@a"
                ),
            )
        ) { v ->
            if (!(v.v("target").isNotEmpty())) return@CommandSpec null
            val sb = StringBuilder("testfor")
            sb.append(" ").append(v.v("target"))
            return@CommandSpec sb.toString()
        },

        CommandSpec(
            keyword = "ride",
            description = "让实体骑乘 / 下来 / 召唤骑手与坐骑",
            category = "实体与效果",
            aliases = listOf("骑乘"),
            params = listOf(
                CommandParam(
                    key = "riders",
                    label = "骑乘者 / 被骑者",
                    type = ParamType.SELECTOR,
                    defaultValue = "@s",
                    hint = "start_riding / stop_riding / summon_ride 填骑乘者；evict_riders / summon_rider 填被骑的实体"
                ),
                CommandParam(
                    key = "mode",
                    label = "操作",
                    type = ParamType.CHOICE,
                    defaultValue = "start_riding",
                    choices = listOf("start_riding", "stop_riding", "evict_riders", "summon_rider", "summon_ride")
                ),
                CommandParam(
                    key = "ride",
                    label = "坐骑",
                    type = ParamType.SELECTOR,
                    required = false,
                    defaultValue = "@e[type=armor_stand]",
                    hint = "start_riding 时填"
                ),
                CommandParam(
                    key = "entityType",
                    label = "召唤的实体",
                    required = false,
                    hint = "summon_rider / summon_ride 时填",
                    suggestions = Suggest.entities.map { it.id },
                    suggestionLabels = Suggest.entities.map { it.name }
                ),
                CommandParam(
                    key = "teleportRules",
                    label = "传送规则",
                    type = ParamType.CHOICE,
                    required = false,
                    defaultValue = "teleport_rider",
                    choices = listOf("teleport_rider", "teleport_ride")
                ),
                CommandParam(
                    key = "howToFill",
                    label = "填充方式",
                    type = ParamType.CHOICE,
                    required = false,
                    defaultValue = "until_full",
                    choices = listOf("until_full", "if_group_fits")
                ),
                CommandParam(
                    key = "rideRules",
                    label = "召唤规则",
                    type = ParamType.CHOICE,
                    required = false,
                    defaultValue = "reassign_rides",
                    choices = listOf("reassign_rides", "no_ride_change", "skip_riders")
                ),
                CommandParam(
                    key = "spawnEvent",
                    label = "生成事件",
                    required = false
                ),
                CommandParam(
                    key = "nameTag",
                    label = "名称",
                    required = false
                ),
            )
        ) { v ->
            if (v.vOr("mode", "") == "stop_riding") {
                if (!(v.v("riders").isNotEmpty())) return@CommandSpec null
                val sb = StringBuilder("ride")
                sb.append(" ").append(v.v("riders") + " stop_riding")
                return@CommandSpec sb.toString()
            }
            if (v.vOr("mode", "") == "evict_riders") {
                if (!(v.v("riders").isNotEmpty())) return@CommandSpec null
                val sb = StringBuilder("ride")
                sb.append(" ").append(v.v("riders") + " evict_riders")
                return@CommandSpec sb.toString()
            }
            if (v.vOr("mode", "") == "summon_rider") {
                if (!(v.v("riders").isNotEmpty() && v.v("entityType").isNotEmpty())) return@CommandSpec null
                val sb = StringBuilder("ride")
                sb.append(" ").append(v.v("riders") + " summon_rider " + v.v("entityType") + " ")
                if (v.v("spawnEvent").isNotEmpty()) sb.append(" ").append(v.v("spawnEvent"))
                sb.append(" ")
                if (v.v("nameTag").isNotEmpty()) sb.append(" ").append(v.v("nameTag"))
                return@CommandSpec sb.toString()
            }
            if (v.vOr("mode", "") == "summon_ride") {
                if (!(v.v("riders").isNotEmpty() && v.v("entityType").isNotEmpty())) return@CommandSpec null
                val sb = StringBuilder("ride")
                sb.append(" ").append(v.v("riders") + " summon_ride " + v.v("entityType") + " ")
                if (v.v("rideRules").isNotEmpty()) sb.append(" ").append(v.v("rideRules"))
                sb.append(" ")
                if (v.v("spawnEvent").isNotEmpty()) sb.append(" ").append(v.v("spawnEvent"))
                sb.append(" ")
                if (v.v("nameTag").isNotEmpty()) sb.append(" ").append(v.v("nameTag"))
                return@CommandSpec sb.toString()
            }
            if (v.v("ride").isNotEmpty()) {
                if (!(v.v("riders").isNotEmpty() && v.v("ride").isNotEmpty())) return@CommandSpec null
                val sb = StringBuilder("ride")
                sb.append(" ").append(v.v("riders") + " start_riding " + v.v("ride") + " ")
                if (v.v("teleportRules").isNotEmpty()) sb.append(" ").append(v.v("teleportRules"))
                sb.append(" ")
                if (v.v("howToFill").isNotEmpty()) sb.append(" ").append(v.v("howToFill"))
                return@CommandSpec sb.toString()
            }
            return@CommandSpec null
        },

        CommandSpec(
            keyword = "tp",
            description = "传送实体",
            category = "实体与效果",
            aliases = listOf("传送"),
            params = listOf(
                CommandParam(
                    key = "target",
                    label = "被传送者",
                    type = ParamType.SELECTOR,
                    defaultValue = "@s"
                ),
                CommandParam(
                    key = "x",
                    label = "X",
                    defaultValue = "~"
                ),
                CommandParam(
                    key = "y",
                    label = "Y",
                    defaultValue = "~"
                ),
                CommandParam(
                    key = "z",
                    label = "Z",
                    defaultValue = "~"
                ),
                CommandParam(
                    key = "yRot",
                    label = "水平朝向",
                    required = false,
                    hint = "如 90 或 ~"
                ),
                CommandParam(
                    key = "xRot",
                    label = "垂直朝向",
                    required = false
                ),
            )
        ) { v ->
            if (!(v.v("target").isNotEmpty() && v.v("x").isNotEmpty() && v.v("y").isNotEmpty() && v.v("z").isNotEmpty())) return@CommandSpec null
            val sb = StringBuilder("tp")
            sb.append(" ").append(v.v("target") + " " + v.v("x") + " " + v.v("y") + " " + v.v("z"))
            if (v.v("yRot").isNotEmpty()) sb.append(" ").append(v.v("yRot"))
            if (v.v("xRot").isNotEmpty()) sb.append(" ").append(v.v("xRot"))
            return@CommandSpec sb.toString()
        },

        CommandSpec(
            keyword = "teleport",
            description = "传送实体（tp 的完整写法，两者等价）",
            category = "实体与效果",
            aliases = listOf("完整传送"),
            params = listOf(
                CommandParam(
                    key = "victim",
                    label = "被传送者",
                    type = ParamType.SELECTOR,
                    required = false,
                    hint = "留空 = 传送自己"
                ),
                CommandParam(
                    key = "destination",
                    label = "目的地实体",
                    type = ParamType.SELECTOR,
                    required = false,
                    hint = "传送到某实体身上；与坐标二选一"
                ),
                CommandParam(
                    key = "x",
                    label = "X",
                    required = false,
                    defaultValue = "~"
                ),
                CommandParam(
                    key = "y",
                    label = "Y",
                    required = false,
                    defaultValue = "~"
                ),
                CommandParam(
                    key = "z",
                    label = "Z",
                    required = false,
                    defaultValue = "~"
                ),
                CommandParam(
                    key = "yRot",
                    label = "水平朝向",
                    required = false,
                    hint = "如 90 或 ~"
                ),
                CommandParam(
                    key = "xRot",
                    label = "垂直朝向",
                    required = false
                ),
                CommandParam(
                    key = "checkForBlocks",
                    label = "检查阻挡",
                    type = ParamType.BOOLEAN,
                    required = false,
                    hint = "true = 目标位置被挡住就不传送"
                ),
            )
        ) { v ->
            if (v.v("victim").isNotEmpty() && v.v("destination").isNotEmpty()) {
                if (!(v.v("victim").isNotEmpty() && v.v("destination").isNotEmpty())) return@CommandSpec null
                val sb = StringBuilder("teleport")
                sb.append(" ").append(v.v("victim") + " " + v.v("destination") + " ")
                if (v.v("checkForBlocks").isNotEmpty()) sb.append(" ").append(v.v("checkForBlocks"))
                return@CommandSpec sb.toString()
            }
            if (v.v("victim").isNotEmpty() && v.v("yRot").isNotEmpty()) {
                if (!(v.v("victim").isNotEmpty() && v.v("yRot").isNotEmpty())) return@CommandSpec null
                val sb = StringBuilder("teleport")
                sb.append(" ").append(v.v("victim") + " " + v.vOr("x", "~") + " " + v.vOr("y", "~") + " " + v.vOr("z", "~") + " " + v.v("yRot") + " " + v.vOr("xRot", "0") + " ")
                if (v.v("checkForBlocks").isNotEmpty()) sb.append(" ").append(v.v("checkForBlocks"))
                return@CommandSpec sb.toString()
            }
            if (v.v("victim").isNotEmpty()) {
                if (!(v.v("victim").isNotEmpty())) return@CommandSpec null
                val sb = StringBuilder("teleport")
                sb.append(" ").append(v.v("victim") + " " + v.vOr("x", "~") + " " + v.vOr("y", "~") + " " + v.vOr("z", "~") + " ")
                if (v.v("checkForBlocks").isNotEmpty()) sb.append(" ").append(v.v("checkForBlocks"))
                return@CommandSpec sb.toString()
            }
            if (v.v("destination").isNotEmpty()) {
                if (!(v.v("destination").isNotEmpty())) return@CommandSpec null
                val sb = StringBuilder("teleport")
                sb.append(" ").append(v.v("destination"))
                return@CommandSpec sb.toString()
            }
            if (v.v("yRot").isNotEmpty()) {
                if (!(v.v("yRot").isNotEmpty())) return@CommandSpec null
                val sb = StringBuilder("teleport")
                sb.append(" ").append(v.vOr("x", "~") + " " + v.vOr("y", "~") + " " + v.vOr("z", "~") + " " + v.v("yRot") + " " + v.vOr("xRot", "0") + " ")
                if (v.v("checkForBlocks").isNotEmpty()) sb.append(" ").append(v.v("checkForBlocks"))
                return@CommandSpec sb.toString()
            }
            if (v.v("x").isNotEmpty()) {
                if (!(v.v("x").isNotEmpty())) return@CommandSpec null
                val sb = StringBuilder("teleport")
                sb.append(" ").append(v.v("x") + " " + v.vOr("y", "~") + " " + v.vOr("z", "~") + " ")
                if (v.v("checkForBlocks").isNotEmpty()) sb.append(" ").append(v.v("checkForBlocks"))
                return@CommandSpec sb.toString()
            }
            val sb = StringBuilder("teleport")
            return@CommandSpec sb.toString()
        },

        CommandSpec(
            keyword = "particle",
            description = "在指定位置播放粒子效果",
            category = "特效与音效",
            aliases = listOf("粒子", "特效粒子"),
            params = listOf(
                CommandParam(
                    key = "effect",
                    label = "粒子名称",
                    suggestions = Suggest.particles.map { it.id },
                    suggestionLabels = Suggest.particles.map { it.name }
                ),
                CommandParam(
                    key = "x",
                    label = "X",
                    defaultValue = "~"
                ),
                CommandParam(
                    key = "y",
                    label = "Y",
                    defaultValue = "~"
                ),
                CommandParam(
                    key = "z",
                    label = "Z",
                    defaultValue = "~"
                ),
            )
        ) { v ->
            if (!(v.v("effect").isNotEmpty() && v.v("x").isNotEmpty() && v.v("y").isNotEmpty() && v.v("z").isNotEmpty())) return@CommandSpec null
            val sb = StringBuilder("particle")
            sb.append(" ").append(v.v("effect") + " " + v.v("x") + " " + v.v("y") + " " + v.v("z"))
            return@CommandSpec sb.toString()
        },

        CommandSpec(
            keyword = "playsound",
            description = "给玩家播放音效",
            category = "特效与音效",
            aliases = listOf("音效", "播放声音"),
            params = listOf(
                CommandParam(
                    key = "sound",
                    label = "音效 ID",
                    hint = "如 random.levelup、mob.zombie.say",
                    suggestions = Suggest.sounds.map { it.id },
                    suggestionLabels = Suggest.sounds.map { it.name }
                ),
                CommandParam(
                    key = "target",
                    label = "目标",
                    type = ParamType.SELECTOR,
                    defaultValue = "@a"
                ),
                CommandParam(
                    key = "x",
                    label = "X",
                    required = false,
                    defaultValue = "~"
                ),
                CommandParam(
                    key = "y",
                    label = "Y",
                    required = false,
                    defaultValue = "~"
                ),
                CommandParam(
                    key = "z",
                    label = "Z",
                    required = false,
                    defaultValue = "~"
                ),
                CommandParam(
                    key = "volume",
                    label = "音量",
                    type = ParamType.NUMBER,
                    required = false,
                    defaultValue = "1"
                ),
                CommandParam(
                    key = "pitch",
                    label = "音调",
                    type = ParamType.NUMBER,
                    required = false,
                    defaultValue = "1"
                ),
                CommandParam(
                    key = "minimumVolume",
                    label = "最小音量",
                    type = ParamType.NUMBER,
                    required = false,
                    hint = "可选，越小传得越远"
                ),
            )
        ) { v ->
            if (v.v("sound").isNotEmpty() && v.v("minimumVolume").isNotEmpty()) {
                if (!(v.v("sound").isNotEmpty() && v.v("target").isNotEmpty() && v.v("x").isNotEmpty() && v.v("y").isNotEmpty() && v.v("z").isNotEmpty() && v.v("minimumVolume").isNotEmpty())) return@CommandSpec null
                val sb = StringBuilder("playsound")
                sb.append(" ").append(v.v("sound") + " " + v.v("target") + " " + v.v("x") + " " + v.v("y") + " " + v.v("z") + " " + v.vOr("volume", "1") + " " + v.vOr("pitch", "1") + " " + v.v("minimumVolume"))
                return@CommandSpec sb.toString()
            }
            if (v.v("sound").isNotEmpty()) {
                if (!(v.v("sound").isNotEmpty() && v.v("target").isNotEmpty() && v.v("x").isNotEmpty() && v.v("y").isNotEmpty() && v.v("z").isNotEmpty())) return@CommandSpec null
                val sb = StringBuilder("playsound")
                sb.append(" ").append(v.v("sound") + " " + v.v("target") + " " + v.v("x") + " " + v.v("y") + " " + v.v("z") + " " + v.vOr("volume", "1") + " " + v.vOr("pitch", "1"))
                return@CommandSpec sb.toString()
            }
            val sb = StringBuilder("playsound")
            return@CommandSpec sb.toString()
        },

        CommandSpec(
            keyword = "camera",
            description = "控制玩家镜头（运镜 / 淡入淡出 / 视角绑定，基岩版特有，做剧情演出用）",
            category = "特效与音效",
            aliases = listOf("镜头", "运镜"),
            params = listOf(
                CommandParam(
                    key = "player",
                    label = "玩家",
                    type = ParamType.SELECTOR,
                    defaultValue = "@a"
                ),
                CommandParam(
                    key = "mode",
                    label = "子命令",
                    type = ParamType.CHOICE,
                    defaultValue = "clear",
                    choices = listOf("clear", "attach_to_entity", "detach_from_entity", "fade", "fov_set", "fov_clear", "set", "play_spline", "remove_target")
                ),
                CommandParam(
                    key = "fadeMode",
                    label = "fade 方式",
                    type = ParamType.CHOICE,
                    required = false,
                    defaultValue = "time",
                    choices = listOf("time", "color"),
                    hint = "fade 专用"
                ),
                CommandParam(
                    key = "fadeIn",
                    label = "淡入秒数",
                    type = ParamType.NUMBER,
                    required = false,
                    defaultValue = "1"
                ),
                CommandParam(
                    key = "hold",
                    label = "保持秒数",
                    type = ParamType.NUMBER,
                    required = false,
                    defaultValue = "1"
                ),
                CommandParam(
                    key = "fadeOut",
                    label = "淡出秒数",
                    type = ParamType.NUMBER,
                    required = false,
                    defaultValue = "1"
                ),
                CommandParam(
                    key = "red",
                    label = "红 0-255",
                    type = ParamType.NUMBER,
                    required = false,
                    defaultValue = "0"
                ),
                CommandParam(
                    key = "green",
                    label = "绿 0-255",
                    type = ParamType.NUMBER,
                    required = false,
                    defaultValue = "0"
                ),
                CommandParam(
                    key = "blue",
                    label = "蓝 0-255",
                    type = ParamType.NUMBER,
                    required = false,
                    defaultValue = "0"
                ),
                CommandParam(
                    key = "preset",
                    label = "预设名",
                    required = false,
                    hint = "set 专用，如 minecraft:free"
                ),
                CommandParam(
                    key = "fov",
                    label = "视野值",
                    type = ParamType.NUMBER,
                    required = false,
                    defaultValue = "70"
                ),
                CommandParam(
                    key = "easeTime",
                    label = "过渡秒数",
                    type = ParamType.NUMBER,
                    required = false,
                    defaultValue = "1"
                ),
                CommandParam(
                    key = "easeType",
                    label = "过渡曲线",
                    required = false,
                    defaultValue = "linear",
                    hint = "Easing 枚举：linear / in_quad / out_circ / in_out_cubic…（共 31 个）"
                ),
                CommandParam(
                    key = "entity",
                    label = "绑定实体",
                    type = ParamType.SELECTOR,
                    required = false,
                    hint = "attach_to_entity 专用"
                ),
                CommandParam(
                    key = "spline",
                    label = "样条名",
                    required = false,
                    hint = "play_spline 专用"
                ),
            )
        ) { v ->
            if (v.vOr("mode", "") == "attach_to_entity") {
                if (!(v.v("player").isNotEmpty() && v.v("entity").isNotEmpty())) return@CommandSpec null
                val sb = StringBuilder("camera")
                sb.append(" ").append(v.v("player") + " attach_to_entity " + v.v("entity"))
                return@CommandSpec sb.toString()
            }
            if (v.vOr("mode", "") == "detach_from_entity") {
                if (!(v.v("player").isNotEmpty())) return@CommandSpec null
                val sb = StringBuilder("camera")
                sb.append(" ").append(v.v("player") + " detach_from_entity")
                return@CommandSpec sb.toString()
            }
            if (v.vOr("mode", "") == "remove_target") {
                if (!(v.v("player").isNotEmpty())) return@CommandSpec null
                val sb = StringBuilder("camera")
                sb.append(" ").append(v.v("player") + " remove_target")
                return@CommandSpec sb.toString()
            }
            if (v.vOr("mode", "") == "fov_clear") {
                if (!(v.v("player").isNotEmpty())) return@CommandSpec null
                val sb = StringBuilder("camera")
                sb.append(" ").append(v.v("player") + " fov_clear ")
                if (v.v("easeTime").isNotEmpty()) sb.append(" ").append(v.v("easeTime"))
                sb.append(" ")
                if (v.v("easeType").isNotEmpty()) sb.append(" ").append(v.v("easeType"))
                return@CommandSpec sb.toString()
            }
            if (v.vOr("mode", "") == "fov_set") {
                if (!(v.v("player").isNotEmpty())) return@CommandSpec null
                val sb = StringBuilder("camera")
                sb.append(" ").append(v.v("player") + " fov_set " + v.vOr("fov", "70") + " ")
                if (v.v("easeTime").isNotEmpty()) sb.append(" ").append(v.v("easeTime"))
                sb.append(" ")
                if (v.v("easeType").isNotEmpty()) sb.append(" ").append(v.v("easeType"))
                return@CommandSpec sb.toString()
            }
            if (v.vOr("mode", "") == "play_spline") {
                if (!(v.v("player").isNotEmpty() && v.v("spline").isNotEmpty())) return@CommandSpec null
                val sb = StringBuilder("camera")
                sb.append(" ").append(v.v("player") + " play_spline " + v.v("spline"))
                return@CommandSpec sb.toString()
            }
            if (v.vOr("mode", "") == "set") {
                if (!(v.v("player").isNotEmpty() && v.v("preset").isNotEmpty())) return@CommandSpec null
                val sb = StringBuilder("camera")
                sb.append(" ").append(v.v("player") + " set " + v.v("preset"))
                return@CommandSpec sb.toString()
            }
            if (v.vOr("mode", "") == "fade" && v.vOr("fadeMode", "") == "color") {
                if (!(v.v("player").isNotEmpty())) return@CommandSpec null
                val sb = StringBuilder("camera")
                sb.append(" ").append(v.v("player") + " fade color " + v.vOr("red", "0") + " " + v.vOr("green", "0") + " " + v.vOr("blue", "0"))
                return@CommandSpec sb.toString()
            }
            if (v.vOr("mode", "") == "fade") {
                if (!(v.v("player").isNotEmpty())) return@CommandSpec null
                val sb = StringBuilder("camera")
                sb.append(" ").append(v.v("player") + " fade time " + v.vOr("fadeIn", "1") + " " + v.vOr("hold", "1") + " " + v.vOr("fadeOut", "1"))
                return@CommandSpec sb.toString()
            }
            if (v.vOr("mode", "") == "clear") {
                if (!(v.v("player").isNotEmpty())) return@CommandSpec null
                val sb = StringBuilder("camera")
                sb.append(" ").append(v.v("player") + " clear")
                return@CommandSpec sb.toString()
            }
            return@CommandSpec null
        },

        CommandSpec(
            keyword = "fog",
            description = "给玩家套用 / 移除迷雾（做气氛）",
            category = "特效与音效",
            aliases = listOf("迷雾"),
            params = listOf(
                CommandParam(
                    key = "target",
                    label = "目标",
                    type = ParamType.SELECTOR,
                    defaultValue = "@a"
                ),
                CommandParam(
                    key = "mode",
                    label = "操作",
                    type = ParamType.CHOICE,
                    defaultValue = "push",
                    choices = listOf("push", "delete"),
                    hint = "push = 套用迷雾；delete = 按标识移除"
                ),
                CommandParam(
                    key = "fogId",
                    label = "迷雾 ID",
                    required = false,
                    hint = "如 minecraft:fog_crimson_forest；push 时必填"
                ),
                CommandParam(
                    key = "userId",
                    label = "自定义标识",
                    hint = "自己起的名字，移除时按它来找；两端都要填"
                ),
            )
        ) { v ->
            if (v.vOr("mode", "") == "delete") {
                if (!(v.v("target").isNotEmpty() && v.v("userId").isNotEmpty())) return@CommandSpec null
                val sb = StringBuilder("fog")
                sb.append(" ").append(v.v("target") + " delete " + v.v("userId"))
                return@CommandSpec sb.toString()
            }
            if (v.v("fogId").isNotEmpty()) {
                if (!(v.v("target").isNotEmpty() && v.v("fogId").isNotEmpty() && v.v("userId").isNotEmpty())) return@CommandSpec null
                val sb = StringBuilder("fog")
                sb.append(" ").append(v.v("target") + " push " + v.v("fogId") + " " + v.v("userId"))
                return@CommandSpec sb.toString()
            }
            return@CommandSpec null
        },

        CommandSpec(
            keyword = "camerashake add",
            description = "给玩家加镜头抖动（爆炸 / 地震气氛）",
            category = "特效与音效",
            aliases = listOf("镜头抖动"),
            params = listOf(
                CommandParam(
                    key = "player",
                    label = "玩家",
                    type = ParamType.SELECTOR,
                    defaultValue = "@a"
                ),
                CommandParam(
                    key = "intensity",
                    label = "强度",
                    type = ParamType.NUMBER,
                    required = false,
                    defaultValue = "0.1"
                ),
                CommandParam(
                    key = "seconds",
                    label = "持续秒数",
                    type = ParamType.NUMBER,
                    required = false,
                    defaultValue = "1"
                ),
                CommandParam(
                    key = "shakeType",
                    label = "抖动类型",
                    type = ParamType.CHOICE,
                    required = false,
                    defaultValue = "positional",
                    choices = listOf("positional", "rotational")
                ),
            )
        ) { v ->
            if (!(v.v("player").isNotEmpty() && v.v("intensity").isNotEmpty() && v.v("seconds").isNotEmpty() && v.v("shakeType").isNotEmpty())) return@CommandSpec null
            val sb = StringBuilder("camerashake add")
            sb.append(" ").append(v.v("player") + " " + v.v("intensity") + " " + v.v("seconds") + " " + v.v("shakeType"))
            return@CommandSpec sb.toString()
        },

        CommandSpec(
            keyword = "camerashake stop",
            description = "停止镜头抖动",
            category = "特效与音效",
            aliases = listOf("停止镜头抖动"),
            params = listOf(
                CommandParam(
                    key = "player",
                    label = "玩家",
                    type = ParamType.SELECTOR,
                    defaultValue = "@a"
                ),
            )
        ) { v ->
            if (!(v.v("player").isNotEmpty())) return@CommandSpec null
            val sb = StringBuilder("camerashake stop")
            sb.append(" ").append(v.v("player"))
            return@CommandSpec sb.toString()
        },

        CommandSpec(
            keyword = "stopsound",
            description = "停止正在播放的音效",
            category = "特效与音效",
            aliases = listOf("停止音效"),
            params = listOf(
                CommandParam(
                    key = "player",
                    label = "玩家",
                    type = ParamType.SELECTOR,
                    defaultValue = "@a"
                ),
            )
        ) { v ->
            if (!(v.v("player").isNotEmpty())) return@CommandSpec null
            val sb = StringBuilder("stopsound")
            sb.append(" ").append(v.v("player"))
            return@CommandSpec sb.toString()
        },

        CommandSpec(
            keyword = "music play",
            description = "播放背景音乐",
            category = "特效与音效",
            aliases = listOf("播放音乐"),
            params = listOf(
                CommandParam(
                    key = "track",
                    label = "曲目",
                    hint = "如 music.game、music.overworld"
                ),
                CommandParam(
                    key = "volume",
                    label = "音量",
                    type = ParamType.NUMBER,
                    required = false,
                    defaultValue = "1"
                ),
                CommandParam(
                    key = "fadeSeconds",
                    label = "淡入秒数",
                    type = ParamType.NUMBER,
                    required = false,
                    defaultValue = "1"
                ),
                CommandParam(
                    key = "repeatMode",
                    label = "循环方式",
                    type = ParamType.CHOICE,
                    required = false,
                    defaultValue = "play_once",
                    choices = listOf("play_once", "loop")
                ),
            )
        ) { v ->
            if (!(v.v("track").isNotEmpty() && v.v("volume").isNotEmpty() && v.v("fadeSeconds").isNotEmpty() && v.v("repeatMode").isNotEmpty())) return@CommandSpec null
            val sb = StringBuilder("music play")
            sb.append(" ").append(v.v("track") + " " + v.v("volume") + " " + v.v("fadeSeconds") + " " + v.v("repeatMode"))
            return@CommandSpec sb.toString()
        },

        CommandSpec(
            keyword = "music stop",
            description = "停止背景音乐",
            category = "特效与音效",
            aliases = listOf("停止音乐"),
            params = listOf(
                CommandParam(
                    key = "fadeSeconds",
                    label = "淡出秒数",
                    type = ParamType.NUMBER,
                    required = false,
                    defaultValue = "1"
                ),
            )
        ) { v ->
            val sb = StringBuilder("music stop")
            if (v.v("fadeSeconds").isNotEmpty()) sb.append(" ").append(v.v("fadeSeconds"))
            return@CommandSpec sb.toString()
        },

        CommandSpec(
            keyword = "music volume",
            description = "调整音乐音量",
            category = "特效与音效",
            aliases = listOf("音乐音量"),
            params = listOf(
                CommandParam(
                    key = "volume",
                    label = "音量",
                    type = ParamType.NUMBER,
                    defaultValue = "1"
                ),
            )
        ) { v ->
            if (!(v.v("volume").isNotEmpty())) return@CommandSpec null
            val sb = StringBuilder("music volume")
            sb.append(" ").append(v.v("volume"))
            return@CommandSpec sb.toString()
        },

        CommandSpec(
            keyword = "music queue",
            description = "把一首曲目排进播放队列",
            category = "特效与音效",
            aliases = listOf("音乐 排队"),
            params = listOf(
                CommandParam(
                    key = "track",
                    label = "曲目",
                    defaultValue = "music.game",
                    hint = "如 music.game、music.overworld"
                ),
                CommandParam(
                    key = "volume",
                    label = "音量",
                    type = ParamType.NUMBER,
                    required = false,
                    defaultValue = "1"
                ),
                CommandParam(
                    key = "fadeSeconds",
                    label = "淡入秒数",
                    type = ParamType.NUMBER,
                    required = false,
                    defaultValue = "1"
                ),
                CommandParam(
                    key = "repeatMode",
                    label = "循环方式",
                    type = ParamType.CHOICE,
                    required = false,
                    defaultValue = "play_once",
                    choices = listOf("play_once", "loop")
                ),
            )
        ) { v ->
            if (!(v.v("track").isNotEmpty())) return@CommandSpec null
            val sb = StringBuilder("music queue")
            sb.append(" ").append(v.v("track") + " ")
            if (v.v("volume").isNotEmpty()) sb.append(" ").append(v.v("volume"))
            sb.append(" ")
            if (v.v("fadeSeconds").isNotEmpty()) sb.append(" ").append(v.v("fadeSeconds"))
            sb.append(" ")
            if (v.v("repeatMode").isNotEmpty()) sb.append(" ").append(v.v("repeatMode"))
            return@CommandSpec sb.toString()
        },

        CommandSpec(
            keyword = "gamemode",
            description = "切换游戏模式",
            category = "玩家与游戏规则",
            aliases = listOf("游戏模式"),
            params = listOf(
                CommandParam(
                    key = "mode",
                    label = "模式",
                    defaultValue = "creative",
                    hint = "survival / creative / adventure / spectator",
                    suggestions = Suggest.gameModes.map { it.id },
                    suggestionLabels = Suggest.gameModes.map { it.name }
                ),
                CommandParam(
                    key = "target",
                    label = "目标",
                    type = ParamType.SELECTOR,
                    required = false,
                    defaultValue = "@s"
                ),
            )
        ) { v ->
            if (!(v.v("mode").isNotEmpty())) return@CommandSpec null
            val sb = StringBuilder("gamemode")
            sb.append(" ").append(v.v("mode") + " ")
            if (v.v("target").isNotEmpty()) sb.append(" ").append(v.v("target"))
            return@CommandSpec sb.toString()
        },

        CommandSpec(
            keyword = "xp",
            description = "给玩家增加或扣除经验",
            category = "玩家与游戏规则",
            aliases = listOf("经验"),
            params = listOf(
                CommandParam(
                    key = "amount",
                    label = "数量",
                    type = ParamType.NUMBER,
                    defaultValue = "10"
                ),
                CommandParam(
                    key = "target",
                    label = "目标",
                    type = ParamType.SELECTOR,
                    required = false,
                    defaultValue = "@s"
                ),
            )
        ) { v ->
            if (!(v.v("amount").isNotEmpty())) return@CommandSpec null
            val sb = StringBuilder("xp")
            sb.append(" ").append(v.v("amount") + " ")
            if (v.v("target").isNotEmpty()) sb.append(" ").append(v.v("target"))
            return@CommandSpec sb.toString()
        },

        CommandSpec(
            keyword = "execute",
            description = "以指定身份 / 条件执行命令（新版子命令语法）",
            category = "玩家与游戏规则",
            aliases = listOf("执行", "条件执行"),
            params = listOf(
                CommandParam(
                    key = "target",
                    label = "执行者",
                    type = ParamType.SELECTOR,
                    required = false,
                    hint = "留空则不改变执行者"
                ),
                CommandParam(
                    key = "condition",
                    label = "条件",
                    required = false,
                    hint = "仅支持：if/unless block | blocks | entity | score。如 if entity @s[tag=队长]"
                ),
                CommandParam(
                    key = "command",
                    label = "要执行的命令"
                ),
            )
        ) { v ->
            if (v.v("target").isNotEmpty() && v.v("condition").isNotEmpty() && v.v("command").isNotEmpty()) {
                if (!(v.v("target").isNotEmpty() && v.v("condition").isNotEmpty() && v.v("command").isNotEmpty())) return@CommandSpec null
                val sb = StringBuilder("execute")
                sb.append(" ").append("as " + v.v("target") + " at @s " + v.v("condition") + " run " + v.v("command"))
                return@CommandSpec sb.toString()
            }
            if (v.v("target").isNotEmpty() && v.v("command").isNotEmpty()) {
                if (!(v.v("target").isNotEmpty() && v.v("command").isNotEmpty())) return@CommandSpec null
                val sb = StringBuilder("execute")
                sb.append(" ").append("as " + v.v("target") + " at @s run " + v.v("command"))
                return@CommandSpec sb.toString()
            }
            if (v.v("condition").isNotEmpty() && v.v("command").isNotEmpty()) {
                if (!(v.v("condition").isNotEmpty() && v.v("command").isNotEmpty())) return@CommandSpec null
                val sb = StringBuilder("execute")
                sb.append(" ").append(v.v("condition") + " run " + v.v("command"))
                return@CommandSpec sb.toString()
            }
            if (v.v("command").isNotEmpty()) {
                if (!(v.v("command").isNotEmpty())) return@CommandSpec null
                val sb = StringBuilder("execute")
                sb.append(" ").append("run " + v.v("command"))
                return@CommandSpec sb.toString()
            }
            val sb = StringBuilder("execute")
            return@CommandSpec sb.toString()
        },

        CommandSpec(
            keyword = "spreadplayers",
            description = "把实体随机散布在一片区域里（做小游戏开局很好用）",
            category = "玩家与游戏规则",
            aliases = listOf("随机散布"),
            params = listOf(
                CommandParam(
                    key = "x",
                    label = "中心 X",
                    defaultValue = "~"
                ),
                CommandParam(
                    key = "z",
                    label = "中心 Z",
                    defaultValue = "~"
                ),
                CommandParam(
                    key = "spreadDistance",
                    label = "最小间距",
                    type = ParamType.NUMBER,
                    defaultValue = "10"
                ),
                CommandParam(
                    key = "maxRange",
                    label = "散布范围",
                    type = ParamType.NUMBER,
                    defaultValue = "50"
                ),
                CommandParam(
                    key = "target",
                    label = "目标",
                    type = ParamType.SELECTOR,
                    defaultValue = "@a"
                ),
            )
        ) { v ->
            if (!(v.v("x").isNotEmpty() && v.v("z").isNotEmpty() && v.v("spreadDistance").isNotEmpty() && v.v("maxRange").isNotEmpty() && v.v("target").isNotEmpty())) return@CommandSpec null
            val sb = StringBuilder("spreadplayers")
            sb.append(" ").append(v.v("x") + " " + v.v("z") + " " + v.v("spreadDistance") + " " + v.v("maxRange") + " " + v.v("target"))
            return@CommandSpec sb.toString()
        },

        CommandSpec(
            keyword = "inputpermission",
            description = "开关玩家的移动 / 视角输入（做剧情锁操作）",
            category = "玩家与游戏规则",
            aliases = listOf("输入权限"),
            params = listOf(
                CommandParam(
                    key = "target",
                    label = "目标",
                    type = ParamType.SELECTOR,
                    defaultValue = "@a"
                ),
                CommandParam(
                    key = "permission",
                    label = "权限项",
                    type = ParamType.CHOICE,
                    defaultValue = "movement",
                    choices = listOf("movement", "camera")
                ),
                CommandParam(
                    key = "state",
                    label = "状态",
                    type = ParamType.CHOICE,
                    defaultValue = "disabled",
                    choices = listOf("enabled", "disabled")
                ),
            )
        ) { v ->
            if (!(v.v("target").isNotEmpty() && v.v("permission").isNotEmpty() && v.v("state").isNotEmpty())) return@CommandSpec null
            val sb = StringBuilder("inputpermission")
            sb.append(" ").append("set " + v.v("target") + " " + v.v("permission") + " " + v.v("state"))
            return@CommandSpec sb.toString()
        },

        CommandSpec(
            keyword = "structure save",
            description = "把一块区域保存成结构（做地标 / 建筑复制）",
            category = "世界与方块",
            aliases = listOf("结构 保存"),
            params = listOf(
                CommandParam(
                    key = "name",
                    label = "结构名"
                ),
                CommandParam(
                    key = "from",
                    label = "起始坐标",
                    defaultValue = "~ ~ ~"
                ),
                CommandParam(
                    key = "to",
                    label = "结束坐标",
                    defaultValue = "~ ~ ~"
                ),
                CommandParam(
                    key = "includeEntities",
                    label = "包含实体",
                    type = ParamType.BOOLEAN,
                    required = false,
                    defaultValue = "false"
                ),
                CommandParam(
                    key = "saveMode",
                    label = "保存方式",
                    type = ParamType.CHOICE,
                    required = false,
                    hint = "留空 = memory（内存，重启就没了）；disk = 存到磁盘"
                ),
                CommandParam(
                    key = "includeBlocks",
                    label = "包含方块",
                    type = ParamType.BOOLEAN,
                    required = false,
                    defaultValue = "true"
                ),
            )
        ) { v ->
            if (v.v("includeBlocks").isNotEmpty()) {
                if (!(v.v("name").isNotEmpty() && v.v("from").isNotEmpty() && v.v("to").isNotEmpty() && v.v("includeBlocks").isNotEmpty())) return@CommandSpec null
                val sb = StringBuilder("structure save")
                sb.append(" ").append(v.v("name") + " " + v.v("from") + " " + v.v("to") + " ")
                if (v.v("includeEntities").isNotEmpty()) sb.append(" ").append(v.v("includeEntities"))
                sb.append(" ")
                if (v.v("saveMode").isNotEmpty()) sb.append(" ").append(v.v("saveMode"))
                sb.append(" " + v.v("includeBlocks"))
                return@CommandSpec sb.toString()
            }
            if (v.v("includeEntities").isNotEmpty()) {
                if (!(v.v("name").isNotEmpty() && v.v("from").isNotEmpty() && v.v("to").isNotEmpty() && v.v("includeEntities").isNotEmpty())) return@CommandSpec null
                val sb = StringBuilder("structure save")
                sb.append(" ").append(v.v("name") + " " + v.v("from") + " " + v.v("to") + " " + v.v("includeEntities") + " ")
                if (v.v("saveMode").isNotEmpty()) sb.append(" ").append(v.v("saveMode"))
                return@CommandSpec sb.toString()
            }
            if (v.v("saveMode").isNotEmpty()) {
                if (!(v.v("name").isNotEmpty() && v.v("from").isNotEmpty() && v.v("to").isNotEmpty() && v.v("saveMode").isNotEmpty())) return@CommandSpec null
                val sb = StringBuilder("structure save")
                sb.append(" ").append(v.v("name") + " " + v.v("from") + " " + v.v("to") + " ")
                if (v.v("includeEntities").isNotEmpty()) sb.append(" ").append(v.v("includeEntities"))
                sb.append(" " + v.v("saveMode"))
                return@CommandSpec sb.toString()
            }
            if (!(v.v("name").isNotEmpty() && v.v("from").isNotEmpty() && v.v("to").isNotEmpty())) return@CommandSpec null
            val sb = StringBuilder("structure save")
            sb.append(" ").append(v.v("name") + " " + v.v("from") + " " + v.v("to"))
            return@CommandSpec sb.toString()
        },

        CommandSpec(
            keyword = "structure load",
            description = "把保存过的结构加载到指定位置",
            category = "世界与方块",
            aliases = listOf("结构 加载"),
            params = listOf(
                CommandParam(
                    key = "name",
                    label = "结构名"
                ),
                CommandParam(
                    key = "x",
                    label = "X",
                    defaultValue = "~"
                ),
                CommandParam(
                    key = "y",
                    label = "Y",
                    defaultValue = "~"
                ),
                CommandParam(
                    key = "z",
                    label = "Z",
                    defaultValue = "~"
                ),
                CommandParam(
                    key = "rotation",
                    label = "旋转",
                    type = ParamType.CHOICE,
                    required = false,
                    defaultValue = "0_degrees",
                    choices = listOf("0_degrees", "90_degrees", "180_degrees", "270_degrees")
                ),
                CommandParam(
                    key = "mirror",
                    label = "镜像",
                    type = ParamType.CHOICE,
                    required = false,
                    defaultValue = "none",
                    choices = listOf("none", "x", "z", "xz")
                ),
                CommandParam(
                    key = "animationMode",
                    label = "生成动画",
                    type = ParamType.CHOICE,
                    required = false,
                    defaultValue = "block_by_block",
                    choices = listOf("block_by_block", "layer_by_layer")
                ),
                CommandParam(
                    key = "animationSeconds",
                    label = "动画秒数",
                    type = ParamType.NUMBER,
                    required = false,
                    defaultValue = "0"
                ),
                CommandParam(
                    key = "includeEntities",
                    label = "包含实体",
                    type = ParamType.BOOLEAN,
                    required = false,
                    defaultValue = "false"
                ),
                CommandParam(
                    key = "includeBlocks",
                    label = "包含方块",
                    type = ParamType.BOOLEAN,
                    required = false,
                    defaultValue = "true"
                ),
            )
        ) { v ->
            if (!(v.v("name").isNotEmpty() && v.v("x").isNotEmpty() && v.v("y").isNotEmpty() && v.v("z").isNotEmpty())) return@CommandSpec null
            val sb = StringBuilder("structure load")
            sb.append(" ").append(v.v("name") + " " + v.v("x") + " " + v.v("y") + " " + v.v("z") + " ")
            if (v.v("rotation").isNotEmpty()) sb.append(" ").append(v.v("rotation"))
            sb.append(" ")
            if (v.v("mirror").isNotEmpty()) sb.append(" ").append(v.v("mirror"))
            sb.append(" ")
            if (v.v("animationMode").isNotEmpty()) sb.append(" ").append(v.v("animationMode"))
            sb.append(" ")
            if (v.v("animationSeconds").isNotEmpty()) sb.append(" ").append(v.v("animationSeconds"))
            sb.append(" ")
            if (v.v("includeEntities").isNotEmpty()) sb.append(" ").append(v.v("includeEntities"))
            sb.append(" ")
            if (v.v("includeBlocks").isNotEmpty()) sb.append(" ").append(v.v("includeBlocks"))
            return@CommandSpec sb.toString()
        },

        CommandSpec(
            keyword = "structure delete",
            description = "删除已保存的结构",
            category = "世界与方块",
            aliases = listOf("结构 删除"),
            params = listOf(
                CommandParam(
                    key = "name",
                    label = "结构名"
                ),
            )
        ) { v ->
            if (!(v.v("name").isNotEmpty())) return@CommandSpec null
            val sb = StringBuilder("structure delete")
            sb.append(" ").append(v.v("name"))
            return@CommandSpec sb.toString()
        },

        CommandSpec(
            keyword = "dialogue open",
            description = "打开对话（NPC 剧情用）",
            category = "玩家与游戏规则",
            aliases = listOf("对话 打开"),
            params = listOf(
                CommandParam(
                    key = "npc",
                    label = "NPC",
                    type = ParamType.SELECTOR,
                    defaultValue = "@e[type=npc]"
                ),
                CommandParam(
                    key = "player",
                    label = "玩家",
                    type = ParamType.SELECTOR,
                    defaultValue = "@s"
                ),
                CommandParam(
                    key = "sceneTag",
                    label = "场景标签",
                    required = false,
                    hint = "留空则用 NPC 自己的对话"
                ),
            )
        ) { v ->
            if (!(v.v("npc").isNotEmpty() && v.v("player").isNotEmpty())) return@CommandSpec null
            val sb = StringBuilder("dialogue open")
            sb.append(" ").append(v.v("npc") + " " + v.v("player") + " ")
            if (v.v("sceneTag").isNotEmpty()) sb.append(" ").append(v.v("sceneTag"))
            return@CommandSpec sb.toString()
        },

        CommandSpec(
            keyword = "dialogue change",
            description = "切换某个 NPC 的对话场景",
            category = "玩家与游戏规则",
            aliases = listOf("对话 切换"),
            params = listOf(
                CommandParam(
                    key = "npc",
                    label = "NPC",
                    type = ParamType.SELECTOR,
                    defaultValue = "@e[type=npc]"
                ),
                CommandParam(
                    key = "sceneTag",
                    label = "场景标签"
                ),
                CommandParam(
                    key = "player",
                    label = "玩家",
                    type = ParamType.SELECTOR,
                    required = false,
                    defaultValue = "@s"
                ),
            )
        ) { v ->
            if (!(v.v("npc").isNotEmpty() && v.v("sceneTag").isNotEmpty())) return@CommandSpec null
            val sb = StringBuilder("dialogue change")
            sb.append(" ").append(v.v("npc") + " " + v.v("sceneTag") + " ")
            if (v.v("player").isNotEmpty()) sb.append(" ").append(v.v("player"))
            return@CommandSpec sb.toString()
        },

        CommandSpec(
            keyword = "reload",
            description = "重新加载行为包与函数（改完数据包不用重启）",
            category = "玩家与游戏规则",
            aliases = listOf("重载"),
            params = emptyList()
        ) { v ->
            val sb = StringBuilder("reload")
            return@CommandSpec sb.toString()
        },

        CommandSpec(
            keyword = "function",
            description = "运行函数文件里的一组命令",
            category = "玩家与游戏规则",
            aliases = listOf("函数"),
            params = listOf(
                CommandParam(
                    key = "name",
                    label = "函数路径",
                    hint = "如 mypack:start"
                ),
            )
        ) { v ->
            if (!(v.v("name").isNotEmpty())) return@CommandSpec null
            val sb = StringBuilder("function")
            sb.append(" ").append(v.v("name"))
            return@CommandSpec sb.toString()
        },

        CommandSpec(
            keyword = "schedule on_area_loaded add",
            description = "区域加载时延后执行函数",
            category = "玩家与游戏规则",
            aliases = listOf("计划任务 区域加载"),
            params = listOf(
                CommandParam(
                    key = "circle",
                    label = "区域",
                    hint = "如 circle 0 0 0 20"
                ),
                CommandParam(
                    key = "name",
                    label = "函数路径"
                ),
            )
        ) { v ->
            if (!(v.v("circle").isNotEmpty() && v.v("name").isNotEmpty())) return@CommandSpec null
            val sb = StringBuilder("schedule on_area_loaded add")
            sb.append(" ").append(v.v("circle") + " " + v.v("name"))
            return@CommandSpec sb.toString()
        },

        CommandSpec(
            keyword = "gametest run",
            description = "运行游戏内测试（开发者用）",
            category = "玩家与游戏规则",
            aliases = listOf("游戏测试"),
            params = listOf(
                CommandParam(
                    key = "testName",
                    label = "测试名"
                ),
            )
        ) { v ->
            if (!(v.v("testName").isNotEmpty())) return@CommandSpec null
            val sb = StringBuilder("gametest run")
            sb.append(" ").append(v.v("testName"))
            return@CommandSpec sb.toString()
        },

        CommandSpec(
            keyword = "gametest runthis",
            description = "运行距离最近的测试",
            category = "玩家与游戏规则",
            aliases = listOf("游戏测试 最近"),
            params = listOf(
                CommandParam(
                    key = "testName",
                    label = "测试名",
                    required = false
                ),
            )
        ) { v ->
            if (v.v("testName").isNotEmpty()) {
                if (!(v.v("testName").isNotEmpty())) return@CommandSpec null
                val sb = StringBuilder("gametest runthis")
                sb.append(" ").append(v.v("testName"))
                return@CommandSpec sb.toString()
            }
            val sb = StringBuilder("gametest runthis")
            return@CommandSpec sb.toString()
        },

        CommandSpec(
            keyword = "gametest runthese",
            description = "运行一定范围内的所有测试",
            category = "玩家与游戏规则",
            aliases = listOf("游戏测试 范围内"),
            params = listOf(
                CommandParam(
                    key = "radius",
                    label = "半径",
                    type = ParamType.NUMBER,
                    required = false,
                    defaultValue = "10"
                ),
            )
        ) { v ->
            val sb = StringBuilder("gametest runthese")
            if (v.v("radius").isNotEmpty()) sb.append(" ").append(v.v("radius"))
            return@CommandSpec sb.toString()
        },

        CommandSpec(
            keyword = "gametest runset",
            description = "运行一整个测试类",
            category = "玩家与游戏规则",
            aliases = listOf("游戏测试 测试类"),
            params = listOf(
                CommandParam(
                    key = "tag",
                    label = "测试类标签",
                    required = false,
                    hint = "留空 = 全部"
                ),
            )
        ) { v ->
            val sb = StringBuilder("gametest runset")
            if (v.v("tag").isNotEmpty()) sb.append(" ").append(v.v("tag"))
            return@CommandSpec sb.toString()
        },

        CommandSpec(
            keyword = "gametest runsetuntilfail",
            description = "运行测试类，遇到第一个失败就停",
            category = "玩家与游戏规则",
            aliases = listOf("游戏测试 失败即停"),
            params = listOf(
                CommandParam(
                    key = "tag",
                    label = "测试类标签",
                    required = false
                ),
            )
        ) { v ->
            val sb = StringBuilder("gametest runsetuntilfail")
            if (v.v("tag").isNotEmpty()) sb.append(" ").append(v.v("tag"))
            return@CommandSpec sb.toString()
        },

        CommandSpec(
            keyword = "gametest clearall",
            description = "清理测试区域",
            category = "玩家与游戏规则",
            aliases = listOf("游戏测试 清理"),
            params = listOf(
                CommandParam(
                    key = "radius",
                    label = "半径",
                    type = ParamType.NUMBER,
                    required = false,
                    defaultValue = "10"
                ),
            )
        ) { v ->
            val sb = StringBuilder("gametest clearall")
            if (v.v("radius").isNotEmpty()) sb.append(" ").append(v.v("radius"))
            return@CommandSpec sb.toString()
        },

        CommandSpec(
            keyword = "gametest create",
            description = "在指定位置创建测试结构",
            category = "玩家与游戏规则",
            aliases = listOf("游戏测试 创建"),
            params = listOf(
                CommandParam(
                    key = "name",
                    label = "测试名"
                ),
                CommandParam(
                    key = "x",
                    label = "X",
                    required = false,
                    defaultValue = "~"
                ),
                CommandParam(
                    key = "y",
                    label = "Y",
                    required = false,
                    defaultValue = "~"
                ),
                CommandParam(
                    key = "z",
                    label = "Z",
                    required = false,
                    defaultValue = "~"
                ),
            )
        ) { v ->
            if (!(v.v("name").isNotEmpty())) return@CommandSpec null
            val sb = StringBuilder("gametest create")
            sb.append(" ").append(v.v("name") + " ")
            if (v.v("x").isNotEmpty()) sb.append(" ").append(v.v("x"))
            sb.append(" ")
            if (v.v("y").isNotEmpty()) sb.append(" ").append(v.v("y"))
            sb.append(" ")
            if (v.v("z").isNotEmpty()) sb.append(" ").append(v.v("z"))
            return@CommandSpec sb.toString()
        },

        CommandSpec(
            keyword = "gametest pos",
            description = "定位测试结构的位置",
            category = "玩家与游戏规则",
            aliases = listOf("游戏测试 定位"),
            params = emptyList()
        ) { v ->
            val sb = StringBuilder("gametest pos")
            return@CommandSpec sb.toString()
        },

        CommandSpec(
            keyword = "gametest stopall",
            description = "停止所有正在运行的测试",
            category = "玩家与游戏规则",
            aliases = listOf("游戏测试 停止全部"),
            params = emptyList()
        ) { v ->
            val sb = StringBuilder("gametest stopall")
            return@CommandSpec sb.toString()
        },

        CommandSpec(
            keyword = "schedule delay add",
            description = "延迟若干刻之后执行函数",
            category = "玩家与游戏规则",
            aliases = listOf("计划任务 延迟"),
            params = listOf(
                CommandParam(
                    key = "name",
                    label = "函数路径",
                    defaultValue = "mypack:start"
                ),
                CommandParam(
                    key = "delay",
                    label = "延迟（刻）",
                    type = ParamType.NUMBER,
                    defaultValue = "20",
                    hint = "20 刻 = 1 秒"
                ),
                CommandParam(
                    key = "mode",
                    label = "重复时",
                    type = ParamType.CHOICE,
                    required = false,
                    defaultValue = "replace",
                    choices = listOf("replace", "append")
                ),
            )
        ) { v ->
            if (!(v.v("name").isNotEmpty() && v.v("delay").isNotEmpty())) return@CommandSpec null
            val sb = StringBuilder("schedule delay add")
            sb.append(" ").append(v.v("name") + " " + v.v("delay") + " ")
            if (v.v("mode").isNotEmpty()) sb.append(" ").append(v.v("mode"))
            return@CommandSpec sb.toString()
        },

        CommandSpec(
            keyword = "schedule delay clear",
            description = "取消延迟执行的函数",
            category = "玩家与游戏规则",
            aliases = listOf("计划任务 取消延迟"),
            params = listOf(
                CommandParam(
                    key = "name",
                    label = "函数路径",
                    defaultValue = "mypack:start"
                ),
            )
        ) { v ->
            if (!(v.v("name").isNotEmpty())) return@CommandSpec null
            val sb = StringBuilder("schedule delay clear")
            sb.append(" ").append(v.v("name"))
            return@CommandSpec sb.toString()
        },

        CommandSpec(
            keyword = "schedule clear",
            description = "清除某个函数的全部计划任务",
            category = "玩家与游戏规则",
            aliases = listOf("计划任务 清除"),
            params = listOf(
                CommandParam(
                    key = "name",
                    label = "函数路径",
                    defaultValue = "mypack:start"
                ),
            )
        ) { v ->
            if (!(v.v("name").isNotEmpty())) return@CommandSpec null
            val sb = StringBuilder("schedule clear")
            sb.append(" ").append(v.v("name"))
            return@CommandSpec sb.toString()
        },

        CommandSpec(
            keyword = "schedule on_area_loaded clear",
            description = "取消区域加载时的计划任务",
            category = "玩家与游戏规则",
            aliases = listOf("计划任务 取消区域加载"),
            params = listOf(
                CommandParam(
                    key = "kind",
                    label = "按什么取消",
                    type = ParamType.CHOICE,
                    defaultValue = "function",
                    choices = listOf("function", "tickingarea")
                ),
                CommandParam(
                    key = "name",
                    label = "函数路径 / 区域名",
                    defaultValue = "mypack:start"
                ),
                CommandParam(
                    key = "chunkX",
                    label = "区块 X",
                    required = false,
                    hint = "tickingarea 时可指定区块"
                ),
                CommandParam(
                    key = "chunkZ",
                    label = "区块 Z",
                    required = false
                ),
            )
        ) { v ->
            if (v.vOr("kind", "") == "tickingarea") {
                if (!(v.v("name").isNotEmpty())) return@CommandSpec null
                val sb = StringBuilder("schedule on_area_loaded clear")
                sb.append(" ").append("tickingarea " + v.v("name") + " ")
                if (v.v("chunkX").isNotEmpty()) sb.append(" ").append(v.v("chunkX"))
                sb.append(" ")
                if (v.v("chunkZ").isNotEmpty()) sb.append(" ").append(v.v("chunkZ"))
                return@CommandSpec sb.toString()
            }
            if (v.v("name").isNotEmpty()) {
                if (!(v.v("name").isNotEmpty())) return@CommandSpec null
                val sb = StringBuilder("schedule on_area_loaded clear")
                sb.append(" ").append("function " + v.v("name"))
                return@CommandSpec sb.toString()
            }
            return@CommandSpec null
        },

        CommandSpec(
            keyword = "help",
            description = "查看命令用法或列出全部命令",
            category = "文本显示",
            aliases = listOf("帮助", "命令帮助"),
            params = listOf(
                CommandParam(
                    key = "command",
                    label = "命令名",
                    required = false,
                    hint = "填了就看这条命令的用法；不填则列出全部"
                ),
                CommandParam(
                    key = "page",
                    label = "页码",
                    type = ParamType.NUMBER,
                    required = false,
                    defaultValue = "1"
                ),
            )
        ) { v ->
            if (v.v("command").isNotEmpty()) {
                if (!(v.v("command").isNotEmpty())) return@CommandSpec null
                val sb = StringBuilder("help")
                sb.append(" ").append(v.v("command"))
                return@CommandSpec sb.toString()
            }
            val sb = StringBuilder("help")
            sb.append(" ").append(v.vOr("page", "1"))
            return@CommandSpec sb.toString()
        },

        CommandSpec(
            keyword = "ability",
            description = "授予或撤销玩家能力（飞行、隐身、世界建造者等）",
            category = "玩家与游戏规则",
            aliases = listOf("玩家能力", "飞行隐身"),
            params = listOf(
                CommandParam(
                    key = "player",
                    label = "玩家",
                    type = ParamType.SELECTOR,
                    defaultValue = "@s"
                ),
                CommandParam(
                    key = "ability",
                    label = "能力",
                    required = false,
                    hint = "如 mayfly、mute、worldbuilder、invulnerable"
                ),
                CommandParam(
                    key = "value",
                    label = "开关",
                    type = ParamType.BOOLEAN,
                    required = false,
                    defaultValue = "true"
                ),
            )
        ) { v ->
            if (!(v.v("player").isNotEmpty())) return@CommandSpec null
            val sb = StringBuilder("ability")
            sb.append(" ").append(v.v("player") + " ")
            if (v.v("ability").isNotEmpty()) sb.append(" ").append(v.v("ability"))
            sb.append(" ")
            if (v.v("value").isNotEmpty()) sb.append(" ").append(v.v("value"))
            return@CommandSpec sb.toString()
        },

        CommandSpec(
            keyword = "allowlist",
            description = "管理服务器白名单（allowlist 是基岩版的写法）",
            category = "玩家与游戏规则",
            aliases = listOf("服务器白名单", "允许名单"),
            params = listOf(
                CommandParam(
                    key = "mode",
                    label = "操作",
                    type = ParamType.CHOICE,
                    defaultValue = "list",
                    choices = listOf("add", "remove", "list", "reload")
                ),
                CommandParam(
                    key = "name",
                    label = "玩家名",
                    required = false,
                    hint = "add / remove 时填；不需要玩家在线"
                ),
            )
        ) { v ->
            if (v.vOr("mode", "") == "list") {
                val sb = StringBuilder("allowlist")
                sb.append(" ").append("list")
                return@CommandSpec sb.toString()
            }
            if (v.vOr("mode", "") == "reload") {
                val sb = StringBuilder("allowlist")
                sb.append(" ").append("reload")
                return@CommandSpec sb.toString()
            }
            if (v.v("name").isNotEmpty()) {
                if (!(v.v("mode").isNotEmpty() && v.v("name").isNotEmpty())) return@CommandSpec null
                val sb = StringBuilder("allowlist")
                sb.append(" ").append(v.v("mode") + " " + v.v("name"))
                return@CommandSpec sb.toString()
            }
            return@CommandSpec null
        },

        CommandSpec(
            keyword = "aimassist",
            description = "设置或清除玩家的瞄准辅助（基岩版特有的辅助瞄准）",
            category = "玩家与游戏规则",
            aliases = listOf("瞄准辅助"),
            params = listOf(
                CommandParam(
                    key = "players",
                    label = "玩家",
                    type = ParamType.SELECTOR,
                    defaultValue = "@s"
                ),
                CommandParam(
                    key = "mode",
                    label = "操作",
                    type = ParamType.CHOICE,
                    defaultValue = "clear",
                    choices = listOf("clear", "set")
                ),
                CommandParam(
                    key = "angleX",
                    label = "水平角度",
                    type = ParamType.NUMBER,
                    required = false,
                    hint = "set 时可选"
                ),
                CommandParam(
                    key = "angleY",
                    label = "垂直角度",
                    type = ParamType.NUMBER,
                    required = false
                ),
                CommandParam(
                    key = "distance",
                    label = "最大距离",
                    type = ParamType.NUMBER,
                    required = false
                ),
                CommandParam(
                    key = "targetMode",
                    label = "目标模式",
                    required = false,
                    hint = "AimAssistTargetMode 枚举"
                ),
                CommandParam(
                    key = "presetId",
                    label = "预设 ID",
                    required = false
                ),
            )
        ) { v ->
            if (!(v.v("players").isNotEmpty() && v.v("mode").isNotEmpty())) return@CommandSpec null
            val sb = StringBuilder("aimassist")
            sb.append(" ").append(v.v("players") + " " + v.v("mode") + " ")
            if (v.v("angleX").isNotEmpty()) sb.append(" ").append(v.v("angleX"))
            sb.append(" ")
            if (v.v("angleY").isNotEmpty()) sb.append(" ").append(v.v("angleY"))
            sb.append(" ")
            if (v.v("distance").isNotEmpty()) sb.append(" ").append(v.v("distance"))
            sb.append(" ")
            if (v.v("targetMode").isNotEmpty()) sb.append(" ").append(v.v("targetMode"))
            sb.append(" ")
            if (v.v("presetId").isNotEmpty()) sb.append(" ").append(v.v("presetId"))
            return@CommandSpec sb.toString()
        },

        CommandSpec(
            keyword = "daylock",
            description = "锁定或解锁永昼",
            category = "世界与方块",
            aliases = listOf("锁定昼夜", "永昼"),
            params = listOf(
                CommandParam(
                    key = "lock",
                    label = "锁定",
                    type = ParamType.BOOLEAN,
                    required = false,
                    defaultValue = "true"
                ),
            )
        ) { v ->
            val sb = StringBuilder("daylock")
            if (v.v("lock").isNotEmpty()) sb.append(" ").append(v.v("lock"))
            return@CommandSpec sb.toString()
        },

        CommandSpec(
            keyword = "changesetting",
            description = "在专用服务器运行时修改设置",
            category = "玩家与游戏规则",
            aliases = listOf("服务器设置"),
            params = listOf(
                CommandParam(
                    key = "setting",
                    label = "设置项",
                    type = ParamType.CHOICE,
                    defaultValue = "allow-cheats",
                    choices = listOf("allow-cheats", "difficulty")
                ),
                CommandParam(
                    key = "value",
                    label = "值",
                    defaultValue = "true",
                    hint = "allow-cheats 用 true/false；difficulty 用 peaceful/easy/normal/hard"
                ),
            )
        ) { v ->
            if (v.vOr("setting", "") == "allow-cheats") {
                val sb = StringBuilder("changesetting")
                sb.append(" ").append("allow-cheats " + v.vOr("value", "true"))
                return@CommandSpec sb.toString()
            }
            if (v.v("value").isNotEmpty()) {
                if (!(v.v("value").isNotEmpty())) return@CommandSpec null
                val sb = StringBuilder("changesetting")
                sb.append(" ").append("difficulty " + v.v("value"))
                return@CommandSpec sb.toString()
            }
            return@CommandSpec null
        },

        CommandSpec(
            keyword = "clearspawnpoint",
            description = "清除玩家的个人出生点",
            category = "世界与方块",
            aliases = listOf("清除出生点"),
            params = listOf(
                CommandParam(
                    key = "player",
                    label = "玩家",
                    type = ParamType.SELECTOR,
                    required = false,
                    defaultValue = "@s"
                ),
            )
        ) { v ->
            val sb = StringBuilder("clearspawnpoint")
            if (v.v("player").isNotEmpty()) sb.append(" ").append(v.v("player"))
            return@CommandSpec sb.toString()
        },

        CommandSpec(
            keyword = "wsserver",
            description = "连接 WebSocket 服务器（教育版/脚本调试用）",
            category = "玩家与游戏规则",
            aliases = listOf("连接 WebSocket"),
            params = listOf(
                CommandParam(
                    key = "uri",
                    label = "服务器地址",
                    defaultValue = "ws://localhost:19131",
                    hint = "如 ws://localhost:19131"
                ),
            )
        ) { v ->
            if (!(v.v("uri").isNotEmpty())) return@CommandSpec null
            val sb = StringBuilder("wsserver")
            sb.append(" ").append(v.v("uri"))
            return@CommandSpec sb.toString()
        },

        CommandSpec(
            keyword = "controlscheme",
            description = "切换创作者相机的控制方案",
            category = "玩家与游戏规则",
            aliases = listOf("控制方案"),
            params = listOf(
                CommandParam(
                    key = "players",
                    label = "玩家",
                    type = ParamType.SELECTOR,
                    defaultValue = "@s"
                ),
                CommandParam(
                    key = "mode",
                    label = "操作",
                    type = ParamType.CHOICE,
                    defaultValue = "clear",
                    choices = listOf("clear", "set")
                ),
                CommandParam(
                    key = "scheme",
                    label = "控制方案",
                    required = false,
                    hint = "set 时必填，见 controlscheme 枚举"
                ),
            )
        ) { v ->
            if (!(v.v("players").isNotEmpty() && v.v("mode").isNotEmpty())) return@CommandSpec null
            val sb = StringBuilder("controlscheme")
            sb.append(" ").append(v.v("players") + " " + v.v("mode") + " ")
            if (v.v("scheme").isNotEmpty()) sb.append(" ").append(v.v("scheme"))
            return@CommandSpec sb.toString()
        },

        CommandSpec(
            keyword = "dedicatedwsserver",
            description = "连接专用服务器的 WebSocket 地址",
            category = "玩家与游戏规则",
            aliases = listOf("专用服务器连接"),
            params = listOf(
                CommandParam(
                    key = "address",
                    label = "服务器地址",
                    defaultValue = "localhost"
                ),
                CommandParam(
                    key = "port",
                    label = "端口",
                    type = ParamType.NUMBER,
                    defaultValue = "19131"
                ),
            )
        ) { v ->
            if (!(v.v("address").isNotEmpty() && v.v("port").isNotEmpty())) return@CommandSpec null
            val sb = StringBuilder("dedicatedwsserver")
            sb.append(" ").append(v.v("address") + " " + v.v("port"))
            return@CommandSpec sb.toString()
        },

        CommandSpec(
            keyword = "op",
            description = "把玩家设为管理员",
            category = "玩家与游戏规则",
            aliases = listOf("授予管理员", "给OP"),
            params = listOf(
                CommandParam(
                    key = "player",
                    label = "玩家",
                    type = ParamType.SELECTOR,
                    defaultValue = "@s"
                ),
            )
        ) { v ->
            if (!(v.v("player").isNotEmpty())) return@CommandSpec null
            val sb = StringBuilder("op")
            sb.append(" ").append(v.v("player"))
            return@CommandSpec sb.toString()
        },

        CommandSpec(
            keyword = "deop",
            description = "撤销玩家的管理员身份",
            category = "玩家与游戏规则",
            aliases = listOf("撤销管理员", "取消OP"),
            params = listOf(
                CommandParam(
                    key = "player",
                    label = "玩家",
                    type = ParamType.SELECTOR,
                    defaultValue = "@s"
                ),
            )
        ) { v ->
            if (!(v.v("player").isNotEmpty())) return@CommandSpec null
            val sb = StringBuilder("deop")
            sb.append(" ").append(v.v("player"))
            return@CommandSpec sb.toString()
        },

        CommandSpec(
            keyword = "permission",
            description = "查看或重载服务器权限列表（ops 是别名）",
            category = "玩家与游戏规则",
            aliases = listOf("权限重载"),
            params = listOf(
                CommandParam(
                    key = "mode",
                    label = "操作",
                    type = ParamType.CHOICE,
                    defaultValue = "list",
                    choices = listOf("list", "reload", "set")
                ),
                CommandParam(
                    key = "player",
                    label = "玩家",
                    type = ParamType.SELECTOR,
                    required = false,
                    defaultValue = "@s"
                ),
                CommandParam(
                    key = "level",
                    label = "权限等级",
                    required = false,
                    hint = "如 member / operator / visitor"
                ),
            )
        ) { v ->
            if (v.vOr("mode", "") == "list") {
                val sb = StringBuilder("permission")
                sb.append(" ").append("list")
                return@CommandSpec sb.toString()
            }
            if (v.vOr("mode", "") == "reload") {
                val sb = StringBuilder("permission")
                sb.append(" ").append("reload")
                return@CommandSpec sb.toString()
            }
            if (v.v("player").isNotEmpty()) {
                if (!(v.v("player").isNotEmpty())) return@CommandSpec null
                val sb = StringBuilder("permission")
                sb.append(" ").append("set " + v.v("player") + " ")
                if (v.v("level").isNotEmpty()) sb.append(" ").append(v.v("level"))
                return@CommandSpec sb.toString()
            }
            return@CommandSpec null
        },

        CommandSpec(
            keyword = "editor-allowlist",
            description = "管理 Minecraft Editor 服务器的白名单",
            category = "玩家与游戏规则",
            aliases = listOf("编辑器白名单"),
            params = listOf(
                CommandParam(
                    key = "mode",
                    label = "操作",
                    type = ParamType.CHOICE,
                    defaultValue = "list",
                    choices = listOf("add", "remove", "list")
                ),
                CommandParam(
                    key = "name",
                    label = "玩家名",
                    required = false,
                    hint = "add / remove 时填"
                ),
            )
        ) { v ->
            if (v.vOr("mode", "") == "list") {
                val sb = StringBuilder("editor-allowlist")
                sb.append(" ").append("list")
                return@CommandSpec sb.toString()
            }
            if (v.v("name").isNotEmpty()) {
                if (!(v.v("mode").isNotEmpty() && v.v("name").isNotEmpty())) return@CommandSpec null
                val sb = StringBuilder("editor-allowlist")
                sb.append(" ").append(v.v("mode") + " " + v.v("name"))
                return@CommandSpec sb.toString()
            }
            return@CommandSpec null
        },

        CommandSpec(
            keyword = "enchant",
            description = "给玩家手持的物品附魔（不消耗经验）",
            category = "物品",
            aliases = listOf("附魔", "给手持物品附魔"),
            params = listOf(
                CommandParam(
                    key = "player",
                    label = "玩家",
                    type = ParamType.SELECTOR,
                    defaultValue = "@s"
                ),
                CommandParam(
                    key = "enchantment",
                    label = "附魔",
                    defaultValue = "sharpness",
                    hint = "附魔名（如 sharpness）或数字 ID"
                ),
                CommandParam(
                    key = "level",
                    label = "等级",
                    type = ParamType.NUMBER,
                    required = false,
                    defaultValue = "1"
                ),
            )
        ) { v ->
            if (!(v.v("player").isNotEmpty() && v.v("enchantment").isNotEmpty())) return@CommandSpec null
            val sb = StringBuilder("enchant")
            sb.append(" ").append(v.v("player") + " " + v.v("enchantment") + " ")
            if (v.v("level").isNotEmpty()) sb.append(" ").append(v.v("level"))
            return@CommandSpec sb.toString()
        },

        CommandSpec(
            keyword = "event",
            description = "对实体触发一个行为包事件",
            category = "实体与效果",
            aliases = listOf("实体事件"),
            params = listOf(
                CommandParam(
                    key = "target",
                    label = "目标",
                    type = ParamType.SELECTOR,
                    defaultValue = "@e"
                ),
                CommandParam(
                    key = "eventName",
                    label = "事件名",
                    defaultValue = "minecraft:entity_born",
                    hint = "如 minecraft:entity_born"
                ),
            )
        ) { v ->
            if (!(v.v("target").isNotEmpty() && v.v("eventName").isNotEmpty())) return@CommandSpec null
            val sb = StringBuilder("event")
            sb.append(" ").append("entity " + v.v("target") + " " + v.v("eventName"))
            return@CommandSpec sb.toString()
        },

        CommandSpec(
            keyword = "gametips",
            description = "开关本机的游戏提示",
            category = "玩家与游戏规则",
            aliases = listOf("游戏提示"),
            params = listOf(
                CommandParam(
                    key = "status",
                    label = "状态",
                    type = ParamType.CHOICE,
                    defaultValue = "on",
                    choices = listOf("on", "off")
                ),
            )
        ) { v ->
            if (!(v.v("status").isNotEmpty())) return@CommandSpec null
            val sb = StringBuilder("gametips")
            sb.append(" ").append(v.v("status"))
            return@CommandSpec sb.toString()
        },

        CommandSpec(
            keyword = "hud",
            description = "显示或隐藏某个 HUD 元素",
            category = "玩家与游戏规则",
            aliases = listOf("界面元素", "HUD"),
            params = listOf(
                CommandParam(
                    key = "target",
                    label = "目标",
                    type = ParamType.SELECTOR,
                    defaultValue = "@s"
                ),
                CommandParam(
                    key = "visible",
                    label = "可见性",
                    type = ParamType.CHOICE,
                    defaultValue = "hide",
                    choices = listOf("hide", "show")
                ),
                CommandParam(
                    key = "element",
                    label = "HUD 元素",
                    required = false,
                    hint = "如 paper_doll、armor、tooltips、crosshair"
                ),
            )
        ) { v ->
            if (!(v.v("target").isNotEmpty() && v.v("visible").isNotEmpty())) return@CommandSpec null
            val sb = StringBuilder("hud")
            sb.append(" ").append(v.v("target") + " " + v.v("visible") + " ")
            if (v.v("element").isNotEmpty()) sb.append(" ").append(v.v("element"))
            return@CommandSpec sb.toString()
        },

        CommandSpec(
            keyword = "immutableworld",
            description = "锁定世界，禁止玩家破坏或放置方块",
            category = "世界与方块",
            aliases = listOf("世界不可变", "锁世界"),
            params = listOf(
                CommandParam(
                    key = "value",
                    label = "锁定",
                    type = ParamType.BOOLEAN,
                    required = false,
                    defaultValue = "true"
                ),
            )
        ) { v ->
            val sb = StringBuilder("immutableworld")
            if (v.v("value").isNotEmpty()) sb.append(" ").append(v.v("value"))
            return@CommandSpec sb.toString()
        },

        CommandSpec(
            keyword = "kick",
            description = "把玩家踢出服务器",
            category = "玩家与游戏规则",
            aliases = listOf("踢出玩家", "踢人"),
            params = listOf(
                CommandParam(
                    key = "target",
                    label = "玩家",
                    type = ParamType.SELECTOR,
                    defaultValue = "@s"
                ),
                CommandParam(
                    key = "reason",
                    label = "原因",
                    type = ParamType.LONG_TEXT,
                    required = false,
                    hint = "会显示给被踢的玩家"
                ),
            )
        ) { v ->
            if (!(v.v("target").isNotEmpty())) return@CommandSpec null
            val sb = StringBuilder("kick")
            sb.append(" ").append(v.v("target") + " ")
            if (v.v("reason").isNotEmpty()) sb.append(" ").append(v.v("reason"))
            return@CommandSpec sb.toString()
        },

        CommandSpec(
            keyword = "list",
            description = "列出服务器上在线的玩家",
            category = "玩家与游戏规则",
            aliases = listOf("玩家列表", "在线玩家"),
            params = emptyList()
        ) { v ->
            val sb = StringBuilder("list")
            return@CommandSpec sb.toString()
        },

        CommandSpec(
            keyword = "loot",
            description = "把战利品表的内容给玩家 / 塞进容器 / 掉到地上",
            category = "物品",
            aliases = listOf("战利品", "掉落物品"),
            params = listOf(
                CommandParam(
                    key = "targetMode",
                    label = "给到哪",
                    type = ParamType.CHOICE,
                    defaultValue = "give",
                    choices = listOf("give", "insert", "spawn")
                ),
                CommandParam(
                    key = "player",
                    label = "玩家",
                    type = ParamType.SELECTOR,
                    required = false,
                    defaultValue = "@s",
                    hint = "give 时用"
                ),
                CommandParam(
                    key = "x",
                    label = "X",
                    required = false,
                    defaultValue = "~"
                ),
                CommandParam(
                    key = "y",
                    label = "Y",
                    required = false,
                    defaultValue = "~"
                ),
                CommandParam(
                    key = "z",
                    label = "Z",
                    required = false,
                    defaultValue = "~"
                ),
                CommandParam(
                    key = "sourceMode",
                    label = "来源",
                    type = ParamType.CHOICE,
                    defaultValue = "loot",
                    choices = listOf("loot", "kill", "mine")
                ),
                CommandParam(
                    key = "table",
                    label = "战利品表",
                    required = false,
                    defaultValue = "minecraft:chests/simple_dungeon",
                    hint = "如 minecraft:chests/simple_dungeon"
                ),
                CommandParam(
                    key = "entity",
                    label = "实体",
                    type = ParamType.SELECTOR,
                    required = false,
                    defaultValue = "@e",
                    hint = "source 选 kill 时填"
                ),
            )
        ) { v ->
            if (v.vOr("sourceMode", "") == "kill") {
                if (!(v.v("targetMode").isNotEmpty() && v.v("player").isNotEmpty() && v.v("entity").isNotEmpty())) return@CommandSpec null
                val sb = StringBuilder("loot")
                sb.append(" ").append(v.v("targetMode") + " " + v.v("player") + " kill " + v.v("entity"))
                return@CommandSpec sb.toString()
            }
            if (v.vOr("sourceMode", "") == "mine") {
                if (!(v.v("targetMode").isNotEmpty() && v.v("player").isNotEmpty() && v.v("x").isNotEmpty() && v.v("y").isNotEmpty() && v.v("z").isNotEmpty())) return@CommandSpec null
                val sb = StringBuilder("loot")
                sb.append(" ").append(v.v("targetMode") + " " + v.v("player") + " mine " + v.v("x") + " " + v.v("y") + " " + v.v("z"))
                return@CommandSpec sb.toString()
            }
            if (v.vOr("targetMode", "") == "give") {
                if (!(v.v("player").isNotEmpty() && v.v("table").isNotEmpty())) return@CommandSpec null
                val sb = StringBuilder("loot")
                sb.append(" ").append("give " + v.v("player") + " loot " + v.v("table"))
                return@CommandSpec sb.toString()
            }
            if (v.v("x").isNotEmpty()) {
                if (!(v.v("targetMode").isNotEmpty() && v.v("x").isNotEmpty() && v.v("y").isNotEmpty() && v.v("z").isNotEmpty() && v.v("table").isNotEmpty())) return@CommandSpec null
                val sb = StringBuilder("loot")
                sb.append(" ").append(v.v("targetMode") + " " + v.v("x") + " " + v.v("y") + " " + v.v("z") + " loot " + v.v("table"))
                return@CommandSpec sb.toString()
            }
            return@CommandSpec null
        },

        CommandSpec(
            keyword = "mobevent",
            description = "启用或禁用指定的生物事件",
            category = "实体与效果",
            aliases = listOf("生物事件"),
            params = listOf(
                CommandParam(
                    key = "event",
                    label = "生物事件",
                    defaultValue = "minecraft:pillager_patrols_event",
                    hint = "如 minecraft:pillager_patrols_event"
                ),
                CommandParam(
                    key = "value",
                    label = "开关",
                    type = ParamType.BOOLEAN,
                    required = false,
                    defaultValue = "true"
                ),
            )
        ) { v ->
            if (!(v.v("event").isNotEmpty())) return@CommandSpec null
            val sb = StringBuilder("mobevent")
            sb.append(" ").append(v.v("event") + " ")
            if (v.v("value").isNotEmpty()) sb.append(" ").append(v.v("value"))
            return@CommandSpec sb.toString()
        },

        CommandSpec(
            keyword = "msg",
            description = "给指定玩家发私聊消息（tell / w 是别名）",
            category = "文本显示",
            aliases = listOf("私聊", "发消息"),
            params = listOf(
                CommandParam(
                    key = "target",
                    label = "目标",
                    type = ParamType.SELECTOR,
                    defaultValue = "@a"
                ),
                CommandParam(
                    key = "message",
                    label = "消息内容",
                    type = ParamType.LONG_TEXT
                ),
            )
        ) { v ->
            if (!(v.v("target").isNotEmpty() && v.v("message").isNotEmpty())) return@CommandSpec null
            val sb = StringBuilder("msg")
            sb.append(" ").append(v.v("target") + " " + v.v("message"))
            return@CommandSpec sb.toString()
        },

        CommandSpec(
            keyword = "party",
            description = "向当前队伍聊天频道发一条消息",
            category = "文本显示",
            aliases = listOf("队伍聊天"),
            params = listOf(
                CommandParam(
                    key = "message",
                    label = "消息内容",
                    type = ParamType.LONG_TEXT
                ),
            )
        ) { v ->
            if (!(v.v("message").isNotEmpty())) return@CommandSpec null
            val sb = StringBuilder("party")
            sb.append(" ").append(v.v("message"))
            return@CommandSpec sb.toString()
        },

        CommandSpec(
            keyword = "place",
            description = "在指定位置放置地物、拼图或结构",
            category = "世界与方块",
            aliases = listOf("放置结构", "放地物"),
            params = listOf(
                CommandParam(
                    key = "kind",
                    label = "类型",
                    type = ParamType.CHOICE,
                    defaultValue = "feature",
                    choices = listOf("feature", "featurerule", "jigsaw", "structure")
                ),
                CommandParam(
                    key = "name",
                    label = "名称",
                    defaultValue = "minecraft:oak",
                    hint = "feature 填地物名（如 minecraft:oak）；structure 填结构名"
                ),
                CommandParam(
                    key = "x",
                    label = "X",
                    required = false,
                    defaultValue = "~"
                ),
                CommandParam(
                    key = "y",
                    label = "Y",
                    required = false,
                    defaultValue = "~"
                ),
                CommandParam(
                    key = "z",
                    label = "Z",
                    required = false,
                    defaultValue = "~"
                ),
                CommandParam(
                    key = "pool",
                    label = "拼图池",
                    required = false,
                    hint = "jigsaw 专用，如 minecraft:village/plains/houses"
                ),
                CommandParam(
                    key = "jigsawTarget",
                    label = "起始拼图",
                    required = false,
                    hint = "jigsaw 专用"
                ),
                CommandParam(
                    key = "maxDepth",
                    label = "最大深度",
                    type = ParamType.NUMBER,
                    required = false,
                    defaultValue = "7",
                    hint = "jigsaw 专用"
                ),
            )
        ) { v ->
            if (v.vOr("kind", "") == "jigsaw") {
                if (!(v.v("pool").isNotEmpty() && v.v("jigsawTarget").isNotEmpty())) return@CommandSpec null
                val sb = StringBuilder("place")
                sb.append(" ").append("jigsaw " + v.v("pool") + " " + v.v("jigsawTarget") + " " + v.vOr("maxDepth", "7") + " ")
                if (v.v("x").isNotEmpty()) sb.append(" ").append(v.v("x"))
                sb.append(" ")
                if (v.v("y").isNotEmpty()) sb.append(" ").append(v.v("y"))
                sb.append(" ")
                if (v.v("z").isNotEmpty()) sb.append(" ").append(v.v("z"))
                return@CommandSpec sb.toString()
            }
            if (v.v("name").isNotEmpty()) {
                if (!(v.v("kind").isNotEmpty() && v.v("name").isNotEmpty())) return@CommandSpec null
                val sb = StringBuilder("place")
                sb.append(" ").append(v.v("kind") + " " + v.v("name") + " ")
                if (v.v("x").isNotEmpty()) sb.append(" ").append(v.v("x"))
                sb.append(" ")
                if (v.v("y").isNotEmpty()) sb.append(" ").append(v.v("y"))
                sb.append(" ")
                if (v.v("z").isNotEmpty()) sb.append(" ").append(v.v("z"))
                return@CommandSpec sb.toString()
            }
            return@CommandSpec null
        },

        CommandSpec(
            keyword = "playanimation",
            description = "让实体播放一次动画（行为包定义过的）",
            category = "实体与效果",
            aliases = listOf("播放动画"),
            params = listOf(
                CommandParam(
                    key = "entity",
                    label = "实体",
                    type = ParamType.SELECTOR,
                    defaultValue = "@e"
                ),
                CommandParam(
                    key = "animation",
                    label = "动画名",
                    defaultValue = "animation.armor_stand.attack_rot",
                    hint = "行为包里的动画名"
                ),
                CommandParam(
                    key = "nextState",
                    label = "下一状态",
                    required = false
                ),
                CommandParam(
                    key = "blendOutTime",
                    label = "淡出秒数",
                    type = ParamType.NUMBER,
                    required = false
                ),
                CommandParam(
                    key = "stopExpression",
                    label = "停止表达式",
                    required = false
                ),
                CommandParam(
                    key = "controller",
                    label = "控制器",
                    required = false
                ),
            )
        ) { v ->
            if (!(v.v("entity").isNotEmpty() && v.v("animation").isNotEmpty())) return@CommandSpec null
            val sb = StringBuilder("playanimation")
            sb.append(" ").append(v.v("entity") + " " + v.v("animation") + " ")
            if (v.v("nextState").isNotEmpty()) sb.append(" ").append(v.v("nextState"))
            sb.append(" ")
            if (v.v("blendOutTime").isNotEmpty()) sb.append(" ").append(v.v("blendOutTime"))
            sb.append(" ")
            if (v.v("stopExpression").isNotEmpty()) sb.append(" ").append(v.v("stopExpression"))
            sb.append(" ")
            if (v.v("controller").isNotEmpty()) sb.append(" ").append(v.v("controller"))
            return@CommandSpec sb.toString()
        },

        CommandSpec(
            keyword = "project",
            description = "Minecraft Editor 的项目工具",
            category = "玩家与游戏规则",
            aliases = listOf("编辑器项目"),
            params = listOf(
                CommandParam(
                    key = "exportType",
                    label = "导出类型",
                    defaultValue = "minecraft:project",
                    hint = "ExportTypes 枚举"
                ),
            )
        ) { v ->
            if (!(v.v("exportType").isNotEmpty())) return@CommandSpec null
            val sb = StringBuilder("project")
            sb.append(" ").append("export " + v.v("exportType"))
            return@CommandSpec sb.toString()
        },

        CommandSpec(
            keyword = "recipe",
            description = "解锁或锁定玩家的配方",
            category = "物品",
            aliases = listOf("配方", "解锁配方"),
            params = listOf(
                CommandParam(
                    key = "mode",
                    label = "操作",
                    type = ParamType.CHOICE,
                    defaultValue = "give",
                    choices = listOf("give", "take")
                ),
                CommandParam(
                    key = "player",
                    label = "玩家",
                    type = ParamType.SELECTOR,
                    defaultValue = "@s"
                ),
                CommandParam(
                    key = "recipe",
                    label = "配方",
                    required = false,
                    defaultValue = "\"*\"",
                    hint = "填 \"*\" 表示全部配方；也可填具体配方名"
                ),
            )
        ) { v ->
            if (v.v("recipe").isNotEmpty()) {
                if (!(v.v("mode").isNotEmpty() && v.v("player").isNotEmpty() && v.v("recipe").isNotEmpty())) return@CommandSpec null
                val sb = StringBuilder("recipe")
                sb.append(" ").append(v.v("mode") + " " + v.v("player") + " " + v.v("recipe"))
                return@CommandSpec sb.toString()
            }
            if (!(v.v("mode").isNotEmpty() && v.v("player").isNotEmpty())) return@CommandSpec null
            val sb = StringBuilder("recipe")
            sb.append(" ").append(v.v("mode") + " " + v.v("player") + " \"*\"")
            return@CommandSpec sb.toString()
        },

        CommandSpec(
            keyword = "reloadconfig",
            description = "重新加载变量、权限等配置文件",
            category = "玩家与游戏规则",
            aliases = listOf("重载配置"),
            params = emptyList()
        ) { v ->
            val sb = StringBuilder("reloadconfig")
            return@CommandSpec sb.toString()
        },

        CommandSpec(
            keyword = "reloadpacketlimitconfig",
            description = "重新加载 packetlimitconfig.json",
            category = "玩家与游戏规则",
            aliases = listOf("重载包限制配置"),
            params = emptyList()
        ) { v ->
            val sb = StringBuilder("reloadpacketlimitconfig")
            return@CommandSpec sb.toString()
        },

        CommandSpec(
            keyword = "save",
            description = "准备备份、查询备份状态或恢复存档",
            category = "世界与方块",
            aliases = listOf("存档备份"),
            params = listOf(
                CommandParam(
                    key = "mode",
                    label = "操作",
                    type = ParamType.CHOICE,
                    required = false,
                    hint = "留空 = 开始保存；hold = 挂起等待复制；query = 查询；resume = 恢复"
                ),
            )
        ) { v ->
            if (v.v("mode").isNotEmpty()) {
                if (!(v.v("mode").isNotEmpty())) return@CommandSpec null
                val sb = StringBuilder("save")
                sb.append(" ").append(v.v("mode"))
                return@CommandSpec sb.toString()
            }
            val sb = StringBuilder("save")
            return@CommandSpec sb.toString()
        },

        CommandSpec(
            keyword = "script",
            description = "GameTest 框架的脚本调试工具",
            category = "玩家与游戏规则",
            aliases = listOf("脚本调试"),
            params = listOf(
                CommandParam(
                    key = "group",
                    label = "类别",
                    type = ParamType.CHOICE,
                    defaultValue = "debugger",
                    choices = listOf("debugger", "profiler", "watchdog")
                ),
                CommandParam(
                    key = "action",
                    label = "操作",
                    defaultValue = "close",
                    hint = "debugger: close/connect/listen；profiler: start/stop；watchdog: exportstats"
                ),
                CommandParam(
                    key = "host",
                    label = "主机",
                    required = false
                ),
                CommandParam(
                    key = "port",
                    label = "端口",
                    type = ParamType.NUMBER,
                    required = false
                ),
            )
        ) { v ->
            if (v.vOr("group", "") == "debugger") {
                val sb = StringBuilder("script")
                sb.append(" ").append("debugger " + v.vOr("action", "close") + " ")
                if (v.v("host").isNotEmpty()) sb.append(" ").append(v.v("host"))
                sb.append(" ")
                if (v.v("port").isNotEmpty()) sb.append(" ").append(v.v("port"))
                return@CommandSpec sb.toString()
            }
            if (v.vOr("group", "") == "profiler") {
                val sb = StringBuilder("script")
                sb.append(" ").append("profiler " + v.vOr("action", "start"))
                return@CommandSpec sb.toString()
            }
            if (v.vOr("group", "") == "watchdog") {
                val sb = StringBuilder("script")
                sb.append(" ").append("watchdog exportstats")
                return@CommandSpec sb.toString()
            }
            return@CommandSpec null
        },

        CommandSpec(
            keyword = "scriptevent",
            description = "用一个 ID 和消息触发脚本事件",
            category = "玩家与游戏规则",
            aliases = listOf("脚本事件"),
            params = listOf(
                CommandParam(
                    key = "messageId",
                    label = "事件 ID",
                    defaultValue = "minecraft:example",
                    hint = "如 mypack:hello"
                ),
                CommandParam(
                    key = "message",
                    label = "消息内容",
                    type = ParamType.LONG_TEXT,
                    required = false
                ),
            )
        ) { v ->
            if (!(v.v("messageId").isNotEmpty())) return@CommandSpec null
            val sb = StringBuilder("scriptevent")
            sb.append(" ").append(v.v("messageId") + " ")
            if (v.v("message").isNotEmpty()) sb.append(" ").append(v.v("message"))
            return@CommandSpec sb.toString()
        },

        CommandSpec(
            keyword = "sendshowstoreoffer",
            description = "请求向玩家显示商店优惠",
            category = "玩家与游戏规则",
            aliases = listOf("商店优惠"),
            params = listOf(
                CommandParam(
                    key = "player",
                    label = "玩家",
                    type = ParamType.SELECTOR,
                    defaultValue = "@s"
                ),
                CommandParam(
                    key = "mode",
                    label = "方式",
                    type = ParamType.CHOICE,
                    defaultValue = "server",
                    choices = listOf("server", "offer")
                ),
                CommandParam(
                    key = "redirectType",
                    label = "跳转类型",
                    required = false,
                    hint = "offer 时填，RedirectLocation 枚举"
                ),
                CommandParam(
                    key = "offerId",
                    label = "优惠 ID",
                    required = false,
                    hint = "offer 时填"
                ),
            )
        ) { v ->
            if (v.vOr("mode", "") == "server") {
                if (!(v.v("player").isNotEmpty())) return@CommandSpec null
                val sb = StringBuilder("sendshowstoreoffer")
                sb.append(" ").append(v.v("player") + " server")
                return@CommandSpec sb.toString()
            }
            if (v.v("offerId").isNotEmpty()) {
                if (!(v.v("player").isNotEmpty() && v.v("redirectType").isNotEmpty() && v.v("offerId").isNotEmpty())) return@CommandSpec null
                val sb = StringBuilder("sendshowstoreoffer")
                sb.append(" ").append(v.v("player") + " " + v.v("redirectType") + " " + v.v("offerId"))
                return@CommandSpec sb.toString()
            }
            return@CommandSpec null
        },

        CommandSpec(
            keyword = "set_movement_authority",
            description = "设置玩家移动由客户端还是服务器校验",
            category = "玩家与游戏规则",
            aliases = listOf("移动校验"),
            params = listOf(
                CommandParam(
                    key = "authority",
                    label = "校验方",
                    type = ParamType.CHOICE,
                    defaultValue = "server",
                    choices = listOf("server", "client")
                ),
            )
        ) { v ->
            if (!(v.v("authority").isNotEmpty())) return@CommandSpec null
            val sb = StringBuilder("set_movement_authority")
            sb.append(" ").append(v.v("authority"))
            return@CommandSpec sb.toString()
        },

        CommandSpec(
            keyword = "setmaxplayers",
            description = "设置服务器允许加入的最大玩家数",
            category = "玩家与游戏规则",
            aliases = listOf("最大玩家数"),
            params = listOf(
                CommandParam(
                    key = "maxPlayers",
                    label = "最大人数",
                    type = ParamType.NUMBER,
                    defaultValue = "10"
                ),
            )
        ) { v ->
            if (!(v.v("maxPlayers").isNotEmpty())) return@CommandSpec null
            val sb = StringBuilder("setmaxplayers")
            sb.append(" ").append(v.v("maxPlayers"))
            return@CommandSpec sb.toString()
        },

        CommandSpec(
            keyword = "spawnpoint",
            description = "设置玩家的个人出生点（重生点）",
            category = "世界与方块",
            aliases = listOf("个人出生点", "设置重生点"),
            params = listOf(
                CommandParam(
                    key = "player",
                    label = "玩家",
                    type = ParamType.SELECTOR,
                    required = false,
                    defaultValue = "@s"
                ),
                CommandParam(
                    key = "x",
                    label = "X",
                    required = false,
                    defaultValue = "~"
                ),
                CommandParam(
                    key = "y",
                    label = "Y",
                    required = false,
                    defaultValue = "~"
                ),
                CommandParam(
                    key = "z",
                    label = "Z",
                    required = false,
                    defaultValue = "~"
                ),
            )
        ) { v ->
            if (v.v("player").isNotEmpty() && v.v("x").isNotEmpty() && v.v("y").isNotEmpty() && v.v("z").isNotEmpty()) {
                if (!(v.v("player").isNotEmpty() && v.v("x").isNotEmpty() && v.v("y").isNotEmpty() && v.v("z").isNotEmpty())) return@CommandSpec null
                val sb = StringBuilder("spawnpoint")
                sb.append(" ").append(v.v("player") + " " + v.v("x") + " " + v.v("y") + " " + v.v("z"))
                return@CommandSpec sb.toString()
            }
            if (v.v("player").isNotEmpty()) {
                if (!(v.v("player").isNotEmpty())) return@CommandSpec null
                val sb = StringBuilder("spawnpoint")
                sb.append(" ").append(v.v("player"))
                return@CommandSpec sb.toString()
            }
            if (v.v("x").isNotEmpty() && v.v("y").isNotEmpty() && v.v("z").isNotEmpty()) {
                if (!(v.v("x").isNotEmpty() && v.v("y").isNotEmpty() && v.v("z").isNotEmpty())) return@CommandSpec null
                val sb = StringBuilder("spawnpoint")
                sb.append(" ").append(v.v("x") + " " + v.v("y") + " " + v.v("z"))
                return@CommandSpec sb.toString()
            }
            val sb = StringBuilder("spawnpoint")
            return@CommandSpec sb.toString()
        },

        CommandSpec(
            keyword = "stop",
            description = "停止服务器",
            category = "玩家与游戏规则",
            aliases = listOf("停止服务器"),
            params = emptyList()
        ) { v ->
            val sb = StringBuilder("stop")
            return@CommandSpec sb.toString()
        },

        CommandSpec(
            keyword = "testforblock",
            description = "检测指定位置是不是某个方块（旧式写法，建议改用 execute if block）",
            category = "世界与方块",
            aliases = listOf("检测方块"),
            params = listOf(
                CommandParam(
                    key = "x",
                    label = "X",
                    defaultValue = "~"
                ),
                CommandParam(
                    key = "y",
                    label = "Y",
                    defaultValue = "~"
                ),
                CommandParam(
                    key = "z",
                    label = "Z",
                    defaultValue = "~"
                ),
                CommandParam(
                    key = "block",
                    label = "方块 ID",
                    suggestions = Suggest.blocks.map { it.id },
                    suggestionLabels = Suggest.blocks.map { it.name }
                ),
                CommandParam(
                    key = "states",
                    label = "方块状态",
                    required = false,
                    suggestions = listOf("[\"facing_direction\"=1]", "[\"facing_direction\"=0]", "[\"powered_bit\"=true]", "[\"open_bit\"=true]")
                ),
            )
        ) { v ->
            if (!(v.v("x").isNotEmpty() && v.v("y").isNotEmpty() && v.v("z").isNotEmpty() && v.v("block").isNotEmpty())) return@CommandSpec null
            val sb = StringBuilder("testforblock")
            sb.append(" ").append(v.v("x") + " " + v.v("y") + " " + v.v("z") + " " + v.v("block") + " ")
            if (v.v("states").isNotEmpty()) sb.append(" ").append(v.v("states"))
            return@CommandSpec sb.toString()
        },

        CommandSpec(
            keyword = "testforblocks",
            description = "检测两个区域的方块是否相同（旧式写法）",
            category = "世界与方块",
            aliases = listOf("检测区域方块"),
            params = listOf(
                CommandParam(
                    key = "x1",
                    label = "起点 X",
                    defaultValue = "~"
                ),
                CommandParam(
                    key = "y1",
                    label = "起点 Y",
                    defaultValue = "~"
                ),
                CommandParam(
                    key = "z1",
                    label = "起点 Z",
                    defaultValue = "~"
                ),
                CommandParam(
                    key = "x2",
                    label = "终点 X",
                    defaultValue = "~"
                ),
                CommandParam(
                    key = "y2",
                    label = "终点 Y",
                    defaultValue = "~"
                ),
                CommandParam(
                    key = "z2",
                    label = "终点 Z",
                    defaultValue = "~"
                ),
                CommandParam(
                    key = "dx",
                    label = "目标 X",
                    defaultValue = "~"
                ),
                CommandParam(
                    key = "dy",
                    label = "目标 Y",
                    defaultValue = "~"
                ),
                CommandParam(
                    key = "dz",
                    label = "目标 Z",
                    defaultValue = "~"
                ),
                CommandParam(
                    key = "maskMode",
                    label = "比较方式",
                    type = ParamType.CHOICE,
                    required = false,
                    defaultValue = "all",
                    choices = listOf("all", "masked")
                ),
            )
        ) { v ->
            if (!(v.v("x1").isNotEmpty() && v.v("y1").isNotEmpty() && v.v("z1").isNotEmpty() && v.v("x2").isNotEmpty() && v.v("y2").isNotEmpty() && v.v("z2").isNotEmpty() && v.v("dx").isNotEmpty() && v.v("dy").isNotEmpty() && v.v("dz").isNotEmpty())) return@CommandSpec null
            val sb = StringBuilder("testforblocks")
            sb.append(" ").append(v.v("x1") + " " + v.v("y1") + " " + v.v("z1") + " " + v.v("x2") + " " + v.v("y2") + " " + v.v("z2") + " " + v.v("dx") + " " + v.v("dy") + " " + v.v("dz") + " ")
            if (v.v("maskMode").isNotEmpty()) sb.append(" ").append(v.v("maskMode"))
            return@CommandSpec sb.toString()
        },

        CommandSpec(
            keyword = "toggledownfall",
            description = "在晴天与下雨之间切换",
            category = "世界与方块",
            aliases = listOf("切换天气"),
            params = emptyList()
        ) { v ->
            val sb = StringBuilder("toggledownfall")
            return@CommandSpec sb.toString()
        },

        CommandSpec(
            keyword = "transfer",
            description = "把玩家转移到另一台服务器",
            category = "玩家与游戏规则",
            aliases = listOf("转移服务器"),
            params = listOf(
                CommandParam(
                    key = "player",
                    label = "玩家标识",
                    defaultValue = "@s",
                    hint = "BE 写 pfid 或 MSA 标识"
                ),
                CommandParam(
                    key = "server",
                    label = "服务器地址",
                    defaultValue = "localhost"
                ),
                CommandParam(
                    key = "port",
                    label = "端口",
                    type = ParamType.NUMBER,
                    defaultValue = "19132"
                ),
            )
        ) { v ->
            if (!(v.v("player").isNotEmpty() && v.v("server").isNotEmpty() && v.v("port").isNotEmpty())) return@CommandSpec null
            val sb = StringBuilder("transfer")
            sb.append(" ").append(v.v("player") + " " + v.v("server") + " " + v.v("port"))
            return@CommandSpec sb.toString()
        },

        CommandSpec(
            keyword = "worldbuilder",
            description = "切换世界建造者模式（可编辑受限方块）",
            category = "世界与方块",
            aliases = listOf("世界建造者", "编辑受限方块"),
            params = emptyList()
        ) { v ->
            val sb = StringBuilder("worldbuilder")
            return@CommandSpec sb.toString()
        },
    )

    /** 按关键字找一条命令规格。 */
    fun find(keyword: String): CommandSpec? = all.firstOrNull { it.keyword == keyword }

    /**
     * 把用户输入的文本匹配到一条命令规格（**宽松版，供展示用**）。
     *
     * 难点在于命令关键字**有长有短**：
     *
     *  * `scoreboard objectives add` 与 `scoreboard objectives setdisplay` 共享前两个词，
     *    只按"前缀相同"匹配会两个都命中，结果取决于谁在列表里靠前 —— 典型"有时对有时错"；
     *  * `scoreboard players` 这种"多词命令打了一半"的输入也必须匹配上，
     *    否则玩家刚打完第一个词就什么提示都没有（实测踩过）；
     *  * `give` 这种单词关键字后面直接跟参数。
     *
     * 规则是"**按词边界双向匹配，取最长**"。
     * ⚠️ 本函数只回答"像哪条命令"，**不区分"命令名打完没有"**；
     * 需要那个判断请用 [CommandCompletion]（它按 token 逐个比对，更严格）。
     */
    fun match(input: String): CommandSpec? = match(input, input)

    /**
     * 同上，但把"用来匹配命令名的文本"与"原始输入"分开。
     *
     * 补全时要传**只有命令名那一段**的文本：玩家打到 `@e[type=zombie` 时，
     * 原始输入里那个 token 是 `@e[type=zombie` 而不是 `@e`，
     * 直接拿去匹配英文关键字会一条都命中不了。
     */
    fun match(input: String, nameText: String): CommandSpec? {
        val text = nameText.trim().removePrefix("/").trim()
        if (text.isEmpty()) return null

        // 第一优先：输入**已经包含**一条完整命令名。
        // 要求"关键字后面是空格"，不能只是字符串前缀 —— 否则
        // `scoreboard objectives` 会被 `scoreboard objectives add` 抢走，
        // 把"还没打完的命令名"误判成"已经打完的命令 + 参数"。
        all.filter { spec ->
            text == spec.keyword || text.startsWith(spec.keyword + " ")
        }.maxByOrNull { it.keyword.length }?.let { return it }

        // 第一·五优先：**中文命令名**。
        // 玩家会打 `/粒子` 而不是 `/particle`，这条必须有（用户实测报过打中文没反应）。
        // 只按"别名本身"或"别名 + 空格"匹配，不做前缀 ——
        // 中文没有词边界，前缀匹配会让 `/队伍` 与 `/队伍 创建` 互相抢。
        // 同分别名（`tp` 与 `teleport` 都是"传送"）时取**更短的关键字**：
        // 玩家基本都用短名。按长度升序配 maxWith，取到的就是最短的那个。
        all.filter { spec -> spec.aliases.any { text == it || text.startsWith(it + " ") } }
            .maxWithOrNull(compareBy({ spec -> spec.aliases.maxOf { it.length } }, { spec -> spec.keyword.length }))
            ?.let { return it }

        // 第二优先：单个词的前缀，用于"还没打完就出提示"（`giv` → `give`）。
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
     * 中文没有词边界，所以这里用**包含**匹配：`/粒子` 能命中 `particle`，
     * `/计分板` 能命中所有计分板子命令。返回按别名长度升序 ——
     * 越短的别名越可能是玩家的意图（`/队伍` 应该先给"队伍 创建"这类短名）。
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
