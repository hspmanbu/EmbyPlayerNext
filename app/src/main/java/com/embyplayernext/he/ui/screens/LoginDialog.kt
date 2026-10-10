package com.embyplayernext.he.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.SettingsEthernet
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.embyplayernext.he.data.model.EmbyServerConfig
import com.embyplayernext.he.data.model.SavedServerProfile

@Composable
fun LoginDialog(
    config: EmbyServerConfig,
    savedServers: List<SavedServerProfile> = emptyList(),
    onDismiss: () -> Unit,
    onLogin: (String, String, String) -> Unit,
    onQuickSwitch: (SavedServerProfile) -> Unit = {},
    onDeleteServer: (String) -> Unit = {},
    onOpenDynamicPort: () -> Unit = {},
    onTest: (String, (String) -> Unit) -> Unit,
) {
    var server by remember { mutableStateOf(if (savedServers.isEmpty()) config.serverUrl else "") }
    var user by remember { mutableStateOf(if (savedServers.isEmpty()) config.username else "") }
    var pass by remember { mutableStateOf("") }

    val baseDensity = LocalDensity.current
    val factor = config.uiScale.coerceIn(0.5f, 2.5f)
    val scaledDensity = remember(baseDensity, factor) {
        Density(
            density = baseDensity.density * factor,
            fontScale = baseDensity.fontScale * factor,
        )
    }

    CompositionLocalProvider(LocalDensity provides scaledDensity) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = {
                CompositionLocalProvider(LocalDensity provides scaledDensity) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Dns, null, tint = MaterialTheme.colorScheme.primary)
                            Text(
                                if (savedServers.isEmpty()) "连接 Emby 服务器" else "服务器与账号管理",
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        OutlinedButton(
                            onClick = onOpenDynamicPort,
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Icon(Icons.Default.SettingsEthernet, null, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("动态端口", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            },
            text = {
                CompositionLocalProvider(LocalDensity provides scaledDensity) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        if (savedServers.isNotEmpty()) {
                            Text(
                                "已保存的服务器及账号",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                savedServers.forEach { s ->
                                    val isActive = s.serverUrl.trimEnd('/') == config.serverUrl.trimEnd('/') &&
                                            s.username.equals(config.username, ignoreCase = true)
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = if (isActive) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                                               else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                        border = if (isActive) BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else null,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                server = s.serverUrl
                                                user = s.username
                                                pass = ""
                                            }
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(10.dp),
                                            verticalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                                    modifier = Modifier.weight(1f)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.AccountCircle,
                                                        contentDescription = null,
                                                        tint = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                                        modifier = Modifier.size(22.dp)
                                                    )
                                                    Text(
                                                        text = s.username.ifBlank { "未命名用户" },
                                                        style = MaterialTheme.typography.titleMedium,
                                                        fontWeight = FontWeight.Bold,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                    if (isActive) {
                                                        Surface(
                                                            shape = RoundedCornerShape(4.dp),
                                                            color = MaterialTheme.colorScheme.primary
                                                        ) {
                                                            Text(
                                                                "当前使用",
                                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                                                                style = MaterialTheme.typography.labelSmall,
                                                                color = MaterialTheme.colorScheme.onPrimary,
                                                                fontWeight = FontWeight.Bold
                                                            )
                                                        }
                                                    }
                                                    if (s.serverName.isNotBlank() && s.serverName != "Emby Server") {
                                                        Surface(
                                                            shape = RoundedCornerShape(4.dp),
                                                            color = MaterialTheme.colorScheme.secondaryContainer
                                                        ) {
                                                            Text(
                                                                s.serverName,
                                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                                                                style = MaterialTheme.typography.labelSmall,
                                                                color = MaterialTheme.colorScheme.onSecondaryContainer
                                                            )
                                                        }
                                                    }
                                                }

                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                                ) {
                                                    if (s.accessToken.isNotBlank() && !isActive) {
                                                        Button(
                                                            onClick = { onQuickSwitch(s) },
                                                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                                            modifier = Modifier.height(30.dp)
                                                        ) {
                                                            Text("切换", style = MaterialTheme.typography.labelSmall)
                                                        }
                                                    }
                                                    IconButton(
                                                        onClick = { onDeleteServer(s.id) },
                                                        modifier = Modifier.size(30.dp)
                                                    ) {
                                                        Icon(
                                                            Icons.Default.Delete,
                                                            contentDescription = "删除",
                                                            modifier = Modifier.size(16.dp),
                                                            tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f)
                                                        )
                                                    }
                                                }
                                            }

                                            Surface(
                                                shape = RoundedCornerShape(6.dp),
                                                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Dns,
                                                        contentDescription = null,
                                                        modifier = Modifier.size(14.dp),
                                                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                                                    )
                                                    Spacer(Modifier.width(6.dp))
                                                    Text(
                                                        text = s.serverUrl,
                                                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                                        color = MaterialTheme.colorScheme.onSurface,
                                                        maxLines = 2,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                            HorizontalDivider(Modifier.padding(vertical = 4.dp))
                            Text("添加新账号 / 登录新服务器", style = MaterialTheme.typography.labelLarge)
                        }

                        OutlinedTextField(
                            value = server,
                            onValueChange = { server = it },
                            label = { Text("服务器地址 (如 http://192.168.1.100:8096)") },
                            supportingText = { Text("若配置了动态端口，首次连接失败时将自动抓取最新端口重连") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = user,
                            onValueChange = { user = it },
                            label = { Text("用户名") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = pass,
                            onValueChange = { pass = it },
                            label = { Text("密码") },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(onClick = {
                                onTest(server) { updatedUrl ->
                                    server = updatedUrl
                                }
                            }) {
                                Text("测试服务器联通性")
                            }
                            if (config.dynamicPortEnabled || config.dynamicPortFetchUrl.isNotBlank()) {
                                Text(
                                    "已启用动态端口自动匹配",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                CompositionLocalProvider(LocalDensity provides scaledDensity) {
                    Button(
                        onClick = { onLogin(server, user, pass) },
                        enabled = server.isNotBlank() && user.isNotBlank()
                    ) {
                        Text("连接登录")
                    }
                }
            },
            dismissButton = {
                CompositionLocalProvider(LocalDensity provides scaledDensity) {
                    TextButton(onClick = onDismiss) {
                        Text("取消")
                    }
                }
            }
        )
    }
}
