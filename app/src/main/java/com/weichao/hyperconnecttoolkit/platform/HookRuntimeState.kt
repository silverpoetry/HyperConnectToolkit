package com.weichao.hyperconnecttoolkit.platform

import com.weichao.hyperconnecttoolkit.config.Feature

/** Version- and boot-matched evidence reported by the process that installed each Hook group. */
data class HookRuntimeState(
    val appliedFeatureMask: Int = 0,
) {
    fun isApplied(feature: Feature): Boolean = appliedFeatureMask and feature.mask != 0

    companion object {
        fun fromMask(mask: Int): HookRuntimeState = HookRuntimeState(
            appliedFeatureMask = mask and HookStatusContract.ALL_FEATURES,
        )
    }
}

private val Feature.mask: Int
    get() = when (this) {
        Feature.CROSS_DEVICE_NOTIFICATION ->
            HookStatusContract.FEATURE_CROSS_DEVICE_NOTIFICATION
        Feature.PORTRAIT_STREAMING -> HookStatusContract.FEATURE_PORTRAIT_STREAMING
        Feature.MILINK_MULTI_CHANNEL -> HookStatusContract.FEATURE_MILINK_MULTI_CHANNEL
    }
