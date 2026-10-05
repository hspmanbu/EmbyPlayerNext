@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package com.embyplayernext.he.playback

import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import android.app.ActivityManager
import android.media.MediaCodecList
import android.media.MediaFormat
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.SeekParameters
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.exoplayer.video.VideoFrameMetadataListener
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.embyplayernext.he.data.network.NetworkSupport
import com.embyplayernext.he.data.prefs.AppPreferences
import com.embyplayernext.he.util.DiagnosticsLogger

class PlaybackService : MediaSessionService() {
    private var mediaSession: MediaSession? = null
    override fun onCreate() {
        super.onCreate()
        val prefs = AppPreferences(this)
        val config = prefs.config.value
        val logger = DiagnosticsLogger(this)
        val rockchipFingerprint = listOf(
            Build.MANUFACTURER, Build.BRAND, Build.HARDWARE, Build.BOARD, Build.DEVICE, Build.PRODUCT,
            if (Build.VERSION.SDK_INT >= 31) Build.SOC_MODEL else "",
        ).joinToString(" ").lowercase()
        val isRockchip = rockchipFingerprint.contains("rockchip") ||
            rockchipFingerprint.contains("rk3566") || rockchipFingerprint.contains("rk356") ||
            Build.HARDWARE.lowercase().startsWith("rk")
        logger.log("Device", "manufacturer=${Build.MANUFACTURER} brand=${Build.BRAND} model=${Build.MODEL} device=${Build.DEVICE} product=${Build.PRODUCT} hardware=${Build.HARDWARE} board=${Build.BOARD} sdk=${Build.VERSION.SDK_INT} soc=${if (Build.VERSION.SDK_INT >= 31) Build.SOC_MODEL else "n/a"} rockchip=$isRockchip hwCompat=${config.hardwareCompatibilityMode}")
        logVideoCodecCapabilities(logger)
        val selector = MediaCodecSelector { mimeType, secure, tunneling ->
            val all = MediaCodecSelector.DEFAULT.getDecoderInfos(mimeType, secure, tunneling)
            val candidates = if (config.hardwareDecoding) all else {
                all.filter { info ->
                    val n = info.name.lowercase()
                    n.contains("google") || n.contains("android") || n.contains("software") || n.contains("sw") || n.contains("ffmpeg")
                }.ifEmpty { all }
            }
            val ordered = when {
                config.hardwareDecoding && config.hardwareCompatibilityMode && isRockchip && mimeType.equals("video/hevc", true) -> {
                    // RK3566/Android 11 devices can expose both Codec2 and legacy OMX HEVC decoders.
                    // Some firmware renders a black surface through c2.rk.hevc.decoder while the vendor OMX/MPP path works.
                    candidates.sortedBy { info ->
                        val n = info.name.lowercase()
                        when {
                            n == "omx.rk.video_decoder.hevc" -> 0
                            n.startsWith("omx.rk") && n.contains("hevc") -> 1
                            config.hardwareCompatibilityMode && n.startsWith("c2.rk") && n.contains("hevc") -> 2
                            n.startsWith("c2.rk") && n.contains("hevc") -> 4
                            n.contains("google") || n.contains("android") || n.contains("software") -> 9
                            else -> 3
                        }
                    }
                }
                else -> candidates
            }
            logger.log("Codec", "mime=$mimeType rockchip=$isRockchip compat=${config.hardwareCompatibilityMode} secure=$secure tunneling=$tunneling decoders=${ordered.joinToString { it.name }}")
            ordered
        }
        val hasVendorOmxHevc = runCatching {
            selector.getDecoderInfos(MimeTypes.VIDEO_H265, false, false).any { info ->
                val name = info.name.lowercase()
                name.startsWith("omx.") && !name.contains("google")
            }
        }.getOrDefault(false)
        val hybridDirectVideoEnabled =
            config.hardwareDecoding &&
                config.hardwareCompatibilityMode &&
                isRockchip &&
                hasVendorOmxHevc
        logger.log(
            "RKHybrid",
            "requested=$hybridDirectVideoEnabled rockchip=$isRockchip compat=${config.hardwareCompatibilityMode} hw=${config.hardwareDecoding} vendorOmxHevc=$hasVendorOmxHevc route=auto-omx",
        )
        val renderers = RockchipHybridRenderersFactory(
            context = this,
            isRockchip = isRockchip,
            compatibilityEnabled = config.hardwareCompatibilityMode,
            enableHybridDirectVideo = hybridDirectVideoEnabled,
            logger = logger,
        )
            .setMediaCodecSelector(selector)
            .setEnableDecoderFallback(true)
            .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)
            .apply {
                if (hybridDirectVideoEnabled) {
                    // Keep stock Media3 behavior unless the OMX compatibility architecture is active.
                    forceDisableMediaCodecAsynchronousQueueing()
                    setAllowedVideoJoiningTimeMs(7_500)
                }
            }

        val lowRam = (getSystemService(ACTIVITY_SERVICE) as? ActivityManager)?.isLowRamDevice == true
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                if (lowRam) 8_000 else 15_000,
                if (lowRam) 30_000 else 60_000,
                1_000,
                2_500,
            )
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()
        val upstream = OkHttpDataSource.Factory(NetworkSupport.mediaClient(config, if (config.dynamicPortEnabled) config.dynamicPortTimeoutSeconds else 15))
            .setUserAgent("EmbyPlayerNext/2.3.28")
        val cacheFactory = if (config.cacheMb > 0) {
            CacheDataSource.Factory()
                .setCache(PlayerCache.get(this, config.cacheMb))
                .setUpstreamDataSourceFactory(upstream)
                .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
        } else null
        val mediaSourceFactory = DefaultMediaSourceFactory(cacheFactory ?: upstream)
        val trackSelector = DefaultTrackSelector(this).apply {
            if (hybridDirectVideoEnabled) {
                parameters = buildUponParameters()
                    .setExceedRendererCapabilitiesIfNecessary(true)
                    .setExceedVideoConstraintsIfNecessary(true)
                    .setTunnelingEnabled(false)
                    .build()
            }
        }
        logger.log(
            "Codec",
            if (hybridDirectVideoEnabled)
                "trackSelector hybridCompat=true exceedRendererCapabilities=true exceedVideoConstraints=true tunneling=false"
            else
                "trackSelector hybridCompat=false media3-defaults=true",
        )
        var videoFrameCount = 0L
        var lastVideoFrameRealtimeMs = 0L
        var lastVideoFramePtsUs = C.TIME_UNSET
        var videoFrameMediaId = ""
        val mainHandler = Handler(Looper.getMainLooper())
        logger.log("Codec", "media3Baseline=true videoChangeFrameRateStrategy=library-default")
        val player = ExoPlayer.Builder(this, renderers)
            .setTrackSelector(trackSelector)
            .setMediaSourceFactory(mediaSourceFactory)
            .setLoadControl(loadControl)
            .build().apply {
                setAudioAttributes(AudioAttributes.DEFAULT, true)
                repeatMode = Player.REPEAT_MODE_OFF
                if (hybridDirectVideoEnabled) {
                    setSeekParameters(SeekParameters.PREVIOUS_SYNC)
                    logger.log("RKSeek", "seekParameters=PREVIOUS_SYNC")
                }
                setVideoFrameMetadataListener(VideoFrameMetadataListener { presentationTimeUs, releaseTimeNs, format, mediaFormat ->
                    val now = SystemClock.elapsedRealtime()
                    val previousPts = lastVideoFramePtsUs
                    val previousRealtime = lastVideoFrameRealtimeMs
                    videoFrameCount += 1
                    lastVideoFrameRealtimeMs = now
                    lastVideoFramePtsUs = presentationTimeUs
                    val shouldPublish = videoFrameCount <= 5L || videoFrameCount % 30L == 0L
                    if (shouldPublish) {
                        logger.log(
                            "VideoFrame",
                            "mediaId=$videoFrameMediaId count=$videoFrameCount ptsUs=$presentationTimeUs deltaPtsUs=${if (previousPts == C.TIME_UNSET) -1 else presentationTimeUs - previousPts} releaseNs=$releaseTimeNs deltaRealtimeMs=${if (previousRealtime <= 0) -1 else now - previousRealtime} mime=${format.sampleMimeType} size=${format.width}x${format.height} color=${mediaFormat?.getInteger(MediaFormat.KEY_COLOR_FORMAT)}"
                        )
                        val extras = Bundle().apply {
                            putString("vf_media_id", videoFrameMediaId)
                            putLong("vf_count", videoFrameCount)
                            putLong("vf_last_realtime_ms", lastVideoFrameRealtimeMs)
                            putLong("vf_pts_us", lastVideoFramePtsUs)
                        }
                        mainHandler.post { mediaSession?.setSessionExtras(extras) }
                    }
                })

                addListener(object : Player.Listener {
                    override fun onMediaItemTransition(mediaItem: androidx.media3.common.MediaItem?, reason: Int) {
                        videoFrameMediaId = mediaItem?.mediaId.orEmpty()
                        videoFrameCount = 0L
                        lastVideoFrameRealtimeMs = 0L
                        lastVideoFramePtsUs = C.TIME_UNSET
                        logger.log("VideoFrame", "reset mediaId=${mediaItem?.mediaId} reason=$reason")
                        mainHandler.post { mediaSession?.setSessionExtras(Bundle()) }
                    }
                })
                addAnalyticsListener(object : AnalyticsListener {
                    override fun onVideoInputFormatChanged(
                        eventTime: AnalyticsListener.EventTime,
                        format: androidx.media3.common.Format,
                        decoderReuseEvaluation: androidx.media3.exoplayer.DecoderReuseEvaluation?,
                    ) {
                        val csdSummary = format.initializationData.mapIndexed { index, bytes ->
                            val head = bytes.take(24).joinToString("") { b ->
                                "%02x".format(b.toInt() and 0xff)
                            }
                            "csd$index=${bytes.size}:$head"
                        }.joinToString(",")
                        logger.log(
                            "CodecInput",
                            "mime=${format.sampleMimeType} codecs=${format.codecs} size=${format.width}x${format.height} fps=${format.frameRate} maxInput=${format.maxInputSize} rotation=${format.rotationDegrees} color=${format.colorInfo} init=[$csdSummary] reuse=${decoderReuseEvaluation?.result}"
                        )
                    }

                    override fun onPlayerError(
                        eventTime: AnalyticsListener.EventTime,
                        error: androidx.media3.common.PlaybackException,
                    ) {
                        val causeChain = generateSequence<Throwable>(error) { it.cause }
                            .take(8)
                            .joinToString(" <- ") { "${it.javaClass.name}:${it.message}" }
                        logger.log(
                            "PlayerErrorService",
                            "code=${error.errorCode} name=${error.errorCodeName} message=${error.message} positionMs=${eventTime.currentPlaybackPositionMs} cause=$causeChain"
                        )
                    }

                    override fun onLoadError(
                        eventTime: AnalyticsListener.EventTime,
                        loadEventInfo: androidx.media3.exoplayer.source.LoadEventInfo,
                        mediaLoadData: androidx.media3.exoplayer.source.MediaLoadData,
                        error: java.io.IOException,
                        wasCanceled: Boolean,
                    ) {
                        logger.log(
                            "LoadError",
                            "uri=${loadEventInfo.uri} bytesLoaded=${loadEventInfo.bytesLoaded} dataType=${mediaLoadData.dataType} trackType=${mediaLoadData.trackType} canceled=$wasCanceled positionMs=${eventTime.currentPlaybackPositionMs} error=${error.javaClass.name}:${error.message}"
                        )
                    }

                    override fun onVideoDecoderInitialized(
                        eventTime: AnalyticsListener.EventTime,
                        decoderName: String,
                        initializedTimestampMs: Long,
                        initializationDurationMs: Long,
                    ) {
                        logger.log("CodecRuntime", "videoDecoder=$decoderName initMs=$initializationDurationMs compat=${config.hardwareCompatibilityMode}")
                    }

                    override fun onAudioDecoderInitialized(
                        eventTime: AnalyticsListener.EventTime,
                        decoderName: String,
                        initializedTimestampMs: Long,
                        initializationDurationMs: Long,
                    ) {
                        logger.log("CodecRuntime", "audioDecoder=$decoderName initMs=$initializationDurationMs")
                    }

                    override fun onVideoCodecError(eventTime: AnalyticsListener.EventTime, videoCodecError: Exception) {
                        logger.log("CodecRuntime", "videoCodecError=${videoCodecError.javaClass.simpleName}:${videoCodecError.message} positionMs=${eventTime.currentPlaybackPositionMs} frameCount=$videoFrameCount")
                    }

                    override fun onDroppedVideoFrames(eventTime: AnalyticsListener.EventTime, droppedFrames: Int, elapsedMs: Long) {
                        logger.log("CodecRuntime", "droppedFrames=$droppedFrames elapsedMs=$elapsedMs positionMs=${eventTime.currentPlaybackPositionMs} frameCount=$videoFrameCount")
                    }

                    override fun onRenderedFirstFrame(eventTime: AnalyticsListener.EventTime, output: Any, renderTimeMs: Long) {
                        logger.log("CodecRuntime", "firstFrame renderTimeMs=$renderTimeMs positionMs=${eventTime.currentPlaybackPositionMs}")
                    }
                })
            }
        mediaSession = MediaSession.Builder(this, player).build()
    }

    private fun logVideoCodecCapabilities(logger: DiagnosticsLogger) {
        runCatching {
            val list = MediaCodecList(MediaCodecList.ALL_CODECS).codecInfos
            list.asSequence()
                .filterNot { it.isEncoder }
                .filter { info -> info.supportedTypes.any { it.equals(MediaFormat.MIMETYPE_VIDEO_HEVC, true) || it.equals(MediaFormat.MIMETYPE_VIDEO_AVC, true) || it.equals("video/av01", true) } }
                .forEach { info ->
                    info.supportedTypes.filter { it.startsWith("video/") }.forEach { mime ->
                        runCatching {
                            val caps = info.getCapabilitiesForType(mime)
                            val video = caps.videoCapabilities
                            val profiles = caps.profileLevels.joinToString { "${it.profile}/${it.level}" }.ifBlank { "none" }
                            val colors = caps.colorFormats.joinToString().ifBlank { "none" }
                            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                "hw=${info.isHardwareAccelerated} sw=${info.isSoftwareOnly} vendor=${info.isVendor}"
                            } else "hw=? sw=? vendor=?"
                            val perf = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                video.supportedPerformancePoints?.joinToString()?.ifBlank { "empty" } ?: "null"
                            } else "n/a"
                            val uhd60 = runCatching { video.areSizeAndRateSupported(3840, 2160, 60.0) }.getOrDefault(false)
                            logger.log("CodecCaps", "name=${info.name} mime=$mime $flags maxInstances=${caps.maxSupportedInstances} widths=${video.supportedWidths} heights=${video.supportedHeights} fps=${video.supportedFrameRates} align=${video.widthAlignment}x${video.heightAlignment} UHD60=$uhd60 profiles=$profiles colors=$colors perf=$perf")
                        }.onFailure { logger.log("CodecCaps", "name=${info.name} mime=$mime error=${it.message}") }
                    }
                }
        }.onFailure { logger.log("CodecCaps", "enumeration error=${it.message}") }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    override fun onDestroy() {
        mediaSession?.run { player.release(); release() }
        mediaSession = null
        PlayerCache.release()
        super.onDestroy()
    }
}
