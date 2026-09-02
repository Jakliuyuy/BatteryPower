package com.jakliuyuy.batterypower.core.model

/**
 * Units are declared explicitly per device. Never auto-detect by magnitude
 * (spec sections 97, 135.6): guessing orders of magnitude is forbidden.
 */
enum class CurrentUnit { MA, UA }
enum class VoltageUnit { UV, MV, V }
enum class PowerUnit { UW, MW }
enum class TemperatureUnit { DECI_C, MILLI_C, C }

data class DeviceProfile(
    val name: String,
    val batteryPaths: List<String>,
    val currentUnit: CurrentUnit,
    val voltageUnit: VoltageUnit,
    val powerUnit: PowerUnit,
    val temperatureUnit: TemperatureUnit,
    val supportsPowerNow: Boolean
) {
    /** Converts a raw current reading into mA. */
    fun currentToMa(raw: Long): Long = when (currentUnit) {
        CurrentUnit.MA -> raw
        CurrentUnit.UA -> raw / 1000L
    }

    /** Converts a raw voltage reading into uV. */
    fun voltageToUv(raw: Long): Long = when (voltageUnit) {
        VoltageUnit.UV -> raw
        VoltageUnit.MV -> raw * 1000L
        VoltageUnit.V -> raw * 1_000_000L
    }

    /** Converts a raw power reading into uW. */
    fun powerToUw(raw: Long): Long = when (powerUnit) {
        PowerUnit.UW -> raw
        PowerUnit.MW -> raw * 1000L
    }

    /** Converts a raw temperature reading into decidegree Celsius. */
    fun temperatureToDeciC(raw: Long): Int = when (temperatureUnit) {
        TemperatureUnit.DECI_C -> raw.toInt()
        TemperatureUnit.MILLI_C -> (raw / 10L).toInt()
        TemperatureUnit.C -> (raw * 10L).toInt()
    }

    companion object {
        /**
         * OnePlus Ace 6T / PLR110, verified on ColorOS 16:
         *  power_now  = uW
         *  current_now= mA
         *  voltage_now= uV
         *  temp       = 0.1 C
         */
        val PLR110 = DeviceProfile(
            name = "PLR110",
            batteryPaths = listOf(
                "/sys/class/power_supply/battery",
                "/sys/class/power_supply/BAT0"
            ),
            currentUnit = CurrentUnit.MA,
            voltageUnit = VoltageUnit.UV,
            powerUnit = PowerUnit.UW,
            temperatureUnit = TemperatureUnit.DECI_C,
            supportsPowerNow = true
        )

        /** Best-effort profile for unknown devices: no unit guessing, standard sysfs units. */
        val GENERIC = DeviceProfile(
            name = "generic",
            batteryPaths = listOf(
                "/sys/class/power_supply/battery",
                "/sys/class/power_supply/BAT0",
                "/sys/class/power_supply/BAT1"
            ),
            currentUnit = CurrentUnit.UA,
            voltageUnit = VoltageUnit.UV,
            powerUnit = PowerUnit.UW,
            temperatureUnit = TemperatureUnit.DECI_C,
            supportsPowerNow = true
        )

        fun defaultProfile(): DeviceProfile {
            return if (isPlr110()) PLR110 else GENERIC
        }

        private fun isPlr110(): Boolean {
            return try {
                android.os.Build.DEVICE.equals("PLR110", ignoreCase = true) ||
                    android.os.Build.DEVICE.equals("OP5D3FL1", ignoreCase = true) ||
                    android.os.Build.PRODUCT.equals("PLR110", ignoreCase = true) ||
                    android.os.Build.MODEL.contains("Ace 6T", ignoreCase = true)
            } catch (t: Throwable) {
                false
            }
        }
    }
}
