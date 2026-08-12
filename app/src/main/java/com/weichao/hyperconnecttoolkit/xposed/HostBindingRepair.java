package com.weichao.hyperconnecttoolkit.xposed;

import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;

/** Replays MiLink's official bind sequence only after its own HostNotBound result. */
final class HostBindingRepair {
    static final int SUCCESS = 100;
    static final int HOST_NOT_BOUND = 215;
    private static final String DISCOVERY_HOST =
            "com.miui.headset.runtime.DiscoveryHost";
    private static final String DISCOVERY_PROXY =
            "com.miui.headset.runtime.DiscoveryProxy";
    private static final String MULTIPLATFORM_PROCESSOR =
            "com.miui.headset.runtime.MultiplatformProcessor";
    private static final String COMPATIBILITY_EXTRA =
            "com.miui.headset.runtime.CompatibilityExtra";

    private final Field headsetDiscoveriesField;
    private final Field boundResultField;
    private final Method getProxyMethod;
    private final Method doBindMethod;
    private final Method bindStatusMethod;
    private final Method compatibilityMethod;
    private final Object multiplatformProcessor;
    private final Method overrideMethod;
    private final Method startDiscoveryMethod;
    private volatile WeakReference<Object> host = new WeakReference<>(null);

    private HostBindingRepair(
            Class<?> discoveryHostClass,
            Class<?> discoveryProxyClass,
            Class<?> multiplatformProcessorClass,
            Class<?> compatibilityExtraClass) throws ReflectiveOperationException {
        headsetDiscoveriesField = field(discoveryHostClass, "headsetDiscoveries");
        boundResultField = field(discoveryProxyClass, "boundResult");
        getProxyMethod = method(discoveryProxyClass, "getProxy");
        startDiscoveryMethod = method(discoveryProxyClass, "startDiscovery");

        Class<?> remoteProxyClass = getProxyMethod.getReturnType();
        doBindMethod = method(remoteProxyClass, "doBind", String.class);
        Class<?> bindResultClass = doBindMethod.getReturnType();
        bindStatusMethod = method(bindResultClass, "component1");
        compatibilityMethod = method(bindResultClass, "component2");

        multiplatformProcessor = field(multiplatformProcessorClass, "INSTANCE").get(null);
        overrideMethod = method(
                multiplatformProcessorClass,
                "override",
                String.class,
                boolean.class,
                compatibilityExtraClass);
    }

    static HostBindingRepair resolve(ClassLoader classLoader)
            throws ReflectiveOperationException {
        return resolve(
                Class.forName(DISCOVERY_HOST, false, classLoader),
                Class.forName(DISCOVERY_PROXY, false, classLoader),
                Class.forName(MULTIPLATFORM_PROCESSOR, false, classLoader),
                Class.forName(COMPATIBILITY_EXTRA, false, classLoader));
    }

    static HostBindingRepair resolve(
            Class<?> discoveryHostClass,
            Class<?> discoveryProxyClass,
            Class<?> multiplatformProcessorClass,
            Class<?> compatibilityExtraClass) throws ReflectiveOperationException {
        return new HostBindingRepair(
                discoveryHostClass,
                discoveryProxyClass,
                multiplatformProcessorClass,
                compatibilityExtraClass);
    }

    void attach(Object discoveryHost) {
        host = new WeakReference<>(discoveryHost);
    }

    boolean bind(Object hostIdValue) throws ReflectiveOperationException {
        if (!(hostIdValue instanceof String hostId) || hostId.isBlank()) {
            return false;
        }
        Object discoveryHost = host.get();
        if (discoveryHost == null) {
            return false;
        }
        if (!(headsetDiscoveriesField.get(discoveryHost) instanceof Map<?, ?> discoveries)) {
            return false;
        }
        Object proxy = discoveries.get(hostId);
        if (proxy == null) {
            return false;
        }
        Object remoteProxy = getProxyMethod.invoke(proxy);
        if (remoteProxy == null) {
            return false;
        }

        synchronized (remoteProxy) {
            if (discoveries.get(hostId) != proxy || boundResultField.getBoolean(proxy)) {
                return false;
            }
            Object bindResult = doBindMethod.invoke(remoteProxy, hostId);
            Object statusValue = bindStatusMethod.invoke(bindResult);
            if (!(statusValue instanceof Number status) || status.intValue() != SUCCESS) {
                return false;
            }
            overrideMethod.invoke(
                    multiplatformProcessor,
                    hostId,
                    true,
                    compatibilityMethod.invoke(bindResult));
            boundResultField.setBoolean(proxy, true);
            startDiscoveryMethod.invoke(proxy);
        }
        return true;
    }

    private static Field field(Class<?> owner, String name) throws NoSuchFieldException {
        Field field = owner.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    private static Method method(Class<?> owner, String name, Class<?>... parameters)
            throws NoSuchMethodException {
        Method method = owner.getDeclaredMethod(name, parameters);
        method.setAccessible(true);
        return method;
    }
}
