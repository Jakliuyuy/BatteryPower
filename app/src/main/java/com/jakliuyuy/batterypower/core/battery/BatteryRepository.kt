package com.jakliuyuy.batterypower.core.battery

import android.content.Context
import com.jakliuyuy.batterypower.core.log.BLog
import com.jakliuyuy.batterypower.core.model.BatterySnapshot
import com.jakliuyuy.batterypower.core.model.BatterySource
import com.jakliuyuy.batterypower.core.model.BatteryStatus
import com.jakliuyuy.batterypower.core.model.DeviceProfile
import com.jakliuyuy.batterypower.core.model.ProbeResult
import com.jakliuyuy.batterypower.core.root.PersistentSuShell

data class ReaderFlags(
    val enableRootReader: Boolean = true,
    val enableBatteryManagerFallback: Boolean = true
)

/**
 * Produces a validated BatterySnapshot by combining root sysfs data with the
 * BatteryManager fallback. Field-level fallback is used so that a single missing
 * node never invalidates the whole sample (spec sections 54, 135.7).
 *
 * Must be called from a worker thread.
 */
class BatteryRepository(
    private val context: Context,
    private val shell: PersistentSuShell,
    private val profile: DeviceProfile,
    private val flags: ReaderFlags
) {

    private val androidSource by lazy { AndroidBatterySource(context.applicationContext) }

    @Volatile
    private var lastValid: BatterySnapshot? = null

    @Volatile
    var lastSnapshot: BatterySnapshot = BatterySnapshot.empty()
        private set

    @Volatile
    var lastBatteryPath: String = ""
        private set

    /** Samples one snapshot. Never throws. */
    fun sample(): BatterySnapshot {
        return try {
            val snapshot = buildSample()
            lastSnapshot = snapshot
            if (snapshot.valid) lastValid = snapshot
            snapshot
        } catch (t: Throwable) {
            BLog.throttledError("Battery", "sample-failed", "sample() failed: ${t.message}")
            val fallback = lastValid ?: BatterySnapshot.empty()
            lastSnapshot = fallback
            fallback
        }
    }

    fun lastValidSnapshot(): BatterySnapshot? = lastValid

    private fun buildSample(): BatterySnapshot {
        val notes = ArrayList<String>(3)
        val rawMap = LinkedHashMap<String, String>()

        var rootValues: SysfsBatteryReader.RawBatteryValues? = null
        var rootError: com.jakliuyuy.batterypower.core.model.RootError? = null

        if (flags.enableRootReader) {
            val result = rootSource.read()
            if (result != null) {
                rootValues = result
            } else if (rootSource.isInCooldown()) {
                rootError = com.jakliuyuy.batterypower.core.model.RootError.COMMAND_FAILED
                notes.add("root cooldown active")
            }
        }

        val android = if (flags.enableBatteryManagerFallback) androidSource.read() else null

        // --- status -------------------------------------------------------
        var status = rootValues?.status ?: BatteryStatus.UNKNOWN
        if (status == BatteryStatus.UNKNOWN && android != null) {
            status = android.status
            notes.add("status from BatteryManager")
        }

        val charging = when (status) {
            BatteryStatus.CHARGING, BatteryStatus.FULL -> true
            BatteryStatus.DISCHARGING, BatteryStatus.NOT_CHARGING -> false
            BatteryStatus.UNKNOWN -> android?.charging ?: false
        }

        // --- power: power_now -> current x voltage -> null -----------------
        var powerSource = BatterySource.UNAVAILABLE
        var power: Long? = rootValues?.powerUw
        if (power != null) {
            powerSource = BatterySource.ROOT_POWER_NOW
            rawMap["power_now_uW"] = power.toString()
        } else {
            val current = rootValues?.currentMa
            val voltage = rootValues?.voltageUv
            if (current != null && voltage != null) {
                // P(W) = current(mA)/1000 * voltage(uV)/1e6  -> uW
                power = (current * voltage) / 1_000L
                powerSource = BatterySource.ROOT_CURRENT_VOLTAGE
                rawMap["computed_power_uW"] = power.toString()
                notes.add("power computed from current x voltage")
            }
        }

        // --- per-field fallback to BatteryManager --------------------------
        var voltage = rootValues?.voltageUv
        if (voltage == null && android?.voltageUv != null) {
            voltage = android.voltageUv
            notes.add("voltage from BatteryManager")
        }

        var temperature = rootValues?.temperatureDeciC
        if (temperature == null && android?.temperatureDeciC != null) {
            temperature = android.temperatureDeciC
            notes.add("temperature from BatteryManager")
        }

        var capacity = rootValues?.capacityPercent
        if (capacity == null && android?.capacityPercent != null) {
            capacity = android.capacityPercent
            notes.add("capacity from BatteryManager")
        }

        val current = rootValues?.currentMa

        val source = when {
            power != null -> powerSource
            voltage != null || capacity != null || temperature != null -> BatterySource.BATTERY_MANAGER
            else -> BatterySource.UNAVAILABLE
        }

        if (rootValues != null) {
            rootValues.powerUw?.let { rawMap["power_now"] = it.toString() }
            rootValues.currentMa?.let { rawMap["current_now"] = it.toString() }
            rootValues.voltageUv?.let { rawMap["voltage_now"] = it.toString() }
            rootValues.temperatureDeciC?.let { rawMap["temp"] = it.toString() }
            rootValues.capacityPercent?.let { rawMap["capacity"] = it.toString() }
            rawMap["status"] = rootValues.status.displayName
        }

        val snapshot = BatterySnapshot(
            timestampMs = System.currentTimeMillis(),
            powerUw = power,
            currentMa = current,
            voltageUv = voltage,
            temperatureDeciC = temperature,
            capacityPercent = capacity,
            status = status,
            charging = charging,
            valid = power != null || voltage != null || capacity != null || temperature != null,
            source = source,
            batteryPath = rootSource.lastPathValue(),
            deviceProfileName = profile.name,
            raw = rawMap,
            notes = notes,
            error = if (source == BatterySource.UNAVAILABLE) rootError else null
        )
        lastBatteryPath = snapshot.batteryPath
        return BatteryValidator.sanitize(snapshot)
    }

    /** Diagnostics probe (spec section 146). */
    fun probe(): ProbeResult = try {
        val result = rootSource.probe()
        lastBatteryPath = result.batteryPath
        result
    } catch (t: Throwable) {
        ProbeResult(ok = false, message = t.message)
    }

    fun shellState(): PersistentSuShell.State = shell.state

    private val rootSource: RootBatterySource by lazy {
        RootBatterySource(shell, profile)
    }

    private fun RootBatterySource.lastPathValue(): String = try {
        detectPath() ?: ""
    } catch (t: Throwable) {
        ""
    }
}
