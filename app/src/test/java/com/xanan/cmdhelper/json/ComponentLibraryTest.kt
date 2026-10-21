package com.xanan.cmdhelper.json

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `give` / `replaceitem` 的组件库与 JSON 补全测试。
 *
 * 场景照玩家的真实输入走：
 * ```
 * /give @s diamond_sword 1 0 {
 *     → 出现 item_name / enchantments / lore … 组件候选（中文可搜）
 * ```
 */
class ComponentLibraryTest {

    private fun complete(input: String, cursor: Int = input.length) =
        CommandCompletion.complete(input, cursor)

    // ── 组件库本身 ──────────────────────────────────────────────────────────

    @Test
    fun `组件都有中文名与模板`() {
        assertTrue("组件太少：${ComponentLibrary.components.size}", ComponentLibrary.components.size >= 12)
        ComponentLibrary.components.forEach { c ->
            assertTrue("${c.id} 没有中文名", c.name.isNotBlank() && c.name != c.id)
            assertTrue("${c.id} 没有说明", c.detail.isNotBlank())
            assertTrue("${c.id} 模板为空", c.template.isNotBlank())
            // 模板必须整段能塞进一个对象里，且自身格式合法
            val err = TextComponent.validate("{" + c.template + "}")
            assertNull("${c.id} 的模板不是合法 JSON：${c.template}", err)
        }
    }

    @Test
    fun `组件 ID 不重复`() {
        val seen = HashSet<String>()
        ComponentLibrary.components.forEach {
            assertTrue("组件 ID 重复：${it.id}", seen.add(it.id))
        }
    }

    @Test
    fun `Wiki 文档化的四个 NBT 组件都在`() {
        // 依据：https://minecraft.wiki/w/Item_components 的 NBT 组件小节
        // 「The available NBT components are minecraft:can_place_on, minecraft:can_destroy,
        //   minecraft:item_lock and minecraft:keep_on_death.」
        listOf(
            "minecraft:can_place_on",
            "minecraft:can_destroy",
            "minecraft:item_lock",
            "minecraft:keep_on_death",
        ).forEach { id ->
            assertNotNull("缺少 Wiki 文档化的组件 $id", ComponentLibrary.find(id))
        }
        assertEquals(4, ComponentLibrary.documentedIds.size)
    }

    @Test
    fun `不含行为包 item JSON 专用的组件`() {
        // 这些是**定义自定义物品**时用的（写在行为包的 minecraft:item 里），
        // 命令里根本用不了。第一版照着"组件列表"抄错了这些，查 Wiki 才发现，这里钉死。
        listOf(
            "minecraft:food", "minecraft:durability", "minecraft:max_stack_size",
            "minecraft:glint", "minecraft:dyed_color", "minecraft:wearable",
            "minecraft:cooldown", "minecraft:repairable", "minecraft:block_placer",
        ).forEach { id ->
            assertNull("命令里用不了这个组件，不该收录：$id", ComponentLibrary.find(id))
        }
    }

    @Test
    fun `keep_on_death 的两种写法都收录`() {
        // 官方示例里带前缀与不带前缀都出现过，玩家两种写法都要能被补全命中
        assertNotNull(ComponentLibrary.find("minecraft:keep_on_death"))
        assertNotNull(ComponentLibrary.find("keep_on_death"))
    }

    @Test
    fun `按中文能搜到组件`() {
        assertTrue(ComponentLibrary.search("附魔").any { it.id == "enchantments" })
        assertTrue(ComponentLibrary.search("改名").any { it.id == "item_name" })
        assertTrue(ComponentLibrary.search("描述").any { it.id == "lore" })
        assertTrue(ComponentLibrary.search("不掉落").any { it.id == "keep_on_death" })
        assertTrue(ComponentLibrary.search("item_name").any { it.id == "item_name" })
    }

    @Test
    fun `拼出来的组件 JSON 合法`() {
        val json = ComponentLibrary.buildJson(listOf("item_name", "enchantments"))
        assertNull(TextComponent.validate(json))
        assertTrue(json.startsWith("{"))
        assertTrue(json.contains("\"item_name\""))
        assertTrue(json.contains("\"enchantments\""))
        // 多个组件之间要有逗号
        assertTrue(json.contains("\",\"") || json.contains(",\"enchantments\""))

        // 一个都不选时给空串（表示不写组件参数）
        assertEquals("", ComponentLibrary.buildJson(emptyList()))
        assertEquals("", ComponentLibrary.buildJson(listOf("不存在的组件")))
    }

    // ── JSON 补全 ──────────────────────────────────────────────────────────

    @Test
    fun `give 的组件里给组件候选而不是文本组件键`() {
        val items = complete("/give @s diamond_sword 1 0 {").items
        val labels = items.map { it.label }
        assertTrue("应给组件候选：$labels", labels.any { it.contains("名称") })
        assertTrue("应有附魔：$labels", labels.any { it.contains("附魔") })
        // get 到的插入文本必须带组件键
        assertTrue(
            "插入文本要带组件键：${items.take(2).map { it.value }}",
            items.any { it.value.startsWith("\"item_name\":") }
        )
        // 不该给文本组件的键
        assertTrue("不该出现 text 键：$labels", labels.none { it == "\"text\"" })
    }

    @Test
    fun `replaceitem 的组件里也给组件候选`() {
        // ⚠️ 输入里必须带 `entity` 判别词 —— 维基 BE 的 replaceitem 两种形式
        //    （`replaceitem entity <target> …` / `replaceitem block <pos> …`）
        //    都要这个判别词，早先的写法把它整个漏了。
        val items = complete("/replaceitem entity @s slot.weapon.mainhand 0 diamond_sword 1 0 {").items
        assertTrue(
            "replaceitem 也要给组件候选：${items.take(3).map { it.label }}",
            items.any { it.value.startsWith("\"item_name\":") }
        )
    }

    @Test
    fun `组件候选按中文能搜到`() {
        val items = complete("/give @s diamond_sword 1 0 {附魔").items
        assertTrue("搜「附魔」应命中 enchantments：${items.map { it.value }}",
            items.any { it.value.contains("\"enchantments\":") })
    }

    @Test
    fun `点亮一个组件后光标落在占位符里`() {
        // item_name 的模板是 "item_name":"<名称>"
        val item = ComponentLibrary.find("item_name")!!
        val at = item.template.indexOf('<') + 1
        assertTrue("模板应有占位符", at > 0)

        val before = "/give @s diamond_sword 1 0 {"
        val result = complete(before)
        val suggestion = result.items.first { it.value.startsWith("\"item_name\":") }
        // 落点 = 光标处 + 组件键前缀 + 模板里 '<' 之后的位置
        assertEquals(before.length + item.template.indexOf('<') + 1, before.length + suggestion.caretOffset)
    }

    @Test
    fun `连续补两个组件会带逗号且 JSON 合法`() {
        var input = "/give @s diamond_sword 1 0 {"
        var result = complete(input)

        val first = result.items.first { it.value.startsWith("\"item_name\":") }
        var applied = CommandCompletion.apply(input, result, first, appendSpace = !first.noSpace)
        input = applied.first
        assertTrue("第一个组件前不该有逗号：$input", !input.contains("{,"))

        // 玩家把占位符改成自己的名字，光标落到末尾
        input = input.replace("<名称>", "宝剑")
        var caret = input.length

        result = complete(input, caret)
        val second = result.items.first { it.value.contains("\"enchantments\":") }
        assertTrue("第二个组件应带逗号：${second.value}", second.value.startsWith(","))

        applied = CommandCompletion.apply(input, result, second, appendSpace = !second.noSpace)
        input = applied.first

        // 补上收尾括号后必须是合法 JSON
        val json = input.substringAfter("1 0 ") + "}"
        val err = TextComponent.validate(json)
        if (err != null) throw AssertionError("生成的组件 JSON 不合法（$err）：$json")
        assertTrue(json.contains("\"item_name\":\"宝剑\""))
        assertTrue(json.contains("\"enchantments\""))
    }

    @Test
    fun `组件补全不补空格`() {
        // 与文本组件同一条规则：JSON 里补空格会拼坏结构
        val result = complete("/give @s diamond_sword 1 0 {")
        assertTrue(result.items.isNotEmpty())
        result.items.forEach {
            assertTrue("组件候选不该补空格：${it.label}", it.noSpace)
        }
    }

    @Test
    fun `文本组件的补全没有被组件库抢走`() {
        // titleraw 的 rawtext 里仍然应该是 text / selector / score
        val items = complete("/titleraw @p actionbar show {\"rawtext\":[{\"text\":\"a\",").items
        val labels = items.map { it.label }
        assertTrue("应仍是文本组件键：$labels", labels.any { it.contains("selector") })
        assertTrue("不该出现物品组件：$labels", labels.none { it.contains("附魔") })
    }
}
