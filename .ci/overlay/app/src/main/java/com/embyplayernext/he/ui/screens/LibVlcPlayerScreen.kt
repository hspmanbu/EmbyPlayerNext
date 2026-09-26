package com.embyplayernext.he.ui.screens

import android.net.Uri
import android.view.SurfaceView
import android.view.View
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
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
    var dragging by remember { mutableStateOf(false) }
    var dragPositionMs by remember { mutableFloatStateOf(0f) }
    var speed by remember { mutableFloatStateOf(1f) }
    var controlsVisible by remember { mutableStateOf(true) }
    var locked by remember { mutableStateOf(false) }
    var aspectMode by remember { mutableStateOf(0) }

    fun playbackUrl(): String {
        if (config.accessToken.isBlank()) return descriptor.streamUrl
        val parsed = Uri.parse(descriptor.streamUrl)
        if (parsed.getQueryParameter("api_key") != null) return descriptor.streamUrl
        return parsed.buildUpon()
            .appendQueryParameter("api_key", config.accessToken)
            .build()
            .toString()
    }

    fun seekTo(targetMs: Long) {
        val bounded = if (durationMs > 0) targetMs.coerceIn(0L, durationMs) else targetMs.coerceAtLeast(0L)
        runCatching { player.setTime(bounded) }
        positionMs = bounded
        logger.log("LibVLC", "seek item=${descriptor.item.id} targetMs=$bounded")
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
        val layoutListener = View.OnLayoutChangeListener { _, left, top, right, bottom, _, _, _, _ ->
            val width = right - left
            val height = bottom - top
            if (width > 0 && height > 0) {
                vout.setWindowSize(width, height)
                logger.log("LibVLC", "surfaceWindow item=${descriptor.item.id} size=${width}x${height}")
            }
        }
        surface.addOnLayoutChangeListener(layoutListener)
        vout.setVideoView(surface)
        vout.attachViews()
        runCatching {
            player.aspectRatio = null
            player.scale = 0f
        }
        surface.post {
            if (surface.width > 0 && surface.height > 0) {
                vout.setWindowSize(surface.width, surface.height)
            }
        }

        player.setEventListener { event ->
            when (event.type) {
                MediaPlayer.Event.Opening -> logger.log("LibVLC", "event=Opening item=${descriptor.item.id}")
                MediaPlayer.Event.Playing -> {
                    playing = true
                    runCatching {
                        player.aspectRatio = null
                        player.scale = 0f
                        player.rate = speed
                    }
                    if (!started) {
                        started = true
                        scope.launch {
                            val requested = descriptor.initialPositionMs.coerceAtLeast(0L)
                            if (requested > 0L) {
                                delay(100)
                                seekTo(requested)
                            }
                            positionMs = runCatching { player.time.coerceAtLeast(0L) }.getOrDefault(requested)
                            durationMs = runCatching { player.length.coerceAtLeast(0L) }.getOrDefault(durationMs)
                            logger.log(
                                "LibVLC",
                                "event=Playing item=${descriptor.item.id} positionMs=$positionMs durationMs=$durationMs scale=${runCatching { player.scale }.getOrNull()} aspect=${runCatching { player.aspectRatio }.getOrNull()}",
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
                    logger.log(
                        "LibVLC",
                        "event=Vout item=${descriptor.item.id} positionMs=${runCatching { player.time }.getOrDefault(-1L)} surface=${surface.width}x${surface.height}",
                    )
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
            surface.removeOnLayoutChangeListener(layoutListener)
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
            if (!dragging) {
                positionMs = runCatching { player.time.coerceAtLeast(0L) }.getOrDefault(positionMs)
            }
            runCatching { player.length }.getOrDefault(-1L).takeIf { it > 0 }?.let { durationMs = it }
            heartbeat++
            if (heartbeat % 5 == 0) {
                logger.log(
                    "LibVLC",
                    "heartbeat item=${descriptor.item.id} positionMs=$positionMs durationMs=$durationMs playing=${runCatching { player.isPlaying }.getOrDefault(false)} failed=$failed speed=$speed surface=${surface.width}x${surface.height}",
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

    BackHandler { if (!locked) exitPlayer() }

    Box(Modifier.fillMaxSize().background(Color.Black).pointerInput(locked) { detectTapGestures(onTap = { if (!locked) controlsVisible = !controlsVisible }) }) {
        AndroidView(factory = { surface }, modifier = Modifier.fillMaxSize())

        if (failed) {
            Text(
                text = "LibVLC 播放失败（不会自动切换内核或服务器转码）",
                color = Color.White,
                modifier = Modifier.align(Alignment.Center).background(Color.Black.copy(alpha = 0.75f)).padding(16.dp),
            )
        }

        SharedPlayerControls(
            title = descriptor.item.name,
            engineLabel = "LibVLC · 强制硬解",
            visible = controlsVisible,
            locked = locked,
            positionMs = if (dragging) dragPositionMs.toLong() else positionMs,
            durationMs = durationMs,
            isPlaying = playing,
            rewindSeconds = config.rewindSeconds,
            forwardSeconds = config.forwardSeconds,
            audioEnabled = false,
            subtitleEnabled = false,
            onBack = { exitPlayer() },
            onToggleLock = { locked = !locked },
            onSeek = { seekTo(it) },
            onTogglePlay = { if (player.isPlaying) player.pause() else player.play() },
            onSpeed = {
                speed = when (speed) {
                    1f -> 1.25f
                    1.25f -> 1.5f
                    1.5f -> 2f
                    else -> 1f
                }
                runCatching { player.rate = speed }
                logger.log("LibVLC", "rate item=${descriptor.item.id} rate=$speed")
            },
            onAudio = {},
            onSubtitle = {},
            onAspect = {
                aspectMode = (aspectMode + 1) % 3
                runCatching {
                    when (aspectMode) {
                        0 -> { player.aspectRatio = null; player.scale = 0f }
                        1 -> { player.aspectRatio = "16:9"; player.scale = 0f }
                        else -> { player.aspectRatio = null; player.scale = 1f }
                    }
                }
            },
        )
    }
}
