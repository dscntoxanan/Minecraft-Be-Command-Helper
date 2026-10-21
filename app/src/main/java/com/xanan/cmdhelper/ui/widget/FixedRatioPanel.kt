package com.xanan.cmdhelper.ui.widget

import android.content.Context
import android.util.AttributeSet
import android.widget.LinearLayout

/**
 * 悬浮面板的底板。
 *
 * 它只做一件事：**决定自己有多高**。
 *
 * 原版的悬浮面板不是"铺满屏幕"，而是从屏幕底部往上占大约四分之三，留出上方一块
 * 能看见游戏画面的区域 —— 这个比例是刻意的，玩家要一边看游戏一边选命令。
 * 全屏形态时则由调用方把窗口高度直接设成 MATCH_PARENT，这里不再干预。
 *
 * 用自定义 View 而不是在 XML 里写死 dp 高度，是因为不同机型的屏幕高度差异很大：
 * 写死会在小屏上顶到状态栏、在大屏上留一大片空白。
 */
class FixedRatioPanel @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    /** 面板高度占可用高度的比例（原版观感约为 76%）。 */
    var heightRatio: Float = DEFAULT_RATIO

    /** 全屏模式：此时交给窗口决定大小，View 不再按比例算高。 */
    var fullscreen: Boolean = false

    init {
        orientation = VERTICAL
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        if (fullscreen) {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec)
            return
        }

        val available = MeasureSpec.getSize(heightMeasureSpec)
        val ratio = when (MeasureSpec.getMode(heightMeasureSpec)) {
            // 父容器给了确定高度才按比例算；UNSPECIFIED 时不用管，正常测量即可。
            MeasureSpec.EXACTLY, MeasureSpec.AT_MOST -> {
                if (available > 0) available else resources.displayMetrics.heightPixels
            }

            else -> resources.displayMetrics.heightPixels
        }

        val target = (ratio * heightRatio).toInt()
            .coerceAtLeast(dp(MIN_HEIGHT_DP))
            .coerceAtMost(available)

        super.onMeasure(
            widthMeasureSpec,
            MeasureSpec.makeMeasureSpec(target, MeasureSpec.EXACTLY)
        )
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    private companion object {
        /**
         * 面板高度比例（XML 里没设 heightRatio 时的兜底值）。
         *
         * ⚠️ 必须与 [com.xanan.cmdhelper.data.SettingsStore.DEFAULT_RATIO] 保持一致：
         * 面板实际用的比例是 SettingsStore 推过来的，这个常量只在"还没收到设置"时生效。
         * 两处不一致会导致刚打开面板的那一瞬间高度跳一下。
         */
        const val DEFAULT_RATIO = 0.52f

        /** 最小高度：保证列表至少能露出两三行，否则面板没有使用价值。 */
        const val MIN_HEIGHT_DP = 200
    }
}
