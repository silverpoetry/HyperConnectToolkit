package com.weichao.hyperconnecttoolkit.xposed;

import android.app.Application;
import android.content.SharedPreferences;
import android.util.Log;

import com.weichao.hyperconnecttoolkit.BuildConfig;
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
        DiagnosticLogger diagnostics = new DiagnosticLogger(
                this,
                features.detailedLogging,
                features.diagnosticSessionId,
                target.processName);
        log(Log.INFO, TAG,
                "Feature snapshot for " + target.processName
                        + ": notification=" + features.crossDeviceNotification
                        + ", portrait=" + features.portraitStreaming
                        + ", multiChannel=" + features.miLinkMultiChannel
                        + ", detailedLogging=" + features.detailedLogging);
        recordDiagnosticBootstrap(target, features, diagnostics);

        switch (target) {
            case MILINK_NOTIFICATION_DISCOVERY -> installNotificationDiscoveryFeature(
                    param.getDefaultClassLoader(),
                    features,
                    diagnostics);
            case MILINK_NOTIFICATION_FLOW -> installNotificationFlowFeature(
                    param.getDefaultClassLoader(),
                    features,
                    diagnostics);
            case MILINK_CORE -> installMultiChannelFeature(
                    param.getDefaultClassLoader(),
                    features,
                    diagnostics);
            case XIAOMI_MIRROR -> installPortraitFeature(
                    param.getDefaultClassLoader(),
                    features,
                    diagnostics);
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
            HookFeatureFlags features,
            DiagnosticLogger diagnostics) {
        boolean tablet = isTablet(classLoader, diagnostics);
        MiLinkDiscoveryHooks hooks = new MiLinkDiscoveryHooks(this, diagnostics);
        boolean installed = false;
        if (features.crossDeviceNotification && tablet) {
            installed = hooks.install(classLoader);
        } else if (features.crossDeviceNotification) {
            log(Log.INFO, TAG, "Skipped tablet notification compatibility on a phone");
        }

        if (features.detailedLogging) {
            new NotificationDiscoveryDiagnostics(this, diagnostics).install(
                    classLoader,
                    installed);
        }
        startRuntime(
                installed,
                diagnostics,
                TargetProcess.MILINK_NOTIFICATION_DISCOVERY,
                HookStatusContract.COMPONENT_NOTIFICATION_DISCOVERY);
    }

    private void installNotificationFlowFeature(
            ClassLoader classLoader,
            HookFeatureFlags features,
            DiagnosticLogger diagnostics) {
        boolean tablet = isTablet(classLoader, diagnostics);
        boolean installed = features.crossDeviceNotification
                && tablet
                && new MiLinkNotificationFlowHooks(this, diagnostics).install(classLoader);
        if (features.crossDeviceNotification && !tablet) {
            log(Log.INFO, TAG, "Skipped tablet notification compatibility on a phone");
        }
        startRuntime(
                installed,
                diagnostics,
                TargetProcess.MILINK_NOTIFICATION_FLOW,
                HookStatusContract.COMPONENT_NOTIFICATION_FLOW);
    }

    private void installMultiChannelFeature(
            ClassLoader classLoader,
            HookFeatureFlags features,
            DiagnosticLogger diagnostics) {
        boolean installed = features.miLinkMultiChannel
                && new MiLinkMultiChannelHooks(this).install(classLoader);
        startRuntime(
                installed,
                diagnostics,
                TargetProcess.MILINK_CORE,
                HookStatusContract.FEATURE_MILINK_MULTI_CHANNEL);
    }

    private void installPortraitFeature(
            ClassLoader classLoader,
            HookFeatureFlags features,
            DiagnosticLogger diagnostics) {
        if (!features.portraitStreaming) {
            startRuntime(false, diagnostics, TargetProcess.XIAOMI_MIRROR, 0);
            return;
        }
        if (!isTablet(classLoader, diagnostics)) {
            log(Log.INFO, TAG, "Skipped tablet portrait compatibility on a phone");
            startRuntime(false, diagnostics, TargetProcess.XIAOMI_MIRROR, 0);
            return;
        }

        mirrorRuntime = new TargetProcessRuntime(
                this,
                diagnostics,
                TargetProcess.XIAOMI_MIRROR.packageName,
                TargetProcess.XIAOMI_MIRROR.processName);
        portraitHooks = new PortraitStreamingHooks(
                this,
                mirrorRuntime::applicationContext);
        if (portraitHooks.installGeometry(classLoader)) {
            mirrorReporterInstalled = mirrorRuntime.installLifecycleHook();
        }
    }

    private void startRuntime(
            boolean installed,
            DiagnosticLogger diagnostics,
            TargetProcess target,
            int capability) {
        if (!installed && !diagnostics.isEnabled()) return;
        TargetProcessRuntime runtime = new TargetProcessRuntime(
                this,
                diagnostics,
                target.packageName,
                target.processName);
        if (runtime.installLifecycleHook() && installed) {
            runtime.recordInstalledCapability(capability);
        }
    }

    private void recordDiagnosticBootstrap(
            TargetProcess target,
            HookFeatureFlags features,
            DiagnosticLogger diagnostics) {
        if (!diagnostics.isEnabled()) return;
        try {
            diagnostics.event(
                    "module_loaded",
                    "moduleVersion", BuildConfig.VERSION_NAME + "(" + BuildConfig.VERSION_CODE + ")",
                    "targetPackage", target.packageName,
                    "framework", getFrameworkName(),
                    "frameworkVersion", getFrameworkVersion()
                            + "(" + getFrameworkVersionCode() + ")",
                    "frameworkApi", Integer.toString(getApiVersion()),
                    "frameworkProperties", Long.toUnsignedString(getFrameworkProperties()),
                    "notificationEnabled", Boolean.toString(features.crossDeviceNotification),
                    "portraitEnabled", Boolean.toString(features.portraitStreaming),
                    "multiChannelEnabled", Boolean.toString(features.miLinkMultiChannel));
        } catch (Throwable error) {
            diagnostics.error("module_loaded", error);
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

    private boolean isTablet(ClassLoader classLoader, DiagnosticLogger diagnostics) {
        try {
            boolean tablet = DeviceFormFactor.isTablet(classLoader);
            diagnostics.event("form_factor", "isTablet", Boolean.toString(tablet));
            return tablet;
        } catch (Throwable error) {
            log(Log.WARN, TAG, "Could not read HyperOS tablet form factor", error);
            diagnostics.error("form_factor", error, "isTablet", "unknown");
            return false;
        }
    }
}
