package com.jakliuyuy.batterypower.xposed

import android.content.Context
import android.net.Uri
import com.jakliuyuy.batterypower.core.config.AppConfig
import com.jakliuyuy.batterypower.core.config.ConfigCodec
import com.jakliuyuy.batterypower.core.config.ConfigContract
import com.jakliuyuy.batterypower.core.config.SystemUiConfigCache
import com.jakliuyuy.batterypower.core.log.BLog

/**
 * Reads configuration from the app's ContentProvider and keeps a local cache
 * inside SystemUI (spec sections 40-45, 136, 150).
 *
 * Rules:
 *  - every query happens on a worker thread, never on the SystemUI main thread
 *  - if the provider is unavailable the last known configuration stays in use
 *  - the whole snapshot is replaced atomically (no half-updated state)
 */
class ConfigBridge(private val context: Context) {

    private val authorities = listOf(
        ConfigContract.authorityFor("com.jakliuyuy.batterypower")
    )

    private val cache = SystemUiConfigCache(context)

    @Volatile
    var config: AppConfig = cache.load() ?: AppConfig.defaults()
        private set

    @Volatile
    var providerAvailable: Boolean = false
        private set

    @Volatile
    var lastSyncMs: Long = 0L
        private set

    @Volatile
    var lastError: String? = null
        private set

    private var cachedVersion = config.configVersion

    /** Returns true when the configuration changed and views must be updated. */
    fun poll(): Boolean {
        for (authority in authorities) {
            val version = queryVersion(authority)
            if (version == null) {
                providerAvailable = false
                return false
            }
            providerAvailable = true
            if (version == cachedVersion) return false
            val map = queryConfig(authority)
            if (map.isNullOrEmpty()) return false
            val parsed = try {
                ConfigCodec.fromMap(map)
            } catch (t: Throwable) {
                BLog.throttledError("Config", "bridge-parse", "config parse failed")
                return false
            }
            config = parsed
            cachedVersion = parsed.configVersion
            lastSyncMs = System.currentTimeMillis()
            cache.save(map)
            BLog.d("Config", "SystemUI config synced v${parsed.configVersion}")
            return true
        }
        return false
    }

    private fun queryVersion(authority: String): Int? {
        return try {
            val uri: Uri = ConfigContract.buildVersionUri(authority)
            val cursor = context.contentResolver.query(uri, null, null, null, null)
                ?: return null
            val value = try {
                if (cursor.moveToFirst()) {
                    val index = cursor.getColumnIndex(ConfigContract.COL_VERSION)
                    if (index >= 0) cursor.getInt(index) else null
                } else null
            } finally {
                try {
                    cursor.close()
                } catch (ignored: Throwable) {
                }
            }
            lastError = null
            value
        } catch (t: Throwable) {
            lastError = t.message
            BLog.throttledError("Config", "bridge-version", "version query failed")
            null
        }
    }

    private fun queryConfig(authority: String): Map<String, String>? {
        return try {
            val uri: Uri = ConfigContract.buildConfigUri(authority)
            val cursor = context.contentResolver.query(uri, null, null, null, null)
                ?: return null
            val map = LinkedHashMap<String, String>()
            try {
                val keyIndex = cursor.getColumnIndex(ConfigContract.COL_KEY)
                val valueIndex = cursor.getColumnIndex(ConfigContract.COL_VALUE)
                if (keyIndex < 0 || valueIndex < 0) return null
                while (cursor.moveToNext()) {
                    map[cursor.getString(keyIndex)] = cursor.getString(valueIndex) ?: ""
                }
            } finally {
                try {
                    cursor.close()
                } catch (ignored: Throwable) {
                }
            }
            map
        } catch (t: Throwable) {
            lastError = t.message
            BLog.throttledError("Config", "bridge-config", "config query failed")
            null
        }
    }
}
