from pathlib import Path

def replace_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{label}: expected 1 match, found {count}")
    return text.replace(old, new, 1)

build = Path("app/build.gradle.kts")
s = build.read_text()
s = replace_once(s, 'versionCode = 129', 'versionCode = 130', "versionCode")
s = replace_once(s, 'versionName = "2.3.6"', 'versionName = "2.3.7"', "versionName")
dep_marker = '    implementation("androidx.media3:media3-datasource-okhttp:$media3")\n'
if 'org.videolan.android:libvlc-all' not in s:
    s = replace_once(s, dep_marker, dep_marker + '    implementation("org.videolan.android:libvlc-all:3.7.5")\n', "libvlc dependency")
build.write_text(s)

models = Path("app/src/main/java/com/embyplayernext/he/data/model/Models.kt")
s = models.read_text()
s = replace_once(
    s,
    '    val hardwareCompatibilityMode: Boolean = false,\n',
    '    val hardwareCompatibilityMode: Boolean = false,\n    val playbackEngine: String = "media3",\n',
    "playbackEngine model",
)
models.write_text(s)

prefs = Path("app/src/main/java/com/embyplayernext/he/data/prefs/AppPreferences.kt")
s = prefs.read_text()
s = replace_once(
    s,
    '            hardwareCompatibilityMode = prefs.getBoolean("hardware_compatibility_mode", false),\n',
    '            hardwareCompatibilityMode = prefs.getBoolean("hardware_compatibility_mode", false),\n            playbackEngine = prefs.getString("playback_engine", "media3") ?: "media3",\n',
    "playbackEngine prefs read",
)
s = replace_once(
    s,
    '            .putBoolean("hardware_compatibility_mode", c.hardwareCompatibilityMode)\n',
    '            .putBoolean("hardware_compatibility_mode", c.hardwareCompatibilityMode)\n            .putString("playback_engine", c.playbackEngine)\n',
    "playbackEngine prefs write",
)
prefs.write_text(s)

settings = Path("app/src/main/java/com/embyplayernext/he/ui/screens/SettingsScreen.kt")
s = settings.read_text()
needle = '                        config.hardwareDecoding,\n'
idx = s.find(needle)
if idx < 0:
    raise SystemExit("settings hardwareDecoding marker not found")
switch_idx = s.rfind('                    SwitchRow(', 0, idx)
if switch_idx < 0:
    raise SystemExit("settings SwitchRow marker not found")
engine_block = '''                    SettingRow(
                        "播放器内核",
                        if (config.playbackEngine.equals("libvlc", true)) "LibVLC · 独立 VLC/FFmpeg + MediaCodec 路径" else "Media3 / ExoPlayer · 当前默认路径",
                        Icons.Default.PlayCircle,
                    ) {
                        onUpdate(config.copy(playbackEngine = if (config.playbackEngine.equals("libvlc", true)) "media3" else "libvlc"))
                    }
                    HorizontalDivider()
'''
s = s[:switch_idx] + engine_block + s[switch_idx:]
s = s.replace('headlineContent = { Text("EmbyPlayerNext 2.3.3") }', 'headlineContent = { Text("EmbyPlayerNext 2.3.7") }')
settings.write_text(s)

player = Path("app/src/main/java/com/embyplayernext/he/ui/screens/PlayerScreen.kt")
s = player.read_text()
func_idx = s.find('fun PlayerScreen(')
if func_idx < 0:
    raise SystemExit("PlayerScreen function not found")
ctx_marker = '    val context = LocalContext.current\n'
ctx_idx = s.find(ctx_marker, func_idx)
if ctx_idx < 0:
    raise SystemExit("PlayerScreen context marker not found")
branch = '''    if (config.playbackEngine.equals("libvlc", true)) {
        LibVlcPlayerScreen(
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
s = s[:ctx_idx] + branch + s[ctx_idx:]
player.write_text(s)
