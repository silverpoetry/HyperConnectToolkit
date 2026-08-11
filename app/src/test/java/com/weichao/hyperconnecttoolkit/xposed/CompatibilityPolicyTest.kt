package com.weichao.hyperconnecttoolkit.xposed

import android.content.pm.ActivityInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CompatibilityPolicyTest {
    @Test
    fun `valid notification provider calls are bridged to the stock pin-app flow`() {
        assertEquals(
            "launchAppFromPendingIntentSink",
            CompatibilityPolicy.NOTIFICATION_CLICK_PROVIDER_METHOD,
        )
        assertTrue(
            CompatibilityPolicy.shouldBridgeNotificationCall(
                CompatibilityPolicy.NOTIFICATION_CLICK_PROVIDER_METHOD,
                "device-id",
                "com.example.app",
            ),
        )
    }

    @Test
    fun `provider bridge rejects unrelated or incomplete calls`() {
        assertFalse(
            CompatibilityPolicy.shouldBridgeNotificationCall(
                "launchNotificationSink",
                "device-id",
                "com.example.app",
            ),
        )
        assertFalse(
            CompatibilityPolicy.shouldBridgeNotificationCall(
                CompatibilityPolicy.PIN_APP_PROVIDER_METHOD,
                "device-id",
                "com.example.app",
            ),
        )
        assertFalse(
            CompatibilityPolicy.shouldBridgeNotificationCall(
                CompatibilityPolicy.NOTIFICATION_CLICK_PROVIDER_METHOD,
                "",
                "com.example.app",
            ),
        )
        assertFalse(
            CompatibilityPolicy.shouldBridgeNotificationCall(
                CompatibilityPolicy.NOTIFICATION_CLICK_PROVIDER_METHOD,
                "device-id",
                null,
            ),
        )
    }

    @Test
    fun `orientation override is scoped to tablet sink activity`() {
        val landscape = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE

        assertEquals(
            ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR,
            CompatibilityPolicy.resolveRequestedOrientation(true, landscape),
        )
        assertEquals(landscape, CompatibilityPolicy.resolveRequestedOrientation(false, landscape))
        assertEquals(
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT,
            CompatibilityPolicy.resolveRequestedOrientation(
                true,
                ActivityInfo.SCREEN_ORIENTATION_PORTRAIT,
            ),
        )
    }
}
