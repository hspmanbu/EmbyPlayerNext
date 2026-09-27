from pathlib import Path

def replace_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{label}: expected 1 match, found {count}")
    return text.replace(old, new, 1)

build = Path("app/build.gradle.kts")
s = build.read_text()
s = replace_once(s, 'versionCode = 142', 'versionCode = 143', "versionCode")
s = replace_once(s, 'versionName = "2.3.19"', 'versionName = "2.3.20"', "versionName")
build.write_text(s)

service = Path("app/src/main/java/com/embyplayernext/he/playback/PlaybackService.kt")
s = service.read_text()
s = replace_once(
    s,
    'import androidx.media3.exoplayer.ExoPlayer\n',
    'import androidx.media3.exoplayer.ExoPlayer\nimport androidx.media3.exoplayer.SeekParameters\n',
    "SeekParameters import",
)
s = replace_once(
    s,
    '                repeatMode = Player.REPEAT_MODE_OFF\n',
    '''                repeatMode = Player.REPEAT_MODE_OFF
                if (
                    isRockchip &&
                    config.hardwareCompatibilityMode &&
                    config.media3RockchipMode == "directcodec_renderer"
                ) {
                    setSeekParameters(SeekParameters.PREVIOUS_SYNC)
                    logger.log("RKSeek", "seekParameters=PREVIOUS_SYNC")
                }
''',
    "PREVIOUS_SYNC configuration",
)
service.write_text(s)

settings = Path("app/src/main/java/com/embyplayernext/he/ui/screens/SettingsScreen.kt")
s = settings.read_text()
s = s.replace(
    '2/3 · Media3 + OMX-recreate Seek',
    '2/3 · Media3 + Sync-Seek Clock Gate',
)
s = s.replace(
    'headlineContent = { Text("EmbyPlayerNext 2.3.19") }',
    'headlineContent = { Text("EmbyPlayerNext 2.3.20") }',
)
settings.write_text(s)
