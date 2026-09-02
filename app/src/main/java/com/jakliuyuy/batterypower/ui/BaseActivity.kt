package com.jakliuyuy.batterypower.ui

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import com.jakliuyuy.batterypower.R
import com.jakliuyuy.batterypower.app.BatteryEngine
import com.jakliuyuy.batterypower.core.config.AppConfig
import com.jakliuyuy.batterypower.core.config.ConfigStore
import com.jakliuyuy.batterypower.core.log.BLog
import com.jakliuyuy.batterypower.core.model.BatterySnapshot

/**
 * Base class for all app screens: applies the user theme (mode + accent) and
 * wires configuration change notifications (spec sections 78, 139).
 */
abstract class BaseActivity : AppCompatActivity() {

    val configStore: ConfigStore by lazy { ConfigStore.get(this) }
    val engine: BatteryEngine by lazy { BatteryEngine.get(this) }

    private val configListener: (AppConfig) -> Unit = { config ->
        runOnUiThread {
            try {
                onConfigChanged(config)
            } catch (t: Throwable) {
                BLog.w("UI", "config listener failed: ${t.message}")
            }
        }
    }

    private val batteryListener: (BatterySnapshot) -> Unit = { snapshot ->
        runOnUiThread {
            try {
                onBatterySnapshot(snapshot)
            } catch (t: Throwable) {
                BLog.w("UI", "battery listener failed: ${t.message}")
            }
        }
    }

    private var listeningBattery = false

    protected open fun onConfigChanged(config: AppConfig) {}
    protected open fun onBatterySnapshot(snapshot: BatterySnapshot) {}

    protected open fun wantsBatteryUpdates(): Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        applyUserTheme()
        super.onCreate(savedInstanceState)
        configStore.addListener(configListener)
    }

    override fun onStart() {
        super.onStart()
        if (wantsBatteryUpdates() && !listeningBattery) {
            engine.addListener(batteryListener)
            listeningBattery = true
        }
    }

    override fun onResume() {
        super.onResume()
        engine.acquire()
        if (wantsBatteryUpdates() && !listeningBattery) {
            engine.addListener(batteryListener)
            listeningBattery = true
        }
    }

    override fun onPause() {
        engine.release()
        super.onPause()
    }

    override fun onStop() {
        if (listeningBattery) {
            engine.removeListener(batteryListener)
            listeningBattery = false
        }
        super.onStop()
    }

    override fun onDestroy() {
        configStore.removeListener(configListener)
        if (listeningBattery) {
            engine.removeListener(batteryListener)
            listeningBattery = false
        }
        super.onDestroy()
    }

    private fun applyUserTheme() {
        try {
            val config = ConfigStore.get(this).get()
            val nightMode = when (config.theme.mode) {
                com.jakliuyuy.batterypower.core.config.ThemeMode.LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
                com.jakliuyuy.batterypower.core.config.ThemeMode.DARK -> AppCompatDelegate.MODE_NIGHT_YES
                com.jakliuyuy.batterypower.core.config.ThemeMode.FOLLOW_SYSTEM -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            }
            if (AppCompatDelegate.getDefaultNightMode() != nightMode) {
                AppCompatDelegate.setDefaultNightMode(nightMode)
            }
            setTheme(R.style.Theme_BatteryPower)
            theme.applyStyle(accentStyleFor(config.theme.accentArgb), true)
        } catch (t: Throwable) {
            BLog.w("UI", "theme application failed: ${t.message}")
        }
    }

    private fun accentStyleFor(argb: Int): Int {
        // Accent presets only affect the app UI, never the overlay/status bar colours.
        return when (argb) {
            0xFF5B8DEF.toInt() -> R.style.OverlayAccent_Blue
            0xFF42C76A.toInt() -> R.style.OverlayAccent_Green
            0xFFFFB300.toInt() -> R.style.OverlayAccent_Amber
            0xFFB06BEF.toInt() -> R.style.OverlayAccent_Purple
            0xFFFFFFFF.toInt() -> R.style.OverlayAccent_White
            else -> R.style.OverlayAccent_Blue
        }
    }
}
