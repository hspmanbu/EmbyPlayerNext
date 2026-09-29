package com.embyplayernext.he.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.embyplayernext.he.ui.components.tvScrollThenFocus
import com.embyplayernext.he.data.model.*
import com.embyplayernext.he.ui.Breadcrumb
import com.embyplayernext.he.ui.components.MediaCard
import com.embyplayernext.he.ui.components.MediaListItem
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch

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
    columns: Int,
    layoutMode: LibraryLayoutMode = LibraryLayoutMode.GRID,
    restoreItemId: String?,
    loading: Boolean,
    loadingMore: Boolean,
    imageFor: (EmbyItem) -> String?,
    onBack: () -> Unit,
    onCrumb: (Int) -> Unit,
    onItem: (EmbyItem) -> Unit,
    onMode: (EmbyDisplayMode) -> Unit,
    onSearch: (String) -> Unit,
    onSort: (EmbySortField, EmbySortOrder) -> Unit,
    onColumns: (Int) -> Unit,
    onLayoutMode: (LibraryLayoutMode) -> Unit = {},
    onRestoreConsumed: () -> Unit,
    onRefresh: () -> Unit,
    onLoadMore: () -> Unit,
) {
    var sortMenu by remember { mutableStateOf(false) }
    var columnsMenu by remember { mutableStateOf(false) }
    var searchExpanded by rememberSaveable { mutableStateOf(search.isNotBlank()) }
    val gridState = rememberLazyGridState()
    val scope = rememberCoroutineScope()

    val showScrollToTop by remember {
        derivedStateOf { gridState.firstVisibleItemIndex > 5 }
    }

    val modes = remember(collectionType, crumbs.size) { libraryModes(collectionType, crumbs.size > 1) }
    val contextKey = remember(crumbs, mode, search, sortField, sortOrder) {
        listOf(crumbs.lastOrNull()?.id.orEmpty(), mode.name, search, sortField.name, sortOrder.name).joinToString("|")
    }
    var previousContextKey by rememberSaveable { mutableStateOf(contextKey) }

    LaunchedEffect(contextKey) {
        if (previousContextKey != contextKey) {
            gridState.scrollToItem(0)
            previousContextKey = contextKey
        }
    }

    LaunchedEffect(gridState, items.size, totalItems, loading, loadingMore) {
        snapshotFlow {
            val last = gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
            val threshold = (gridState.layoutInfo.totalItemsCount - 8).coerceAtLeast(0)
            last >= threshold
        }
            .distinctUntilChanged()
            .filter { it }
            .collect {
                if (!loading && !loadingMore && items.isNotEmpty() && items.size < totalItems) onLoadMore()
            }
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val screenWidth = maxWidth
        val autoColumns = when {
            screenWidth >= 1400.dp -> 7
            screenWidth >= 1100.dp -> 6
            screenWidth >= 840.dp -> 5
            screenWidth >= 600.dp -> 4
            else -> 3
        }
        val actualColumns = if (columns <= 0) autoColumns else columns.coerceIn(2, 8)

        LaunchedEffect(restoreItemId, items, actualColumns, layoutMode) {
            val targetId = restoreItemId ?: return@LaunchedEffect
            val index = items.indexOfFirst { it.id == targetId }
            if (index >= 0) {
                val cols = if (layoutMode == LibraryLayoutMode.LIST) {
                    if (screenWidth >= 1100.dp) 2 else 1
                } else {
                    actualColumns
                }
                gridState.scrollToItem((index / cols) * cols)
                onRestoreConsumed()
            }
        }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text(title, maxLines = 1, fontWeight = FontWeight.SemiBold)
                            Text(
                                if (totalItems > items.size) "已显示 ${items.size} / $totalItems 项" else "$totalItems 项",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                    navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "返回") } },
                    actions = {
                        IconButton(onClick = { searchExpanded = true }) { Icon(Icons.Default.Search, "搜索") }
                        IconButton(onClick = {
                            val next = if (layoutMode == LibraryLayoutMode.GRID) LibraryLayoutMode.LIST else LibraryLayoutMode.GRID
                            onLayoutMode(next)
                        }) {
                            Icon(
                                if (layoutMode == LibraryLayoutMode.GRID) Icons.Default.ViewList else Icons.Default.GridView,
                                contentDescription = if (layoutMode == LibraryLayoutMode.GRID) "切换为列表视图" else "切换为网格视图"
                            )
                        }
                        if (layoutMode == LibraryLayoutMode.GRID) {
                            Box {
                                TextButton(onClick = { columnsMenu = true }) {
                                    Icon(Icons.Default.ViewModule, null, Modifier.size(18.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text(if (columns <= 0) "自适应(${actualColumns})" else "${actualColumns}列")
                                }
                                DropdownMenu(expanded = columnsMenu, onDismissRequest = { columnsMenu = false }) {
                                    DropdownMenuItem(
                                        text = { Text("自适应 (${autoColumns} 列)") },
                                        leadingIcon = { if (columns <= 0) Icon(Icons.Default.Check, null) },
                                        onClick = { onColumns(0); columnsMenu = false },
                                    )
                                    HorizontalDivider()
                                    (2..8).forEach { value ->
                                        DropdownMenuItem(
                                            text = { Text("$value 列") },
                                            leadingIcon = { if (columns == value) Icon(Icons.Default.Check, null) },
                                            onClick = { onColumns(value); columnsMenu = false },
                                        )
                                    }
                                }
                            }
                        }
                        IconButton(onClick = onRefresh) { Icon(Icons.Default.Refresh, "刷新") }
                    },
                )
            },
        ) { pad ->
            Box(Modifier.fillMaxSize().padding(pad)) {
                Column(Modifier.fillMaxSize()) {
                    if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())

                    if (searchExpanded) {
                        OutlinedTextField(
                            value = search,
                            onValueChange = onSearch,
                            leadingIcon = { Icon(Icons.Default.Search, null) },
                            trailingIcon = {
                                Row {
                                    if (search.isNotBlank()) {
                                        IconButton(onClick = { onSearch("") }) { Icon(Icons.Default.Clear, "清空") }
                                    }
                                    IconButton(onClick = {
                                        if (search.isNotBlank()) onSearch("")
                                        searchExpanded = false
                                    }) { Icon(Icons.Default.Close, "关闭搜索") }
                                }
                            },
                            placeholder = { Text("在“$title”中搜索") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp),
                            shape = RoundedCornerShape(16.dp),
                        )
                    }

                    if (crumbs.size > 1) {
                        Row(
                            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 14.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            crumbs.forEachIndexed { idx, c ->
                                AssistChip(
                                    onClick = { onCrumb(idx) },
                                    label = { Text(c.name, maxLines = 1) },
                                    leadingIcon = if (idx == 0) ({ Icon(Icons.Default.Home, null, Modifier.size(16.dp)) }) else null,
                                )
                                if (idx != crumbs.lastIndex) {
                                    Icon(Icons.Default.ChevronRight, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }

                    Surface(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 5.dp).fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        color = MaterialTheme.colorScheme.surfaceContainer,
                    ) {
                        Column(Modifier.padding(horizontal = 10.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(
                                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                modes.forEach { m ->
                                    FilterChip(
                                        selected = mode == m,
                                        onClick = { onMode(m) },
                                        label = { Text(m.label) },
                                        leadingIcon = if (mode == m) ({ Icon(Icons.Default.Check, null, Modifier.size(16.dp)) }) else null,
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
                                    modifier = Modifier.weight(1f),
                                )
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(
                                        onClick = {
                                            val nextOrder = if (sortOrder == EmbySortOrder.ASCENDING) EmbySortOrder.DESCENDING else EmbySortOrder.ASCENDING
                                            onSort(sortField, nextOrder)
                                        },
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(
                                            if (sortOrder == EmbySortOrder.ASCENDING) Icons.Default.ArrowUpward else Icons.Default.ArrowDownward,
                                            contentDescription = if (sortOrder == EmbySortOrder.ASCENDING) "当前升序，点击降序" else "当前降序，点击升序",
                                            modifier = Modifier.size(18.dp),
                                            tint = MaterialTheme.colorScheme.primary,
                                        )
                                    }
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
                                                    onClick = { onSort(field, sortOrder); sortMenu = false },
                                                )
                                            }
                                            HorizontalDivider()
                                            EmbySortOrder.entries.forEach { order ->
                                                DropdownMenuItem(
                                                    text = { Text(order.label) },
                                                    leadingIcon = { if (order == sortOrder) Icon(Icons.Default.Check, null) },
                                                    onClick = { onSort(sortField, order); sortMenu = false },
                                                )
                                            }
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
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Text(
                                    if (search.isBlank()) "当前分类没有内容" else "没有找到与“$search”匹配的内容",
                                    style = MaterialTheme.typography.titleMedium,
                                )
                                if (search.isNotBlank()) TextButton(onClick = { onSearch("") }) { Text("清除搜索") }
                            }
                        }
                    } else {
                        val gridCells = if (layoutMode == LibraryLayoutMode.LIST) {
                            GridCells.Fixed(if (screenWidth >= 1100.dp) 2 else 1)
                        } else {
                            GridCells.Fixed(actualColumns)
                        }
                        LazyVerticalGrid(
                            state = gridState,
                            columns = gridCells,
                            contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 6.dp, bottom = 48.dp),
                            modifier = Modifier.fillMaxSize().tvScrollThenFocus(gridState),
                            verticalArrangement = Arrangement.spacedBy(if (layoutMode == LibraryLayoutMode.LIST) 10.dp else 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            items(items, key = { it.id }) { item ->
                                if (layoutMode == LibraryLayoutMode.LIST) {
                                    MediaListItem(item, imageFor(item), { onItem(item) })
                                } else {
                                    MediaCard(item, imageFor(item), { onItem(item) })
                                }
                            }
                            if (items.isNotEmpty() && items.size < totalItems) {
                                item(span = { GridItemSpan(maxLineSpan) }) {
                                    Box(Modifier.fillMaxWidth().padding(12.dp), contentAlignment = Alignment.Center) {
                                        if (loadingMore) {
                                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                                CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                                                Text("正在加载更多…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                        } else {
                                            TextButton(onClick = onLoadMore) {
                                                Icon(Icons.Default.ExpandMore, null)
                                                Spacer(Modifier.width(6.dp))
                                                Text("继续加载（剩余 ${totalItems - items.size} 项）")
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                AnimatedVisibility(
                    visible = showScrollToTop,
                    enter = fadeIn() + scaleIn(),
                    exit = fadeOut() + scaleOut(),
                    modifier = Modifier.align(Alignment.BottomEnd).padding(end = 20.dp, bottom = 24.dp)
                ) {
                    SmallFloatingActionButton(
                        onClick = {
                            scope.launch { gridState.animateScrollToItem(0) }
                        },
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    ) {
                        Icon(Icons.Default.VerticalAlignTop, contentDescription = "回到顶部")
                    }
                }
            }
        }
    }
}

private fun libraryModes(collectionType: String?, nested: Boolean): List<EmbyDisplayMode> {
    if (nested) return listOf(
        EmbyDisplayMode.ALL, EmbyDisplayMode.FOLDERS, EmbyDisplayMode.FAVORITES,
        EmbyDisplayMode.UNPLAYED, EmbyDisplayMode.RESUMABLE,
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
