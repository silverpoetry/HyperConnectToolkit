package com.weichao.hyperconnecttoolkit.xposed;

import android.app.Application;
import android.content.SharedPreferences;
import android.util.Log;

import com.weichao.hyperconnecttoolkit.config.FeaturePreferences;
import com.weichao.hyperconnecttoolkit.platform.HookStatusContract;

import io.github.libxposed.api.XposedModule;
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam;

/** Lightweight LSPosed entry point: exact process routing, configuration gating and lifecycle. */
public final class HyperConnectModule extends XposedModule {
    private static final String TAG = "HyperConnectToolkit";

    private PortraitStreamingHooks portraitHooks;
    private TargetProcessRuntime mirrorRuntime;
    private boolean mirrorReporterInstalled;
    private boolean mirrorReadyHandled;

    @Override
    public void onPackageLoaded(PackageLoadedParam param) {
        if (!param.isFirstPackage()) return;

        TargetProcess target = TargetProcess.resolve(
                param.getPackageName(),
                Application.getProcessName());
        if (target == null) return;

        HookFeatureFlags features = readFeatureFlags();
        log(Log.INFO, TAG,
                "Feature snapshot for " + target.processName
                        + ": notification=" + features.crossDeviceNotification
                        + ", portrait=" + features.portraitStreaming
                        + ", multiChannel=" + features.miLinkMultiChannel);

        switch (target) {
            case MILINK_NOTIFICATION -> installNotificationFeature(
                    param.getDefaultClassLoader(),
                    features);
            case MILINK_CORE -> installMultiChannelFeature(
                    param.getDefaultClassLoader(),
                    features);
            case XIAOMI_MIRROR -> installPortraitFeature(
                    param.getDefaultClassLoader(),
                    features);
        }
    }

    @Override
    public void onPackageReady(PackageReadyParam param) {
        if (mirrorReadyHandled || portraitHooks == null || !param.isFirstPackage()) return;
        if (!TargetProcess.XIAOMI_MIRROR.matches(
                param.getPackageName(),
                Application.getProcessName())) {
            return;
        }

        mirrorReadyHandled = true;
        if (portraitHooks.installOrientation(param.getClassLoader())
                && portraitHooks.isComplete()
                && mirrorReporterInstalled) {
            mirrorRuntime.recordInstalledFeature(HookStatusContract.FEATURE_PORTRAIT_STREAMING);
        }
    }

    private void installNotificationFeature(
            ClassLoader classLoader,
            HookFeatureFlags features) {
        if (!features.crossDeviceNotification) return;
        if (!isTablet(classLoader)) {
            log(Log.INFO, TAG, "Skipped tablet notification compatibility on a phone");
            return;
        }

        boolean installed = new MiLinkNotificationHooks(this).install(classLoader);
        reportInstalledFeature(
                installed,
                TargetProcess.MILINK_NOTIFICATION.packageName,
                TargetProcess.MILINK_NOTIFICATION.processName,
                HookStatusContract.FEATURE_CROSS_DEVICE_NOTIFICATION);
    }

    private void installMultiChannelFeature(
            ClassLoader classLoader,
            HookFeatureFlags features) {
        if (!features.miLinkMultiChannel) return;
        boolean installed = new MiLinkMultiChannelHooks(this).install(classLoader);
        reportInstalledFeature(
                installed,
                TargetProcess.MILINK_CORE.packageName,
                TargetProcess.MILINK_CORE.processName,
                HookStatusContract.FEATURE_MILINK_MULTI_CHANNEL);
    }

    private void installPortraitFeature(
            ClassLoader classLoader,
            HookFeatureFlags features) {
        if (!features.portraitStreaming) return;
        if (!isTablet(classLoader)) {
            log(Log.INFO, TAG, "Skipped tablet portrait compatibility on a phone");
            return;
        }

        mirrorRuntime = new TargetProcessRuntime(
                this,
                TargetProcess.XIAOMI_MIRROR.packageName,
                TargetProcess.XIAOMI_MIRROR.processName);
        portraitHooks = new PortraitStreamingHooks(
                this,
                mirrorRuntime::applicationContext);
        if (portraitHooks.installGeometry(classLoader)) {
            mirrorReporterInstalled = mirrorRuntime.installLifecycleHook();
        }
    }

    private void reportInstalledFeature(
            boolean installed,
            String sourcePackage,
            String sourceProcess,
            int feature) {
        if (!installed) return;
        TargetProcessRuntime runtime = new TargetProcessRuntime(
                this,
                sourcePackage,
                sourceProcess);
        if (runtime.installLifecycleHook()) {
            runtime.recordInstalledFeature(feature);
        }
    }

    private HookFeatureFlags readFeatureFlags() {
        try {
            SharedPreferences preferences = getRemotePreferences(FeaturePreferences.GROUP);
            return HookFeatureFlags.read(preferences);
        } catch (Throwable error) {
            log(Log.ERROR, TAG,
                    "Remote configuration unavailable; skipped all functional hooks",
                    error);
            return HookFeatureFlags.disabled();
        }
    }

    private boolean isTablet(ClassLoader classLoader) {
        try {
            return DeviceFormFactor.isTablet(classLoader);
        } catch (Throwable error) {
            log(Log.WARN, TAG, "Could not read HyperOS tablet form factor", error);
            return false;
        }
    }
}
