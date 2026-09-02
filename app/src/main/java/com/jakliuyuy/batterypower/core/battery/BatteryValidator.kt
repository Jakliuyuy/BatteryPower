package com.jakliuyuy.batterypower.core.battery

import com.jakliuyuy.batterypower.core.model.BatterySnapshot

/**
 * Single place where value ranges are defined (spec sections 75, 135.9).
 * Out-of-range values are invalidated to null instead of being shown as garbage.
 */
object BatteryValidator {

    // Power: -1000W .. +1000W (stored in uW)
    const val POWER_UW_MIN = -1_000_000_000L
    const val POWER_UW_MAX = 1_000_000_000L

    // Temperature: -40C .. +100C (stored in 0.1C)
    const val TEMP_DECI_MIN = -400
    const val TEMP_DECI_MAX = 1000

    // Voltage: 0 .. 100V (stored in uV)
    const val VOLTAGE_UV_MIN = 0L
    const val VOLTAGE_UV_MAX = 100_000_000L

    // Current: 0 .. 500A (stored in mA)
    const val CURRENT_MA_MIN = -500_000L
    const val CURRENT_MA_MAX = 500_000L

    // Capacity: 0 .. 100%
    const val CAPACITY_MIN = 0
    const val CAPACITY_MAX = 100

    fun powerUw(value: Long?): Long? {
        if (value == null) return null
        if (value.isNaNLike()) return null
        return if (value in POWER_UW_MIN..POWER_UW_MAX) value else null
    }

    fun currentMa(value: Long?): Long? {
        if (value == null) return null
        if (value.isNaNLike()) return null
        return if (value in CURRENT_MA_MIN..CURRENT_MA_MAX) value else null
    }

    fun voltageUv(value: Long?): Long? {
        if (value == null) return null
        if (value.isNaNLike()) return null
        return if (value in VOLTAGE_UV_MIN..VOLTAGE_UV_MAX) value else null
    }

    fun temperatureDeciC(value: Int?): Int? {
        if (value == null) return null
        return if (value in TEMP_DECI_MIN..TEMP_DECI_MAX) value else null
    }

    fun capacityPercent(value: Int?): Int? {
        if (value == null) return null
        return if (value in CAPACITY_MIN..CAPACITY_MAX) value else null
    }

    fun sanitize(snapshot: BatterySnapshot): BatterySnapshot = snapshot.copy(
        powerUw = powerUw(snapshot.powerUw),
        currentMa = currentMa(snapshot.currentMa),
        voltageUv = voltageUv(snapshot.voltageUv),
        temperatureDeciC = temperatureDeciC(snapshot.temperatureDeciC),
        capacityPercent = capacityPercent(snapshot.capacityPercent)
    )

    private fun Long.isNaNLike(): Boolean = this == Long.MAX_VALUE || this == Long.MIN_VALUE
}
