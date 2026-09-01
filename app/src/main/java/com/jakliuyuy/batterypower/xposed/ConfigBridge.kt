package com.jakliuyuy.batterypower.xposed

import android.content.Context
import com.jakliuyuy.batterypower.model.Config
import de.robv.android.xposed.XposedBridge

/**
 * SystemUI 进程侧的配置读取桥。
 *
 * 双缓存兜底（避坑 1 的后半段）：
 *  ① 内存缓存 —— 每 tick 直接用，零开销
 *  ② SystemUI 自己的 DE SharedPreferences —— 主 App 被杀、SystemUI 重启后仍不丢
 *
 * 拉取失败时做 10s 节流，避免每秒疯狂拉起 Provider 拖慢 SystemUI。
 */
internal object ConfigBridge {

    private const val TAG = "BatteryPower/Bridge"
    private const val PREFS_NAME = "batterypower_config"
    private const val RETRY_INTERVAL_MS = 10_000L

    @Volatile
    private var memory: Config? = null

    @Volatile
    private var lastFailedAt = 0L

    /** 上次成功拉取配置的时间，用于判断缓存新鲜度 */
    @Volatile
    var lastSuccessAt = 0L
        private set

    /**
     * 取配置。优先走 Provider 刷新，失败则用缓存/磁盘值。
     * 调用频率高（每 0.5~5s 一次），因此内部自带节流。
     */
    fun obtain(context: Context): Config {
        val now = android.os.SystemClock.elapsedRealtime()

        // 刚刚失败过，节流期内不再尝试
        if (lastFailedAt > 0 && now - lastFailedAt < RETRY_INTERVAL_MS) {
            return memory ?: loadFromDisk(context)
        }

        val fetched = try {
            queryProvider(context)
        } catch (t: Throwable) {
            XposedBridge.log("$TAG query 异常: ${t.message}")
            null
        }

        return if (fetched != null) {
            memory = fetched
            lastFailedAt = 0L
            lastSuccessAt = now
            saveToDisk(context, fetched)
            fetched
        } else {
            lastFailedAt = now
            memory ?: loadFromDisk(context)
        }
    }

    /**
     * 依次尝试两个 authority：正式包与 debug 包的 applicationId 不同，
     * 用户可能装的是 debug 版，只认一个会导致"配置改了没反应"。
     */
    private fun queryProvider(context: Context): Config? {
        for (authority in ConfigProviderAuthority.candidates) {
            val result = queryOne(context, authority)
            if (result != null) return result
        }
        return null
    }

    private fun queryOne(context: Context, authority: String): Config? {
        val cursor = try {
            context.contentResolver.query(
                android.net.Uri.parse("content://$authority/config"),
                null, null, null, null
            )
        } catch (t: Throwable) {
            // 未安装对应包时会抛异常，静默继续尝试下一个
            null
        } ?: return null
        return try {
            val keyIdx = cursor.getColumnIndex("key")
            val valIdx = cursor.getColumnIndex("value")
            if (keyIdx < 0 || valIdx < 0) return null
            val map = HashMap<String, String>()
            while (cursor.moveToNext()) {
                map[cursor.getString(keyIdx)] = cursor.getString(valIdx)
            }
            if (map.isEmpty()) null else Config.fromMap(map)
        } finally {
            try { cursor.close() } catch (_: Throwable) {}
        }
    }

    /**
     * SystemUI 自身目录下的缓存。
     * 用 DeviceProtected 存储：SystemUI 在 direct boot 阶段也会起来，
     * 普通 CE 存储此时不可用。
     */
    private fun diskPrefs(context: Context): android.content.SharedPreferences {
        val de = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
            context.createDeviceProtectedStorageContext()
        } else {
            context
        }
        return de.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    private fun saveToDisk(context: Context, cfg: Config) {
        try {
            val editor = diskPrefs(context).edit().clear()
            cfg.toMap().forEach { (k, v) -> editor.putString(k, v) }
            editor.apply()
        } catch (t: Throwable) {
            XposedBridge.log("$TAG 落盘失败: ${t.message}")
        }
    }

    private fun loadFromDisk(context: Context): Config {
        return try {
            val all = diskPrefs(context).all
                .filterValues { it is String }
                .mapValues { it.value as String }
            Config.fromMap(all).also {
                if (all.isNotEmpty()) memory = it
            }
        } catch (t: Throwable) {
            XposedBridge.log("$TAG 读盘失败: ${t.message}")
            Config.default()
        }
    }

    /** 强制清缓存（模块被重新加载时调用） */
    fun reset() {
        memory = null
        lastFailedAt = 0L
    }
}

/**
 * Provider authority。debug 包 applicationId 带 .debug 后缀，
 * 这里两个都尝试，避免装错包导致读不到配置。
 */
internal object ConfigProviderAuthority {
    val candidates: List<String> = listOf(
        "com.jakliuyuy.batterypower.config",
        "com.jakliuyuy.batterypower.debug.config"
    )
}
