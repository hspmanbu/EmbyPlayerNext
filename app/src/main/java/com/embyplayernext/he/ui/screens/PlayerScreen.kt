@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package com.embyplayernext.he.ui.screens

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.media.AudioManager
import android.net.TrafficStats
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import android.content.pm.PackageManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.*
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.embyplayernext.he.data.model.EmbyItem
import com.embyplayernext.he.data.model.EmbyServerConfig
import com.embyplayernext.he.data.model.PlaybackDescriptor
import com.embyplayernext.he.playback.PlaybackService
import com.embyplayernext.he.util.DiagnosticsLogger
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

enum class AspectMode(val label: String) { FIT("适应"), FILL("填满"), STRETCH("拉伸") }
enum class PlayerSettingsPage { MAIN, SPEED, AUDIO, SUBTITLE }
enum class GestureMode { NONE, SEEK, BRIGHTNESS, VOLUME }
data class GestureHud(val icon: ImageVector, val title: String, val value: String, val progress: Float? = null)

@androidx.annotation.OptIn(UnstableApi::class)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(
    descriptor: PlaybackDescriptor,
    config: EmbyServerConfig,
    previousItem: EmbyItem? = null,
    nextItem: EmbyItem? = null,
    onStart: suspend (PlaybackDescriptor, Long, Long?) -> Unit,
    onProgress: suspend (PlaybackDescriptor, Long, Long?, Boolean, String) -> Unit,
    onStopped: suspend (PlaybackDescriptor, Long, Long?) -> Unit,
    onRecover: suspend (PlaybackDescriptor, Long) -> PlaybackDescriptor?,
    onPlayAdjacent: (EmbyItem) -> Unit,
    onExit: () -> Unit,
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val controller = rememberMediaController(context)
    val activity = context as? Activity
    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val isTv = (configuration.uiMode and Configuration.UI_MODE_TYPE_MASK) == Configuration.UI_MODE_TYPE_TELEVISION ||
        context.packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)

    var controlsVisible by remember { mutableStateOf(true) }
    var locked by remember { mutableStateOf(false) }
    var aspect by remember { mutableStateOf(AspectMode.FIT) }
    var speed by remember { mutableFloatStateOf(1f) }
    var settingsVisible by remember { mutableStateOf(false) }
    var settingsPage by remember { mutableStateOf(PlayerSettingsPage.MAIN) }
    var error by remember { mutableStateOf<String?>(null) }
    var networkSpeed by remember { mutableStateOf("0.00 MB/s") }
    var stopped by remember(descriptor.playSessionId) { mutableStateOf(false) }
    var started by remember(descriptor.playSessionId) { mutableStateOf(false) }
    var lastPaused by remember { mutableStateOf(false) }
    var ended by remember(descriptor.playSessionId) { mutableStateOf(false) }
    var buffering by remember { mutableStateOf(false) }
    var scrubFraction by remember { mutableStateOf<Float?>(null) }
    var gestureHud by remember { mutableStateOf<GestureHud?>(null) }
    var uiTicker by remember { mutableLongStateOf(0L) }
    var remoteActivityTick by remember { mutableLongStateOf(0L) }
    var playerIsPlaying by remember(descriptor.playSessionId) { mutableStateOf(false) }
    var remoteSeekTargetMs by remember(descriptor.playSessionId) { mutableStateOf<Long?>(null) }
    var remoteSeekKeyCode by remember(descriptor.playSessionId) { mutableIntStateOf(android.view.KeyEvent.KEYCODE_UNKNOWN) }
    var renderedFirstFrame by remember(descriptor.playSessionId) { mutableStateOf(false) }
    var recoveryAttempt by remember(descriptor.playSessionId) { mutableIntStateOf(0) }
    var recoveryStartPositionMs by remember(descriptor.playSessionId) { mutableLongStateOf(descriptor.initialPositionMs.coerceAtLeast(0L)) }
    var recoveryInProgress by remember(descriptor.playSessionId) { mutableStateOf(false) }
    var lastObservedFrameCount by remember(descriptor.playSessionId) { mutableLongStateOf(-1L) }
    var lastFrameAdvanceRealtimeMs by remember(descriptor.playSessionId) { mutableLongStateOf(0L) }
    var firstFrameRealtimeMs by remember(descriptor.playSessionId) { mutableLongStateOf(0L) }
    val diagnostics = remember { DiagnosticsLogger(context) }
    val rockchipFingerprint = remember {
        listOf(Build.MANUFACTURER, Build.BRAND, Build.HARDWARE, Build.BOARD, Build.DEVICE, Build.PRODUCT).joinToString(" ").lowercase()
    }
    val isRockchip = rockchipFingerprint.contains("rockchip") || rockchipFingerprint.contains("rk356") || Build.HARDWARE.lowercase().startsWith("rk")
    val isHevc = descriptor.item.mediaStreams.any { it.type.equals("Video", true) && it.codec?.contains("hevc", true) == true }
    val safeResumeMode = config.hardwareCompatibilityMode && isRockchip && isHevc && descriptor.playMethod == "DirectPlay" && descriptor.initialPositionMs > 0

    val durationMs: Long? = controller?.duration?.takeIf { it > 0 && it != C.TIME_UNSET }
        ?: descriptor.serverRunTimeTicks?.let { it / 10_000L }?.takeIf { it > 0 }

    LaunchedEffect(Unit) {
        while (isActive) {
            delay(500)
            uiTicker++
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, controller) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE && !settingsVisible) controller?.pause()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
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

    LaunchedEffect(controller, descriptor.streamUrl, recoveryAttempt) {
        val player = controller ?: return@LaunchedEffect
        val item = descriptor.item
        ended = false
        error = null
        renderedFirstFrame = false
        recoveryInProgress = false
        lastObservedFrameCount = -1L
        lastFrameAdvanceRealtimeMs = 0L
        firstFrameRealtimeMs = 0L
        player.setPlaybackSpeed(speed)
        playerIsPlaying = player.isPlaying
        val requestedMs = recoveryStartPositionMs.coerceAtLeast(0L)
        val backoffMs = when {
            safeResumeMode && recoveryAttempt == 0 -> 4_000L
            safeResumeMode && recoveryAttempt == 1 -> 15_000L
            else -> 0L
        }
        val safeStartMs = (requestedMs - backoffMs).coerceAtLeast(0L)
        diagnostics.log("PlayerAttempt", "prepare item=${item.id} session=${descriptor.playSessionId} attempt=$recoveryAttempt method=${descriptor.playMethod} requestedMs=$requestedMs safeStartMs=$safeStartMs backoffMs=$backoffMs rockchip=$isRockchip hevc=$isHevc safeResume=$safeResumeMode stream=${descriptor.streamUrl.substringBefore('?')}")
        val metadata = MediaMetadata.Builder()
            .setTitle(item.name)
            .setArtist(item.seriesName ?: item.albumArtist ?: item.artists.firstOrNull())
            .setAlbumTitle(item.seriesName ?: item.album)
            .build()
        player.setMediaItem(
            MediaItem.Builder()
                .setUri(descriptor.streamUrl)
                .setMediaId(item.id)
                .setMediaMetadata(metadata)
                .build(),
            safeStartMs,
        )
        player.prepare()
        player.playWhenReady = true
        diagnostics.log("PlayerAttempt", "prepare issued item=${item.id} attempt=$recoveryAttempt state=${player.playbackState} positionMs=${player.currentPosition} bufferedMs=${player.bufferedPosition} loading=${player.isLoading} playWhenReady=${player.playWhenReady}")
    }

    LaunchedEffect(controller, descriptor.playSessionId) {
        val player = controller ?: return@LaunchedEffect
        while (!started && isActive) {
            if (player.playbackState == Player.STATE_READY) {
                onStart(descriptor, player.currentPosition, player.duration.takeIf { it > 0 && it != C.TIME_UNSET })
                started = true
                lastPaused = !player.isPlaying
            } else delay(200)
        }
        while (isActive) {
            delay(10_000)
            if (started) onProgress(
                descriptor,
                player.currentPosition,
                player.duration.takeIf { it > 0 && it != C.TIME_UNSET },
                !player.isPlaying,
                "TimeUpdate",
            )
        }
    }

    DisposableEffect(controller, descriptor.playSessionId) {
        val player = controller
        val listener = if (player == null) null else object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                playerIsPlaying = isPlaying
                if (started && lastPaused == isPlaying) {
                    lastPaused = !isPlaying
                    scope.launch {
                        onProgress(
                            descriptor,
                            player.currentPosition,
                            player.duration.takeIf { it > 0 && it != C.TIME_UNSET },
                            !isPlaying,
                            if (isPlaying) "Unpause" else "Pause",
                        )
                    }
                }
            }

            override fun onRenderedFirstFrame() {
                renderedFirstFrame = true
                firstFrameRealtimeMs = SystemClock.elapsedRealtime()
                lastFrameAdvanceRealtimeMs = firstFrameRealtimeMs
                error = null
                diagnostics.log("PlayerRuntime", "firstFrame item=${descriptor.item.id} attempt=$recoveryAttempt requestedMs=${descriptor.initialPositionMs} currentMs=${player.currentPosition} state=${player.playbackState}")
            }

            override fun onPlayerError(playbackError: PlaybackException) {
                val causeChain = generateSequence<Throwable>(playbackError) { it.cause }
                    .take(8)
                    .joinToString(" <- ") { "${it.javaClass.name}:${it.message}" }
                diagnostics.log(
                    "PlayerError",
                    "code=${playbackError.errorCode} name=${playbackError.errorCodeName} message=${playbackError.message} state=${player.playbackState} positionMs=${player.currentPosition} bufferedMs=${player.bufferedPosition} loading=${player.isLoading} playWhenReady=${player.playWhenReady} uiCompat=${config.hardwareCompatibilityMode} cause=$causeChain"
                )
                val position = player.currentPosition
                scope.launch {
                    val recovered = onRecover(descriptor, position)
                    error = if (recovered != null) null else "播放遇到问题，请稍后重试"
                }
            }

            override fun onPlaybackStateChanged(state: Int) {
                buffering = state == Player.STATE_BUFFERING
                diagnostics.log("PlayerState", "state=$state item=${descriptor.item.id} attempt=$recoveryAttempt positionMs=${player.currentPosition} bufferedMs=${player.bufferedPosition} loading=${player.isLoading} isPlaying=${player.isPlaying} playWhenReady=${player.playWhenReady}")
                if (state == Player.STATE_ENDED && !stopped) {
                    ended = true
                    controlsVisible = true
                    stopped = true
                    scope.launch {
                        onStopped(
                            descriptor,
                            player.currentPosition,
                            player.duration.takeIf { it > 0 && it != C.TIME_UNSET },
                        )
                    }
                }
            }
        }
        if (listener != null) player?.addListener(listener)
        onDispose { if (listener != null) player?.removeListener(listener) }
    }

    LaunchedEffect(controller, descriptor.playSessionId, descriptor.playMethod, recoveryAttempt) {
        val player = controller ?: return@LaunchedEffect
        val hasVideo = descriptor.item.mediaStreams.any { it.type.equals("Video", true) } ||
            descriptor.item.type in setOf("Movie", "Episode", "Video", "MusicVideo", "Trailer")
        if (!hasVideo || descriptor.playMethod == "Transcode") return@LaunchedEffect
        val startedAt = SystemClock.elapsedRealtime()
        var noFirstFrameClockStart = startedAt
        var lastHeartbeat = 0L
        var wasActivelyPlaying = false
        var frameStallGraceUntilMs = startedAt + 3_500L
        while (isActive && !recoveryInProgress) {
            delay(500)
            val now = SystemClock.elapsedRealtime()
            val elapsed = now - startedAt
            val extras = player.sessionExtras
            val mediaId = extras.getString("vf_media_id", "")
            val frameCount = if (mediaId == descriptor.item.id) extras.getLong("vf_count", -1L) else -1L
            val lastFrameRealtime = if (mediaId == descriptor.item.id) extras.getLong("vf_last_realtime_ms", 0L) else 0L
            if (frameCount >= 0 && frameCount != lastObservedFrameCount) {
                lastObservedFrameCount = frameCount
                lastFrameAdvanceRealtimeMs = now
                diagnostics.log("PlayerFrames", "advance item=${descriptor.item.id} attempt=$recoveryAttempt count=$frameCount lastFrameRealtime=$lastFrameRealtime currentMs=${player.currentPosition}")
            }
            if (now - lastHeartbeat >= 2_000L) {
                lastHeartbeat = now
                diagnostics.log("PlayerRuntime", "heartbeat item=${descriptor.item.id} attempt=$recoveryAttempt state=${player.playbackState} positionMs=${player.currentPosition} bufferedMs=${player.bufferedPosition} totalBufferedMs=${player.totalBufferedDuration} durationMs=${player.duration} loading=${player.isLoading} isPlaying=${player.isPlaying} firstFrame=$renderedFirstFrame frameCount=$frameCount lastFrameAgeMs=${if (lastFrameAdvanceRealtimeMs > 0) now-lastFrameAdvanceRealtimeMs else -1}")
            }
            if (!player.playWhenReady) {
                noFirstFrameClockStart = now
                wasActivelyPlaying = false
                if (renderedFirstFrame) lastFrameAdvanceRealtimeMs = now
            }
            val activelyPlaying = player.playWhenReady && player.isPlaying
            if (activelyPlaying && !wasActivelyPlaying) {
                frameStallGraceUntilMs = now + 3_500L
                if (renderedFirstFrame) lastFrameAdvanceRealtimeMs = now
            }
            wasActivelyPlaying = activelyPlaying
            val noFirstFrameTimeout = if (recoveryAttempt == 0) 7_000L else 9_000L
            val noFirstFrame = !renderedFirstFrame && player.playWhenReady && now - noFirstFrameClockStart >= noFirstFrameTimeout
            val frameStalled = renderedFirstFrame && player.playbackState == Player.STATE_READY && activelyPlaying &&
                now >= frameStallGraceUntilMs &&
                lastFrameAdvanceRealtimeMs > 0 && now - lastFrameAdvanceRealtimeMs >= 3_000L &&
                player.currentPosition > 1_500L
            if (!noFirstFrame && !frameStalled) continue

            val reason = if (noFirstFrame) "noFirstFrame" else "videoFrameStall"
            recoveryInProgress = true
            diagnostics.log("PlayerWatchdog", "$reason item=${descriptor.item.id} attempt=$recoveryAttempt elapsedMs=$elapsed state=${player.playbackState} requestedMs=${descriptor.initialPositionMs} currentMs=${player.currentPosition} bufferedMs=${player.bufferedPosition} loading=${player.isLoading} firstFrame=$renderedFirstFrame frameCount=$frameCount lastFrameAgeMs=${if (lastFrameAdvanceRealtimeMs>0) now-lastFrameAdvanceRealtimeMs else -1} safeResume=$safeResumeMode")
            if (safeResumeMode && recoveryAttempt == 0) {
                recoveryStartPositionMs = player.currentPosition.coerceAtLeast(0L)
                diagnostics.log("PlayerRetry", "local OMX retry item=${descriptor.item.id} fromAttempt=0 toAttempt=1 requestedMs=$recoveryStartPositionMs reason=$reason")
                recoveryAttempt = 1
                continue
            }
            val recoverPosition = player.currentPosition.takeIf { it > 0L } ?: recoveryStartPositionMs
            diagnostics.log("PlayerRetry", "terminal recovery item=${descriptor.item.id} attempt=$recoveryAttempt recoverPositionMs=$recoverPosition reason=$reason method=${descriptor.playMethod}")
            val recovered = onRecover(descriptor, recoverPosition)
            if (recovered != null) {
                diagnostics.log("PlayerRetry", "terminal recovery success item=${descriptor.item.id} newMethod=${recovered.playMethod} newUrl=${recovered.streamUrl.substringBefore('?')}")
                error = null
            } else {
                diagnostics.log("PlayerRetry", "terminal recovery failed item=${descriptor.item.id}")
                error = "播放遇到问题，请稍后重试"
            }
            break
        }
    }

    fun exit() {
        val player = controller
        if (!stopped && player != null) {
            stopped = true
            scope.launch {
                onStopped(descriptor, player.currentPosition, player.duration.takeIf { it > 0 && it != C.TIME_UNSET })
                player.pause()
                onExit()
            }
        } else onExit()
    }

    fun switchTo(item: EmbyItem) {
        val player = controller
        if (player == null) {
            onPlayAdjacent(item)
            return
        }
        scope.launch {
            if (!stopped) {
                stopped = true
                onStopped(descriptor, player.currentPosition, player.duration.takeIf { it > 0 && it != C.TIME_UNSET })
            }
            player.pause()
            onPlayAdjacent(item)
        }
    }

    fun markPlayerInteraction(showControls: Boolean = true) {
        remoteActivityTick += 1L
        if (showControls) controlsVisible = true
    }

    fun quickRemoteSeek(deltaMs: Long) {
        val player = controller ?: return
        val max = player.duration.takeIf { it > 0 && it != C.TIME_UNSET } ?: Long.MAX_VALUE
        player.seekTo((player.currentPosition + deltaMs).coerceIn(0L, max))
        markPlayerInteraction()
    }

    fun updateRemoteScrub(direction: Int, keyCode: Int, repeatCount: Int) {
        val player = controller ?: return
        val duration = player.duration.takeIf { it > 0 && it != C.TIME_UNSET }
        val base = remoteSeekTargetMs ?: player.currentPosition
        val stepMs = when {
            repeatCount >= 24 -> 10_000L
            repeatCount >= 10 -> 5_000L
            else -> 2_000L
        }
        val upper = duration ?: Long.MAX_VALUE
        val target = (base + direction * stepMs).coerceIn(0L, upper)
        remoteSeekTargetMs = target
        remoteSeekKeyCode = keyCode
        scrubFraction = duration?.takeIf { it > 0 }?.let { target.toFloat() / it.toFloat() }
        gestureHud = GestureHud(
            if (direction < 0) Icons.Default.FastRewind else Icons.Default.FastForward,
            if (direction < 0) "长按后退" else "长按快进",
            time(target),
            duration?.takeIf { it > 0 }?.let { target.toFloat() / it.toFloat() },
        )
        markPlayerInteraction()
    }

    fun finishRemoteScrub(keyCode: Int): Boolean {
        val target = remoteSeekTargetMs ?: return false
        if (remoteSeekKeyCode != keyCode) return false
        controller?.seekTo(target)
        remoteSeekTargetMs = null
        remoteSeekKeyCode = android.view.KeyEvent.KEYCODE_UNKNOWN
        scrubFraction = null
        markPlayerInteraction()
        return true
    }

    fun toggleOrientation() {
        val host = activity ?: return
        host.requestedOrientation = if (isLandscape) {
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        } else {
            ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        }
        markPlayerInteraction()
    }

    BackHandler {
        when {
            settingsVisible -> settingsVisible = false
            locked -> { locked = false; markPlayerInteraction() }
            controlsVisible -> controlsVisible = false
            else -> exit()
        }
    }

    LaunchedEffect(Unit) {
        var last = TrafficStats.getUidRxBytes(android.os.Process.myUid())
        var lastTime = System.currentTimeMillis()
        while (isActive) {
            delay(1000)
            val now = TrafficStats.getUidRxBytes(android.os.Process.myUid())
            val nowTime = System.currentTimeMillis()
            val bps = (now - last) * 1000.0 / (nowTime - lastTime).coerceAtLeast(1)
            networkSpeed = if (bps >= 1024 * 1024) "%.2f MB/s".format(bps.coerceAtLeast(0.0) / 1024 / 1024)
            else "%.0f KB/s".format(bps.coerceAtLeast(0.0) / 1024)
            last = now
            lastTime = nowTime
        }
    }

    LaunchedEffect(
        controlsVisible,
        locked,
        settingsVisible,
        ended,
        remoteActivityTick,
        isTv,
        playerIsPlaying,
        remoteSeekTargetMs,
        gestureHud,
    ) {
        val shouldAutoHide = isTv || playerIsPlaying
        if (controlsVisible && !locked && !settingsVisible && !ended && shouldAutoHide && remoteSeekTargetMs == null && gestureHud == null) {
            val activityAtStart = remoteActivityTick
            delay(if (isTv) 5000 else 4200)
            if (
                activityAtStart == remoteActivityTick &&
                controlsVisible &&
                !locked &&
                !settingsVisible &&
                !ended &&
                (isTv || playerIsPlaying) &&
                remoteSeekTargetMs == null &&
                gestureHud == null
            ) {
                controlsVisible = false
            }
        }
    }

    LaunchedEffect(gestureHud) {
        if (gestureHud != null) {
            delay(1000)
            gestureHud = null
        }
    }

    val playerFocusRequester = remember { FocusRequester() }
    val playFocusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { playerFocusRequester.requestFocus() }
    LaunchedEffect(controlsVisible, isTv, locked, settingsVisible) {
        if (!isTv || settingsVisible) return@LaunchedEffect
        delay(80)
        if (controlsVisible && !locked) {
            runCatching { playFocusRequester.requestFocus() }
        } else {
            runCatching { playerFocusRequester.requestFocus() }
        }
    }

    BoxWithConstraints(
        Modifier.fillMaxSize().background(Color.Black)
            .focusRequester(playerFocusRequester)
            .focusable()
            .onPreviewKeyEvent { keyEvent ->
                val native = keyEvent.nativeKeyEvent
                val code = native.keyCode

                if (native.action == android.view.KeyEvent.ACTION_UP) {
                    return@onPreviewKeyEvent when (code) {
                        android.view.KeyEvent.KEYCODE_DPAD_LEFT,
                        android.view.KeyEvent.KEYCODE_DPAD_RIGHT,
                        android.view.KeyEvent.KEYCODE_MEDIA_REWIND,
                        android.view.KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> finishRemoteScrub(code)
                        else -> false
                    }
                }
                if (native.action != android.view.KeyEvent.ACTION_DOWN) return@onPreviewKeyEvent false

                if (isTv) remoteActivityTick += 1L
                when (code) {
                    android.view.KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                        if (native.repeatCount == 0) {
                            controller?.let { if (it.isPlaying) it.pause() else it.play() }
                            markPlayerInteraction()
                        }
                        true
                    }
                    android.view.KeyEvent.KEYCODE_MEDIA_PLAY -> {
                        if (native.repeatCount == 0) {
                            controller?.play()
                            markPlayerInteraction()
                        }
                        true
                    }
                    android.view.KeyEvent.KEYCODE_MEDIA_PAUSE -> {
                        if (native.repeatCount == 0) {
                            controller?.pause()
                            markPlayerInteraction()
                        }
                        true
                    }
                    android.view.KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> {
                        if (native.repeatCount > 0) updateRemoteScrub(1, code, native.repeatCount)
                        else quickRemoteSeek(config.forwardSeconds * 1000L)
                        true
                    }
                    android.view.KeyEvent.KEYCODE_MEDIA_REWIND -> {
                        if (native.repeatCount > 0) updateRemoteScrub(-1, code, native.repeatCount)
                        else quickRemoteSeek(-config.rewindSeconds * 1000L)
                        true
                    }
                    android.view.KeyEvent.KEYCODE_MEDIA_NEXT -> {
                        if (native.repeatCount == 0) nextItem?.let(::switchTo)
                        true
                    }
                    android.view.KeyEvent.KEYCODE_MEDIA_PREVIOUS -> {
                        if (native.repeatCount == 0) previousItem?.let(::switchTo)
                        true
                    }
                    android.view.KeyEvent.KEYCODE_DPAD_LEFT -> {
                        when {
                            native.repeatCount > 0 -> {
                                updateRemoteScrub(-1, code, native.repeatCount)
                                true
                            }
                            !controlsVisible -> {
                                quickRemoteSeek(-config.rewindSeconds * 1000L)
                                true
                            }
                            else -> false
                        }
                    }
                    android.view.KeyEvent.KEYCODE_DPAD_RIGHT -> {
                        when {
                            native.repeatCount > 0 -> {
                                updateRemoteScrub(1, code, native.repeatCount)
                                true
                            }
                            !controlsVisible -> {
                                quickRemoteSeek(config.forwardSeconds * 1000L)
                                true
                            }
                            else -> false
                        }
                    }
                    android.view.KeyEvent.KEYCODE_DPAD_CENTER,
                    android.view.KeyEvent.KEYCODE_ENTER,
                    android.view.KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                        if (native.repeatCount > 0) {
                            true
                        } else if (!controlsVisible) {
                            controller?.let { if (it.isPlaying) it.pause() else it.play() }
                            markPlayerInteraction()
                            true
                        } else {
                            false
                        }
                    }
                    android.view.KeyEvent.KEYCODE_DPAD_UP,
                    android.view.KeyEvent.KEYCODE_DPAD_DOWN -> {
                        if (!controlsVisible) {
                            markPlayerInteraction()
                            true
                        } else {
                            false
                        }
                    }
                    else -> false
                }
            },

    ) {
        val compact = maxHeight < 430.dp || maxWidth < 620.dp

        controller?.let { player ->
            AndroidView(
                factory = { ctx -> PlayerView(ctx).apply { this.player = player; useController = false; keepScreenOn = true; isFocusable = false; isFocusableInTouchMode = false } },
                update = { view ->
                    view.player = player
                    view.resizeMode = when (aspect) {
                        AspectMode.FIT -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                        AspectMode.FILL -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                        AspectMode.STRETCH -> AspectRatioFrameLayout.RESIZE_MODE_FILL
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )
        } ?: CircularProgressIndicator(Modifier.align(Alignment.Center), color = Color.White)

        Box(
            Modifier
                .matchParentSize()
                .pointerInput(locked, controller, config.gestureSeekSeconds) {
                    var startX = 0f
                    var verticalGestureAllowed = false
                    var totalX = 0f
                    var totalY = 0f
                    var mode = GestureMode.NONE
                    var startBrightness = .5f
                    var startVolume = 0
                    var gestureStartPositionMs = 0L
                    val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
                    detectDragGestures(
                        onDragStart = { offset ->
                            startX = offset.x
                            verticalGestureAllowed = offset.y in (size.height * .18f)..(size.height * .82f)
                            totalX = 0f
                            totalY = 0f
                            mode = GestureMode.NONE
                            val currentBrightness = activity?.window?.attributes?.screenBrightness ?: -1f
                            val systemBrightness = runCatching {
                                Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS) / 255f
                            }.getOrDefault(.5f)
                            startBrightness = if (currentBrightness in 0f..1f) currentBrightness else systemBrightness.coerceIn(.05f, 1f)
                            startVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                            gestureStartPositionMs = controller?.currentPosition ?: 0L
                            markPlayerInteraction(showControls = false)
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
                                if (mode != GestureMode.NONE) {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    controlsVisible = false
                                    markPlayerInteraction(showControls = false)
                                }
                            }
                            when (mode) {
                                GestureMode.SEEK -> {
                                    val player = controller ?: return@detectDragGestures
                                    val delta = (totalX / size.width * config.gestureSeekSeconds * 1000).toLong()
                                    val duration = player.duration.takeIf { it > 0 && it != C.TIME_UNSET } ?: durationMs ?: 0L
                                    val target = (gestureStartPositionMs + delta).coerceIn(0L, if (duration > 0) duration else Long.MAX_VALUE)
                                    val seconds = abs(delta / 1000)
                                    gestureHud = GestureHud(
                                        if (delta >= 0) Icons.Default.FastForward else Icons.Default.FastRewind,
                                        if (delta >= 0) "快进 ${seconds} 秒" else "后退 ${seconds} 秒",
                                        if (duration > 0) "${time(target)} / ${time(duration)}" else time(target),
                                        if (duration > 0) target.toFloat() / duration else null,
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
                                controller?.let { player ->
                                    val delta = (totalX / size.width * config.gestureSeekSeconds * 1000).toLong()
                                    val duration = player.duration.takeIf { it > 0 && it != C.TIME_UNSET } ?: durationMs ?: Long.MAX_VALUE
                                    player.seekTo((gestureStartPositionMs + delta).coerceIn(0L, duration))
                                    markPlayerInteraction(showControls = false)
                                }
                            }
                        },
                    )
                }
                .pointerInput(locked, controller) {
                    detectTapGestures(
                        onTap = { controlsVisible = if (locked) true else !controlsVisible },
                        onDoubleTap = { position ->
                            if (!locked) controller?.let { player ->
                                val seconds = if (position.x < size.width / 2) -config.rewindSeconds else config.forwardSeconds
                                val duration = player.duration.takeIf { it > 0 && it != C.TIME_UNSET } ?: durationMs ?: Long.MAX_VALUE
                                val target = (player.currentPosition + seconds * 1000L).coerceIn(0L, duration)
                                player.seekTo(target)
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                gestureHud = GestureHud(
                                    if (seconds < 0) Icons.Default.Replay10 else Icons.Default.Forward30,
                                    if (seconds < 0) "后退 ${abs(seconds)} 秒" else "快进 ${abs(seconds)} 秒",
                                    if (duration != Long.MAX_VALUE) "${time(target)} / ${time(duration)}" else time(target),
                                    if (duration != Long.MAX_VALUE && duration > 0) target.toFloat() / duration else null,
                                )
                                controlsVisible = false
                                markPlayerInteraction(showControls = false)
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

        gestureHud?.let { hud ->
            when (hud.title) {
                "亮度" -> GestureEdgeBar(
                    hud = hud,
                    modifier = Modifier.align(Alignment.CenterStart).padding(start = 18.dp),
                )
                "音量" -> GestureEdgeBar(
                    hud = hud,
                    modifier = Modifier.align(Alignment.CenterEnd).padding(end = 18.dp),
                )
                else -> GestureSeekHint(
                    hud = hud,
                    modifier = Modifier.align(Alignment.Center).offset(y = (-56).dp),
                )
            }
        }

        if (controlsVisible || locked) {
            PlayerTopChrome(
                item = descriptor.item,
                networkSpeed = networkSpeed,
                locked = locked,
                onBack = ::exit,
                modifier = Modifier.align(Alignment.TopCenter),
            )

            if (!ended) {
                val player = controller
                val position = player?.currentPosition ?: 0L
                val duration = (player?.duration ?: 0L).takeIf { it > 0 && it != C.TIME_UNSET } ?: durationMs ?: 0L
                PlayerCenterControls(
                    compact = compact,
                    locked = locked,
                    isPlaying = player?.isPlaying == true,
                    previousEnabled = previousItem != null,
                    nextEnabled = nextItem != null,
                    onToggleLock = { locked = !locked; markPlayerInteraction() },
                    onPrevious = { previousItem?.let(::switchTo) },
                    onRewind = {
                        player?.seekTo((position - config.rewindSeconds * 1000L).coerceAtLeast(0))
                        markPlayerInteraction()
                    },
                    onTogglePlay = {
                        if (player?.isPlaying == true) player.pause() else player?.play()
                        markPlayerInteraction()
                    },
                    onForward = {
                        player?.seekTo((position + config.forwardSeconds * 1000L).coerceAtMost(if (duration > 0) duration else Long.MAX_VALUE))
                        markPlayerInteraction()
                    },
                    onNext = { nextItem?.let(::switchTo) },
                    playFocusRequester = playFocusRequester,
                    remoteMode = isTv && isLandscape,
                    modifier = Modifier.align(Alignment.Center),
                )
                if (!locked) PlayerBottomChrome(
                    tick = uiTicker,
                    position = position,
                    duration = duration,
                    scrubFraction = scrubFraction,
                    speed = speed,
                    audioLabel = selectedTrackLabel(player, C.TRACK_TYPE_AUDIO) ?: "默认",
                    subtitleLabel = selectedTrackLabel(player, C.TRACK_TYPE_TEXT) ?: "关闭",
                    aspect = aspect,
                    onScrub = { scrubFraction = it; markPlayerInteraction() },
                    onScrubFinished = {
                        val value = scrubFraction
                        if (duration > 0 && value != null) player?.seekTo((value * duration).toLong())
                        scrubFraction = null
                    },
                    onSpeed = { markPlayerInteraction(); settingsPage = PlayerSettingsPage.SPEED; settingsVisible = true },
                    onAudio = { markPlayerInteraction(); settingsPage = PlayerSettingsPage.AUDIO; settingsVisible = true },
                    onSubtitle = { markPlayerInteraction(); settingsPage = PlayerSettingsPage.SUBTITLE; settingsVisible = true },
                    onAspect = {
                        aspect = AspectMode.entries[(aspect.ordinal + 1) % AspectMode.entries.size]
                        markPlayerInteraction()
                    },
                    onRotate = ::toggleOrientation,
                    onMore = { markPlayerInteraction(); settingsPage = PlayerSettingsPage.MAIN; settingsVisible = true },
                    remoteMode = isTv && isLandscape,
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
            }
        }

        error?.let { message ->
            Card(Modifier.align(Alignment.Center).padding(24.dp), shape = RoundedCornerShape(24.dp)) {
                Column(Modifier.widthIn(max = 420.dp).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.ErrorOutline, null, tint = MaterialTheme.colorScheme.error)
                        Spacer(Modifier.width(10.dp))
                        Text("播放遇到问题", style = MaterialTheme.typography.titleLarge)
                    }
                    Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { error = null; controller?.prepare(); controller?.play() }) { Text("重试") }
                        TextButton(onClick = ::exit) { Text("返回详情") }
                    }
                }
            }
        }
    }

    if (settingsVisible) {
        PlayerSettingsSheet(
            page = settingsPage,
            player = controller,
            descriptor = descriptor,
            config = config,
            speed = speed,
            networkSpeed = networkSpeed,
            remoteMode = isTv || maxOf(configuration.screenWidthDp, configuration.screenHeightDp) >= 900,
            onPage = { settingsPage = it },
            onSpeed = {
                speed = it
                controller?.setPlaybackSpeed(it)
            },
            onDismiss = { settingsVisible = false; settingsPage = PlayerSettingsPage.MAIN },
        )
    }
}

@Composable
fun PlayerTopChrome(
    item: EmbyItem,
    networkSpeed: String,
    locked: Boolean,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var clockText by remember { mutableStateOf(SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())) }
    LaunchedEffect(Unit) {
        while (isActive) {
            clockText = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
            delay(15_000L)
        }
    }
    Box(
        modifier.fillMaxWidth().background(
            Brush.verticalGradient(listOf(Color.Black.copy(alpha = .68f), Color.Black.copy(alpha = .22f), Color.Transparent)),
        ),
    ) {
        Row(
            Modifier.fillMaxWidth()
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (!locked) {
                PlayerRoundIcon(Icons.Default.ArrowBack, "返回", onBack)
                Spacer(Modifier.width(10.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(item.name, color = Color.White, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                playerSubtitle(item)?.let {
                    Text(it, color = Color.White.copy(alpha = .68f), style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            if (!locked) {
                PlayerInfoPill(clockText, Icons.Default.Schedule)
                Spacer(Modifier.width(8.dp))
                PlayerInfoPill(networkSpeed, Icons.Default.NetworkCheck)
            }
        }
    }
}

@Composable
fun PlayerCenterControls(
    compact: Boolean,
    locked: Boolean,
    isPlaying: Boolean,
    previousEnabled: Boolean,
    nextEnabled: Boolean,
    onToggleLock: () -> Unit,
    onPrevious: () -> Unit,
    onRewind: () -> Unit,
    onTogglePlay: () -> Unit,
    onForward: () -> Unit,
    onNext: () -> Unit,
    playFocusRequester: FocusRequester,
    remoteMode: Boolean,
    modifier: Modifier = Modifier,
) {
    val small = if (remoteMode) 54.dp else 48.dp
    val side = if (remoteMode) 62.dp else if (compact) 52.dp else 56.dp
    val play = if (remoteMode) 82.dp else if (compact) 68.dp else 74.dp
    Box(modifier.fillMaxWidth().padding(horizontal = if (compact) 14.dp else 24.dp)) {
        PlayerRoundIcon(
            if (locked) Icons.Default.Lock else Icons.Default.LockOpen,
            if (locked) "解锁" else "锁定",
            onToggleLock,
            size = small,
            modifier = Modifier.align(Alignment.CenterStart),
        )
        if (!locked) {
            Row(
                modifier = Modifier.align(Alignment.Center),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                if (previousEnabled) {
                    PlayerRoundIcon(Icons.Default.SkipPrevious, "上一集", onPrevious, size = small)
                    Spacer(Modifier.width(if (compact) 8.dp else 14.dp))
                }
                PlayerRoundIcon(Icons.Default.FastRewind, "后退", onRewind, size = side)
                Spacer(Modifier.width(if (compact) 12.dp else 18.dp))
                PlayerRoundIcon(
                    if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    if (isPlaying) "暂停" else "播放",
                    onTogglePlay,
                    size = play,
                    modifier = Modifier.focusRequester(playFocusRequester),
                )
                Spacer(Modifier.width(if (compact) 12.dp else 18.dp))
                PlayerRoundIcon(Icons.Default.FastForward, "快进", onForward, size = side)
                if (nextEnabled) {
                    Spacer(Modifier.width(if (compact) 8.dp else 14.dp))
                    PlayerRoundIcon(Icons.Default.SkipNext, "下一集", onNext, size = small)
                }
            }
        }
    }
}

@Composable
fun PlayerBottomChrome(
    tick: Long,
    position: Long,
    duration: Long,
    scrubFraction: Float?,
    speed: Float,
    audioLabel: String,
    subtitleLabel: String,
    aspect: AspectMode,
    onScrub: (Float) -> Unit,
    onScrubFinished: () -> Unit,
    onSpeed: () -> Unit,
    onAudio: () -> Unit,
    onSubtitle: () -> Unit,
    onAspect: () -> Unit,
    onRotate: () -> Unit,
    onMore: () -> Unit,
    remoteMode: Boolean,
    modifier: Modifier = Modifier,
) {
    @Suppress("UNUSED_VARIABLE") val refreshTick = tick
    val actual = if (duration > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f
    val shownPosition = if (scrubFraction != null && duration > 0) (scrubFraction * duration).toLong() else position

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal))
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .widthIn(max = if (remoteMode) 1180.dp else 980.dp),
        shape = RoundedCornerShape(18.dp),
        color = Color.Black.copy(alpha = .40f),
        tonalElevation = 0.dp,
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 9.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = if (remoteMode) Arrangement.spacedBy(22.dp, Alignment.CenterHorizontally) else Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PlayerQuickIcon(Icons.Default.Speed, "倍速 ${speed}x", onSpeed)
                PlayerQuickIcon(Icons.Default.GraphicEq, "音轨 $audioLabel", onAudio)
                PlayerQuickIcon(Icons.Default.Subtitles, "字幕 $subtitleLabel", onSubtitle)
                PlayerQuickIcon(Icons.Default.AspectRatio, "画面 ${aspect.label}", onAspect)
                PlayerQuickIcon(Icons.Default.ScreenRotation, "切换横竖屏", onRotate)
                PlayerQuickIcon(Icons.Default.MoreHoriz, "更多", onMore)
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(time(shownPosition), color = Color.White, style = MaterialTheme.typography.labelSmall)
                Spacer(Modifier.weight(1f))
                Text(if (duration > 0) time(duration) else "--:--", color = Color.White.copy(alpha = .7f), style = MaterialTheme.typography.labelSmall)
            }
            ThinSeekBar(
                value = scrubFraction ?: actual,
                enabled = duration > 0,
                onValueChange = onScrub,
                onValueChangeFinished = onScrubFinished,
            )
        }
    }
}

@Composable
private fun ThinSeekBar(
    value: Float,
    enabled: Boolean,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit,
) {
    val current = value.coerceIn(0f, 1f)
    val gesture = if (enabled) {
        Modifier
            .pointerInput(Unit) {
                detectTapGestures(onTap = { offset ->
                    onValueChange((offset.x / size.width).coerceIn(0f, 1f))
                    onValueChangeFinished()
                })
            }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { offset -> onValueChange((offset.x / size.width).coerceIn(0f, 1f)) },
                    onDrag = { change, _ ->
                        change.consume()
                        onValueChange((change.position.x / size.width).coerceIn(0f, 1f))
                    },
                    onDragEnd = onValueChangeFinished,
                    onDragCancel = onValueChangeFinished,
                )
            }
    } else Modifier

    Canvas(Modifier.fillMaxWidth().height(16.dp).then(gesture)) {
        val y = size.height / 2f
        val x = size.width * current
        val trackWidth = 2.dp.toPx()
        drawLine(Color.White.copy(alpha = .26f), Offset(0f, y), Offset(size.width, y), strokeWidth = trackWidth, cap = StrokeCap.Round)
        if (x > 0f) drawLine(Color.White, Offset(0f, y), Offset(x, y), strokeWidth = trackWidth, cap = StrokeCap.Round)
        if (enabled) drawCircle(Color.White, radius = 4.dp.toPx(), center = Offset(x, y))
    }
}

@Composable
private fun PlayerQuickIcon(icon: ImageVector, description: String, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val active = focused || pressed
    Surface(
        onClick = onClick,
        interactionSource = interactionSource,
        modifier = Modifier
            .size(48.dp)
            .onFocusChanged { focused = it.isFocused }
            .scale(if (focused) 1.06f else if (pressed) .94f else 1f),
        shape = CircleShape,
        color = if (active) Color.White.copy(alpha = .18f) else Color.Transparent,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, description, tint = Color.White.copy(alpha = if (active) 1f else .92f), modifier = Modifier.size(24.dp))
        }
    }
}

@Composable
private fun PlayerInfoPill(text: String, icon: ImageVector? = null) {
    Surface(shape = RoundedCornerShape(999.dp), color = Color.Black.copy(alpha = .34f)) {
        Row(Modifier.padding(horizontal = 8.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
            icon?.let { Icon(it, null, tint = Color.White.copy(alpha = .8f), modifier = Modifier.size(14.dp)); Spacer(Modifier.width(4.dp)) }
            Text(text, color = Color.White.copy(alpha = .84f), style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun PlayerRoundIcon(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
    size: Dp = 48.dp,
    modifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val active = focused || pressed
    Surface(
        modifier = modifier
            .size(size)
            .onFocusChanged { focused = it.isFocused }
            .scale(if (focused) 1.06f else if (pressed) .94f else 1f),
        shape = CircleShape,
        color = if (active) Color.White.copy(alpha = .18f) else Color.Transparent,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        interactionSource = interactionSource,
        onClick = onClick,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                icon,
                description,
                tint = Color.White.copy(alpha = if (active) 1f else .94f),
                modifier = Modifier.size(size * .48f),
            )
        }
    }
}

@Composable
private fun GestureEdgeBar(hud: GestureHud, modifier: Modifier = Modifier) {
    val progress = (hud.progress ?: 0f).coerceIn(0f, 1f)
    Surface(
        modifier = modifier.width(46.dp),
        shape = RoundedCornerShape(23.dp),
        color = Color.Black.copy(alpha = .46f),
        tonalElevation = 0.dp,
    ) {
        Column(
            Modifier.padding(horizontal = 10.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(hud.icon, null, tint = Color.White, modifier = Modifier.size(20.dp))
            Box(
                Modifier
                    .width(6.dp)
                    .height(112.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(Color.White.copy(alpha = .22f)),
            ) {
                Box(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .fillMaxHeight(progress)
                        .background(Color.White),
                )
            }
            Text(hud.value, color = Color.White, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun GestureSeekHint(hud: GestureHud, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(999.dp),
        color = Color.Black.copy(alpha = .56f),
        tonalElevation = 0.dp,
    ) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(hud.icon, null, tint = Color.White, modifier = Modifier.size(24.dp))
            Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text(hud.title, color = Color.White, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                Text(hud.value, color = Color.White.copy(alpha = .78f), style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
fun PlaybackEndedCard(
    item: EmbyItem,
    nextItem: EmbyItem?,
    onReplay: () -> Unit,
    onNext: () -> Unit,
    onExit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(modifier = modifier.padding(24.dp), shape = RoundedCornerShape(26.dp), color = Color.Black.copy(alpha = .78f)) {
        Column(
            Modifier.widthIn(max = 440.dp).padding(22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(Icons.Default.CheckCircle, null, tint = Color.White, modifier = Modifier.size(34.dp))
            Text("已播放完《${item.name}》", color = Color.White, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            nextItem?.let {
                Text("下一集 · ${it.name}", color = Color.White.copy(alpha = .72f), maxLines = 2, overflow = TextOverflow.Ellipsis)
                Button(onClick = onNext) {
                    Icon(Icons.Default.SkipNext, null)
                    Spacer(Modifier.width(6.dp))
                    Text("播放下一集")
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onReplay, colors = ButtonDefaults.textButtonColors(contentColor = Color.White)) { Text("重新播放") }
                TextButton(onClick = onExit, colors = ButtonDefaults.textButtonColors(contentColor = Color.White)) { Text("返回详情") }
            }
        }
    }
}

@Composable
private fun PlayerSettingsSheet(
    page: PlayerSettingsPage,
    player: MediaController?,
    descriptor: PlaybackDescriptor,
    config: EmbyServerConfig,
    speed: Float,
    networkSpeed: String,
    remoteMode: Boolean,
    onPage: (PlayerSettingsPage) -> Unit,
    onSpeed: (Float) -> Unit,
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
                                    Text("播放信息", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                                    IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, "关闭") }
                                }
                                Text("查看当前播放状态和媒体信息", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            item {
                                SettingsGroup("当前媒体") {
                                    val item = descriptor.item
                                    val video = item.mediaStreams.firstOrNull { it.type.equals("Video", true) }
                                    val audio = item.mediaStreams.firstOrNull { it.type.equals("Audio", true) }
                                    InfoLine("播放方式", if (descriptor.playMethod.equals("Transcode", true)) "服务器转码" else "直接播放")
                                    InfoLine("实时网络", networkSpeed)
                                    InfoLine("视频", listOfNotNull(video?.codec?.uppercase(), if (item.width != null && item.height != null) "${item.width}×${item.height}" else null).joinToString(" · ").ifBlank { "未知" })
                                    InfoLine("音频", audio?.displayTitle ?: audio?.codec?.uppercase() ?: "未知")
                                    InfoLine("解码", if (!config.hardwareDecoding) "软件解码" else if (config.hardwareCompatibilityMode) "硬件解码（兼容模式）" else "硬件解码")
                                    InfoLine("磁盘缓存", if (config.cacheMb > 0) "${config.cacheMb} MB" else "关闭")
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
                    PlayerSettingsPage.AUDIO -> TrackPickerPage("音轨", player, C.TRACK_TYPE_AUDIO, false, remoteMode, { onPage(PlayerSettingsPage.MAIN) }, onDismiss)
                    PlayerSettingsPage.SUBTITLE -> TrackPickerPage("字幕", player, C.TRACK_TYPE_TEXT, true, remoteMode, { onPage(PlayerSettingsPage.MAIN) }, onDismiss)
                }
            }
        }
    }
}

@Composable
fun SpeedPickerPage(
    speed: Float,
    remoteMode: Boolean,
    onSpeed: (Float) -> Unit,
    onBack: () -> Unit,
    onDone: () -> Unit,
) {
    val speeds = listOf(.5f, .75f, 1f, 1.25f, 1.5f, 1.75f, 2f, 2.25f, 2.5f, 2.75f, 3f)
    Column(Modifier.fillMaxSize().padding(22.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "返回") }
            Text("播放速度", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            IconButton(onClick = onDone) { Icon(Icons.Default.Close, "关闭") }
        }
        Spacer(Modifier.height(14.dp))
        LazyVerticalGrid(
            columns = GridCells.Adaptive(if (remoteMode) 150.dp else 110.dp),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(speeds) { value ->
                PlayerChoiceTile(
                    selected = speed == value,
                    title = "${value}x",
                    onClick = { onSpeed(value) },
                )
            }
        }
    }
}

@Composable
fun PlayerChoiceTile(
    selected: Boolean,
    title: String,
    subtitle: String? = null,
    leading: ImageVector? = null,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = when {
            focused -> MaterialTheme.colorScheme.primaryContainer
            selected -> MaterialTheme.colorScheme.secondaryContainer
            else -> MaterialTheme.colorScheme.surfaceContainer
        },
        modifier = modifier
            .onFocusChanged { focused = it.isFocused }
            .scale(if (focused) 1.035f else 1f)
            .border(if (focused) 3.dp else 0.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(16.dp)),
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 15.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(leading ?: if (selected) Icons.Default.RadioButtonChecked else Icons.Default.RadioButtonUnchecked, null)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (!subtitle.isNullOrBlank()) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
fun SettingsGroup(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
            Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
        }
    }
}

@Composable
fun InfoLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Text(label, modifier = Modifier.width(84.dp), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        Text(value, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun TrackPickerPage(
    title: String,
    player: MediaController?,
    type: Int,
    allowDisable: Boolean,
    remoteMode: Boolean,
    onBack: () -> Unit,
    onDone: () -> Unit,
) {
    val groups = player?.currentTracks?.groups?.filter { it.type == type }.orEmpty()
    Column(Modifier.fillMaxSize().padding(22.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "返回") }
            Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
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
                        selected = false,
                        title = "关闭字幕",
                        leading = Icons.Default.SubtitlesOff,
                        onClick = {
                            if (player != null) player.trackSelectionParameters = player.trackSelectionParameters.buildUpon().setTrackTypeDisabled(type, true).build()
                            onDone()
                        },
                    )
                }
            }
            if (groups.isEmpty()) {
                item("empty") { Text("当前媒体没有可选择的${title}", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(16.dp)) }
            } else {
                groups.forEachIndexed { groupIndex, group ->
                    itemsIndexed((0 until group.length).toList(), key = { _, index -> "$groupIndex:$index" }) { _, index ->
                        val format = group.getTrackFormat(index)
                        val selected = group.isTrackSelected(index)
                        val label = format.label ?: format.language ?: "轨道 ${index + 1}"
                        val meta = listOfNotNull(format.sampleMimeType?.substringAfter('/'), format.language, format.channelCount.takeIf { it > 0 }?.let { "${it}ch" }).distinct().joinToString(" · ")
                        PlayerChoiceTile(
                            selected = selected,
                            title = label,
                            subtitle = meta,
                            onClick = {
                                if (player != null) {
                                    val override = TrackSelectionOverride(group.mediaTrackGroup, listOf(index))
                                    player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                                        .setTrackTypeDisabled(type, false)
                                        .clearOverridesOfType(type)
                                        .addOverride(override)
                                        .build()
                                }
                                onDone()
                            },
                        )
                    }
                }
            }
        }
    }
}

private fun selectedTrackLabel(player: MediaController?, type: Int): String? {
    val groups = player?.currentTracks?.groups?.filter { it.type == type }.orEmpty()
    for (group in groups) {
        for (index in 0 until group.length) {
            if (group.isTrackSelected(index)) {
                val format = group.getTrackFormat(index)
                return format.label ?: format.language ?: "轨道 ${index + 1}"
            }
        }
    }
    return null
}

private fun playerSubtitle(item: EmbyItem): String? = when {
    item.type == "Episode" -> listOfNotNull(
        item.seriesName,
        item.parentIndexNumber?.let { "S$it" },
        item.indexNumber?.let { "E$it" },
    ).joinToString(" · ").takeIf { it.isNotBlank() }
    item.type == "Audio" -> listOfNotNull(item.albumArtist ?: item.artists.firstOrNull(), item.album).joinToString(" · ").takeIf { it.isNotBlank() }
    else -> item.productionYear?.toString()
}

@Composable
private fun rememberMediaController(context: Context): MediaController? {
    var controller by remember { mutableStateOf<MediaController?>(null) }
    DisposableEffect(Unit) {
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val future = MediaController.Builder(context, token).buildAsync()
        future.addListener({ controller = runCatching { future.get() }.getOrNull() }, ContextCompat.getMainExecutor(context))
        onDispose { MediaController.releaseFuture(future); controller = null }
    }
    return controller
}

private fun time(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
}
