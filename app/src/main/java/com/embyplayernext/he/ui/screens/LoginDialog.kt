package com.embyplayernext.he.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.SettingsEthernet
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
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

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.Dns, null, tint = MaterialTheme.colorScheme.primary)
                    Text(if (savedServers.isEmpty()) "连接 Emby 服务器" else "服务器与账号管理", maxLines = 1, overflow = TextOverflow.Ellipsis)
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
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (savedServers.isNotEmpty()) {
                    Text("已保存的服务器及账号", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        savedServers.forEach { s ->
                            val isActive = s.serverUrl.trimEnd('/') == config.serverUrl.trimEnd('/') && s.username.equals(config.username, ignoreCase = true)
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (isActive) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                                       else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(
                                        Modifier
                                            .weight(1f)
                                            .clickable {
                                                server = s.serverUrl
                                                user = ""
                                                pass = ""
                                            }
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            Text(s.serverName, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            if (isActive) {
                                                Surface(shape = RoundedCornerShape(4.dp), color = MaterialTheme.colorScheme.primary) {
                                                    Text("当前", modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold)
                                                }
                                            }
                                        }
                                        Text(
                                            "账号: ${s.username.ifBlank { "未命名" }} · ${s.serverUrl}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    if (s.accessToken.isNotBlank() && !isActive) {
                                        Button(
                                            onClick = { onQuickSwitch(s) },
                                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                            modifier = Modifier.padding(end = 4.dp)
                                        ) {
                                            Text("切换")
                                        }
                                    }
                                    TextButton(
                                        onClick = {
                                            server = s.serverUrl
                                            user = ""
                                            pass = ""
                                        },
                                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                                    ) {
                                        Text("填入地址", style = MaterialTheme.typography.labelSmall)
                                    }
                                    IconButton(onClick = { onDeleteServer(s.id) }) {
                                        Icon(Icons.Default.Delete, "删除", modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f))
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
        },
        confirmButton = {
            Button(
                onClick = { onLogin(server, user, pass) },
                enabled = server.isNotBlank() && user.isNotBlank()
            ) {
                Text("连接登录")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}
