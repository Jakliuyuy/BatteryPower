package com.jakliuyuy.batterypower.model

/**
 * 一次电池采样结果。
 *
 * 符号约定：充电为正功率、放电为负功率（与需求一致）。
 * 单位：功率 W、电流 mA、电压 V、温度 ℃、电量 %
 */
data class BatterySnapshot(
    /** 功率，W。充电为正，放电为负 */
    val powerW: Double = 0.0,
    /** 电流，mA。充电流入记为正 */
    val currentMa: Double = 0.0,
    /** 电压，V */
    val voltageV: Double = 0.0,
    /** 温度，℃ */
    val tempC: Double = 0.0,
    /** 电量，% */
    val level: Int = 0,
    /** 是否正在充电 */
    val charging: Boolean = false,
    /** 数据来源，用于诊断页展示 */
    val source: String = "unknown",
    /** 采样时刻 */
    val timestamp: Long = System.currentTimeMillis()
) {

    /** 是否拿到了有效数据（功率或电流至少有一个非零） */
    val isValid: Boolean
        get() = powerW != 0.0 || currentMa != 0.0

    companion object {
        val EMPTY = BatterySnapshot(source = "empty")
    }
}

/** 可显示的字段 */
enum class Field(val key: String, val label: String) {
    POWER("power", "功率"),
    CURRENT("current", "电流"),
    VOLTAGE("voltage", "电压"),
    TEMP("temp", "温度"),
    LEVEL("level", "电量");

    companion object {
        /** 默认显示：功率 + 电流 + 电量 */
        val DEFAULT = listOf(POWER, CURRENT, LEVEL)

        fun fromKeys(raw: String?): List<Field> {
            if (raw.isNullOrBlank()) return DEFAULT
            val set = raw.split(",").map { it.trim() }.toSet()
            val picked = values().filter { set.contains(it.key) }
            // 保持枚举顺序，避免勾选顺序影响显示顺序
            return picked.ifEmpty { DEFAULT }
        }

        fun toKeys(list: List<Field>): String =
            list.joinToString(",") { it.key }
    }
}
