package com.embyplayernext.he.ui.screens

import android.view.KeyEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlin.math.abs

data class PlayerTrackOption(val id: String, val label: String, val selected: Boolean = false)

@Composable
fun SharedPlayerControls(
    title: String,
    visible: Boolean,
    locked: Boolean,
    positionMs: Long,
    durationMs: Long,
    isPlaying: Boolean,
    networkSpeed: String? = null,
    rewindSeconds: Int,
    forwardSeconds: Int,
    gestureSeekSeconds: Int = 60,
    playbackSpeed: Float = 1f,
    audioTracks: List<PlayerTrackOption> = emptyList(),
    subtitleTracks: List<PlayerTrackOption> = emptyList(),
    onBack: () -> Unit,
    onToggleLock: () -> Unit,
    onSeek: (Long) -> Unit,
    onTogglePlay: () -> Unit,
    onSetSpeed: (Float) -> Unit,
    onSelectAudio: (String) -> Unit,
    onSelectSubtitle: (String) -> Unit,
    onAspect: () -> Unit,
    onSetVisible: (Boolean) -> Unit = {},
) {
    var speedDialog by remember { mutableStateOf(false) }
    var audioDialog by remember { mutableStateOf(false) }
    var subtitleDialog by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        runCatching { focusRequester.requestFocus() }
    }

    val safeDuration = durationMs.coerceAtLeast(0L)
    val safePosition = if (safeDuration > 0) positionMs.coerceIn(0L, safeDuration) else positionMs.coerceAtLeast(0L)

    Box(
        Modifier
            .fillMaxSize()
            .focusRequester(focusRequester)
            .focusable()
            .onPreviewKeyEvent { event ->
                if (event.nativeKeyEvent.action != KeyEvent.ACTION_DOWN) return@onPreviewKeyEvent false
                when (event.nativeKeyEvent.keyCode) {
                    KeyEvent.KEYCODE_DPAD_LEFT -> {
                        if (!locked) {
                            onSeek((safePosition - rewindSeconds * 1000L).coerceAtLeast(0L))
                            onSetVisible(true)
                        }
                        true
                    }
                    KeyEvent.KEYCODE_DPAD_RIGHT -> {
                        if (!locked) {
                            onSeek(safePosition + forwardSeconds * 1000L)
                            onSetVisible(true)
                        }
                        true
                    }
                    KeyEvent.KEYCODE_DPAD_UP,
                    KeyEvent.KEYCODE_DPAD_DOWN -> {
                        if (!locked) onSetVisible(true)
                        true
                    }
                    KeyEvent.KEYCODE_DPAD_CENTER,
                    KeyEvent.KEYCODE_ENTER -> {
                        if (locked) return@onPreviewKeyEvent true
                        if (!visible) onSetVisible(true) else onTogglePlay()
                        true
                    }
                    else -> false
                }
            }
            .pointerInput(locked, safePosition, safeDuration) {
                var totalX = 0f
                detectDragGestures(
                    onDragStart = { totalX = 0f },
                    onDrag = { change, drag ->
                        if (!locked) {
                            change.consume()
                            totalX += drag.x
                        }
                    },
                    onDragEnd = {
                        if (!locked && abs(totalX) > 50f) {
                            val delta = (totalX / size.width * gestureSeekSeconds * 1000f).toLong()
                            onSeek((safePosition + delta).coerceAtLeast(0L))
                        }
                    },
                )
            }
            .pointerInput(locked, visible, safePosition) {
                detectTapGestures(
                    onTap = { if (!locked) onSetVisible(!visible) },
                    onDoubleTap = { pos ->
                        if (!locked) {
                            val delta = if (pos.x < size.width / 2f) -rewindSeconds * 1000L else forwardSeconds * 1000L
                            onSeek((safePosition + delta).coerceAtLeast(0L))
                        }
                    },
                )
            },
    ) {
        if (!locked) {
            Box(
                Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(.22f)
                    .align(Alignment.CenterEnd)
                    .pointerInput(playbackSpeed) {
                        detectTapGestures(
                            onPress = {
                                val old = playbackSpeed
                                onSetSpeed(2f)
                                tryAwaitRelease()
                                onSetSpeed(old)
                            },
                        )
                    },
            )
        }

        if (visible || locked) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .background(Color.Black.copy(alpha = .45f))
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (!locked) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, null, tint = Color.White)
                    }
                }
                Text(title, color = Color.White, modifier = Modifier.weight(1f))
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
                        .align(Alignment.BottomCenter)
                        .background(Color.Black.copy(alpha = .5f))
                        .padding(10.dp),
                ) {
                    Slider(
                        value = if (safeDuration > 0) safePosition.toFloat() / safeDuration else 0f,
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
                        IconButton(onClick = { speedDialog = true }) {
                            Icon(Icons.Default.Speed, null, tint = Color.White)
                        }
                        IconButton(onClick = { audioDialog = true }, enabled = audioTracks.isNotEmpty()) {
                            Icon(Icons.Default.Audiotrack, null, tint = if (audioTracks.isNotEmpty()) Color.White else Color.Gray)
                        }
                        IconButton(onClick = { subtitleDialog = true }) {
                            Icon(Icons.Default.Subtitles, null, tint = Color.White)
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

    if (speedDialog) {
        val speeds = listOf(.5f, .75f, 1f, 1.25f, 1.5f, 1.75f, 2f)
        AlertDialog(
            onDismissRequest = { speedDialog = false },
            title = { Text("选择播放倍速") },
            text = {
                Column {
                    speeds.forEach { value ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onSetSpeed(value)
                                    speedDialog = false
                                }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = abs(playbackSpeed - value) < .01f, onClick = null)
                            Text(value.toString() + "x")
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { speedDialog = false }) { Text("关闭") } },
        )
    }

    if (audioDialog) {
        SharedTrackDialog(
            title = "选择音轨",
            tracks = audioTracks,
            includeOff = false,
            onDismiss = { audioDialog = false },
            onSelect = {
                onSelectAudio(it)
                audioDialog = false
            },
        )
    }

    if (subtitleDialog) {
        SharedTrackDialog(
            title = "选择字幕",
            tracks = subtitleTracks,
            includeOff = true,
            onDismiss = { subtitleDialog = false },
            onSelect = {
                onSelectSubtitle(it)
                subtitleDialog = false
            },
        )
    }
}

@Composable
private fun SharedTrackDialog(
    title: String,
    tracks: List<PlayerTrackOption>,
    includeOff: Boolean,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                if (includeOff) {
                    Row(
                        Modifier.fillMaxWidth().clickable { onSelect("__off__") }.padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = tracks.none { it.selected }, onClick = null)
                        Text("关闭字幕")
                    }
                }
                tracks.forEach { track ->
                    Row(
                        Modifier.fillMaxWidth().clickable { onSelect(track.id) }.padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = track.selected, onClick = null)
                        Text(track.label)
                    }
                }
            }
        },
        confirmButton = {},
    )
}

private fun sharedTime(ms: Long): String {
    val total = (ms / 1000L).coerceAtLeast(0L)
    val h = total / 3600L
    val m = (total % 3600L) / 60L
    val s = total % 60L
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
}
