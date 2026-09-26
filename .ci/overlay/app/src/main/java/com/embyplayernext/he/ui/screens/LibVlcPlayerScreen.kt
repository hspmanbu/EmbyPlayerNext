package com.embyplayernext.he.ui.screens

import android.net.Uri
import android.view.SurfaceView
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.embyplayernext.he.data.model.EmbyServerConfig
import com.embyplayernext.he.data.model.PlaybackDescriptor
import com.embyplayernext.he.util.DiagnosticsLogger
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer

@Composable
fun LibVlcPlayerScreen(
    descriptor: PlaybackDescriptor,
    config: EmbyServerConfig,
    onStart: suspend (PlaybackDescriptor, Long, Long?) -> Unit,
    onProgress: suspend (PlaybackDescriptor, Long, Long?, Boolean, String) -> Unit,
    onStopped: suspend (PlaybackDescriptor, Long, Long?) -> Unit,
    onExit: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val logger = remember { DiagnosticsLogger(context) }
    val libVlc = remember {
        LibVLC(
            context.applicationContext,
            arrayListOf("--verbose=2", "--network-caching=1500", "--file-caching=1500"),
        )
    }
    val player = remember { MediaPlayer(libVlc) }
    val surface = remember { SurfaceView(context) }

    var started by remember(descriptor.playSessionId) { mutableStateOf(false) }
    var stopped by remember(descriptor.playSessionId) { mutableStateOf(false) }
    var failed by remember(descriptor.playSessionId) { mutableStateOf(false) }
    var playing by remember { mutableStateOf(false) }
    var positionMs by remember(descriptor.playSessionId) { mutableLongStateOf(0L) }
    var durationMs by remember(descriptor.playSessionId) {
        mutableLongStateOf(descriptor.serverRunTimeTicks?.div(10_000L) ?: 0L)
    }

    fun playbackUrl(): String {
        if (config.accessToken.isBlank()) return descriptor.streamUrl
        val parsed = Uri.parse(descriptor.streamUrl)
        if (parsed.getQueryParameter("api_key") != null) return descriptor.streamUrl
        return parsed.buildUpon()
            .appendQueryParameter("api_key", config.accessToken)
            .build()
            .toString()
    }

    fun exitPlayer() {
        if (stopped) {
            onExit()
            return
        }
        stopped = true
        val p = runCatching { player.time.coerceAtLeast(0L) }.getOrDefault(positionMs)
        val d = runCatching { player.length }.getOrDefault(-1L).takeIf { it > 0 }
        logger.log("LibVLC", "exit item=${descriptor.item.id} positionMs=$p durationMs=$d")
        scope.launch {
            onStopped(descriptor, p, d)
            runCatching { player.stop() }
            onExit()
        }
    }

    DisposableEffect(player, surface, descriptor.playSessionId) {
        val vout = player.vlcVout
        vout.setVideoView(surface)
        vout.attachViews()

        player.setEventListener { event ->
            when (event.type) {
                MediaPlayer.Event.Opening -> logger.log("LibVLC", "event=Opening item=${descriptor.item.id}")
                MediaPlayer.Event.Playing -> {
                    playing = true
                    if (!started) {
                        started = true
                        scope.launch {
                            val requested = descriptor.initialPositionMs.coerceAtLeast(0L)
                            if (requested > 0L) {
                                delay(100)
                                runCatching { player.setTime(requested) }
                            }
                            positionMs = runCatching { player.time.coerceAtLeast(0L) }.getOrDefault(requested)
                            durationMs = runCatching { player.length.coerceAtLeast(0L) }.getOrDefault(durationMs)
                            logger.log(
                                "LibVLC",
                                "event=Playing item=${descriptor.item.id} positionMs=$positionMs durationMs=$durationMs",
                            )
                            onStart(descriptor, positionMs, durationMs.takeIf { it > 0 })
                        }
                    }
                }
                MediaPlayer.Event.Paused -> {
                    playing = false
                    logger.log("LibVLC", "event=Paused item=${descriptor.item.id}")
                }
                MediaPlayer.Event.Vout -> {
                    logger.log("LibVLC", "event=Vout item=${descriptor.item.id} positionMs=${runCatching { player.time }.getOrDefault(-1L)}")
                }
                MediaPlayer.Event.EncounteredError -> {
                    failed = true
                    playing = false
                    logger.log(
                        "LibVLC",
                        "event=EncounteredError item=${descriptor.item.id} positionMs=${runCatching { player.time }.getOrDefault(-1L)}",
                    )
                }
                MediaPlayer.Event.EndReached -> {
                    playing = false
                    if (!stopped) {
                        stopped = true
                        scope.launch {
                            val p = runCatching { player.time.coerceAtLeast(0L) }.getOrDefault(positionMs)
                            val d = runCatching { player.length }.getOrDefault(-1L).takeIf { it > 0 }
                            logger.log("LibVLC", "event=EndReached item=${descriptor.item.id} positionMs=$p durationMs=$d")
                            onStopped(descriptor, p, d)
                        }
                    }
                }
            }
        }
        logger.log("LibVLC", "surfaceAttached item=${descriptor.item.id} method=${descriptor.playMethod}")

        onDispose {
            logger.log("LibVLC", "dispose item=${descriptor.item.id}")
            runCatching { player.setEventListener(null) }
            runCatching { player.stop() }
            runCatching { if (vout.areViewsAttached()) vout.detachViews() }
            runCatching { player.release() }
            runCatching { libVlc.release() }
        }
    }

    LaunchedEffect(descriptor.streamUrl, descriptor.playSessionId) {
        failed = false
        val media = Media(libVlc, Uri.parse(playbackUrl()))
        media.setHWDecoderEnabled(true, true)
        media.addOption(":network-caching=1500")
        media.addOption(":file-caching=1500")
        logger.log(
            "LibVLC",
            "prepare item=${descriptor.item.id} method=${descriptor.playMethod} hwDecoder=true force=true requestedMs=${descriptor.initialPositionMs} url=${descriptor.streamUrl.substringBefore('?')}",
        )
        player.media = media
        media.release()
        player.play()
    }

    LaunchedEffect(descriptor.playSessionId) {
        var heartbeat = 0
        while (isActive && !stopped) {
            delay(1000)
            positionMs = runCatching { player.time.coerceAtLeast(0L) }.getOrDefault(positionMs)
            runCatching { player.length }.getOrDefault(-1L).takeIf { it > 0 }?.let { durationMs = it }
            heartbeat++
            if (heartbeat % 5 == 0) {
                logger.log(
                    "LibVLC",
                    "heartbeat item=${descriptor.item.id} positionMs=$positionMs durationMs=$durationMs playing=${runCatching { player.isPlaying }.getOrDefault(false)} failed=$failed",
                )
            }
            if (heartbeat % 10 == 0 && started) {
                onProgress(
                    descriptor,
                    positionMs,
                    durationMs.takeIf { it > 0 },
                    !runCatching { player.isPlaying }.getOrDefault(false),
                    "TimeUpdate",
                )
            }
        }
    }

    BackHandler { exitPlayer() }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(factory = { surface }, modifier = Modifier.fillMaxSize())
        Text(
            text = "LibVLC · 强制硬解 · DirectPlay",
            color = Color.White,
            modifier = Modifier.align(Alignment.TopCenter).background(Color.Black.copy(alpha = 0.55f)).padding(8.dp),
        )
        if (failed) {
            Text(
                text = "LibVLC 播放失败（不会自动切换内核或服务器转码）",
                color = Color.White,
                modifier = Modifier.align(Alignment.Center).background(Color.Black.copy(alpha = 0.75f)).padding(16.dp),
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth().align(Alignment.BottomCenter).background(Color.Black.copy(alpha = 0.55f)).padding(8.dp),
        ) {
            Button(onClick = { exitPlayer() }) { Text("返回") }
            Button(onClick = { if (player.isPlaying) player.pause() else player.play() }) {
                Text(if (playing) "暂停" else "播放")
            }
            Text(
                "  ${positionMs / 1000}s / ${if (durationMs > 0) durationMs / 1000 else 0}s",
                color = Color.White,
                modifier = Modifier.padding(10.dp),
            )
        }
    }
}
