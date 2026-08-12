package com.weichao.hyperconnecttoolkit.ui.home

import com.weichao.hyperconnecttoolkit.config.FeatureConfiguration
import com.weichao.hyperconnecttoolkit.config.Feature
import com.weichao.hyperconnecttoolkit.domain.model.EnvironmentSnapshot
import com.weichao.hyperconnecttoolkit.platform.HookRuntimeState

data class HomeUiState(
    val isRefreshing: Boolean = true,
    val isActionRunning: Boolean = false,
    val hookRuntime: HookRuntimeState = HookRuntimeState(),
    val environment: EnvironmentSnapshot? = null,
    val features: FeatureConfiguration = FeatureConfiguration(),
    val pendingRestartFeatures: Set<Feature> = emptySet(),
)

sealed interface HomeEvent {
    data class Message(val text: String) : HomeEvent
}
