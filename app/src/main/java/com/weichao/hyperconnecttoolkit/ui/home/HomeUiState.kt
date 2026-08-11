package com.weichao.hyperconnecttoolkit.ui.home

import com.weichao.hyperconnecttoolkit.domain.model.EnvironmentSnapshot

data class HomeUiState(
    val isRefreshing: Boolean = true,
    val isActionRunning: Boolean = false,
    val isModuleActive: Boolean = false,
    val environment: EnvironmentSnapshot? = null,
)

sealed interface HomeEvent {
    data class Message(val text: String) : HomeEvent
}
