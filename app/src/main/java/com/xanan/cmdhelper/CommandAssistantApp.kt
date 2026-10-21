package com.xanan.cmdhelper

import android.app.Application
import android.os.Build
import android.util.Log
import com.xanan.cmdhelper.data.CommandStore
import com.xanan.cmdhelper.data.SettingsStore
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 应用入口。
 *
 * 这里做两件"必须尽早"的事：
 *  1. 把命令库读进内存 —— 悬浮面板由 Service 拉起，界面可能在 Activity 还没创建时
 *     就要渲染列表，所以数据初始化放在 Application 上。
 *  2. 装一个全局未捕获异常处理器 —— 把崩溃栈**落盘**。
 *
 * 第 2 点不是可有可无的：本应用的重灾区是"悬浮窗里弹窗"这类问题，一旦踩中
 * BadTokenException，异常在主线程抛出、系统直接把进程杀掉。实测在部分 ROM 上
 * logcat 里**什么都留不下**（进程被判定为"不稳定应用"后日志被清），排查只能靠猜。
 * 有了这个处理器，崩溃栈会落到应用私有目录，随时可捞。
 */
class CommandAssistantApp : Application() {

    override fun onCreate() {
        super.onCreate()

        // 用 applicationContext，避免 Store 意外持有 Activity 引用造成泄漏。
        CommandStore.init(this)
        SettingsStore.init(this)

        installCrashLogger()
    }

    /**
     * 安装崩溃记录器。
     *
     * 刻意**不**吞掉异常：记录完仍然交给系统默认处理器（进程照常结束），
     * 否则会掩盖问题、让"应用看起来没崩但功能不对"这种更难查的故障出现。
     */
    private fun installCrashLogger() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching { writeCrashLog(thread, throwable) }
            previous?.uncaughtException(thread, throwable)
        }
    }

    /** 把崩溃信息追加到 filesDir/crash.log，方便真机复现后取证。 */
    private fun writeCrashLog(thread: Thread, throwable: Throwable) {
        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
        val trace = StringWriter().also { throwable.printStackTrace(PrintWriter(it)) }.toString()

        val entry = buildString {
            appendLine("===== $timestamp =====")
            appendLine("thread: ${thread.name}")
            appendLine("device: ${Build.MANUFACTURER} ${Build.MODEL}, API ${Build.VERSION.SDK_INT}")
            appendLine(trace)
        }

        File(filesDir, CRASH_LOG).appendText(entry)
        Log.e(TAG, "崩溃已记录到 $CRASH_LOG", throwable)
    }

    companion object {
        private const val TAG = "CommandAssistant"
        const val CRASH_LOG = "crash.log"
    }
}
