package com.jakliuyuy.batterypower.app

import android.content.ContentProvider
import android.content.ContentValues
import android.content.UriMatcher
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import com.jakliuyuy.batterypower.core.config.ConfigContract
import com.jakliuyuy.batterypower.core.config.ConfigStore
import com.jakliuyuy.batterypower.core.log.BLog

/**
 * Read-only configuration provider consumed by SystemUI (spec sections 40, 41,
 * 44, 45, 136.1).
 *
 * Hard rules:
 *  - exported, but query-only: insert/update/delete are rejected
 *  - no su, no sysfs, no network, no long-running work inside query()
 *  - returns a flat key/value snapshot plus the monotonic config version
 */
class ConfigProvider : ContentProvider() {

    private val matcher = UriMatcher(UriMatcher.NO_MATCH).apply {
        addURI("*", ConfigContract.PATH_VERSION, CODE_VERSION)
        addURI("*", ConfigContract.PATH_CONFIG, CODE_CONFIG)
    }

    override fun onCreate(): Boolean {
        return try {
            context?.let { ConfigStore.get(it) }
            true
        } catch (t: Throwable) {
            BLog.e("Config", "provider init failed", t)
            false
        }
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor? {
        return try {
            when (matcher.match(uri)) {
                CODE_VERSION -> buildVersionCursor()
                CODE_CONFIG -> buildConfigCursor()
                else -> null
            }
        } catch (t: Throwable) {
            // Never let an exception escape into SystemUI.
            BLog.throttledError("Config", "provider-query", "query failed", t)
            null
        }
    }

    private fun buildVersionCursor(): Cursor {
        val store = context?.let { ConfigStore.get(it) }
        val version = store?.configVersion() ?: 0
        val cursor = MatrixCursor(arrayOf(ConfigContract.COL_VERSION, ConfigContract.COL_UPDATED_AT))
        cursor.addRow(arrayOf(version, System.currentTimeMillis()))
        return cursor
    }

    private fun buildConfigCursor(): Cursor {
        val store = context?.let { ConfigStore.get(it) }
        val map = store?.get()?.let { com.jakliuyuy.batterypower.core.config.ConfigCodec.toMap(it) }
            ?: emptyMap()
        val version = store?.configVersion() ?: 0
        val updatedAt = System.currentTimeMillis()
        val cursor = MatrixCursor(
            arrayOf(
                ConfigContract.COL_KEY,
                ConfigContract.COL_VALUE,
                ConfigContract.COL_TYPE,
                ConfigContract.COL_VERSION,
                ConfigContract.COL_UPDATED_AT
            )
        )
        for ((key, value) in map) {
            cursor.addRow(arrayOf(key, value, typeOf(value), version, updatedAt))
        }
        return cursor
    }

    private fun typeOf(value: String): String = when {
        value.equals("true", ignoreCase = true) || value.equals("false", ignoreCase = true) -> "boolean"
        value.toIntOrNull() != null -> "int"
        value.toLongOrNull() != null -> "long"
        value.toFloatOrNull() != null -> "float"
        else -> "string"
    }

    override fun getType(uri: Uri): String = when (matcher.match(uri)) {
        CODE_VERSION -> "vnd.android.cursor.item/vnd.batterypower.config"
        CODE_CONFIG -> "vnd.android.cursor.dir/vnd.batterypower.config"
        else -> "vnd.android.cursor.item/vnd.batterypower.unknown"
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? {
        BLog.w("Config", "rejected insert on read-only provider")
        return null
    }

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int {
        BLog.w("Config", "rejected delete on read-only provider")
        return 0
    }

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?
    ): Int {
        BLog.w("Config", "rejected update on read-only provider")
        return 0
    }

    companion object {
        private const val CODE_VERSION = 1
        private const val CODE_CONFIG = 2
    }
}
