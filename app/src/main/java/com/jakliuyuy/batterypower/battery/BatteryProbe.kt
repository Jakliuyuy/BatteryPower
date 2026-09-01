package com.jakliuyuy.batterypower.battery

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import com.jakliuyuy.batterypower.model.BatterySnapshot
import java.io.File

/**
 * 电池数据读取。三条通道，按优先级降级：
 *
 *   1. sysfs 直读（SystemUI 进程 uid 1000 可直接读，最快最准）
 *   2. sysfs + root（主 App 进程，走常驻 su 会话）
 *   3. BatteryManager（无 root 兜底，电流可能恒 0）
 *
 * 避坑 2：current_now 的单位在不同内核上不一致！
 *   - 文档约定是 μA，但实测一加 Ace 6T（PLR110，内核 6.12）写的是 mA。
 *   - 这里做自动判定：绝对值 > 100000 视作 μA，否则视作 mA。
 *     典型充电电流 1000~10000mA（或 1e6~1e7 μA），阈值 1e5 能可靠区分。
 *   更稳妥的是优先读 power_now（μW），它不带单位歧义。
 */
object BatteryProbe {

    private const val TAG = "BatteryPower/Probe"

    /** 候选电池节点根目录，兼容多机型 */
    private val ROOTS = listOf(
        "/sys/class/power_supply/battery",
        "/sys/class/power_supply/Battery",
        "/sys/class/power_supply/main"
    )

    /** 首次探测到的可用根目录 */
    @Volatile
    private var resolvedRoot: String? = null

    private fun rootDir(): String {
        resolvedRoot?.let { return it }
        val found = ROOTS.firstOrNull { File(it).exists() } ?: ROOTS.first()
        resolvedRoot = found
        return found
    }

    private fun path(name: String) = "${rootDir()}/$name"

    // =====================================================================
    // 通道 1：直接读文件（无需 root，SystemUI 进程可用）
    // =====================================================================
    fun readDirect(): BatterySnapshot? {
        val dir = rootDir()
        if (!File(dir).exists()) return null
        return parse(
            powerNow = readText(path("power_now")),
            currentNow = readText(path("current_now")),
            voltageNow = readText(path("voltage_now")),
            temp = readText(path("temp")),
            capacity = readText(path("capacity")),
            status = readText(path("status")),
            source = "sysfs"
        )
    }

    private fun readText(p: String): String? =
        try {
            val f = File(p)
            if (f.exists() && f.canRead()) f.readText().trim().ifEmpty { null } else null
        } catch (_: Exception) {
            null
        }

    // =====================================================================
    // 通道 2：root 读取（主 App 进程）
    // =====================================================================
    fun readViaRoot(helper: RootHelper): BatterySnapshot? {
        if (!helper.isAvailable && !helper.start()) return null
        val paths = listOf(
            path("power_now"),
            path("current_now"),
            path("voltage_now"),
            path("temp"),
            path("capacity"),
            path("status")
        )
        val values = helper.readNodes(paths)
        if (values.isEmpty()) return null
        return parse(
            powerNow = values[paths[0]],
            currentNow = values[paths[1]],
            voltageNow = values[paths[2]],
            temp = values[paths[3]],
            capacity = values[paths[4]],
            status = values[paths[5]],
            source = "sysfs+root"
        )
    }

    // =====================================================================
    // 通道 3：BatteryManager（无 root 兜底）
    // =====================================================================
    fun readViaBatteryManager(context: Context): BatterySnapshot {
        // 避坑 9：BATTERY_PROPERTY_VOLTAGE_NOW 在新 SDK 上被隐藏，编译不过。
        // 电压改从 sticky 广播的 EXTRA_VOLTAGE 取（单位 mV）。
        val sticky = context.registerReceiver(
            null,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        )
        val voltageMv = sticky?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0) ?: 0
        val tempTenth = sticky?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0
        val level = sticky?.getIntExtra(BatteryManager.EXTRA_LEVEL, 0) ?: 0
        val scale = sticky?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        val status = sticky?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL

        val bm = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
        // 文档单位是 μA，符号约定为负=放电；这里取绝对值后自行按充放电定符号
        val currentUa = bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
            ?: Int.MIN_VALUE
        val currentMa = if (currentUa == Int.MIN_VALUE) 0.0 else currentUa / 1000.0

        val voltageV = voltageMv / 1000.0
        val powerW = currentMa / 1000.0 * voltageV
        val signed = if (charging) kotlin.math.abs(powerW) else -kotlin.math.abs(powerW)

        return BatterySnapshot(
            powerW = signed,
            currentMa = if (charging) kotlin.math.abs(currentMa) else -kotlin.math.abs(currentMa),
            voltageV = voltageV,
            tempC = tempTenth / 10.0,
            level = if (scale > 0) (level * 100 / scale) else 0,
            charging = charging,
            source = "BatteryManager"
        )
    }

    // =====================================================================
    // 解析：统一处理单位换算与符号
    // =====================================================================
    fun parse(
        powerNow: String?,
        currentNow: String?,
        voltageNow: String?,
        temp: String?,
        capacity: String?,
        status: String?,
        source: String
    ): BatterySnapshot? {
        val voltageV = (voltageNow?.toDoubleOrNull() ?: return null) / 1_000_000.0

        // current_now 单位自动判定（见类注释）
        val currentRaw = currentNow?.toDoubleOrNull() ?: 0.0
        val currentMa = if (kotlin.math.abs(currentRaw) > 100_000.0) {
            currentRaw / 1000.0      // 原始单位是 μA → 转成 mA
        } else {
            currentRaw               // 原始单位就是 mA
        }

        // 优先用 power_now（μW → W），避开电流单位歧义
        val powerW = powerNow?.toDoubleOrNull()?.let { it / 1_000_000.0 }
            ?: (currentMa / 1000.0 * voltageV)

        val statusStr = status?.trim().orEmpty()
        val charging = statusStr.contains("Charg", ignoreCase = true) ||
                statusStr.contains("Full", ignoreCase = true)

        // 节点值常见约定：充电时电流为负（电流流入）。
        // 需求要求充电为正、放电为负，这里统一按 status 取符号。
        val signedPower = if (charging) kotlin.math.abs(powerW) else -kotlin.math.abs(powerW)
        val signedCurrent = if (charging) kotlin.math.abs(currentMa) else -kotlin.math.abs(currentMa)

        val tempRaw = temp?.toDoubleOrNull() ?: 0.0
        // 温度节点单位多数是 0.1℃；个别机型直接给 ℃，用数值范围兜底
        val tempC = if (tempRaw > 1000) tempRaw / 1000.0
        else if (tempRaw > 200) tempRaw / 100.0
        else tempRaw / 10.0

        return BatterySnapshot(
            powerW = signedPower,
            currentMa = signedCurrent,
            voltageV = voltageV,
            tempC = tempC,
            level = capacity?.toIntOrNull() ?: 0,
            charging = charging,
            source = source
        )
    }

    /** 诊断页用：列出所有探测到的节点路径与内容 */
    fun diagnose(helper: RootHelper?): String {
        val sb = StringBuilder()
        sb.appendLine("电池根目录: ${rootDir()}")
        sb.appendLine("直接可读: ${File(rootDir()).exists()}")
        sb.appendLine("root 会话: ${if (helper?.isAvailable == true) "已建立" else "不可用"}")
        sb.appendLine()
        sb.appendLine("--- 节点内容 ---")
        val names = listOf(
            "power_now", "current_now", "voltage_now", "temp",
            "capacity", "status", "charge_full_design", "technology"
        )
        names.forEach { n ->
            val p = path(n)
            val direct = readText(p) ?: "<读不到>"
            val rootVal = if (helper?.isAvailable == true) {
                helper.readNodes(listOf(p))[p]?.ifEmpty { "<空>" } ?: "<读不到>"
            } else {
                "<无 root>"
            }
            sb.appendLine("$n")
            sb.appendLine("  直读: $direct")
            sb.appendLine("  su  : $rootVal")
        }
        return sb.toString()
    }
}
