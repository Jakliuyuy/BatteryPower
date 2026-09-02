package com.jakliuyuy.batterypower.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.jakliuyuy.batterypower.core.config.ConfigStore
import com.jakliuyuy.batterypower.core.log.BLog

/**
 * Restores the overlay after boot / package update, but only when the user
 * actually enabled it (spec section 149).
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        try {
            val action = intent.action
            if (action != Intent.ACTION_BOOT_COMPLETED &&
                action != Intent.ACTION_LOCKED_BOOT_COMPLETED &&
                action != Intent.ACTION_MY_PACKAGE_REPLACED
            ) return

            val config = ConfigStore.get(context).get()
            if (!config.overlay.enabled || !config.flags.enableOverlay) {
                BLog.i("Boot", "overlay disabled, nothing to restore")
                return
            }
            OverlayService.start(context)
            BLog.i("Boot", "overlay restored after $action")
        } catch (t: Throwable) {
            BLog.e("Boot", "boot restore failed", t)
        }
    }
}
