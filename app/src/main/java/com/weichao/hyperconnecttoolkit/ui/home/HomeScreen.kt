@file:OptIn(ExperimentalMaterial3Api::class)

package com.weichao.hyperconnecttoolkit.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.AdminPanelSettings
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Devices
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.HighQuality
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.ScreenRotation
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
                Text("这会结束当前手机串流，并重启妙享中心与互联服务进程。重新进入功能后，新钩子才会完整载入。")
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
                title = {
                    Column {
                        Text("互联增强")
                        Text(
                            text = "HyperOS 平板兼容工具",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
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
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
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
                    .widthIn(max = 920.dp),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                item {
                    ModuleStatusCard(
                        isActive = state.isModuleActive,
                        environment = state.environment,
                    )
                }

                item { SectionTitle("快捷操作", "进入系统原本隐藏的功能，或让作用域重新加载") }

                item {
                    QuickActionsCard(
                        isBusy = state.isActionRunning,
                        onOpenNotificationSettings = onOpenNotificationSettings,
                        onRequestRestart = onRequestRestart,
                    )
                }

                item { SectionTitle("已集成补丁", "所有兼容逻辑集中在一个最小作用域模块中") }

                item {
                    BoxWithConstraints(Modifier.fillMaxWidth()) {
                        if (maxWidth >= 700.dp) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                PatchCard(
                                    modifier = Modifier.weight(1f),
                                    icon = Icons.Rounded.NotificationsActive,
                                    title = "跨设备通知",
                                    description = "解除平板能力误判，恢复设备发现、通知点击串流和冷启动认证检查。",
                                )
                                PatchCard(
                                    modifier = Modifier.weight(1f),
                                    icon = Icons.Rounded.ScreenRotation,
                                    title = "竖屏串流",
                                    description = "允许完整方向传感，并在远端创建、认证结束和首帧阶段校准画面尺寸。",
                                )
                                PatchCard(
                                    modifier = Modifier.weight(1f),
                                    icon = Icons.Rounded.HighQuality,
                                    title = "120 Hz",
                                    description = "将虚拟显示、流配置和编码上限从 60 fps 一致提升到 120 fps。",
                                )
                            }
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                PatchCard(
                                    icon = Icons.Rounded.NotificationsActive,
                                    title = "跨设备通知",
                                    description = "解除平板能力误判，恢复设备发现、通知点击串流和冷启动认证检查。",
                                )
                                PatchCard(
                                    icon = Icons.Rounded.ScreenRotation,
                                    title = "竖屏串流",
                                    description = "允许完整方向传感，并在远端创建、认证结束和首帧阶段校准画面尺寸。",
                                )
                                PatchCard(
                                    icon = Icons.Rounded.HighQuality,
                                    title = "120 Hz",
                                    description = "将虚拟显示、流配置和编码上限从 60 fps 一致提升到 120 fps。",
                                )
                            }
                        }
                    }
                }

                item { SectionTitle("环境诊断", "状态来自当前设备，不修改系统开关") }

                item {
                    EnvironmentCard(
                        environment = state.environment,
                        isLoading = state.isRefreshing,
                    )
                }

                item {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        shape = MaterialTheme.shapes.large,
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.Top,
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Security,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                            )
                            Text(
                                text = "模块只作用于互联服务、妙享中心和自身状态检测；root 操作均为代码中固定的组件启动、状态读取与进程重启命令。",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
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
    val containerColor = if (isActive) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.errorContainer
    }
    val contentColor = if (isActive) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onErrorContainer
    }

    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.elevatedCardColors(containerColor = containerColor),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 1.dp),
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .background(
                        color = contentColor.copy(alpha = 0.12f),
                        shape = MaterialTheme.shapes.large,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = if (isActive) Icons.Rounded.CheckCircle else Icons.Rounded.WarningAmber,
                    contentDescription = null,
                    tint = contentColor,
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (isActive) "LSPosed 模块已生效" else "等待 LSPosed 激活",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = contentColor,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = if (isActive) {
                        "跨设备通知与横竖屏补丁已在当前进程加载"
                    } else {
                        "安装后请在 LSPosed 启用模块及推荐作用域，再重启目标服务"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = contentColor.copy(alpha = 0.82f),
                )
                environment?.let {
                    Spacer(Modifier.height(10.dp))
                    AssistChip(
                        onClick = {},
                        label = { Text("${it.deviceName} · ${it.androidVersion}") },
                        leadingIcon = {
                            Icon(
                                Icons.Rounded.Devices,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun QuickActionsCard(
    isBusy: Boolean,
    onOpenNotificationSettings: () -> Unit,
    onRequestRestart: () -> Unit,
) {
    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Rounded.AdminPanelSettings,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp),
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "互联中心 · 跨设备通知",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = "打开 HyperOS 隐藏的“本机接收通知 / 同步本机通知”双标签页。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(20.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Button(
                    onClick = onOpenNotificationSettings,
                    enabled = !isBusy,
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.AutoMirrored.Rounded.OpenInNew, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("打开跨设备通知")
                }
                FilledTonalButton(
                    onClick = onRequestRestart,
                    enabled = !isBusy,
                ) {
                    Icon(Icons.Rounded.RestartAlt, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("重启服务")
                }
            }
        }
    }
}

@Composable
private fun PatchCard(
    icon: ImageVector,
    title: String,
    description: String,
    modifier: Modifier = Modifier,
) {
    ElevatedCard(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 1.dp),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp),
            )
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
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
                Text("正在读取设备环境…")
            }
            return@OutlinedCard
        }

        EnvironmentRow(
            icon = Icons.Rounded.Security,
            title = "Root 权限",
            value = if (environment.isRootAvailable) "可用" else "未授权（请在 KernelSU 中允许）",
            healthy = environment.isRootAvailable,
        )
        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
        EnvironmentRow(
            icon = Icons.Rounded.Devices,
            title = "互联组件",
            value = when {
                environment.isMiLinkInstalled && environment.isMirrorInstalled -> "MiLink 与 Mirror 已安装"
                else -> "系统组件不完整"
            },
            healthy = environment.isMiLinkInstalled && environment.isMirrorInstalled,
        )
        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
        EnvironmentRow(
            icon = Icons.Rounded.NotificationsActive,
            title = "本机接收通知",
            value = environment.notificationReceiveState.label,
            healthy = environment.notificationReceiveState == ToggleState.ENABLED,
            unknownIsNeutral = environment.notificationReceiveState == ToggleState.UNKNOWN,
        )
        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
        EnvironmentRow(
            icon = Icons.AutoMirrored.Rounded.OpenInNew,
            title = "同步本机通知",
            value = environment.notificationSendState.label,
            healthy = environment.notificationSendState == ToggleState.ENABLED,
            unknownIsNeutral = environment.notificationSendState == ToggleState.UNKNOWN,
        )
    }
}

@Composable
private fun EnvironmentRow(
    icon: ImageVector,
    title: String,
    value: String,
    healthy: Boolean,
    unknownIsNeutral: Boolean = false,
) {
    val stateColor = when {
        unknownIsNeutral -> MaterialTheme.colorScheme.onSurfaceVariant
        healthy -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.error
    }
    ListItem(
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        leadingContent = { Icon(icon, contentDescription = null) },
        headlineContent = { Text(title) },
        supportingContent = { Text(value, color = stateColor) },
        trailingContent = {
            Icon(
                imageVector = when {
                    unknownIsNeutral -> Icons.Rounded.ErrorOutline
                    healthy -> Icons.Rounded.CheckCircle
                    else -> Icons.Rounded.WarningAmber
                },
                contentDescription = null,
                tint = stateColor,
            )
        },
    )
}

@Composable
private fun SectionTitle(title: String, supporting: String) {
    Column(
        modifier = Modifier.padding(top = 8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = supporting,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private val ToggleState.label: String
    get() = when (this) {
        ToggleState.ENABLED -> "已开启"
        ToggleState.DISABLED -> "未开启"
        ToggleState.UNKNOWN -> "系统未返回状态"
    }
