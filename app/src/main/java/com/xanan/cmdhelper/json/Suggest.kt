package com.xanan.cmdhelper.json

/**
 * 常用取值库 —— 自动补全的"词表"，每个值都带中文名。
 *
 * ⚠️ **本文件由工具生成，不要手改**（改完下次生成会被覆盖）。
 *    生成脚本：`.tmp/mc-lang/gen-suggest.mjs`（数据来自下面两个来源）。
 *
 * ── 数据来源与依据 ──────────────────────────────────────────────────────────
 *  1. **官方中文译名**：中国版/Mojang 官方资源包的 `texts/zh_CN.lang`
 *     （https://github.com/Mojang/bedrock-samples）。
 *  2. **ID 以命令为准**：贴图文件名和命令里的 ID 经常不一致
 *     （`apple_golden.png` 的 ID 是 `golden_apple`、`tile.grass.name` 的 ID 是
 *     `grass_block`），所以只要两者冲突，一律**以官方语言文件里的命令 ID 为准**。
 *     `WSX/Be原版材质包.zip` 只用来核对"这个 ID 是否真的存在贴图"。
 *  3. **俗名**：官方译名往往不是玩家嘴里的说法，所以同一行会写成
 *     `官方译名 / 俗名`（例：`钻石护腿 / 钻石裤`），搜索时两种叫法都能命中。
 *
 * ── 一条重要约定 ────────────────────────────────────────────────────────────
 * 中文名**只用于显示**；真正插进命令里的永远是 [Term.id] 这个英文 ID。
 * 拿中文名去拼命令会直接生成无效命令，`CommandCompletionTest` 里有测试钉住这条。
 */
object Suggest {

    /**
     * 一个候选词。
     *
     * @param id   真正插入到命令里的英文 ID
     * @param name 界面显示名（官方译名，可能带 ` / ` 分隔的俗名）
     */
    data class Term(val id: String, val name: String)

    /** 目标选择器：符号本身不好记，中文名就是它的用途。 */
    val selectors: List<Pair<String, String>> = listOf(
        "@s" to "自己",
        "@a" to "所有玩家",
        "@p" to "最近的玩家",
        "@r" to "随机玩家",
        "@e" to "所有实体",
        "@n" to "最近的实体",
        "@initiator" to "点 NPC 对话按钮的玩家"
    )

    /** 实体（用于 summon 与 @e[type=…]）。 */
    val entities: List<Term> = listOf(
        Term("agent", "代理机器人"),
        Term("allay", "悦灵 / 小精灵"),
        Term("area_effect_cloud", "区域效果云雾"),
        Term("armadillo", "犰狳"),
        Term("armor_stand", "盔甲架"),
        Term("arrow", "箭"),
        Term("axolotl", "美西螈"),
        Term("bat", "蝙蝠"),
        Term("bee", "蜜蜂"),
        Term("blaze", "烈焰人"),
        Term("boat", "船"),
        Term("bogged", "沼骸"),
        Term("breeze", "旋风人"),
        Term("breeze_wind_charge_projectile", "风弹"),
        Term("camel", "骆驼"),
        Term("camel_husk", "骆驼尸傀"),
        Term("cat", "猫"),
        Term("cave_spider", "洞穴蜘蛛"),
        Term("chest_boat", "运输船"),
        Term("chest_minecart", "带箱子的矿车"),
        Term("chest_raft", "带箱子的竹筏"),
        Term("chicken", "鸡"),
        Term("cod", "鳕鱼"),
        Term("command_block_minecart", "命令方块矿车"),
        Term("copper_golem", "铜傀儡"),
        Term("cow", "牛"),
        Term("creaking", "嘎枝"),
        Term("creeper", "苦力怕 / 爬行者"),
        Term("cushion", "坐垫"),
        Term("dolphin", "海豚"),
        Term("donkey", "驴"),
        Term("dragon_fireball", "末影龙火球"),
        Term("drowned", "溺尸"),
        Term("egg", "鸡蛋"),
        Term("elder_guardian", "远古守卫者 / 远古海守卫"),
        Term("ender_crystal", "末影水晶"),
        Term("ender_dragon", "末影龙"),
        Term("ender_eye", "末影之眼 / 末影之眼信号"),
        Term("ender_pearl", "末影珍珠"),
        Term("enderman", "末影人"),
        Term("endermite", "末影螨"),
        Term("evocation_fang", "唤魔者尖牙"),
        Term("evocation_illager", "唤魔者"),
        Term("experience_bottle", "附魔之瓶 / 经验瓶"),
        Term("experience_orb", "经验球"),
        Term("eye_of_ender_signal", "末影之眼"),
        Term("falling_block", "下落的方块"),
        Term("fireball", "火球"),
        Term("fireworks_rocket", "焰火火箭"),
        Term("fishing_hook", "鱼钩"),
        Term("fox", "狐狸"),
        Term("frog", "青蛙"),
        Term("furnace_minecart", "动力矿车"),
        Term("ghast", "恶魂"),
        Term("glow_squid", "荧光鱿鱼"),
        Term("goat", "山羊"),
        Term("guardian", "守卫者 / 海守卫"),
        Term("happy_ghast", "善念恶魂"),
        Term("hoglin", "疣猪兽 / 疣猪"),
        Term("hopper_minecart", "漏斗矿车"),
        Term("horse", "马"),
        Term("husk", "尸壳"),
        Term("iron_golem", "铁傀儡 / 铁巨人"),
        Term("item", "物品"),
        Term("leash_knot", "拴绳结"),
        Term("lightning_bolt", "闪电 / 闪电束"),
        Term("lingering_potion", "滞留药水"),
        Term("llama", "羊驼"),
        Term("llama_spit", "羊驼口水"),
        Term("magma_cube", "岩浆怪"),
        Term("minecart", "矿车"),
        Term("mooshroom", "哞菇"),
        Term("moving_block", "移动中的方块"),
        Term("mule", "骡"),
        Term("nautilus", "鹦鹉螺"),
        Term("npc", "NPC"),
        Term("ocelot", "豹猫"),
        Term("ominous_item_spawner", "不祥物品生成器"),
        Term("painting", "画"),
        Term("panda", "熊猫"),
        Term("parched", "焦骷髅"),
        Term("parrot", "鹦鹉"),
        Term("phantom", "幻翼"),
        Term("pig", "猪"),
        Term("piglin", "猪灵 / 猪灵蛮兵"),
        Term("piglin_brute", "猪灵蛮兵"),
        Term("pillager", "掠夺者"),
        Term("player", "玩家"),
        Term("polar_bear", "北极熊"),
        Term("pufferfish", "河豚"),
        Term("rabbit", "兔子"),
        Term("ravager", "劫掠兽"),
        Term("salmon", "鲑鱼"),
        Term("sheep", "羊"),
        Term("shulker", "潜影贝 / 潜影盒怪"),
        Term("shulker_bullet", "潜影贝子弹"),
        Term("silverfish", "蠹虫"),
        Term("skeleton", "骷髅 / 骨骸"),
        Term("skeleton_horse", "骷髅马"),
        Term("slime", "史莱姆"),
        Term("small_fireball", "小火球"),
        Term("sniffer", "嗅探兽"),
        Term("snow_golem", "雪傀儡 / 雪人"),
        Term("snowball", "雪球"),
        Term("spider", "蜘蛛"),
        Term("splash_potion", "药水"),
        Term("squid", "鱿鱼"),
        Term("stray", "流浪者"),
        Term("strider", "炽足兽"),
        Term("sulfur_cube", "硫方怪"),
        Term("tadpole", "蝌蚪"),
        Term("thrown_trident", "三叉戟"),
        Term("tnt", "TNT 方块"),
        Term("tnt_minecart", "TNT 矿车"),
        Term("trader_llama", "行商羊驼"),
        Term("trial_spawner", "试炼刷怪笼"),
        Term("tripod_camera", "三脚架摄像机"),
        Term("tropicalfish", "热带鱼"),
        Term("turtle", "海龟"),
        Term("unknown", "未知"),
        Term("vex", "恼鬼"),
        Term("villager", "村民"),
        Term("villager_v2", "村民"),
        Term("vindicator", "卫道士"),
        Term("wandering_trader", "流浪商人"),
        Term("warden", "监守者 / 循声守卫"),
        Term("wind_charge_projectile", "风弹"),
        Term("witch", "女巫"),
        Term("wither", "凋灵"),
        Term("wither_skeleton", "凋灵骷髅"),
        Term("wither_skull", "凋灵头颅"),
        Term("wither_skull_dangerous", "凋灵头颅"),
        Term("wolf", "狼"),
        Term("xp_bottle", "附魔之瓶"),
        Term("xp_orb", "经验球"),
        Term("zoglin", "僵尸疣猪兽"),
        Term("zombie", "僵尸 / 丧尸"),
        Term("zombie_horse", "僵尸马"),
        Term("zombie_nautilus", "僵尸鹦鹉螺"),
        Term("zombie_pigman", "僵尸猪灵"),
        Term("zombie_villager", "僵尸村民"),
        Term("zombie_villager_v2", "怪人村民")
    )

    /** 常用状态效果。 */
    val effects: List<Term> = listOf(
        Term("speed", "速度提升 / 速度"),
        Term("slowness", "缓慢"),
        Term("haste", "急迫"),
        Term("mining_fatigue", "挖掘疲劳"),
        Term("strength", "力量"),
        Term("instant_health", "瞬间治疗"),
        Term("instant_damage", "瞬间伤害"),
        Term("jump_boost", "跳跃提升"),
        Term("nausea", "反胃"),
        Term("regeneration", "生命恢复 / 回血"),
        Term("resistance", "抗性提升"),
        Term("fire_resistance", "抗火"),
        Term("water_breathing", "水下呼吸"),
        Term("invisibility", "隐身"),
        Term("blindness", "失明"),
        Term("night_vision", "夜视"),
        Term("hunger", "饥饿"),
        Term("weakness", "虚弱"),
        Term("poison", "中毒"),
        Term("wither", "凋零"),
        Term("health_boost", "生命提升"),
        Term("absorption", "伤害吸收 / 吸收"),
        Term("saturation", "饱和"),
        Term("levitation", "漂浮"),
        Term("slow_falling", "缓降"),
        Term("conduit_power", "潮涌能量"),
        Term("village_hero", "村庄英雄"),
        Term("darkness", "黑暗"),
        Term("bad_omen", "不祥之兆"),
        Term("clear", "清除所有效果")
    )

    /** 游戏规则。 */
    val gamerules: List<Term> = listOf(
        Term("keepInventory", "保留物品栏 / 死亡不掉落"),
        Term("mobGriefing", "生物破坏方块"),
        Term("doDaylightCycle", "昼夜更替"),
        Term("doWeatherCycle", "天气更替"),
        Term("showcoordinates", "显示坐标"),
        Term("commandBlockOutput", "命令方块输出"),
        Term("sendCommandFeedback", "命令执行反馈"),
        Term("doFireTick", "火焰蔓延"),
        Term("doMobSpawning", "生物生成"),
        Term("pvp", "友军伤害 / PVP"),
        Term("naturalRegeneration", "自然生命恢复 / 自然回血"),
        Term("doMobLoot", "生物战利品 / 生物掉落"),
        Term("doTileDrops", "方块掉落"),
        Term("keepCommandBlock", "保留命令方块"),
        Term("showtags", "显示实体标签"),
        Term("doImmediateRespawn", "立即重生"),
        Term("fallDamage", "摔落伤害"),
        Term("fireDamage", "火焰伤害"),
        Term("drowningDamage", "溺水伤害"),
        Term("freezeDamage", "冰冻伤害")
    )

    /** 游戏模式。 */
    val gameModes: List<Term> = listOf(
        Term("survival", "生存"),
        Term("creative", "创造"),
        Term("adventure", "冒险"),
        Term("spectator", "旁观")
    )

    /** 附魔 ID。 */
    val enchantments: List<Term> = listOf(
        Term("sharpness", "锋利"),
        Term("smite", "亡灵杀手"),
        Term("bane_of_arthropods", "节肢杀手"),
        Term("efficiency", "效率"),
        Term("silk_touch", "精准采集"),
        Term("fortune", "时运"),
        Term("unbreaking", "耐久"),
        Term("mending", "经验修补"),
        Term("protection", "保护"),
        Term("fire_protection", "火焰保护"),
        Term("feather_falling", "摔落保护"),
        Term("blast_protection", "爆炸保护"),
        Term("projectile_protection", "弹射物保护"),
        Term("thorns", "荆棘"),
        Term("respiration", "水下呼吸"),
        Term("aqua_affinity", "水下速掘"),
        Term("depth_strider", "深海探索者"),
        Term("frost_walker", "冰霜行者"),
        Term("soul_speed", "灵魂疾行"),
        Term("knockback", "击退"),
        Term("fire_aspect", "火焰附加"),
        Term("looting", "抢夺"),
        Term("power", "力量（弓）"),
        Term("punch", "冲击（弓）"),
        Term("flame", "火矢"),
        Term("infinity", "无限"),
        Term("multishot", "多重射击"),
        Term("quick_charge", "快速装填"),
        Term("piercing", "穿透"),
        Term("loyalty", "忠诚"),
        Term("impaling", "穿刺"),
        Term("riptide", "激流"),
        Term("channeling", "引雷")
    )

    /**
     * JSON 组件骨架。
     *
     * 手写 JSON 是这套命令里最容易出错的部分（引号、括号、中英混排），
     * 所以这里给的是**完整可直接用**的骨架，而不是需要玩家自己补全的半成品。
     */
    val jsonTemplates: List<Pair<String, String>> = listOf(
        "{\"rawtext\":[{\"text\":\"你好\"}]}" to "纯文本",
        "{\"rawtext\":[{\"text\":\"第 \"},{\"score\":{\"name\":\"@s\",\"objective\":\"分数\"}},{\"text\":\" 分\"}]}" to "文本 + 计分板分数",
        "{\"rawtext\":[{\"selector\":\"@s\"},{\"text\":\"，欢迎！\"}]}" to "玩家名 + 文本",
        "{\"rawtext\":[{\"translate\":\"item.diamond_sword\"}]}" to "翻译键（随语言变化）",
        "{\"text\":\"金色粗体\",\"color\":\"gold\",\"bold\":true}" to "带样式（直接用在 text 字段）",
        "{\"item_name\":\"名刀·村正\"}" to "give 的自定义名称",
        "{\"item_name\":\"宝剑\",\"enchantments\":[{\"id\":\"sharpness\",\"lvl\":5}]}" to "give 的名称 + 附魔",
        "{\"minecraft:can_destroy\":{\"blocks\":[\"dirt\"]}}" to "give 的可破坏方块"
    )

    // ── 大表转发 ────────────────────────────────────────────────────────────
    // items / blocks / sounds / particles 四张表各自在独立文件里（见 SuggestItems 等），
    // 原因见 SuggestItems 的注释（JVM 单方法 64KB 上限）。
    // 这里只做转发，调用方仍然写 `Suggest.items`，不受影响。
    /** 物品（用于 give / replaceitem）。 */
    val items: List<Term> get() = SuggestItems.items

    /** 方块（用于 setblock / fill）。 */
    val blocks: List<Term> get() = SuggestBlocks.blocks

    /** 粒子（对应 /particle 的第一个参数）。 */
    val particles: List<Term> get() = SuggestParticles.particles

    /** 音效（对应 /playsound 的第一个参数）。 */
    val sounds: List<Term> get() = SuggestSounds.sounds

    /**
     * **能 /give 的全部 ID** = 物品 ∪ 方块。
     *
     * 基岩版里方块也是合法的 /give 目标，所以补全必须用这张合集，
     * 不能只用 [items]。详见生成器里对应的注释。
     */
    val giveableItems: List<Term> = (blocks + items).distinctBy { it.id }

}
