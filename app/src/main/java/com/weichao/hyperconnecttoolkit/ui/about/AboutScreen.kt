@file:OptIn(ExperimentalMaterial3Api::class)

package com.weichao.hyperconnecttoolkit.ui.about

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.PrivacyTip
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.weichao.hyperconnecttoolkit.BuildConfig
import com.weichao.hyperconnecttoolkit.app.ProjectMetadata

@Composable
fun AboutRoute(
    onNavigateUp: () -> Unit,
) {
    val uriHandler = LocalUriHandler.current
    AboutScreen(
        onNavigateUp = onNavigateUp,
        onOpenRepository = { uriHandler.openUri(ProjectMetadata.REPOSITORY_URL) },
    )
}

@Composable
private fun AboutScreen(
    onNavigateUp: () -> Unit,
    onOpenRepository: () -> Unit,
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        topBar = {
            TopAppBar(
                title = { Text("关于") },
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(
                            Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "返回",
                        )
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
                    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                        ListItem(
                            leadingContent = {
                                Icon(
                                    Icons.Rounded.Info,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            },
                            headlineContent = {
                                Text("HyperConnect Toolkit", fontWeight = FontWeight.SemiBold)
                            },
                            supportingContent = {
                                Text("版本 ${BuildConfig.VERSION_NAME}（${BuildConfig.VERSION_CODE}）")
                            },
                        )
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                        ListItem(
                            modifier = Modifier.clickable(onClick = onOpenRepository),
                            leadingContent = {
                                Icon(
                                    Icons.Rounded.Code,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            },
                            headlineContent = { Text("GitHub") },
                            supportingContent = { Text(ProjectMetadata.REPOSITORY_URL) },
                            trailingContent = {
                                Icon(
                                    Icons.AutoMirrored.Rounded.OpenInNew,
                                    contentDescription = "在浏览器中打开",
                                )
                            },
                        )
                    }
                }
                item {
                    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                        ListItem(
                            leadingContent = {
                                Icon(
                                    Icons.Rounded.VerifiedUser,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            },
                            headlineContent = { Text("开源许可") },
                            supportingContent = { Text(ProjectMetadata.LICENSE_NAME) },
                        )
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                        ListItem(
                            leadingContent = {
                                Icon(
                                    Icons.Rounded.PrivacyTip,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            },
                            headlineContent = { Text("隐私") },
                            supportingContent = {
                                Text("不申请网络权限；不包含分析、遥测或远程崩溃上报。")
                            },
                        )
                    }
                }
            }
        }
    }
}
