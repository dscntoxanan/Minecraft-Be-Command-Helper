package com.xanan.cmdhelper.ui.dialog

import com.xanan.cmdhelper.data.CommandEntry

/**
 * 「插入……」菜单的片段库。
 *
 * 这些是写命令时最常需要、又最难记的部分：选择器参数、JSON 组件骨架、
 * 坐标写法、计分板语法。把它们做成一点即插的片段，是这个 App 相对"记事本存命令"
 * 的核心价值。
 *
 * 片段文本按 Minecraft 基岩版**新版语法**整理（1.19+ / 1.21 系列均适用）：
 *  * 坐标一律用 `~`/`^` 相对写法，绝对坐标用 `0 0 0`；
 *  * JSON 组件用 `{"rawtext":[{"text":"..."}]}` 形式；
 *  * 目标选择器参数顺序遵循 `@a[tag=...,scores={...}]` 的常规写法。
 */
object SnippetLibrary {

    /** 一个可插入的片段。 */
    data class Snippet(
        val label: String,
        val text: String,
        /** 插入后光标要落在的位置（相对片段开头的偏移），方便直接改参数。 */
        val cursorOffset: Int = text.length
    )

    fun all(): List<Snippet> = listOf(
        Snippet("目标选择器 · 所有玩家", "@a"),
        Snippet("目标选择器 · 最近的玩家", "@p"),
        Snippet("目标选择器 · 随机玩家", "@r"),
        Snippet("目标选择器 · 自己", "@s"),
        Snippet("目标选择器 · 所有实体", "@e"),
        Snippet(
            label = "目标选择器 · 带条件（半径+标签）",
            text = "@a[r=10,tag=队长]",
            cursorOffset = 3
        ),
        Snippet(
            label = "目标选择器 · 按计分板筛选",
            text = "@a[scores={分数=1..}]",
            cursorOffset = 11
        ),
        Snippet(
            label = "JSON 组件 · 纯文本",
            text = "{\"rawtext\":[{\"text\":\"文本\"}]}",
            cursorOffset = 20
        ),
        Snippet(
            label = "JSON 组件 · 多段拼接",
            text = "{\"rawtext\":[{\"text\":\"A\"},{\"text\":\"B\"}]}",
            cursorOffset = 20
        ),
        Snippet(
            label = "JSON 组件 · 读取计分板",
            text = "{\"rawtext\":[{\"score\":{\"name\":\"@s\",\"objective\":\"分数\"}}]}",
            cursorOffset = 40
        ),
        Snippet(
            label = "JSON 组件 · 翻译键（物品名）",
            text = "{\"rawtext\":[{\"translate\":\"item.diamond_sword\"}]}",
            cursorOffset = 32
        ),
        Snippet(
            label = "JSON 组件 · 带样式",
            text = "{\"text\":\"文本\",\"color\":\"gold\",\"bold\":true}",
            cursorOffset = 8
        ),
        Snippet("相对坐标（当前位置）", "~ ~ ~"),
        Snippet("相对坐标（上方 5 格）", "~ ~5 ~"),
        Snippet("局部坐标（朝向前方 3 格）", "^ ^ ^3"),
        Snippet("计分板目标 · 假玩家", "计分板 价格"),
        Snippet("时间 · 正午", "time set noon"),
        Snippet("天气 · 晴天", "weather clear"),
        Snippet("游戏规则 · 保留物品", "gamerule keepInventory true")
    )

    /**
     * 把片段转成一条可保存的命令条目。
     *
     * 目前所有片段都是"命令的一部分"，因此不加 `/` —— 由玩家自己决定最终形态。
     */
    fun toEntry(snippet: Snippet): CommandEntry = CommandEntry(text = snippet.text)
}
