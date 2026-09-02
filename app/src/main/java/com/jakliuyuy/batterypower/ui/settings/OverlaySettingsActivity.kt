package com.jakliuyuy.batterypower.ui.settings

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.ScrollView
import com.jakliuyuy.batterypower.R
import com.jakliuyuy.batterypower.app.OverlayService
import com.jakliuyuy.batterypower.core.config.ANCHOR_CLOCK_LEFT
import com.jakliuyuy.batterypower.core.config.AppConfig
import com.jakliuyuy.batterypower.core.config.FONT_STYLE_BOLD
import com.jakliuyuy.batterypower.core.config.FONT_STYLE_MEDIUM
import com.jakliuyuy.batterypower.core.config.LAYOUT_AUTO_WRAP
import com.jakliuyuy.batterypower.core.config.NO_POSITION
import com.jakliuyuy.batterypower.core.config.OVERLAY_FONT_MAX_SP
import com.jakliuyuy.batterypower.core.config.OVERLAY_FONT_MIN_SP
import com.jakliuyuy.batterypower.core.config.REFRESH_1000
import com.jakliuyuy.batterypower.core.config.REFRESH_2000
import com.jakliuyuy.batterypower.core.config.REFRESH_500
import com.jakliuyuy.batterypower.ui.BaseActivity
import com.jakliuyuy.batterypower.ui.PermissionHelper
import com.jakliuyuy.batterypower.ui.Ui
import com.jakliuyuy.batterypower.ui.color.ColorActivity
import java.util.Locale

/** Overlay settings (spec sections 8-18, 80, 143). */
class OverlaySettingsActivity : BaseActivity() {

    private val handler = Handler(Looper.getMainLooper())
    private var repeatRunnable: Runnable? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val scrollView = ScrollView(this)
        val root = Ui.scrollRoot(this)
        scrollView.addView(root)
        setContentView(scrollView)
        build(root)
    }

    override fun onDestroy() {
        repeatRunnable?.let { handler.removeCallbacks(it) }
        super.onDestroy()
    }

    private fun build(root: android.widget.LinearLayout) {
        val config = configStore.get()

        // --- master switch -------------------------------------------------
        val switchCard = Ui.cardContent(this)
        switchCard.addView(
            Ui.switchRow(
                this,
                getString(R.string.overlay_title),
                getString(R.string.overlay_summary),
                config.overlay.enabled
            ) { checked ->
                if (checked) {
                    if (!PermissionHelper.canDrawOverlays(this)) {
                        PermissionHelper.requestOverlayPermission(this) { intent ->
                            configStore.update { it.copy(overlay = it.overlay.copy(enabled = true)) }
                            startActivity(intent)
                        }
                        recreate()
                        return@switchRow
                    }
                    configStore.update { it.copy(overlay = it.overlay.copy(enabled = true)) }
                    OverlayService.start(this)
                } else {
                    configStore.update { it.copy(overlay = it.overlay.copy(enabled = false)) }
                    OverlayService.stop(this)
                }
            }
        )
        switchCard.addView(Ui.divider(this))
        switchCard.addView(
            Ui.switchRow(
                this,
                "锁定位置",
                "锁定后不可拖动，数据仍会更新",
                config.overlay.locked
            ) { checked ->
                configStore.update { it.copy(overlay = it.overlay.copy(locked = checked)) }
            }
        )
        root.addView(Ui.card(this, switchCard))

        // --- display -------------------------------------------------------
        val displayCard = Ui.cardContent(this)
        displayCard.addView(Ui.sectionTitle(this, "显示"))
        displayCard.addView(
            Ui.navRow(this, getString(R.string.display_fields), describeFields(config)) {
                startActivity(Intent(this, DisplayFieldsActivity::class.java))
            }
        )
        displayCard.addView(Ui.divider(this))
        displayCard.addView(
            Ui.switchRow(this, "显示单位", "关闭后仅显示数值，精度不变", config.overlay.showUnit) { checked ->
                configStore.update { it.copy(overlay = it.overlay.copy(showUnit = checked)) }
            }
        )
        root.addView(Ui.card(this, displayCard))

        // --- typography ----------------------------------------------------
        val fontCard = Ui.cardContent(this)
        fontCard.addView(Ui.sectionTitle(this, "字体"))
        fontCard.addView(
            fontStepper(
                "字体大小",
                config.overlay.fontSizeSp,
                OVERLAY_FONT_MIN_SP,
                OVERLAY_FONT_MAX_SP
            )
        )
        fontCard.addView(
            Ui.sliderRow(
                this,
                "字号滑块",
                config.overlay.fontSizeSp,
                OVERLAY_FONT_MIN_SP,
                OVERLAY_FONT_MAX_SP,
                1f,
                { v -> String.format(Locale.US, "%.0fsp", v) },
                { v ->
                    configStore.update {
                        it.copy(overlay = it.overlay.copy(fontSizeSp = v))
                    }
                }
            )
        )
        fontCard.addView(Ui.divider(this))
        fontCard.addView(
            Ui.segmentedRow(
                this,
                "字体样式",
                listOf("普通", "Medium", "Bold"),
                config.overlay.fontStyle.coerceIn(0, 2)
            ) { index ->
                configStore.update { it.copy(overlay = it.overlay.copy(fontStyle = index)) }
            }
        )
        fontCard.addView(
            Ui.segmentedRow(
                this,
                "数字字体",
                listOf("等宽数字", "系统默认"),
                if (config.overlay.fontFamily == 1) 0 else 1
            ) { index ->
                val family = if (index == 0) 1 else 0
                configStore.update { it.copy(overlay = it.overlay.copy(fontFamily = family)) }
            }
        )
        root.addView(Ui.card(this, fontCard))

        // --- appearance ----------------------------------------------------
        val appearanceCard = Ui.cardContent(this)
        appearanceCard.addView(Ui.sectionTitle(this, "外观"))
        appearanceCard.addView(
            Ui.navRow(this, "颜色", String.format("#%08X", config.overlayColor.argb)) {
                startActivity(
                    Intent(this, ColorActivity::class.java)
                        .putExtra(ColorActivity.EXTRA_TARGET, ColorActivity.TARGET_OVERLAY)
                )
            }
        )
        appearanceCard.addView(Ui.divider(this))
        appearanceCard.addView(
            Ui.segmentedRow(
                this,
                "布局",
                listOf("单行", "自动换行"),
                if (config.overlay.autoWrap) LAYOUT_AUTO_WRAP else 0
            ) { index ->
                configStore.update { it.copy(overlay = it.overlay.copy(autoWrap = index == LAYOUT_AUTO_WRAP)) }
            }
        )
        root.addView(Ui.card(this, appearanceCard))

        // --- refresh -------------------------------------------------------
        val refreshCard = Ui.cardContent(this)
        refreshCard.addView(Ui.sectionTitle(this, "刷新"))
        refreshCard.addView(
            Ui.segmentedRow(
                this,
                "刷新频率",
                listOf("0.5 秒", "1 秒", "2 秒"),
                refreshIndex(config.overlay.refreshMs)
            ) { index ->
                val ms = when (index) {
                    0 -> REFRESH_500
                    1 -> REFRESH_1000
                    else -> REFRESH_2000
                }
                configStore.update { it.copy(overlay = it.overlay.copy(refreshMs = ms)) }
                engine.refreshSampleInterval()
            }
        )
        root.addView(Ui.card(this, refreshCard))

        // --- advanced ------------------------------------------------------
        val advancedCard = Ui.cardContent(this)
        advancedCard.addView(Ui.sectionTitle(this, "高级"))
        advancedCard.addView(
            Ui.sliderRow(
                this,
                "字间距",
                config.overlay.letterSpacing,
                -0.5f,
                5f,
                0.1f,
                { v -> String.format(Locale.US, "%.1f", v) },
                { v -> configStore.update { it.copy(overlay = it.overlay.copy(letterSpacing = v)) } }
            )
        )
        advancedCard.addView(
            Ui.sliderRow(
                this,
                "行间距",
                config.overlay.lineSpacingExtra,
                0f,
                16f,
                0.5f,
                { v -> String.format(Locale.US, "%.1fdp", v) },
                { v -> configStore.update { it.copy(overlay = it.overlay.copy(lineSpacingExtra = v)) } }
            )
        )
        advancedCard.addView(
            Ui.switchRow(this, "文字发光", "默认关闭，避免在 SystemUI 中产生额外开销", config.overlay.glow) { checked ->
                configStore.update { it.copy(overlay = it.overlay.copy(glow = checked)) }
            }
        )
        advancedCard.addView(Ui.divider(this))
        advancedCard.addView(
            Ui.navRow(this, "重置悬浮窗位置", "清除已保存的拖动位置") {
                configStore.updateSync {
                    it.copy(overlay = it.overlay.copy(x = NO_POSITION, y = NO_POSITION))
                }
                OverlayService.stop(this)
                OverlayService.start(this)
            }
        )
        root.addView(Ui.card(this, advancedCard))
    }

    /** [-] 14sp [+] with long-press auto repeat (spec section 10). */
    private fun fontStepper(
        title: String,
        current: Float,
        min: Float,
        max: Float
    ): android.view.View {
        var value = current
        fun step(direction: Int) {
            value = (value + direction).coerceIn(min, max)
            configStore.update { it.copy(overlay = it.overlay.copy(fontSizeSp = value)) }
            recreate()
        }
        return Ui.stepperRow(
            this,
            title,
            String.format(Locale.US, "%.0fsp", value),
            onStep = { direction -> step(direction) },
            onLongPressRepeat = { direction ->
                repeatRunnable?.let { handler.removeCallbacks(it) }
                val runnable = object : Runnable {
                    override fun run() {
                        if (!isFinishing) {
                            step(direction)
                            handler.postDelayed(this, 250L)
                        }
                    }
                }
                repeatRunnable = runnable
                handler.postDelayed(runnable, 400L)
            }
        )
    }

    private fun refreshIndex(ms: Long): Int = when {
        ms <= REFRESH_500 -> 0
        ms <= REFRESH_1000 -> 1
        else -> 2
    }

    private fun describeFields(config: AppConfig): String {
        val enabled = mutableListOf<String>()
        if (config.display.power) enabled.add(getString(R.string.field_power))
        if (config.display.current) enabled.add(getString(R.string.field_current))
        if (config.display.voltage) enabled.add(getString(R.string.field_voltage))
        if (config.display.temperature) enabled.add(getString(R.string.field_temperature))
        if (config.display.capacity) enabled.add(getString(R.string.field_capacity))
        return enabled.joinToString(" / ")
    }

    override fun onConfigChanged(config: AppConfig) {
        // Rebuild so sliders and steppers stay in sync with the committed value.
        if (!isFinishing) recreate()
    }
}
