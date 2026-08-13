package com.weichao.hyperconnecttoolkit.xposed;

import android.util.Log;

import java.lang.reflect.Array;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import io.github.libxposed.api.XposedModule;

/** Privacy-preserving, bounded structured logging backed by libxposed's official logger. */
final class DiagnosticLogger {
    private static final String TAG = "HyperConnectToolkit/Diagnostic";
    private static final String PREFIX = "HCT_DIAG";
    private static final int MAX_TOTAL_EVENTS = 2500;
    private static final int DEFAULT_EVENT_LIMIT = 80;

    private final XposedModule module;
    private final boolean enabled;
    private final String sessionId;
    private final String processName;
    private final AtomicInteger totalEvents = new AtomicInteger();
    private final AtomicBoolean totalSuppressionReported = new AtomicBoolean();
    private final ConcurrentHashMap<String, AtomicInteger> eventCounts =
            new ConcurrentHashMap<>();

    DiagnosticLogger(
            XposedModule module,
            boolean enabled,
            String sessionId,
            String processName) {
        this.module = module;
        this.enabled = enabled;
        this.sessionId = normalizeToken(sessionId, "missing");
        this.processName = normalizeToken(processName, "unknown");
    }

    boolean isEnabled() {
        return enabled;
    }

    String sessionId() {
        return sessionId;
    }

    void event(String event, String... fields) {
        limited(event, DEFAULT_EVENT_LIMIT, event, fields);
    }

    void trace(String rateLimitKey, int limit, String event, String... fields) {
        limited(rateLimitKey, limit, event, fields);
    }

    void error(String event, Throwable error, String... fields) {
        if (!enabled) return;
        String[] extended = new String[fields.length + 6];
        System.arraycopy(fields, 0, extended, 0, fields.length);
        extended[fields.length] = "errorType";
        extended[fields.length + 1] = error == null
                ? "unknown"
                : normalizeToken(error.getClass().getName(), "unknown");
        extended[fields.length + 2] = "errorTrace";
        extended[fields.length + 3] = summarizeThrowable(error);
        extended[fields.length + 4] = "errorDetail";
        extended[fields.length + 5] = summarizeErrorDetail(error);
        limited(event, DEFAULT_EVENT_LIMIT, event, extended);
    }

    String summarize(Object value) {
        if (value == null) return "null";
        if (value instanceof Boolean || value instanceof Byte
                || value instanceof Short || value instanceof Integer
                || value instanceof Long || value instanceof Float
                || value instanceof Double) {
            return value.toString();
        }
        if (value instanceof Enum<?> enumeration) {
            return "enum:" + normalizeToken(enumeration.name(), "unknown");
        }
        if (value instanceof CharSequence text) {
            return "text(len=" + text.length() + ",hash=" + hash(text.toString()) + ")";
        }
        Class<?> type = value.getClass();
        if (type.isArray()) {
            return "array(type=" + normalizeToken(type.getComponentType().getName(), "unknown")
                    + ",len=" + Array.getLength(value) + ")";
        }
        if (value instanceof Collection<?> collection) {
            StringBuilder summary = new StringBuilder("collection(size=")
                    .append(collection.size());
            int emitted = 0;
            for (Object item : collection) {
                if (emitted >= 8 || !isSafeScalar(item)) break;
                summary.append(emitted == 0 ? ",values=" : ",").append(summarize(item));
                emitted++;
            }
            return summary.append(')').toString();
        }
        if (value instanceof Map<?, ?> map) {
            return "map(size=" + map.size() + ")";
        }
        return "object(type=" + normalizeToken(type.getName(), "unknown") + ")";
    }

    String identifier(String value) {
        return value == null ? "null" : "id(hash=" + hash(value) + ",len=" + value.length() + ")";
    }

    private void limited(
            String rateLimitKey,
            int limit,
            String event,
            String... fields) {
        if (!enabled) return;
        int total = totalEvents.incrementAndGet();
        if (total > MAX_TOTAL_EVENTS) {
            if (totalSuppressionReported.compareAndSet(false, true)) {
                emit(Log.WARN, "log_suppressed", new String[]{
                        "scope", "process",
                        "limit", Integer.toString(MAX_TOTAL_EVENTS),
                });
            }
            return;
        }

        AtomicInteger count = eventCounts.computeIfAbsent(rateLimitKey, ignored -> new AtomicInteger());
        int occurrence = count.incrementAndGet();
        if (occurrence > limit) {
            if (occurrence == limit + 1) {
                emit(Log.WARN, "log_suppressed", new String[]{
                        "scope", normalizeToken(rateLimitKey, "unknown"),
                        "limit", Integer.toString(limit),
                });
            }
            return;
        }
        emit(Log.INFO, event, fields);
    }

    private void emit(int level, String event, String[] fields) {
        StringBuilder message = new StringBuilder(PREFIX)
                .append("|schema=1")
                .append("|session=").append(sessionId)
                .append("|process=").append(processName)
                .append("|event=").append(normalizeToken(event, "unknown"));
        for (int index = 0; index + 1 < fields.length; index += 2) {
            message.append('|')
                    .append(normalizeToken(fields[index], "field"))
                    .append('=')
                    .append(normalizeValue(fields[index + 1]));
        }
        runCatchingLog(level, message.toString());
    }

    private void runCatchingLog(int level, String message) {
        try {
            module.log(level, TAG, message);
        } catch (Throwable ignored) {
            // Diagnostics must never affect the vendor process.
        }
    }

    private String hash(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(sessionId.getBytes(StandardCharsets.UTF_8));
            digest.update((byte) ':');
            byte[] bytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(12);
            for (int index = 0; index < 6; index++) {
                result.append(String.format(java.util.Locale.ROOT, "%02x", bytes[index]));
            }
            return result.toString();
        } catch (Throwable ignored) {
            return Integer.toHexString((sessionId + ':' + value).hashCode());
        }
    }

    private String summarizeThrowable(Throwable error) {
        if (error == null) return "unknown";
        StringBuilder trace = new StringBuilder();
        StackTraceElement[] elements = error.getStackTrace();
        int limit = Math.min(elements.length, 8);
        for (int index = 0; index < limit; index++) {
            if (index > 0) trace.append('>');
            StackTraceElement element = elements[index];
            trace.append(normalizeToken(element.getClassName(), "unknown"))
                    .append('#')
                    .append(normalizeToken(element.getMethodName(), "unknown"))
                    .append(':')
                    .append(element.getLineNumber());
        }
        return trace.toString();
    }

    private String summarizeErrorDetail(Throwable error) {
        if (error == null || error.getMessage() == null) return "none";
        if (error instanceof ClassNotFoundException
                || error instanceof NoSuchMethodException
                || error instanceof NoSuchFieldException) {
            return normalizeValue(error.getMessage());
        }
        return "hash=" + hash(error.getMessage());
    }

    private static boolean isSafeScalar(Object value) {
        return value == null || value instanceof Boolean || value instanceof Number
                || value instanceof Enum<?>;
    }

    private static String normalizeToken(String value, String fallback) {
        if (value == null || value.isBlank()) return fallback;
        String normalized = value.replaceAll("[^A-Za-z0-9_.:-]", "_");
        return normalized.length() <= 160 ? normalized : normalized.substring(0, 160);
    }

    private static String normalizeValue(String value) {
        if (value == null) return "null";
        String normalized = value
                .replace('\n', '_')
                .replace('\r', '_')
                .replace('|', '/');
        return normalized.length() <= 400 ? normalized : normalized.substring(0, 400);
    }
}
