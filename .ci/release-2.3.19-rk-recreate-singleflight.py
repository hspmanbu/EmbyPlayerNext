from pathlib import Path

def replace_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{label}: expected 1 match, found {count}")
    return text.replace(old, new, 1)

build = Path("app/build.gradle.kts")
s = build.read_text()
s = replace_once(s, 'versionCode = 141', 'versionCode = 142', "versionCode")
s = replace_once(s, 'versionName = "2.3.18"', 'versionName = "2.3.19"', "versionName")
build.write_text(s)

settings = Path("app/src/main/java/com/embyplayernext/he/ui/screens/SettingsScreen.kt")
s = settings.read_text()
s = s.replace(
    '2/3 · Media3 + Seek-generation Renderer',
    '2/3 · Media3 + OMX-recreate Seek',
)
s = s.replace(
    'headlineContent = { Text("EmbyPlayerNext 2.3.18") }',
    'headlineContent = { Text("EmbyPlayerNext 2.3.19") }',
)
settings.write_text(s)
