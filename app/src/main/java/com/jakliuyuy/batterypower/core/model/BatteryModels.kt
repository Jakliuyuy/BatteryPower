package com.jakliuyuy.batterypower.core.model

/**
 * How the snapshot was produced (spec section 47).
 */
enum class BatterySource(val displayName: String) {
    ROOT_POWER_NOW("ROOT_POWER_NOW"),
    ROOT_CURRENT_VOLTAGE("ROOT_CURRENT_VOLTAGE"),
    BATTERY_MANAGER("BATTERY_MANAGER"),
    UNAVAILABLE("UNAVAILABLE")
}

/**
 * Charging state (spec section 48).
 */
enum class BatteryStatus(val displayName: String) {
    CHARGING("Charging"),
    DISCHARGING("Discharging"),
    FULL("Full"),
    NOT_CHARGING("Not Charging"),
    UNKNOWN("Unknown");

    companion object {
        fun parse(raw: String?): BatteryStatus = when (raw?.trim()?.lowercase()) {
            "charging" -> CHARGING
            "discharging" -> DISCHARGING
            "full", "charged" -> FULL
            "not charging", "not_charging", "notcharging" -> NOT_CHARGING
            "unknown" -> UNKNOWN
            else -> UNKNOWN
        }

        fun fromAndroidStatus(status: Int): BatteryStatus = when (status) {
            android.os.BatteryManager.BATTERY_STATUS_CHARGING -> CHARGING
            android.os.BatteryManager.BATTERY_STATUS_DISCHARGING -> DISCHARGING
            android.os.BatteryManager.BATTERY_STATUS_FULL -> FULL
            android.os.BatteryManager.BATTERY_STATUS_NOT_CHARGING -> NOT_CHARGING
            else -> UNKNOWN
        }
    }
}

/**
 * Root shell / sysfs failure classification (spec section 59).
 */
enum class RootError(val displayName: String) {
    SU_NOT_FOUND("su not found"),
    ROOT_DENIED("root denied"),
    SHELL_START_FAILED("shell start failed"),
    COMMAND_TIMEOUT("command timeout"),
    SHELL_EOF("shell eof"),
    BROKEN_PIPE("broken pipe"),
    COMMAND_FAILED("command failed"),
    NODE_NOT_FOUND("node not found"),
    PARSE_ERROR("parse error"),
    NOT_STARTED("shell not started"),
    BUSY("shell busy")
}

/**
 * Unified battery data model (spec sections 46, 135.4).
 *
 * Internal units are fixed and must never be guessed:
 *  power       micro-watt (uW)
 *  current     milli-ampere (mA)
 *  voltage     micro-volt (uV)
 *  temperature decidegree Celsius (0.1 C)
 *  capacity    percent (0..100)
 *
 * A null field means "no valid data" and must render as "--" in the UI.
 */
data class BatterySnapshot(
    val timestampMs: Long = 0L,
    val powerUw: Long? = null,
    val currentMa: Long? = null,
    val voltageUv: Long? = null,
    val temperatureDeciC: Int? = null,
    val capacityPercent: Int? = null,
    val status: BatteryStatus = BatteryStatus.UNKNOWN,
    val charging: Boolean = false,
    val valid: Boolean = false,
    val source: BatterySource = BatterySource.UNAVAILABLE,
    val batteryPath: String = "",
    val deviceProfileName: String = "",
    val raw: Map<String, String> = emptyMap(),
    val notes: List<String> = emptyList(),
    val error: RootError? = null
) {
    companion object {
        fun empty(reason: RootError? = null) = BatterySnapshot(
            timestampMs = System.currentTimeMillis(),
            source = BatterySource.UNAVAILABLE,
            error = reason
        )
    }
}

/**
 * Result of a raw sysfs probe, used by the diagnostics center (spec 146).
 */
data class ProbeResult(
    val ok: Boolean,
    val batteryPath: String = "",
    val raw: Map<String, String> = emptyMap(),
    val candidates: List<String> = emptyList(),
    val error: RootError? = null,
    val message: String? = null,
    val elapsedMs: Long = 0L
)

/**
 * Presentation state derived from a snapshot (spec section 155).
 * Views must consume this instead of touching sysfs or shell.
 */
data class PresentationState(
    val text: String,
    val statusText: String,
    val status: BatteryStatus,
    val valid: Boolean,
    val timestampMs: Long
)
