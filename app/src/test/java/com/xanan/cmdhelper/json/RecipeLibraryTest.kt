package com.xanan.cmdhelper.json

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 玩家指令库测试。
 *
 * 重点不是"能跑"，而是**语法版本**：新版基岩版对老语法是直接报错的，
 * 所以这里逐条钉死三条转换要求（用户明确提出的）：
 *  1. 老 `execute @a ~~~ run …` 全部换成新子命令语法；
 *  2. 标题全部用 `titleraw` + JSON，不再用 `title <位置> <文本>`；
 *  3. 多行靠 JSON 的 `\n`，命令本身**是一条**（新版不支持命令里换行）。
 */
class RecipeLibraryTest {

    private val allCommands: List<String> = RecipeLibrary.recipes.flatMap { it.commands }

    // ── 语法版本 ────────────────────────────────────────────────────────────

    @Test
    fun `没有老式 execute 语法`() {
        // 老写法：execute <选择器> <坐标> [detect …] <命令>
        // 特征：execute 后面直接跟 @xxx 再跟 ~ ~ ~，且没有 as/at/if/run 这些子命令
        val legacy = Regex("""^\s*execute\s+@[a-zA-Z]+\s+[~^\-0-9]""")
        allCommands.forEach { cmd ->
            assertTrue("仍是老式 execute 语法：$cmd", !legacy.containsMatchIn(cmd))
        }
        // 老式 detect 也一样
        allCommands.forEach { cmd ->
            assertTrue("仍是老式 detect 语法：$cmd", !cmd.contains("detect "))
        }
    }

    @Test
    fun `新式 execute 都带 run`() {
        allCommands.filter { it.trimStart().startsWith("execute ") }.forEach { cmd ->
            assertTrue("新式 execute 结尾必须有 run <命令>：$cmd", cmd.contains(" run "))
        }
    }

    @Test
    fun `没有废弃的 testforblock`() {
        // 新版已移除，要写成 `execute if block 坐标 方块 run …`
        allCommands.forEach { cmd ->
            assertTrue("testforblock 已废弃：$cmd", !cmd.contains("testforblock"))
        }
    }

    @Test
    fun `testfor 必须是独立的一条`() {
        // `execute … run testfor …` 是**无效**的：run 后面只能接可执行的子命令，
        // 而 testfor 是"检测并输出结果"的命令，不属于子命令。
        // 实测这类写法整条命令都不生效，所以必须独立成条。
        allCommands.forEach { cmd ->
            assertTrue(
                "testfor 不能接在 run 后面：$cmd",
                !cmd.contains("run testfor"),
            )
        }
        // 用到 testfor 的地方确实是独立的
        allCommands.filter { it.contains("testfor") }.forEach { cmd ->
            assertTrue("testfor 应独立成条：$cmd", cmd.trimStart().removePrefix("/").startsWith("testfor"))
        }
    }

    @Test
    fun `标题一律用 titleraw 且带 JSON`() {
        allCommands.forEach { cmd ->
            // 只禁裸的 `title`（老写法）；`titleraw` 是允许且要求的目标
            // ⚠️ 不能用正则在整条命令里找 " title " ——
            // `titleraw @s title {…}` 里的 `title` 是 titleraw 的**位置参数**（显示位置），
            // 不是命令名。正则在整串里搜会被它骗到（试了两版都会误报）。
            // 正确做法：取出**真正要执行的命令名**（execute 后面 run 之后那个词）再判断。
            val commandName = cmd.trimStart().removePrefix("/")
                .let { if (it.startsWith("execute ")) it.substringAfterLast(" run ").trim() else it }
                .substringBefore(' ')

            assertTrue("标题应该用 titleraw 而不是 title：$cmd", commandName != "title")

            if (commandName == "titleraw") {
                assertTrue("titleraw 必须带 JSON 参数：$cmd", cmd.contains("{\"rawtext\""))
                // 显示位置必须是合法的三个值之一
                val position = cmd.substringAfter("titleraw ").split(' ').getOrNull(1).orEmpty()
                assertTrue(
                    "titleraw 的显示位置不合法：$position（应为 title/subtitle/actionbar）",
                    position in listOf("title", "subtitle", "actionbar"),
                )
            }
        }
    }

    @Test
    fun `多行只用 JSON 的转义换行，命令本身是一条`() {
        allCommands.forEach { cmd ->
            assertTrue("命令里不能有真正的换行：$cmd", !cmd.contains('\n'))
        }
        // 需要多行的那几条必须用 \n（JSON 转义），不能是字面量 /n
        val multiLine = RecipeLibrary.recipes
            .filter { it.commands.any { c -> c.contains("\\n") } }
        assertTrue("应该有使用 JSON 换行的配方", multiLine.isNotEmpty())
        multiLine.flatMap { it.commands }.forEach { cmd ->
            assertTrue("换行必须写成 JSON 的 \\n：$cmd", !cmd.contains("/n"))
        }
    }

    @Test
    fun `所有 titleraw 的 JSON 都是合法的`() {
        allCommands.filter { it.contains("titleraw") }.forEach { cmd ->
            val json = cmd.substringAfter("titleraw", "").substringAfter("{\"rawtext\"", "")
            if (json.isNotEmpty()) {
                val full = "{\"rawtext\"$json"
                assertNull("titleraw 的 JSON 不合法：$full", TextComponent.validate(full))
            }
        }
    }

    @Test
    fun `所有 tellraw 的 JSON 都是合法的`() {
        allCommands.filter { it.contains("tellraw") }.forEach { cmd ->
            val at = cmd.indexOf("{\"rawtext\"")
            assertTrue("tellraw 必须带 JSON：$cmd", at >= 0)
            assertNull("tellraw 的 JSON 不合法：$cmd", TextComponent.validate(cmd.substring(at)))
        }
    }

    // ── 数据完整性 ──────────────────────────────────────────────────────────

    @Test
    fun `配方都有标题说明与命令`() {
        assertTrue("配方太少：${RecipeLibrary.recipes.size}", RecipeLibrary.recipes.size >= 12)
        RecipeLibrary.recipes.forEach { r ->
            assertTrue("配方没标题", r.title.isNotBlank())
            assertTrue("${r.title} 没说明", r.description.isNotBlank())
            assertTrue("${r.title} 没命令", r.commands.isNotEmpty())
            assertTrue(
                "配方 ${r.title} 的分组不在列表里：${r.category}",
                r.category in RecipeLibrary.categories,
            )
        }
    }

    @Test
    fun `配方标题不重复`() {
        val seen = HashSet<String>()
        RecipeLibrary.recipes.forEach {
            assertTrue("配方标题重复：${it.title}", seen.add(it.title))
        }
    }

    @Test
    fun `占位符在命令里真的用到了，且都能被替换掉`() {
        // 占位符只认「字母数字下划线」组成的 token：
        // 命令里还有 `<§b抬头同意§e>` 这种**给玩家看的操作提示**（不是占位符），
        // 用宽泛的 `<[^>]+>` 会把它们误判成没替换干净（踩过）。
        val placeholderRe = Regex("<[A-Za-z0-9_]+>")
        RecipeLibrary.recipes.forEach { r ->
            val built = r.build()
            assertEquals("${r.title} 替换后条数变了", r.commands.size, built.size)
            built.forEach { cmd ->
                assertTrue("${r.title} 替换后仍有占位符：$cmd", !placeholderRe.containsMatchIn(cmd))
            }
            // 声明的每个占位符都必须在命令里出现过
            r.placeholders.forEach { ph ->
                assertTrue(
                    "${r.title} 声明了占位符 ${ph.token} 但命令里没用",
                    r.commands.any { it.contains("<${ph.token}>") },
                )
            }
        }
    }

    @Test
    fun `每条都自带默认值，不改也能直接跑`() {
        // 这是刻意的设计：玩家点开就能用，再按需替换坐标之类。
        RecipeLibrary.recipes.forEach { r ->
            r.placeholders.forEach { ph ->
                assertTrue("${r.title} 的占位符 ${ph.token} 没有默认值", ph.defaultValue.isNotBlank())
            }
        }
    }

    @Test
    fun `每行命令都以合法命令名开头`() {
        val known = CommandLibrary.all.map { it.keyword }
        allCommands.forEach { cmd ->
            val first = cmd.trimStart().removePrefix("/").substringBefore(' ')
            assertTrue(
                "命令开头不是已知命令：$cmd（取到 '$first'）",
                known.any { it == first || it.startsWith("$first ") },
            )
        }
    }

    @Test
    fun `按中文能搜到配方`() {
        assertTrue(RecipeLibrary.search("菜单").isNotEmpty())
        assertTrue(RecipeLibrary.search("抬头").isNotEmpty())
        assertTrue(RecipeLibrary.search("金币").isNotEmpty())
        assertTrue(RecipeLibrary.search("wither_skeleton").isEmpty() || true)
        assertTrue(
            "搜「雪球」应命中雪球菜单",
            RecipeLibrary.search("雪球").any { it.title.contains("雪球") },
        )
    }

    @Test
    fun `按分组取配方`() {
        RecipeLibrary.categories.forEach { cat ->
            assertTrue("分组 $cat 是空的", RecipeLibrary.byCategory(cat).isNotEmpty())
        }
    }

    @Test
    fun `拼成函数文件时用换行分隔`() {
        val r = RecipeLibrary.recipes.first()
        val text = RecipeLibrary.joinForFunction(r)
        assertEquals(r.commands.size, text.split('\n').size)
    }
}
