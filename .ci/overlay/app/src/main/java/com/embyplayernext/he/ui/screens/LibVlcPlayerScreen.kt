package com.embyplayernext.he.ui.screens

import android.app.Activity
import android.content.Context
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.media.AudioManager
import android.net.TrafficStats
import android.net.Uri
import android.os.Build
import android.view.SurfaceView
import android.view.View
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.focusable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.embyplayernext.he.data.model.EmbyItem
import com.embyplayernext.he.data.model.EmbyServerConfig
import com.embyplayernext.he.data.model.PlaybackDescriptor
import com.embyplayernext.he.util.DiagnosticsLogger
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import kotlin.math.abs
import kotlin.math.roundToInt

private data class VlcTrackOption(val id: Int, val label: String, val selected: Boolean)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibVlcPlayerScreen(
    descriptor: PlaybackDescriptor,
    config: EmbyServerConfig,
    previousItem: EmbyItem? = null,
    nextItem: EmbyItem? = null,
    onStart: suspend (PlaybackDescriptor, Long, Long?) -> Unit,
    onProgress: suspend (PlaybackDescriptor, Long, Long?, Boolean, String) -> Unit,
    onStopped: suspend (PlaybackDescriptor, Long, Long?) -> Unit,
    onPlayAdjacent: (EmbyItem) -> Unit,
    onExit: () -> Unit,
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val activity = context as? Activity
    val scope = rememberCoroutineScope()
    val logger = remember { DiagnosticsLogger(context) }
    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val isTv = (configuration.uiMode and Configuration.UI_MODE_TYPE_MASK) == Configuration.UI_MODE_TYPE_TELEVISION ||
        context.packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)
    val isRockchip = remember {
        listOf(Build.MANUFACTURER, Build.BRAND, Build.HARDWARE, Build.BOARD, Build.DEVICE, Build.PRODUCT)
            .any { value ->
                value.contains("rockchip", ignoreCase = true) ||
                    value.contains("rk35", ignoreCase = true) ||
                    value.contains("rk30", ignoreCase = true)
            }
    }

    val libVlc = remember {
        LibVLC(
            context.applicationContext,
            arrayListOf("--verbose=2", "--network-caching=1500", "--file-caching=1500"),
        )
    }
    var player by remember { mutableStateOf(MediaPlayer(libVlc)) }
    var playerGeneration by remember(descriptor.playSessionId) { mutableIntStateOf(0) }
    val surface = remember { SurfaceView(context).apply { isFocusable = false; isFocusableInTouchMode = false } }

    var started by remember(descriptor.playSessionId) { mutableStateOf(false) }
    var stopped by remember(descriptor.playSessionId) { mutableStateOf(false) }
    var failed by remember(descriptor.playSessionId) { mutableStateOf(false) }
    var ended by remember(descriptor.playSessionId) { mutableStateOf(false) }
    var buffering by remember(descriptor.playSessionId) { mutableStateOf(true) }
    var playing by remember { mutableStateOf(false) }
    var positionMs by remember(descriptor.playSessionId) { mutableLongStateOf(0L) }
    var durationMs by remember(descriptor.playSessionId) {
        mutableLongStateOf(descriptor.serverRunTimeTicks?.div(10_000L) ?: 0L)
    }
    var speed by remember { mutableFloatStateOf(1f) }
    var controlsVisible by remember { mutableStateOf(true) }
    var locked by remember { mutableStateOf(false) }
    var aspect by remember { mutableStateOf(AspectMode.FIT) }
    var settingsVisible by remember { mutableStateOf(false) }
    var settingsPage by remember { mutableStateOf(PlayerSettingsPage.MAIN) }
    var networkSpeed by remember { mutableStateOf("0.00 MB/s") }
    var audioTracks by remember { mutableStateOf<List<VlcTrackOption>>(emptyList()) }
    var subtitleTracks by remember { mutableStateOf<List<VlcTrackOption>>(emptyList()) }
    var scrubFraction by remember { mutableStateOf<Float?>(null) }
    var gestureHud by remember { mutableStateOf<GestureHud?>(null) }
    var uiTicker by remember { mutableLongStateOf(0L) }
    var remoteActivityTick by remember { mutableLongStateOf(0L) }
    var pendingRestartSeekMs by remember(descriptor.playSessionId) { mutableStateOf<Long?>(null) }

    fun playbackUrl(): String {
        if (config.accessToken.isBlank()) return descriptor.streamUrl
        val parsed = Uri.parse(descriptor.streamUrl)
        if (parsed.getQueryParameter("api_key") != null) return descriptor.streamUrl
        return parsed.buildUpon().appendQueryParameter("api_key", config.accessToken).build().toString()
    }

    fun configureVlcMedia(media: Media, reason: String) {
        val copyMode = config.vlcHardwareMode.equals("copy", ignoreCase = true)
        media.setHWDecoderEnabled(true, true)
        if (copyMode) {
            media.addOption(":no-mediacodec-dr")
            media.addOption(":no-omxil-dr")
        }
        media.addOption(":network-caching=1500")
        media.addOption(":file-caching=1500")
        logger.log(
            "LibVLCMode",
            "mode=${if (copyMode) "copy" else "direct"} reason=$reason hwDecoder=true directRendering=${!copyMode} item=${descriptor.item.id}",
        )
    }

    fun directSeekTo(targetMs: Long, reason: String) {
        val bounded = if (durationMs > 0) targetMs.coerceIn(0L, durationMs) else targetMs.coerceAtLeast(0L)
        runCatching { player.setTime(bounded) }
        positionMs = bounded
        logger.log("LibVLCSeek", "direct item=${descriptor.item.id} targetMs=$bounded reason=$reason")
    }

    fun restartDecoderAt(targetMs: Long) {
        val bounded = if (durationMs > 0) targetMs.coerceIn(0L, durationMs) else targetMs.coerceAtLeast(0L)
        pendingRestartSeekMs = bounded
        positionMs = bounded
        buffering = true
        controlsVisible = true
        val oldPlayer = player
        val oldGeneration = playerGeneration
        logger.log(
            "LibVLCSeek",
            "recreate begin item=${descriptor.item.id} targetMs=$bounded oldGeneration=$oldGeneration rockchip=$isRockchip",
        )
        runCatching { oldPlayer.setEventListener(null) }
        runCatching { oldPlayer.stop() }
        runCatching {
            val oldVout = oldPlayer.vlcVout
            if (oldVout.areViewsAttached()) oldVout.detachViews()
        }
        playerGeneration = oldGeneration + 1
        player = MediaPlayer(libVlc)
        logger.log(
            "LibVLCSeek",
            "recreate player item=${descriptor.item.id} targetMs=$bounded newGeneration=$playerGeneration",
        )
    }

    fun seekTo(targetMs: Long) {
        val bounded = if (durationMs > 0) targetMs.coerceIn(0L, durationMs) else targetMs.coerceAtLeast(0L)
        if (
            isRockchip &&
            config.vlcHardwareMode.equals("direct", ignoreCase = true) &&
            started &&
            !ended
        ) {
            restartDecoderAt(bounded)
        } else {
            directSeekTo(bounded, "plain-" + config.vlcHardwareMode)
        }
    }

    fun applyAspect() {
        runCatching {
            when (aspect) {
                AspectMode.FIT -> {
                    player.aspectRatio = null
                    player.scale = 0f
                }
                AspectMode.FILL -> {
                    player.aspectRatio = "16:9"
                    player.scale = 0f
                }
                AspectMode.STRETCH -> {
                    val w = surface.width.coerceAtLeast(1)
                    val h = surface.height.coerceAtLeast(1)
                    player.aspectRatio = "$w:$h"
                    player.scale = 0f
                }
            }
        }
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

    fun switchTo(item: EmbyItem) {
        scope.launch {
            if (!stopped) {
                stopped = true
                val p = runCatching { player.time.coerceAtLeast(0L) }.getOrDefault(positionMs)
                val d = runCatching { player.length }.getOrDefault(-1L).takeIf { it > 0 }
                onStopped(descriptor, p, d)
            }
            runCatching { player.stop() }
            onPlayAdjacent(item)
        }
    }

    fun toggleOrientation() {
        val host = activity ?: return
        host.requestedOrientation = if (isLandscape) ActivityInfo.SCREEN_ORIENTATION_PORTRAIT else ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        controlsVisible = true
    }

    LaunchedEffect(Unit) {
        while (isActive) {
            delay(500)
            uiTicker++
        }
    }

    DisposableEffect(Unit) {
        val oldOrientation = activity?.requestedOrientation
        val window = activity?.window
        val insetsController = window?.let { WindowCompat.getInsetsController(it, it.decorView) }
        insetsController?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        insetsController?.hide(WindowInsetsCompat.Type.systemBars())
        onDispose {
            insetsController?.show(WindowInsetsCompat.Type.systemBars())
            if (oldOrientation != null) activity?.requestedOrientation = oldOrientation
        }
    }

    DisposableEffect(libVlc) {
        onDispose {
            logger.log("LibVLC", "libVlcRelease item=${descriptor.item.id}")
            runCatching { libVlc.release() }
        }
    }

    DisposableEffect(player, surface, descriptor.playSessionId, playerGeneration) {
        val activePlayer = player
        val activeGeneration = playerGeneration
        val vout = activePlayer.vlcVout
        val layoutListener = View.OnLayoutChangeListener { _, left, top, right, bottom, _, _, _, _ ->
            val width = right - left
            val height = bottom - top
            if (width > 0 && height > 0) {
                vout.setWindowSize(width, height)
                applyAspect()
                logger.log("LibVLC", "surfaceWindow item=${descriptor.item.id} size=${width}x${height} aspect=${aspect.name}")
            }
        }
        surface.addOnLayoutChangeListener(layoutListener)
        vout.setVideoView(surface)
        vout.attachViews()
        surface.post {
            if (surface.width > 0 && surface.height > 0) vout.setWindowSize(surface.width, surface.height)
            applyAspect()
        }

        activePlayer.setEventListener { event ->
            when (event.type) {
                MediaPlayer.Event.Opening -> {
                    buffering = true
                    logger.log("LibVLC", "event=Opening item=${descriptor.item.id} generation=$activeGeneration")
                }
                MediaPlayer.Event.Playing -> {
                    playing = true
                    buffering = false
                    ended = false
                    runCatching { activePlayer.rate = speed }
                    applyAspect()
                    val restartTarget = pendingRestartSeekMs
                    if (restartTarget != null) {
                        pendingRestartSeekMs = null
                        scope.launch {
                            delay(140)
                            directSeekTo(restartTarget, "restart-after-playing")
                            delay(120)
                            applyAspect()
                            logger.log(
                                "LibVLCSeek",
                                "restart seek issued item=${descriptor.item.id} targetMs=$restartTarget surface=${surface.width}x${surface.height}",
                            )
                        }
                    }
                    if (!started) {
                        started = true
                        scope.launch {
                            val requested = descriptor.initialPositionMs.coerceAtLeast(0L)
                            if (requested > 0L) {
                                delay(100)
                                directSeekTo(requested, "initial-resume")
                            }
                            positionMs = runCatching { activePlayer.time.coerceAtLeast(0L) }.getOrDefault(requested)
                            durationMs = runCatching { activePlayer.length.coerceAtLeast(0L) }.getOrDefault(durationMs)
                            logger.log(
                                "LibVLC",
                                "event=Playing item=${descriptor.item.id} generation=$activeGeneration positionMs=$positionMs durationMs=$durationMs scale=${runCatching { activePlayer.scale }.getOrNull()} aspect=${runCatching { activePlayer.aspectRatio }.getOrNull()}",
                            )
                            onStart(descriptor, positionMs, durationMs.takeIf { it > 0 })
                        }
                    }
                }
                MediaPlayer.Event.Paused -> {
                    playing = false
                    buffering = false
                    logger.log("LibVLC", "event=Paused item=${descriptor.item.id}")
                }
                MediaPlayer.Event.Vout -> {
                    logger.log(
                        "LibVLC",
                        "event=Vout item=${descriptor.item.id} generation=$activeGeneration positionMs=${runCatching { activePlayer.time }.getOrDefault(-1L)} surface=${surface.width}x${surface.height}",
                    )
                }
                MediaPlayer.Event.EncounteredError -> {
                    failed = true
                    buffering = false
                    playing = false
                    controlsVisible = true
                    logger.log("LibVLC", "event=EncounteredError item=${descriptor.item.id} positionMs=${runCatching { activePlayer.time }.getOrDefault(-1L)}")
                }
                MediaPlayer.Event.EndReached -> {
                    playing = false
                    buffering = false
                    ended = true
                    controlsVisible = true
                    if (!stopped) {
                        stopped = true
                        scope.launch {
                            val p = runCatching { activePlayer.time.coerceAtLeast(0L) }.getOrDefault(positionMs)
                            val d = runCatching { activePlayer.length }.getOrDefault(-1L).takeIf { it > 0 }
                            logger.log("LibVLC", "event=EndReached item=${descriptor.item.id} positionMs=$p durationMs=$d")
                            onStopped(descriptor, p, d)
                        }
                    }
                }
            }
        }

        logger.log(
            "LibVLC",
            "surfaceAttached item=${descriptor.item.id} method=${descriptor.playMethod} sourceVideo=${descriptor.item.mediaStreams.firstOrNull { it.type.equals("Video", true) }?.displayTitle}",
        )

        onDispose {
            logger.log("LibVLC", "dispose item=${descriptor.item.id}")
            surface.removeOnLayoutChangeListener(layoutListener)
            runCatching { activePlayer.setEventListener(null) }
            runCatching { activePlayer.stop() }
            runCatching { if (vout.areViewsAttached()) vout.detachViews() }
            runCatching { activePlayer.release() }
            logger.log("LibVLC", "playerRelease item=${descriptor.item.id} generation=$activeGeneration")
        }
    }

    LaunchedEffect(descriptor.streamUrl, descriptor.playSessionId, playerGeneration) {
        failed = false
        ended = false
        buffering = true
        val media = Media(libVlc, Uri.parse(playbackUrl()))
        configureVlcMedia(media, if (playerGeneration == 0) "initial" else "recreate-$playerGeneration")
        logger.log(
            "LibVLC",
            "prepare item=${descriptor.item.id} generation=$playerGeneration method=${descriptor.playMethod} hwDecoder=true force=true vlcMode=${config.vlcHardwareMode} requestedMs=${descriptor.initialPositionMs} pendingSeekMs=${pendingRestartSeekMs ?: -1L} url=${descriptor.streamUrl.substringBefore('?')}",
        )
        player.media = media
        media.release()
        player.play()
    }

    LaunchedEffect(descriptor.playSessionId) {
        var heartbeat = 0
        while (isActive) {
            delay(1000)
            positionMs = runCatching { player.time.coerceAtLeast(0L) }.getOrDefault(positionMs)
            runCatching { player.length }.getOrDefault(-1L).takeIf { it > 0 }?.let { durationMs = it }
            audioTracks = runCatching {
                player.audioTracks?.map { track ->
                    VlcTrackOption(track.id, track.name ?: "音轨 " + track.id, track.id == player.audioTrack)
                }.orEmpty()
            }.getOrDefault(emptyList())
            subtitleTracks = runCatching {
                player.spuTracks?.map { track ->
                    VlcTrackOption(track.id, track.name ?: "字幕 " + track.id, track.id == player.spuTrack)
                }.orEmpty()
            }.getOrDefault(emptyList())
            heartbeat++
            if (heartbeat % 5 == 0) {
                logger.log(
                    "LibVLC",
                    "heartbeat item=${descriptor.item.id} positionMs=$positionMs durationMs=$durationMs playing=${runCatching { player.isPlaying }.getOrDefault(false)} failed=$failed speed=$speed surface=${surface.width}x${surface.height}",
                )
            }
            if (heartbeat % 10 == 0 && started && !ended) {
                onProgress(descriptor, positionMs, durationMs.takeIf { it > 0 }, !playing, "TimeUpdate")
            }
        }
    }

    LaunchedEffect(Unit) {
        var last = TrafficStats.getUidRxBytes(android.os.Process.myUid())
        var lastTime = System.currentTimeMillis()
        while (isActive) {
            delay(1000)
            val now = TrafficStats.getUidRxBytes(android.os.Process.myUid())
            val nowTime = System.currentTimeMillis()
            val bps = (now - last) * 1000.0 / (nowTime - lastTime).coerceAtLeast(1L)
            networkSpeed = if (bps >= 1024 * 1024) "%.2f MB/s".format(bps.coerceAtLeast(0.0) / 1024 / 1024)
            else "%.0f KB/s".format(bps.coerceAtLeast(0.0) / 1024)
            last = now
            lastTime = nowTime
        }
    }

    LaunchedEffect(controlsVisible, locked, settingsVisible, playing, ended, remoteActivityTick, isTv) {
        if (controlsVisible && !locked && !settingsVisible && !ended && playing) {
            delay(if (isTv) 7000 else 4200)
            controlsVisible = false
        }
    }

    LaunchedEffect(gestureHud) {
        if (gestureHud != null) {
            delay(850)
            gestureHud = null
        }
    }

    BackHandler {
        when {
            settingsVisible -> settingsVisible = false
            locked -> {
                locked = false
                controlsVisible = true
            }
            else -> exitPlayer()
        }
    }

    val playerFocusRequester = remember { FocusRequester() }
    val playFocusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { playerFocusRequester.requestFocus() }
    LaunchedEffect(controlsVisible, isTv) {
        if (controlsVisible && isTv && !locked && !settingsVisible) {
            delay(80)
            runCatching { playFocusRequester.requestFocus() }
        }
    }

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(playerFocusRequester)
            .focusable()
            .onPreviewKeyEvent { keyEvent ->
                if (keyEvent.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                if (isTv) remoteActivityTick++
                when (keyEvent.key) {
                    Key.MediaPlayPause -> {
                        if (playing) player.pause() else player.play()
                        controlsVisible = true
                        true
                    }
                    Key.MediaFastForward -> {
                        seekTo(positionMs + config.forwardSeconds * 1000L)
                        controlsVisible = true
                        true
                    }
                    Key.MediaRewind -> {
                        seekTo(positionMs - config.rewindSeconds * 1000L)
                        controlsVisible = true
                        true
                    }
                    Key.MediaNext -> {
                        nextItem?.let(::switchTo)
                        true
                    }
                    Key.MediaPrevious -> {
                        previousItem?.let(::switchTo)
                        true
                    }
                    Key.DirectionLeft -> {
                        if (!controlsVisible) {
                            seekTo(positionMs - config.rewindSeconds * 1000L)
                            controlsVisible = true
                            true
                        } else false
                    }
                    Key.DirectionRight -> {
                        if (!controlsVisible) {
                            seekTo(positionMs + config.forwardSeconds * 1000L)
                            controlsVisible = true
                            true
                        } else false
                    }
                    Key.DirectionUp, Key.DirectionDown, Key.Enter, Key.NumPadEnter -> {
                        if (!controlsVisible) {
                            controlsVisible = true
                            true
                        } else false
                    }
                    else -> false
                }
            },
    ) {
        val compact = maxHeight < 430.dp || maxWidth < 620.dp

        AndroidView(factory = { surface }, modifier = Modifier.fillMaxSize())

        Box(
            Modifier
                .matchParentSize()
                .pointerInput(locked, config.gestureSeekSeconds) {
                    var startX = 0f
                    var verticalGestureAllowed = false
                    var totalX = 0f
                    var totalY = 0f
                    var mode = GestureMode.NONE
                    var startBrightness = .5f
                    var startVolume = 0
                    val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
                    detectDragGestures(
                        onDragStart = { offset ->
                            startX = offset.x
                            verticalGestureAllowed = offset.y in (size.height * .18f)..(size.height * .82f)
                            totalX = 0f
                            totalY = 0f
                            mode = GestureMode.NONE
                            val currentBrightness = activity?.window?.attributes?.screenBrightness ?: -1f
                            startBrightness = if (currentBrightness in 0f..1f) currentBrightness else .5f
                            startVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                            controlsVisible = true
                        },
                        onDrag = { change, drag ->
                            if (locked) return@detectDragGestures
                            change.consume()
                            totalX += drag.x
                            totalY += drag.y
                            if (mode == GestureMode.NONE && (abs(totalX) > 18f || abs(totalY) > 18f)) {
                                mode = if (abs(totalX) >= abs(totalY)) GestureMode.SEEK
                                else if (!verticalGestureAllowed) GestureMode.NONE
                                else if (startX < size.width / 2f) GestureMode.BRIGHTNESS else GestureMode.VOLUME
                            }
                            when (mode) {
                                GestureMode.SEEK -> {
                                    val delta = (totalX / size.width * config.gestureSeekSeconds * 1000).toLong()
                                    val target = (positionMs + delta).coerceIn(0L, if (durationMs > 0) durationMs else Long.MAX_VALUE)
                                    gestureHud = GestureHud(
                                        if (delta >= 0) Icons.Default.FastForward else Icons.Default.FastRewind,
                                        if (delta >= 0) "快进" else "后退",
                                        (if (delta >= 0) "+" else "") + (delta / 1000) + " 秒 · " + vlcTime(target),
                                        if (durationMs > 0) target.toFloat() / durationMs else null,
                                    )
                                }
                                GestureMode.BRIGHTNESS -> {
                                    val value = (startBrightness - totalY / size.height).coerceIn(.05f, 1f)
                                    activity?.window?.let { window ->
                                        val attrs = window.attributes
                                        attrs.screenBrightness = value
                                        window.attributes = attrs
                                    }
                                    gestureHud = GestureHud(Icons.Default.Brightness6, "亮度", "${(value * 100).roundToInt()}%", value)
                                }
                                GestureMode.VOLUME -> {
                                    val fraction = (startVolume.toFloat() / maxVolume - totalY / size.height).coerceIn(0f, 1f)
                                    val volume = (fraction * maxVolume).roundToInt().coerceIn(0, maxVolume)
                                    audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, volume, 0)
                                    gestureHud = GestureHud(Icons.Default.VolumeUp, "音量", "${(fraction * 100).roundToInt()}%", fraction)
                                }
                                GestureMode.NONE -> Unit
                            }
                        },
                        onDragEnd = {
                            if (!locked && mode == GestureMode.SEEK && abs(totalX) > 35f) {
                                val delta = (totalX / size.width * config.gestureSeekSeconds * 1000).toLong()
                                seekTo(positionMs + delta)
                            }
                        },
                    )
                }
                .pointerInput(locked) {
                    detectTapGestures(
                        onTap = { controlsVisible = if (locked) true else !controlsVisible },
                        onDoubleTap = { tap ->
                            if (!locked) {
                                val seconds = if (tap.x < size.width / 2) -config.rewindSeconds else config.forwardSeconds
                                seekTo(positionMs + seconds * 1000L)
                                gestureHud = GestureHud(
                                    if (seconds < 0) Icons.Default.Replay10 else Icons.Default.Forward30,
                                    if (seconds < 0) "后退" else "快进",
                                    "${abs(seconds)} 秒",
                                )
                                controlsVisible = true
                            }
                        },
                    )
                },
        )

        if (buffering && !ended) {
            Surface(
                modifier = Modifier.align(Alignment.Center),
                shape = CircleShape,
                color = Color.Black.copy(alpha = .46f),
            ) {
                CircularProgressIndicator(Modifier.padding(18.dp).size(36.dp), color = Color.White, strokeWidth = 3.dp)
            }
        }

        if (controlsVisible || locked) {
            PlayerTopChrome(
                item = descriptor.item,
                networkSpeed = networkSpeed,
                locked = locked,
                onBack = ::exitPlayer,
                modifier = Modifier.align(Alignment.TopCenter),
            )

            if (!ended) {
                PlayerCenterControls(
                    compact = compact,
                    locked = locked,
                    isPlaying = playing,
                    previousEnabled = previousItem != null,
                    nextEnabled = nextItem != null,
                    onToggleLock = {
                        locked = !locked
                        controlsVisible = true
                    },
                    onPrevious = { previousItem?.let(::switchTo) },
                    onRewind = { seekTo(positionMs - config.rewindSeconds * 1000L) },
                    onTogglePlay = { if (playing) player.pause() else player.play() },
                    onForward = { seekTo(positionMs + config.forwardSeconds * 1000L) },
                    onNext = { nextItem?.let(::switchTo) },
                    playFocusRequester = playFocusRequester,
                    remoteMode = isTv && isLandscape,
                    modifier = Modifier.align(Alignment.Center),
                )

                if (!locked) {
                    PlayerBottomChrome(
                        tick = uiTicker,
                        position = positionMs,
                        duration = durationMs,
                        scrubFraction = scrubFraction,
                        speed = speed,
                        audioLabel = audioTracks.firstOrNull { it.selected }?.label ?: "默认",
                        subtitleLabel = subtitleTracks.firstOrNull { it.selected }?.label ?: "关闭",
                        aspect = aspect,
                        onScrub = {
                            scrubFraction = it
                            controlsVisible = true
                        },
                        onScrubFinished = {
                            val value = scrubFraction
                            if (durationMs > 0 && value != null) seekTo((value * durationMs).toLong())
                            scrubFraction = null
                        },
                        onSpeed = {
                            settingsPage = PlayerSettingsPage.SPEED
                            settingsVisible = true
                        },
                        onAudio = {
                            settingsPage = PlayerSettingsPage.AUDIO
                            settingsVisible = true
                        },
                        onSubtitle = {
                            settingsPage = PlayerSettingsPage.SUBTITLE
                            settingsVisible = true
                        },
                        onAspect = {
                            aspect = AspectMode.entries[(aspect.ordinal + 1) % AspectMode.entries.size]
                            applyAspect()
                        },
                        onRotate = ::toggleOrientation,
                        onMore = {
                            settingsPage = PlayerSettingsPage.MAIN
                            settingsVisible = true
                        },
                        remoteMode = isTv && isLandscape,
                        modifier = Modifier.align(Alignment.BottomCenter),
                    )
                }
            }
        }

        gestureHud?.let { hud -> GestureHudCard(hud, Modifier.align(Alignment.Center)) }

        if (ended) {
            PlaybackEndedCard(
                item = descriptor.item,
                nextItem = nextItem,
                onReplay = {
                    stopped = false
                    ended = false
                    seekTo(0L)
                    player.play()
                },
                onNext = { nextItem?.let(::switchTo) },
                onExit = ::exitPlayer,
                modifier = Modifier.align(Alignment.Center),
            )
        }

        if (failed) {
            Card(Modifier.align(Alignment.Center).padding(24.dp), shape = RoundedCornerShape(24.dp)) {
                Column(Modifier.widthIn(max = 420.dp).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.ErrorOutline, null, tint = MaterialTheme.colorScheme.error)
                        Spacer(Modifier.width(10.dp))
                        Text("播放遇到问题", style = MaterialTheme.typography.titleLarge)
                    }
                    Text("LibVLC 播放失败（不会自动切换内核或服务器转码）", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = {
                            failed = false
                            buffering = true
                            player.play()
                        }) { Text("重试") }
                        TextButton(onClick = ::exitPlayer) { Text("返回详情") }
                    }
                }
            }
        }
    }

    if (settingsVisible) {
        LibVlcPlayerSettingsSheet(
            page = settingsPage,
            descriptor = descriptor,
            config = config,
            speed = speed,
            networkSpeed = networkSpeed,
            audioTracks = audioTracks,
            subtitleTracks = subtitleTracks,
            remoteMode = isTv || maxOf(configuration.screenWidthDp, configuration.screenHeightDp) >= 900,
            onPage = { settingsPage = it },
            onSpeed = {
                speed = it
                runCatching { player.rate = it }
                logger.log("LibVLC", "rate item=${descriptor.item.id} rate=$it")
            },
            onSelectAudio = { id ->
                runCatching { player.audioTrack = id }
                logger.log("LibVLC", "audioTrack item=${descriptor.item.id} id=$id")
            },
            onSelectSubtitle = { id ->
                runCatching { player.spuTrack = id }
                logger.log("LibVLC", "subtitleTrack item=${descriptor.item.id} id=$id")
            },
            onDisableSubtitle = {
                runCatching { player.spuTrack = -1 }
                logger.log("LibVLC", "subtitleTrack item=${descriptor.item.id} id=off")
            },
            onDismiss = {
                settingsVisible = false
                settingsPage = PlayerSettingsPage.MAIN
            },
        )
    }
}

@Composable
private fun LibVlcPlayerSettingsSheet(
    page: PlayerSettingsPage,
    descriptor: PlaybackDescriptor,
    config: EmbyServerConfig,
    speed: Float,
    networkSpeed: String,
    audioTracks: List<VlcTrackOption>,
    subtitleTracks: List<VlcTrackOption>,
    remoteMode: Boolean,
    onPage: (PlayerSettingsPage) -> Unit,
    onSpeed: (Float) -> Unit,
    onSelectAudio: (Int) -> Unit,
    onSelectSubtitle: (Int) -> Unit,
    onDisableSubtitle: () -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = !remoteMode),
    ) {
        Box(Modifier.fillMaxSize().padding(if (remoteMode) 36.dp else 16.dp), contentAlignment = Alignment.Center) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth(if (remoteMode) .78f else .96f)
                    .fillMaxHeight(if (remoteMode) .78f else .86f)
                    .widthIn(max = 1040.dp),
                shape = RoundedCornerShape(if (remoteMode) 28.dp else 24.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                tonalElevation = 8.dp,
            ) {
                when (page) {
                    PlayerSettingsPage.MAIN -> {
                        LazyColumn(
                            Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(24.dp),
                            verticalArrangement = Arrangement.spacedBy(18.dp),
                        ) {
                            item {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("播放信息", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
                                    IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, "关闭") }
                                }
                                Text("低频信息集中在这里，常用操作仍保留在播放器一级界面。", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            item {
                                SettingsGroup("当前媒体") {
                                    val item = descriptor.item
                                    val video = item.mediaStreams.firstOrNull { it.type.equals("Video", true) }
                                    val audio = item.mediaStreams.firstOrNull { it.type.equals("Audio", true) }
                                    InfoLine("播放方式", if (descriptor.playMethod.equals("Transcode", true)) "服务器转码" else "Direct Play / Direct Stream")
                                    InfoLine("实时网络", networkSpeed)
                                    InfoLine("视频", listOfNotNull(video?.codec?.uppercase(), if (item.width != null && item.height != null) "${item.width}×${item.height}" else null).joinToString(" · ").ifBlank { "未知" })
                                    InfoLine("音频", audio?.displayTitle ?: audio?.codec?.uppercase() ?: "未知")
                                    InfoLine("解码", "LibVLC · MediaCodec 硬解")
                                    InfoLine("缓存", "网络缓存 1500 ms")
                                }
                            }
                            item {
                                SettingsGroup("手势") {
                                    Text("双击左/右侧：后退 / 快进", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text("水平滑动：快速定位", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text("左侧上下滑：亮度 · 右侧上下滑：音量", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                    PlayerSettingsPage.SPEED -> SpeedPickerPage(speed, remoteMode, { onSpeed(it); onDismiss() }, { onPage(PlayerSettingsPage.MAIN) }, onDismiss)
                    PlayerSettingsPage.AUDIO -> LibVlcTrackPickerPage("音轨", audioTracks, false, onSelectAudio, {}, remoteMode, { onPage(PlayerSettingsPage.MAIN) }, onDismiss)
                    PlayerSettingsPage.SUBTITLE -> LibVlcTrackPickerPage("字幕", subtitleTracks, true, onSelectSubtitle, onDisableSubtitle, remoteMode, { onPage(PlayerSettingsPage.MAIN) }, onDismiss)
                }
            }
        }
    }
}

@Composable
private fun LibVlcTrackPickerPage(
    title: String,
    tracks: List<VlcTrackOption>,
    allowDisable: Boolean,
    onSelect: (Int) -> Unit,
    onDisable: () -> Unit,
    remoteMode: Boolean,
    onBack: () -> Unit,
    onDone: () -> Unit,
) {
    Column(Modifier.fillMaxSize().padding(22.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "返回") }
            Text(title, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
            IconButton(onClick = onDone) { Icon(Icons.Default.Close, "关闭") }
        }
        Spacer(Modifier.height(10.dp))
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 8.dp),
            verticalArrangement = Arrangement.spacedBy(if (remoteMode) 10.dp else 8.dp),
        ) {
            if (allowDisable) {
                item("disable") {
                    PlayerChoiceTile(
                        selected = tracks.none { it.selected },
                        title = "关闭字幕",
                        leading = Icons.Default.SubtitlesOff,
                        onClick = {
                            onDisable()
                            onDone()
                        },
                    )
                }
            }
            if (tracks.isEmpty()) {
                item("empty") {
                    Text("当前媒体没有可选择的$title", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(16.dp))
                }
            } else {
                items(tracks, key = { it.id }) { track ->
                    PlayerChoiceTile(
                        selected = track.selected,
                        title = track.label,
                        onClick = {
                            onSelect(track.id)
                            onDone()
                        },
                    )
                }
            }
        }
    }
}

private fun vlcTime(ms: Long): String {
    val total = (ms / 1000L).coerceAtLeast(0L)
    val h = total / 3600L
    val m = (total % 3600L) / 60L
    val s = total % 60L
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
}
