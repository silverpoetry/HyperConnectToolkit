package com.weichao.hyperconnecttoolkit.ui.home

import org.junit.Assert.assertEquals
import org.junit.Test

class FeatureApplicationStateTest {
    @Test
    fun `desired and reported states resolve independently`() {
        assertEquals(
            FeatureApplicationState.DISABLED,
            FeatureApplicationState.resolve(false, false, false),
        )
        assertEquals(
            FeatureApplicationState.APPLIED,
            FeatureApplicationState.resolve(true, true, false),
        )
        assertEquals(
            FeatureApplicationState.NOT_APPLIED,
            FeatureApplicationState.resolve(true, false, false),
        )
        assertEquals(
            FeatureApplicationState.STILL_RUNNING,
            FeatureApplicationState.resolve(false, true, false),
        )
    }

    @Test
    fun `pending restart takes precedence over previous runtime evidence`() {
        assertEquals(
            FeatureApplicationState.PENDING_ENABLE,
            FeatureApplicationState.resolve(true, false, true),
        )
        assertEquals(
            FeatureApplicationState.PENDING_DISABLE,
            FeatureApplicationState.resolve(false, true, true),
        )
    }
}
