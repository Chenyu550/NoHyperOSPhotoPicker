package com.example.hyperblocker;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.widget.Toast;

import java.io.File;
import java.lang.reflect.Method;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class MainHook implements IXposedHookLoadPackage {

    private static final String TAG = "HyperBlocker";

    private static final String PHOTO_PKG       = "com.android.photopicker";
    private static final String HYPER_ACTIVITY  = "com.android.photopicker.hyper.HyperMainActivity";

    private static final String FILE_PKG        = "com.android.fileexplorer";
    private static final String PICK_ACTIVITY   = "com.android.fileexplorer.picker.PickMainNavigatorActivity";


    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        if (PHOTO_PKG.equals(lpparam.packageName)) {
            hookPhotoPicker();
        } else if (FILE_PKG.equals(lpparam.packageName)) {
            hookFileExplorer();
        }
    }

    private void hookPhotoPicker() throws Throwable {
        // XposedBridge.log(TAG + ": hooked into " + PHOTO_PKG);
        Method onCreate = Activity.class.getDeclaredMethod("onCreate", Bundle.class);

        XposedBridge.hookMethod(onCreate, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                Activity activity = (Activity) param.thisObject;
                if (!HYPER_ACTIVITY.equals(activity.getClass().getName())) return;

                Log.w(TAG, "Blocking " + HYPER_ACTIVITY);

                setFlag(true, activity);

                activity.finish();
                Toast.makeText(activity,
                        "已拦截并禁用小米魔改选择器，再次调用即可使用AOSP原生选择器",
                        Toast.LENGTH_LONG).show();

                disableComponent(activity);
            }
        });
    }

    private void hookFileExplorer() throws Throwable {
        // XposedBridge.log(TAG + ": hooked into " + FILE_PKG);
        Method onCreate = Activity.class.getDeclaredMethod("onCreate", Bundle.class);

        XposedBridge.hookMethod(onCreate, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                Activity activity = (Activity) param.thisObject;
                if (!PICK_ACTIVITY.equals(activity.getClass().getName())) return;

                // 只有由照片和视频触发的调用才要拦截，避免误伤少数App直接调用小米文件管理的情况
                if (!getFlag(activity)) {
                    XposedBridge.log(TAG + ": 第三方直接调用安全访问文件，放行");
                    return;
                }

                setFlag(false, activity);

                activity.finish();
                Toast.makeText(activity,
                        "已拦截魔改“安全访问文件”",
                        Toast.LENGTH_SHORT).show();
            }
        });
    }

    private static void setFlag(boolean value, Context ctx) {
        try {
            Settings.Global.putInt(ctx.getContentResolver(), // Settings.Global 需要 WRITE_SECURE_SETTINGS 权限，而系统应用默认都有
                    "hyperblocker_flag", value ? 1 : 0);
            XposedBridge.log(TAG + ": setFlag(" + value + ") 成功");
        } catch (Exception e) {
            XposedBridge.log(TAG + ": setFlag(" + value + ") 失败 — " + e.getMessage());
        }
    }

    private static boolean getFlag(Context ctx) {
        try {
            int val = Settings.Global.getInt(ctx.getContentResolver(),
                    "hyperblocker_flag", 0);
            XposedBridge.log(TAG + ": getFlag() = " + val);
            return val == 1;
        } catch (Exception e) {
            XposedBridge.log(TAG + ": getFlag() 失败 — " + e.getMessage());
            return false;
        }
    }

    private static void disableComponent(Activity activity) {
        new Thread(() -> {
            // try { Thread.sleep(1500); } catch (InterruptedException ignored) {}

            Context ctx = activity.getApplicationContext();
            PackageManager pm = ctx.getPackageManager();

            setComponentState(pm, PHOTO_PKG, ".hyper.HyperMainActivity",
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED);

            String[] toEnable = {
                    ".MainActivity",
                    ".PhotopickerGetContentActivity",
                    ".PhotopickerUserSelectActivity"
            };
            for (String cls : toEnable) {
                setComponentState(pm, PHOTO_PKG, cls,
                        PackageManager.COMPONENT_ENABLED_STATE_ENABLED);
            }
        }, "HyperBlocker-disable").start();
    }

    private static void setComponentState(PackageManager pm, String pkg, String shortCls, int state) {
        String fullCls = shortCls.startsWith(".") ? pkg + shortCls : shortCls;
        ComponentName cn = new ComponentName(pkg, fullCls);
        try {
            pm.setComponentEnabledSetting(cn, state, PackageManager.DONT_KILL_APP);
            XposedBridge.log(TAG + ": 设置组件状态成功: " + fullCls + " -> " + state);
        } catch (Exception e) {
            XposedBridge.log(TAG + ": 设置组件状态失败: " + fullCls + " — " + e.getMessage());
        }
    }
}