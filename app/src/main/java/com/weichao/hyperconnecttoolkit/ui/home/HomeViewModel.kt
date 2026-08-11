package com.weichao.hyperconnecttoolkit.ui.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.weichao.hyperconnecttoolkit.data.AndroidSystemStatusRepository
import com.weichao.hyperconnecttoolkit.platform.HiddenSettingsLauncher
import com.weichao.hyperconnecttoolkit.platform.LaunchMethod
import com.weichao.hyperconnecttoolkit.platform.LaunchResult
import com.weichao.hyperconnecttoolkit.platform.ModuleRuntimeState
import com.weichao.hyperconnecttoolkit.platform.RootShell
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class HomeViewModel(application: Application) : AndroidViewModel(application) {
    private val rootShell = RootShell()
    private val repository = AndroidSystemStatusRepository(application, rootShell)
    private val settingsLauncher = HiddenSettingsLauncher(application, rootShell)

    private val mutableState = MutableStateFlow(HomeUiState())
    val state: StateFlow<HomeUiState> = mutableState.asStateFlow()

    private val eventChannel = Channel<HomeEvent>(Channel.BUFFERED)
    val events = eventChannel.receiveAsFlow()

    init {
        refresh()
    }

    fun refresh() {
        if (mutableState.value.isRefreshing) {
            // The initial state is refreshing but still needs its first read.
            if (mutableState.value.environment != null) return
        }
        viewModelScope.launch {
            mutableState.update {
                it.copy(
                    isRefreshing = true,
                    isModuleActive = ModuleRuntimeState.isActive(getApplication()),
                )
            }
            val snapshot = repository.readEnvironment()
            mutableState.update {
                it.copy(
                    isRefreshing = false,
                    isModuleActive = ModuleRuntimeState.isActive(getApplication()),
                    environment = snapshot,
                )
            }
        }
    }

    fun openCrossDeviceNotification() = runAction {
        when (val result = settingsLauncher.openCrossDeviceNotification()) {
            is LaunchResult.Success -> {
                val suffix = if (result.method == LaunchMethod.ROOT) "（root 兼容启动）" else ""
                eventChannel.send(HomeEvent.Message("已打开跨设备通知$suffix"))
            }

            is LaunchResult.Failure -> {
                eventChannel.send(HomeEvent.Message("打开失败：${result.reason}"))
            }
        }
    }

    fun restartServices() = runAction {
        val result = repository.restartTargetProcesses()
        eventChannel.send(HomeEvent.Message(result.message))
        refresh()
    }

    private fun runAction(block: suspend () -> Unit) {
        if (mutableState.value.isActionRunning) return
        viewModelScope.launch {
            mutableState.update { it.copy(isActionRunning = true) }
            try {
                block()
            } catch (error: Throwable) {
                eventChannel.send(
                    HomeEvent.Message(error.message ?: "操作未完成，请查看 LSPosed 日志"),
                )
            } finally {
                mutableState.update { it.copy(isActionRunning = false) }
            }
        }
    }
}
