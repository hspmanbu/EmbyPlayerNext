package com.aistudio.embyplayer.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
    onBack: () -> Unit,
) {
    var category by remember(query) { mutableStateOf("全部") }
    val categories = remember(results) {
        buildList {
            add("全部")
            if (results.any { it.type == "Movie" }) add("电影")
            if (results.any { it.type == "Series" }) add("剧集")
            if (results.any { it.type == "Episode" }) add("单集")
            if (results.any { it.type in setOf("Audio", "MusicAlbum", "MusicArtist") }) add("音乐")
            if (results.any { it.type == "Photo" }) add("照片")
            if (results.any { it.type in setOf("Folder", "CollectionFolder", "BoxSet") }) add("合集/文件夹")
        }
    }
    val filtered = remember(results, category) {
        results.filter { item ->
            when (category) {
                "电影" -> item.type == "Movie"
                "剧集" -> item.type == "Series"
                "单集" -> item.type == "Episode"
                "音乐" -> item.type in setOf("Audio", "MusicAlbum", "MusicArtist")
                "照片" -> item.type == "Photo"
                "合集/文件夹" -> item.type in setOf("Folder", "CollectionFolder", "BoxSet")
                else -> true
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("搜索") },
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
                placeholder = { Text("搜索标题、剧集、歌曲、照片…") },
                singleLine = true,
                shape = RoundedCornerShape(18.dp)
            )
            if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())

            if (query.isNotBlank() && results.isNotEmpty()) {
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    categories.forEach { c ->
                        FilterChip(selected = category == c, onClick = { category = c }, label = { Text(c) })
                    }
                }
                Text(
                    "${filtered.size} 个结果",
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            when {
                query.isBlank() -> SearchWelcome()
                filtered.isEmpty() && !loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.SearchOff, null, Modifier.size(52.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("没有找到与“$query”匹配的内容", style = MaterialTheme.typography.titleMedium)
                        if (category != "全部") TextButton(onClick = { category = "全部" }) { Text("查看全部类型") }
                    }
                }
                else -> LazyVerticalGrid(
                    columns = GridCells.Adaptive(150.dp),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(14.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(filtered, key = { it.id }) { item ->
                        MediaCard(item, imageFor(item), { onItem(item) }, showTypeBadge = true)
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchWelcome() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            Modifier.padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(Icons.Default.Search, null, Modifier.size(56.dp), tint = MaterialTheme.colorScheme.primary)
            Text("搜索整个 Emby", style = MaterialTheme.typography.headlineMedium)
            Text("电影、剧集、单集、音乐、照片和合集均可统一检索", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
