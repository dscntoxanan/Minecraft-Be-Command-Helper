package com.xanan.cmdhelper.json

/**
 * 目标选择器的 `[]` 参数库。
 *
 * ── 权威依据 ────────────────────────────────────────────────────────────────
 * <https://minecraft.wiki/w/Target_selectors>（Minecraft Wiki · 目标选择器）
 *
 * ⚠️ **基岩版和 Java 版的参数名差别很大，不要混用**。Wiki 的汇总表明确标了 `[BE only]`：
 *
 * | 用途 | Java | **基岩版（本表采用）** |
 * |---|---|---|
 * | 距离 | `distance` | **`r` / `rm`** |
 * | 垂直朝向 | `x_rotation` | **`rx` / `rxm`** |
 * | 水平朝向 | `y_rotation` | **`ry` / `rym`** |
 * | 等级 | `level` | **`l` / `lm`** |
 * | 游戏模式 | `gamemode` | **`m`** |
 * | 数量限制 | `limit` / `sort` | **`c`** |
 * | 队伍 | `team` | *基岩版没有* |
 * | NBT | `nbt` | *基岩版没有* |
 *
 * 另外基岩版**独有** `family`、`hasitem`、`haspermission`、`has_property`。
 *
 * 写错参数名不会报错、只是"一个也选不中"，所以这里严格只收基岩版能用的。
 */
object SelectorLibrary {

    /** 参数值的填写类型，决定给什么候选。 */
    enum class ValueKind {
        /** 实体类型（type）—— 用实体词表。 */
        ENTITY_TYPE,

        /** 游戏模式（m）。 */
        GAMEMODE,

        /** 实体族（family）—— 基岩版专有。 */
        FAMILY,

        /** 自由文本 / 数字 / 坐标：不给候选，只给提示。 */
        FREE,
    }

    /**
     * 一个选择器参数。
     *
     * @param name      参数名（严格照基岩版 Wiki）
     * @param cn        中文名 / 别称（` / ` 分隔，显示与搜索都用）
     * @param detail    一句话说明
     * @param valueKind 值的类型
     * @param example   一段可直接用的示例（不含 `@e[` 前缀）
     * @param appliesTo 适用于哪些选择器变量；空表示全部
     */
    data class Argument(
        val name: String,
        val cn: String,
        val detail: String,
        val valueKind: ValueKind = ValueKind.FREE,
        val example: String,
        val appliesTo: List<String> = emptyList(),
    ) {
        val aliases: List<String> get() = cn.split(" / ").map { it.trim() }.filter { it.isNotEmpty() }
    }

    // ── 基岩版选择器变量 ────────────────────────────────────────────────────
    /** 变量 → 中文说明。 */
    val variables: List<Pair<String, String>> = listOf(
        "@p" to "最近的玩家",
        "@r" to "随机玩家（基岩版可用 type= 选生物）",
        "@a" to "所有玩家",
        "@e" to "所有实体",
        "@s" to "命令的执行者自己",
        "@n" to "最近的实体（基岩版）",
        "@initiator" to "点 NPC 对话按钮的玩家（基岩版）",
    )

    // ── 参数表 ──────────────────────────────────────────────────────────────
    val arguments: List<Argument> = listOf(
        Argument(
            "type", "类型 / 实体类型 / 种类", "按实体 ID 筛选，填 type=zombie 或 type=!zombie",
            ValueKind.ENTITY_TYPE, "type=zombie",
        ),
        Argument(
            "name", "名字 / 名称", "按名字筛选，带空格要用双引号；加 ! 表示排除",
            ValueKind.FREE, "name=\"Steve\"",
        ),
        Argument(
            "tag", "标签", "按计分板标签筛选；tag=!x 排除，tag= 表示没有任何标签",
            ValueKind.FREE, "tag=队长",
        ),
        Argument(
            "family", "族 / 生物族", "按实体族筛选（基岩版专有），如 mob / monster / undead",
            ValueKind.FAMILY, "family=monster",
        ),

        // 空间
        Argument(
            "x", "中心 X", "选择起点坐标（配合 r / dx 使用）", ValueKind.FREE, "x=0",
        ),
        Argument(
            "y", "中心 Y", "选择起点坐标", ValueKind.FREE, "y=64",
        ),
        Argument(
            "z", "中心 Z", "选择起点坐标", ValueKind.FREE, "z=0",
        ),
        Argument(
            "r", "最大距离", "只选距离小于等于 r 的（基岩版专有，对应 Java 的 distance）",
            ValueKind.FREE, "r=10",
        ),
        Argument(
            "rm", "最小距离", "只选距离大于等于 rm 的", ValueKind.FREE, "rm=5",
        ),
        Argument(
            "dx", "X 范围", "从起点往 X 方向延伸多少格（选矩形区域）", ValueKind.FREE, "dx=5",
        ),
        Argument(
            "dy", "Y 范围", "从起点往 Y 方向延伸多少格", ValueKind.FREE, "dy=3",
        ),
        Argument(
            "dz", "Z 范围", "从起点往 Z 方向延伸多少格", ValueKind.FREE, "dz=5",
        ),

        // 朝向
        Argument(
            "rx", "垂直朝向上限", "俯仰角上限，-90 抬头、0 平视、90 低头", ValueKind.FREE, "rx=0",
        ),
        Argument(
            "rxm", "垂直朝向下限", "俯仰角下限", ValueKind.FREE, "rxm=-90",
        ),
        Argument(
            "ry", "水平朝向上限", "偏航角上限，0 朝南、90 朝西、±180 朝北", ValueKind.FREE, "ry=90",
        ),
        Argument(
            "rym", "水平朝向下限", "偏航角下限", ValueKind.FREE, "rym=-90",
        ),

        // 玩家数据
        Argument(
            "l", "等级上限", "经验等级上限（基岩版专有，对应 Java 的 level）",
            ValueKind.FREE, "l=30",
        ),
        Argument(
            "lm", "等级下限", "经验等级下限", ValueKind.FREE, "lm=10",
        ),
        Argument(
            "m", "游戏模式", "按游戏模式筛选（基岩版专有），值用 survival/creative/adventure/spectator",
            ValueKind.GAMEMODE, "m=creative",
        ),
        Argument(
            "c", "数量限制", "只选最近的 c 个；c=-1 表示取最远的（基岩版专有，对应 Java 的 limit）",
            ValueKind.FREE, "c=1",
        ),
        Argument(
            "scores", "计分板分数", "按计分板分数筛选，写成 {\"目标名\":值}；基岩版可用 ! 取反",
            ValueKind.FREE, "scores={\"分数\"=10}",
        ),

        // 物品
        Argument(
            "hasitem", "拥有物品", "按背包里的物品筛选（基岩版专有），如 {item=diamond,quantity=1..}",
            ValueKind.FREE, "hasitem={item=diamond}",
        ),

        // 权限与属性
        Argument(
            "haspermission", "权限状态", "按玩家权限筛选（基岩版专有），如 {camera=enabled}",
            ValueKind.FREE, "haspermission={camera=enabled}",
        ),
        Argument(
            "has_property", "实体属性", "按实体属性筛选（基岩版专有），如 {minecraft:has_nectar=true}",
            ValueKind.FREE, "has_property={minecraft:has_nectar=true}",
        ),
    )

    private val byName: Map<String, Argument> = arguments.associateBy { it.name }

    fun find(name: String): Argument? = byName[name.trim()]

    /** 全部参数名。 */
    val names: List<String> get() = arguments.map { it.name }

    /** 按中文名或参数名模糊搜索。 */
    fun search(query: String): List<Argument> {
        val q = query.trim()
        if (q.isEmpty()) return arguments
        return arguments.filter { a ->
            a.name.contains(q, ignoreCase = true) ||
                a.detail.contains(q) ||
                a.aliases.any { it.contains(q) }
        }
    }

    /**
     * 按选择器变量筛参数。
     *
     * 目前 `@p` / `@r` / `@a` 这类"玩家型"选择器与 `@e` 用的参数集合基本一致，
     * 所以不过滤，只保留这个入口便于以后细分（Wiki 的"Player type"一节有说明）。
     */
    fun argumentsFor(variable: String): List<Argument> {
        val v = variable.trim()
        return arguments.filter { it.appliesTo.isEmpty() || v in it.appliesTo }
    }

    /** 游戏模式的可用值（照 Wiki：基岩版还能用单字母与数字简写）。 */
    val gameModes: List<Pair<String, String>> = listOf(
        "survival" to "生存",
        "creative" to "创造",
        "adventure" to "冒险",
        "spectator" to "旁观",
        "default" to "默认（基岩版）",
        "s" to "生存（简写）",
        "c" to "创造（简写）",
        "a" to "冒险（简写）",
        "d" to "默认（简写）",
    )

    /**
     * 实体族的合法值。
     *
     * ⚠️ 不要在这里手写族名 —— 数据来自官方行为包，由 [EntityFamilies] 提供
     * （见 `tools/suggest/fetch-families.mjs`）。Wiki 只举了几个例子（mob / monster /
     * undead / zombie / skeleton…），真正的清单有 136 个，只写那 10 个会让玩家
     * "知道要写 family 却找不到能用的值"。
     */
    val families: List<Pair<String, String>> get() = EntityFamilies.all
}
