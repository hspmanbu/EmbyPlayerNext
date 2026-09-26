package com.aistudio.embyplayer.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.aistudio.embyplayer.data.model.*
import com.aistudio.embyplayer.ui.Breadcrumb
import com.aistudio.embyplayer.ui.components.MediaCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    title: String,
    collectionType: String?,
    items: List<EmbyItem>,
    totalItems: Int,
    crumbs: List<Breadcrumb>,
    mode: EmbyDisplayMode,
    sortField: EmbySortField,
    sortOrder: EmbySortOrder,
    search: String,
    loading: Boolean,
    loadingMore: Boolean,
    imageFor: (EmbyItem) -> String?,
    onBack: () -> Unit,
    onCrumb: (Int) -> Unit,
    onItem: (EmbyItem) -> Unit,
    onMode: (EmbyDisplayMode) -> Unit,
    onSearch: (String) -> Unit,
    onSort: (EmbySortField, EmbySortOrder) -> Unit,
    onRefresh: () -> Unit,
    onLoadMore: () -> Unit,
) {
    var sortMenu by remember { mutableStateOf(false) }
    var compactGrid by remember { mutableStateOf(false) }
    val modes = remember(collectionType, crumbs.size) { libraryModes(collectionType, crumbs.size > 1) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(title, maxLines = 1, fontWeight = FontWeight.SemiBold)
                        Text(
                            if (totalItems > items.size) "已显示 ${items.size} / $totalItems 项" else "$totalItems 项",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "返回") } },
                actions = {
                    IconButton(onClick = { compactGrid = !compactGrid }) {
                        Icon(if (compactGrid) Icons.Default.ViewComfy else Icons.Default.ViewModule, "切换网格密度")
                    }
                    IconButton(onClick = onRefresh) { Icon(Icons.Default.Refresh, "刷新") }
                }
            )
        }
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad)) {
            if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())

            if (crumbs.size > 1) {
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    crumbs.forEachIndexed { idx, c ->
                        AssistChip(
                            onClick = { onCrumb(idx) },
                            label = { Text(c.name, maxLines = 1) },
                            leadingIcon = if (idx == 0) ({ Icon(Icons.Default.Home, null, Modifier.size(16.dp)) }) else null
                        )
                        if (idx != crumbs.lastIndex) Icon(Icons.Default.ChevronRight, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            Surface(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp).fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                color = MaterialTheme.colorScheme.surfaceContainer
            ) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = search,
                        onValueChange = onSearch,
                        leadingIcon = { Icon(Icons.Default.Search, null) },
                        trailingIcon = {
                            if (search.isNotBlank()) IconButton(onClick = { onSearch("") }) { Icon(Icons.Default.Clear, "清空") }
                        },
                        placeholder = { Text("在“$title”中搜索") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp)
                    )

                    Row(
                        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        modes.forEach { m ->
                            FilterChip(
                                selected = mode == m,
                                onClick = { onMode(m) },
                                label = { Text(m.label) },
                                leadingIcon = if (mode == m) ({ Icon(Icons.Default.Check, null, Modifier.size(16.dp)) }) else null
                            )
                        }
                    }

                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            when {
                                search.isNotBlank() -> "搜索结果"
                                mode == EmbyDisplayMode.FAVORITES -> "已收藏"
                                mode == EmbyDisplayMode.UNPLAYED -> "未看内容"
                                mode == EmbyDisplayMode.RESUMABLE -> "继续观看"
                                else -> mode.label
                            },
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.weight(1f)
                        )
                        Box {
                            TextButton(onClick = { sortMenu = true }) {
                                Icon(Icons.Default.Sort, null, Modifier.size(18.dp))
                                Spacer(Modifier.width(5.dp))
                                Text("${sortField.label} · ${sortOrder.label}")
                            }
                            DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                                Text("排序字段", Modifier.padding(horizontal = 12.dp, vertical = 6.dp), style = MaterialTheme.typography.labelMedium)
                                EmbySortField.entries.forEach { field ->
                                    DropdownMenuItem(
                                        text = { Text(field.label) },
                                        leadingIcon = { if (field == sortField) Icon(Icons.Default.Check, null) },
                                        onClick = { onSort(field, sortOrder); sortMenu = false }
                                    )
                                }
                                HorizontalDivider()
                                EmbySortOrder.entries.forEach { order ->
                                    DropdownMenuItem(
                                        text = { Text(order.label) },
                                        leadingIcon = { if (order == sortOrder) Icon(Icons.Default.Check, null) },
                                        onClick = { onSort(sortField, order); sortMenu = false }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (items.isEmpty() && !loading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(
                            if (search.isBlank()) Icons.Default.VideoLibrary else Icons.Default.SearchOff,
                            null,
                            Modifier.size(52.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            if (search.isBlank()) "当前分类没有内容" else "没有找到与“$search”匹配的内容",
                            style = MaterialTheme.typography.titleMedium
                        )
                        if (search.isNotBlank()) TextButton(onClick = { onSearch("") }) { Text("清除搜索") }
                    }
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(if (compactGrid) 118.dp else 154.dp),
                    contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 8.dp, bottom = 24.dp),
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(18.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(items, key = { it.id }) { item ->
                        MediaCard(item, imageFor(item), { onItem(item) })
                    }
                    if (items.isNotEmpty() && items.size < totalItems) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            Box(Modifier.fillMaxWidth().padding(12.dp), contentAlignment = Alignment.Center) {
                                if (loadingMore) {
                                    CircularProgressIndicator(Modifier.size(28.dp))
                                } else {
                                    OutlinedButton(onClick = onLoadMore) {
                                        Icon(Icons.Default.ExpandMore, null)
                                        Spacer(Modifier.width(6.dp))
                                        Text("加载更多（剩余 ${totalItems - items.size} 项）")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun libraryModes(collectionType: String?, nested: Boolean): List<EmbyDisplayMode> {
    if (nested) return listOf(
        EmbyDisplayMode.ALL, EmbyDisplayMode.FOLDERS, EmbyDisplayMode.FAVORITES,
        EmbyDisplayMode.UNPLAYED, EmbyDisplayMode.RESUMABLE
    )
    return when (collectionType?.lowercase()) {
        "movies" -> listOf(EmbyDisplayMode.MOVIES, EmbyDisplayMode.FOLDERS, EmbyDisplayMode.FAVORITES, EmbyDisplayMode.UNPLAYED, EmbyDisplayMode.RESUMABLE)
        "tvshows" -> listOf(EmbyDisplayMode.SERIES, EmbyDisplayMode.EPISODES, EmbyDisplayMode.FOLDERS, EmbyDisplayMode.FAVORITES, EmbyDisplayMode.UNPLAYED, EmbyDisplayMode.RESUMABLE)
        "music" -> listOf(EmbyDisplayMode.ALBUMS, EmbyDisplayMode.ARTISTS, EmbyDisplayMode.SONGS, EmbyDisplayMode.FOLDERS, EmbyDisplayMode.FAVORITES)
        "books" -> listOf(EmbyDisplayMode.BOOKS, EmbyDisplayMode.FOLDERS, EmbyDisplayMode.FAVORITES)
        "musicvideos" -> listOf(EmbyDisplayMode.VIDEOS, EmbyDisplayMode.FOLDERS, EmbyDisplayMode.FAVORITES)
        "homevideos", "photos" -> listOf(EmbyDisplayMode.ALL, EmbyDisplayMode.PHOTOS, EmbyDisplayMode.VIDEOS, EmbyDisplayMode.FOLDERS, EmbyDisplayMode.FAVORITES)
        else -> listOf(EmbyDisplayMode.ALL, EmbyDisplayMode.FOLDERS, EmbyDisplayMode.FAVORITES, EmbyDisplayMode.UNPLAYED, EmbyDisplayMode.RESUMABLE)
    }
}
