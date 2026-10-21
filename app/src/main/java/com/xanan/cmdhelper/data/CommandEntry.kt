package com.xanan.cmdhelper.data

import org.json.JSONArray
import org.json.JSONObject

/**
 * 一条命令记录。
 *
 * 一个条目同时可以被"历史"和"收藏"两个面板看到，所以收藏与否是条目自身的属性，
 * 而不是两个各自独立的列表 —— 这样从历史拖到收藏不会复制出两份数据。
 *
 * @param text     命令原文（可能带也可能不带开头的 `/`，原版两种都会出现在历史里）
 * @param favorite 是否已收藏
 * @param createdAt 首次创建时间戳（毫秒），用于按时间倒序排列历史
 * @param usedAt   最近一次被使用的时间戳（毫秒），点击条目时刷新
 * @param useCount 被使用次数，后续可以按"常用度"排序
 */
data class CommandEntry(
    val text: String,
    val favorite: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val usedAt: Long = createdAt,
    val useCount: Int = 0
) {

    /** 历史列表要求最新的在最上面。 */
    fun sortKey(): Long = maxOf(createdAt, usedAt)

    fun toJson(): JSONObject = JSONObject().apply {
        put(KEY_TEXT, text)
        put(KEY_FAVORITE, favorite)
        put(KEY_CREATED_AT, createdAt)
        put(KEY_USED_AT, usedAt)
        put(KEY_USE_COUNT, useCount)
    }

    companion object {
        private const val KEY_TEXT = "text"
        private const val KEY_FAVORITE = "favorite"
        private const val KEY_CREATED_AT = "createdAt"
        private const val KEY_USED_AT = "usedAt"
        private const val KEY_USE_COUNT = "useCount"

        /** 从 JSON 还原；字段缺失或类型不对时返回 null，由调用方跳过这条脏数据。 */
        fun fromJson(json: JSONObject): CommandEntry? {
            val text = json.optString(KEY_TEXT).takeIf { it.isNotBlank() } ?: return null
            val created = json.optLong(KEY_CREATED_AT, System.currentTimeMillis())
            return CommandEntry(
                text = text,
                favorite = json.optBoolean(KEY_FAVORITE, false),
                createdAt = created,
                usedAt = json.optLong(KEY_USED_AT, created),
                useCount = json.optInt(KEY_USE_COUNT, 0)
            )
        }

        fun listToJson(list: List<CommandEntry>): JSONArray {
            val array = JSONArray()
            list.forEach { array.put(it.toJson()) }
            return array
        }

        fun listFromJson(array: JSONArray?): List<CommandEntry> {
            if (array == null) return emptyList()
            val result = ArrayList<CommandEntry>(array.length())
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                fromJson(item)?.let(result::add)
            }
            return result
        }
    }
}

/**
 * 命令库（导出/导入用的容器）。
 *
 * 原版的 `.json` 命令库导入功能就对应这个结构。这里刻意加了一个 version 字段：
 * 将来若要改结构，可以据此做迁移，而不是让老文件直接读不出来。
 */
data class CommandLibrary(
    val name: String,
    val entries: List<CommandEntry>,
    val version: Int = CURRENT_VERSION
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put(KEY_NAME, name)
        put(KEY_VERSION, version)
        put(KEY_ENTRIES, CommandEntry.listToJson(entries))
    }

    companion object {
        const val CURRENT_VERSION = 1
        private const val KEY_NAME = "name"
        private const val KEY_VERSION = "version"
        private const val KEY_ENTRIES = "entries"

        fun fromJson(json: JSONObject): CommandLibrary = CommandLibrary(
            name = json.optString(KEY_NAME, "未命名命令库"),
            entries = CommandEntry.listFromJson(json.optJSONArray(KEY_ENTRIES)),
            version = json.optInt(KEY_VERSION, CURRENT_VERSION)
        )
    }
}
