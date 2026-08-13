package com.weichao.hyperconnecttoolkit.xposed;

import android.util.Log;

import java.lang.reflect.Method;

import io.github.libxposed.api.XposedModule;

/** Notification-device discovery compatibility owned by MiLink's UI process. */
final class MiLinkDiscoveryHooks {
    private static final String TAG = "HyperConnectToolkit";
    private static final String DISCOVER_CLASS =
            "com.xiaomi.dist.notification.discover.LyraDiscover";
    private static final String CONTINUITY_SERVICE_NAME_CLASS =
            "com.xiaomi.continuity.ServiceName";

    private final XposedModule module;
    private final ThreadLocal<Integer> localDiscoveryDepth = new ThreadLocal<>();

    MiLinkDiscoveryHooks(XposedModule module) {
        this.module = module;
    }

    /** Resolves the complete discovery contract before registering either functional Hook. */
    boolean install(ClassLoader classLoader) {
        try {
            Bindings bindings = Bindings.resolve(classLoader);
            installHooks(bindings);
            module.log(Log.INFO, TAG, "Installed MiLink notification discovery hooks");
            return true;
        } catch (Throwable error) {
            module.log(Log.ERROR, TAG,
                    "MiLink notification discovery hooks are incompatible",
                    error);
            return false;
        }
    }

    /**
     * Bypasses the Cast bit only while MiLink evaluates its synchronous local prerequisite.
     * Remote-device capability negotiation continues through the stock implementation.
     */
    private void installHooks(Bindings bindings) {
        module.hook(bindings.localServiceSupport).intercept(chain -> {
            enterLocalDiscovery();
            try {
                return chain.proceed();
            } finally {
                exitLocalDiscovery();
            }
        });

        module.hook(bindings.castSupport).intercept(chain -> {
            if (isLocalDiscoveryActive()) {
                return Boolean.TRUE;
            }
            return chain.proceed();
        });
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

    private static final class Bindings {
        final Method localServiceSupport;
        final Method castSupport;

        private Bindings(Method localServiceSupport, Method castSupport) {
            this.localServiceSupport = localServiceSupport;
            this.castSupport = castSupport;
        }

        static Bindings resolve(ClassLoader classLoader) throws ReflectiveOperationException {
            Class<?> discoverClass = classLoader.loadClass(DISCOVER_CLASS);
            Class<?> serviceNameClass = classLoader.loadClass(CONTINUITY_SERVICE_NAME_CLASS);
            return new Bindings(
                    method(discoverClass, "isLocalServiceDataSupport", serviceNameClass),
                    method(discoverClass, "isCastServiceSupport", String.class));
        }
    }
}
