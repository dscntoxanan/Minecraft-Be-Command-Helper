package com.xanan.cmdhelper.ui

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Process
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import com.xanan.cmdhelper.R
import com.xanan.cmdhelper.service.PanelService

/**
 * 唯一的「外部唤起」入口 —— 只干一件事：把 [PanelService] 唤起或收起。
 *
 * # 为什么需要它
 *
 * 面板是悬浮窗，本体在 [PanelService] 里，而那个 Service 是
 * `exported=false`（见 AndroidManifest）。白影 X 的 XP 模块想在
 * 「工具」栏目里放一个开关来控制命令助手，就必须有一条**跨应用**的通路。
 *
 * # 为什么是 Activity，而不是广播 / 导出 Service
 *
 * 这是整条链路里唯一一个别人能碰到的组件，所以选型按"哪条路能收得最窄"来：
 *
 *  1. **不能用广播**：广播是隐式的，任何应用都能发同名 action。
 *  2. **不能用导出的 Service**：Android 12（API 31）起，后台应用调用
 *     `startForegroundService` 会抛 `ForegroundServiceStartNotAllowedException`。
 *     游戏进程里的模块正是"后台"（它只是往屏幕上挂了个悬浮窗，算不上前台应用），
 *     走 Service 这条路在目标机型上大概率直接失败。
 *  3. **Activity 天然解决上面那条**：由系统把本 Activity 拉到前台，
 *     它就是前台进程，随后由**它**去 `startForegroundService` 永远合法。
 *     本 Activity 不画任何像素（`windowNoDisplay` + 无动画 + 立刻 finish），
 *     用户看不到闪一下。
 *
 * # ★ 版本演进：为什么**没有**用签名级权限（重要，别改回去）
 *
 * 第一版在 Manifest 上挂了 `protectionLevel="signature"` 的自定义权限
 * `CONTROL_PANEL`，思路是"只有同一把钥匙签出来的 APK 才发得过来"。
 * **这条路在本工程的注入链路上是死的**，证据在代码里：
 *
 *  - 模块是在游戏进程里跑的，调用方 UID 就是**游戏**的 UID；
 *  - 而游戏包是被内嵌的 LSPatch 补丁器**重新签名**过的 ——
 *    `app/src/main/java/com/xanan/top/util/XpInjector.java:203`
 *    明明白白传了 `-k <keystore> <pass> <alias> <keypass>`；
 *  - 也就是说宿主拿到的是**补丁器那把钥匙**，永远拿不到命令助手的官方签名。
 *
 * 所以签名级权限只会把唯一合法的调用方一起挡在门外（而且是静默失败，
 * 排查起来极其费劲）。**结论：这个入口的安全不靠权限，靠下面这套"首个合法调用方绑定"。**
 *
 * # 收窄成了什么样（四层，任何一层不过就拒绝）
 *
 *  1. **必须精确匹配组件**：调用方得显式指名这个 Activity，**没有写任何
 *     intent-filter**，隐式 Intent 匹配不上。
 *  2. **必须带令牌**：`EXTRA_TOKEN` 必须等于 [TOKEN]。
 *     这不是密码学密钥（反编译就能读到），它的作用是把"碰巧知道包名"
 *     和"确实知道协议"区分开。
 *  3. **必须是对外公布的 action**：只认 [ACTION_SHOW_PANEL] /
 *     [ACTION_HIDE_PANEL] / [ACTION_TOGGLE]，其余一律拒绝。
 *     ⚠️ 这一步必须在**本类**里做：`PanelService` 收到未知 action 时默认是
 *     "显示面板"，把未知 action 透传下去等于把闸门焊死成常开。
 *  4. **首个合法调用方绑定**（[resolveCallUid]）：第一次成功调用它的那个 UID
 *     会被记进 SharedPreferences，之后**只认这一个 UID**。
 *     游戏进程的 UID 在一次安装里是稳定的；别的应用想蹭这个通道，
 *     得在你之前抢先把通道绑到自己身上 —— 那要求它先于模块读明白这个协议。
 *
 * 说清能力边界：这一套挡的是"别的应用随手就能控制命令助手"，
 * **挡不住能改包的人**（改包的人可以直接删掉这段校验）。所以通道的能力本身
 * 被压到极小 —— 全部动作只有"唤起 / 收起 / 切换"三件，改不了任何数据。
 *
 * # 可追溯
 *
 * 每次调用都把调用方 UID / 包名 / referrer 写进日志（[logCaller]），
 * 出问题能倒查是谁在动它。
 */
class ShowPanelActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val action = intent?.action
        val token = intent?.getStringExtra(EXTRA_TOKEN)

        // ---- 第 5 步：先留痕。调用方是谁，出了问题要能倒查 ----
        val callUid = resolveCallUid()
        logCaller(action, callUid)

        // ---- 第 4 层：首个合法调用方绑定 ----
        if (!isTrustedCaller(callUid)) {
            Log.w(TAG, "rejected: caller uid $callUid is not the bound caller")
            reject()
            return
        }
        // ---- 第 2 层：令牌 ----
        if (token != TOKEN) {
            Log.w(TAG, "rejected: bad or missing token")
            reject()
            return
        }
        // ---- 第 3 层：action 白名单 ----
        if (action != ACTION_SHOW_PANEL &&
            action != ACTION_HIDE_PANEL &&
            action != ACTION_TOGGLE
        ) {
            Log.w(TAG, "rejected: unexpected action $action")
            reject()
            return
        }
        // 抗骚扰：同一秒内被反复拉起时不重复干活（面板本身是幂等的，
        // 这里挡的是"被别的东西当开关反复拨"）。
        val now = SystemClock.elapsedRealtime()
        if (now - lastHandledMs < COOLDOWN_MS && action == lastHandledAction) {
            Log.w(TAG, "throttled: same action within ${COOLDOWN_MS}ms")
            reject()
            return
        }
        lastHandledMs = now
        lastHandledAction = action

        if (!canDrawOverlays()) {
            // 悬浮窗权限没给：这时把 Service 拉起来，它会 addView 失败并自杀，
            // 用户看到的现象是"开关打开了但什么都没有"。所以在这里就给一句明确的话，
            // 并且把用户直接送到授权页 —— 比让开关静默失败强得多。
            Log.w(TAG, "rejected: overlay permission not granted")
            Toast.makeText(this, R.string.toast_overlay_permission_required, Toast.LENGTH_LONG)
                .show()
            openOverlaySettings()
            finish()
            return
        }

        when (action) {
            ACTION_HIDE_PANEL -> PanelService.start(this, PanelService.ACTION_HIDE_PANEL)
            ACTION_TOGGLE -> PanelService.start(this, PanelService.ACTION_TOGGLE)
            else -> PanelService.start(this, PanelService.ACTION_SHOW_PANEL)
        }
        finish()
    }

    // =====================================================================
    // 调用方身份
    // =====================================================================

    /**
     * 调用方 UID。
     *
     * `getLaunchedFromUid()`（API 22+）比 `getLaunchedFromPackage()` 可靠：
     * 后者只给包名字符串、可以被伪造/被系统填成 `null`；UID 是内核视角的事实。
     * 拿不到时返回 [INVALID_UID]（-1），后续校验一定不通过 —— 宁可不干活。
     */
    private fun resolveCallUid(): Int {
        return try {
            launchedFromUid
        } catch (t: Throwable) {
            Log.w(TAG, "resolveCallUid failed", t)
            INVALID_UID
        }
    }

    /**
     * 首个合法调用方绑定。
     *
     * - 通道还没被绑过 → 记下本次 UID 并放行（正常情况下这就是游戏进程）；
     * - 已经绑过 → 只认当初那个 UID。
     *
     * `INVALID_UID` 永远拒绝。
     *
     * ⚠️ 想换绑（例如换了宿主包、或误绑）：清除本应用数据即可（设置 → 应用 → 存储 → 清除数据）。
     * 这条路径刻意不做成"应用内一键重置" —— 那等于给攻击者一个重置按钮。
     */
    private fun isTrustedCaller(uid: Int): Boolean {
        if (uid == INVALID_UID) return false
        val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
        val bound = prefs.getInt(KEY_BOUND_UID, INVALID_UID)
        if (bound == INVALID_UID) {
            prefs.edit().putInt(KEY_BOUND_UID, uid).apply()
            Log.i(TAG, "channel bound to uid $uid")
            return true
        }
        return bound == uid
    }

    /**
     * 把调用方记进日志。
     *
     * `getLaunchedFromPackage()` / `referrer` 都只是**尽力而为**：系统可能给出
     * `null` 或 `"android"`，所以这里只做记录，不做判定（判定看 UID）。
     */
    private fun logCaller(action: String?, uid: Int) {
        try {
            val fromPackage = launchedFromPackage
            val referrer = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
                referrer?.toString()
            } else {
                null
            }
            Log.i(
                TAG,
                "call action=$action uid=$uid pid=${Process.myPid()} " +
                    "fromPackage=$fromPackage referrer=$referrer"
            )
        } catch (t: Throwable) {
            Log.w(TAG, "logCaller failed", t)
        }
    }

    /** 静默拒绝：不回 Toast，避免被外部应用当成"探测有没有装命令助手"的探针。 */
    private fun reject() {
        finish()
    }

    private fun canDrawOverlays(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(this)

    /**
     * 跳系统授权页。
     *
     * 注意这是本 Activity **唯一**会做的"额外动作"，而且只在用户主动打开那个开关、
     * 且确实缺权限时才会走到 —— 不存在"被外部应用反复拉起来骚扰用户"的可能。
     */
    private fun openOverlaySettings() {
        runCatching {
            startActivity(
                Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
            )
        }
    }

    companion object {
        private const val TAG = "CmdHelperEntry"

        /** 与 XP 模块 `CommandAssistantLink` 里的常量必须逐字一致。 */
        const val ACTION_SHOW_PANEL = "com.xanan.cmdhelper.action.EXT_SHOW_PANEL"
        const val ACTION_HIDE_PANEL = "com.xanan.cmdhelper.action.EXT_HIDE_PANEL"
        const val ACTION_TOGGLE = "com.xanan.cmdhelper.action.EXT_TOGGLE"

        /**
         * 通道令牌。
         *
         * 它不是密码学意义上的密钥 —— 谁反编译我们的 APK 都能读到它。
         * 它的作用是**把"碰巧知道包名"和"确实知道协议"区分开**：
         * 没有它，任何应用只要猜到组件名就能控制命令助手的面板。
         */
        const val EXTRA_TOKEN = "com.xanan.cmdhelper.extra.TOKEN"
        const val TOKEN = "wsx-cmdhelper-v1"

        private const val PREFS = "cmdhelper_entry"
        private const val KEY_BOUND_UID = "bound_caller_uid"
        private const val INVALID_UID = -1

        /** 同一 action 的最小间隔，防"被反复拨动"。 */
        private const val COOLDOWN_MS = 400L

        /** 节流状态。只在主线程读写（Activity 生命周期回调本来就在主线程）。 */
        private var lastHandledMs = 0L
        private var lastHandledAction: String? = null
    }
}
