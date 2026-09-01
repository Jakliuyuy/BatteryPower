package com.jakliuyuy.batterypower.data

import android.content.Context
import com.jakliuyuy.batterypower.model.Config
import com.jakliuyuy.batterypower.model.Field

/**
 * 主 App 进程的配置仓储。
 *
 * 避坑 7：位置等易丢失的关键配置必须用 commit() 同步落盘，
 * apply() 是异步的，杀后台时可能还没写完进程就没了。
 */
class ConfigRepository(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private var cache: Config? = null

    @Synchronized
    fun get(): Config {
        cache?.let { return it }
        val map = prefs.all
            .filterValues { it is String }
            .mapValues { it.value as String }
        val cfg = Config.fromMap(map)
        cache = cfg
        return cfg
    }

    /**
     * 写入配置。
     * @param sync true 时用 commit() 同步落盘（用于悬浮窗位置这类关键数据）
     */
    @Synchronized
    fun save(cfg: Config, sync: Boolean = false) {
        cache = cfg
        val editor = prefs.edit().clear()
        cfg.toMap().forEach { (k, v) -> editor.putString(k, v) }
        if (sync) {
            editor.commit()
        } else {
            editor.apply()
        }
    }

    /** 修改单个字段的便捷方法 */
    @Synchronized
    fun update(sync: Boolean = false, block: (Config) -> Config) {
        save(block(get()), sync)
    }

    companion object {
        const val PREFS_NAME = "batterypower_config"
    }
}

/** 把采样数据按勾选字段渲染成文字 */
fun renderBatteryText(
    snapshot: com.jakliuyuy.batterypower.model.BatterySnapshot,
    fields: List<Field>,
    showUnit: Boolean
): String {
    if (fields.isEmpty()) return ""
    return fields.joinToString("  ") { field ->
        when (field) {
            Field.POWER -> {
                // 充电显示正功率、放电显示负功率（负号由格式化自然带出）
                val sign = if (snapshot.powerW > 0) "+" else ""
                "$sign${fmt(snapshot.powerW, 2)}${if (showUnit) "W" else ""}"
            }
            Field.CURRENT -> "${fmt(snapshot.currentMa, 0)}${if (showUnit) "mA" else ""}"
            Field.VOLTAGE -> "${fmt(snapshot.voltageV, 2)}${if (showUnit) "V" else ""}"
            Field.TEMP -> "${fmt(snapshot.tempC, 1)}${if (showUnit) "℃" else ""}"
            Field.LEVEL -> "${snapshot.level}${if (showUnit) "%" else ""}"
        }
    }
}

private fun fmt(v: Double, digits: Int): String =
    if (v.isNaN() || v.isInfinite()) "0.0" else String.format("%.${digits}f", v)
