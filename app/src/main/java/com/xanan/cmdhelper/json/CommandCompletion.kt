package com.xanan.cmdhelper.json

/**
 * 自动补全引擎。
 *
 * ── 它解决什么 ──────────────────────────────────────────────────────────────
 * 玩家在面板输入框里打字时，按**当前光标位置**推断"下一步该填什么"，给出候选：
 *
 * ```
 * 输入 "/"              → 列出全部命令
 * 输入 "/g"             → 列出 give / gamemode / gamerule
 * 输入 "/give "         → 该填目标选择器了 → 列出 @s @a @p @r @e …
 * 输入 "/give @s "      → 该填物品 ID 了   → 列出 diamond_sword、stone …
 * 输入 "/titleraw @a title set " → 该填 JSON 组件 → 列出可直接用的 JSON 骨架
 * ```
 *
 * ── 设计取舍 ────────────────────────────────────────────────────────────────
 *  * **纯函数、无 Android 依赖**：全部逻辑放在这里，配 [CommandCompletionTest] 单测。
 *    补全这种东西"看着对、边界全错"，靠手点是测不完的（试过）。
 *  * **不追求完整命令解析器**：只做到"识别命令 + 数到第几个参数"。
 *    基岩版命令语法（尤其是 JSON 参数与 `execute` 子命令）真要做完整解析器，
 *    工作量远超收益，而且解析错了反而让补全更迷惑。
 *  * 分词**尊重 JSON**：`{...}` 与引号内部的空格不算分隔符 ——
 *    否则 JSON 组件会被切成十几个 token，参数序号全乱。
 */
object CommandCompletion {

    /** 补全阶段。 */
    enum class Stage {
        /** 还在打命令名（或还没开始打）。 */
        COMMAND_NAME,

        /** 命令名已确定，正在填第 N 个参数。 */
        ARGUMENT,

        /** 命令名不认识，也没有能继续补全的候选。 */
        UNKNOWN
    }

    /** 一条补全候选。 */
    data class Item(
        /** 点击后真正插入的文本。 */
        val value: String,
        /** 界面上显示的短标签（默认与 value 相同）。 */
        val label: String = value,
        /** 一句话说明，帮玩家理解这个值是干什么的（可为空）。 */
        val detail: String = "",
        /**
         * 插入后光标要落在 [value] 里的第几个字符处；-1（默认）表示落在末尾。
         *
         * JSON 补全靠它实现"光标停在 `[]` 内""落在值的引号里"这类行为。
         */
        val caretOffset: Int = -1,
        /**
         * 插入后**不要**自动补空格。
         *
         * 默认行为是补一个空格（命令行参数之间需要），但 JSON 骨架里
         * 补空格会把 JSON 拼坏（`{"text":"a"} {"text":"b"}` 不是合法 JSON）。
         * 用户明确报过"选一个子键后自动打了个空格，这不对"。
         */
        val noSpace: Boolean = false,
    )

    /**
     * 一次补全请求的结果。
     *
     * @param stage            当前阶段
     * @param items            候选列表（可能为空）
     * @param replaceStart     点击候补时，要替换掉的原文本起始下标（绝对下标）
     * @param replaceEnd       要替换掉的原文本结束下标（不含）
     * @param matchedCommand   已识别的命令（未识别为 null）
     * @param currentParamIndex 正在填第几个参数（从 0 开始；命令名阶段为 -1）
     * @param prompt           给玩家看的一句提示，例如"选择目标"
     * @param placeholder      没有候选、需要玩家**自己输入**时的引导文案，
     *                         例如整数参数给"请输入整数"。
     *                         这类参数不适合给候选：数量填多少完全看玩家要做什么，
     *                         列一排 1/2/5/10 反而像"只有这几个能选"。
     */
    data class Result(
        val stage: Stage,
        val items: List<Item> = emptyList(),
        val replaceStart: Int = 0,
        val replaceEnd: Int = 0,
        val matchedCommand: CommandSpec? = null,
        val currentParamIndex: Int = -1,
        val prompt: String = "",
        val placeholder: String = "",
        /**
         * 列表被截断时的说明，例如"还有 1471 条，继续输入可缩小范围"。
         *
         * 为什么要截断：音效有 1671 条，一条一个 View，**全渲染会把面板卡死**。
         * 截断后必须明确告诉玩家"不是只有这些"，否则他会以为词表就这么点。
         */
        val truncatedNote: String = "",
        /**
         * 插入候选时要补在**值前面**的文本（目前只有空格）。
         *
         * 为什么需要这个字段：站长报过「手打 `/give` 后直接点 `@s` 不会自动加空格」。
         * `/give` 后面没打空格时只有一个 token，替换区间正好盖住命令名，
         * 插进去就成了 `@s` 紧贴 `give` → `/give@s`，游戏里是无效命令。
         *
         * 补在**后面**的那个空格由 [apply] 的 `suffix` 负责；
         * 这个补在**前面**，两者互不干扰。
         */
        val prefix: String = ""
    ) {
        val isEmpty: Boolean get() = items.isEmpty()
    }

    /** 一个词在原始输入里的位置。 */
    private data class Token(val text: String, val start: Int, val end: Int)

    /**
     * 主入口。
     *
     * @param input  输入框里的完整文本
     * @param cursor 光标位置（多数调用方传 input.length）
     */
    fun complete(input: String, cursor: Int = input.length): Result {
        if (input.isBlank()) return Result(Stage.COMMAND_NAME)

        // 名字用 caret 而不是 cursor：避免与参数同名造成"自己初始化自己"的歧义。
        val caret = cursor.coerceIn(0, input.length)
        // 只对光标之前的内容做补全 —— 玩家把光标移到中间改参数时，
        // 应该补的是"光标左侧那条命令"的下一个参数，而不是整行的最后一个参数。
        val head = input.substring(0, caret)
        val tokens = tokenize(head)
        if (tokens.isEmpty()) return Result(Stage.COMMAND_NAME)

        val last = tokens.last()
        val trailingSpace = last.text.isEmpty() && tokens.size > 1
        val atCommandName = tokens.size == 1 && !trailingSpace

        return if (atCommandName) {
            completeCommandName(input, tokens)
        } else {
            completeArgument(input, caret, tokens, trailingSpace)
        }
    }

    /**
     * 把连续空格压成一个。
     *
     * 现在分词本身已经能处理连续空格（见 [tokenize]），这个函数保留是给
     * 将来需要"按规范化后的文本算下标"的场景用的；目前 complete() 已不再调用它。
     */
    private fun normalizeSpaces(text: String): String {
        val sb = StringBuilder(text.length)
        var lastWasSpace = false
        text.forEach { ch ->
            if (ch == ' ') {
                if (!lastWasSpace) sb.append(ch)
                lastWasSpace = true
            } else {
                sb.append(ch)
                lastWasSpace = false
            }
        }
        return sb.toString()
    }

    // =======================================================================
    // 命令名阶段
    // =======================================================================

    private fun completeCommandName(input: String, tokens: List<Token>): Result {
        // 已输入的整段命令名（可能跨多个词，如 `scoreboard players`）。
        // 用 join 而不是取第一个 token —— 多词命令的续写必须看整段。
        val typed = tokens.joinToString(" ") { it.text }.removePrefix("/").trim()
        // 替换范围：从第一个 token 的"命令名起点"（跳过 `/`）到最后一个 token 的结尾。
        // 点击候选时替换掉整段，这样 `scoreboard players` → `scoreboard players set`
        // 不会拼成 `scoreboard scoreboard players set`。
        val rawFirst = tokens.first().text
        val slashOffset = if (rawFirst.startsWith("/")) 1 else 0
        val replaceStart = tokens.first().start + slashOffset
        val replaceEnd = tokens.last().end

        // 已经打完一整条命令名（含多词命令），只是后面还没加空格。
        // 例：输入 "scoreboard players set" → 直接按这条命令进入参数阶段，
        // 而不是把 "set" 当成某个更长的命令名的中间态。
        val exact = if (typed.isNotEmpty()) {
            CommandLibrary.all
                .filter { it.keyword == typed || it.aliases.any { alias -> alias == typed } }
                .maxByOrNull { it.keyword.length }
        } else {
            null
        }
        if (exact != null) {
            // ── 命令族：普通版 / raw 版先让用户选一次 ───────────────────────────
            //
            // 站长报的：手打 `/title` 会**直接进入 title 的参数阶段**，没有机会选
            // titleraw。这两条名字只差三个字母、用途却不同（一个纯文本、一个支持
            // JSON 上色），直接进去等于替他做了决定 —— 他多半想要的是能上色的那个，
            // 却要填完参数才发现生成出来的标题没有颜色。
            //
            // 判定用 **keyword 精确相等**而不是别名：玩家打「标题」时意图已经明确，
            // 再问一遍是噪音。只在"打的就是基准关键字"时介入。
            //
            // 选完（无论选哪个）都会带一个尾随空格，所以下一次 complete 走的是
            // completeArgument 的 trailingSpace 分支，**不会再次弹这个选择**。
            val family = if (typed == exact.keyword) familyChoices(exact) else emptyList()
            if (family.size > 1) {
                return Result(
                    stage = Stage.COMMAND_NAME,
                    items = family.map {
                        Item(
                            value = it.keyword,
                            label = if (it.variantNote.isNotEmpty()) {
                                "${it.keyword}　${it.variantNote}"
                            } else {
                                it.keyword
                            },
                            detail = "",
                        )
                    },
                    replaceStart = replaceStart,
                    replaceEnd = replaceEnd,
                    // ⚠️ 这里**必须**是空串：typed 本身就是完整关键字，
                    // COMMAND_NAME 分支默认会补一个前导空格，那会拼成 `/ title`
                    // （斜杠和命令名之间多一个空格），命令直接报废。
                    prefix = "",
                    prompt = "「${exact.keyword}」有两种写法，选一个",
                )
            }

            val firstParam = exact.params.firstOrNull()
            val all = firstParam?.let { suggestionsFor(exact, 0, "") } ?: emptyList()
            // ── 命令名与参数之间必须留一个空格 ────────────────────────────────
            //
            // 站长报的：「手打 `/give` 后直接点 `@s` 不会自动加空格」。
            // 实测拼出来的是 `/give@s ` —— 空格补在了 `@s` **后面**，
            // 命令名与参数之间却是粘的；`/give@s` 在游戏里是无效命令。
            //
            // ⚠️ 关键：`/give`（没打尾随空格）走的就是**这个 `exact` 分支**
            //    —— 命令名已完整，直接进参数阶段，替换区间收缩到光标处（= 关键字末尾）。
            //    所以"前导空格"必须在这里补；只在 completeArgument 与
            //    COMMAND_NAME 分支加都不够（前面查过，`/give` 根本不走那两条）。
            //
            // 判定：光标左边紧邻的不是空格 → 说明命令名与参数会粘在一起。
            // 用 replaceEnd 而不是整串长度，因为 `replaceStart == replaceEnd == 光标处`。
            val cursorAt = replaceEnd
            val needsSpace = cursorAt > 0 && input.getOrNull(cursorAt - 1) != ' '
            return Result(
                stage = Stage.ARGUMENT,
                items = all.take(MAX_ITEMS),
                // 命令名已经打完，替换范围收缩到光标处（后面没有内容要替换）
                replaceStart = replaceEnd,
                replaceEnd = replaceEnd,
                matchedCommand = exact,
                currentParamIndex = 0,
                prefix = if (needsSpace) " " else "",
                prompt = firstParam?.let { promptFor(it) } ?: "",
                truncatedNote = truncationNote(all, all, all.take(MAX_ITEMS), ""),
            )
        }

        return Result(
            stage = Stage.COMMAND_NAME,
            items = commandNameItems(typed),
            replaceStart = replaceStart,
            replaceEnd = replaceEnd,
            // ── 命令名与参数之间必须留一个空格 ────────────────────────────────
            //
            // 站长报的：「手打 `/give` 后直接点 `@s` 不会自动加空格」。
            // `/give` 后面没打空格时只有一个 token，替换区间正好盖住命令名，
            // 插进去就成了 `@s` 紧贴 `give` → `/give@s`（游戏里是无效命令）。
            //
            // `/give` 这种"命令名已打完、只是没打空格"的输入，恰恰是走到
            // **这里**（completeArgument 判定"还在打命令名"就转交给本函数），
            // 所以 prefix 必须在这里补 —— 只在 completeArgument 里加是不够的。
            //
            // 判定：输入正好就是一条完整命令的关键字（trim 后相等）。
            prefix = if (typed.isNotEmpty() && CommandLibrary.all.any { it.keyword == typed }) " " else "",
            prompt = if (typed.isEmpty()) "输入命令名，或从下面选一条" else "匹配的命令"
        )
    }

    /**
     * 展开一条命令的「命令族」：它自己 + [CommandSpec.alternatives] 里声明的兄弟命令。
     *
     * 返回的列表**第一个永远是它自己**，所以调用方用 `size > 1` 判断"要不要让用户选"。
     * 声明的兄弟关键字如果拼错、或者指向不存在的命令，会被静默跳过 ——
     * 这里宁可少给一个选项，也不能因为数据写错让补全直接崩掉。
     */
    private fun familyChoices(spec: CommandSpec): List<CommandSpec> {
        if (spec.alternatives.isEmpty()) return listOf(spec)
        val siblings = spec.alternatives.mapNotNull { keyword ->
            CommandLibrary.all.firstOrNull { it.keyword == keyword }
        }
        return listOf(spec) + siblings
    }

    /**
     * 命令名阶段的候选：英文前缀匹配的完整命令 + 多词命令的续写 + **中文名匹配**。
     *
     * 输入 `scoreboard players` 时：
     *  * 完整命令候选 → `scoreboard players set` / `add` / `test`（这些以输入开头）；
     *  * 续写候选     → 同样这三条（它们比输入长一个词，是"接着打下去"的形态）。
     *
     * 输入中文（`粒子`、`传送`）时走 [CommandLibrary.matchByAlias]：
     * 中文没有词边界，只能按"别名包含输入"来找；候选的**显示名是中文**、
     * `value` 仍是英文关键字 —— 点一下插入的是 `particle`，不是"粒子"。
     */
    private fun commandNameItems(typed: String): List<Item> {
        val commands = CommandLibrary.all
            .filter { typed.isEmpty() || it.keyword.startsWith(typed) }
            .sortedBy { it.keyword }
            .map { Item(value = it.keyword, label = it.keyword, detail = it.description) }

        if (typed.isEmpty()) return commands

        // 中文名匹配：混在同一个列表里，点进去都是英文关键字。
        val seen = commands.map { it.value }.toHashSet()
        val byAlias = CommandLibrary.matchByAlias(typed)
            .mapNotNull { spec -> spec.takeIf { it.keyword !in seen } }
            .map { spec ->
                Item(
                    value = spec.keyword,
                    label = spec.aliases.joinToString(" / "),
                    detail = spec.keyword,
                )
            }

        val continuations = continuationWords(typed)
            .filter { it !in seen && byAlias.none { item -> item.value == it } }
            .map { Item(value = it, label = it, detail = "继续输入") }

        return (commands + byAlias + continuations).distinctBy { it.value }
    }

    /**
     * 给出能接在当前已输入词后面的"下一个词"。
     *
     * 例：输入 `scoreboard` → 提示 `players`、`objectives`；
     * 输入 `scoreboard players` → 提示 `set`、`add`、`test`。
     *
     * 返回的是**从行首开始的完整文本**（`scoreboard players`），
     * 而不是单独一个词 —— 这样点击后替换整段，不用调用方再拼。
     */
    private fun continuationWords(typed: String): List<String> {
        val nextWords = HashSet<String>()
        CommandLibrary.all.forEach { spec ->
            val words = spec.keyword.split(' ')
            if (words.size < 2) return@forEach
            // 把关键字按词逐级拼成前缀，检查是否与输入完全相等
            for (i in 1 until words.size) {
                val prefix = words.take(i).joinToString(" ")
                if (prefix == typed) {
                    nextWords.add(words.take(i + 1).joinToString(" "))
                }
            }
        }
        return nextWords.sorted()
    }

    // =======================================================================
    // 参数阶段
    // =======================================================================

    private fun completeArgument(
        input: String,
        cursor: Int,
        tokens: List<Token>,
        trailingSpace: Boolean
    ): Result {
        // 关键：用来匹配命令名的 token 里**必须剔掉末尾那个空 token**。
        // 末尾空 token 是 tokenize 留下的"已经打了空格"标记（见 tokenize 的说明），
        // 它不是参数；如果不剔掉，`/give ` 会变成 ["/give", ""]，
        // matchKeyword 看到 tokens[1]="" 就一条命令都匹配不上（实测踩过）。
        val nameTokens = if (trailingSpace) tokens.dropLast(1) else tokens

        // 用来匹配命令名的文本：**把选择器方括号那一段剥掉**。
        //
        // 玩家打到 `@e[type=zombie` 时，token 是整个 `@e[type=zombie`，
        // 按词比对时它与 `@e` 不相等，一条命令都命中不了 ——
        // 结果就是"在方括号里打字，整个补全面板空了"（选择器补全也就永远触发不到）。
        //
        // ⚠️ 已知限制：`@e[name="a b"]` 这种**方括号里带空格**的选择器，
        // 分词器仍会按空格切成两段（分词器不认识方括号）。这是既有行为，
        // 本次不引入改动，以免影响现有大量测试；真要修得让分词器认括号。
        val nameText = nameTokens.joinToString(" ") { it.text }
            .substringBefore('[')
            .removePrefix("/")
            .trim()

        // ── 先判断"是不是还在打命令名"
        //
        // 这一步是必须的，否则多词命令打到一半会被误判：
        // 输入 `scoreboard players` 时，matchKeyword 会把开头两个词匹配到
        // `scoreboard players set`，如果直接往下走，引擎就以为"命令名已经打完、
        // 下一个词是参数"，给出"玩家名"的候选 —— 而玩家其实还想接着打 `set`。
        //
        // 判定规则：**匹配掉的词数 < 这条命令关键字的总词数** → 命令名还没打完。
        //  * `give` 对 `give ` → 命中 1 词，关键字 1 词 → 完整 ✓
        //  * `scoreboard players` 对 `scoreboard players set` → 命中 2 词，关键字 3 词 → 没打完 ✓
        val match = matchKeyword(nameTokens)
        val keywordWordCount = match?.first?.keyword?.split(' ')?.size ?: 0
        if (match != null && match.second < keywordWordCount) {
            return completeCommandName(input, nameTokens)
        }

        // 没匹配上时，再用"剥掉方括号的文本"兜一次（见 nameText 的说明）
        val spec = match?.first
            ?: CommandLibrary.match(input, nameText)
            ?: return Result(Stage.UNKNOWN)
        val keywordTokenCount = match?.second ?: spec.keyword.split(' ').size

        // ── 当前正在输入的那个参数下标：
        //  * 已经打了空格 → 前面的 token 都算"已填完的参数"
        //  * 没打空格 → 最后一个 token 是正在输入的参数，不额外计数
        val filled = nameTokens.size - keywordTokenCount - if (trailingSpace) 0 else 1
        val paramIndex = filled.coerceAtLeast(0)

        // 光标所在的 token 文本（用来做前缀过滤与"替换这一段"）
        val currentToken: Token? = if (trailingSpace) null else tokens.lastOrNull()
        val currentText = currentToken?.text.orEmpty()

        // 普通候选替换**整个 token**（token 属于光标左侧，所以不会碰到右边的文本）。
        val tokenStart = (currentToken?.start ?: input.length).coerceIn(0, cursor)
        var tokenEnd = (currentToken?.end ?: input.length).coerceIn(tokenStart, cursor)

        // ⚠️ 选择器参数特殊处理：**已经打出的 `[` 不能被吃掉**。
        //
        // 玩家打到 `/give @[]` 想换成 `@e` 时，token 是 `@[]`，如果照常替换整段，
        // 方括号会被一起删掉，补出来变成 `@e`（还得再打一次 `[`）。
        // 更糟的是成对插入时会出现 `@e[[]` 这种叠括号。
        // 所以替换区间**停在第一个方括号之前**，方括号与里面的内容原样保留。
        if (spec.params.getOrNull(paramIndex)?.type == ParamType.SELECTOR) {
            val bracket = currentText.indexOfFirst { it == '[' || it == ']' }
            if (bracket >= 0) {
                tokenEnd = (tokenStart + bracket).coerceIn(tokenStart, tokenEnd)
            }
        }

        // ── 命令名与参数之间**必须留一个空格**。
        //
        // 站长报的：「手打 `/give` 后直接点 `@s` 不会自动加空格」。
        // 实测那一步拼出来的是 `/give@s ` —— 空格补在了 `@s` **后面**，
        // 而命令名与参数之间是粘的；`/give@s` 在游戏里是无效命令。
        //
        // 原因：`/give` 后面没打空格时只有一个 token，替换区间正好盖住 `give`，
        // 插进去就成了 `@s` 紧贴 `give`。
        //
        // 修法：让结果带上 `prefix = " "`，由 [apply] 补在插入值**前面**。
        // （尾部那个空格仍由 apply 的 suffix 负责，两者互不干扰。）
        //
        // 只在「没打尾随空格」+「这个 token 就是完整命令关键字」时才动手：
        // 参数写到一半（`/give @s dia`）时 token 不是关键字，走不到这里。
        val needsLeadingSpace = !trailingSpace && nameText == spec.keyword &&
            currentText.isNotEmpty() && !currentText.startsWith("[")

        val param = spec.params.getOrNull(paramIndex)

        // ── 选择器的 `[]` 语法：光标在 `@e[...]` 里时给参数候选。
        //
        // 这一步必须放在"参数为 null"之前：`@e[type=zombie` 这种 token 会让
        // 参数下标算偏（它不是一个完整的参数），但玩家真正想补的是方括号里的东西。
        // 触发条件只看"光标是不是在某个未配平的选择器 `[` 里"，与参数下标无关。
        run {
            val before = input.substring(0, cursor)
            // 取方括号里"当前这一段"（最后一个逗号之后）来算查询词。
            // ⚠️ 不能用 substringAfterLast('[')：`@e[r=10,ty` 那样会把 `r=10,ty` 整段
            // 当成参数名，过滤结果全错（踩过）。
            val inner = currentText.substringAfterLast('[')
            val segment = if (currentText.contains('[')) inner.substringAfterLast(',') else ""
            val argQuery = segment.substringBefore('=').trim()
            val valueQuery = if (segment.contains('=')) {
                segment.substringAfter('=').trim().trim('"')
            } else {
                ""
            }

            val selectorItems = SelectorCompletion.suggest(before, argQuery, valueQuery)
            // ⚠️ null = 不在选择器里（继续走原来的参数补全）；
            //    空列表 = **在选择器里但没有候选**（例如 `x=` 是自由坐标值）。
            //    两者必须分开：后者绝不能穿透到 JSON 分支去给文本组件候选（踩过）。
            if (selectorItems != null) {
                return Result(
                    stage = Stage.ARGUMENT,
                    items = selectorItems.take(MAX_ITEMS),
                    replaceStart = tokenStart,
                    replaceEnd = tokenEnd,
                    matchedCommand = spec,
                    currentParamIndex = paramIndex,
                    prefix = if (needsLeadingSpace) " " else "",
                    prompt = if (selectorItems.isEmpty()) "该选择器参数请自己输入" else "选择器参数",
                    placeholder = if (selectorItems.isEmpty()) "这里填具体的值，没有可选列表" else "",
                    truncatedNote = truncationNote(
                        selectorItems, selectorItems, selectorItems.take(MAX_ITEMS), "",
                    ),
                )
            }
        }

        // ── 光标已经在 JSON 里时，优先按"物品组件"这一层来补。
        //
        // ⚠️ 为什么要在 `param == null` 之前做：
        //    玩家写 `… data {` 时光标前的 token 是裸 `{`，它会被当成"下一个参数"，
        //    于是 paramIndex 指到了**下一个参数**（`replaceitem` 里就是 item），
        //    甚至直接越界成 null。两种情况下 isInsideJson 都拿不到 components 这个键，
        //    结果补出来的是 `{"rawtext":[]}` 这种文本组件模板（单元测试抓到过）。
        //
        //    这里显式回退到"命令里那个叫 components 的参数"，把 paramKey 传对，
        //    JsonCompletion 才知道该给物品组件层。
        val insideJson = isInsideJson(input, cursor, param)
        val effectiveParam: CommandParam? = if (!insideJson) {
            param
        } else if (param?.key == COMPONENTS_KEY) {
            param
        } else if (param == null || param.type != ParamType.JSON) {
            // 回退到命令里那个叫 components 的参数；这条命令没有就维持原样。
            spec.params.firstOrNull { it.key == COMPONENTS_KEY } ?: param
        } else {
            param
        }

        if (param == null && effectiveParam == null) {
            // 参数已经填满：不再给候选，避免误导玩家"还能再填"。
            return Result(
                stage = Stage.ARGUMENT,
                items = emptyList(),
                replaceStart = tokenStart,
                replaceEnd = tokenEnd,
                matchedCommand = spec,
                currentParamIndex = paramIndex,
                prompt = "参数已填完",
                placeholder = "参数已填完，可以直接发送"
            )
        }

        // ── JSON 参数走"逐层补全"。
        //
        // 光标在未闭合的 JSON 里时（写了 `{`、`{"rawtext":[` 等），
        // 候选项是**下一层的 JSON 骨架**，插入点是**光标处**：
        // 既不吃掉光标左边的 `{`（玩家自己打的），也不吃掉右边的收尾括号。
        // ⚠️ 这里必须显式用 `cursor..cursor`，不能沿用 token 的起止：
        // token 是按整行切的，光标停在 JSON 中间时它的范围会跨过光标，
        // 拿它当替换区间会把右边刚写好的内容删掉（实测踩过）。
        if (insideJson) {
            val before = input.substring(0, cursor)
            val suggestions = JsonCompletion.suggest(
                before = before,
                alreadyOpenBrace = JsonCompletion.endsWithOpenBrace(before),
                // 把"正在填哪个参数"传进去：give/replaceitem 的组件 JSON 里
                // 玩家打 `{` 时**键名还没写**（`@s x 1 0 {` 这种裸 JSON），
                // 光看括号栈判断不出这是物品组件层还是文本组件层（踩过）。
                paramKey = effectiveParam?.key.orEmpty(),
            )
            if (suggestions.isNotEmpty()) {
                return Result(
                    stage = Stage.ARGUMENT,
                    items = suggestions.map { s ->
                        Item(
                            value = s.insert,
                            label = s.label,
                            detail = s.detail,
                            caretOffset = s.caretIn,
                            // JSON 里不能补空格，否则拼出来的不是合法 JSON
                            noSpace = true,
                        )
                    },
                    replaceStart = cursor,
                    replaceEnd = cursor,
                    matchedCommand = spec,
                    currentParamIndex = paramIndex,
                    prefix = if (needsLeadingSpace) " " else "",
                    prompt = "JSON：继续往下写",
                    placeholder = "",
                )
            }
        }

        val all = suggestionsFor(spec, paramIndex, currentText)
        val filtered = filterSuggestions(all, currentText)
        val shown = filtered.take(MAX_ITEMS)
        // 走到这里 effectiveParam 必定非空（上面已经对 null 提前返回）。
        val active = effectiveParam ?: param!!

        return Result(
            stage = Stage.ARGUMENT,
            items = shown,
            replaceStart = tokenStart,
            replaceEnd = tokenEnd,
            matchedCommand = spec,
            currentParamIndex = paramIndex,
            prefix = if (needsLeadingSpace) " " else "",
            prompt = promptFor(active),
            // 没有候选可给时，给一句"该填什么"的引导，而不是让面板空着。
            placeholder = if (filtered.isEmpty()) placeholderFor(active) else "",
            // 一条都没匹配上：明确说"没找到"，不要把整张表当成搜索结果摆出来 ——
            // 那样玩家会以为搜索生效了（实测被用户报成"搜不到中文"）。
            truncatedNote = truncationNote(all, filtered, shown, currentText),
        )
    }

    /**
     * 生成列表下方的说明文字。
     *
     * 三种情况：
     *  1. 有输入但**一条都没匹配** → 直说没找到，并提示会显示全部（避免误以为搜索生效）；
     *  2. 匹配太多被截断 → 告诉玩家还有多少条、继续输入可缩小范围；
     *  3. 其余 → 空串，不加噪音。
     */
    private fun truncationNote(
        all: List<Item>,
        filtered: List<Item>,
        shown: List<Item>,
        typed: String
    ): String {
        if (typed.isNotBlank() && filtered.size == all.size && all.size > MAX_ITEMS) {
            return "没有匹配「$typed」的项，下面显示的是全部 ${all.size} 条"
        }
        val rest = filtered.size - shown.size
        if (rest > 0) return "还有 $rest 条，继续输入可缩小范围"
        return ""
    }

    /**
     * 把开头若干 token 匹配到一条命令；第二个返回值是**匹配掉的词数**。
     *
     * 支持"多词命令打了一半"：输入 `scoreboard players` 时，
     * 它与 `scoreboard players set` 的前两个词相同，因此匹配成功、返回 2 个词。
     * 调用方据此判断"命令名还没打完"（返回值 < 关键字总词数）。
     *
     * 两个容易踩的点：
     *  * **第一个 token 要剥掉前导 `/`** —— 玩家打的是 `/give`，命令名是 `give`。
     *    不剥的话一条命令都匹配不上（`/give ` 这种最常见输入直接失效）。
     *  * 采用"最长匹配优先"：`scoreboard objectives add` 与
     *    `scoreboard objectives setdisplay` 共享前两个词，必须取匹配词数最多的那个。
     */
    private fun matchKeyword(tokens: List<Token>): Pair<CommandSpec, Int>? {
        var best: Pair<CommandSpec, Int>? = null
        CommandLibrary.all.forEach { spec ->
            val words = spec.keyword.split(' ')
            val limit = minOf(words.size, tokens.size)
            if (limit == 0) return@forEach
            val matched = (0 until limit).all { i ->
                val tokenText = if (i == 0) tokens[i].text.removePrefix("/") else tokens[i].text
                tokenText.equals(words[i], ignoreCase = true)
            }
            if (matched) {
                val current = best
                if (current == null || limit > current.second) {
                    best = spec to limit
                }
            }
        }
        if (best != null) return best

        // ── 中文命令名。
        //
        // 玩家会打 \`/粒子 \` 而不是 \`/particle \`。中文没有词边界，别名词里可能自带空格
        // （"队伍 创建" 是一个别名），所以这里按"**整个已输入前缀**"去比，
        // 而不是逐 token 比 —— 逐 token 在中文明明是对不上的（用户实测报过这个）。
        //
        // [CommandSpec.aliases] 里每个元素**已经是一个独立变体**（"粒子"、"特效粒子"），
        // 生成器在数据层就把 \`/\` 拆好了，这里直接用，别再拆一次。
        //
        // 匹配成功时把消耗掉的 token 数返回 0：中文名不参与英文关键字的词数计算，
        // 否则参数下标会偏。
        val typed = tokens.joinToString(" ") { it.text }.removePrefix("/").trim()
        if (typed.isNotEmpty()) {
            // 同分别名（`tp` 与 `teleport` 都是"传送"）时**取最短的关键字** ——
            // 玩家基本都用短名，取长名会让 `/传送` 匹配到不常用的那个。
            val hit = CommandLibrary.all
                .filter { spec ->
                    spec.aliases.any { it.isNotEmpty() && (typed == it || typed.startsWith(it + " ")) }
                }
                // 比较器是"最大者胜"：第一项越大越优先（别名越长越具体），
                // 第二项按关键字长度**升序** —— maxWith 会取到"长度最小"的那个，
                // 也就是 `tp` 优先于 `teleport`。
                // ⚠️ 别写成 `-spec.keyword.length`，那会把最长的选出来（刚踩过）。
                .maxWithOrNull(
                    compareBy({ spec -> spec.aliases.maxOf { it.length } }, { spec -> spec.keyword.length })
                )
            if (hit != null) return hit to 0
        }
        return null
    }

    /**
     * 某个参数位置的候选值。
     *
     * 顺序：参数显式声明的候选 → 按参数类型推导 → 按参数键 / 命令回退到词表。
     *
     * 所有候选项都带**中文名**（[Item.label]），英文 ID 放在 [Item.value]。
     * 界面按中文名展示、按英文 ID 插入 —— 玩家记得住"钻石剑"，但记不住
     * `diamond_sword`，只列英文等于把"查 wiki"换成"在长列表里找"。
     */
    fun suggestionsFor(spec: CommandSpec, paramIndex: Int, typed: String): List<Item> {
        val param = spec.params.getOrNull(paramIndex) ?: return emptyList()

        // 1) 参数显式声明的候选。
        //    显示名取 suggestionLabels（中文），插入值取 suggestions（英文 ID）。
        if (param.suggestions.isNotEmpty()) {
            return param.suggestions.mapIndexed { index, value ->
                val name = param.suggestionLabels.getOrNull(index) ?: value
                Item(value = value, label = name, detail = value)
            }
        }

        // 2) 按类型推导
        return when (param.type) {
            // 目标选择器：插入**只带左方括号**，方便接着写参数。
            //
            // ⚠️ 不要插 `@a[]` 这种成对的完整形式。玩家点 `@e` 的下一步一定是填参数
            // （`type=`、`r=`…），光标落在里面的确方便，但**方括号里的内容是他自己写的**，
            // 一下补全把 `[]` 和光标一起塞进来，看着像 App 替他做了决定；
            // 而且玩家已经手打了 `[` 时，成对插入会变成 `@e[[]`。
            // 所以只给 `@e` —— 玩家想加参数自己打一个 `[`，那时选择器参数补全会接手。
            ParamType.SELECTOR -> Suggest.selectors.map { Item(it.first, it.first, it.second) }

            ParamType.CHOICE -> param.choices.map { choice ->
                Item(choice, choice, describeChoice(choice))
            }

            ParamType.BOOLEAN -> listOf(
                Item("true", "true", "开启"),
                Item("false", "false", "关闭")
            )

            // ⚠️ `components` 这个键是**物品组件**，不能给 `{"rawtext":[]}` 这类文本模板。
            //    早先只有 `give` 的组件参数碰巧走到了组件库（因为参数名匹配），
            //    `replaceitem` 的组件参数却掉进了 jsonTemplates 分支，补出来的是
            //    `{"rawtext":[]}`（实测被单元测试抓到）。这里按**参数键名**统一判定，
            //    以后再给别的命令加组件参数也不会漏。
            ParamType.JSON -> if (param.key == COMPONENTS_KEY) {
                ComponentLibrary.search("").map {
                    Item(it.template, it.name, it.detail)
                }
            } else {
                Suggest.jsonTemplates.map { Item(it.first, shortJson(it.first), it.second) }
            }

            ParamType.NUMBER -> emptyList()

            ParamType.TEXT, ParamType.LONG_TEXT -> textSuggestions(spec, param)
        }
    }

    /**
     * 没有候选时，给一句"该填什么"的引导。
     *
     * 目前只有数字类参数会走到这里：数量填多少完全看玩家要做什么，
     * 列一排 `1 2 5 10` 反而像"只有这几个能选"。给一句"请输入整数"更诚实，
     * 也符合用户的明确要求。
     */
    private fun placeholderFor(param: CommandParam): String = when (param.type) {
        ParamType.NUMBER -> "请输入整数"
        ParamType.TEXT, ParamType.LONG_TEXT -> "请输入内容"
        else -> ""
    }

    /**
     * 下拉候选的中文说明。
     *
     * 只给"看不出来是什么"的值加说明：`survival` 要写"生存"，
     * 而 `title` / `set` 这种玩家在命令语法里已经见过的就不必重复解释。
     */
    private fun describeChoice(choice: String): String = when (choice) {
        "survival" -> "生存"
        "creative" -> "创造"
        "adventure" -> "冒险"
        "spectator" -> "旁观"
        "sidebar" -> "屏幕右侧常驻显示"
        "list" -> "玩家列表（Tab）里显示"
        "belowname" -> "显示在玩家名字下方"
        "ascending" -> "从小到大"
        "descending" -> "从大到小"
        "dummy" -> "只能被命令修改"
        "trigger" -> "可被玩家用 /trigger 触发"
        "deathCount" -> "统计死亡次数"
        "playerKillCount" -> "统计击杀玩家数"
        "totalKillCount" -> "统计击杀总数"
        "health" -> "统计生命值"
        "replace" -> "直接替换"
        "destroy" -> "破坏（掉落原方块）"
        "keep" -> "只替换空气"
        "hollow" -> "只填外壳"
        "outline" -> "只填外框"
        "masked" -> "只复制非空气方块"
        "filtered" -> "按方块过滤"
        "normal" -> "普通复制"
        "force" -> "强制复制（允许重叠）"
        "move" -> "剪贴（复制后清空原处）"
        "times" -> "设置淡入/停留/淡出时间"
        "clear" -> "清除"
        "reset" -> "恢复默认"
        "set" -> "设置"
        "strict" -> "只填充指定方块（不留随机旋转等）"
        "give" -> "给予效果"
        "on" -> "开启"
        "off" -> "关闭"
        "hide" -> "隐藏"
        "show" -> "显示"
        "server" -> "由服务器校验"
        "client" -> "由客户端校验"
        else -> ""
    }

    /**
     * 数字参数**不再给候选**。
     *
     * 用户明确要求："数量就不用做自动补全了，写个「请输入整数」即可"。
     * 这个取舍是对的：数量、时长、等级、坐标偏移填多少完全看玩家要做什么，
     * 列一排 `1 2 5 10` 反而像"只有这几个能选"，
     * 而"请输入整数"这种引导既诚实又不会误导。
     *
     * （保留这个说明是因为历史上这里真的有一份 `numberSuggestions`，
     *   后来按要求整体删掉了 —— 别再把它加回来。）
     *
     * ---------------------------------------------------------------------
     *
     * 文本参数：三级回退找词表。
     *
     *  1. 参数自己声明的候选（已经在 [suggestionsFor] 里处理，这里不重复）；
     *  2. 按**参数键**匹配（`item` → 可给予表、`block` → 方块表 …）；
     *  3. 按**命令关键字**匹配（`give` → 可给予表、`summon` → 实体表 …），
     *     用于参数键不在第 2 条里的情况（例如 `replaceitem` 的物品参数）。
     *
     * ⚠️ 物品参数用 [Suggest.giveableItems]（物品 ∪ 方块）而不是 [Suggest.items]：
     *    基岩版里**方块也能 /give**，只用 items 会让 `/give @s oak_log`、
     *    `/give @s white_wool` 这类查询"没有匹配的项"（真机上复现过）。
     *    方块的归类在 `blocks` 表里，所以这里必须取并集。
     */
    private fun textSuggestions(spec: CommandSpec, param: CommandParam): List<Item> {
        val byKey = when (param.key) {
            "item" -> Suggest.giveableItems
            "block" -> Suggest.blocks
            "entity" -> Suggest.entities
            "effect" -> Suggest.effects
            "rule" -> Suggest.gamerules
            "objective" -> emptyList()
            else -> emptyList()
        }
        if (byKey.isNotEmpty()) return byKey.map { Item(it.id, it.name, it.id) }

        val byCommand = when (spec.keyword) {
            "give", "replaceitem" -> Suggest.giveableItems
            "summon" -> Suggest.entities
            "effect" -> Suggest.effects
            "setblock", "fill" -> Suggest.blocks
            "gamemode" -> Suggest.gameModes
            else -> emptyList()
        }
        return byCommand.map { Item(it.id, it.name, it.id) }
    }

    /**
     * 枚举某条命令全部参数位置的候选（供测试与"命令一览"之类的界面使用）。
     *
     * [complete] 返回的是"光标当前位置"的候选，而这里给出的是整条命令**所有**位置的
     * 候选合集 —— 测试要断言"词表里确实有某个 ID"时用它，不然只能看到第一屏。
     */
    fun allSuggestionsFor(spec: CommandSpec): List<Item> =
        spec.params.indices.flatMap { suggestionsFor(spec, it, "") }.distinctBy { it.value }

    /**
     * 判断光标此刻是不是"正在写 JSON"。
     *
     * 两种情况都算：
     *  1. 当前正在填的参数本身就是 JSON 类型（例如 titleraw 的 JSON 组件）；
     *  2. 光标左侧已经出现了一个**没闭合的 `{` 或 `[`** —— 玩家可能是在别的参数里
     *     手写了 JSON（`give` 的组件参数就是这种），也要能补。
     */
    private fun isInsideJson(input: String, cursor: Int, param: CommandParam?): Boolean {
        if (param?.type == ParamType.JSON) return true
        val before = input.substring(0, cursor.coerceIn(0, input.length))
        val ctx = JsonCompletion.analyze(before)
        return ctx.openObjects > 0 || ctx.openArrays > 0
    }

    /**
     * 候选列表最多显示多少条。
     *
     * 音效有 1671 条、粒子 192 条，每条都是一个 View。不设上限时点开 `/playsound `
     * 要一次性创建 1671 个 View，悬浮面板会明显卡顿甚至卡死。
     * 截断后由 [Result.truncatedNote] 明确告知还有多少条。
     */
    const val MAX_ITEMS = 150

    /**
     * 物品组件参数的键名。
     *
     * `give` 与 `replaceitem` 的组件参数都叫这个键。补全时按它判断
     * "这是物品组件"而不是"一段 JSON 文本" —— 后者会给出 `{"rawtext":[]}`
     * 这类文本组件模板，填进物品组件里是错的（单元测试抓到过）。
     */
    private const val COMPONENTS_KEY = "components"

    /**
     * 过滤候选。
     *
     * 中文名与英文 ID 都参与匹配，并且用**包含**而不是前缀：
     *  * 中文玩家会打「钻石」而不是官方名「钻石剑」，前缀匹配会漏；
     *  * 打英文时通常是前缀（`dia` → `diamond_sword`），包含同样覆盖；
     *  * 候选可能带俗名（"爬行者 / 苦力怕"），包含匹配让两种叫法都能命中。
     *
     * 一条都不命中时退回完整列表：玩家可能正在打一个没被收录的值，
     * 这时把整份词表摆出来比给一个空列表有用。
     */
    fun filterSuggestions(items: List<Item>, typed: String): List<Item> {
        if (typed.isBlank()) return items
        val hit = items.filter {
            it.label.contains(typed, ignoreCase = true) ||
                it.value.contains(typed, ignoreCase = true)
        }
        return hit.ifEmpty { items }
    }

    /** 给玩家的一句提示。 */
    private fun promptFor(param: CommandParam): String {
        val required = if (param.required) "必填" else "可选"
        val hint = param.hint.takeIf { it.isNotBlank() }?.let { "　$it" } ?: ""
        return "${param.label}（$required）$hint"
    }

    /** JSON 骨架太长，列表里只显示开头一小段。 */
    private fun shortJson(json: String): String =
        if (json.length <= 34) json else json.take(34) + "…"

    // =======================================================================
    // 分词
    // =======================================================================

    /**
     * 按空格切分，但**不切** JSON 对象/数组内部与引号内部。
     *
     * 末尾的空格会产生一个"空的最后一个 token"，它标记了
     * "上一个词已经打完、正在等新词"这个状态（见 [complete] 的 trailingSpace）。
     */
    private fun tokenize(text: String): List<Token> {
        val tokens = ArrayList<Token>()
        val current = StringBuilder()
        var tokenStart = 0
        var depth = 0
        var inQuote = false
        var escaped = false

        text.forEachIndexed { index, ch ->
            if (escaped) {
                current.append(ch)
                escaped = false
                return@forEachIndexed
            }
            when {
                ch == '\\' && inQuote -> {
                    current.append(ch)
                    escaped = true
                }

                ch == '"' -> {
                    if (current.isEmpty()) tokenStart = index
                    inQuote = !inQuote
                    current.append(ch)
                }

                inQuote -> {
                    if (current.isEmpty()) tokenStart = index
                    current.append(ch)
                }

                ch == '{' || ch == '[' -> {
                    if (current.isEmpty()) tokenStart = index
                    depth++
                    current.append(ch)
                }

                ch == '}' || ch == ']' -> {
                    depth--
                    current.append(ch)
                }

                ch == ' ' && depth == 0 -> {
                    // 连续多个空格只当一个分隔符：
                    // 每个空格都产出 token 的话，参数序号会整体后移 ——
                    // 实测 `/give  @s `（give 后两个空格）会被算成"第 3 个参数"，
                    // 补全因此给出"数量"的候选而不是"物品 ID"。
                    // 基岩版本身也把连续空格当单个分隔符，压掉不改变语义。
                    if (current.isNotEmpty()) {
                        tokens.add(Token(current.toString(), tokenStart, index))
                        current.clear()
                    }
                    tokenStart = index + 1
                }

                else -> {
                    if (current.isEmpty()) tokenStart = index
                    current.append(ch)
                }
            }
        }
        // 末尾 token 的起点必须用 tokenStart，**不能用 text.length** ——
        // 打了空格之后 tokenStart 已经被推进到下一个词的起点，
        // 而 text.length 是整行的长度，会得到一个 start > end 的非法区间，
        // 替换时 substring(start, end) 直接抛异常。
        // 这个 bug 只在"多词输入"时显形（`scoreboard players`），
        // 单词输入恰好 start == length 才没暴露（实测踩过）。
        tokens.add(Token(current.toString(), tokenStart, text.length))
        return tokens
    }

    // =======================================================================
    // 插入
    // =======================================================================

    /**
     * 记录"点击候选后期望的光标位置"。
     *
     * ── 为什么需要它（真实踩过的坑）──────────────────────────────────────────
     * `EditText.setText()` 会把 selection 重置为 0，并**立刻**触发 TextWatcher；
     * 而 `setSelection()` 要下一帧才生效。于是 TextWatcher 里读到的光标必然是 0，
     * 补全按"光标在行首"重算 —— JSON 场景下的表现就是"候选换了一层"
     * （想要数组里的组件候选，却给了对象键候选），用户看到的现象与预期不符。
     *
     * 解决：点击候选时把期望落点记下来，紧接着的那一次渲染优先用它。
     * 这段状态刻意放在**引擎**里而不是 UI 层 —— 放 UI 里就没法用单元测试覆盖，
     * 而它恰恰是最容易出错的一段。
     */
    private var pendingCaret: Pair<String, Int>? = null

    /** 供测试与调用方观察是否有待落位的光标。 */
    fun hasPendingCaret(): Boolean = pendingCaret != null

    /** 清掉待落位光标（面板销毁时调用）。 */
    fun clearPendingCaret() {
        pendingCaret = null
    }

    /**
     * 取本次渲染应该用的光标位置。
     *
     * 规则：
     *  1. 有待落位光标且文本与之匹配 → 用待落位的（这是"刚点了候选"那一刻）；
     *  2. 文本非空但光标取到 ≤0 → 按末尾处理（`setText` 之后那个瞬间的正常状态）；
     *  3. 其余情况用真实光标，并夹在文本范围内。
     */
    fun resolveCaret(text: String, selection: Int): Int {
        val pending = pendingCaret
        if (pending != null && pending.first == text) {
            pendingCaret = null
            return pending.second.coerceIn(0, text.length)
        }
        return if (selection <= 0 && text.isNotEmpty()) text.length
        else selection.coerceIn(0, text.length)
    }

    /**
     * 把候选插入到文本里，替换掉 [Result.replaceStart]..[Result.replaceEnd] 这一段。
     *
     * 空格处理是这里最容易出问题的地方，规则定死如下：
     *  * 插入后**保证恰好一个空格**结尾（光标右侧本来就有空格时不重复补，
     *    避免出现两个空格把参数序号推偏）；
     *  * 插入的是"最后一个参数"（后面没有内容）时也补空格 —— 玩家接着打字就能
     *    直接接上，不用自己去点空格键。用户反馈过"点补全没自动加空格"，
     *    这条就是对着那个反馈写死的。
     *
     * @param appendSpace 是否在末尾补空格（默认 true；插入 JSON 这类整段值时也无害）
     * @return 新文本与新的光标位置
     */
    fun apply(
        input: String,
        result: Result,
        item: Item,
        appendSpace: Boolean = true
    ): Pair<String, Int> {
        val start = result.replaceStart.coerceIn(0, input.length)
        val end = result.replaceEnd.coerceIn(start, input.length)
        val tail = input.substring(end)
        // 后面紧跟着空格的，就不再多补一个。
        var suffix = if (appendSpace && !tail.startsWith(" ")) " " else ""

        // ⚠️ 「点了一个**什么都没替换掉**的候选」也要补空格。
        //
        // 场景：`/give` 已经是一条完整命令，引擎把 token 交给 completeCommandName，
        // 候选列表里仍然包含 `give` 自己（按 keyword.startsWith(typed) 筛出来的）。
        // 点它时 replaceStart == replaceEnd（没有要替换的文本），
        // 下面那条路判不出"该补空格"，结果停在 `/give` ——
        // 玩家接着打字会拼成 `/give@s`，命令直接废掉。
        //
        // 判定条件：**没替换任何文本**、光标就在输入末尾、而且输入正好以这个值为结尾。
        if (suffix.isEmpty() && appendSpace && start == end && end == input.length &&
            input.endsWith(item.value)
        ) {
            suffix = " "
        }

        val merged = input.substring(0, start) + result.prefix + item.value + suffix + tail
        // JSON 之类的候选会指定光标落点（例如停在 `[]` 中间），
        // 这时不能把光标直接放到末尾，否则玩家还得手动挪回去。
        val lead = result.prefix.length
        val cursor = if (item.caretOffset >= 0) {
            (start + lead + item.caretOffset).coerceIn(start, start + lead + item.value.length)
        } else {
            start + lead + item.value.length + suffix.length
        }
        // 记下期望落点：TextWatcher 里读不到真光标，只能靠它。
        pendingCaret = merged to cursor
        return merged to cursor
    }
}
