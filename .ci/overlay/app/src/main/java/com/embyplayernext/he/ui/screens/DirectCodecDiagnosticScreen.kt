package com.embyplayernext.he.ui.screens

import android.app.Activity
import android.content.Context
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.TrafficStats
import android.net.Uri
import android.os.SystemClock
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.embyplayernext.he.data.model.EmbyServerConfig
import com.embyplayernext.he.data.model.PlaybackDescriptor
import com.embyplayernext.he.util.DiagnosticsLogger
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun DirectCodecDiagnosticScreen(
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
    var surfaceReady by remember(descriptor.playSessionId) { mutableStateOf(false) }
    var playing by remember(descriptor.playSessionId) { mutableStateOf(true) }
    var started by remember(descriptor.playSessionId) { mutableStateOf(false) }
    var ended by remember(descriptor.playSessionId) { mutableStateOf(false) }
    var failed by remember(descriptor.playSessionId) { mutableStateOf<String?>(null) }
    var positionMs by remember(descriptor.playSessionId) { mutableLongStateOf(0L) }
    var durationMs by remember(descriptor.playSessionId) {
        mutableLongStateOf(descriptor.serverRunTimeTicks?.div(10_000L) ?: 0L)
    }
    var frameCount by remember(descriptor.playSessionId) { mutableLongStateOf(0L) }
    var scrubFraction by remember { mutableStateOf<Float?>(null) }
    var uiTicker by remember { mutableLongStateOf(0L) }
    var networkSpeed by remember { mutableStateOf("0.00 MB/s") }
    var controlsVisible by remember { mutableStateOf(true) }
    var speed by remember { mutableFloatStateOf(1f) }
    var speedDialog by remember { mutableStateOf(false) }

    val pausedAtomic = remember(descriptor.playSessionId) { AtomicBoolean(false) }
    val stopAtomic = remember(descriptor.playSessionId) { AtomicBoolean(false) }
    val seekTargetUs = remember(descriptor.playSessionId) { AtomicLong(Long.MIN_VALUE) }
    val positionAtomic = remember(descriptor.playSessionId) { AtomicLong(0L) }
    val durationAtomic = remember(descriptor.playSessionId) { AtomicLong(durationMs) }
    val frameAtomic = remember(descriptor.playSessionId) { AtomicLong(0L) }
    val speedMilliAtomic = remember(descriptor.playSessionId) { AtomicLong(1000L) }
    val speedSerialAtomic = remember(descriptor.playSessionId) { AtomicLong(0L) }
    val focusRequester = remember { FocusRequester() }
    val playFocusRequester = remember { FocusRequester() }

    fun playbackUrl(): String {
        if (config.accessToken.isBlank()) return descriptor.streamUrl
        val parsed = Uri.parse(descriptor.streamUrl)
        if (parsed.getQueryParameter("api_key") != null) return descriptor.streamUrl
        return parsed.buildUpon().appendQueryParameter("api_key", config.accessToken).build().toString()
    }

    fun requestSeek(targetMs: Long) {
        val bounded = if (durationMs > 0) targetMs.coerceIn(0L, durationMs) else targetMs.coerceAtLeast(0L)
        seekTargetUs.set(bounded * 1000L)
        positionAtomic.set(bounded)
        positionMs = bounded
        logger.log("DirectCodecSeek", "request item=${descriptor.item.id} targetMs=$bounded")
    }

    fun exit() {
        stopAtomic.set(true)
        val p = positionAtomic.get().coerceAtLeast(0L)
        val d = durationAtomic.get().takeIf { it > 0 }
        scope.launch {
            if (started) onStopped(descriptor, p, d)
            onExit()
        }
    }

    BackHandler(onBack = ::exit)

    DisposableEffect(surface) {
        val callback = object : SurfaceHolder.Callback {
            override fun surfaceCreated(holder: SurfaceHolder) {
                surfaceReady = holder.surface?.isValid == true
                logger.log("DirectCodec", "surfaceCreated item=${descriptor.item.id} valid=$surfaceReady size=${surface.width}x${surface.height}")
            }

            override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
                surfaceReady = holder.surface?.isValid == true
                logger.log("DirectCodec", "surfaceChanged item=${descriptor.item.id} valid=$surfaceReady size=${width}x$height format=$format")
            }

            override fun surfaceDestroyed(holder: SurfaceHolder) {
                surfaceReady = false
                logger.log("DirectCodec", "surfaceDestroyed item=${descriptor.item.id}")
            }
        }
        surface.holder.addCallback(callback)
        if (surface.holder.surface?.isValid == true) surfaceReady = true
        onDispose {
            surface.holder.removeCallback(callback)
            stopAtomic.set(true)
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

    LaunchedEffect(surfaceReady, descriptor.playSessionId) {
        if (!surfaceReady) return@LaunchedEffect
        stopAtomic.set(false)
        failed = null
        ended = false
        val outputSurface = surface.holder.surface
        try {
            withContext(Dispatchers.IO) {
                var extractor: MediaExtractor? = null
                var codec: MediaCodec? = null
                try {
                    val ex = MediaExtractor()
                    extractor = ex
                    ex.setDataSource(playbackUrl(), emptyMap<String, String>())

                    var videoTrack = -1
                    var sourceFormat: MediaFormat? = null
                    for (i in 0 until ex.trackCount) {
                        val fmt = ex.getTrackFormat(i)
                        val mime = fmt.getString(MediaFormat.KEY_MIME).orEmpty()
                        if (mime.startsWith("video/")) {
                            videoTrack = i
                            sourceFormat = fmt
                            break
                        }
                    }
                    if (videoTrack < 0 || sourceFormat == null) error("No video track")

                    ex.selectTrack(videoTrack)
                    val src = sourceFormat!!
                    val mime = src.getString(MediaFormat.KEY_MIME) ?: error("Missing video mime")
                    val width = src.getInteger(MediaFormat.KEY_WIDTH)
                    val height = src.getInteger(MediaFormat.KEY_HEIGHT)
                    val minimal = MediaFormat.createVideoFormat(mime, width, height)

                    fun copyBuffer(key: String) {
                        if (!src.containsKey(key)) return
                        src.getByteBuffer(key)?.let { original ->
                            val copy = ByteBuffer.allocate(original.remaining())
                            val dup = original.duplicate()
                            dup.position(original.position())
                            copy.put(dup)
                            copy.flip()
                            minimal.setByteBuffer(key, copy)
                        }
                    }

                    fun copyInt(key: String) {
                        if (!src.containsKey(key)) return
                        runCatching { minimal.setInteger(key, src.getInteger(key)) }
                    }

                    copyBuffer("csd-0")
                    copyBuffer("csd-1")
                    copyInt(MediaFormat.KEY_ROTATION)
                    copyInt(MediaFormat.KEY_COLOR_STANDARD)
                    copyInt(MediaFormat.KEY_COLOR_RANGE)
                    copyInt(MediaFormat.KEY_COLOR_TRANSFER)
                    copyBuffer("hdr-static-info")

                    if (src.containsKey(MediaFormat.KEY_DURATION)) {
                        val d = runCatching { src.getLong(MediaFormat.KEY_DURATION) / 1000L }.getOrDefault(0L)
                        if (d > 0) durationAtomic.set(d)
                    }

                    logger.log(
                        "DirectCodecFormat",
                        "item=${descriptor.item.id} track=$videoTrack source=$src minimal=$minimal url=${descriptor.streamUrl.substringBefore('?')}",
                    )

                    val mc = MediaCodec.createDecoderByType(mime)
                    codec = mc
                    mc.configure(minimal, outputSurface, null, 0)
                    mc.start()
                    logger.log("DirectCodec", "codecStarted item=${descriptor.item.id} codec=${mc.name} mime=$mime size=${width}x$height")

                    val initialUs = descriptor.initialPositionMs.coerceAtLeast(0L) * 1000L
                    if (initialUs > 0L) {
                        ex.seekTo(initialUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
                        logger.log("DirectCodecSeek", "initial item=${descriptor.item.id} requestedUs=$initialUs actualUs=${ex.sampleTime}")
                    }

                    val info = MediaCodec.BufferInfo()
                    var inputDone = false
                    var outputDone = false
                    var basePtsUs = Long.MIN_VALUE
                    var baseRealtimeNs = 0L
                    var appliedSpeedSerial = speedSerialAtomic.get()

                    while (currentCoroutineContext().isActive && !stopAtomic.get() && !outputDone) {
                        if (pausedAtomic.get()) {
                            Thread.sleep(10)
                            continue
                        }

                        val requestedSeekUs = seekTargetUs.getAndSet(Long.MIN_VALUE)
                        if (requestedSeekUs != Long.MIN_VALUE) {
                            ex.seekTo(requestedSeekUs.coerceAtLeast(0L), MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
                            mc.flush()
                            inputDone = false
                            outputDone = false
                            basePtsUs = Long.MIN_VALUE
                            baseRealtimeNs = 0L
                            logger.log(
                                "DirectCodecSeek",
                                "flush item=${descriptor.item.id} requestedUs=$requestedSeekUs actualSampleUs=${ex.sampleTime}",
                            )
                        }

                        if (!inputDone) {
                            val inputIndex = mc.dequeueInputBuffer(10_000)
                            if (inputIndex >= 0) {
                                val input = mc.getInputBuffer(inputIndex) ?: error("Null codec input buffer")
                                input.clear()
                                val sampleSize = ex.readSampleData(input, 0)
                                if (sampleSize < 0) {
                                    mc.queueInputBuffer(
                                        inputIndex,
                                        0,
                                        0,
                                        0L,
                                        MediaCodec.BUFFER_FLAG_END_OF_STREAM,
                                    )
                                    inputDone = true
                                    logger.log("DirectCodec", "inputEos item=${descriptor.item.id}")
                                } else {
                                    val ptsUs = ex.sampleTime.coerceAtLeast(0L)
                                    mc.queueInputBuffer(inputIndex, 0, sampleSize, ptsUs, 0)
                                    ex.advance()
                                }
                            }
                        }

                        when (val outputIndex = mc.dequeueOutputBuffer(info, 10_000)) {
                            MediaCodec.INFO_TRY_AGAIN_LATER -> Unit
                            MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                                logger.log("DirectCodec", "outputFormat item=${descriptor.item.id} format=${mc.outputFormat}")
                            }
                            else -> if (outputIndex >= 0) {
                                val ptsUs = info.presentationTimeUs.coerceAtLeast(0L)
                                val nowNs = System.nanoTime()
                                val speedSerial = speedSerialAtomic.get()
                                if (basePtsUs == Long.MIN_VALUE || speedSerial != appliedSpeedSerial) {
                                    basePtsUs = ptsUs
                                    baseRealtimeNs = nowNs + 60_000_000L
                                    appliedSpeedSerial = speedSerial
                                    logger.log(
                                        "DirectCodecSpeed",
                                        "apply item=${descriptor.item.id} speed=${speedMilliAtomic.get() / 1000f} ptsUs=$ptsUs serial=$speedSerial",
                                    )
                                }
                                val speedValue = (speedMilliAtomic.get().coerceAtLeast(250L) / 1000.0)
                                val mediaDeltaNs = ((ptsUs - basePtsUs).coerceAtLeast(0L) * 1000.0 / speedValue).toLong()
                                val renderNs = (baseRealtimeNs + mediaDeltaNs).coerceAtLeast(nowNs)
                                mc.releaseOutputBuffer(outputIndex, renderNs)

                                val currentPositionMs = ptsUs / 1000L
                                positionAtomic.set(currentPositionMs)
                                val count = frameAtomic.incrementAndGet()
                                if (count <= 10L || count % 60L == 0L) {
                                    logger.log(
                                        "DirectCodecFrame",
                                        "item=${descriptor.item.id} count=$count ptsUs=$ptsUs renderNs=$renderNs realtimeMs=${SystemClock.elapsedRealtime()}",
                                    )
                                }
                                if (!started) {
                                    withContext(Dispatchers.Main) {
                                        if (!started) {
                                            started = true
                                            onStart(
                                                descriptor,
                                                currentPositionMs,
                                                durationAtomic.get().takeIf { it > 0 },
                                            )
                                        }
                                    }
                                }

                                if ((info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                                    outputDone = true
                                    logger.log("DirectCodec", "outputEos item=${descriptor.item.id} frames=$count positionMs=$currentPositionMs")
                                }
                            }
                        }
                    }
                } finally {
                    runCatching { codec?.stop() }
                    runCatching { codec?.release() }
                    runCatching { extractor?.release() }
                    logger.log(
                        "DirectCodec",
                        "released item=${descriptor.item.id} frames=${frameAtomic.get()} positionMs=${positionAtomic.get()} stopped=${stopAtomic.get()}",
                    )
                }
            }
            if (!stopAtomic.get()) ended = true
        } catch (cancel: CancellationException) {
            logger.log(
                "DirectCodec",
                "coroutineCancelled item=${descriptor.item.id} type=${cancel.javaClass.simpleName} message=${cancel.message}",
            )
            throw cancel
        } catch (t: Throwable) {
            failed = "${t.javaClass.simpleName}: ${t.message ?: "unknown"}"
            logger.log(
                "DirectCodecError",
                "item=${descriptor.item.id} type=${t.javaClass.name} message=${t.message} cause=${t.cause?.javaClass?.name}:${t.cause?.message}",
            )
        }
    }

    LaunchedEffect(descriptor.playSessionId) {
        while (isActive) {
            delay(250)
            positionMs = positionAtomic.get().coerceAtLeast(0L)
            durationAtomic.get().takeIf { it > 0 }?.let { durationMs = it }
            frameCount = frameAtomic.get()
            uiTicker++
        }
    }

    LaunchedEffect(descriptor.playSessionId, started, playing) {
        var elapsed = 0
        while (isActive && started && !ended) {
            delay(1000)
            elapsed++
            if (elapsed % 10 == 0) {
                onProgress(
                    descriptor,
                    positionAtomic.get().coerceAtLeast(0L),
                    durationAtomic.get().takeIf { it > 0 },
                    !playing,
                    "TimeUpdate",
                )
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
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(focusRequester)
            .focusable()
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (event.key) {
                    Key.MediaPlayPause -> {
                        playing = !playing
                        pausedAtomic.set(!playing)
                        controlsVisible = true
                        true
                    }
                    Key.MediaFastForward -> {
                        requestSeek(positionAtomic.get() + config.forwardSeconds * 1000L)
                        controlsVisible = true
                        true
                    }
                    Key.MediaRewind -> {
                        requestSeek(positionAtomic.get() - config.rewindSeconds * 1000L)
                        controlsVisible = true
                        true
                    }
                    Key.DirectionLeft -> {
                        if (!controlsVisible) {
                            requestSeek(positionAtomic.get() - config.rewindSeconds * 1000L)
                            controlsVisible = true
                            true
                        } else false
                    }
                    Key.DirectionRight -> {
                        if (!controlsVisible) {
                            requestSeek(positionAtomic.get() + config.forwardSeconds * 1000L)
                            controlsVisible = true
                            true
                        } else false
                    }
                    Key.DirectionUp, Key.DirectionDown, Key.Enter, Key.DirectionCenter -> {
                        if (!controlsVisible) {
                            controlsVisible = true
                            true
                        } else false
                    }
                    else -> false
                }
            },
    ) {
        AndroidView(
            factory = { surface },
            modifier = Modifier.fillMaxSize(),
        )

        if (controlsVisible) {
            PlayerTopChrome(
                item = descriptor.item,
                networkSpeed = networkSpeed,
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
            onRewind = { requestSeek(positionAtomic.get() - config.rewindSeconds * 1000L) },
            onTogglePlay = {
                playing = !playing
                pausedAtomic.set(!playing)
            },
            onForward = { requestSeek(positionAtomic.get() + config.forwardSeconds * 1000L) },
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
            audioLabel = "无音频·诊断",
            subtitleLabel = "关闭",
            aspect = AspectMode.FIT,
            onScrub = { scrubFraction = it },
            onScrubFinished = {
                val value = scrubFraction
                if (durationMs > 0 && value != null) requestSeek((value * durationMs).toLong())
                scrubFraction = null
            },
            onSpeed = {
                speedDialog = true
                controlsVisible = true
            },
            onAudio = {},
            onSubtitle = {},
            onAspect = {},
            onRotate = {
                val host = activity ?: return@PlayerBottomChrome
                host.requestedOrientation =
                    if (isLandscape) ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                    else ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            },
            onMore = {},
            remoteMode = isTv && isLandscape,
            modifier = Modifier.align(Alignment.BottomCenter),
            )
        }

        if (!surfaceReady || (!started && failed == null)) {
            CircularProgressIndicator(Modifier.align(Alignment.Center))
        }

        failed?.let {
            Text(
                text = "DirectCodec 错误：$it",
                color = Color.White,
                modifier = Modifier.align(Alignment.Center).padding(24.dp),
            )
        }
    }

    if (speedDialog) {
        Dialog(
            onDismissRequest = { speedDialog = false },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth(if (isTv || isLandscape) .78f else .96f)
                    .fillMaxHeight(if (isTv || isLandscape) .78f else .86f),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(28.dp),
            ) {
                SpeedPickerPage(
                    speed = speed,
                    remoteMode = isTv || isLandscape,
                    onSpeed = { value ->
                        speed = value
                        speedMilliAtomic.set((value * 1000f).toLong())
                        val serial = speedSerialAtomic.incrementAndGet()
                        logger.log("DirectCodecSpeed", "request item=${descriptor.item.id} speed=$value serial=$serial")
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
