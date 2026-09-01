# 当前 release 未开启 minify；若后续开启，需保留 Xposed 入口类（由 xposed_init 反射加载）
-keep class com.jakliuyuy.batterypower.xposed.HookEntry { *; }
-keep class de.robv.android.xposed.** { *; }
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
