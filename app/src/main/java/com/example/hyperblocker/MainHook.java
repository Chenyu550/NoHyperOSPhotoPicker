package com.example.hyperblocker;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.util.Log;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Iterator;
import java.util.List;

import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;
import io.github.libxposed.api.XposedModuleInterface.SystemServerStartingParam;

public class MainHook extends XposedModule {
    private static final String TAG = "HyperBlocker";

    private static final String PHOTO_PKG = "com.android.photopicker";
    private static final String HYPER_ACTIVITY =
            "com.android.photopicker.hyper.HyperMainActivity";

    private static final String[] NATIVE_ACTIVITIES = {
            ".MainActivity",
            ".PhotopickerGetContentActivity",
            ".PhotopickerUserSelectActivity"
    };

    private static final ThreadLocal<Boolean> sEnabling = new ThreadLocal<Boolean>();

    @Override
    public void onSystemServerStarting(SystemServerStartingParam param) {
        ClassLoader classLoader = param.getClassLoader();

        hookMayReferToFileExplore(classLoader);
        hookQueryIntentActivitiesInternal(classLoader);
    }

    private void hookMayReferToFileExplore(ClassLoader classLoader) {
        try {
            Class<?> clazz = Class.forName(
                    "com.android.server.wm.ActivityTaskManagerServiceImpl",
                    false,
                    classLoader
            );

            Method method = clazz.getDeclaredMethod(
                    "mayReferToFileExplore",
                    Intent.class,
                    String.class
            );
            method.setAccessible(true);

            hook(method)
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept(chain -> {
                        // 对应 HyperCeiler:
                        // param.setResult(param.getArgs()[0])
                        return chain.getArg(0);
                    });

            log(Log.INFO, TAG, "hooked mayReferToFileExplore");
        } catch (Throwable t) {
            log(Log.ERROR, TAG, "hook mayReferToFileExplore failed", t);
        }
    }

    private void hookQueryIntentActivitiesInternal(ClassLoader classLoader) {
        try {
            Class<?> clazz = Class.forName(
                    "com.android.server.pm.ComputerEngine",
                    false,
                    classLoader
            );

            Method method = clazz.getDeclaredMethod(
                    "queryIntentActivitiesInternal",
                    Intent.class,
                    String.class,
                    long.class,
                    long.class,
                    int.class,
                    int.class,
                    int.class,
                    boolean.class,
                    boolean.class
            );
            method.setAccessible(true);

            hook(method)
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept(chain -> {

                        Object arg0 = chain.getArg(0);
                        Intent intent = arg0 instanceof Intent ? (Intent) arg0 : null;

                        boolean shouldHandle = shouldHandleIntent(intent);


                        if (shouldHandle && isResolveForStart(chain)) {
                            Context context = getComputerEngineContext(chain.getThisObject());
                            enableNativePickerActivitiesIfNeeded(context);
                        }

                        Object result = chain.proceed();

                        if (shouldHandle && result instanceof List<?>) {
                            @SuppressWarnings("unchecked")
                            List<ResolveInfo> list = (List<ResolveInfo>) result;
                            removeHyperMainActivity(list);
                        }

                        return result;
                    });

            log(Log.INFO, TAG, "hooked queryIntentActivitiesInternal");
        } catch (Throwable t) {
            log(Log.ERROR, TAG, "hook queryIntentActivitiesInternal failed", t);
        }
    }

    private static boolean isResolveForStart(XposedInterface.Chain chain) {
        try {
            Object arg7 = chain.getArg(7);// 第8个参数代表是否resolveForStart == true
            return arg7 instanceof Boolean && (Boolean) arg7;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static boolean shouldHandleIntent(Intent intent) {
        if (intent == null) return false;

        String action = intent.getAction();
        String type = intent.getType();

        if ("android.provider.action.PICK_IMAGES".equals(action)) {
            return true;
        }

        if (Intent.ACTION_GET_CONTENT.equals(action)
                || Intent.ACTION_PICK.equals(action)
                || Intent.ACTION_OPEN_DOCUMENT.equals(action)) {
            return true;
        }

        return false;
    }

    private Context getComputerEngineContext(Object computerEngine) {
        if (computerEngine == null) return null;

        try {
            Field field = computerEngine.getClass().getDeclaredField("mContext");
            field.setAccessible(true);

            Object value = field.get(computerEngine);
            if (value instanceof Context) {
                return (Context) value;
            }
        } catch (Throwable t) {
            log(Log.ERROR, TAG, "get ComputerEngine.mContext failed", t);
        }

        return null;
    }

    private void enableNativePickerActivitiesIfNeeded(Context context) {
        if (context == null) return;
        if (Boolean.TRUE.equals(sEnabling.get())) return;

        try {
            sEnabling.set(Boolean.TRUE);

            PackageManager pm = context.getPackageManager();

            for (String cls : NATIVE_ACTIVITIES) {
                enableComponentIfNeeded(pm, PHOTO_PKG, cls);
            }
        } catch (Throwable t) {
            log(Log.ERROR, TAG, "enable native picker activities failed", t);
        } finally {
            sEnabling.remove();
        }
    }

    private void enableComponentIfNeeded(
            PackageManager pm,
            String pkg,
            String shortCls
    ) {
        String fullCls = shortCls.startsWith(".") ? pkg + shortCls : shortCls;
        ComponentName componentName = new ComponentName(pkg, fullCls);

        try {
            int currentState = pm.getComponentEnabledSetting(componentName);


            if (currentState == PackageManager.COMPONENT_ENABLED_STATE_ENABLED) {
                return;
            }

            pm.setComponentEnabledSetting(
                    componentName,
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                    PackageManager.DONT_KILL_APP
            );

            // log(Log.INFO, TAG, "enabled " + fullCls);
        } catch (Throwable t) {
            log(Log.ERROR, TAG, "enable component failed: " + fullCls, t);
        }
    }

    private void removeHyperMainActivity(List<ResolveInfo> list) {
        if (list == null || list.isEmpty()) return;

        try {
            Iterator<ResolveInfo> iterator = list.iterator();

            while (iterator.hasNext()) {
                ResolveInfo resolveInfo = iterator.next();
                if (resolveInfo == null) continue;

                ActivityInfo activityInfo = resolveInfo.activityInfo;
                if (activityInfo == null) continue;

                if (HYPER_ACTIVITY.equals(activityInfo.name)) {
                    iterator.remove();
                    // log(Log.INFO, TAG, "removed " + HYPER_ACTIVITY);
                }
            }
        } catch (Throwable t) {
            log(Log.ERROR, TAG, "remove HyperMainActivity failed", t);
        }
    }
}