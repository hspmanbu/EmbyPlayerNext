package com.aistudio.embyplayer.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Movie
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

@Composable
fun MediaCard(item: EmbyItem, imageUrl: String?, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.clickable(onClick = onClick),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
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
                    if (item.isFolder) Icons.Default.Folder else Icons.Default.Movie,
                    null,
                    Modifier.size(48.dp).align(Alignment.Center),
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = .28f)
                )
            }

            Box(
                Modifier
                    .fillMaxWidth()
                    .height(76.dp)
                    .align(Alignment.BottomCenter)
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = .72f))))
            )

            if (item.userData.isFavorite) {
                Icon(
                    Icons.Default.Favorite,
                    "已收藏",
                    Modifier.align(Alignment.TopStart).padding(8.dp).size(20.dp),
                    tint = Color(0xFFFF6B7D)
                )
            }
            if (item.isPlayed) {
                Icon(
                    Icons.Default.CheckCircle,
                    "已观看",
                    Modifier.align(Alignment.TopEnd).padding(8.dp).size(22.dp),
                    tint = Color(0xFF72D26A)
                )
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
        Text(
            subtitle(item),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun ResumeCard(item: EmbyItem, imageUrl: String?, onClick: () -> Unit, modifier: Modifier = Modifier) {
    ElevatedCard(
        onClick = onClick,
        modifier = modifier.width(292.dp),
        shape = RoundedCornerShape(18.dp)
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).background(MaterialTheme.colorScheme.surfaceVariant)) {
            if (imageUrl != null) {
                AsyncImage(imageUrl, item.name, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            }
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        .55f to Color.Black.copy(alpha = .15f),
                        1f to Color.Black.copy(alpha = .86f)
                    )
                )
            )
            Column(
                Modifier.align(Alignment.BottomStart).padding(horizontal = 14.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(item.name, maxLines = 1, overflow = TextOverflow.Ellipsis, color = Color.White, fontWeight = FontWeight.Bold)
                val mins = item.resumePositionMs / 60_000
                Text("从 ${mins} 分钟继续", style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = .82f))
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
        shape = RoundedCornerShape(24.dp)
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(16f / 7.5f).background(MaterialTheme.colorScheme.surfaceVariant)) {
            if (imageUrl != null) AsyncImage(imageUrl, item.name, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            Box(
                Modifier.fillMaxSize().background(
                    Brush.horizontalGradient(
                        listOf(Color.Black.copy(alpha = .86f), Color.Black.copy(alpha = .32f), Color.Transparent)
                    )
                )
            )
            Column(
                Modifier.align(Alignment.CenterStart).fillMaxWidth(.72f).padding(22.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    if (item.resumePositionMs > 0 && !item.isPlayed) "继续观看" else "最近入库",
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.labelLarge
                )
                Text(item.name, color = Color.White, style = MaterialTheme.typography.headlineMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                val meta = listOfNotNull(
                    item.productionYear?.toString(),
                    item.communityRating?.let { "★ %.1f".format(it) },
                    item.durationMs.takeIf { it > 0 }?.let { "${it / 60_000} 分钟" }
                ).joinToString(" · ")
                if (meta.isNotBlank()) Text(meta, color = Color.White.copy(alpha = .78f), style = MaterialTheme.typography.bodyMedium)
                FilledTonalButton(onClick = onClick) { Text(if (item.resumePositionMs > 0 && !item.isPlayed) "继续播放" else "查看详情") }
            }
        }
    }
}

private fun progress(item: EmbyItem): Float {
    return (item.userData.playedPercentage?.toFloat()?.div(100f)
        ?: if (item.runTimeTicks != null && item.runTimeTicks > 0) {
            (item.userData.playbackPositionTicks.toDouble() / item.runTimeTicks).toFloat()
        } else 0f).coerceIn(0f, 1f)
}

private fun subtitle(item: EmbyItem): String = when {
    item.type == "Episode" -> listOfNotNull(
        item.seasonName,
        item.parentIndexNumber?.let { "S$it" },
        item.indexNumber?.let { "E$it" }
    ).joinToString(" · ")
    item.productionYear != null -> item.productionYear.toString()
    item.childCount != null -> "${item.childCount} 项"
    item.recursiveItemCount != null -> "${item.recursiveItemCount} 项"
    else -> item.type
}