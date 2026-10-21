package com.xanan.cmdhelper.util

import android.graphics.Rect
import android.os.Build
import android.view.View
import android.view.ViewTreeObserver
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import kotlin.math.max

/**
 * 软键盘（输入法）高度监听。
 *
 * ── 为什么需要自己算 ────────────────────────────────────────────────────────
 * 命令助手的面板跑在 **Service 的悬浮窗**（`TYPE_APPLICATION_OVERLAY`）里，不是
 * Activity。这类窗口**不参与**系统的 `adjustResize`：键盘弹出时系统既不会缩小
 * 窗口，也不会把我们推上去 —— 结果就是**输入框被键盘挡住**，玩家打字时看不见自己
 * 在输入什么。
 *
 * 所以在悬浮窗里只能自己量键盘有多高，再把窗口整体抬上去
 * （见 [com.xanan.cmdhelper.service.PanelService] 的 `applyWindowGeometry`）。
 *
 * ── 两条腿走路（重要）──────────────────────────────────────────────────────
 * 悬浮窗拿到 IME 高度的可靠程度**因系统而异**，只靠一条路会在部分机型上直接失效，
 * 所以这里同时用两种方式，取两者中较大的那个：
 *
 *  1. **WindowInsets**（API 30+）：`WindowInsetsCompat.Type.ime()` 是官方途径。
 *     窗口可获焦时系统会把 IME 作为 insets 派发下来，数值最准。
 *  2. **OnGlobalLayout + getWindowVisibleDisplayFrame**（全版本兜底）：
 *     屏幕高度减去"当前可见区域底边"，差值就是被键盘占掉的部分。
 *     这是悬浮窗场景里最常能真正生效的一条。
 *
 * 取 max 是刻意的：任一条报了数就用它；两条都没报（键盘没弹）就是 0。
 *
 * @param root 窗口的根 View
 * @param onChange 高度变化回调，参数是键盘占用的像素高度（0 表示键盘收起）
 * @return 停止监听的函数，窗口移除时务必调用
 */
object SoftKeyboardWatcher {

    /** 低于屏幕高度这个比例的一律当成"不是键盘"（导航栏/状态栏也会让这个值非 0）。 */
    private const val MIN_IME_RATIO = 0.15f

    /**
     * 开始监听。
     *
     * @param root 窗口根 View
     * @param onChange 键盘高度变化回调（像素，0 = 收起）
     */
    fun attach(root: View, onChange: (Int) -> Unit): () -> Unit {
        val screenHeight = root.resources.displayMetrics.heightPixels
        val minIme = (screenHeight * MIN_IME_RATIO).toInt()

        // 两条来源各自记住最后一次报的高度，取较大者。
        var fromInsets = 0
        var fromLayout = 0
        var lastReported = -1

        fun publish() {
            val raw = max(fromInsets, fromLayout)
            // 小于阈值的当作 0：否则导航栏/刘海造成的几十像素会被误判成键盘。
            val value = if (raw >= minIme) raw else 0
            if (value == lastReported) return
            lastReported = value
            onChange(value)
        }

        // ── 来源 1：WindowInsets ────────────────────────────────────────────
        val insetsListener = ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            fromInsets = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
            } else {
                0
            }
            publish()
            // 不消费：其它监听者（弹出层等）还要用这份 insets。
            ViewCompat.onApplyWindowInsets(v, insets)
        }

        // ── 来源 2：OnGlobalLayout ──────────────────────────────────────────
        val rect = Rect()
        val layoutListener = ViewTreeObserver.OnGlobalLayoutListener {
            // 「可见区域」的底边以下就是被键盘（或导航栏）占掉的部分。
            //
            // ⚠️ 这里**必须**用屏幕高度，不能用 `root.rootView.height`。
            //    因为调用方拿到键盘高度后会**反过来缩小窗口**（把面板收到键盘上方），
            //    窗口一矮，`rootView.height` 就变了 —— 用它算会形成反馈回路，
            //    高度在"键盘高度"和"缩小后的高度"之间来回震荡，面板疯狂跳动。
            //    屏幕高度是恒定值，不受我们自己改窗口的影响。
            root.getWindowVisibleDisplayFrame(rect)
            val occupied = screenHeight - rect.bottom
            fromLayout = if (occupied > 0) occupied else 0
            publish()
        }

        root.viewTreeObserver.addOnGlobalLayoutListener(layoutListener)

        // ── 兜底触发：焦点变化 ──────────────────────────────────────────────
        // 键盘弹出/收起几乎总是伴随输入框获得/失去焦点。某些 ROM 上悬浮窗拿不到
        // IME 的 insets、也不触发全局布局回调，但焦点回调一定会来 ——
        // 在这两个时刻各主动取一次，能明显提高命中率。
        val focusListener =
            ViewTreeObserver.OnGlobalFocusChangeListener { _, _ -> publish() }
        root.viewTreeObserver.addOnGlobalFocusChangeListener(focusListener)

        // 主动请求一次，让 insets 立即派发下来（不然要等下一次布局）。
        ViewCompat.requestApplyInsets(root)

        return {
            runCatching { root.viewTreeObserver.removeOnGlobalLayoutListener(layoutListener) }
            runCatching { root.viewTreeObserver.removeOnGlobalFocusChangeListener(focusListener) }
            runCatching { ViewCompat.setOnApplyWindowInsetsListener(root, null) }
        }
    }
}
