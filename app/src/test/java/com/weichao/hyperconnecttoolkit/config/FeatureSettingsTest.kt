package com.weichao.hyperconnecttoolkit.config

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FeatureSettingsTest {
    @Test
    fun `fresh configuration enables only cross-device notification`() {
        val settings = FeatureSettings()

        assertTrue(settings.crossDeviceNotification)
        assertFalse(settings.portraitStreaming)
        assertFalse(settings.miLinkMultiChannel)
    }
}
