package com.weichao.hyperconnecttoolkit.xposed;

import android.content.pm.ActivityInfo;

/** Pure compatibility decisions kept separate from ROM reflection and LSPosed plumbing. */
final class CompatibilityPolicy {
    static final int CROSS_DEVICE_NOTIFICATION_REASON = 17;
    static final int STOCK_FPS = 60;
    static final int TARGET_FPS = 120;

    private CompatibilityPolicy() {
    }

    static boolean isCrossDeviceNotificationSession(int startReason) {
        return startReason == CROSS_DEVICE_NOTIFICATION_REASON;
    }

    static boolean useAppScreenOpenRequest(boolean stockValue, int startReason) {
        return stockValue || isCrossDeviceNotificationSession(startReason);
    }

    static boolean forceLandscapeInitialBounds(boolean stockValue, int startReason) {
        return isCrossDeviceNotificationSession(startReason) ? false : stockValue;
    }

    static int resolveRequestedOrientation(boolean isTabletSink, int requestedOrientation) {
        if (isTabletSink
                && requestedOrientation == ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE) {
            return ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR;
        }
        return requestedOrientation;
    }

    static int resolveFrameRate(int stockFps) {
        return stockFps == STOCK_FPS ? TARGET_FPS : stockFps;
    }
}
