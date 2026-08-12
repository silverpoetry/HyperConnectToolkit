package com.weichao.hyperconnecttoolkit.config

data class FeatureSettings(
    val crossDeviceNotification: Boolean =
        FeaturePreferences.DEFAULT_CROSS_DEVICE_NOTIFICATION_ENABLED,
    val portraitStreaming: Boolean = FeaturePreferences.DEFAULT_PORTRAIT_STREAMING_ENABLED,
    val miLinkMultiChannel: Boolean = FeaturePreferences.DEFAULT_MILINK_MULTI_CHANNEL_ENABLED,
) {
    fun with(feature: Feature, enabled: Boolean): FeatureSettings = when (feature) {
        Feature.CROSS_DEVICE_NOTIFICATION -> copy(crossDeviceNotification = enabled)
        Feature.PORTRAIT_STREAMING -> copy(portraitStreaming = enabled)
        Feature.MILINK_MULTI_CHANNEL -> copy(miLinkMultiChannel = enabled)
    }
}

enum class Feature {
    CROSS_DEVICE_NOTIFICATION,
    PORTRAIT_STREAMING,
    MILINK_MULTI_CHANNEL,
}

data class FeatureConfiguration(
    val settings: FeatureSettings = FeatureSettings(),
    val isRemoteAvailable: Boolean = false,
    val hasPendingRemoteWrite: Boolean = false,
)
