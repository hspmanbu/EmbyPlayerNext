@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package com.embyplayernext.he.playback

import android.media.MediaCodec
import android.media.MediaFormat
import android.os.Handler
import android.os.SystemClock
import android.view.Surface
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.exoplayer.FormatHolder
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.VideoSize
import androidx.media3.common.util.MediaFormatUtil
import androidx.media3.decoder.DecoderInputBuffer
import androidx.media3.exoplayer.BaseRenderer
import androidx.media3.exoplayer.DecoderCounters
import androidx.media3.exoplayer.Renderer
import androidx.media3.exoplayer.RendererCapabilities
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.video.VideoFrameMetadataListener
import androidx.media3.exoplayer.video.VideoRendererEventListener
import com.embyplayernext.he.util.DiagnosticsLogger

/**
 * Rockchip OMX compatibility renderer.
 *
 * Media3 still owns demuxing, buffering, audio, text/subtitles, track selection, playlist and the
 * master playback clock. Only the compressed-video -> MediaCodec -> Surface leg is managed here.
 */
class RockchipDirectVideoRenderer(
    private val decoderName: String,
    eventHandler: Handler,
    eventListener: VideoRendererEventListener,
    private val logger: DiagnosticsLogger,
) : BaseRenderer(C.TRACK_TYPE_VIDEO) {

    private val eventDispatcher = VideoRendererEventListener.EventDispatcher(eventHandler, eventListener)
    private val decoderCounters = DecoderCounters()
    private val sampleBuffer = DecoderInputBuffer(DecoderInputBuffer.BUFFER_REPLACEMENT_MODE_DIRECT)
    private val outputInfo = MediaCodec.BufferInfo()

    private var surface: Surface? = null
    private var frameMetadataListener: VideoFrameMetadataListener? = null
    private var currentFormat: Format? = null
    private var codec: MediaCodec? = null
    private var outputMediaFormat: MediaFormat? = null

    private var samplePending = false
    private var inputEnded = false
    private var outputEnded = false
    private var renderedFirstFrame = false
    private var waitingForKeyFrame = true

    private var pendingOutputIndex = C.INDEX_UNSET
    private var pendingOutputPtsUs = 0L
    private var pendingOutputFlags = 0
    private var playbackSpeed = 1f
    private var renderGeneration = 0L

    override fun getName(): String = "RockchipDirectVideoRenderer($decoderName)"

    override fun supportsFormat(format: Format): Int {
        if (!MimeTypes.VIDEO_H265.equals(format.sampleMimeType, ignoreCase = true)) {
            return RendererCapabilities.create(
                if (format.sampleMimeType?.startsWith("video/") == true) {
                    C.FORMAT_UNSUPPORTED_SUBTYPE
                } else {
                    C.FORMAT_UNSUPPORTED_TYPE
                },
            )
        }
        if (format.drmInitData != null) {
            return RendererCapabilities.create(C.FORMAT_UNSUPPORTED_DRM)
        }
        return RendererCapabilities.create(
            C.FORMAT_HANDLED,
            RendererCapabilities.ADAPTIVE_NOT_SUPPORTED,
            RendererCapabilities.TUNNELING_NOT_SUPPORTED,
            RendererCapabilities.HARDWARE_ACCELERATION_SUPPORTED,
            RendererCapabilities.DECODER_SUPPORT_PRIMARY,
        )
    }

    override fun setPlaybackSpeed(currentPlaybackSpeed: Float, targetPlaybackSpeed: Float) {
        playbackSpeed = currentPlaybackSpeed.coerceAtLeast(0.25f)
        logger.log(
            "RKHybridSpeed",
            "current=$currentPlaybackSpeed target=$targetPlaybackSpeed applied=$playbackSpeed generation=$renderGeneration",
        )
    }

    override fun handleMessage(messageType: Int, message: Any?) {
        when (messageType) {
            Renderer.MSG_SET_VIDEO_OUTPUT -> {
                val newSurface = message as? Surface
                if (surface !== newSurface) {
                    logger.log(
                        "RKHybridSurface",
                        "changed old=${surface != null} new=${newSurface != null} codec=${codec?.name}",
                    )
                    surface = newSurface
                    if (codec != null) {
                        releaseCodec("surface-change")
                        waitingForKeyFrame = true
                        renderedFirstFrame = false
                    }
                }
            }

            Renderer.MSG_SET_VIDEO_FRAME_METADATA_LISTENER -> {
                frameMetadataListener = message as? VideoFrameMetadataListener
            }

            else -> super.handleMessage(messageType, message)
        }
    }

    override fun onEnabled(joining: Boolean, mayRenderStartOfStream: Boolean) {
        decoderCounters.ensureUpdated()
        eventDispatcher.enabled(decoderCounters)
        logger.log("RKHybrid", "enabled joining=$joining mayRenderStart=$mayRenderStartOfStream decoder=$decoderName")
    }

    override fun onStreamChanged(
        formats: Array<Format>,
        startPositionUs: Long,
        offsetUs: Long,
        mediaPeriodId: MediaSource.MediaPeriodId,
    ) {
        val newFormat = formats.firstOrNull()
        if (newFormat != null && newFormat != currentFormat) {
            currentFormat = newFormat
            eventDispatcher.inputFormatChanged(newFormat, null)
            eventDispatcher.videoSizeChanged(
                VideoSize(
                    newFormat.width.coerceAtLeast(0),
                    newFormat.height.coerceAtLeast(0),
                    newFormat.pixelWidthHeightRatio.takeIf { it > 0f } ?: 1f,
                ),
            )
            releaseCodec("stream-format-change")
            waitingForKeyFrame = true
            logger.log(
                "RKHybridFormat",
                "stream mime=${newFormat.sampleMimeType} size=${newFormat.width}x${newFormat.height} fps=${newFormat.frameRate} offsetUs=$offsetUs",
            )
        }
    }

    override fun onPositionReset(positionUs: Long, joining: Boolean) {
        renderGeneration += 1L
        releaseCodec("position-reset")
        clearPipelineState()
        waitingForKeyFrame = true
        logger.log(
            "RKHybridSeek",
            "reset generation=$renderGeneration positionUs=$positionUs joining=$joining strategy=release-recreate",
        )
    }

    override fun onDisabled() {
        releaseCodec("disabled")
        clearPipelineState()
        eventDispatcher.disabled(decoderCounters)
    }

    override fun onReset() {
        releaseCodec("reset")
        clearPipelineState()
        surface = null
        currentFormat = null
    }

    override fun onRelease() {
        releaseCodec("release")
    }

    override fun isReady(): Boolean = outputEnded || renderedFirstFrame

    override fun isEnded(): Boolean = outputEnded

    override fun render(positionUs: Long, elapsedRealtimeUs: Long) {
        if (outputEnded) return
        try {
            maybeInitCodec()
            val activeCodec = codec ?: return

            var drained = 0
            while (drained < 12 && drainOne(activeCodec, positionUs)) {
                drained += 1
            }

            var fed = 0
            while (fed < 12 && feedOne(activeCodec)) {
                fed += 1
            }

            if (pendingOutputIndex == C.INDEX_UNSET) {
                var extra = 0
                while (extra < 4 && drainOne(activeCodec, positionUs)) {
                    extra += 1
                }
            }
        } catch (t: Throwable) {
            logger.log(
                "RKHybridError",
                "generation=$renderGeneration type=${t.javaClass.name} message=${t.message} codec=${codec?.name} format=${currentFormat?.sampleMimeType}",
            )
            eventDispatcher.videoCodecError(t as? Exception ?: RuntimeException(t))
            throw createRendererException(
                t,
                currentFormat,
                PlaybackException.ERROR_CODE_DECODING_FAILED,
            )
        }
    }

    private fun maybeInitCodec() {
        if (codec != null || inputEnded || outputEnded) return
        val outputSurface = surface ?: return
        if (!outputSurface.isValid) return
        val format = currentFormat ?: getStreamFormats().firstOrNull()?.also { currentFormat = it } ?: return
        if (!MimeTypes.VIDEO_H265.equals(format.sampleMimeType, ignoreCase = true)) return

        val mediaFormat = buildMinimalMediaFormat(format)
        val startedAt = SystemClock.elapsedRealtime()
        val mediaCodec = MediaCodec.createByCodecName(decoderName)
        try {
            mediaCodec.configure(mediaFormat, outputSurface, null, 0)
            mediaCodec.start()
        } catch (t: Throwable) {
            runCatching { mediaCodec.release() }
            throw t
        }
        codec = mediaCodec
        outputMediaFormat = null
        waitingForKeyFrame = true
        decoderCounters.decoderInitCount += 1
        val finishedAt = SystemClock.elapsedRealtime()
        eventDispatcher.decoderInitialized(
            decoderName,
            finishedAt,
            finishedAt - startedAt,
        )
        logger.log(
            "RKHybridCodec",
            "started generation=$renderGeneration decoder=$decoderName mediaFormat=$mediaFormat",
        )
    }

    private fun buildMinimalMediaFormat(format: Format): MediaFormat {
        val mime = format.sampleMimeType ?: MimeTypes.VIDEO_H265
        val mediaFormat = MediaFormat.createVideoFormat(
            mime,
            format.width.coerceAtLeast(1),
            format.height.coerceAtLeast(1),
        )
        MediaFormatUtil.setCsdBuffers(mediaFormat, format.initializationData)
        MediaFormatUtil.maybeSetInteger(mediaFormat, MediaFormat.KEY_ROTATION, format.rotationDegrees)
        MediaFormatUtil.maybeSetColorInfo(mediaFormat, format.colorInfo)
        return mediaFormat
    }

    private fun drainOne(activeCodec: MediaCodec, positionUs: Long): Boolean {
        if (pendingOutputIndex == C.INDEX_UNSET) {
            val index = activeCodec.dequeueOutputBuffer(outputInfo, 0L)
            when {
                index >= 0 -> {
                    pendingOutputIndex = index
                    pendingOutputPtsUs = outputInfo.presentationTimeUs
                    pendingOutputFlags = outputInfo.flags
                }

                index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    outputMediaFormat = activeCodec.outputFormat
                    logger.log(
                        "RKHybridCodec",
                        "output-format generation=$renderGeneration format=$outputMediaFormat",
                    )
                    return true
                }

                else -> return false
            }
        }

        val index = pendingOutputIndex
        val ptsUs = pendingOutputPtsUs
        val flags = pendingOutputFlags
        val eos = flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0

        if (eos && outputInfo.size == 0) {
            activeCodec.releaseOutputBuffer(index, false)
            pendingOutputIndex = C.INDEX_UNSET
            outputEnded = true
            logger.log("RKHybridCodec", "output-eos generation=$renderGeneration ptsUs=$ptsUs")
            return false
        }

        if (renderedFirstFrame && state != Renderer.STATE_STARTED) {
            return false
        }

        val earlyUs = ptsUs - positionUs
        val nowNs = System.nanoTime()
        val releaseNs: Long
        if (!renderedFirstFrame || earlyUs <= 0L) {
            releaseNs = nowNs
        } else {
            val delayUs = (earlyUs / playbackSpeed).toLong()
            if (delayUs > 50_000L) {
                return false
            }
            releaseNs = nowNs + delayUs.coerceAtLeast(0L) * 1_000L
        }

        val format = currentFormat
        if (format != null) {
            frameMetadataListener?.onVideoFrameAboutToBeRendered(
                ptsUs - getStreamOffsetUs(),
                releaseNs,
                format,
                outputMediaFormat,
            )
        }

        // Never use releaseOutputBuffer(false) for ordinary Rockchip frames. Late frames are
        // rendered immediately and the Surface naturally collapses the backlog while we catch up.
        activeCodec.releaseOutputBuffer(index, releaseNs)
        pendingOutputIndex = C.INDEX_UNSET
        decoderCounters.renderedOutputBufferCount += 1

        if (!renderedFirstFrame) {
            renderedFirstFrame = true
            surface?.let { eventDispatcher.renderedFirstFrame(it) }
            logger.log(
                "RKHybridFrame",
                "first generation=$renderGeneration ptsUs=$ptsUs positionUs=$positionUs earlyUs=$earlyUs speed=$playbackSpeed",
            )
        } else if (decoderCounters.renderedOutputBufferCount <= 10 || decoderCounters.renderedOutputBufferCount % 60 == 0) {
            logger.log(
                "RKHybridFrame",
                "count=${decoderCounters.renderedOutputBufferCount} generation=$renderGeneration ptsUs=$ptsUs positionUs=$positionUs earlyUs=$earlyUs speed=$playbackSpeed releaseNs=$releaseNs",
            )
        }

        if (eos) {
            outputEnded = true
            logger.log("RKHybridCodec", "output-eos-with-frame generation=$renderGeneration ptsUs=$ptsUs")
            return false
        }

        return earlyUs < -20_000L
    }

    private fun feedOne(activeCodec: MediaCodec): Boolean {
        if (inputEnded) return false

        if (!samplePending) {
            sampleBuffer.clear()
            val holder: FormatHolder = getFormatHolder()
            when (readSource(holder, sampleBuffer, 0)) {
                C.RESULT_NOTHING_READ -> return false

                C.RESULT_FORMAT_READ -> {
                    val newFormat = holder.format
                    if (newFormat != null && newFormat != currentFormat) {
                        currentFormat = newFormat
                        eventDispatcher.inputFormatChanged(newFormat, null)
                        releaseCodec("inline-format-change")
                        waitingForKeyFrame = true
                    }
                    return true
                }

                C.RESULT_BUFFER_READ -> {
                    if (sampleBuffer.isEndOfStream) {
                        val inputIndex = activeCodec.dequeueInputBuffer(0L)
                        if (inputIndex < 0) return false
                        activeCodec.queueInputBuffer(
                            inputIndex,
                            0,
                            0,
                            0L,
                            MediaCodec.BUFFER_FLAG_END_OF_STREAM,
                        )
                        inputEnded = true
                        decoderCounters.queuedInputBufferCount += 1
                        logger.log("RKHybridCodec", "input-eos generation=$renderGeneration")
                        return false
                    }
                    sampleBuffer.flip()
                    samplePending = true
                }
            }
        }

        if (sampleBuffer.isEncrypted) {
            throw IllegalStateException("Encrypted samples are not supported by RockchipDirectVideoRenderer")
        }

        if (waitingForKeyFrame && !sampleBuffer.isKeyFrame) {
            samplePending = false
            sampleBuffer.clear()
            decoderCounters.skippedInputBufferCount += 1
            return true
        }

        val inputIndex = activeCodec.dequeueInputBuffer(0L)
        if (inputIndex < 0) return false
        val codecBuffer = activeCodec.getInputBuffer(inputIndex)
            ?: throw IllegalStateException("MediaCodec input buffer $inputIndex is null")
        val source = sampleBuffer.data ?: throw IllegalStateException("Sample data is null")
        if (codecBuffer.capacity() < source.remaining()) {
            throw IllegalStateException(
                "Codec input too small capacity=${codecBuffer.capacity()} sample=${source.remaining()}",
            )
        }

        codecBuffer.clear()
        codecBuffer.put(source)
        val size = codecBuffer.position()
        val flags = if (sampleBuffer.isKeyFrame) MediaCodec.BUFFER_FLAG_KEY_FRAME else 0
        activeCodec.queueInputBuffer(
            inputIndex,
            0,
            size,
            sampleBuffer.timeUs,
            flags,
        )
        waitingForKeyFrame = false
        samplePending = false
        decoderCounters.queuedInputBufferCount += 1
        sampleBuffer.clear()
        return true
    }

    private fun clearPipelineState() {
        samplePending = false
        sampleBuffer.clear()
        inputEnded = false
        outputEnded = false
        pendingOutputIndex = C.INDEX_UNSET
        pendingOutputPtsUs = 0L
        pendingOutputFlags = 0
        outputMediaFormat = null
        renderedFirstFrame = false
    }

    private fun releaseCodec(reason: String) {
        val activeCodec = codec ?: return
        codec = null
        val name = runCatching { activeCodec.name }.getOrDefault(decoderName)
        runCatching { activeCodec.stop() }
        runCatching { activeCodec.release() }
        pendingOutputIndex = C.INDEX_UNSET
        outputMediaFormat = null
        decoderCounters.decoderReleaseCount += 1
        eventDispatcher.decoderReleased(name)
        logger.log(
            "RKHybridCodec",
            "released generation=$renderGeneration decoder=$name reason=$reason queued=${decoderCounters.queuedInputBufferCount} rendered=${decoderCounters.renderedOutputBufferCount}",
        )
    }
}
