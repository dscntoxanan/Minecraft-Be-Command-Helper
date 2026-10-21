package com.xanan.cmdhelper.ui.dialog

import android.content.Context
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.xanan.cmdhelper.R
import com.xanan.cmdhelper.databinding.PanelOverlayBinding
import com.xanan.cmdhelper.json.RecipeLibrary
import com.xanan.cmdhelper.util.ClipboardUtil

/**
 * 玩家指令库的浏览界面。
 *
 * ── 交互路径 ────────────────────────────────────────────────────────────────
 * ```
 * CA 菜单 →「指令库」
 *   → 选分组（交互与菜单 / 玩家检测 / 物品与计分 / 世界与方块）
 *     → 选配方
 *       → 详情（说明 + 新版语法要点 + 一条条命令）
 *         → 点某条命令 → 插进输入框
 * ```
 *
 * ── 为什么要分三级而不是平铺 ────────────────────────────────────────────────
 * 一条配方往往有 3~10 行命令。平铺的话玩家要先在几十行里找，再自己判断哪几行是一组。
 * 分组 → 配方 → 命令 三级刚好对应"我想做什么 → 用哪套机制 → 复制哪一行"。
 *
 * 弹层画在面板窗口内部，理由见 [OverlayDialogs] 的说明（用 Dialog 会把进程杀掉）。
 */
object RecipeLibraryDialog {

    /** 入口：列出全部分组。 */
    fun show(binding: PanelOverlayBinding) {
        val context = OverlayDialogs.contextFor(binding.commandInput)
        val groups = RecipeLibrary.categories

        OverlayDialogs.showList(
            binding = binding,
            title = context.getString(R.string.recipe_title),
            items = groups.map { cat ->
                val count = RecipeLibrary.byCategory(cat).size
                "$cat（$count）"
            }.toTypedArray(),
            onItemClick = { which -> showRecipes(binding, groups[which]) },
            onCancel = { show(binding) },
        )
    }

    /** 二级：某个分组下的配方。 */
    private fun showRecipes(binding: PanelOverlayBinding, category: String) {
        val recipes = RecipeLibrary.byCategory(category)

        OverlayDialogs.showList(
            binding = binding,
            title = category,
            items = recipes.map { "${it.title}　·　${it.commands.size} 条" }.toTypedArray(),
            onItemClick = { which -> showDetail(binding, recipes[which]) },
            onCancel = { show(binding) },
        )
    }

    /** 三级：配方详情 —— 说明、语法要点、每一条命令。 */
    private fun showDetail(binding: PanelOverlayBinding, recipe: RecipeLibrary.Recipe) {
        val context = OverlayDialogs.contextFor(binding.commandInput)
        val commands = recipe.build()

        val card = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundResource(R.drawable.bg_dialog_card)
            val pad = dp(context, 14)
            setPadding(pad, pad, pad, pad)
        }

        card.addView(titleView(context, recipe.title))
        card.addView(bodyView(context, recipe.description))

        if (recipe.notes.isNotBlank()) {
            card.addView(sectionLabel(context, "新版语法要点"))
            card.addView(bodyView(context, recipe.notes))
        }

        if (recipe.placeholders.isNotEmpty()) {
            card.addView(sectionLabel(context, "可替换的参数（默认值）"))
            card.addView(
                bodyView(
                    context,
                    recipe.placeholders.joinToString("\n") { "· ${it.label}：${it.defaultValue}" },
                )
            )
        }

        card.addView(sectionLabel(context, "命令（点一下插进输入框）"))
        // 命令是横向很长的单行，用横向滚动的等宽文本，方便核对又不撑破弹层。
        commands.forEach { cmd ->
            card.addView(commandRow(context, binding, cmd))
        }

        card.addView(
            actionRow(
                context = context,
                copyLabel = "全部复制",
                insertLabel = "全部插入",
                onCopy = {
                    ClipboardUtil.copy(context, commands.joinToString("\n"))
                    Toast.makeText(context, R.string.recipe_copied, Toast.LENGTH_SHORT).show()
                },
                onInsert = {
                    // 多行命令在输入框里用换行分隔，方便一次粘进函数文件
                    OverlayDialogs.dismiss(binding)
                    binding.commandInput.setText(commands.joinToString("\n"))
                    binding.commandInput.setSelection(binding.commandInput.text?.length ?: 0)
                },
            )
        )

        OverlayDialogs.showCustom(binding, card) { showRecipes(binding, recipe.category) }
    }

    // =======================================================================
    // 小部件
    // =======================================================================

    private fun titleView(context: Context, text: String): TextView = TextView(context).apply {
        this.text = text
        textSize = 17f
        setTextColor(context.getColorCompat(R.color.text_primary))
        setTypeface(typeface, android.graphics.Typeface.BOLD)
        setPadding(0, 0, 0, dp(context, 6))
    }

    private fun sectionLabel(context: Context, text: String): TextView = TextView(context).apply {
        this.text = text
        textSize = 12f
        setTextColor(context.getColorCompat(R.color.brand_teal))
        setPadding(0, dp(context, 10), 0, dp(context, 4))
    }

    private fun bodyView(context: Context, text: String): TextView = TextView(context).apply {
        this.text = text
        textSize = 13f
        setTextColor(context.getColorCompat(R.color.text_secondary))
        setLineSpacing(0f, 1.15f)
    }

    /** 一行命令：等宽、横向可滚、点一下插入。 */
    private fun commandRow(context: Context, binding: PanelOverlayBinding, command: String): LinearLayout {
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundResource(R.drawable.bg_code_area)
            val pad = dp(context, 8)
            setPadding(pad, pad, pad, pad)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { topMargin = dp(context, 6) }
            isClickable = true
            setOnClickListener {
                OverlayDialogs.dismiss(binding)
                val input = binding.commandInput
                val start = input.selectionStart.coerceAtLeast(0)
                val text = input.text?.toString().orEmpty()
                // 在光标处插入；光标落在插入内容的末尾
                val merged = text.substring(0, start) + command + text.substring(start)
                input.setText(merged)
                input.setSelection((start + command.length).coerceAtMost(merged.length))
            }
        }

        row.addView(
            android.widget.HorizontalScrollView(context).apply {
                isHorizontalScrollBarEnabled = false
                addView(
                    TextView(context).apply {
                        text = command
                        textSize = 11f
                        typeface = android.graphics.Typeface.MONOSPACE
                        setTextColor(context.getColorCompat(R.color.text_primary))
                        setTextIsSelectable(true)
                    }
                )
            }
        )
        return row
    }

    /** 底部两个按钮：全部复制 / 全部插入。 */
    private fun actionRow(
        context: Context,
        copyLabel: String,
        insertLabel: String,
        onCopy: () -> Unit,
        onInsert: () -> Unit,
    ): LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.END
        setPadding(0, dp(context, 14), 0, 0)

        addView(button(context, copyLabel, primary = false, onClick = onCopy))
        addView(button(context, insertLabel, primary = true, onClick = onInsert))
    }

    private fun button(context: Context, label: String, primary: Boolean, onClick: () -> Unit): TextView =
        TextView(context).apply {
            text = label
            textSize = 14f
            setTextColor(
                if (primary) android.graphics.Color.WHITE else context.getColorCompat(R.color.brand_teal)
            )
            setBackgroundResource(if (primary) R.drawable.bg_primary_button else R.drawable.bg_chip)
            val padH = dp(context, 14)
            val padV = dp(context, 8)
            setPadding(padH, padV, padH, padV)
            isClickable = true
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { leftMargin = dp(context, 8) }
            setOnClickListener { onClick() }
        }

    private fun dp(context: Context, value: Int): Int =
        (value * context.resources.displayMetrics.density).toInt()

    /**
     * 取颜色。
     *
     * 本工程里每个对话框都自带一份这个扩展（`private`），而不是抽到公共文件 ——
     * [OverlayDialogs]、`JsonEditorDialog`、`BatchTemplateDialog` 各有一份。
     * 这里保持一致，不去动它们的可见性（改动面越小越好）。
     */
    private fun Context.getColorCompat(resId: Int): Int =
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            resources.getColor(resId, theme)
        } else {
            @Suppress("DEPRECATION")
            resources.getColor(resId)
        }
}
