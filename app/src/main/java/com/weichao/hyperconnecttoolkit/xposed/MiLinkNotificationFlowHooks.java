package com.weichao.hyperconnecttoolkit.xposed;

import android.content.Context;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.Log;

import java.lang.reflect.Method;

import io.github.libxposed.api.XposedModule;

/** Notification-click circulation compatibility owned by MiLink's cross-device process. */
final class MiLinkNotificationFlowHooks {
    private static final String TAG = "HyperConnectToolkit";
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
    private final NotificationClickSession clickSession = new NotificationClickSession();

    MiLinkNotificationFlowHooks(XposedModule module) {
        this.module = module;
    }

    /** Resolves the complete click-flow contract before registering either functional Hook. */
    boolean install(ClassLoader classLoader) {
        try {
            Bindings bindings = Bindings.resolve(classLoader);
            installHooks(bindings);
            module.log(Log.INFO, TAG, "Installed MiLink notification flow hooks");
            return true;
        } catch (Throwable error) {
            module.log(Log.ERROR, TAG,
                    "MiLink notification flow hooks are incompatible",
                    error);
            return false;
        }
    }

    /**
     * Converts one notification click into one native PIN_APP transaction and consumes MiLink's
     * immediately following duplicate passive-stream command on the same worker thread.
     */
    private void installHooks(Bindings bindings) {
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
            // Stock still calls handleNotification after this callback; consume that paired command.
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
                return true;
            }
            module.log(Log.ERROR, TAG, "PIN_APP provider returned no result");
        } catch (Throwable error) {
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

    private static Method method(Class<?> owner, String name, Class<?>... parameters)
            throws NoSuchMethodException {
        Method method = owner.getDeclaredMethod(name, parameters);
        method.setAccessible(true);
        return method;
    }

    private static final class Bindings {
        final Method launchNotificationSink;
        final Method relaySuccess;
        final Method relayFailure;
        final Method publishNotification;
        final Method getPackageName;
        final Method publishResult;

        private Bindings(
                Method launchNotificationSink,
                Method relaySuccess,
                Method relayFailure,
                Method publishNotification,
                Method getPackageName,
                Method publishResult) {
            this.launchNotificationSink = launchNotificationSink;
            this.relaySuccess = relaySuccess;
            this.relayFailure = relayFailure;
            this.publishNotification = publishNotification;
            this.getPackageName = getPackageName;
            this.publishResult = publishResult;
        }

        static Bindings resolve(ClassLoader classLoader) throws ReflectiveOperationException {
            Class<?> synergySdkClass = classLoader.loadClass(MIUI_SYNERGY_SDK_CLASS);
            Class<?> relayCallbackClass = classLoader.loadClass(RELAY_APP_CALLBACK_CLASS);
            Class<?> transClientClass = classLoader.loadClass(NOTIFICATION_TRANS_CLIENT_CLASS);
            Class<?> notificationMessageClass = classLoader.loadClass(NOTIFICATION_MESSAGE_CLASS);
            Class<?> transCallbackClass = classLoader.loadClass(
                    NOTIFICATION_TRANS_CALLBACK_CLASS);

            return new Bindings(
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
