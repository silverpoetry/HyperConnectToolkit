package com.weichao.hyperconnecttoolkit.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Devices
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.ScreenRotation
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.weichao.hyperconnecttoolkit.config.Feature
import com.weichao.hyperconnecttoolkit.config.FeatureConfiguration
import com.weichao.hyperconnecttoolkit.platform.HookRuntimeState

@Composable
internal fun FeatureControlsCard(
    configuration: FeatureConfiguration,
    hookRuntime: HookRuntimeState,
    pendingRestartFeatures: Set<Feature>,
    isBusy: Boolean,
    onFeatureChanged: (Feature, Boolean) -> Unit,
) {
    val settings = configuration.settings
    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "功能",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp),
        )
        FeatureSwitchRow(
            feature = Feature.CROSS_DEVICE_NOTIFICATION,
            icon = Icons.Rounded.NotificationsActive,
            title = "解锁跨设备通知流转",
            summary = "设备发现与通知点击串流",
            targetApplication = "MiLink Notification",
            checked = settings.crossDeviceNotification,
            hookRuntime = hookRuntime,
            pendingRestartFeatures = pendingRestartFeatures,
            enabled = !isBusy,
            onFeatureChanged = onFeatureChanged,
        )
        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
        FeatureSwitchRow(
            feature = Feature.PORTRAIT_STREAMING,
            icon = Icons.Rounded.ScreenRotation,
            title = "允许平板竖屏流转应用",
            summary = "自由旋转与首包画面尺寸校正",
            targetApplication = "Xiaomi Mirror",
            checked = settings.portraitStreaming,
            hookRuntime = hookRuntime,
            pendingRestartFeatures = pendingRestartFeatures,
            enabled = !isBusy,
            onFeatureChanged = onFeatureChanged,
        )
        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
        FeatureSwitchRow(
            feature = Feature.MILINK_MULTI_CHANNEL,
            icon = Icons.Rounded.Devices,
            title = "MiLink Multi-Channel",
            summary = "支持手机与多台平板建立流转，需在手机上开启此选项",
            targetApplication = "MiLink Core",
            checked = settings.miLinkMultiChannel,
            hookRuntime = hookRuntime,
            pendingRestartFeatures = pendingRestartFeatures,
            enabled = !isBusy,
            onFeatureChanged = onFeatureChanged,
        )

        ConfigurationStatus(configuration, pendingRestartFeatures)
    }
}

@Composable
private fun FeatureSwitchRow(
    feature: Feature,
    icon: ImageVector,
    title: String,
    summary: String,
    targetApplication: String,
    checked: Boolean,
    hookRuntime: HookRuntimeState,
    pendingRestartFeatures: Set<Feature>,
    enabled: Boolean,
    onFeatureChanged: (Feature, Boolean) -> Unit,
) {
    val applicationState = FeatureApplicationState.resolve(
        enabled = checked,
        applied = hookRuntime.isApplied(feature),
        pendingRestart = feature in pendingRestartFeatures,
    )
    val stateColor = when {
        applicationState.isPending -> MaterialTheme.colorScheme.tertiary
        applicationState.isAttentionRequired -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    ListItem(
        modifier = Modifier.clickable(enabled = enabled) {
            onFeatureChanged(feature, !checked)
        },
        leadingContent = {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        },
        headlineContent = { Text(title) },
        supportingContent = {
            Column {
                Text(summary)
                Text(
                    text = "$targetApplication · ${applicationState.label}",
                    style = MaterialTheme.typography.labelMedium,
                    color = stateColor,
                )
            }
        },
        trailingContent = {
            Switch(
                checked = checked,
                onCheckedChange = { onFeatureChanged(feature, it) },
                enabled = enabled,
            )
        },
    )
}

@Composable
private fun ConfigurationStatus(
    configuration: FeatureConfiguration,
    pendingRestartFeatures: Set<Feature>,
) {
    val message = when {
        configuration.hasPendingRemoteWrite -> "配置待同步到 LSPosed"
        !configuration.isRemoteAvailable -> "LSPosed 配置服务未连接"
        pendingRestartFeatures.isNotEmpty() -> "重启服务后应用更改"
        else -> null
    } ?: return

    Text(
        text = message,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
    )
}
