package com.xanan.cmdhelper.ui.dialog

import android.content.Context
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import com.xanan.cmdhelper.R
import com.xanan.cmdhelper.data.CommandStore
import com.xanan.cmdhelper.data.SettingsStore
import com.xanan.cmdhelper.databinding.PanelOverlayBinding
import com.xanan.cmdhelper.json.CommandLibrary
import com.xanan.cmdhelper.ui.json.BatchTemplateDialog
import com.xanan.cmdhelper.ui.json.JsonEditorDialog
import com.xanan.cmdhelper.util.ClipboardUtil

/**
 * CA 菜单。
 *
 * 这是命令助手的"主菜单"，入口是底部输入栏最左边的 `CA` 两个字。
 * 菜单项的顺序与文案**刻意与原版保持一致**（插入……、显示样式代码栏、创建批量生成模板、
 * 切换全屏/悬浮窗、教程、设置、退出命令助手），老用户换过来不用重新学。
 *
 * 实现上没有用系统 AlertDialog：原版的菜单是**居中弹出的白色卡片**，宽度比系统
 * 对话框宽、行距更大、条目之间没有分隔线，这些细节用系统对话框改不动。
 * 更重要的是，面板跑在 Service 的悬浮窗里，系统对话框会因为拿不到窗口令牌而崩溃
 * （详见 [OverlayDialogs] 的说明），所以菜单直接叠在面板窗口内部。
 */
object CaMenuDialog {

    /**
     * 弹出 CA 菜单。
     *
     * @param binding 已经 inflate 好的面板绑定对象，菜单直接叠在它上面
     */
    fun show(
        context: Context,
        binding: PanelOverlayBinding,
        onToggleFavoriteView: () -> Unit,
        onToggleFullscreen: () -> Unit,
        onTutorial: () -> Unit,
        onSettings: () -> Unit,
        onExit: () -> Unit
    ) {
        val card = binding.caMenuCard
        card.removeAllViews()

        // 菜单项定义：文案 → 动作。用列表驱动而不是逐个 addView，
        // 以后增删条目只改这一处。
        val items: List<Pair<Int, () -> Unit>> = listOf(
            R.string.ca_insert to { showInsertMenu(context, binding) },
            R.string.ca_recipe_library to { RecipeLibraryDialog.show(binding) },
            R.string.ca_style_bar to { showStyleBar(binding) },
            R.string.ca_batch_template to { showBatchTemplate(binding) },
            // 原版把 § 颜色面板叫做「显示样式代码栏」，这里并列成独立一项：
            // 它解决的是"给纯文本上色"，和上面那个 JSON 生成器不是一回事。
            R.string.ca_format_codes to { showFormatCodes(context, binding) },
            R.string.ca_toggle_fullscreen to {
                dismiss(binding)
                onToggleFullscreen()
            },
            R.string.ca_tutorial to {
                dismiss(binding)
                onTutorial()
            },
            R.string.ca_settings to {
                dismiss(binding)
                onSettings()
            },
            R.string.ca_exit to {
                dismiss(binding)
                onExit()
            }
        )

        items.forEach { (labelRes, action) ->
            card.addView(createRow(context, context.getString(labelRes)) {
                // 除了最后一项，其余动作都先把菜单收起来。
                if (labelRes != R.string.ca_exit) dismiss(binding)
                action()
            })
        }

        binding.menuScrim.visibility = View.VISIBLE
        card.visibility = View.VISIBLE
        // 点遮罩关闭菜单，和原版一致。
        binding.menuScrim.setOnClickListener { dismiss(binding) }
    }

    /** 收起菜单。 */
    fun dismiss(binding: PanelOverlayBinding) {
        binding.caMenuCard.visibility = View.GONE
        binding.menuScrim.visibility = View.GONE
    }

    /**
     * 造一行菜单项。
     *
     * 行高、左右内边距、字号都取自 dimens，方便统一调整；
     * 背景用 selector，按下时有浅灰反馈。
     */
    private fun createRow(context: Context, text: String, onClick: () -> Unit): TextView =
        TextView(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                context.resources.getDimensionPixelSize(R.dimen.dialog_item_height)
            )
            this.text = text
            setTextAppearance(R.style.CaMenuItemText)
            gravity = Gravity.CENTER_VERTICAL
            val pad = context.resources.getDimensionPixelSize(R.dimen.dialog_item_padding_horizontal)
            setPadding(pad, 0, pad, 0)
            isClickable = true
            isFocusable = true
            background = context.getDrawable(R.drawable.bg_menu_item)
            setOnClickListener { onClick() }
        }

    // =======================================================================
    // 子功能
    // =======================================================================

    /** 「插入……」：把常用片段插到输入框光标处。 */
    private fun showInsertMenu(context: Context, binding: PanelOverlayBinding) {
        val snippets = SnippetLibrary.all()
        val labels = snippets.map { it.label }.toTypedArray()
        OverlayDialogs.showList(
            binding = binding,
            title = context.getString(R.string.ca_insert),
            items = labels,
            onItemClick = { which -> insertAtCursor(binding, snippets[which].text) }
        )
    }

    /** 「显示样式代码栏」：JSON 命令生成器。 */
    private fun showStyleBar(binding: PanelOverlayBinding) {
        JsonEditorDialog.show(binding)
    }

    /** 「创建批量生成模板」：用占位符批量展开成多条命令。 */
    private fun showBatchTemplate(binding: PanelOverlayBinding) {
        BatchTemplateDialog.show(binding)
    }

    /** 「教程」：把主要用法讲清楚。 */
    fun showTutorial(context: Context, binding: PanelOverlayBinding) {
        OverlayDialogs.showMessage(
            binding = binding,
            title = context.getString(R.string.ca_tutorial),
            message = context.getString(R.string.tutorial_body)
        )
    }

    /**
     * 「设置」：7 个主项，分组与原版保持一致。
     *
     * 原版的设置是：未登录 / 外观 / 智能补全 / 用户数据 / 推送信息 / 辅助功能 / 关于命令助手。
     * 重制版做了两处**有意的调整**：
     *  * 第一项从「未登录」变成「账号」—— 本版本不联网、不需要账号，如实说明；
     *  * 每一项要么真的能用（外观、用户数据、辅助功能、关于），要么明确标注
     *    「待实现」。原版那种"点进去一片空白"的子页在这里不做。
     */
    fun showSettings(context: Context, binding: PanelOverlayBinding) {
        val options = arrayOf(
            context.getString(R.string.settings_account),
            context.getString(R.string.settings_appearance),
            context.getString(R.string.settings_autocomplete),
            context.getString(R.string.settings_user_data),
            context.getString(R.string.settings_push),
            context.getString(R.string.settings_accessibility),
            context.getString(R.string.settings_about_entry)
        )
        OverlayDialogs.showList(
            binding = binding,
            title = context.getString(R.string.ca_settings),
            items = options,
            onItemClick = { which ->
                when (which) {
                    0 -> OverlayDialogs.showMessage(
                        binding, options[0], context.getString(R.string.account_local_only)
                    )

                    1 -> showAppearance(context, binding)
                    2 -> OverlayDialogs.showMessage(
                        binding, options[2], context.getString(R.string.autocomplete_tip)
                    )

                    3 -> showUserData(context, binding)
                    4 -> OverlayDialogs.showMessage(
                        binding, options[4], context.getString(R.string.not_implemented_yet)
                    )

                    5 -> showAccessibility(context, binding)
                    6 -> showAbout(context, binding)
                }
            }
        )
    }

    /** 外观子页：8 项，其中字号 / 面板高度 / 关闭动作是真的会生效的。 */
    private fun showAppearance(context: Context, binding: PanelOverlayBinding) {
        val scaleNow = SettingsStore.fontScale.value
        val scaleLabel = SettingsStore.fontScaleOptions
            .firstOrNull { kotlin.math.abs(it.second - scaleNow) < 0.01f }?.first ?: "标准"
        val ratioNow = SettingsStore.panelRatio.value
        val ratioLabel = SettingsStore.panelRatioOptions
            .firstOrNull { kotlin.math.abs(it.second - ratioNow) < 0.01f }?.first ?: "四分之三（默认）"
        val closeLabel = if (SettingsStore.closeAction.value == SettingsStore.CLOSE_ACTION_COPY) {
            context.getString(R.string.appearance_close_copy)
        } else {
            context.getString(R.string.appearance_close_send)
        }

        val items = arrayOf(
            "${context.getString(R.string.appearance_font)}：$scaleLabel",
            "${context.getString(R.string.appearance_panel_ratio)}：$ratioLabel",
            "${context.getString(R.string.appearance_close_action)}：$closeLabel",
            "${context.getString(R.string.appearance_theme)}：${context.getString(R.string.appearance_theme_value)}",
            "${context.getString(R.string.appearance_editor)}：${context.getString(R.string.appearance_editor_value)}",
            "${context.getString(R.string.appearance_drag)}：${context.getString(R.string.appearance_drag_value)}",
            "${context.getString(R.string.appearance_sound)}：${context.getString(R.string.appearance_sound_value)}",
            "${context.getString(R.string.appearance_vibrate)}：${context.getString(R.string.appearance_vibrate_value)}"
        )

        OverlayDialogs.showList(
            binding = binding,
            title = context.getString(R.string.settings_appearance),
            items = items,
            onItemClick = { which ->
                when (which) {
                    0 -> pickFontScale(context, binding)
                    1 -> pickPanelRatio(context, binding)
                    2 -> pickCloseAction(context, binding)
                    // 主题刻意只做浅色：原版就是浅色面板，深色会让面板在游戏画面上辨识度变差。
                    3 -> OverlayDialogs.showMessage(
                        binding, items[3], context.getString(R.string.appearance_theme_value)
                    )

                    4 -> OverlayDialogs.showMessage(
                        binding, items[4], context.getString(R.string.appearance_editor_value)
                    )

                    else -> OverlayDialogs.showMessage(
                        binding, null, context.getString(R.string.not_implemented_yet)
                    )
                }
            }
        )
    }

    private fun pickFontScale(context: Context, binding: PanelOverlayBinding) {
        val options = SettingsStore.fontScaleOptions
        OverlayDialogs.showList(
            binding = binding,
            title = context.getString(R.string.appearance_font),
            items = options.map { it.first }.toTypedArray(),
            onItemClick = { which -> SettingsStore.setFontScale(options[which].second) }
        )
    }

    private fun pickPanelRatio(context: Context, binding: PanelOverlayBinding) {
        val options = SettingsStore.panelRatioOptions
        OverlayDialogs.showList(
            binding = binding,
            title = context.getString(R.string.appearance_panel_ratio),
            items = options.map { it.first }.toTypedArray(),
            onItemClick = { which -> SettingsStore.setPanelRatio(options[which].second) }
        )
    }

    private fun pickCloseAction(context: Context, binding: PanelOverlayBinding) {
        val options = arrayOf(
            context.getString(R.string.appearance_close_send),
            context.getString(R.string.appearance_close_copy)
        )
        OverlayDialogs.showList(
            binding = binding,
            title = context.getString(R.string.appearance_close_action),
            items = options,
            onItemClick = { which ->
                SettingsStore.setCloseAction(
                    if (which == 0) SettingsStore.CLOSE_ACTION_SEND else SettingsStore.CLOSE_ACTION_COPY
                )
            }
        )
    }

    /**
     * 用户数据：导出 / 导入命令库、清空历史、数量统计。
     *
     * 导入导出走系统剪贴板而不是文件选择器，是两个考虑：
     *  * 命令库是纯文本 JSON，粘进聊天软件发给别人本来就是最常见的分享方式；
     *  * 不申请存储权限，Android 16 上少一道运行时授权的麻烦。
     */
    private fun showUserData(context: Context, binding: PanelOverlayBinding) {
        val items = arrayOf(
            context.getString(R.string.userdata_export),
            context.getString(R.string.userdata_import),
            context.getString(R.string.userdata_clear),
            context.getString(R.string.userdata_stats)
        )
        OverlayDialogs.showList(
            binding = binding,
            title = context.getString(R.string.settings_user_data),
            items = items,
            onItemClick = { which ->
                when (which) {
                    0 -> {
                        val library = CommandStore.exportLibrary("命令助手命令库")
                        ClipboardUtil.copy(
                            context,
                            library.toJson().toString(),
                            context.getString(R.string.userdata_export_done, library.entries.size)
                        )
                    }

                    1 -> {
                        val raw = ClipboardUtil.read(context)
                        val imported = raw?.let { text ->
                            runCatching {
                                // fromJson 是 CommandLibrary 伴生对象上的工厂方法，
                                // 这里刻意写全限定名，避免和"导入 CommandLibrary 单例"
                                // 那种歧义阅读（这个类名同时是数据类与命令库单例的名字来源）。
                                com.xanan.cmdhelper.data.CommandLibrary
                                    .fromJson(org.json.JSONObject(text))
                            }.getOrNull()?.let { CommandStore.importLibrary(it) }
                        }
                        OverlayDialogs.showMessage(
                            binding = binding,
                            title = null,
                            message = if (imported == null) {
                                context.getString(R.string.userdata_import_empty)
                            } else {
                                context.getString(R.string.userdata_import_done, imported)
                            }
                        )
                    }

                    2 -> CommandStore.clearHistory()
                    3 -> {
                        val all = CommandStore.entries.value
                        val favorites = all.count { it.favorite }
                        OverlayDialogs.showMessage(
                            binding = binding,
                            title = context.getString(R.string.userdata_stats),
                            message = "全部 ${all.size} 条\n收藏 $favorites 条\n历史 ${all.size - favorites} 条"
                        )
                    }
                }
            }
        )
    }

    /** 辅助功能：把无障碍服务的状态与开启入口放在这里。 */
    private fun showAccessibility(context: Context, binding: PanelOverlayBinding) {
        val ready = com.xanan.cmdhelper.service.PasteAccessibilityService.isReady()
        val label = context.getString(R.string.state_enabled)
        val notEnabled = context.getString(R.string.state_optional)
        val items = arrayOf(
            "${context.getString(R.string.permission_accessibility_title)}：${if (ready) label else notEnabled}",
            context.getString(R.string.permission_accessibility_desc)
        )
        // 只有第一项可点：直接跳到系统无障碍设置页。
        OverlayDialogs.showList(
            binding = binding,
            title = context.getString(R.string.settings_accessibility),
            items = items,
            onItemClick = { which ->
                if (which == 0) {
                    runCatching {
                        context.startActivity(
                            android.content.Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS)
                                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    }
                }
            }
        )
    }

    private fun showAbout(context: Context, binding: PanelOverlayBinding) {
        OverlayDialogs.showMessage(
            binding = binding,
            title = context.getString(R.string.settings_about_entry),
            message = context.getString(R.string.about_body)
        )
    }

    /**
     * 「显示 § 格式代码面板」。
     *
     * 原版的「显示样式代码栏」其实是这个：一排 § 颜色/格式快捷码
     * （§l §m §n §o §k §r + §0~§f）。它的用途是给**普通文本**上色 ——
     * 例如 `/say §a你好` 会显示绿色。
     *
     * 重制版把它和 JSON 生成器**分成两个入口**，因为两者解决的场景不同：
     *  * § 格式码：给 say/title 这类纯文本命令快速上色，一键插入；
     *  * JSON 生成器：给 titleraw/tellraw/give 生成完整 JSON 组件。
     * 原版只有前者，遇到 JSON 命令就无能为力了。
     */
    private fun showFormatCodes(context: Context, binding: PanelOverlayBinding) {
        val codes = FORMAT_CODES + COLOR_CODES
        val labels = codes.map { "$it  ${describeCode(it)}" }.toTypedArray()
        OverlayDialogs.showList(
            binding = binding,
            title = context.getString(R.string.ca_format_codes),
            items = labels,
            onItemClick = { which -> insertAtCursor(binding, codes[which]) }
        )
    }

    /** 给每个 § 码一句人话说明，否则用户只能靠颜色代号猜。 */
    private fun describeCode(code: String): String = when (code) {
        "§l" -> "加粗"
        "§m" -> "删除线"
        "§n" -> "下划线"
        "§o" -> "斜体"
        "§k" -> "乱码"
        "§r" -> "重置格式"
        "§0" -> "黑色"
        "§1" -> "深蓝"
        "§2" -> "深绿"
        "§3" -> "深青"
        "§4" -> "深红"
        "§5" -> "深紫"
        "§6" -> "金色"
        "§7" -> "灰色"
        "§8" -> "深灰"
        "§9" -> "蓝色"
        "§a" -> "绿色"
        "§b" -> "青色"
        "§c" -> "红色"
        "§d" -> "粉色"
        "§e" -> "黄色"
        "§f" -> "白色"
        else -> ""
    }

    /**
     * 长按命令条目弹出的操作菜单。
     *
     * 单独暴露成公开方法，是因为它由列表适配器触发，调用方在 PanelService 里，
     * 不方便走 CA 菜单那条路径。
     */
    fun showEntryActions(
        binding: PanelOverlayBinding,
        items: Array<String>,
        onItemClick: (Int) -> Unit
    ) {
        OverlayDialogs.showList(
            binding = binding,
            title = null,
            items = items,
            onItemClick = onItemClick
        )
    }

    /**
     * 在输入框光标处插入文本。
     *
     * 用光标位置而不是直接 setText：玩家通常已经打了一半命令，插入片段应该接在
     * 光标后面，而不是把已输入的内容冲掉。
     */
    private fun insertAtCursor(binding: PanelOverlayBinding, text: String) {
        val edit = binding.commandInput
        val current = edit.text?.toString().orEmpty()
        val start = edit.selectionStart.coerceIn(0, current.length)
        val end = edit.selectionEnd.coerceIn(0, current.length)
        val merged = current.substring(0, start) + text + current.substring(end)
        edit.setText(merged)
        edit.setSelection((start + text.length).coerceAtMost(merged.length))
    }

    /**
     * § 格式码与颜色码。
     *
     * 顺序照原版面板排：先格式（§l §m §n §o §k §r），再颜色（§0~§f）。
     * 这些码在 Minecraft 聊天框里直接生效，是给 say / title 这类纯文本命令上色最快的办法。
     */
    private val FORMAT_CODES = listOf("§l", "§m", "§n", "§o", "§k", "§r")

    private val COLOR_CODES = listOf(
        "§0", "§1", "§2", "§3", "§4", "§5", "§6", "§7",
        "§8", "§9", "§a", "§b", "§c", "§d", "§e", "§f"
    )
}
