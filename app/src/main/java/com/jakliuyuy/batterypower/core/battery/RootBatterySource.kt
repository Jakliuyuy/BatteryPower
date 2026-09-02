package com.jakliuyuy.batterypower.core.battery

import com.jakliuyuy.batterypower.core.log.BLog
import com.jakliuyuy.batterypower.core.model.DeviceProfile
import com.jakliuyuy.batterypower.core.model.ProbeResult
import com.jakliuyuy.batterypower.core.root.PersistentSuShell

/**
 * Root sysfs reader backed by the persistent su shell.
 *
 * Spec 147.2: 1 failure -> retry, 2 failures -> rebuild shell, 3 failures ->
 * fall back to BatteryManager for a cool-down period (never a tight loop).
 */
class RootBatterySource(
    private val shell: PersistentSuShell,
    private val profile: DeviceProfile
) {

    @Volatile
    private var cachedPath: String? = null

    @Volatile
    private var failureCount = 0

    @Volatile
    private var cooldownUntilMs = 0L

    @Volatile
    var lastError: String? = null
        private set

    fun isInCooldown(): Boolean = System.currentTimeMillis() < cooldownUntilMs

    fun failureStreak(): Int = failureCount

    fun invalidatePath() {
        cachedPath = null
    }

    /** Detects the battery sysfs directory (spec section 70). */
    fun detectPath(): String? {
        cachedPath?.let { return it }
        val result = shell.execute(SysfsBatteryReader.buildDetectCommand(), 4_000L)
        if (!result.success) {
            lastError = result.error?.displayName
            return null
        }
        val found = SysfsBatteryReader.parseList(result.output)
            .map { it.trimEnd('/') }
        for (preferred in profile.batteryPaths) {
            val p = preferred.trimEnd('/')
            if (found.any { it.equals(p, ignoreCase = true) }) {
                cachedPath = p
                return p
            }
        }
        val fallback = found.firstOrNull()
        if (fallback != null) {
            cachedPath = fallback
            return fallback
        }
        lastError = "no battery node found"
        return null
    }

    /** Reads one sample. Returns null when root reading is unavailable. */
    fun read(): SysfsBatteryReader.RawBatteryValues? {
        if (isInCooldown()) return null
        if (!shell.isAlive() && !shell.start()) {
            lastError = "shell unavailable"
            return null
        }
        val path = detectPath()
        if (path == null) {
            registerFailure()
            return null
        }
        val result = shell.execute(SysfsBatteryReader.buildReadCommand(path), 4_000L)
        if (!result.success) {
            lastError = result.error?.displayName
            registerFailure()
            return null
        }
        val raw = SysfsBatteryReader.parse(result.output)
        if (raw.isEmpty()) {
            lastError = "empty sysfs output"
            registerFailure()
            return null
        }
        failureCount = 0
        cooldownUntilMs = 0L
        lastError = null
        SysfsBatteryReader.logRaw(raw)
        return SysfsBatteryReader.convert(raw, profile)
    }

    /** Full probe for the diagnostics center (spec section 146). */
    fun probe(): ProbeResult {
        val started = System.currentTimeMillis()
        if (!shell.isAlive() && !shell.start()) {
            return ProbeResult(
                ok = false,
                error = com.jakliuyuy.batterypower.core.model.RootError.SHELL_START_FAILED,
                message = lastError ?: "su shell not available",
                elapsedMs = System.currentTimeMillis() - started
            )
        }
        val listResult = shell.execute(SysfsBatteryReader.buildListCommand(), 4_000L)
        val candidates = if (listResult.success) SysfsBatteryReader.parseList(listResult.output) else emptyList()
        val path = detectPath()
            ?: return ProbeResult(
                ok = false,
                candidates = candidates,
                error = com.jakliuyuy.batterypower.core.model.RootError.NODE_NOT_FOUND,
                message = lastError ?: "battery node not found",
                elapsedMs = System.currentTimeMillis() - started
            )
        val readResult = shell.execute(SysfsBatteryReader.buildReadCommand(path), 4_000L)
        if (!readResult.success) {
            return ProbeResult(
                ok = false,
                batteryPath = path,
                candidates = candidates,
                error = readResult.error ?: com.jakliuyuy.batterypower.core.model.RootError.COMMAND_FAILED,
                message = "read command failed",
                elapsedMs = System.currentTimeMillis() - started
            )
        }
        val raw = SysfsBatteryReader.parse(readResult.output)
        return ProbeResult(
            ok = true,
            batteryPath = path,
            raw = raw,
            candidates = candidates,
            elapsedMs = System.currentTimeMillis() - started
        )
    }

    private fun registerFailure() {
        failureCount++
        when (failureCount) {
            1 -> BLog.d("Battery", "root read failed once, will retry")
            2 -> {
                BLog.w("Battery", "root read failed twice, rebuilding su shell")
                shell.restart()
                invalidatePath()
            }
            else -> {
                BLog.w("Battery", "root read failed ${failureCount} times, falling back for 60s")
                cooldownUntilMs = System.currentTimeMillis() + 60_000L
                invalidatePath()
            }
        }
    }
}
