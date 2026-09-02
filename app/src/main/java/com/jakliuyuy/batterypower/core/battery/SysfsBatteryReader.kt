package com.jakliuyuy.batterypower.core.battery

import com.jakliuyuy.batterypower.core.log.BLog
import com.jakliuyuy.batterypower.core.model.BatteryStatus
import com.jakliuyuy.batterypower.core.model.DeviceProfile
import java.util.Locale

/**
 * Parses the raw key=value output produced by SysfsCommands.
 * The reader never guesses units: every conversion goes through DeviceProfile.
 */
object SysfsBatteryReader {

    const val KEY_POWER_NOW = "power_now"
    const val KEY_CURRENT_NOW = "current_now"
    const val KEY_VOLTAGE_NOW = "voltage_now"
    const val KEY_TEMP = "temp"
    const val KEY_CAPACITY = "capacity"
    const val KEY_STATUS = "status"
    const val KEY_CHARGE_TYPE = "charge_type"

    /** Reads every interesting node with a single shell round-trip. */
    fun buildReadCommand(path: String): String {
        val p = path.trimEnd('/')
        return "for f in power_now current_now voltage_now temp capacity status charge_type; do " +
            "printf '%s=' \"\$f\"; " +
            "cat $p/\$f 2>/dev/null | tr -d '\\n'; " +
            "printf '\\n'; " +
            "done"
    }

    fun buildListCommand(): String =
        "ls /sys/class/power_supply/ 2>/dev/null"

    fun buildDetectCommand(): String =
        "for d in /sys/class/power_supply/*; do if [ -f \"\$d/capacity\" ] || [ -f \"\$d/voltage_now\" ]; then echo \"\$d\"; fi; done"

    fun parse(output: String): Map<String, String> {
        val map = LinkedHashMap<String, String>()
        if (output.isEmpty()) return map
        for (line in output.lineSequence()) {
            val trimmed = line.trim()
            if (trimmed.isEmpty()) continue
            val idx = trimmed.indexOf('=')
            if (idx <= 0) continue
            val key = trimmed.substring(0, idx).trim()
            val value = trimmed.substring(idx + 1).trim()
            if (key.isEmpty()) continue
            map[key] = value
        }
        return map
    }

    fun parseList(output: String): List<String> = output
        .lineSequence()
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .toList()

    fun readLong(map: Map<String, String>, key: String): Long? {
        val raw = map[key] ?: return null
        return raw.trim().toLongOrNull()
    }

    fun readInt(map: Map<String, String>, key: String): Int? = readLong(map, key)?.toInt()

    fun readStatus(map: Map<String, String>): BatteryStatus {
        val raw = map[KEY_STATUS] ?: map[KEY_CHARGE_TYPE]
        return BatteryStatus.parse(raw)
    }

    /**
     * Applies the device profile to raw sysfs values and returns already-clamped values.
     */
    fun convert(
        map: Map<String, String>,
        profile: DeviceProfile
    ): RawBatteryValues {
        val power = readLong(map, KEY_POWER_NOW)?.let { profile.powerToUw(it) }
        val current = readLong(map, KEY_CURRENT_NOW)?.let { profile.currentToMa(it) }
        val voltage = readLong(map, KEY_VOLTAGE_NOW)?.let { profile.voltageToUv(it) }
        val temp = readLong(map, KEY_TEMP)?.let { profile.temperatureToDeciC(it) }
        val capacity = readInt(map, KEY_CAPACITY)
        val status = readStatus(map)
        return RawBatteryValues(
            powerUw = power,
            currentMa = current,
            voltageUv = voltage,
            temperatureDeciC = temp,
            capacityPercent = capacity,
            status = status
        )
    }

    data class RawBatteryValues(
        val powerUw: Long?,
        val currentMa: Long?,
        val voltageUv: Long?,
        val temperatureDeciC: Int?,
        val capacityPercent: Int?,
        val status: BatteryStatus
    )

    fun describe(raw: Map<String, String>): String {
        if (raw.isEmpty()) return "(empty)"
        return raw.entries.joinToString(", ") {
            "${it.key}=${it.value.lowercase(Locale.US)}"
        }
    }

    fun logRaw(raw: Map<String, String>) {
        BLog.d("Battery", "raw: ${describe(raw)}")
    }
}
