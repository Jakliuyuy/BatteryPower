package com.jakliuyuy.batterypower.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import com.jakliuyuy.batterypower.core.log.BLog

/** Overlay permission and root helpers shared by the UI (spec sections 62, 94). */
object PermissionHelper {

    fun canDrawOverlays(context: Context): Boolean =
        com.jakliuyuy.batterypower.app.OverlayService.canDrawOverlays(context)

    fun requestOverlayPermission(context: Context, launcher: (Intent) -> Unit) {
        try {
            if (canDrawOverlays(context)) return
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:${context.packageName}")
            )
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            launcher(intent)
        } catch (t: Throwable) {
            BLog.w("Permission", "overlay permission request failed: ${t.message}")
            try {
                Toast.makeText(context, "无法打开悬浮窗权限设置", Toast.LENGTH_SHORT).show()
            } catch (ignored: Throwable) {
            }
        }
    }

    fun openAppDetails(context: Context) {
        try {
            val intent = Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:${context.packageName}")
            )
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } catch (t: ActivityNotFoundException) {
            BLog.w("Permission", "app details not available")
        }
    }
}
