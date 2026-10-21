package com.xanan.cmdhelper.json

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 命令生成逻辑的单元测试。
 *
 * 这些测试的作用是把「基岩版命令语法」这件事**钉死**：语法出错时命令在游戏里
 * 只会静默失败（或者报一个看不懂的错），靠人工点界面很难发现。有了测试，
 * 以后调整参数或新增命令时，能立刻知道有没有把老命令改坏。
 *
 * 全部测试都在纯 JVM 上跑，不需要设备：
 *   ./gradlew :app:testDebugUnitTest
 */
class CommandBuilderTest {

    // =======================================================================
    // JSON 文本组件
    // =======================================================================

    @Test
    fun `rawtext 单段文本结构正确`() {
        val json = TextComponent.rawText().text("欢迎来到服务器").toJsonString()
        assertEquals("""{"rawtext":[{"text":"欢迎来到服务器"}]}""", json)
    }

    @Test
    fun `rawtext 支持选择器与计分板组件混排`() {
        val json = TextComponent.rawText()
            .text("欢迎 ")
            .selector("@s")
            .text(" 你的分数是 ")
            .score("@s", "分数")
            .toJsonString()

        // 顺序必须与调用顺序一致，游戏按数组顺序拼接显示。
        assertEquals(
            """{"rawtext":[{"text":"欢迎 "},{"selector":"@s"},{"text":" 你的分数是 "},{"score":{"name":"@s","objective":"分数"}}]}""",
            json
        )
    }

    @Test
    fun `rawtext 正确转义引号与反斜杠`() {
        val json = TextComponent.rawText().text("""他说"你好"\世界""").toJsonString()
        // 引号与反斜杠必须被转义，否则游戏解析到这里就断了。
        assertTrue(json.contains("""\"你好\""""))
        assertTrue(json.contains("""\\世界"""))
    }

    @Test
    fun `translate 组件带参数`() {
        val json = TextComponent.rawText()
            .translate("item.diamond_sword")
            .translate("death.attack.mob", listOf("僵尸"))
            .toJsonString()
        assertTrue(json.contains(""""translate":"item.diamond_sword""""))
        assertTrue(json.contains(""""with":["僵尸"]"""))
    }

    @Test
    fun `separator 会在相邻文本之间插入分隔`() {
        val json = TextComponent.rawText(separator = "、")
            .text("A").text("B").toJsonString()
        assertEquals("""{"rawtext":[{"text":"A"},{"text":"、"},{"text":"B"}]}""", json)
    }

    @Test
    fun `validate 能识别合法与非法 JSON`() {
        assertNull(TextComponent.validate("""{"rawtext":[]}"""))
        assertNull(TextComponent.validate("""[1,2,3]"""))
        assertNotNull(TextComponent.validate(""))
        assertNotNull(TextComponent.validate("rawtext"))
        assertNotNull(TextComponent.validate("""{"rawtext":["""))
    }

    @Test
    fun `compact 去掉多余空白但不改变语义`() {
        val pretty = """
            {
              "rawtext": [
                { "text": "你好" }
              ]
            }
        """.trimIndent()
        assertEquals("""{"rawtext":[{"text":"你好"}]}""", TextComponent.compact(pretty))
    }

    // =======================================================================
    // titleraw
    //
    // ⚠️ 基岩版的 titleraw **没有 set 这个关键字**：
    //      titleraw <目标> <位置> <JSON>       ← 位置后面直接跟 JSON
    //      titleraw <目标> times <淡入> <停留> <淡出>
    //      titleraw <目标> <位置> clear|reset
    //    早先照 Java 版的 `/title <目标> title <文本>` 写成了 `... set ...`，
    //    生成的命令在游戏里会被判为语法错误。这几条测试就是防止再写回去。
    // =======================================================================

    @Test
    fun `titleraw 直接跟 JSON 且不带 set`() {
        val spec = CommandLibrary.find("titleraw")!!
        val command = spec.build(
            mapOf(
                "target" to "@a",
                "position" to "title",
                "json" to """{"rawtext":[{"text":"你好"}]}"""
            )
        )
        assertEquals("""titleraw @a title {"rawtext":[{"text":"你好"}]}""", command)
        assertTrue("titleraw 里不该出现 set：$command", !command!!.contains(" set "))
    }

    @Test
    fun `titleraw 缺少 JSON 时不生成命令`() {
        val spec = CommandLibrary.find("titleraw")!!
        assertNull(spec.build(mapOf("target" to "@a", "position" to "title", "json" to "")))
    }

    @Test
    fun `titleraw clear 不需要 JSON`() {
        val spec = CommandLibrary.find("titleraw")!!
        assertEquals(
            "titleraw @a clear",
            spec.build(mapOf("target" to "@a", "position" to "actionbar", "action" to "clear"))
        )
    }

    @Test
    fun `titleraw times 使用三个数字且顺序为 淡入 停留 淡出`() {
        val spec = CommandLibrary.find("titleraw")!!
        // times 不接受位置参数，直接跟三个数字
        assertEquals(
            "titleraw @a times 5 40 10",
            spec.build(
                mapOf(
                    "target" to "@a", "action" to "times",
                    "fadeIn" to "5", "stay" to "40", "fadeOut" to "10"
                )
            )
        )
    }

    // =======================================================================
    // give
    // =======================================================================

    @Test
    fun `give 基础形式`() {
        val spec = CommandLibrary.find("give")!!
        assertEquals(
            "give @s diamond_sword 1",
            spec.build(mapOf("target" to "@s", "item" to "diamond_sword", "amount" to "1"))
        )
    }

    @Test
    fun `give 带组件时必须先补数据值占位`() {
        val spec = CommandLibrary.find("give")!!
        val command = spec.build(
            mapOf(
                "target" to "@s",
                "item" to "diamond_sword",
                "amount" to "2",
                "data" to "",
                "components" to """{"item_name":"宝剑"}"""
            )
        )
        // 组件前面必须有 [数量] [数据值]，缺了数据值游戏会把组件当数据值解析。
        assertEquals("""give @s diamond_sword 2 0 {"item_name":"宝剑"}""", command)
    }

    @Test
    fun `give 缺少物品 ID 时不生成命令`() {
        val spec = CommandLibrary.find("give")!!
        assertNull(spec.build(mapOf("target" to "@s", "item" to "", "amount" to "1")))
    }

    // =======================================================================
    // 计分板与其它
    // =======================================================================

    @Test
    fun `scoreboard objectives setdisplay 只有 belowname 不输出排序`() {
        // ⚠️ 这条改过：维基 BE 的语法是
        //      setdisplay <list|sidebar> [objective] [ascending|descending]
        //      setdisplay belowname [objective]
        //    也就是 **list 也接受排序参数**，只有 belowname 不接受。
        //    早先写成"只有 sidebar 才输出排序"，把 list 的排序能力漏掉了。
        val spec = CommandLibrary.find("scoreboard objectives setdisplay")!!

        // list + 排序：要输出
        assertEquals(
            "scoreboard objectives setdisplay list 分数 ascending",
            spec.build(mapOf("slot" to "list", "objective" to "分数", "order" to "ascending"))
        )
        // sidebar + 排序：要输出
        assertEquals(
            "scoreboard objectives setdisplay sidebar 分数 descending",
            spec.build(mapOf("slot" to "sidebar", "objective" to "分数", "order" to "descending"))
        )
        // belowname：**不**接受排序
        assertEquals(
            "scoreboard objectives setdisplay belowname 分数",
            spec.build(mapOf("slot" to "belowname", "objective" to "分数", "order" to "descending"))
        )
    }

    @Test
    fun `effect clear 是独立形式`() {
        val spec = CommandLibrary.find("effect")!!
        // ⚠️ 用 `mode=clear` 而不是 `effect=clear`：维基 BE 的 clear 是**子命令**，
        //    不是效果名。早先把两者合成一个键，会生成 `effect @a clear clear` —— 见 BedrockSyntaxTest。
        assertEquals(
            "effect @a clear",
            spec.build(mapOf("target" to "@a", "mode" to "clear"))
        )
    }

    @Test
    fun `summon 的名称是纯字符串不是 JSON`() {
        val spec = CommandLibrary.find("summon")!!
        val command = spec.build(
            mapOf(
                "entity" to "armor_stand",
                "x" to "~", "y" to "~", "z" to "~",
                "nameTag" to "守卫 一号"
            )
        )
        assertNotNull(command)
        // 维基 BE：nameTag 类型是 basic_string（纯字符串）。
        // 早先按 Java 的思路包成了 {"nameTag":"..."}，名字里会带上花括号字面量。
        assertFalse("nameTag 不该被包成 JSON：$command", command!!.contains("{"))
        assertTrue("应当直接输出名字：$command", command.contains("守卫 一号"))
    }

    @Test
    fun `tp 只填水平朝向时不会多输出空参数`() {
        val spec = CommandLibrary.find("tp")!!
        assertEquals(
            "tp @s ~ ~ ~ 90",
            spec.build(
                mapOf(
                    "target" to "@s", "x" to "~", "y" to "~", "z" to "~",
                    "yRot" to "90", "xRot" to ""
                )
            )
        )
    }

    @Test
    fun `execute 使用新版子命令语法`() {
        val spec = CommandLibrary.find("execute")!!
        assertEquals(
            "execute as @a at @s if entity @s[tag=队长] run say 你好",
            spec.build(
                mapOf(
                    "target" to "@a",
                    "condition" to "if entity @s[tag=队长]",
                    "command" to "say 你好"
                )
            )
        )
    }

    // =======================================================================
    // 库层面的约束
    // =======================================================================

    @Test
    fun `所有命令的关键字唯一`() {
        val keywords = CommandLibrary.all.map { it.keyword }
        assertEquals(keywords.size, keywords.toSet().size)
    }

    @Test
    fun `所有命令都能用默认值生成出非空命令或明确的空结果`() {
        // 这条不是要求"默认值一定合法"，而是要求 build 不抛异常 ——
        // 默认值下缺少必填项时应当规规矩矩返回 null。
        CommandLibrary.all.forEach { spec ->
            val result = runCatching { spec.build(spec.defaultValues()) }
            assertTrue("${spec.keyword} 的 build 抛异常了: ${result.exceptionOrNull()}", result.isSuccess)
        }
    }

    @Test
    fun `每个参数键在命令内唯一`() {
        CommandLibrary.all.forEach { spec ->
            val keys = spec.params.map { it.key }
            assertEquals("${spec.keyword} 有重复参数键", keys.size, keys.toSet().size)
        }
    }

    @Test
    fun `必填参数都出现在界面默认值里`() {
        CommandLibrary.all.forEach { spec ->
            val defaults = spec.defaultValues()
            spec.requiredKeys.forEach { param ->
                assertTrue(
                    "${spec.keyword} 的必填参数 ${param.key} 没有默认值条目",
                    defaults.containsKey(param.key)
                )
            }
        }
    }
}
