from pathlib import Path

def replace_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{label}: expected 1 match, found {count}")
    return text.replace(old, new, 1)

build = Path("app/build.gradle.kts")
s = build.read_text()
s = replace_once(s, 'versionCode = 131', 'versionCode = 132', "versionCode")
s = replace_once(s, 'versionName = "2.3.8"', 'versionName = "2.3.9"', "versionName")
build.write_text(s)

service = Path("app/src/main/java/com/embyplayernext/he/playback/PlaybackService.kt")
s = service.read_text()
s = replace_once(s, "        var lastVideoFramePtsUs = C.TIME_UNSET\n", "        var lastVideoFramePtsUs = C.TIME_UNSET\n        var videoFrameMediaId = \"\"\n", "video frame media id")
start_marker = "                setVideoFrameMetadataListener(VideoFrameMetadataListener {"
end_marker = "                })\n                addListener(object : Player.Listener {"
start = s.find(start_marker)
if start < 0: raise SystemExit("video frame listener start not found")
end = s.find(end_marker, start)
if end < 0: raise SystemExit("video frame listener end not found")
new_listener = '''                setVideoFrameMetadataListener(VideoFrameMetadataListener { presentationTimeUs, releaseTimeNs, format, mediaFormat ->
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
'''
s = s[:start] + new_listener + s[end + len("                })"):]
s = replace_once(
    s,
    "                    override fun onMediaItemTransition(mediaItem: androidx.media3.common.MediaItem?, reason: Int) {\n                        videoFrameCount = 0L\n",
    "                    override fun onMediaItemTransition(mediaItem: androidx.media3.common.MediaItem?, reason: Int) {\n                        videoFrameMediaId = mediaItem?.mediaId.orEmpty()\n                        videoFrameCount = 0L\n",
    "cache media id on application thread",
)
service.write_text(s)

player = Path("app/src/main/java/com/embyplayernext/he/ui/screens/PlayerScreen.kt")
s = player.read_text()
import re
m = re.search(r"        if\s*\(\s*(controlsVisible|controls)\s*\|\|\s*locked\s*\)\s*\{", s)
if not m: raise SystemExit("Media3 controls block start not found")
controls_name = m.group(1)
start = m.start()
end = s.find("        error?.let", start)
if end < 0: raise SystemExit("Media3 controls block end not found")
shared = f'''        SharedPlayerControls(
            title = descriptor.item.name,
            engineLabel = "Media3 / ExoPlayer",
            visible = {controls_name},
            locked = locked,
            positionMs = controller?.currentPosition ?: 0L,
            durationMs = controller?.duration?.takeIf {{ it > 0 && it != C.TIME_UNSET }} ?: durationMs ?: 0L,
            isPlaying = controller?.isPlaying == true,
            networkSpeed = networkSpeed,
            rewindSeconds = config.rewindSeconds,
            forwardSeconds = config.forwardSeconds,
            onBack = {{ exit() }},
            onToggleLock = {{ locked = !locked }},
            onSeek = {{ controller?.seekTo(it) }},
            onTogglePlay = {{ if (controller?.isPlaying == true) controller.pause() else controller?.play() }},
            onSpeed = {{ speedDialog = true }},
            onAudio = {{ audioDialog = true }},
            onSubtitle = {{ subtitleDialog = true }},
            onAspect = {{ aspect = AspectMode.entries[(aspect.ordinal + 1) % AspectMode.entries.size] }},
        )
'''
s = s[:start] + shared + "\n" + s[end:]
player.write_text(s)

settings = Path("app/src/main/java/com/embyplayernext/he/ui/screens/SettingsScreen.kt")
s = settings.read_text().replace('headlineContent = { Text("EmbyPlayerNext 2.3.8") }', 'headlineContent = { Text("EmbyPlayerNext 2.3.9") }')
settings.write_text(s)