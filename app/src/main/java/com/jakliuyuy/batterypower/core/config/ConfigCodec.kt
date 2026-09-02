package com.jakliuyuy.batterypower.core.config

/**
 * Flat key/value codec shared by three transports:
 *  - SharedPreferences (app process)
 *  - ContentProvider rows (app -> SystemUI)
 *  - SystemUI local cache
 *
 * Unknown or malformed entries fall back to the default value so that a broken
 * key can never produce a half-configured state (spec sections 108, 109).
 */
object ConfigCodec {

    // --- keys -------------------------------------------------------------
    const val K_SCHEMA = "schema_version"
    const val K_CONFIG_VERSION = "config_version"
    const val K_ONBOARDED = "onboarded"
    const val K_THEME_MODE = "theme.mode"
    const val K_THEME_ACCENT = "theme.accent_argb"

    const val K_OV_ENABLED = "overlay.enabled"
    const val K_OV_X = "overlay.x"
    const val K_OV_Y = "overlay.y"
    const val K_OV_FONT_SIZE = "overlay.font_size_sp"
    const val K_OV_FONT_STYLE = "overlay.font_style"
    const val K_OV_FONT_FAMILY = "overlay.font_family"
    const val K_OV_REFRESH = "overlay.refresh_ms"
    const val K_OV_SHOW_UNIT = "overlay.show_unit"
    const val K_OV_AUTO_WRAP = "overlay.auto_wrap"
    const val K_OV_LOCKED = "overlay.locked"
    const val K_OV_LETTER_SPACING = "overlay.letter_spacing"
    const val K_OV_LINE_SPACING = "overlay.line_spacing_extra"
    const val K_OV_BG_ALPHA = "overlay.background_alpha"
    const val K_OV_GLOW = "overlay.glow"

    const val K_SB_ENABLED = "statusbar.enabled"
    const val K_SB_ANCHOR = "statusbar.anchor"
    const val K_SB_OFFSET_X = "statusbar.offset_x"
    const val K_SB_OFFSET_Y = "statusbar.offset_y"
    const val K_SB_FONT_SIZE = "statusbar.font_size_sp"
    const val K_SB_FONT_STYLE = "statusbar.font_style"
    const val K_SB_REFRESH = "statusbar.refresh_ms"
    const val K_SB_SHOW_UNIT = "statusbar.show_unit"
    const val K_SB_GAP_DP = "statusbar.gap_dp"
    const val K_SB_MAX_WIDTH_DP = "statusbar.max_width_dp"
    const val K_SB_AUTO_SCALE = "statusbar.auto_scale"
    const val K_SB_ADAPTIVE_FIELDS = "statusbar.adaptive_fields"

    const val K_DP_POWER = "display.power"
    const val K_DP_CURRENT = "display.current"
    const val K_DP_VOLTAGE = "display.voltage"
    const val K_DP_TEMPERATURE = "display.temperature"
    const val K_DP_CAPACITY = "display.capacity"

    const val K_PR_POWER = "precision.power_decimals"
    const val K_PR_CURRENT = "precision.current_decimals"
    const val K_PR_VOLTAGE = "precision.voltage_decimals"
    const val K_PR_TEMPERATURE = "precision.temperature_decimals"
    const val K_PR_MONO = "precision.monospace_digits"

    const val K_OC_ARGB = "overlay_color.argb"
    const val K_OC_AUTO = "overlay_color.auto"
    const val K_SC_ARGB = "statusbar_color.argb"
    const val K_SC_AUTO = "statusbar_color.auto"

    const val K_FF_OVERLAY = "flags.enable_overlay"
    const val K_FF_STATUS_BAR = "flags.enable_statusbar_hook"
    const val K_FF_ROOT_READER = "flags.enable_root_reader"
    const val K_FF_BM_FALLBACK = "flags.enable_batterymanager_fallback"
    const val K_FF_DEBUG_LOG = "flags.enable_debug_log"
    const val K_FF_AUTO_SCALE = "flags.enable_auto_scale"
    const val K_FF_ADAPTIVE_FIELDS = "flags.enable_adaptive_fields"
    const val K_FF_ANIMATIONS = "flags.enable_animations"
    const val K_FF_ADVANCED_THEME = "flags.enable_advanced_theme"

    const val K_UPDATED_AT = "updated_at"

    fun toMap(config: AppConfig): Map<String, String> {
        val map = LinkedHashMap<String, String>(64)
        map[K_SCHEMA] = config.schemaVersion.toString()
        map[K_CONFIG_VERSION] = config.configVersion.toString()
        map[K_ONBOARDED] = config.onboarded.toString()
        map[K_THEME_MODE] = config.theme.mode.name
        map[K_THEME_ACCENT] = config.theme.accentArgb.toString()

        val o = config.overlay
        map[K_OV_ENABLED] = o.enabled.toString()
        map[K_OV_X] = o.x.toString()
        map[K_OV_Y] = o.y.toString()
        map[K_OV_FONT_SIZE] = o.fontSizeSp.toString()
        map[K_OV_FONT_STYLE] = o.fontStyle.toString()
        map[K_OV_FONT_FAMILY] = o.fontFamily.toString()
        map[K_OV_REFRESH] = o.refreshMs.toString()
        map[K_OV_SHOW_UNIT] = o.showUnit.toString()
        map[K_OV_AUTO_WRAP] = o.autoWrap.toString()
        map[K_OV_LOCKED] = o.locked.toString()
        map[K_OV_LETTER_SPACING] = o.letterSpacing.toString()
        map[K_OV_LINE_SPACING] = o.lineSpacingExtra.toString()
        map[K_OV_BG_ALPHA] = o.backgroundAlpha.toString()
        map[K_OV_GLOW] = o.glow.toString()

        val s = config.statusBar
        map[K_SB_ENABLED] = s.enabled.toString()
        map[K_SB_ANCHOR] = s.anchor.toString()
        map[K_SB_OFFSET_X] = s.offsetX.toString()
        map[K_SB_OFFSET_Y] = s.offsetY.toString()
        map[K_SB_FONT_SIZE] = s.fontSizeSp.toString()
        map[K_SB_FONT_STYLE] = s.fontStyle.toString()
        map[K_SB_REFRESH] = s.refreshMs.toString()
        map[K_SB_SHOW_UNIT] = s.showUnit.toString()
        map[K_SB_GAP_DP] = s.gapDp.toString()
        map[K_SB_MAX_WIDTH_DP] = s.maxWidthDp.toString()
        map[K_SB_AUTO_SCALE] = s.autoScale.toString()
        map[K_SB_ADAPTIVE_FIELDS] = s.adaptiveFields.toString()

        val d = config.display
        map[K_DP_POWER] = d.power.toString()
        map[K_DP_CURRENT] = d.current.toString()
        map[K_DP_VOLTAGE] = d.voltage.toString()
        map[K_DP_TEMPERATURE] = d.temperature.toString()
        map[K_DP_CAPACITY] = d.capacity.toString()

        val p = config.precision
        map[K_PR_POWER] = p.powerDecimals.toString()
        map[K_PR_CURRENT] = p.currentDecimals.toString()
        map[K_PR_VOLTAGE] = p.voltageDecimals.toString()
        map[K_PR_TEMPERATURE] = p.temperatureDecimals.toString()
        map[K_PR_MONO] = p.monospaceDigits.toString()

        map[K_OC_ARGB] = config.overlayColor.argb.toString()
        map[K_OC_AUTO] = config.overlayColor.autoColor.toString()
        map[K_SC_ARGB] = config.statusBarColor.argb.toString()
        map[K_SC_AUTO] = config.statusBarColor.autoColor.toString()

        val f = config.flags
        map[K_FF_OVERLAY] = f.enableOverlay.toString()
        map[K_FF_STATUS_BAR] = f.enableStatusBarHook.toString()
        map[K_FF_ROOT_READER] = f.enableRootReader.toString()
        map[K_FF_BM_FALLBACK] = f.enableBatteryManagerFallback.toString()
        map[K_FF_DEBUG_LOG] = f.enableDebugLog.toString()
        map[K_FF_AUTO_SCALE] = f.enableAutoScale.toString()
        map[K_FF_ADAPTIVE_FIELDS] = f.enableAdaptiveFields.toString()
        map[K_FF_ANIMATIONS] = f.enableAnimations.toString()
        map[K_FF_ADVANCED_THEME] = f.enableAdvancedTheme.toString()

        map[K_UPDATED_AT] = System.currentTimeMillis().toString()
        return map
    }

    fun fromMap(map: Map<String, String>): AppConfig {
        fun int(key: String, default: Int) = map[key]?.toIntOrNull() ?: default
        fun long(key: String, default: Long) = map[key]?.toLongOrNull() ?: default
        fun float(key: String, default: Float) = map[key]?.toFloatOrNull() ?: default
        fun bool(key: String, default: Boolean) = map[key]?.toBooleanStrictOrNull() ?: default

        val themeMode = try {
            ThemeMode.valueOf(map[K_THEME_MODE] ?: "")
        } catch (t: Throwable) {
            ThemeMode.FOLLOW_SYSTEM
        }

        val config = AppConfig(
            schemaVersion = int(K_SCHEMA, SCHEMA_VERSION),
            configVersion = int(K_CONFIG_VERSION, 1).coerceAtLeast(1),
            onboarded = bool(K_ONBOARDED, false),
            theme = ThemeConfig(
                mode = themeMode,
                accentArgb = int(K_THEME_ACCENT, DEFAULT_ACCENT_ARGB)
            ),
            overlay = OverlayConfig(
                enabled = bool(K_OV_ENABLED, false),
                x = int(K_OV_X, NO_POSITION),
                y = int(K_OV_Y, NO_POSITION),
                fontSizeSp = float(K_OV_FONT_SIZE, 14f)
                    .coerceIn(OVERLAY_FONT_MIN_SP, OVERLAY_FONT_MAX_SP),
                fontStyle = int(K_OV_FONT_STYLE, FONT_STYLE_MEDIUM).coerceIn(0, 2),
                fontFamily = int(K_OV_FONT_FAMILY, FONT_FAMILY_MONOSPACE).coerceIn(0, 1),
                refreshMs = sanitizeOverlayRefresh(long(K_OV_REFRESH, REFRESH_1000)),
                showUnit = bool(K_OV_SHOW_UNIT, true),
                autoWrap = bool(K_OV_AUTO_WRAP, false),
                locked = bool(K_OV_LOCKED, false),
                letterSpacing = float(K_OV_LETTER_SPACING, 0f).coerceIn(-0.1f, 0.5f),
                lineSpacingExtra = float(K_OV_LINE_SPACING, 0f).coerceIn(0f, 16f),
                backgroundAlpha = int(K_OV_BG_ALPHA, 0).coerceIn(0, 255),
                glow = bool(K_OV_GLOW, false)
            ),
            statusBar = StatusBarConfig(
                enabled = bool(K_SB_ENABLED, false),
                anchor = if (int(K_SB_ANCHOR, ANCHOR_CLOCK_LEFT) == ANCHOR_CLOCK_RIGHT)
                    ANCHOR_CLOCK_RIGHT else ANCHOR_CLOCK_LEFT,
                offsetX = int(K_SB_OFFSET_X, 0).coerceIn(OFFSET_X_MIN, OFFSET_X_MAX),
                offsetY = int(K_SB_OFFSET_Y, 0).coerceIn(OFFSET_Y_MIN, OFFSET_Y_MAX),
                fontSizeSp = float(K_SB_FONT_SIZE, 12f)
                    .coerceIn(STATUSBAR_FONT_MIN_SP, STATUSBAR_FONT_MAX_SP),
                fontStyle = int(K_SB_FONT_STYLE, FONT_STYLE_MEDIUM).coerceIn(0, 2),
                refreshMs = sanitizeStatusBarRefresh(long(K_SB_REFRESH, REFRESH_1000)),
                showUnit = bool(K_SB_SHOW_UNIT, true),
                gapDp = int(K_SB_GAP_DP, 8).coerceIn(0, 48),
                maxWidthDp = int(K_SB_MAX_WIDTH_DP, 140).coerceIn(40, 400),
                autoScale = bool(K_SB_AUTO_SCALE, true),
                adaptiveFields = bool(K_SB_ADAPTIVE_FIELDS, true)
            ),
            display = DisplayConfig.sanitize(
                DisplayConfig(
                    power = bool(K_DP_POWER, true),
                    current = bool(K_DP_CURRENT, true),
                    voltage = bool(K_DP_VOLTAGE, true),
                    temperature = bool(K_DP_TEMPERATURE, false),
                    capacity = bool(K_DP_CAPACITY, false)
                )
            ),
            precision = PrecisionConfig(
                powerDecimals = int(K_PR_POWER, 2).coerceIn(0, 4),
                currentDecimals = int(K_PR_CURRENT, 0).coerceIn(0, 4),
                voltageDecimals = int(K_PR_VOLTAGE, 3).coerceIn(0, 4),
                temperatureDecimals = int(K_PR_TEMPERATURE, 1).coerceIn(0, 4),
                monospaceDigits = bool(K_PR_MONO, true)
            ),
            overlayColor = ColorConfig(
                argb = int(K_OC_ARGB, DEFAULT_COLOR_ARGB),
                autoColor = bool(K_OC_AUTO, false)
            ),
            statusBarColor = ColorConfig(
                argb = int(K_SC_ARGB, DEFAULT_COLOR_ARGB),
                autoColor = bool(K_SC_AUTO, false)
            ),
            flags = FeatureFlags(
                enableOverlay = bool(K_FF_OVERLAY, true),
                enableStatusBarHook = bool(K_FF_STATUS_BAR, true),
                enableRootReader = bool(K_FF_ROOT_READER, true),
                enableBatteryManagerFallback = bool(K_FF_BM_FALLBACK, true),
                enableDebugLog = bool(K_FF_DEBUG_LOG, false),
                enableAutoScale = bool(K_FF_AUTO_SCALE, true),
                enableAdaptiveFields = bool(K_FF_ADAPTIVE_FIELDS, true),
                enableAnimations = bool(K_FF_ANIMATIONS, true),
                enableAdvancedTheme = bool(K_FF_ADVANCED_THEME, false)
            )
        )
        return ConfigMigration.migrate(config)
    }

    private fun sanitizeOverlayRefresh(value: Long): Long = when {
        value <= 500L -> REFRESH_500
        value <= 1000L -> REFRESH_1000
        else -> REFRESH_2000
    }

    private fun sanitizeStatusBarRefresh(value: Long): Long = when {
        value <= 500L -> REFRESH_500
        value <= 1000L -> REFRESH_1000
        value <= 2000L -> REFRESH_2000
        value <= 3000L -> REFRESH_3000
        else -> REFRESH_5000
    }
}
