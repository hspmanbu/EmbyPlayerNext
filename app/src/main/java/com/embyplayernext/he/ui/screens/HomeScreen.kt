package com.embyplayernext.he.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import com.embyplayernext.he.ui.components.tvScrollThenFocus
import com.embyplayernext.he.data.model.*
import com.embyplayernext.he.ui.components.LibraryCoverCard
import com.embyplayernext.he.ui.components.MediaCard
import com.embyplayernext.he.ui.components.ResumeCard

@Composable
fun HomeScreen(
    config: EmbyServerConfig,
    views: List<EmbyView>,
    resume: List<EmbyItem>,
    recentPlayed: List<EmbyItem>,
    latest: List<EmbyItem>,
    favorites: List<EmbyItem>,
    loading: Boolean,
    imageFor: (EmbyItem, String) -> String?,
    viewImageFor: (EmbyView) -> String?,
    onOpenItem: (EmbyItem) -> Unit,
    onResumeItem: (EmbyItem) -> Unit,
    onOpenView: (EmbyView) -> Unit,
    onLibraries: () -> Unit,
    onSearch: () -> Unit,
    onRefresh: () -> Unit,
    onLogin: () -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth >= 840.dp
        val horizontalPadding = if (wide) 30.dp else 18.dp
        val resumeWidth = if (wide) 340.dp else 300.dp
        val libraryWidth = if (wide) 270.dp else 232.dp
        val pageState = rememberLazyListState()

        LazyColumn(
            state = pageState,
            modifier = Modifier.fillMaxSize().statusBarsPadding().widthIn(max = 1440.dp).align(Alignment.TopCenter).tvScrollThenFocus(pageState),
            contentPadding = PaddingValues(start = horizontalPadding, end = horizontalPadding, top = 18.dp, bottom = 36.dp),
            verticalArrangement = Arrangement.spacedBy(if (wide) 28.dp else 24.dp),
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(if (wide) 54.dp else 48.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                config.serverName.take(1).uppercase().ifBlank { "E" },
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleLarge,
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                if (config.accessToken.isBlank()) "欢迎使用 EmbyPlayerNext" else "你好，${config.username.ifBlank { "Emby 用户" }}",
                                style = if (wide) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.titleLarge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                if (config.accessToken.isBlank()) "连接服务器后开始浏览" else config.serverName,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        IconButton(onClick = onRefresh) { Icon(Icons.Default.Refresh, "刷新") }
                        if (config.accessToken.isBlank()) IconButton(onClick = onLogin) { Icon(Icons.Default.Login, "登录") }
                    }

                    Surface(
                        modifier = Modifier.fillMaxWidth().clickable(onClick = onSearch),
                        shape = RoundedCornerShape(18.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    ) {
                        Row(
                            Modifier.padding(horizontal = 16.dp, vertical = if (wide) 16.dp else 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Default.Search, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.width(10.dp))
                            Text("搜索电影、剧集、音乐、照片…", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                            Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
                }
            }

            if (config.accessToken.isBlank()) {
                item {
                    ElevatedCard(shape = RoundedCornerShape(26.dp)) {
                        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Icon(Icons.Default.CloudOff, null, Modifier.size(44.dp), tint = MaterialTheme.colorScheme.primary)
                            Text("连接你的 Emby 服务器", style = MaterialTheme.typography.headlineMedium)
                            Text("登录后可浏览全部媒体库、继续观看、收藏、搜索并使用完整播放器。", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Button(onClick = onLogin) {
                                Icon(Icons.Default.Login, null)
                                Spacer(Modifier.width(8.dp))
                                Text("登录服务器")
                            }
                        }
                    }
                }
            } else {
                if (resume.isNotEmpty()) {
                    item { SectionTitle("继续观看", "${resume.size} 个有播放进度内容") }
                    item {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(end = 6.dp)) {
                            items(resume, key = { it.id }) { item ->
                                ResumeCard(
                                    item = item,
                                    imageUrl = imageFor(item, "Backdrop") ?: imageFor(item, "Primary"),
                                    onClick = { onOpenItem(item) },
                                    onResume = { onResumeItem(item) },
                                    modifier = Modifier.width(resumeWidth),
                                )
                            }
                        }
                    }
                }

                if (views.isNotEmpty()) {
                    item { SectionTitle("我的媒体库", "查看全部", onLibraries) }
                    item {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(end = 6.dp)) {
                            items(views, key = { it.id }) { view ->
                                LibraryCoverCard(view, viewImageFor(view), { onOpenView(view) }, Modifier.width(libraryWidth))
                            }
                        }
                    }
                }

                if (latest.isNotEmpty()) {
                    item { SectionTitle("最近入库", "当前服务器最新加入的媒体") }
                    item {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(end = 6.dp)) {
                            items(latest.take(24), key = { it.id }) { item ->
                                MediaCard(item, imageFor(item, "Primary"), { onOpenItem(item) }, Modifier.width(cardWidth(item, wide)))
                            }
                        }
                    }
                }

                if (favorites.isNotEmpty()) {
                    item { SectionTitle("我的收藏", "跨媒体库收藏") }
                    item {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(end = 6.dp)) {
                            items(favorites.take(24), key = { it.id }) { item ->
                                MediaCard(item, imageFor(item, "Primary"), { onOpenItem(item) }, Modifier.width(cardWidth(item, wide)))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(title: String, subtitle: String, onAction: (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
        if (onAction != null) {
            TextButton(onClick = onAction) {
                Text(subtitle)
                Icon(Icons.Default.ChevronRight, null, Modifier.size(18.dp))
            }
        } else {
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun cardWidth(item: EmbyItem, wide: Boolean) = when (item.type) {
    "Episode", "Video", "MusicVideo", "Photo" -> if (wide) 236.dp else 210.dp
    "MusicAlbum", "MusicArtist", "Audio" -> if (wide) 166.dp else 150.dp
    else -> if (wide) 164.dp else 148.dp
}
