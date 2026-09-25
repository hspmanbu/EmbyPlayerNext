package com.aistudio.embyplayer.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.aistudio.embyplayer.data.model.EmbyView
import com.aistudio.embyplayer.ui.components.LibraryCoverCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibrariesScreen(
    views: List<EmbyView>,
    loading: Boolean,
    imageFor: (EmbyView) -> String?,
    onOpen: (EmbyView) -> Unit,
    onRefresh: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("媒体库")
                        Text("${views.size} 个资料库", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                actions = { IconButton(onClick = onRefresh) { Icon(Icons.Default.Refresh, "刷新") } }
            )
        }
    ) { pad ->
        Box(Modifier.fillMaxSize().padding(pad)) {
            if (views.isEmpty() && !loading) {
                Column(
                    Modifier.align(Alignment.Center).padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(Icons.Default.VideoLibrary, null, Modifier.size(56.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("没有可用媒体库", style = MaterialTheme.typography.titleLarge)
                    Text("请检查当前账号的媒体库权限", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedButton(onClick = onRefresh) { Text("重新加载") }
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(220.dp),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    items(views, key = { it.id }) { view ->
                        LibraryCoverCard(view, imageFor(view), { onOpen(view) })
                    }
                }
            }
            if (loading) LinearProgressIndicator(Modifier.fillMaxWidth().align(Alignment.TopCenter))
        }
    }
}
