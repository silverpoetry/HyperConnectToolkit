package com.weichao.hyperconnecttoolkit.domain.model

data class EnvironmentSnapshot(
    val deviceName: String,
    val androidVersion: String,
    val isRootAvailable: Boolean,
    val isMiLinkInstalled: Boolean,
    val isMirrorInstalled: Boolean,
)

fun EnvironmentSnapshot.isTargetEnvironmentReady(): Boolean =
    isRootAvailable && isMiLinkInstalled && isMirrorInstalled
