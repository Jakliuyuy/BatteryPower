package com.jakliuyuy.batterypower.ui.settings

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.ScrollView
import android.widget.Toast
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.jakliuyuy.batterypower.R
import com.jakliuyuy.batterypower.app.OverlayService
import com.jakliuyuy.batterypower.core.config.AppConfig
import com.jakliuyuy.batterypower.core.log.BLog
import com.jakliuyuy.batterypower.ui.AboutActivity
import com.jakliuyuy.batterypower.ui.BaseActivity
import com.jakliuyuy.batterypower.ui.ModuleStatus
import com.jakliuyuy.batterypower.ui.Ui

/** Advanced settings (spec sections 68, 83, 99, 110, 121, 149). */
class AdvancedActivity : BaseActivity() {

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

        val systemCard = Ui.cardContent(this)
        systemCard.addView(Ui.sectionTitle(this, "系统"))
        systemCard.addView(
            Ui.navRow(this, "重启 SystemUI", "pkill -f com.android.systemui") {
                confirmRestartSystemUi(this)
            }
        )
        systemCard.addView(Ui.divider(this))
        systemCard.addView(
            Ui.navRow(this, "Root 状态", if (engine.shellAlive()) "Shell READY" else "Shell 未连接") {
                engine.sampleOnce { snapshot ->
                    Toast.makeText(
                        this,
                        "source=${snapshot.source.displayName} valid=${snapshot.valid}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        )
        systemCard.addView(Ui.divider(this))
        systemCard.addView(
            Ui.navRow(this, "LSPosed 状态", if (ModuleStatus.isLsposedManagerInstalled(this)) "环境已检测" else "环境未检测") {
                ModuleStatus.pingHook(this) { state ->
                    Toast.makeText(this, "Hook: ${state.name}", Toast.LENGTH_SHORT).show()
                }
            }
        )
        systemCard.addView(Ui.divider(this))
        systemCard.addView(
            Ui.switchRow(this, "开机自动恢复悬浮窗", "仅在悬浮窗开关打开时生效", true) { checked ->
                // Feature flag for boot restore; overlay.enabled remains the authority.
                configStore.update { it.copy(flags = it.flags.copy(enableOverlay = checked)) }
            }
        )
        root.addView(Ui.card(this, systemCard))

        val flagsCard = Ui.cardContent(this)
        flagsCard.addView(Ui.sectionTitle(this, "功能开关"))
        flagsCard.addView(Ui.bodyText(this, "出现问题时可以单独关闭某个功能，而不必卸载模块。"))
        flagsCard.addView(
            Ui.switchRow(this, "悬浮窗模块", "关闭后不会启动 OverlayService", config.flags.enableOverlay) { checked ->
                configStore.update { it.copy(flags = it.flags.copy(enableOverlay = checked)) }
                if (!checked) OverlayService.stop(this)
            }
        )
        flagsCard.addView(Ui.divider(this))
        flagsCard.addView(
            Ui.switchRow(this, "状态栏 Hook", "关闭后 SystemUI 不显示功率", config.flags.enableStatusBarHook) { checked ->
                configStore.update { it.copy(flags = it.flags.copy(enableStatusBarHook = checked)) }
            }
        )
        flagsCard.addView(Ui.divider(this))
        flagsCard.addView(
            Ui.switchRow(this, "Root 读取", "关闭后仅使用 BatteryManager", config.flags.enableRootReader) { checked ->
                configStore.update { it.copy(flags = it.flags.copy(enableRootReader = checked)) }
            }
        )
        flagsCard.addView(Ui.divider(this))
        flagsCard.addView(
            Ui.switchRow(this, "BatteryManager 降级", "无 Root 时继续显示可用字段", config.flags.enableBatteryManagerFallback) { checked ->
                configStore.update { it.copy(flags = it.flags.copy(enableBatteryManagerFallback = checked)) }
            }
        )
        flagsCard.addView(Ui.divider(this))
        flagsCard.addView(
            Ui.switchRow(this, "自动缩放", "状态栏文字过宽时缩小字号", config.flags.enableAutoScale) { checked ->
                configStore.update { it.copy(flags = it.flags.copy(enableAutoScale = checked)) }
            }
        )
        flagsCard.addView(Ui.divider(this))
        flagsCard.addView(
            Ui.switchRow(this, "自适应字段", "空间不足时隐藏低优先级字段", config.flags.enableAdaptiveFields) { checked ->
                configStore.update { it.copy(flags = it.flags.copy(enableAdaptiveFields = checked)) }
            }
        )
        flagsCard.addView(Ui.divider(this))
        flagsCard.addView(
            Ui.switchRow(this, "界面动画", "关闭后不创建额外动画任务", config.flags.enableAnimations) { checked ->
                configStore.update { it.copy(flags = it.flags.copy(enableAnimations = checked)) }
            }
        )
        flagsCard.addView(Ui.divider(this))
        flagsCard.addView(
            Ui.switchRow(this, "调试日志", "在 logcat 输出 BatteryPower 详细日志", config.flags.enableDebugLog) { checked ->
                configStore.update { it.copy(flags = it.flags.copy(enableDebugLog = checked)) }
                BLog.setDebugEnabled(checked)
            }
        )
        root.addView(Ui.card(this, flagsCard))

        val dangerCard = Ui.cardContent(this)
        dangerCard.addView(Ui.sectionTitle(this, "维护"))
        dangerCard.addView(
            Ui.navRow(this, "恢复默认配置", "颜色 / 字号 / 显示项目 / 位置 / 偏移 / 刷新频率") {
                confirmReset(this)
            }
        )
        dangerCard.addView(Ui.divider(this))
        dangerCard.addView(
            Ui.navRow(this, "清除 SystemUI 缓存", "删除状态栏本地配置缓存") {
                // The cache lives in the SystemUI process; reset bumps the version so
                // SystemUI re-reads the authoritative configuration.
                configStore.update { it.copy(configVersion = it.configVersion + 1) }
                Toast.makeText(this, "已提示 SystemUI 重新读取配置", Toast.LENGTH_SHORT).show()
            }
        )
        dangerCard.addView(Ui.divider(this))
        dangerCard.addView(
            Ui.navRow(this, getString(R.string.title_about), "版本与开源信息") {
                startActivity(Intent(this, AboutActivity::class.java))
            }
        )
        root.addView(Ui.card(this, dangerCard))
    }

    private fun confirmReset(context: Context) {
        try {
            MaterialAlertDialogBuilder(context)
                .setTitle("确定恢复所有设置？")
                .setMessage("包括：颜色、字号、显示项目、悬浮窗位置、状态栏偏移、刷新频率。")
                .setNegativeButton(R.string.action_cancel, null)
                .setPositiveButton(R.string.action_reset) { _, _ ->
                    configStore.reset()
                    recreate()
                    Toast.makeText(context, "已恢复默认配置", Toast.LENGTH_SHORT).show()
                }
                .show()
        } catch (t: Throwable) {
            BLog.w("UI", "reset dialog failed: ${t.message}")
        }
    }

    override fun onConfigChanged(config: AppConfig) {
        if (!isFinishing) recreate()
    }

    companion object {
        /** Spec section 68: always confirm before restarting SystemUI. */
        fun confirmRestartSystemUi(activity: BaseActivity) {
            try {
                MaterialAlertDialogBuilder(activity)
                    .setTitle("重启 SystemUI")
                    .setMessage("SystemUI 将重新启动，状态栏可能短暂闪烁。")
                    .setNegativeButton(R.string.action_cancel, null)
                    .setPositiveButton("重启") { _, _ ->
                        activity.engine.runRootCommandAsync("pkill -f com.android.systemui") { result ->
                            val message = if (result.success) "已发送重启命令" else "重启失败：${result.error?.displayName ?: "未知错误"}"
                            try {
                                Toast.makeText(activity, message, Toast.LENGTH_SHORT).show()
                            } catch (t: Throwable) {
                                BLog.w("UI", "toast failed: ${t.message}")
                            }
                        }
                    }
                    .show()
            } catch (t: Throwable) {
                BLog.w("UI", "restart dialog failed: ${t.message}")
            }
        }
    }
}
