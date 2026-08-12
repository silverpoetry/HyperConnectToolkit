package com.weichao.hyperconnecttoolkit.xposed

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CompatibilityPolicyTest {
    @Test
    fun `valid notification clicks use the stock pin-app flow`() {
        assertTrue(
            CompatibilityPolicy.isValidNotificationClick(
                "device-id",
                "com.example.app",
            ),
        )
    }

    @Test
    fun `notification click rejects incomplete identities`() {
        assertFalse(
            CompatibilityPolicy.isValidNotificationClick(
                "",
                "com.example.app",
            ),
        )
        assertFalse(
            CompatibilityPolicy.isValidNotificationClick(
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

    @Test
    fun `portrait pin-app opens restore dimensions that were forced landscape`() {
        assertTrue(
            CompatibilityPolicy.shouldRestorePortraitInitialBounds(
                Configuration.ORIENTATION_PORTRAIT,
                CompatibilityPolicy.PIN_APP_START_REASON,
                3008,
                1880,
            ),
        )
    }

    @Test
    fun `initial bounds preserve matching orientation and unrelated streams`() {
        assertFalse(
            CompatibilityPolicy.shouldRestorePortraitInitialBounds(
                Configuration.ORIENTATION_PORTRAIT,
                CompatibilityPolicy.PIN_APP_START_REASON,
                1880,
                3008,
            ),
        )
        assertFalse(
            CompatibilityPolicy.shouldRestorePortraitInitialBounds(
                Configuration.ORIENTATION_LANDSCAPE,
                CompatibilityPolicy.PIN_APP_START_REASON,
                3008,
                1880,
            ),
        )
        assertFalse(
            CompatibilityPolicy.shouldRestorePortraitInitialBounds(
                Configuration.ORIENTATION_PORTRAIT,
                17,
                3008,
                1880,
            ),
        )
        assertFalse(
            CompatibilityPolicy.shouldRestorePortraitInitialBounds(
                Configuration.ORIENTATION_PORTRAIT,
                CompatibilityPolicy.PIN_APP_START_REASON,
                0,
                0,
            ),
        )
    }
}
