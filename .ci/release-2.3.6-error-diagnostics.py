from pathlib import Path

def replace_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{label}: expected 1 match, found {count}")
    return text.replace(old, new, 1)

build = Path("app/build.gradle.kts")
s = build.read_text()
s = replace_once(s, 'versionCode = 128', 'versionCode = 129', "versionCode")
s = replace_once(s, 'versionName = "2.3.5"', 'versionName = "2.3.6"', "versionName")
build.write_text(s)

service = Path("app/src/main/java/com/embyplayernext/he/playback/PlaybackService.kt")
s = service.read_text()
service_marker = """                    override fun onVideoDecoderInitialized(
"""
service_insert = """                    override fun onPlayerError(
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
"""
s = replace_once(s, service_marker, service_insert, "service analytics marker")
service.write_text(s)

screen = Path("app/src/main/java/com/embyplayernext/he/ui/screens/PlayerScreen.kt")
s = screen.read_text()
screen_marker = """            override fun onPlayerError(playbackError: PlaybackException) {
"""
screen_insert = """            override fun onPlayerError(playbackError: PlaybackException) {
                val causeChain = generateSequence<Throwable>(playbackError) { it.cause }
                    .take(8)
                    .joinToString(" <- ") { "${it.javaClass.name}:${it.message}" }
                diagnostics.log(
                    "PlayerError",
                    "code=${playbackError.errorCode} name=${playbackError.errorCodeName} message=${playbackError.message} state=${player.playbackState} positionMs=${player.currentPosition} bufferedMs=${player.bufferedPosition} loading=${player.isLoading} playWhenReady=${player.playWhenReady} uiCompat=${config.hardwareCompatibilityMode} cause=$causeChain"
                )
"""
s = replace_once(s, screen_marker, screen_insert, "PlayerScreen onPlayerError marker")
screen.write_text(s)
