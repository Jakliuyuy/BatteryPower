package com.jakliuyuy.batterypower.xposed

import android.os.Build
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * Xposed / LSPosed 模块入口。
 *
 * 由 assets/xposed_init 声明，框架在目标进程启动时回调。
 * 本模块只关心 SystemUI。
 */
class HookEntry : IXposedHookLoadPackage {

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        if (lpparam.packageName != SYSTEM_UI) return
        try {
            SystemUIHook.init(lpparam)
            log("模块已载入 SystemUI (sdk=${Build.VERSION.SDK_INT})")
        } catch (t: Throwable) {
            log("初始化失败: ${t.message}")
            XposedBridge.log(t)
        }
    }

    private fun log(msg: String) {
        XposedBridge.log("BatteryPower: $msg")
    }

    companion object {
        const val SYSTEM_UI = "com.android.systemui"
    }
}
