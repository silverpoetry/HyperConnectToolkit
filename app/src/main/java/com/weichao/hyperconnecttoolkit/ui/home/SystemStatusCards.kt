package com.weichao.hyperconnecttoolkit.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Devices
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.weichao.hyperconnecttoolkit.domain.model.EnvironmentSnapshot

@Composable
internal fun NotificationSettingsCard(
    isBusy: Boolean,
    onOpenNotificationSettings: () -> Unit,
    onRequestRestart: () -> Unit,
) {
    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        ListItem(
            leadingContent = {
                Icon(
                    Icons.Rounded.NotificationsActive,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
            },
            headlineContent = {
                Text("系统跨设备流转设置页", fontWeight = FontWeight.SemiBold)
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
internal fun EnvironmentCard(
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
            title = "MiLink",
            value = if (environment.isMiLinkInstalled) "已安装" else "未安装",
            healthy = environment.isMiLinkInstalled,
        )
        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
        EnvironmentRow(
            icon = Icons.Rounded.Devices,
            title = "Xiaomi Mirror",
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
                    imageVector = if (healthy) {
                        Icons.Rounded.CheckCircle
                    } else {
                        Icons.Rounded.ErrorOutline
                    },
                    contentDescription = null,
                    tint = stateColor,
                    modifier = Modifier.size(20.dp),
                )
            }
        },
    )
}
