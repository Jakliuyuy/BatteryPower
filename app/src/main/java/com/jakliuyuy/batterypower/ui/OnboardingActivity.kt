package com.jakliuyuy.batterypower.ui

import android.content.Intent
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.google.android.material.button.MaterialButton
import com.jakliuyuy.batterypower.R

/**
 * First-run checklist (spec section 93).
 * The user is never forced to enable everything.
 */
class OnboardingActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val scrollView = ScrollView(this)
        val root = Ui.scrollRoot(this)
        scrollView.addView(root)
        setContentView(scrollView)

        val header = TextView(this).apply {
            text = "欢迎使用 Battery Power"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 24f)
            setTextColor(Ui.primaryColor(this@OnboardingActivity))
            setPadding(Ui.dp(this@OnboardingActivity, 8f), Ui.dp(this@OnboardingActivity, 24f), Ui.dp(this@OnboardingActivity, 8f), Ui.dp(this@OnboardingActivity, 8f))
        }
        root.addView(header)
        root.addView(Ui.bodyText(this, "请先完成以下准备，之后可以随时回来调整。"))

        val card = Ui.cardContent(this)
        card.addView(Ui.navRow(this, "① Root 权限", "需要 Root 才能读取 power_now 等电池节点") {
            // Root status is verified on the diagnostics page.
            startActivity(Intent(this, DiagnosticsActivity::class.java))
        })
        card.addView(Ui.divider(this))
        card.addView(Ui.navRow(this, "② 悬浮窗权限", "显示在其他应用上层") {
            PermissionHelper.requestOverlayPermission(this) { intent -> startActivity(intent) }
        })
        card.addView(Ui.divider(this))
        card.addView(Ui.navRow(this, "③ LSPosed 模块", "Scope 勾选 SystemUI 后重启 SystemUI") {})
        root.addView(Ui.card(this, card))

        val skip = MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
            text = "跳过"
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = Ui.dp(this@OnboardingActivity, 8f) }
        }
        skip.setOnClickListener { finishOnboarding() }

        val start = MaterialButton(this).apply {
            text = "开始使用"
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            gravity = Gravity.CENTER
        }
        start.setOnClickListener { finishOnboarding() }

        root.addView(skip)
        root.addView(start)
    }

    private fun finishOnboarding() {
        configStore.update { it.copy(onboarded = true) }
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}
