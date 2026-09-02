package com.jakliuyuy.batterypower.core.battery

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import com.jakliuyuy.batterypower.core.log.BLog
import com.jakliuyuy.batterypower.core.model.BatteryStatus

/**
 * BatteryManager fallback used when root is unavailable (spec sections 60, 148).
 *
 * Current is intentionally not synthesised: if the platform does not expose a
 * reliable value we return null so the UI shows "--" instead of a fake 0.
 */
class AndroidBatterySource(private val context: Context) {

    data class Values(
        val capacityPercent: Int?,
        val voltageUv: Long?,
        val temperatureDeciC: Int?,
        val status: BatteryStatus,
        val charging: Boolean
    )

    @Volatile
    private var lastGood: Values? = null

    fun read(): Values? {
        return try {
            val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            val intent = if (Build.VERSION.SDK_INT >= 33) {
                context.registerReceiver(null, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                context.registerReceiver(null, filter)
            } ?: return lastGood

            val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
            val capacity = if (level >= 0 && scale > 0) {
                ((level * 100) / scale).coerceIn(0, 100)
            } else null

            // EXTRA_VOLTAGE is in millivolts.
            val millivolts = intent.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1)
            val voltageUv = if (millivolts > 0) millivolts * 1_000L else null

            // EXTRA_TEMPERATURE is in tenths of a degree Celsius.
            val tenths = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE)
            val temperatureDeciC = if (tenths != Int.MIN_VALUE) tenths else null

            val rawStatus = intent.getIntExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_UNKNOWN)
            val status = BatteryStatus.fromAndroidStatus(rawStatus)
            val plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)
            val charging = status == BatteryStatus.CHARGING ||
                status == BatteryStatus.FULL ||
                plugged != 0

            val values = Values(capacity, voltageUv, temperatureDeciC, status, charging)
            lastGood = values
            values
        } catch (t: Throwable) {
            BLog.throttledError("Battery", "bm-fallback", "BatteryManager read failed", t)
            lastGood
        }
    }
}
