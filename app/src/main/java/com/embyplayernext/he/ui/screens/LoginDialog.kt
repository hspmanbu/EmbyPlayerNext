package com.embyplayernext.he.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Dns
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
    onTest: (String) -> Unit,
) {
    var server by remember(config.serverUrl) { mutableStateOf(config.serverUrl) }
    var user by remember(config.username) { mutableStateOf(config.username) }
    var pass by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.Dns, null, tint = MaterialTheme.colorScheme.primary)
                Text("连接 Emby 服务器")
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
                            val isActive = s.serverUrl.trimEnd('/') == config.serverUrl.trimEnd('/') && s.username == config.username
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (isActive) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                                       else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        server = s.serverUrl
                                        user = s.username
                                        if (s.accessToken.isNotBlank() && !isActive) {
                                            onQuickSwitch(s)
                                        }
                                    }
                            ) {
                                Row(
                                    Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(Modifier.weight(1f)) {
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            Text(s.serverName, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            if (isActive) {
                                                Text("[当前]", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                        Text(
                                            "${s.username.ifBlank { "未命名用户" }} · ${s.serverUrl}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    if (s.accessToken.isNotBlank() && !isActive) {
                                        TextButton(onClick = { onQuickSwitch(s) }) {
                                            Text("切换")
                                        }
                                    }
                                    IconButton(onClick = { onDeleteServer(s.id) }) {
                                        Icon(Icons.Default.Delete, "删除", modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f))
                                    }
                                }
                            }
                        }
                    }
                    HorizontalDivider(Modifier.padding(vertical = 4.dp))
                    Text("添加新服务器 / 重新登录", style = MaterialTheme.typography.labelLarge)
                }

                OutlinedTextField(
                    value = server,
                    onValueChange = { server = it },
                    label = { Text("服务器地址 (如 http://192.168.1.100:8096)") },
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
                TextButton(onClick = { onTest(server) }) {
                    Text("测试服务器联通性")
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
