package com.jakliuyuy.batterypower.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.util.TypedValue
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.jakliuyuy.batterypower.R
import com.jakliuyuy.batterypower.core.config.ConfigContract
import com.jakliuyuy.batterypower.core.model.ProbeResult
import com.jakliuyuy.batterypower.ui.settings.AdvancedActivity
import java.util.Locale

/**
 * Diagnostics center (spec sections 69, 146).
 * Shows raw sysfs values, the parsed units, the DeviceProfile in use and the
 * current hook status. Copyable text never contains account or environment data.
 */
class DiagnosticsActivity : BaseActivity() {

    private lateinit var reportText: TextView
    private lateinit var statusText: TextView
    private var lastProbe: ProbeResult? = null
    private val reportBuilder = StringBuilder()

    override fun wantsBatteryUpdates(): Boolean = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val scrollView = ScrollView(this)
        val root = Ui.scrollRoot(this)
        scrollView.addView(root)
        setContentView(scrollView)

        statusText = TextView(this).apply {
            text = "正在检测..."
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setTextColor(Ui.secondaryColor(this@DiagnosticsActivity))
        }
        reportText = TextView(this).apply {
            textSize = 12f
            typeface = android.graphics.Typeface.MONOSPACE
            setTextIsSelectable(true)
            setTextColor(Ui.primaryColor(this@DiagnosticsActivity))
            setPadding(Ui.dp(this@DiagnosticsActivity, 8f), Ui.dp(this@DiagnosticsActivity, 8f), Ui.dp(this@DiagnosticsActivity, 8f), Ui.dp(this@DiagnosticsActivity, 8f))
        }

        val statusCard = Ui.cardContent(this)
        statusCard.addView(Ui.sectionTitle(this, "状态"))
        statusCard.addView(statusText)
        root.addView(Ui.card(this, statusCard))

        val reportCard = Ui.cardContent(this)
        reportCard.addView(Ui.sectionTitle(this, "诊断信息"))
        reportCard.addView(reportText)
        root.addView(Ui.card(this, reportCard))

        val actionCard = Ui.cardContent(this)
        actionCard.addView(Ui.sectionTitle(this, "操作"))
        actionCard.addView(Ui.navRow(this, "重新检测", "重新探测电池节点与 Root") { runProbe() })
        actionCard.addView(Ui.divider(this))
        actionCard.addView(Ui.navRow(this, "测试 Root Shell", "验证常驻 su 通道") { testShell() })
        actionCard.addView(Ui.divider(this))
        actionCard.addView(Ui.navRow(this, "测试 Provider", "验证配置跨进程读取") { testProvider() })
        actionCard.addView(Ui.divider(this))
        actionCard.addView(Ui.navRow(this, getString(R.string.action_copy), "复制技术信息到剪贴板") { copyReport() })
        actionCard.addView(Ui.divider(this))
        actionCard.addView(
            Ui.navRow(this, getString(R.string.action_restart_systemui), "pkill -f com.android.systemui") {
                AdvancedActivity.confirmRestartSystemUi(this)
            }
        )
        root.addView(Ui.card(this, actionCard))

        runProbe()
    }

    private fun runProbe() {
        statusText.text = "正在探测电池节点..."
        engine.probeAsync { result ->
            lastProbe = result
            buildReport(result)
            statusText.text = if (result.ok) "探测成功" else "探测失败：${result.message ?: result.error?.displayName}"
        }
    }

    private fun testShell() {
        engine.probeAsync { result ->
            val alive = engine.shellAlive()
            Toast.makeText(
                this,
                if (result.ok && alive) "Root Shell 正常（${result.elapsedMs}ms）" else "Root Shell 不可用",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun testProvider() {
        try {
            val authority = ConfigContract.authorityFor(packageName)
            val uri = ConfigContract.buildVersionUri(authority)
            val cursor = contentResolver.query(uri, null, null, null, null)
            val version = if (cursor != null && cursor.moveToFirst()) {
                cursor.getInt(cursor.getColumnIndexOrThrow(ConfigContract.COL_VERSION))
            } else -1
            cursor?.close()
            Toast.makeText(this, "Provider 正常，configVersion=$version", Toast.LENGTH_SHORT).show()
        } catch (t: Throwable) {
            Toast.makeText(this, "Provider 不可用：${t.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun buildReport(probe: ProbeResult) {
        val snapshot = engine.lastSnapshot()
        val config = configStore.get()
        val sb = StringBuilder()
        sb.append("Device\n").append("  ").append(Build.DEVICE).append(" / ").append(Build.MODEL).append('\n')
        sb.append("  profile=").append(snapshot.deviceProfileName.ifEmpty { "n/a" }).append('\n')
        sb.append('\n')
        sb.append("Android\n").append("  ").append(Build.VERSION.RELEASE).append(" (API ").append(Build.VERSION.SDK_INT).append(")\n")
        sb.append('\n')
        sb.append("Root\n")
        sb.append("  shell=").append(if (engine.shellAlive()) "READY" else "UNAVAILABLE").append('\n')
        sb.append("  probe=").append(if (probe.ok) "OK" else "FAIL").append('\n')
        if (!probe.message.isNullOrEmpty()) sb.append("  message=").append(probe.message).append('\n')
        sb.append('\n')
        sb.append("Battery\n")
        sb.append("  path=").append(probe.batteryPath.ifEmpty { "(none)" }).append('\n')
        sb.append("  candidates=").append(probe.candidates.joinToString(",").ifEmpty { "(none)" }).append('\n')
        for ((key, value) in probe.raw) {
            sb.append("  ").append(key).append('=').append(value).append('\n')
        }
        sb.append('\n')
        sb.append("Parsed\n")
        sb.append("  power=").append(snapshot.powerUw?.toString() ?: "--").append(" uW\n")
        sb.append("  current=").append(snapshot.currentMa?.toString() ?: "--").append(" mA\n")
        sb.append("  voltage=").append(snapshot.voltageUv?.toString() ?: "--").append(" uV\n")
        sb.append("  temp=").append(snapshot.temperatureDeciC?.toString() ?: "--").append(" (0.1C)\n")
        sb.append("  capacity=").append(snapshot.capacityPercent?.toString() ?: "--").append(" %\n")
        sb.append("  status=").append(snapshot.status.displayName).append('\n')
        sb.append("  source=").append(snapshot.source.displayName).append('\n')
        if (snapshot.notes.isNotEmpty()) {
            sb.append("  notes=").append(snapshot.notes.joinToString("; ")).append('\n')
        }
        sb.append('\n')
        sb.append("Config\n")
        sb.append("  schema=").append(config.schemaVersion).append(" version=").append(config.configVersion).append('\n')
        sb.append("  fields=").append(
            listOfNotNull(
                if (config.display.power) "power" else null,
                if (config.display.current) "current" else null,
                if (config.display.voltage) "voltage" else null,
                if (config.display.temperature) "temp" else null,
                if (config.display.capacity) "capacity" else null
            ).joinToString(",")
        ).append('\n')
        sb.append("  overlayFont=").append(String.format(Locale.US, "%.0fsp", config.overlay.fontSizeSp))
        sb.append(" statusBarFont=").append(String.format(Locale.US, "%.0fsp", config.statusBar.fontSizeSp)).append('\n')
        sb.append('\n')
        sb.append("Hook\n")
        sb.append("  lsposed=").append(if (ModuleStatus.isLsposedManagerInstalled(this)) "detected" else "not-detected").append('\n')
        sb.append("  hook=").append(ModuleStatus.hookState().name).append('\n')

        reportBuilder.clear()
        reportBuilder.append(sb)
        reportText.text = sb.toString()
    }

    private fun copyReport() {
        try {
            val manager = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
            manager.setPrimaryClip(ClipData.newPlainText("BatteryPowerDiagnostics", reportBuilder.toString()))
            Toast.makeText(this, R.string.copied, Toast.LENGTH_SHORT).show()
        } catch (t: Throwable) {
            Toast.makeText(this, "复制失败", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onBatterySnapshot(snapshot: com.jakliuyuy.batterypower.core.model.BatterySnapshot) {
        lastProbe?.let { buildReport(it) }
    }
}
