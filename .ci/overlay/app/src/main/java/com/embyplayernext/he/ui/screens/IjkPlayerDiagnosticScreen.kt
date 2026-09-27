package com.embyplayernext.he.ui.screens

import android.app.Activity
import android.content.Context
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.net.Uri
import android.os.SystemClock
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.embyplayernext.he.data.model.EmbyServerConfig
import com.embyplayernext.he.data.model.PlaybackDescriptor
import com.embyplayernext.he.util.DiagnosticsLogger
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import tv.danmaku.ijk.media.player.IjkMediaPlayer
import tv.danmaku.ijk.media.player.IMediaPlayer

@Composable
fun IjkPlayerDiagnosticScreen(
    descriptor: PlaybackDescriptor,
    config: EmbyServerConfig,
    onStart: suspend (PlaybackDescriptor, Long, Long?) -> Unit,
    onProgress: suspend (PlaybackDescriptor, Long, Long?, Boolean, String) -> Unit,
    onStopped: suspend (PlaybackDescriptor, Long, Long?) -> Unit,
    onExit: () -> Unit,
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val activity = context as? Activity
    val scope = rememberCoroutineScope()
    val logger = remember { DiagnosticsLogger(context) }
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val isTv = (configuration.uiMode and Configuration.UI_MODE_TYPE_MASK) == Configuration.UI_MODE_TYPE_TELEVISION ||
        context.packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)
    val surface = remember { SurfaceView(context).apply { isFocusable = false; isFocusableInTouchMode = false } }
    val player = remember(descriptor.playSessionId) {
        IjkMediaPlayer().apply {
            setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "mediacodec", 1L)
            setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "mediacodec-hevc", 1L)
            setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "mediacodec-auto-rotate", 1L)
            setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "mediacodec-handle-resolution-change", 1L)
            setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "framedrop", 1L)
            setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "start-on-prepared", 1L)
        }
    }
    var surfaceReady by remember { mutableStateOf(false) }
    var started by remember { mutableStateOf(false) }
    var prepared by remember { mutableStateOf(false) }
    var playing by remember { mutableStateOf(false) }
    var ended by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf<String?>(null) }
    var positionMs by remember { mutableLongStateOf(descriptor.initialPositionMs.coerceAtLeast(0L)) }
    var durationMs by remember { mutableLongStateOf(descriptor.serverRunTimeTicks?.div(10_000L) ?: 0L) }
    var controlsVisible by remember { mutableStateOf(true) }
    var scrubFraction by remember { mutableStateOf<Float?>(null) }
    var speed by remember { mutableFloatStateOf(1f) }
    var speedDialog by remember { mutableStateOf(false) }
    var uiTicker by remember { mutableLongStateOf(0L) }
    var buffering by remember { mutableStateOf(false) }
    var bufferingPercent by remember { mutableIntStateOf(0) }
    var actionGeneration by remember { mutableLongStateOf(0L) }
    var lastAction by remember { mutableStateOf("startup") }
    var lastActionStartMs by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    val focusRequester = remember { FocusRequester() }
    val playFocusRequester = remember { FocusRequester() }

    fun playbackUrl(): String {
        if (config.accessToken.isBlank()) return descriptor.streamUrl
        val parsed = Uri.parse(descriptor.streamUrl)
        if (parsed.getQueryParameter("api_key") != null) return descriptor.streamUrl
        return parsed.buildUpon().appendQueryParameter("api_key", config.accessToken).build().toString()
    }

    fun decoderLabel(value: Int): String = when (value) {
        IjkMediaPlayer.FFP_PROPV_DECODER_MEDIACODEC -> "MediaCodec"
        IjkMediaPlayer.FFP_PROPV_DECODER_AVCODEC -> "AVCodec"
        IjkMediaPlayer.FFP_PROPV_DECODER_VIDEOTOOLBOX -> "VideoToolbox"
        else -> "Unknown($value)"
    }

    fun logDecoder(reason: String) {
        runCatching {
            val type = player.videoDecoder
            val info = player.mediaInfo
            logger.log(
                "IjkDecoder",
                "item=${descriptor.item.id} reason=$reason type=${decoderLabel(type)} raw=$type video=${info?.mVideoDecoder} impl=${info?.mVideoDecoderImpl} audio=${info?.mAudioDecoder} audioImpl=${info?.mAudioDecoderImpl}",
            )
        }.onFailure {
            logger.log("IjkDecoder", "item=${descriptor.item.id} reason=$reason error=${it.javaClass.simpleName}:${it.message}")
        }
    }

    fun markAction(action: String) {
        actionGeneration += 1L
        lastAction = action
        lastActionStartMs = SystemClock.elapsedRealtime()
        logger.log(
            "IjkAction",
            "item=${descriptor.item.id} generation=$actionGeneration action=$action positionMs=$positionMs speed=$speed",
        )
    }

    fun seekTo(targetMs: Long) {
        val bounded = if (durationMs > 0) targetMs.coerceIn(0L, durationMs) else targetMs.coerceAtLeast(0L)
        markAction("seek:$bounded")
        runCatching { player.seekTo(bounded) }
        positionMs = bounded
        logger.log("IjkSeek", "item=" + descriptor.item.id + " generation=" + actionGeneration + " targetMs=" + bounded)
    }

    fun togglePlay() {
        if (playing) player.pause() else player.start()
        playing = !playing
        controlsVisible = true
    }

    fun exit() {
        val p = runCatching { player.currentPosition.coerceAtLeast(0L) }.getOrDefault(positionMs)
        val d = runCatching { player.duration }.getOrDefault(-1L).takeIf { it > 0 }
        scope.launch {
            if (started) onStopped(descriptor, p, d)
            runCatching { player.stop() }
            runCatching { player.release() }
            onExit()
        }
    }

    BackHandler(onBack = ::exit)

    DisposableEffect(surface) {
        val callback = object : SurfaceHolder.Callback {
            override fun surfaceCreated(holder: SurfaceHolder) {
                surfaceReady = holder.surface?.isValid == true
                if (surfaceReady) player.setDisplay(holder)
            }
            override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
                surfaceReady = holder.surface?.isValid == true
                if (surfaceReady) player.setDisplay(holder)
            }
            override fun surfaceDestroyed(holder: SurfaceHolder) {
                surfaceReady = false
                player.setDisplay(null)
            }
        }
        surface.holder.addCallback(callback)
        onDispose { surface.holder.removeCallback(callback) }
    }

    DisposableEffect(player, descriptor.playSessionId) {
        player.setOnPreparedListener {
            prepared = true
            val d = runCatching { player.duration }.getOrDefault(-1L)
            if (d > 0) durationMs = d
            if (descriptor.initialPositionMs > 0) runCatching { player.seekTo(descriptor.initialPositionMs) }
            runCatching { player.start() }
            playing = true
            logger.log("IjkPlayer", "prepared item=" + descriptor.item.id)
            logDecoder("prepared")
        }
        player.setOnBufferingUpdateListener { _, percent ->
            bufferingPercent = percent
            if (percent == 0 || percent == 100 || percent % 10 == 0) {
                logger.log(
                    "IjkBuffer",
                    "item=${descriptor.item.id} update percent=$percent positionMs=$positionMs buffering=$buffering",
                )
            }
        }
        player.setOnInfoListener { _, what, extra ->
            when (what) {
                IMediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START -> {
                    if (!started) {
                        started = true
                        val p = runCatching { player.currentPosition.coerceAtLeast(0L) }.getOrDefault(0L)
                        scope.launch { onStart(descriptor, p, durationMs.takeIf { it > 0 }) }
                    }
                    logger.log(
                        "IjkPlayer",
                        "firstFrame item=${descriptor.item.id} generation=$actionGeneration action=$lastAction extra=$extra elapsedActionMs=${SystemClock.elapsedRealtime() - lastActionStartMs}",
                    )
                    logDecoder("video-rendering-start")
                }
                IMediaPlayer.MEDIA_INFO_BUFFERING_START -> {
                    buffering = true
                    logger.log(
                        "IjkBuffer",
                        "item=${descriptor.item.id} event=start generation=$actionGeneration action=$lastAction positionMs=$positionMs percent=$bufferingPercent",
                    )
                }
                IMediaPlayer.MEDIA_INFO_BUFFERING_END -> {
                    buffering = false
                    logger.log(
                        "IjkBuffer",
                        "item=${descriptor.item.id} event=end generation=$actionGeneration action=$lastAction positionMs=$positionMs percent=$bufferingPercent elapsedActionMs=${SystemClock.elapsedRealtime() - lastActionStartMs}",
                    )
                }
                IMediaPlayer.MEDIA_INFO_VIDEO_DECODED_START,
                IMediaPlayer.MEDIA_INFO_AUDIO_DECODED_START,
                IMediaPlayer.MEDIA_INFO_AUDIO_RENDERING_START,
                IMediaPlayer.MEDIA_INFO_VIDEO_SEEK_RENDERING_START,
                IMediaPlayer.MEDIA_INFO_AUDIO_SEEK_RENDERING_START -> {
                    logger.log(
                        "IjkInfo",
                        "item=${descriptor.item.id} what=$what extra=$extra generation=$actionGeneration action=$lastAction positionMs=$positionMs elapsedActionMs=${SystemClock.elapsedRealtime() - lastActionStartMs}",
                    )
                    if (what == IMediaPlayer.MEDIA_INFO_VIDEO_SEEK_RENDERING_START) {
                        logDecoder("video-seek-rendering-start")
                    }
                }
                else -> logger.log(
                    "IjkInfo",
                    "item=${descriptor.item.id} what=$what extra=$extra generation=$actionGeneration action=$lastAction positionMs=$positionMs",
                )
            }
            false
        }
        player.setOnCompletionListener {
            ended = true
            playing = false
        }
        player.setOnErrorListener { _, what, extra ->
            failed = "Ijk error what=" + what + " extra=" + extra
            playing = false
            logger.log("IjkPlayerError", "item=" + descriptor.item.id + " what=" + what + " extra=" + extra)
            true
        }
        runCatching {
            if (surface.holder.surface?.isValid == true) player.setDisplay(surface.holder)
            player.setDataSource(context, Uri.parse(playbackUrl()))
            player.prepareAsync()
        }.onFailure {
            failed = it.javaClass.simpleName + ": " + (it.message ?: "unknown")
        }
        onDispose {
            runCatching { player.stop() }
            runCatching { player.release() }
        }
    }

    LaunchedEffect(descriptor.playSessionId) {
        while (isActive) {
            delay(250)
            if (prepared) {
                positionMs = runCatching { player.currentPosition.coerceAtLeast(0L) }.getOrDefault(positionMs)
                val d = runCatching { player.duration }.getOrDefault(-1L)
                if (d > 0) durationMs = d
            }
            uiTicker++
        }
    }

    LaunchedEffect(started, playing) {
        var seconds = 0
        var lowOutputSeconds = 0
        while (isActive && started && !ended) {
            delay(1000)
            seconds++

            val decodeFps = runCatching { player.videoDecodeFramesPerSecond }.getOrDefault(-1f)
            val outputFps = runCatching { player.videoOutputFramesPerSecond }.getOrDefault(-1f)
            val dropRate = runCatching { player.dropFrameRate }.getOrDefault(-1f)
            val videoCacheMs = runCatching { player.videoCachedDuration }.getOrDefault(-1L)
            val audioCacheMs = runCatching { player.audioCachedDuration }.getOrDefault(-1L)
            val videoCacheBytes = runCatching { player.videoCachedBytes }.getOrDefault(-1L)
            val audioCacheBytes = runCatching { player.audioCachedBytes }.getOrDefault(-1L)
            val videoPackets = runCatching { player.videoCachedPackets }.getOrDefault(-1L)
            val audioPackets = runCatching { player.audioCachedPackets }.getOrDefault(-1L)
            val tcpSpeed = runCatching { player.tcpSpeed }.getOrDefault(-1L)
            val seekLoadMs = runCatching { player.seekLoadDuration }.getOrDefault(-1L)
            val decoder = runCatching { player.videoDecoder }.getOrDefault(-1)

            logger.log(
                "IjkStats",
                "item=${descriptor.item.id} posMs=$positionMs playing=$playing buffering=$buffering percent=$bufferingPercent speed=$speed decoder=${decoderLabel(decoder)} decodeFps=$decodeFps outputFps=$outputFps dropRate=$dropRate videoCacheMs=$videoCacheMs audioCacheMs=$audioCacheMs videoCacheBytes=$videoCacheBytes audioCacheBytes=$audioCacheBytes videoPackets=$videoPackets audioPackets=$audioPackets tcpBps=$tcpSpeed seekLoadMs=$seekLoadMs actionGen=$actionGeneration action=$lastAction actionAgeMs=${SystemClock.elapsedRealtime() - lastActionStartMs}",
            )

            if (playing && !buffering && outputFps in 0f..0.5f) {
                lowOutputSeconds++
            } else {
                lowOutputSeconds = 0
            }
            if (lowOutputSeconds == 2) {
                logger.log(
                    "IjkVideoStall",
                    "item=${descriptor.item.id} posMs=$positionMs speed=$speed decoder=${decoderLabel(decoder)} decodeFps=$decodeFps outputFps=$outputFps buffering=$buffering videoCacheMs=$videoCacheMs actionGen=$actionGeneration action=$lastAction actionAgeMs=${SystemClock.elapsedRealtime() - lastActionStartMs}",
                )
            }

            if (seconds % 10 == 0) {
                onProgress(descriptor, positionMs, durationMs.takeIf { it > 0 }, !playing, "TimeUpdate")
            }
        }
    }

    LaunchedEffect(Unit) {
        delay(200)
        runCatching { focusRequester.requestFocus() }
    }

    LaunchedEffect(controlsVisible, isTv, playing, speedDialog) {
        if (controlsVisible && isTv && !speedDialog) {
            delay(80)
            runCatching { playFocusRequester.requestFocus() }
        }
        if (controlsVisible && isTv && playing && !speedDialog) {
            delay(7000)
            controlsVisible = false
            runCatching { focusRequester.requestFocus() }
        }
    }

    Box(
        Modifier.fillMaxSize()
            .background(Color.Black)
            .focusRequester(focusRequester)
            .focusable()
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (event.key) {
                    Key.MediaPlayPause -> { togglePlay(); true }
                    Key.MediaFastForward -> { seekTo(positionMs + config.forwardSeconds * 1000L); controlsVisible = true; true }
                    Key.MediaRewind -> { seekTo(positionMs - config.rewindSeconds * 1000L); controlsVisible = true; true }
                    Key.DirectionLeft -> if (!controlsVisible) { seekTo(positionMs - config.rewindSeconds * 1000L); controlsVisible = true; true } else false
                    Key.DirectionRight -> if (!controlsVisible) { seekTo(positionMs + config.forwardSeconds * 1000L); controlsVisible = true; true } else false
                    Key.DirectionUp, Key.DirectionDown, Key.Enter, Key.DirectionCenter -> if (!controlsVisible) { controlsVisible = true; true } else false
                    else -> false
                }
            },
    ) {
        AndroidView(factory = { surface }, modifier = Modifier.fillMaxSize())
        if (controlsVisible) {
            PlayerTopChrome(
                item = descriptor.item,
                networkSpeed = "Ijk/MediaCodec",
                locked = false,
                onBack = ::exit,
                modifier = Modifier.align(Alignment.TopCenter),
            )
            PlayerCenterControls(
                compact = !isLandscape,
                locked = false,
                isPlaying = playing,
                previousEnabled = false,
                nextEnabled = false,
                onToggleLock = {},
                onPrevious = {},
                onRewind = { seekTo(positionMs - config.rewindSeconds * 1000L) },
                onTogglePlay = ::togglePlay,
                onForward = { seekTo(positionMs + config.forwardSeconds * 1000L) },
                onNext = {},
                playFocusRequester = playFocusRequester,
                remoteMode = isTv && isLandscape,
                modifier = Modifier.align(Alignment.Center),
            )
            PlayerBottomChrome(
                tick = uiTicker,
                position = positionMs,
                duration = durationMs,
                scrubFraction = scrubFraction,
                speed = speed,
                audioLabel = "Ijk 音频",
                subtitleLabel = "关闭",
                aspect = AspectMode.FIT,
                onScrub = { scrubFraction = it },
                onScrubFinished = {
                    val value = scrubFraction
                    if (durationMs > 0 && value != null) seekTo((value * durationMs).toLong())
                    scrubFraction = null
                },
                onSpeed = { speedDialog = true; controlsVisible = true },
                onAudio = {},
                onSubtitle = {},
                onAspect = {},
                onRotate = {
                    val host = activity ?: return@PlayerBottomChrome
                    host.requestedOrientation = if (isLandscape) ActivityInfo.SCREEN_ORIENTATION_PORTRAIT else ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                },
                onMore = {},
                remoteMode = isTv && isLandscape,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
        if (!surfaceReady || (!started && failed == null)) CircularProgressIndicator(Modifier.align(Alignment.Center))
        failed?.let { Text(it, color = Color.White, modifier = Modifier.align(Alignment.Center).padding(24.dp)) }
    }

    if (speedDialog) {
        Dialog(onDismissRequest = { speedDialog = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Surface(
                modifier = Modifier.fillMaxWidth(if (isTv || isLandscape) .78f else .96f)
                    .fillMaxHeight(if (isTv || isLandscape) .78f else .86f),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(28.dp),
            ) {
                SpeedPickerPage(
                    speed = speed,
                    remoteMode = isTv || isLandscape,
                    onSpeed = { value ->
                        speed = value
                        markAction("speed:$value")
                        runCatching { player.setSpeed(value) }
                        logger.log("IjkSpeed", "item=" + descriptor.item.id + " generation=" + actionGeneration + " speed=" + value)
                        speedDialog = false
                        controlsVisible = true
                    },
                    onBack = { speedDialog = false },
                    onDone = { speedDialog = false },
                )
            }
        }
    }
}
