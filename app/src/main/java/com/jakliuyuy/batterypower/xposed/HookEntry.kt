package com.jakliuyuy.batterypower.xposed

import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * Xposed module entry point (spec sections 25, 120).
 *
 * Only com.android.systemui is ever touched. Every failure is logged and
 * swallowed: the module must never prevent SystemUI from starting.
 */
class HookEntry : IXposedHookLoadPackage {

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        try {
            if (lpparam.packageName != SystemUIHook.TARGET_PACKAGE) return
        } catch (t: Throwable) {
            return
        }
        try {
            SystemUIHook.init(lpparam)
        } catch (t: Throwable) {
            try {
                XposedBridge.log("BatteryPower: hook init failed")
                XposedBridge.log(t)
            } catch (ignored: Throwable) {
            }
        }
    }
}
