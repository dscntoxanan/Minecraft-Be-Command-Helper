# 自动补全词表的生成方式

`app/src/main/java/com/xanan/cmdhelper/json/Suggest.kt` 是**生成出来的**，不要手改。

## 数据来源

| 来源 | 用途 |
|---|---|
| `zh_CN.lang`（Mojang 官方资源包 `texts/zh_CN.lang`，本目录已存档） | **权威中文译名 + 权威命令 ID** |
| `sounds.csv` / `particles.csv`（由 `fetch-extra.mjs` 从官方仓库拉取） | 音效与粒子的 ID + 中文名 |
| `WSX/Be原版材质包.zip`（不在本目录，只用来核对） | 确认某个 ID 是否真的有贴图 |
| `gen-suggest.mjs` 里的 `ALIASES` / `EXTRA_BLOCKS` | 补俗名、补官方语言文件里缺失的常用方块 |

**ID 一律以命令为准。** 贴图文件名和命令里的 ID 经常不一致，例如
`apple_golden.png` 的 ID 是 `golden_apple`、`tile.grass.name` 的 ID 是 `grass_block`。
两者冲突时用 `zh_CN.lang` 里的键名。

**中文名只用于显示**，插进命令里的永远是英文 ID（`Suggest.Term.id`）。

## 重新生成

```powershell
# 1)（可选，需要联网）拉官方音效/粒子清单 —— 只有这两份数据变了才需要重跑
node tools\suggest\fetch-extra.mjs

# 2) 生成音效/粒子词表（纯本地）
node tools\suggest\gen-extra-terms.mjs

# 3) 生成 Suggest.kt（纯本地，改了俗称/别名就重跑这一步）
node tools\suggest\gen-suggest.mjs
```

第 1、2 步与第 3 步分开，是因为 1 需要联网、3 要频繁重跑（改个俗名就要跑一次）。

## 音效的中文名是怎么来的（重要）

**官方没有给音效起中文名** —— `zh_CN.lang` 里 `sound.*` 键数量是 **0**。
但官方给**字幕**起了中文（`subtitles.entity.player.levelup` = "升级了"），
而 `sound_definitions.json` 里每个音效正好带一个 `subtitle` 字段。

所以音效中文名的取法是：**音效 ID → subtitle 键 → zh_CN.lang 的官方译文**。
1671 个音效里 1607 个能这样拿到官方译文，剩下 64 个（近年新增、官方还没加字幕的）
用手工说明表 `SOUND_CN` 补 —— **这部分不是官方译文**，报告里如实标注了。

粒子同理：`zh_CN.lang` 里没有 `particle.*` 键，192 个粒子的中文名
全部来自 `gen-extra-terms.mjs` 里的 `PARTICLE_CN` 表，属于**说明性翻译**。

## 为什么用 Node 生成，而不是继续用 PowerShell

第一版生成器是 PowerShell，连踩两个坑：

1. **`$hash[$k] = $hash[$k] + 'x'` 会变成字符串数组**，插值出来是一长串粘连的名字
   （实测出现过 `Term("elytra", "锁子甲锁子裤钻石甲钻石裤鞘翅")`）。
2. **CSV/数组元素是引用语义**，给 `$row.name` 赋值会跨条目串味。

Node 里是值语义、字符串拼接行为确定，所以第二次改成 `.mjs`。**不要为了图省事改回 PowerShell。**

## 命名注意

少数 ID 与 Kotlin/Android 常见类型同名（`Item`、`Char`、`String` 等），
这些名字在 Kotlin 文件里始终在作用域内，会遮蔽预期类型。
生成脚本原本带了改名逻辑，目前的数据里没有命中；若以后命中，变量名要跟着改。
