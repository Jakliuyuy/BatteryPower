package com.jakliuyuy.batterypower.ui.settings

import android.os.Bundle
import android.widget.ScrollView
import com.jakliuyuy.batterypower.R
import com.jakliuyuy.batterypower.core.config.AppConfig
import com.jakliuyuy.batterypower.core.config.DisplayConfig
import com.jakliuyuy.batterypower.ui.BaseActivity
import com.jakliuyuy.batterypower.ui.Ui

/**
 * Display field selection shared by the overlay and the status bar
 * (spec sections 37, 92, 137.4). At least one field must stay enabled and
 * Power is force-kept when it would otherwise become empty.
 */
class DisplayFieldsActivity : BaseActivity() {

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
        val card = Ui.cardContent(this)
        card.addView(Ui.sectionTitle(this, "显示项目"))
        card.addView(Ui.bodyText(this, "字段显示顺序：功率 > 电流 > 电压 > 温度 > 电量"))
        card.addView(
            Ui.switchRow(
                this,
                getString(R.string.field_power),
                "W",
                config.display.power
            ) { checked ->
                update { it.copy(display = DisplayConfig.sanitize(it.display.copy(power = checked))) }
            }
        )
        card.addView(Ui.divider(this))
        card.addView(
            Ui.switchRow(
                this,
                getString(R.string.field_current),
                "mA",
                config.display.current
            ) { checked ->
                update { it.copy(display = DisplayConfig.sanitize(it.display.copy(current = checked))) }
            }
        )
        card.addView(Ui.divider(this))
        card.addView(
            Ui.switchRow(
                this,
                getString(R.string.field_voltage),
                "V",
                config.display.voltage
            ) { checked ->
                update { it.copy(display = DisplayConfig.sanitize(it.display.copy(voltage = checked))) }
            }
        )
        card.addView(Ui.divider(this))
        card.addView(
            Ui.switchRow(
                this,
                getString(R.string.field_temperature),
                "°C",
                config.display.temperature
            ) { checked ->
                update { it.copy(display = DisplayConfig.sanitize(it.display.copy(temperature = checked))) }
            }
        )
        card.addView(Ui.divider(this))
        card.addView(
            Ui.switchRow(
                this,
                getString(R.string.field_capacity),
                "%",
                config.display.capacity
            ) { checked ->
                update { it.copy(display = DisplayConfig.sanitize(it.display.copy(capacity = checked))) }
            }
        )
        root.addView(Ui.card(this, card))

        val hintCard = Ui.cardContent(this)
        hintCard.addView(Ui.bodyText(this, "如果取消最后一个字段，功率会被自动保留。"))
        hintCard.addView(
            Ui.navRow(this, "恢复默认字段", "功率 / 电流 / 电压") {
                update { it.copy(display = DisplayConfig()) }
                recreate()
            }
        )
        root.addView(Ui.card(this, hintCard))
    }

    private fun update(block: (AppConfig) -> AppConfig) {
        configStore.update(block)
        recreate()
    }

    override fun onConfigChanged(config: AppConfig) {
        if (!isFinishing) recreate()
    }
}
