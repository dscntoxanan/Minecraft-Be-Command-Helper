# 命令库的生成方式

`app/src/main/java/com/xanan/cmdhelper/json/CommandLibrary.kt` 是**生成出来的**，不要手改。

## 为什么要生成

基岩版有 70+ 条命令，每条都要声明参数、类型、候选值、生成逻辑。手写有两个问题：

1. **慢且易错**：本项目在纯手写阶段踩过 `return@CommandSpec` 的语法坑、
   多词命令匹配的边界问题、`give` 组件 JSON 的占位顺序问题。
2. **结构重复**：绝大多数命令就是"把参数按顺序拼起来"，只有少数需要分支。

所以改成：**数据表 + 生成器**。生成出来的 Kotlin 依然是可读的、带中文注释的普通代码。

## 文件

| 文件 | 作用 |
|---|---|
| `commands.data.mjs` | 命令数据表（一条命令 = 一条数据） |
| `gen-commands.mjs` | 生成器：数据 → Kotlin |

## 重新生成

```powershell
node tools\commands\gen-commands.mjs
```

生成器会做自检：关键字重复、参数键重复、缺少 template/variants 都会直接报错。

## 数据表写法

```js
tpl('weather', '切换天气', CAT.world, [
  { k: 'type', n: '类型', t: 'choice', d: 'clear', c: ['clear', 'rain', 'thunder'] },
  { k: 'duration', n: '持续时长', t: 'number', r: false, d: '600' },
], 'weather {type} {{duration}}');
```

参数简写：

| 键 | 含义 |
|---|---|
| `k` / `n` | 参数键 / 界面标签 |
| `t` | 类型：`text`(默认) `long` `number` `bool` `choice` `json` `selector` |
| `r` | `false` = 可选（**默认必填**） |
| `d` | 默认值 |
| `c` | 固定候选（choices） |
| `s` | 词表候选：`items` `blocks` `entities` `effects` `particles` `gameModes` `gamerules` `blockStates` `giveComponents` |
| `h` | 输入提示 |

模板语法（刻意做得很小）：

| 写法 | 含义 |
|---|---|
| `{key}` | 取值；为空则**整条命令不生成**（返回 null） |
| `{key\|默认}` | 取值，为空时用默认值 |
| `{{key}}` | 可选段：为空时整段省略（**自动补一个空格**，不要再手写空格） |
| `{@json:key}` | 取值并做 JSON 字符串转义（`nameTag` 这类必须走 JSON 的参数用） |

需要按条件走不同写法时用 `variant()`，给一组 `variants`：

```js
variant('titleraw', '…', CAT.text, [...], [
  { require: { mode: 'clear' }, text: 'titleraw {target} {position} clear' },
  { require: ['json'], text: 'titleraw {target} {position} {mode|set} {json}' },
  { require: { mode: 'set' }, text: '' },   // 空模板 = 这个分支明确不生成命令
  { text: 'titleraw {target} {position}' }, // 兜底（必须有一条不带 require 的）
]);
```

`require` 两种形式：数组 = 这些键都非空；对象 = 该键等于指定值。

## 生成器踩过的坑（都已在代码注释里写明）

1. **`required` 不能用"有没有默认值"推导。** 默认值只说明界面预填什么；
   写反过一次，导致有默认值的参数全被当成可选，`syntaxTemplate()` 把它们
   全滤掉，生成出 `<JSON 组件:JSON?>` 这种残缺模板。
2. **`{{key}}` 里的 key 是裸 key**（没有花括号），所以可选段的正则要同时接受
   `{key}` 和裸 `key`；只认前者会把它当字面量，生成出 `append("slotId")` 和
   `if ()` 这种废代码。
3. **关键字已经写进 `StringBuilder(keyword)`**，模板里不能再含关键字，
   否则输出 `titlerawtitleraw @a`；关键字后面那个空格也要剥掉，
   统一由 `append(" ")` 负责，否则出现双空格。
4. **模板里不要再手写可选段之间的空格**：`{{...}}` 会自己补。
   写成 `{z}{{yRot}}{{xRot}}`（连在一起），不要 `{z} {{yRot}} {{xRot}}`。

## 检查清单（改完数据后必做）

```powershell
node tools\commands\gen-commands.mjs
cd D:\Agent\Deepseek\CommandAssistant
.\gradlew.bat :app:testDebugUnitTest
```

`CommandBuilderTest` 里的断言是照着**实际命令语法**写的（例如 give 的组件 JSON
前面必须先有数据值占位、setdisplay 对 list 槽位不输出排序），
生成器改动后如果测试红了，先怀疑生成逻辑而不是改测试。
