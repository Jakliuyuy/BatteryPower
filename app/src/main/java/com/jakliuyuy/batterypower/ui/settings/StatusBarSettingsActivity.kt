package com.jakliuyuy.batterypower.ui.settings

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.ScrollView
import com.jakliuyuy.batterypower.R
import com.jakliuyuy.batterypower.core.config.ANCHOR_CLOCK_LEFT
import com.jakliuyuy.batterypower.core.config.ANCHOR_CLOCK_RIGHT
import com.jakliuyuy.batterypower.core.config.AppConfig
import com.jakliuyuy.batterypower.core.config.FONT_STYLE_BOLD
import com.jakliuyuy.batterypower.core.config.FONT_STYLE_MEDIUM
import com.jakliuyuy.batterypower.core.config.OFFSET_STEP
import com.jakliuyuy.batterypower.core.config.OFFSET_X_MAX
import com.jakliuyuy.batterypower.core.config.OFFSET_X_MIN
import com.jakliuyuy.batterypower.core.config.OFFSET_Y_MAX
import com.jakliuyuy.batterypower.core.config.OFFSET_Y_MIN
import com.jakliuyuy.batterypower.core.config.REFRESH_1000
import com.jakliuyuy.batterypower.core.config.REFRESH_2000
import com.jakliuyuy.batterypower.core.config.REFRESH_3000
import com.jakliuyuy.batterypower.core.config.REFRESH_500
import com.jakliuyuy.batterypower.core.config.REFRESH_5000
import com.jakliuyuy.batterypower.core.config.STATUSBAR_FONT_MAX_SP
import com.jakliuyuy.batterypower.core.config.STATUSBAR_FONT_MIN_SP
import com.jakliuyuy.batterypower.ui.BaseActivity
import com.jakliuyuy.batterypower.ui.ModuleStatus
import com.jakliuyuy.batterypower.ui.Ui
import com.jakliuyuy.batterypower.ui.color.ColorActivity
import java.util.Locale

/** Status bar settings (spec sections 19-24, 80, 142, 144). */
class StatusBarSettingsActivity : BaseActivity() {

    private val handler = Handler(Looper.getMainLooper())

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

        val switchCard = Ui.cardContent(this)
        switchCard.addView(
            Ui.switchRow(
                this,
                getString(R.string.statusbar_title),
                getString(R.string.statusbar_summary),
                config.statusBar.enabled
            ) { checked ->
                configStore.update { it.copy(statusBar = it.statusBar.copy(enabled = checked)) }
            }
        )
        switchCard.addView(Ui.bodyText(this, "修改后通常在 1 秒内生效。若未生效，请确认 LSPosed 已启用本模块并重启 SystemUI。"))
        root.addView(Ui.card(this, switchCard))

        val positionCard = Ui.cardContent(this)
        positionCard.addView(Ui.sectionTitle(this, "位置"))
        positionCard.addView(
            Ui.segmentedRow(
                this,
                "锚点",
                listOf("时钟左侧", "时钟右侧"),
                if (config.statusBar.anchor == ANCHOR_CLOCK_RIGHT) 1 else 0
            ) { index ->
                val anchor = if (index == 1) ANCHOR_CLOCK_RIGHT else ANCHOR_CLOCK_LEFT
                configStore.update { it.copy(statusBar = it.statusBar.copy(anchor = anchor)) }
            }
        )
        positionCard.addView(Ui.divider(this))
        positionCard.addView(
            offsetStepperRow(
                "水平偏移",
                config.statusBar.offsetX,
                OFFSET_X_MIN,
                OFFSET_X_MAX
            ) { value ->
                configStore.update { it.copy(statusBar = it.statusBar.copy(offsetX = value)) }
            }
        )
        positionCard.addView(
            Ui.sliderRow(
                this,
                "水平偏移滑块",
                config.statusBar.offsetX.toFloat(),
                OFFSET_X_MIN.toFloat(),
                OFFSET_X_MAX.toFloat(),
                OFFSET_STEP.toFloat(),
                { v -> String.format(Locale.US, "%d px", v.toInt()) },
                { v ->
                    configStore.update {
                        it.copy(statusBar = it.statusBar.copy(offsetX = v.toInt()))
                    }
                }
            )
        )
        positionCard.addView(Ui.divider(this))
        positionCard.addView(
            offsetStepperRow(
                "垂直偏移",
                config.statusBar.offsetY,
                OFFSET_Y_MIN,
                OFFSET_Y_MAX
            ) { value ->
                configStore.update { it.copy(statusBar = it.statusBar.copy(offsetY = value)) }
            }
        )
        positionCard.addView(
            Ui.sliderRow(
                this,
                "垂直偏移滑块",
                config.statusBar.offsetY.toFloat(),
                OFFSET_Y_MIN.toFloat(),
                OFFSET_Y_MAX.toFloat(),
                OFFSET_STEP.toFloat(),
                { v -> String.format(Locale.US, "%d px", v.toInt()) },
                { v ->
                    configStore.update {
                        it.copy(statusBar = it.statusBar.copy(offsetY = v.toInt()))
                    }
                }
            )
        )
        positionCard.addView(Ui.divider(this))
        positionCard.addView(
            Ui.navRow(this, "归零偏移", "将 X / Y 偏移恢复为 0") {
                configStore.update { it.copy(statusBar = it.statusBar.copy(offsetX = 0, offsetY = 0)) }
                recreate()
            }
        )
        root.addView(Ui.card(this, positionCard))

        val typographyCard = Ui.cardContent(this)
        typographyCard.addView(Ui.sectionTitle(this, "文字"))
        typographyCard.addView(
            Ui.sliderRow(
                this,
                "字号",
                config.statusBar.fontSizeSp,
                STATUSBAR_FONT_MIN_SP,
                STATUSBAR_FONT_MAX_SP,
                1f,
                { v -> String.format(Locale.US, "%.0fsp", v) },
                { v ->
                    configStore.update {
                        it.copy(statusBar = it.statusBar.copy(fontSizeSp = v))
                    }
                }
            )
        )
        typographyCard.addView(Ui.divider(this))
        typographyCard.addView(
            Ui.segmentedRow(
                this,
                "字体样式",
                listOf("普通", "Medium", "Bold"),
                config.statusBar.fontStyle.coerceIn(0, 2)
            ) { index ->
                configStore.update { it.copy(statusBar = it.statusBar.copy(fontStyle = index)) }
            }
        )
        typographyCard.addView(Ui.divider(this))
        typographyCard.addView(
            Ui.navRow(this, "颜色", String.format("#%08X", config.statusBarColor.argb)) {
                startActivity(
                    Intent(this, ColorActivity::class.java)
                        .putExtra(ColorActivity.EXTRA_TARGET, ColorActivity.TARGET_STATUS_BAR)
                )
            }
        )
        typographyCard.addView(Ui.divider(this))
        typographyCard.addView(
            Ui.switchRow(this, "显示单位", "关闭后仅显示数值", config.statusBar.showUnit) { checked ->
                configStore.update { it.copy(statusBar = it.statusBar.copy(showUnit = checked)) }
            }
        )
        root.addView(Ui.card(this, typographyCard))

        val adaptiveCard = Ui.cardContent(this)
        adaptiveCard.addView(Ui.sectionTitle(this, "自适应"))
        adaptiveCard.addView(
            Ui.switchRow(this, "自动缩放", "文字过宽时自动缩小字号（最小 8sp）", config.statusBar.autoScale) { checked ->
                configStore.update { it.copy(statusBar = it.statusBar.copy(autoScale = checked)) }
            }
        )
        adaptiveCard.addView(
            Ui.switchRow(this, "自适应字段", "空间不足时按优先级隐藏低优先级字段", config.statusBar.adaptiveFields) { checked ->
                configStore.update { it.copy(statusBar = it.statusBar.copy(adaptiveFields = checked)) }
            }
        )
        adaptiveCard.addView(
            Ui.sliderRow(
                this,
                "最大宽度",
                config.statusBar.maxWidthDp.toFloat(),
                40f,
                320f,
                10f,
                { v -> String.format(Locale.US, "%d dp", v.toInt()) },
                { v ->
                    configStore.update {
                        it.copy(statusBar = it.statusBar.copy(maxWidthDp = v.toInt()))
                    }
                }
            )
        )
        adaptiveCard.addView(
            Ui.sliderRow(
                this,
                "与时钟间距",
                config.statusBar.gapDp.toFloat(),
                0f,
                32f,
                1f,
                { v -> String.format(Locale.US, "%d dp", v.toInt()) },
                { v ->
                    configStore.update {
                        it.copy(statusBar = it.statusBar.copy(gapDp = v.toInt()))
                    }
                }
            )
        )
        root.addView(Ui.card(this, adaptiveCard))

        val refreshCard = Ui.cardContent(this)
        refreshCard.addView(Ui.sectionTitle(this, "刷新"))
        refreshCard.addView(
            Ui.segmentedRow(
                this,
                "刷新频率",
                listOf("0.5 秒", "1 秒", "2 秒", "3 秒", "5 秒"),
                refreshIndex(config.statusBar.refreshMs)
            ) { index ->
                val ms = when (index) {
                    0 -> REFRESH_500
                    1 -> REFRESH_1000
                    2 -> REFRESH_2000
                    3 -> REFRESH_3000
                    else -> REFRESH_5000
                }
                configStore.update { it.copy(statusBar = it.statusBar.copy(refreshMs = ms)) }
                engine.refreshSampleInterval()
            }
        )
        refreshCard.addView(Ui.bodyText(this, "仅更新文字内容，不会重建 View。"))
        root.addView(Ui.card(this, refreshCard))

        val statusCard = Ui.cardContent(this)
        statusCard.addView(Ui.sectionTitle(this, "状态"))
        val statusText = android.widget.TextView(this).apply {
            text = "正在检测 SystemUI Hook..."
            setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 13f)
            setTextColor(Ui.secondaryColor(this@StatusBarSettingsActivity))
            setPadding(Ui.dp(this@StatusBarSettingsActivity, 16f), Ui.dp(this@StatusBarSettingsActivity, 8f), Ui.dp(this@StatusBarSettingsActivity, 16f), Ui.dp(this@StatusBarSettingsActivity, 8f))
        }
        statusCard.addView(statusText)
        val pingButton = com.google.android.material.button.MaterialButton(this).apply {
            text = "重新检测 Hook"
            val params = android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
            )
            params.setMargins(Ui.dp(this@StatusBarSettingsActivity, 16f), 0, 0, 0)
            layoutParams = params
        }
        pingButton.setOnClickListener { ping(statusText) }
        statusCard.addView(pingButton)
        root.addView(Ui.card(this, statusCard))
        ping(statusText)
    }

    private fun ping(statusText: android.widget.TextView) {
        ModuleStatus.pingHook(this) { state ->
            val text = when (state) {
                ModuleStatus.HookState.RUNNING -> "SystemUI Hook：正常运行"
                ModuleStatus.HookState.WAITING_RESTART -> "SystemUI Hook：未收到响应，等待 SystemUI 重启"
                ModuleStatus.HookState.NOT_RUNNING -> "SystemUI Hook：未运行"
            }
            statusText.text = text
        }
    }

    private fun offsetStepperRow(
        title: String,
        current: Int,
        min: Int,
        max: Int,
        onChanged: (Int) -> Unit
    ): android.view.View {
        var value = current
        fun step(direction: Int) {
            value = (value + direction * OFFSET_STEP).coerceIn(min, max)
            onChanged(value)
            recreate()
        }
        return Ui.stepperRow(
            this,
            title,
            String.format(Locale.US, "%d px", value),
            onStep = { direction -> step(direction) },
            onLongPressRepeat = { direction ->
                val runnable = object : Runnable {
                    override fun run() {
                        if (!isFinishing) {
                            step(direction)
                            handler.postDelayed(this, 200L)
                        }
                    }
                }
                handler.postDelayed(runnable, 400L)
            }
        )
    }

    private fun refreshIndex(ms: Long): Int = when {
        ms <= REFRESH_500 -> 0
        ms <= REFRESH_1000 -> 1
        ms <= REFRESH_2000 -> 2
        ms <= REFRESH_3000 -> 3
        else -> 4
    }

    override fun onConfigChanged(config: AppConfig) {
        if (!isFinishing) recreate()
    }
}
