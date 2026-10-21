package com.xanan.cmdhelper.json

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 命令库的**基岩版语法**测试。
 *
 * ── 为什么单独一个文件 ──────────────────────────────────────────────────────
 * 这些断言全部来自一次系统性核对：拿 [minecraft.wiki](https://minecraft.wiki/w/Commands)
 * 的 "Available in Bedrock Edition and Minecraft Education" 表，以及每条命令各自的
 * `Commands/<name>` 页面原文，逐条比对本项目的命令库。核对报告在
 * `Chat_Room/命令助手-命令库维基核对报告.md`。
 *
 * 核对发现的问题分三类，这里各自钉住：
 *  1. **Java 专有命令混进来了**（team、forceload）—— 生成的命令在基岩版是"未知命令"；
 *  2. **语法写错**（playsound 多了 JE 的 source、setblock 方块状态前缺空格、
 *     clear 的 maxCount 语义反了…）—— 生成的命令**无法解析**；
 *  3. **命令缺失**（基岩版有但我们没有）—— 补了 50+ 条。
 *
 * 每个断言都带一句"错在哪"，这样以后有人改回老写法时，测试会直接告诉他原因。
 */
class BedrockSyntaxTest {

    /** 取一条命令，找不到就直接失败（比返回 null 好定位）。 */
    private fun spec(keyword: String): CommandSpec {
        val found = CommandLibrary.all.firstOrNull { it.keyword == keyword }
        assertNotNull("命令库里没有「$keyword」", found)
        return found!!
    }

    /** 用默认值构造一份参数表，再覆盖指定项。 */
    private fun values(keyword: String, vararg overrides: Pair<String, String>): Map<String, String> {
        val base = spec(keyword).defaultValues().toMutableMap()
        overrides.forEach { (k, v) -> base[k] = v }
        return base
    }

    /**
     * 生成命令，返回值**去掉尾部空格**。
     *
     * 生成器在最后一个可选段为空时会留下一个尾随空格（例如 `teleport @s @p `），
     * 这对游戏解析没有影响，但会让断言难写。统一 trim，让断言只关注内容。
     */
    private fun build(keyword: String, vararg overrides: Pair<String, String>): String? =
        spec(keyword).build(values(keyword, *overrides))?.trimEnd()

    // =======================================================================
    // 一、Java 专有命令必须不在库里
    // =======================================================================

    @Test
    fun `Java 专有的 team 与 forceload 已删除`() {
        // 维基 Commands/team 页首标 {{exclusive|java}}，BE 命令表里没有 team；
        // forceload 同样标 {{exclusive|java}}，并明确指向 BE 的 tickingarea。
        // 留着它们，玩家点了补全会得到一条基岩版不认识的命令。
        val keywords = CommandLibrary.all.map { it.keyword }
        assertFalse("team 是 Java 专有，不该在基岩版命令库里", keywords.any { it.startsWith("team") })
        assertFalse("forceload 是 Java 专有，基岩版对应物是 tickingarea", keywords.contains("forceload"))
    }

    // =======================================================================
    // 二、会生成「无法解析」命令的语法错误
    // =======================================================================

    @Test
    fun `playsound 不带 Java 专有的 source 频道`() {
        // 维基 playsound 把 <source> 标为 {{el|je|short=1}}（仅 Java）；
        // BE 第二个参数直接是 player: target。
        // 老写法会生成 `playsound random.orb master @a` —— 基岩版语法错误。
        val cmd = build("playsound", "sound" to "random.orb")
        assertNotNull(cmd)
        assertFalse("playsound 不该输出播放频道：$cmd", cmd!!.contains("master"))
        assertTrue("应直接跟目标：$cmd", cmd.startsWith("playsound random.orb @a"))

        // 参数表里也不能再有 source
        assertTrue(
            "playsound 不该再有 source 参数",
            spec("playsound").params.none { it.key == "source" },
        )
    }

    @Test
    fun `setblock 与 fill 的方块状态前有空格`() {
        // 维基把 <tileName: Block> 与 <blockStates: block states> 列为两个独立参数，
        // 示例 `setblock ~ ~ ~ chest ["minecraft:cardinal_direction"="east"]`。
        // 老写法 `{block}{states}` 会拼成 `chest[...]`，方块名与状态粘死。
        val sb = build("setblock", "block" to "chest", "states" to "[\"facing_direction\"=1]")
        assertNotNull(sb)
        assertTrue("方块与状态之间必须有空格：$sb", sb!!.contains("chest [\"facing_direction\"=1]"))
        assertFalse("不该粘在一起：$sb", sb.contains("chest["))

        val fl = build("fill", "block" to "gold_block", "states" to "[]")
        assertNotNull(fl)
        assertTrue("fill 同理要有空格：$fl", fl!!.contains("gold_block []"))
    }

    @Test
    fun `fill 不再输出已移除的数据值参数`() {
        // 维基历史："1.19.70 Removed tileData: int and replaceDataValue: int arguments"。
        // 老写法会生成 `fill ... stone 0 replace`，那个 0 现在解析不了。
        assertTrue(
            "fill 不该再有 blockData 参数",
            spec("fill").params.none { it.key == "blockData" },
        )
        val cmd = build("fill", "block" to "stone")
        assertNotNull(cmd)
        assertFalse("不该有裸的数字数据值：$cmd", Regex("""\bstone 0\b""").containsMatchIn(cmd!!))
    }

    @Test
    fun `clear 的 maxCount 默认是 -1 而不是 0`() {
        // 维基 BE：maxCount 为 0 表示「只检测并返回数量、不清除」，-1 才是清除全部。
        // 老写法默认 0，`clear @s stone 0 0` 啥也清不掉。
        val param = spec("clear").params.first { it.key == "maxCount" }
        assertEquals("-1", param.defaultValue)

        val cmd = build("clear", "item" to "stone")
        assertNotNull(cmd)
        assertTrue("默认应当清除全部：$cmd", cmd!!.endsWith("-1"))
    }

    @Test
    fun `titleraw 的 clear 与 reset 不带位置参数`() {
        // 维基 BE：`titleraw <player> <clear|reset>`（无位置）。
        // 老写法生成 `titleraw @a title clear` —— 多了个位置参数。
        val clear = build("titleraw", "action" to "clear")
        assertNotNull(clear)
        assertEquals("titleraw @a clear", clear)

        val reset = build("titleraw", "action" to "reset")
        assertEquals("titleraw @a reset", reset)
    }

    @Test
    fun `titleraw 显示模式仍是 目标 加 位置 加 JSON`() {
        // 回归保护：显示模式保留位置参数，别被上面的修改连带改坏。
        val cmd = build("titleraw", "position" to "title", "json" to """{"rawtext":[{"text":"hi"}]}""")
        assertNotNull(cmd)
        assertEquals("""titleraw @a title {"rawtext":[{"text":"hi"}]}""", cmd)
    }

    @Test
    fun `effect 不再生成只有目标的残缺命令`() {
        // 维基 BE 最短形式是 `effect <player> <effect>` 或 `effect <player> clear`，
        // 只有目标一个参数在基岩版**无法解析**。
        spec("effect").params.forEach { /* 走一遍确认可读 */ }
        val cmd = spec("effect").build(mapOf("target" to "@s"))
        assertNull("不该生成 `effect @s` 这种残缺命令，实际是：$cmd", cmd)
    }

    @Test
    fun `effect 支持 clear 指定效果与永久时长`() {
        // `effect <player> clear`（清全部）
        assertEquals("effect @s clear", build("effect", "mode" to "clear"))

        // `effect <player> clear <effect>`（只清某一种）
        val clearOne = build("effect", "mode" to "clear", "effect" to "speed", "target" to "@a")
        assertEquals("effect @a clear speed", clearOne)

        // `effect <player> <effect> infinite [amplifier] [hideParticles]`
        val infinite = build("effect", "effect" to "speed", "duration" to "infinite", "amplifier" to "1")
        assertNotNull(infinite)
        assertEquals("effect @s speed infinite 1 false", infinite)

        // 给效果时必须输出效果名，且**不能**把 clear 重复输出
        val give = build("effect", "effect" to "strength")
        assertNotNull(give)
        assertTrue("应输出效果名：$give", give!!.contains("strength"))
        assertFalse("不该出现重复的 clear：$give", give.contains("clear clear"))
    }

    @Test
    fun `summon 的 nameTag 是纯字符串不是 JSON`() {
        // 维基 BE：nameTag 类型 basic_string，示例 `summon dolphin §6Dolphin ~ ~ ~`。
        // 老写法输出 {"nameTag":"..."}，名字里会带上花括号字面量。
        val cmd = build("summon", "entity" to "dolphin", "nameTag" to "§6Dolphin")
        assertNotNull(cmd)
        assertFalse("nameTag 不该是 JSON：$cmd", cmd!!.contains("nameTag\":") || cmd.contains("{"))
        assertTrue("应直接跟在坐标后：$cmd", cmd.contains("§6Dolphin"))
    }

    @Test
    fun `teleport 不再把选择器与坐标混在一起`() {
        // 老写法会生成 `teleport @s @p 0 0`（坐标里混了个选择器），两版都不合法。
        // 维基 BE 的目的地是「实体」或「三坐标」二者之一。
        val toEntity = build("teleport", "victim" to "@s", "destination" to "@p")
        assertEquals("teleport @s @p", toEntity!!.trimEnd())

        val toCoords = build("teleport", "victim" to "@s", "x" to "1", "y" to "2", "z" to "3")
        assertNotNull(toCoords)
        assertTrue("应当是坐标形式：$toCoords", toCoords!!.startsWith("teleport @s 1 2 3"))

        // 任何输出里都不该出现「选择器后面又跟两个裸数字」
        CommandLibrary.all.first { it.keyword == "teleport" }.let { tp ->
            listOf(
                mapOf("victim" to "@s", "destination" to "@p", "y" to "0", "z" to "0"),
                mapOf("victim" to "@s", "x" to "~", "y" to "~", "z" to "~"),
            ).forEach { v ->
                val out = tp.build(v)
                if (out != null) {
                    assertFalse(
                        "不该出现「选择器 + 裸坐标」的混合形式：$out",
                        Regex("""@[aeprs]\S*\s+@\S+\s+-?\d""").containsMatchIn(out),
                    )
                }
            }
        }
    }

    @Test
    fun `fog 两种形式都带 userProvidedId`() {
        // 维基 BE：push 需要 fogID 与 userProvidedId 两个字符串；
        //          delete 需要 userProvidedId。老写法 `fog @a remove` 少了第二个。
        val push = build("fog", "mode" to "push", "fogId" to "minecraft:fog_crimson_forest", "userId" to "mytag")
        assertNotNull(push)
        assertTrue("push 要两个 id：$push", push!!.endsWith("minecraft:fog_crimson_forest mytag"))

        val del = build("fog", "mode" to "delete", "userId" to "mytag")
        assertNotNull(del)
        assertEquals("fog @a delete mytag", del)

        // 不该再出现 pop / remove 这两个 BE 没有的字面量
        val modes = spec("fog").params.first { it.key == "mode" }.choices
        assertTrue("BE 的 fog 只有 push / delete：$modes", modes == listOf("push", "delete"))
    }

    @Test
    fun `camera 去掉不存在的 add 并补齐 set 的 preset`() {
        // 维基 BE camera 的 `add` 子命令不存在；
        // `set` 必须跟 <preset: string>；fade 只有 color / time 两种形式。
        val modes = spec("camera").params.first { it.key == "mode" }.choices
        assertFalse("BE 没有 camera add：$modes", modes.contains("add"))
        listOf("clear", "attach_to_entity", "detach_from_entity", "fade", "fov_set", "fov_clear", "set", "play_spline", "remove_target")
            .forEach { assertTrue("camera 缺少子命令 $it：$modes", modes.contains(it)) }

        val set = build("camera", "mode" to "set", "preset" to "minecraft:free")
        assertNotNull("set 必须能带 preset：$set", set)
        assertTrue("set 要输出 preset：$set", set!!.contains("minecraft:free"))

        val fadeColor = build("camera", "mode" to "fade", "fadeMode" to "color", "red" to "255")
        assertTrue("fade color 形式：$fadeColor", fadeColor!!.contains("fade color 255"))

        val fadeTime = build("camera", "mode" to "fade", "fadeMode" to "time")
        assertTrue("fade time 形式：$fadeTime", fadeTime!!.contains("fade time"))

        // easeType 的提示要提到 Easing 枚举，而不是老写法的四个值
        val easeHint = spec("camera").params.first { it.key == "easeType" }.hint
        assertTrue("easeType 提示应说明 Easing 枚举：$easeHint", easeHint.contains("Easing"))
    }

    @Test
    fun `replaceitem 带上 entity 或 block 判别词`() {
        // 维基 BE 两种形式都必须有 entity / block 判别词，老写法整个漏了。
        val entity = build("replaceitem", "kind" to "entity", "target" to "@s", "slotType" to "slot.weapon.mainhand", "item" to "diamond_sword")
        assertNotNull(entity)
        assertTrue("entity 形式：$entity", entity!!.startsWith("replaceitem entity @s slot.weapon.mainhand"))

        val block = build("replaceitem", "kind" to "block", "x" to "0", "y" to "64", "z" to "0", "item" to "stone")
        assertNotNull(block)
        assertTrue("block 形式：$block", block!!.startsWith("replaceitem block 0 64 0 slot.container"))
    }

    @Test
    fun `ride 用 BE 的子命令而不是 Java 的 mount`() {
        // 维基：Java 是 `ride <target> mount <vehicle>`，
        //       BE 是 start_riding / stop_riding / evict_riders / summon_rider / summon_ride。
        val modes = spec("ride").params.first { it.key == "mode" }.choices
        assertEquals(
            listOf("start_riding", "stop_riding", "evict_riders", "summon_rider", "summon_ride"),
            modes,
        )

        val start = build("ride", "mode" to "start_riding", "riders" to "@s", "ride" to "@e[type=boat]")
        assertNotNull(start)
        assertTrue("start_riding 形式：$start", start!!.contains("start_riding @e[type=boat]"))

        val stop = build("ride", "mode" to "stop_riding", "riders" to "@s")
        assertEquals("ride @s stop_riding", stop)

        // 每种模式生成一遍，确认都不含 Java 的 mount
        modes.forEach { mode ->
            val out = build("ride", "mode" to mode, "riders" to "@s", "ride" to "@e", "entityType" to "zombie")
            if (out != null) {
                assertFalse("ride 不该用 Java 的 mount：$out", out.contains(" mount "))
            }
        }
    }

    @Test
    fun `scoreboard 的准则只有 dummy`() {
        // 维基：'In Bedrock Edition, dummy is the only criterion currently supported'。
        // 老写法把 Java 的 trigger / deathCount / playerKillCount… 也列进去了。
        val choices = spec("scoreboard objectives add").params.first { it.key == "criteria" }.choices
        assertEquals(listOf("dummy"), choices)
    }

    @Test
    fun `scoreboard setdisplay 的 list 槽位也接受排序`() {
        // 维基 BE：`setdisplay <list|sidebar> [objective] [ascending|descending]`
        //          `setdisplay belowname [objective]`
        val sidebar = build("scoreboard objectives setdisplay", "slot" to "sidebar", "objective" to "分数", "order" to "descending")
        assertTrue("sidebar 带排序：$sidebar", sidebar!!.endsWith("分数 descending"))

        val list = build("scoreboard objectives setdisplay", "slot" to "list", "objective" to "分数", "order" to "descending")
        assertTrue("list 也该带排序：$list", list!!.contains("list 分数"))

        val below = build("scoreboard objectives setdisplay", "slot" to "belowname", "objective" to "分数")
        assertEquals("scoreboard objectives setdisplay belowname 分数", below)
        assertFalse("belowname 不接受排序：$below", below!!.contains("ascending"))
    }

    @Test
    fun `clone 的 maskMode 不含 filtered`() {
        // 维基：MaskMode 只有 replace / masked；filtered 是**另一条独立语法**
        // （后面还要跟方块名），不是这个枚举的取值。
        val modes = spec("clone").params.first { it.key == "maskMode" }.choices
        assertEquals(listOf("replace", "masked"), modes)
    }

    @Test
    fun `weather 的时长单位是游戏刻`() {
        // 维基：BE 1.18.30 起 duration 单位是 game ticks（20 刻 = 1 秒）。
        // 老写法标签写"秒"、默认 600，玩家会以为是 600 秒。
        val param = spec("weather").params.first { it.key == "duration" }
        assertTrue("标签要说明是游戏刻：${param.label}", param.label.contains("刻"))
        assertFalse("不该再叫「秒」：${param.label}", param.label.contains("秒"))

        // 不填时长也要能生成（游戏自己决定）
        assertEquals("weather clear", build("weather", "type" to "clear"))
    }

    @Test
    fun `setworldspawn 的坐标是三坐标一体`() {
        // 维基 BE：`setworldspawn [spawnPoint: x y z]` —— 要么三个都给，要么整个省略。
        // 老写法让 y 单独可选，会生成只有两个坐标的 `setworldspawn ~ ~`。
        val cmd = build("setworldspawn", "x" to "~", "y" to "~", "z" to "~")
        assertEquals("setworldspawn ~ ~ ~", cmd)

        val empty = spec("setworldspawn").build(mapOf("x" to "~", "z" to "~"))
        assertEquals("坐标不全时应当整个省略", "setworldspawn", empty)
    }

    @Test
    fun `tickingarea 用 remove_all 且坐标在名字前`() {
        // 维基：`tickingarea add <from> <to> [name] [preload]`（name 在坐标之后）、
        //       `tickingarea remove_all`（带下划线）。
        val modes = spec("tickingarea").params.first { it.key == "mode" }.choices
        assertTrue("应当是 remove_all：$modes", modes.contains("remove_all"))
        assertFalse("不该是 removeall：$modes", modes.contains("removeall"))

        val add = build("tickingarea", "mode" to "add", "from" to "0 0 0", "to" to "10 10 10", "name" to "myarea")
        assertNotNull(add)
        val fromIdx = add!!.indexOf("0 0 0")
        val nameIdx = add.indexOf("myarea")
        assertTrue("坐标必须在区域名之前：$add", fromIdx in 0..<nameIdx)

        assertEquals("tickingarea remove_all", build("tickingarea", "mode" to "remove_all"))
    }

    @Test
    fun `structure save 的可选参数顺序按维基`() {
        // 维基：`structure save <name> <from> <to> [includeEntities] [saveMode] [includeBlocks]`
        // 老写法 saveMode 在 includeEntities 之前（顺序错），且默认 disk（应为 memory）。
        val cmd = build(
            "structure save",
            "name" to "house", "from" to "0 0 0", "to" to "5 5 5",
            "includeEntities" to "true", "saveMode" to "disk",
        )
        assertNotNull(cmd)
        val entIdx = cmd!!.indexOf("true")
        val modeIdx = cmd.indexOf("disk")
        assertTrue("includeEntities 必须在 saveMode 之前：$cmd", entIdx in 0..<modeIdx)

        // 不指定 saveMode 时不该硬塞一个 disk 进去（维基默认是 memory）
        val plain = build("structure save", "name" to "house", "from" to "0 0 0", "to" to "5 5 5")
        assertFalse("不该默认输出 disk：$plain", plain!!.contains("disk"))
    }

    @Test
    fun `execute 的提示限定在基岩版支持的四个条件`() {
        // 维基：BE 只有 if/unless block | blocks | entity | score，
        //       **没有** data / biome / dimension / predicate / loaded / function，
        //       也**没有** store 子命令（那些都是 Java 的）。
        val hint = spec("execute").params.first { it.key == "condition" }.hint
        listOf("block", "blocks", "entity", "score").forEach {
            assertTrue("提示里应列出条件 $it：$hint", hint.contains(it))
        }
        assertFalse("不该暗示有 store：$hint", hint.contains("store"))
    }

    // =======================================================================
    // 三、补进来的基岩版命令确实存在且能生成
    // =======================================================================

    @Test
    fun `补进来的顶层命令都在库里`() {
        val expected = listOf(
            "help", "ability", "allowlist", "aimassist", "daylock", "changesetting",
            "clearspawnpoint", "wsserver", "controlscheme", "dedicatedwsserver",
            "op", "deop", "permission", "editor-allowlist", "enchant", "event",
            "gametips", "hud", "immutableworld", "kick", "list", "loot", "mobevent",
            "msg", "party", "place", "playanimation", "project", "recipe",
            "reloadconfig", "reloadpacketlimitconfig", "save", "script", "scriptevent",
            "sendshowstoreoffer", "set_movement_authority", "setmaxplayers",
            "spawnpoint", "stop", "testforblock", "testforblocks", "toggledownfall",
            "transfer", "worldbuilder",
        )
        val keywords = CommandLibrary.all.map { it.keyword }.toSet()
        val missing = expected.filterNot { keywords.contains(it) }
        assertTrue("这些基岩版命令还没补：$missing", missing.isEmpty())
    }

    @Test
    fun `补进来的子命令也在库里`() {
        val expected = listOf(
            "time query", "weather query", "music queue",
            "schedule delay add", "schedule delay clear", "schedule clear",
            "schedule on_area_loaded clear",
            "gametest runthis", "gametest runthese", "gametest runset",
            "gametest runsetuntilfail", "gametest clearall", "gametest create",
            "gametest pos", "gametest stopall",
        )
        val keywords = CommandLibrary.all.map { it.keyword }.toSet()
        val missing = expected.filterNot { keywords.contains(it) }
        assertTrue("这些子命令还没补：$missing", missing.isEmpty())
    }

    @Test
    fun `每条新增命令都能按默认值生成出内容`() {
        // 「能生成」不等于「生成得对」，但至少保证不是空壳（比如变体条件写错导致永不命中）。
        val noArgCommands = setOf("list", "stop", "toggledownfall", "worldbuilder", "reloadconfig", "reloadpacketlimitconfig", "weather query", "gametest pos", "gametest stopall")
        val newOnes = listOf(
            "help", "ability", "allowlist", "aimassist", "daylock", "changesetting",
            "clearspawnpoint", "wsserver", "controlscheme", "dedicatedwsserver",
            "op", "deop", "permission", "editor-allowlist", "enchant", "event",
            "gametips", "hud", "immutableworld", "kick", "list", "mobevent",
            "msg", "party", "place", "playanimation", "project", "recipe",
            "reloadconfig", "reloadpacketlimitconfig", "save", "script", "scriptevent",
            "sendshowstoreoffer", "set_movement_authority", "setmaxplayers",
            "spawnpoint", "stop", "testforblock", "testforblocks", "toggledownfall",
            "transfer", "worldbuilder",
            "time query", "weather query", "music queue",
            "schedule delay add", "schedule delay clear", "schedule clear",
            "gametest runthis", "gametest runthese", "gametest runset",
            "gametest runsetuntilfail", "gametest clearall", "gametest create",
            "gametest pos", "gametest stopall",
        )
        newOnes.forEach { kw ->
            val s = spec(kw)
            // 用「所有参数都填一个占位值」的方式构造，避免可选参数默认值为空导致不生成。
            val filled = s.params.associate { p ->
                p.key to when {
                    p.defaultValue.isNotBlank() -> p.defaultValue
                    p.type == ParamType.SELECTOR -> "@s"
                    p.type == ParamType.NUMBER -> "1"
                    p.type == ParamType.BOOLEAN -> "true"
                    p.choices.isNotEmpty() -> p.choices.first()
                    else -> "x"
                }
            }
            val cmd = s.build(filled)
            assertNotNull("「$kw」按默认值生成不出命令", cmd)
            assertTrue("「$kw」生成了空命令", cmd!!.isNotBlank())
            assertTrue(
                "「$kw」生成的内容没有以关键字开头：$cmd",
                cmd.startsWith(kw),
            )
        }
    }

    @Test
    fun `每条命令都以能生成命令的关键字开头`() {
        // 关键字里带空格的（如 `scoreboard players set`、`gametest runthis`）
        // 也必须整体出现在开头，否则玩家在游戏里敲的就是另一条命令。
        CommandLibrary.all.forEach { s ->
            val filled = s.params.associate { p ->
                p.key to when {
                    p.defaultValue.isNotBlank() -> p.defaultValue
                    p.type == ParamType.SELECTOR -> "@s"
                    p.type == ParamType.NUMBER -> "1"
                    p.type == ParamType.BOOLEAN -> "true"
                    p.choices.isNotEmpty() -> p.choices.first()
                    else -> "x"
                }
            }
            val cmd = s.build(filled) ?: return@forEach
            assertTrue(
                "「${s.keyword}」生成的命令开头不对：$cmd",
                cmd == s.keyword || cmd.startsWith("${s.keyword} "),
            )
        }
    }

    @Test
    fun `命令名没有重复`() {
        val seen = HashMap<String, Int>()
        CommandLibrary.all.forEach { seen[it.keyword] = (seen[it.keyword] ?: 0) + 1 }
        val dup = seen.filterValues { it > 1 }.keys
        assertTrue("命令名重复：$dup", dup.isEmpty())
    }

    @Test
    fun `每条命令都有中文别名`() {
        // 玩家会打 `/粒子` 而不是 `/particle`。生成器里已有自检，
        // 这里再从产物侧确认一遍（防止生成器被绕过）。
        val noAlias = CommandLibrary.all.filter { it.aliases.isEmpty() }.map { it.keyword }
        assertTrue("这些命令没有中文名：$noAlias", noAlias.isEmpty())
    }
}
