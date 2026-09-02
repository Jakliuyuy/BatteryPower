package com.jakliuyuy.batterypower.core.config

import android.content.Context
import com.jakliuyuy.batterypower.core.log.BLog

/**
 * Local configuration cache owned by the SystemUI process (spec sections 43,
 * 87, 150).
 *
 * Purpose: if the main app is killed or the provider is temporarily unreachable,
 * the status bar must keep the last known configuration instead of reverting to
 * defaults.
 */
class SystemUiConfigCache(context: Context) {

    private val prefs = try {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    } catch (t: Throwable) {
        BLog.w("Config", "SystemUI cache unavailable: ${t.message}")
        null
    }

    fun load(): AppConfig? {
        return try {
            val all = prefs?.all
            if (all.isNullOrEmpty()) return null
            val map = HashMap<String, String>()
            for ((key, value) in all) {
                if (value == null) continue
                map[key] = value.toString()
            }
            ConfigCodec.fromMap(map)
        } catch (t: Throwable) {
            BLog.w("Config", "failed to read SystemUI cache: ${t.message}")
            null
        }
    }

    fun save(map: Map<String, String>) {
        try {
            val editor = prefs?.edit() ?: return
            for ((key, value) in map) {
                when {
                    value.toIntOrNull() != null -> editor.putInt(key, value.toInt())
                    value.toLongOrNull() != null -> editor.putLong(key, value.toLong())
                    value.toFloatOrNull() != null -> editor.putFloat(key, value.toFloat())
                    value.equals("true", ignoreCase = true) ||
                        value.equals("false", ignoreCase = true) ->
                        editor.putBoolean(key, value.toBoolean())
                    else -> editor.putString(key, value)
                }
            }
            editor.apply()
        } catch (t: Throwable) {
            BLog.w("Config", "failed to write SystemUI cache: ${t.message}")
        }
    }

    fun save(config: AppConfig) = save(ConfigCodec.toMap(config))

    fun clear() {
        try {
            prefs?.edit()?.clear()?.apply()
        } catch (t: Throwable) {
            BLog.w("Config", "failed to clear SystemUI cache")
        }
    }

    companion object {
        const val PREFS_NAME = "battery_power_systemui_cache"
    }
}
