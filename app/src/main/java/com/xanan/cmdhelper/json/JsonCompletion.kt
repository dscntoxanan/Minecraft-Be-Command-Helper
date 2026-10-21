package com.xanan.cmdhelper.json

/**
 * JSON 组件的**逐层补全**引擎。
 *
 * ── 它解决什么 ──────────────────────────────────────────────────────────────
 * 玩家在写 `titleraw` 这类命令时，JSON 参数是最难的部分：括号配不配平、
 * 里层该写什么、键名记不住。所以补全要**跟着括号层级走**：
 *
 * ```
 * /titleraw @p actionbar {          → 提示 {"rawtext":[]}，点击后光标落在 [] 内
 * /titleraw @p actionbar {"rawtext":[   → 提示三种组件骨架
 *                                        {"text":"请输入文本"}
 *                                        {"selector":"选择器"}
 *                                        {"score":{"name":"选择器","objective":"计分板名称"}}
 * ```
 *
 * ── 实现方式 ────────────────────────────────────────────────────────────────
 * 从**光标往前扫**，维护一个括号栈（`{`/`[`）与"当前对象里最后见到的键"，
 * 由此判断光标处在哪一层、这一层该填什么。刻意不做完整 JSON 语法分析 ——
 * 编辑中的 JSON 本来就是不完整的（括号没闭合），严格解析必然失败。
 *
 * 纯函数、无 Android 依赖，配 [JsonCompletionTest] 单测。
 */
object JsonCompletion {

    /** 光标所处的上下文。 */
    enum class Cursor {
        /** 在一个对象 `{}` 里。 */
        OBJECT,

        /** 在一个数组 `[]` 里。 */
        ARRAY
    }

    /**
     * 一条 JSON 补全候选。
     *
     * @param insert   真正插入的文本
     * @param label    界面显示的短标签
     * @param detail   一句话说明
     * @param caretIn  **光标插入后要落在 insert 里的第几个字符处**；
     *                 默认 -1 表示落在末尾。用于"光标停在 [] 内"这种需求。
     */
    data class Suggestion(
        val insert: String,
        val label: String,
        val detail: String = "",
        val caretIn: Int = -1,
    ) {
        /** 插入后光标的绝对位置。 */
        fun caretAfter(insertStart: Int): Int =
            insertStart + (if (caretIn >= 0) caretIn.coerceAtMost(insert.length) else insert.length)
    }

    /** 光标位置的上下文信息。 */
    data class Context(
        val cursor: Cursor,
        /**
         * **当前容器的归属键**：正在写的这个 `{}` / `[]` 是挂在哪个键下的。
         * 例如 `{"score":{` → `score`；`{"rawtext":[` → `rawtext`。
         *
         * ⚠️ 它和 [lastSeenKey] 是**两个不同的东西**，早先混成一个变量，
         * 结果数组里放进组件后"这是不是 rawtext 数组"判断失效、逗号补错。
         */
        val ownerKey: String,
        /**
         * **当前对象里最后一个键名**（用于判断"已经写过哪些键"，
         * 例如 score 里已经写了 name 就该接着补 objective）。
         */
        val lastSeenKey: String,
        /**
         * **最外层未闭合数组的父键**（例如 `{"rawtext":[` 的 `rawtext`）。
         * 用于判断这个数组是不是 rawtext 数组。
         */
        val arrayParentKey: String,
        /** 光标前紧邻的、已开启但没闭合的对象数（>0 说明有 `{` 还没闭上）。 */
        val openObjects: Int,
        /** 光标前紧邻的、已开启但没闭合的数组数。 */
        val openArrays: Int,
        /** 括号栈（从外到内），元素是 '{' 或 '['。 */
        val stack: List<Char>,
    )

    // ── JSON 组件骨架 ────────────────────────────────────────────────────────
    // 占位文本刻意用中文描述而不是具体值（用户要求）：
    // 之前 score 里预填了 `@s`，玩家还得手动改成选择器；直接写"选择器"更明确。
    private const val PLACEHOLDER_TEXT = "请输入文本"
    private const val PLACEHOLDER_SELECTOR = "选择器"
    private const val PLACEHOLDER_OBJECTIVE = "计分板名称"

    private const val TEXT_OBJ = "{\"text\":\"$PLACEHOLDER_TEXT\"}"
    private const val SELECTOR_OBJ = "{\"selector\":\"$PLACEHOLDER_SELECTOR\"}"
    private const val SCORE_OBJ = "{\"score\":{\"name\":\"$PLACEHOLDER_SELECTOR\",\"objective\":\"$PLACEHOLDER_OBJECTIVE\"}}"

    /** 未跨度的 `{"rawtext":[]}` 骨架（不含玩家已打出的那个 `{`）。 */
    private const val RAWTEXT_TAIL = "\"rawtext\":[]}"

    /**
     * `give` / `replaceitem` 的组件对象所属的键。
     *
     * 这两条命令的组件参数在命令里是 `{"components":{…}}` 结构的一部分，
     * 所以"进入物品组件层"的判断就是"当前对象的归属键是 components"。
     */
    private const val COMPONENTS_KEY = "components"

    /**
     * 分析光标左侧的文本，得到上下文。
     *
     * 实现用一个**帧栈**：每个 `{` 或 `[` 压一帧，帧里记着"这个容器属于哪个键"。
     * 这样嵌套时不会串味 —— 早期版本用单个 `lastKey` 变量，放进组件后
     * `lastKey` 被子键（`text`、`name`…）覆盖，于是"这个数组是不是 rawtext"
     * 就判断错了，逗号补不补也跟着错。
     *
     * @param before 光标**之前**的整段文本（调用方负责裁剪）
     */
    fun analyze(before: String): Context {
        /** 一帧：'{' 或 '['，以及开启它的那个键（顶层容器为空串）。 */
        data class Frame(val bracket: Char, val ownerKey: String)

        val stack = ArrayList<Frame>()
        var pendingKey = ""
        // 当前对象里最后见到的键（同一层内有效）。
        var lastSeenKey = ""
        var inString = false
        var escaped = false

        var i = 0
        while (i < before.length) {
            val ch = before[i]
            when {
                escaped -> escaped = false

                ch == '\\' && inString -> escaped = true

                // ⚠️ 顺序很重要：必须先判断"在字符串里"。
                // 文本组件的内容经常含花括号（`{"text":"{ 这不是括号 }"}`），
                // 如果先判断 `ch == '{'` 就会把它当结构，括号栈与 openObjects 全错。
                inString -> Unit

                ch == '"' -> {
                    // 字符串开始：读出整段，再往后看是不是紧跟冒号（是则它是键）
                    val start = i + 1
                    var j = start
                    var esc = false
                    while (j < before.length) {
                        val c = before[j]
                        if (esc) {
                            esc = false
                        } else if (c == '\\') {
                            esc = true
                        } else if (c == '"') {
                            break
                        }
                        j++
                    }
                    var k = j + 1
                    while (k < before.length && before[k] == ' ') k++
                    if (k < before.length && before[k] == ':') {
                        // 这是一个键 —— 记下来，等它后面的容器开启时归属给它
                        pendingKey = before.substring(start, minOf(j, before.length))
                        lastSeenKey = pendingKey
                    }
                    i = j
                    inString = false
                }

                ch == '{' || ch == '[' -> {
                    stack.add(Frame(ch, pendingKey))
                    // 进入新容器：内部的"最后见到的键"从现在开始重新计。
                    // `{"score":{` → score 对象里还没写过任何键，所以是空串；
                    // "这是 score 的对象"这件事由 frame.ownerKey 记住，不靠 lastSeenKey。
                    lastSeenKey = ""
                    pendingKey = ""
                }

                ch == '}' || ch == ']' -> if (stack.isNotEmpty()) {
                    val popped = stack.removeAt(stack.size - 1)
                    // 退出容器：回到上一层，而上一层"最后见到的键"就是刚关掉的那个键。
                    // ⚠️ 早先写成"用进入时保存的外层值恢复"，那是错的：
                    // `{"rawtext":[{"text":"a","selector":"@s"}` 会恢复成 `rawtext`，
                    // 而正确答案是 `selector`（实测被测试抓到）。
                    if (popped.ownerKey.isNotEmpty()) lastSeenKey = popped.ownerKey
                }

                else -> Unit
            }
            i++
        }

        val top = stack.lastOrNull()
        return Context(
            cursor = if (top?.bracket == '[') Cursor.ARRAY else Cursor.OBJECT,
            ownerKey = top?.ownerKey.orEmpty(),
            lastSeenKey = lastSeenKey,
            arrayParentKey = stack.firstOrNull { it.bracket == '[' }?.ownerKey.orEmpty(),
            openObjects = stack.count { it.bracket == '{' },
            openArrays = stack.count { it.bracket == '[' },
            stack = stack.map { it.bracket },
        )
    }

    /**
     * 按上下文给出候选。
     *
     * @param before 光标之前的文本
     * @param alreadyOpenBrace 光标前紧邻的是一个**尚未成对的 `{`**（玩家刚打了左花括号）。
     *        这种情况插入的是"花括号里面的内容"，因为 `{` 已经由玩家打出来了 ——
     *        用户明确要求"`{` 也会显示"，指的就是不能把玩家打的括号吃掉。
     * @param paramKey 正在填的参数键（来自 [CommandParam.key]）。用于区分
     *        "物品组件层"与"文本组件层" —— 光靠括号栈分不出来，见下面第 0 步。
     */
    fun suggest(
        before: String,
        alreadyOpenBrace: Boolean,
        paramKey: String = "",
    ): List<Suggestion> {
        val ctx = analyze(before)
        val last = ctx.stack.lastOrNull()

        // 0) 先按"正在填的参数"判定**物品组件层**。
        //
        //    ⚠️ 这一步必须放在最前面，而且不能只靠括号栈：
        //    give / replaceitem 的组件 JSON 是裸写的（`@s x 1 0 {`），
        //    玩家打 `{` 的那一刻键名还没出现，[analyze] 得到的 ownerKey 是空的，
        //    于是会被下面"刚打花括号 → 给 rawtext 骨架"那条分支抢走
        //    （实测踩过：在 give 里打 `{` 弹出的是 {"rawtext":[]}）。
        if (isItemComponentsLayer(paramKey, ctx)) {
            return componentKeySuggestions(needsComma(before, ctx))
        }

        // ① 刚打出 `{`：给最外层骨架。
        //    插入的是 `"rawtext":[]}`（玩家打的 `{` 保留），光标要落在 `[` 与 `]` 之间 ——
        //    也就是 `"rawtext":[` 的长度 12。
        //    ⚠️ 这里最容易差 1：位置算错光标就会跑到 `:` 后面或 `]` 后面，
        //    括号层级随之变化，下一层候选变成"对象键"而不是"数组里的组件"（实测踩过）。
        //    所以用 `indexOf(']')` 直接算，别手写常量。
        // ① 刚打出 `{`：看它是在哪儿开的。
        //    ⚠️ 不能一律给 `{"rawtext":[]}` —— 在**数组里**打 `{` 是"开始一个新组件"，
        //    再提示 rawtext 骨架就驴唇不对马嘴（实测踩过）。
        //    ⚠️ 也不能抢在**物品组件层**前面：`/give @s x 1 0 {` 里那个 `{` 开的是
        //    组件对象，该列 item_name / enchantments，不是 rawtext（刚踩过）。
        if (alreadyOpenBrace) {
            val outer = analyze(before.trimEnd().dropLast(1))
            if (outer.ownerKey == COMPONENTS_KEY) {
                return componentKeySuggestions(comma = false)
            }
            if (outer.cursor == Cursor.ARRAY) {
                // 数组里的新组件对象：直接给三种组件的键，
                // 插入文本以 `}` 收尾，把这个 `{` 关上，光标落在 `}` 之后。
                return componentSuggestions(comma = false)
            }
            // 最外层（或普通对象里）：给 rawtext 骨架
            val insert = RAWTEXT_TAIL
            return listOf(
                Suggestion(
                    insert = insert,
                    label = "{\"rawtext\":[]}",
                    detail = "文本组件最外层，光标会落在 [] 里",
                    caretIn = insert.indexOf(']'),
                ),
            )
        }

        if (last == null) {
            // 没有任何未闭合容器（理论上不该走到这里）
            val insert = "{$RAWTEXT_TAIL"
            return listOf(
                Suggestion(insert, insert, "文本组件最外层", caretIn = insert.indexOf(']')),
            )
        }

        // ② 在数组里（rawtext 数组）：给三种组件骨架。
        //    逗号：数组里已经放了东西就要先补一个逗号，否则下一项会拼成
        //    `{…}{…}` 这种非法 JSON（用户明确要求"自动加逗号"）。
        if (last == '[') return componentSuggestions(needsComma(before, ctx))

        // ③ 在对象里：按"这个对象的父键"决定该填什么。
        //    对象里的键同理：已经写过键就要补逗号。
        return objectKeys(ctx, needsComma(before, ctx))
    }

    /**
     * 光标处是不是"已经有一项、需要先补逗号"。
     *
     * 判断依据：从光标往前跳过空白，如果紧邻的是**一个已经写完的值**
     * （`}`、`]`、`"`、数字、`e`/`l` 结尾的 true/false/null），
     * 而**不是** `{`、`[`、`,`、`:`，说明这一层已经装了一项，下一项前必须加逗号。
     *
     * 不直接看"有没有逗号"是因为：玩家可能手打了一半，而我们要的结果是
     * "语法上需不需要"，不是"文本上有没有"。
     */
    private fun needsComma(before: String, ctx: Context): Boolean {
        if (ctx.stack.isEmpty()) return false
        val trimmed = before.trimEnd()
        val lastChar = trimmed.lastOrNull() ?: return false
        return when (lastChar) {
            '{', '[', ',', ':' -> false
            else -> true
        }
    }

    /**
     * 一个组件**骨架写完之后**，光标该落在哪里。
     *
     * 规则：落在这个组件对象的 `}` **之后**，这样层级就回到数组层，
     * 面板会继续提示"下一个组件"，玩家可以一路连着往下补。
     *
     * 两个例外（用户明确要求的）：
     *  * `{@code {"rawtext":[]}}` —— 里面有数组，光标要落在 `[]` **里**；
     *  * 普通组件 —— 光标也可以落在"最后一个值"里方便打字，所以用**最后一个**
     *    `:"` 而不是第一个（`score` 里有 name/objective 两个，用第一个会插进
     *    `"选择器"` 中间，面板就会显示成对象内解析而不是组件可选子键 —— 实测踩过）。
     */
    private fun caretAfterSkeleton(obj: String): Int {
        // 有嵌套的 `[]` → 落在方括号里（`{"rawtext":[]}` 就是这种情况）
        val open = obj.indexOf('[')
        if (open >= 0) {
            val close = obj.indexOf(']', open)
            if (close > open) return close
        }
        // 普通组件 → 落在整个对象的 `}` 之后，回到数组层
        val lastBrace = obj.lastIndexOf('}')
        if (lastBrace >= 0) return lastBrace + 1
        // 兜底：落在最后一个 `:"` 之后（方便直接打字）
        val at = obj.lastIndexOf(":\"")
        return if (at >= 0) at + 2 else obj.length
    }

    /** rawtext 数组里的三种组件骨架。 */
    private fun componentSuggestions(comma: Boolean): List<Suggestion> {
        val prefix = if (comma) "," else ""
        return listOf(
            Suggestion(
                insert = "$prefix$TEXT_OBJ",
                label = TEXT_OBJ,
                detail = "纯文本",
                caretIn = prefix.length + caretAfterSkeleton(TEXT_OBJ),
            ),
            Suggestion(
                insert = "$prefix$SELECTOR_OBJ",
                label = SELECTOR_OBJ,
                detail = "玩家名 / 目标选择器",
                caretIn = prefix.length + caretAfterSkeleton(SELECTOR_OBJ),
            ),
            Suggestion(
                insert = "$prefix$SCORE_OBJ",
                label = "{\"score\":{…}}",
                detail = "显示某个计分板的分数（name 填选择器、objective 填计分板名）",
                caretIn = prefix.length + caretAfterSkeleton(SCORE_OBJ),
            ),
        )
    }

    /** 对象内部该填的键。 */
    private fun objectKeys(ctx: Context, comma: Boolean): List<Suggestion> {
        val prefix = if (comma) "," else ""

        fun key(insert: String, label: String, detail: String, valueMarker: String): Suggestion {
            val full = prefix + insert
            // 光标落在值的引号之后（或冒号之后），玩家直接打字
            val marker = valueMarker
            val at = full.indexOf(marker)
            return Suggestion(full, label, detail, caretIn = if (at >= 0) at + marker.length else full.length)
        }

        // 用 ownerKey 判断"当前在哪个对象里"，而不是 lastSeenKey：
        // lastSeenKey 会随写过的键变化，score 里写过 name 之后就变成 "name"，
        // 靠它判断层级会错。用户报过"score 之后显示的是对象解析、看不到子键"。
        return when (ctx.ownerKey) {
            // give / replaceitem 的组件对象：给组件名候选。
            // 结构是 {"components":{"item_name":"…","enchantments":[…]}}，
            // 所以进到这一层就该列组件，而不是列 text/selector 那套文本组件键。
            COMPONENTS_KEY -> componentKeySuggestions(comma)

            // score 对象：刚进来给 name + objective；写过 name 就只给 objective
            "score" -> if (ctx.lastSeenKey == "name") {
                listOf(
                    key("\"objective\":\"$PLACEHOLDER_OBJECTIVE\"", "\"objective\"", "计分板目标名", "\":\""),
                )
            } else {
                listOf(
                    key("\"name\":\"$PLACEHOLDER_SELECTOR\"", "\"name\"", "玩家名或选择器", "\":\""),
                    key("\"objective\":\"$PLACEHOLDER_OBJECTIVE\"", "\"objective\"", "计分板目标名", "\":\""),
                )
            }

            // 其它情况都是"组件对象内部"：给能构成一个文本组件的键。
            // 这里刻意**不给** color / bold 这类修饰键 ——
            // 它们对整合包作者有用，但对普通玩家是噪音，而且现在还没做嵌套支持，
            // 给出来会让"看不到该填的子键"这个问题更严重。
            else -> listOf(
                key("\"text\":\"$PLACEHOLDER_TEXT\"", "\"text\"", "纯文本", "\":\""),
                key("\"selector\":\"$PLACEHOLDER_SELECTOR\"", "\"selector\"", "玩家名 / 目标选择器", "\":\""),
                key(
                    "\"score\":{\"name\":\"$PLACEHOLDER_SELECTOR\",\"objective\":\"$PLACEHOLDER_OBJECTIVE\"}",
                    "\"score\"",
                    "计分板分数",
                    "\":\"",
                ),
            )
        }
    }

    /**
     * 判断光标此刻是不是在**物品组件层**（`give` / `replaceitem` 的组件 JSON 里）。
     *
     * 两种情况都算：
     *  1. 正在填的参数就叫 `components`（give/replaceitem 的组件参数）——
     *     这时玩家还没写键名，靠括号栈判断不出来，只能靠参数名；
     *  2. 括号栈里已经出现了 `{"components":{…}` 这一层（玩家手写了完整结构）。
     */
    private fun isItemComponentsLayer(paramKey: String, ctx: Context): Boolean {
        if (paramKey == COMPONENTS_KEY) return true
        if (ctx.ownerKey == COMPONENTS_KEY) return true
        if (ctx.arrayParentKey == COMPONENTS_KEY) return true
        // 玩家可能写成 {"minecraft:components":{…}} 之类；不匹配，避免误判。
        return false
    }

    /**
     * 物品组件层的候选（`give` / `replaceitem` 的组件对象）。
     *
     * 插入的是一整段可用的键值对，例如 `"item_name":"<名称>"`；
     * 光标落在模板的第一个 `<占位>` 里，玩家直接打字就能替换。
     */
    private fun componentKeySuggestions(comma: Boolean): List<Suggestion> {
        val prefix = if (comma) "," else ""
        return ComponentLibrary.components.map { c ->
            // 模板本身**已经含键名**（`"item_name":"<名称>"`），不要再拼一次 id ——
            // 拼了会得到 `"item_name":"item_name":"<名称>"` 这种坏 JSON（实测踩过）。
            Suggestion(
                insert = prefix + c.template,
                label = c.name,
                detail = c.detail,
                caretIn = caretInTemplate(prefix, c),
            )
        }
    }

    /**
     * 组件模板里光标的落点：第一个 `<占位>` 的**内容起始处**。
     *
     * 没有占位符（例如 `"keep_on_death":{}`）时落在整个模板末尾，
     * 这样连续补全时逗号判断仍然正确。
     */
    private fun caretInTemplate(prefix: String, component: ComponentLibrary.Component): Int {
        val at = component.template.indexOf('<')
        val inside = component.template.indexOf('>', at + 1)
        return if (at >= 0 && inside > at) {
            prefix.length + at + 1
        } else {
            prefix.length + component.template.length
        }
    }

    /**
     * 判断光标前紧邻的是不是一个"尚未配对的 `{`"。
     *
     * 只看光标前最后一个非空字符是否为 `{`，并且此刻栈顶确实是它。
     */
    fun endsWithOpenBrace(before: String): Boolean {
        val trimmed = before.trimEnd()
        if (!trimmed.endsWith("{")) return false
        val ctx = analyze(trimmed)
        return ctx.stack.lastOrNull() == '{'
    }
}
