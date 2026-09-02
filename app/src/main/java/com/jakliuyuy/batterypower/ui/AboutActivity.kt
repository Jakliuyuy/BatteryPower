package com.jakliuyuy.batterypower.ui

import android.os.Build
import android.os.Bundle
import android.widget.ScrollView
import com.jakliuyuy.batterypower.BuildConfig
import com.jakliuyuy.batterypower.R

/** About screen (spec section 96). */
class AboutActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val scrollView = ScrollView(this)
        val root = Ui.scrollRoot(this)
        scrollView.addView(root)
        setContentView(scrollView)

        val card = Ui.cardContent(this)
        card.addView(Ui.sectionTitle(this, "Battery Power Overlay"))
        card.addView(Ui.bodyText(this, "Version ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})"))
        card.addView(Ui.bodyText(this, "Android ${Build.VERSION.RELEASE} / API ${Build.VERSION.SDK_INT}"))
        card.addView(Ui.bodyText(this, "Device ${Build.DEVICE} / ${Build.MODEL}"))
        card.addView(Ui.bodyText(this, "KernelSU + LSPosed"))
        card.addView(Ui.divider(this))
        card.addView(
            Ui.navRow(this, "开源项目", "github.com/Jakliuyuy/BatteryPower") {
                openUrl("https://github.com/Jakliuyuy/BatteryPower")
            }
        )
        card.addView(
            Ui.navRow(this, "问题反馈", "提交 Issue") {
                openUrl("https://github.com/Jakliuyuy/BatteryPower/issues")
            }
        )
        root.addView(Ui.card(this, card))

        val infoCard = Ui.cardContent(this)
        infoCard.addView(Ui.sectionTitle(this, "设计原则"))
        infoCard.addView(Ui.bodyText(this, "稳定 > 准确 > 持久化 > 兼容性 > UI > 扩展"))
        infoCard.addView(Ui.bodyText(this, "SystemUI 不崩、功率不乱、配置不丢、Root 通道稳定、UI 不干扰系统。"))
        root.addView(Ui.card(this, infoCard))
    }

    private fun openUrl(url: String) {
        try {
            startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url)))
        } catch (t: Throwable) {
            // ignore
        }
    }
}
