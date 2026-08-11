package com.weichao.hyperconnecttoolkit.data

import com.weichao.hyperconnecttoolkit.domain.model.EnvironmentSnapshot

interface SystemStatusRepository {
    suspend fun readEnvironment(): EnvironmentSnapshot
    suspend fun restartTargetProcesses(): RestartResult
}

data class RestartResult(
    val isSuccess: Boolean,
    val message: String,
)
