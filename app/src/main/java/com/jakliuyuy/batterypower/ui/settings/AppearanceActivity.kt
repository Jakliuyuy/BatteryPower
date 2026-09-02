package com.jakliuyuy.batterypower.ui.settings

import android.os.Bundle
import android.widget.ScrollView
import com.jakliuyuy.batterypower.R
import com.jakliuyuy.batterypower.core.config.AppConfig
import com.jakliuyuy.batterypower.core.config.ThemeMode
import com.jakliuyuy.batterypower.ui.BaseActivity
import com.jakliuyuy.batterypower.ui.Ui
import java.util.Locale

/** Appearance: theme, accent and numeric precision (spec sections 78, 139, 141). */
class AppearanceActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val scrollView = ScrollView(this)
        val root = Ui.scrollRoot(this)
        scrollView.addView(root)
        setContentView(scrollView)
        build(root)
    }

    private fun build(root: android.widget.LinearLayout) {
        val config = configStore.get()

        val themeCard = Ui.cardContent(this)
        themeCard.addView(Ui.sectionTitle(this, "主题"))
        themeCard.addView(
            Ui.segmentedRow(
                this,
                "主题模式",
                listOf("跟随系统", "浅色", "深色"),
                when (config.theme.mode) {
                    ThemeMode.LIGHT -> 1
                    ThemeMode.DARK -> 2
                    else -> 0
                }
            ) { index ->
                val mode = when (index) {
                    1 -> ThemeMode.LIGHT
                    2 -> ThemeMode.DARK
                    else -> ThemeMode.FOLLOW_SYSTEM
                }
                configStore.update { it.copy(theme = it.theme.copy(mode = mode)) }
                recreate()
            }
        )
        themeCard.addView(Ui.divider(this))
        themeCard.addView(Ui.bodyText(this, "App 主色只影响设置界面，不会覆盖悬浮窗 / 状态栏颜色。"))
        themeCard.addView(
            Ui.segmentedRow(
                this,
                "App 主色",
                listOf("青", "蓝", "绿", "琥珀", "紫", "白"),
                accentIndex(config.theme.accentArgb)
            ) { index ->
                val argb = accentFor(index)
                configStore.update { it.copy(theme = it.theme.copy(accentArgb = argb)) }
                recreate()
            }
        )
        root.addView(Ui.card(this, themeCard))

        val precisionCard = Ui.cardContent(this)
        precisionCard.addView(Ui.sectionTitle(this, "数值精度"))
        precisionCard.addView(
            Ui.sliderRow(
                this,
                "功率小数位",
                config.precision.powerDecimals.toFloat(),
                0f, 3f, 1f,
                { v -> String.format(Locale.US, "%d 位", v.toInt()) },
                { v -> configStore.update { it.copy(precision = it.precision.copy(powerDecimals = v.toInt())) } }
            )
        )
        precisionCard.addView(
            Ui.sliderRow(
                this,
                "电流小数位",
                config.precision.currentDecimals.toFloat(),
                0f, 3f, 1f,
                { v -> String.format(Locale.US, "%d 位", v.toInt()) },
                { v -> configStore.update { it.copy(precision = it.precision.copy(currentDecimals = v.toInt())) } }
            )
        )
        precisionCard.addView(
            Ui.sliderRow(
                this,
                "电压小数位",
                config.precision.voltageDecimals.toFloat(),
                0f, 4f, 1f,
                { v -> String.format(Locale.US, "%d 位", v.toInt()) },
                { v -> configStore.update { it.copy(precision = it.precision.copy(voltageDecimals = v.toInt())) } }
            )
        )
        precisionCard.addView(
            Ui.sliderRow(
                this,
                "温度小数位",
                config.precision.temperatureDecimals.toFloat(),
                0f, 2f, 1f,
                { v -> String.format(Locale.US, "%d 位", v.toInt()) },
                { v -> configStore.update { it.copy(precision = it.precision.copy(temperatureDecimals = v.toInt())) } }
            )
        )
        root.addView(Ui.card(this, precisionCard))

        val fontCard = Ui.cardContent(this)
        fontCard.addView(Ui.sectionTitle(this, "字体"))
        fontCard.addView(
            Ui.switchRow(
                this,
                "等宽数字",
                "避免 9.99W → 10.00W 时布局跳动",
                config.precision.monospaceDigits
            ) { checked ->
                configStore.update { it.copy(precision = it.precision.copy(monospaceDigits = checked)) }
            }
        )
        root.addView(Ui.card(this, fontCard))
    }

    private fun accentIndex(argb: Int): Int = when (argb) {
        0xFF4DD0E1.toInt() -> 0
        0xFF5B8DEF.toInt() -> 1
        0xFF42C76A.toInt() -> 2
        0xFFFFB300.toInt() -> 3
        0xFFB06BEF.toInt() -> 4
        0xFFFFFFFF.toInt() -> 5
        else -> 1
    }

    private fun accentFor(index: Int): Int = when (index) {
        0 -> 0xFF4DD0E1.toInt()
        1 -> 0xFF5B8DEF.toInt()
        2 -> 0xFF42C76A.toInt()
        3 -> 0xFFFFB300.toInt()
        4 -> 0xFFB06BEF.toInt()
        5 -> 0xFFFFFFFF.toInt()
        else -> 0xFF5B8DEF.toInt()
    }

    override fun onConfigChanged(config: AppConfig) {
        if (!isFinishing) recreate()
    }
}
