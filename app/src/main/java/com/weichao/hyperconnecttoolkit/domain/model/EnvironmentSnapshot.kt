package com.weichao.hyperconnecttoolkit.domain.model

enum class ToggleState {
    ENABLED,
    DISABLED,
    UNKNOWN,
}

data class EnvironmentSnapshot(
    val deviceName: String,
    val androidVersion: String,
    val isRootAvailable: Boolean,
    val isMiLinkInstalled: Boolean,
    val isMirrorInstalled: Boolean,
    val notificationReceiveState: ToggleState,
    val notificationSendState: ToggleState,
)

fun EnvironmentSnapshot.isTargetEnvironmentReady(): Boolean =
    isRootAvailable && isMiLinkInstalled && isMirrorInstalled
