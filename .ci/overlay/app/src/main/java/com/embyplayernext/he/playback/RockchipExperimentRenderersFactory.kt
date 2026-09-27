@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package com.embyplayernext.he.playback

import android.content.Context
import android.media.MediaFormat
import android.os.Build
import android.os.Handler
import android.os.SystemClock
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.decoder.DecoderInputBuffer
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.DecoderReuseEvaluation
import androidx.media3.exoplayer.Renderer
import androidx.media3.exoplayer.mediacodec.MediaCodecAdapter
import androidx.media3.exoplayer.mediacodec.MediaCodecInfo
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.exoplayer.video.MediaCodecVideoRenderer
import androidx.media3.exoplayer.video.VideoRendererEventListener
import com.embyplayernext.he.util.DiagnosticsLogger
import java.nio.ByteBuffer
import java.util.ArrayList
import java.util.concurrent.atomic.AtomicLong

class RockchipExperimentRenderersFactory(
    context: Context,
    private val isRockchip: Boolean,
    private val logger: DiagnosticsLogger,
) : DefaultRenderersFactory(context) {

    override fun buildVideoRenderers(
        context: Context,
        extensionRendererMode: Int,
        mediaCodecSelector: MediaCodecSelector,
        enableDecoderFallback: Boolean,
        eventHandler: Handler,
        eventListener: VideoRendererEventListener,
        allowedVideoJoiningTimeMs: Long,
        out: ArrayList<Renderer>,
    ) {
        val start = out.size
        super.buildVideoRenderers(
            context,
            extensionRendererMode,
            mediaCodecSelector,
            enableDecoderFallback,
            eventHandler,
            eventListener,
            allowedVideoJoiningTimeMs,
            out,
        )
        if (!isRockchip) return

        val coreIndex = (out.size - 1 downTo start).firstOrNull { index ->
            out[index].javaClass == MediaCodecVideoRenderer::class.java
        } ?: return

        out[coreIndex] = RockchipExperimentVideoRenderer(
            context = context,
            codecAdapterFactory = getCodecAdapterFactory(),
            mediaCodecSelector = mediaCodecSelector,
            allowedVideoJoiningTimeMs = allowedVideoJoiningTimeMs,
            enableDecoderFallback = enableDecoderFallback,
            eventHandler = eventHandler,
            eventListener = eventListener,
            logger = logger,
        )
        logger.log("RKExperiment", "customVideoRenderer=true coreIndex=$coreIndex")
    }
}

@Suppress("DEPRECATION")
private class RockchipExperimentVideoRenderer(
    context: Context,
    codecAdapterFactory: MediaCodecAdapter.Factory,
    mediaCodecSelector: MediaCodecSelector,
    allowedVideoJoiningTimeMs: Long,
    enableDecoderFallback: Boolean,
    eventHandler: Handler,
    eventListener: VideoRendererEventListener,
    private val logger: DiagnosticsLogger,
) : MediaCodecVideoRenderer(
    context,
    codecAdapterFactory,
    mediaCodecSelector,
    allowedVideoJoiningTimeMs,
    enableDecoderFallback,
    eventHandler,
    eventListener,
    DefaultRenderersFactory.MAX_DROPPED_VIDEO_FRAME_COUNT_TO_NOTIFY,
) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences("emby_player_prefs", Context.MODE_PRIVATE)
    private val codecGeneration = AtomicLong(0L)
    private var seekGeneration = 0L
    private var pendingSeekGeneration = -1L
    private var seekTargetUs = Long.MIN_VALUE
    private var seekFirstInputUs = Long.MIN_VALUE
    private var seekStartRealtimeMs = 0L
    private var seekStallLogged = false
    private var forceReleaseForSeek = false

    private fun experimentMode(): String {
        if (!prefs.getBoolean("hardware_compatibility_mode", false)) return "default"
        return prefs.getString("media3_rk_mode", "default") ?: "default"
    }

    private fun isTarget(format: Format): Boolean =
        format.sampleMimeType.equals(MimeTypes.VIDEO_H265, ignoreCase = true)


    override fun onPositionReset(positionUs: Long, joining: Boolean) {
        val directMode = experimentMode() == "directcodec_renderer"
        val hadCodec = getCodec() != null
        if (directMode && hadCodec) {
            seekGeneration += 1L
            pendingSeekGeneration = seekGeneration
            seekTargetUs = positionUs
            seekFirstInputUs = Long.MIN_VALUE
            seekStartRealtimeMs = SystemClock.elapsedRealtime()
            seekStallLogged = false
            forceReleaseForSeek = true
            logger.log(
                "RKSeek",
                "begin generation=$seekGeneration targetUs=$positionUs strategy=release-recreate codec=${getCodecInfo()?.name} thread=${Thread.currentThread().name}",
            )
        }
        try {
            super.onPositionReset(positionUs, joining)
        } finally {
            forceReleaseForSeek = false
        }
        if (directMode && hadCodec) {
            logger.log(
                "RKSeek",
                "recreate-complete generation=$seekGeneration targetUs=$positionUs codec=${getCodecInfo()?.name} codecPresent=${getCodec() != null}",
            )
        }
    }

    override fun flushOrReleaseCodec(): Boolean {
        if (
            experimentMode() == "directcodec_renderer" &&
            forceReleaseForSeek &&
            getCodec() != null
        ) {
            logger.log(
                "RKSeek",
                "release-codec generation=$pendingSeekGeneration targetUs=$seekTargetUs codec=${getCodecInfo()?.name}",
            )
            releaseCodec()
            return true
        }
        return super.flushOrReleaseCodec()
    }

    override fun resetCodecStateForFlush() {
        super.resetCodecStateForFlush()
        if (experimentMode() == "directcodec_renderer" && pendingSeekGeneration >= 0L) {
            logger.log(
                "RKSeek",
                "codec-state-reset generation=$pendingSeekGeneration targetUs=$seekTargetUs strategy=release-recreate",
            )
        }
    }

    override fun onQueueInputBuffer(buffer: DecoderInputBuffer) {
        super.onQueueInputBuffer(buffer)
        if (
            experimentMode() == "directcodec_renderer" &&
            pendingSeekGeneration >= 0L &&
            seekFirstInputUs == Long.MIN_VALUE
        ) {
            seekFirstInputUs = buffer.timeUs
            logger.log(
                "RKSeek",
                "first-input generation=$pendingSeekGeneration targetUs=$seekTargetUs ptsUs=${buffer.timeUs}",
            )
        }
    }

    override fun processOutputBuffer(
        positionUs: Long,
        elapsedRealtimeUs: Long,
        codec: MediaCodecAdapter?,
        buffer: ByteBuffer?,
        bufferIndex: Int,
        bufferFlags: Int,
        sampleCount: Int,
        bufferPresentationTimeUs: Long,
        isDecodeOnlyBuffer: Boolean,
        isLastBuffer: Boolean,
        format: Format,
    ): Boolean {
        if (experimentMode() != "directcodec_renderer" || pendingSeekGeneration < 0L) {
            return super.processOutputBuffer(
                positionUs,
                elapsedRealtimeUs,
                codec,
                buffer,
                bufferIndex,
                bufferFlags,
                sampleCount,
                bufferPresentationTimeUs,
                isDecodeOnlyBuffer,
                isLastBuffer,
                format,
            )
        }

        val activeCodec = codec ?: return false
        val generation = pendingSeekGeneration
        val presentationTimeUs = bufferPresentationTimeUs - getOutputStreamOffsetUs()
        if (!isLastBuffer && isDecodeOnlyBuffer) {
            logger.log(
                "RKSeek",
                "discard-decode-only generation=$generation targetUs=$seekTargetUs ptsUs=$bufferPresentationTimeUs",
            )
            skipOutputBuffer(activeCodec, bufferIndex, presentationTimeUs)
            return true
        }

        // The codec instance is fresh after a Rockchip seek. Let decode-only preroll be discarded,
        // then render the first valid frame immediately so Media3 can leave BUFFERING promptly.
        @Suppress("DEPRECATION")
        super.renderOutputBuffer(activeCodec, bufferIndex, presentationTimeUs)
        pendingSeekGeneration = -1L
        val elapsedMs = (SystemClock.elapsedRealtime() - seekStartRealtimeMs).coerceAtLeast(0L)
        logger.log(
            "RKSeek",
            "first-output generation=$generation targetUs=$seekTargetUs ptsUs=$bufferPresentationTimeUs elapsedMs=$elapsedMs immediate=true",
        )
        return true
    }

    override fun render(positionUs: Long, elapsedRealtimeUs: Long) {
        super.render(positionUs, elapsedRealtimeUs)
        if (
            experimentMode() == "directcodec_renderer" &&
            pendingSeekGeneration >= 0L &&
            seekFirstInputUs != Long.MIN_VALUE &&
            !seekStallLogged
        ) {
            val elapsedMs = SystemClock.elapsedRealtime() - seekStartRealtimeMs
            if (elapsedMs >= 900L) {
                seekStallLogged = true
                logger.log(
                    "RKSeek",
                    "stall generation=$pendingSeekGeneration targetUs=$seekTargetUs firstInputUs=$seekFirstInputUs elapsedMs=$elapsedMs codec=${getCodecInfo()?.name}",
                )
            }
        }
    }

    override fun canReuseCodec(
        codecInfo: MediaCodecInfo,
        oldFormat: Format,
        newFormat: Format,
    ): DecoderReuseEvaluation {
        val mode = experimentMode()
        if (isTarget(newFormat) && mode != "default") {
            logger.log(
                "RKCodecReuse",
                "mode=$mode decoder=${codecInfo.name} forced=false old=${oldFormat.width}x${oldFormat.height}@${oldFormat.frameRate} new=${newFormat.width}x${newFormat.height}@${newFormat.frameRate}",
            )
            return DecoderReuseEvaluation(
                codecInfo.name,
                oldFormat,
                newFormat,
                DecoderReuseEvaluation.REUSE_RESULT_NO,
                DecoderReuseEvaluation.DISCARD_REASON_APP_OVERRIDE,
            )
        }
        return super.canReuseCodec(codecInfo, oldFormat, newFormat)
    }

    override fun getCodecOperatingRateV23(
        targetPlaybackSpeed: Float,
        format: Format,
        streamFormats: Array<Format>,
    ): Float {
        val baseline = super.getCodecOperatingRateV23(targetPlaybackSpeed, format, streamFormats)
        val mode = experimentMode()
        if (isTarget(format) && (mode == "no_operating_rate" || mode == "dangbei_minimal" || mode == "directcodec_renderer")) {
            logger.log(
                "RKCodecRate",
                "mode=$mode mime=${format.sampleMimeType} frameRate=${format.frameRate} baseline=$baseline override=$CODEC_OPERATING_RATE_UNSET",
            )
            return CODEC_OPERATING_RATE_UNSET
        }
        if (isTarget(format)) {
            logger.log(
                "RKCodecRate",
                "mode=$mode mime=${format.sampleMimeType} frameRate=${format.frameRate} baseline=$baseline override=none",
            )
        }
        return baseline
    }


    override fun shouldDropOutputBuffer(earlyUs: Long, elapsedRealtimeUs: Long, isLastBuffer: Boolean): Boolean {
        val drop = super.shouldDropOutputBuffer(earlyUs, elapsedRealtimeUs, isLastBuffer)
        if (experimentMode() == "directcodec_renderer" && drop) {
            logger.log("RKClockGate", "drop-late earlyUs=$earlyUs")
        }
        return drop
    }

    override fun shouldDropBuffersToKeyframe(earlyUs: Long, elapsedRealtimeUs: Long, isLastBuffer: Boolean): Boolean {
        val drop = super.shouldDropBuffersToKeyframe(earlyUs, elapsedRealtimeUs, isLastBuffer)
        if (experimentMode() == "directcodec_renderer" && drop) {
            logger.log("RKClockGate", "drop-to-keyframe earlyUs=$earlyUs")
        }
        return drop
    }

    override fun shouldSkipBuffersWithIdenticalReleaseTime(): Boolean {
        return super.shouldSkipBuffersWithIdenticalReleaseTime()
    }

    override fun renderOutputBufferV21(
        codec: MediaCodecAdapter,
        index: Int,
        presentationTimeUs: Long,
        releaseTimeNs: Long,
    ) {
        if (experimentMode() == "directcodec_renderer") {
            val deltaUs = (releaseTimeNs - System.nanoTime()) / 1_000L
            logger.log("RKDirectRenderer", "ptsUs=$presentationTimeUs release=immediate scheduledDeltaUs=$deltaUs")
            @Suppress("DEPRECATION")
            super.renderOutputBuffer(codec, index, presentationTimeUs)
        } else {
            super.renderOutputBufferV21(codec, index, presentationTimeUs, releaseTimeNs)
        }
    }

    override fun getMediaFormat(
        format: Format,
        codecMimeType: String,
        codecMaxValues: CodecMaxValues,
        codecOperatingRate: Float,
        deviceNeedsNoPostProcessWorkaround: Boolean,
        tunnelingAudioSessionId: Int,
    ): MediaFormat {
        val mediaFormat = super.getMediaFormat(
            format,
            codecMimeType,
            codecMaxValues,
            codecOperatingRate,
            deviceNeedsNoPostProcessWorkaround,
            tunnelingAudioSessionId,
        )
        val mode = experimentMode()
        if (!isTarget(format)) return mediaFormat

        if (Build.VERSION.SDK_INT >= 29) {
            when (mode) {
                "no_operating_rate" -> {
                    mediaFormat.removeKey("operating-rate")
                }
                "dangbei_minimal", "directcodec_renderer" -> {
                    listOf(
                        "operating-rate",
                        "priority",
                        "max-width",
                        "max-height",
                        "max-input-size",
                        "frame-rate",
                    ).forEach { key ->
                        if (mediaFormat.containsKey(key)) mediaFormat.removeKey(key)
                    }
                }
            }
        }

        val generation = codecGeneration.incrementAndGet()
        logger.log(
            "RKMediaFormat",
            "generation=$generation mode=$mode sdk=${Build.VERSION.SDK_INT} mime=$codecMimeType input=${format.width}x${format.height}@${format.frameRate} codecRate=$codecOperatingRate mediaFormat=$mediaFormat",
        )
        return mediaFormat
    }
}
