package com.jakliuyuy.batterypower.app

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.os.Build
import android.util.TypedValue
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import com.jakliuyuy.batterypower.core.config.AppConfig
import com.jakliuyuy.batterypower.core.config.FONT_FAMILY_MONOSPACE
import com.jakliuyuy.batterypower.core.config.FONT_STYLE_BOLD
import com.jakliuyuy.batterypower.core.config.FONT_STYLE_MEDIUM
import com.jakliuyuy.batterypower.core.config.OVERLAY_FONT_MIN_SP
import com.jakliuyuy.batterypower.core.format.BatteryFormatter
import com.jakliuyuy.batterypower.core.model.BatterySnapshot
import kotlin.math.roundToInt

/**
 * Transparent, text-only overlay (spec sections 9, 77, 139.4, 143).
 *
 * Visual rules: no background, no card, no border, no shadow, no rounded
 * container. Only text is drawn.
 */
class OverlayView(context: Context) : FrameLayout(context) {

    interface DragCallback {
        fun onDrag(x: Int, y: Int)
        fun onDragEnd(x: Int, y: Int)
    }

    var dragCallback: DragCallback? = null

    @Volatile
    var locked: Boolean = false

    private val textView: TextView = TextView(context)

    @Volatile
    private var currentConfig: AppConfig? = null

    @Volatile
    private var currentSnapshot: BatterySnapshot = BatterySnapshot.empty()

    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop

    private var winX = 0
    private var winY = 0
    private var downRawX = 0f
    private var downRawY = 0f
    private var dragging = false

    init {
        isClickable = true
        // Fully transparent container: no background, no elevation.
        setBackgroundColor(Color.TRANSPARENT)
        elevation = 0f
        textView.setBackgroundColor(Color.TRANSPARENT)
        textView.includeFontPadding = false
        textView.setShadowLayer(0f, 0f, 0f, Color.TRANSPARENT)
        val params = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        addView(textView, params)
    }

    fun setWindowPosition(x: Int, y: Int) {
        winX = x
        winY = y
    }

    fun windowX(): Int = winX
    fun windowY(): Int = winY

    /** Applies configuration and re-renders with the latest snapshot. */
    fun applyConfig(config: AppConfig) {
        currentConfig = config
        locked = config.overlay.locked
        val overlay = config.overlay

        textView.setTextSize(TypedValue.COMPLEX_UNIT_SP, overlay.fontSizeSp)
        textView.setTypeface(resolveTypeface(config))
        textView.letterSpacing = overlay.letterSpacing / 10f
        textView.setLineSpacing(overlay.lineSpacingExtra, 1.0f)
        textView.setTextColor(config.overlayColor.argb)

        if (overlay.autoWrap) {
            textView.setSingleLine(false)
            textView.maxLines = 3
            textView.ellipsize = null
        } else {
            textView.setSingleLine(true)
            textView.ellipsize = android.text.TextUtils.TruncateAt.END
        }

        val maxWidthPx = (resources.displayMetrics.widthPixels -
            (20f * resources.displayMetrics.density)).roundToInt()
        textView.maxWidth = maxWidthPx.coerceAtLeast(40)

        if (overlay.backgroundAlpha > 0) {
            val alpha = overlay.backgroundAlpha.coerceIn(0, 255)
            textView.setBackgroundColor(Color.argb(alpha, 0, 0, 0))
        } else {
            textView.setBackgroundColor(Color.TRANSPARENT)
        }

        if (overlay.glow) {
            textView.setShadowLayer(4f, 0f, 0f, config.overlayColor.argb)
        } else {
            textView.setShadowLayer(0f, 0f, 0f, Color.TRANSPARENT)
        }

        render()
    }

    fun update(snapshot: BatterySnapshot) {
        currentSnapshot = snapshot
        render()
    }

    fun measureCurrentWidth(): Int {
        return try {
            textView.paint.measureText(textView.text.toString()).roundToInt() +
                textView.paddingLeft + textView.paddingRight
        } catch (t: Throwable) {
            0
        }
    }

    private fun render() {
        val config = currentConfig ?: return
        val snapshot = currentSnapshot
        val options = BatteryFormatter.Options(
            showUnit = config.overlay.showUnit,
            powerDecimals = config.precision.powerDecimals,
            currentDecimals = config.precision.currentDecimals,
            voltageDecimals = config.precision.voltageDecimals,
            temperatureDecimals = config.precision.temperatureDecimals,
            monospaceDigits = config.precision.monospaceDigits
        )
        val fields = BatteryFormatter.Fields(
            power = config.display.power,
            current = config.display.current,
            voltage = config.display.voltage,
            temperature = config.display.temperature,
            capacity = config.display.capacity
        )
        val text = BatteryFormatter.buildText(snapshot, fields, options)
        if (textView.text?.toString() == text) return
        textView.text = text
        if (!config.overlay.autoWrap) {
            shrinkToFit(config)
        }
    }

    /**
     * Spec section 77: in single-line mode the font shrinks (never below 8sp)
     * instead of letting the text run off screen.
     */
    private fun shrinkToFit(config: AppConfig) {
        val available = textView.maxWidth
        if (available <= 0) return
        var sizeSp = config.overlay.fontSizeSp
        textView.setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp)
        var guard = 0
        while (measureCurrentWidth() > available && sizeSp > OVERLAY_FONT_MIN_SP && guard < 40) {
            sizeSp = (sizeSp - 0.5f).coerceAtLeast(OVERLAY_FONT_MIN_SP)
            textView.setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp)
            guard++
        }
    }

    private fun resolveTypeface(config: AppConfig): Typeface {
        val base = if (config.overlay.fontFamily == FONT_FAMILY_MONOSPACE) {
            Typeface.MONOSPACE
        } else {
            Typeface.DEFAULT
        }
        return when (config.overlay.fontStyle) {
            FONT_STYLE_BOLD -> Typeface.create(base, Typeface.BOLD)
            FONT_STYLE_MEDIUM -> if (Build.VERSION.SDK_INT >= 28) {
                Typeface.create(base, 500, false)
            } else {
                Typeface.create(base, Typeface.BOLD)
            }
            else -> Typeface.create(base, Typeface.NORMAL)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (locked) return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downRawX = event.rawX
                downRawY = event.rawY
                dragging = false
                parent?.requestDisallowInterceptTouchEvent(true)
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                val dx = event.rawX - downRawX
                val dy = event.rawY - downRawY
                if (!dragging && (kotlin.math.abs(dx) > touchSlop || kotlin.math.abs(dy) > touchSlop)) {
                    dragging = true
                }
                if (dragging) {
                    val newX = winX + dx.roundToInt()
                    val newY = winY + dy.roundToInt()
                    downRawX = event.rawX
                    downRawY = event.rawY
                    winX = newX
                    winY = newY
                    dragCallback?.onDrag(newX, newY)
                }
                return true
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (dragging) {
                    dragCallback?.onDragEnd(winX, winY)
                }
                dragging = false
                return dragging || event.actionMasked == MotionEvent.ACTION_UP
            }
        }
        return super.onTouchEvent(event)
    }
}
