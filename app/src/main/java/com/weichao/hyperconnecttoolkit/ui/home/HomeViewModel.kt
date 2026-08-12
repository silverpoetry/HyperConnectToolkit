package com.weichao.hyperconnecttoolkit.ui.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.weichao.hyperconnecttoolkit.config.Feature
import com.weichao.hyperconnecttoolkit.config.FeatureSettingsStore
import com.weichao.hyperconnecttoolkit.data.AndroidSystemStatusRepository
import com.weichao.hyperconnecttoolkit.platform.HiddenSettingsLauncher
import com.weichao.hyperconnecttoolkit.platform.LaunchMethod
import com.weichao.hyperconnecttoolkit.platform.LaunchResult
import com.weichao.hyperconnecttoolkit.platform.HookRuntimeStore
import com.weichao.hyperconnecttoolkit.platform.RootShell
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class HomeViewModel(application: Application) : AndroidViewModel(application) {
    private val rootShell = RootShell()
    private val repository = AndroidSystemStatusRepository(application, rootShell)
    private val settingsLauncher = HiddenSettingsLauncher(application, rootShell)

    private val mutableState = MutableStateFlow(HomeUiState())
    val state: StateFlow<HomeUiState> = mutableState.asStateFlow()

    private val eventChannel = Channel<HomeEvent>(Channel.BUFFERED)
    val events = eventChannel.receiveAsFlow()

    init {
        viewModelScope.launch {
            FeatureSettingsStore.configuration.collect { configuration ->
                mutableState.update { it.copy(features = configuration) }
            }
        }
        viewModelScope.launch {
            HookRuntimeStore.state.collect { runtime ->
                mutableState.update { it.copy(hookRuntime = runtime) }
            }
        }
        refresh()
    }

    fun refresh() {
        if (mutableState.value.isRefreshing) {
            // The initial state is refreshing but still needs its first read.
            if (mutableState.value.environment != null) return
        }
        viewModelScope.launch {
            mutableState.update {
                it.copy(isRefreshing = true)
            }
            val snapshot = repository.readEnvironment()
            mutableState.update {
                it.copy(
                    isRefreshing = false,
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
        if (FeatureSettingsStore.configuration.value.hasPendingRemoteWrite) {
            eventChannel.send(HomeEvent.Message("配置尚未同步到 LSPosed，暂不能重启服务"))
            return@runAction
        }
        val result = repository.restartTargetProcesses()
        if (result.isSuccess) {
            HookRuntimeStore.clear(getApplication())
            mutableState.update {
                it.copy(
                    pendingRestartFeatures = emptySet(),
                )
            }
        }
        eventChannel.send(HomeEvent.Message(result.message))
        refresh()
    }

    fun setFeatureEnabled(feature: Feature, enabled: Boolean) = runAction {
        val saved = withContext(Dispatchers.IO) {
            FeatureSettingsStore.setEnabled(feature, enabled)
        }
        if (!saved) {
            eventChannel.send(HomeEvent.Message("配置保存失败"))
            return@runAction
        }
        mutableState.update {
            it.copy(pendingRestartFeatures = it.pendingRestartFeatures + feature)
        }
        val pending = FeatureSettingsStore.configuration.value.hasPendingRemoteWrite
        eventChannel.send(
            HomeEvent.Message(
                if (pending) {
                    "已保存；连接 LSPosed 后会自动同步"
                } else {
                    "已保存；重启服务后生效"
                },
            ),
        )
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
