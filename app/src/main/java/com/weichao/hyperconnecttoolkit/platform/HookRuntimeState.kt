package com.weichao.hyperconnecttoolkit.platform

import com.weichao.hyperconnecttoolkit.config.Feature

/** Version- and boot-matched evidence reported by the process that installed each Hook group. */
data class HookRuntimeState(
    val appliedFeatureMask: Int = 0,
) {
    fun isApplied(feature: Feature): Boolean = appliedFeatureMask and feature.mask != 0

    companion object {
        fun fromMask(mask: Int): HookRuntimeState {
            val reported = mask and HookStatusContract.ALL_REPORT_BITS
            var applied = reported and (
                HookStatusContract.FEATURE_PORTRAIT_STREAMING or
                    HookStatusContract.FEATURE_MILINK_MULTI_CHANNEL
                )
            val notificationComponents =
                HookStatusContract.COMPONENT_NOTIFICATION_DISCOVERY or
                    HookStatusContract.COMPONENT_NOTIFICATION_FLOW
            if (reported and notificationComponents == notificationComponents) {
                applied = applied or HookStatusContract.FEATURE_CROSS_DEVICE_NOTIFICATION
            }
            return HookRuntimeState(appliedFeatureMask = applied)
        }
    }
}

private val Feature.mask: Int
    get() = when (this) {
        Feature.CROSS_DEVICE_NOTIFICATION ->
            HookStatusContract.FEATURE_CROSS_DEVICE_NOTIFICATION
        Feature.PORTRAIT_STREAMING -> HookStatusContract.FEATURE_PORTRAIT_STREAMING
        Feature.MILINK_MULTI_CHANNEL -> HookStatusContract.FEATURE_MILINK_MULTI_CHANNEL
    }
