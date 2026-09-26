from pathlib import Path

def replace_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{label}: expected 1 match, found {count}")
    return text.replace(old, new, 1)

build = Path("app/build.gradle.kts")
s = build.read_text()
s = replace_once(s, 'versionCode = 130', 'versionCode = 131', "versionCode")
s = replace_once(s, 'versionName = "2.3.7"', 'versionName = "2.3.8"', "versionName")
build.write_text(s)

service = Path("app/src/main/java/com/embyplayernext/he/playback/PlaybackService.kt")
s = service.read_text()

old = '''        val useDangbeiStartupCompat = isRockchip && config.hardwareCompatibilityMode
        logger.log(
            "Codec",
            "dangbeiStartupCompat=$useDangbeiStartupCompat surfaceSetFrameRate=${if (useDangbeiStartupCompat) "disabled" else "media3-default"}"
        )
        val player = ExoPlayer.Builder(this, renderers)
            .setTrackSelector(trackSelector)
            .setVideoChangeFrameRateStrategy(
                if (useDangbeiStartupCompat) C.VIDEO_CHANGE_FRAME_RATE_STRATEGY_OFF
                else C.VIDEO_CHANGE_FRAME_RATE_STRATEGY_ONLY_IF_SEAMLESS
            )
            .setMediaSourceFactory(mediaSourceFactory)
'''
new = '''        logger.log("Codec", "media3Baseline=true videoChangeFrameRateStrategy=library-default")
        val player = ExoPlayer.Builder(this, renderers)
            .setTrackSelector(trackSelector)
            .setMediaSourceFactory(mediaSourceFactory)
'''
s = replace_once(s, old, new, "restore Media3 builder baseline")
service.write_text(s)

settings = Path("app/src/main/java/com/embyplayernext/he/ui/screens/SettingsScreen.kt")
s = settings.read_text().replace('headlineContent = { Text("EmbyPlayerNext 2.3.7") }', 'headlineContent = { Text("EmbyPlayerNext 2.3.8") }')
settings.write_text(s)