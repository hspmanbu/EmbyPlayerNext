package com.aistudio.embyplayer.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.aistudio.embyplayer.data.model.*
import com.aistudio.embyplayer.ui.Breadcrumb
import com.aistudio.embyplayer.ui.components.MediaCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    title: String,
    items: List<EmbyItem>,
    crumbs: List<Breadcrumb>,
    mode: EmbyDisplayMode,
    sortField: EmbySortField,
    sortOrder: EmbySortOrder,
    search: String,
    loading: Boolean,
    imageFor: (EmbyItem) -> String?,
    onBack: () -> Unit,
    onCrumb: (Int) -> Unit,
    onItem: (EmbyItem) -> Unit,
    onMode: (EmbyDisplayMode) -> Unit,
    onSearch: (String) -> Unit,
    onSort: (EmbySortField, EmbySortOrder) -> Unit,
    onRefresh: () -> Unit
) {
    var sortMenu by remember { mutableStateOf(false) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(title, maxLines = 1)
                        Text("${items.size} 项", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "返回") } },
                actions = { IconButton(onClick = onRefresh) { Icon(Icons.Default.Refresh, "刷新") } }
            )
        }
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad)) {
            if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())

            if (crumbs.size > 1) {
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 14.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    crumbs.forEachIndexed { idx, c ->
                        AssistChip(onClick = { onCrumb(idx) }, label = { Text(if (idx == 0) c.name else "› ${c.name}") })
                    }
                }
            }

            Surface(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp).fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceContainer
            ) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = search,
                        onValueChange = onSearch,
                        leadingIcon = { Icon(Icons.Default.Search, null) },
                        placeholder = { Text("在此媒体库中搜索") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.medium
                    )
                    Row(
                        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        EmbyDisplayMode.entries.forEach { m ->
                            FilterChip(
                                selected = mode == m,
                                onClick = { onMode(m) },
                                label = {
                                    Text(
                                        when (m) {
                                            EmbyDisplayMode.MOVIES -> "媒体"
                                            EmbyDisplayMode.FOLDERS -> "文件夹"
                                            EmbyDisplayMode.FAVORITES -> "收藏"
                                        }
                                    )
                                }
                            )
                        }
                        Box {
                            AssistChip(
                                onClick = { sortMenu = true },
                                label = { Text("${sortField.label} · ${sortOrder.label}") },
                                leadingIcon = { Icon(Icons.Default.Sort, null) }
                            )
                            DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                                EmbySortField.entries.forEach { f ->
                                    DropdownMenuItem(
                                        text = { Text(f.label) },
                                        onClick = { onSort(f, sortOrder); sortMenu = false }
                                    )
                                }
                                HorizontalDivider()
                                EmbySortOrder.entries.forEach { o ->
                                    DropdownMenuItem(
                                        text = { Text(o.label) },
                                        onClick = { onSort(sortField, o); sortMenu = false }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (items.isEmpty() && !loading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        if (search.isBlank()) "这里还没有内容" else "没有找到匹配内容",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(152.dp),
                    contentPadding = PaddingValues(12.dp),
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(items, key = { it.id }) { i -> MediaCard(i, imageFor(i), { onItem(i) }) }
                }
            }
        }
    }
}