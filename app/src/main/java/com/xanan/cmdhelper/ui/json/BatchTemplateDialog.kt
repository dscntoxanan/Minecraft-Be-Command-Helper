package com.xanan.cmdhelper.ui.json

import android.content.Context
import android.text.InputType
import android.view.LayoutInflater
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.xanan.cmdhelper.R
import com.xanan.cmdhelper.data.CommandStore
import com.xanan.cmdhelper.databinding.DialogBatchTemplateBinding
import com.xanan.cmdhelper.databinding.PanelOverlayBinding
import com.xanan.cmdhelper.util.ClipboardUtil

/**
 * 批量生成模板。
 *
 * 对应 CA 菜单里的「创建批量生成模板」。
 *
 * ── 解决的问题 ──────────────────────────────────────────────────────────────
 * 服主/地图作者经常要一次生成几十条几乎相同的命令，例如给 10 个玩家每人发一把
 * 不同附魔等级的剑，或者给 5 个关卡各写一条标题。逐条改数字非常枯燥，也容易漏。
 *
 * ── 用法 ────────────────────────────────────────────────────────────────────
 * 模板里用花括号写占位符：
 * ```
 * give @s diamond_sword {数量} {"enchantments":[{"id":"sharpness","lvl":{等级}}]}
 * ```
 * 下面每个占位符填一行取值（英文逗号分隔）：
 * ```
 * {数量} = 1,2,3
 * {等级} = 1,2,3
 * ```
 * 生成时会做**笛卡尔积**（3 × 3 = 9 条），并自动跳过重复的占位符。
 *
 * 生成结果可以整体复制，也可以一次性全部存进命令库 —— 后者是这个功能最实用的地方：
 * 生成完直接躺在历史里，游戏里一条条点就行。
 */
object BatchTemplateDialog {

    /** 匹配 `{名字}` 形式的占位符。名字允许中文、字母、数字、下划线。 */
    private val PLACEHOLDER_REGEX = Regex("\\{([\\p{IsHan}A-Za-z0-9_]+)}")

    /**
     * 弹出批量生成模板。
     *
     * 和代码栏一样，这里**不用 Dialog**：内容直接挂在面板窗口内部的 `dialogHost`。
     * Service 的悬浮窗里创建独立对话框窗口会抛 BadTokenException 并杀进程，
     * 详见 [com.xanan.cmdhelper.ui.dialog.OverlayDialogs]。
     */
    fun show(hostBinding: PanelOverlayBinding) {
        val anchor = hostBinding.commandInput
        val dialogContext = com.xanan.cmdhelper.ui.dialog.OverlayDialogs.contextFor(anchor)

        val binding = DialogBatchTemplateBinding.inflate(LayoutInflater.from(dialogContext))

        // 默认给一个能直接跑通的示例，比空模板好懂得多。
        binding.templateInput.setText(dialogContext.getString(R.string.batch_sample_template))

        // 模板一变就重算占位符列表，下面长出对应的取值输入框。
        binding.templateInput.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) = Unit
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) = Unit
            override fun afterTextChanged(s: android.text.Editable?) {
                rebuildValueRows(dialogContext, binding)
            }
        })

        rebuildValueRows(dialogContext, binding)

        binding.batchPreviewButton.setOnClickListener {
            val commands = generate(binding)
            if (commands.isEmpty()) {
                Toast.makeText(dialogContext, R.string.batch_empty, Toast.LENGTH_SHORT).show()
            } else {
                showPreview(hostBinding, commands)
            }
        }

        binding.batchCopyButton.setOnClickListener {
            val commands = generate(binding)
            if (commands.isEmpty()) {
                Toast.makeText(dialogContext, R.string.batch_empty, Toast.LENGTH_SHORT).show()
            } else {
                ClipboardUtil.copy(
                    dialogContext,
                    commands.joinToString("\n"),
                    "批量命令 ${commands.size} 条"
                )
                com.xanan.cmdhelper.ui.dialog.OverlayDialogs.dismiss(hostBinding)
            }
        }

        binding.batchSaveButton.setOnClickListener {
            val commands = generate(binding)
            if (commands.isEmpty()) {
                Toast.makeText(dialogContext, R.string.batch_empty, Toast.LENGTH_SHORT).show()
            } else {
                commands.forEach { CommandStore.record(it) }
                Toast.makeText(
                    dialogContext,
                    dialogContext.getString(R.string.batch_saved, commands.size),
                    Toast.LENGTH_SHORT
                ).show()
                com.xanan.cmdhelper.ui.dialog.OverlayDialogs.dismiss(hostBinding)
            }
        }

        binding.batchCloseButton.setOnClickListener {
            com.xanan.cmdhelper.ui.dialog.OverlayDialogs.dismiss(hostBinding)
        }

        com.xanan.cmdhelper.ui.dialog.OverlayDialogs.showCustom(hostBinding, binding.root)
    }

    // =======================================================================
    // 占位符与取值
    // =======================================================================

    /**
     * 按模板里出现的占位符重建取值输入行。
     *
     * 已经填过的值会保留 —— 用户往往先写模板再补取值，如果每次改模板都把
     * 已填的取值清掉，这个功能就没法用了。
     */
    private fun rebuildValueRows(
        context: Context,
        binding: DialogBatchTemplateBinding
    ) {
        val template = binding.templateInput.text?.toString().orEmpty()
        val names = PLACEHOLDER_REGEX.findAll(template).map { it.groupValues[1] }.distinct().toList()

        // 记住旧值
        val existing = HashMap<String, String>()
        for (i in 0 until binding.valuesContainer.childCount) {
            val row = binding.valuesContainer.getChildAt(i) as? LinearLayout ?: continue
            val name = row.tag as? String ?: continue
            val edit = row.getChildAt(1) as? EditText ?: continue
            existing[name] = edit.text?.toString().orEmpty()
        }

        binding.valuesContainer.removeAllViews()
        if (names.isEmpty()) return

        val label = TextView(context).apply {
            text = context.getString(R.string.batch_no_placeholder_hint)
            textSize = 11f
            setTextColor(context.getColorCompat(R.color.text_disabled))
        }
        binding.valuesContainer.addView(label)

        names.forEach { name ->
            val row = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                tag = name
                val top = (6 * context.resources.displayMetrics.density).toInt()
                setPadding(0, top, 0, 0)
            }
            row.addView(TextView(context).apply {
                text = "{$name} ="
                textSize = 13f
                setTextColor(context.getColorCompat(R.color.text_primary))
                minWidth = (72 * context.resources.displayMetrics.density).toInt()
            })
            row.addView(EditText(context).apply {
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                textSize = 13f
                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
                setBackgroundResource(R.drawable.bg_input_field)
                hint = context.getString(R.string.batch_values_hint_row)
                setText(existing[name] ?: "")
            })
            binding.valuesContainer.addView(row)
        }
    }

    /**
     * 按取值生成全部命令（笛卡尔积）。
     *
     * 例：`n` 有 3 个取值、`i` 有 2 个，则生成 6 条。
     * 某个占位符没填取值时，模板里的 `{名字}` 原样保留 —— 这样用户能立刻看出漏了哪个。
     */
    private fun generate(binding: DialogBatchTemplateBinding): List<String> {
        val template = binding.templateInput.text?.toString().orEmpty().trim()
        if (template.isEmpty()) return emptyList()

        val names = PLACEHOLDER_REGEX.findAll(template).map { it.groupValues[1] }.distinct().toList()

        // 收集每个占位符的取值列表
        val valueMap = LinkedHashMap<String, List<String>>()
        for (i in 0 until binding.valuesContainer.childCount) {
            val row = binding.valuesContainer.getChildAt(i) as? LinearLayout ?: continue
            val name = row.tag as? String ?: continue
            val edit = row.getChildAt(1) as? EditText ?: continue
            val values = edit.text?.toString().orEmpty()
                .split(',', '，')
                .map { it.trim() }
                .filter { it.isNotEmpty() }
            valueMap[name] = values
        }

        // 没有任何取值时，把模板本身当一条命令返回更符合直觉。
        if (names.isEmpty() || valueMap.values.all { it.isEmpty() }) {
            return listOf(template)
        }

        // 只对"填了取值"的占位符做展开，没填的保留原样。
        val expandable = names.filter { !valueMap[it].isNullOrEmpty() }
        if (expandable.isEmpty()) return listOf(template)

        var results = listOf(template)
        expandable.forEach { name ->
            val values = valueMap[name] ?: return@forEach
            results = results.flatMap { command ->
                values.map { value ->
                    command.replace("{$name}", value)
                }
            }
        }

        // 去重并限制规模：几百条以上既没法看也没法用。
        return results.distinct().take(MAX_BATCH)
    }

    /** 预览：把生成结果列出来给用户看一眼再决定。 */
    private fun showPreview(hostBinding: PanelOverlayBinding, commands: List<String>) {
        val preview = if (commands.size > PREVIEW_LIMIT) {
            commands.take(PREVIEW_LIMIT).joinToString("\n") +
                "\n…（还有 ${commands.size - PREVIEW_LIMIT} 条）"
        } else {
            commands.joinToString("\n")
        }

        val context = com.xanan.cmdhelper.ui.dialog.OverlayDialogs.contextFor(hostBinding.commandInput)
        com.xanan.cmdhelper.ui.dialog.OverlayDialogs.showMessage(
            binding = hostBinding,
            title = context.getString(R.string.batch_preview_title, commands.size),
            message = preview
        )
    }

    /** 生成上限：再多也不适合用手点，属于脚本的领域了。 */
    private const val MAX_BATCH = 200

    /** 预览里最多列几条，避免对话框无限长。 */
    private const val PREVIEW_LIMIT = 20

    private fun Context.getColorCompat(resId: Int): Int =
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) getColor(resId)
        else resources.getColor(resId)
}
