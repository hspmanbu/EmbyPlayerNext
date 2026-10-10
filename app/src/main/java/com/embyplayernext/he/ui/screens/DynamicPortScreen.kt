package com.embyplayernext.he.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.embyplayernext.he.data.model.EmbyServerConfig
import com.embyplayernext.he.ui.DynamicPortStatus

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DynamicPortScreen(config:EmbyServerConfig,status:DynamicPortStatus,onBack:()->Unit,onSave:(EmbyServerConfig)->Unit,onTest:()->Unit) {
    var c by remember(config) { mutableStateOf(config) }
    Scaffold(topBar={TopAppBar(title={Text("动态端口解析")},navigationIcon={IconButton(onClick=onBack){Icon(Icons.Default.ArrowBack,null)}})}) { pad ->
        Column(Modifier.padding(pad).padding(18.dp).fillMaxSize(),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Text("针对无公网 IP 家庭服务器：连接超时时自动抓取指定页面中的最新端口，自动更换并重连。")
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Text("启用自动动态端口更新");Switch(c.dynamicPortEnabled,{c=c.copy(dynamicPortEnabled=it)})}
            OutlinedTextField(c.dynamicPortTargetDomain,{c=c.copy(dynamicPortTargetDomain=it)},label={Text("目标服务器域名包含")},supportingText={Text("可选；填写后仅对匹配该域名的服务器自动切换")},modifier=Modifier.fillMaxWidth())
            OutlinedTextField(c.dynamicPortFetchUrl,{c=c.copy(dynamicPortFetchUrl=it)},label={Text("端口抓取页面地址")},supportingText={Text("填写返回端口信息的 HTTP/HTTPS 地址")},modifier=Modifier.fillMaxWidth())
            OutlinedTextField(c.dynamicPortServiceName,{c=c.copy(dynamicPortServiceName=it)},label={Text("表格中的服务名称")},supportingText={Text("填写端口页面中用于匹配的服务名称")},modifier=Modifier.fillMaxWidth())
            OutlinedTextField(c.dynamicPortTimeoutSeconds.toString(),{v->v.toIntOrNull()?.let{c=c.copy(dynamicPortTimeoutSeconds=it.coerceIn(2,60))}},label={Text("连接超时时间阈值 (秒)")},modifier=Modifier.fillMaxWidth())
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) { Button(onClick={onSave(c)}){Text("保存动态端口配置")};OutlinedButton(onClick=onTest){Text("立即拉取并测试")}}
            when(status){DynamicPortStatus.Idle->Unit;DynamicPortStatus.Resolving->Text("正在拉取并解析端口...");is DynamicPortStatus.Success->Text("✓ 拉取成功！解析端口: ${status.port}",color=MaterialTheme.colorScheme.primary);is DynamicPortStatus.Error->Text("✗ 拉取失败: ${status.message}",color=MaterialTheme.colorScheme.error)}
            HorizontalDivider()
            if (c.serverUrl.isNotBlank()) {
                Text("当前服务器地址: ${c.serverUrl}", style = MaterialTheme.typography.bodySmall)
            } else {
                Text("当前未连接服务器（端口配置保存后，添加匹配服务器时将在首次连接时自动生效）", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}
