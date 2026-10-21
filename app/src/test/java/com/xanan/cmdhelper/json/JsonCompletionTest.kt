package com.xanan.cmdhelper.json

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * JSON 逐层补全的测试。
 *
 * 场景直接照用户描述写：
 * ```
 * /titleraw @p actionbar {           → 出现 {"rawtext":[]}，光标落在 [] 内
 * /titleraw @p actionbar {"rawtext":[  → 出现 text / selector / score 三种骨架
 * ```
 */
class JsonCompletionTest {

    private fun complete(input: String, cursor: Int = input.length) =
        CommandCompletion.complete(input, cursor)

    // ── 括号栈 ──────────────────────────────────────────────────────────────

    @Test
    fun `空文本是对象上下文`() {
        val ctx = JsonCompletion.analyze("")
        assertEquals(JsonCompletion.Cursor.OBJECT, ctx.cursor)
        assertEquals(0, ctx.openObjects)
    }

    @Test
    fun `未闭合的花括号被识别为对象层`() {
        val ctx = JsonCompletion.analyze("{\"rawtext\":[{\"text\":\"a\"}")
        assertTrue("应有未闭合对象", ctx.openObjects >= 1)
    }

    @Test
    fun `rawtext 之后的数组层能被识别`() {
        val ctx = JsonCompletion.analyze("{\"rawtext\":[")
        assertEquals(JsonCompletion.Cursor.ARRAY, ctx.cursor)
        assertEquals("rawtext", ctx.ownerKey)
        assertEquals("rawtext", ctx.arrayParentKey)
        assertEquals(1, ctx.openArrays)
    }

    @Test
    fun `已闭合的括号不会残留`() {
        val ctx = JsonCompletion.analyze("{\"rawtext\":[{\"text\":\"a\"}]}")
        assertEquals(0, ctx.openObjects)
        assertEquals(0, ctx.openArrays)
    }

    @Test
    fun `字符串里的括号不参与计数`() {
        // 文本内容里写了花括号，不能被当成结构。
        // 注意这条串本身是**闭合**的，所以未闭合对象数应当是 0：
        // 如果实现把字符串里的 `{` 当成结构，这里会变成 1（实测踩过这个顺序问题：
        // when 分支必须把 inString 判断放在 ch == '{' 之前）。
        val closed = JsonCompletion.analyze("{\"text\":\"{ 这不是括号 }\"}")
        assertEquals(0, closed.openObjects)

        // 字符串没闭合时，外层那个 `{` 仍然是未闭合的 → 1
        val open = JsonCompletion.analyze("{\"text\":\"{ 未闭合")
        assertEquals(1, open.openObjects)
    }

    @Test
    fun `score 里的 name 被识别成当前键`() {
        val ctx = JsonCompletion.analyze("{\"score\":{\"name\":\"选择器\",")
        // ownerKey = 这个对象归属 score；lastSeenKey = 里面刚写过的键是 name
        assertEquals("score", ctx.ownerKey)
        assertEquals("name", ctx.lastSeenKey)
    }

    @Test
    fun `对象里最后见到的键会被记住`() {
        val ctx = JsonCompletion.analyze("{\"rawtext\":[{\"text\":\"a\",\"selector\":\"@s\"}")
        // 关掉内层对象后，外层的"最后键"就是刚关掉的那个键 selector
        assertEquals("selector", ctx.lastSeenKey)
        // 数组的父键始终是 rawtext，不会被组件内部的键带偏（这是逗号判断的依据）
        assertEquals("rawtext", ctx.arrayParentKey)
    }

    @Test
    fun `数组装进组件后父键仍然是 rawtext`() {
        // 回归：早先用单个 lastKey 变量，放进组件后被 text 覆盖，
        // 导致"这是不是 rawtext 数组"判断失效，逗号补不补也跟着错。
        val ctx = JsonCompletion.analyze("{\"rawtext\":[{\"text\":\"a\"},")
        assertEquals(JsonCompletion.Cursor.ARRAY, ctx.cursor)
        assertEquals("rawtext", ctx.arrayParentKey)
    }

    // ── 补全内容 ────────────────────────────────────────────────────────────

    @Test
    fun `刚打出左花括号时提示 rawtext 骨架`() {
        val before = "/titleraw @p actionbar {"
        assertTrue("应识别为刚打出的左花括号", JsonCompletion.endsWithOpenBrace(before))

        val items = JsonCompletion.suggest(before, true)
        assertEquals(1, items.size)
        val item = items.first()
        assertEquals("\"rawtext\":[]}", item.insert)
        assertEquals("{\"rawtext\":[]}", item.label)
        // 光标落在 [] 中间：`"rawtext":[` 之后
        assertEquals("\"rawtext\":[".length, item.caretIn)
    }

    @Test
    fun `点击 rawtext 骨架后光标真的落在方括号里`() {
        // 端到端：拼出来的文本与光标位置都要对
        val base = "/titleraw @p actionbar {"
        val result = CommandCompletion.Result(
            stage = CommandCompletion.Stage.ARGUMENT,
            items = emptyList(),
            replaceStart = base.length,
            replaceEnd = base.length,
        )
        val item = CommandCompletion.Item(
            value = "\"rawtext\":[]}",
            caretOffset = "\"rawtext\":[".length,
        )
        val (merged, cursor) = CommandCompletion.apply(base, result, item, appendSpace = false)

        assertEquals("/titleraw @p actionbar {\"rawtext\":[]}", merged)
        // 光标位置 = 左方括号之后
        assertEquals(base.length + "\"rawtext\":[".length, cursor)
        assertEquals('[', merged[cursor - 1])
        assertEquals(']', merged[cursor])
    }

    @Test
    fun `在 rawtext 数组里提示三种组件骨架`() {
        val items = JsonCompletion.suggest("/titleraw @p actionbar {\"rawtext\":[", false)
        assertEquals(3, items.size)
        assertTrue(items.any { it.label.contains("\"text\"") })
        assertTrue(items.any { it.label.contains("\"selector\"") })
        assertTrue(items.any { it.label.contains("\"score\"") })
        // 每种骨架本身都必须是合法 JSON
        items.forEach { assertNull(TextComponent.validate(it.insert)) }
    }

    @Test
    fun `点 text 骨架后光标落在该组件的花括号之后`() {
        val items = JsonCompletion.suggest("/titleraw @p actionbar {\"rawtext\":[", false)
        val text = items.first { it.insert.contains("\"text\"") }
        // 落在整个组件的 `}` 之后 → 层级回到数组层，面板继续提示"下一个组件"
        assertEquals(text.insert.length, text.caretIn)
        // 顺带确认占位文本（用户要求用中文提示，而不是塞个假值）
        assertTrue("占位文本：${text.insert}", text.insert.contains("请输入文本"))
    }

    @Test
    fun `点 score 骨架后光标落在 score 的最后一个花括号之后`() {
        val items = JsonCompletion.suggest("/titleraw @p actionbar {\"rawtext\":[", false)
        val score = items.first { it.label.contains("score") }
        // score 里有嵌套对象，必须落在**最后一个** `}` 之后，
        // 而不是第一个 `:"` 之后 —— 后者会插进 "选择器" 中间，
        // 面板于是显示成"对象内解析"，用户就看不到可选子键了（实测报过）。
        assertEquals(score.insert.length, score.caretIn)
        assertEquals('}', score.insert[score.caretIn - 1])
    }

    @Test
    fun `rawtext 骨架的光标落在方括号里`() {
        val item = JsonCompletion.suggest("/titleraw @p actionbar {", true).first()
        assertEquals('[', item.insert[item.caretIn - 1])
        assertEquals(']', item.insert[item.caretIn])
    }

    @Test
    fun `在数组里打花括号是开始新组件，不再提示 rawtext 骨架`() {
        // 回归：早先只要光标前是 `{` 就给 `{"rawtext":[]}`，
        // 于是在数组里打 `{`（开始一个组件）也被提示 rawtext 骨架，层级完全不对。
        val inArray = JsonCompletion.suggest("{\"rawtext\":[", false).let { "{\"rawtext\":[{" }
        val items = JsonCompletion.suggest(inArray, true)
        val labels = items.map { it.label }
        assertTrue("应给组件键：$labels", labels.any { it.contains("\"text\"") })
        assertTrue("应给组件键：$labels", labels.any { it.contains("\"selector\"") })
        assertTrue("不该再提示 rawtext 骨架：$labels", labels.none { it.contains("rawtext") })
        // 插入文本要把玩家打的这个 `{` 收尾，光标落在 `}` 之后
        items.forEach {
            assertTrue("应以 } 收尾：${it.insert}", it.insert.endsWith("}"))
            assertEquals(it.insert.length, it.caretIn)
        }
    }

    @Test
    fun `组件对象里只给真正的子键`() {
        // 刚打完 `{"score":` 准备写值：这里已经在**组件对象**里了，
        // 该给 text / selector / score 三个子键，而不是 translate/color/bold 这类修饰键
        val items = JsonCompletion.suggest("{\"rawtext\":[{\"score\":", false)
        val labels = items.map { it.label }
        assertEquals(listOf("\"text\"", "\"selector\"", "\"score\""), labels)
    }

    @Test
    fun `score 骨架闭合后回到组件层的候选`() {
        // 点完 score 骨架、光标落在 `}` 之后 → 应该又能选下一个组件
        val after = "{\"rawtext\":[{\"score\":{\"name\":\"选择器\",\"objective\":\"计分板名称\"}}"
        val items = JsonCompletion.suggest(after, false)
        val labels = items.map { it.label }
        assertTrue("应能继续选组件：$labels", labels.any { it.contains("text") })
        assertTrue("应能继续选组件：$labels", labels.any { it.contains("selector") })
        // 数组里已经有一项了 → 要带逗号
        assertTrue("应带逗号：${items.map { it.insert }}", items.all { it.insert.startsWith(",") })
    }

    @Test
    fun `score 骨架里的 name 用中文占位而不是 @s`() {
        // 用户要求：不要预填 @s，直接写"选择器"，玩家一看就知道该填什么
        val items = JsonCompletion.suggest("/titleraw @p actionbar {\"rawtext\":[", false)
        val score = items.first { it.label.contains("score") }
        assertTrue("score 骨架：${score.insert}", score.insert.contains("\"name\":\"选择器\""))
        assertTrue(score.insert.contains("\"objective\":\"计分板名称\""))
        assertTrue("不该出现 @s：${score.insert}", !score.insert.contains("@s"))

        // 光标落在整个 score 组件的 `}` 之后（见上面那条专门的测试）
        assertEquals(score.insert.length, score.caretIn)
    }

    // ── 逗号与空格（用户报的三个问题）──────────────────────────────────────

    @Test
    fun `数组里已有组件时自动加逗号`() {
        val items = JsonCompletion.suggest("{\"rawtext\":[{\"text\":\"a\"}", false)
        items.forEach {
            assertTrue("应当补逗号：${it.insert}", it.insert.startsWith(","))
        }
    }

    @Test
    fun `空数组里不加逗号`() {
        val items = JsonCompletion.suggest("{\"rawtext\":[", false)
        items.forEach {
            assertTrue("空数组不该加逗号：${it.insert}", !it.insert.startsWith(","))
        }
    }

    @Test
    fun `对象里已经写过键时自动加逗号`() {
        val items = JsonCompletion.suggest("{\"rawtext\":[{\"text\":\"a\"", false)
        assertTrue("对象里也该补逗号", items.all { it.insert.startsWith(",") })
    }

    @Test
    fun `加逗号后光标位置把逗号算进去`() {
        val items = JsonCompletion.suggest("{\"rawtext\":[{\"text\":\"a\"}", false)
        val text = items.first { it.insert.contains("\"text\"") }
        // 插入文本是 ,{"text":"请输入文本"}，光标落在整个 `}` 之后
        assertEquals(text.insert.length, text.caretIn)
        assertTrue("应带逗号", text.insert.startsWith(","))
    }

    @Test
    fun `JSON 候选插入后不补空格`() {
        // 用户报过"选一个子键后自动打了个空格，这不对"。
        // JSON 骨架属于 JSON 内部，补空格会把 JSON 拼坏。
        val result = complete("/titleraw @p actionbar show {\"rawtext\":[")
        assertTrue("应有候选", result.items.isNotEmpty())
        result.items.forEach {
            assertTrue("JSON 候选不该补空格：${it.label}", it.noSpace)
        }
    }

    @Test
    fun `连续插入两个组件得到合法 JSON 且带逗号`() {
        // ── 真实用法：点第一个组件 → 把文本改好 → 光标在末尾 → 再点第二个组件。
        //    （不要把光标留在占位文本中间还要求结果是合法 JSON，那是自造的不现实场景。）
        var input = "/titleraw @p actionbar show {\"rawtext\":["

        val first = complete(input)
        val firstItem = first.items.first { it.label.contains("\"text\"") }
        var applied = CommandCompletion.apply(input, first, firstItem, appendSpace = !firstItem.noSpace)
        input = applied.first
        assertTrue("第一个组件前不该有逗号：$input", !input.contains("[,"))

        // 玩家把占位文本改成自己的内容，光标落在**末尾**
        // （这是"打完字"的真实状态；留在占位文本中间属于编辑进行中，
        //  那时给的是对象键候选，不该拿来断言数组层的行为。）
        input = input.replace("请输入文本", "你好")
        var caret = input.length

        val second = complete(input, caret)
        val secondItem = second.items.first { it.label.contains("\"selector\"") }
        assertTrue("第二个组件应带逗号：${secondItem.value}", secondItem.value.startsWith(","))

        applied = CommandCompletion.apply(input, second, secondItem, appendSpace = !secondItem.noSpace)
        input = applied.first
        caret = applied.second

        // 补齐收尾括号，验证拼出来的 JSON 真的合法
        // （这正是不补空格 + 自动逗号的意义）
        val json = input.substringAfter("actionbar show ") + "]}"
        val err = TextComponent.validate(json)
        if (err != null) {
            throw AssertionError("生成的 JSON 不合法（$err），实际 = " + json)
        }
        // 内容也要对
        assertTrue("应含 text：$json", json.contains("\"text\":\"你好\""))
        assertTrue("应含 selector：$json", json.contains("\"selector\":\"选择器\""))
        assertTrue("光标不应越界", caret in 0..input.length)
    }

    @Test
    fun `光标停在 JSON 中间时不会删掉右边的文本`() {
        // 回归：早先普通候选用 token 的 end 当替换终点，而 token 按整行切，
        // 光标停在 JSON 中间时那个范围会跨过光标，一点候选就把右边内容删了。
        val input = "/titleraw @p actionbar show {\"rawtext\":[{\"text\":\"你好\"}]}"
        val caret = input.indexOf('"', input.indexOf("你好"))  // 光标落在"你好"的收尾引号前
        val result = complete(input, caret)
        // JSON 模式必须"原地追加"，不吃左右任何内容
        assertEquals(caret, result.replaceStart)
        assertEquals(caret, result.replaceEnd)
    }

    @Test
    fun `在 score 对象里提示 name 与 objective`() {
        // 刚进 score 对象：两个键都该提示
        val fresh = JsonCompletion.suggest("{\"score\":{", false)
        val freshLabels = fresh.map { it.insert }.joinToString(" | ")
        if (!fresh.any { it.insert.startsWith("\"name\"") }) {
            throw AssertionError("应提示 name，实际候选 = " + freshLabels)
        }
        if (!fresh.any { it.insert.startsWith("\"objective\"") }) {
            throw AssertionError("应提示 objective，实际候选 = " + freshLabels)
        }

        // 已经写了 name：不该再重复推荐 name，直接给 objective
        val afterName = JsonCompletion.suggest("{\"score\":{\"name\":\"选择器\",", false)
        val afterLabels = afterName.map { it.insert }.joinToString(" | ")
        if (!afterName.any { it.insert.contains("\"objective\"") }) {
            throw AssertionError("写过 name 后应给 objective，实际候选 = " + afterLabels)
        }
        if (afterName.any { it.label == "\"name\"" }) {
            throw AssertionError("不该重复推荐 name，实际候选 = " + afterLabels)
        }
    }

    @Test
    fun `在组件对象里提示常用键`() {
        val items = JsonCompletion.suggest("{\"rawtext\":[{", false)
        val labels = items.map { it.label }
        assertTrue("应有 text：$labels", labels.any { it.contains("text") })
        assertTrue("应有 selector：$labels", labels.any { it.contains("selector") })
        assertTrue("应有 score：$labels", labels.any { it.contains("score") })
    }

    // ── 与命令补全打通 ──────────────────────────────────────────────────────

    @Test
    fun `命令补全在 JSON 层里给出 JSON 骨架而不是词表`() {
        // `/titleraw @p actionbar ` 这一刻光标还在"操作"参数上（show/times/clear…），
        // 所以那时给的是操作候选，不是 JSON —— 这是对的。
        val atAction = complete("/titleraw @p actionbar ")
        assertEquals(2, atAction.currentParamIndex)
        assertTrue(atAction.items.any { it.value == "show" })

        // 操作打完（show 是默认值，也可以显式打）之后轮到 JSON 参数，这时给整段骨架
        val atJson = complete("/titleraw @p actionbar show ")
        assertTrue(
            "JSON 参数应给整段骨架：${atJson.items.take(2).map { it.label }}",
            atJson.items.any { it.value.contains("rawtext") }
        )

        // 打了 `{` 之后给骨架的"里面那半"
        val openBrace = complete("/titleraw @p actionbar show {")
        assertTrue(openBrace.items.any { it.value == "\"rawtext\":[]}" })

        // 进了 rawtext 数组就给三种组件骨架
        val inArray = complete("/titleraw @p actionbar show {\"rawtext\":[")
        assertTrue("进了数组应给组件骨架", inArray.items.any { it.value.contains("\"text\"") })
        assertTrue(inArray.items.any { it.value.contains("\"selector\"") })
        assertTrue(inArray.items.any { it.value.contains("\"score\"") })
    }

    @Test
    fun `give 的组件参数里手写花括号也能补`() {
        val result = complete("/give @s diamond_sword 1 0 {")
        assertNotNull(result.matchedCommand)
        // 光标右侧没有内容可替换时，在光标处追加
        assertEquals(result.replaceStart, result.replaceEnd)
        assertTrue(
            "give 的组件里也应给 JSON 骨架：${result.items.take(2).map { it.label }}",
            result.items.isNotEmpty()
        )
    }

    // ── 连续补全（回归）─────────────────────────────────────────────────────
    //
    // 这一条是照着真实点击顺序走的，专门盯住一个**已修的真实 bug**：
    // 点击 `{"rawtext":[]}` 之后光标错落在 `:` 后面（而不是 `[` 与 `]` 之间），
    // 于是下一层给出的候选从"数组里的组件"变成了"对象键"。
    // 根因是候选的 caretIn 少算了开头那个引号（11 vs 12）。

    @Test
    fun `连续两次补全——点骨架后光标进方括号，下一层给组件`() {
        var input = "/titleraw @p actionbar show "

        // 第一次：给整段骨架
        val first = complete(input)
        assertTrue("应给整段骨架", first.items.any { it.value.contains("rawtext") })

        val (merged, caret) =
            CommandCompletion.apply(input, first, first.items.first(), appendSpace = false)
        input = merged

        // 文本必须完整，且光标恰好夹在方括号中间
        assertEquals("/titleraw @p actionbar show {\"rawtext\":[]}", input)
        assertEquals('[', input[caret - 1])
        assertEquals(']', input[caret])

        // 模拟 EditText：setText 之后 selection 会被重置为 0，
        // 由 resolveCaret 决定这次渲染该用哪个光标（这正是踩坑的地方）。
        val resolved = CommandCompletion.resolveCaret(input, 0)
        assertEquals("resolveCaret 应还原到方括号中间", caret, resolved)

        // 第二次：应当给三种组件骨架，而不是对象键
        val second = complete(input, resolved)
        val labels = second.items.map { it.label }
        assertTrue("应给组件骨架：$labels", labels.any { it.contains("text") })
        assertTrue("应给组件骨架：$labels", labels.any { it.contains("selector") })
        assertTrue("应给组件骨架：$labels", labels.any { it.contains("score") })
        assertTrue("不该给对象键：$labels", labels.none { it == "\"color\"" })
    }

    @Test
    fun `resolveCaret 在没有待落位光标时按末尾兜底`() {
        // 文本非空但 selection 为 0：这是 setText 之后那个瞬间，按末尾处理
        assertEquals(5, CommandCompletion.resolveCaret("hello", 0))
        // 有正常光标时原样返回
        assertEquals(2, CommandCompletion.resolveCaret("hello", 2))
        // 超过范围时夹住
        assertEquals(5, CommandCompletion.resolveCaret("hello", 99))
    }

    @Test
    fun `骨架候选的光标落点用 indexOf 算，不手写常量`() {
        // 手写常量最容易差 1（踩过），所以断言落点就是 `]` 的位置
        val insert = "\"rawtext\":[]}"
        val items = JsonCompletion.suggest("/titleraw @p actionbar {", true)
        val item = items.first()
        assertEquals(insert, item.insert)
        assertEquals(insert.indexOf(']'), item.caretIn)
    }
}
