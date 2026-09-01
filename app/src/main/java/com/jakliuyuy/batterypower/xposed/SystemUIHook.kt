package com.jakliuyuy.batterypower.xposed

import android.content.Context
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewParent
import android.widget.FrameLayout
import android.widget.TextView
import com.jakliuyuy.batterypower.battery.BatteryProbe
import com.jakliuyuy.batterypower.data.renderBatteryText
import com.jakliuyuy.batterypower.model.Config
import com.jakliuyuy.batterypower.model.SbPosition
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * SystemUI 状态栏功率注入。
 *
 * 关键点：
 * - 挂载点是**状态栏最外层 FrameLayout**，不是时钟的父容器（避坑 5）
 * - elevation/translationZ 设成 100，保证盖在所有通知图标之上（避坑 6）
 * - 坐标用 getLocationOnScreen 换算，因为 clock 与 root 不在同一坐标系（避坑 5）
 * - 每次 tick 重新取配置，改设置后 ≤1 秒生效
 */
internal object SystemUIHook {

    private const val TAG = "BatteryPower/SystemUI"

    /** 配置轮询间隔。与显示刷新频率解耦，保证改设置后 500ms 内生效 */
    private const val CONFIG_POLL_MS = 500L

    /** 时钟类名候选：ColorOS 优先，AOSP 兜底 */
    private val CLOCK_CLASSES = arrayOf(
        "com.oplus.systemui.statusbar.policy.Clock",
        "com.android.systemui.statusbar.policy.Clock",
        "com.android.systemui.statusbar.policy.ClockControllerImpl",
        "com.android.systemui.statusbar.phone.ClockController"
    )

    private val handler = Handler(Looper.getMainLooper())
    private var injectedView: TextView? = null
    private var rootRef: FrameLayout? = null
    private var clockRef: View? = null

    /** 每 tick 的刷新任务 */
    private var ticker: Runnable? = null

    fun init(lpparam: XC_LoadPackage.LoadPackageParam) {
        val cl = lpparam.classLoader
        // 逐个尝试候选类名，取第一个存在的（ColorOS 优先，AOSP 兜底）
        var clockClass: Class<*>? = null
        for (name in CLOCK_CLASSES) {
            clockClass = try {
                XposedHelpers.findClass(name, cl)
            } catch (_: Throwable) {
                null
            }
            if (clockClass != null) break
        }

        if (clockClass == null) {
            XposedBridge.log("$TAG 未找到时钟类，模块不生效")
            return
        }
        XposedBridge.log("$TAG 找到时钟类: ${clockClass.name}")

        XposedHelpers.findAndHookMethod(
            clockClass,
            "onAttachedToWindow",
            object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    val clock = param.thisObject as? View ?: return
                    try {
                        onClockAttached(clock)
                    } catch (t: Throwable) {
                        XposedBridge.log("$TAG 注入异常: ${t.message}")
                        XposedBridge.log(t)
                    }
                }
            }
        )
    }

    private fun onClockAttached(clock: View) {
        clockRef = clock

        val root = findStatusBarRoot(clock)
        if (root == null) {
            XposedBridge.log("$TAG 未找到状态栏根容器")
            return
        }
        rootRef = root

        val tv = ensureView(root.context, root)
        injectedView = tv

        // 布局：gravity=START + marginStart=0，之后用 translationX 做像素级定位
        val lp = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = Gravity.START or Gravity.CENTER_VERTICAL
            marginStart = 0
        }

        if (tv.parent !== root) {
            (tv.parent as? android.view.ViewGroup)?.removeView(tv)
            root.addView(tv, lp)
            XposedBridge.log("$TAG 已注入状态栏")
        }

        startTicker()
    }

    private fun ensureView(context: Context, root: FrameLayout): TextView {
        injectedView?.let { if (it.parent === root) return it }
        val tv = TextView(context).apply {
            // 无背景、紧凑，风格对齐 Scene 迷你监视器
            background = null
            setSingleLine(true)
            includeFontPadding = false
            setPadding(2, 0, 2, 0)
            // 描边，保证在深浅色状态栏上都看得清
            setShadowLayer(2.5f, 0f, 0f, Color.BLACK)
            // 避坑 6：不设 elevation 会被通知图标盖住
            elevation = 100f
            translationZ = 100f
        }
        return tv
    }

    /**
     * 从时钟往上找最外层 FrameLayout。
     * 避坑 5：插到时钟的父容器里会被通知图标挤掉，必须挂到根容器。
     */
    private fun findStatusBarRoot(view: View): FrameLayout? {
        var p: ViewParent? = view.parent
        var last: FrameLayout? = null
        var depth = 0
        while (p is View && depth < 40) {
            if (p is FrameLayout) last = p
            p = p.parent
            depth++
        }
        return last
    }

    /**
     * 轮询任务固定 500ms 一次，与显示刷新频率解耦。
     * 这样即使显示间隔设成 5s，改配置也能在 500ms 内生效（需求：≤1 秒）。
     */
    private fun startTicker() {
        ticker?.let { handler.removeCallbacks(it) }
        val task = object : Runnable {
            override fun run() {
                try {
                    tick()
                } catch (t: Throwable) {
                    XposedBridge.log("$TAG tick 异常: ${t.message}")
                }
                handler.postDelayed(this, CONFIG_POLL_MS)
            }
        }
        ticker = task
        handler.post(task)
    }

    private var lastRenderAt = 0L
    private var lastConfigSnapshot: Config? = null

    private fun tick() {
        val tv = injectedView ?: return
        val root = rootRef ?: return
        val clock = clockRef ?: return
        val context = tv.context
        val now = android.os.SystemClock.elapsedRealtime()

        // 每轮都拉一次配置，保证改动 500ms 内可见
        val cfg = ConfigBridge.obtain(context)
        val configChanged = cfg != lastConfigSnapshot
        val interval = cfg.sbIntervalMs.coerceIn(400L, 10_000L)

        if (!cfg.sbEnabled) {
            if (tv.visibility != View.GONE) tv.visibility = View.GONE
            lastConfigSnapshot = cfg
            return
        }
        if (tv.visibility != View.VISIBLE) tv.visibility = View.VISIBLE

        // 仅当配置变了 或 到了显示刷新周期 才真正重绘
        if (!configChanged && now - lastRenderAt < interval) return
        lastRenderAt = now
        lastConfigSnapshot = cfg

        // 取数据：SystemUI 是 uid 1000，多数机型可直接读 sysfs
        val snapshot = BatteryProbe.readDirect()
            ?: BatteryProbe.readViaBatteryManager(context)

        tv.setTextColor(cfg.sbColor)
        tv.textSize = cfg.sbTextSizeSp
        tv.text = renderBatteryText(snapshot, cfg.sbFields, cfg.sbShowUnit)

        updatePosition(tv, root, clock, cfg)
    }

    /**
     * 像素级定位。
     * clock 与 root 坐标系不同，统一换算到屏幕坐标再相减。
     */
    private fun updatePosition(tv: TextView, root: FrameLayout, clock: View, cfg: Config) {
        val clockLoc = IntArray(2)
        val rootLoc = IntArray(2)
        try {
            clock.getLocationOnScreen(clockLoc)
            root.getLocationOnScreen(rootLoc)
        } catch (t: Throwable) {
            return
        }

        val clockLeft = clockLoc[0] - rootLoc[0]
        val clockRight = clockLeft + clock.width

        // 用 Paint 量文字宽度，无需等待布局
        val textWidth = try {
            tv.paint.measureText(tv.text.toString())
        } catch (_: Throwable) {
            0f
        }
        val extra = tv.paddingStart + tv.paddingEnd

        // 三个分支都显式 .toFloat()：when 作为表达式时会推断成最宽的公共类型
        // （Number & Comparable），直接赋值给 Float 字段会类型不匹配
        val offsetX = cfg.sbOffsetX.toFloat()
        val left: Float = when (SbPosition.fromKey(cfg.sbPosition)) {
            SbPosition.CLOCK_LEFT ->
                // 文字右端贴住时钟左端
                clockLeft + offsetX - (textWidth + extra)
            SbPosition.CLOCK_RIGHT ->
                // 文字左端贴住时钟右端
                (clockRight + offsetX).toFloat()
        }

        tv.translationX = left
        tv.translationY = cfg.sbOffsetY.toFloat()
    }
}
