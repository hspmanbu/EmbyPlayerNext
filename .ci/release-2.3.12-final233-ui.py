from pathlib import Path

def replace_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{label}: expected 1 match, found {count}")
    return text.replace(old, new, 1)

build = Path("app/build.gradle.kts")
s = build.read_text()
s = replace_once(s, 'versionCode = 134', 'versionCode = 135', "versionCode")
s = replace_once(s, 'versionName = "2.3.11"', 'versionName = "2.3.12"', "versionName")
build.write_text(s)

player = Path("app/src/main/java/com/embyplayernext/he/ui/screens/PlayerScreen.kt")
s = player.read_text()

# Restore the exact final-2.3.3 Media3 chrome block, replacing the temporary shared-control layer.
start = s.find("        val sharedAudioTracks =")
if start < 0:
    start = s.find("        SharedPlayerControls(")
if start < 0:
    raise SystemExit("shared player controls block not found")
end = s.find("        error?.let", start)
if end < 0:
    raise SystemExit("error block marker not found")
legacy = '''        if (controlsVisible || locked) {
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
                    onToggleLock = { locked = !locked; controlsVisible = true },
                    onPrevious = { previousItem?.let(::switchTo) },
                    onRewind = { player?.seekTo((position - config.rewindSeconds * 1000L).coerceAtLeast(0)) },
                    onTogglePlay = { if (player?.isPlaying == true) player.pause() else player?.play() },
                    onForward = { player?.seekTo((position + config.forwardSeconds * 1000L).coerceAtMost(if (duration > 0) duration else Long.MAX_VALUE)) },
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
                    onScrub = { scrubFraction = it; controlsVisible = true },
                    onScrubFinished = {
                        val value = scrubFraction
                        if (duration > 0 && value != null) player?.seekTo((value * duration).toLong())
                        scrubFraction = null
                    },
                    onSpeed = { settingsPage = PlayerSettingsPage.SPEED; settingsVisible = true },
                    onAudio = { settingsPage = PlayerSettingsPage.AUDIO; settingsVisible = true },
                    onSubtitle = { settingsPage = PlayerSettingsPage.SUBTITLE; settingsVisible = true },
                    onAspect = { aspect = AspectMode.entries[(aspect.ordinal + 1) % AspectMode.entries.size] },
                    onRotate = ::toggleOrientation,
                    onMore = { settingsPage = PlayerSettingsPage.MAIN; settingsVisible = true },
                    remoteMode = isTv && isLandscape,
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
            }
        }

'''
s = s[:start] + legacy + s[end:]

# LibVLC receives the same adjacent-item navigation inputs as Media3.
call_old = '''        LibVlcPlayerScreen(
            descriptor = descriptor,
            config = config,
            onStart = onStart,
            onProgress = onProgress,
            onStopped = onStopped,
            onExit = onExit,
        )
'''
call_new = '''        LibVlcPlayerScreen(
            descriptor = descriptor,
            config = config,
            previousItem = previousItem,
            nextItem = nextItem,
            onStart = onStart,
            onProgress = onProgress,
            onStopped = onStopped,
            onPlayAdjacent = onPlayAdjacent,
            onExit = onExit,
        )
'''
s = replace_once(s, call_old, call_new, "LibVLC navigation wiring")

# Expose the original 2.3.3 chrome primitives to the LibVLC adapter without changing their code.
for old, new in [
    ("private enum class AspectMode", "enum class AspectMode"),
    ("private enum class PlayerSettingsPage", "enum class PlayerSettingsPage"),
    ("private enum class GestureMode", "enum class GestureMode"),
    ("private data class GestureHud", "data class GestureHud"),
    ("private fun PlayerTopChrome(", "fun PlayerTopChrome("),
    ("private fun PlayerCenterControls(", "fun PlayerCenterControls("),
    ("private fun PlayerBottomChrome(", "fun PlayerBottomChrome("),
    ("private fun GestureHudCard(", "fun GestureHudCard("),
    ("private fun PlaybackEndedCard(", "fun PlaybackEndedCard("),
    ("private fun SpeedPickerPage(", "fun SpeedPickerPage("),
    ("private fun PlayerChoiceTile(", "fun PlayerChoiceTile("),
    ("private fun SettingsGroup(", "fun SettingsGroup("),
    ("private fun InfoLine(", "fun InfoLine("),
]:
    if old not in s:
        raise SystemExit(f"UI primitive marker missing: {old}")
    s = s.replace(old, new, 1)

player.write_text(s)

settings = Path("app/src/main/java/com/embyplayernext/he/ui/screens/SettingsScreen.kt")
s = settings.read_text().replace('headlineContent = { Text("EmbyPlayerNext 2.3.11") }', 'headlineContent = { Text("EmbyPlayerNext 2.3.12") }')
settings.write_text(s)