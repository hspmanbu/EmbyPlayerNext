package com.embyplayernext.he.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.embyplayernext.he.data.model.EmbyItem
import com.embyplayernext.he.data.model.EmbyView

@Composable
fun MediaCard(
    item: EmbyItem,
    imageUrl: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    showTypeBadge: Boolean = false,
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(18.dp)
    Column(
        modifier = modifier
            .onFocusChanged { focused = it.isFocused }
            .graphicsLayer {
                val scale = if (focused) 1.045f else 1f
                scaleX = scale
                scaleY = scale
            }
            .border(if (focused) 3.dp else 0.dp, MaterialTheme.colorScheme.primary, shape)
            .clickable(onClick = onClick),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(cardAspectRatio(item))
                .clip(RoundedCornerShape(18.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
        ) {
            if (imageUrl != null) {
                AsyncImage(
                    model = imageUrl,
                    contentDescription = item.name,
                    contentScale = if (item.type == "Photo") ContentScale.Fit else ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Icon(
                    mediaIcon(item),
                    null,
                    Modifier.size(48.dp).align(Alignment.Center),
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = .24f)
                )
            }

            Box(
                Modifier
                    .fillMaxWidth()
                    .height(68.dp)
                    .align(Alignment.BottomCenter)
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = .72f))))
            )

            // Top-left badge: Type badge or Rating badge
            Row(
                Modifier.align(Alignment.TopStart).padding(6.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (showTypeBadge) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = Color.Black.copy(alpha = .65f)
                    ) {
                        Text(
                            typeLabel(item),
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White
                        )
                    }
                }
                item.communityRating?.takeIf { it > 0 }?.let { rating ->
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = Color.Black.copy(alpha = .65f)
                    ) {
                        Row(
                            Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text(
                                "★",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFFFFC107),
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "%.1f".format(rating),
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }

            // Top-right icons: Favorite, Played, or Unplayed Count Badge
            Row(
                Modifier.align(Alignment.TopEnd).padding(6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (item.userData.isFavorite) {
                    Icon(Icons.Default.Favorite, "已收藏", Modifier.size(18.dp), tint = Color(0xFFFF6B7D))
                }
                if (item.isPlayed) {
                    Icon(Icons.Default.CheckCircle, "已看完", Modifier.size(18.dp), tint = Color(0xFF79D774))
                } else {
                    val unplayed = item.userData.unplayedItemCount ?: item.recursiveUnplayedItemCount
                    if (unplayed != null && unplayed > 0) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = .92f)
                        ) {
                            Text(
                                unplayed.toString(),
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            // Bottom-left: Resolution tag (4K / 1080P)
            val res = resolutionBadge(item)
            if (res != null) {
                Surface(
                    modifier = Modifier.align(Alignment.BottomStart).padding(6.dp),
                    shape = RoundedCornerShape(4.dp),
                    color = Color.Black.copy(alpha = .65f)
                ) {
                    Text(
                        res,
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF81D4FA),
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            val p = progress(item)
            if (p > 0f && p < 1f) {
                LinearProgressIndicator(
                    progress = { p },
                    modifier = Modifier.fillMaxWidth().align(Alignment.BottomCenter).height(4.dp),
                    trackColor = Color.White.copy(alpha = .18f)
                )
            }
        }

        Text(
            item.name,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
        )
        subtitle(item).takeIf { it.isNotBlank() }?.let {
            Text(
                it,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
fun ResumeCard(
    item: EmbyItem,
    imageUrl: String?,
    onClick: () -> Unit,
    onResume: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(20.dp)
    ElevatedCard(
        onClick = onClick,
        modifier = modifier
            .onFocusChanged { focused = it.isFocused }
            .graphicsLayer {
                val scale = if (focused) 1.045f else 1f
                scaleX = scale
                scaleY = scale
            }
            .border(if (focused) 3.dp else 0.dp, MaterialTheme.colorScheme.primary, shape),
        shape = shape
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).background(MaterialTheme.colorScheme.surfaceContainerHigh)) {
            if (imageUrl != null) {
                AsyncImage(imageUrl, item.name, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            }
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        .5f to Color.Black.copy(alpha = .12f),
                        1f to Color.Black.copy(alpha = .9f)
                    )
                )
            )
            Column(
                Modifier.align(Alignment.BottomStart).padding(horizontal = 14.dp, vertical = 13.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(item.name, maxLines = 1, overflow = TextOverflow.Ellipsis, color = Color.White, fontWeight = FontWeight.Bold)
                Text(
                    resumeLabel(item),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = .82f)
                )
            }
            Surface(
                modifier = Modifier.align(Alignment.Center).size(48.dp).clickable(onClick = onResume),
                shape = RoundedCornerShape(23.dp),
                color = Color.Black.copy(alpha = .48f)
            ) {
                Icon(Icons.Default.PlayArrow, "继续播放", Modifier.padding(10.dp), tint = Color.White)
            }
            LinearProgressIndicator(
                progress = { progress(item).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().align(Alignment.BottomCenter).height(4.dp),
                trackColor = Color.White.copy(alpha = .18f)
            )
        }
    }
}

@Composable
fun NextUpCard(
    item: EmbyItem,
    imageUrl: String?,
    onClick: () -> Unit,
    onPlay: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(20.dp)
    ElevatedCard(
        onClick = onClick,
        modifier = modifier
            .onFocusChanged { focused = it.isFocused }
            .graphicsLayer {
                val scale = if (focused) 1.045f else 1f
                scaleX = scale
                scaleY = scale
            }
            .border(if (focused) 3.dp else 0.dp, MaterialTheme.colorScheme.primary, shape),
        shape = shape
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).background(MaterialTheme.colorScheme.surfaceContainerHigh)) {
            if (imageUrl != null) AsyncImage(imageUrl, item.name, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = .12f), Color.Black.copy(alpha = .88f))),
                ),
            )
            Column(
                Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(start = 14.dp, end = 58.dp, bottom = 13.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(item.name, color = Color.White, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    listOfNotNull(
                        item.seriesName,
                        item.parentIndexNumber?.let { "S$it" },
                        item.indexNumber?.let { "E$it" },
                    ).joinToString(" · ").ifBlank { "下一集" },
                    color = Color.White.copy(alpha = .76f),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Surface(
                modifier = Modifier.align(Alignment.BottomEnd).padding(12.dp).size(42.dp).clickable(onClick = onPlay),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary,
            ) {
                Icon(Icons.Default.PlayArrow, "播放下一集", Modifier.padding(9.dp), tint = MaterialTheme.colorScheme.onPrimary)
            }
        }
    }
}

@Composable
fun HeroCard(
    item: EmbyItem,
    imageUrl: String?,
    eyebrow: String? = null,
    onClick: () -> Unit,
    onPrimaryAction: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ElevatedCard(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp)
    ) {
        BoxWithConstraints(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainerHigh)) {
            val heroHeight = if (maxWidth >= 840.dp) 320.dp else maxWidth * (7.4f / 16f)
            Box(Modifier.fillMaxWidth().height(heroHeight)) {
            if (imageUrl != null) AsyncImage(imageUrl, item.name, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            Box(
                Modifier.fillMaxSize().background(
                    Brush.horizontalGradient(
                        listOf(Color.Black.copy(alpha = .88f), Color.Black.copy(alpha = .44f), Color.Transparent)
                    )
                )
            )
            Column(
                Modifier.align(Alignment.CenterStart).fillMaxWidth(.76f).padding(22.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                eyebrow?.takeIf { it.isNotBlank() }?.let { label ->
                    Text(
                        label,
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.labelLarge
                    )
                }
                Text(item.name, color = Color.White, style = MaterialTheme.typography.headlineMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                val meta = listOfNotNull(
                    item.productionYear?.toString(),
                    item.communityRating?.let { "★ %.1f".format(it) },
                    item.durationMs.takeIf { it > 0 }?.let(::formatDuration)
                ).joinToString(" · ")
                if (meta.isNotBlank()) Text(meta, color = Color.White.copy(alpha = .8f), style = MaterialTheme.typography.bodyMedium)
                FilledTonalButton(onClick = onPrimaryAction) {
                    Icon(if (item.hasResumePosition) Icons.Default.PlayArrow else Icons.Default.Info, null)
                    Spacer(Modifier.width(6.dp))
                    Text(if (item.hasResumePosition) "继续观看" else "查看详情")
                }
            }
            }
        }
    }
}

@Composable
fun MediaListItem(
    item: EmbyItem,
    imageUrl: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(16.dp)
    Surface(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .onFocusChanged { focused = it.isFocused }
            .graphicsLayer {
                val scale = if (focused) 1.02f else 1f
                scaleX = scale
                scaleY = scale
            }
            .border(if (focused) 2.5.dp else 0.dp, MaterialTheme.colorScheme.primary, shape),
        shape = shape,
        color = if (focused) MaterialTheme.colorScheme.surfaceContainerHighest else MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = if (focused) 6.dp else 2.dp,
    ) {
        Row(
            Modifier.fillMaxWidth().padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .width(108.dp)
                    .aspectRatio(if (item.type in setOf("Episode", "Video", "MusicVideo")) 16f / 10f else 2f / 3f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            ) {
                if (imageUrl != null) {
                    AsyncImage(
                        model = imageUrl,
                        contentDescription = item.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(
                        mediaIcon(item),
                        null,
                        Modifier.size(36.dp).align(Alignment.Center),
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = .24f)
                    )
                }
                val res = resolutionBadge(item)
                if (res != null) {
                    Surface(
                        modifier = Modifier.align(Alignment.BottomStart).padding(4.dp),
                        shape = RoundedCornerShape(4.dp),
                        color = Color.Black.copy(alpha = .72f)
                    ) {
                        Text(
                            res,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF81D4FA),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                val p = progress(item)
                if (p > 0f && p < 1f) {
                    LinearProgressIndicator(
                        progress = { p },
                        modifier = Modifier.fillMaxWidth().align(Alignment.BottomCenter).height(3.dp),
                        trackColor = Color.White.copy(alpha = .18f)
                    )
                }
            }

            Spacer(Modifier.width(14.dp))

            Column(
                Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        item.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    if (item.userData.isFavorite) {
                        Spacer(Modifier.width(6.dp))
                        Icon(Icons.Default.Favorite, "已收藏", Modifier.size(16.dp), tint = Color(0xFFFF6B7D))
                    }
                    if (item.isPlayed) {
                        Spacer(Modifier.width(6.dp))
                        Icon(Icons.Default.CheckCircle, "已看完", Modifier.size(16.dp), tint = Color(0xFF79D774))
                    }
                }

                val metaParts = buildList {
                    item.productionYear?.let { add(it.toString()) }
                    item.communityRating?.takeIf { it > 0 }?.let { add("★ %.1f".format(it)) }
                    item.durationMs.takeIf { it > 0 }?.let { add(formatDuration(it)) }
                    if (item.genres.isNotEmpty()) add(item.genres.take(2).joinToString("/"))
                    if (item.childCount != null && item.childCount > 0) add("${item.childCount} 项")
                }
                if (metaParts.isNotEmpty()) {
                    Text(
                        metaParts.joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                item.overview?.takeIf { it.isNotBlank() }?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
fun LibraryCoverCard(
    view: EmbyView,
    imageUrl: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(22.dp)
    ElevatedCard(
        onClick = onClick,
        modifier = modifier
            .onFocusChanged { focused = it.isFocused }
            .graphicsLayer {
                val scale = if (focused) 1.05f else 1f
                scaleX = scale
                scaleY = scale
            }
            .border(if (focused) 3.dp else 0.dp, MaterialTheme.colorScheme.primary, shape),
        shape = shape,
        elevation = CardDefaults.elevatedCardElevation(
            defaultElevation = 3.dp,
            focusedElevation = 10.dp,
        )
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(16f / 10f).background(MaterialTheme.colorScheme.surfaceContainerHigh)) {
            if (imageUrl != null) {
                AsyncImage(imageUrl, view.name, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            }
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(
                        0f to Color.Black.copy(alpha = .15f),
                        .45f to Color.Black.copy(alpha = .35f),
                        1f to Color.Black.copy(alpha = .90f)
                    )
                )
            )
            Surface(
                modifier = Modifier.align(Alignment.TopStart).padding(12.dp),
                shape = CircleShape,
                color = Color.Black.copy(alpha = .45f)
            ) {
                Icon(
                    libraryIcon(view),
                    null,
                    Modifier.padding(7.dp).size(20.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            Column(
                Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(view.name, color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(libraryTypeLabel(view), color = Color.White.copy(alpha = .78f), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

fun resolutionBadge(item: EmbyItem): String? {
    val w = item.width ?: 0
    val h = item.height ?: 0
    return when {
        w >= 3800 || h >= 2000 -> "4K"
        w >= 1900 || h >= 1000 -> "1080P"
        w >= 1200 || h >= 700 -> "720P"
        else -> null
    }
}

private fun progress(item: EmbyItem): Float = (
    item.userData.playedPercentage?.toFloat()?.div(100f)
        ?: if (item.runTimeTicks != null && item.runTimeTicks > 0) {
            (item.userData.playbackPositionTicks.toDouble() / item.runTimeTicks).toFloat()
        } else 0f
    ).coerceIn(0f, 1f)

private fun resumeLabel(item: EmbyItem): String {
    val current = item.resumePositionMs / 60_000
    val total = item.durationMs / 60_000
    return if (total > 0) "${current} / ${total} 分钟" else "从 ${current} 分钟继续"
}

private fun formatDuration(ms: Long): String {
    val minutes = ms / 60_000
    val h = minutes / 60
    val m = minutes % 60
    return if (h > 0) "${h}小时${m}分" else "${m}分钟"
}

private fun cardAspectRatio(item: EmbyItem): Float = when (item.type) {
    "Episode", "Video", "MusicVideo", "Trailer" -> 16f / 10f
    "Photo" -> when {
        item.width != null && item.height != null && item.width > item.height * 1.15f -> 16f / 10f
        item.width != null && item.height != null && item.height > item.width * 1.15f -> 3f / 4f
        else -> 1f
    }
    "MusicAlbum", "MusicArtist", "Audio" -> 1f
    else -> 2f / 3f
}

private fun mediaIcon(item: EmbyItem) = when (item.type) {
    "Folder", "CollectionFolder" -> Icons.Default.Folder
    "Series", "Episode" -> Icons.Default.Tv
    "Audio", "MusicAlbum", "MusicArtist" -> Icons.Default.LibraryMusic
    "Photo" -> Icons.Default.Photo
    "Book" -> Icons.Default.MenuBook
    "BoxSet" -> Icons.Default.CollectionsBookmark
    else -> Icons.Default.Movie
}

private fun typeLabel(item: EmbyItem): String = when (item.type) {
    "Movie" -> "电影"
    "Series" -> "剧集"
    "Episode" -> "单集"
    "Audio" -> "歌曲"
    "MusicAlbum" -> "专辑"
    "MusicArtist" -> "艺术家"
    "Photo" -> "照片"
    "Book" -> "图书"
    "BoxSet" -> "合集"
    "Folder", "CollectionFolder" -> "文件夹"
    else -> item.type
}

private fun subtitle(item: EmbyItem): String = when {
    item.type == "Movie" -> listOfNotNull(
        item.productionYear?.toString(),
        item.durationMs.takeIf { it > 0 }?.let(::formatDuration)
    ).joinToString(" · ")
    item.type == "Episode" -> listOfNotNull(
        item.seriesName,
        item.parentIndexNumber?.let { "S$it" },
        item.indexNumber?.let { "E$it" },
        item.durationMs.takeIf { it > 0 }?.let(::formatDuration)
    ).joinToString(" · ")
    item.type == "Audio" -> listOfNotNull(item.albumArtist ?: item.artists.firstOrNull(), item.album).joinToString(" · ")
    item.type == "MusicAlbum" -> item.albumArtist ?: item.productionYear?.toString().orEmpty()
    item.recursiveUnplayedItemCount != null && item.recursiveUnplayedItemCount > 0 -> "${item.recursiveUnplayedItemCount} 个未看"
    item.productionYear != null -> item.productionYear.toString()
    item.childCount != null -> "${item.childCount} 项"
    item.recursiveItemCount != null -> "${item.recursiveItemCount} 项"
    else -> typeLabel(item)
}

private fun libraryIcon(view: EmbyView) = when (view.collectionType?.lowercase()) {
    "tvshows" -> Icons.Default.Tv
    "music" -> Icons.Default.LibraryMusic
    "books" -> Icons.Default.MenuBook
    "homevideos", "photos" -> Icons.Default.PhotoLibrary
    "musicvideos" -> Icons.Default.VideoLibrary
    else -> Icons.Default.Movie
}

private fun libraryTypeLabel(view: EmbyView): String = when (view.collectionType?.lowercase()) {
    "movies" -> "电影"
    "tvshows" -> "电视剧"
    "music" -> "音乐"
    "books" -> "图书"
    "homevideos" -> "家庭视频与照片"
    "musicvideos" -> "音乐视频"
    "games" -> "游戏"
    "livetv" -> "直播电视"
    else -> "媒体库"
}
