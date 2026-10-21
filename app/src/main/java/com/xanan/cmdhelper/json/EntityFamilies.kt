// 由 tools/suggest/gen-families.mjs 生成，不要手改
// 数据来源：Mojang 官方 behavior_pack/entities 下的实体定义
// 共 136 个族（有中文名 136 个）

package com.xanan.cmdhelper.json

/**
 * 实体族清单（`@e[family=…]` 的合法值）。
 *
 * ⚠️ **本文件由工具生成，不要手改**。数据来自 Mojang 官方行为包里
 * 每个实体的 `minecraft:type_family` 组件 —— 这是唯一的权威来源
 * （语言文件里没有族名，Wiki 也只举了几个例子）。
 *
 * 生成链：`fetch-families.mjs`（联网抓官方包）→ `gen-families.mjs`（配中文名）。
 */
internal object EntityFamilies {
    /** 族名 → 中文名；没有官方译名的保留英文 ID，不编造翻译。 */
    val all: List<Pair<String, String>> = listOf(
        "actor" to "实体（总类）", // 1 个实体，如 cushion
        "adult_piglin" to "成年猪灵", // 1 个实体，如 piglin_brute
        "allay" to "悦灵", // 1 个实体，如 allay
        "animal" to "动物", // 2 个实体，如 goat/sulfur_cube
        "aquatic" to "水生生物", // 14 个实体，如 axolotl/dolphin/elder_guardian
        "armadillo" to "犰狳", // 1 个实体，如 armadillo
        "armor_stand" to "盔甲架", // 1 个实体，如 armor_stand
        "armorer" to "盔甲匠", // 1 个实体，如 zombie_villager
        "arthropod" to "节肢动物", // 5 个实体，如 bee/cave_spider/endermite
        "axolotl" to "美西螈", // 1 个实体，如 axolotl
        "baby_piglin" to "幼年猪灵", // 1 个实体，如 piglin
        "baby_turtle" to "幼年海龟", // 1 个实体，如 turtle
        "baby_undead" to "幼年亡灵", // 2 个实体，如 drowned/zombie_pigman
        "baby_zombie_pigman" to "幼年僵尸猪灵", // 1 个实体，如 zombie_pigman
        "bat" to "蝙蝠", // 1 个实体，如 bat
        "bee" to "蜜蜂", // 1 个实体，如 bee
        "blaze" to "烈焰人", // 1 个实体，如 blaze
        "boat" to "船", // 2 个实体，如 boat/chest_boat
        "bogged" to "沼骸", // 1 个实体，如 bogged
        "breeze" to "旋风人", // 1 个实体，如 breeze
        "breeze_wind_charge_projectile" to "旋风人风弹", // 1 个实体，如 breeze_wind_charge_projectile
        "camel" to "骆驼", // 1 个实体，如 camel
        "camel_husk" to "骆驼尸傀", // 1 个实体，如 camel_husk
        "cartographer" to "制图师", // 1 个实体，如 zombie_villager_v2
        "cat" to "猫", // 1 个实体，如 cat
        "cavespider" to "洞穴蜘蛛", // 1 个实体，如 cave_spider
        "chicken" to "鸡", // 1 个实体，如 chicken
        "cod" to "鳕鱼", // 1 个实体，如 fish
        "copper_golem" to "铜傀儡", // 1 个实体，如 copper_golem
        "cow" to "牛", // 1 个实体，如 cow
        "creaking" to "嘎枝", // 1 个实体，如 creaking
        "creeper" to "苦力怕 / 爬行者", // 1 个实体，如 creeper
        "cushion" to "坐垫", // 1 个实体，如 cushion
        "dolphin" to "海豚", // 1 个实体，如 dolphin
        "donkey" to "驴", // 1 个实体，如 donkey
        "dragon" to "末影龙", // 1 个实体，如 ender_dragon
        "dragon_fireball" to "末影龙火球", // 1 个实体，如 dragon_fireball
        "drowned" to "溺尸", // 1 个实体，如 drowned
        "egg" to "鸡蛋", // 1 个实体，如 egg
        "ender_pearl" to "末影珍珠", // 1 个实体，如 ender_pearl
        "enderman" to "末影人", // 1 个实体，如 enderman
        "endermite" to "末影螨", // 1 个实体，如 endermite
        "evocation_illager" to "唤魔者", // 1 个实体，如 evocation_illager
        "fireball" to "火球", // 1 个实体，如 fireball
        "fireworks_rocket" to "焰火火箭", // 1 个实体，如 fireworks_rocket
        "fish" to "鱼类", // 4 个实体，如 fish/pufferfish/salmon
        "fishing_hook" to "鱼钩", // 1 个实体，如 fishing_hook
        "fox" to "狐狸", // 1 个实体，如 fox
        "frog" to "青蛙", // 1 个实体，如 frog
        "ghast" to "恶魂", // 1 个实体，如 ghast
        "goat" to "山羊", // 1 个实体，如 goat
        "guardian" to "守卫者", // 1 个实体，如 guardian
        "guardian_elder" to "远古守卫者", // 1 个实体，如 elder_guardian
        "happy_ghast" to "善念恶魂", // 1 个实体，如 happy_ghast
        "hoglin" to "疣猪兽", // 1 个实体，如 hoglin
        "hoglin_baby" to "幼年疣猪兽", // 1 个实体，如 hoglin
        "horse" to "马", // 1 个实体，如 horse
        "husk" to "尸壳", // 1 个实体，如 husk
        "illager" to "灾厄村民", // 3 个实体，如 evocation_illager/pillager/vindicator
        "inanimate" to "非生物（方块/物品类）", // 12 个实体，如 boat/chest_boat/chest_minecart
        "irongolem" to "铁傀儡", // 1 个实体，如 iron_golem
        "lightning" to "闪电", // 1 个实体，如 lightning_bolt
        "lingering_potion" to "滞留药水", // 1 个实体，如 lingering_potion
        "llama" to "羊驼", // 2 个实体，如 llama/trader_llama
        "llama_spit" to "羊驼口水", // 1 个实体，如 llama_spit
        "magmacube" to "岩浆怪", // 1 个实体，如 magma_cube
        "minecart" to "矿车", // 5 个实体，如 chest_minecart/command_block_minecart/hopper_minecart
        "mob" to "生物（所有生物）", // 87 个实体，如 allay/armadillo/axolotl
        "monster" to "怪物", // 39 个实体，如 blaze/bogged/breeze
        "mule" to "骡", // 1 个实体，如 mule
        "mushroomcow" to "哞菇", // 1 个实体，如 mooshroom
        "nautilus" to "鹦鹉螺", // 1 个实体，如 nautilus
        "npc" to "NPC", // 1 个实体，如 npc
        "ocelot" to "豹猫", // 1 个实体，如 ocelot
        "pacified" to "被安抚", // 1 个实体，如 bee
        "panda" to "熊猫", // 1 个实体，如 panda
        "panda_aggressive" to "熊猫（攻击性）", // 1 个实体，如 panda
        "parched" to "焦骷髅", // 1 个实体，如 parched
        "parrot_tame" to "已驯服鹦鹉", // 1 个实体，如 parrot
        "phantom" to "幻翼", // 1 个实体，如 phantom
        "pig" to "猪", // 1 个实体，如 pig
        "piglin" to "猪灵", // 2 个实体，如 piglin/piglin_brute
        "piglin_brute" to "猪灵蛮兵", // 1 个实体，如 piglin_brute
        "pillager" to "掠夺者", // 1 个实体，如 pillager
        "player" to "玩家", // 1 个实体，如 player
        "polarbear" to "北极熊", // 1 个实体，如 polar_bear
        "projectile" to "弹射物", // 18 个实体，如 breeze_wind_charge_projectile/dragon_fireball/egg
        "pufferfish" to "河豚", // 1 个实体，如 pufferfish
        "rabbit" to "兔子", // 1 个实体，如 rabbit
        "ravager" to "劫掠兽", // 1 个实体，如 ravager
        "salmon" to "鲑鱼", // 1 个实体，如 salmon
        "sheep" to "羊", // 1 个实体，如 sheep
        "shulker" to "潜影贝", // 1 个实体，如 shulker
        "shulker_bullet" to "潜影贝子弹", // 1 个实体，如 shulker_bullet
        "silverfish" to "蠹虫", // 1 个实体，如 silverfish
        "skeleton" to "骷髅", // 6 个实体，如 bogged/parched/skeleton
        "skeletonhorse" to "骷髅马", // 1 个实体，如 skeleton_horse
        "slime" to "史莱姆", // 1 个实体，如 slime
        "small_fireball" to "小火球", // 1 个实体，如 small_fireball
        "sniffer" to "嗅探兽", // 1 个实体，如 sniffer
        "snowball" to "雪球", // 1 个实体，如 snowball
        "snowgolem" to "雪傀儡", // 1 个实体，如 snow_golem
        "spider" to "蜘蛛", // 1 个实体，如 spider
        "splash_potion" to "喷溅药水", // 1 个实体，如 splash_potion
        "squid" to "鱿鱼", // 2 个实体，如 glow_squid/squid
        "stray" to "流浪者", // 1 个实体，如 stray
        "strider" to "炽足兽", // 1 个实体，如 strider
        "strider_baby" to "幼年炽足兽", // 1 个实体，如 strider
        "sulfur_cube" to "硫方怪", // 1 个实体，如 sulfur_cube
        "tadpole" to "蝌蚪", // 1 个实体，如 tadpole
        "thrown_trident" to "三叉戟", // 1 个实体，如 thrown_trident
        "tnt" to "TNT", // 1 个实体，如 tnt
        "trader_llama" to "行商羊驼", // 1 个实体，如 trader_llama
        "tripodcamera" to "三脚架摄像机", // 1 个实体，如 tripod_camera
        "tropicalfish" to "热带鱼", // 1 个实体，如 tropicalfish
        "turtle" to "海龟", // 1 个实体，如 turtle
        "undead" to "亡灵", // 18 个实体，如 bogged/camel_husk/drowned
        "vex" to "恼鬼", // 1 个实体，如 vex
        "villager" to "村民", // 2 个实体，如 villager/villager_v2
        "vindicator" to "卫道士", // 1 个实体，如 vindicator
        "wandering_trader" to "流浪商人", // 1 个实体，如 wandering_trader
        "warden" to "监守者", // 1 个实体，如 warden
        "wind_charge" to "风弹", // 2 个实体，如 breeze_wind_charge_projectile/wind_charge_projectile
        "wind_charge_projectile" to "风弹（投射物）", // 2 个实体，如 breeze_wind_charge_projectile/wind_charge_projectile
        "witch" to "女巫", // 1 个实体，如 witch
        "wither" to "凋灵", // 2 个实体，如 wither/wither_skeleton
        "wither_skull" to "凋灵之首", // 1 个实体，如 wither_skull
        "wither_skull_dangerous" to "凋灵之首（危险）", // 1 个实体，如 wither_skull_dangerous
        "wolf" to "狼", // 1 个实体，如 wolf
        "xp_bottle" to "附魔之瓶", // 1 个实体，如 xp_bottle
        "zoglin" to "僵尸疣猪兽", // 1 个实体，如 zoglin
        "zoglin_adult" to "成年僵尸疣猪兽", // 1 个实体，如 zoglin
        "zombie" to "僵尸", // 5 个实体，如 drowned/husk/zombie
        "zombie_nautilus" to "僵尸鹦鹉螺", // 1 个实体，如 zombie_nautilus
        "zombie_villager" to "僵尸村民", // 2 个实体，如 zombie_villager/zombie_villager_v2
        "zombiehorse" to "僵尸马", // 1 个实体，如 zombie_horse
    )
}
