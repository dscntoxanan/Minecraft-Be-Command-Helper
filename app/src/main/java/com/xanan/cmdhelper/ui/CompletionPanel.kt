package com.xanan.cmdhelper.ui

import android.content.Context
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import com.xanan.cmdhelper.R
import com.xanan.cmdhelper.json.CommandCompletion
import com.xanan.cmdhelper.json.CommandLibrary

/**
 * 输入补全面板。
 *
 * ── 交互 ────────────────────────────────────────────────────────────────────
 * 玩家在输入框里打字时，这个面板实时显示"下一步能填什么"，**点一下就能填上**：
 *
 * ```
 * /                 → 全部命令（give / titleraw / execute …）
 * /g                → give、gamemode、gamerule
 * /give             → 点一下直接补成 "give "（自己补空格）
 * /give             → 已识别命令 → 下一步该填选择器 → @s @a @p @r @e …
 * /give @s          → 下一步该填物品 → diamond_sword、stone、netherite_ingot …
 * /titleraw @a title set
 *                   → 下一步该填 JSON 组件 → 可直接用的 JSON 骨架
 * ```
 *
 * 判断"下一步该填什么"的逻辑全部在 [CommandCompletion]（纯函数、有单测）；
 * 这里只负责把它画出来、以及处理点击。
 *
 * ── 为什么不用系统输入法的补全 ──────────────────────────────────────────────
 * 面板是一个悬浮窗，输入框背后是**系统输入法**。让输入法做命令补全需要写一个
 * 完整的 IME，代价极高且用户还得切换输入法；而补全面板就在光标上方，点一下就行，
 * 反而比输入法的候选栏更顺手（不必先用输入法打字）。
 */
object CompletionPanel {

    /** 供测试与调用方观察是否有待落位的光标（状态存在引擎里，见 CommandCompletion）。 */
    fun hasPendingCaret(): Boolean = CommandCompletion.hasPendingCaret()

    /**
     * 渲染补全面板。
     *
     * @param container 补全面板的宿主容器
     * @param input     当前输入框控件（点击候选后直接改它的文本与光标）
     * @return true 表示有内容可显示（调用方据此隐藏历史列表）
     */
    fun render(container: LinearLayout, input: EditText): Boolean {
        container.removeAllViews()

        val text = input.text?.toString().orEmpty()
        // 光标位置由引擎统一决定：它会优先采用"刚点击候选项时期望的落点"，
        // 否则在"文本非空但光标为 0"（setText 之后那个瞬间）时按末尾处理。
        // 这段逻辑放在引擎里是为了能用单元测试覆盖 —— 它最容易出错。
        val cursor = CommandCompletion.resolveCaret(text, input.selectionStart)

        // 什么都没输入时不显示补全面板，让历史列表照常占位。
        if (text.isBlank()) return false

        val result = CommandCompletion.complete(text, cursor)
        val context = container.context

        // ── 第一行：当前状态（识别到哪条命令 / 该填什么）
        container.addView(statusRow(context, text, cursor, result))

        // ── 第二行：语法进度（把整条命令模板画出来，高亮"当前这一段"）
        result.matchedCommand?.let { spec ->
            container.addView(syntaxProgress(context, spec, result.currentParamIndex))
        }

        // ── 候选列表
        if (result.items.isEmpty()) {
            // 没有候选可给时显示引导文案（例如整数参数 → "请输入整数"），
            // 而不是一句干巴巴的"没有更多候选"。
            val message = result.placeholder.ifEmpty {
                context.getString(R.string.completion_none)
            }
            container.addView(hint(context, message))
            return true
        }

        container.addView(suggestionList(context, input, result))

        // 参数说明：让玩家知道当前这个位置到底要什么
        result.matchedCommand?.let { spec ->
            spec.params.getOrNull(result.currentParamIndex)?.let { param ->
                val doc = buildString {
                    append(if (param.required) "必填" else "可选")
                    append(" · ")
                    append(typeName(param))
                    if (param.hint.isNotBlank()) {
                        append("　")
                        append(param.hint)
                    }
                }
                container.addView(subtle(context, doc))
            }
        }

        return true
    }

    // =======================================================================
    // 视图构造
    // =======================================================================

    /** 状态行：左边一句提示，右边显示已识别到的命令。 */
    private fun statusRow(
        context: Context,
        text: String,
        cursor: Int,
        result: CommandCompletion.Result
    ): View = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(context, 14), dp(context, 10), dp(context, 14), dp(context, 4))

        addView(TextView(context).apply {
            // 已识别到命令时显示命令名（带 `/`，与聊天框里的写法一致）；
            // 还在选命令时显示"命令"占位，免得出现一个孤零零的斜杠。
            // 这里显式用 setText：用属性赋值时 Kotlin 会因为 TextView 同时有
            // getText()/setText() 与 Editable 类型歧义而推导失败。
            setText(result.matchedCommand?.let { "/" + it.keyword } ?: "命令")
            textSize = 14f
            setTextColor(color(context, R.color.text_primary))
            typeface = android.graphics.Typeface.MONOSPACE
        })
        addView(TextView(context).apply {
            setText(
                if (result.matchedCommand != null && result.prompt.isNotEmpty()) {
                    "　" + result.prompt
                } else {
                    ""
                }
            )
            textSize = 12f
            setTextColor(color(context, R.color.text_secondary))
            setPadding(dp(context, 6), 0, 0, 0)
        })
    }

    /**
     * 语法进度行：把命令模板按"已填 / 正在填 / 待填"三段着色。
     *
     * 比单纯列一行灰色模板有用得多 —— 玩家一眼就知道光标卡在哪一段。
     */
    private fun syntaxProgress(
        context: Context,
        spec: com.xanan.cmdhelper.json.CommandSpec,
        currentIndex: Int
    ): View {
        // 拼出「已填部分」与「待填部分」两段文本
        val filled = StringBuilder(spec.keyword)
        spec.params.take(currentIndex.coerceAtLeast(0)).forEach { param ->
            filled.append(' ').append(param.defaultValue.ifEmpty { "…" })
        }
        val remaining = StringBuilder()
        spec.params.drop(currentIndex.coerceAtLeast(0)).forEach { param ->
            remaining.append(' ').append('<').append(param.label).append('>')
        }

        val spannable = SpannableString(filled.toString() + remaining.toString())
        spannable.setSpan(
            ForegroundColorSpan(color(context, R.color.brand_teal)),
            0, filled.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        if (remaining.isNotEmpty()) {
            spannable.setSpan(
                ForegroundColorSpan(color(context, R.color.text_disabled)),
                filled.length, spannable.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }

        return TextView(context).apply {
            text = spannable
            textSize = 12f
            typeface = android.graphics.Typeface.MONOSPACE
            setBackgroundResource(R.drawable.bg_code_area)
            val pad = dp(context, 8)
            setPadding(pad, pad, pad, pad)
            val margin = dp(context, 14)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(margin, dp(context, 4), margin, dp(context, 2)) }
        }
    }

    /**
     * 候选列表：**纵向**排列，一行一个候选。
     *
     * 一开始做的是横向滚动的"胶囊"，实际用起来很难受：候选项一多就得左右划，
     * 手指在悬浮面板上横向滑动还容易误触到面板拖动/系统手势。
     * 改成纵向列表后，一行一个、从上往下点，和玩家熟悉的列表操作一致；
     * 外层套 ScrollView，候选再多也只是往下滚。
     */
    private fun suggestionList(
        context: Context,
        input: EditText,
        result: CommandCompletion.Result
    ): View {
        val column = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            val pad = dp(context, 14)
            setPadding(pad, dp(context, 2), pad, dp(context, 8))
        }

        result.items.forEach { item ->
            column.addView(suggestionRow(context, item) {
                // 点击 → 替换光标处那一段 → 重新渲染。
                //
                // ⚠️ 顺序很讲究，两件事都要做：
                //  1. **先记下期望落点**再 setText —— 因为 `setText()` 会立刻把
                //     selection 重置为 0 并触发 TextWatcher，那一刻读不到真光标，
                //     补全就会按"光标在行首"重算（JSON 场景下表现为候选换了一层）。
                //  2. **再 post 设一次 selection** —— 让玩家看到的光标也落在正确位置。
                val (merged, newCursor) =
                    CommandCompletion.apply(
                        input.text?.toString().orEmpty(),
                        result,
                        item,
                        // JSON 骨架不能补空格（会把 JSON 拼坏）；其余候选补一个空格。
                        appendSpace = !item.noSpace,
                    )
                input.setText(merged)
                input.post { input.setSelection(newCursor.coerceIn(0, merged.length)) }
            })
        }

        // 候选太多时列表会被截断（音效有 1671 条，全渲染会卡死面板），
        // 用一行灰字说明还剩多少，避免玩家以为"词表就这么点"。
        if (result.truncatedNote.isNotBlank()) {
            column.addView(noteRow(context, result.truncatedNote))
        }

        return android.widget.ScrollView(context).apply {
            isVerticalScrollBarEnabled = true
            addView(
                column,
                android.widget.FrameLayout.LayoutParams(
                    android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                    android.widget.FrameLayout.LayoutParams.WRAP_CONTENT
                )
            )
        }
    }

    /**
     * 单个候选行。
     *
     * 版式：**左边中文名（主）、右边英文 ID（次）**。
     * 中文名放主位是因为玩家按"我想给钻石剑"来找，而不是按"diamond_sword"来找；
     * 英文 ID 保留是为了让玩家顺便记住命令里的写法。
     * 两行都为空时（例如纯数字候选）只显示值本身。
     */
    /** 一行说明文字（例如"还有 1500 条，继续输入可缩小范围"）。 */
    private fun noteRow(context: Context, text: String): View = TextView(context).apply {
        this.text = text
        textSize = 12f
        setTextColor(color(context, R.color.text_secondary))
        val padH = dp(context, 10)
        setPadding(padH, dp(context, 6), padH, dp(context, 6))
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
    }

    private fun suggestionRow(
        context: Context,
        item: CommandCompletion.Item,
        onClick: () -> Unit
    ): View = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setBackgroundResource(R.drawable.bg_list_item)
        val padH = dp(context, 10)
        setPadding(padH, dp(context, 8), padH, dp(context, 8))
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )

        isClickable = true
        isFocusable = true
        setOnClickListener { onClick() }

        // 主文本：中文名（没有中文名时就用值本身）
        addView(TextView(context).apply {
            text = item.label
            textSize = 15f
            setTextColor(color(context, R.color.text_primary))
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            layoutParams = LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f
            )
        })

        // 次文本：英文 ID（与 label 相同时不重复显示）
        if (item.detail.isNotBlank() && item.detail != item.label) {
            addView(TextView(context).apply {
                text = item.detail
                textSize = 11f
                typeface = android.graphics.Typeface.MONOSPACE
                setTextColor(color(context, R.color.text_secondary))
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
                maxWidth = dp(context, 200)
                setPadding(dp(context, 8), 0, 0, 0)
            })
        }
    }

    private fun hint(context: Context, message: String): View =
        TextView(context).apply {
            text = message
            textSize = 12f
            setTextColor(color(context, R.color.text_secondary))
            setPadding(dp(context, 14), dp(context, 8), dp(context, 14), dp(context, 8))
        }

    private fun subtle(context: Context, message: String): View =
        TextView(context).apply {
            text = message
            textSize = 11f
            setTextColor(color(context, R.color.text_secondary))
            setPadding(dp(context, 14), 0, dp(context, 14), dp(context, 8))
        }

    private fun typeName(param: com.xanan.cmdhelper.json.CommandParam): String =
        when (param.type) {
            com.xanan.cmdhelper.json.ParamType.TEXT -> "文本"
            com.xanan.cmdhelper.json.ParamType.LONG_TEXT -> "文本"
            com.xanan.cmdhelper.json.ParamType.NUMBER -> "整数"
            com.xanan.cmdhelper.json.ParamType.BOOLEAN -> "true / false"
            com.xanan.cmdhelper.json.ParamType.CHOICE -> param.choices.joinToString(" / ")
            com.xanan.cmdhelper.json.ParamType.JSON -> "JSON 组件"
            com.xanan.cmdhelper.json.ParamType.SELECTOR -> "目标选择器"
        }

    private fun dp(context: Context, value: Int): Int =
        (value * context.resources.displayMetrics.density).toInt()

    private fun color(context: Context, resId: Int): Int =
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) context.getColor(resId)
        else context.resources.getColor(resId)
}
