package com.weichao.hyperconnecttoolkit.xposed;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Application;
import android.app.BroadcastOptions;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

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
    private static final String CONTINUITY_SERVICE_NAME_CLASS =
            "com.xiaomi.continuity.ServiceName";
    private static final String CALL_PROVIDER_CLASS =
            "com.xiaomi.mirror.provider.CallProvider";
    private static final String TABLET_SINK_ACTIVITY_CLASS = "com.xiaomi.mirror.sink.g";

    private static final String HOOK_STATUS_ACTION =
            "com.weichao.hyperconnecttoolkit.action.HOOKS_ACTIVE";

    private boolean miLinkHookInstalled;
    private boolean mirrorNotificationHooksInstalled;
    private boolean rotationHookInstalled;
    private boolean activationReported;
    private final ThreadLocal<Integer> localDiscoveryCheckDepth = new ThreadLocal<>();

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
                || rotationHookInstalled) {
            return;
        }
        rotationHookInstalled = true;
        installRotationHook(param.getClassLoader());
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

    /**
     * Bypasses only the local Cast bit read by notification discovery.
     *
     * <p>The same Cast predicate is also used later for remote phones, so returning true globally
     * would weaken peer capability negotiation. A thread-confined scope around
     * {@code isLocalServiceDataSupport} identifies its synchronous nested Cast check without
     * reflecting MiLink's networking manager or constructing vendor device objects.</p>
     */
    private void installMiLinkDiscoveryHook(ClassLoader classLoader) {
        try {
            Class<?> discoverClass = classLoader.loadClass(DISCOVER_CLASS);
            Class<?> serviceNameClass = classLoader.loadClass(CONTINUITY_SERVICE_NAME_CLASS);

            Method localServiceSupport = discoverClass.getDeclaredMethod(
                    "isLocalServiceDataSupport",
                    serviceNameClass);
            localServiceSupport.setAccessible(true);
            Method castSupport = discoverClass.getDeclaredMethod(
                    "isCastServiceSupport", String.class);
            castSupport.setAccessible(true);

            hook(localServiceSupport).intercept(chain -> {
                enterLocalDiscoveryCheck();
                try {
                    return chain.proceed();
                } finally {
                    exitLocalDiscoveryCheck();
                }
            });

            hook(castSupport).intercept(chain -> {
                if (isLocalDiscoveryCheckActive()) {
                    log(Log.INFO, TAG,
                            "Notification discovery: bypassed the scoped local Cast prerequisite");
                    return Boolean.TRUE;
                }
                return chain.proceed();
            });

            log(Log.INFO, TAG, "Installed scoped MiLink notification discovery hooks");
        } catch (Throwable error) {
            log(Log.ERROR, TAG, "MiLink notification discovery hooks are incompatible", error);
        }
    }

    private void enterLocalDiscoveryCheck() {
        Integer depth = localDiscoveryCheckDepth.get();
        localDiscoveryCheckDepth.set(depth == null ? 1 : depth + 1);
    }

    private void exitLocalDiscoveryCheck() {
        Integer depth = localDiscoveryCheckDepth.get();
        if (depth == null || depth <= 1) {
            localDiscoveryCheckDepth.remove();
            return;
        }
        localDiscoveryCheckDepth.set(depth - 1);
    }

    private boolean isLocalDiscoveryCheckActive() {
        Integer depth = localDiscoveryCheckDepth.get();
        return depth != null && depth > 0;
    }

    private void installMirrorNotificationFlowHooks(ClassLoader classLoader) {
        if (!isTabletDevice(classLoader)) {
            log(Log.INFO, TAG, "Skipped tablet notification flow hook on a non-tablet device");
            return;
        }

        installNotificationProviderBridge(classLoader);
    }

    /** Routes notification clicks through Xiaomi's complete pinned-app flow (reason 9). */
    private void installNotificationProviderBridge(ClassLoader classLoader) {
        try {
            Class<?> providerClass = classLoader.loadClass(CALL_PROVIDER_CLASS);
            Method call = providerClass.getDeclaredMethod(
                    "call",
                    String.class,
                    String.class,
                    Bundle.class);
            call.setAccessible(true);

            hook(call).intercept(chain -> {
                String method = (String) chain.getArg(0);
                if (!(chain.getArg(2) instanceof Bundle)) {
                    return chain.proceed();
                }
                Bundle extras = (Bundle) chain.getArg(2);

                String deviceId = extras.getString("deviceId");
                String packageName = extras.getString("package");
                if (!CompatibilityPolicy.shouldBridgeNotificationCall(
                        method,
                        deviceId,
                        packageName)) {
                    return chain.proceed();
                }

                Bundle pinAppExtras = new Bundle(extras);
                pinAppExtras.putString("remoteDeviceId", deviceId);

                Object[] args = chain.getArgs().toArray();
                args[0] = CompatibilityPolicy.PIN_APP_PROVIDER_METHOD;
                args[2] = pinAppExtras;

                // Invoke the provider exactly once. If Xiaomi's native PIN_APP path fails, let
                // that failure propagate instead of replaying the original notification action
                // after the first invocation may already have changed connection state.
                Object stockResult = chain.proceed(args);

                Bundle result = stockResult instanceof Bundle
                        ? new Bundle((Bundle) stockResult)
                        : new Bundle();
                result.putInt("value", 0);
                log(Log.INFO, TAG,
                        "Notification click routed through the stock PIN_APP flow");
                return result;
            });

            log(Log.INFO, TAG, "Installed notification provider bridge");
        } catch (Throwable error) {
            log(Log.ERROR, TAG, "Notification provider bridge is incompatible", error);
        }
    }

    private void installRotationHook(ClassLoader classLoader) {
        hookRequestedOrientation(classLoader);
        log(Log.INFO, TAG, "Installed scoped orientation hook");
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

    private boolean isTabletDevice(ClassLoader classLoader) {
        try {
            Class<?> miuiBuild = Class.forName("miui.os.Build", false, classLoader);
            return miuiBuild.getField("IS_TABLET").getBoolean(null);
        } catch (Throwable error) {
            log(Log.WARN, TAG, "Could not read miui.os.Build.IS_TABLET", error);
            return false;
        }
    }

}
