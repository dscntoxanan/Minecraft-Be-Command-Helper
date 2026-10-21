package com.xanan.cmdhelper.json

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 词表覆盖度测试 —— 钉住「官方语言文件的**全部** ID 都进了词表」这件事。
 *
 * ── 为什么需要这个文件 ──────────────────────────────────────────────────────
 * 2026-10 做了一次词表核对，发现 zh_CN.lang 里**点分键那一大批整族没被提取**：
 * 老脚本只认 `^item\.<id>\.name$`，且显式排除了含点的 id，于是这些全部漏掉：
 *
 * ```
 * tile.wool.black.name     = 黑色羊毛    →  black_wool
 * tile.planks.acacia.name  = 金合欢木板  →  acacia_planks
 * item.boat.oak.name       = 橡木船      →  oak_boat
 * tile.log.acacia.name     = 金合欢原木  →  acacia_log
 * ```
 *
 * 实测当时 `white_wool`、`oak_log`、`acacia_planks`、**所有船**都不在词表里 ——
 * 玩家搜「羊毛」「原木」一条都搜不到。这不是"要不要多收"的取舍，是覆盖度上的洞。
 *
 * 这个文件的断言就是防止那个洞再出现：**每一族都必须有代表作**，
 * 而且必须带官方中文名（不是拿 ID 顶替）。
 */
class TermCoverageTest {

    /** 在词表里找一个 ID，找不到就断言失败并给出排查方向。 */
    private fun find(table: List<Suggest.Term>, id: String, tableName: String): Suggest.Term {
        val hit = table.firstOrNull { it.id == id }
        assertNotNull(
            "$tableName 词表里没有「$id」。\n" +
                "这通常意味着 zh_CN.lang 的键→ID 映射漏了某一族（见 gen-suggest.mjs / lang-key-map.mjs）。",
            hit,
        )
        return hit!!
    }

    /** 断言某个 ID 在表里，且**中文名不是 ID 本身**（即确实取到了译名）。 */
    private fun assertNamed(table: List<Suggest.Term>, id: String, tableName: String, expected: String? = null) {
        val t = find(table, id, tableName)
        assertTrue(
            "$tableName 的「$id」没有中文名（name 就是 id：${t.name}）—— 说明只拿到了 ID、没取到译名",
            t.name != id && t.name.isNotBlank(),
        )
        if (expected != null) {
            assertTrue(
                "$tableName 的「$id」译名是「${t.name}」，应包含「$expected」",
                t.name.contains(expected),
            )
        }
    }

    // =======================================================================
    // 方块：按"族"逐个检查（这些族原来整族缺失）
    // =======================================================================

    @Test
    fun `羊毛与地毯全色都在方块表里`() {
        val colors = listOf(
            "white", "orange", "magenta", "light_blue", "yellow", "lime", "pink",
            "gray", "light_gray", "cyan", "purple", "blue", "brown", "green", "red", "black",
        )
        colors.forEach { c ->
            assertNamed(Suggest.blocks, "${c}_wool", "方块", "羊毛")
            assertNamed(Suggest.blocks, "${c}_carpet", "方块", "地毯")
            assertNamed(Suggest.blocks, "${c}_concrete", "方块", "混凝土")
            assertNamed(Suggest.blocks, "${c}_concrete_powder", "方块", "混凝土粉末")
            assertNamed(Suggest.blocks, "${c}_stained_glass", "方块", "玻璃")
            assertNamed(Suggest.blocks, "${c}_stained_glass_pane", "方块", "玻璃板")
            assertNamed(Suggest.blocks, "${c}_terracotta", "方块", "陶瓦")
            assertNamed(Suggest.blocks, "${c}_glazed_terracotta", "方块", "带釉陶瓦")
        }
        // light_gray 特别检查：语言文件用的是 silver（历史叫法），
        // 如果忘了这个映射就会整族丢失。
        assertNamed(Suggest.blocks, "light_gray_wool", "方块", "淡灰色羊毛")
    }

    @Test
    fun `木种族全都在方块表里`() {
        // ⚠️ **不能用一个后缀套所有木种** —— 官方命名按木种不同，实测核实过：
        //
        //   主世界木种   oak_log / oak_wood / oak_sapling / oak_leaves / oak_planks
        //   竹子         **bamboo_block**（没有 bamboo_log）、bamboo_sapling、bamboo_planks
        //   红树         **mangrove_propagule**（不是 mangrove_sapling）
        //   下界木种     crimson_stem / crimson_hyphae（不是 _log / _wood）
        //
        // 这些差异都是查官方 Blocks.md 得来的，不是推断。
        val overworldWoods = listOf("oak", "spruce", "birch", "jungle", "acacia", "dark_oak", "mangrove", "cherry")
        val netherWoods = listOf("crimson", "warped")

        // 木板与台阶：所有木种都有
        (overworldWoods + netherWoods + "bamboo").forEach { w ->
            assertNamed(Suggest.blocks, "${w}_planks", "方块")
            assertNamed(Suggest.blocks, "${w}_slab", "方块", "台阶")
        }

        // 原木：主世界叫 _log，下界叫 _stem，竹子叫 _block
        overworldWoods.forEach { w -> assertNamed(Suggest.blocks, "${w}_log", "方块", "原木") }
        netherWoods.forEach { w -> assertNamed(Suggest.blocks, "${w}_stem", "方块", "菌柄") }
        assertNamed(Suggest.blocks, "bamboo_block", "方块", "竹")

        // 原木的"块"形态：主世界叫 _wood，本界叫 _hyphae
        overworldWoods.forEach { w -> assertNamed(Suggest.blocks, "${w}_wood", "方块") }
        netherWoods.forEach { w -> assertNamed(Suggest.blocks, "${w}_hyphae", "方块") }

        // 树叶只有主世界那几种
        overworldWoods.forEach { w -> assertNamed(Suggest.blocks, "${w}_leaves", "方块", "树叶") }

        // 树苗：红树是例外（mangrove_propagule）；
        // 竹子的官方译名是「竹苗」而不是「竹树苗」，所以它只查"有中文名"。
        (overworldWoods - "mangrove").forEach { w ->
            assertNamed(Suggest.blocks, "${w}_sapling", "方块", "树苗")
        }
        assertNamed(Suggest.blocks, "bamboo_sapling", "方块")
        assertNamed(Suggest.blocks, "mangrove_propagule", "方块", "红树")

        // 木板的官方译名按木种不同：多数是「XX木板」，但 bamboo_planks = 竹板。
        listOf("oak", "spruce", "acacia", "cherry").forEach { w ->
            assertNamed(Suggest.blocks, "${w}_planks", "方块", "木板")
        }
    }

    @Test
    fun `珊瑚五种与失活形态都在`() {
        // 珊瑚的"颜色"段其实是**种类**：blue=管、pink=脑纹、purple=气泡、red=火、yellow=鹿角。
        // 这个映射写错的话名字会全部对不上，所以逐个对中文。
        val corals = listOf(
            "tube" to "管",
            "brain" to "脑纹",
            "bubble" to "气泡",
            "fire" to "火",
            "horn" to "鹿角",
        )
        corals.forEach { (kind, cn) ->
            assertNamed(Suggest.blocks, "${kind}_coral", "方块", cn)
            assertNamed(Suggest.blocks, "${kind}_coral_block", "方块", cn)
            assertNamed(Suggest.blocks, "${kind}_coral_fan", "方块", cn)
            assertNamed(Suggest.blocks, "dead_${kind}_coral_block", "方块", "珊瑚")
        }
    }

    // =======================================================================
    // 物品：同样按族检查
    // =======================================================================

    @Test
    fun `所有木种的船都在物品表里`() {
        listOf("oak", "spruce", "birch", "jungle", "acacia", "dark_oak", "mangrove", "cherry")
            .forEach { w -> assertNamed(Suggest.items, "${w}_boat", "物品", "船") }
        // 运输船（带箱子）
        listOf("oak", "spruce", "acacia").forEach { w ->
            assertNamed(Suggest.items, "${w}_chest_boat", "物品", "运输船")
        }
        // dark_oak 在语言文件里写作 big_oak，漏了这个映射就会缺
        assertNamed(Suggest.items, "dark_oak_boat", "物品", "深色橡木")
    }

    @Test
    fun `染料全色与旗帜色都在`() {
        listOf("white", "black", "red", "blue").forEach { c ->
            assertNamed(Suggest.items, "${c}_dye", "物品")
            assertNamed(Suggest.items, "${c}_banner", "物品", "旗帜")
        }
        // 染料名有两种来源，实测：
        //   `item.dye.black_new.name` = 黑色染料  ← 新键，名字规范
        //   `item.dye.blue.name`      = 青金石    ← 老键，是**旧名字**（骨粉/墨囊/青金石…）
        // 语言文件里两种键并存，所以不强制要求含"染料"二字，只要求有中文名。
        assertNamed(Suggest.items, "light_gray_dye", "物品")
        assertNamed(Suggest.items, "red_dye", "物品")
    }

    @Test
    fun `桶类都在物品表里`() {
        // 桶的语言文件键名（bucketWater / bucketFish…）与官方 ID 差别很大，
        // 靠显式映射表才拿得到。
        assertNamed(Suggest.items, "water_bucket", "物品", "水桶")
        assertNamed(Suggest.items, "lava_bucket", "物品", "熔岩桶")
        assertNamed(Suggest.items, "cod_bucket", "物品", "鳕鱼桶")
        assertNamed(Suggest.items, "tropical_fish_bucket", "物品", "热带鱼桶")
        assertNamed(Suggest.items, "axolotl_bucket", "物品", "美西螈桶")
    }

    @Test
    fun `刷怪蛋带中文名`() {
        // 刷怪蛋有 90 多个（`zombie_spawn_egg` 等）。
        // 一开始在生成侧把它们排除了（看着像噪音），后来改回来：
        // 它们是官方 Items.md 认可的、能 /give 的 ID，而且都带官方中文名，
        // 玩家搜「僵尸」时本来就该看到"僵尸生成蛋"。
        // 噪音问题交给"按输入过滤"解决，不该在数据层砍掉。
        listOf("zombie", "creeper", "skeleton", "allay", "armadillo").forEach { e ->
            assertNamed(Suggest.items, "${e}_spawn_egg", "物品", "生成蛋")
        }
    }

    @Test
    fun `陶片与盔甲纹饰都在物品表里`() {
        // 这两族共 41 条，原来被 JUNK 规则排除。
        assertNamed(Suggest.items, "heart_pottery_sherd", "物品", "陶片")
        assertNamed(Suggest.items, "skull_pottery_sherd", "物品", "陶片")
        assertNamed(Suggest.items, "netherite_upgrade_smithing_template", "物品", "下界合金")
        assertNamed(Suggest.items, "sentry_armor_trim_smithing_template", "物品", "盔甲纹饰")
    }

    @Test
    fun `旗帜图案与告示牌都在物品表里`() {
        assertNamed(Suggest.items, "creeper_banner_pattern", "物品", "旗帜图案")
        assertNamed(Suggest.items, "banner_pattern", "物品", "旗帜图案")
        listOf("oak", "spruce", "acacia").forEach { w ->
            assertNamed(Suggest.items, "${w}_sign", "物品", "告示牌")
        }
    }

    // =======================================================================
    // 规模下限（防止将来某次生成又悄悄丢掉一整族）
    // =======================================================================

    @Test
    fun `各词表规模不低于已知下限`() {
        // 下限取"实测值往下留一点余量"，不是精确值 —— 官方加内容时不用改测试，
        // 但一旦某族被漏掉（数量骤降）就会立刻失败。
        assertTrue("物品词表太小：${Suggest.items.size}", Suggest.items.size >= 550)
        assertTrue("方块词表太小：${Suggest.blocks.size}", Suggest.blocks.size >= 1100)
        assertTrue("实体词表太小：${Suggest.entities.size}", Suggest.entities.size >= 140)
        assertTrue("音效词表太小：${Suggest.sounds.size}", Suggest.sounds.size >= 1650)
        assertTrue("粒子词表太小：${Suggest.particles.size}", Suggest.particles.size >= 190)
    }

    @Test
    fun `方块表里同时有羊毛与原木这两族代表作`() {
        // 这两条是本次修复的**直接证据**：修复前它们都不在表里。
        assertTrue("方块表里应有 white_wool", Suggest.blocks.any { it.id == "white_wool" })
        assertTrue("方块表里应有 oak_log", Suggest.blocks.any { it.id == "oak_log" })
        assertTrue("方块表里应有 acacia_planks", Suggest.blocks.any { it.id == "acacia_planks" })
        assertTrue("物品表里应有 oak_boat", Suggest.items.any { it.id == "oak_boat" })
    }

    @Test
    fun `能 give 的合集同时覆盖物品与方块`() {
        // ⚠️ 这是个**真机上复现过的 bug**：基岩版里**方块也能 /give**
        //    （`/give @s oak_log`、`/give @s white_wool`、`/give @s stone` 都成立），
        //    但"纯物品"归 Suggest.items、方块归 Suggest.blocks。
        //    早先 give 的物品参数只查 items，于是 `/give @s oak_log` 会得到
        //    "没有匹配的项" + 一整屏按字母序排的物品 —— 看起来就是"搜不到原木"。
        //
        // 修法：`Suggest.giveableItems` = 方块 ∪ 物品（去重），
        //      并把 gen-commands.mjs 里 `items` 词表映射到它。
        val give = Suggest.giveableItems.map { it.id }.toSet()
        assertTrue("合集应当不小于物品表", give.size >= Suggest.items.size)
        assertTrue("合集应当不小于方块表", give.size >= Suggest.blocks.size)

        // 只能在方块表里找到的（本次 bug 的主体）
        listOf("oak_log", "white_wool", "acacia_planks", "stone", "sand", "glass").forEach { id ->
            assertTrue("$id 是方块但也能 /give，必须在合集中", give.contains(id))
        }
        // 只能在物品表里找到的
        listOf("diamond_sword", "apple", "stick", "bow").forEach { id ->
            assertTrue("$id 是物品，必须在合集中", give.contains(id))
        }
        // 去重：不能有重复 ID
        val ids = Suggest.giveableItems.map { it.id }
        assertEquals("合集里有重复 ID", ids.size, ids.toSet().size)
    }

    @Test
    fun `物品参数真的用上了能 give 的合集`() {
        // 上面查的是数据，这里再走一遍**端到端的补全**，
        // 确认 give 的物品参数确实查的是合集（而不是有人把映射改回去了）。
        listOf("oak_log", "white_wool", "acacia_planks").forEach { id ->
            val r = CommandCompletion.complete("/give @s $id")
            assertTrue(
                "「/give @s $id」应当能补到它自己，实际候选前 5=${r.items.take(5).map { it.value }}",
                r.items.any { it.value == id },
            )
        }
    }

    // =======================================================================
    // 拆分表的结构（JVM 64KB 单方法上限的应对）
    // =======================================================================

    @Test
    fun `大表拆到独立文件后转发依然通`() {
        // items / blocks / sounds / particles 四张表各自在独立 object 里
        // （SuggestItems / SuggestBlocks / SuggestSounds / SuggestParticles），
        // 原因是 Kotlin 会把同一文件所有顶层属性的初始化塞进一个 `<clinit>`，
        // 而 JVM 对单个方法有 64KB 字节码上限 —— 表一大就 `Method too large`。
        // 这里确认转发没问题、内容确实拿到了。
        assertEquals(SuggestItems.items.size, Suggest.items.size)
        assertEquals(SuggestBlocks.blocks.size, Suggest.blocks.size)
        assertEquals(SuggestSounds.sounds.size, Suggest.sounds.size)
        assertEquals(SuggestParticles.particles.size, Suggest.particles.size)

        assertTrue("转发后的物品表为空", Suggest.items.isNotEmpty())
        assertTrue("转发后的方块的表为空", Suggest.blocks.isNotEmpty())
        assertTrue("转发后的音效表为空", Suggest.sounds.isNotEmpty())
    }

    @Test
    fun `词表里没有空 ID 或空名字`() {
        val all = listOf(
            "物品" to Suggest.items, "方块" to Suggest.blocks, "实体" to Suggest.entities,
            "音效" to Suggest.sounds, "粒子" to Suggest.particles,
        )
        all.forEach { (name, table) ->
            table.forEach { t ->
                assertTrue("$name 里有空 ID", t.id.isNotBlank())
                assertTrue("$name 的「${t.id}」名字为空", t.name.isNotBlank())
            }
        }
    }
}
