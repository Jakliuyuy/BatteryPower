package com.jakliuyuy.batterypower

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.RadioGroup
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.material.checkbox.MaterialCheckBox
import com.google.android.material.materialswitch.MaterialSwitch
import com.jakliuyuy.batterypower.battery.BatteryProbe
import com.jakliuyuy.batterypower.battery.BatteryReader
import com.jakliuyuy.batterypower.battery.RootHelper
import com.jakliuyuy.batterypower.data.ConfigRepository
import com.jakliuyuy.batterypower.model.Field
import com.jakliuyuy.batterypower.model.SbPosition
import com.jakliuyuy.batterypower.overlay.OverlayService
import com.jakliuyuy.batterypower.ui.HsvColorPickerView
import kotlin.concurrent.thread

/**
 * 设置主界面。
 *
 * 所有改动即时写入 ConfigRepository 并通知悬浮窗服务；
 * 状态栏侧由 SystemUI 进程每 500ms 轮询配置，故改完 ≤1 秒生效。
 */
class MainActivity : AppCompatActivity() {

    private lateinit var repo: ConfigRepository
    private lateinit var reader: BatteryReader

    // 是否正在回灌 UI（避免初始化时触发一堆保存）
    private var binding = false

    // ===== 运行状态 =====
    private lateinit var tvStatus: TextView
    private lateinit var btnGrantOverlay: Button
    private lateinit var btnGrantNotify: Button
    private lateinit var btnRestartSystemUi: Button

    // ===== 悬浮窗 =====
    private lateinit var swOverlay: MaterialSwitch
    private lateinit var overlayFieldsContainer: LinearLayout
    private lateinit var pickerOverlay: HsvColorPickerView
    private lateinit var overlayPresets: LinearLayout
    private lateinit var tvOverlaySize: TextView
    private lateinit var sbOverlaySize: SeekBar
    private lateinit var rgOverlayInterval: RadioGroup
    private lateinit var swOverlayUnit: MaterialSwitch
    private lateinit var rbOverlayIds: Map<Long, Int>

    // ===== 状态栏 =====
    private lateinit var swSb: MaterialSwitch
    private lateinit var sbFieldsContainer: LinearLayout
    private lateinit var pickerSb: HsvColorPickerView
    private lateinit var sbPresets: LinearLayout
    private lateinit var tvSbSize: TextView
    private lateinit var sbSbSize: SeekBar
    private lateinit var rgSbInterval: RadioGroup
    private lateinit var swSbUnit: MaterialSwitch
    private lateinit var rgSbPosition: RadioGroup
    private lateinit var tvOffsetX: TextView
    private lateinit var tvOffsetY: TextView
    private lateinit var sbOffsetX: SeekBar
    private lateinit var sbOffsetY: SeekBar
    private lateinit var rbSbIds: Map<Long, Int>

    // ===== 诊断 =====
    private lateinit var btnDiagnose: Button
    private lateinit var tvDiagnose: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        repo = ConfigRepository(this)
        reader = BatteryReader(this)
        reader.prepareAsync()

        bindViews()
        buildFieldCheckboxes()
        buildPresets()
        bindListeners()
        refreshUi()
        refreshStatus()
    }

    override fun onResume() {
        super.onResume()
        refreshStatus()
    }

    // ==================================================================
    // 绑定与构建
    // ==================================================================
    private fun bindViews() {
        tvStatus = findViewById(R.id.tvStatus)
        btnGrantOverlay = findViewById(R.id.btnGrantOverlay)
        btnGrantNotify = findViewById(R.id.btnGrantNotify)
        btnRestartSystemUi = findViewById(R.id.btnRestartSystemUi)

        swOverlay = findViewById(R.id.swOverlay)
        overlayFieldsContainer = findViewById(R.id.overlayFields)
        pickerOverlay = findViewById(R.id.pickerOverlay)
        overlayPresets = findViewById(R.id.overlayPresets)
        tvOverlaySize = findViewById(R.id.tvOverlaySize)
        sbOverlaySize = findViewById(R.id.sbOverlaySize)
        rgOverlayInterval = findViewById(R.id.rgOverlayInterval)
        swOverlayUnit = findViewById(R.id.swOverlayUnit)
        rbOverlayIds = mapOf(
            500L to R.id.rbOverlay500,
            1000L to R.id.rbOverlay1000,
            2000L to R.id.rbOverlay2000
        )

        swSb = findViewById(R.id.swSb)
        sbFieldsContainer = findViewById(R.id.sbFields)
        pickerSb = findViewById(R.id.pickerSb)
        sbPresets = findViewById(R.id.sbPresets)
        tvSbSize = findViewById(R.id.tvSbSize)
        sbSbSize = findViewById(R.id.sbSbSize)
        rgSbInterval = findViewById(R.id.rgSbInterval)
        swSbUnit = findViewById(R.id.swSbUnit)
        rgSbPosition = findViewById(R.id.rgSbPosition)
        tvOffsetX = findViewById(R.id.tvOffsetX)
        tvOffsetY = findViewById(R.id.tvOffsetY)
        sbOffsetX = findViewById(R.id.sbOffsetX)
        sbOffsetY = findViewById(R.id.sbOffsetY)
        rbSbIds = mapOf(
            500L to R.id.rbSb500,
            1000L to R.id.rbSb1000,
            2000L to R.id.rbSb2000,
            3000L to R.id.rbSb3000,
            5000L to R.id.rbSb5000
        )

        btnDiagnose = findViewById(R.id.btnDiagnose)
        tvDiagnose = findViewById(R.id.tvDiagnose)
    }

    private fun buildFieldCheckboxes() {
        val cfg = repo.get()
        Field.values().forEach { field ->
            overlayFieldsContainer.addView(
                MaterialCheckBox(this).apply {
                    text = field.label
                    isChecked = cfg.overlayFields.contains(field)
                    tag = field
                    setOnCheckedChangeListener { _, _ ->
                        if (!binding) saveOverlayFields()
                    }
                },
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            )
            sbFieldsContainer.addView(
                MaterialCheckBox(this).apply {
                    text = field.label
                    isChecked = cfg.sbFields.contains(field)
                    tag = field
                    setOnCheckedChangeListener { _, _ ->
                        if (!binding) saveSbFields()
                    }
                },
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            )
        }
    }

    private fun buildPresets() {
        PRESET_COLORS.forEach { color ->
            overlayPresets.addView(makeSwatch(color) { pickerOverlay.color = color })
            sbPresets.addView(makeSwatch(color) { pickerSb.color = color })
        }
    }

    private fun makeSwatch(color: Int, onClick: () -> Unit): android.view.View {
        val size = (36 * resources.displayMetrics.density).toInt()
        val pad = (3 * resources.displayMetrics.density).toInt()
        return android.view.View(this).apply {
            layoutParams = LinearLayout.LayoutParams(size, size).apply {
                marginEnd = pad
            }
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(color)
                cornerRadius = 6 * resources.displayMetrics.density
                setStroke(1, 0x33000000)
            }
            setOnClickListener { onClick() }
        }
    }

    // ==================================================================
    // 事件绑定
    // ==================================================================
    private fun bindListeners() {
        btnGrantOverlay.setOnClickListener { requestOverlayPermission() }
        btnGrantNotify.setOnClickListener { requestNotificationPermission() }
        btnRestartSystemUi.setOnClickListener { restartSystemUi() }
        btnDiagnose.setOnClickListener { runDiagnose() }

        swOverlay.setOnCheckedChangeListener { _, checked ->
            if (binding) return@setOnCheckedChangeListener
            if (checked && !canDrawOverlay()) {
                swOverlay.isChecked = false
                Toast.makeText(this, R.string.toast_need_overlay, Toast.LENGTH_SHORT).show()
                requestOverlayPermission()
                return@setOnCheckedChangeListener
            }
            repo.update { it.copy(overlayEnabled = checked) }
            if (checked) {
                if (!hasNotificationPermission()) requestNotificationPermission()
                OverlayService.start(this)
            } else {
                OverlayService.stop(this)
            }
        }

        swSb.setOnCheckedChangeListener { _, checked ->
            if (binding) return@setOnCheckedChangeListener
            repo.update { it.copy(sbEnabled = checked) }
            Toast.makeText(
                this,
                if (checked) R.string.toast_sb_on else R.string.toast_sb_off,
                Toast.LENGTH_SHORT
            ).show()
        }

        pickerOverlay.onColorChanged = { color ->
            if (!binding) {
                repo.update { it.copy(overlayColor = color) }
                OverlayService.notifyConfigChanged(this)
            }
        }

        pickerSb.onColorChanged = { color ->
            if (!binding) repo.update { it.copy(sbColor = color) }
        }

        sbOverlaySize.setOnSeekBarChangeListener(object : SimpleSeekListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (!binding && fromUser) {
                    val sp = (progress + MIN_OVERLAY_SP).toFloat()
                    tvOverlaySize.text = getString(R.string.label_text_size_value, sp)
                    repo.update { it.copy(overlayTextSizeSp = sp) }
                    OverlayService.notifyConfigChanged(this@MainActivity)
                }
            }
        })

        sbSbSize.setOnSeekBarChangeListener(object : SimpleSeekListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (!binding && fromUser) {
                    val sp = (progress + MIN_SB_SP).toFloat()
                    tvSbSize.text = getString(R.string.label_text_size_value, sp)
                    repo.update { it.copy(sbTextSizeSp = sp) }
                }
            }
        })

        rgOverlayInterval.setOnCheckedChangeListener { _, checkedId ->
            if (binding) return@setOnCheckedChangeListener
            val ms = rbOverlayIds.entries.firstOrNull { it.value == checkedId }?.key ?: 1000L
            repo.update { it.copy(overlayIntervalMs = ms) }
            OverlayService.notifyConfigChanged(this)
        }

        rgSbInterval.setOnCheckedChangeListener { _, checkedId ->
            if (binding) return@setOnCheckedChangeListener
            val ms = rbSbIds.entries.firstOrNull { it.value == checkedId }?.key ?: 1000L
            repo.update { it.copy(sbIntervalMs = ms) }
        }

        swOverlayUnit.setOnCheckedChangeListener { _, checked ->
            if (binding) return@setOnCheckedChangeListener
            repo.update { it.copy(overlayShowUnit = checked) }
            OverlayService.notifyConfigChanged(this)
        }

        swSbUnit.setOnCheckedChangeListener { _, checked ->
            if (binding) return@setOnCheckedChangeListener
            repo.update { it.copy(sbShowUnit = checked) }
        }

        rgSbPosition.setOnCheckedChangeListener { _, checkedId ->
            if (binding) return@setOnCheckedChangeListener
            val key = if (checkedId == R.id.rbPosLeft) {
                SbPosition.CLOCK_LEFT.key
            } else {
                SbPosition.CLOCK_RIGHT.key
            }
            repo.update { it.copy(sbPosition = key) }
        }

        sbOffsetX.setOnSeekBarChangeListener(object : SimpleSeekListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val v = progress - OFFSET_X_RANGE
                tvOffsetX.text = getString(R.string.offset_x, v)
                if (!binding && fromUser) repo.update { it.copy(sbOffsetX = v) }
            }
        })

        sbOffsetY.setOnSeekBarChangeListener(object : SimpleSeekListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val v = progress - OFFSET_Y_RANGE
                tvOffsetY.text = getString(R.string.offset_y, v)
                if (!binding && fromUser) repo.update { it.copy(sbOffsetY = v) }
            }
        })

        findViewById<ImageButton>(R.id.btnOffsetXMinus).setOnClickListener { stepOffsetX(-10) }
        findViewById<ImageButton>(R.id.btnOffsetXPlus).setOnClickListener { stepOffsetX(10) }
        findViewById<ImageButton>(R.id.btnOffsetYMinus).setOnClickListener { stepOffsetY(-10) }
        findViewById<ImageButton>(R.id.btnOffsetYPlus).setOnClickListener { stepOffsetY(10) }
    }

    private fun stepOffsetX(delta: Int) {
        val next = (repo.get().sbOffsetX + delta).coerceIn(-OFFSET_X_RANGE, OFFSET_X_RANGE)
        repo.update { it.copy(sbOffsetX = next) }
        sbOffsetX.progress = next + OFFSET_X_RANGE
        tvOffsetX.text = getString(R.string.offset_x, next)
    }

    private fun stepOffsetY(delta: Int) {
        val next = (repo.get().sbOffsetY + delta).coerceIn(-OFFSET_Y_RANGE, OFFSET_Y_RANGE)
        repo.update { it.copy(sbOffsetY = next) }
        sbOffsetY.progress = next + OFFSET_Y_RANGE
        tvOffsetY.text = getString(R.string.offset_y, next)
    }

    private fun saveOverlayFields() {
        val picked = overlayFieldsContainer.pickedFields()
        repo.update { it.copy(overlayFields = picked) }
        OverlayService.notifyConfigChanged(this)
    }

    private fun saveSbFields() {
        val picked = sbFieldsContainer.pickedFields()
        repo.update { it.copy(sbFields = picked) }
    }

    private fun LinearLayout.pickedFields(): List<Field> {
        val result = ArrayList<Field>(Field.values().size)
        for (i in 0 until childCount) {
            val cb = getChildAt(i) as? MaterialCheckBox ?: continue
            if (cb.isChecked) {
                (cb.tag as? Field)?.let { result.add(it) }
            }
        }
        return Field.values().filter { it in result }
    }

    // ==================================================================
    // 回灌 UI
    // ==================================================================
    private fun refreshUi() {
        val cfg = repo.get()
        binding = true
        try {
            swOverlay.isChecked = cfg.overlayEnabled
            swOverlayUnit.isChecked = cfg.overlayShowUnit

            for (i in 0 until overlayFieldsContainer.childCount) {
                val cb = overlayFieldsContainer.getChildAt(i) as? MaterialCheckBox ?: continue
                val f = cb.tag as? Field ?: continue
                cb.isChecked = cfg.overlayFields.contains(f)
            }

            pickerOverlay.color = cfg.overlayColor
            sbOverlaySize.progress = (cfg.overlayTextSizeSp.toInt() - MIN_OVERLAY_SP)
                .coerceIn(0, sbOverlaySize.max)
            tvOverlaySize.text = getString(R.string.label_text_size_value, cfg.overlayTextSizeSp)
            rgOverlayInterval.check(rbOverlayIds[cfg.overlayIntervalMs] ?: R.id.rbOverlay1000)

            swSb.isChecked = cfg.sbEnabled
            swSbUnit.isChecked = cfg.sbShowUnit

            for (i in 0 until sbFieldsContainer.childCount) {
                val cb = sbFieldsContainer.getChildAt(i) as? MaterialCheckBox ?: continue
                val f = cb.tag as? Field ?: continue
                cb.isChecked = cfg.sbFields.contains(f)
            }

            pickerSb.color = cfg.sbColor
            sbSbSize.progress = (cfg.sbTextSizeSp.toInt() - MIN_SB_SP).coerceIn(0, sbSbSize.max)
            tvSbSize.text = getString(R.string.label_text_size_value, cfg.sbTextSizeSp)
            rgSbInterval.check(rbSbIds[cfg.sbIntervalMs] ?: R.id.rbSb1000)

            rgSbPosition.check(
                if (cfg.sbPosition == SbPosition.CLOCK_LEFT.key) R.id.rbPosLeft else R.id.rbPosRight
            )

            sbOffsetX.progress = (cfg.sbOffsetX + OFFSET_X_RANGE).coerceIn(0, sbOffsetX.max)
            sbOffsetY.progress = (cfg.sbOffsetY + OFFSET_Y_RANGE).coerceIn(0, sbOffsetY.max)
            tvOffsetX.text = getString(R.string.offset_x, cfg.sbOffsetX)
            tvOffsetY.text = getString(R.string.offset_y, cfg.sbOffsetY)
        } finally {
            binding = false
        }
    }

    private fun refreshStatus() {
        val overlayOk = canDrawOverlay()
        val notifyOk = hasNotificationPermission()
        val rootOk = reader.isRootAvailable()

        val snap = try {
            reader.readOnce()
        } catch (e: Exception) {
            null
        }

        val sb = StringBuilder()
        sb.append(getString(R.string.status_overlay_perm, yesNo(overlayOk))).append('\n')
        sb.append(getString(R.string.status_notify_perm, yesNo(notifyOk))).append('\n')
        sb.append(getString(R.string.status_root, yesNo(rootOk))).append('\n')
        if (snap != null) {
            sb.append('\n')
            sb.append(getString(R.string.status_source, snap.source)).append('\n')
            sb.append(getString(R.string.status_power, snap.powerW)).append('\n')
            sb.append(getString(R.string.status_current, snap.currentMa)).append('\n')
            sb.append(getString(R.string.status_voltage, snap.voltageV)).append('\n')
            sb.append(getString(R.string.status_temp, snap.tempC)).append('\n')
            sb.append(getString(R.string.status_level, snap.level))
            if (!rootOk) {
                sb.append('\n').append(getString(R.string.status_no_root_hint))
            }
        }
        tvStatus.text = sb.toString()

        btnGrantOverlay.isEnabled = !overlayOk
        btnGrantNotify.isEnabled = !notifyOk
        btnGrantOverlay.text =
            if (overlayOk) getString(R.string.grant_overlay_done) else getString(R.string.grant_overlay)
        btnGrantNotify.text =
            if (notifyOk) getString(R.string.grant_notify_done) else getString(R.string.grant_notify)
    }

    private fun yesNo(b: Boolean) = if (b) "✓" else "✗"

    // ==================================================================
    // 权限 / root 操作
    // ==================================================================
    private fun canDrawOverlay(): Boolean = Settings.canDrawOverlays(this)

    private fun hasNotificationPermission(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(
            this, Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                startActivity(
                    Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:$packageName")
                    )
                )
            } catch (e: Exception) {
                Toast.makeText(this, R.string.toast_open_settings_failed, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ActivityCompat.requestPermissions(
                this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQ_NOTIFY
            )
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ_NOTIFY) refreshStatus()
    }

    /**
     * 重启 SystemUI。
     * 避坑 4：模块 APK 升级后，SystemUI 进程里跑的还是旧代码，
     * 必须杀掉 SystemUI（system_server 会自动重新拉起）才会加载新模块。
     */
    private fun restartSystemUi() {
        btnRestartSystemUi.isEnabled = false
        Toast.makeText(this, R.string.toast_restarting_systemui, Toast.LENGTH_SHORT).show()
        thread {
            val ok = try {
                val helper = RootHelper()
                val started = helper.start()
                val result = if (started) {
                    helper.exec("pkill -f com.android.systemui", timeoutMs = 8000)
                    true
                } else {
                    false
                }
                helper.close()
                result
            } catch (e: Exception) {
                false
            }
            runOnUiThread {
                btnRestartSystemUi.isEnabled = true
                Toast.makeText(
                    this,
                    if (ok) R.string.toast_restart_done else R.string.toast_restart_failed,
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun runDiagnose() {
        btnDiagnose.isEnabled = false
        tvDiagnose.setText(R.string.diagnose_running)
        thread {
            val helper = RootHelper()
            helper.start()
            val text = try {
                BatteryProbe.diagnose(helper)
            } catch (e: Exception) {
                "诊断失败: ${e.message}"
            } finally {
                helper.close()
            }
            runOnUiThread {
                btnDiagnose.isEnabled = true
                tvDiagnose.text = text
            }
        }
    }

    override fun onDestroy() {
        reader.destroy()
        super.onDestroy()
    }

    companion object {
        private const val REQ_NOTIFY = 1001

        const val MIN_OVERLAY_SP = 8     // 悬浮窗字号范围 8~28sp
        const val MIN_SB_SP = 8          // 状态栏字号范围 8~20sp
        const val OFFSET_X_RANGE = 2000  // 水平偏移 ±2000px
        const val OFFSET_Y_RANGE = 1000  // 垂直偏移 ±1000px

        val PRESET_COLORS = intArrayOf(
            0xFF00E676.toInt(),  // 绿
            0xFFFFFFFF.toInt(),  // 白
            0xFF000000.toInt(),  // 黑
            0xFFFF5252.toInt(),  // 红
            0xFFFFEB3B.toInt(),  // 黄
            0xFF448AFF.toInt(),  // 蓝
            0xFFE040FB.toInt(),  // 紫
            0xFF69F0AE.toInt()   // 青绿
        )
    }
}

/** 只实现需要的方法，避免每个 SeekBar 都写一整套空实现 */
private interface SimpleSeekListener : SeekBar.OnSeekBarChangeListener {
    override fun onStartTrackingTouch(seekBar: SeekBar?) {}
    override fun onStopTrackingTouch(seekBar: SeekBar?) {}
}
