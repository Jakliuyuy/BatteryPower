package com.jakliuyuy.batterypower.ui

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.setPadding
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.card.MaterialCardView
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.slider.Slider
import kotlin.math.roundToInt

/**
 * Small programmatic UI kit used by the settings screens.
 *
 * Interaction principles (spec section 81): prefer Switch / Slider /
 * SegmentedButton / BottomSheet / Snackbar over traditional dialogs.
 */
object Ui {

    fun dp(context: Context, value: Float): Int =
        (value * context.resources.displayMetrics.density).roundToInt()

    fun sp(context: Context, value: Float): Float = value

    // ------------------------------------------------------------ containers

    fun scrollRoot(context: Context): LinearLayout {
        val root = LinearLayout(context)
        root.orientation = LinearLayout.VERTICAL
        val pad = dp(context, 16f)
        root.setPadding(pad, dp(context, 8f), pad, dp(context, 32f))
        root.layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        return root
    }

    fun card(context: Context, content: LinearLayout): MaterialCardView {
        val card = MaterialCardView(context).apply {
            radius = dp(context, 20f).toFloat()
            cardElevation = dp(context, 1f).toFloat()
            setContentPadding(dp(context, 4f), dp(context, 12f), dp(context, 4f), dp(context, 12f))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dp(context, 16f)
            }
        }
        card.addView(content)
        return card
    }

    fun cardContent(context: Context): LinearLayout {
        val ll = LinearLayout(context)
        ll.orientation = LinearLayout.VERTICAL
        ll.layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        return ll
    }

    fun sectionTitle(context: Context, text: String): TextView {
        return TextView(context).apply {
            this.text = text
            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_TitleSmall)
            setTextColor(secondaryColor(context))
            setPadding(dp(context, 8f), dp(context, 16f), dp(context, 8f), dp(context, 6f))
        }
    }

    fun bodyText(context: Context, text: String): TextView {
        return TextView(context).apply {
            this.text = text
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setTextColor(secondaryColor(context))
            setPadding(dp(context, 16f), dp(context, 6f), dp(context, 16f), dp(context, 6f))
        }
    }

    fun warningText(context: Context, text: String): TextView {
        return bodyText(context, text).apply {
            setTextColor(Color.parseColor("#FFFFB4AB"))
        }
    }

    // ------------------------------------------------------------------- rows

    fun switchRow(
        context: Context,
        title: String,
        summary: String?,
        checked: Boolean,
        enabled: Boolean = true,
        onChanged: (Boolean) -> Unit
    ): View {
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = dp(context, 64f)
            setPadding(dp(context, 16f), dp(context, 8f), dp(context, 12f), dp(context, 8f))
            isEnabled = enabled
            alpha = if (enabled) 1f else 0.5f
        }
        val texts = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        val titleView = TextView(context).apply {
            this.text = title
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            setTextColor(primaryColor(context))
        }
        texts.addView(titleView)
        if (!summary.isNullOrEmpty()) {
            texts.addView(TextView(context).apply {
                this.text = summary
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                setTextColor(secondaryColor(context))
            })
        }
        row.addView(texts)
        val switch = MaterialSwitch(context).apply {
            isChecked = checked
            isEnabled = enabled
        }
        switch.setOnCheckedChangeListener { _, isChecked -> onChanged(isChecked) }
        row.addView(switch)
        // Allow tapping anywhere on the row.
        row.setOnClickListener {
            if (enabled) switch.toggle()
        }
        return row
    }

    fun navRow(
        context: Context,
        title: String,
        summary: String? = null,
        onClick: () -> Unit
    ): View {
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = dp(context, 56f)
            setPadding(dp(context, 16f), dp(context, 10f), dp(context, 12f), dp(context, 10f))
        }
        val texts = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        texts.addView(TextView(context).apply {
            this.text = title
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            setTextColor(primaryColor(context))
        })
        if (!summary.isNullOrEmpty()) {
            texts.addView(TextView(context).apply {
                this.text = summary
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                setTextColor(secondaryColor(context))
            })
        }
        row.addView(texts)
        row.addView(TextView(context).apply {
            text = "›"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
            setTextColor(secondaryColor(context))
            setPadding(dp(context, 4f), 0, dp(context, 4f), 0)
        })
        row.setOnClickListener { onClick() }
        return row
    }

    /** Slider row with a live value label (spec section 21: button/slider stay in sync). */
    fun sliderRow(
        context: Context,
        title: String,
        value: Float,
        min: Float,
        max: Float,
        step: Float,
        formatter: (Float) -> String,
        onChanged: (Float) -> Unit,
        onCommit: ((Float) -> Unit)? = null
    ): View {
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(context, 16f), dp(context, 6f), dp(context, 16f), dp(context, 6f))
        }
        val header = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        header.addView(TextView(context).apply {
            this.text = title
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            setTextColor(primaryColor(context))
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        })
        val valueView = TextView(context).apply {
            this.text = formatter(value)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setTypeface(Typeface.MONOSPACE, Typeface.NORMAL)
            setTextColor(secondaryColor(context))
        }
        header.addView(valueView)
        container.addView(header)

        val slider = Slider(context).apply {
            valueFrom = min
            valueTo = max
            stepSize = step
            this.value = value.coerceIn(min, max)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        slider.addOnChangeListener { _, v, fromUser ->
            valueView.text = formatter(v)
            if (fromUser) onChanged(v)
        }
        slider.addOnSliderTouchListener(object : Slider.OnSliderTouchListener {
            override fun onStartTrackingTouch(slider: Slider) {}
            override fun onStopTrackingTouch(slider: Slider) {
                onCommit?.invoke(slider.value)
            }
        })
        container.addView(slider)
        return container
    }

    /** [-] value [+] row with long-press repeat (spec section 10, 21). */
    fun stepperRow(
        context: Context,
        title: String,
        valueLabel: String,
        onStep: (Int) -> Unit,
        onLongPressRepeat: ((Int) -> Unit)? = null
    ): View {
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(context, 16f), dp(context, 8f), dp(context, 16f), dp(context, 8f))
            minimumHeight = dp(context, 56f)
        }
        row.addView(TextView(context).apply {
            this.text = title
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            setTextColor(primaryColor(context))
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        })
        val valueText = TextView(context).apply {
            text = valueLabel
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setTypeface(Typeface.MONOSPACE, Typeface.NORMAL)
            setTextColor(primaryColor(context))
            setPadding(dp(context, 12f), 0, dp(context, 12f), 0)
        }
        fun makeButton(label: String, direction: Int): MaterialButton {
            val button = MaterialButton(context, null, com.google.android.material.R.attr.materialIconButtonFilledTonalStyle).apply {
                text = label
                textSize = 16f
                minimumWidth = 0
                minWidth = dp(context, 44f)
                minimumHeight = dp(context, 44f)
                setPadding(0, 0, 0, 0)
            }
            button.setOnClickListener { onStep(direction) }
            if (onLongPressRepeat != null) {
                button.setOnLongClickListener {
                    onLongPressRepeat(direction)
                    true
                }
            }
            return button
        }
        row.addView(makeButton("−", -1))
        row.addView(valueText)
        row.addView(makeButton("+", 1))
        return row
    }

    fun segmentedRow(
        context: Context,
        title: String,
        options: List<String>,
        selectedIndex: Int,
        onSelected: (Int) -> Unit
    ): View {
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(context, 16f), dp(context, 10f), dp(context, 16f), dp(context, 10f))
        }
        container.addView(TextView(context).apply {
            this.text = title
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            setTextColor(primaryColor(context))
            setPadding(0, 0, 0, dp(context, 8f))
        })
        val group = MaterialButtonToggleGroup(context).apply {
            isSingleSelection = true
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        options.forEachIndexed { index, label ->
            val button = MaterialButton(
                context,
                null,
                com.google.android.material.R.attr.materialButtonOutlinedStyle
            ).apply {
                id = View.generateViewId()
                text = label
                textSize = 13f
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            }
            group.addView(button)
            if (index == selectedIndex) group.check(button.id)
        }
        group.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            val index = group.indexOfChild(group.findViewById<View>(checkedId))
            if (index >= 0) onSelected(index)
        }
        container.addView(group)
        return container
    }

    fun chipToggleGroup(
        context: Context,
        options: List<String>,
        checked: List<Boolean>,
        onToggle: (Int, Boolean) -> Unit
    ): MaterialButtonToggleGroup {
        val group = MaterialButtonToggleGroup(context).apply {
            isSingleSelection = false
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        options.forEachIndexed { index, label ->
            val button = MaterialButton(
                context,
                null,
                com.google.android.material.R.attr.materialButtonOutlinedStyle
            ).apply {
                id = View.generateViewId()
                text = label
                textSize = 12f
                isCheckable = true
                minWidth = 0
                minimumWidth = 0
                setPadding(dp(context, 10f), 0, dp(context, 10f), 0)
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            }
            group.addView(button)
            if (checked.getOrNull(index) == true) group.check(button.id)
        }
        group.addOnButtonCheckedListener { _, checkedId, isChecked ->
            val index = group.indexOfChild(group.findViewById<View>(checkedId))
            if (index >= 0) onToggle(index, isChecked)
        }
        return group
    }

    fun divider(context: Context): View {
        return View(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(context, 1f)
            ).apply {
                setMargins(dp(context, 12f), dp(context, 4f), dp(context, 12f), dp(context, 4f))
            }
            setBackgroundColor(Color.parseColor("#22FFFFFF"))
        }
    }

    fun frameContainer(context: Context, view: View, paddingDp: Float = 16f): FrameLayout {
        return FrameLayout(context).apply {
            val p = dp(context, paddingDp)
            setPadding(p, p, p, p)
            addView(view)
        }
    }

    // ------------------------------------------------------------------ colors

    fun primaryColor(context: Context): Int {
        return resolveColor(context, android.R.attr.textColorPrimary, Color.WHITE)
    }

    fun secondaryColor(context: Context): Int {
        return resolveColor(context, android.R.attr.textColorSecondary, Color.LTGRAY)
    }

    private fun resolveColor(context: Context, attr: Int, fallback: Int): Int {
        return try {
            val typedValue = TypedValue()
            val resolved = context.theme.resolveAttribute(attr, typedValue, true)
            if (resolved) typedValue.data else fallback
        } catch (t: Throwable) {
            fallback
        }
    }
}
