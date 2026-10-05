package com.embyplayernext.he.ui.screens

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.embyplayernext.he.ui.components.tvScrollThenFocus
import com.embyplayernext.he.data.model.EmbyServerConfig
import com.embyplayernext.he.data.model.SavedServerProfile

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    config: EmbyServerConfig,
    savedServers: List<SavedServerProfile> = emptyList(),
    onSwitchServer: (SavedServerProfile) -> Unit = {},
    onDeleteServer: (String) -> Unit = {},
    onUpdate: (EmbyServerConfig) -> Unit,
    onDynamic: () -> Unit,
    onLogin: () -> Unit,
    onLogout: () -> Unit,
    onExportLog: () -> Unit,
    onClearLog: () -> Unit,
    onBack: () -> Unit,
) {
    var logoutConfirm by remember { mutableStateOf(false) }
    val pageState = rememberLazyListState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("设置")
                        Text("播放、显示与服务器", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "返回") } },
            )
        },
    ) { padding ->
        LazyColumn(
            state = pageState,
            modifier = Modifier.fillMaxSize().padding(padding).tvScrollThenFocus(pageState),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            item {
                SettingSection("服务器与多账号管理", Icons.Default.Dns) {
                    if (savedServers.isNotEmpty()) {
                        savedServers.forEach { server ->
                            val isActive = server.serverUrl.trimEnd('/') == config.serverUrl.trimEnd('/') && server.username == config.username
                            SettingServerItem(
                                server = server,
                                isActive = isActive,
                                onSelect = { if (!isActive) onSwitchServer(server) },
                                onDelete = { onDeleteServer(server.id) },
                            )
                            HorizontalDivider()
                        }
                    }
                    SettingRow(
                        title = "添加 / 登录新服务器",
                        subtitle = "支持登录多个 Emby 服务器账号并自由切换",
                        icon = Icons.Default.AddCircleOutline,
                        onClick = onLogin,
                    )
                    HorizontalDivider()
                    SettingRow(
                        title = "动态端口",
                        subtitle = if (config.dynamicPortEnabled) "已启用 · ${config.dynamicPortTargetDomain.ifBlank { "全部服务器" }}" else "未启用",
                        icon = Icons.Default.SettingsEthernet,
                        onClick = onDynamic,
                    )
                    if (config.accessToken.isNotBlank()) {
                        HorizontalDivider()
                        SettingRow(
                            title = "退出当前登录",
                            subtitle = "清除当前服务器登录会话",
                            icon = Icons.Default.Logout,
                            onClick = { logoutConfirm = true },
                        )
                    }
                }
            }

            item {
                SettingSection("界面", Icons.Default.Tune) {
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("界面缩放", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "调整界面文字与控件大小。当前 ${(config.uiScale * 100).toInt()}%",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        ChoiceChips(
                            options = listOf(
                                .85f to "85%",
                                1f to "100%",
                                1.15f to "115%",
                                1.25f to "125%",
                                1.4f to "140%",
                                1.6f to "160%",
                                1.8f to "180%",
                                2f to "200%",
                                2.25f to "225%",
                                2.5f to "250%",
                            ),
                            selected = config.uiScale,
                        ) { onUpdate(config.copy(uiScale = it)) }
                    }
                }
            }

            item {
                SettingSection("播放与解码", Icons.Default.PlayCircle) {
                    SwitchRow(
                        "硬件加速解码",
                        "优先使用设备硬件解码，通常可获得更低的功耗和更流畅的播放",
                        config.hardwareDecoding,
                    ) { onUpdate(config.copy(hardwareDecoding = it)) }
                    HorizontalDivider()
                    SwitchRow(
                        "增强硬解兼容",
                        "用于部分老旧设备的硬件解码兼容。播放正常的设备无需开启",
                        config.hardwareCompatibilityMode,
                    ) { onUpdate(config.copy(hardwareCompatibilityMode = it)) }
                    HorizontalDivider()
                    SwitchRow(
                        "自动播放下一集",
                        "剧集单集播放完毕后，自动倒计时并无缝跳转至下一集播放",
                        config.autoPlayNextEpisode,
                    ) { onUpdate(config.copy(autoPlayNextEpisode = it)) }
                    HorizontalDivider()
                    IntChoice(
                        title = "后退步长",
                        current = config.rewindSeconds,
                        options = listOf(5, 10, 15, 20, 30, 45, 60, 90),
                        suffix = { "$it 秒" },
                    ) { onUpdate(config.copy(rewindSeconds = it)) }
                    HorizontalDivider()
                    IntChoice(
                        title = "前进步长",
                        current = config.forwardSeconds,
                        options = listOf(5, 10, 15, 20, 30, 45, 60, 90),
                        suffix = { "$it 秒" },
                    ) { onUpdate(config.copy(forwardSeconds = it)) }
                    HorizontalDivider()
                    IntChoice(
                        title = "全屏手势快进跨度",
                        current = config.gestureSeekSeconds,
                        options = listOf(60, 120, 180, 300, 600),
                        suffix = { value -> if (value < 60) "$value 秒" else "${value / 60} 分钟" },
                    ) { onUpdate(config.copy(gestureSeekSeconds = it)) }
                    HorizontalDivider()
                    IntChoice(
                        title = "本地磁盘缓冲",
                        current = config.cacheMb,
                        options = listOf(0, 64, 128, 256, 512),
                        suffix = { if (it == 0) "关闭" else "$it MB" },
                    ) { onUpdate(config.copy(cacheMb = it)) }
                }
            }

            item {
                SettingSection("网络与兼容性", Icons.Default.NetworkCheck) {
                    SwitchRow(
                        "允许自签名 HTTPS",
                        "仅在你明确使用家庭自签名证书时开启",
                        config.allowInsecureHttps,
                    ) { onUpdate(config.copy(allowInsecureHttps = it)) }
                }
            }

            item {
                SettingSection("帮助与关于", Icons.Default.Info) {
                    SettingRow("导出运行日志", "将运行日志保存到下载目录", Icons.Default.Download, onExportLog)
                    HorizontalDivider()
                    SettingRow("清除运行日志", "删除本机已记录的运行日志", Icons.Default.DeleteSweep, onClearLog)
                    HorizontalDivider()
                    ListItem(
                        headlineContent = { Text("EmbyPlayerNext 2.3.27") },
                        supportingContent = { Text("Android / Android TV") },
                        leadingContent = { Icon(Icons.Default.Info, null) },
                    )
                }
            }

            item { Spacer(Modifier.height(20.dp)) }
        }
    }

    if (logoutConfirm) {
        AlertDialog(
            onDismissRequest = { logoutConfirm = false },
            title = { Text("确认退出登录？") },
            text = { Text("退出后将清除登录 Token，下次使用需要重新登录服务器。") },
            confirmButton = {
                Button(onClick = { logoutConfirm = false; onLogout() }) { Text("退出") }
            },
            dismissButton = { TextButton(onClick = { logoutConfirm = false }) { Text("取消") } },
        )
    }
}

@Composable
private fun SettingSection(title: String, icon: ImageVector, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            Text(title, style = MaterialTheme.typography.titleLarge)
        }
        Surface(
            shape = RoundedCornerShape(22.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
            content = { Column(content = content) },
        )
    }
}

@Composable
private fun SettingRow(title: String, subtitle: String, icon: ImageVector, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant) },
        leadingContent = { Icon(icon, null) },
        trailingContent = { Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = Modifier.clickable(onClick = onClick),
    )
}

@Composable
private fun SwitchRow(title: String, subtitle: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant) },
        trailingContent = { Switch(checked = checked, onCheckedChange = onChecked) },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    )
}

@Composable
private fun <T> ChoiceChips(options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        options.forEach { (value, label) ->
            FilterChip(selected = value == selected, onClick = { onSelect(value) }, label = { Text(label) })
        }
    }
}

@Composable
private fun IntChoice(
    title: String,
    current: Int,
    options: List<Int>,
    suffix: (Int) -> String = { it.toString() },
    onSelect: (Int) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Box {
        ListItem(
            headlineContent = { Text(title) },
            supportingContent = { Text(suffix(current), color = MaterialTheme.colorScheme.onSurfaceVariant) },
            trailingContent = { Icon(Icons.Default.ExpandMore, null) },
            colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            modifier = Modifier.clickable { open = true },
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { value ->
                DropdownMenuItem(
                    text = { Text(suffix(value)) },
                    onClick = { open = false; onSelect(value) },
                    trailingIcon = {
                        if (value == current) Icon(Icons.Default.Check, null, tint = MaterialTheme.colorScheme.primary)
                    },
                )
            }
        }
    }
}

@Composable
private fun SettingServerItem(
    server: SavedServerProfile,
    isActive: Boolean,
    onSelect: () -> Unit,
    onDelete: () -> Unit,
) {
    ListItem(
        headlineContent = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(server.serverName, fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal)
                if (isActive) {
                    Surface(shape = RoundedCornerShape(4.dp), color = MaterialTheme.colorScheme.primary) {
                        Text(
                            "当前使用",
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        },
        supportingContent = {
            Text(
                "${server.username.ifBlank { "未命名用户" }} · ${server.serverUrl}",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (!isActive && server.accessToken.isNotBlank()) {
                    TextButton(onClick = onSelect) { Text("切换") }
                }
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "删除服务器",
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f)
                    )
                }
            }
        },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = if (!isActive) Modifier.clickable(onClick = onSelect) else Modifier
    )
}

