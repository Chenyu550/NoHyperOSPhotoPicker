# Keep the hook entry point — LSPosed loads it by class name from assets/xposed_init
-keep class com.example.hyperblocker.MainHook { *; }

# Keep XposedBridge callback interfaces
-keep interface de.robv.android.xposed.** { *; }
-keep class de.robv.android.xposed.** { *; }
