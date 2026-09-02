package com.jakliuyuy.batterypower.app

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import com.jakliuyuy.batterypower.R
import com.jakliuyuy.batterypower.core.config.AppConfig
import com.jakliuyuy.batterypower.core.config.ConfigStore
import com.jakliuyuy.batterypower.core.log.BLog
import com.jakliuyuy.batterypower.core.model.BatterySnapshot
import com.jakliuyuy.batterypower.ui.MainActivity
import kotlin.math.roundToInt

/**
 * Foreground service hosting the draggable overlay (spec sections 61-66, 104,
 * 105, 149).
 *
 * - START_STICKY: after being killed the service restarts and restores position
 * - TYPE_APPLICATION_OVERLAY with a transparent, non-focusable window
 * - reads configuration from ConfigStore and data from BatteryEngine
 */
class OverlayService : Service() {

    private var windowManager: WindowManager? = null
    private var overlayView: OverlayView? = null
    private var params: WindowManager.LayoutParams? = null

    private val configStore by lazy { ConfigStore.get(this) }
    private val engine by lazy { BatteryEngine.get(this) }

    private var lastUiUpdateMs = 0L
    private var refreshMs = 1000L

    private val configListener: (AppConfig) -> Unit = { config ->
        mainHandler.post { applyConfig(config) }
    }

    private val batteryListener: (BatterySnapshot) -> Unit = { snapshot ->
        mainHandler.post { onSnapshot(snapshot) }
    }

    private val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())

    override fun onCreate() {
        super.onCreate()
        try {
            windowManager = getSystemService(WINDOW_SERVICE) as? WindowManager
            startInForeground()
            createView()
            val config = configStore.get()
            applyConfig(config)
            configStore.addListener(configListener)
            engine.acquire()
            engine.addListener(batteryListener)
            engine.refreshSampleInterval()
            BLog.i("Overlay", "service created")
        } catch (t: Throwable) {
            BLog.e("Overlay", "service creation failed", t)
            stopSelf()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Restore after being killed (spec 65).
        if (overlayView == null) {
            try {
                createView()
                applyConfig(configStore.get())
            } catch (t: Throwable) {
                BLog.e("Overlay", "failed to restore overlay", t)
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        try {
            // Spec 104, 105: re-clamp the window after rotation / size change.
            val view = overlayView ?: return
            val p = params ?: return
            val (cx, cy) = clampToScreen(p.x, p.y, view)
            p.x = cx
            p.y = cy
            view.setWindowPosition(cx, cy)
            windowManager?.updateViewLayout(view, p)
            configStore.updateSync { it.copy(overlay = it.overlay.copy(x = cx, y = cy)) }
        } catch (t: Throwable) {
            BLog.w("Overlay", "configuration change handling failed: ${t.message}")
        }
    }

    override fun onDestroy() {
        try {
            configStore.removeListener(configListener)
            engine.removeListener(batteryListener)
            engine.release()
            removeView()
        } catch (t: Throwable) {
            BLog.w("Overlay", "destroy failed: ${t.message}")
        }
        super.onDestroy()
        BLog.i("Overlay", "service destroyed")
    }

    // ------------------------------------------------------------------ setup

    private fun startInForeground() {
        val notification = buildNotification()
        try {
            if (Build.VERSION.SDK_INT >= 29) {
                startForeground(
                    BatteryPowerApp.NOTIFICATION_ID,
                    notification,
                    android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                )
            } else {
                startForeground(BatteryPowerApp.NOTIFICATION_ID, notification)
            }
        } catch (t: Throwable) {
            BLog.w("Overlay", "startForeground failed: ${t.message}")
            try {
                startForeground(BatteryPowerApp.NOTIFICATION_ID, notification)
            } catch (t2: Throwable) {
                BLog.e("Overlay", "startForeground fallback failed", t2)
            }
        }
    }

    private fun buildNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val flags = if (Build.VERSION.SDK_INT >= 23) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        val pendingIntent = PendingIntent.getActivity(this, 0, intent, flags)
        return NotificationCompat.Builder(this, BatteryPowerApp.CHANNEL_ID)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(getString(R.string.notification_text))
            .setSmallIcon(R.drawable.ic_stat_battery)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun createView() {
        if (overlayView != null) return
        val view = OverlayView(this)
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }
        val p = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
        }
        overlayView = view
        params = p
        view.dragCallback = object : OverlayView.DragCallback {
            override fun onDrag(x: Int, y: Int) {
                val p2 = params ?: return
                val (cx, cy) = clampToScreen(x, y, view)
                p2.x = cx
                p2.y = cy
                view.setWindowPosition(cx, cy)
                try {
                    windowManager?.updateViewLayout(view, p2)
                } catch (t: Throwable) {
                    BLog.w("Overlay", "drag update failed: ${t.message}")
                }
            }

            override fun onDragEnd(x: Int, y: Int) {
                val (cx, cy) = clampToScreen(x, y, view)
                // Spec 17, 143.1: final position is written synchronously.
                configStore.commitPosition(cx, cy)
                BLog.d("Overlay", "position saved ($cx,$cy)")
            }
        }
        val config = configStore.get()
        val (startX, startY) = resolveStartPosition(config, view)
        p.x = startX
        p.y = startY
        view.setWindowPosition(startX, startY)
        try {
            windowManager?.addView(view, p)
        } catch (t: Throwable) {
            BLog.e("Overlay", "addView failed", t)
            overlayView = null
            params = null
        }
    }

    private fun removeView() {
        try {
            overlayView?.let { windowManager?.removeView(it) }
        } catch (t: Throwable) {
            BLog.w("Overlay", "removeView failed: ${t.message}")
        }
        overlayView = null
        params = null
        try {
            if (Build.VERSION.SDK_INT >= 24) {
                stopForeground(STOP_FOREGROUND_REMOVE)
            } else {
                @Suppress("DEPRECATION")
                stopForeground(true)
            }
        } catch (t: Throwable) {
            BLog.w("Overlay", "stopForeground failed")
        }
    }

    private fun applyConfig(config: AppConfig) {
        val view = overlayView
        if (view == null) {
            createView()
        }
        refreshMs = config.overlay.refreshMs
        overlayView?.applyConfig(config)
        lastUiUpdateMs = 0L
        overlayView?.update(engine.lastSnapshot())
    }

    private fun onSnapshot(snapshot: BatterySnapshot) {
        val now = android.os.SystemClock.elapsedRealtime()
        if (now - lastUiUpdateMs < refreshMs) return
        lastUiUpdateMs = now
        overlayView?.update(snapshot)
    }

    // ------------------------------------------------------------- positioning

    private fun resolveStartPosition(config: AppConfig, view: OverlayView): Pair<Int, Int> {
        val metrics = resources.displayMetrics
        val savedX = config.overlay.x
        val savedY = config.overlay.y
        val hasSaved = savedX != com.jakliuyuy.batterypower.core.config.NO_POSITION &&
            savedY != com.jakliuyuy.batterypower.core.config.NO_POSITION
        val rawX = if (hasSaved) savedX else (metrics.widthPixels * 0.08f).roundToInt()
        val rawY = if (hasSaved) savedY else (metrics.heightPixels * 0.12f).roundToInt()
        return clampToScreen(rawX, rawY, view)
    }

    /** Keeps at least 10px of the window visible (spec section 18). */
    private fun clampToScreen(x: Int, y: Int, view: OverlayView): Pair<Int, Int> {
        val wm = windowManager ?: return x to y
        return try {
            val metrics = android.util.DisplayMetrics()
            wm.defaultDisplay.getRealMetrics(metrics)
            val width = view.measuredWidth.takeIf { it > 0 }
                ?: view.measureCurrentWidth().takeIf { it > 0 }
                ?: (120 * metrics.density).roundToInt()
            val height = view.measuredHeight.takeIf { it > 0 }
                ?: (32 * metrics.density).roundToInt()
            val margin = 10
            val maxX = (metrics.widthPixels - width + margin).coerceAtLeast(margin)
            val maxY = (metrics.heightPixels - height + margin).coerceAtLeast(margin)
            val cx = x.coerceIn(-0, maxX).coerceAtMost(maxX)
            val cy = y.coerceIn(0, maxY)
            cx to cy
        } catch (t: Throwable) {
            BLog.w("Overlay", "clamp failed: ${t.message}")
            x to y
        }
    }

    companion object {
        fun start(context: Context) {
            try {
                val intent = Intent(context, OverlayService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (t: Throwable) {
                BLog.e("Overlay", "start service failed", t)
            }
        }

        fun stop(context: Context) {
            try {
                context.stopService(Intent(context, OverlayService::class.java))
            } catch (t: Throwable) {
                BLog.e("Overlay", "stop service failed", t)
            }
        }

        fun canDrawOverlays(context: Context): Boolean {
            return try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    Settings.canDrawOverlays(context)
                } else true
            } catch (t: Throwable) {
                false
            }
        }
    }
}
