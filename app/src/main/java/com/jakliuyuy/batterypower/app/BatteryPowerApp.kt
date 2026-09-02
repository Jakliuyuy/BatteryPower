package com.jakliuyuy.batterypower.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import com.jakliuyuy.batterypower.core.config.ConfigStore
import com.jakliuyuy.batterypower.core.log.BLog

/**
 * Application entry point. Initializes configuration, logging and the
 * notification channel required by the foreground overlay service.
 *
 * This class is also instantiated when SystemUI queries the ConfigProvider and
 * the app process is not running yet: it must stay cheap and exception free.
 */
class BatteryPowerApp : Application() {

    override fun onCreate() {
        super.onCreate()
        try {
            val config = ConfigStore.get(this).get()
            BLog.setDebugEnabled(config.flags.enableDebugLog)
            createNotificationChannel()
            BLog.i("App", "BatteryPower ${Build.VERSION.RELEASE} initialized")
        } catch (t: Throwable) {
            BLog.e("App", "initialization failed", t)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        try {
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                ?: return
            val existing = manager.getNotificationChannel(CHANNEL_ID)
            if (existing != null) return
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(com.jakliuyuy.batterypower.R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(com.jakliuyuy.batterypower.R.string.notification_text)
                setShowBadge(false)
            }
            manager.createNotificationChannel(channel)
        } catch (t: Throwable) {
            BLog.w("App", "failed to create notification channel: ${t.message}")
        }
    }

    companion object {
        const val CHANNEL_ID = "battery_power_monitor"
        const val NOTIFICATION_ID = 0x4C42
    }
}
