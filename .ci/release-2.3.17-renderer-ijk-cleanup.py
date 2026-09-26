from pathlib import Path

def replace_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{label}: expected 1 match, found {count}")
    return text.replace(old, new, 1)

build = Path("app/build.gradle.kts")
s = build.read_text()
s = replace_once(s, 'versionCode = 139', 'versionCode = 140', "versionCode")
s = replace_once(s, 'versionName = "2.3.16"', 'versionName = "2.3.17"', "versionName")
s = s.replace('    implementation("org.videolan.android:libvlc-all:3.6.5")\n', '')
media_marker = '    implementation("androidx.media3:media3-datasource-okhttp:$media3")\n'
ijk_deps = (
    '    implementation("io.github.superjianpang:ijkplayer-java:0.0.2")\n'
    '    implementation("io.github.superjianpang:ijkplayer-arm64:0.0.2")\n'
)
if "ijkplayer-java" not in s:
    s = replace_once(s, media_marker, media_marker + ijk_deps, "ijk dependencies")
build.write_text(s)

models = Path("app/src/main/java/com/embyplayernext/he/data/model/Models.kt")
s = models.read_text()
s = s.replace('    val vlcHardwareMode: String = "direct",\n', '')
models.write_text(s)

prefs = Path("app/src/main/java/com/embyplayernext/he/data/prefs/AppPreferences.kt")
s = prefs.read_text()
s = s.replace('            vlcHardwareMode = prefs.getString("vlc_hardware_mode", "direct") ?: "direct",\n', '')
s = s.replace('            .putString("vlc_hardware_mode", c.vlcHardwareMode)\n', '')
prefs.write_text(s)

settings = Path("app/src/main/java/com/embyplayernext/he/ui/screens/SettingsScreen.kt")
s = settings.read_text()

engine_start = s.find('                    SettingRow(\n                        "播放器内核",')
if engine_start >= 0:
    divider = '                    HorizontalDivider()\n'
    engine_end = s.find(divider, engine_start)
    if engine_end < 0:
        raise SystemExit("player engine divider not found")
    s = s[:engine_start] + s[engine_end + len(divider):]

start = s.find('                    SettingRow(\n                        "五组对照模式",')
if start < 0:
    raise SystemExit("five-way settings start not found")
end_marker = '                    SwitchRow(\n                        "硬件加速解码",'
end = s.find(end_marker, start)
if end < 0:
    raise SystemExit("five-way settings end not found")
block = '''                    SettingRow(
                        "三路解码验证",
                        when {
                            config.playbackEngine.equals("directcodec", true) -> "1/3 · DirectCodec 基线 · Seek/倍速"
                            config.playbackEngine.equals("media3", true) && config.media3RockchipMode == "directcodec_renderer" -> "2/3 · Media3 + DirectCodec-style Renderer"
                            config.playbackEngine.equals("ijk", true) -> "3/3 · ijkplayer + MediaCodec/OMX"
                            else -> "点击进入 1/3 · DirectCodec 基线"
                        } + " · 点击切换，退出播放后重新进入生效",
                        Icons.Default.Tune,
                    ) {
                        val next = when {
                            config.playbackEngine.equals("directcodec", true) ->
                                config.copy(
                                    playbackEngine = "media3",
                                    media3RockchipMode = "directcodec_renderer",
                                    hardwareCompatibilityMode = true,
                                )
                            config.playbackEngine.equals("media3", true) && config.media3RockchipMode == "directcodec_renderer" ->
                                config.copy(
                                    playbackEngine = "ijk",
                                    media3RockchipMode = "default",
                                    hardwareCompatibilityMode = true,
                                )
                            else ->
                                config.copy(
                                    playbackEngine = "directcodec",
                                    media3RockchipMode = "default",
                                    hardwareCompatibilityMode = true,
                                )
                        }
                        onUpdate(next)
                    }
                    HorizontalDivider()
'''
s = s[:start] + block + s[end:]
s = s.replace('headlineContent = { Text("EmbyPlayerNext 2.3.16") }', 'headlineContent = { Text("EmbyPlayerNext 2.3.17") }')
settings.write_text(s)

player = Path("app/src/main/java/com/embyplayernext/he/ui/screens/PlayerScreen.kt")
s = player.read_text()
lib_start = s.find('    if (config.playbackEngine.equals("libvlc", true)) {\n')
context_marker = '    val context = LocalContext.current\n'
if lib_start < 0:
    raise SystemExit("LibVLC routing branch not found")
lib_end = s.find(context_marker, lib_start)
if lib_end < 0:
    raise SystemExit("PlayerScreen context marker not found")
ijk_branch = '''    if (config.playbackEngine.equals("ijk", true)) {
        IjkPlayerDiagnosticScreen(
            descriptor = descriptor,
            config = config,
            onStart = onStart,
            onProgress = onProgress,
            onStopped = onStopped,
            onExit = onExit,
        )
        return
    }

'''
s = s[:lib_start] + ijk_branch + s[lib_end:]
player.write_text(s)

libvlc = Path("app/src/main/java/com/embyplayernext/he/ui/screens/LibVlcPlayerScreen.kt")
if libvlc.exists():
    libvlc.unlink()
