package com.jakliuyuy.batterypower.xposed

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import com.jakliuyuy.batterypower.core.config.AppConfig
import com.jakliuyuy.batterypower.core.log.BLog
import com.jakliuyuy.batterypower.core.model.BatterySnapshot
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage
import java.lang.ref.WeakReference
import java.util.WeakHashMap
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

/**
 * Main controller running inside com.android.systemui
 * (spec sections 25-33, 84-88, 135.1-135.3, 150).
 *
 * Safety rules enforced here:
 *  - every entry point is wrapped in try/catch(Throwable)
 *  - no su, no sysfs, no heavy I/O on the SystemUI main thread
 *  - duplicate PowerView injection is impossible (unique tag + maps)
 *  - detach releases all references (no Activity/View leaks)
 */
object SystemUIHook {

    const val TARGET_PACKAGE = "com.android.systemui"
    const val ACTION_PING = "com.jakliuyuy.batterypower.PING"
    const val ACTION_ALIVE = "com.jakliuyuy.batterypower.HOOK_ALIVE"
    const val APP_PACKAGE = "com.jakliuyuy.batterypower"

    private val CLOCK_CANDIDATES = listOf(
        "com.oplus.systemui.statusbar.policy.Clock",
        "com.android.systemui.statusbar.policy.Clock"
    )

    @Volatile
    var clockClassName: String = ""

    @Volatile
    var hookInstalled: Boolean = false

    @Volatile
    var lastError: String? = null

    @Volatile
    var attachedViewCount: Int = 0

    private val mainHandler = Handler(Looper.getMainLooper())
    private val background = Executors.newSingleThreadScheduledExecutor { runnable ->
        Thread(runnable, "bpo-su-config").apply { isDaemon = true }
    }

    private var contextRef: WeakReference<Context>? = null
    private var sampler: SystemUiSampler? = null
    private var configBridge: ConfigBridge? = null

    private val clockToRoot = WeakHashMap<View, ViewGroup>()
    private val clockToView = WeakHashMap<View, SystemUIPowerView>()
    private val rootToView = WeakHashMap<ViewGroup, SystemUIPowerView>()

    private var configFuture: ScheduledFuture<*>? = null
    private var uiLoopScheduled = false
    private var pingReceiver: BroadcastReceiver? = null

    // ------------------------------------------------------------------ hooking

    fun init(lpparam: XC_LoadPackage.LoadPackageParam) {
        val classLoader = lpparam.classLoader ?: return
        for (className in CLOCK_CANDIDATES) {
            val clockClass = try {
                XposedHelpers.findClass(className, classLoader)
            } catch (t: Throwable) {
                continue
            }
            if (!View::class.java.isAssignableFrom(clockClass)) continue
            if (hookClockConstructors(clockClass)) {
                clockClassName = className
                hookInstalled = true
                try {
                    XposedBridge.log("BatteryPower: hooked clock $className")
                } catch (ignored: Throwable) {
                }
                return
            }
        }
        lastError = "no clock class found"
        BLog.w("SystemUI", "no Clock class could be hooked; module stays inactive")
    }

    private fun hookClockConstructors(clockClass: Class<*>): Boolean {
        var hooked = false
        val constructors = try {
            clockClass.declaredConstructors
        } catch (t: Throwable) {
            return false
        }
        for (constructor in constructors) {
            try {
                val args = ArrayList<Any>()
                args.addAll(constructor.parameterTypes.toList())
                args.add(object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        try {
                            HookLifecycle.onClockCreated(param.thisObject as? View)
                        } catch (t: Throwable) {
                            BLog.throttledError("SystemUI", "ctor-hook", "clock hook failed", t)
                        }
                    }
                })
                XposedHelpers.findAndHookConstructor(clockClass, *args.toTypedArray())
                hooked = true
            } catch (t: Throwable) {
                BLog.w("SystemUI", "constructor hook failed: ${t.message}")
            }
        }
        return hooked
    }

    // -------------------------------------------------------------- lifecycle

    fun onClockAttached(clock: View) {
        try {
            val context = clock.context?.applicationContext ?: return
            ensureRuntime(context)
            registerPingReceiver(context)

            val config = configBridge?.config ?: return
            if (!config.statusBar.enabled || !config.flags.enableStatusBarHook) return

            val root = findStatusBarRoot(clock)
            if (root == null) {
                lastError = "status bar root not found"
                return
            }
            val powerView = ensurePowerView(root) ?: return

            clockToRoot[clock] = root
            clockToView[clock] = powerView
            attachedViewCount = clockToView.size

            startLoops()
            powerView.applyConfig(config)
            powerView.render(sampler?.current() ?: BatterySnapshot.empty())
            relocate(clock)
            observeLayoutChanges(root)
            lastError = null
        } catch (t: Throwable) {
            BLog.throttledError("SystemUI", "clock-attached", "attach failed", t)
        }
    }

    fun onClockDetached(clock: View) {
        try {
            val powerView = clockToView.remove(clock)
            val root = clockToRoot.remove(clock)
            if (powerView != null) {
                try {
                    (powerView.parent as? ViewGroup)?.removeView(powerView)
                } catch (t: Throwable) {
                    BLog.w("SystemUI", "remove view failed: ${t.message}")
                }
            }
            if (root != null) {
                rootToView.remove(root)
            }
            attachedViewCount = clockToView.size
            if (clockToView.isEmpty()) {
                stopLoops()
            }
        } catch (t: Throwable) {
            BLog.throttledError("SystemUI", "clock-detached", "detach failed", t)
        }
    }

    private fun ensureRuntime(context: Context) {
        contextRef = WeakReference(context)
        if (configBridge == null) configBridge = ConfigBridge(context)
        if (sampler == null) {
            sampler = SystemUiSampler(context)
            sampler?.applyConfig(configBridge?.config ?: AppConfig.defaults())
        }
        val config = configBridge?.config
        if (config != null) {
            BLog.setDebugEnabled(config.flags.enableDebugLog)
        }
    }

    private fun startLoops() {
        try {
            sampler?.start()
        } catch (t: Throwable) {
            BLog.w("SystemUI", "sampler start failed: ${t.message}")
        }
        if (configFuture == null) {
            configFuture = background.scheduleWithFixedDelay(
                {
                    try {
                        val changed = configBridge?.poll() ?: false
                        if (changed) {
                            mainHandler.post { onConfigSynced() }
                        }
                    } catch (t: Throwable) {
                        BLog.throttledError("SystemUI", "config-poll", "poll failed", t)
                    }
                },
                0L,
                1_000L,
                TimeUnit.MILLISECONDS
            )
        }
        if (!uiLoopScheduled) {
            uiLoopScheduled = true
            mainHandler.post(uiRunnable)
        }
    }

    private fun stopLoops() {
        try {
            sampler?.stop()
        } catch (t: Throwable) {
            BLog.w("SystemUI", "sampler stop failed: ${t.message}")
        }
        try {
            configFuture?.cancel(false)
            configFuture = null
        } catch (t: Throwable) {
            // ignore
        }
        uiLoopScheduled = false
        mainHandler.removeCallbacks(uiRunnable)
    }

    private val uiRunnable = object : Runnable {
        override fun run() {
            try {
                val config = configBridge?.config
                if (config == null || !config.statusBar.enabled || !config.flags.enableStatusBarHook) {
                    removeAllViews()
                } else {
                    val snapshot = sampler?.current() ?: BatterySnapshot.empty()
                    val entries = clockToView.entries.toList()
                    for ((clock, powerView) in entries) {
                        if (!clock.isAttachedToWindow) continue
                        try {
                            powerView.render(snapshot)
                            powerView.measure(
                                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
                                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
                            )
                            PowerViewLocator.locate(powerView, clock, clockToRoot[clock] ?: continue, config)
                        } catch (t: Throwable) {
                            BLog.throttledError("SystemUI", "ui-loop", "render failed", t)
                        }
                    }
                }
            } catch (t: Throwable) {
                BLog.throttledError("SystemUI", "ui-loop", "loop failed", t)
            } finally {
                val delay = (configBridge?.config?.statusBar?.refreshMs ?: 1_000L)
                    .coerceIn(500L, 5_000L)
                if (uiLoopScheduled) {
                    mainHandler.postDelayed(this, delay)
                }
            }
        }
    }

    private fun onConfigSynced() {
        try {
            val config = configBridge?.config ?: return
            BLog.setDebugEnabled(config.flags.enableDebugLog)
            sampler?.applyConfig(config)
            if (!config.statusBar.enabled || !config.flags.enableStatusBarHook) {
                removeAllViews()
                return
            }
            val snapshot = sampler?.current() ?: BatterySnapshot.empty()
            val entries = clockToView.entries.toList()
            for ((clock, powerView) in entries) {
                try {
                    powerView.applyConfig(config)
                    powerView.render(snapshot)
                    powerView.measure(
                        View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
                        View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
                    )
                    PowerViewLocator.locate(powerView, clock, clockToRoot[clock] ?: continue, config)
                } catch (t: Throwable) {
                    BLog.throttledError("SystemUI", "config-sync", "apply failed", t)
                }
            }
        } catch (t: Throwable) {
            BLog.throttledError("SystemUI", "config-sync", "sync failed", t)
        }
    }

    private fun removeAllViews() {
        try {
            val roots = rootToView.keys.toList()
            for (root in roots) {
                val view = rootToView.remove(root) ?: continue
                try {
                    root.removeView(view)
                } catch (t: Throwable) {
                    BLog.w("SystemUI", "remove failed: ${t.message}")
                }
            }
            clockToView.clear()
            clockToRoot.clear()
            attachedViewCount = 0
            stopLoops()
        } catch (t: Throwable) {
            BLog.w("SystemUI", "removeAll failed: ${t.message}")
        }
    }

    private fun relocate(clock: View) {
        try {
            val powerView = clockToView[clock] ?: return
            val root = clockToRoot[clock] ?: return
            val config = configBridge?.config ?: return
            powerView.measure(
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
            )
            PowerViewLocator.locate(powerView, clock, root, config)
        } catch (t: Throwable) {
            BLog.throttledError("SystemUI", "relocate", "relocate failed", t)
        }
    }

    // -------------------------------------------------------------- view setup

    /**
     * Spec 27: prefer the outermost FrameLayout instead of the Clock's direct
     * parent, because notification icons keep re-parenting the clock.
     */
    private fun findStatusBarRoot(view: View): ViewGroup? {
        var current = view.parent
        var lastFrame: ViewGroup? = null
        var lastGroup: ViewGroup? = null
        while (current is ViewGroup) {
            val group = current
            lastGroup = group
            if (group is FrameLayout) lastFrame = group
            current = group.parent
        }
        return lastFrame ?: lastGroup
    }

    private fun ensurePowerView(root: ViewGroup): SystemUIPowerView? {
        try {
            // Spec 85: never inject twice.
            val existing = root.findViewWithTag<View>(SystemUIPowerView.TAG_POWER_VIEW)
            if (existing is SystemUIPowerView) {
                rootToView[root] = existing
                return existing
            }
            val powerView = SystemUIPowerView(root.context)
            val params = try {
                root.generateDefaultLayoutParams()
            } catch (t: Throwable) {
                null
            } ?: ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            root.addView(powerView, params)
            // Spec 33: highest elevation + bringToFront, no extra window.
            powerView.bringToFront()
            rootToView[root] = powerView
            BLog.i("SystemUI", "PowerView injected into ${root.javaClass.simpleName}")
            return powerView
        } catch (t: Throwable) {
            BLog.throttledError("SystemUI", "ensure-view", "PowerView creation failed", t)
            return null
        }
    }

    private fun observeLayoutChanges(root: ViewGroup) {
        try {
            root.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
                try {
                    val entries = clockToView.entries.toList()
                    for ((clock, _) in entries) {
                        if (clockToRoot[clock] === root) relocate(clock)
                    }
                } catch (t: Throwable) {
                    BLog.throttledError("SystemUI", "layout-change", "relocate failed", t)
                }
            }
        } catch (t: Throwable) {
            BLog.w("SystemUI", "layout listener failed: ${t.message}")
        }
    }

    // -------------------------------------------------------------- hook ping

    private fun registerPingReceiver(context: Context) {
        if (pingReceiver != null) return
        try {
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(ctx: Context, intent: Intent) {
                    try {
                        ctx.sendBroadcast(
                            Intent(ACTION_ALIVE).setPackage(APP_PACKAGE)
                        )
                    } catch (t: Throwable) {
                        // ignore
                    }
                }
            }
            val filter = IntentFilter(ACTION_PING)
            if (Build.VERSION.SDK_INT >= 33) {
                context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                context.registerReceiver(receiver, filter)
            }
            pingReceiver = receiver
        } catch (t: Throwable) {
            BLog.w("SystemUI", "ping receiver failed: ${t.message}")
        }
    }
}
