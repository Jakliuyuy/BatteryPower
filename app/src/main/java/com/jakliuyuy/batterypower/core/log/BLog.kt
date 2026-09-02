package com.jakliuyuy.batterypower.core.log

import android.util.Log
import java.util.concurrent.ConcurrentHashMap

/**
 * Unified logging facade (spec section 100, 101, 153).
 *
 * - Tag is always "BatteryPower" with a sub-category suffix.
 * - Default level is INFO; DEBUG/TRACE are opt-in from the diagnostics page.
 * - Repeated identical messages are throttled so a 1 Hz loop can never flood logcat.
 */
object BLog {

    const val TAG = "BatteryPower"

    enum class Level(val priority: Int) {
        ERROR(Log.ERROR),
        WARN(Log.WARN),
        INFO(Log.INFO),
        DEBUG(Log.DEBUG),
        TRACE(Log.VERBOSE);

        fun enabledAgainst(min: Level): Boolean = priority >= min.priority
    }

    @Volatile
    var minLevel: Level = Level.INFO

    private val throttleState = ConcurrentHashMap<String, Long>()

    fun isDebugEnabled(): Boolean = minLevel == Level.DEBUG || minLevel == Level.TRACE

    fun setDebugEnabled(enabled: Boolean) {
        minLevel = if (enabled) Level.DEBUG else Level.INFO
    }

    fun e(sub: String, msg: String, t: Throwable? = null) = log(Level.ERROR, sub, msg, t)
    fun w(sub: String, msg: String, t: Throwable? = null) = log(Level.WARN, sub, msg, t)
    fun i(sub: String, msg: String, t: Throwable? = null) = log(Level.INFO, sub, msg, t)
    fun d(sub: String, msg: String, t: Throwable? = null) = log(Level.DEBUG, sub, msg, t)
    fun v(sub: String, msg: String, t: Throwable? = null) = log(Level.TRACE, sub, msg, t)

    fun log(level: Level, sub: String, msg: String, t: Throwable? = null) {
        if (!level.enabledAgainst(minLevel)) return
        val tag = if (sub.isEmpty()) TAG else "$TAG/$sub"
        when (level) {
            Level.ERROR -> if (t != null) Log.e(tag, msg, t) else Log.e(tag, msg)
            Level.WARN -> if (t != null) Log.w(tag, msg, t) else Log.w(tag, msg)
            Level.INFO -> if (t != null) Log.i(tag, msg, t) else Log.i(tag, msg)
            Level.DEBUG -> if (t != null) Log.d(tag, msg, t) else Log.d(tag, msg)
            Level.TRACE -> if (t != null) Log.v(tag, msg, t) else Log.v(tag, msg)
        }
    }

    /**
     * Records the first occurrence immediately, then at most once per [intervalMs]
     * for the same key (spec section 153: prevent log flooding).
     */
    fun throttled(level: Level, sub: String, key: String, msg: String, intervalMs: Long = 10_000L) {
        val now = android.os.SystemClock.elapsedRealtime()
        val last = throttleState[key]
        if (last != null && now - last < intervalMs) return
        throttleState[key] = now
        log(level, sub, "$msg (throttled)", null)
    }

    fun throttledError(sub: String, key: String, msg: String, t: Throwable? = null) {
        val now = android.os.SystemClock.elapsedRealtime()
        val last = throttleState[key]
        if (last != null && now - last < 10_000L) return
        throttleState[key] = now
        log(Level.ERROR, sub, "$msg (throttled)", t)
    }

    fun clearThrottle() = throttleState.clear()
}
