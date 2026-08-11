package com.weichao.hyperconnecttoolkit.xposed;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Application;
import android.app.BroadcastOptions;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.hardware.display.VirtualDisplayConfig;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.Window;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import com.weichao.hyperconnecttoolkit.BuildConfig;

import io.github.libxposed.api.XposedModule;
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam;

/**
 * HyperOS cross-device compatibility layer for tablets.
 *
 * <p>Every hook is constrained by package, class and method signature. Failure is isolated per
 * feature so a vendor update cannot turn one incompatible hook into a process-wide failure.</p>
 */
public final class HyperConnectModule extends XposedModule {
    private static final String TAG = "HyperConnectToolkit";
    private static final String MODULE_PACKAGE = "com.weichao.hyperconnecttoolkit";
    private static final String MILINK_PACKAGE = "com.milink.service";
    private static final String MIRROR_PACKAGE = "com.xiaomi.mirror";

    private static final String DISCOVER_CLASS =
            "com.xiaomi.dist.notification.discover.LyraDiscover";
    private static final String SHARE_NOTIFICATION_HELPER_CLASS = "G3.g";
    private static final String PHONE_SINK_MANAGER_CLASS = "com.xiaomi.mirror.sink.c";
    private static final String TABLET_SINK_ACTIVITY_CLASS = "com.xiaomi.mirror.sink.g";
    private static final String SESSION_OPEN_COORDINATOR_CLASS = "j4.h";
    private static final String REMOTE_TRUST_MANAGER_CLASS = "com.xiaomi.mirror.trust.u";

    private static final String HOOK_STATUS_ACTION =
            "com.weichao.hyperconnecttoolkit.action.HOOKS_ACTIVE";

    private boolean miLinkHookInstalled;
    private boolean mirrorNotificationHooksInstalled;
    private boolean rotationHooksInstalled;
    private boolean activationReported;

    @Override
    public void onPackageLoaded(PackageLoadedParam param) {
        String packageName = param.getPackageName();
        if (MODULE_PACKAGE.equals(packageName)) {
            markModuleAppProcessActive(param.getDefaultClassLoader());
            return;
        }

        if (MILINK_PACKAGE.equals(packageName) && !miLinkHookInstalled) {
            miLinkHookInstalled = true;
            installMiLinkDiscoveryHook(param.getDefaultClassLoader());
        } else if (MIRROR_PACKAGE.equals(packageName) && !mirrorNotificationHooksInstalled) {
            mirrorNotificationHooksInstalled = true;
            installMirrorNotificationFlowHooks(param.getDefaultClassLoader());
            installTargetStatusReporter();
        }
    }

    @Override
    public void onPackageReady(PackageReadyParam param) {
        if (!param.isFirstPackage()
                || !MIRROR_PACKAGE.equals(param.getPackageName())
                || rotationHooksInstalled) {
            return;
        }
        rotationHooksInstalled = true;
        installRotationAndFrameRateHooks(param.getClassLoader());
    }

    private void markModuleAppProcessActive(ClassLoader appClassLoader) {
        try {
            Class<?> marker = appClassLoader.loadClass(
                    "com.weichao.hyperconnecttoolkit.platform.ModuleRuntimeState");
            Method markActive = marker.getDeclaredMethod("markActive");
            markActive.invoke(null);
            log(Log.INFO, TAG, "Module app activation handshake completed");
        } catch (Throwable error) {
            log(Log.WARN, TAG, "Could not update the in-app activation indicator", error);
        }
    }

    /**
     * Reports status from the process where the functional hooks actually loaded.
     *
     * <p>Some LSPosed builds deliberately do not inject a module into its own application process,
     * so self-process class-loader handshakes can remain false even while every target hook is
     * active. The explicit receiver validates the original sending UID against the audited target
     * packages before persisting this versioned evidence.</p>
     */
    @SuppressLint("DiscouragedPrivateApi")
    private void installTargetStatusReporter() {
        try {
            Method attach = Application.class.getDeclaredMethod("attach", Context.class);
            attach.setAccessible(true);
            hook(attach).intercept(chain -> {
                Object result = chain.proceed();
                Object context = chain.getArg(0);
                if (context instanceof Context) {
                    reportTargetHooksActive((Context) context);
                }
                return result;
            });
            log(Log.INFO, TAG, "Installed application-attach activation reporter");
        } catch (Throwable error) {
            log(Log.WARN, TAG, "Could not install target hook activation reporter", error);
        }
    }

    private void reportTargetHooksActive(Context context) {
        if (activationReported) {
            return;
        }

        try {
            Intent status = new Intent(HOOK_STATUS_ACTION)
                    .setComponent(new ComponentName(
                            MODULE_PACKAGE,
                            MODULE_PACKAGE + ".platform.HookStatusReceiver"))
                    .putExtra("source_package", MIRROR_PACKAGE)
                    .putExtra("module_version", BuildConfig.VERSION_CODE)
                    .addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES
                            | Intent.FLAG_RECEIVER_FOREGROUND);
            BroadcastOptions options = BroadcastOptions.makeBasic();
            options.setShareIdentityEnabled(true);
            context.sendBroadcast(status, null, options.toBundle());
            activationReported = true;
            log(Log.INFO, TAG, "Reported active hooks from Mirror process");
        } catch (Throwable error) {
            log(Log.WARN, TAG, "Could not report target hook activation to the app", error);
        }
    }

    /** Allows only the local tablet to pass the cast-capability prerequisite for discovery. */
    private void installMiLinkDiscoveryHook(ClassLoader classLoader) {
        try {
            Class<?> discoverClass = classLoader.loadClass(DISCOVER_CLASS);
            Method castSupport = discoverClass.getDeclaredMethod(
                    "isCastServiceSupport", String.class);
            castSupport.setAccessible(true);

            hook(castSupport).intercept(chain -> {
                try {
                    String requestedDeviceId = (String) chain.getArg(0);
                    String localDeviceId = getLocalDeviceId(
                            discoverClass,
                            chain.getThisObject());
                    if (localDeviceId != null && localDeviceId.equals(requestedDeviceId)) {
                        log(Log.INFO, TAG,
                                "Notification discovery: bypassed local tablet cast capability");
                        return Boolean.TRUE;
                    }
                } catch (Throwable error) {
                    log(Log.WARN, TAG,
                            "Notification discovery identity check failed; using stock result",
                            error);
                }
                return chain.proceed();
            });

            log(Log.INFO, TAG, "Installed MiLink notification discovery hook");
        } catch (Throwable error) {
            log(Log.ERROR, TAG, "MiLink notification discovery hook is incompatible", error);
        }
    }

    private void installMirrorNotificationFlowHooks(ClassLoader classLoader) {
        if (!isTabletDevice(classLoader)) {
            log(Log.INFO, TAG, "Skipped tablet notification flow hook on a non-tablet device");
            return;
        }

        installNotificationLaunchHook(classLoader);
        installNotificationSessionEstablishmentHook(classLoader);
    }

    /** Replays the stock launch call after the tablet-only guard in G3.g#v(Bundle). */
    private void installNotificationLaunchHook(ClassLoader classLoader) {
        try {
            Class<?> helperClass = classLoader.loadClass(SHARE_NOTIFICATION_HELPER_CLASS);
            Method launchNotificationSink = helperClass.getDeclaredMethod("v", Bundle.class);
            launchNotificationSink.setAccessible(true);

            Class<?> sinkManagerClass = classLoader.loadClass(PHONE_SINK_MANAGER_CLASS);
            Method getSinkManager = sinkManagerClass.getDeclaredMethod("l");
            getSinkManager.setAccessible(true);
            Method launch = sinkManagerClass.getDeclaredMethod(
                    "L",
                    String.class,
                    int.class,
                    String.class,
                    boolean.class,
                    boolean.class);
            launch.setAccessible(true);

            hook(launchNotificationSink).intercept(chain -> {
                Bundle extras = (Bundle) chain.getArg(0);
                if (extras == null) {
                    return chain.proceed();
                }

                String deviceId = extras.getString("deviceId");
                String packageName = extras.getString("package");
                if (TextUtils.isEmpty(deviceId) || TextUtils.isEmpty(packageName)) {
                    return chain.proceed();
                }

                try {
                    Object sinkManager = getSinkManager.invoke(null);
                    launch.invoke(
                            sinkManager,
                            deviceId,
                            CompatibilityPolicy.CROSS_DEVICE_NOTIFICATION_REASON,
                            packageName,
                            false,
                            false);

                    Bundle success = new Bundle();
                    success.putInt("value", 0);
                    log(Log.INFO, TAG, "Started notification-triggered tablet sub-screen");
                    return success;
                } catch (Throwable error) {
                    log(Log.ERROR, TAG,
                            "Notification sub-screen launch failed; falling back to stock behavior",
                            unwrapInvocationTarget(error));
                    return chain.proceed();
                }
            });

            log(Log.INFO, TAG, "Installed notification sub-screen launch hook");
        } catch (Throwable error) {
            log(Log.ERROR, TAG, "Notification sub-screen launch hook is incompatible", error);
        }
    }

    /**
     * Corrects the notification session before its first transport message is sent.
     *
     * <p>Reason 17 reaches the generic tablet sink, whose stock policy sends a dimensionless
     * {@code ScreenActionMessage}. The pinned-app path instead sends a
     * {@code RequestAppScreenMessage} with the current window metrics. Reusing that stock branch
     * here gives the source device the correct portrait dimensions at display creation time. The
     * trust refresh is deliberately ordered before the open request, matching Xiaomi's phone
     * mirror activities.</p>
     */
    private void installNotificationSessionEstablishmentHook(ClassLoader classLoader) {
        try {
            Class<?> coordinatorClass = classLoader.loadClass(SESSION_OPEN_COORDINATOR_CLASS);
            Method openSession = coordinatorClass.getDeclaredMethod(
                    "a",
                    String.class,
                    int.class,
                    String.class,
                    Window.class,
                    boolean.class,
                    boolean.class,
                    Activity.class);
            openSession.setAccessible(true);

            Method getTrustManager = null;
            Method refreshStatus = null;
            try {
                Class<?> trustManagerClass = classLoader.loadClass(REMOTE_TRUST_MANAGER_CLASS);
                getTrustManager = trustManagerClass.getDeclaredMethod("A");
                getTrustManager.setAccessible(true);
                refreshStatus = trustManagerClass.getDeclaredMethod("m0", String.class);
                refreshStatus.setAccessible(true);
            } catch (Throwable error) {
                log(Log.WARN, TAG,
                        "Trust initialization is unavailable; initial-size policy remains active",
                        error);
            }
            Method resolvedGetTrustManager = getTrustManager;
            Method resolvedRefreshStatus = refreshStatus;

            hook(openSession).intercept(chain -> {
                int startReason = (int) chain.getArg(1);
                if (!CompatibilityPolicy.isCrossDeviceNotificationSession(startReason)) {
                    return chain.proceed();
                }

                String deviceId = (String) chain.getArg(0);
                if (!TextUtils.isEmpty(deviceId)
                        && resolvedGetTrustManager != null
                        && resolvedRefreshStatus != null) {
                    try {
                        Object trustManager = resolvedGetTrustManager.invoke(null);
                        resolvedRefreshStatus.invoke(trustManager, deviceId);
                    } catch (Throwable error) {
                        log(Log.ERROR, TAG,
                                "Remote trust status initialization failed",
                                unwrapInvocationTarget(error));
                    }
                }

                Object[] args = chain.getArgs().toArray();
                args[4] = CompatibilityPolicy.useAppScreenOpenRequest(
                        (boolean) args[4],
                        startReason);
                args[5] = CompatibilityPolicy.forceLandscapeInitialBounds(
                        (boolean) args[5],
                        startReason);
                log(Log.INFO, TAG,
                        "Notification session established with native window metrics and trust");
                return chain.proceed(args);
            });

            log(Log.INFO, TAG, "Installed notification session-establishment hook");
        } catch (Throwable error) {
            log(Log.ERROR, TAG, "Notification session-establishment hook is incompatible", error);
        }
    }

    private void installRotationAndFrameRateHooks(ClassLoader classLoader) {
        hookRequestedOrientation(classLoader);
        hookVirtualDisplayRefreshRate();
        hookStreamFrameRate(classLoader);
        log(Log.INFO, TAG, "Installed scoped orientation and establishment-time 120 fps hooks");
    }

    private void hookRequestedOrientation(ClassLoader classLoader) {
        try {
            Class<?> sinkActivityClass = Class.forName(
                    TABLET_SINK_ACTIVITY_CLASS,
                    false,
                    classLoader);
            Method method = Activity.class.getDeclaredMethod(
                    "setRequestedOrientation",
                    int.class);
            hook(method).intercept(chain -> {
                Object activity = chain.getThisObject();
                int requestedOrientation = (int) chain.getArg(0);
                int resolvedOrientation = CompatibilityPolicy.resolveRequestedOrientation(
                        sinkActivityClass.isInstance(activity),
                        requestedOrientation);
                if (resolvedOrientation != requestedOrientation) {
                    log(Log.INFO, TAG,
                            "Tablet sink orientation policy: SENSOR_LANDSCAPE -> FULL_SENSOR");
                    return chain.proceed(new Object[]{resolvedOrientation});
                }
                return chain.proceed();
            });
        } catch (Throwable error) {
            log(Log.ERROR, TAG, "Activity orientation hook is incompatible", error);
        }
    }

    // Intentional: this code runs inside the privileged Mirror process through LSPosed and hooks
    // the ROM's own builder. It is not invoked from the normal application process.
    @SuppressLint("BlockedPrivateApi")
    private void hookVirtualDisplayRefreshRate() {
        try {
            Method build = VirtualDisplayConfig.Builder.class.getDeclaredMethod("build");
            hook(build).intercept(chain -> {
                VirtualDisplayConfig.Builder builder =
                        (VirtualDisplayConfig.Builder) chain.getThisObject();
                builder.setRequestedRefreshRate(CompatibilityPolicy.TARGET_FPS);
                return chain.proceed();
            });
        } catch (ReflectiveOperationException error) {
            log(Log.ERROR, TAG, "Virtual-display refresh-rate hook is incompatible", error);
        }
    }

    private void hookStreamFrameRate(ClassLoader classLoader) {
        try {
            Class<?> displayConfigBuilderClass = Class.forName(
                    "r3.J$b",
                    false,
                    classLoader);
            Method setFrameRate = displayConfigBuilderClass.getDeclaredMethod("h", int.class);
            setFrameRate.setAccessible(true);
            hook(setFrameRate).intercept(chain -> {
                int stockFps = (int) chain.getArg(0);
                int targetFps = CompatibilityPolicy.resolveFrameRate(stockFps);
                return targetFps == stockFps
                        ? chain.proceed()
                        : chain.proceed(new Object[]{targetFps});
            });

            Class<?> codecConfigClass = Class.forName("t3.m", false, classLoader);
            Method initializeEncoderLimits = codecConfigClass.getDeclaredMethod(
                    "n",
                    int.class,
                    int.class);
            initializeEncoderLimits.setAccessible(true);
            hook(initializeEncoderLimits).intercept(chain -> {
                int stockFps = (int) chain.getArg(0);
                int targetFps = CompatibilityPolicy.resolveFrameRate(stockFps);
                if (targetFps == stockFps) {
                    return chain.proceed();
                }
                return chain.proceed(new Object[]{targetFps, chain.getArg(1)});
            });
            log(Log.INFO, TAG, "Installed display and encoder initialization frame-rate hooks");
        } catch (ReflectiveOperationException error) {
            log(Log.ERROR, TAG, "Stream frame-rate hook is incompatible", error);
        }
    }

    private boolean isTabletDevice(ClassLoader classLoader) {
        try {
            Class<?> miuiBuild = Class.forName("miui.os.Build", false, classLoader);
            return miuiBuild.getField("IS_TABLET").getBoolean(null);
        } catch (Throwable error) {
            log(Log.WARN, TAG, "Could not read miui.os.Build.IS_TABLET", error);
            return false;
        }
    }

    private static String getLocalDeviceId(
            Class<?> discoverClass,
            Object discoverInstance) throws ReflectiveOperationException {
        Field managerField = discoverClass.getDeclaredField("mNetworkingManager");
        managerField.setAccessible(true);
        Object networkingManager = managerField.get(discoverInstance);
        if (networkingManager == null) {
            return null;
        }

        Method getLocalDeviceInfo = networkingManager.getClass()
                .getMethod("getLocalDeviceInfo");
        Object localDeviceInfo = getLocalDeviceInfo.invoke(networkingManager);
        if (localDeviceInfo == null) {
            return null;
        }

        Method getDeviceId = localDeviceInfo.getClass().getMethod("getDeviceId");
        Object value = getDeviceId.invoke(localDeviceInfo);
        return value instanceof String ? (String) value : null;
    }

    private static Throwable unwrapInvocationTarget(Throwable error) {
        if (error instanceof InvocationTargetException
                && ((InvocationTargetException) error).getCause() != null) {
            return ((InvocationTargetException) error).getCause();
        }
        return error;
    }
}
