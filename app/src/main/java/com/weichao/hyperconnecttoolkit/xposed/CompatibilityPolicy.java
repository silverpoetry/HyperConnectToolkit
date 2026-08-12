package com.weichao.hyperconnecttoolkit.xposed;

import android.content.pm.ActivityInfo;
import android.content.res.Configuration;

/** Pure compatibility decisions kept separate from ROM reflection and LSPosed plumbing. */
final class CompatibilityPolicy {
    static final String PIN_APP_PROVIDER_METHOD = "performPinIconClick";
    static final int PIN_APP_START_REASON = 9;

    private CompatibilityPolicy() {
    }

    static boolean isValidNotificationClick(String deviceId, String packageName) {
        return deviceId != null
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

    static boolean shouldRestorePortraitInitialBounds(
            int currentOrientation,
            int screenFrom,
            int width,
            int height) {
        return currentOrientation == Configuration.ORIENTATION_PORTRAIT
                && screenFrom == PIN_APP_START_REASON
                && width > 0
                && height > 0
                && width > height;
    }
}
