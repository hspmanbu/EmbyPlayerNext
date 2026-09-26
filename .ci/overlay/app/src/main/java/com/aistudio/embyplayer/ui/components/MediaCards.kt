package com.aistudio.embyplayer.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
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
import com.aistudio.embyplayer.data.model.EmbyItem
import com.aistudio.embyplayer.data.model.EmbyView

@Composable
fun MediaCard(
    item: EmbyItem,
    imageUrl: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    showTypeBadge: Boolean = false,
) {
    Column(
        modifier = modifier.clickable(onClick = onClick),
        verticalArrangement = Arrangement.spacedBy(8.dp)
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
                    contentScale = ContentScale.Crop,
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
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = .68f))))
            )

            if (showTypeBadge) {
                Surface(
                    modifier = Modifier.align(Alignment.TopStart).padding(8.dp),
                    shape = RoundedCornerShape(8.dp),
                    color = Color.Black.copy(alpha = .62f)
                ) {
                    Text(
                        typeLabel(item),
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White
                    )
                }
            }

            Row(
                Modifier.align(Alignment.TopEnd).padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (item.userData.isFavorite) {
                    Icon(Icons.Default.Favorite, "已收藏", Modifier.size(20.dp), tint = Color(0xFFFF6B7D))
                }
                if (item.isPlayed) {
                    Icon(Icons.Default.CheckCircle, "已看完", Modifier.size(21.dp), tint = Color(0xFF79D774))
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
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold
        )
        subtitle(item).takeIf { it.isNotBlank() }?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun ResumeCard(item: EmbyItem, imageUrl: String?, onClick: () -> Unit, modifier: Modifier = Modifier) {
    ElevatedCard(
        onClick = onClick,
        modifier = modifier.width(300.dp),
        shape = RoundedCornerShape(20.dp)
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
                modifier = Modifier.align(Alignment.Center).size(46.dp),
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
fun HeroCard(item: EmbyItem, imageUrl: String?, onClick: () -> Unit, modifier: Modifier = Modifier) {
    ElevatedCard(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp)
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(16f / 7.4f).background(MaterialTheme.colorScheme.surfaceContainerHigh)) {
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
                Text(
                    if (item.resumePositionMs > 0 && !item.isPlayed) "继续观看" else "为你推荐",
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.labelLarge
                )
                Text(item.name, color = Color.White, style = MaterialTheme.typography.headlineMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                val meta = listOfNotNull(
                    item.productionYear?.toString(),
                    item.communityRating?.let { "★ %.1f".format(it) },
                    item.durationMs.takeIf { it > 0 }?.let(::formatDuration)
                ).joinToString(" · ")
                if (meta.isNotBlank()) Text(meta, color = Color.White.copy(alpha = .8f), style = MaterialTheme.typography.bodyMedium)
                FilledTonalButton(onClick = onClick) {
                    Icon(if (item.resumePositionMs > 0 && !item.isPlayed) Icons.Default.PlayArrow else Icons.Default.Info, null)
                    Spacer(Modifier.width(6.dp))
                    Text(if (item.resumePositionMs > 0 && !item.isPlayed) "继续观看" else "查看详情")
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
    ElevatedCard(onClick = onClick, modifier = modifier, shape = RoundedCornerShape(22.dp)) {
        Box(Modifier.fillMaxWidth().aspectRatio(16f / 10f).background(MaterialTheme.colorScheme.surfaceContainerHigh)) {
            if (imageUrl != null) {
                AsyncImage(imageUrl, view.name, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            }
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = .82f)))
                )
            )
            if (imageUrl == null) {
                Icon(
                    libraryIcon(view),
                    null,
                    Modifier.align(Alignment.Center).size(48.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            Column(
                Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(view.name, color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(libraryTypeLabel(view), color = Color.White.copy(alpha = .72f), style = MaterialTheme.typography.bodySmall)
            }
        }
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
    "Episode", "Video", "MusicVideo", "Trailer", "Photo" -> 16f / 10f
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
    item.type == "Episode" -> listOfNotNull(
        item.seriesName,
        item.parentIndexNumber?.let { "S$it" },
        item.indexNumber?.let { "E$it" }
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
