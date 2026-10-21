package com.xanan.cmdhelper.json

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 自动补全引擎的测试。
 *
 * 这块逻辑"看着对、边界全错"，必须靠测试钉住。测试直接对着用户描述的场景写：
 *  * 输入 `/` → 出全部命令；
 *  * 输入 `/give ` → 出选择器；
 *  * 输入 `/give @s ` → 出物品 ID；
 *  * 点击候选 → 真的能拼出下一步。
 */
class CommandCompletionTest {

    private fun complete(input: String) = CommandCompletion.complete(input)

    // =======================================================================
    // 命令名阶段
    // =======================================================================

    @Test
    fun `只输入斜杠时列出全部命令`() {
        val result = complete("/")
        assertEquals(CommandCompletion.Stage.COMMAND_NAME, result.stage)
        assertEquals(CommandLibrary.all.size, result.items.size)
        assertTrue(result.items.any { it.value == "give" })
        assertTrue(result.items.any { it.value == "titleraw" })
        assertTrue(result.items.any { it.value == "execute" })
    }

    @Test
    fun `输入斜杠加字母时按前缀过滤命令`() {
        val result = complete("/g")
        val names = result.items.map { it.value }
        assertTrue(names.contains("give"))
        assertTrue(names.contains("gamemode"))
        assertTrue(names.contains("gamerule"))
        // 不该出现不匹配的命令
        assertTrue(names.none { it == "titleraw" })
    }

    @Test
    fun `不带斜杠也能补全命令`() {
        val result = complete("giv")
        assertEquals(CommandCompletion.Stage.COMMAND_NAME, result.stage)
        assertTrue(result.items.any { it.value == "give" })
    }

    @Test
    fun `多词命令给出续写提示`() {
        val result = complete("scoreboard")
        val values = result.items.map { it.value }
        assertTrue("应提示 players：$values", values.contains("scoreboard players"))
        assertTrue("应提示 objectives：$values", values.contains("scoreboard objectives"))
    }

    @Test
    fun `多词命令打到一半时只提示能接上的`() {
        val values = complete("scoreboard players").items.map { it.value }
        assertTrue(values.contains("scoreboard players set"))
        assertTrue(values.contains("scoreboard players add"))
        assertTrue(values.contains("scoreboard players test"))
        // objectives 那条接不上 players，不能出现
        assertTrue(values.none { it == "scoreboard objectives add" })
    }

    @Test
    fun `命令名与候选的替换范围覆盖整段输入不含斜杠`() {
        val result = complete("/g")
        assertEquals(1, result.replaceStart)
        assertEquals(2, result.replaceEnd)
        // 点击 give 后应当得到 "/give "（保留玩家打的斜杠），
        // 光标落在末尾（长度 6：/,g,i,v,e,空格）
        val (merged, cursor) =
            CommandCompletion.apply("/g", result, CommandCompletion.Item("give"))
        assertEquals("/give ", merged)
        assertEquals(6, cursor)
    }

    @Test
    fun `手打命令名后直接点参数会补上命令名与参数之间的空格`() {
        // ⚠️ 站长报的原话：「手打 /give 后直接点 `@s` 不会自动加空格」。
        //
        // 实测那一步拼出来的是 `/give@s ` —— 空格补在了 `@s` **后面**，
        // 而命令名与参数之间是粘的；`/give@s` 在游戏里是无效命令。
        //
        // 原因：`/give` 后面没打空格时只有一个 token，替换区间正好盖住命令名，
        // 插进去就成了 `@s` 紧贴 `give`。
        // 修法：这类情况下 Result 带上 `prefix = " "`，由 apply 补在值**前面**。
        val result = complete("/give")
        assertEquals(CommandCompletion.Stage.ARGUMENT, result.stage)
        val sItem = result.items.firstOrNull { it.value == "@s" }
        assertNotNull("应有 @s 候选：${result.items.take(3).map { it.value }}", sItem)

        val (merged, cursor) = CommandCompletion.apply("/give", result, sItem!!)
        assertEquals("命令名与参数之间必须有一个空格", "/give @s ", merged)
        // 光标落在末尾：`/give @s ` 共 9 个字符
        assertEquals(9, cursor)
    }

    @Test
    fun `打完 give 这种没有同类写法的命令时直接进参数阶段`() {
        // 命令族机制只对**声明了 alternatives** 的命令生效。
        // `give` 没有另类写法，所以 `/give` 必须照旧直接进参数阶段 ——
        // 如果这条挂了，说明命令族判断写宽了，会把所有命令都拦一道。
        val result = complete("/give")
        assertEquals(CommandCompletion.Stage.ARGUMENT, result.stage)
        assertEquals("give", result.matchedCommand?.keyword)
    }

    // =======================================================================
    // 命令族：title / titleraw、tell / tellraw
    // =======================================================================

    @Test
    fun `打 title 先让用户选 title 还是 titleraw`() {
        // 站长报的：手打 `/title` 会直接进入 title 的参数阶段，没机会选 titleraw。
        // 这两条名字只差三个字母，用途却不同（纯文本 vs JSON 可上色），
        // 直接进去等于替用户做了决定。
        val result = complete("/title")
        assertEquals(CommandCompletion.Stage.COMMAND_NAME, result.stage)

        val values = result.items.map { it.value }
        assertTrue("应同时给出 title 与 titleraw：$values", values.containsAll(listOf("title", "titleraw")))
        // 顺序：它自己排第一
        assertEquals("title", values.first())
        // 差异说明要带上，否则两条几乎一样、用户没法选
        val titleraw = result.items.first { it.value == "titleraw" }
        assertTrue("titleraw 选项应说明是 JSON：${titleraw.label}", titleraw.label.contains("JSON"))
    }

    @Test
    fun `在 title 的族选项里点 titleraw 得到干净的 titleraw 命令`() {
        // ⚠️ 这里最容易出的错是拼成 `/ titleraw` —— 命令名分支默认会补一个
        // 前导空格（用于 `/give` 点 `@s` 的场景），但族选项里 typed 本身就是
        // 完整关键字，再加前导空格会变成"斜杠与命令名之间有空隙"，命令直接报废。
        val result = complete("/title")
        val titleraw = result.items.first { it.value == "titleraw" }

        val (merged, cursor) = CommandCompletion.apply("/title", result, titleraw)
        assertEquals("/titleraw ", merged)
        // 光标落在末尾：`/titleraw ` 共 10 个字符
        assertEquals(10, cursor)
    }

    @Test
    fun `选完 title 族之后不会再次弹出同一个选择`() {
        // 选完会留下尾随空格，所以下一次补全走的是参数阶段 ——
        // 否则用户会陷入"点了 title 又让你选 title 还是 titleraw"的死循环。
        val afterChooseTitle = complete("/title ")
        assertEquals(CommandCompletion.Stage.ARGUMENT, afterChooseTitle.stage)
        assertEquals("title", afterChooseTitle.matchedCommand?.keyword)

        val afterChooseRaw = complete("/titleraw ")
        assertEquals(CommandCompletion.Stage.ARGUMENT, afterChooseRaw.stage)
        assertEquals("titleraw", afterChooseRaw.matchedCommand?.keyword)
    }

    @Test
    fun `直接打 titleraw 不会被拦一道`() {
        // 族选项只挂在基准关键字上。用户明确打出 titleraw，就该直接进它的参数阶段。
        val result = complete("/titleraw")
        assertEquals(CommandCompletion.Stage.ARGUMENT, result.stage)
        assertEquals("titleraw", result.matchedCommand?.keyword)
    }

    @Test
    fun `打 titl 这种半截仍是普通前缀过滤`() {
        // 半截输入本来就会把两条都列出来，不能因为"是命令族"就改变这条路径。
        val result = complete("/titl")
        assertEquals(CommandCompletion.Stage.COMMAND_NAME, result.stage)
        val values = result.items.map { it.value }
        assertTrue(values.contains("title"))
        assertTrue(values.contains("titleraw"))
    }

    @Test
    fun `tell 也走同一套族机制`() {
        // 通用机制：新增同类命令只需在命令库里标一句 alternatives，界面自动生效。
        val result = complete("/tell")
        assertEquals(CommandCompletion.Stage.COMMAND_NAME, result.stage)
        val values = result.items.map { it.value }
        assertTrue("应给出 tell 与 tellraw：$values", values.containsAll(listOf("tell", "tellraw")))
    }

    @Test
    fun `用中文别名打标题时不弹族选项`() {
        // 玩家打「标题文字」时意图已经明确（那是 title 的别名），再问一遍是噪音。
        // 族判断用的是 keyword 精确相等，不是别名匹配。
        val result = complete("/标题文字")
        assertEquals(
            "中文别名应直接认成 title 并进参数阶段，实际 stage=${result.stage}",
            CommandCompletion.Stage.ARGUMENT,
            result.stage,
        )
    }

    // =======================================================================
    // 命令族：结束
    // =======================================================================

    @Test
    fun `已经打了空格时不会补出双空格`() {
        // 回归：`/give ` 已经带空格，点 @s 只能是 `/give @s `，不能变成两个空格。
        val result = complete("/give ")
        val sItem = result.items.first { it.value == "@s" }
        val (merged, _) = CommandCompletion.apply("/give ", result, sItem)
        assertEquals("/give @s ", merged)
    }

    @Test
    fun `参数写到一半时不会乱补前导空格`() {
        // `/give @s dia` 这种：token 不是命令关键字，不该补前导空格。
        val result = complete("/give @s dia")
        val item = result.items.firstOrNull { it.value == "diamond_sword" } ?: return
        val (merged, _) = CommandCompletion.apply("/give @s dia", result, item)
        assertEquals("/give @s diamond_sword ", merged)
    }

    @Test
    fun `打完一整条多词命令后直接进入参数阶段`() {
        val result = complete("scoreboard players set")
        assertEquals(CommandCompletion.Stage.ARGUMENT, result.stage)
        assertNotNull(result.matchedCommand)
        assertEquals("scoreboard players set", result.matchedCommand!!.keyword)
        assertEquals(0, result.currentParamIndex)
    }

    @Test
    fun `无法识别的输入不报错`() {
        val result = complete("这不是命令")
        assertEquals(CommandCompletion.Stage.COMMAND_NAME, result.stage)
        assertTrue(result.items.isEmpty())
    }

    // =======================================================================
    // 参数阶段 —— 用户明确要求的两个场景
    // =======================================================================

    @Test
    fun `give 后面跟空格时提示目标选择器`() {
        val result = complete("/give ")
        assertEquals(CommandCompletion.Stage.ARGUMENT, result.stage)
        assertEquals("give", result.matchedCommand!!.keyword)
        assertEquals(0, result.currentParamIndex)

        val values = result.items.map { it.value }
        assertTrue("@s 应在候选里：$values", values.contains("@s"))
        assertTrue("@a 应在候选里：$values", values.contains("@a"))
        assertTrue("@p 应在候选里：$values", values.contains("@p"))
        assertTrue("@e 应在候选里：$values", values.contains("@e"))
        // 并且带说明，玩家不用计符号含义
        assertTrue(result.items.first { it.value == "@a" }.detail.isNotEmpty())
    }

    @Test
    fun `选择器候选只插 @e 不带方括号`() {
        // 玩家要求：补全只写 `@e`，不要连 `[]` 一起塞进去 ——
        // 方括号里的参数是玩家自己写的，App 不该替他决定。
        val items = complete("/give ").items
        items.forEach {
            assertTrue(
                "选择器候选不该含方括号：${it.value}",
                !it.value.contains('[') && !it.value.contains(']'),
            )
        }
        assertTrue("应有 @e", items.any { it.value == "@e" })
    }

    @Test
    fun `已经打了左方括号时点选择器不会吃掉它`() {
        // 回归：原来替换整个 token，`@[]` 会被整个换成 `@e`，方括号就没了。
        val result = complete("/give @[]")
        val item = result.items.first { it.value == "@e" }
        val (merged, _) = CommandCompletion.apply("/give @[]", result, item, appendSpace = false)
        assertEquals("/give @e[]", merged)
    }

    @Test
    fun `选择器填完后提示物品 ID`() {
        val result = complete("/give @s ")
        assertEquals(CommandCompletion.Stage.ARGUMENT, result.stage)
        assertEquals(1, result.currentParamIndex)

        val values = result.items.map { it.value }
        // 物品表有近 300 条，不能断言某一条一定在候选里（顺序可能变），
        // 只断言"给出的是物品候选"以及"中文名都在"。
        assertTrue("应有物品候选：${values.take(5)}", values.isNotEmpty())
        assertTrue("中文名要全：${result.items.take(3)}", result.items.all { it.label.isNotBlank() })
        // 物品阶段不该再混进选择器
        assertTrue(values.none { it == "@s" })
        // 打上几个字母后必须能定位到具体物品
        val narrowed = complete("/give @s diamond_sword").items.map { it.value }
        assertTrue("输入完整 ID 后应命中：$narrowed", narrowed.contains("diamond_sword"))
    }

    @Test
    fun `物品 ID 打到一半时只留匹配的候选`() {
        val result = complete("/give @s dia")
        assertTrue(result.items.isNotEmpty())
        // 显示名与 ID 都参与"包含"匹配，所以只要求每一项至少命中其中之一。
        result.items.forEach { item ->
            assertTrue(
                "候选 ${item.value} / ${item.label} 与输入 dia 无关",
                item.value.contains("dia", ignoreCase = true) ||
                    item.label.contains("dia", ignoreCase = true)
            )
        }
        assertTrue("应包含钻石剑", result.items.any { it.value == "diamond_sword" })
    }

    @Test
    fun `titleraw 的显示位置与操作按候选值给出`() {
        // 第 2 个参数（下标 1）是显示位置
        val position = complete("/titleraw @a ").items.map { it.value }
        assertTrue(position.contains("title"))
        assertTrue(position.contains("actionbar"))

        // 第 3 个参数（下标 2）是操作。
        // 注意没有 set —— 基岩版 titleraw 直接跟 JSON，不存在 set 关键字。
        val action = complete("/titleraw @a title ").items.map { it.value }
        assertTrue("操作候选：$action", action.contains("show"))
        assertTrue(action.contains("times"))
        assertTrue(action.contains("clear"))
        assertTrue("不该出现 set：$action", !action.contains("set"))
    }

    @Test
    fun `titleraw 的 JSON 参数给出可直接用的骨架`() {
        val values = complete("/titleraw @a title set ").items.map { it.value }
        assertTrue("应有 JSON 骨架：${values.take(3)}", values.any { it.contains("rawtext") })
        // 骨架必须是合法 JSON
        values.filter { it.startsWith("{") }.forEach {
            assertNull("骨架不是合法 JSON：$it", TextComponent.validate(it))
        }
    }

    @Test
    fun `effect 提示操作与效果 ID`() {
        // ⚠️ 参数顺序变了：现在第 2 个是 `mode`（give / clear），第 3 个才是效果 ID。
        //    早先 clear 与效果名共用一个键，会生成 `effect @s clear clear`。
        val modes = complete("/effect @s ").items.map { it.value }
        assertTrue("应给出操作候选：$modes", modes.contains("give"))
        assertTrue("应给出 clear：$modes", modes.contains("clear"))

        // 选完操作之后才给效果 ID
        val effects = complete("/effect @s give ").items.map { it.value }
        assertTrue("应给出效果 ID：${effects.take(5)}", effects.contains("speed"))
        assertTrue("应给出效果 ID：${effects.take(5)}", effects.contains("strength"))
    }

    @Test
    fun `gamerule 提示规则名与真假值`() {
        val rules = complete("/gamerule ").items.map { it.value }
        assertTrue(rules.contains("keepInventory"))
        assertTrue(rules.contains("mobGriefing"))

        val bools = complete("/gamerule keepInventory ").items.map { it.value }
        assertTrue(bools.contains("true"))
        assertTrue(bools.contains("false"))
    }

    @Test
    fun `summon 提示实体 ID`() {
        val values = complete("/summon ").items.map { it.value }
        assertTrue(values.contains("zombie"))
        assertTrue(values.contains("armor_stand"))
    }

    @Test
    fun `参数填满后不再给候选`() {
        // gamemode 只有两个参数，都填完后不该继续提示
        val result = complete("/gamemode creative @s ")
        assertEquals(CommandCompletion.Stage.ARGUMENT, result.stage)
        assertTrue(result.items.isEmpty())
        assertTrue("该提示已经填完：${result.placeholder}", result.placeholder.isNotEmpty())
    }

    // =======================================================================
    // 整数参数：按用户要求不给候选，只给"请输入整数"
    // =======================================================================

    @Test
    fun `整数参数不给候选只给提示`() {
        // 用户明确要求：数量这类参数不做自动补全，写"请输入整数"即可。
        val result = complete("/give @s diamond_sword ")
        assertEquals(CommandCompletion.Stage.ARGUMENT, result.stage)
        assertEquals("give", result.matchedCommand!!.keyword)
        assertEquals(2, result.currentParamIndex)
        assertTrue("整数参数不该给候选：${result.items.map { it.value }}", result.items.isEmpty())
        assertEquals("请输入整数", result.placeholder)
    }

    @Test
    fun `所有整数参数都不给候选`() {
        // 逐条扫一遍命令库：任何 NUMBER 参数都不该产出候选。
        CommandLibrary.all.forEach { spec ->
            spec.params.forEachIndexed { index, param ->
                if (param.type == com.xanan.cmdhelper.json.ParamType.NUMBER) {
                    val items = CommandCompletion.suggestionsFor(spec, index, "")
                    assertTrue(
                        "${spec.keyword} 的整数参数「${param.label}」不该有候选：${items.map { it.value }}",
                        items.isEmpty()
                    )
                }
            }
        }
    }

    @Test
    fun `填了整数之后继续提示下一个参数`() {
        // 数量填完后应当回到"可以有候选"的状态（物品的组件 JSON 参数）。
        val result = complete("/give @s diamond_sword 1 ")
        assertEquals(CommandCompletion.Stage.ARGUMENT, result.stage)
        // 第 4 个参数是"数据值"（也是整数）→ 仍然只给提示
        assertEquals(3, result.currentParamIndex)
        assertEquals("请输入整数", result.placeholder)
    }

    // =======================================================================
    // 分词与光标
    // =======================================================================

    @Test
    fun `JSON 里的空格不会打乱参数计数`() {
        // JSON 组件内部有空格，但它仍然只是"第 4 个参数"
        val result = complete("/titleraw @a title set {\"rawtext\":[{\"text\":\"a b c\"}]}")
        assertEquals(CommandCompletion.Stage.ARGUMENT, result.stage)
        assertEquals(3, result.currentParamIndex)
    }

    @Test
    fun `连续多个空格不会把参数序号推偏`() {
        // 手指在悬浮面板上打字很容易多打空格。基岩版把连续空格当单个分隔符，
        // 补全也必须这样 —— 否则 `/give  @s ` 会被算成"第 3 个参数"，
        // 给出"数量"的候选而不是"物品 ID"（实测踩过）。
        // ⚠️ 断言不能依赖"某条候选恰好在第一屏"：物品表已经有 550 多条，
        //    候选按 150 条截断（MAX_ITEMS），`diamond_sword` 会被挤出可见范围。
        //    这里先输入几个字母把范围收窄 —— 这也正是玩家的真实用法。
        val result = complete("/give  @s diamond_sw")
        assertEquals(CommandCompletion.Stage.ARGUMENT, result.stage)
        assertEquals("give", result.matchedCommand!!.keyword)
        assertEquals(1, result.currentParamIndex)
        assertTrue(
            "应给出物品候选：${result.items.take(3).map { it.value }}",
            result.items.any { it.value == "diamond_sword" }
        )
    }

    @Test
    fun `补全只看光标左侧的内容`() {
        // 光标停在 "give" 之后，右侧的 "@s" 不该参与判断
        val text = "/give @s"
        val result = CommandCompletion.complete(text, 5)
        assertEquals("give", result.matchedCommand!!.keyword)
        assertEquals(0, result.currentParamIndex)
    }

    // =======================================================================
    // 插入行为
    // =======================================================================

    @Test
    fun `点击参数候选会替换当前这一词并补空格`() {
        val base = "/give @s "
        val result = complete(base)
        val (merged, cursor) =
            CommandCompletion.apply(base, result, CommandCompletion.Item("diamond_sword"))
        assertEquals("/give @s diamond_sword ", merged)
        assertEquals(merged.length, cursor)
    }

    @Test
    fun `点击候选会替换掉已经打了一半的词而不是追加`() {
        val base = "/give @s dia"
        val result = complete(base)
        val (merged, _) =
            CommandCompletion.apply(base, result, CommandCompletion.Item("diamond_sword"))
        assertEquals("/give @s diamond_sword ", merged)
    }

    @Test
    fun `不补空格时用于插入 JSON 这类不需要空格的场合`() {
        val base = "/titleraw @a title set "
        val result = complete(base)
        val (merged, _) = CommandCompletion.apply(
            base, result, CommandCompletion.Item("{\"rawtext\":[]}"), appendSpace = false
        )
        assertEquals("/titleraw @a title set {\"rawtext\":[]}", merged)
    }

    @Test
    fun `插入后保证恰好一个空格结尾`() {
        // 用户反馈过"点自动补全不会自动加空格"，所以这条钉死：
        // 无论右侧有没有空格，插入结果都必须**恰好一个**空格结尾。
        val base = "/give "
        val result = complete(base)

        val (once, cursor) = CommandCompletion.apply(base, result, CommandCompletion.Item("@s"))
        assertEquals("/give @s ", once)
        assertEquals(once.length, cursor)

        // 右侧本来就有空格时不能补成两个
        val withTrailingContent = "/give 3"
        val r2 = CommandCompletion.complete(withTrailingContent, 6)
        val (merged2, _) = CommandCompletion.apply(
            withTrailingContent, r2, CommandCompletion.Item("@s")
        )
        assertTrue("不能出现两个连续空格：$merged2", !merged2.contains("  "))
    }

    @Test
    fun `补全结果不依赖 Android 环境`() {
        // 这条测试的意义：证明整个补全引擎是纯 JVM 逻辑 ——
        // 一旦有人往里面加了 Context/View 依赖，这条会先在编译期炸掉。
        assertNotNull(CommandCompletion.complete("/give @s "))
    }

    // =======================================================================
    // 中文名（需求：每个 ID 都要有中文）
    // =======================================================================

    /**
     * 取某个输入下、某个英文 ID 对应的候选，用来检查它的显示名。
     *
     * 为什么需要回退：候选列表现在**有上限**（`MAX_ITEMS`，音效 1671 条时是必须的），
     * 所以目标项可能不在被截断后的那一屏里。这时退回"该条命令的全部候选"去找。
     * 这不是绕过测试 —— 中文名本身仍然要求存在，只是不要求它排在第一屏。
     */
    private fun labelOf(input: String, value: String): String? {
        val result = complete(input)
        result.items.firstOrNull { it.value == value }?.let { return it.label }
        val spec = result.matchedCommand ?: return null
        return CommandCompletion.allSuggestionsFor(spec).firstOrNull { it.value == value }?.label
    }

    /**
     * 在**整条命令的全部候选**里找一个 ID 的显示名。
     *
     * 断言不能用 `complete(...)`：它只返回"光标当前位置"的候选，
     * 而方块表有 900 多条，目标项很可能不在第一屏里。
     */
    private fun fullLabelOf(command: String, value: String): String? {
        val spec = CommandLibrary.find(command) ?: return null
        return CommandCompletion.allSuggestionsFor(spec).firstOrNull { it.value == value }?.label
    }

    /**
     * 断言显示名**包含**某个中文名。
     *
     * 不能用相等：官方译名后面可能还跟着俗名（"钻石护腿 / 钻石裤"），
     * 这是刻意的设计，不是脏数据。
     */
    private fun assertLabel(input: String, value: String, expected: String) {
        val label = labelOf(input, value)
        assertNotNull("候选里没有 $value（输入 $input）", label)
        assertTrue("$value 的显示名是「$label」，应包含「$expected」", label!!.contains(expected))
    }

    /** 同上，但在整条命令的全量候选里查（词表很大、目标项不在第一屏时用）。 */
    private fun assertFullLabel(command: String, value: String, expected: String) {
        val label = fullLabelOf(command, value)
        assertNotNull("$command 的候选里没有 $value", label)
        assertTrue("$value 的显示名是「$label」，应包含「$expected」", label!!.contains(expected))
    }

    @Test
    fun `物品候选带中文名`() {
        assertLabel("/give @s ", "diamond_sword", "钻石剑")
        assertLabel("/give @s ", "netherite_sword", "下界合金剑")
        assertLabel("/give @s ", "golden_apple", "金苹果")
        // command_block 是**方块**，不在 give 的物品表里，要用方块命令去查。
        assertFullLabel("setblock", "command_block", "命令方块")
    }

    @Test
    fun `实体候选带中文名`() {
        assertLabel("/summon ", "zombie", "僵尸")
        assertLabel("/summon ", "creeper", "苦力怕")
        assertLabel("/summon ", "ender_dragon", "末影龙")
        assertLabel("/summon ", "armor_stand", "盔甲架")
    }

    @Test
    fun `方块候选带中文名`() {
        assertFullLabel("setblock", "stone", "石头")
        assertFullLabel("setblock", "grass_block", "草")
        assertFullLabel("setblock", "barrier", "屏障")
    }

    @Test
    fun `状态效果候选带中文名`() {
        // ⚠️ `give` / `clear` 这类**固定候选**（CHOICE）的显示名就是它自己
        //    （`give`、`clear`），中文说明放在 detail 里 —— 与词表候选
        //    （`speed` 显示成「速度」）不是同一种展示方式。
        //    所以这里对操作查 detail，对效果 ID 查 label。
        assertDetail("/effect @s ", "give", "给予")
        assertDetail("/effect @s ", "clear", "清除")
        assertLabel("/effect @s give ", "speed", "速度")
        assertLabel("/effect @s give ", "strength", "力量")
    }

    /** 在候选里找一个值的**说明**（detail）里是否含某段中文。 */
    private fun assertDetail(input: String, value: String, expected: String) {
        val result = complete(input)
        val item = result.items.firstOrNull { it.value == value }
        assertNotNull("候选里没有 $value（输入 $input）", item)
        assertTrue(
            "$value 的说明是「${item!!.detail}」，应包含「$expected」",
            item.detail.contains(expected),
        )
    }

    @Test
    fun `游戏规则候选带中文说明`() {
        assertLabel("/gamerule ", "keepInventory", "保留物品栏")
        assertLabel("/gamerule ", "doDaylightCycle", "昼夜")
    }

    @Test
    fun `游戏模式候选带中文名`() {
        assertEquals("创造", labelOf("/gamemode ", "creative"))
        assertEquals("旁观", labelOf("/gamemode ", "spectator"))
    }

    @Test
    fun `俗名也写在显示名里`() {
        // 用户的明确要求：官方译名与常用俗名都要能搜到。
        // 苦力怕是官方译名、爬行者是旧译/俗名；护腿官方、裤是玩家叫法。
        assertLabel("/summon ", "creeper", "爬行者")
        assertLabel("/give @s ", "diamond_leggings", "钻石裤")
        assertLabel("/give @s ", "diamond_chestplate", "钻石甲")
        assertLabel("/give @s ", "elytra", "翅膀")
    }

    @Test
    fun `按中文或俗名都能搜到候选`() {
        // 显示名参与过滤且用"包含"：打「钻石」应列出钻石制品。
        val byChinese = complete("/give @s 钻石").items.map { it.value }
        assertTrue("中文搜索应命中钻石剑：${byChinese.take(5)}", byChinese.contains("diamond_sword"))
        assertTrue(byChinese.contains("diamond_leggings"))

        // 俗名同样能搜到
        val byAlias = complete("/give @s 裤").items.map { it.value }
        assertTrue(
            "搜「裤」应命中护腿：${byAlias.take(5)}",
            byAlias.any { it.endsWith("_leggings") }
        )
    }

    @Test
    fun `所有词表项都填了中文名且不重复`() {
        // 这几张表就是"玩家要按中文找东西"的地方，任何一条缺中文名都要当场发现。
        val tables = mapOf(
            "物品" to Suggest.items,
            "方块" to Suggest.blocks,
            "实体" to Suggest.entities,
            "状态效果" to Suggest.effects,
            "粒子" to Suggest.particles,
            "音效" to Suggest.sounds,
            "游戏规则" to Suggest.gamerules
        )
        tables.forEach { (name, terms) ->
            assertTrue("$name 词表为空", terms.isNotEmpty())
            // 用 Set 判重，别用 count{} —— 音效有 1671 条，平方复杂度会让测试明显变慢
            val seen = HashSet<String>()
            terms.forEach { term ->
                assertTrue("$name 的 '${term.id}' 没有中文名", term.name.isNotBlank())
                assertTrue("$name 的 '${term.id}' 中文名与 ID 相同，没起到翻译作用", term.name != term.id)
                assertTrue("$name 的 '${term.id}' 重复", seen.add(term.id))
            }
        }
    }

    // =======================================================================
    // 音效 / 粒子（用户要求"能写中文的就写上中文"）
    // =======================================================================

    @Test
    fun `音效词表规模够用`() {
        assertTrue("音效太少：${Suggest.sounds.size}", Suggest.sounds.size > 1000)
    }

    @Test
    fun `粒子词表带命名空间`() {
        assertTrue("粒子太少：${Suggest.particles.size}", Suggest.particles.size > 100)
        Suggest.particles.forEach {
            assertTrue("粒子 ID 应带命名空间：${it.id}", it.id.startsWith("minecraft:"))
        }
    }

    @Test
    fun `playsound 的音效参数给词表候选并显示中文`() {
        // 结构：playsound <音效> [频道] [目标] …，所以下标 0 就是音效
        val items = complete("/playsound ").items
        assertEquals("候选应被截断到上限", CommandCompletion.MAX_ITEMS, items.size)
        assertTrue("应给音效候选", items.size > 100)

        // 用具体 ID 缩小范围再查（全表 1671 条会被截断，不能直接找）
        val levelup = complete("/playsound random.levelup").items
            .firstOrNull { it.value == "random.levelup" }
        assertNotNull("应有 random.levelup", levelup)
        assertTrue(
            "音效应显示中文名：${levelup!!.label}",
            levelup.label != levelup.value && levelup.label.any { it.code > 0x2E80 }
        )
    }

    @Test
    fun `particle 的粒子参数给词表候选`() {
        val items = complete("/particle ").items
        assertTrue("应给粒子候选", items.size > 100)
        assertTrue("应有基础火焰", items.any { it.value == "minecraft:basic_flame" })
    }

    @Test
    fun `音效与粒子插入的仍然是英文 ID`() {
        val soundResult = complete("/playsound random.levelup")
        val sound = soundResult.items.first { it.value == "random.levelup" }
        val (sm, _) = CommandCompletion.apply("/playsound random.levelup", soundResult, sound, appendSpace = false)
        assertTrue("插入的应是英文 ID：$sm", sm.contains("random.levelup"))
        assertTrue("不该插入中文名：$sm", !sm.contains(sound.label))

        val pResult = complete("/particle ")
        val p = pResult.items.first { it.value == "minecraft:basic_flame" }
        val (pm, _) = CommandCompletion.apply("/particle ", pResult, p, appendSpace = false)
        assertTrue("粒子插入的应是 ID：$pm", pm.contains("minecraft:basic_flame"))
    }

    @Test
    fun `实体的官方名与俗名并存（闪电）`() {
        // 用户说"闪电的官方译名好像是什么束"。
        // 实测官方 `entity.lightning_bolt.name` 就是「闪电」（见 zh_CN.lang）；
        // 把「闪电束」作为俗名一起写上，两种叫法都能搜到。
        val bolt = Suggest.entities.first { it.id == "lightning_bolt" }
        assertTrue("应含官方名：${bolt.name}", bolt.name.contains("闪电"))
        assertTrue("应含俗名闪电束：${bolt.name}", bolt.name.contains("闪电束"))

        assertTrue(complete("/summon 闪电").items.any { it.value == "lightning_bolt" })
        assertTrue(complete("/summon 闪电束").items.any { it.value == "lightning_bolt" })
    }

    @Test
    fun `实体词表覆盖近年新增的生物`() {
        assertTrue("实体太少：${Suggest.entities.size}", Suggest.entities.size >= 130)
        listOf("armadillo", "bogged", "breeze", "creaking", "sniffer", "allay", "camel", "goat")
            .forEach { id ->
                assertTrue("缺少实体 $id", Suggest.entities.any { it.id == id })
            }
    }

    @Test
    fun `插入用的仍然是英文 ID 而不是中文名`() {
        // 中文名只用于显示；一旦被插进命令，命令就废了。这条把它钉死。
        //
        // ⚠️ 同样先收窄再取：物品表 550+ 条，候选截断到 150 条之后
        //    `diamond_sword` 不在第一屏里。
        val base = "/give @s diamond_sw"
        val result = complete(base)
        val item = result.items.first { it.value == "diamond_sword" }
        assertEquals("钻石剑", item.label)
        val (merged, _) = CommandCompletion.apply(base, result, item)
        assertTrue("插入结果里不能出现中文名：$merged", !merged.contains("钻石剑"))
        assertEquals("/give @s diamond_sword ", merged)
    }

    // =======================================================================
    // 中文搜索音效 / 粒子（用户报过"particle 搜中文搜不到"）
    //
    // 实测引擎本身是通的；真正的问题是另外两个：
    //   1. **一条都没命中时静默退回全表** → 看上去像搜索没生效；
    //   2. 音效 1671 条候选**全量渲染** → 悬浮面板卡死。
    // 下面把这两条行为连同中文搜索本身一起钉死。
    // =======================================================================

    @Test
    fun `按中文搜粒子能命中`() {
        fun hits(q: String) = complete("/particle $q").items.map { it.value }

        assertTrue("搜「火焰」应命中：${hits("火焰").take(3)}", hits("火焰").contains("minecraft:basic_flame"))
        assertTrue(hits("火焰").contains("minecraft:blue_flame"))
        assertTrue("搜「爆炸」应命中", hits("爆炸").contains("minecraft:explosion_level"))
        assertTrue("搜「基础」应命中", hits("基础").contains("minecraft:basic_smoke"))
        assertEquals("搜「爱心」应只剩一条", listOf("minecraft:heart"), hits("爱心"))
    }

    @Test
    fun `按中文搜音效能命中且带可搜索别名`() {
        val hits = complete("/playsound 爆炸").items.map { it.value }
        assertTrue("搜「爆炸」应命中：$hits", hits.any { it.contains("explode") })

        // 官方字幕名经常看不出是什么音效（random.levelup 的字幕是"玩家叮当作响"），
        // 所以补了可搜索别名；搜「升级」必须能命中。
        val levelup = complete("/playsound 升级").items
        assertTrue(
            "搜「升级」应命中 random.levelup：${levelup.map { it.value }}",
            levelup.any { it.value == "random.levelup" }
        )
        val label = levelup.first { it.value == "random.levelup" }.label
        assertTrue("应保留官方名：$label", label.contains("玩家叮当作响"))
        assertTrue("应含别名：$label", label.contains("升级"))
    }

    @Test
    fun `候选过多时截断并给出说明`() {
        // 音效 1671 条，全渲染会卡死面板，必须截断并告知。
        val all = complete("/playsound ")
        assertEquals(CommandCompletion.MAX_ITEMS, all.items.size)
        assertTrue("应说明还有多少条：${all.truncatedNote}", all.truncatedNote.contains("还有"))

        // 缩小范围后不该再截断，也不该有说明
        val narrow = complete("/playsound 爆炸")
        assertTrue("缩小后条数应少于上限", narrow.items.size < CommandCompletion.MAX_ITEMS)
        assertEquals("", narrow.truncatedNote)
    }

    // =======================================================================
    // 中文命令名（用户报过"我输入 /粒子 没有匹配到 particle"）
    //
    // 之前只给**参数值**做了中文，命令名一直是纯英文匹配，所以打中文一条都不出。
    // =======================================================================

    @Test
    fun `打中文命令名能匹配到英文命令`() {
        // 用户报的就是这一条
        val particle = complete("/粒子")
        assertEquals("打 /粒子 应匹配 particle", "particle", particle.matchedCommand?.keyword)

        // 打完中文名 + 空格后，应进入参数阶段并给出粒子候选
        val withSpace = complete("/粒子 ")
        assertEquals("particle", withSpace.matchedCommand?.keyword)
        assertEquals("应停在第 1 个参数", 0, withSpace.currentParamIndex)
        assertTrue("应给粒子候选", withSpace.items.any { it.value == "minecraft:basic_flame" })

        // 其它常用命令也要能按中文找。
        // `tp` 与 `teleport` 别名相同（都是"传送"），应当优先匹配**更短的关键字**。
        assertEquals("playsound", complete("/音效").matchedCommand?.keyword)
        assertEquals("give", complete("/给予").matchedCommand?.keyword)
        assertEquals("tp", complete("/传送").matchedCommand?.keyword)
        assertEquals("summon", complete("/生成生物").matchedCommand?.keyword)
        assertEquals("gamemode", complete("/游戏模式").matchedCommand?.keyword)
        assertEquals("fill", complete("/填充方块").matchedCommand?.keyword)
    }

    @Test
    fun `打中文但还没打完时给出命令候选`() {
        // 别名匹配不到整条时，应退回"命令名候选"让玩家挑。
        // 注意：别名能**整条命中**时（如 `/粒子`）会直接进参数阶段，这是刻意的 —— 少点一次。
        val items = complete("/计分板").items.map { it.value }
        assertTrue("打 /计分板 应给出计分板相关命令：$items", items.any { it.startsWith("scoreboard") })
        assertTrue(items.contains("scoreboard objectives add"))
    }

    @Test
    fun `中文命令名的候选里显示中文但插入英文关键字`() {
        // 用"还没打完"的输入去看候选（整条命中时直接进参数阶段了）
        val items = complete("/计分板目标").items
        val hit = items.firstOrNull { it.value == "scoreboard objectives add" }
        assertNotNull("候选里应有 scoreboard objectives add：${items.take(3).map { it.value }}", hit)
        assertTrue("应显示中文名：${hit!!.label}", hit.label.contains("计分板"))

        val result = complete("/计分板目标")
        val (merged, _) = CommandCompletion.apply("/计分板目标", result, hit, appendSpace = false)
        // 输入里的 `/` 是原有的，替换的只是命令名那一段
        assertEquals("插入的必须是英文关键字", "/scoreboard objectives add", merged)
    }

    @Test
    fun `多词命令的中文名也能匹配`() {
        // ⚠️ 这里原来用的是 `team add`（队伍 创建），
        //    但 team 是 Java 专有命令，已按维基从基岩版命令库里删除
        //    （见 BedrockSyntaxTest 与核对报告）。换成其它带空格的中文别名。
        assertEquals("scoreboard objectives add", complete("/计分板目标 新建").matchedCommand?.keyword)
        assertEquals("structure save", complete("/结构 保存").matchedCommand?.keyword)
        assertEquals("schedule delay add", complete("/计划任务 延迟").matchedCommand?.keyword)
        // 只打一部分时应给出候选，而不是一条都没有
        assertTrue(
            "打 /计分板 应给出候选：${complete("/计分板").items.take(3).map { it.value }}",
            complete("/计分板").items.isNotEmpty()
        )
    }

    @Test
    fun `每条命令都配了中文名`() {
        val missing = CommandLibrary.all.filter { it.aliases.isEmpty() }
        assertTrue("这些命令没有中文名：${missing.map { it.keyword }}", missing.isEmpty())
    }
}
