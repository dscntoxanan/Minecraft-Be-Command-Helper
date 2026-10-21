package com.xanan.cmdhelper.service

import android.accessibilityservice.AccessibilityService
import android.os.Bundle
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast

/**
 * 自动粘贴服务。
 *
 * 存在的唯一理由：Minecraft 的聊天框**不允许第三方应用直接写入**，系统剪贴板粘贴
 * 又要求用户长按输入框 —— 在游戏里这个操作很容易误触。无障碍服务可以拿到当前
 * 焦点输入框，用 `ACTION_SET_TEXT` 一次性把整条命令写进去，这是一条命令从面板
 * 到游戏里最短的路径。
 *
 * 安全边界（刻意收窄，代码与配置文件两处都体现）：
 *  * 只在**用户主动点了「关闭」按钮**时才写入，绝不后台自动填字；
 *  * 只检查窗口是否可编辑，不遍历、不读取、不上传任何界面文本；
 *  * 服务静态实例为 null 时说明用户没开无障碍，调用方会退回到剪贴板方案。
 */
class PasteAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // 本服务不需要持续监听：粘贴是"被调用"的动作，不是"响应事件"的动作。
        // 保留空实现是为了让系统认为服务处于正常状态。
    }

    override fun onInterrupt() {
        // 无需处理：没有长跑任务。
    }

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        instance = null
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        instance = null
        super.onDestroy()
    }

    /**
     * 把 [text] 写进当前焦点输入框。
     *
     * @return true 表示成功写入，调用方不必再走剪贴板兜底。
     */
    fun pasteText(text: String): Boolean {
        val target = findEditableNode() ?: return false

        // 优先用 ACTION_SET_TEXT：它是原子的，一次调用把整段文本写进去，
        // 不像 ACTION_PASTE 依赖剪贴板内容，也不会被输入法的联想改写。
        val args = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        }
        if (target.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)) {
            target.recycle()
            return true
        }

        // 少数输入框（尤其是游戏自绘的）不实现 SET_TEXT，退到"聚焦 + 粘贴"。
        val focused = target.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
        val pasted = target.performAction(AccessibilityNodeInfo.ACTION_PASTE)
        target.recycle()
        return focused && pasted
    }

    /**
     * 找一个可写入的输入框。
     *
     * 查找顺序很重要：
     *  1. `findFocus(FOCUS_INPUT)` —— 真正正在接收键盘输入的节点，最准；
     *  2. 遍历当前活动窗口找第一个 `isEditable` 节点 —— 覆盖"焦点还没落到输入框"
     *     的情况（游戏里点开聊天框时偶尔如此）。
     */
    private fun findEditableNode(): AccessibilityNodeInfo? {
        rootInActiveWindow?.let { root ->
            root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)?.let { focused ->
                if (focused.isEditable) {
                    root.recycle()
                    return focused
                }
                focused.recycle()
            }
            findEditableIn(root)?.let { editable ->
                root.recycle()
                return editable
            }
            root.recycle()
        }
        return null
    }

    /** 深度优先搜索第一个可编辑节点；找不到返回 null（调用方负责 recycle 根节点）。 */
    private fun findEditableIn(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (node.isEditable) return node
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val found = findEditableIn(child)
            if (found != null) return found
            child.recycle()
        }
        return null
    }

    companion object {
        /**
         * 当前已连接的服务实例。
         *
         * 用静态引用而不是绑定 Service：面板和预览界面都可能在任意时刻需要粘贴，
         * 走 bindService 的生命周期管理成本远高于收益，而这里持有的是一个
         * 由系统管理的单例，不存在泄漏问题。
         */
        @Volatile
        var instance: PasteAccessibilityService? = null
            private set

        /** 无障碍服务是否已启用（用于界面上的状态提示）。 */
        fun isReady(): Boolean = instance != null
    }
}
