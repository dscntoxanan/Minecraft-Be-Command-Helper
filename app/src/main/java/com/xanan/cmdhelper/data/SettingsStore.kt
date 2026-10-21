package com.xanan.cmdhelper.data

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 用户偏好设置。
 *
 * 目前只包含**真的会影响界面**的几项（字号、面板高度、默认动作），
 * 其余设置项在界面上以"待实现"呈现 —— 宁可诚实标注，也不要放一堆点了没反应的开关。
 *
 * 与 [CommandStore] 一样用 SharedPreferences 落盘：设置项数量少、结构简单，
 * 上数据库属于过度设计。
 */
object SettingsStore {

    private const val PREFS_NAME = "command_settings"

    private const val KEY_FONT_SCALE = "font_scale"
    private const val KEY_PANEL_RATIO = "panel_ratio"
    private const val KEY_CLOSE_ACTION = "close_action"

    /** 命令列表字号倍率。 */
    private val _fontScale = MutableStateFlow(1.0f)
    val fontScale: StateFlow<Float> = _fontScale.asStateFlow()

    /** 悬浮面板占屏幕高度的比例。 */
    private val _panelRatio = MutableStateFlow(DEFAULT_RATIO)
    val panelRatio: StateFlow<Float> = _panelRatio.asStateFlow()

    /**
     * 点「关闭」时的默认动作。
     *
     *  * [CLOSE_ACTION_SEND]：尝试粘进当前输入框并发走（默认，最省事）
     *  * [CLOSE_ACTION_COPY]：只复制到剪贴板
     */
    private val _closeAction = MutableStateFlow(CLOSE_ACTION_SEND)
    val closeAction: StateFlow<String> = _closeAction.asStateFlow()

    private var prefs: android.content.SharedPreferences? = null

    fun init(context: Context) {
        if (prefs != null) return
        val store = context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs = store

        _fontScale.value = store.getFloat(KEY_FONT_SCALE, 1.0f).coerceIn(MIN_FONT_SCALE, MAX_FONT_SCALE)
        _panelRatio.value = store.getFloat(KEY_PANEL_RATIO, DEFAULT_RATIO).coerceIn(MIN_RATIO, MAX_RATIO)
        _closeAction.value = store.getString(KEY_CLOSE_ACTION, CLOSE_ACTION_SEND)
            ?: CLOSE_ACTION_SEND
    }

    fun setFontScale(value: Float) {
        val clamped = value.coerceIn(MIN_FONT_SCALE, MAX_FONT_SCALE)
        _fontScale.value = clamped
        prefs?.edit { putFloat(KEY_FONT_SCALE, clamped) }
    }

    fun setPanelRatio(value: Float) {
        val clamped = value.coerceIn(MIN_RATIO, MAX_RATIO)
        _panelRatio.value = clamped
        prefs?.edit { putFloat(KEY_PANEL_RATIO, clamped) }
    }

    fun setCloseAction(value: String) {
        _closeAction.value = value
        prefs?.edit { putString(KEY_CLOSE_ACTION, value) }
    }

    /** 字号可选档位，界面上按"小/标准/大/特大"展示。 */
    val fontScaleOptions: List<Pair<String, Float>> = listOf(
        "小" to 0.85f,
        "标准" to 1.0f,
        "大" to 1.15f,
        "特大" to 1.3f
    )

    /**
     * 面板高度可选档位。
     *
     * ⚠️ 这里的取向改过两次，别再来回改：
     *
     * 1. 初版默认 0.76（约四分之三屏）。玩家反馈"悬浮窗太大、挡住游戏画面" →
     *    默认降到半屏，档位下移到 0.3~0.7。
     * 2. 站长又要求"点击悬浮窗后改成全屏，不要半屏" →
     *    默认改回**满屏**，档位上限放开到 1.0。
     *
     * 结论：**默认满屏**。理由（站长的用法）：面板是"要打字、要看补全列表"的界面，
     * 半屏时列表只有几行、历史一眼看不全；而遮挡问题已经由
     * "**悬浮球常驻、再点一下就能收起面板**"解决了 —— 不需要靠默认矮来避让。
     * 想留出游戏画面的玩家仍可在这里选小尺寸。
     */
    val panelRatioOptions: List<Pair<String, Float>> = listOf(
        "全屏（默认）" to 1.0f,
        "大（约七成屏）" to 0.7f,
        "标准（约半屏）" to 0.52f,
        "中（约五分之二屏）" to 0.4f,
        "小（约三分之一屏）" to 0.3f
    )

    const val CLOSE_ACTION_SEND = "send"
    const val CLOSE_ACTION_COPY = "copy"

    private const val MIN_FONT_SCALE = 0.8f
    private const val MAX_FONT_SCALE = 1.4f
    private const val MIN_RATIO = 0.25f
    private const val MAX_RATIO = 1.0f

    /** 悬浮面板的默认高度比例（满屏）。 */
    const val DEFAULT_RATIO = 1.0f
}
