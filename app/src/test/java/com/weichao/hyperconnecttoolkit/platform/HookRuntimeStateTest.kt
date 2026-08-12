package com.weichao.hyperconnecttoolkit.platform

import com.weichao.hyperconnecttoolkit.config.Feature
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HookRuntimeStateTest {
    @Test
    fun `decodes independent feature evidence`() {
        val state = HookRuntimeState.fromMask(
            HookStatusContract.FEATURE_CROSS_DEVICE_NOTIFICATION or
                HookStatusContract.FEATURE_MILINK_MULTI_CHANNEL,
        )

        assertTrue(state.isApplied(Feature.CROSS_DEVICE_NOTIFICATION))
        assertFalse(state.isApplied(Feature.PORTRAIT_STREAMING))
        assertTrue(state.isApplied(Feature.MILINK_MULTI_CHANNEL))
    }

    @Test
    fun `drops unknown protocol bits`() {
        val state = HookRuntimeState.fromMask(Int.MAX_VALUE)

        assertTrue(state.appliedFeatureMask == HookStatusContract.ALL_FEATURES)
    }
}
