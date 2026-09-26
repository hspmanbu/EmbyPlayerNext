@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package com.embyplayernext.he.playback

import android.content.Context
import android.media.MediaFormat
import android.os.Build
import android.os.Handler
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.DecoderReuseEvaluation
import androidx.media3.exoplayer.Renderer
import androidx.media3.exoplayer.mediacodec.MediaCodecAdapter
import androidx.media3.exoplayer.mediacodec.MediaCodecInfo
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.exoplayer.video.MediaCodecVideoRenderer
import androidx.media3.exoplayer.video.VideoRendererEventListener
import com.embyplayernext.he.util.DiagnosticsLogger
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

    private fun experimentMode(): String {
        if (!prefs.getBoolean("hardware_compatibility_mode", false)) return "default"
        return prefs.getString("media3_rk_mode", "default") ?: "default"
    }

    private fun isTarget(format: Format): Boolean =
        format.sampleMimeType.equals(MimeTypes.VIDEO_H265, ignoreCase = true)

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
        if (experimentMode() == "directcodec_renderer") return false
        return super.shouldDropOutputBuffer(earlyUs, elapsedRealtimeUs, isLastBuffer)
    }

    override fun shouldDropBuffersToKeyframe(earlyUs: Long, elapsedRealtimeUs: Long, isLastBuffer: Boolean): Boolean {
        if (experimentMode() == "directcodec_renderer") return false
        return super.shouldDropBuffersToKeyframe(earlyUs, elapsedRealtimeUs, isLastBuffer)
    }

    override fun shouldSkipBuffersWithIdenticalReleaseTime(): Boolean {
        if (experimentMode() == "directcodec_renderer") return false
        return super.shouldSkipBuffersWithIdenticalReleaseTime()
    }

    override fun renderOutputBufferV21(
        codec: MediaCodecAdapter,
        index: Int,
        presentationTimeUs: Long,
        releaseTimeNs: Long,
    ) {
        if (experimentMode() == "directcodec_renderer") {
            logger.log("RKDirectRenderer", "ptsUs=" + presentationTimeUs + " release=immediate")
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
