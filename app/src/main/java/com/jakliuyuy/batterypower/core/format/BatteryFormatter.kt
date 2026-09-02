package com.jakliuyuy.batterypower.core.format

import com.jakliuyuy.batterypower.core.model.BatterySnapshot
import com.jakliuyuy.batterypower.core.model.BatterySource
import com.jakliuyuy.batterypower.core.model.BatteryStatus
import java.util.Locale
import kotlin.math.abs

/**
 * The single formatting authority for Overlay and SystemUI (spec sections 12-14,
 * 73-74, 107, 135.5). Both display surfaces must share these rules.
 *
 * Rules:
 *  - null / invalid -> "--"   (never 0)
 *  - no data at all -> "N/A"
 *  - read exception -> "ERR"
 *  - real zero      -> "0"
 */
object BatteryFormatter {

    const val UNKNOWN = "--"
    const val NOT_AVAILABLE = "N/A"
    const val ERROR = "ERR"

    data class Options(
        val showUnit: Boolean = true,
        val powerDecimals: Int = 2,
        val currentDecimals: Int = 0,
        val voltageDecimals: Int = 3,
        val temperatureDecimals: Int = 1,
        val capacityDecimals: Int = 0,
        val monospaceDigits: Boolean = true
    )

    data class Fields(
        val power: Boolean = true,
        val current: Boolean = true,
        val voltage: Boolean = true,
        val temperature: Boolean = false,
        val capacity: Boolean = false
    ) {
        fun anyEnabled(): Boolean = power || current || voltage || temperature || capacity
        companion object {
            fun defaultSet() = Fields()
        }
    }

    /** Power in watts with an explicit charge/discharge sign (spec 14, 53, 135.8). */
    fun signedPowerWatts(snapshot: BatterySnapshot): Double? {
        val raw = snapshot.powerUw ?: return null
        val watts = raw / 1_000_000.0
        return when (snapshot.status) {
            BatteryStatus.CHARGING, BatteryStatus.FULL -> abs(watts)
            BatteryStatus.DISCHARGING -> -abs(watts)
            BatteryStatus.NOT_CHARGING, BatteryStatus.UNKNOWN -> watts
        }
    }

    /** Current in mA; sign follows the raw value when the status is unknown. */
    fun signedCurrentMa(snapshot: BatterySnapshot): Double? {
        val raw = snapshot.currentMa ?: return null
        val ma = raw.toDouble()
        return when (snapshot.status) {
            BatteryStatus.CHARGING, BatteryStatus.FULL -> abs(ma)
            BatteryStatus.DISCHARGING -> -abs(ma)
            BatteryStatus.NOT_CHARGING, BatteryStatus.UNKNOWN -> ma
        }
    }

    fun formatPower(snapshot: BatterySnapshot, options: Options = Options()): String {
        if (snapshot.error != null) return withUnit(ERROR, "W", options.showUnit)
        val watts = signedPowerWatts(snapshot)
            ?: return withUnit(missing(snapshot), "W", options.showUnit)
        val text = formatSigned(watts, options.powerDecimals)
        return withUnit(text, "W", options.showUnit)
    }

    fun formatCurrent(snapshot: BatterySnapshot, options: Options = Options()): String {
        if (snapshot.error != null) return withUnit(ERROR, "mA", options.showUnit)
        val ma = signedCurrentMa(snapshot)
            ?: return withUnit(missing(snapshot), "mA", options.showUnit)
        val text = formatSigned(ma, options.currentDecimals)
        return withUnit(text, "mA", options.showUnit)
    }

    fun formatVoltage(snapshot: BatterySnapshot, options: Options = Options()): String {
        if (snapshot.error != null) return withUnit(ERROR, "V", options.showUnit)
        val uv = snapshot.voltageUv
            ?: return withUnit(missing(snapshot), "V", options.showUnit)
        val volts = uv / 1_000_000.0
        return withUnit(decimal(volts, options.voltageDecimals), "V", options.showUnit)
    }

    fun formatTemperature(snapshot: BatterySnapshot, options: Options = Options()): String {
        if (snapshot.error != null) return withUnit(ERROR, "°C", options.showUnit)
        val deci = snapshot.temperatureDeciC
            ?: return withUnit(missing(snapshot), "°C", options.showUnit)
        return withUnit(decimal(deci / 10.0, options.temperatureDecimals), "°C", options.showUnit)
    }

    fun formatCapacity(snapshot: BatterySnapshot, options: Options = Options()): String {
        if (snapshot.error != null) return withUnit(ERROR, "%", options.showUnit)
        val percent = snapshot.capacityPercent
            ?: return withUnit(missing(snapshot), "%", options.showUnit)
        return withUnit(decimal(percent.toDouble(), options.capacityDecimals), "%", options.showUnit)
    }

    /**
     * Builds the complete single-line text, e.g. "+3.46W  865mA  4.005V".
     */
    fun buildText(
        snapshot: BatterySnapshot,
        fields: Fields,
        options: Options = Options(),
        separator: String = "  "
    ): String {
        val parts = ArrayList<String>(5)
        if (fields.power) parts.add(formatPower(snapshot, options))
        if (fields.current) parts.add(formatCurrent(snapshot, options))
        if (fields.voltage) parts.add(formatVoltage(snapshot, options))
        if (fields.temperature) parts.add(formatTemperature(snapshot, options))
        if (fields.capacity) parts.add(formatCapacity(snapshot, options))
        if (parts.isEmpty()) parts.add(formatPower(snapshot, options))
        return parts.joinToString(separator)
    }

    /** Long-form preview used by the home card, e.g. "+3.46 W". */
    fun buildPreviewParts(
        snapshot: BatterySnapshot,
        fields: Fields,
        options: Options = Options()
    ): List<String> {
        val parts = ArrayList<String>(5)
        if (fields.power) parts.add(spaced(formatPower(snapshot, options)))
        if (fields.current) parts.add(spaced(formatCurrent(snapshot, options)))
        if (fields.voltage) parts.add(spaced(formatVoltage(snapshot, options)))
        if (fields.temperature) parts.add(spaced(formatTemperature(snapshot, options)))
        if (fields.capacity) parts.add(spaced(formatCapacity(snapshot, options)))
        if (parts.isEmpty()) parts.add(spaced(formatPower(snapshot, options)))
        return parts
    }

    fun statusText(snapshot: BatterySnapshot): String = when {
        snapshot.error != null && !snapshot.valid -> BatteryStatus.UNKNOWN.displayName
        snapshot.source == BatterySource.UNAVAILABLE && !snapshot.valid -> "Unavailable"
        else -> snapshot.status.displayName
    }

    private fun missing(snapshot: BatterySnapshot): String =
        if (snapshot.source == BatterySource.UNAVAILABLE) NOT_AVAILABLE else UNKNOWN

    private fun spaced(text: String): String {
        // "3.46W" -> "3.46 W" for the comfortable in-app preview card.
        val idx = text.indexOfFirst { it.isLetter() || it == '%' || it == '°' }
        return if (idx > 0) text.substring(0, idx) + " " + text.substring(idx) else text
    }

    private fun formatSigned(value: Double, decimals: Int): String {
        if (abs(value) < 0.5 * pow10(decimals)) {
            // Genuine zero: never render as "+0.00".
            return decimal(0.0, decimals)
        }
        val sign = if (value > 0) "+" else "-"
        return sign + decimal(abs(value), decimals)
    }

    private fun decimal(value: Double, decimals: Int): String =
        String.format(Locale.US, "%.${decimals.coerceIn(0, 4)}f", value)

    private fun pow10(exp: Int): Double = when (exp) {
        0 -> 1.0
        1 -> 0.1
        2 -> 0.01
        3 -> 0.001
        4 -> 0.0001
        else -> 0.01
    }

    private fun withUnit(value: String, unit: String, showUnit: Boolean): String =
        if (showUnit) value + unit else value
}
