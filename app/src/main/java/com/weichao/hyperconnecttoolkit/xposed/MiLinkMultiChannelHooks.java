package com.weichao.hyperconnecttoolkit.xposed;

import android.util.Log;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

import io.github.libxposed.api.XposedModule;

/** Complete Multi-Channel feature installed only in {@code com.milink.service:core}. */
final class MiLinkMultiChannelHooks {
    private static final String TAG = "HyperConnectToolkit";
    private static final String SHARED_CHANNEL_IMPL_CLASS =
            "com.miui.circulate.channel.SharedChannelImpl";
    private static final String SHARED_CHANNEL_POLICY_FIELD = "sharedChannelPolicy";
    private static final String DISCOVERY_HOST_CLASS =
            "com.miui.headset.runtime.DiscoveryHost";
    private static final String MULTIPLATFORM_PROCESSOR_CLASS =
            "com.miui.headset.runtime.MultiplatformProcessor";

    private final XposedModule module;

    MiLinkMultiChannelHooks(XposedModule module) {
        this.module = module;
    }

    /** Resolves the complete vendor surface before registering either half of the feature. */
    boolean install(ClassLoader classLoader) {
        try {
            Bindings bindings = Bindings.resolve(classLoader);
            installChannelPolicyHook(bindings);
            installHostBindingHook(bindings);
            module.log(Log.INFO, TAG, "Installed MiLink Multi-Channel hooks");
            return true;
        } catch (Throwable error) {
            module.log(Log.ERROR, TAG, "MiLink Multi-Channel hooks are incompatible", error);
            return false;
        }
    }

    private void installChannelPolicyHook(Bindings bindings) {
        module.hook(bindings.channelPolicyConstructor).intercept(chain -> {
            Object result = chain.proceed();
            ChannelLimitPolicy.Result policyResult =
                    ChannelLimitPolicy.ensureAndroidPadDualChannel(chain.getThisObject());
            if (policyResult == ChannelLimitPolicy.Result.UPDATED) {
                module.log(Log.INFO, TAG, "MiLink AndroidPad channel limit set to 2");
            } else if (policyResult == ChannelLimitPolicy.Result.UNSUPPORTED) {
                module.log(Log.WARN, TAG, "MiLink channel policy layout is unsupported");
            }
            return result;
        });
    }

    private void installHostBindingHook(Bindings bindings) {
        module.hook(bindings.discoveryHostConstructor).intercept(chain -> {
            Object result = chain.proceed();
            bindings.hostBinding.attach(chain.getThisObject());
            return result;
        });

        module.hook(bindings.circulateCheck).intercept(chain -> {
            Object result = chain.proceed();
            if (!(result instanceof Number status)
                    || status.intValue() != HostBindingRepair.HOST_NOT_BOUND) {
                return result;
            }

            Object hostId = chain.getArg(0);
            try {
                if (bindings.hostBinding.bind(hostId)) {
                    module.log(Log.INFO, TAG, "MiLink official Host bind completed");
                    return HostBindingRepair.SUCCESS;
                }
            } catch (ReflectiveOperationException | RuntimeException error) {
                module.log(Log.WARN, TAG, "MiLink official Host bind failed", error);
            }
            return result;
        });
    }

    private static Constructor<?> onlyConstructor(Class<?> type)
            throws NoSuchMethodException {
        Constructor<?>[] constructors = type.getDeclaredConstructors();
        if (constructors.length != 1) {
            throw new NoSuchMethodException(
                    type.getName() + " expected one constructor, found " + constructors.length);
        }
        constructors[0].setAccessible(true);
        return constructors[0];
    }

    /** Immutable reflection bindings for one audited MiLink version. */
    private static final class Bindings {
        final Constructor<?> channelPolicyConstructor;
        final Constructor<?> discoveryHostConstructor;
        final Method circulateCheck;
        final HostBindingRepair hostBinding;

        private Bindings(
                Constructor<?> channelPolicyConstructor,
                Constructor<?> discoveryHostConstructor,
                Method circulateCheck,
                HostBindingRepair hostBinding) {
            this.channelPolicyConstructor = channelPolicyConstructor;
            this.discoveryHostConstructor = discoveryHostConstructor;
            this.circulateCheck = circulateCheck;
            this.hostBinding = hostBinding;
        }

        static Bindings resolve(ClassLoader classLoader) throws ReflectiveOperationException {
            Class<?> sharedChannelImpl = Class.forName(
                    SHARED_CHANNEL_IMPL_CLASS,
                    false,
                    classLoader);
            Field policyField = sharedChannelImpl.getDeclaredField(
                    SHARED_CHANNEL_POLICY_FIELD);
            Class<?> discoveryHost = Class.forName(
                    DISCOVERY_HOST_CLASS,
                    false,
                    classLoader);
            Class<?> processor = Class.forName(
                    MULTIPLATFORM_PROCESSOR_CLASS,
                    false,
                    classLoader);
            Method circulateCheck = processor.getDeclaredMethod("circulateCheck", String.class);
            circulateCheck.setAccessible(true);

            return new Bindings(
                    onlyConstructor(policyField.getType()),
                    onlyConstructor(discoveryHost),
                    circulateCheck,
                    HostBindingRepair.resolve(classLoader));
        }
    }
}
