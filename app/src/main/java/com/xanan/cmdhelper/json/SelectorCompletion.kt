package com.xanan.cmdhelper.json

/**
 * 目标选择器 `[]` 里的**逐层补全**。
 *
 * ── 它解决什么 ──────────────────────────────────────────────────────────────
 * 选择器参数有二十多个、名字还都是缩写（`r` / `rm` / `rx` / `rym` / `lm` / `c`…），
 * 玩家基本靠背。这里按"光标在方括号里的哪个位置"给候选：
 *
 * ```
 * /give @e[            → type / name / tag / family / r / x …（22 个参数，中文可搜）
 * /give @e[type=       → 实体词表（僵尸 / 苦力怕 …）
 * /give @e[m=          → survival / creative / adventure / spectator
 * /give @e[family=     → mob / monster / undead …
 * /give @e[r=10,       → 继续给参数（自动补逗号）
 * ```
 *
 * ── 为什么不用严格的括号匹配 ────────────────────────────────────────────────
 * 选择器里随时可能出现嵌套括号（`scores={"a"=1}`、`hasitem={item=x}`），
 * 编辑中的文本又是不完整的。所以算法是"**从光标往前找最近的、还没配平的 `[`**"，
 * 找不到就返回空 —— 宁可不补，也不要在错误的位置乱给候选。
 */
object SelectorCompletion {

    /** 一段选择器的解析结果。 */
    data class Context(
        /** 选择器变量，如 `@e`。 */
        val variable: String,
        /** 光标前是否是"刚打完 `[` 或 `,`"，也就是该写**参数名**了。 */
        val expectingArgument: Boolean,
        /** 正在写的参数名（`expectingArgument` 为 false 时有效）。 */
        val argumentName: String,
        /** 该参数已经写了值（`=` 后面有内容）。 */
        val hasValue: Boolean,
    )

    /**
     * 分析光标左侧文本，判断是不是在某个选择器的方括号里。
     *
     * @param before 光标**之前**的整段文本
     * @return 不在选择器里时返回 null
     */
    fun analyze(before: String): Context? {
        // 从光标往前扫，维护"括号深度"，找到最外层未配平的 `[`
        var depth = 0
        var bracketStart = -1
        var i = before.length - 1
        while (i >= 0) {
            when (before[i]) {
                ']', '}' -> depth++
                '{' -> if (depth > 0) depth--
                '[' -> {
                    if (depth == 0) {
                        bracketStart = i
                        break
                    }
                    depth--
                }

                else -> Unit
            }
            i--
        }
        if (bracketStart < 0) return null

        // `[` 左边必须紧跟一个选择器变量，否则可能是数组语法（JSON 里的 []）
        val head = before.substring(0, bracketStart)
        val variable = SELECTOR_RE.find(head)?.value ?: return null
        if (head.trimEnd().length != (SELECTOR_RE.find(head)?.range?.last ?: -1) + 1) return null

        // 取方括号里光标之前那一段
        val inner = before.substring(bracketStart + 1)
        return parseInner(variable, inner)
    }

    /** 解析方括号内部已写的内容。 */
    private fun parseInner(variable: String, inner: String): Context {
        // 找最后一个"顶层"逗号（不在花括号/方括号里的）
        val lastComma = lastTopLevelComma(inner)
        val current = if (lastComma < 0) inner else inner.substring(lastComma + 1)

        val eq = current.indexOf('=')
        return if (eq < 0) {
            Context(
                variable = variable,
                expectingArgument = true,
                argumentName = current.trim(),
                hasValue = false,
            )
        } else {
            val name = current.substring(0, eq).trim()
            val value = current.substring(eq + 1).trim()
            Context(
                variable = variable,
                expectingArgument = false,
                argumentName = name,
                hasValue = value.isNotEmpty(),
            )
        }
    }

    /** 找最外层（不在 `{}` `[]` 里）的最后一个逗号。 */
    private fun lastTopLevelComma(text: String): Int {
        var depth = 0
        var result = -1
        text.forEachIndexed { index, c ->
            when (c) {
                '{', '[' -> depth++
                '}', ']' -> if (depth > 0) depth--
                ',' -> if (depth == 0) result = index
            }
        }
        return result
    }

    // =======================================================================
    // 候选
    // =======================================================================

    /**
     * 给当前光标位置出候选。
     *
     * @param before 光标之前的文本
     * @param argumentQuery 玩家在参数名位置已经打进去的字（用于过滤）
     * @param valueQuery 玩家在值位置已经打进去的字
     * @return null 表示"不在选择器里"；空列表表示"在但没候选"
     */
    fun suggest(before: String, argumentQuery: String, valueQuery: String): List<CommandCompletion.Item>? {
        val ctx = analyze(before) ?: return null

        // ① 该写参数名（刚打完 `[` 或 `,`）
        if (ctx.expectingArgument) {
            val all = SelectorLibrary.argumentsFor(ctx.variable)
            val filtered = if (argumentQuery.isBlank()) {
                all
            } else {
                all.filter { a ->
                    a.name.startsWith(argumentQuery, ignoreCase = true) ||
                        a.aliases.any { it.contains(argumentQuery) }
                }
            }
            // 已经写过的参数不再重复给（同名参数在基岩版里基本不允许重复）
            val used = usedArguments(before)
            val fresh = filtered.filter { it.name !in used }
            val source = fresh.ifEmpty { filtered }

            if (source.isEmpty()) return emptyList()
            return source.map { a ->
                CommandCompletion.Item(
                    value = "${a.name}=",
                    label = "${a.name}　${a.aliases.firstOrNull().orEmpty()}",
                    detail = a.detail,
                    // 插入 `name=` 后光标落在等号后面，玩家接着填值
                    caretOffset = a.name.length + 1,
                )
            }
        }

        // ② 该写值
        val arg = SelectorLibrary.find(ctx.argumentName)
            ?: return emptyList()

        // 已经写了值：说明玩家在改值，仍然给候选（按已输入过滤）
        //
        // `!` 取反：Wiki 明确 `type=!creeper`、`family=!monster`、`name=!Steve` 都合法，
        // 而且是很常用的一类写法。玩家只要打了 `!`，候选项也要跟着变成 `!xxx` ——
        // 否则选了 `creeper` 会把"排除苦力怕"变成"只选苦力怕"，语义正好相反。
        //
        // ⚠️ `!` 还要**显示在中文名上**：插入值放在 [CommandCompletion.Item.value]，
        // 而面板显示的唯一英文位置是 detail，那个位置已经被"实体类型"这类说明占掉了。
        // 只把 `!` 塞进 value 的话，玩家在界面上完全看不到它 ——
        // 看上去和"只选苦力怕"一模一样，等于把上面那个语义风险留给了玩家（实测踩过）。
        val negated = valueQuery.startsWith("!")
        val query = if (negated) valueQuery.removePrefix("!") else valueQuery
        val bang = if (negated) "!" else ""

        fun label(name: String) = if (negated) "$bang$name" else name

        return when (arg.valueKind) {
            SelectorLibrary.ValueKind.ENTITY_TYPE ->
                entityTypeItems(query).map { it.copy(value = bang + it.value, label = label(it.label)) }

            SelectorLibrary.ValueKind.GAMEMODE -> SelectorLibrary.gameModes
                // 中文名也要参与匹配 —— 玩家会打「创造」而不是 creative
                .filter { query.isBlank() || it.first.startsWith(query, true) || it.second.contains(query) }
                .map { (id, cn) ->
                    CommandCompletion.Item(bang + id, label("$cn（$id）"), "游戏模式")
                }

            SelectorLibrary.ValueKind.FAMILY -> SelectorLibrary.families
                .filter { query.isBlank() || it.first.startsWith(query, true) || it.second.contains(query) }
                .map { (id, cn) ->
                    CommandCompletion.Item(bang + id, label("$cn（$id）"), "实体族（基岩版专有）")
                }

            // 自由值：不给候选（坐标、数字、JSON 子对象都不适合列一堆出来）
            SelectorLibrary.ValueKind.FREE -> emptyList()
        }
    }

    /**
     * 实体类型候选：直接复用实体词表，中文可搜。
     *
     * ⚠️ 这里**不做 take(80)**：实体表是按字母序的，`zombie` 排在 100 名以后，
     * 一截断就搜不到最常用的僵尸了（实测踩过）。截断交给上层统一的 MAX_ITEMS。
     */
    private fun entityTypeItems(query: String): List<CommandCompletion.Item> {
        val all = Suggest.entities.map {
            CommandCompletion.Item(it.id, it.name, "实体类型")
        }
        if (query.isBlank()) return all
        val hit = all.filter {
            it.value.contains(query, ignoreCase = true) || it.label.contains(query)
        }
        return hit.ifEmpty { all }
    }

    /** 已经用过的参数名（避免重复推荐）。 */
    private fun usedArguments(before: String): Set<String> {
        val start = before.lastIndexOf('[')
        if (start < 0) return emptySet()
        val inner = before.substring(start + 1)
        val names = HashSet<String>()
        for (part in inner.split(',')) {
            val eq = part.indexOf('=')
            if (eq > 0) names.add(part.substring(0, eq).trim())
        }
        return names
    }

    /** 选择器变量：`@` + 字母（`@initiator` 这种长名字也要覆盖）。 */
    private val SELECTOR_RE = Regex("@[a-zA-Z_]+")
}
