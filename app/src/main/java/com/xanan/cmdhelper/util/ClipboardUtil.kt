package com.xanan.cmdhelper.util

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast

/**
 * 剪贴板与提示的小工具。
 *
 * 单独抽出来是因为「复制命令」这个动作在悬浮面板、全屏界面、无障碍粘贴三处都要用，
 * 各写一遍容易出现有的带 `/`、有的不带这种不一致。
 */
object ClipboardUtil {

    /**
     * 复制文本到系统剪贴板并给出提示。
     *
     * @param label 提示里显示的内容，过长时会被截断，避免 Toast 铺满屏幕。
     */
    fun copy(context: Context, text: String, label: String? = null) {
        val manager = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            ?: return
        manager.setPrimaryClip(ClipData.newPlainText("命令助手", text))
        val shown = label ?: text
        val short = if (shown.length > 40) shown.take(40) + "…" else shown
        Toast.makeText(context, "已复制：$short", Toast.LENGTH_SHORT).show()
    }

    /** 读取剪贴板文本，为空时返回 null。 */
    fun read(context: Context): String? {
        val manager = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            ?: return null
        val clip = manager.primaryClip ?: return null
        if (clip.itemCount == 0) return null
        return clip.getItemAt(0).coerceToText(context)?.toString()?.takeIf { it.isNotBlank() }
    }
}
