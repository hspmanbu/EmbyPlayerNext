package com.aistudio.embyplayer.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aistudio.embyplayer.data.model.*
import com.aistudio.embyplayer.ui.components.HeroCard
import com.aistudio.embyplayer.ui.components.MediaCard
import com.aistudio.embyplayer.ui.components.ResumeCard

@Composable
fun HomeScreen(
    config: EmbyServerConfig,
    views: List<EmbyView>,
    resume: List<EmbyItem>,
    latest: List<EmbyItem>,
    favorites: List<EmbyItem>,
    loading: Boolean,
    imageFor: (EmbyItem, String) -> String?,
    onOpenItem: (EmbyItem) -> Unit,
    onOpenView: (EmbyView) -> Unit,
    onSearch: () -> Unit,
    onRefresh: () -> Unit,
    onLogin: () -> Unit
) {
    val hero = resume.firstOrNull() ?: latest.firstOrNull()
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 18.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp)
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(46.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            config.serverName.take(1).uppercase().ifBlank { "E" },
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(config.serverName, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            if (config.accessToken.isBlank()) "尚未连接 Emby" else "${config.username} · ${config.serverUrl}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    IconButton(onClick = onRefresh) { Icon(Icons.Default.Refresh, "刷新") }
                    if (config.accessToken.isBlank()) IconButton(onClick = onLogin) { Icon(Icons.Default.Login, "登录") }
                }

                Surface(
                    modifier = Modifier.fillMaxWidth().clickable(onClick = onSearch),
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh
                ) {
                    Row(Modifier.padding(horizontal = 16.dp, vertical = 13.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Search, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(10.dp))
                        Text("搜索电影、剧集、单集…", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                        Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            }
        }

        if (config.accessToken.isBlank()) {
            item {
                ElevatedCard(shape = RoundedCornerShape(24.dp)) {
                    Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Icon(Icons.Default.CloudOff, null, Modifier.size(42.dp), tint = MaterialTheme.colorScheme.primary)
                        Text("连接你的 Emby 服务器", style = MaterialTheme.typography.headlineMedium)
                        Text("登录后可浏览媒体库、继续观看、收藏内容并使用完整播放器。", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Button(onClick = onLogin) { Icon(Icons.Default.Login, null); Spacer(Modifier.width(8.dp)); Text("登录服务器") }
                    }
                }
            }
        } else {
            hero?.let { h ->
                item { HeroCard(h, imageFor(h, "Backdrop") ?: imageFor(h, "Primary"), { onOpenItem(h) }) }
            }

            if (resume.isNotEmpty()) {
                item { SectionTitle("继续观看", "${resume.size} 个未看完内容") }
                item {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(end = 6.dp)) {
                        items(resume, key = { it.id }) { i ->
                            ResumeCard(i, imageFor(i, "Backdrop") ?: imageFor(i, "Primary")) { onOpenItem(i) }
                        }
                    }
                }
            }

            if (latest.isNotEmpty()) {
                item { SectionTitle("最近入库", "最新添加到服务器") }
                item {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(end = 6.dp)) {
                        items(latest.take(20), key = { it.id }) { i ->
                            MediaCard(i, imageFor(i, "Primary"), { onOpenItem(i) }, Modifier.width(148.dp))
                        }
                    }
                }
            }

            if (favorites.isNotEmpty()) {
                item { SectionTitle("我的收藏", "快速回到喜欢的内容") }
                item {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(end = 6.dp)) {
                        items(favorites.take(20), key = { it.id }) { i ->
                            MediaCard(i, imageFor(i, "Primary"), { onOpenItem(i) }, Modifier.width(148.dp))
                        }
                    }
                }
            }

            if (views.isNotEmpty()) {
                item { SectionTitle("媒体库", "${views.size} 个资料库") }
                items(views.chunked(2), key = { row -> row.joinToString("|") { it.id } }) { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        row.forEach { v -> LibraryTile(v, { onOpenView(v) }, Modifier.weight(1f)) }
                        if (row.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(title: String, subtitle: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
        Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun LibraryTile(view: EmbyView, onClick: () -> Unit, modifier: Modifier = Modifier) {
    ElevatedCard(onClick = onClick, modifier = modifier, shape = RoundedCornerShape(20.dp)) {
        Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                Icon(
                    when (view.collectionType?.lowercase()) {
                        "tvshows" -> Icons.Default.Tv
                        "music" -> Icons.Default.LibraryMusic
                        "photos" -> Icons.Default.PhotoLibrary
                        else -> Icons.Default.Movie
                    },
                    null,
                    Modifier.padding(11.dp).size(25.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(view.name, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(view.collectionType ?: "媒体库", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}