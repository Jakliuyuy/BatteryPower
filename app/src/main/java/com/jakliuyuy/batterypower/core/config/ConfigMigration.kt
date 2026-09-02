package com.jakliuyuy.batterypower.core.config

import com.jakliuyuy.batterypower.core.log.BLog

/**
 * Migrates old configuration schemas (spec sections 39, 108, 151).
 *
 * Requirements:
 *  - idempotent (safe to run repeatedly)
 *  - never overwrites an existing user setting
 *  - missing fields are filled with defaults
 *  - illegal values are replaced with safe defaults
 */
object ConfigMigration {

    fun migrate(config: AppConfig): AppConfig {
        var current = config
        var from = current.schemaVersion

        if (from < 2) {
            current = migrateV1ToV2(current)
            from = 2
        }

        current = current.copy(schemaVersion = SCHEMA_VERSION)
        current = enforceInvariants(current)
        if (current.schemaVersion != config.schemaVersion) {
            BLog.i("Config", "migrated config from schema ${config.schemaVersion} to $SCHEMA_VERSION")
        }
        return current
    }

    /**
     * v1 had a single shared colour and no precision block. Existing user values
     * are preserved; only new fields are filled in.
     */
    private fun migrateV1ToV2(old: AppConfig): AppConfig {
        return old.copy(
            schemaVersion = 2,
            statusBarColor = old.statusBarColor,
            precision = old.precision,
            display = DisplayConfig.sanitize(old.display)
        )
    }

    /** Structural guarantees that must hold for every configuration. */
    fun enforceInvariants(config: AppConfig): AppConfig {
        val display = DisplayConfig.sanitize(config.display)
        val overlay = config.overlay.copy(
            fontSizeSp = config.overlay.fontSizeSp.coerceIn(OVERLAY_FONT_MIN_SP, OVERLAY_FONT_MAX_SP),
            fontStyle = config.overlay.fontStyle.coerceIn(0, 2)
        )
        val statusBar = config.statusBar.copy(
            fontSizeSp = config.statusBar.fontSizeSp
                .coerceIn(STATUSBAR_FONT_MIN_SP, STATUSBAR_FONT_MAX_SP),
            fontStyle = config.statusBar.fontStyle.coerceIn(0, 2),
            offsetX = config.statusBar.offsetX.coerceIn(OFFSET_X_MIN, OFFSET_X_MAX),
            offsetY = config.statusBar.offsetY.coerceIn(OFFSET_Y_MIN, OFFSET_Y_MAX)
        )
        return config.copy(display = display, overlay = overlay, statusBar = statusBar)
    }
}
