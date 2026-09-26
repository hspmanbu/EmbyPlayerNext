from pathlib import Path

def replace_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{label}: expected 1 match, found {count}")
    return text.replace(old, new, 1)

build = Path("app/build.gradle.kts")
s = build.read_text()
s = replace_once(s, 'versionCode = 137', 'versionCode = 138', "versionCode")
s = replace_once(s, 'versionName = "2.3.14"', 'versionName = "2.3.15"', "versionName")
build.write_text(s)

settings = Path("app/src/main/java/com/embyplayernext/he/ui/screens/SettingsScreen.kt")
s = settings.read_text()
start = s.find('                    SettingRow(\n                        "五组对照模式",')
if start < 0:
    raise SystemExit("five-way settings start not found")
end_marker = '                    SwitchRow(\n                        "硬件加速解码",'
end = s.find(end_marker, start)
if end < 0:
    raise SystemExit("five-way settings end not found")
block = '''                    SettingRow(
                        "五组对照模式",
                        when {
                            config.playbackEngine.equals("media3", true) && config.media3RockchipMode == "default" -> "1/5 · Media3 默认参数"
                            config.playbackEngine.equals("media3", true) && config.media3RockchipMode == "no_operating_rate" -> "2/5 · Media3 无 operating-rate"
                            config.playbackEngine.equals("media3", true) && config.media3RockchipMode == "dangbei_minimal" -> "3/5 · Media3 当贝最小参数 · fresh codec"
                            config.playbackEngine.equals("libvlc", true) -> "4/5 · LibVLC Direct · Seek 完整重建播放器"
                            config.playbackEngine.equals("directcodec", true) -> "5/5 · DirectCodec · Dangbei-like · 仅视频诊断"
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
                            config.playbackEngine.equals("libvlc", true) ->
                                config.copy(playbackEngine = "directcodec", media3RockchipMode = "default", vlcHardwareMode = "direct", hardwareCompatibilityMode = true)
                            else ->
                                config.copy(playbackEngine = "media3", media3RockchipMode = "default", vlcHardwareMode = "direct", hardwareCompatibilityMode = true)
                        }
                        onUpdate(next)
                    }
                    HorizontalDivider()
'''
s = s[:start] + block + s[end:]
s = s.replace(
    'if (config.playbackEngine.equals("libvlc", true)) "LibVLC · 独立 VLC/FFmpeg + MediaCodec 路径" else "Media3 / ExoPlayer · 当前默认路径"',
    'when { config.playbackEngine.equals("libvlc", true) -> "LibVLC · VLC/FFmpeg + MediaCodec"; config.playbackEngine.equals("directcodec", true) -> "DirectCodec · Dangbei-like 视频诊断"; else -> "Media3 / ExoPlayer" }',
)
s = s.replace('headlineContent = { Text("EmbyPlayerNext 2.3.14") }', 'headlineContent = { Text("EmbyPlayerNext 2.3.15") }')
settings.write_text(s)

player = Path("app/src/main/java/com/embyplayernext/he/ui/screens/PlayerScreen.kt")
s = player.read_text()
libvlc_marker = '    if (config.playbackEngine.equals("libvlc", true)) {\n'
idx = s.find(libvlc_marker)
if idx < 0:
    raise SystemExit("LibVLC routing marker not found")
direct_branch = '''    if (config.playbackEngine.equals("directcodec", true)) {
        DirectCodecDiagnosticScreen(
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
s = s[:idx] + direct_branch + s[idx:]
s = s.replace(
    '"播放遇到问题！本地硬解与服务器转码均未成功"',
    '"RK HEVC 直播放检测到视频停帧；诊断模式已禁止自动服务器转码"',
)
player.write_text(s)