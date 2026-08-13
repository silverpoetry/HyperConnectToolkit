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
    private final DiagnosticLogger diagnostics;
    private final ThreadLocal<Integer> localDiscoveryDepth = new ThreadLocal<>();

    MiLinkDiscoveryHooks(XposedModule module, DiagnosticLogger diagnostics) {
        this.module = module;
        this.diagnostics = diagnostics;
    }

    /** Resolves the complete discovery contract before registering either functional Hook. */
    boolean install(ClassLoader classLoader) {
        try {
            Bindings bindings = Bindings.resolve(classLoader);
            installHooks(bindings);
            diagnostics.event("functional_hooks", "group", "discovery", "state", "installed");
            module.log(Log.INFO, TAG, "Installed MiLink notification discovery hooks");
            return true;
        } catch (Throwable error) {
            diagnostics.error(
                    "functional_hooks",
                    error,
                    "group", "discovery",
                    "state", "incompatible");
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
