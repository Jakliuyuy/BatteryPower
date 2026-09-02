package com.jakliuyuy.batterypower.core.config

/**
 * Configuration model (spec sections 34-38, 92, 139, 141, 152).
 * Immutable data classes: every mutation produces a new instance.
 */

const val SCHEMA_VERSION = 2

enum class ThemeMode { FOLLOW_SYSTEM, LIGHT, DARK }

/** 0 = normal, 1 = medium, 2 = bold (spec section 11). */
const val FONT_STYLE_NORMAL = 0
const val FONT_STYLE_MEDIUM = 1
const val FONT_STYLE_BOLD = 2

/** 0 = default sans, 1 = monospace digits (spec section 141.1). */
const val FONT_FAMILY_DEFAULT = 0
const val FONT_FAMILY_MONOSPACE = 1

/** Status bar anchor (spec section 20, 144.1). */
const val ANCHOR_CLOCK_LEFT = 0
const val ANCHOR_CLOCK_RIGHT = 1

/** Overlay layout (spec section 9). */
const val LAYOUT_SINGLE_LINE = 0
const val LAYOUT_AUTO_WRAP = 1

const val DEFAULT_COLOR_ARGB: Int = 0xFFFFFFFF.toInt()
const val DEFAULT_ACCENT_ARGB: Int = 0xFF4DD0E1.toInt()

const val OVERLAY_FONT_MIN_SP = 8f
const val OVERLAY_FONT_MAX_SP = 28f
const val STATUSBAR_FONT_MIN_SP = 8f
const val STATUSBAR_FONT_MAX_SP = 24f

const val OFFSET_X_MIN = -2000
const val OFFSET_X_MAX = 2000
const val OFFSET_Y_MIN = -1000
const val OFFSET_Y_MAX = 1000
const val OFFSET_STEP = 10

const val REFRESH_500 = 500L
const val REFRESH_1000 = 1000L
const val REFRESH_2000 = 2000L
const val REFRESH_3000 = 3000L
const val REFRESH_5000 = 5000L

const val NO_POSITION = Int.MIN_VALUE

data class ThemeConfig(
    val mode: ThemeMode = ThemeMode.FOLLOW_SYSTEM,
    val accentArgb: Int = DEFAULT_ACCENT_ARGB
)

data class OverlayConfig(
    val enabled: Boolean = false,
    val x: Int = NO_POSITION,
    val y: Int = NO_POSITION,
    val fontSizeSp: Float = 14f,
    val fontStyle: Int = FONT_STYLE_MEDIUM,
    val fontFamily: Int = FONT_FAMILY_MONOSPACE,
    val refreshMs: Long = REFRESH_1000,
    val showUnit: Boolean = true,
    val autoWrap: Boolean = false,
    val locked: Boolean = false,
    val letterSpacing: Float = 0f,
    val lineSpacingExtra: Float = 0f,
    val backgroundAlpha: Int = 0,
    val glow: Boolean = false
)

data class StatusBarConfig(
    val enabled: Boolean = false,
    val anchor: Int = ANCHOR_CLOCK_LEFT,
    val offsetX: Int = 0,
    val offsetY: Int = 0,
    val fontSizeSp: Float = 12f,
    val fontStyle: Int = FONT_STYLE_MEDIUM,
    val refreshMs: Long = REFRESH_1000,
    val showUnit: Boolean = true,
    val gapDp: Int = 8,
    val maxWidthDp: Int = 140,
    val autoScale: Boolean = true,
    val adaptiveFields: Boolean = true
)

data class DisplayConfig(
    val power: Boolean = true,
    val current: Boolean = true,
    val voltage: Boolean = true,
    val temperature: Boolean = false,
    val capacity: Boolean = false
) {
    companion object {
        const val ORDER_DEFAULT = "power,current,voltage,temperature,capacity"

        /** Spec section 37: Power can never be the last field removed. */
        fun sanitize(config: DisplayConfig): DisplayConfig {
            if (config.power || config.current || config.voltage ||
                config.temperature || config.capacity
            ) return config
            return config.copy(power = true)
        }
    }
}

data class PrecisionConfig(
    val powerDecimals: Int = 2,
    val currentDecimals: Int = 0,
    val voltageDecimals: Int = 3,
    val temperatureDecimals: Int = 1,
    val monospaceDigits: Boolean = true
)

data class ColorConfig(
    val argb: Int = DEFAULT_COLOR_ARGB,
    val autoColor: Boolean = false
)

data class FeatureFlags(
    val enableOverlay: Boolean = true,
    val enableStatusBarHook: Boolean = true,
    val enableRootReader: Boolean = true,
    val enableBatteryManagerFallback: Boolean = true,
    val enableDebugLog: Boolean = false,
    val enableAutoScale: Boolean = true,
    val enableAdaptiveFields: Boolean = true,
    val enableAnimations: Boolean = true,
    val enableAdvancedTheme: Boolean = false
)

data class AppConfig(
    val schemaVersion: Int = SCHEMA_VERSION,
    val configVersion: Int = 1,
    val onboarded: Boolean = false,
    val theme: ThemeConfig = ThemeConfig(),
    val overlay: OverlayConfig = OverlayConfig(),
    val statusBar: StatusBarConfig = StatusBarConfig(),
    val display: DisplayConfig = DisplayConfig(),
    val precision: PrecisionConfig = PrecisionConfig(),
    val overlayColor: ColorConfig = ColorConfig(),
    val statusBarColor: ColorConfig = ColorConfig(),
    val flags: FeatureFlags = FeatureFlags()
) {
    companion object {
        fun defaults(): AppConfig = AppConfig()
    }
}
