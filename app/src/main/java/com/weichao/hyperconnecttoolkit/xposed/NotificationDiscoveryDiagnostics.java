package com.weichao.hyperconnecttoolkit.xposed;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Set;
import java.util.Map;

import io.github.libxposed.api.XposedModule;

/**
 * Diagnostic-only observation of MiLink's complete notification-discovery decision chain.
 *
 * <p>The observer records booleans, lengths, types and session-scoped hashes only. It never logs
 * notification data, account names, network addresses or raw Xiaomi device identifiers.</p>
 */
final class NotificationDiscoveryDiagnostics {
    private static final String DISCOVER_CLASS =
            "com.xiaomi.dist.notification.discover.LyraDiscover";
    private static final String SERVICE_NAME_CLASS = "com.xiaomi.continuity.ServiceName";
    private static final String TRUSTED_DEVICE_CLASS =
            "com.xiaomi.continuity.networking.TrustedDeviceInfo";
    private static final String BUSINESS_SERVICE_CLASS =
            "com.xiaomi.continuity.networking.BusinessServiceInfo";
    private static final String SERVICE_LISTENER_CLASS =
            "com.xiaomi.dist.notification.discover.LyraDiscover$NotifiServiceListener";
    private static final String DEVICE_LISTENER_CLASS =
            "com.xiaomi.dist.notification.discover.LyraDiscover$LyraDeviceListener";

    private final XposedModule module;
    private final DiagnosticLogger logger;

    NotificationDiscoveryDiagnostics(XposedModule module, DiagnosticLogger logger) {
        this.module = module;
        this.logger = logger;
    }

    void install(ClassLoader classLoader, boolean functionalDiscoveryInstalled) {
        if (!logger.isEnabled()) return;
        try {
            Bindings bindings = Bindings.resolve(classLoader);
            hookLifecycle(bindings);
            hookCandidates(bindings);
            hookServiceCallbacks(bindings);
            hookLocalPrerequisite(bindings);
            hookCastPrerequisite(bindings);
            logger.event(
                    "discovery_diagnostics",
                    "state", "installed",
                    "functionalDiscovery", Boolean.toString(functionalDiscoveryInstalled));
        } catch (Throwable error) {
            logger.error("discovery_diagnostics", error, "state", "incompatible");
            int fallbackHooks = installFallback(classLoader);
            logger.event(
                    "discovery_diagnostics",
                    "state", fallbackHooks > 0 ? "fallback_installed" : "unavailable",
                    "fallbackHooks", Integer.toString(fallbackHooks));
        }
    }

    /**
     * Version-drift fallback. It observes only named semantic entry points and records shapes,
     * never values, so a changed private field or parameter class cannot erase the whole trace.
     */
    private int installFallback(ClassLoader classLoader) {
        int installed = 0;
        installed += installFallbackMethods(classLoader, DISCOVER_CLASS, Set.of(
                "init",
                "startDiscovery",
                "stopDiscovery",
                "getDeviceTypeFilter",
                "isLocalServiceDataSupport",
                "isCastServiceSupport",
                "addOrUpdateDevice"));
        installed += installFallbackMethods(classLoader, SERVICE_LISTENER_CLASS, Set.of(
                "onDeviceChanged",
                "onServiceChanged",
                "onServiceOffline",
                "onServiceOnline"));
        return installed;
    }

    private int installFallbackMethods(
            ClassLoader classLoader,
            String className,
            Set<String> methodNames) {
        try {
            Class<?> type = classLoader.loadClass(className);
            int installed = 0;
            for (Method method : type.getDeclaredMethods()) {
                if (!methodNames.contains(method.getName()) || method.isSynthetic()) continue;
                method.setAccessible(true);
                module.hook(method).intercept(chain -> {
                    String arguments = summarizeArguments(chain.getArgs().toArray());
                    try {
                        Object result = chain.proceed();
                        logger.trace(
                                "fallback:" + className + '#' + method.getName(),
                                160,
                                "fallback_call",
                                "owner", className,
                                "method", method.getName(),
                                "arguments", arguments,
                                "result", logger.summarize(result));
                        return result;
                    } catch (Throwable error) {
                        logger.error(
                                "fallback_call",
                                error,
                                "owner", className,
                                "method", method.getName(),
                                "arguments", arguments);
                        throw error;
                    }
                });
                installed++;
            }
            return installed;
        } catch (Throwable error) {
            logger.error(
                    "fallback_binding",
                    error,
                    "owner", className);
            return 0;
        }
    }

    private String summarizeArguments(Object[] arguments) {
        if (arguments.length == 0) return "none";
        StringBuilder summary = new StringBuilder();
        int limit = Math.min(arguments.length, 8);
        for (int index = 0; index < limit; index++) {
            if (index > 0) summary.append(',');
            summary.append(index).append(':').append(logger.summarize(arguments[index]));
        }
        if (arguments.length > limit) summary.append(",remaining=").append(arguments.length - limit);
        return summary.toString();
    }

    private void hookLifecycle(Bindings bindings) {
        module.hook(bindings.init).intercept(chain -> {
            logger.trace(
                    "discover_init",
                    20,
                    "discover_init",
                    "listener", logger.summarize(chain.getArg(0)),
                    "handler", logger.summarize(chain.getArg(1)));
            return chain.proceed();
        });

        module.hook(bindings.startDiscovery).intercept(chain -> {
            boolean startedBefore = bindings.hasStarted.getBoolean(chain.getThisObject());
            try {
                Object result = chain.proceed();
                boolean startedAfter = bindings.hasStarted.getBoolean(chain.getThisObject());
                logger.trace(
                        "start_discovery",
                        40,
                        "start_discovery",
                        "startedBefore", Boolean.toString(startedBefore),
                        "startedAfter", Boolean.toString(startedAfter),
                        "listenerRegistered", Boolean.toString(
                                bindings.serviceListener.get(chain.getThisObject()) != null));
                return result;
            } catch (Throwable error) {
                logger.error(
                        "start_discovery",
                        error,
                        "startedBefore", Boolean.toString(startedBefore));
                throw error;
            }
        });

        module.hook(bindings.stopDiscovery).intercept(chain -> {
            boolean startedBefore = bindings.hasStarted.getBoolean(chain.getThisObject());
            try {
                Object result = chain.proceed();
                logger.trace(
                        "stop_discovery",
                        20,
                        "stop_discovery",
                        "startedBefore", Boolean.toString(startedBefore),
                        "startedAfter", Boolean.toString(
                                bindings.hasStarted.getBoolean(chain.getThisObject())));
                return result;
            } catch (Throwable error) {
                logger.error("stop_discovery", error);
                throw error;
            }
        });

        module.hook(bindings.deviceTypeFilter).intercept(chain -> {
            Object result = chain.proceed();
            logger.trace(
                    "device_type_filter",
                    20,
                    "device_type_filter",
                    "result", logger.summarize(result));
            return result;
        });
    }

    private void hookCandidates(Bindings bindings) {
        module.hook(bindings.addOrUpdateDevice).intercept(chain -> {
            Object owner = chain.getThisObject();
            Object trustedDevice = chain.getArg(0);
            Object serviceInfo = chain.getArg(1);
            CandidateSnapshot before = bindings.inspectCandidate(
                    owner,
                    trustedDevice,
                    serviceInfo,
                    logger);
            try {
                Object result = chain.proceed();
                boolean accepted = bindings.containsCandidate(owner, before.rawDeviceId);
                logger.trace(
                        "candidate_evaluation",
                        240,
                        "candidate_evaluation",
                        "device", before.maskedDeviceId,
                        "deviceType", before.deviceType,
                        "serviceData", before.serviceData,
                        "miLinkBit4", before.miLinkBit4,
                        "region", before.region,
                        "deviceListener", Boolean.toString(
                                bindings.hasDeviceListener(owner)),
                        "accepted", Boolean.toString(accepted),
                        "knownDevices", Integer.toString(bindings.deviceCount(owner)));
                return result;
            } catch (Throwable error) {
                logger.error(
                        "candidate_evaluation",
                        error,
                        "device", before.maskedDeviceId,
                        "serviceData", before.serviceData);
                throw error;
            }
        });
    }

    private void hookServiceCallbacks(Bindings bindings) {
        for (Method callback : bindings.serviceCallbacks) {
            module.hook(callback).intercept(chain -> {
                CandidateSnapshot snapshot = bindings.inspectCallback(chain.getArgs().toArray(), logger);
                logger.trace(
                        "service_callback:" + callback.getName(),
                        240,
                        "service_callback",
                        "callback", callback.getName(),
                        "device", snapshot.maskedDeviceId,
                        "deviceType", snapshot.deviceType,
                        "serviceData", snapshot.serviceData,
                        "reason", bindings.numericArgument(chain.getArgs().toArray()));
                try {
                    return chain.proceed();
                } catch (Throwable error) {
                    logger.error(
                            "service_callback",
                            error,
                            "callback", callback.getName(),
                            "device", snapshot.maskedDeviceId);
                    throw error;
                }
            });
        }
    }

    private void hookLocalPrerequisite(Bindings bindings) {
        module.hook(bindings.localServiceSupport).intercept(chain -> {
            LocalSnapshot snapshot = bindings.inspectLocalPrerequisite(
                    chain.getThisObject(),
                    chain.getArg(0),
                    logger);
            try {
                Object result = chain.proceed();
                logger.trace(
                        "local_prerequisite_snapshot",
                        80,
                        "local_prerequisite",
                        "service", logger.summarize(chain.getArg(0)),
                        "localDevice", snapshot.localDevice,
                        "serviceInfo", snapshot.serviceInfo,
                        "serviceData", snapshot.serviceData,
                        "notificationBit5", snapshot.notificationBit5,
                        "result", logger.summarize(result));
                return result;
            } catch (Throwable error) {
                logger.error(
                        "local_prerequisite",
                        error,
                        "localDevice", snapshot.localDevice,
                        "serviceData", snapshot.serviceData);
                throw error;
            }
        });
    }

    private void hookCastPrerequisite(Bindings bindings) {
        module.hook(bindings.castSupport).intercept(chain -> {
            String deviceId = (String) chain.getArg(0);
            CastSnapshot snapshot = bindings.inspectCastPrerequisite(
                    chain.getThisObject(),
                    deviceId,
                    logger);
            try {
                Object result = chain.proceed();
                logger.trace(
                        "cast_prerequisite_snapshot",
                        200,
                        "cast_prerequisite",
                        "device", logger.identifier(deviceId),
                        "serviceInfo", snapshot.serviceInfo,
                        "serviceData", snapshot.serviceData,
                        "castBit5", snapshot.castBit5,
                        "result", logger.summarize(result));
                return result;
            } catch (Throwable error) {
                logger.error(
                        "cast_prerequisite",
                        error,
                        "device", logger.identifier(deviceId),
                        "serviceData", snapshot.serviceData);
                throw error;
            }
        });
    }

    private static Method method(Class<?> owner, String name, Class<?>... parameters)
            throws NoSuchMethodException {
        Method method = owner.getDeclaredMethod(name, parameters);
        method.setAccessible(true);
        return method;
    }

    private static Field field(Class<?> owner, String name) throws NoSuchFieldException {
        Field field = owner.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    private static final class CandidateSnapshot {
        final String rawDeviceId;
        final String maskedDeviceId;
        final String deviceType;
        final String serviceData;
        final String miLinkBit4;
        final String region;

        CandidateSnapshot(
                String rawDeviceId,
                String maskedDeviceId,
                String deviceType,
                String serviceData,
                String miLinkBit4,
                String region) {
            this.rawDeviceId = rawDeviceId;
            this.maskedDeviceId = maskedDeviceId;
            this.deviceType = deviceType;
            this.serviceData = serviceData;
            this.miLinkBit4 = miLinkBit4;
            this.region = region;
        }

        static CandidateSnapshot unknown() {
            return new CandidateSnapshot(null, "null", "unknown", "unknown", "unknown", "unknown");
        }
    }

    private static final class LocalSnapshot {
        final String localDevice;
        final String serviceInfo;
        final String serviceData;
        final String notificationBit5;

        LocalSnapshot(
                String localDevice,
                String serviceInfo,
                String serviceData,
                String notificationBit5) {
            this.localDevice = localDevice;
            this.serviceInfo = serviceInfo;
            this.serviceData = serviceData;
            this.notificationBit5 = notificationBit5;
        }

        static LocalSnapshot error(Throwable error) {
            return new LocalSnapshot(
                    "unavailable:" + error.getClass().getSimpleName(),
                    "unknown",
                    "unknown",
                    "unknown");
        }
    }

    private static final class CastSnapshot {
        final String serviceInfo;
        final String serviceData;
        final String castBit5;

        CastSnapshot(String serviceInfo, String serviceData, String castBit5) {
            this.serviceInfo = serviceInfo;
            this.serviceData = serviceData;
            this.castBit5 = castBit5;
        }

        static CastSnapshot error(Throwable error) {
            return new CastSnapshot(
                    "unavailable:" + error.getClass().getSimpleName(),
                    "unknown",
                    "unknown");
        }
    }

    private static final class Bindings {
        final Method init;
        final Method startDiscovery;
        final Method stopDiscovery;
        final Method deviceTypeFilter;
        final Method addOrUpdateDevice;
        final Method localServiceSupport;
        final Method castSupport;
        final Method trustedDeviceId;
        final Method trustedDeviceType;
        final Method serviceData;
        final Field hasStarted;
        final Field serviceListener;
        final Field deviceListener;
        final Field devices;
        final Field cachedServiceData;
        final Method[] serviceCallbacks;

        private Bindings(
                Method init,
                Method startDiscovery,
                Method stopDiscovery,
                Method deviceTypeFilter,
                Method addOrUpdateDevice,
                Method localServiceSupport,
                Method castSupport,
                Method trustedDeviceId,
                Method trustedDeviceType,
                Method serviceData,
                Field hasStarted,
                Field serviceListener,
                Field deviceListener,
                Field devices,
                Field cachedServiceData,
                Method[] serviceCallbacks) {
            this.init = init;
            this.startDiscovery = startDiscovery;
            this.stopDiscovery = stopDiscovery;
            this.deviceTypeFilter = deviceTypeFilter;
            this.addOrUpdateDevice = addOrUpdateDevice;
            this.localServiceSupport = localServiceSupport;
            this.castSupport = castSupport;
            this.trustedDeviceId = trustedDeviceId;
            this.trustedDeviceType = trustedDeviceType;
            this.serviceData = serviceData;
            this.hasStarted = hasStarted;
            this.serviceListener = serviceListener;
            this.deviceListener = deviceListener;
            this.devices = devices;
            this.cachedServiceData = cachedServiceData;
            this.serviceCallbacks = serviceCallbacks;
        }

        static Bindings resolve(ClassLoader classLoader) throws ReflectiveOperationException {
            Class<?> discover = classLoader.loadClass(DISCOVER_CLASS);
            Class<?> serviceName = classLoader.loadClass(SERVICE_NAME_CLASS);
            Class<?> trustedDevice = classLoader.loadClass(TRUSTED_DEVICE_CLASS);
            Class<?> businessService = classLoader.loadClass(BUSINESS_SERVICE_CLASS);
            Class<?> serviceListener = classLoader.loadClass(SERVICE_LISTENER_CLASS);
            Class<?> deviceListener = classLoader.loadClass(DEVICE_LISTENER_CLASS);
            Method[] callbacks = java.util.Arrays.stream(serviceListener.getDeclaredMethods())
                    .filter(candidate -> candidate.getName().equals("onDeviceChanged")
                            || candidate.getName().equals("onServiceChanged")
                            || candidate.getName().equals("onServiceOffline")
                            || candidate.getName().equals("onServiceOnline"))
                    .peek(candidate -> candidate.setAccessible(true))
                    .toArray(Method[]::new);
            if (callbacks.length != 4) {
                throw new NoSuchMethodException(
                        SERVICE_LISTENER_CLASS + " expected four discovery callbacks, found "
                                + callbacks.length);
            }
            return new Bindings(
                    method(discover, "init", deviceListener, android.os.Handler.class),
                    method(discover, "startDiscovery"),
                    method(discover, "stopDiscovery"),
                    method(discover, "getDeviceTypeFilter"),
                    method(discover, "addOrUpdateDevice", trustedDevice, businessService),
                    method(discover, "isLocalServiceDataSupport", serviceName),
                    method(discover, "isCastServiceSupport", String.class),
                    method(trustedDevice, "getDeviceId"),
                    method(trustedDevice, "getDeviceType"),
                    method(businessService, "getServiceData"),
                    field(discover, "mHasStarDiscovered"),
                    field(discover, "mNotifiServiceListener"),
                    field(discover, "mLyraDeviceListener"),
                    field(discover, "mNotifiDeivces"),
                    field(discover, "mDeviceDataList"),
                    callbacks);
        }

        CandidateSnapshot inspectCandidate(
                Object owner,
                Object trustedDevice,
                Object businessService,
                DiagnosticLogger logger) {
            try {
                String deviceId = trustedDevice == null
                        ? null
                        : (String) trustedDeviceId.invoke(trustedDevice);
                Object type = trustedDevice == null ? null : trustedDeviceType.invoke(trustedDevice);
                byte[] data = businessService == null
                        ? cachedData(owner, deviceId)
                        : (byte[]) serviceData.invoke(businessService);
                if ((data == null || data.length == 0) && deviceId != null) {
                    data = cachedData(owner, deviceId);
                }
                return new CandidateSnapshot(
                        deviceId,
                        logger.identifier(deviceId),
                        logger.summarize(type),
                        data == null ? "null" : "bytes(len=" + data.length + ")",
                        data == null || data.length == 0
                                ? "unavailable"
                                : Boolean.toString((data[0] & (1 << 4)) != 0),
                        readRegion(owner, deviceId));
            } catch (Throwable error) {
                logger.error("candidate_snapshot", error);
                return CandidateSnapshot.unknown();
            }
        }

        LocalSnapshot inspectLocalPrerequisite(
                Object owner,
                Object serviceName,
                DiagnosticLogger logger) {
            try {
                Field networkingField = field(owner.getClass(), "mNetworkingManager");
                Object networking = networkingField.get(owner);
                Method getLocalDeviceInfo = method(networking.getClass(), "getLocalDeviceInfo");
                Object localDevice = getLocalDeviceInfo.invoke(networking);
                if (localDevice == null) {
                    return new LocalSnapshot("null", "not_queried", "not_queried", "unavailable");
                }
                String deviceId = (String) trustedDeviceId.invoke(localDevice);
                Method getServiceInfo = method(
                        networking.getClass(),
                        "getServiceInfo",
                        String.class,
                        serviceName.getClass());
                Object info = getServiceInfo.invoke(networking, deviceId, serviceName);
                byte[] data = info == null ? null : (byte[]) serviceData.invoke(info);
                return new LocalSnapshot(
                        logger.identifier(deviceId),
                        info == null ? "null" : "present",
                        data == null ? "null" : "bytes(len=" + data.length + ")",
                        data == null || data.length == 0
                                ? "unavailable"
                                : Boolean.toString((data[0] & (1 << 5)) != 0));
            } catch (Throwable error) {
                logger.error("local_prerequisite_snapshot", error);
                return LocalSnapshot.error(error);
            }
        }

        CastSnapshot inspectCastPrerequisite(
                Object owner,
                String deviceId,
                DiagnosticLogger logger) {
            try {
                Field networkingField = field(owner.getClass(), "mNetworkingManager");
                Object networking = networkingField.get(owner);
                ClassLoader classLoader = networking.getClass().getClassLoader();
                Class<?> serviceName = classLoader.loadClass(SERVICE_NAME_CLASS);
                Class<?> builderType = classLoader.loadClass(SERVICE_NAME_CLASS + "$Builder");
                Object builder = builderType.getDeclaredConstructor().newInstance();
                method(builderType, "setName", String.class).invoke(builder, "cast");
                method(builderType, "setPackageName", String.class)
                        .invoke(builder, "com.xiaomi.mirror");
                Object castService = method(builderType, "build").invoke(builder);
                Method getServiceInfo = method(
                        networking.getClass(),
                        "getServiceInfo",
                        String.class,
                        serviceName);
                Object info = getServiceInfo.invoke(networking, deviceId, castService);
                byte[] data = info == null ? null : (byte[]) serviceData.invoke(info);
                return new CastSnapshot(
                        info == null ? "null" : "present",
                        data == null ? "null" : "bytes(len=" + data.length + ")",
                        data == null || data.length < 4
                                ? "unavailable"
                                : Boolean.toString((data[3] & (1 << 5)) != 0));
            } catch (Throwable error) {
                logger.error("cast_prerequisite_snapshot", error);
                return CastSnapshot.error(error);
            }
        }

        CandidateSnapshot inspectCallback(Object[] arguments, DiagnosticLogger logger) {
            Object trusted = null;
            Object service = null;
            for (Object argument : arguments) {
                if (argument == null) continue;
                if (TRUSTED_DEVICE_CLASS.equals(argument.getClass().getName())) trusted = argument;
                if (BUSINESS_SERVICE_CLASS.equals(argument.getClass().getName())) service = argument;
            }
            return inspectCandidate(null, trusted, service, logger);
        }

        @SuppressWarnings("unchecked")
        private byte[] cachedData(Object owner, String deviceId) throws IllegalAccessException {
            if (owner == null || deviceId == null) return null;
            return ((Map<String, byte[]>) cachedServiceData.get(owner)).get(deviceId);
        }

        @SuppressWarnings("unchecked")
        boolean containsCandidate(Object owner, String deviceId) throws IllegalAccessException {
            return owner != null && deviceId != null
                    && ((Map<String, ?>) devices.get(owner)).containsKey(deviceId);
        }

        @SuppressWarnings("unchecked")
        int deviceCount(Object owner) throws IllegalAccessException {
            return owner == null ? -1 : ((Map<String, ?>) devices.get(owner)).size();
        }

        boolean hasDeviceListener(Object owner) throws IllegalAccessException {
            return owner != null && deviceListener.get(owner) != null;
        }

        String numericArgument(Object[] arguments) {
            for (Object argument : arguments) {
                if (argument instanceof Number number) return number.toString();
            }
            return "none";
        }

        private String readRegion(Object owner, String deviceId) {
            if (owner == null || deviceId == null) return "unavailable";
            try {
                Field networkingField = field(owner.getClass(), "mNetworkingManager");
                Object networking = networkingField.get(owner);
                Class<?> propertyType = networking.getClass().getClassLoader().loadClass(
                        "com.xiaomi.continuity.networking.PropertyType");
                Object regionProperty = propertyType.getField("PropBuildRegion").get(null);
                Method toInteger = method(propertyType, "toInteger");
                int property = ((Number) toInteger.invoke(regionProperty)).intValue();
                Method getStringProperty = method(
                        networking.getClass(),
                        "getStringProperty",
                        String.class,
                        int.class);
                Object value = getStringProperty.invoke(networking, deviceId, property);
                if (value == null) return "null";
                return "cn".equalsIgnoreCase(value.toString()) ? "cn" : "non_cn";
            } catch (Throwable error) {
                return "unavailable:" + error.getClass().getSimpleName();
            }
        }
    }
}
