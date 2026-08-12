package com.weichao.hyperconnecttoolkit.platform

import com.weichao.hyperconnecttoolkit.BuildConfig

object HookStatusPolicy {
    const val EXPECTED_MODULE_VERSION = BuildConfig.VERSION_CODE

    private val allowedFeaturesByTarget = mapOf(
        HookTarget("com.milink.service", "com.milink.crossdeviceservice") to
            HookStatusContract.FEATURE_CROSS_DEVICE_NOTIFICATION,
        HookTarget("com.milink.service", "com.milink.service:core") to
            HookStatusContract.FEATURE_MILINK_MULTI_CHANNEL,
        HookTarget("com.xiaomi.mirror", "com.xiaomi.mirror") to
            HookStatusContract.FEATURE_PORTRAIT_STREAMING,
    )

    fun validate(
        senderPackages: Collection<String>,
        sourcePackage: String?,
        sourceProcess: String?,
        moduleVersion: Int,
        reportedFeatureMask: Int,
    ): ValidatedHookReport? {
        if (
            moduleVersion != EXPECTED_MODULE_VERSION ||
            sourcePackage == null ||
            sourceProcess == null
        ) return null
        if (sourcePackage !in senderPackages) return null

        val target = HookTarget(sourcePackage, sourceProcess)
        val allowedMask = allowedFeaturesByTarget[target] ?: return null
        if (reportedFeatureMask == 0) return null
        if (reportedFeatureMask and HookStatusContract.ALL_FEATURES != reportedFeatureMask) {
            return null
        }
        if (reportedFeatureMask and allowedMask != reportedFeatureMask) return null

        return ValidatedHookReport(target, reportedFeatureMask)
    }
}

data class ValidatedHookReport(
    val target: HookTarget,
    val featureMask: Int,
)

data class HookTarget(
    val packageName: String,
    val processName: String,
)
