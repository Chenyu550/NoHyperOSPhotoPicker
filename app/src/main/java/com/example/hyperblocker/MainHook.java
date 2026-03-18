package com.example.hyperblocker;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

import java.lang.reflect.Method;

/**
 * LSPosed hook module targeting com.android.photopicker.
 *
 * Fix: do NOT use setResult(null) on onCreate — Android framework enforces that
 * super.onCreate() must be called, and skipping the method body triggers
 * SuperNotCalledException → host app crash.
 *
 * Correct approach: use afterHookedMethod so super.onCreate() has already run,
 * then call finish() immediately. The Activity is created but closes before
 * onStart/onResume, so no UI is ever visible to the user.
 */
public class MainHook implements IXposedHookLoadPackage {

    private static final String TAG = "HyperBlocker";
    private static final String TARGET_PKG = "com.android.photopicker";
    private static final String TARGET_ACTIVITY = "com.android.photopicker.hyper.HyperMainActivity";

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) {
        if (!TARGET_PKG.equals(lpparam.packageName)) return;

        XposedBridge.log(TAG + ": hooked into " + TARGET_PKG);

        try {
            Method onCreate = Activity.class.getDeclaredMethod("onCreate", Bundle.class);

            XposedBridge.hookMethod(onCreate, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    Activity activity = (Activity) param.thisObject;

                    if (!TARGET_ACTIVITY.equals(activity.getClass().getName())) return;

                    Log.w(TAG, "Blocking " + TARGET_ACTIVITY);
                    XposedBridge.log(TAG + ": intercepted " + TARGET_ACTIVITY + " — finishing");

                    // super.onCreate() has already been called at this point,
                    // so the framework is satisfied. finish() here causes the
                    // Activity to go straight to onDestroy without ever reaching
                    // onStart or onResume — no UI is drawn.
                    activity.finish();

                    Toast.makeText(activity, "已拦截并禁用小米魔改选择器，再次调用即可使用AOSP原生选择器", Toast.LENGTH_LONG).show();

                    // Also persistently disable the component via pm.
                    disableComponent();
                }
            });

        } catch (NoSuchMethodException e) {
            XposedBridge.log(TAG + ": failed to hook onCreate — " + e.getMessage());
        }
    }

    private static void disableComponent() {
        new Thread(() -> {
            String cmd = "pm disable "
                    + TARGET_PKG + "/.hyper.HyperMainActivity";
            try {
                Process proc = Runtime.getRuntime().exec(new String[]{"sh", "-c", cmd});
                int exit = proc.waitFor();
                XposedBridge.log(TAG + ": 成功禁用魔改选择器，返回码为 " + exit);
            } catch (Exception e) {
                XposedBridge.log(TAG + ": 禁用魔改选择器时出现错误 — " + e.getMessage());
            }
        }, "HyperBlocker-disable").start();
    }
}