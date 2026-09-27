from pathlib import Path

def replace_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{label}: expected 1 match, found {count}")
    return text.replace(old, new, 1)

build = Path("app/build.gradle.kts")
s = build.read_text()
s = replace_once(s, 'versionCode = 144', 'versionCode = 145', "versionCode")
s = replace_once(s, 'versionName = "2.3.21"', 'versionName = "2.3.22"', "versionName")
build.write_text(s)

service = Path("app/src/main/java/com/embyplayernext/he/playback/PlaybackService.kt")
s = service.read_text()

s = replace_once(
    s,
    'config.hardwareDecoding && isRockchip && mimeType.equals("video/hevc", true) -> {',
    'config.hardwareDecoding && config.hardwareCompatibilityMode && isRockchip && mimeType.equals("video/hevc", true) -> {',
    "default Media3 codec ordering outside compatibility mode",
)

old_factory = '''        val renderers = RockchipExperimentRenderersFactory(this, isRockchip, logger)
            .setMediaCodecSelector(selector)
'''
new_factory = '''        val hybridDirectVideoEnabled =
            config.hardwareDecoding &&
                config.hardwareCompatibilityMode &&
                isRockchip &&
                config.media3RockchipMode == "directcodec_renderer"
        logger.log(
            "RKHybrid",
            "requested=$hybridDirectVideoEnabled rockchip=$isRockchip compat=${config.hardwareCompatibilityMode} hw=${config.hardwareDecoding} mode=${config.media3RockchipMode}",
        )
        val renderers = RockchipHybridRenderersFactory(
            context = this,
            isRockchip = isRockchip,
            compatibilityEnabled = config.hardwareCompatibilityMode,
            enableHybridDirectVideo = hybridDirectVideoEnabled,
            logger = logger,
        )
            .setMediaCodecSelector(selector)
'''
s = replace_once(s, old_factory, new_factory, "hybrid renderer factory")
service.write_text(s)

settings = Path("app/src/main/java/com/embyplayernext/he/ui/screens/SettingsScreen.kt")
s = settings.read_text()
s = s.replace(
    '2/3 · Media3 + No-Keyframe-Drop',
    '2/3 · Media3音频/字幕 + Direct OMX视频',
)
s = s.replace(
    'headlineContent = { Text("EmbyPlayerNext 2.3.21") }',
    'headlineContent = { Text("EmbyPlayerNext 2.3.22") }',
)
settings.write_text(s)
