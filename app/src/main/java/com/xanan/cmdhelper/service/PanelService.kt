package com.xanan.cmdhelper.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.TextView
import androidx.core.app.NotificationCompat
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.xanan.cmdhelper.R
import com.xanan.cmdhelper.data.CommandEntry
import com.xanan.cmdhelper.data.CommandStore
import com.xanan.cmdhelper.data.SettingsStore
import com.xanan.cmdhelper.databinding.PanelOverlayBinding
import com.xanan.cmdhelper.ui.CommandAdapter
import com.xanan.cmdhelper.ui.CompletionPanel
import com.xanan.cmdhelper.ui.MainActivity
import com.xanan.cmdhelper.ui.dialog.CaMenuDialog
import com.xanan.cmdhelper.util.ClipboardUtil
import com.xanan.cmdhelper.util.SoftKeyboardWatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * 悬浮面板服务 —— 整个 App 的主界面。
 *
 * 「命令助手」的形态不是一个要打开的 App，而是一块**一直浮在游戏上面的面板**：
 * 玩家在 Minecraft 里想输入命令时，直接从面板点一下，命令就被粘进聊天框。
 * 因此面板必须跑在 Service + WindowManager 里，而不是 Activity 里 —— Activity
 * 一切到后台就会被系统冻结，悬浮窗也跟着消失。
 *
 * 与 Android 16（API 36）相关的处理，逐条说明：
 *
 *  1. **前台服务类型**：Android 14 起，前台服务必须声明类型，否则启动即崩。
 *     本服务已在清单里声明 `specialUse` 并填写用途说明。这里用
 *     [android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE] 启动。
 *  2. **通知权限**：Android 13+ 的通知是运行时权限。没拿到权限时
 *     `startForeground` 依然要调用（服务本身照常运行），只是通知不展示 ——
 *     所以这里不能因为权限缺失就跳过 startForeground。
 *  3. **悬浮窗类型**：Android 8 起 `TYPE_PHONE` / `TYPE_SYSTEM_ALERT` 已废弃，
 *     统一用 `TYPE_APPLICATION_OVERLAY`，否则直接抛异常。
 *  4. **边到边**：面板窗口自己处理 inset，底部导航栏区域留出安全间距，
 *     避免"关闭"按钮被手势条盖住点不到。
 */
class PanelService : Service(), LifecycleOwner {

    private lateinit var windowManager: WindowManager
    private lateinit var binding: PanelOverlayBinding
    private lateinit var adapter: CommandAdapter

    /** 面板窗口的布局参数，拖动窗口时直接改这个对象再 updateViewLayout。 */
    private lateinit var panelParams: WindowManager.LayoutParams

    /** 收起后的悬浮球窗口。 */
    private var bubbleView: View? = null
    private var bubbleParams: WindowManager.LayoutParams? = null

    private var panelAttached = false
    private var fullscreen = false

    /**
     * 用户拖动面板产生的下移量（像素，≥ 0）。
     *
     * ⚠️ 不能直接往 `panelParams.y` 里写这个值：`y` 现在同时承载"拖动偏移"和
     * "让开键盘的上移量"两部分（见 [applyWindowGeometry]）。把两者拆开存，
     * 否则键盘一收，拖动出来的位置就会被覆盖掉。
     */
    private var manualOffsetY = 0

    /** 当前软键盘高度（像素，0 = 收起）。由 [SoftKeyboardWatcher] 推送。 */
    private var imeHeightPx = 0

    /**
     * 窗口**可用区域**高度（不含状态栏）。
     *
     * ⚠️ **不能**用 `displayMetrics.heightPixels` 代替它。实测本机（ColorOS）：
     * 屏幕 2340，但面板窗口顶边被系统压到状态栏以下（y=108），实际可用只有 2231。
     * 早先按 `屏幕高 - 键盘高` 设窗口高度 → 内容比窗口高 108px →
     * **输入栏底部被裁掉、CA/关闭那行直接看不见**。
     */
    private var areaHeight = 0

    /** 键盘收起时面板的**自然高度**（全屏形态下等于 [areaHeight]；比例形态下是比例算出来的值）。 */
    private var naturalHeight = 0

    /**
     * 当前窗口高度是否正被我们按键盘高度压过。
     *
     * 用来避免"把缩小后的高度当成自然高度"：一旦在键盘态采样，
     * 面板会越缩越小（键盘收起后也回不去）。
     */
    private var heightAdjusted = false

    /** 停止键盘监听的句柄，面板卸载时调用。 */
    private var stopImeWatch: (() -> Unit)? = null

    /** 当前列表显示的是收藏还是全部（原版左右两块，这里用标题栏切换）。 */
    private var showingFavorites = false

    /** 生命周期：让 repeatOnLifecycle 能正确随服务停止而取消协程。 */
    private val lifecycleRegistry = LifecycleRegistry(this)
    override val lifecycle: Lifecycle get() = lifecycleRegistry

    private var collectJob: Job? = null

    /** 设置订阅任务（字号、面板高度）。 */
    private var settingsJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        lifecycleRegistry.currentState = Lifecycle.State.CREATED
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        startForegroundCompat()
        lifecycleRegistry.currentState = Lifecycle.State.STARTED
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action

        when (action) {
            ACTION_SHOW_PANEL -> showPanel()
            ACTION_HIDE_PANEL -> hidePanel()
            ACTION_TOGGLE -> if (panelAttached) hidePanel() else showPanel()
            else -> showPanel()
        }

        // ⚠️ 悬浮球必须在**面板之后**创建，顺序不能反。
        //
        // WindowManager 里后 addView 的窗口层级更高。面板是全屏覆盖的，
        // 如果先建球再开面板，球会被面板整个盖住（实测：球窗口确实在，
        // 但 `mAttrs` 显示它是先加的那个，屏幕上完全看不到）。
        //
        // 站长要的交互是"展开后也显示悬浮窗，再次点击悬浮窗关闭界面"，
        // 所以球必须**压在最上面**。放这里（when 之后）就保证了这一点，
        // 而且 `else`（默认 SHOW_PANEL）路径也一并覆盖。
        attachBubble()

        return START_STICKY
    }

    override fun onDestroy() {
        collectJob?.cancel()
        settingsJob?.cancel()
        detachPanel()
        detachBubble()
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        super.onDestroy()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        // 用户从最近任务里划掉界面时，面板本身不该跟着消失 —— 它还在游戏上面飘着。
        super.onTaskRemoved(rootIntent)
    }

    // =======================================================================
    // 通知与前台服务
    // =======================================================================

    /**
     * 启动前台服务。
     *
     * 注意 API 级别的分叉：`startForeground(id, notification, type)` 这个三参重载
     * 是 API 29 才有的；API 28 及以下必须用两参版本，否则 NoSuchMethodError。
     */
    private fun startForegroundCompat() {
        createNotificationChannel()
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notification_channel_panel),
            NotificationManager.IMPORTANCE_MIN
        ).apply {
            description = getString(R.string.notification_channel_panel_desc)
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pending = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_bubble)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(getString(R.string.notification_text))
            .setContentIntent(pending)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setSilent(true)
            .build()
    }

    // =======================================================================
    // 面板窗口
    // =======================================================================

    /** 显示悬浮面板。已显示时是幂等的。 */
    fun showPanel() {
        if (panelAttached) return

        // ⚠️ 这里**不再 detachBubble()**：悬浮球要一直在，展开面板后仍然能点到它
        // 来收起面板（站长要求的交互）。悬浮球窗口与面板窗口是两个独立窗口，
        // 可以同时存在；面板后 addView，所以显示层级在球之上 —— 但球的
        // x/y 是贴着屏幕右侧单独算的，不会被面板压住。
        //
        // 历史：旧实现是"开面板就收球"，那时点球是单向的（只能开、不能关）。

        binding = PanelOverlayBinding.inflate(LayoutInflater.from(this))
        // 面板高度按用户设置的比例展开（默认满屏，见 SettingsStore.DEFAULT_RATIO）。
        binding.panel.heightRatio = SettingsStore.panelRatio.value

        // 新面板从"没被拖动过、也没有键盘"开始，避免沿用上一个面板的位置与高度基准。
        manualOffsetY = 0
        imeHeightPx = 0
        areaHeight = 0
        naturalHeight = 0
        heightAdjusted = false

        setupWindowParams()
        setupList()
        setupHeader()
        setupInputBar()

        // 部分 ROM 在服务没有悬浮窗权限时 addView 会直接抛 BadTokenException，
        // 这里兜住并回退到"提示用户去授权"，而不是让整个服务挂掉。
        runCatching { windowManager.addView(binding.root, panelParams) }
            .onSuccess { panelAttached = true }
            .onFailure { showOverlayPermissionHint() }

        // 面板刚挂上、还没弹键盘时量一次基准尺寸。
        // ⚠️ **只在这里量（外加全屏/比例变化且键盘收起时）**，不要在布局回调里反复量：
        //    键盘弹起也会触发布局，那时量到的 `getWindowVisibleDisplayFrame` 已经被
        //    键盘影响（实测会拿到 1372 而不是 2231），
        //    之后再拿它减键盘高度 → 面板直接缩成一半、键盘收起也回不去。
        runCatching { binding.root.post { capturePanelMetrics() } }

        // 悬浮窗不参与系统的 adjustResize，键盘弹出时必须自己把窗口抬上去。
        startImeWatch()

        observeStore()
        observeSettings()
    }

    /**
     * 量一次面板的"自然高度"与"可用区域高度"。
     *
     * ⚠️ 键盘开着时**直接返回**：这时量到的是被键盘挤过的尺寸，拿它当基准会让面板
     * 越缩越小。调用方要么在面板刚挂上时调，要么在明确"键盘已收起"时调。
     *
     * `getWindowVisibleDisplayFrame` 给的是**这块窗口能用的显示区域**（已扣掉状态栏），
     * 比 `displayMetrics.heightPixels` 准确 —— 后者在本机会多出 108px 的状态栏高度，
     * 用它算窗口高度会让内容超出窗口、输入栏被裁掉。
     */
    private fun capturePanelMetrics() {
        if (!::binding.isInitialized) return
        if (imeHeightPx > 0) return
        val r = android.graphics.Rect()
        runCatching { binding.root.getWindowVisibleDisplayFrame(r) }
        if (r.height() > 0) areaHeight = r.height()
        val h = binding.root.height
        if (h > 0) naturalHeight = h
    }

    // =======================================================================
    // 输入法避让
    // =======================================================================

    /**
     * 开始监听软键盘高度。
     *
     * 记录句柄是为了在面板卸载时**一定**解绑：`OnGlobalLayoutListener` 与
     * `setOnApplyWindowInsetsListener` 都会持有根 View 的引用，忘了解会泄漏。
     */
    private fun startImeWatch() {
        stopImeWatch?.invoke()
        stopImeWatch = SoftKeyboardWatcher.attach(binding.root) { height ->
            if (height == imeHeightPx) return@attach
            imeHeightPx = height
            applyWindowGeometry()
        }
    }

    /**
     * 按"拖动偏移 + 键盘高度 + 当前形态"重算窗口几何。
     *
     * ── 为什么是改窗口参数，而不是给 View 加 padding/translation ──────────────
     * 面板窗口是 `WRAP_CONTENT` + `Gravity.BOTTOM` 的独立窗口，键盘是**盖在它上面**
     * 的另一层。给内容加 padding 只会把内容挤在同一个窗口里，窗口底边仍然在
     * 键盘后面 —— 输入框还是看不见。唯一有效的做法是**把窗口本身往上挪**。
     *
     * 同时还要把窗口高度收到"键盘上方那部分"，而且必须用**实测的可用高度**
     * （[areaHeight] / [naturalHeight]），不能用屏幕高度：
     *  * 悬浮窗形态下，`FixedRatioPanel` 按"可用高度"算比例，收窄后它自己会变矮；
     *  * 全屏形态下高度本来是 MATCH_PARENT，不收窄就会上移出屏幕、标题栏消失；
     *  * 而"可用高度"比屏幕高度少一截（状态栏），用屏幕高度算会导致
     *    内容比窗口高、**输入栏底部被裁掉**（实测踩过，本机差 108px）。
     *
     * 高度取 `min(自然高度, 可用区域 - 键盘高)`：
     *  * 全屏时自然高度 = 可用区域 → 结果就是"键盘上方那部分"；
     *  * 比例形态下面板本来就只有五成高，键盘再高也放得下 → 保持原高，只上移，
     *    不因为弹键盘而莫名变小。
     *
     * `y` 取负值表示上移：`Gravity.BOTTOM` 下，窗口底边从屏幕底部往上偏移
     * `-y` 像素，所以 `y = -imeHeight` 正好让底边落在键盘上沿。
     */
    private fun applyWindowGeometry() {
        if (!::panelParams.isInitialized) return

        val screenHeight = resources.displayMetrics.heightPixels
        // 上限给到 75%：横屏时输入法能占到屏幕一半以上，卡在 screenH/2 会导致抬得不够。
        val ime = imeHeightPx.coerceIn(0, (screenHeight * 0.75f).toInt())

        if (ime <= 0) {
            heightAdjusted = false
            panelParams.y = manualOffsetY
            panelParams.height = if (fullscreen) {
                WindowManager.LayoutParams.MATCH_PARENT
            } else {
                WindowManager.LayoutParams.WRAP_CONTENT
            }
        } else {
            // 还没采过样（键盘在首次布局前就弹了）：退回屏幕高度，宁可按大一点，
            // 也不要在键盘态采样把自己越缩越小。
            val area = if (areaHeight > 0) areaHeight else screenHeight
            val natural = if (naturalHeight > 0) naturalHeight else area

            heightAdjusted = true
            panelParams.y = manualOffsetY - ime
            panelParams.height = minOf(natural, area - ime)
                .coerceAtLeast(area / 3)
        }

        if (panelAttached) {
            runCatching { windowManager.updateViewLayout(binding.root, panelParams) }
                .onFailure { android.util.Log.e(TAG, "applyWindowGeometry failed", it) }
        }
    }

    /**
     * 隐藏面板，并留下一个悬浮球。
     *
     * 原版关闭后仍保留入口，所以这里必须先卸面板、再挂悬浮球。
     * ⚠️ **两步都可能失败，而且失败的表现极具误导性**：
     * 悬浮窗权限没开时 `addView` 会抛，如果只是 `runCatching` 吞掉，
     * 结果就是"面板关掉了、但没有任何入口能再打开它"，玩家看到的现象是
     * **"点关闭没反应"**（实测踩过）。所以失败一定要留日志（见 attachBubble）。
     */
    fun hidePanel() {
        // 只要求 binding 已初始化。**不能**再拿 `panelAttached` 当守卫 ——
        // 上面那个"面板是否在屏幕上"的分支一旦与它不同步，就会两个函数双双 return，
        // 表现成"点球没反应"。这里让 hidePanel 自己容错：`detachPanel()` 内部
        // 有 `if (!panelAttached) return`，面板本来就不在时也安全。
        if (!::binding.isInitialized) return
        detachPanel()
        attachBubble()
    }

    private fun detachPanel() {
        if (!panelAttached) return
        // 先解绑键盘监听：它持有根 View 的引用，面板都卸了还挂着会泄漏。
        stopImeWatch?.invoke()
        stopImeWatch = null
        imeHeightPx = 0
        runCatching { windowManager.removeView(binding.root) }
            .onFailure { android.util.Log.e(TAG, "detachPanel removeView failed", it) }
        panelAttached = false
        collectJob?.cancel()
        settingsJob?.cancel()
    }

    /**
     * 组装窗口参数。
     *
     * 高度用 WRAP_CONTENT：真正的比例由 [com.xanan.cmdhelper.ui.widget.FixedRatioPanel]
     * 在测量阶段算出来，这样横竖屏切换、分屏模式下都能自适应。
     */
    private fun setupWindowParams() {
        panelParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayWindowType(),
            // NOT_FOCUSABLE 会让输入框收不到键盘，所以这里必须可获焦。
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.START
            // 抬高一点，避免被三键导航栏的手势区盖住底部按钮。
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
    }

    /** Android 8.0 起必须用 TYPE_APPLICATION_OVERLAY，旧类型会直接抛异常。 */
    private fun overlayWindowType(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

    private fun setupHeader() {
        binding.headerTitle.text = getString(
            if (showingFavorites) R.string.panel_favorites else R.string.panel_history
        )

        // 点标题栏在「历史 / 收藏」之间切换。
        binding.headerTitle.setOnClickListener {
            showingFavorites = !showingFavorites
            binding.headerTitle.text = getString(
                if (showingFavorites) R.string.panel_favorites else R.string.panel_history
            )
            refreshList()
        }

        // 小球：展开 / 收起成悬浮球。
        binding.bubbleIcon.setOnClickListener { hidePanel() }

        // 标题栏整条都可以拖动窗口，手感比只抓小球好得多。
        attachWindowDrag(binding.headerBar)
    }

    private fun setupList() {
        adapter = CommandAdapter(
            onCopy = { entry -> ClipboardUtil.copy(this, entry.text) },
            onPick = { entry ->
                // 点一条命令 = 粘进输入框（不是直接发出去）。
                // 这是刻意的：玩家往往要再改几个参数才发送。
                binding.commandInput.setText(entry.text)
                binding.commandInput.setSelection(entry.text.length)
                CommandStore.record(entry.text)
            },
            onLongPress = { entry, _ -> showEntryMenu(entry) }
        )

        binding.commandList.layoutManager = LinearLayoutManager(this)
        binding.commandList.adapter = adapter
        binding.commandList.addItemDecoration(
            DividerItemDecoration(this, DividerItemDecoration.VERTICAL)
        )
    }

    private fun setupInputBar() {
        binding.caButton.setOnClickListener { showCaMenu() }
        // 记录一次触摸事件。
        //
        // 不是为了调试某个已知 bug，而是留一条**现场证据**：ColorOS 这类 ROM 上
        // 偶尔会出现"点了没反应"（触摸被系统层截走），此时这个日志能立刻区分
        // "按钮没收到事件" 和 "收到了但界面没画出来" —— 这两种问题的排查方向完全不同。
        // 只在 ACTION_DOWN 打一行，不会刷屏。
        binding.caButton.setOnTouchListener { _, event ->
            if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                android.util.Log.d("CommandAssistant", "CA 按钮收到 ACTION_DOWN")
            }
            false
        }

        binding.addButton.setOnClickListener {
            val text = binding.commandInput.text?.toString().orEmpty().trim()
            if (text.isEmpty()) {
                // 空输入时"+"当作"从剪贴板插入"，这是最常用的一步操作。
                ClipboardUtil.read(this)?.let { clip ->
                    binding.commandInput.setText(clip)
                    binding.commandInput.setSelection(clip.length)
                }
            } else {
                CommandStore.record(text)
                binding.commandInput.setText("")
            }
        }

        binding.commandInput.setOnEditorActionListener { _, _, _ ->
            val text = binding.commandInput.text?.toString().orEmpty().trim()
            if (text.isNotEmpty()) {
                CommandStore.record(text)
                binding.commandInput.setText("")
            }
            true
        }

        binding.closeButton.setOnClickListener { sendCommand() }

        // 长按「关闭」= 复制（原版把复制单独放在右下角，这里用长按承载，
        // 这样"发送"与"复制"两种意图都能一次到达，不必先清空输入框）。
        binding.closeButton.setOnLongClickListener {
            val text = binding.commandInput.text?.toString().orEmpty().trim()
            if (text.isEmpty()) {
                android.widget.Toast.makeText(
                    this, R.string.toast_empty_command, android.widget.Toast.LENGTH_SHORT
                ).show()
            } else {
                ClipboardUtil.copy(this, text)
            }
            true
        }

        // 输入内容一变就切换下半屏的内容：空着看历史，一开始打字就换成语法帮助。
        // 这是照原版的动态行为做的，见 CommandHelp 的类注释。
        binding.commandInput.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) = Unit
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) = Unit
            override fun afterTextChanged(s: android.text.Editable?) {
                applyInputMode(s?.toString().orEmpty())
            }
        })
    }

    /**
     * 按输入框内容切换界面模式。
     *
     *  * **输入框为空**：下半屏显示历史/收藏列表，右下角按钮是「关闭」（送命令并收起）。
     *  * **输入框有内容**：下半屏换成**自动补全面板** —— 边打边给候选，点一下就补上。
     *
     * 补全的判定逻辑在 [com.xanan.cmdhelper.json.CommandCompletion]（纯函数、有单测），
     * 画界面在 [com.xanan.cmdhelper.ui.CompletionPanel]。
     *
     * 两处刻意保留的差异：
     *  * 有内容时**仍然**允许点「关闭」发送（原版把它换成了复制，想发送得先清空输入框），
     *    这里把"复制"做成**长按**关闭按钮，两种意图都能一次到达；
     *  * 补全面板始终显示，不会因为识别不到命令就变回历史 —— 否则玩家打字到一半
     *    列表突然跳走，很难受。
     */
    private fun applyInputMode(input: String) {
        if (input.isNotBlank()) {
            CompletionPanel.render(binding.helpContainer, binding.commandInput)
            binding.helpPanel.visibility = View.VISIBLE
            binding.commandList.visibility = View.GONE
            binding.emptyHint.visibility = View.GONE
        } else {
            binding.helpPanel.visibility = View.GONE
            refreshList()
        }
    }

    /**
     * 「关闭」按钮：把输入框里的命令**送进游戏**并收起面板。
     *
     * 优先用无障碍服务直接粘贴（体验最好）；无障碍没开时退回到"复制到剪贴板"，
     * 并明确告诉用户需要手动粘贴 —— 悄悄什么都不做是最糟的体验。
     */
    private fun sendCommand() {
        val text = binding.commandInput.text?.toString().orEmpty().trim()
        if (text.isEmpty()) {
            android.widget.Toast.makeText(this, R.string.toast_empty_command, android.widget.Toast.LENGTH_SHORT).show()
            return
        }
        CommandStore.record(text)

        // 用户可以在设置里把「关闭」的行为改成"只复制"（有些玩家更习惯自己粘贴）。
        if (SettingsStore.closeAction.value == SettingsStore.CLOSE_ACTION_COPY) {
            ClipboardUtil.copy(this, text)
            binding.commandInput.setText("")
            hidePanel()
            return
        }

        val service = PasteAccessibilityService.instance
        if (service != null && service.pasteText(text)) {
            android.widget.Toast.makeText(this, R.string.toast_pasted, android.widget.Toast.LENGTH_SHORT).show()
        } else {
            ClipboardUtil.copy(this, text)
            android.widget.Toast.makeText(this, R.string.toast_paste_failed, android.widget.Toast.LENGTH_SHORT).show()
        }

        binding.commandInput.setText("")
        hidePanel()
    }

    /**
     * 让面板窗口可以被拖动。
     *
     * 只改 y 坐标：面板横向始终贴满屏幕（原版观感），上下拖动用来避开游戏里的
     * 虚拟按键、血条等 UI。拖动结束后夹在安全区内，防止被拖出屏幕再也拉不回来。
     */
    private fun attachWindowDrag(handle: View) {
        var downY = 0f
        var startY = 0
        var dragging = false

        handle.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downY = event.rawY
                    startY = manualOffsetY
                    dragging = false
                    false
                }

                MotionEvent.ACTION_MOVE -> {
                    val delta = event.rawY - downY
                    if (!dragging && abs(delta) < dp(6)) return@setOnTouchListener false
                    dragging = true
                    manualOffsetY = (startY + delta.toInt()).coerceAtLeast(0)
                    applyWindowGeometry()
                    true
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (dragging) {
                        clampPanelPosition()
                        true
                    } else {
                        false
                    }
                }

                else -> false
            }
        }
    }

    /** 拖动结束后把面板拉回可见范围，最多允许下移屏幕高度的一半。 */
    private fun clampPanelPosition() {
        val screenHeight = resources.displayMetrics.heightPixels
        manualOffsetY = manualOffsetY.coerceIn(0, screenHeight / 2)
        applyWindowGeometry()
    }

    /** 全屏 / 悬浮窗切换（CA 菜单里的那一项）。 */
    private fun toggleFullscreen() {
        fullscreen = !fullscreen
        binding.panel.fullscreen = fullscreen
        // 形态变了要重算高度；键盘还开着的话也要继续让开它，所以统一走这一个入口。
        applyWindowGeometry()
        // 全屏/悬浮窗的高度基准不同，切完要重新量一次（键盘开着时 capturePanelMetrics
        // 会自己跳过，等键盘收起后的下一次采样）。
        runCatching { binding.root.post { capturePanelMetrics() } }
    }

    // =======================================================================
    // 悬浮球
    // =======================================================================

    private fun attachBubble() {
        if (bubbleView != null) return

        val view = ImageView(this).apply {
            // 悬浮球用**应用图标**（原版命令助手的命令方块图标），不是抽象的青圈 ——
            // 玩家在游戏里一眼要能认出"这是我那个命令助手"。
            //
            // 用 `ic_bubble_round` 而不是 `ic_launcher`：启动图标是**方形** PNG，
            // 直接贴上去会是个方块盖在游戏画面上。圆形版本由
            // `tools/make-bubble-icon.ps1` 从原版图标预先生成，
            // 这样布局里的 bubbleIcon 与这里的悬浮球能共用同一份资源、长得一样。
            setImageResource(R.mipmap.ic_bubble_round)
            contentDescription = getString(R.string.app_name)

            // ⚠️ 悬浮球**常驻**，点击是"开/关面板"的开关 —— 这是站长要的交互：
            // "点击悬浮窗后改成全屏…展开后也显示悬浮窗，再次点击悬浮窗关闭界面"。
            //
            // 所以这里**不能**再 `detachBubble()`：旧实现是"点球 → 收球 → 开面板"，
            // 展开后就没有球了，也就没法再点它关面板。
            //
            // 开关状态以 `panelAttached` 为准。
            //
            // ⚠️ 别用 `binding.root.isAttachedToWindow` 来判断 —— 试过，**在 Service 的
            // 悬浮窗里它一直是 false**（窗口是 WindowManager 直接加的，不挂在 Activity
            // 的 view tree 上）。结果是每次点击都走 `showPanel()`，而那里
            // `if (panelAttached) return` 直接返回，表现为"点球完全没反应"（实测踩过）。
            setOnClickListener {
                if (panelAttached) hidePanel() else showPanel()
            }
        }

        // 悬浮球尺寸：**32dp，与原版命令助手一致**。
        //
        // 这个值是**量出来的**，不是估的：把原版装回设备打开悬浮窗后
        // `adb shell dumpsys window windows` 里能看到
        // ```
        // Window #11 Window{...} PopupWindow@CA.Icon/1  package=com.xero.ca
        // mFrame=[432,804][540,912]      → 108 × 108 px
        // ```
        // 本机密度是 540dpi 覆盖值 = 3.3875，108 / 3.3875 = **31.9dp → 取 32dp**。
        //
        // 历史：56dp（初版，面板占 0.76 屏时的比例）→ 48dp（面板调小后跟着缩）→ 36dp（对齐原版）。
        // 站长两次反馈"太大了"，所以这次直接以原版为准，不再自己估。
        val bubbleSize = dp(BUBBLE_SIZE_DP)
        val params = WindowManager.LayoutParams(
            bubbleSize,
            bubbleSize,
            overlayWindowType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            // 贴右边留一点边距；这个边距要跟着球的大小走，否则小球会离屏幕边太远。
            x = resources.displayMetrics.widthPixels - bubbleSize - dp(8)
            y = resources.displayMetrics.heightPixels / 3
        }

        runCatching { windowManager.addView(view, params) }
            .onSuccess {
                attachBubbleDrag(view, params)
                bubbleView = view
                bubbleParams = params
            }
            .onFailure {
                // ⚠️ 这里原来只是静默失败，导致"点关闭后面板消失、悬浮球也没出现"，
                // 看起来就是"关闭点了没反应"（实际是关闭生效了但入口没了）。
                android.util.Log.e(TAG, "attachBubble addView failed", it)
            }
    }

    /** 悬浮球可自由拖动：游戏里两侧的按钮位置各不相同，固定位置一定有人被挡。 */
    private fun attachBubbleDrag(view: View, params: WindowManager.LayoutParams) {
        var downX = 0f
        var downY = 0f
        var startX = 0
        var startY = 0
        var dragging = false

        view.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.rawX
                    downY = event.rawY
                    startX = params.x
                    startY = params.y
                    dragging = false
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - downX
                    val dy = event.rawY - downY
                    if (!dragging && abs(dx) + abs(dy) < dp(6)) return@setOnTouchListener true
                    dragging = true
                    params.x = (startX + dx.toInt())
                        .coerceIn(0, resources.displayMetrics.widthPixels - dp(BUBBLE_SIZE_DP))
                    params.y = (startY + dy.toInt())
                        .coerceIn(0, resources.displayMetrics.heightPixels - dp(BUBBLE_SIZE_DP))
                    runCatching { windowManager.updateViewLayout(view, params) }
                    true
                }

                MotionEvent.ACTION_UP -> {
                    // 拖动过就不算点击，避免"想挪位置结果把面板叫出来"。
                    if (!dragging) view.performClick()
                    true
                }

                else -> false
            }
        }
    }

    private fun detachBubble() {
        bubbleView?.let { runCatching { windowManager.removeView(it) } }
        bubbleView = null
        bubbleParams = null
    }

    // =======================================================================
    // 数据订阅
    // =======================================================================

    /** 订阅命令库，数据一变就刷新列表；收藏视图只过滤、不改顺序。 */
    private fun observeStore() {
        collectJob?.cancel()
        collectJob = lifecycleScope.launch {
            CommandStore.entries.collect { refreshList() }
        }
    }

    /**
     * 订阅设置：字号与面板高度。
     *
     * 两项都做成"改了立刻生效"，不需要重启面板 —— 设置页是从面板里弹出来的，
     * 让用户改完还要手动重启才能看到效果是很差的体验。
     */
    private fun observeSettings() {
        settingsJob?.cancel()
        settingsJob = lifecycleScope.launch {
            launch {
                SettingsStore.fontScale.collect { scale ->
                    applyFontScale(scale)
                }
            }
            launch {
                SettingsStore.panelRatio.collect { ratio ->
                    binding.panel.heightRatio = ratio
                    // 触发重新测量，面板立刻改变高度。
                    binding.panel.requestLayout()
                    // 比例变了，"自然高度"也变了 —— 重新量一次，
                    // 否则下次弹键盘会拿旧基准算高度，面板高度会跳一下。
                    // （键盘开着时 capturePanelMetrics 自己会跳过。）
                    binding.root.post { capturePanelMetrics() }
                }
            }
        }
    }

    /**
     * 应用字号倍率。
     *
     * 只缩放"内容"文字（列表、输入框、标题），不动 CA / 关闭 这类操作按钮 ——
     * 那些按钮的字号变化会让底栏宽度跳来跳去，反而不好按。
     */
    private fun applyFontScale(scale: Float) {
        val inputSize = 16f * scale
        val headerSize = 15f * scale

        binding.commandInput.textSize = inputSize
        binding.headerTitle.textSize = headerSize
        adapter.fontScale = scale
        adapter.notifyDataSetChanged()
    }

    private fun refreshList() {
        // 输入框里有内容时下半屏归语法帮助管，列表不能抢回来 ——
        // 否则命令库一变（例如刚保存了一条）列表就会盖掉玩家正在看的帮助。
        if (binding.commandInput.text?.isNotBlank() == true) return

        val source = if (showingFavorites) CommandStore.favorites else CommandStore.history
        adapter.submitList(source)
        binding.emptyHint.visibility = if (source.isEmpty()) View.VISIBLE else View.GONE
        binding.commandList.visibility = if (source.isEmpty()) View.GONE else View.VISIBLE
    }

    // =======================================================================
    // CA 菜单
    // =======================================================================

    private fun showCaMenu() {
        CaMenuDialog.show(
            context = this,
            binding = binding,
            onToggleFavoriteView = {
                showingFavorites = !showingFavorites
                binding.headerTitle.text = getString(
                    if (showingFavorites) R.string.panel_favorites else R.string.panel_history
                )
                refreshList()
            },
            onToggleFullscreen = { toggleFullscreen() },
            onTutorial = { CaMenuDialog.showTutorial(this, binding) },
            onSettings = { CaMenuDialog.showSettings(this, binding) },
            onExit = {
                stopSelf()
            }
        )
    }

    /** 长按一条命令弹出的操作菜单。 */
    private fun showEntryMenu(entry: CommandEntry) {
        val items = arrayOf(
            getString(if (entry.favorite) R.string.panel_unfavorite else R.string.panel_favorite),
            getString(R.string.panel_delete),
            getString(R.string.panel_copy)
        )
        CaMenuDialog.showEntryActions(binding, items) { which ->
            when (which) {
                0 -> CommandStore.toggleFavorite(entry.text)
                1 -> CommandStore.remove(entry.text)
                2 -> ClipboardUtil.copy(this, entry.text)
            }
        }
    }

    // =======================================================================
    // 工具
    // =======================================================================

    private fun showOverlayPermissionHint() {
        android.widget.Toast.makeText(
            this,
            R.string.toast_overlay_permission_required,
            android.widget.Toast.LENGTH_LONG
        ).show()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            // 不做自动跳转：用户可能正在游戏里，突然被拉去设置页是很糟的体验。
            // 真正的引导放在 MainActivity 的权限卡片里。
            stopSelf()
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).roundToInt()

    companion object {
        const val ACTION_SHOW_PANEL = "com.xanan.cmdhelper.action.SHOW_PANEL"
        const val ACTION_HIDE_PANEL = "com.xanan.cmdhelper.action.HIDE_PANEL"
        const val ACTION_TOGGLE = "com.xanan.cmdhelper.action.TOGGLE"

        private const val TAG = "CmdHelperPanel"
        private const val CHANNEL_ID = "panel"
        private const val NOTIFICATION_ID = 1001

        /**
         * 悬浮球边长（dp）。
         *
         * 拖动边界与初始位置都用它算，所以必须是常量而不是散落的字面量 ——
         * 之前位置写 `widthPixels - 72`、边界写 `- dp(56)`，改尺寸时很容易漏掉一处。
         */
        private const val BUBBLE_SIZE_DP = 32

        /** 便捷入口：从任何地方拉起面板。 */
        fun start(context: Context, action: String = ACTION_SHOW_PANEL) {
            val intent = Intent(context, PanelService::class.java).setAction(action)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, PanelService::class.java))
        }
    }
}
