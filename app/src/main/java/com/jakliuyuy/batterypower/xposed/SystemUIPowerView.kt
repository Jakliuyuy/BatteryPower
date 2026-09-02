package com.jakliuyuy.batterypower.xposed

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.text.TextUtils
import android.util.TypedValue
import android.widget.TextView
import com.jakliuyuy.batterypower.core.config.AppConfig
import com.jakliuyuy.batterypower.core.config.STATUSBAR_FONT_MIN_SP
import com.jakliuyuy.batterypower.core.format.BatteryFormatter
import com.jakliuyuy.batterypower.core.model.BatterySnapshot
import kotlin.math.roundToInt

/**
 * The status bar text view (spec sections 19, 76, 139.4, 142).
 *
 * - transparent, borderless, shadowless: only text is drawn
 * - unique tag prevents duplicate injection
 * - adaptive typography shrinks the font and then drops low priority fields
 */
class SystemUIPowerView(context: Context) : TextView(context) {

    companion object {
        const val TAG_POWER_VIEW = "battery_power_overlay"
    }

    private var currentConfig: AppConfig? = null
    private var currentSnapshot: BatterySnapshot = BatterySnapshot.empty()
    private var maxWidthPx = 0

    init {
        tag = TAG_POWER_VIEW
        setBackgroundColor(Color.TRANSPARENT)
        includeFontPadding = false
        setSingleLine(true)
        maxLines = 1
        isClickable = false
        isFocusable = false
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        // Spec 33: keep the view above notification icons.
        elevation = 100f
        translationZ = 100f
    }

    fun applyConfig(config: AppConfig) {
        currentConfig = config
        val sb = config.statusBar
        setTextSize(TypedValue.COMPLEX_UNIT_SP, sb.fontSizeSp)
        // Spec 79: automatic colour adapts to the status bar background, manual
        // colour always wins.
        setTextColor(
            if (config.statusBarColor.autoColor) autoTextColor() else config.statusBarColor.argb
        )
        typeface = resolveTypeface(sb.fontStyle)
        letterSpacing = 0f
        maxWidthPx = (sb.maxWidthDp * resources.displayMetrics.density).roundToInt()
        render(currentSnapshot)
    }

    fun render(snapshot: BatterySnapshot) {
        currentSnapshot = snapshot
        val config = currentConfig ?: return
        val options = BatteryFormatter.Options(
            showUnit = config.statusBar.showUnit,
            powerDecimals = config.precision.powerDecimals,
            currentDecimals = config.precision.currentDecimals,
            voltageDecimals = config.precision.voltageDecimals,
            temperatureDecimals = config.precision.temperatureDecimals
        )
        var fields = BatteryFormatter.Fields(
            power = config.display.power,
            current = config.display.current,
            voltage = config.display.voltage,
            temperature = config.display.temperature,
            capacity = config.display.capacity
        )
        var text = BatteryFormatter.buildText(snapshot, fields, options)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, config.statusBar.fontSizeSp)

        if (maxWidthPx > 0) {
            if (config.flags.enableAutoScale && config.statusBar.autoScale) {
                text = shrinkToFit(text, options, fields, snapshot, config)
            }
            if (config.flags.enableAdaptiveFields && config.statusBar.adaptiveFields) {
                text = dropFieldsToFit(text, options, fields, snapshot, config)
            }
        }
        if (this.text?.toString() == text) return
        this.text = text
        ellipsize = TextUtils.TruncateAt.END
    }

    /** Step 1 of adaptation: shrink the font, never below 8sp (spec 76). */
    private fun shrinkToFit(
        text: String,
        options: BatteryFormatter.Options,
        fields: BatteryFormatter.Fields,
        snapshot: BatterySnapshot,
        config: AppConfig
    ): String {
        var sizeSp = config.statusBar.fontSizeSp
        var guard = 0
        while (measure(text) > maxWidthPx && sizeSp > STATUSBAR_FONT_MIN_SP && guard < 40) {
            sizeSp = (sizeSp - 0.5f).coerceAtLeast(STATUSBAR_FONT_MIN_SP)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp)
            guard++
        }
        return text
    }

    /** Step 2: drop low priority fields (Power > Current > Voltage > Temp > Capacity). */
    private fun dropFieldsToFit(
        text: String,
        options: BatteryFormatter.Options,
        fields: BatteryFormatter.Fields,
        snapshot: BatterySnapshot,
        config: AppConfig
    ): String {
        if (measure(text) <= maxWidthPx) return text
        var current = fields
        val order = listOf("capacity", "temperature", "voltage", "current")
        for (drop in order) {
            current = when (drop) {
                "capacity" -> current.copy(capacity = false)
                "temperature" -> current.copy(temperature = false)
                "voltage" -> current.copy(voltage = false)
                "current" -> current.copy(current = false)
                else -> current
            }
            if (!current.anyEnabled()) break
            val candidate = BatteryFormatter.buildText(snapshot, current, options)
            if (measure(candidate) <= maxWidthPx) return candidate
        }
        return BatteryFormatter.buildText(snapshot, current, options)
    }

    private fun measure(text: String): Float = try {
        paint.measureText(text) + paddingLeft + paddingRight
    } catch (t: Throwable) {
        0f
    }

    /** Dark status bar -> light text, light status bar -> dark text (spec 79). */
    private fun autoTextColor(): Int {
        return try {
            val night = (resources.configuration.uiMode and
                android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
                android.content.res.Configuration.UI_MODE_NIGHT_YES
            if (night) 0xFFFFFFFF.toInt() else 0xFF000000.toInt()
        } catch (t: Throwable) {
            0xFFFFFFFF.toInt()
        }
    }

    fun measuredTextWidth(): Int = try {
        measure(text?.toString() ?: "").roundToInt()
    } catch (t: Throwable) {
        0
    }

    private fun resolveTypeface(style: Int): Typeface {
        val base = Typeface.DEFAULT
        return when (style) {
            2 -> Typeface.create(base, Typeface.BOLD)
            1 -> if (android.os.Build.VERSION.SDK_INT >= 28) {
                Typeface.create(base, 500, false)
            } else {
                Typeface.create(base, Typeface.BOLD)
            }
            else -> Typeface.create(base, Typeface.NORMAL)
        }
    }
}
