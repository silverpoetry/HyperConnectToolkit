@file:OptIn(ExperimentalMaterial3Api::class)

package com.weichao.hyperconnecttoolkit.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.weichao.hyperconnecttoolkit.config.Feature
import kotlinx.coroutines.flow.Flow

@Composable
fun HomeRoute(
    state: HomeUiState,
    events: Flow<HomeEvent>,
    onRefresh: () -> Unit,
    onOpenNotificationSettings: () -> Unit,
    onRestartServices: () -> Unit,
    onFeatureChanged: (Feature, Boolean) -> Unit,
    onDetailedLoggingChanged: (Boolean) -> Unit,
    onExportDiagnostics: () -> Unit,
    onOpenAbout: () -> Unit,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    var showRestartConfirmation by remember { mutableStateOf(false) }

    LaunchedEffect(events) {
        events.collect { event ->
            when (event) {
                is HomeEvent.Message -> snackbarHostState.showSnackbar(event.text)
            }
        }
    }

    HomeScreen(
        state = state,
        snackbarHostState = snackbarHostState,
        onRefresh = onRefresh,
        onOpenNotificationSettings = onOpenNotificationSettings,
        onRequestRestart = { showRestartConfirmation = true },
        onFeatureChanged = onFeatureChanged,
        onDetailedLoggingChanged = onDetailedLoggingChanged,
        onExportDiagnostics = onExportDiagnostics,
        onOpenAbout = onOpenAbout,
    )

    if (showRestartConfirmation) {
        RestartConfirmationDialog(
            onDismiss = { showRestartConfirmation = false },
            onConfirm = {
                showRestartConfirmation = false
                onRestartServices()
            },
        )
    }
}

@Composable
private fun HomeScreen(
    state: HomeUiState,
    snackbarHostState: SnackbarHostState,
    onRefresh: () -> Unit,
    onOpenNotificationSettings: () -> Unit,
    onRequestRestart: () -> Unit,
    onFeatureChanged: (Feature, Boolean) -> Unit,
    onDetailedLoggingChanged: (Boolean) -> Unit,
    onExportDiagnostics: () -> Unit,
    onOpenAbout: () -> Unit,
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("HyperConnect Toolkit") },
                actions = {
                    IconButton(onClick = onOpenAbout) {
                        Icon(Icons.Rounded.Info, contentDescription = "关于")
                    }
                    IconButton(
                        onClick = onRefresh,
                        enabled = !state.isRefreshing && !state.isActionRunning,
                    ) {
                        if (state.isRefreshing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(22.dp),
                                strokeWidth = 2.dp,
                            )
                        } else {
                            Icon(Icons.Rounded.Refresh, contentDescription = "刷新状态")
                        }
                    }
                },
            )
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter,
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 720.dp),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    FeatureControlsCard(
                        configuration = state.features,
                        hookRuntime = state.hookRuntime,
                        pendingRestartFeatures = state.pendingRestartFeatures,
                        isBusy = state.isActionRunning,
                        onFeatureChanged = onFeatureChanged,
                    )
                }
                item {
                    NotificationSettingsCard(
                        isBusy = state.isActionRunning,
                        onOpenNotificationSettings = onOpenNotificationSettings,
                        onRequestRestart = onRequestRestart,
                    )
                }
                item {
                    DiagnosticsCard(
                        settings = state.features.settings,
                        isRestartPending = state.isDiagnosticRestartPending,
                        canExport = state.environment?.isRootAvailable == true,
                        isBusy = state.isActionRunning,
                        onDetailedLoggingChanged = onDetailedLoggingChanged,
                        onExportDiagnostics = onExportDiagnostics,
                    )
                }
                item {
                    EnvironmentCard(
                        environment = state.environment,
                        isLoading = state.isRefreshing,
                    )
                }
            }
        }
    }
}

@Composable
private fun RestartConfirmationDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.RestartAlt, contentDescription = null) },
        title = { Text("重启互联服务？") },
        text = { Text("当前串流会结束，MiLink 与 Xiaomi Mirror 将在下次启动时加载新配置。") },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("重启")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        },
    )
}
