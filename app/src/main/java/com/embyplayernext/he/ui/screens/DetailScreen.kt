package com.embyplayernext.he.ui.screens

import android.content.pm.PackageManager
import android.content.res.Configuration
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.embyplayernext.he.ui.components.tvScrollThenFocus
import com.embyplayernext.he.data.model.*
import com.embyplayernext.he.ui.components.MediaCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(
    item: EmbyItem?,
    seasons: List<EmbyItem>,
    selectedSeasonId: String?,
    episodes: List<EmbyItem>,
    loading: Boolean,
    selectedPerson: EmbyPerson?,
    personWorks: List<EmbyItem>,
    similarItems: List<EmbyItem>,
    imageFor: (EmbyItem, String) -> String?,
    personImageFor: (EmbyPerson) -> String?,
    onBack: () -> Unit,
    onPlay: (Boolean) -> Unit,
    onFavorite: () -> Unit,
    onPlayed: () -> Unit,
    onDelete: () -> Unit,
    onSeason: (EmbyItem) -> Unit,
    onEpisode: (EmbyItem) -> Unit,
    onPlayEpisode: (EmbyItem) -> Unit,
    onPerson: (EmbyPerson) -> Unit,
    onClosePerson: () -> Unit,
) {
    if (item == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }

    val configuration = LocalConfiguration.current
    val context = LocalContext.current
    val isTv = (configuration.uiMode and Configuration.UI_MODE_TYPE_MASK) == Configuration.UI_MODE_TYPE_TELEVISION ||
        context.packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)
    val isWide = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE || configuration.screenWidthDp >= 760
    val contentPadding = if (isTv) 38.dp else if (isWide) 28.dp else 18.dp
    val contentMaxWidth = if (isWide) 1280.dp else 920.dp
    var deleteConfirm by remember(item.id) { mutableStateOf(false) }
    val pageState = rememberLazyListState()

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        DetailBackdrop(item, loading, isWide, imageFor)
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = {},
                    navigationIcon = {
                        FocusSurfaceButton(
                            isTv = isTv,
                            onClick = onBack,
                            modifier = Modifier.padding(start = 8.dp),
                            shape = CircleShape,
                            normalColor = MaterialTheme.colorScheme.surface.copy(alpha = .50f),
                        ) {
                            Icon(Icons.Default.ArrowBack, "返回", Modifier.padding(12.dp))
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                )
            },
        ) { padding ->
            LazyColumn(
                state = pageState,
                modifier = Modifier.fillMaxSize().padding(top = padding.calculateTopPadding()).tvScrollThenFocus(pageState),
                contentPadding = PaddingValues(bottom = if (isTv) 64.dp else 36.dp),
                verticalArrangement = Arrangement.spacedBy(if (isTv) 22.dp else 18.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                item {
                    Column(
                        Modifier.widthIn(max = contentMaxWidth).fillMaxWidth().padding(horizontal = contentPadding),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        DetailHeader(item, isWide)

                        if (item.isPlayable) {
                            PlaybackActions(item, onPlay, isTv)
                        }

                        Row(
                            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(9.dp),
                        ) {
                            DetailPill(
                                text = if (item.userData.isFavorite) "已收藏" else "收藏",
                                icon = if (item.userData.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                isTv = isTv,
                                onClick = onFavorite,
                            )
                            DetailPill(
                                text = if (item.isPlayed) "标记未看" else "标记已看",
                                icon = Icons.Default.CheckCircle,
                                isTv = isTv,
                                onClick = onPlayed,
                            )
                            DetailPill(
                                text = "删除",
                                icon = Icons.Default.Delete,
                                isTv = isTv,
                                onClick = { deleteConfirm = true },
                            )
                        }

                        if (item.genres.isNotEmpty()) {
                            Row(
                                Modifier.horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(7.dp),
                            ) {
                                item.genres.forEach { genre ->
                                    Surface(
                                        shape = RoundedCornerShape(999.dp),
                                        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = .88f),
                                    ) {
                                        Text(
                                            genre,
                                            Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                                            style = MaterialTheme.typography.labelLarge,
                                        )
                                    }
                                }
                            }
                        }

                        item.overview?.takeIf { it.isNotBlank() }?.let { overview ->
                            InfoPanel("剧情简介") {
                                Text(
                                    overview,
                                    style = if (isWide) MaterialTheme.typography.bodyLarge else MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }

                if (item.type == "Series" && seasons.isNotEmpty()) {
                    item { SectionHeader("剧集", "${episodes.size} 集", contentMaxWidth, contentPadding) }
                    item {
                        Row(
                            Modifier.widthIn(max = contentMaxWidth).fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                                .padding(horizontal = contentPadding),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            seasons.forEach { season ->
                                FilterChip(
                                    selected = season.id == selectedSeasonId,
                                    onClick = { onSeason(season) },
                                    label = { Text(season.name) },
                                )
                            }
                        }
                    }
                    episodes.firstOrNull { !it.isPlayed }?.let { nextEpisode ->
                        item {
                            Surface(
                                modifier = Modifier.widthIn(max = contentMaxWidth).fillMaxWidth().padding(horizontal = contentPadding),
                                shape = RoundedCornerShape(20.dp),
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = .94f),
                            ) {
                                Row(
                                    Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 15.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(Icons.Default.PlayCircle, null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                                    Spacer(Modifier.width(12.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(
                                            "继续这部剧",
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        )
                                        Text(
                                            listOfNotNull(
                                                nextEpisode.parentIndexNumber?.let { "S$it" },
                                                nextEpisode.indexNumber?.let { "E$it" },
                                                nextEpisode.name,
                                            ).joinToString(" · "),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = .76f),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                    FocusActionButton(
                                        text = "播放",
                                        icon = Icons.Default.PlayArrow,
                                        isTv = isTv,
                                        primary = true,
                                        onClick = { onPlayEpisode(nextEpisode) },
                                    )
                                }
                            }
                        }
                    }
                    items(episodes, key = { it.id }) { episode ->
                        EpisodeRow(
                            item = episode,
                            imageUrl = imageFor(episode, "Primary"),
                            isTv = isTv,
                            contentMaxWidth = contentMaxWidth,
                            contentPadding = contentPadding,
                            onClick = { onEpisode(episode) },
                            onPlay = { onPlayEpisode(episode) },
                        )
                    }
                }

                if (item.type == "Episode" && episodes.any { it.id != item.id }) {
                    item {
                        SectionHeader(
                            "更多来自",
                            listOfNotNull(item.seriesName, item.seasonName).joinToString(" · ").ifBlank { "同系列剧集" },
                            contentMaxWidth,
                            contentPadding,
                        )
                    }
                    item {
                        LazyRow(
                            modifier = Modifier.widthIn(max = contentMaxWidth).fillMaxWidth(),
                            contentPadding = PaddingValues(horizontal = contentPadding),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            items(episodes.filter { it.id != item.id }.take(30), key = { it.id }) { episode ->
                                MoreFromEpisodeCard(
                                    item = episode,
                                    imageUrl = imageFor(episode, "Primary"),
                                    isTv = isTv,
                                    onClick = { onEpisode(episode) },
                                    onPlay = { onPlayEpisode(episode) },
                                )
                            }
                        }
                    }
                }

                if (hasMediaInfo(item)) {
                    item {
                        Box(
                            Modifier.widthIn(max = contentMaxWidth).fillMaxWidth().padding(horizontal = contentPadding),
                        ) {
                            MediaInfoPanel(item)
                        }
                    }
                }

                if (item.people.isNotEmpty()) {
                    item { SectionHeader("演职人员", "点击查看作品", contentMaxWidth, contentPadding) }
                    item {
                        LazyRow(
                            modifier = Modifier.widthIn(max = contentMaxWidth).fillMaxWidth(),
                            contentPadding = PaddingValues(horizontal = contentPadding),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            items(item.people.take(30), key = { it.id + it.name + (it.role ?: "") }) { person ->
                                PersonCard(person, personImageFor(person), isTv) { onPerson(person) }
                            }
                        }
                    }
                }

                if (similarItems.isNotEmpty()) {
                    item { SectionHeader("你可能还喜欢", "相关推荐", contentMaxWidth, contentPadding) }
                    item {
                        LazyRow(
                            modifier = Modifier.widthIn(max = contentMaxWidth).fillMaxWidth(),
                            contentPadding = PaddingValues(horizontal = contentPadding),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            items(similarItems.take(18), key = { it.id }) { related ->
                                MediaCard(
                                    related,
                                    imageFor(related, "Primary"),
                                    { onEpisode(related) },
                                    Modifier.width(if (isTv) 174.dp else 154.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (deleteConfirm) {
        AlertDialog(
            onDismissRequest = { deleteConfirm = false },
            title = { Text("删除媒体") },
            text = { Text("确定要从服务器删除《${item.name}》吗？\n\n这可能同时删除对应的媒体文件，且无法撤销。") },
            confirmButton = {
                Button(onClick = { deleteConfirm = false; onDelete() }) { Text("删除") }
            },
            dismissButton = { TextButton(onClick = { deleteConfirm = false }) { Text("取消") } },
        )
    }

    if (selectedPerson != null) {
        ModalBottomSheet(onDismissRequest = onClosePerson) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val avatar = personImageFor(selectedPerson)
                Surface(Modifier.size(58.dp), shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                    if (avatar != null) AsyncImage(avatar, selectedPerson.name, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    else Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Icon(Icons.Default.Person, null) }
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(selectedPerson.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    Text("相关作品", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                }
            }
            if (personWorks.isEmpty()) {
                Text("暂无作品", Modifier.padding(18.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                LazyRow(
                    contentPadding = PaddingValues(18.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(personWorks, key = { it.id }) { work ->
                        MediaCard(work, imageFor(work, "Primary"), { onEpisode(work) }, Modifier.width(150.dp))
                    }
                }
            }
            Spacer(Modifier.height(26.dp))
        }
    }
}

@Composable
private fun DetailBackdrop(
    item: EmbyItem,
    loading: Boolean,
    isWide: Boolean,
    imageFor: (EmbyItem, String) -> String?,
) {
    val background = MaterialTheme.colorScheme.background
    Box(Modifier.fillMaxSize()) {
        (imageFor(item, "Backdrop") ?: imageFor(item, "Primary"))?.let { image ->
            AsyncImage(
                image,
                item.name,
                Modifier.fillMaxWidth().height(if (isWide) 620.dp else 430.dp).alpha(if (isWide) .32f else .24f),
                contentScale = ContentScale.Crop,
            )
        }
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    0f to Color.Transparent,
                    .42f to background.copy(alpha = .42f),
                    .72f to background.copy(alpha = .90f),
                    1f to background,
                ),
            ),
        )
        if (isWide) {
            Box(
                Modifier.fillMaxSize().background(
                    Brush.horizontalGradient(
                        0f to background.copy(alpha = .52f),
                        .58f to Color.Transparent,
                        1f to background.copy(alpha = .24f),
                    ),
                ),
            )
        }
        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth().align(Alignment.TopCenter))
    }
}

@Composable
private fun DetailHeader(item: EmbyItem, isWide: Boolean) {
    Column(
        Modifier.fillMaxWidth().padding(top = if (isWide) 30.dp else 8.dp, bottom = 2.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            item.name,
            style = if (isWide) MaterialTheme.typography.displaySmall else MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        val meta = listOfNotNull(
            item.productionYear?.toString(),
            item.officialRating,
            item.communityRating?.let { "★ %.1f".format(it) },
            item.durationMs.takeIf { it > 0 }?.let { "${it / 60_000} 分钟" },
        )
        if (meta.isNotEmpty()) {
            Text(
                meta.joinToString(" · "),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = if (isWide) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyLarge,
            )
        }
        item.tagline?.takeIf { it.isNotBlank() }?.let {
            Text(it, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
private fun PlaybackActions(item: EmbyItem, onPlay: (Boolean) -> Unit, isTv: Boolean) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(9.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(if (isTv) 18.dp else 10.dp)) {
            if (item.hasResumePosition) {
                FocusActionButton(
                    text = "继续播放",
                    icon = Icons.Default.PlayArrow,
                    isTv = isTv,
                    primary = true,
                    modifier = Modifier.weight(1f),
                    onClick = { onPlay(true) },
                )
                FocusActionButton(
                    text = "从头",
                    icon = Icons.Default.Replay,
                    isTv = isTv,
                    primary = false,
                    onClick = { onPlay(false) },
                )
            } else {
                FocusActionButton(
                    text = "立即播放",
                    icon = Icons.Default.PlayArrow,
                    isTv = isTv,
                    primary = true,
                    modifier = Modifier.weight(1f),
                    onClick = { onPlay(false) },
                )
            }
        }

        if (item.hasResumePosition) {
            val fraction = (
                item.userData.playedPercentage?.toFloat()?.div(100f)
                    ?: if (item.durationMs > 0) item.resumePositionMs.toFloat() / item.durationMs else 0f
                ).coerceIn(0f, 1f)
            LinearProgressIndicator(
                progress = { fraction },
                modifier = Modifier.fillMaxWidth().height(3.dp),
                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            )
            Row(Modifier.fillMaxWidth()) {
                Text(
                    "上次播放到 ${detailTime(item.resumePositionMs)} · ${(fraction * 100).toInt()}%",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                if (item.durationMs > 0) {
                    Text(
                        "共 ${detailTime(item.durationMs)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun FocusActionButton(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isTv: Boolean,
    primary: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val focusActive = isTv && focused
    val scale by animateFloatAsState(if (focusActive) 1.03f else 1f, label = "detailActionScale")
    val normalContainer = if (primary) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest
    val normalContent = if (primary) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
    Button(
        onClick = onClick,
        modifier = modifier.onFocusChanged { focused = it.isFocused }.scale(scale),
        shape = RoundedCornerShape(14.dp),
        border = if (focusActive) BorderStroke(3.dp, MaterialTheme.colorScheme.onSurface) else null,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (focusActive) MaterialTheme.colorScheme.tertiary else normalContainer,
            contentColor = if (focusActive) MaterialTheme.colorScheme.onTertiary else normalContent,
        ),
        contentPadding = PaddingValues(horizontal = if (isTv) 20.dp else 16.dp, vertical = if (isTv) 13.dp else 10.dp),
    ) {
        Icon(icon, null)
        Spacer(Modifier.width(7.dp))
        Text(text, fontWeight = if (focusActive) FontWeight.Bold else FontWeight.SemiBold)
    }
}

@Composable
private fun DetailPill(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isTv: Boolean,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val focusActive = isTv && focused
    val scale by animateFloatAsState(if (focusActive) 1.05f else 1f, label = "detailPillScale")
    Surface(
        onClick = onClick,
        modifier = Modifier.onFocusChanged { focused = it.isFocused }.scale(scale),
        shape = RoundedCornerShape(999.dp),
        color = if (focusActive) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = .90f),
        border = if (focusActive) BorderStroke(2.dp, MaterialTheme.colorScheme.onSurface) else null,
    ) {
        Row(
            Modifier.padding(horizontal = 13.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Icon(icon, null, Modifier.size(18.dp))
            Text(text, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun FocusSurfaceButton(
    isTv: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(16.dp),
    normalColor: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    content: @Composable () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val focusActive = isTv && focused
    val scale by animateFloatAsState(if (focusActive) 1.07f else 1f, label = "detailSurfaceScale")
    Surface(
        onClick = onClick,
        modifier = modifier.onFocusChanged { focused = it.isFocused }.scale(scale),
        shape = shape,
        color = if (focusActive) MaterialTheme.colorScheme.primaryContainer else normalColor,
        border = if (focusActive) BorderStroke(2.dp, MaterialTheme.colorScheme.onSurface) else null,
        content = content,
    )
}

private fun detailTime(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds) else "%02d:%02d".format(minutes, seconds)
}

private fun hasMediaInfo(item: EmbyItem): Boolean = item.mediaStreams.isNotEmpty() || item.path != null

@Composable
private fun MediaInfoPanel(item: EmbyItem) {
    val video = item.mediaStreams.firstOrNull { it.type.equals("Video", true) }
    val audio = item.mediaStreams.firstOrNull { it.type.equals("Audio", true) }
    InfoPanel("媒体信息") {
        Text(
            listOfNotNull(
                video?.codec?.uppercase(),
                if (item.width != null && item.height != null) "${item.width}×${item.height}" else null,
                audio?.displayTitle ?: audio?.codec?.uppercase(),
            ).joinToString(" · ").ifBlank { item.type },
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        item.path?.let { path ->
            Text(
                path,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun InfoPanel(title: String, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = .90f),
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

@Composable
private fun SectionHeader(title: String, subtitle: String, maxWidth: androidx.compose.ui.unit.Dp, padding: androidx.compose.ui.unit.Dp) {
    Row(
        Modifier.widthIn(max = maxWidth).fillMaxWidth().padding(horizontal = padding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun PersonCard(person: EmbyPerson, imageUrl: String?, isTv: Boolean, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val focusActive = isTv && focused
    val scale by animateFloatAsState(if (focusActive) 1.05f else 1f, label = "personCardScale")
    Surface(
        onClick = onClick,
        modifier = Modifier.width(if (isTv) 172.dp else 158.dp).onFocusChanged { focused = it.isFocused }.scale(scale),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = if (focusActive) BorderStroke(3.dp, MaterialTheme.colorScheme.primary) else null,
        tonalElevation = 4.dp,
    ) {
        Column {
            Box(Modifier.fillMaxWidth().aspectRatio(.82f).background(MaterialTheme.colorScheme.surfaceContainerHigh)) {
                if (imageUrl != null) AsyncImage(imageUrl, person.name, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                else Icon(Icons.Default.Person, null, Modifier.align(Alignment.Center).size(44.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(person.name, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    person.role ?: person.type.orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun EpisodeRow(
    item: EmbyItem,
    imageUrl: String?,
    isTv: Boolean,
    contentMaxWidth: androidx.compose.ui.unit.Dp,
    contentPadding: androidx.compose.ui.unit.Dp,
    onClick: () -> Unit,
    onPlay: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val focusActive = isTv && focused
    val scale by animateFloatAsState(if (focusActive) 1.018f else 1f, label = "episodeRowScale")
    Surface(
        onClick = onClick,
        modifier = Modifier.widthIn(max = contentMaxWidth).fillMaxWidth().padding(horizontal = contentPadding)
            .onFocusChanged { focused = it.isFocused }.scale(scale),
        shape = RoundedCornerShape(16.dp),
        color = if (focusActive) MaterialTheme.colorScheme.surfaceContainerHighest else MaterialTheme.colorScheme.surface.copy(alpha = .58f),
        border = if (focusActive) BorderStroke(3.dp, MaterialTheme.colorScheme.primary) else null,
    ) {
        Row(
            Modifier.fillMaxWidth().padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.width(if (isTv) 170.dp else 138.dp).aspectRatio(16f / 9f).clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            ) {
                if (imageUrl != null) AsyncImage(imageUrl, item.name, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                val progress = item.userData.playedPercentage?.toFloat()?.div(100f)
                    ?: if (item.runTimeTicks != null && item.runTimeTicks > 0) {
                        (item.userData.playbackPositionTicks.toDouble() / item.runTimeTicks).toFloat()
                    } else 0f
                if (progress > 0f && progress < 1f) {
                    LinearProgressIndicator(
                        progress = { progress.coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth().height(3.dp).align(Alignment.BottomCenter),
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(item.name, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    listOfNotNull(
                        item.parentIndexNumber?.let { "S$it" },
                        item.indexNumber?.let { "E$it" },
                        item.durationMs.takeIf { it > 0 }?.let { "${it / 60_000} 分钟" },
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (item.isPlayed) {
                Icon(Icons.Default.CheckCircle, "已看", tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
            }
            FocusActionButton(
                text = if (item.hasResumePosition) "继续" else "播放",
                icon = Icons.Default.PlayArrow,
                isTv = isTv,
                primary = false,
                onClick = onPlay,
            )
        }
    }
}

@Composable
private fun MoreFromEpisodeCard(
    item: EmbyItem,
    imageUrl: String?,
    isTv: Boolean,
    onClick: () -> Unit,
    onPlay: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val focusActive = isTv && focused
    val scale by animateFloatAsState(if (focusActive) 1.045f else 1f, label = "moreEpisodeScale")
    Surface(
        onClick = onClick,
        modifier = Modifier.width(if (isTv) 280.dp else 236.dp).onFocusChanged { focused = it.isFocused }.scale(scale),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = .94f),
        border = if (focusActive) BorderStroke(3.dp, MaterialTheme.colorScheme.primary) else null,
        tonalElevation = 3.dp,
    ) {
        Column {
            Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).background(MaterialTheme.colorScheme.surfaceVariant)) {
                if (imageUrl != null) AsyncImage(imageUrl, item.name, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                Surface(
                    onClick = onPlay,
                    modifier = Modifier.align(Alignment.Center),
                    shape = CircleShape,
                    color = Color.Black.copy(alpha = .56f),
                ) {
                    Icon(Icons.Default.PlayArrow, "播放", Modifier.padding(10.dp), tint = Color.White)
                }
                val progress = item.userData.playedPercentage?.toFloat()?.div(100f)
                    ?: if (item.runTimeTicks != null && item.runTimeTicks > 0) {
                        (item.userData.playbackPositionTicks.toDouble() / item.runTimeTicks).toFloat()
                    } else 0f
                if (progress > 0f && progress < 1f) {
                    LinearProgressIndicator(
                        progress = { progress.coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth().height(4.dp).align(Alignment.BottomCenter),
                    )
                }
            }
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    listOfNotNull(
                        item.parentIndexNumber?.let { "S$it" },
                        item.indexNumber?.let { "E$it" },
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(item.name, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                item.durationMs.takeIf { it > 0 }?.let {
                    Text("${it / 60_000} 分钟", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
