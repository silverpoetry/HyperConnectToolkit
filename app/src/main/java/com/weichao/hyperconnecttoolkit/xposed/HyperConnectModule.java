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
            case MILINK_NOTIFICATION_DISCOVERY -> installNotificationDiscoveryFeature(
                    param.getDefaultClassLoader(),
                    features);
            case MILINK_NOTIFICATION_FLOW -> installNotificationFlowFeature(
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
            mirrorRuntime.recordInstalledCapability(
                    HookStatusContract.FEATURE_PORTRAIT_STREAMING);
        }
    }

    private void installNotificationDiscoveryFeature(
            ClassLoader classLoader,
            HookFeatureFlags features) {
        if (!features.crossDeviceNotification) return;
        if (!isTablet(classLoader)) {
            log(Log.INFO, TAG, "Skipped tablet notification compatibility on a phone");
            return;
        }

        boolean installed = new MiLinkDiscoveryHooks(this).install(classLoader);
        reportInstalledCapability(
                installed,
                TargetProcess.MILINK_NOTIFICATION_DISCOVERY,
                HookStatusContract.COMPONENT_NOTIFICATION_DISCOVERY);
    }

    private void installNotificationFlowFeature(
            ClassLoader classLoader,
            HookFeatureFlags features) {
        if (!features.crossDeviceNotification) return;
        if (!isTablet(classLoader)) {
            log(Log.INFO, TAG, "Skipped tablet notification compatibility on a phone");
            return;
        }

        boolean installed = new MiLinkNotificationFlowHooks(this).install(classLoader);
        reportInstalledCapability(
                installed,
                TargetProcess.MILINK_NOTIFICATION_FLOW,
                HookStatusContract.COMPONENT_NOTIFICATION_FLOW);
    }

    private void installMultiChannelFeature(
            ClassLoader classLoader,
            HookFeatureFlags features) {
        if (!features.miLinkMultiChannel) return;
        boolean installed = new MiLinkMultiChannelHooks(this).install(classLoader);
        reportInstalledCapability(
                installed,
                TargetProcess.MILINK_CORE,
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

    private void reportInstalledCapability(
            boolean installed,
            TargetProcess target,
            int capability) {
        if (!installed) return;
        TargetProcessRuntime runtime = new TargetProcessRuntime(
                this,
                target.packageName,
                target.processName);
        if (runtime.installLifecycleHook()) {
            runtime.recordInstalledCapability(capability);
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
