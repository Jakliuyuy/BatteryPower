package com.jakliuyuy.batterypower.ui

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.card.MaterialCardView
import com.google.android.material.materialswitch.MaterialSwitch
import com.jakliuyuy.batterypower.R
import com.jakliuyuy.batterypower.app.OverlayService
import com.jakliuyuy.batterypower.core.config.AppConfig
import com.jakliuyuy.batterypower.core.config.ConfigStore
import com.jakliuyuy.batterypower.core.format.BatteryFormatter
import com.jakliuyuy.batterypower.core.log.BLog
import com.jakliuyuy.batterypower.core.model.BatterySnapshot
import com.jakliuyuy.batterypower.ui.color.ColorActivity
import com.jakliuyuy.batterypower.ui.settings.AdvancedActivity
import com.jakliuyuy.batterypower.ui.settings.OverlaySettingsActivity
import com.jakliuyuy.batterypower.ui.settings.StatusBarSettingsActivity
import java.util.Locale

/**
 * Home screen (spec sections 5, 6, 7, 82, 94, 137.2).
 * The preview card always shows real data using the current configuration.
 */
class MainActivity : BaseActivity() {

    private lateinit var statusText: TextView
    private lateinit var powerText: TextView
    private lateinit var fieldSlots: List<TextView>
    private lateinit var updatedText: TextView
    private lateinit var fieldChips: MaterialButtonToggleGroup
    private lateinit var fieldHint: TextView
    private lateinit var switchContainer: LinearLayout
    private lateinit var navContainer: LinearLayout
    private lateinit var moduleHint: TextView

    private var overlaySwitch: MaterialSwitch? = null
    private var statusBarSwitch: MaterialSwitch? = null

    private val overlayPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        // Returning from the permission page: re-check and start the service.
        val config = configStore.get()
        if (config.overlay.enabled && PermissionHelper.canDrawOverlays(this)) {
            OverlayService.start(this)
        } else {
            configStore.update { it.copy(overlay = it.overlay.copy(enabled = false)) }
        }
        refreshSwitches(configStore.get())
    }

    override fun wantsBatteryUpdates(): Boolean = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, true)
        setContentView(R.layout.activity_main)

        statusText = findViewById(R.id.statusText)
        powerText = findViewById(R.id.powerText)
        updatedText = findViewById(R.id.updatedText)
        fieldChips = findViewById(R.id.fieldChips)
        fieldHint = findViewById(R.id.fieldHint)
        switchContainer = findViewById(R.id.switchContainer)
        navContainer = findViewById(R.id.navContainer)
        moduleHint = findViewById(R.id.moduleHint)
        fieldSlots = listOf(
            findViewById(R.id.fieldLeft1),
            findViewById(R.id.fieldRight1),
            findViewById(R.id.fieldLeft2),
            findViewById(R.id.fieldRight2)
        )

        if (!configStore.get().onboarded) {
            startActivity(Intent(this, OnboardingActivity::class.java))
        }

        buildSwitches()
        buildFieldChips()
        buildNavigation()
        applyConfigToUi(configStore.get())
        onBatterySnapshot(engine.lastSnapshot())
        applyWindowInsets()
    }

    override fun onResume() {
        super.onResume()
        pingHookStatus()
    }

    private fun applyWindowInsets() {
        try {
            val appBar = findViewById<View>(R.id.appBar)
            ViewCompat.setOnApplyWindowInsetsListener(appBar) { view, insets ->
                val top = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
                view.setPadding(view.paddingLeft, top, view.paddingRight, view.paddingBottom)
                insets
            }
        } catch (t: Throwable) {
            BLog.w("UI", "insets failed: ${t.message}")
        }
    }

    // ------------------------------------------------------------------ wiring

    private fun buildSwitches() {
        val container = switchContainer
        val config = configStore.get()

        val overlayRow = Ui.switchRow(
            this,
            getString(R.string.overlay_title),
            getString(R.string.overlay_summary),
            config.overlay.enabled && config.flags.enableOverlay
        ) { checked -> onOverlayToggled(checked) }
        overlaySwitch = findSwitch(overlayRow)
        container.addView(overlayRow)

        val statusRow = Ui.switchRow(
            this,
            getString(R.string.statusbar_title),
            getString(R.string.statusbar_summary),
            config.statusBar.enabled && config.flags.enableStatusBarHook
        ) { checked -> onStatusBarToggled(checked) }
        statusBarSwitch = findSwitch(statusRow)
        container.addView(statusRow)
    }

    private fun findSwitch(row: View): MaterialSwitch? {
        if (row !is android.view.ViewGroup) return null
        for (i in 0 until row.childCount) {
            val child = row.getChildAt(i)
            if (child is MaterialSwitch) return child
        }
        return null
    }

    private fun buildFieldChips() {
        val config = configStore.get()
        val options = listOf(
            getString(R.string.field_power),
            getString(R.string.field_current),
            getString(R.string.field_voltage),
            getString(R.string.field_temperature),
            getString(R.string.field_capacity)
        )
        val checked = listOf(
            config.display.power,
            config.display.current,
            config.display.voltage,
            config.display.temperature,
            config.display.capacity
        )
        fieldChips.removeAllViews()
        options.forEachIndexed { index, label ->
            val button = MaterialButton(
                this,
                null,
                com.google.android.material.R.attr.materialButtonOutlinedStyle
            ).apply {
                id = View.generateViewId()
                text = label
                textSize = 12f
                isCheckable = true
                minimumWidth = 0
                minWidth = 0
                setPadding(Ui.dp(this@MainActivity, 10f), 0, Ui.dp(this@MainActivity, 10f), 0)
            }
            fieldChips.addView(button)
            if (checked[index]) fieldChips.check(button.id)
        }
        fieldChips.addOnButtonCheckedListener { group, checkedId, isChecked ->
            val index = group.indexOfChild(group.findViewById<View>(checkedId))
            if (index >= 0) onFieldToggled(index, isChecked)
        }
    }

    private fun onFieldToggled(index: Int, checked: Boolean) {
        var config = configStore.get()
        val display = config.display
        val next = when (index) {
            0 -> display.copy(power = checked)
            1 -> display.copy(current = checked)
            2 -> display.copy(voltage = checked)
            3 -> display.copy(temperature = checked)
            4 -> display.copy(capacity = checked)
            else -> display
        }
        val sanitized = com.jakliuyuy.batterypower.core.config.DisplayConfig.sanitize(next)
        if (!checked && sanitized == display) {
            // Spec section 37: Power cannot be removed as the last field.
            buildFieldChips()
            fieldHint.visibility = View.VISIBLE
            fieldHint.postDelayed({ fieldHint.visibility = View.GONE }, 2200L)
            return
        }
        configStore.update { it.copy(display = sanitized) }
    }

    private fun buildNavigation() {
        navContainer.addView(
            Ui.navRow(this, getString(R.string.settings_overlay), "字号 / 颜色 / 布局 / 刷新频率") {
                startActivity(Intent(this, OverlaySettingsActivity::class.java))
            }
        )
        navContainer.addView(
            Ui.navRow(this, getString(R.string.settings_statusbar), "位置 / 偏移 / 字号 / 刷新频率") {
                startActivity(Intent(this, StatusBarSettingsActivity::class.java))
            }
        )
        navContainer.addView(
            Ui.navRow(this, getString(R.string.title_appearance), "外观 / 精度 / 主题") {
                startActivity(Intent(this, com.jakliuyuy.batterypower.ui.settings.AppearanceActivity::class.java))
            }
        )
        navContainer.addView(
            Ui.navRow(this, getString(R.string.title_color), "HSV 调色") {
                startActivity(Intent(this, ColorActivity::class.java))
            }
        )
        navContainer.addView(
            Ui.navRow(this, getString(R.string.settings_diagnostics), "Root / 节点 / Hook 状态") {
                startActivity(Intent(this, DiagnosticsActivity::class.java))
            }
        )
        navContainer.addView(
            Ui.navRow(this, getString(R.string.settings_advanced), "重启 SystemUI / 重置 / 关于") {
                startActivity(Intent(this, AdvancedActivity::class.java))
            }
        )
    }

    // ------------------------------------------------------------------ actions

    private fun onOverlayToggled(enabled: Boolean) {
        if (enabled) {
            if (!configStore.get().flags.enableOverlay) return
            if (!PermissionHelper.canDrawOverlays(this)) {
                PermissionHelper.requestOverlayPermission(this) { intent ->
                    configStore.update { it.copy(overlay = it.overlay.copy(enabled = true)) }
                    overlayPermissionLauncher.launch(intent)
                }
                return
            }
            configStore.update { it.copy(overlay = it.overlay.copy(enabled = true)) }
            OverlayService.start(this)
        } else {
            configStore.update { it.copy(overlay = it.overlay.copy(enabled = false)) }
            OverlayService.stop(this)
        }
    }

    private fun onStatusBarToggled(enabled: Boolean) {
        configStore.update { it.copy(statusBar = it.statusBar.copy(enabled = enabled)) }
        if (enabled) {
            // Spec 82: never fail silently, always tell the user what to check.
            pingHookStatus()
        }
    }

    private fun pingHookStatus() {
        ModuleStatus.pingHook(this) { state ->
            val lsposed = ModuleStatus.isLsposedManagerInstalled(this)
            val config = configStore.get()
            val hookText = when (state) {
                ModuleStatus.HookState.RUNNING -> "正常运行"
                ModuleStatus.HookState.WAITING_RESTART -> "等待 SystemUI 重启"
                ModuleStatus.HookState.NOT_RUNNING -> "未运行"
            }
            val lsposedText = if (lsposed) "已检测" else "未检测"
            val lines = StringBuilder()
            lines.append("LSPosed 环境：$lsposedText\n")
            lines.append("SystemUI Hook：$hookText")
            if (config.statusBar.enabled && state != ModuleStatus.HookState.RUNNING) {
                lines.append("\n请确认 LSPosed 已启用本模块，Scope 包含 SystemUI，并已重启 SystemUI。")
            }
            moduleHint.text = lines.toString()
        }
    }

    private fun refreshSwitches(config: AppConfig) {
        overlaySwitch?.isChecked = config.overlay.enabled && config.flags.enableOverlay
        statusBarSwitch?.isChecked = config.statusBar.enabled && config.flags.enableStatusBarHook
    }

    // ------------------------------------------------------------------ render

    override fun onConfigChanged(config: AppConfig) {
        applyConfigToUi(config)
        refreshSwitches(config)
        buildFieldChips()
    }

    private fun applyConfigToUi(config: AppConfig) {
        powerText.setTextColor(config.overlayColor.argb)
    }

    override fun onBatterySnapshot(snapshot: BatterySnapshot) {
        val config = configStore.get()
        val options = BatteryFormatter.Options(
            showUnit = true,
            powerDecimals = config.precision.powerDecimals,
            currentDecimals = config.precision.currentDecimals,
            voltageDecimals = config.precision.voltageDecimals,
            temperatureDecimals = config.precision.temperatureDecimals
        )

        statusText.text = BatteryFormatter.statusText(snapshot).uppercase(Locale.US)

        val color = when {
            snapshot.error != null -> Color.parseColor("#FFF2B8B5")
            !snapshot.valid -> Color.parseColor("#FF9AA0A6")
            else -> config.overlayColor.argb
        }
        powerText.setTextColor(color)

        // The headline metric follows the display configuration: power when it is
        // enabled, otherwise the first enabled field (spec 137.3).
        val items = LinkedHashMap<String, String>()
        if (config.display.power) items["power"] = BatteryFormatter.formatPower(snapshot, options)
        if (config.display.current) items["current"] = BatteryFormatter.formatCurrent(snapshot, options)
        if (config.display.voltage) items["voltage"] = BatteryFormatter.formatVoltage(snapshot, options)
        if (config.display.temperature) items["temperature"] = BatteryFormatter.formatTemperature(snapshot, options)
        if (config.display.capacity) items["capacity"] = BatteryFormatter.formatCapacity(snapshot, options)

        if (items.isEmpty()) {
            powerText.text = spaced(BatteryFormatter.formatPower(snapshot, options))
        } else {
            val first = items.entries.first().value
            powerText.text = spaced(first)
            // Big number for the headline, smaller for the remaining fields.
            powerText.textSize = if (items.size > 1) 30f else 34f
        }

        val rest = items.entries.drop(1).map { spaced(it.value) }
        for (i in fieldSlots.indices) {
            val slot = fieldSlots[i]
            val value = rest.getOrNull(i)
            if (value == null) {
                slot.visibility = View.INVISIBLE
                slot.text = ""
            } else {
                slot.visibility = View.VISIBLE
                slot.text = value
            }
        }

        val ageMs = (System.currentTimeMillis() - snapshot.timestampMs).coerceAtLeast(0L)
        updatedText.text = if (snapshot.timestampMs == 0L) {
            "Updated --"
        } else {
            String.format(Locale.US, "Updated %.1fs ago", ageMs / 1000f)
        }
    }

    private fun spaced(text: String): String {
        val idx = text.indexOfFirst { it.isLetter() || it == '%' || it == '°' }
        return if (idx > 0) text.substring(0, idx) + " " + text.substring(idx) else text
    }
}
