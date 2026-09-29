package com.embyplayernext.he.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.embyplayernext.he.data.model.EmbyServerConfig

@Composable
fun LoginDialog(config: EmbyServerConfig, onDismiss: () -> Unit, onLogin: (String,String,String)->Unit, onTest: (String)->Unit) {
    var server by remember(config.serverUrl) { mutableStateOf(config.serverUrl) }
    var user by remember(config.username) { mutableStateOf(config.username) }
    var pass by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("连接 Emby 服务器") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(server, { server = it }, label = { Text("服务器地址") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(user, { user = it }, label = { Text("用户名") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(pass, { pass = it }, label = { Text("密码") }, singleLine = true, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
                TextButton(onClick = { onTest(server) }) { Text("测试服务器联通性") }
            }
        },
        confirmButton = { Button(onClick = { onLogin(server,user,pass) }, enabled = server.isNotBlank() && user.isNotBlank()) { Text("连接登录") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}
