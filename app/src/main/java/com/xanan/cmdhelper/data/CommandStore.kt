package com.xanan.cmdhelper.data

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 命令库的内存 + 持久化仓库。
 *
 * 设计取舍：
 *  * 命令条目数量是"个人使用量级"（几百条），一次性读进内存完全够用，
 *    因此不做数据库，直接用 SharedPreferences 存一段 JSON，简单、无迁移负担。
 *  * 对外只暴露 StateFlow，界面订阅它即可自动刷新 —— 悬浮面板和全屏界面
 *    同时打开时不会出现两边数据不一致。
 *  * 所有写操作都"先改内存、再落盘、最后通知订阅者"，保证 UI 无卡顿。
 */
object CommandStore {

    private const val PREFS_NAME = "command_store"
    private const val KEY_ENTRIES = "entries"
    private const val KEY_SEEDED = "seeded"

    /** 历史条数上限：超出后丢弃最久未使用的，避免无限膨胀。 */
    private const val MAX_HISTORY = 500

    private lateinit var prefs: android.content.SharedPreferences

    private val _entries = MutableStateFlow<List<CommandEntry>>(emptyList())

    /** 全部条目（历史 + 收藏），按使用时间倒序。 */
    val entries: StateFlow<List<CommandEntry>> = _entries.asStateFlow()

    /** 只含收藏的视图，顺序与 [entries] 一致。 */
    val favorites: List<CommandEntry> get() = _entries.value.filter { it.favorite }

    /** 只含历史的视图（历史面板显示全部条目，收藏过的也保留，与原版一致）。 */
    val history: List<CommandEntry> get() = _entries.value

    /**
     * 初始化：读取持久化数据。重复调用是安全的（幂等）。
     *
     * @param context 任意 Context，内部只取 applicationContext。
     */
    fun init(context: Context) {
        if (::prefs.isInitialized) return
        prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val loaded = runCatching {
            val raw = prefs.getString(KEY_ENTRIES, null) ?: return@runCatching emptyList()
            CommandEntry.listFromJson(org.json.JSONArray(raw))
        }.getOrDefault(emptyList())

        _entries.value = loaded.sortedByDescending { it.sortKey() }

        // 首次启动时给几条示例，让用户一进来就能看懂这个 App 是干什么的
        // （原版也是这么做的：历史里预置了 /say 的引导语）。
        if (!prefs.getBoolean(KEY_SEEDED, false)) {
            prefs.edit { putBoolean(KEY_SEEDED, true) }
            if (loaded.isEmpty()) seedSamples()
        }
    }

    /**
     * 记录一条命令。已存在同样的文本时不重复插入，只更新时间与次数，
     * 并把它顶到历史最前面 —— 这是"常用命令自然上浮"的行为。
     */
    fun record(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return

        val list = _entries.value.toMutableList()
        val index = list.indexOfFirst { it.text == trimmed }
        if (index >= 0) {
            val old = list[index]
            list[index] = old.copy(
                usedAt = System.currentTimeMillis(),
                useCount = old.useCount + 1
            )
        } else {
            list.add(0, CommandEntry(text = trimmed))
        }
        commit(list)
    }

    /** 切换收藏状态。 */
    fun toggleFavorite(text: String) {
        val list = _entries.value.toMutableList()
        val index = list.indexOfFirst { it.text == text }
        if (index < 0) return
        list[index] = list[index].copy(favorite = !list[index].favorite)
        commit(list)
    }

    /** 显式设置收藏状态，供拖拽落点这类"幂等"操作使用。 */
    fun setFavorite(text: String, favorite: Boolean) {
        val list = _entries.value.toMutableList()
        val index = list.indexOfFirst { it.text == text }
        if (index < 0 || list[index].favorite == favorite) return
        list[index] = list[index].copy(favorite = favorite)
        commit(list)
    }

    /** 删除一条命令。 */
    fun remove(text: String) {
        val list = _entries.value.filterNot { it.text == text }
        if (list.size == _entries.value.size) return
        commit(list)
    }

    /** 清空历史（保留收藏，收藏是用户明确标记过的数据）。 */
    fun clearHistory() {
        commit(_entries.value.filter { it.favorite })
    }

    /** 导入一个命令库：同文本条目合并，收藏状态取并集。 */
    fun importLibrary(library: CommandLibrary): Int {
        val list = _entries.value.toMutableList()
        var added = 0
        library.entries.forEach { incoming ->
            val index = list.indexOfFirst { it.text == incoming.text }
            if (index >= 0) {
                val old = list[index]
                list[index] = old.copy(favorite = old.favorite || incoming.favorite)
            } else {
                list.add(incoming)
                added++
            }
        }
        commit(list)
        return added
    }

    /** 导出当前全部条目为一个命令库。 */
    fun exportLibrary(name: String): CommandLibrary = CommandLibrary(name, _entries.value)

    /** 把内存数据裁剪、排序、落盘，并推给订阅者。 */
    private fun commit(list: List<CommandEntry>) {
        val trimmed = list
            .sortedByDescending { it.sortKey() }
            .let { if (it.size > MAX_HISTORY) it.take(MAX_HISTORY) else it }
        _entries.value = trimmed
        persist(trimmed)
    }

    private fun persist(list: List<CommandEntry>) {
        if (!::prefs.isInitialized) return
        prefs.edit {
            putString(KEY_ENTRIES, CommandEntry.listToJson(list).toString())
        }
    }

    /**
     * 首次启动时的引导条目。
     *
     * ⚠️ **只放这一条**。原来这里还塞了 `/titleraw …` 与 `/give …` 两条当"示例"，
     * 但那两条会一直占着历史列表，看起来像 App 自己带的垃圾数据 ——
     * 玩家反馈过「为啥还自带三条命令」。原版也只有这一条 `/say` 引导语，
     * 这里对齐原版，不再塞第二条。
     *
     * 这条本身也不是"示例命令"，而是**操作说明**：它告诉玩家左边是历史、
     * 右边是收藏、可以拖动、可以长按管理。删掉玩家就没人告诉他这些了。
     */
    private fun seedSamples() {
        val samples = listOf(
            "/say 你好，我是命令助手！左边是历史，右边是收藏，可以拖来拖去，也可以长按管理。",
        )
        commit(samples.mapIndexed { index, text -> CommandEntry(text = text, createdAt = System.currentTimeMillis() - index) })
    }
}
