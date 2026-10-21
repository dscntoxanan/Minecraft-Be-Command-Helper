package com.xanan.cmdhelper.ui.dialog

import android.content.Context
import android.content.ContextWrapper
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.xanan.cmdhelper.R
import com.xanan.cmdhelper.databinding.PanelOverlayBinding

/**
 * 悬浮面板内部的弹层。
 *
 * ── 为什么不用 Dialog ───────────────────────────────────────────────────────
 * 面板跑在 Service 里，窗口类型是 `TYPE_APPLICATION_OVERLAY`。`Dialog.show()` 会尝试
 * 创建一个**独立窗口**，而它拿不到合法的窗口令牌，WindowManager 直接抛：
 *
 * ```
 * WindowManager$BadTokenException: Unable to add window -- token null is not valid;
 *                                   is your activity running?
 * ```
 *
 * 这个异常发生在主线程，结果是**整个进程被杀掉**。实测现象就是：
 * 点 CA 菜单里的任何一项，面板连同服务一起消失。
 *
 * ── 解决办法 ───────────────────────────────────────────────────────────────
 * 把弹层画在**面板窗口内部**（panel_overlay.xml 里的 `dialogHost`）。
 * 面板窗口本身已经有令牌，弹层只是它的一层 View，完全不涉及新的系统窗口，
 * 因此不存在令牌问题，也没有任何权限门槛。
 *
 * 这个容器同时负责三件事：遮罩、点空白关闭、内容居中。
 */
object OverlayDialogs {

    /** 是否已经有弹层展开（避免叠加多层）。 */
    private var showing = false

    /** 面板 View 的 Context 套上对话框主题后的版本缓存。 */
    private var themedContext: Context? = null

    /**
     * 弹出一个自定义内容的弹层。
     *
     * @param binding 面板绑定对象，弹层挂在它的 `dialogHost` 里
     * @param content 弹层内容（一般是某个 dialog_*.xml 的根布局）
     * @param onDismiss 关闭时的回调，调用方用它恢复被遮挡的状态
     */
    fun showCustom(
        binding: PanelOverlayBinding,
        content: View,
        onDismiss: (() -> Unit)? = null
    ) {
        val host = binding.dialogHost
        host.removeAllViews()

        // 遮罩：点击空白处关闭。
        val scrim = View(host.context).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(host.context.getColorCompat(R.color.scrim))
            isClickable = true
            setOnClickListener { dismiss(binding, onDismiss) }
        }
        host.addView(scrim)

        // 内容：限宽 + 限高居中。
        //
        // 限高是必须的：弹层容器只有面板那么高，而 JSON 代码栏这种弹层内容比面板还高，
        // 不限高就会被窗口裁掉上下两头。这里给一个最大高度，超出部分靠滚动查看 ——
        // 比"内容被切掉"或"撑破窗口"都合理。
        val wrapper = FrameLayout(host.context).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER
            )
            val margin = host.resources.getDimensionPixelSize(R.dimen.dialog_horizontal_margin)
            setPadding(margin, margin, margin, margin)
        }

        (content.parent as? android.view.ViewGroup)?.removeView(content)
        val scroll = android.widget.ScrollView(host.context).apply {
            isFillViewport = false
            addView(
                content,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT
                )
            )
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                maxContentHeight(host)
            )
        }
        wrapper.addView(scroll)

        // 右上角关闭按钮：**必须有一个明确能点的关闭入口**。
        //
        // ⚠️ 这里踩过一个真实的坑：原来只靠"点遮罩空白处"关闭，但弹层卡片是
        // `MATCH_PARENT` 宽、内容又高，遮罩只剩左右各 24dp 的窄条 ——
        // **几乎没有可点的空白**。站长报的"玩家指令库无法正常关闭"就是这个：
        // 三级弹层进得去、出不来。
        //
        // 加在 wrapper 上而不是 card 里，有两个原因：
        //  1. 不随内容滚动，永远停在右上角；
        //  2. card 是调用方传进来的，这里不该往里塞东西。
        // 放在 host.addView 之前 addView 到 wrapper，绘制顺序在 scroll 之后，所以压在最上层。
        wrapper.addView(
            TextView(host.context).apply {
                text = "✕"
                textSize = 17f
                gravity = Gravity.CENTER
                setTextColor(host.context.getColorCompat(R.color.text_secondary))
                contentDescription = host.context.getString(R.string.overlay_close)
                val size = dp(host.context, 36)
                layoutParams = FrameLayout.LayoutParams(size, size, Gravity.TOP or Gravity.END)
                isClickable = true
                setOnClickListener { dismiss(binding, onDismiss) }
            }
        )

        host.addView(wrapper)

        host.visibility = View.VISIBLE
        // 明确把弹层提到最上层。
        //
        // 只靠布局顺序不够可靠：面板自己带了 elevation=12dp 的阴影，
        // 在部分 ROM 上绘制顺序会按 elevation 决定，导致弹层被面板的白色底板盖住
        // （实测踩过：JSON 代码栏只露出最上面一条，其余全被面板挡住）。
        // bringToFront() 改变的是同级子 View 的绘制顺序，是确定性的修法。
        host.bringToFront()
        showing = true
    }

    /**
     * 弹层最大高度：面板高度减去上下留白。
     *
     * 以面板 View 的实际高度为准（而不是屏幕高度），这样即使面板被拖动或切换成
     * 全屏，弹层也始终刚好装得下。
     */
    private fun maxContentHeight(host: android.view.View): Int {
        val panel = host.rootView.findViewById<android.view.View>(R.id.panel)
        val available = (panel?.height ?: 0).takeIf { it > 0 }
            ?: host.resources.displayMetrics.heightPixels
        // 留出 48dp：既是视觉呼吸空间，也避免刚好贴边时被系统手势条挡住底部按钮。
        val reserved = (48 * host.resources.displayMetrics.density).toInt()
        return (available - reserved).coerceAtLeast(reserved * 4)
    }

    /** 关闭弹层。 */
    fun dismiss(binding: PanelOverlayBinding, onDismiss: (() -> Unit)? = null) {
        if (!showing) return
        binding.dialogHost.removeAllViews()
        binding.dialogHost.visibility = View.GONE
        showing = false
        onDismiss?.invoke()
    }

    /** 当前是否有弹层展开。 */
    fun isShowing(): Boolean = showing

    /** 面板被销毁时清理，避免静态状态残留。 */
    fun reset() {
        showing = false
        themedContext = null
    }

    /**
     * 取一个带应用主题的 Context 用来 inflate 弹层内容。
     *
     * Service 的 Context 不带应用主题，直接用会让控件退回系统默认样式
     * （黑底、与面板完全不搭）。这里用 [ContextThemeWrapper] 补上。
     */
    fun contextFor(anchor: View): Context {
        val existing = themedContext
        if (existing != null) return existing
        val created = ContextThemeWrapper(anchor.context, R.style.Theme_CommandAssistant)
        themedContext = created
        return created
    }

    /** inflate 一个弹层布局，Context 已经带好主题。 */
    fun inflate(anchor: View, layoutRes: Int): View =
        LayoutInflater.from(contextFor(anchor)).inflate(layoutRes, null, false)

    // =======================================================================
    // 两个最常用的预制弹层：列表选择 与 纯文本提示
    // =======================================================================

    /**
     * 弹出「列表选择」弹层。
     *
     * @param items 选项文案
     * @param onItemClick 选中回调（回调前会自动关闭弹层）
     * @param onCancel 可选的取消回调
     */
    fun showList(
        binding: PanelOverlayBinding,
        title: String?,
        items: Array<String>,
        onItemClick: (Int) -> Unit,
        onCancel: (() -> Unit)? = null
    ) {
        val context = contextFor(binding.commandInput)
        val card = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundResource(R.drawable.bg_dialog_card)
            val pad = context.resources.getDimensionPixelSize(R.dimen.dialog_item_padding_horizontal)
            setPadding(0, pad / 2, 0, pad / 2)
        }

        if (title != null) {
            card.addView(
                TextView(context).apply {
                    text = title
                    textSize = 17f
                    setTextColor(context.getColorCompat(R.color.text_primary))
                    setTypeface(typeface, android.graphics.Typeface.BOLD)
                    setPadding(pad(context), pad(context), pad(context), pad(context) / 2)
                }
            )
        }

        items.forEachIndexed { index, label ->
            card.addView(
                TextView(context).apply {
                    text = label
                    textSize = 16f
                    setTextColor(context.getColorCompat(R.color.text_primary))
                    gravity = Gravity.CENTER_VERTICAL
                    val itemHeight = context.resources.getDimensionPixelSize(R.dimen.dialog_item_height)
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, itemHeight
                    )
                    setPadding(pad(context), 0, pad(context), 0)
                    isClickable = true
                    background = context.getDrawable(R.drawable.bg_menu_item)
                    setOnClickListener {
                        dismiss(binding)
                        onItemClick(index)
                    }
                }
            )
        }

        showCustom(binding, card) { onCancel?.invoke() }
    }

    /** 弹出「一段文字 + 知道了」弹层（教程、关于）。 */
    fun showMessage(
        binding: PanelOverlayBinding,
        title: String?,
        message: String
    ) {
        val context = contextFor(binding.commandInput)
        val card = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundResource(R.drawable.bg_dialog_card)
            setPadding(pad(context), pad(context), pad(context), 0)
        }

        if (title != null) {
            card.addView(
                TextView(context).apply {
                    text = title
                    textSize = 17f
                    setTextColor(context.getColorCompat(R.color.text_primary))
                    setTypeface(typeface, android.graphics.Typeface.BOLD)
                }
            )
        }

        // 长文案（教程正文）必须能滚：直接塞满会把底部按钮顶出屏幕。
        val scroll = android.widget.ScrollView(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
            )
            isFillViewport = false
        }
        scroll.addView(
            TextView(context).apply {
                text = message
                textSize = 13f
                setTextColor(context.getColorCompat(R.color.text_primary))
                setLineSpacing(dp(context, 4).toFloat(), 1f)
                setPadding(0, pad(context), 0, pad(context))
            }
        )
        card.addView(scroll)

        card.addView(
            TextView(context).apply {
                text = context.getString(android.R.string.ok)
                textSize = 15f
                setTextColor(context.getColorCompat(R.color.brand_teal))
                gravity = Gravity.CENTER
                val itemHeight = context.resources.getDimensionPixelSize(R.dimen.dialog_item_height)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, itemHeight
                )
                isClickable = true
                background = context.getDrawable(R.drawable.bg_menu_item)
                setOnClickListener { dismiss(binding) }
            }
        )

        showCustom(binding, card)
    }

    private fun pad(context: Context): Int =
        context.resources.getDimensionPixelSize(R.dimen.dialog_item_padding_horizontal)

    private fun dp(context: Context, value: Int): Int =
        (value * context.resources.displayMetrics.density).toInt()

    private fun Context.getColorCompat(resId: Int): Int =
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) getColor(resId)
        else resources.getColor(resId)
}
