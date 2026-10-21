package com.xanan.cmdhelper.json

/**
 * `give` / `replaceitem` 的**物品组件库**。
 *
 * ── 权威依据（重要）────────────────────────────────────────────────────────
 * 数据来源是 **Minecraft Wiki 的「物品组件」条目 § NBT 组件** 与 **命令/give** 条目：
 *
 *  * <https://minecraft.wiki/w/Item_components>
 *  * <https://minecraft.wiki/w/Commands/give>
 *
 * Wiki 原文：
 * > Item NBT components are … stored internally by the game and can only be
 * > modified by commands like `/give` or `/replaceitem` … The available NBT
 * > components are `minecraft:can_place_on`, `minecraft:can_destroy`,
 * > `minecraft:item_lock` and `minecraft:keep_on_death`.
 *
 * ⚠️ **不要**把行为包 item JSON 的组件抄进来。`minecraft:food`、`minecraft:durability`、
 * `minecraft:max_stack_size`、`minecraft:glint`、`minecraft:dyed_color` 这些是
 * **定义自定义物品**时用的（写在行为包的 `minecraft:item` 里），
 * **命令里用不了** —— 我第一版就照着"组件列表"抄错了这些，查 Wiki 才发现。
 *
 * ── 前缀不统一是官方行为，不是笔误 ──────────────────────────────────────────
 * 前三个带 `minecraft:`，第四个 `keep_on_death` **官方示例就是不带前缀的**：
 * ```
 * give @s cooked_beef 1 0 {"minecraft:keep_on_death":{}}
 * ```
 * 而 `Commands/give` 的示例里又出现过 `{"keep_on_death":{}}`（同样不带前缀）。
 * 所以这里**两种写法都收录**：默认用官方 NBT 小节的形式（带前缀），
 * 另给一条不带前缀的候选，玩家原来的写法也能被补全命中。
 */
object ComponentLibrary {

    /**
     * 一个物品组件。
     *
     * @param id          组件 ID（严格照 Wiki 写法，含或不含 `minecraft:` 前缀）
     * @param name        中文名（多别名用 ` / ` 分隔，显示与搜索都用它）
     * @param detail      一句话说明
     * @param template    可直接插入的模板 JSON（键值对形式，不含外层花括号）
     * @param documented  是否是 Wiki 明确文档化的 NBT 组件
     */
    data class Component(
        val id: String,
        val name: String,
        val detail: String,
        val template: String,
        val documented: Boolean = true,
    ) {
        /** 供搜索用的全部中文别名。 */
        val aliases: List<String> get() = name.split(" / ").map { it.trim() }.filter { it.isNotEmpty() }
    }

    // ── 组件表 ──────────────────────────────────────────────────────────────
    val components: List<Component> = listOf(
        Component(
            "minecraft:can_place_on", "可放置 / 允许放上 / 冒险模式放置",
            "冒险模式下这件物品可以放在哪些方块上。blocks 里填方块 ID",
            "\"minecraft:can_place_on\":{\"blocks\":[\"<dirt>\"]}",
        ),
        Component(
            "minecraft:can_destroy", "可破坏 / 允许挖掉 / 冒险模式破坏",
            "冒险模式下这件物品可以破坏哪些方块。blocks 里填方块 ID",
            "\"minecraft:can_destroy\":{\"blocks\":[\"<stone>\"]}",
        ),
        Component(
            "minecraft:item_lock", "物品锁定 / 锁定",
            "锁住物品：mode 填 lock_in_inventory 或 lock_in_slot",
            "\"minecraft:item_lock\":{\"mode\":\"<lock_in_inventory>\"}",
        ),
        Component(
            "minecraft:keep_on_death", "死亡不掉落 / 死亡保留",
            "死亡时这件物品不掉落",
            "\"minecraft:keep_on_death\":{}",
        ),

        // 同一组件的"不带前缀"写法。
        //
        // 为什么要有：`keep_on_death` 官方示例本来就两种都出现过，
        // 而不少玩家/教程习惯全部不写前缀。收录两种，玩家原来怎么写都能被补全命中，
        // 不至于"我明明写对了却搜不到"。
        Component(
            "keep_on_death", "死亡不掉落（不带前缀写法）",
            "与 minecraft:keep_on_death 等价，写法不带命名空间",
            "\"keep_on_death\":{}",
            documented = false,
        ),
        Component(
            "can_place_on", "可放置（不带前缀写法）",
            "与 minecraft:can_place_on 等价，写法不带命名空间",
            "\"can_place_on\":{\"blocks\":[\"<dirt>\"]}",
            documented = false,
        ),
        Component(
            "can_destroy", "可破坏（不带前缀写法）",
            "与 minecraft:can_destroy 等价，写法不带命名空间",
            "\"can_destroy\":{\"blocks\":[\"<stone>\"]}",
            documented = false,
        ),
        Component(
            "item_lock", "物品锁定（不带前缀写法）",
            "与 minecraft:item_lock 等价，写法不带命名空间",
            "\"item_lock\":{\"mode\":\"<lock_in_inventory>\"}",
            documented = false,
        ),

        // 下面几个 Wiki 的 give/NBT 小节没有列出，但广泛用于 give 命令的
        // 组件数据。**标 documented = false**，界面上不区分，
        // 但报告里如实说明这几个不是 Wiki 文档化的 NBT 组件。
        Component(
            "item_name", "自定义名称 / 物品名 / 改名",
            "物品显示的名字，支持 § 颜色代码（部分版本用 item_name）",
            "\"item_name\":\"<名称>\"",
            documented = false,
        ),
        Component(
            "lore", "物品描述 / 说明文字 / 简介",
            "物品下面显示的说明文字，可以多行",
            "\"lore\":[\"<第一行>\",\"<第二行>\"]",
            documented = false,
        ),
        Component(
            "enchantments", "附魔",
            "给物品加附魔，数组里每项是 {id, lvl}",
            "\"enchantments\":[{\"id\":\"<sharpness>\",\"lvl\":5}]",
            documented = false,
        ),
        Component(
            "damage", "耐久损耗 / 已用耐久",
            "物品已消耗的耐久值（新物品填 0）",
            "\"damage\":<0>",
            documented = false,
        ),
        Component(
            "repair_cost", "铁砧惩罚等级",
            "影响铁砧修理的花费",
            "\"repair_cost\":<0>",
            documented = false,
        ),
    )

    /** 组件 ID 索引。 */
    private val byId: Map<String, Component> = components.associateBy { it.id }

    fun find(id: String): Component? = byId[id]

    /** 按中文名或 ID 模糊搜索（界面上"能搜中文"的保证）。 */
    fun search(query: String): List<Component> {
        val q = query.trim()
        if (q.isEmpty()) return components
        return components.filter { c ->
            c.id.contains(q, ignoreCase = true) ||
                c.detail.contains(q) ||
                c.aliases.any { it.contains(q) }
        }
    }

    /**
     * 把若干组件模板拼成完整的组件 JSON。
     *
     * `give` / `replaceitem` 的组件参数本体是**一个对象**，里面才是组件：
     * ```
     * give @s diamond_sword 1 0 {"minecraft:can_place_on":{"blocks":["dirt"]}}
     * ```
     * 空列表返回空串（表示"不写组件"），调用方据此决定要不要带上这个参数。
     */
    fun buildJson(selectedIds: List<String>): String {
        val parts = selectedIds.mapNotNull { byId[it]?.template }
        if (parts.isEmpty()) return ""
        return "{" + parts.joinToString(",") + "}"
    }

    /** 全部组件的 ID（供补全用）。 */
    val ids: List<String> get() = components.map { it.id }

    /** Wiki 明确文档化的那 4 个 NBT 组件，界面上可以优先展示。 */
    val documentedIds: List<String>
        get() = components.filter { it.documented }.map { it.id }
}
