package com.jakliuyuy.batterypower.model

/**
 * 全局配置。主 App 与 SystemUI 进程共用同一份定义，
 * 通过 Map<String, String> 在 ContentProvider 两侧互转。
 *
 * 注意：所有字段都必须是可安全降级为默认值的类型——
 * SystemUI 侧读不到配置时不能崩溃，只能回退默认值。
 */
data class Config(
    // ===== 悬浮窗 =====
    val overlayEnabled: Boolean = false,
    val overlayFields: List<Field> = Field.DEFAULT,
    val overlayColor: Int = 0xFF00E676.toInt(),
    val overlayTextSizeSp: Float = 14f,
    val overlayIntervalMs: Long = 1000L,
    val overlayShowUnit: Boolean = true,
    val overlayX: Int = 60,
    val overlayY: Int = 240,

    // ===== 状态栏 Hook =====
    val sbEnabled: Boolean = false,
    val sbFields: List<Field> = listOf(Field.POWER),
    val sbColor: Int = 0xFF00E676.toInt(),
    val sbTextSizeSp: Float = 11f,
    val sbIntervalMs: Long = 1000L,
    val sbShowUnit: Boolean = true,
    /** 见 SbPosition */
    val sbPosition: String = SbPosition.CLOCK_RIGHT.key,
    val sbOffsetX: Int = 0,
    val sbOffsetY: Int = 0
) {
    companion object {
        fun default() = Config()

        /**
         * 从字符串 Map 还原。任何一项解析失败都回退到默认值，
         * 保证 SystemUI 侧缓存损坏时不会崩。
         */
        fun fromMap(map: Map<String, String>): Config {
            val d = default()
            if (map.isEmpty()) return d
            return Config(
                overlayEnabled = map.bool(K.OVERLAY_ENABLED, d.overlayEnabled),
                overlayFields = Field.fromKeys(map[K.OVERLAY_FIELDS]),
                overlayColor = map.int(K.OVERLAY_COLOR, d.overlayColor),
                overlayTextSizeSp = map.float(K.OVERLAY_TEXT_SIZE, d.overlayTextSizeSp),
                overlayIntervalMs = map.long(K.OVERLAY_INTERVAL, d.overlayIntervalMs),
                overlayShowUnit = map.bool(K.OVERLAY_SHOW_UNIT, d.overlayShowUnit),
                overlayX = map.int(K.OVERLAY_X, d.overlayX),
                overlayY = map.int(K.OVERLAY_Y, d.overlayY),

                sbEnabled = map.bool(K.SB_ENABLED, d.sbEnabled),
                sbFields = Field.fromKeys(map[K.SB_FIELDS]),
                sbColor = map.int(K.SB_COLOR, d.sbColor),
                sbTextSizeSp = map.float(K.SB_TEXT_SIZE, d.sbTextSizeSp),
                sbIntervalMs = map.long(K.SB_INTERVAL, d.sbIntervalMs),
                sbShowUnit = map.bool(K.SB_SHOW_UNIT, d.sbShowUnit),
                sbPosition = map[K.SB_POSITION] ?: d.sbPosition,
                sbOffsetX = map.int(K.SB_OFFSET_X, d.sbOffsetX),
                sbOffsetY = map.int(K.SB_OFFSET_Y, d.sbOffsetY)
            )
        }
    }

    /** 序列化为字符串 Map，供 ContentProvider 传输 */
    fun toMap(): Map<String, String> = mapOf(
        K.OVERLAY_ENABLED to overlayEnabled.toString(),
        K.OVERLAY_FIELDS to Field.toKeys(overlayFields),
        K.OVERLAY_COLOR to overlayColor.toString(),
        K.OVERLAY_TEXT_SIZE to overlayTextSizeSp.toString(),
        K.OVERLAY_INTERVAL to overlayIntervalMs.toString(),
        K.OVERLAY_SHOW_UNIT to overlayShowUnit.toString(),
        K.OVERLAY_X to overlayX.toString(),
        K.OVERLAY_Y to overlayY.toString(),

        K.SB_ENABLED to sbEnabled.toString(),
        K.SB_FIELDS to Field.toKeys(sbFields),
        K.SB_COLOR to sbColor.toString(),
        K.SB_TEXT_SIZE to sbTextSizeSp.toString(),
        K.SB_INTERVAL to sbIntervalMs.toString(),
        K.SB_SHOW_UNIT to sbShowUnit.toString(),
        K.SB_POSITION to sbPosition,
        K.SB_OFFSET_X to sbOffsetX.toString(),
        K.SB_OFFSET_Y to sbOffsetY.toString()
    )

    /** 配置键常量。SystemUI 侧只读这些 key，改名需同步 */
    object K {
        const val OVERLAY_ENABLED = "overlay_enabled"
        const val OVERLAY_FIELDS = "overlay_fields"
        const val OVERLAY_COLOR = "overlay_color"
        const val OVERLAY_TEXT_SIZE = "overlay_text_size"
        const val OVERLAY_INTERVAL = "overlay_interval"
        const val OVERLAY_SHOW_UNIT = "overlay_show_unit"
        const val OVERLAY_X = "overlay_x"
        const val OVERLAY_Y = "overlay_y"

        const val SB_ENABLED = "sb_enabled"
        const val SB_FIELDS = "sb_fields"
        const val SB_COLOR = "sb_color"
        const val SB_TEXT_SIZE = "sb_text_size"
        const val SB_INTERVAL = "sb_interval"
        const val SB_SHOW_UNIT = "sb_show_unit"
        const val SB_POSITION = "sb_position"
        const val SB_OFFSET_X = "sb_offset_x"
        const val SB_OFFSET_Y = "sb_offset_y"
    }
}

/** 状态栏文字相对时钟的位置 */
enum class SbPosition(val key: String, val label: String) {
    CLOCK_LEFT("clock_left", "时钟左侧"),
    CLOCK_RIGHT("clock_right", "时钟右侧");

    companion object {
        fun fromKey(key: String?): SbPosition =
            values().firstOrNull { it.key == key } ?: CLOCK_RIGHT
    }
}

// ===== Map 读取辅助：全部容错，解析失败返回默认值 =====
private fun Map<String, String>.bool(key: String, fallback: Boolean): Boolean =
    this[key]?.toBooleanStrictOrNull() ?: fallback

private fun Map<String, String>.int(key: String, fallback: Int): Int =
    this[key]?.toIntOrNull() ?: fallback

private fun Map<String, String>.long(key: String, fallback: Long): Long =
    this[key]?.toLongOrNull() ?: fallback

private fun Map<String, String>.float(key: String, fallback: Float): Float =
    this[key]?.toFloatOrNull() ?: fallback
