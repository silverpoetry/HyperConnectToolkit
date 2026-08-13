package com.weichao.hyperconnecttoolkit.platform

import com.weichao.hyperconnecttoolkit.config.Feature
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HookRuntimeStateTest {
    @Test
    fun `decodes independent feature evidence`() {
        val state = HookRuntimeState.fromMask(
            HookStatusContract.COMPONENT_NOTIFICATION_DISCOVERY or
                HookStatusContract.COMPONENT_NOTIFICATION_FLOW or
                HookStatusContract.FEATURE_MILINK_MULTI_CHANNEL,
        )

        assertTrue(state.isApplied(Feature.CROSS_DEVICE_NOTIFICATION))
        assertFalse(state.isApplied(Feature.PORTRAIT_STREAMING))
        assertTrue(state.isApplied(Feature.MILINK_MULTI_CHANNEL))
    }

    @Test
    fun `requires both notification process components`() {
        assertFalse(
            HookRuntimeState.fromMask(HookStatusContract.COMPONENT_NOTIFICATION_DISCOVERY)
                .isApplied(Feature.CROSS_DEVICE_NOTIFICATION),
        )
        assertFalse(
            HookRuntimeState.fromMask(HookStatusContract.COMPONENT_NOTIFICATION_FLOW)
                .isApplied(Feature.CROSS_DEVICE_NOTIFICATION),
        )
    }

    @Test
    fun `merges independently reported process components before decoding`() {
        val first = HookRuntimeStore.mergeReportedCapabilities(
            0,
            HookStatusContract.COMPONENT_NOTIFICATION_DISCOVERY,
        )
        val merged = HookRuntimeStore.mergeReportedCapabilities(
            first,
            HookStatusContract.COMPONENT_NOTIFICATION_FLOW,
        )

        assertTrue(
            HookRuntimeState.fromMask(merged)
                .isApplied(Feature.CROSS_DEVICE_NOTIFICATION),
        )
    }

    @Test
    fun `drops unknown protocol bits`() {
        val state = HookRuntimeState.fromMask(Int.MAX_VALUE)

        assertTrue(state.appliedFeatureMask == HookStatusContract.ALL_FEATURES)
    }
}
