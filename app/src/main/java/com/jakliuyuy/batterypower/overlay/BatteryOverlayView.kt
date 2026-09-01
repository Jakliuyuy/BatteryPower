package com.jakliuyuy.batterypower.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.widget.TextView
import com.jakliuyuy.batterypower.R
import com.jakliuyuy.batterypower.data.renderBatteryText
import com.jakliuyuy.batterypower.model.BatterySnapshot
import com.jakliuyuy.batterypower.model.Config
import kotlin.math.abs

/**
 * 悬浮窗本体：一个全透明的 TextView。
 *
 * - 无背景、无边框（背景设为 null）
 * - 拖动移动，ACTION_UP 时回调位置给服务落盘
 * - 带描边阴影提升任意壁纸上的可读性
 */
@SuppressLint("ViewConstructor")
class BatteryOverlayView(context: Context) : TextView(context) {

    /** 拖动结束/移动时的位置回调 */
    var onPositionChanged: ((x: Int, y: Int) -> Unit)? = null

    private var cfg: Config = Config.default()
    private var lastSnapshot: BatterySnapshot = BatterySnapshot.EMPTY
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop

    // ===== 拖动状态 =====
    private var downRawX = 0f
    private var downRawY = 0f
    private var startX = 0
    private var startY = 0
    private var dragging = false

    val isAttached: Boolean get() = isAttachedToWindow

    init {
        background = null                       // 全透明，无背景无边框
        setPadding(6, 3, 6, 3)
        setSingleLine(true)
        includeFontPadding = false
        // 描边：深色壁纸/浅色壁纸都能看清
        setShadowLayer(3f, 0f, 0f, Color.BLACK)
        text = "..."
    }

    fun applyConfig(cfg: Config) {
        this.cfg = cfg
        setTextColor(cfg.overlayColor)
        textSize = cfg.overlayTextSizeSp        // TextView.textSize 单位就是 sp
        render(lastSnapshot)
    }

    fun update(snapshot: BatterySnapshot) {
        lastSnapshot = snapshot
        render(snapshot)
    }

    private fun render(s: BatterySnapshot) {
        text = when {
            !s.isValid && s.source.startsWith("no-root") ->
                context.getString(R.string.overlay_need_root)
            !s.isValid -> context.getString(R.string.overlay_no_data)
            else -> renderBatteryText(s, cfg.overlayFields, cfg.overlayShowUnit)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downRawX = event.rawX
                downRawY = event.rawY
                val lp = layoutParams as? android.view.WindowManager.LayoutParams
                startX = lp?.x ?: 0
                startY = lp?.y ?: 0
                dragging = false
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                val dx = event.rawX - downRawX
                val dy = event.rawY - downRawY
                if (!dragging && (abs(dx) > touchSlop || abs(dy) > touchSlop)) {
                    dragging = true
                }
                if (dragging) {
                    val newX = (startX + dx).toInt()
                    val newY = (startY + dy).toInt()
                    updatePosition(newX, newY)
                }
                return true
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (dragging) {
                    val lp = layoutParams as? android.view.WindowManager.LayoutParams
                    if (lp != null) {
                        // 松手时交给服务同步落盘（Commit，见 ConfigRepository）
                        onPositionChanged?.invoke(lp.x, lp.y)
                    }
                } else {
                    performClick()
                }
                dragging = false
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun updatePosition(x: Int, y: Int) {
        val lp = layoutParams as? android.view.WindowManager.LayoutParams ?: return
        lp.x = x
        lp.y = y
        try {
            (context.getSystemService(Context.WINDOW_SERVICE) as? android.view.WindowManager)
                ?.updateViewLayout(this, lp)
        } catch (_: Exception) {
        }
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }
}
