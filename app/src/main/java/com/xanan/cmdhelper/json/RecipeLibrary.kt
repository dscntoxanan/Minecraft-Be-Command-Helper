package com.xanan.cmdhelper.json

/**
 * 玩家指令库 —— 原版指令机器的**实战模板**。
 *
 * ── 这是什么 ────────────────────────────────────────────────────────────────
 * 不是零散的片段，而是把玩家做地图/服务器的**可复用技术**做成模板。
 *
 * ── 命名与说明的写法（重要约定）────────────────────────────────────────────
 *  1. **[Recipe.title] 一律用站长原始 txt 里的叫法**（工作台互传、抽奖机模板、
 *     盔甲架式单方块、玩家称号、计分板-杀怪得金币、雪球菜单、鸡蛋转账、
 *     玩家数量、进服欢迎、T显…）。这是他已经习惯的名字，不要自作主张改写成
 *     "XX菜单""XX系统"这类我自己造的说法。
 *  2. **[Recipe.description] 只写"做出来是什么效果"**，不写具体命令、不写参数名。
 *     玩家看说明是为了判断"这是不是我要的功能"，术语留给 [Recipe.notes]。
 *
 * ── 语法版本（重要）────────────────────────────────────────────────────────
 * 全部按**新版基岩版**写：
 *
 *  1. **execute 用子命令式新语法**。老写法 `execute @a ~~~ run <命令>` 在新版
 *     已废弃，必须写成 `execute as @a at @s run <命令>`。
 *  2. **标题一律用 titleraw + JSON**。`title @a actionbar 文本\n第二行` 这种写法
 *     在新版不行 —— 换行要靠 JSON 里的 `\n`，而且必须转义成 `\\n`。
 *     用 `title` 传字符串时 `\n` 会被当成字面量，屏幕上看到的就是一个斜杠加 n。
 *  3. **子标题用 `titleraw … subtitle`**，不能用 `title … subtitle` 的设置形式
 *     （后者会把主标题顶掉，实测踩过）。
 *
 * ── 关于换行 ────────────────────────────────────────────────────────────────
 * 新版基岩版**不支持命令里直接换行**，多行显示只能靠 JSON 的 `\n`：
 * ```
 * titleraw @a actionbar {"rawtext":[{"text":"第一行\n第二行"}]}
 * ```
 * 本库里的 [Recipe.commands] 每条都是**一行**，可以直接粘进命令方块。
 */
object RecipeLibrary {

    /**
     * 需要玩家替换的占位符。
     *
     * @param token        模板里的记号，形如 `坐标`
     * @param label        中文说明
     * @param defaultValue 默认值 —— 让模板**不改也能直接跑**，玩家再按需替换
     */
    data class Placeholder(val token: String, val label: String, val defaultValue: String)

    /** 一条完整可用的指令机器。 */
    data class Recipe(
        val title: String,
        val category: String,
        /** 这套机制干什么、怎么用。 */
        val description: String,
        /** 用到的新版语法要点 / 坑（可为空）。 */
        val notes: String = "",
        val placeholders: List<Placeholder> = emptyList(),
        /** 命令原文，一条一行，可含占位符与 `\n`。 */
        val commands: List<String>,
    ) {
        /** 用默认值把占位符替换掉，得到可直接执行的一行行命令。 */
        fun build(): List<String> = build(emptyMap())

        /** 用给定值替换占位符；没给的用默认值。 */
        fun build(values: Map<String, String>): List<String> {
            var result = commands
            placeholders.forEach { ph ->
                val value = values[ph.token]?.takeIf { it.isNotBlank() } ?: ph.defaultValue
                result = result.map { it.replace("<${ph.token}>", value) }
            }
            return result
        }
    }

    /** 分组名，按展示顺序。 */
    val categories: List<String> = listOf(
        "交互与菜单",
        "玩家检测",
        "物品与计分",
        "世界与方块",
    )

    // =======================================================================
    // 交互与菜单
    // =======================================================================

    /** 工作台互传：站在工作台上抬头就能和另一个在工作台上抬头的玩家互传。 */
    private fun workbenchTeleport() = Recipe(
        title = "工作台互传",
        category = "交互与菜单",
        description = "两个玩家各站在一个工作台上抬头，就能互相传送过去。抬头的人先发起请求，" +
            "另一个人也抬头就算同意，对方会收到提示。",
        notes = "核心是 **rxm=85**（视线朝上）+ **if block ~ ~-1 ~**（检查脚下是工作台）。\n" +
            "⚠️ 第 2 行 `testfor` **必须独立成一条**，不能写成 `execute … run testfor …` —— " +
            "`run` 后面只能接可执行的子命令，而 `testfor` 是「检测并输出结果」的命令，" +
            "接在后面整条命令无效。\n" +
            "用法：第 2 行写进**条件命令方块**当判定，其余各行写进**循环命令方块**。\n" +
            "rx / rxm 的区别：`rxm` 是俯仰角**上限**（`rxm=85` = 抬头），" +
            "`rx` 是俯仰角**精确值**（`rx=-85` = 低头）。\n" +
            "文本里的 `[§b抬头同意§e]` 是**给玩家看的操作提示**，不是占位符。",
        commands = listOf(
            // 广播请求：抬头 + 脚下是工作台
            "execute as @a[rxm=85] at @s if block ~ ~-1 ~ crafting_table run titleraw @a actionbar " +
                "{\"rawtext\":[{\"text\":\"§e§l玩家 §a\"},{\"selector\":\"@s\"},{\"text\":\" §e正在请求传送 [§b抬头同意§e]\"}]}",
            // 检测对方是否也抬头 —— 独立一条（见 notes 的说明）
            "testfor @p[rx=-85]",
            // 同意后通知发起者
            "execute as @a[rxm=85] at @s if block ~ ~-1 ~ crafting_table run titleraw @s title " +
                "{\"rawtext\":[{\"text\":\"§e§l您已被同意请求\"}]}",
            "execute as @a[rxm=85] at @s if block ~ ~-1 ~ crafting_table run titleraw @s subtitle " +
                "{\"rawtext\":[{\"text\":\"§a§l玩家 §d\"},{\"selector\":\"@p[rx=-85]\"},{\"text\":\" §a同意了你的§e请求\"}]}",
            // 传送到对方身边
            "execute as @a[rxm=85] at @s if block ~ ~-1 ~ crafting_table run tp @s @p[rx=-85]",
        ),
    )

    /** 雪球菜单 / 鸡蛋转账 的骨架：投掷物品打开菜单，抬头确认、低头关闭。 */
    private fun itemMenu(menuName: String, itemType: String, objective: String) = Recipe(
        title = menuName,
        category = "交互与菜单",
        description = "往地上丢一个物品就会弹出菜单，抬头翻到下一项、低头关掉菜单。" +
            "菜单里每一项都能配一个动作（传送、商店、改模式等）。",
        notes = "**整条链路只有 5 步**：加分项 → 检测抬头/低头 → 用 scores 判断当前选项 → 执行 → 关掉菜单。\n" +
            "⚠️ 老写法 `titleraw @a actionbar … 换行 …` 在新版是**无效的**，多行必须写进 JSON 的 `\\n`。" +
            "本库所有条目都已是**一条一行**。",
        placeholders = listOf(
            Placeholder("选项数", "菜单有几项", "5"),
        ),
        commands = listOf(
            "scoreboard objectives add $objective dummy",
            // 投掷物落地 → 给最近的玩家加菜单分
            "execute as @e[type=$itemType] at @s run scoreboard players add @p[r=3] $objective 1",
            "execute as @e[type=$itemType] at @s run playsound random.orb @p",
            "execute as @e[type=$itemType] at @s run give @p $itemType",
            "execute as @e[type=$itemType] at @s run kill @s",
            // 抬头确认 → 执行当前选项
            "execute as @a[scores={$objective=1},rxm=85] run tp @s ~ ~ ~",
            "execute as @a[scores={$objective=2},rxm=85] run kill @s",
            // 低头取消
            "execute as @a[rxm=85] run titleraw @a actionbar {\"rawtext\":[{\"text\":\"§l§a菜单已关闭\"}]}",
            "scoreboard players reset @a[rxm=85] $objective",
            // 分数越界回绕（防止加过头）
            "scoreboard players add @a[scores={$objective=6..}] $objective -<选项数>",
        ),
    )

    /** 雪球菜单的动作栏画面。 */
    private fun snowballMenuDisplay() = Recipe(
        title = "雪球菜单画面",
        category = "交互与菜单",
        description = "动作栏上常驻一份菜单，当前选中的那一项后面带一个 ✔，抬头低头就会换到下一项。",
        notes = "**这是本次最容易踩的坑**：用 `title` 传字符串时 `\\n` 是字面量，屏幕上会看到 `/n`；" +
            "必须用 `titleraw` + JSON，并且 Kotlin/JSON 里要写成 `\\\\n` → 最终命令里是 `\\n`。" +
            "另外每项之间的空行用连续两个 `\\n\\n` 拉开。",
        commands = listOf(
            // 第 1 项被选中（✔ 在第 1 行）
            "titleraw @a[scores={菜单=1}] actionbar {\"rawtext\":[{\"text\":\"§l§d雪球菜单\\n§r§l§b回城§r§l§a✔\\n\\n§r§l§b原地升天§r\\n\\n§l§b凉心商店§r\\n\\n§l§b设置出生点§r\\n\\n§b§l改生存§r\\n\\n§a§l抬头确认，低头取消\"}]}",
            // 第 2 项
            "titleraw @a[scores={菜单=2}] actionbar {\"rawtext\":[{\"text\":\"§l§d雪球菜单\\n§r§l§b回城§r§l\\n\\n§r§l§b原地升天§r§l§a✔\\n\\n§l§b凉心商店§r\\n\\n§l§b设置出生点§r\\n\\n§b§l改生存§r\\n\\n§a§l抬头确认，低头取消\"}]}",
            // 第 3 项
            "titleraw @a[scores={菜单=3}] actionbar {\"rawtext\":[{\"text\":\"§l§d雪球菜单\\n§r§l§b回城§r§l\\n\\n§r§l§b原地升天§r\\n\\n§l§b凉心商店§r§l§a✔\\n\\n§l§b设置出生点§r\\n\\n§b§l改生存§r\\n\\n§a§l抬头确认，低头取消\"}]}",
            // 第 4 项
            "titleraw @a[scores={菜单=4}] actionbar {\"rawtext\":[{\"text\":\"§l§d雪球菜单\\n§r§l§b回城§r§l\\n\\n§r§l§b原地升天§r\\n\\n§l§b凉心商店§r\\n\\n§l§b设置出生点§r§l§a✔\\n\\n§b§l改生存§r\\n\\n§a§l抬头确认，低头取消\"}]}",
            // 第 5 项
            "titleraw @a[scores={菜单=5}] actionbar {\"rawtext\":[{\"text\":\"§l§d雪球菜单\\n§r§l§b回城§r§l\\n\\n§r§l§b原地升天§r\\n\\n§l§b凉心商店§r\\n\\n§l§b设置出生点§r\\n\\n§b§l改生存§r§l§a✔\\n\\n§a§l抬头确认，低头取消\"}]}",
        ),
    )

    // =======================================================================
    // 玩家检测
    // =======================================================================

    /** 抬头确认 / 低头取消。 */
    private fun lookUpDown() = Recipe(
        title = "抬头确认 / 低头取消",
        category = "玩家检测",
        description = "把视角当成按钮：抬头就算确认，低头就算取消。做菜单、商店、确认框都用它。",
        notes = "**rxm=85** 表示「俯仰角最大值 ≤ 85」也就是抬头；" +
            "**rx=-85** 表示「俯仰角 -85」，也就是低头。注意区分 rxm（上限）与 rx（精确值）。",
        commands = listOf(
            // 抬头
            "execute as @a[rxm=85] run titleraw @a actionbar {\"rawtext\":[{\"text\":\"§a已确认\"}]}",
            // 低头
            "execute as @a[rx=-85] run titleraw @a actionbar {\"rawtext\":[{\"text\":\"§c已取消\"}]}",
            // 精确水平（0 度）
            "execute as @a[rxm=0,rx=0] run titleraw @a actionbar {\"rawtext\":[{\"text\":\"§e平视\"}]}",
        ),
    )

    /** 脚下方块检测。 */
    private fun blockUnderFoot() = Recipe(
        title = "脚下方块检测",
        category = "玩家检测",
        description = "玩家踩到指定的方块上就会触发效果，比如站上传送台就被传走。",
        notes = "`~ ~-1 ~` 是**脚下一格**。新语法把条件放在 `run` 之前：`execute as @a at @s if block ~ ~-1 ~ <方块> run <命令>`。",
        placeholders = listOf(
            Placeholder("检测方块", "踩到什么方块触发", "crafting_table"),
            Placeholder("执行命令", "触发后做什么", "titleraw @s actionbar {\"rawtext\":[{\"text\":\"§a触发\"}]}"),
        ),
        commands = listOf(
            "execute as @a at @s if block ~ ~-1 ~ <检测方块> run <执行命令>",
        ),
    )

    /** 按分数走不同分支 —— 同一个触发条件，分数不同效果不同。 */
    private fun scoreSwitch() = Recipe(
        title = "按分数执行不同效果",
        category = "玩家检测",
        description = "同一个触发条件，分数是几就执行第几个效果。做菜单选项、抽奖档位都是这个结构。",
        notes = "条件可以叠加：`@a[scores={菜单=1},rxm=85]` 表示「菜单分数为 1 且抬头」。",
        placeholders = listOf(
            Placeholder("计分项", "用哪个计分板", "菜单"),
        ),
        commands = listOf(
            "execute as @a[scores={<计分项>=1},rxm=85] run titleraw @a actionbar {\"rawtext\":[{\"text\":\"§a执行第 1 项\"}]}",
            "execute as @a[scores={<计分项>=2},rxm=85] run titleraw @a actionbar {\"rawtext\":[{\"text\":\"§a执行第 2 项\"}]}",
            "execute as @a[scores={<计分项>=3},rxm=85] run titleraw @a actionbar {\"rawtext\":[{\"text\":\"§a执行第 3 项\"}]}",
        ),
    )

    /** 玩家数量。 */
    private fun playerCount() = Recipe(
        title = "玩家数量",
        category = "玩家检测",
        description = "实时统计服务器上有多少人在线，可以挂到侧边栏一直显示。",
        notes = "⚠️ 老写法用「循环命令方块 + 每 20 tick 重置再累加」。新版仍需要一个重置步骤：" +
            "`execute as @a run scoreboard players add 计数 <计分项> 1` 是每 tick 累加，" +
            "不先清零就会越加越多。这里给出的就是可用的三行结构。",
        placeholders = listOf(
            Placeholder("计分项", "用哪个计分板", "在线人数"),
        ),
        commands = listOf(
            "scoreboard objectives add <计分项> dummy",
            // 重置
            "scoreboard players set 计数 <计分项> 0",
            // 每个在线玩家 +1
            "execute as @a run scoreboard players add 计数 <计分项> 1",
        ),
    )

    // =======================================================================
    // 物品与计分
    // =======================================================================

    /** 计分板-杀怪得金币。 */
    private fun killReward() = Recipe(
        title = "计分板-杀怪得金币",
        category = "物品与计分",
        description = "玩家打死生物就自动获得金币，动作栏会提示一句，同一个生物不会重复给钱。",
        notes = "**tag=!hd 是防重复的关键**：没有它，同一个经验球每 tick 都会加一次钱。" +
            "处理完立刻 `tag … add hd`，下一 tick 就被排除掉了。",
        placeholders = listOf(
            Placeholder("计分项", "金币记在哪个计分板", "金币"),
            Placeholder("每次奖励", "杀一只给多少", "1"),
        ),
        commands = listOf(
            "scoreboard objectives add <计分项> dummy",
            "execute as @e[type=xp_orb,tag=!hd] run scoreboard players add @p <计分项> <每次奖励>",
            "execute as @e[type=xp_orb,tag=!hd] run titleraw @p actionbar {\"rawtext\":[{\"text\":\"§l§a击杀生物，获得金币\"}]}",
            "execute as @e[type=xp_orb,tag=!hd] run tag @s add hd",
        ),
    )

    /** 丢物品触发效果。 */
    private fun itemInteract() = Recipe(
        title = "丢物品触发效果",
        category = "物品与计分",
        description = "往地上丢一个物品就会触发效果，触发后物品会还回背包，所以可以无限次使用。",
        notes = "顺序很重要：**先加分数、播声音、还物品，最后才 kill**。先 kill 的话后面几条就没实体了。",
        placeholders = listOf(
            Placeholder("物品", "丢什么触发", "snowball"),
            Placeholder("计分项", "记到哪个计分板", "菜单"),
        ),
        commands = listOf(
            "execute as @e[type=<物品>] at @s run scoreboard players add @p[r=3] <计分项> 1",
            "execute as @e[type=<物品>] at @s run playsound random.orb @p",
            "execute as @e[type=<物品>] at @s run give @p <物品>",
            "execute as @e[type=<物品>] at @s run kill @s",
        ),
    )

    // =======================================================================
    // 世界与方块
    // =======================================================================

    /** 抽奖机模板。 */
    private fun lottery() = Recipe(
        title = "抽奖机模板",
        category = "世界与方块",
        description = "箱子里给什么，玩家就能拿到什么，像抽奖一样。抽完箱子的内容会传到展示位，" +
            "原箱清空，掉出来的东西也不会被玩家捡走。",
        notes = "`clone` 先复制、`setblock … destroy` 再清空、最后 `kill` 掉掉落物防止玩家捡到原箱内容。" +
            "三行的顺序不能换。",
        placeholders = listOf(
            Placeholder("箱子坐标", "源箱子坐标", "0 64 0"),
            Placeholder("复制坐标", "复制到的位置", "0 65 0"),
        ),
        commands = listOf(
            "clone <箱子坐标> <箱子坐标> <复制坐标>",
            "setblock <箱子坐标> air 0 destroy",
            "kill @e[name=箱子,r=13]",
            "kill @e[type=item,r=13,c=26]",
        ),
    )

    /** 盔甲架式单方块。 */
    private fun armorStandBlock() = Recipe(
        title = "盔甲架式单方块",
        category = "世界与方块",
        description = "用两个命名的盔甲架当游标，把一小块结构搬到指定位置，做出会移动的单方块机器。",
        notes = "⚠️ 老写法 `testforblock 坐标 air` 在**新版已废弃**，改用 `execute if block`：" +
            "`execute if block 坐标 air run …`。",
        placeholders = listOf(
            Placeholder("目标坐标", "要放结构的位置", "0 64 0"),
        ),
        commands = listOf(
            "execute if block <目标坐标> air run titleraw @a actionbar {\"rawtext\":[{\"text\":\"§a位置可用\"}]}",
            "execute as @e[name=a] run spreadplayers ~ ~ 0 3 @e[name=b]",
            "execute as @e[name=b] at @s run clone ~-2 ~-2 ~-2 ~2 ~2 ~2 <目标坐标>",
        ),
    )

    /** 进服欢迎。 */
    private fun joinWelcome() = Recipe(
        title = "进服欢迎",
        category = "世界与方块",
        description = "玩家一进服务器，屏幕中央就会显示一条彩色大字欢迎他。",
        notes = "新语法 `titleraw @p title` + JSON；颜色代码写在文本里。",
        commands = listOf(
            "titleraw @p title {\"rawtext\":[{\"text\":\"§l§1欢§a迎§e您§c来§b到§d本§9服§6务§7器§5游§0玩\"}]}",
        ),
    )

    /** T显：动作栏常驻信息面板。 */
    private fun statusPanel() = Recipe(
        title = "T显",
        category = "玩家检测",
        description = "动作栏上常驻一块信息牌：自己的名字、附近生物与玩家数、附近掉落物、金币、在线时间。",
        notes = "**这是 titleraw 的 JSON 拼接范例**：文本段之间插入 `{\"selector\":…}` 与 " +
            "`{\"score\":{…}}` 就能把实时数据嵌进一句话里。\n" +
            "⚠️ 老写法 `execute @a ~~~ titleraw …` 已废弃，改成 `execute as @a at @s run titleraw @s …`。",
        commands = listOf(
            "execute as @a at @s run titleraw @s actionbar {\"rawtext\":[{\"text\":\"§e§l姓名§2：\"},{\"selector\":\"@s\"},{\"text\":\" §l§b附近§c生物§c:§3§f\"},{\"selector\":\"@e[r=15,rm=0.45,c=1,type=!player]\"},{\"text\":\" §l§b附近§e玩家:§f\"},{\"selector\":\"@p[r=15,rm=0.5,c=1]\"},{\"text\":\"\\n§l§b附近§c掉落物§c:§f\"},{\"selector\":\"@e[type=item,r=15,rm=0.5,c=1]\"},{\"text\":\"\\n§e金币数量:§f\"},{\"score\":{\"name\":\"@s\",\"objective\":\"金币\"}},{\"text\":\"\\n§b在线时间:§f\"},{\"score\":{\"name\":\"@s\",\"objective\":\"a\"}}]}",
        ),
    )

    /** 玩家称号。 */
    private fun playerTitle() = Recipe(
        title = "玩家称号",
        category = "物品与计分",
        description = "聊天栏里，有称号的玩家名字前面会带上称号，全服都能看到。",
        notes = "**必须用 tellraw + JSON 拼接**：`say` 不支持 selector 组件。" +
            "要发给所有人，收件人写 `@a`（在 `run` 后面），执行者用 `as` 指定。\n" +
            "两条老写法都要改：`execute @a ~~~ tellraw …`（老 execute）与只发给自己" +
            "（`tellraw @s`）—— 后者在多人服里别人看不到。",
        placeholders = listOf(
            Placeholder("标签", "判定称号的标签", "VIP10"),
            Placeholder("称号", "显示的前缀", "§e[VIP10]"),
        ),
        commands = listOf(
            "execute as @a[tag=<标签>] run tellraw @a {\"rawtext\":[{\"text\":\"<称号> §f\"},{\"selector\":\"@s\"},{\"text\":\"§f: \"}]}",
        ),
    )

    /** 整份指令库。 */
    val recipes: List<Recipe> = listOf(
        workbenchTeleport(),
        snowballMenuDisplay(),
        itemMenu("雪球菜单", "snowball", "菜单"),
        itemMenu("鸡蛋转账", "egg", "zz"),
        lookUpDown(),
        statusPanel(),
        blockUnderFoot(),
        scoreSwitch(),
        playerCount(),
        killReward(),
        itemInteract(),
        playerTitle(),
        lottery(),
        armorStandBlock(),
        joinWelcome(),
    )

    /** 按分组取。 */
    fun byCategory(category: String): List<Recipe> = recipes.filter { it.category == category }

    /** 按标题 / 说明 / 命令内容模糊搜索。 */
    fun search(query: String): List<Recipe> {
        val q = query.trim()
        if (q.isEmpty()) return recipes
        return recipes.filter {
            it.title.contains(q, true) || it.description.contains(q, true) ||
                it.notes.contains(q, true) || it.commands.any { c -> c.contains(q, true) }
        }
    }

    /**
     * 把一条配方拼成可以一次粘进命令方块的文本。
     *
     * 新版基岩版**不支持命令里换行**，所以用 `\n` 分隔多条命令的写法在命令方块里
     * 其实是"多条命令依次执行"（链式命令方块 / 函数文件）。这里返回 List 让调用方
     * 自己决定：插入输入框时逐条粘贴，或用换行分隔写进函数文件。
     */
    fun joinForFunction(recipe: Recipe, values: Map<String, String> = emptyMap()): String =
        recipe.build(values).joinToString("\n")
}
