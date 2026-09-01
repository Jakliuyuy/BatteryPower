package com.jakliuyuy.batterypower.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View

/**
 * HSV 无极调色板：上方是 饱和度(S) × 明度(V) 平面，下方是色相(H)条。
 *
 * 用自绘实现是为了不引入任何第三方颜色选择库。
 */
class HsvColorPickerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    /** 颜色变化回调 */
    var onColorChanged: ((Int) -> Unit)? = null

    /** 当前颜色（ARGB，alpha 固定 255） */
    var color: Int
        get() = Color.HSVToColor(hsv)
        set(value) {
            Color.colorToHSV(value, hsv)
            hsv[1] = hsv[1].coerceIn(0f, 1f)
            hsv[2] = hsv[2].coerceIn(0f, 1f)
            rebuildSvBitmap()
            invalidate()
        }

    private val hsv = floatArrayOf(0f, 1f, 1f)

    private val svPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val huePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val markerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f
        color = Color.WHITE
    }
    private val markerFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1f
        color = Color.argb(80, 255, 255, 255)
    }

    private var svBitmap: Bitmap? = null

    /** SV 平面与色相条之间的间隙 */
    private val gap = (6 * resources.displayMetrics.density).toInt()
    /** 色相条高度 */
    private val hueBarHeight = (24 * resources.displayMetrics.density).toInt()

    private val svRect = RectF()
    private val hueRect = RectF()

    init {
        rebuildSvBitmap()
    }

    private fun rebuildSvBitmap() {
        val size = 96
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val hue = hsv[0]
        for (y in 0 until size) {
            // 上边 V=1，下边 V=0
            val v = 1f - y.toFloat() / (size - 1)
            for (x in 0 until size) {
                // 左边 S=0，右边 S=1
                val s = x.toFloat() / (size - 1)
                bmp.setPixel(x, y, Color.HSVToColor(floatArrayOf(hue, s, v)))
            }
        }
        svBitmap?.recycle()
        svBitmap = bmp
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        // 默认高度：SV 平面按宽度的一半 + 间隙 + 色相条
        val w = MeasureSpec.getSize(widthMeasureSpec)
        val desired = w / 2 + gap + hueBarHeight
        setMeasuredDimension(w, resolveSize(desired, heightMeasureSpec))
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val hueTop = h - hueBarHeight
        svRect.set(0f, 0f, w.toFloat(), (hueTop - gap).toFloat())
        hueRect.set(0f, hueTop.toFloat(), w.toFloat(), h.toFloat())

        huePaint.shader = LinearGradient(
            0f, 0f, w.toFloat(), 0f,
            HUE_COLORS, null, Shader.TileMode.CLAMP
        )
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // --- SV 平面 ---
        svBitmap?.let {
            canvas.drawBitmap(it, null, svRect, svPaint)
        }
        canvas.drawRect(svRect, borderPaint)

        // --- 色相条 ---
        canvas.drawRoundRect(hueRect, 4f, 4f, huePaint)
        canvas.drawRoundRect(hueRect, 4f, 4f, borderPaint)

        // --- 游标 ---
        // SV 平面上的游标
        val sx = svRect.left + hsv[1] * svRect.width()
        val sy = svRect.top + (1f - hsv[2]) * svRect.height()
        markerFillPaint.color = Color.HSVToColor(floatArrayOf(hsv[0], hsv[1], hsv[2]))
        canvas.drawCircle(sx, sy, 9f, markerPaint)
        canvas.drawCircle(sx, sy, 7f, markerFillPaint)

        // 色相条上的游标
        val hx = hueRect.left + (hsv[0] / 360f) * hueRect.width()
        val hy = hueRect.centerY()
        canvas.drawLine(hx, hueRect.top - 2, hx, hueRect.bottom + 2, markerPaint)
        markerFillPaint.color = Color.HSVToColor(floatArrayOf(hsv[0], 1f, 1f))
        canvas.drawCircle(hx, hy, 7f, markerFillPaint)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                handleTouch(event.x, event.y)
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                parent?.requestDisallowInterceptTouchEvent(false)
                performClick()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun handleTouch(x: Float, y: Float) {
        when {
            y <= svRect.bottom -> {
                // SV 平面
                val s = ((x - svRect.left) / svRect.width()).coerceIn(0f, 1f)
                val v = (1f - (y - svRect.top) / svRect.height()).coerceIn(0f, 1f)
                hsv[1] = s
                hsv[2] = v
                rebuildSvBitmap()
                invalidate()
                notifyChanged()
            }
            y >= hueRect.top -> {
                // 色相条
                hsv[0] = ((x - hueRect.left) / hueRect.width()).coerceIn(0f, 1f) * 360f
                rebuildSvBitmap()
                invalidate()
                notifyChanged()
            }
        }
    }

    private fun notifyChanged() {
        onColorChanged?.invoke(Color.HSVToColor(hsv))
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    private companion object {
        val HUE_COLORS = intArrayOf(
            Color.RED, Color.YELLOW, Color.GREEN, Color.CYAN,
            Color.BLUE, Color.MAGENTA, Color.RED
        )
    }
}
