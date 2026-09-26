from pathlib import Path

def replace_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{label}: expected 1 match, found {count}")
    return text.replace(old, new, 1)

build = Path("app/build.gradle.kts")
s = build.read_text()
s = replace_once(s, 'versionCode = 136', 'versionCode = 137', "versionCode")
s = replace_once(s, 'versionName = "2.3.13"', 'versionName = "2.3.14"', "versionName")
build.write_text(s)

models = Path("app/src/main/java/com/embyplayernext/he/data/model/Models.kt")
s = models.read_text()
s = replace_once(
    s,
    '    val playbackEngine: String = "media3",\n',
    '    val playbackEngine: String = "media3",\n    val vlcHardwareMode: String = "direct",\n    val media3RockchipMode: String = "default",\n',
    "experiment model fields",
)
models.write_text(s)

prefs = Path("app/src/main/java/com/embyplayernext/he/data/prefs/AppPreferences.kt")
s = prefs.read_text()
s = replace_once(
    s,
    '            playbackEngine = prefs.getString("playback_engine", "media3") ?: "media3",\n',
    '            playbackEngine = prefs.getString("playback_engine", "media3") ?: "media3",\n            vlcHardwareMode = prefs.getString("vlc_hardware_mode", "direct") ?: "direct",\n            media3RockchipMode = prefs.getString("media3_rk_mode", "default") ?: "default",\n',
    "experiment prefs read",
)
s = replace_once(
    s,
    '            .putString("playback_engine", c.playbackEngine)\n',
    '            .putString("playback_engine", c.playbackEngine)\n            .putString("vlc_hardware_mode", c.vlcHardwareMode)\n            .putString("media3_rk_mode", c.media3RockchipMode)\n',
    "experiment prefs write",
)
prefs.write_text(s)

settings = Path("app/src/main/java/com/embyplayernext/he/ui/screens/SettingsScreen.kt")
s = settings.read_text()
marker = '''                    HorizontalDivider()
                    SwitchRow(
                        "硬件加速解码",
'''
block = '''                    HorizontalDivider()
                    SettingRow(
                        "五组对照模式",
                        when {
                            config.playbackEngine.equals("media3", true) && config.media3RockchipMode == "default" -> "1/5 · Media3 默认参数"
                            config.playbackEngine.equals("media3", true) && config.media3RockchipMode == "no_operating_rate" -> "2/5 · Media3 无 operating-rate"
                            config.playbackEngine.equals("media3", true) && config.media3RockchipMode == "dangbei_minimal" -> "3/5 · Media3 当贝最小参数"
                            config.playbackEngine.equals("libvlc", true) && config.vlcHardwareMode == "direct" -> "4/5 · LibVLC Direct Rendering"
                            config.playbackEngine.equals("libvlc", true) && config.vlcHardwareMode == "copy" -> "5/5 · LibVLC Copy / Decoding acceleration"
                            else -> "点击回到 1/5 · Media3 默认参数"
                        } + " · 点击切换，退出当前播放后重新进入视频生效",
                        Icons.Default.Tune,
                    ) {
                        val next = when {
                            config.playbackEngine.equals("media3", true) && config.media3RockchipMode == "default" ->
                                config.copy(playbackEngine = "media3", media3RockchipMode = "no_operating_rate", vlcHardwareMode = "direct", hardwareCompatibilityMode = true)
                            config.playbackEngine.equals("media3", true) && config.media3RockchipMode == "no_operating_rate" ->
                                config.copy(playbackEngine = "media3", media3RockchipMode = "dangbei_minimal", vlcHardwareMode = "direct", hardwareCompatibilityMode = true)
                            config.playbackEngine.equals("media3", true) && config.media3RockchipMode == "dangbei_minimal" ->
                                config.copy(playbackEngine = "libvlc", media3RockchipMode = "default", vlcHardwareMode = "direct", hardwareCompatibilityMode = true)
                            config.playbackEngine.equals("libvlc", true) && config.vlcHardwareMode == "direct" ->
                                config.copy(playbackEngine = "libvlc", media3RockchipMode = "default", vlcHardwareMode = "copy", hardwareCompatibilityMode = true)
                            else ->
                                config.copy(playbackEngine = "media3", media3RockchipMode = "default", vlcHardwareMode = "direct", hardwareCompatibilityMode = true)
                        }
                        onUpdate(next)
                    }
                    HorizontalDivider()
                    SwitchRow(
                        "硬件加速解码",
'''
s = replace_once(s, marker, block, "five-mode settings row")
s = s.replace('headlineContent = { Text("EmbyPlayerNext 2.3.13") }', 'headlineContent = { Text("EmbyPlayerNext 2.3.14") }')
settings.write_text(s)

service = Path("app/src/main/java/com/embyplayernext/he/playback/PlaybackService.kt")
s = service.read_text()
s = replace_once(
    s,
    "        val renderers = DefaultRenderersFactory(this)\n",
    "        val renderers = RockchipExperimentRenderersFactory(this, isRockchip, logger)\n",
    "experiment renderers factory",
)
service.write_text(s)