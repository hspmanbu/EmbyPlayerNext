package com.embyplayernext.he.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Forward30
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.PauseCircle
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun SharedPlayerControls(
    title: String,
    engineLabel: String,
    visible: Boolean,
    locked: Boolean,
    positionMs: Long,
    durationMs: Long,
    isPlaying: Boolean,
    networkSpeed: String? = null,
    rewindSeconds: Int,
    forwardSeconds: Int,
    audioEnabled: Boolean = true,
    subtitleEnabled: Boolean = true,
    onBack: () -> Unit,
    onToggleLock: () -> Unit,
    onSeek: (Long) -> Unit,
    onTogglePlay: () -> Unit,
    onSpeed: () -> Unit,
    onAudio: () -> Unit,
    onSubtitle: () -> Unit,
    onAspect: () -> Unit,
) {
    if (!visible && !locked) return
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = .45f))
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (!locked) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, null, tint = Color.White)
                }
            }
            Column(Modifier.weight(1f)) {
                Text(title, color = Color.White)
                Text(engineLabel, color = Color.LightGray, style = MaterialTheme.typography.bodySmall)
            }
            networkSpeed?.let {
                Text("网速: $it", color = Color.White, style = MaterialTheme.typography.bodySmall)
            }
            IconButton(onClick = onToggleLock) {
                Icon(if (locked) Icons.Default.Lock else Icons.Default.LockOpen, null, tint = Color.White)
            }
        }

        if (!locked) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.Bottom,
            ) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = .5f))
                        .padding(10.dp),
                ) {
                    val safeDuration = durationMs.coerceAtLeast(0L)
                    val safePosition = if (safeDuration > 0) positionMs.coerceIn(0L, safeDuration) else positionMs.coerceAtLeast(0L)
                    Slider(
                        value = if (safeDuration > 0) safePosition.toFloat() / safeDuration.toFloat() else 0f,
                        onValueChange = { value ->
                            if (safeDuration > 0) onSeek((value * safeDuration).toLong())
                        },
                    )
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceEvenly,
                    ) {
                        Text(sharedTime(safePosition), color = Color.White)
                        IconButton(onClick = { onSeek((safePosition - rewindSeconds * 1000L).coerceAtLeast(0L)) }) {
                            Icon(Icons.Default.Replay10, null, tint = Color.White)
                        }
                        IconButton(onClick = onTogglePlay) {
                            Icon(
                                if (isPlaying) Icons.Default.PauseCircle else Icons.Default.PlayCircle,
                                null,
                                tint = Color.White,
                                modifier = Modifier.size(48.dp),
                            )
                        }
                        IconButton(onClick = { onSeek(safePosition + forwardSeconds * 1000L) }) {
                            Icon(Icons.Default.Forward30, null, tint = Color.White)
                        }
                        IconButton(onClick = onSpeed) {
                            Icon(Icons.Default.Speed, null, tint = Color.White)
                        }
                        IconButton(onClick = onAudio, enabled = audioEnabled) {
                            Icon(Icons.Default.Audiotrack, null, tint = if (audioEnabled) Color.White else Color.Gray)
                        }
                        IconButton(onClick = onSubtitle, enabled = subtitleEnabled) {
                            Icon(Icons.Default.Subtitles, null, tint = if (subtitleEnabled) Color.White else Color.Gray)
                        }
                        IconButton(onClick = onAspect) {
                            Icon(Icons.Default.AspectRatio, null, tint = Color.White)
                        }
                        Text(if (safeDuration > 0) sharedTime(safeDuration) else "--:--", color = Color.White)
                    }
                }
            }
        }
    }
}

private fun sharedTime(ms: Long): String {
    val seconds = (ms / 1000L).coerceAtLeast(0L)
    val h = seconds / 3600L
    val m = (seconds % 3600L) / 60L
    val s = seconds % 60L
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
}
