package com.jakliuyuy.batterypower.provider

import android.content.ContentProvider
import android.content.ContentValues
import android.content.UriMatcher
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import com.jakliuyuy.batterypower.data.ConfigRepository

/**
 * 跨进程配置通道。
 *
 * 避坑 1：不要用 XSharedPreferences。Android 11+ 起主 App 的 SP 文件是 0600，
 * SystemUI 进程读不到，症状是"配置改了没反应，全是默认值"。
 * 改为 exported ContentProvider，query 直接返回 [key, value] 两列 MatrixCursor。
 *
 * 该 Provider 无 grantUri 需求，任何进程都可 query（配置不含敏感信息）。
 */
class ConfigProvider : ContentProvider() {

    private lateinit var repo: ConfigRepository

    override fun onCreate(): Boolean {
        val ctx = context ?: return false
        repo = ConfigRepository(ctx)
        return true
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor {
        val cursor = MatrixCursor(COLUMNS)
        try {
            when (URI_MATCHER.match(uri)) {
                CODE_ALL -> {
                    // 全量拉取：一次性返回所有配置项
                    repo.get().toMap().forEach { (k, v) ->
                        cursor.addRow(arrayOf(k, v))
                    }
                }
                CODE_ONE -> {
                    // 单项：content://.../config/<key>
                    val key = uri.lastPathSegment
                    val value = repo.get().toMap()[key]
                    if (key != null && value != null) {
                        cursor.addRow(arrayOf(key, value))
                    }
                }
                else -> { /* 未知 URI，返回空游标 */ }
            }
        } catch (e: Exception) {
            android.util.Log.w(TAG, "query 失败: ${e.message}")
        }
        return cursor
    }

    override fun getType(uri: Uri): String = "vnd.android.cursor.dir/vnd.com.jakliuyuy.batterypower.config"

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(
        uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?
    ): Int = 0

    companion object {
        private const val TAG = "BatteryPower/ConfigProvider"

        const val AUTHORITY = "com.jakliuyuy.batterypower.config"
        const val PATH = "config"

        val CONTENT_URI: Uri = Uri.parse("content://$AUTHORITY/$PATH")

        val COLUMNS = arrayOf("key", "value")

        private const val CODE_ALL = 1
        private const val CODE_ONE = 2

        private val URI_MATCHER = UriMatcher(UriMatcher.NO_MATCH).apply {
            addURI(AUTHORITY, PATH, CODE_ALL)
            addURI(AUTHORITY, "$PATH/*", CODE_ONE)
        }
    }
}
