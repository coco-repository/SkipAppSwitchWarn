package com.coco.skipappswitchwarn;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.ActivityOptions;
import android.content.Intent;
import android.content.IntentSender;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class MainHook implements IXposedHookLoadPackage {

    private static final String TAG = "HookLog";
    private static final String FILTER_APP = "system";
    private static final String SOURCE_APP = "net.ankio.auto.xposed";
    private static final String TARGET_APP = "com.mutangtech.qianji";
    private static final String EXTRA_REQUEST_CODE = "extra_request_code";
    public static final String EXTRA_SOURCE_PACAKGE_NAME = "source_package_name";
    public static final String EXTRA_TARGET_PACAKGE_NAME = "target_package_name";
    public static final String KEY_ACTIVITY_OPTIONS = "activityOptions";


    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        if (FILTER_APP.equals(lpparam.packageName)) {
            Log.i(TAG, lpparam.packageName + "---filter app");
            hook(lpparam);
        }
    }

    private void hook(XC_LoadPackage.LoadPackageParam lpparam) {
        Log.i(TAG, lpparam.packageName + "---into hook method");
        XposedHelpers.findAndHookMethod(
            "com.android.internal.app.AppSwitchWarningActivity",
            lpparam.classLoader,
            "onCreate",
            Bundle.class,
            new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    Log.i(TAG, "hook success：beforeHookedMethod \"onCreate\"");
                    Activity activity = (Activity) param.thisObject;
                    activity.overridePendingTransition(0, 0);
                    Intent intent = activity.getIntent();
                    String sourceApp = intent.getStringExtra(EXTRA_SOURCE_PACAKGE_NAME);
                    String targetApp = intent.getStringExtra(EXTRA_TARGET_PACAKGE_NAME);
                    if (TARGET_APP.equals(targetApp)) {
                        Log.i(TAG, "自动跳转授权： " + sourceApp + " -> " + targetApp);
                        IntentSender targetSender = intent.getParcelableExtra(Intent.EXTRA_INTENT);
                        int requestCode = intent.getIntExtra(EXTRA_REQUEST_CODE, -1);
                        try {
                            activity.startIntentSenderForResult(
                                    targetSender,
                                    requestCode,
                                    null,
                                    0, 0, 0,
                                    buildBundle(lpparam, intent)
                            );
                        } catch (IntentSender.SendIntentException e) {
                            Log.e(TAG, "自动跳转失败", e);
                        }
                        reportProcessStartEvent(lpparam, intent, sourceApp, targetApp);
                        activity.finish();
                        activity.overridePendingTransition(0, 0);
                        param.setResult(null);
                    }
                }
            }
        );
    }

    /**
     * 原上报逻辑
     */
    private void reportProcessStartEvent(XC_LoadPackage.LoadPackageParam lpparam, Intent intent, String sourceApp, String targetApp) {
        try {
            Class<?> injectCls = XposedHelpers.findClass("flyme.hook.FrameworkInject", lpparam.classLoader);
            Object inject = XposedHelpers.callStaticMethod(injectCls, "get");
            XposedHelpers.callMethod(inject, "reportProcessStartEvent",
                    0, sourceApp, intent.getIntExtra("source_package_uid", 0),
                    targetApp, intent.getIntExtra("target_package_uid", 0),
                    true, "show_dialog user granted", false, false);
        } catch (Throwable e) {
            Log.w(TAG, "上报失败", e);
        }
    }

    private Bundle buildBundle(XC_LoadPackage.LoadPackageParam lpparam, Intent intent) {
        IBinder resultTo = null;
        Bundle resultToBundle = intent.getBundleExtra("resultToOverride");
        if (resultToBundle != null) {
            resultTo = resultToBundle.getBinder("resultToOverride");
        }

        Bundle optionsBundle = intent.getBundleExtra(KEY_ACTIVITY_OPTIONS);
        int isWindowMode = intent.getIntExtra("start_windowmode", 0);
        if (isWindowMode != 0) {
            optionsBundle = optionsBundle == null ? new Bundle() : optionsBundle;
            if (!optionsBundle.containsKey("start_windowmode")) {
                optionsBundle.putInt("start_windowmode", isWindowMode);
            }
        }

        ActivityOptions options = null;
        if (optionsBundle != null) {
            options = (ActivityOptions) XposedHelpers.callStaticMethod(
                    XposedHelpers.findClass("android.app.ActivityOptions", lpparam.classLoader),
                    "fromBundle",
                    optionsBundle
            );
        }

        Bundle resultToOption = new Bundle();
        if (resultTo != null) {
            resultToOption.putBinder("resultToOverride", resultTo);
        }
        if (options != null) {
            resultToOption.putBundle(KEY_ACTIVITY_OPTIONS, options.toBundle());
        }
        return resultToOption;
    }
}