package com.jakliuyuy.batterypower.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Handler
import android.os.Looper
import com.jakliuyuy.batterypower.core.log.BLog

/**
 * Best-effort LSPosed / SystemUI hook status detection (spec sections 83, 95).
 *
 * The app never claims to detect module activation with certainty: the UI only
 * reports "detected / not detected" and the last time the hook answered a ping.
 */
object ModuleStatus {

    const val ACTION_PING = "com.jakliuyuy.batterypower.PING"
    const val ACTION_ALIVE = "com.jakliuyuy.batterypower.HOOK_ALIVE"
    const val SYSTEMUI_PACKAGE = "com.android.systemui"

    enum class HookState { NOT_RUNNING, WAITING_RESTART, RUNNING }

    @Volatile
    private var lastAliveMs = 0L

    @Volatile
    private var pingInFlight = false

    fun markAlive() {
        lastAliveMs = System.currentTimeMillis()
    }

    fun lastAliveMs(): Long = lastAliveMs

    fun hookState(): HookState {
        val last = lastAliveMs
        return when {
            last == 0L -> HookState.WAITING_RESTART
            System.currentTimeMillis() - last > 5 * 60_000L -> HookState.WAITING_RESTART
            else -> HookState.RUNNING
        }
    }

    fun isLsposedManagerInstalled(context: Context): Boolean {
        return try {
            val pm = context.packageManager
            val packages = listOf(
                "org.lsposed.manager",
                "org.lsposed.manager.stub",
                "com.android.shell"
            )
            packages.any { pkg ->
                try {
                    if (Build.VERSION.SDK_INT >= 33) {
                        pm.getPackageInfo(pkg, android.content.pm.PackageManager.PackageInfoFlags.of(0))
                    } else {
                        @Suppress("DEPRECATION")
                        pm.getPackageInfo(pkg, 0)
                    }
                    true
                } catch (t: Throwable) {
                    false
                }
            }
        } catch (t: Throwable) {
            false
        }
    }

    /**
     * Asks the SystemUI hook to report itself. The result arrives via broadcast;
     * the callback is invoked once with the resulting state.
     */
    fun pingHook(context: Context, callback: (HookState) -> Unit) {
        if (pingInFlight) return
        pingInFlight = true
        val handler = Handler(Looper.getMainLooper())
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                markAlive()
                try {
                    context.unregisterReceiver(this)
                } catch (t: Throwable) {
                    // ignore
                }
                handler.removeCallbacksAndMessages(null)
                pingInFlight = false
                callback(HookState.RUNNING)
            }
        }
        try {
            val filter = IntentFilter(ACTION_ALIVE)
            if (Build.VERSION.SDK_INT >= 33) {
                context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                context.registerReceiver(receiver, filter)
            }
            val ping = Intent(ACTION_PING).apply {
                setPackage(SYSTEMUI_PACKAGE)
                addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
            }
            context.sendBroadcast(ping)
            handler.postDelayed({
                try {
                    context.unregisterReceiver(receiver)
                } catch (t: Throwable) {
                    // ignore
                }
                pingInFlight = false
                callback(hookState())
            }, 1500L)
        } catch (t: Throwable) {
            BLog.w("Status", "hook ping failed: ${t.message}")
            pingInFlight = false
            callback(HookState.NOT_RUNNING)
        }
    }
}
