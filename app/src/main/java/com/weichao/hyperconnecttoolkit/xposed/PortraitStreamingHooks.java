package com.weichao.hyperconnecttoolkit.xposed;

import android.app.Activity;
import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.util.Log;

import java.lang.reflect.Method;
import java.util.function.Supplier;

import io.github.libxposed.api.XposedModule;

/** Xiaomi Mirror portrait-streaming feature split across package-load lifecycle phases. */
final class PortraitStreamingHooks {
    private static final String TAG = "HyperConnectToolkit";
    private static final String REQUEST_APP_SCREEN_MESSAGE_CLASS =
            "com.xiaomi.mirror.message.RequestAppScreenMessage";
    private static final String TABLET_SINK_ACTIVITY_CLASS = "com.xiaomi.mirror.sink.g";

    private final XposedModule module;
    private final Supplier<Context> applicationContext;
    private boolean geometryInstalled;
    private boolean orientationInstalled;

    PortraitStreamingHooks(XposedModule module, Supplier<Context> applicationContext) {
        this.module = module;
        this.applicationContext = applicationContext;
    }

    /** Installs the request-factory Hook available during package loading. */
    boolean installGeometry(ClassLoader classLoader) {
        if (geometryInstalled) return true;
        try {
            Class<?> messageClass = classLoader.loadClass(REQUEST_APP_SCREEN_MESSAGE_CLASS);
            Method openMessageFactory = messageClass.getDeclaredMethod(
                    "getRequestAppScreenOpenMessage",
                    String.class,
                    int.class,
                    int.class,
                    int.class,
                    int.class,
                    int.class,
                    int.class,
                    int.class);
            openMessageFactory.setAccessible(true);

            module.hook(openMessageFactory).intercept(chain -> {
                int width = (int) chain.getArg(2);
                int height = (int) chain.getArg(3);
                int screenFrom = (int) chain.getArg(6);
                if (!CompatibilityPolicy.shouldRestorePortraitInitialBounds(
                        currentOrientation(),
                        screenFrom,
                        width,
                        height)) {
                    return chain.proceed();
                }

                Object[] args = chain.getArgs().toArray();
                args[2] = height;
                args[3] = width;
                module.log(Log.INFO, TAG,
                        "Restored portrait PIN_APP bounds: "
                                + width + "x" + height + " -> " + height + "x" + width);
                return chain.proceed(args);
            });

            geometryInstalled = true;
            module.log(Log.INFO, TAG, "Installed initial app-screen geometry hook");
            return true;
        } catch (Throwable error) {
            module.log(Log.ERROR, TAG, "Initial app-screen geometry hook is incompatible", error);
            return false;
        }
    }

    /** Installs the Activity Hook after the target package's classes become ready. */
    boolean installOrientation(ClassLoader classLoader) {
        if (orientationInstalled) return true;
        try {
            Class<?> sinkActivityClass = Class.forName(
                    TABLET_SINK_ACTIVITY_CLASS,
                    false,
                    classLoader);
            Method setRequestedOrientation = Activity.class.getDeclaredMethod(
                    "setRequestedOrientation",
                    int.class);
            module.hook(setRequestedOrientation).intercept(chain -> {
                int requested = (int) chain.getArg(0);
                int resolved = CompatibilityPolicy.resolveRequestedOrientation(
                        sinkActivityClass.isInstance(chain.getThisObject()),
                        requested);
                return resolved == requested
                        ? chain.proceed()
                        : chain.proceed(new Object[]{resolved});
            });

            orientationInstalled = true;
            module.log(Log.INFO, TAG, "Installed scoped sink-orientation hook");
            return true;
        } catch (Throwable error) {
            module.log(Log.ERROR, TAG, "Sink-orientation hook is incompatible", error);
            return false;
        }
    }

    boolean isComplete() {
        return geometryInstalled && orientationInstalled;
    }

    private int currentOrientation() {
        try {
            Context context = applicationContext.get();
            Resources resources = context != null ? context.getResources() : Resources.getSystem();
            return resources.getConfiguration().orientation;
        } catch (Throwable error) {
            module.log(Log.WARN, TAG, "Could not read Xiaomi Mirror orientation", error);
            return Configuration.ORIENTATION_UNDEFINED;
        }
    }
}
