package com.weichao.hyperconnecttoolkit.xposed;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Application;
import android.app.BroadcastOptions;
import android.app.Instrumentation;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.graphics.Rect;
import android.hardware.display.VirtualDisplayConfig;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

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
    private static final String REMOTE_TRUST_MANAGER_CLASS = "com.xiaomi.mirror.trust.u";
    private static final int CROSS_DEVICE_NOTIFICATION_REASON = 17;

    private static final int STOCK_FPS = 60;
    private static final int TARGET_FPS = 120;
    private static final int MODULE_VERSION_CODE = 3;
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
        installTargetStatusReporter(param.getClassLoader());
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
    private void installTargetStatusReporter(ClassLoader classLoader) {
        try {
            Method callApplicationOnCreate = Instrumentation.class.getDeclaredMethod(
                    "callApplicationOnCreate",
                    Application.class);
            hook(callApplicationOnCreate).intercept(chain -> {
                Object result = chain.proceed();
                Object application = chain.getArg(0);
                if (application instanceof Context) {
                    reportTargetHooksActive((Context) application);
                }
                return result;
            });

            // PackageReady runs while ActivityThread is still binding the application on some
            // HyperOS builds. These retries cover custom Instrumentation implementations that do
            // not delegate to callApplicationOnCreate, without blocking process startup.
            Handler mainHandler = new Handler(Looper.getMainLooper());
            for (long delayMs : new long[]{250L, 1000L, 3000L}) {
                mainHandler.postDelayed(() -> {
                    if (activationReported) {
                        return;
                    }
                    try {
                        Class<?> mirrorClass = classLoader.loadClass(
                                "com.xiaomi.mirror.Mirror");
                        Method getApplicationContext = mirrorClass.getDeclaredMethod("z");
                        getApplicationContext.setAccessible(true);
                        Object context = getApplicationContext.invoke(null);
                        if (context instanceof Context) {
                            reportTargetHooksActive((Context) context);
                        }
                    } catch (Throwable error) {
                        log(Log.WARN, TAG,
                                "Deferred target hook activation report failed", error);
                    }
                }, delayMs);
            }
            log(Log.INFO, TAG, "Installed target-process activation reporter");
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
                    .putExtra("module_version", MODULE_VERSION_CODE)
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
        installNotificationTrustRefreshHook(classLoader);
    }

    /** Replays the stock launch call after the tablet-only guard in G3.g#v(Bundle). */
    private void installNotificationLaunchHook(ClassLoader classLoader) {
        try {
            Class<?> helperClass = classLoader.loadClass(SHARE_NOTIFICATION_HELPER_CLASS);
            Method launchNotificationSink = helperClass.getDeclaredMethod("v", Bundle.class);
            launchNotificationSink.setAccessible(true);

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
                    Class<?> sinkManagerClass = classLoader.loadClass(PHONE_SINK_MANAGER_CLASS);
                    Method getInstance = sinkManagerClass.getDeclaredMethod("l");
                    getInstance.setAccessible(true);
                    Object sinkManager = getInstance.invoke(null);

                    Method launch = sinkManagerClass.getDeclaredMethod(
                            "L",
                            String.class,
                            int.class,
                            String.class,
                            boolean.class,
                            boolean.class);
                    launch.setAccessible(true);
                    launch.invoke(
                            sinkManager,
                            deviceId,
                            CROSS_DEVICE_NOTIFICATION_REASON,
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

    /** Restores the trust-state initialization normally performed by the phone-only activity. */
    private void installNotificationTrustRefreshHook(ClassLoader classLoader) {
        try {
            Class<?> sinkActivityClass = classLoader.loadClass(TABLET_SINK_ACTIVITY_CLASS);
            Method onMirrorConnected = sinkActivityClass.getDeclaredMethod("m");
            onMirrorConnected.setAccessible(true);

            Class<?> trustManagerClass = classLoader.loadClass(REMOTE_TRUST_MANAGER_CLASS);
            Method getTrustManager = trustManagerClass.getDeclaredMethod("A");
            getTrustManager.setAccessible(true);
            Method refreshStatus = trustManagerClass.getDeclaredMethod("m0", String.class);
            refreshStatus.setAccessible(true);

            hook(onMirrorConnected).intercept(chain -> {
                Object target = chain.getThisObject();
                if (target instanceof Activity) {
                    Intent intent = ((Activity) target).getIntent();
                    if (intent != null
                            && intent.getIntExtra("mirror:start_reason", -1)
                            == CROSS_DEVICE_NOTIFICATION_REASON) {
                        String deviceId = intent.getStringExtra("mirror:device_id");
                        if (!TextUtils.isEmpty(deviceId)) {
                            try {
                                Object trustManager = getTrustManager.invoke(null);
                                refreshStatus.invoke(trustManager, deviceId);
                                log(Log.INFO, TAG,
                                        "Refreshed remote trust status before notification stream");
                            } catch (Throwable error) {
                                log(Log.ERROR, TAG,
                                        "Remote trust status refresh failed",
                                        unwrapInvocationTarget(error));
                            }
                        }
                    }
                }
                return chain.proceed();
            });

            log(Log.INFO, TAG, "Installed notification trust-status hook");
        } catch (Throwable error) {
            log(Log.ERROR, TAG, "Notification trust-status hook is incompatible", error);
        }
    }

    private void installRotationAndFrameRateHooks(ClassLoader classLoader) {
        hookRequestedOrientation();
        hookPortraitResizeLifecycle(classLoader);
        hookVirtualDisplayRefreshRate();
        hookStreamFrameRate(classLoader);
        log(Log.INFO, TAG, "Installed rotation, portrait sync and 120 fps hooks");
    }

    private void hookRequestedOrientation() {
        try {
            Method method = Activity.class.getDeclaredMethod(
                    "setRequestedOrientation",
                    int.class);
            hook(method).intercept(chain -> {
                if ((int) chain.getArg(0)
                        == ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE) {
                    log(Log.INFO, TAG, "Orientation request: SENSOR_LANDSCAPE -> FULL_SENSOR");
                    return chain.proceed(
                            new Object[]{ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR});
                }
                return chain.proceed();
            });
        } catch (ReflectiveOperationException error) {
            log(Log.ERROR, TAG, "Activity orientation hook is incompatible", error);
        }
    }

    /**
     * Synchronizes the phone encoder with an already-portrait tablet window.
     *
     * <p>The authentication layer can restore the phone's initial landscape encoder after the
     * remote display has been created. The stock callback g#u1(false) is the stable point where
     * every authentication UI path has exited and UNLOCK_SUBSCREEN is sent. A short bounded retry
     * sequence after that event tolerates transport ordering without manufacturing a fake device
     * rotation. Remote-display creation and first-frame hooks remain compatibility fallbacks.</p>
     */
    private void hookPortraitResizeLifecycle(ClassLoader classLoader) {
        try {
            Class<?> sinkActivityClass = Class.forName(
                    TABLET_SINK_ACTIVITY_CLASS,
                    false,
                    classLoader);
            Method remoteDisplayCreated = findRemoteDisplayCreated(sinkActivityClass);
            Method sendResize = sinkActivityClass.getDeclaredMethod(
                    "k1",
                    int.class,
                    boolean.class);
            sendResize.setAccessible(true);
            Method firstFrameDisplayed = sinkActivityClass.getDeclaredMethod("C");
            firstFrameDisplayed.setAccessible(true);
            Method authenticationVisibilityChanged = sinkActivityClass.getDeclaredMethod(
                    "u1",
                    boolean.class);
            authenticationVisibilityChanged.setAccessible(true);

            hook(remoteDisplayCreated).intercept(chain -> {
                Object result = chain.proceed();
                schedulePortraitSync(
                        (Activity) chain.getThisObject(),
                        sendResize,
                        "remote-display-created",
                        0L,
                        500L);
                return result;
            });

            hook(firstFrameDisplayed).intercept(chain -> {
                Object result = chain.proceed();
                schedulePortraitSync(
                        (Activity) chain.getThisObject(),
                        sendResize,
                        "first-frame-fallback",
                        350L,
                        1200L);
                return result;
            });

            hook(authenticationVisibilityChanged).intercept(chain -> {
                boolean showAuthentication = (boolean) chain.getArg(0);
                Object result = chain.proceed();
                if (!showAuthentication) {
                    schedulePortraitSync(
                            (Activity) chain.getThisObject(),
                            sendResize,
                            "post-auth-unlock",
                            180L,
                            700L,
                            1600L);
                }
                return result;
            });
        } catch (Throwable error) {
            log(Log.ERROR, TAG, "Portrait resize lifecycle hook is incompatible", error);
        }
    }

    private void schedulePortraitSync(
            Activity activity,
            Method sendResize,
            String phase,
            long... delaysMs) {
        for (long delayMs : delaysMs) {
            activity.getWindow().getDecorView().postDelayed(
                    () -> syncPortraitResize(activity, sendResize, phase, delayMs),
                    delayMs);
        }
    }

    private void syncPortraitResize(
            Activity activity,
            Method sendResize,
            String phase,
            long delayMs) {
        try {
            if (activity.isFinishing() || activity.isDestroyed()) {
                return;
            }
            Configuration configuration = activity.getResources().getConfiguration();
            Rect bounds = activity.getWindowManager().getCurrentWindowMetrics().getBounds();

            if (configuration.orientation == Configuration.ORIENTATION_PORTRAIT
                    && bounds.height() > bounds.width()) {
                sendResize.invoke(activity, Configuration.ORIENTATION_PORTRAIT, true);
                log(Log.INFO, TAG,
                        "Portrait sync " + phase + "+" + delayMs + "ms: "
                                + bounds.width() + "x" + bounds.height());
            }
        } catch (Throwable error) {
            log(Log.ERROR, TAG,
                    "Portrait sync failed at " + phase + "+" + delayMs + "ms",
                    unwrapInvocationTarget(error));
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
                builder.setRequestedRefreshRate(TARGET_FPS);
                return chain.proceed();
            });
        } catch (ReflectiveOperationException error) {
            log(Log.ERROR, TAG, "Virtual-display refresh-rate hook is incompatible", error);
        }
    }

    private void hookStreamFrameRate(ClassLoader classLoader) {
        try {
            Class<?> displayConfigClass = Class.forName("r3.J", false, classLoader);
            int hookedConstructors = 0;
            for (Constructor<?> constructor : displayConfigClass.getDeclaredConstructors()) {
                Class<?>[] types = constructor.getParameterTypes();
                if (types.length == 0 || types[0] != int.class) {
                    continue;
                }
                hook(constructor).intercept(chain -> {
                    Object[] args = chain.getArgs().toArray();
                    if ((int) args[0] == STOCK_FPS) {
                        args[0] = TARGET_FPS;
                        return chain.proceed(args);
                    }
                    return chain.proceed();
                });
                hookedConstructors++;
            }

            Class<?> codecConfigClass = Class.forName("t3.m", false, classLoader);
            Method getMaxFps = codecConfigClass.getDeclaredMethod("d");
            hook(getMaxFps).intercept(chain -> {
                int fps = (int) chain.proceed();
                return fps == STOCK_FPS ? TARGET_FPS : fps;
            });
            log(Log.INFO, TAG,
                    "Installed stream frame-rate hooks on " + hookedConstructors
                            + " display-config constructors");
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

    private static Method findRemoteDisplayCreated(Class<?> sinkActivityClass)
            throws NoSuchMethodException {
        for (Method method : sinkActivityClass.getDeclaredMethods()) {
            Class<?>[] types = method.getParameterTypes();
            if (method.getName().equals("H")
                    && types.length == 6
                    && types[0] == int.class
                    && types[1] == int.class
                    && types[3] == String.class
                    && types[5] == boolean.class) {
                method.setAccessible(true);
                return method;
            }
        }
        throw new NoSuchMethodException(
                "com.xiaomi.mirror.sink.g#H(remoteDisplayCreated)");
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
