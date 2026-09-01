package com.jakliuyuy.batterypower.overlay

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import com.jakliuyuy.batterypower.MainActivity
import com.jakliuyuy.batterypower.R
import com.jakliuyuy.batterypower.battery.BatteryReader
import com.jakliuyuy.batterypower.data.ConfigRepository

/**
 * 承载悬浮窗的前台服务。
 *
 * - 悬浮窗类型 TYPE_APPLICATION_OVERLAY（Android 8+ 唯一允许的应用层悬浮窗）
 * - START_STICKY + 常驻通知保活
 * - 配置变更通过 ACTION_CONFIG_CHANGED 广播实时生效（无需重启服务）
 */
class OverlayService : Service() {

    private lateinit var reader: BatteryReader
    private lateinit var repo: ConfigRepository
    private var overlayView: BatteryOverlayView? = null
    private var windowManager: WindowManager? = null

    override fun onCreate() {
        super.onCreate()
        repo = ConfigRepository(this)
        reader = BatteryReader(this)
        reader.prepareAsync()
        reader.onUpdate = { snapshot -> overlayView?.update(snapshot) }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFY_ID, buildNotification())

        when (intent?.action) {
            ACTION_STOP -> {
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_CONFIG_CHANGED -> {
                applyConfig()
                return START_STICKY
            }
        }

        showOverlay()
        return START_STICKY
    }

    private fun showOverlay() {
        if (overlayView != null) {
            applyConfig()
            return
        }
        val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        windowManager = wm

        val cfg = repo.get()
        val view = BatteryOverlayView(this)
        view.applyConfig(cfg)
        view.onPositionChanged = { x, y ->
            // 避坑 7：位置必须 commit() 同步落盘，apply() 会在杀后台时丢
            repo.update(sync = true) { it.copy(overlayX = x, overlayY = y) }
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = cfg.overlayX
            y = cfg.overlayY
        }

        try {
            wm.addView(view, params)
            overlayView = view
            reader.start(cfg.overlayIntervalMs)
        } catch (e: Exception) {
            android.util.Log.e(TAG, "添加悬浮窗失败: ${e.message}")
            stopSelf()
        }
    }

    /** 配置热更新：改颜色/字号/显示项/间隔后立即生效 */
    private fun applyConfig() {
        val cfg = repo.get()
        overlayView?.applyConfig(cfg)
        val params = overlayView?.layoutParams as? WindowManager.LayoutParams
        if (params != null && overlayView?.isAttached == true) {
            params.x = cfg.overlayX
            params.y = cfg.overlayY
            try {
                windowManager?.updateViewLayout(overlayView, params)
            } catch (_: Exception) {
            }
        }
        reader.start(cfg.overlayIntervalMs)
    }

    private fun buildNotification(): Notification {
        createChannel()
        val pi = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or immutableFlag()
        )
        val stopPi = PendingIntent.getService(
            this, 1,
            Intent(this, OverlayService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or immutableFlag()
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.notify_title))
            .setContentText(getString(R.string.notify_text))
            .setSmallIcon(R.drawable.ic_bolt_small)
            .setContentIntent(pi)
            .setOngoing(true)
            .setCategory(Notification.CATEGORY_SERVICE)
            .addAction(0, getString(R.string.notify_action_stop), stopPi)
            .build()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val mgr = getSystemService(NotificationManager::class.java)
            if (mgr.getNotificationChannel(CHANNEL_ID) == null) {
                mgr.createNotificationChannel(
                    NotificationChannel(
                        CHANNEL_ID,
                        getString(R.string.notify_channel_name),
                        NotificationManager.IMPORTANCE_LOW
                    ).apply {
                        setShowBadge(false)
                    }
                )
            }
        }
    }

    private fun immutableFlag(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0

    override fun onDestroy() {
        reader.destroy()
        overlayView?.let {
            try {
                windowManager?.removeView(it)
            } catch (_: Exception) {
            }
        }
        overlayView = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val TAG = "BatteryPower/Overlay"
        private const val CHANNEL_ID = "batterypower_overlay"
        private const val NOTIFY_ID = 10086

        const val ACTION_STOP = "com.jakliuyuy.batterypower.ACTION_STOP"
        const val ACTION_CONFIG_CHANGED = "com.jakliuyuy.batterypower.ACTION_CONFIG_CHANGED"

        fun start(context: Context) {
            val i = Intent(context, OverlayService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(i)
            } else {
                context.startService(i)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, OverlayService::class.java))
        }

        /** 通知服务重新读取配置并热更新（无需重启服务） */
        fun notifyConfigChanged(context: Context) {
            try {
                context.startService(
                    Intent(context, OverlayService::class.java).setAction(ACTION_CONFIG_CHANGED)
                )
            } catch (e: Exception) {
                android.util.Log.w(TAG, "通知配置变更失败: ${e.message}")
            }
        }
    }
}

