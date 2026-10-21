package com.xanan.cmdhelper.json

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 目标选择器 `[]` 语法的补全测试。
 *
 * 场景照玩家真实输入走：
 * ```
 * /give @e[             → 22 个基岩版参数（中文可搜）
 * /give @e[type=        → 实体词表（僵尸 / 苦力怕 …）
 * /give @e[m=           → 游戏模式
 * ```
 *
 * 参数名严格照 <https://minecraft.wiki/w/Target_selectors> 的**基岩版**那一列。
 */
class SelectorLibraryTest {

    private fun complete(input: String, cursor: Int = input.length) =
        CommandCompletion.complete(input, cursor)

    // ── 参数库本身 ──────────────────────────────────────────────────────────

    @Test
    fun `参数都有中文名与示例`() {
        assertTrue("参数太少：${SelectorLibrary.arguments.size}", SelectorLibrary.arguments.size >= 20)
        SelectorLibrary.arguments.forEach { a ->
            assertTrue("${a.name} 没有中文名", a.cn.isNotBlank() && a.cn != a.name)
            assertTrue("${a.name} 没有说明", a.detail.isNotBlank())
            assertTrue("${a.name} 没有示例", a.example.isNotBlank())
            // 示例必须是 `名字=值` 形式，且名字对得上
            assertTrue("${a.name} 的示例不对：${a.example}", a.example.startsWith("${a.name}="))
        }
    }

    @Test
    fun `参数名不重复`() {
        val seen = HashSet<String>()
        SelectorLibrary.arguments.forEach {
            assertTrue("参数名重复：${it.name}", seen.add(it.name))
        }
    }

    @Test
    fun `只收基岩版的参数名`() {
        // Wiki 明确标了 [BE only] 的缩写写法；Java 版的写法写进基岩版命令不会报错，
        // 但**一个实体也选不中**，所以必须用缩写。
        listOf("r", "rm", "rx", "rxm", "ry", "rym", "l", "lm", "m", "c").forEach { name ->
            assertNotNull("缺少基岩版参数 $name", SelectorLibrary.find(name))
        }
        // Java 专有的参数名不该出现
        listOf("distance", "x_rotation", "y_rotation", "level", "gamemode", "limit", "sort", "team", "nbt")
            .forEach { name ->
                assertNull("这是 Java 版参数，基岩版用不了：$name", SelectorLibrary.find(name))
            }
    }

    @Test
    fun `基岩版独有的参数都在`() {
        listOf("family", "hasitem", "haspermission", "has_property").forEach { name ->
            assertNotNull("缺少基岩版独有参数 $name", SelectorLibrary.find(name))
        }
    }

    @Test
    fun `按中文能搜到参数`() {
        assertTrue(SelectorLibrary.search("距离").any { it.name == "r" || it.name == "rm" })
        assertTrue(SelectorLibrary.search("标签").any { it.name == "tag" })
        assertTrue(SelectorLibrary.search("游戏模式").any { it.name == "m" })
        assertTrue(SelectorLibrary.search("分数").any { it.name == "scores" })
        assertTrue(SelectorLibrary.search("族").any { it.name == "family" })
    }

    // ── 上下文识别 ──────────────────────────────────────────────────────────

    @Test
    fun `能识别光标在选择器方括号里`() {
        val ctx = SelectorCompletion.analyze("/give @e[")
        assertNotNull("应识别为选择器上下文", ctx)
        assertEquals("@e", ctx!!.variable)
        assertTrue("刚打完 [ 应期待参数名", ctx.expectingArgument)

        val typed = SelectorCompletion.analyze("/give @e[r=10,")
        assertNotNull(typed)
        assertTrue(typed!!.expectingArgument)

        val inValue = SelectorCompletion.analyze("/give @e[type=zom")
        assertNotNull(inValue)
        assertEquals("type", inValue!!.argumentName)
        assertTrue(inValue.hasValue)
    }

    @Test
    fun `不在选择器里时不误判`() {
        assertNull(SelectorCompletion.analyze("/give @s diamond_sword"))
        assertNull(SelectorCompletion.analyze("/titleraw @a title"))
        // JSON 数组里的方括号不该被当成选择器
        assertNull(SelectorCompletion.analyze("/titleraw @a title {\"rawtext\":["))
    }

    @Test
    fun `嵌套花括号不影响参数切分`() {
        // scores={...} 里的花括号不能让"最后一个逗号"判断出错
        val ctx = SelectorCompletion.analyze("/give @e[scores={\"a\"=1},r=1")
        assertNotNull(ctx)
        assertEquals("r", ctx!!.argumentName)
    }

    // ── 候选 ────────────────────────────────────────────────────────────────

    @Test
    fun `打方括号后给参数候选`() {
        val items = complete("/give @e[").items
        assertTrue("应给参数候选", items.size >= 20)
        val labels = items.map { it.value }
        listOf("type=", "name=", "tag=", "r=", "rm=", "m=", "family=", "hasitem=").forEach { expected ->
            assertTrue("缺少参数候选 $expected：$labels", labels.contains(expected))
        }
    }

    @Test
    fun `参数候选显示中文并能按中文搜`() {
        val items = complete("/give @e[").items
        val type = items.first { it.value == "type=" }
        assertTrue("应显示中文名：${type.label}", type.label.contains("类型"))

        // 按中文过滤
        val byCn = complete("/give @e[类型").items.map { it.value }
        assertTrue("搜「类型」应命中 type=：$byCn", byCn.contains("type="))
    }

    @Test
    fun `插入参数后光标落在等号后面`() {
        val items = complete("/give @e[").items
        val type = items.first { it.value == "type=" }
        assertEquals("type=".length, type.caretOffset)
    }

    @Test
    fun `type 的值给实体词表`() {
        val items = complete("/give @e[type=").items
        assertTrue("应给实体候选", items.size > 50)
        assertTrue("应有僵尸", items.any { it.value == "zombie" })
        assertTrue("应有苦力怕", items.any { it.value == "creeper" })
        // 显示中文名
        val zombie = items.first { it.value == "zombie" }
        assertTrue("应显示中文名：${zombie.label}", zombie.label.any { it.code > 0x2E80 })
        // 按中文过滤
        assertTrue(
            "搜「苦力怕」应命中 creeper",
            complete("/give @e[type=苦力怕").items.any { it.value == "creeper" },
        )
    }

    @Test
    fun `m 的值给游戏模式`() {
        val items = complete("/give @e[m=").items.map { it.value }
        listOf("survival", "creative", "adventure", "spectator").forEach {
            assertTrue("缺少游戏模式 $it：$items", items.contains(it))
        }
    }

    @Test
    fun `family 的值来自官方实体包且覆盖完整`() {
        // 数据源：Mojang 官方 behavior_pack/entities/*.json 的 minecraft:type_family。
        // Wiki 只举了几个例子，真正的清单有一百多个；只写那 10 个会让玩家
        // "知道要写 family 却找不到能用的值"。
        assertTrue("族太少：${SelectorLibrary.families.size}", SelectorLibrary.families.size >= 100)

        val ids = SelectorLibrary.families.map { it.first }
        // 常用大类
        listOf("mob", "monster", "undead", "inanimate", "animal", "arthropod", "illager")
            .forEach { assertTrue("缺少族 $it：$ids", ids.contains(it)) }
        // 具体生物族
        listOf("creeper", "zombie", "skeleton", "spider", "enderman", "slime", "wither", "dragon")
            .forEach { assertTrue("缺少族 $it", ids.contains(it)) }

        // 全部都要有中文名（生成器保证）
        val noCn = SelectorLibrary.families.filter { it.second.isBlank() || it.second == it.first }
        assertTrue("这些族没有中文名：${noCn.map { it.first }}", noCn.isEmpty())
    }

    @Test
    fun `family 的值能给候选并按中文搜`() {
        val items = complete("/give @e[family=").items.map { it.value }
        assertTrue("应有 monster：$items", items.contains("monster"))
        assertTrue("应有 undead：$items", items.contains("undead"))

        // 按中文搜
        assertTrue(
            "搜「亡灵」应命中 undead",
            complete("/give @e[family=亡灵").items.any { it.value == "undead" },
        )
    }

    @Test
    fun `叹号取反时候选也跟着带叹号`() {
        // Wiki：type=!creeper / family=!monster 都合法。
        // 没有这一步的话，选了 creeper 会把"排除"变成"只选"，语义正好相反。
        val typeNeg = complete("/give @e[type=!").items
        assertTrue("应给取反候选", typeNeg.isNotEmpty())
        assertTrue("插入值应带叹号：${typeNeg.take(3).map { it.value }}", typeNeg.all { it.value.startsWith("!") })
        // ⚠️ 显示名也要带叹号：面板唯一显示英文的位置（detail）被"实体类型"占着，
        // 只把叹号放进 value 的话玩家在界面上根本看不到它（实测踩过）。
        assertTrue("显示名也要带叹号：${typeNeg.take(3).map { it.label }}", typeNeg.all { it.label.startsWith("!") })

        val familyNeg = complete("/give @e[family=!m").items
        assertTrue("应给 !monster：${familyNeg.map { it.value }}", familyNeg.any { it.value == "!monster" })
        assertTrue("不该给不带叹号的 monster", familyNeg.none { it.value == "monster" })
        assertTrue(
            "显示名应带叹号：${familyNeg.map { it.label }}",
            familyNeg.all { it.label.startsWith("!") },
        )

        // 不加叹号时不该带
        val plain = complete("/give @e[type=").items
        assertTrue("不该带叹号", plain.none { it.value.startsWith("!") || it.label.startsWith("!") })
    }

    @Test
    fun `写完一个参数后继续给参数并自动补逗号`() {
        val values = complete("/give @e[r=10,").items.map { it.value }
        assertTrue("应继续给参数：$values", values.isNotEmpty())
        assertTrue("应给参数名形式：$values", values.all { it.endsWith("=") })
        // 已经用过的 r 不该再推荐
        assertTrue("不该重复推荐 r：$values", values.none { it == "r=" })
        // 但 rm 与 r 是不同参数，可以并存
        assertTrue("应能继续选 rm：$values", values.contains("rm="))
    }

    @Test
    fun `没有可给的值时不给候选而不是乱给`() {
        // x= 是自由坐标值，列一排候选反而误导
        val items = complete("/give @e[x=").items
        assertTrue("坐标不该给候选：${items.map { it.value }}", items.isEmpty())
    }

    @Test
    fun `type 的值覆盖官方客户端实体清单补进来的那几个`() {
        // 核对方法：把官方 `resource_pack/entity`（180 个文件，去掉版本后缀与渲染用定义
        // 后 126 个）与语言文件的 `entity.*.name`（138 个）比对 ——
        // 语言文件是更全的超集，只多出下面这几个。
        // 见 tools/suggest/audit-entity-types.mjs。
        val ids = Suggest.entities.map { it.id }
        listOf("trial_spawner", "ender_eye", "experience_bottle", "experience_orb")
            .forEach { assertTrue("缺少实体类型 $it", ids.contains(it)) }

        // 常见实体类型一个都不能少（type= 最常用的就是这些）
        listOf(
            "zombie", "zombie_villager", "creeper", "skeleton", "wither_skeleton",
            "player", "item", "xp_orb", "tnt", "armor_stand", "painting",
            "lightning_bolt", "arrow", "snowball", "boat", "minecart",
            "area_effect_cloud", "falling_block", "evocation_fang",
        ).forEach { assertTrue("缺少实体类型 $it", ids.contains(it)) }

        assertTrue("实体类型太少：${ids.size}", ids.size >= 140)

        // 这几个必须在候选里真的出得来
        val items = complete("/give @e[type=").items.map { it.value }
        assertTrue("试炼刷怪笼应可补全", items.contains("trial_spawner"))
    }

    @Test
    fun `给命令的玩法没被破坏`() {
        // 普通参数的补全要照旧（选择器分支不能抢走）。
        // ⚠️ 先收窄再断言：物品表 550+ 条，候选截断到 150 条后
        //    `diamond_sword` 不在第一屏里（按字母序排在更靠后）。
        val items = complete("/give @s diamond_sw").items
        assertTrue("物品候选应还在", items.any { it.value == "diamond_sword" })
    }
}
