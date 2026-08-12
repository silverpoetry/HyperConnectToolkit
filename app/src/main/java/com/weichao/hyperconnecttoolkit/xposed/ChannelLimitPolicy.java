package com.weichao.hyperconnecttoolkit.xposed;

import java.lang.reflect.Field;
import java.util.Map;

/** Structural adapter for MiLink's private shared-channel policy object. */
final class ChannelLimitPolicy {
    enum Result {
        UPDATED,
        ALREADY_SUPPORTED,
        UNSUPPORTED,
    }

    private ChannelLimitPolicy() {
    }

    static Result ensureAndroidPadDualChannel(Object policy) {
        if (policy == null) return Result.UNSUPPORTED;

        for (Class<?> type = policy.getClass(); type != null; type = type.getSuperclass()) {
            for (Field field : type.getDeclaredFields()) {
                if (!Map.class.isAssignableFrom(field.getType())) continue;
                Result result = inspectField(policy, field);
                if (result != Result.UNSUPPORTED) return result;
            }
        }
        return Result.UNSUPPORTED;
    }

    private static Result inspectField(Object policy, Field field) {
        try {
            field.setAccessible(true);
            if (!(field.get(policy) instanceof Map<?, ?> limits) || !isLimitTable(limits)) {
                return Result.UNSUPPORTED;
            }

            int currentLimit = ((Number) limits.get(ANDROID_PAD)).intValue();
            if (currentLimit == DUAL_CHANNEL_LIMIT) {
                return Result.ALREADY_SUPPORTED;
            }
            if (currentLimit != SINGLE_CHANNEL_LIMIT) {
                return Result.UNSUPPORTED;
            }

            put(limits, ANDROID_PAD, DUAL_CHANNEL_LIMIT);
            return ((Number) limits.get(ANDROID_PAD)).intValue() == DUAL_CHANNEL_LIMIT
                    ? Result.UPDATED
                    : Result.UNSUPPORTED;
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return Result.UNSUPPORTED;
        }
    }

    private static boolean isLimitTable(Map<?, ?> limits) {
        return limits.size() == 3
                && isReasonableLimit(limits.get(ANDROID_PHONE))
                && isReasonableLimit(limits.get(ANDROID_PAD))
                && isReasonableLimit(limits.get(WINDOWS_PC));
    }

    private static boolean isReasonableLimit(Object value) {
        return value instanceof Number number
                && number.intValue() >= 1
                && number.intValue() <= 8;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void put(Map<?, ?> map, int key, int value) {
        ((Map) map).put(key, value);
    }

    private static final int ANDROID_PHONE = 1;
    private static final int ANDROID_PAD = 2;
    private static final int WINDOWS_PC = 4;
    private static final int SINGLE_CHANNEL_LIMIT = 1;
    private static final int DUAL_CHANNEL_LIMIT = 2;
}
