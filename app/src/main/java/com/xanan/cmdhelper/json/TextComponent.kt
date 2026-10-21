package com.xanan.cmdhelper.json

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/**
 * Minecraft 基岩版「JSON 文本组件」构造器。
 *
 * ── 为什么需要它 ────────────────────────────────────────────────────────────
 * 基岩版里凡是能显示文字的命令（titleraw / tellraw / titleraw 的 actionbar 等），
 * 参数都不是普通字符串，而是一段 JSON。手写这段 JSON 有三个高频坑：
 *
 *   1. **引号嵌套**：命令本身在聊天框里已经有了一层上下文，JSON 的 `"` 必须原样
 *      保留，不能想当然地转义成 `\"`，否则游戏解析出来的是一堆反斜杠。
 *   2. **中文与特殊字符**：`"` `\` 换行在 JSON 字符串里必须转义，中文本身不用
 *      （基岩版 JSON 解析器按 UTF-8 处理），但很多生成器会把中文转成 `\uXXXX`，
 *      结果命令长得没法读。
 *   3. **组件类型选错**：想显示玩家名要用 `selector`，想显示计分板要用 `score`，
 *      用 `text` 手拼字符串是新手最常见的错误。
 *
 * 这个构造器把上面三件事都封装掉：调用方只描述"我要显示什么"，
 * 由它产出可以直接粘进游戏的 JSON。
 *
 * ── 用法 ────────────────────────────────────────────────────────────────────
 * ```
 * val json = TextComponent.rawText()
 *     .text("欢迎 ")
 *     .selector("@s")
 *     .text(" 来到服务器")
 *     .toJsonString()
 * // {"rawtext":[{"text":"欢迎 "},{"selector":"@s"},{"text":" 来到服务器"}]}
 * ```
 */
object TextComponent {

    /**
     * 构造一个 `{"rawtext":[...]}` 容器。
     *
     * @param separator 当多个 `text` 元素相邻时是否自动插入换行分隔；
     *                  默认不插，保持调用方的完全控制。
     */
    fun rawText(separator: String? = null): Builder = Builder(separator)

    /**
     * 单段文本的 JSON（不带 rawtext 外壳）。
     *
     * 适合 `give` 这类需要内联 JSON 参数、又不是文本显示的场景，例如
     * `{"item_name":"宝剑"}`。
     */
    fun plainText(text: String): String =
        JSONObject().put(KEY_TEXT, text).toString()

    /**
     * 给命令用的转义：只处理 JSON 层面必须转义的字符。
     *
     * 注意**不做** `\"` 这种命令层面的转义 —— 基岩版命令里的 JSON 参数就是原样
     * JSON，多余的反斜杠会让游戏把它当成字面量。唯一例外是文本内部的 `"` 和 `\`，
     * 那两个由 [JSONObject] 负责。
     */
    fun escapeForJson(text: String): String = JSONObject.quote(text).removeSurrounding("\"")

    /**
     * 校验一段字符串是否是合法 JSON。
     *
     * @return 合法时返回 null，否则返回给用户看的中文错误说明。
     */
    fun validate(json: String): String? {
        val trimmed = json.trim()
        if (trimmed.isEmpty()) return "JSON 不能为空"
        if (!trimmed.startsWith("{") && !trimmed.startsWith("[")) {
            return "JSON 必须以 { 或 [ 开头"
        }
        return try {
            if (trimmed.startsWith("[")) JSONArray(trimmed) else JSONObject(trimmed)
            null
        } catch (e: JSONException) {
            "JSON 格式错误：" + (e.message ?: "无法解析")
        }
    }

    /** 美化 JSON：2 空格缩进。解析失败时原样返回，避免编辑框内容被清空。 */
    fun pretty(json: String): String = try {
        val trimmed = json.trim()
        if (trimmed.startsWith("[")) JSONArray(trimmed).toString(2) else JSONObject(trimmed).toString(2)
    } catch (e: JSONException) {
        json
    }

    /** 压缩 JSON：去掉所有多余空白，得到适合粘进游戏的单行形式。 */
    fun compact(json: String): String = try {
        val trimmed = json.trim()
        if (trimmed.startsWith("[")) JSONArray(trimmed).toString() else JSONObject(trimmed).toString()
    } catch (e: JSONException) {
        json.trim()
    }

    private const val KEY_TEXT = "text"

    /**
     * rawtext 数组的流式构造器。
     *
     * 所有 `add*` 方法都返回 this，可以链式书写；构建结果时再统一转成字符串，
     * 这样调用方不需要关心中间态。
     */
    class Builder internal constructor(private val separator: String?) {

        private val parts = JSONArray()
        private var lastWasText = false

        /** 追加一段纯文本。 */
        fun text(value: String): Builder {
            maybeSeparate(isText = true)
            parts.put(JSONObject().put("text", value))
            return this
        }

        /**
         * 追加一个目标选择器，游戏运行时会替换成实际玩家名。
         *
         * @param selector 如 `@s`、`@a`、`@p`
         */
        fun selector(selector: String): Builder {
            parts.put(JSONObject().put("selector", selector))
            lastWasText = false
            return this
        }

        /**
         * 追加一个计分板分数显示。
         *
         * @param name      玩家名或选择器
         * @param objective 计分板目标名
         */
        fun score(name: String, objective: String): Builder {
            parts.put(
                JSONObject().put(
                    "score",
                    JSONObject().put("name", name).put("objective", objective)
                )
            )
            lastWasText = false
            return this
        }

        /**
         * 追加一个本地化键。
         *
         * 例如 `translate("item.diamond_sword")` 会根据客户端语言显示"钻石剑"。
         * 这是做多语言命令时唯一正确的做法。
         *
         * @param with 可选的替换参数（`{"with":[...]}`），用于带占位符的键
         */
        fun translate(key: String, with: List<String> = emptyList()): Builder {
            val obj = JSONObject().put("translate", key)
            if (with.isNotEmpty()) {
                val arr = JSONArray()
                with.forEach { arr.put(it) }
                obj.put("with", arr)
            }
            parts.put(obj)
            lastWasText = false
            return this
        }

        /** 追加一个换行。 */
        fun newline(): Builder = text("\n")

        /** 当前已构造的段数，便于界面判断"还没填内容"。 */
        fun size(): Int = parts.length()

        /** 产出 `{"rawtext":[...]}` 字符串。 */
        fun toJsonString(): String = JSONObject().put("rawtext", parts).toString()

        /** 产出格式化后的 JSON，供"样式代码栏"展示。 */
        fun toPrettyJsonString(): String =
            JSONObject().put("rawtext", parts).toString(2)

        /** 相邻两个 text 之间按需插入分隔符。 */
        private fun maybeSeparate(isText: Boolean) {
            if (separator != null && lastWasText && isText) {
                parts.put(JSONObject().put("text", separator))
            }
            lastWasText = isText
        }
    }
}
