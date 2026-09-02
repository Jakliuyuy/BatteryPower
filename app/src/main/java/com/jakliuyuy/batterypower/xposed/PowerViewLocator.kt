package com.jakliuyuy.batterypower.xposed

import android.os.Build
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import com.jakliuyuy.batterypower.core.config.ANCHOR_CLOCK_RIGHT
import com.jakliuyuy.batterypower.core.config.AppConfig
import com.jakliuyuy.batterypower.core.log.BLog
import kotlin.math.roundToInt

/**
 * Status bar positioning (spec sections 28-33, 144, 145).
 *
 * The algorithm never uses hard-coded status bar heights and never relies on
 * the Clock's LayoutParams: everything is derived from runtime screen
 * coordinates and the safe-area insets.
 */
object PowerViewLocator {

    data class Result(val x: Float, val y: Float, val overlapsClock: Boolean)

    fun locate(powerView: View, clock: View, root: ViewGroup, config: AppConfig): Result {
        return try {
            val density = root.resources.displayMetrics.density
            val gap = (config.statusBar.gapDp * density).roundToInt()

            val clockPos = IntArray(2)
            val rootPos = IntArray(2)
            clock.getLocationOnScreen(clockPos)
            root.getLocationOnScreen(rootPos)

            val clockLeft = clockPos[0] - rootPos[0]
            val clockTop = clockPos[1] - rootPos[1]
            val clockRight = clockLeft + clock.width
            val clockCenterY = clockTop + clock.height / 2

            // measuredWidth reflects the text that was just rendered, which is
            // more accurate than the last laid-out width.
            val width = if (powerView.measuredWidth > 0) powerView.measuredWidth else powerView.width
            val height = if (powerView.measuredHeight > 0) powerView.measuredHeight else powerView.height

            var x = if (config.statusBar.anchor == ANCHOR_CLOCK_RIGHT) {
                // Spec 30: PowerView.left = Clock.right + gap
                (clockRight + gap + config.statusBar.offsetX).toFloat()
            } else {
                // Spec 29: PowerView.right = Clock.left - gap
                (clockLeft - gap - width + config.statusBar.offsetX).toFloat()
            }

            // Spec 31: vertical centering on the clock, plus the user offset.
            var y = (clockCenterY - height / 2 + config.statusBar.offsetY).toFloat()

            // --- safe area (spec 145) ---------------------------------------
            val (insetLeft, insetRight) = safeInsets(root)
            val rootWidth = root.width
            val rootHeight = root.height
            val minX = insetLeft.toFloat()
            val maxX = (rootWidth - insetRight - width).toFloat()
            val minY = 0f
            val maxY = (rootHeight - height).toFloat()

            val clampedX = if (maxX >= minX) x.coerceIn(minX, maxX) else x.coerceAtLeast(minX)
            val clampedY = if (maxY >= minY) y.coerceIn(minY, maxY) else y.coerceAtLeast(minY)

            // --- overlap handling (spec 32, 144.2) --------------------------
            val clockRect = android.graphics.Rect(
                clockLeft,
                clockTop,
                clockRight,
                clockTop + clock.height
            )
            val viewRect = android.graphics.Rect(
                clampedX.roundToInt(),
                clampedY.roundToInt(),
                clampedX.roundToInt() + width,
                clampedY.roundToInt() + height
            )
            val overlaps = android.graphics.Rect.intersects(clockRect, viewRect)
            var finalX = clampedX
            if (overlaps && config.statusBar.offsetX == 0 && config.statusBar.offsetY == 0) {
                // The user did not ask for a custom offset: nudge it out of the clock.
                finalX = if (config.statusBar.anchor == ANCHOR_CLOCK_RIGHT) {
                    (clockRight + gap).toFloat().coerceIn(minX, maxX)
                } else {
                    (clockLeft - gap - width).toFloat().coerceIn(minX, maxX)
                }
            }

            powerView.x = finalX
            powerView.y = clampedY
            Result(finalX, clampedY, overlaps)
        } catch (t: Throwable) {
            BLog.throttledError("SystemUI", "locator", "locate failed: ${t.message}")
            Result(powerView.x, powerView.y, false)
        }
    }

    private fun safeInsets(root: View): Pair<Int, Int> {
        // Prefer the real cutout/window insets when available (API 30+).
        if (Build.VERSION.SDK_INT >= 30) {
            try {
                val insets = root.rootWindowInsets
                if (insets != null) {
                    val bars = insets.getInsetsIgnoringVisibility(
                        WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout()
                    )
                    return bars.left to bars.right
                }
            } catch (t: Throwable) {
                // fall through to padding
            }
        }
        return root.paddingLeft to root.paddingRight
    }
}
