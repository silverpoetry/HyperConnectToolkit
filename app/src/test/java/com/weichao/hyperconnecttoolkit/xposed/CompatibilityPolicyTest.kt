package com.weichao.hyperconnecttoolkit.xposed

import android.content.pm.ActivityInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CompatibilityPolicyTest {
    @Test
    fun `notification sessions use native app-screen request with current bounds`() {
        val reason = CompatibilityPolicy.CROSS_DEVICE_NOTIFICATION_REASON

        assertTrue(CompatibilityPolicy.isCrossDeviceNotificationSession(reason))
        assertTrue(CompatibilityPolicy.useAppScreenOpenRequest(false, reason))
        assertFalse(CompatibilityPolicy.forceLandscapeInitialBounds(true, reason))
    }

    @Test
    fun `unrelated sessions preserve stock request policy`() {
        assertFalse(CompatibilityPolicy.isCrossDeviceNotificationSession(9))
        assertFalse(CompatibilityPolicy.useAppScreenOpenRequest(false, 9))
        assertTrue(CompatibilityPolicy.useAppScreenOpenRequest(true, 9))
        assertTrue(CompatibilityPolicy.forceLandscapeInitialBounds(true, 9))
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
    fun `only stock sixty fps values are upgraded`() {
        assertEquals(120, CompatibilityPolicy.resolveFrameRate(60))
        assertEquals(30, CompatibilityPolicy.resolveFrameRate(30))
        assertEquals(90, CompatibilityPolicy.resolveFrameRate(90))
    }
}
