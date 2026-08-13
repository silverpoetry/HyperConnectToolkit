package com.weichao.hyperconnecttoolkit.xposed;

import android.content.Context;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.Log;

import java.lang.reflect.Method;

import io.github.libxposed.api.XposedModule;

/** Complete cross-device notification feature installed only in MiLink's notification process. */
final class MiLinkNotificationHooks {
    private static final String TAG = "HyperConnectToolkit";

    private static final String DISCOVER_CLASS =
            "com.xiaomi.dist.notification.discover.LyraDiscover";
    private static final String CONTINUITY_SERVICE_NAME_CLASS =
            "com.xiaomi.continuity.ServiceName";
    private static final String MIUI_SYNERGY_SDK_CLASS =
            "com.xiaomi.mirror.synergy.MiuiSynergySdk";
    private static final String RELAY_APP_CALLBACK_CLASS =
            "com.xiaomi.mirror.synergy.MiuiSynergySdk$RelayAppCallback";
    private static final String NOTIFICATION_TRANS_CLIENT_CLASS =
            "com.xiaomi.dist.notification.trans.client.NotifTransClient";
    private static final String NOTIFICATION_MESSAGE_CLASS =
            "com.xiaomi.dist.notification.common.data.NotificationMessage";
    private static final String NOTIFICATION_TRANS_CALLBACK_CLASS =
            "com.xiaomi.dist.notification.trans.client.callback.INotificationTransCallback";
    private static final String MIRROR_PROVIDER_AUTHORITY =
            "com.xiaomi.mirror.callprovider";

    private final XposedModule module;
    private final DiagnosticLogger diagnostics;
    private final ThreadLocal<Integer> localDiscoveryDepth = new ThreadLocal<>();
    private final NotificationClickSession clickSession = new NotificationClickSession();

    MiLinkNotificationHooks(XposedModule module, DiagnosticLogger diagnostics) {
        this.module = module;
        this.diagnostics = diagnostics;
    }

    /** Resolves every vendor method before registering the feature as one compatibility unit. */
    boolean install(ClassLoader classLoader) {
        try {
            Bindings bindings = Bindings.resolve(classLoader);
            installDiscoveryHooks(bindings);
            installClickHooks(bindings);
            diagnostics.event("functional_hooks", "state", "installed");
            module.log(Log.INFO, TAG, "Installed MiLink cross-device notification hooks");
            return true;
        } catch (Throwable error) {
            diagnostics.error("functional_hooks", error, "state", "incompatible");
            module.log(Log.ERROR, TAG,
                    "MiLink cross-device notification hooks are incompatible",
                    error);
            return false;
        }
    }

    void installDiagnostics(ClassLoader classLoader, boolean functionalDiscoveryInstalled) {
        new NotificationDiscoveryDiagnostics(module, diagnostics).install(
                classLoader,
                functionalDiscoveryInstalled);
    }

    /**
     * Bypasses the Cast bit only while MiLink evaluates its synchronous local prerequisite.
     * Remote-device capability negotiation continues through the stock implementation.
     */
    private void installDiscoveryHooks(Bindings bindings) {
        module.hook(bindings.localServiceSupport).intercept(chain -> {
            enterLocalDiscovery();
            try {
                Object result = chain.proceed();
                diagnostics.trace(
                        "local_service_support",
                        80,
                        "local_service_support",
                        "service", diagnostics.summarize(chain.getArg(0)),
                        "result", diagnostics.summarize(result));
                return result;
            } catch (Throwable error) {
                diagnostics.error("local_service_support", error);
                throw error;
            } finally {
                exitLocalDiscovery();
            }
        });

        module.hook(bindings.castSupport).intercept(chain -> {
            if (isLocalDiscoveryActive()) {
                diagnostics.trace(
                        "cast_support_local",
                        80,
                        "cast_support",
                        "device", diagnostics.identifier((String) chain.getArg(0)),
                        "scope", "local_prerequisite",
                        "decision", "override_true");
                return Boolean.TRUE;
            }
            try {
                Object result = chain.proceed();
                diagnostics.trace(
                        "cast_support_remote",
                        200,
                        "cast_support",
                        "device", diagnostics.identifier((String) chain.getArg(0)),
                        "scope", "remote_device",
                        "result", diagnostics.summarize(result));
                return result;
            } catch (Throwable error) {
                diagnostics.error(
                        "cast_support",
                        error,
                        "scope", "remote_device",
                        "device", diagnostics.identifier((String) chain.getArg(0)));
                throw error;
            }
        });
    }

    /**
     * Converts one notification click into one native PIN_APP transaction and consumes MiLink's
     * immediately following duplicate passive-stream command on the same worker thread.
     */
    private void installClickHooks(Bindings bindings) {
        module.hook(bindings.publishNotification).intercept(chain -> {
            String deviceId = (String) chain.getArg(0);
            Object message = chain.getArg(1);
            String packageName;
            try {
                packageName = (String) bindings.getPackageName.invoke(message);
            } catch (Throwable error) {
                clickSession.clear();
                module.log(Log.WARN, TAG,
                        "Could not correlate the follow-up notification command",
                        error);
                return chain.proceed();
            }

            if (!clickSession.consumeMatching(
                    deviceId,
                    packageName,
                    SystemClock.elapsedRealtime())) {
                return chain.proceed();
            }

            diagnostics.event(
                    "notification_duplicate_consumed",
                    "device", diagnostics.identifier(deviceId),
                    "package", diagnostics.identifier(packageName));
            notifyPublishSuccess(chain.getArg(2), bindings.publishResult);
            module.log(Log.INFO, TAG,
                    "Consumed the duplicate passive notification stream request");
            return null;
        });

        module.hook(bindings.launchNotificationSink).intercept(chain -> {
            Object contextValue = chain.getArg(0);
            String deviceId = (String) chain.getArg(1);
            String packageName = (String) chain.getArg(2);
            if (!(contextValue instanceof Context context)
                    || !CompatibilityPolicy.isValidNotificationClick(deviceId, packageName)) {
                return chain.proceed();
            }

            boolean dispatched = dispatchPinApp(context, deviceId, packageName);
            diagnostics.event(
                    "notification_click",
                    "device", diagnostics.identifier(deviceId),
                    "package", diagnostics.identifier(packageName),
                    "pinAppDispatched", Boolean.toString(dispatched));
            // The stock worker calls handleNotification even when this callback reports failure.
            // Consume that paired command so a failed active request cannot create a second stream.
            clickSession.begin(deviceId, packageName, SystemClock.elapsedRealtime());
            notifyRelayResult(
                    chain.getArg(3),
                    dispatched,
                    bindings.relaySuccess,
                    bindings.relayFailure);
            if (dispatched) {
                module.log(Log.INFO, TAG,
                        "Notification click dispatched through the stock PIN_APP flow");
            }
            return null;
        });
    }

    private boolean dispatchPinApp(Context context, String deviceId, String packageName) {
        try {
            Bundle extras = new Bundle();
            extras.putString("remoteDeviceId", deviceId);
            extras.putString("package", packageName);
            Bundle result = context.getContentResolver().call(
                    MIRROR_PROVIDER_AUTHORITY,
                    CompatibilityPolicy.PIN_APP_PROVIDER_METHOD,
                    null,
                    extras);
            if (result != null) {
                diagnostics.event("pin_app_provider", "result", "non_null");
                return true;
            }
            diagnostics.event("pin_app_provider", "result", "null");
            module.log(Log.ERROR, TAG, "PIN_APP provider returned no result");
        } catch (Throwable error) {
            diagnostics.error("pin_app_provider", error);
            module.log(Log.ERROR, TAG, "Could not dispatch notification through PIN_APP", error);
        }
        return false;
    }

    private void notifyRelayResult(
            Object callback,
            boolean success,
            Method onSuccess,
            Method onFailure) {
        if (callback == null) return;
        try {
            if (success) {
                onSuccess.invoke(callback);
            } else {
                onFailure.invoke(callback, -1);
            }
        } catch (Throwable error) {
            module.log(Log.WARN, TAG, "Could not report the PIN_APP dispatch result", error);
        }
    }

    private void notifyPublishSuccess(Object callback, Method onResult) {
        if (callback == null) return;
        try {
            onResult.invoke(callback, 1, null);
        } catch (Throwable error) {
            module.log(Log.WARN, TAG,
                    "Could not acknowledge the consumed notification command",
                    error);
        }
    }

    private void enterLocalDiscovery() {
        Integer depth = localDiscoveryDepth.get();
        localDiscoveryDepth.set(depth == null ? 1 : depth + 1);
    }

    private void exitLocalDiscovery() {
        Integer depth = localDiscoveryDepth.get();
        if (depth == null || depth <= 1) {
            localDiscoveryDepth.remove();
        } else {
            localDiscoveryDepth.set(depth - 1);
        }
    }

    private boolean isLocalDiscoveryActive() {
        Integer depth = localDiscoveryDepth.get();
        return depth != null && depth > 0;
    }

    private static Method method(Class<?> owner, String name, Class<?>... parameters)
            throws NoSuchMethodException {
        Method method = owner.getDeclaredMethod(name, parameters);
        method.setAccessible(true);
        return method;
    }

    /** Immutable reflection bindings resolved before any Hook is registered. */
    private static final class Bindings {
        final Method localServiceSupport;
        final Method castSupport;
        final Method launchNotificationSink;
        final Method relaySuccess;
        final Method relayFailure;
        final Method publishNotification;
        final Method getPackageName;
        final Method publishResult;

        private Bindings(
                Method localServiceSupport,
                Method castSupport,
                Method launchNotificationSink,
                Method relaySuccess,
                Method relayFailure,
                Method publishNotification,
                Method getPackageName,
                Method publishResult) {
            this.localServiceSupport = localServiceSupport;
            this.castSupport = castSupport;
            this.launchNotificationSink = launchNotificationSink;
            this.relaySuccess = relaySuccess;
            this.relayFailure = relayFailure;
            this.publishNotification = publishNotification;
            this.getPackageName = getPackageName;
            this.publishResult = publishResult;
        }

        static Bindings resolve(ClassLoader classLoader) throws ReflectiveOperationException {
            Class<?> discoverClass = classLoader.loadClass(DISCOVER_CLASS);
            Class<?> serviceNameClass = classLoader.loadClass(CONTINUITY_SERVICE_NAME_CLASS);
            Class<?> synergySdkClass = classLoader.loadClass(MIUI_SYNERGY_SDK_CLASS);
            Class<?> relayCallbackClass = classLoader.loadClass(RELAY_APP_CALLBACK_CLASS);
            Class<?> transClientClass = classLoader.loadClass(NOTIFICATION_TRANS_CLIENT_CLASS);
            Class<?> notificationMessageClass = classLoader.loadClass(NOTIFICATION_MESSAGE_CLASS);
            Class<?> transCallbackClass = classLoader.loadClass(
                    NOTIFICATION_TRANS_CALLBACK_CLASS);

            return new Bindings(
                    method(discoverClass, "isLocalServiceDataSupport", serviceNameClass),
                    method(discoverClass, "isCastServiceSupport", String.class),
                    method(
                            synergySdkClass,
                            "launchAppFromPendingIntentSink",
                            Context.class,
                            String.class,
                            String.class,
                            relayCallbackClass),
                    method(relayCallbackClass, "onSuccess"),
                    method(relayCallbackClass, "onFailure", int.class),
                    method(
                            transClientClass,
                            "handleNotification",
                            String.class,
                            notificationMessageClass,
                            transCallbackClass),
                    method(notificationMessageClass, "getPackageName"),
                    method(transCallbackClass, "onResult", int.class, Object.class));
        }
    }
}
