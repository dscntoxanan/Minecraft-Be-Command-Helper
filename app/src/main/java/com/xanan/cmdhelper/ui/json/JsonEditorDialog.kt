package com.xanan.cmdhelper.ui.json

import android.content.Context
import android.text.InputType
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import com.xanan.cmdhelper.R
import com.xanan.cmdhelper.data.CommandStore
import com.xanan.cmdhelper.databinding.PanelOverlayBinding
import com.xanan.cmdhelper.json.CommandLibrary
import com.xanan.cmdhelper.json.CommandParam
import com.xanan.cmdhelper.json.CommandSpec
import com.xanan.cmdhelper.json.ComponentLibrary
import com.xanan.cmdhelper.json.ParamType
import com.xanan.cmdhelper.json.TextComponent
import com.xanan.cmdhelper.util.ClipboardUtil

/**
 * JSON 样式代码栏。
 *
 * 对应 CA 菜单里的「显示样式代码栏」，是整个 App 里**最有技术含量**的一块：
 * 它把「基岩版命令的 JSON 参数」从"要背语法"变成"填表单"。
 *
 * 交互流程：
 *   选命令 → 按参数声明自动生成控件 → 实时拼出预览 → 复制 / 插入
 *
 * 关键设计：**预览是实时计算的**，每次改一个字符都重新调用 [CommandSpec.build]。
 * 这样用户能在按"插入"之前就看到最终命令长什么样，写错了当场发现。
 *
 * @see com.xanan.cmdhelper.json.CommandLibrary 全部命令与参数声明
 * @see TextComponent JSON 组件的构造与校验
 */
object JsonEditorDialog {

    /** `give` / `replaceitem` 的组件参数键。这两个命令要额外给"组件速插"。 */
    private const val COMPONENTS_PARAM_KEY = "components"

    /** 当前对话框里所有参数的输入控件，键是参数 key。 */
    private val views = LinkedHashMap<String, View>()

    /** 当前选中的命令规格。 */
    private var currentSpec: CommandSpec? = null

    /**
     * 弹出代码栏。
     *
     * 注意这里**不创建任何 Dialog**：内容直接挂在面板窗口内部的 `dialogHost` 里。
     * 面板跑在 Service 的悬浮窗中，`Dialog.show()` 会因为拿不到窗口令牌抛
     * BadTokenException 把进程杀掉（踩过一次，详见
     * [com.xanan.cmdhelper.ui.dialog.OverlayDialogs] 的说明）。
     *
     * @param hostBinding 宿主面板的绑定对象 —— 生成的结果直接插到它的输入框里，
     *                    代码栏本身也画在它里面，两者共享同一个 Context 与主题。
     */
    fun show(hostBinding: PanelOverlayBinding) {
        val anchor = hostBinding.commandInput
        val context = com.xanan.cmdhelper.ui.dialog.OverlayDialogs.contextFor(anchor)

        val binding = com.xanan.cmdhelper.databinding.DialogJsonEditorBinding.inflate(
            android.view.LayoutInflater.from(context)
        )

        views.clear()

        setupCommandSpinner(context, binding) { spec ->
            currentSpec = spec
            buildParamViews(context, binding, spec)
            refreshPreview(context, binding)
        }

        binding.copyButton.setOnClickListener {
            val command = buildCommand()
            if (command == null) {
                Toast.makeText(context, R.string.json_empty_preview, Toast.LENGTH_SHORT).show()
            } else {
                ClipboardUtil.copy(context, command)
                com.xanan.cmdhelper.ui.dialog.OverlayDialogs.dismiss(hostBinding)
            }
        }

        binding.insertButton.setOnClickListener {
            val command = buildCommand()
            if (command == null) {
                Toast.makeText(context, R.string.json_empty_preview, Toast.LENGTH_SHORT).show()
            } else {
                hostBinding.commandInput.setText(command)
                hostBinding.commandInput.setSelection(command.length)
                com.xanan.cmdhelper.ui.dialog.OverlayDialogs.dismiss(hostBinding)
            }
        }

        binding.formatJsonButton.setOnClickListener {
            formatJsonFields(context, binding)
        }

        binding.closeButton.setOnClickListener {
            com.xanan.cmdhelper.ui.dialog.OverlayDialogs.dismiss(hostBinding)
        }

        com.xanan.cmdhelper.ui.dialog.OverlayDialogs.showCustom(hostBinding, binding.root)
    }

    // =======================================================================
    // 命令选择
    // =======================================================================

    private fun setupCommandSpinner(
        context: Context,
        binding: com.xanan.cmdhelper.databinding.DialogJsonEditorBinding,
        onSelected: (CommandSpec) -> Unit
    ) {
        // 把命令按「分组 · 关键字」的形式列出来，分组顺序沿用 CommandLibrary 的定义。
        val specs = CommandLibrary.all
        val labels = specs.map { "${it.category} · ${it.keyword}" }

        val adapter = ArrayAdapter(context, android.R.layout.simple_spinner_item, labels)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.commandSpinner.adapter = adapter
        binding.commandSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                onSelected(specs[position])
            }

            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }
    }

    // =======================================================================
    // 参数控件生成
    // =======================================================================

    /**
     * 按参数声明生成输入控件。
     *
     * 每种 [ParamType] 对应一种控件，这是"参数类型"这个枚举存在的意义：
     * 新增命令时只声明参数类型，界面会自动长出正确的输入框。
     */
    private fun buildParamViews(
        context: Context,
        binding: com.xanan.cmdhelper.databinding.DialogJsonEditorBinding,
        spec: CommandSpec
    ) {
        binding.paramContainer.removeAllViews()
        views.clear()

        spec.params.forEach { param ->
            binding.paramContainer.addView(createLabel(context, param))
            val view = createInput(context, binding, param)

            // 物品组件参数（give / replaceitem）额外给一排"组件速插"。
            // 组件 ID 要背、数值格式要记，是这两条命令里最难的部分；
            // 点一下就插一份可用的模板，光标落在占位符里直接替换即可。
            if (param.key == COMPONENTS_PARAM_KEY) {
                val jsonInput = view as? EditText
                if (jsonInput != null) {
                    binding.paramContainer.addView(createComponentShortcuts(context, binding, jsonInput))
                }
            }

            views[param.key] = view
            binding.paramContainer.addView(view)
        }

        // 目标选择器给一排快捷按钮：这几个值占了实际使用的九成以上。
        if (spec.params.any { it.type == ParamType.SELECTOR }) {
            binding.paramContainer.addView(createSelectorShortcuts(context, binding))
        }
    }

    private fun createLabel(context: Context, param: CommandParam): TextView =
        TextView(context).apply {
            text = if (param.required) "${param.label} *" else param.label
            textSize = 12f
            setTextColor(context.getColorCompat(R.color.text_secondary))
            val top = (8 * context.resources.displayMetrics.density).toInt()
            setPadding(0, top, 0, 0)
        }

    private fun createInput(
        context: Context,
        binding: com.xanan.cmdhelper.databinding.DialogJsonEditorBinding,
        param: CommandParam
    ): View {
        // 所有控件在内容变化时都触发一次预览重算，保证预览永远是最新的。
        fun notifyChange() = refreshPreview(context, binding)

        return when (param.type) {
            ParamType.CHOICE -> Spinner(context).apply {
                val choices = param.choices.ifEmpty { listOf(param.defaultValue) }
                adapter = ArrayAdapter(context, android.R.layout.simple_spinner_item, choices).apply {
                    setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                }
                val preset = choices.indexOf(param.defaultValue).coerceAtLeast(0)
                setSelection(preset)
                onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                    override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) =
                        notifyChange()

                    override fun onNothingSelected(p: AdapterView<*>?) = Unit
                }
                layoutParams = rowParams(context, 44)
            }

            ParamType.BOOLEAN -> CheckBox(context).apply {
                text = context.getString(R.string.json_boolean_true)
                isChecked = param.defaultValue == "true"
                setOnCheckedChangeListener { _, _ -> notifyChange() }
                layoutParams = rowParams(context, 40)
            }

            ParamType.JSON -> EditText(context).apply {
                hint = param.hint.ifEmpty { "{\"rawtext\":[{\"text\":\"你好\"}]}" }
                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or
                    InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
                gravity = android.view.Gravity.TOP or android.view.Gravity.START
                typeface = android.graphics.Typeface.MONOSPACE
                textSize = 12f
                minLines = 3
                maxLines = 6
                setBackgroundResource(R.drawable.bg_code_area)
                setPadding(dp(context, 8), dp(context, 8), dp(context, 8), dp(context, 8))
                setText(param.defaultValue)
                addTextChangedListener(SimpleWatcher { notifyChange() })
                layoutParams = rowParams(context, LinearLayout.LayoutParams.WRAP_CONTENT)
            }

            else -> EditText(context).apply {
                hint = param.hint
                inputType = when (param.type) {
                    ParamType.NUMBER -> InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_SIGNED
                    else -> InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
                }
                textSize = 13f
                setBackgroundResource(R.drawable.bg_input_field)
                setPadding(dp(context, 8), dp(context, 8), dp(context, 8), dp(context, 8))
                setSingleLine(param.type != ParamType.LONG_TEXT)
                setText(param.defaultValue)
                addTextChangedListener(SimpleWatcher { notifyChange() })
                layoutParams = rowParams(context, 44)
            }
        }
    }

    /**
     * 物品组件的"速插"按钮排。
     *
     * 只放最常用的几个 —— 完整列表（22 个）在输入框里打 `{` 就有补全，
     * 这里铺满一屏反而更难找。
     *
     * 点击行为：把组件模板追加进 JSON 输入框，并**自动补逗号**、
     * 把光标放进模板的 `<占位>` 里，玩家直接打字就行。
     */
    private fun createComponentShortcuts(
        context: Context,
        binding: com.xanan.cmdhelper.databinding.DialogJsonEditorBinding,
        input: EditText
    ): LinearLayout {
        val quick = listOf("item_name", "lore", "enchantments", "minecraft:can_place_on", "minecraft:can_destroy", "minecraft:item_lock", "minecraft:keep_on_death")

        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(context, 6), 0, 0)

            // 横向滚动：小屏上一排放不下，能划就行。
            val row = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
            addView(
                android.widget.HorizontalScrollView(context).apply {
                    isHorizontalScrollBarEnabled = false
                    addView(row)
                }
            )

            quick.forEach { id ->
                val component = ComponentLibrary.find(id) ?: return@forEach
                row.addView(TextView(context).apply {
                    text = component.name.substringBefore(" / ")
                    textSize = 12f
                    setTextColor(context.getColorCompat(R.color.brand_teal))
                    setBackgroundResource(R.drawable.bg_chip)
                    setPadding(dp(context, 10), dp(context, 5), dp(context, 10), dp(context, 5))
                    isClickable = true
                    setOnClickListener {
                        val current = input.text?.toString().orEmpty().trim()
                        // 已经写过组件就要补逗号；只有 `{` 或空的时候不补。
                        val needComma = current.isNotEmpty() && !current.endsWith("{") && !current.endsWith(",")
                        val piece = (if (needComma) "," else "") + component.template

                        // 输入框原本可能带着 `{`（玩家自己打的），追加到它后面
                        val base = if (current.isEmpty()) "{" else current
                        val merged = base + piece
                        input.setText(merged)

                        // 光标放进第一个占位符里
                        val at = piece.indexOf('<')
                        val inside = piece.indexOf('>', at + 1)
                        val caret = if (at >= 0 && inside > at) {
                            base.length + at + 1
                        } else {
                            merged.length
                        }
                        input.setSelection(caret.coerceIn(0, merged.length))
                        refreshPreview(context, binding)
                    }
                })
                row.addView(TextView(context).apply {
                    text = "  "
                    textSize = 12f
                })
            }
        }
    }

    /** 目标选择器快捷值：点一下就把对应输入框填上。 */
    private fun createSelectorShortcuts(
        context: Context,
        binding: com.xanan.cmdhelper.databinding.DialogJsonEditorBinding
    ): LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        val top = dp(context, 6)
        setPadding(0, top, 0, 0)
        listOf("@a", "@p", "@s", "@r", "@e").forEach { value ->
            addView(TextView(context).apply {
                text = value
                textSize = 13f
                setTextColor(context.getColorCompat(R.color.brand_teal))
                setPadding(dp(context, 10), dp(context, 6), dp(context, 10), dp(context, 6))
                isClickable = true
                setOnClickListener {
                    // 找到当前命令里第一个 SELECTOR 参数并写入。
                    val key = currentSpec?.params?.firstOrNull { it.type == ParamType.SELECTOR }?.key
                    val target = key?.let { views[it] } as? EditText
                    target?.setText(value)
                    refreshPreview(context, binding)
                }
            })
        }
    }

    // =======================================================================
    // 预览与生成
    // =======================================================================

    /** 取当前所有控件的值，交给 [CommandSpec.build] 生成命令。 */
    private fun collectValues(): Map<String, String> {
        val spec = currentSpec ?: return emptyMap()
        val values = LinkedHashMap<String, String>()
        spec.params.forEach { param ->
            values[param.key] = when (val view = views[param.key]) {
                is EditText -> view.text?.toString().orEmpty()
                is Spinner -> view.selectedItem?.toString().orEmpty()
                is CheckBox -> if (view.isChecked) "true" else "false"
                else -> ""
            }
        }
        return values
    }

    /** 生成命令；缺必填或 JSON 非法时返回 null。 */
    private fun buildCommand(): String? {
        val spec = currentSpec ?: return null
        val values = collectValues()
        if (spec.missingRequired(values).isNotEmpty()) return null

        // JSON 参数先校验格式：与其生成一条游戏里报错的命令，不如当场拦住。
        spec.params.filter { it.type == ParamType.JSON }.forEach { param ->
            val raw = values[param.key]
            if (!raw.isNullOrBlank()) {
                TextComponent.validate(raw)?.let { return null }
            }
        }
        return spec.build(values)
    }

    /** 刷新预览区与错误提示。 */
    private fun refreshPreview(
        context: Context,
        binding: com.xanan.cmdhelper.databinding.DialogJsonEditorBinding
    ) {
        val spec = currentSpec ?: return
        val values = collectValues()

        val missing = spec.missingRequired(values)
        if (missing.isNotEmpty()) {
            binding.previewText.text = context.getString(R.string.json_empty_preview)
            binding.errorText.visibility = View.VISIBLE
            binding.errorText.text = context.getString(
                R.string.json_missing_params,
                missing.joinToString("、") { it.label }
            )
            return
        }

        // JSON 参数格式错误要单独提示，这是最高频的出错原因。
        spec.params.filter { it.type == ParamType.JSON }.forEach { param ->
            val raw = values[param.key]
            if (!raw.isNullOrBlank()) {
                val error = TextComponent.validate(raw)
                if (error != null) {
                    binding.previewText.text = context.getString(R.string.json_empty_preview)
                    binding.errorText.visibility = View.VISIBLE
                    binding.errorText.text = context.getString(R.string.json_invalid_json, error)
                    return
                }
            }
        }

        val command = spec.build(values)
        binding.errorText.visibility = View.GONE
        binding.previewText.text = command ?: context.getString(R.string.json_empty_preview)
    }

    /** 「格式化」：把 JSON 参数缩进美化（JSON 太长的命令靠它才看得懂层级）。 */
    private fun formatJsonFields(
        context: Context,
        binding: com.xanan.cmdhelper.databinding.DialogJsonEditorBinding
    ) {
        val spec = currentSpec ?: return
        var changed = false
        spec.params.filter { it.type == ParamType.JSON }.forEach { param ->
            val edit = views[param.key] as? EditText ?: return@forEach
            val raw = edit.text?.toString().orEmpty()
            if (raw.isBlank()) return@forEach
            if (TextComponent.validate(raw) == null) {
                // 长命令里 JSON 用压缩形式更实用：基岩版对空格不敏感，
                // 但聊天框输入长度有限，压掉空白能多塞几个组件。
                val compact = TextComponent.compact(raw)
                if (compact != raw) {
                    edit.setText(compact)
                    changed = true
                }
            }
        }
        if (!changed) {
            Toast.makeText(context, R.string.json_format_done, Toast.LENGTH_SHORT).show()
        }
        refreshPreview(context, binding)
    }

    // =======================================================================
    // 小工具
    // =======================================================================

    private fun rowParams(context: Context, heightDp: Int): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            if (heightDp == LinearLayout.LayoutParams.WRAP_CONTENT) {
                LinearLayout.LayoutParams.WRAP_CONTENT
            } else {
                dp(context, heightDp)
            }
        )

    private fun dp(context: Context, value: Int): Int =
        (value * context.resources.displayMetrics.density).toInt()

    private fun Context.getColorCompat(resId: Int): Int =
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) getColor(resId)
        else resources.getColor(resId)

    /** 极简 TextWatcher：只关心"内容变了"。 */
    private class SimpleWatcher(private val onChanged: () -> Unit) :
        android.text.TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
        override fun afterTextChanged(s: android.text.Editable?) = onChanged()
    }
}
