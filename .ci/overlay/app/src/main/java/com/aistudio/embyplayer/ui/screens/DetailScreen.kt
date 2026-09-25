package com.aistudio.embyplayer.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.aistudio.embyplayer.data.model.*
import com.aistudio.embyplayer.ui.components.MediaCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(
    item: EmbyItem?,
    seasons: List<EmbyItem>,
    episodes: List<EmbyItem>,
    loading: Boolean,
    selectedPerson: EmbyPerson?,
    personWorks: List<EmbyItem>,
    imageFor: (EmbyItem, String) -> String?,
    onBack: () -> Unit,
    onPlay: (Boolean) -> Unit,
    onFavorite: () -> Unit,
    onPlayed: () -> Unit,
    onDelete: () -> Unit,
    onSeason: (EmbyItem) -> Unit,
    onEpisode: (EmbyItem) -> Unit,
    onPerson: (EmbyPerson) -> Unit,
    onClosePerson: () -> Unit
) {
    if (item == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }
    var deleteConfirm by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(item.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "返回") } }
            )
        }
    ) { pad ->
        LazyColumn(
            Modifier.fillMaxSize().padding(pad),
            contentPadding = PaddingValues(bottom = 34.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            item {
                Box(Modifier.fillMaxWidth().aspectRatio(16f / 8.2f).background(MaterialTheme.colorScheme.surfaceVariant)) {
                    imageFor(item, "Backdrop")?.let {
                        AsyncImage(it, item.name, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    }
                    Box(
                        Modifier.fillMaxSize().background(
                            Brush.verticalGradient(
                                listOf(Color.Transparent, Color.Black.copy(alpha = .2f), MaterialTheme.colorScheme.background)
                            )
                        )
                    )
                    if (loading) LinearProgressIndicator(Modifier.fillMaxWidth().align(Alignment.BottomCenter))
                }

                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 18.dp).offset(y = (-34).dp),
                    verticalAlignment = Alignment.Bottom
                ) {
                    Surface(
                        modifier = Modifier.width(116.dp).aspectRatio(2f / 3f),
                        shape = RoundedCornerShape(18.dp),
                        tonalElevation = 6.dp,
                        shadowElevation = 6.dp
                    ) {
                        val poster = imageFor(item, "Primary")
                        if (poster != null) AsyncImage(poster, item.name, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                        else Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainerHigh), contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Movie, null, Modifier.size(40.dp))
                        }
                    }
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f).padding(bottom = 4.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        Text(item.name, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, maxLines = 2)
                        val meta = listOfNotNull(
                            item.productionYear?.toString(),
                            item.officialRating,
                            item.communityRating?.let { "★ %.1f".format(it) },
                            item.durationMs.takeIf { it > 0 }?.let { "${it / 60_000} 分钟" }
                        )
                        if (meta.isNotEmpty()) Text(meta.joinToString(" · "), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        item.tagline?.let { Text(it, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.titleMedium) }
                    }
                }

                Column(
                    Modifier.fillMaxWidth().padding(horizontal = 18.dp).offset(y = (-22).dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (item.resumePositionMs > 0 && !item.isPlayed) {
                            Button(onClick = { onPlay(true) }, modifier = Modifier.weight(1f)) {
                                Icon(Icons.Default.PlayArrow, null); Spacer(Modifier.width(6.dp)); Text("继续播放")
                            }
                            FilledTonalButton(onClick = { onPlay(false) }) { Text("从头") }
                        } else {
                            Button(onClick = { onPlay(false) }, modifier = Modifier.weight(1f)) {
                                Icon(Icons.Default.PlayArrow, null); Spacer(Modifier.width(6.dp)); Text("立即播放")
                            }
                        }
                    }

                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AssistChip(
                            onClick = onFavorite,
                            label = { Text(if (item.userData.isFavorite) "已收藏" else "收藏") },
                            leadingIcon = { Icon(if (item.userData.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder, null) }
                        )
                        AssistChip(
                            onClick = onPlayed,
                            label = { Text(if (item.isPlayed) "已看完" else "标记已看") },
                            leadingIcon = { Icon(Icons.Default.CheckCircle, null) }
                        )
                        AssistChip(
                            onClick = { deleteConfirm = true },
                            label = { Text("删除") },
                            leadingIcon = { Icon(Icons.Default.Delete, null) }
                        )
                    }

                    if (item.genres.isNotEmpty()) {
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                            item.genres.forEach { g -> SuggestionChip(onClick = {}, label = { Text(g) }) }
                        }
                    }

                    item.overview?.takeIf { it.isNotBlank() }?.let {
                        Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
                            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("剧情简介", style = MaterialTheme.typography.titleLarge)
                                Text(it, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }

                    if (item.mediaStreams.isNotEmpty() || item.path != null) {
                        Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
                            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("媒体信息", style = MaterialTheme.typography.titleMedium)
                                val video = item.mediaStreams.firstOrNull { it.type.equals("Video", true) }
                                val audio = item.mediaStreams.firstOrNull { it.type.equals("Audio", true) }
                                Text(
                                    listOfNotNull(
                                        video?.codec?.uppercase(),
                                        if (item.width != null && item.height != null) "${item.width}×${item.height}" else null,
                                        audio?.displayTitle ?: audio?.codec?.uppercase()
                                    ).joinToString(" · ").ifBlank { item.type },
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                item.path?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis) }
                            }
                        }
                    }
                }
            }

            if (item.people.isNotEmpty()) {
                item { SectionHeader("演职人员", "点击查看作品") }
                item {
                    LazyRow(contentPadding = PaddingValues(horizontal = 18.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(item.people.take(24), key = { it.id + it.name + (it.role ?: "") }) { p ->
                            ElevatedCard(onClick = { onPerson(p) }, modifier = Modifier.width(154.dp), shape = RoundedCornerShape(18.dp)) {
                                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                    Icon(Icons.Default.Person, null, tint = MaterialTheme.colorScheme.primary)
                                    Text(p.name, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(p.role ?: p.type.orEmpty(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
                                }
                            }
                        }
                    }
                }
            }

            if (seasons.isNotEmpty()) {
                item { SectionHeader("剧集", "${episodes.size} 集") }
                item {
                    Row(
                        Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 18.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        seasons.forEach { s -> AssistChip(onClick = { onSeason(s) }, label = { Text(s.name) }) }
                    }
                }
                items(episodes, key = { it.id }) { ep ->
                    EpisodeRow(ep, imageFor(ep, "Primary")) { onEpisode(ep) }
                }
            }
        }
    }

    if (deleteConfirm) {
        AlertDialog(
            onDismissRequest = { deleteConfirm = false },
            title = { Text("删除媒体") },
            text = { Text("确定要从服务器删除《${item.name}》吗？\n\n这可能同时删除对应的媒体文件，且无法撤销。") },
            confirmButton = { Button(onClick = { deleteConfirm = false; onDelete() }) { Text("删除") } },
            dismissButton = { TextButton(onClick = { deleteConfirm = false }) { Text("取消") } }
        )
    }

    if (selectedPerson != null) {
        ModalBottomSheet(onDismissRequest = onClosePerson) {
            Text("${selectedPerson.name} 的作品", Modifier.padding(horizontal = 18.dp, vertical = 8.dp), style = MaterialTheme.typography.titleLarge)
            if (personWorks.isEmpty()) {
                Text("暂无作品", Modifier.padding(18.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                LazyRow(contentPadding = PaddingValues(18.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(personWorks, key = { it.id }) { w ->
                        MediaCard(w, imageFor(w, "Primary"), { onEpisode(w) }, Modifier.width(150.dp))
                    }
                }
            }
            Spacer(Modifier.height(26.dp))
        }
    }
}

@Composable
private fun SectionHeader(title: String, subtitle: String) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun EpisodeRow(item: EmbyItem, imageUrl: String?, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 18.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.width(138.dp).aspectRatio(16f / 9f).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            if (imageUrl != null) AsyncImage(imageUrl, item.name, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            Icon(Icons.Default.PlayCircle, null, Modifier.align(Alignment.Center).size(34.dp), tint = Color.White.copy(alpha = .9f))
            val p = item.userData.playedPercentage?.toFloat()?.div(100f) ?: 0f
            if (p > 0f && p < 1f) LinearProgressIndicator(progress = { p }, modifier = Modifier.fillMaxWidth().height(3.dp).align(Alignment.BottomCenter))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(item.name, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(
                listOfNotNull(
                    item.parentIndexNumber?.let { "S$it" },
                    item.indexNumber?.let { "E$it" },
                    item.durationMs.takeIf { it > 0 }?.let { "${it / 60_000} 分钟" }
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (item.isPlayed) Icon(Icons.Default.CheckCircle, "已看", tint = MaterialTheme.colorScheme.primary)
    }
}