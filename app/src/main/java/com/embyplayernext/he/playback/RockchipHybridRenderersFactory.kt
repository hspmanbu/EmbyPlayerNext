@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package com.embyplayernext.he.playback

import android.content.Context
import android.os.Handler
import androidx.media3.common.MimeTypes
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.Renderer
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.exoplayer.video.MediaCodecVideoRenderer
import androidx.media3.exoplayer.video.VideoRendererEventListener
import com.embyplayernext.he.util.DiagnosticsLogger
import java.util.ArrayList

/**
 * Default Media3 renderers everywhere except the explicitly requested Rockchip OMX compatibility
 * path. The stock MediaCodecVideoRenderer is deliberately kept as a fallback renderer.
 */
class RockchipHybridRenderersFactory(
    context: Context,
    private val isRockchip: Boolean,
    private val compatibilityEnabled: Boolean,
    private val enableHybridDirectVideo: Boolean,
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

        if (!isRockchip || !compatibilityEnabled || !enableHybridDirectVideo) {
            logger.log(
                "RKHybrid",
                "route=false rockchip=$isRockchip compat=$compatibilityEnabled requested=$enableHybridDirectVideo reason=default-media3",
            )
            return
        }

        val omxDecoder = runCatching {
            mediaCodecSelector
                .getDecoderInfos(MimeTypes.VIDEO_H265, false, false)
                .firstOrNull { info ->
                    val name = info.name.lowercase()
                    name.startsWith("omx.") && !name.contains("google")
                }
        }.getOrNull()

        if (omxDecoder == null) {
            logger.log(
                "RKHybrid",
                "route=false rockchip=true compat=true requested=true reason=no-vendor-omx-hevc",
            )
            return
        }

        val coreIndex = (out.size - 1 downTo start).firstOrNull { index ->
            out[index].javaClass == MediaCodecVideoRenderer::class.java
        }
        if (coreIndex == null) {
            logger.log("RKHybrid", "route=false reason=no-stock-video-renderer omx=${omxDecoder.name}")
            return
        }

        // Insert before the stock renderer. This renderer only reports support for clear HEVC,
        // so every other format continues to map to Media3's original renderer unchanged.
        out.add(
            coreIndex,
            RockchipDirectVideoRenderer(
                decoderName = omxDecoder.name,
                eventHandler = eventHandler,
                eventListener = eventListener,
                logger = logger,
            ),
        )
        logger.log(
            "RKHybrid",
            "route=true omx=${omxDecoder.name} hybridIndex=$coreIndex stockFallbackIndex=${coreIndex + 1}",
        )
    }
}
