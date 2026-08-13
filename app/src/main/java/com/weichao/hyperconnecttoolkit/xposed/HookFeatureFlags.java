package com.weichao.hyperconnecttoolkit.xposed;

import android.content.SharedPreferences;

import com.weichao.hyperconnecttoolkit.config.FeaturePreferences;

/** Immutable package-load snapshot; disabled features never register their hooks. */
final class HookFeatureFlags {
    final boolean crossDeviceNotification;
    final boolean portraitStreaming;
    final boolean miLinkMultiChannel;
    final boolean detailedLogging;
    final String diagnosticSessionId;

    private HookFeatureFlags(
            boolean crossDeviceNotification,
            boolean portraitStreaming,
            boolean miLinkMultiChannel,
            boolean detailedLogging,
            String diagnosticSessionId) {
        this.crossDeviceNotification = crossDeviceNotification;
        this.portraitStreaming = portraitStreaming;
        this.miLinkMultiChannel = miLinkMultiChannel;
        this.detailedLogging = detailedLogging;
        this.diagnosticSessionId = diagnosticSessionId;
    }

    static HookFeatureFlags read(SharedPreferences preferences) {
        return of(
                preferences.getBoolean(
                        FeaturePreferences.KEY_CROSS_DEVICE_NOTIFICATION,
                        FeaturePreferences.DEFAULT_CROSS_DEVICE_NOTIFICATION_ENABLED),
                preferences.getBoolean(
                        FeaturePreferences.KEY_PORTRAIT_STREAMING,
                        FeaturePreferences.DEFAULT_PORTRAIT_STREAMING_ENABLED),
                preferences.getBoolean(
                        FeaturePreferences.KEY_MILINK_MULTI_CHANNEL,
                        FeaturePreferences.DEFAULT_MILINK_MULTI_CHANNEL_ENABLED),
                preferences.getBoolean(
                        FeaturePreferences.KEY_DETAILED_LOGGING,
                        FeaturePreferences.DEFAULT_DETAILED_LOGGING_ENABLED),
                preferences.getString(
                        FeaturePreferences.KEY_DIAGNOSTIC_SESSION_ID,
                        ""));
    }

    static HookFeatureFlags of(
            boolean crossDeviceNotification,
            boolean portraitStreaming,
            boolean miLinkMultiChannel) {
        return of(
                crossDeviceNotification,
                portraitStreaming,
                miLinkMultiChannel,
                false,
                "");
    }

    static HookFeatureFlags of(
            boolean crossDeviceNotification,
            boolean portraitStreaming,
            boolean miLinkMultiChannel,
            boolean detailedLogging,
            String diagnosticSessionId) {
        return new HookFeatureFlags(
                crossDeviceNotification,
                portraitStreaming,
                miLinkMultiChannel,
                detailedLogging,
                diagnosticSessionId == null ? "" : diagnosticSessionId);
    }

    static HookFeatureFlags disabled() {
        return of(false, false, false, false, "");
    }

}
