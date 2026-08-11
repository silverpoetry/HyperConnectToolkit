package com.weichao.hyperconnecttoolkit.xposed;

import android.content.pm.ActivityInfo;

/** Pure compatibility decisions kept separate from ROM reflection and LSPosed plumbing. */
final class CompatibilityPolicy {
    // CallProvider's public protocol name. Its downstream helper uses
    // "launchNotificationSink" only as a log label, not as the provider method key.
    static final String NOTIFICATION_CLICK_PROVIDER_METHOD =
            "launchAppFromPendingIntentSink";
    static final String PIN_APP_PROVIDER_METHOD = "performPinIconClick";

    private CompatibilityPolicy() {
    }

    static boolean shouldBridgeNotificationCall(
            String providerMethod,
            String deviceId,
            String packageName) {
        return NOTIFICATION_CLICK_PROVIDER_METHOD.equals(providerMethod)
                && deviceId != null
                && !deviceId.isEmpty()
                && packageName != null
                && !packageName.isEmpty();
    }

    static int resolveRequestedOrientation(boolean isTabletSink, int requestedOrientation) {
        if (isTabletSink
                && requestedOrientation == ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE) {
            return ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR;
        }
        return requestedOrientation;
    }
}
