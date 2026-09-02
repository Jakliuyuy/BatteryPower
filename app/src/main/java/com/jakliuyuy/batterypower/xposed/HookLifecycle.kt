package com.jakliuyuy.batterypower.xposed

import android.view.View
import com.jakliuyuy.batterypower.core.log.BLog
import java.util.Collections
import java.util.WeakHashMap

/**
 * Tracks Clock instances across attach / detach / recreate / rotation
 * (spec sections 26, 85, 86, 88, 89, 135.2).
 *
 * Guarantees:
 *  - a Clock is never registered twice
 *  - one PowerView per status bar root, one update loop per module instance
 *  - detach releases every reference so SystemUI cannot leak
 */
object HookLifecycle {

    private val registeredClocks: MutableSet<View> =
        Collections.newSetFromMap(WeakHashMap<View, Boolean>())

    private val attachListener = object : View.OnAttachStateChangeListener {
        override fun onViewAttachedToWindow(v: View) {
            try {
                SystemUIHook.onClockAttached(v)
            } catch (t: Throwable) {
                BLog.throttledError("SystemUI", "attach", "clock attach failed", t)
            }
        }

        override fun onViewDetachedFromWindow(v: View) {
            try {
                SystemUIHook.onClockDetached(v)
            } catch (t: Throwable) {
                BLog.throttledError("SystemUI", "detach", "clock detach failed", t)
            }
        }
    }

    fun onClockCreated(clock: View?) {
        if (clock == null) return
        try {
            if (!registeredClocks.add(clock)) return
            SystemUIHook.onClockDiscovered(clock)
            clock.addOnAttachStateChangeListener(attachListener)
            BLog.d("SystemUI", "clock instance registered: ${clock.javaClass.name}")
            // The clock may already be attached when the constructor hook fires.
            if (clock.isAttachedToWindow) {
                SystemUIHook.onClockAttached(clock)
            }
        } catch (t: Throwable) {
            BLog.throttledError("SystemUI", "clock-created", "failed to register clock", t)
        }
    }

    fun releaseClock(clock: View) {
        try {
            registeredClocks.remove(clock)
            SystemUIHook.onClockForgotten(clock)
            clock.removeOnAttachStateChangeListener(attachListener)
        } catch (t: Throwable) {
            BLog.w("SystemUI", "release clock failed: ${t.message}")
        }
    }
}
