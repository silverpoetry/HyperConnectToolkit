@file:OptIn(ExperimentalMaterial3Api::class)

package com.weichao.hyperconnecttoolkit.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Devices
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.weichao.hyperconnecttoolkit.domain.model.EnvironmentSnapshot
import com.weichao.hyperconnecttoolkit.domain.model.ToggleState
import kotlinx.coroutines.flow.Flow

@Composable
fun HomeRoute(
    state: HomeUiState,
    events: Flow<HomeEvent>,
    onRefresh: () -> Unit,
    onOpenNotificationSettings: () -> Unit,
    onRestartServices: () -> Unit,
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
    )

    if (showRestartConfirmation) {
        AlertDialog(
            onDismissRequest = { showRestartConfirmation = false },
            icon = { Icon(Icons.Rounded.RestartAlt, contentDescription = null) },
            title = { Text("重启互联服务？") },
            text = {
                Text("这会结束当前串流并停止妙享中心与互联服务。相关进程下次启动时会重新加载模块。")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showRestartConfirmation = false
                        onRestartServices()
                    },
                ) {
                    Text("重启")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRestartConfirmation = false }) {
                    Text("取消")
                }
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
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("互联增强") },
                actions = {
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
                    ModuleStatusCard(
                        isActive = state.isModuleActive,
                        environment = state.environment,
                    )
                }

                item {
                    NotificationSettingsCard(
                        environment = state.environment,
                        isBusy = state.isActionRunning,
                        onOpenNotificationSettings = onOpenNotificationSettings,
                        onRequestRestart = onRequestRestart,
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
private fun ModuleStatusCard(
    isActive: Boolean,
    environment: EnvironmentSnapshot?,
) {
    val statusColor = if (isActive) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.error
    }

    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        ListItem(
            leadingContent = {
                Icon(
                    imageVector = if (isActive) Icons.Rounded.CheckCircle else Icons.Rounded.WarningAmber,
                    contentDescription = null,
                    tint = statusColor,
                )
            },
            headlineContent = {
                Text(
                    text = if (isActive) "模块已生效" else "未检测到模块",
                    fontWeight = FontWeight.SemiBold,
                )
            },
            supportingContent = {
                Text(
                    text = if (isActive) {
                        "已收到妙享中心的加载回报"
                    } else {
                        "尚未收到妙享中心的加载回报"
                    },
                )
            },
            trailingContent = environment?.let { snapshot ->
                {
                    Text(
                        text = "${snapshot.deviceName}\n${snapshot.androidVersion}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
        )
    }
}

@Composable
private fun NotificationSettingsCard(
    environment: EnvironmentSnapshot?,
    isBusy: Boolean,
    onOpenNotificationSettings: () -> Unit,
    onRequestRestart: () -> Unit,
) {
    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        ListItem(
            leadingContent = {
                Icon(
                    imageVector = Icons.Rounded.NotificationsActive,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
            },
            headlineContent = {
                Text("跨设备通知", fontWeight = FontWeight.SemiBold)
            },
            supportingContent = {
                Text(notificationStateSummary(environment))
            },
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Button(
                onClick = onOpenNotificationSettings,
                enabled = !isBusy,
                modifier = Modifier.weight(1f),
            ) {
                Icon(Icons.AutoMirrored.Rounded.OpenInNew, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("打开设置")
            }
            OutlinedButton(
                onClick = onRequestRestart,
                enabled = !isBusy,
                modifier = Modifier.weight(1f),
            ) {
                Icon(Icons.Rounded.RestartAlt, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("重启服务")
            }
        }
    }
}

@Composable
private fun EnvironmentCard(
    environment: EnvironmentSnapshot?,
    isLoading: Boolean,
) {
    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        if (environment == null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(12.dp))
                }
                Text("正在读取设备状态")
            }
            return@OutlinedCard
        }

        EnvironmentRow(
            icon = Icons.Rounded.Security,
            title = "Root",
            value = if (environment.isRootAvailable) "已授权" else "未授权",
            healthy = environment.isRootAvailable,
        )
        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
        EnvironmentRow(
            icon = Icons.Rounded.Devices,
            title = "互联服务",
            value = if (environment.isMiLinkInstalled) "已安装" else "未安装",
            healthy = environment.isMiLinkInstalled,
        )
        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
        EnvironmentRow(
            icon = Icons.Rounded.Devices,
            title = "妙享中心",
            value = if (environment.isMirrorInstalled) "已安装" else "未安装",
            healthy = environment.isMirrorInstalled,
        )
    }
}

@Composable
private fun EnvironmentRow(
    icon: ImageVector,
    title: String,
    value: String,
    healthy: Boolean,
) {
    val stateColor = if (healthy) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.error
    }

    ListItem(
        leadingContent = { Icon(icon, contentDescription = null) },
        headlineContent = { Text(title) },
        trailingContent = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(value, color = stateColor)
                Icon(
                    imageVector = if (healthy) Icons.Rounded.CheckCircle else Icons.Rounded.ErrorOutline,
                    contentDescription = null,
                    tint = stateColor,
                    modifier = Modifier.size(20.dp),
                )
            }
        },
    )
}

private fun notificationStateSummary(environment: EnvironmentSnapshot?): String {
    if (environment == null) return "正在读取状态"
    return "本机接收 ${environment.notificationReceiveState.shortLabel} · " +
        "同步本机 ${environment.notificationSendState.shortLabel}"
}

private val ToggleState.shortLabel: String
    get() = when (this) {
        ToggleState.ENABLED -> "已开启"
        ToggleState.DISABLED -> "未开启"
        ToggleState.UNKNOWN -> "未知"
    }
