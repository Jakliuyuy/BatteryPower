package com.jakliuyuy.batterypower.ui.color

import android.graphics.Color
import android.os.Bundle
import android.text.Editable
import android.text.InputFilter
import android.text.TextWatcher
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.google.android.material.slider.Slider
import com.jakliuyuy.batterypower.R
import com.jakliuyuy.batterypower.core.config.AppConfig
import com.jakliuyuy.batterypower.core.config.ColorConfig
import com.jakliuyuy.batterypower.ui.BaseActivity
import com.jakliuyuy.batterypower.ui.Ui
import java.util.Locale

/**
 * HSV colour editor with live preview (spec sections 15, 140).
 *
 * - HSV sliders and HEX input stay bidirectionally in sync
 * - invalid HEX input is rejected and never committed
 * - overlay and status bar colours are stored separately (spec 139.3)
 */
class ColorActivity : BaseActivity() {

    companion object {
        const val EXTRA_TARGET = "target"
        const val TARGET_OVERLAY = 0
        const val TARGET_STATUS_BAR = 1

        private val PRESETS = listOf(
            "白" to 0xFFFFFFFF.toInt(),
            "绿" to 0xFF42C76A.toInt(),
            "蓝" to 0xFF5B8DEF.toInt(),
            "青" to 0xFF4DD0E1.toInt(),
            "黄" to 0xFFFFEB3B.toInt(),
            "橙" to 0xFFFF9800.toInt(),
            "红" to 0xFFF44336.toInt(),
            "紫" to 0xFFB06BEF.toInt()
        )
    }

    private var target = TARGET_OVERLAY

    private lateinit var previewText: TextView
    private lateinit var hexInput: EditText
    private lateinit var hueSlider: Slider
    private lateinit var satSlider: Slider
    private lateinit var valSlider: Slider

    private val hsv = FloatArray(3)
    private var updating = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        target = intent.getIntExtra(EXTRA_TARGET, TARGET_OVERLAY)
        val scrollView = ScrollView(this)
        val root = Ui.scrollRoot(this)
        scrollView.addView(root)
        setContentView(scrollView)

        val current = currentColor()
        Color.colorToHSV(current, hsv)
        build(root)
        updatePreview()
    }

    private fun currentColor(): Int {
        val config = configStore.get()
        return if (target == TARGET_OVERLAY) config.overlayColor.argb else config.statusBarColor.argb
    }

    private fun commit(argb: Int) {
        configStore.update { config ->
            if (target == TARGET_OVERLAY) {
                config.copy(overlayColor = ColorConfig(argb = argb, autoColor = false))
            } else {
                config.copy(statusBarColor = ColorConfig(argb = argb, autoColor = false))
            }
        }
    }

    private fun build(root: LinearLayout) {
        val title = if (target == TARGET_OVERLAY) "悬浮窗颜色" else "状态栏颜色"

        val previewCard = Ui.cardContent(this)
        previewCard.addView(Ui.sectionTitle(this, "实时预览"))
        previewText = TextView(this).apply {
            text = "+3.46W  865mA  4.005V"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            gravity = Gravity.CENTER
            typeface = android.graphics.Typeface.MONOSPACE
            setPadding(Ui.dp(this@ColorActivity, 12f), Ui.dp(this@ColorActivity, 20f), Ui.dp(this@ColorActivity, 12f), Ui.dp(this@ColorActivity, 20f))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        previewCard.addView(previewText)
        root.addView(Ui.card(this, previewCard))

        val presetCard = Ui.cardContent(this)
        presetCard.addView(Ui.sectionTitle(this, "预设颜色"))
        val presetGroup = com.google.android.material.button.MaterialButtonToggleGroup(this).apply {
            isSingleSelection = true
        }
        PRESETS.forEach { (label, argb) ->
            val button = com.google.android.material.button.MaterialButton(
                this,
                null,
                com.google.android.material.R.attr.materialButtonOutlinedStyle
            ).apply {
                id = View.generateViewId()
                text = label
                textSize = 12f
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            }
            presetGroup.addView(button)
            button.setOnClickListener {
                Color.colorToHSV(argb, hsv)
                syncSlidersFromHsv()
                updatePreview()
                commit(argb)
            }
        }
        presetCard.addView(presetGroup)
        root.addView(Ui.card(this, presetCard))

        val hsvCard = Ui.cardContent(this)
        hsvCard.addView(Ui.sectionTitle(this, "自定义"))
        hueSlider = makeSlider(0f, 360f)
        satSlider = makeSlider(0f, 100f)
        valSlider = makeSlider(0f, 100f)
        hsvCard.addView(labeledSlider("Hue", hueSlider))
        hsvCard.addView(labeledSlider("Saturation", satSlider))
        hsvCard.addView(labeledSlider("Brightness", valSlider))
        syncSlidersFromHsv()
        root.addView(Ui.card(this, hsvCard))

        val hexCard = Ui.cardContent(this)
        hexCard.addView(Ui.sectionTitle(this, "HEX"))
        hexInput = EditText(this).apply {
            setText(String.format(Locale.US, "#%08X", currentColor()))
            setTextColor(Ui.primaryColor(this@ColorActivity))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            filters = arrayOf(InputFilter.LengthFilter(9))
            setSingleLine(true)
        }
        hexInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (updating) return
                val parsed = parseHex(s?.toString())
                if (parsed == null) return
                Color.colorToHSV(parsed, hsv)
                syncSlidersFromHsv()
                updatePreview()
                commit(parsed)
            }
        })
        hexCard.addView(hexInput)
        hexCard.addView(Ui.bodyText(this, "输入 8 位 ARGB，例如 #FFFFFFFF。非法值不会被保存。"))
        root.addView(Ui.card(this, hexCard))

        val actionCard = Ui.cardContent(this)
        actionCard.addView(
            Ui.navRow(this, "恢复默认白色", "#FFFFFFFF") {
                Color.colorToHSV(0xFFFFFFFF.toInt(), hsv)
                syncSlidersFromHsv()
                updatePreview()
                commit(0xFFFFFFFF.toInt())
            }
        )
        root.addView(Ui.card(this, actionCard))

        setScreenTitle(title)
    }

    private fun setScreenTitle(value: String) {
        try {
            this.title = value
        } catch (t: Throwable) {
            // ignore
        }
    }

    private fun makeSlider(min: Float, max: Float): Slider {
        return Slider(this).apply {
            valueFrom = min
            valueTo = max
            stepSize = 1f
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            addOnChangeListener { _, _, fromUser ->
                if (!fromUser || updating) return@addOnChangeListener
                hsv[0] = hueSlider.value
                hsv[1] = satSlider.value / 100f
                hsv[2] = valSlider.value / 100f
                updatePreview()
                commit(Color.HSVToColor(hsv))
            }
        }
    }

    private fun labeledSlider(label: String, slider: Slider): LinearLayout {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(Ui.dp(this@ColorActivity, 16f), Ui.dp(this@ColorActivity, 4f), Ui.dp(this@ColorActivity, 16f), Ui.dp(this@ColorActivity, 4f))
        }
        container.addView(TextView(this).apply {
            text = label
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setTextColor(Ui.secondaryColor(this@ColorActivity))
        })
        container.addView(slider)
        return container
    }

    private fun syncSlidersFromHsv() {
        updating = true
        try {
            hueSlider.value = hsv[0].coerceIn(0f, 360f)
            satSlider.value = (hsv[1] * 100f).coerceIn(0f, 100f)
            valSlider.value = (hsv[2] * 100f).coerceIn(0f, 100f)
            val argb = Color.HSVToColor(hsv)
            hexInput.setText(String.format(Locale.US, "#%08X", argb))
            hexInput.setSelection(hexInput.text.length)
        } catch (t: Throwable) {
            // ignore
        } finally {
            updating = false
        }
    }

    private fun updatePreview() {
        val argb = Color.HSVToColor(hsv)
        previewText.setTextColor(argb)
    }

    private fun parseHex(raw: String?): Int? {
        if (raw.isNullOrBlank()) return null
        var value = raw.trim()
        if (value.startsWith("#")) value = value.substring(1)
        if (value.length == 6) value = "FF$value"
        if (value.length != 8) return null
        return try {
            value.toLong(16).toInt()
        } catch (t: Throwable) {
            null
        }
    }

    override fun onConfigChanged(config: AppConfig) {
        // External changes (e.g. reset) are reflected into the editor.
        val argb = currentColor()
        updating = true
        try {
            Color.colorToHSV(argb, hsv)
            hueSlider.value = hsv[0]
            satSlider.value = hsv[1] * 100f
            valSlider.value = hsv[2] * 100f
            hexInput.setText(String.format(Locale.US, "#%08X", argb))
            previewText.setTextColor(argb)
        } catch (t: Throwable) {
            // ignore
        } finally {
            updating = false
        }
    }
}
