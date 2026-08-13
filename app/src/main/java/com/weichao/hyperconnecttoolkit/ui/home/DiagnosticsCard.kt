package com.weichao.hyperconnecttoolkit.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.weichao.hyperconnecttoolkit.config.FeatureSettings

@Composable
internal fun DiagnosticsCard(
    settings: FeatureSettings,
    isRestartPending: Boolean,
    canExport: Boolean,
    isBusy: Boolean,
    onDetailedLoggingChanged: (Boolean) -> Unit,
    onExportDiagnostics: () -> Unit,
) {
    OutlinedCard {
        ListItem(
            modifier = Modifier.clickable(enabled = !isBusy) {
                onDetailedLoggingChanged(!settings.detailedLogging)
            },
            leadingContent = {
                Icon(
                    Icons.Rounded.BugReport,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
            },
            headlineContent = { Text("记录详细日志") },
            supportingContent = {
                Column {
                    Text("记录设备发现判定链")
                    Text("开启后重启服务并复现；LSPosed 需输出日志到守护进程")
                    if (isRestartPending) {
                        Text(
                            "重启服务后生效",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.tertiary,
                        )
                    }
                }
            },
            trailingContent = {
                Switch(
                    checked = settings.detailedLogging,
                    enabled = !isBusy,
                    onCheckedChange = onDetailedLoggingChanged,
                )
            },
        )
        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
        ListItem(
            leadingContent = {
                Icon(
                    Icons.Rounded.FileDownload,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
            },
            headlineContent = { Text("导出诊断日志") },
            supportingContent = {
                Text(
                    if (canExport) {
                        "关键组件版本、LSPosed 模块日志与应用日志"
                    } else {
                        "读取 LSPosed 模块日志需要 Root 权限"
                    },
                )
            },
            trailingContent = {
                Button(
                    onClick = onExportDiagnostics,
                    enabled = canExport && !isBusy,
                ) {
                    Text("导出")
                }
            },
        )
    }
}
