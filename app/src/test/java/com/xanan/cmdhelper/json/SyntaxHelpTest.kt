package com.xanan.cmdhelper.json

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 语法帮助相关逻辑的测试。
 *
 * 对应原版命令助手那个很聪明的动态行为：**输入框有内容时，列表顶部显示这条命令的
 * 语法帮助**（例：`/scoreboard players test <对象:实体> <记分项> <最小值:整数> [*]`）。
 *
 * 原版的模板是每条命令手写的；重制版改成**从参数声明自动生成**，
 * 所以这套生成规则必须被测住，否则新增命令时会悄悄生成出看不懂的模板。
 */
class SyntaxHelpTest {

    @Test
    fun `语法模板把必填参数写成尖括号占位符`() {
        val spec = CommandLibrary.find("scoreboard players set")!!
        assertEquals(
            "scoreboard players set <玩家 / 假玩家名:文本> <目标名:文本> <分数:整数>",
            spec.syntaxTemplate()
        )
    }

    @Test
    fun `下拉参数的候选值会列进语法模板`() {
        // titleraw 的"显示位置"仍是 CHOICE，候选值应当直接写进模板，玩家不用猜。
        val spec = CommandLibrary.find("titleraw")!!
        val template = spec.syntaxTemplate()
        assertTrue("模板应列出候选值: $template", template.contains("title/subtitle/actionbar"))
    }

    @Test
    fun `改用候选词表的参数仍然能生成模板`() {
        // gamemode 的"模式"从 CHOICE 改成了"文本 + 中文候选"（为了在补全列表里显示
        // "创造""旁观"），此时模板里不再罗列候选值 —— 但必须仍然是可读的占位符，
        // 而且候选不能被丢掉（补全还要用它）。
        val spec = CommandLibrary.find("gamemode")!!
        val template = spec.syntaxTemplate()
        assertTrue("模板里应有「模式」占位符: $template", template.contains("<模式:"))
        val mode = spec.params.first { it.key == "mode" }
        assertEquals(4, mode.suggestions.size)
        assertEquals(4, mode.suggestionLabels.size)
        assertTrue("候选里要有中文名", mode.suggestionLabels.contains("创造"))
    }

    @Test
    fun `下拉参数会把候选值直接列进模板`() {
        val spec = CommandLibrary.find("titleraw")!!
        val template = spec.syntaxTemplate()
        // 玩家不用再猜"显示位置"能填什么。
        assertTrue("模板应列出候选值: $template", template.contains("title/subtitle/actionbar"))
    }

    @Test
    fun `有默认值的可选参数不进模板`() {
        val spec = CommandLibrary.find("fill")!!
        val template = spec.syntaxTemplate()
        // blockData 与 mode 都有默认值，模板里不应出现，否则长得没法看。
        assertTrue("模板不该塞进默认值参数: $template", !template.contains("数据值"))
    }

    @Test
    fun `必填参数全部出现在模板里`() {
        CommandLibrary.all.forEach { spec ->
            val template = spec.syntaxTemplate()
            spec.requiredKeys.forEach { param ->
                assertTrue(
                    "${spec.keyword} 的必填参数「${param.label}」没进模板: $template",
                    template.contains("<${param.label}:")
                )
            }
        }
    }

    @Test
    fun `每条命令都能生成非空且以关键字开头的模板`() {
        CommandLibrary.all.forEach { spec ->
            val template = spec.syntaxTemplate()
            assertTrue("${spec.keyword} 的模板为空", template.isNotBlank())
            assertTrue(
                "${spec.keyword} 的模板没以关键字开头: $template",
                template.startsWith(spec.keyword)
            )
        }
    }

    @Test
    fun `参数说明包含必填性和类型`() {
        val spec = CommandLibrary.find("give")!!
        val docs = spec.paramDoc().toMap()
        assertTrue(docs.getValue("物品 ID").startsWith("必填"))
        assertTrue(docs.getValue("数量").startsWith("可选"))
        assertTrue(docs.getValue("数量").contains("整数"))
        assertTrue(docs.getValue("目标").contains("目标选择器"))
    }

    @Test
    fun `命令匹配按最长前缀，不会把 scoreboard 的子命令认错`() {
        // 用被测代码本身（CommandLibrary.match），不是测试里另抄一份规则 ——
        // 抄一份的话，实现改了测试还是绿的，等于没测。
        assertEquals(
            "scoreboard players set",
            CommandLibrary.match("scoreboard players set @s 分数 1")?.keyword
        )
        assertEquals(
            "scoreboard objectives add",
            CommandLibrary.match("/scoreboard objectives add 分数 dummy")?.keyword
        )
        assertEquals(
            "scoreboard objectives setdisplay",
            CommandLibrary.match("scoreboard objectives setdisplay sidebar 分数")?.keyword
        )
        // 多词命令必须严格按词边界匹配：输入 "scoreboard objectives" 不该乱认一个。
        assertNull(CommandLibrary.match("scoreboard objectives"))
        assertNull(CommandLibrary.match("这个是随便打的"))
    }

    @Test
    fun `单词命令允许前缀匹配，方便没打完就出帮助`() {
        assertEquals("give", CommandLibrary.match("giv")?.keyword)
        assertEquals("give", CommandLibrary.match("give")?.keyword)
        assertEquals("titleraw", CommandLibrary.match("titleraw @a title")?.keyword)
        assertEquals("tp", CommandLibrary.match("tp @s ~ ~ ~")?.keyword)
    }

    @Test
    fun `命令库中带空格的命令关键字都能被自身匹配到`() {
        CommandLibrary.all.filter { it.keyword.contains(' ') }.forEach { spec ->
            assertEquals(
                "带空格的命令 ${spec.keyword} 匹配不到自己",
                spec.keyword,
                CommandLibrary.match(spec.keyword)?.keyword
            )
            assertEquals(
                "带空格的命令 ${spec.keyword} 在带上参数后匹配错误",
                spec.keyword,
                CommandLibrary.match(spec.keyword + " <参数>")?.keyword
            )
        }
    }

    @Test
    fun `空白输入匹配不到任何命令`() {
        assertNull(CommandLibrary.match(""))
        assertNull(CommandLibrary.match("   "))
        assertNull(CommandLibrary.match("/"))
    }
}
