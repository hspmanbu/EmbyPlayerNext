package com.aistudio.embyplayer.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.aistudio.embyplayer.data.model.EmbyItem
import com.aistudio.embyplayer.ui.components.MediaCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    query: String,
    results: List<EmbyItem>,
    loading: Boolean,
    imageFor: (EmbyItem) -> String?,
    onQuery: (String) -> Unit,
    onItem: (EmbyItem) -> Unit,
    onBack: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("全局搜索") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "返回") } }
            )
        }
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad)) {
            OutlinedTextField(
                value = query,
                onValueChange = onQuery,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                leadingIcon = { Icon(Icons.Default.Search, null) },
                trailingIcon = {
                    if (query.isNotBlank()) IconButton(onClick = { onQuery("") }) { Icon(Icons.Default.Clear, "清空") }
                },
                placeholder = { Text("片名、剧名、单集名称") },
                singleLine = true,
                shape = MaterialTheme.shapes.large
            )
            if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())

            when {
                query.isBlank() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.Search, null, Modifier.size(52.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("搜索整个 Emby 媒体库", style = MaterialTheme.typography.titleLarge)
                        Text("支持电影、剧集、视频和合集", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                results.isEmpty() && !loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("没有找到与“$query”匹配的内容", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                else -> LazyVerticalGrid(
                    columns = GridCells.Adaptive(150.dp),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(results, key = { it.id }) { item -> MediaCard(item, imageFor(item), { onItem(item) }) }
                }
            }
        }
    }
}