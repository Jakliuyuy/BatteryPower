package com.jakliuyuy.batterypower.core.config

import android.content.Context
import android.content.SharedPreferences
import com.jakliuyuy.batterypower.core.log.BLog
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Authoritative configuration store for the app process (spec sections 34, 43,
 * 109, 143.1).
 *
 * Persistence rules:
 *  - ordinary settings use apply() (async, batched)
 *  - the final overlay drag position uses commit() (synchronous, must survive a kill)
 *  - every mutation bumps configVersion exactly once so SystemUI detects it
 */
class ConfigStore private constructor(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val listeners = CopyOnWriteArrayList<(AppConfig) -> Unit>()

    @Volatile
    private var current: AppConfig = loadFromPrefs()

    init {
        BLog.i("Config", "loaded config v${current.configVersion} schema ${current.schemaVersion}")
    }

    fun get(): AppConfig = current

    fun configVersion(): Int = current.configVersion

    /** Applies a mutation atomically and persists it. */
    fun update(mutator: (AppConfig) -> AppConfig): AppConfig {
        return updateInternal(mutator, sync = false)
    }

    /**
     * Synchronous write for high-priority data such as the final drag position
     * (spec 18, 143.1: never persist the position with apply() only).
     */
    fun updateSync(mutator: (AppConfig) -> AppConfig): AppConfig {
        return updateInternal(mutator, sync = true)
    }

    private fun updateInternal(mutator: (AppConfig) -> AppConfig, sync: Boolean): AppConfig {
        var changed = false
        val next: AppConfig = synchronized(this) {
            val base = current
            val updated = ConfigMigration.enforceInvariants(mutator(base))
            if (updated == base) {
                base
            } else {
                val bumped = updated.copy(configVersion = base.configVersion + 1)
                current = bumped
                writeToPrefs(bumped, sync)
                changed = true
                bumped
            }
        }
        if (changed) notifyListeners(next)
        return next
    }

    /** Saves the final drag position with a synchronous commit. */
    fun commitPosition(x: Int, y: Int) {
        synchronized(this) {
            val base = current
            val next = base.copy(
                overlay = base.overlay.copy(x = x, y = y),
                configVersion = base.configVersion + 1
            )
            current = next
            val editor = prefs.edit()
                .putInt(ConfigCodec.K_OV_X, x)
                .putInt(ConfigCodec.K_OV_Y, y)
                .putInt(ConfigCodec.K_CONFIG_VERSION, next.configVersion)
                .putLong(ConfigCodec.K_UPDATED_AT, System.currentTimeMillis())
            editor.commit()
            BLog.d("Config", "position committed ($x,$y) v${next.configVersion}")
        }
        notifyListeners(current)
    }

    fun reset() {
        synchronized(this) {
            val fresh = AppConfig.defaults().copy(
                configVersion = current.configVersion + 1,
                onboarded = true
            )
            current = fresh
            val editor = prefs.edit().clear()
            val map = ConfigCodec.toMap(fresh)
            for ((key, value) in map) {
                putValue(editor, key, value)
            }
            editor.commit()
        }
        BLog.i("Config", "configuration reset to defaults")
        notifyListeners(current)
    }

    fun addListener(listener: (AppConfig) -> Unit) {
        listeners.add(listener)
    }

    fun removeListener(listener: (AppConfig) -> Unit) {
        listeners.remove(listener)
    }

    private fun notifyListeners(config: AppConfig) {
        for (listener in listeners) {
            try {
                listener(config)
            } catch (t: Throwable) {
                BLog.w("Config", "listener failed: ${t.message}")
            }
        }
    }

    private fun loadFromPrefs(): AppConfig {
        return try {
            val map = HashMap<String, String>()
            val all = prefs.all
            if (all.isNullOrEmpty()) {
                val fresh = AppConfig.defaults()
                writeToPrefs(fresh, sync = true)
                return fresh
            }
            for ((key, value) in all) {
                if (value == null) continue
                map[key] = value.toString()
            }
            ConfigCodec.fromMap(map)
        } catch (t: Throwable) {
            BLog.e("Config", "failed to load config, using defaults", t)
            AppConfig.defaults()
        }
    }

    private fun writeToPrefs(config: AppConfig, sync: Boolean) {
        val editor = prefs.edit()
        val map = ConfigCodec.toMap(config)
        for ((key, value) in map) {
            putValue(editor, key, value)
        }
        if (sync) editor.commit() else editor.apply()
    }

    private fun putValue(editor: SharedPreferences.Editor, key: String, value: String) {
        when (key) {
            ConfigCodec.K_THEME_MODE,
            ConfigCodec.K_UPDATED_AT -> editor.putString(key, value)
            else -> {
                value.toIntOrNull()?.let { editor.putInt(key, it); return }
                value.toLongOrNull()?.let { editor.putLong(key, it); return }
                value.toFloatOrNull()?.let { editor.putFloat(key, it); return }
                value.toBooleanStrictOrNull()?.let { editor.putBoolean(key, it); return }
                editor.putString(key, value)
            }
        }
    }

    companion object {
        const val PREFS_NAME = "battery_power_config"

        @Volatile
        private var instance: ConfigStore? = null

        fun get(context: Context): ConfigStore {
            return instance ?: synchronized(this) {
                instance ?: ConfigStore(context.applicationContext).also { instance = it }
            }
        }
    }
}
