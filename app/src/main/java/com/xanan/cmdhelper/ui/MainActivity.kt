package com.xanan.cmdhelper.ui

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.xanan.cmdhelper.R
import com.xanan.cmdhelper.data.CommandStore
import com.xanan.cmdhelper.databinding.ActivityMainBinding
import com.xanan.cmdhelper.service.PanelService
import com.xanan.cmdhelper.service.PasteAccessibilityService
import kotlinx.coroutines.launch

/**
 * 主界面（设置与引导页）。
 *
 * 这个 App 的"主界面"其实不是命令面板 —— 面板是悬浮窗，不归 Activity 管。
 * Activity 负责的是**把用户送进面板所需的三件事**：
 *
 *  1. 悬浮窗权限（Android 6+ 的 `SYSTEM_ALERT_WINDOW` 是特殊权限，必须跳设置页手动开）；
 *  2. 通知权限（Android 13+，前台服务常驻通知需要）；
 *  3. 无障碍服务（可选，开了才能一键粘进游戏）。
 *
 * 外加一个"立即启动面板"的按钮和历史统计。这样设计的好处是：权限没给全时，
 * 用户在这里能看到**缺什么、为什么缺**，而不是点一下按钮毫无反应。
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    /** 悬浮窗权限结果回调：用户从设置页回来后立刻刷新状态。 */
    private val overlayPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        refreshPermissionState()
    }

    /** 通知权限请求（Android 13+）。 */
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) {
            Toast.makeText(this, R.string.hint_notification_denied, Toast.LENGTH_LONG).show()
        }
        refreshPermissionState()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupActions()
        observeStats()
        requestNotificationPermissionIfNeeded()
    }

    override fun onResume() {
        super.onResume()
        // 从系统设置页回到本页时，权限状态可能已经变了。
        refreshPermissionState()
    }

    private fun setupActions() {
        binding.overlayPermissionCard.setOnClickListener { requestOverlayPermission() }
        binding.accessibilityCard.setOnClickListener { openAccessibilitySettings() }

        binding.startPanelButton.setOnClickListener {
            if (canDrawOverlays()) {
                PanelService.start(this, PanelService.ACTION_SHOW_PANEL)
                // 面板已经浮在屏幕上了，把 Activity 退到后台，别挡着游戏。
                moveTaskToBack(true)
            } else {
                Toast.makeText(this, R.string.toast_overlay_permission_required, Toast.LENGTH_LONG).show()
                requestOverlayPermission()
            }
        }

        binding.stopPanelButton.setOnClickListener {
            PanelService.stop(this)
            Toast.makeText(this, R.string.hint_panel_stopped, Toast.LENGTH_SHORT).show()
        }
    }

    /** 历史条数与收藏条数实时更新。 */
    private fun observeStats() {
        lifecycleScope.launch {
            CommandStore.entries.collect { list ->
                val favorites = list.count { it.favorite }
                binding.statsText.text = getString(R.string.stats_format, list.size, favorites)
            }
        }
    }

    /**
     * 请求悬浮窗权限。
     *
     * 注意不能用 `requestPermissions` —— `SYSTEM_ALERT_WINDOW` 属于"特殊权限"，
     * 只能通过 `ACTION_MANAGE_OVERLAY_PERMISSION` 跳到系统设置页由用户手动开启。
     */
    private fun requestOverlayPermission() {
        if (canDrawOverlays()) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            runCatching { overlayPermissionLauncher.launch(intent) }
                .onFailure {
                    // 个别定制 ROM 没有这个 Activity，退回应用详情页。
                    runCatching {
                        startActivity(
                            Intent(
                                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                Uri.parse("package:$packageName")
                            )
                        )
                    }
                }
        }
    }

    private fun canDrawOverlays(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(this)

    private fun openAccessibilitySettings() {
        runCatching {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }.onFailure {
            Toast.makeText(this, R.string.hint_open_accessibility_failed, Toast.LENGTH_LONG).show()
        }
    }

    /**
     * Android 13+ 需要显式申请通知权限。
     *
     * 不给也不影响面板工作（前台服务仍会启动），但用户就看不到那条常驻通知，
     * 在部分 ROM 上会让服务更容易被清理，所以要主动问一次。
     */
    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED
        if (!granted) {
            notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    /** 把三项权限/服务的当前状态画到界面上。 */
    private fun refreshPermissionState() {
        val overlayOk = canDrawOverlays()
        val accessibilityOk = PasteAccessibilityService.isReady()
        val notificationOk = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
        } else {
            true
        }

        binding.overlayPermissionState.text = getString(
            if (overlayOk) R.string.state_granted else R.string.state_required
        )
        binding.overlayPermissionState.setTextColor(
            color(if (overlayOk) R.color.brand_teal else R.color.text_secondary)
        )

        binding.accessibilityState.text = getString(
            if (accessibilityOk) R.string.state_enabled else R.string.state_optional
        )
        binding.accessibilityState.setTextColor(
            color(if (accessibilityOk) R.color.brand_teal else R.color.text_secondary)
        )

        binding.notificationState.text = getString(
            if (notificationOk) R.string.state_granted else R.string.state_recommended
        )
        binding.notificationState.setTextColor(
            color(if (notificationOk) R.color.brand_teal else R.color.text_secondary)
        )

        // 悬浮窗权限是硬性前提：没有它按钮直接置灰，避免用户点了没反应。
        binding.startPanelButton.isEnabled = overlayOk
    }

    @Suppress("DEPRECATION")
    private fun color(resId: Int): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            getColor(resId)
        } else {
            resources.getColor(resId)
        }
}
