package de.robv.android.xposed;

import android.util.Log;

import java.lang.reflect.Member;

/**
 * Compile-time stub of XposedBridge. At runtime LSPosed provides the real one.
 */
public final class XposedBridge {

    private XposedBridge() {
    }

    public static void log(String text) {
        Log.i("Xposed", String.valueOf(text));
    }

    public static void log(Throwable t) {
        Log.e("Xposed", t != null ? t.toString() : "null", t);
    }

    public static XC_MethodHook.Unhook hookMethod(Member hookMethod, XC_MethodHook callback) {
        throw new UnsupportedOperationException("XposedBridge stub: hookMethod() is only available at runtime");
    }

    public static void unhookMethod(Member hookMethod, XC_MethodHook.Unhook unhook) {
        // no-op stub
    }
}
