from pathlib import Path

def replace_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{label}: expected 1 match, found {count}")
    return text.replace(old, new, 1)

build = Path("app/build.gradle.kts")
s = build.read_text()
s = replace_once(s, 'versionCode = 143', 'versionCode = 144', "versionCode")
s = replace_once(s, 'versionName = "2.3.20"', 'versionName = "2.3.21"', "versionName")
build.write_text(s)

settings = Path("app/src/main/java/com/embyplayernext/he/ui/screens/SettingsScreen.kt")
s = settings.read_text()
s = s.replace(
    '2/3 · Media3 + Sync-Seek Clock Gate',
    '2/3 · Media3 + No-Keyframe-Drop',
)
s = s.replace(
    'headlineContent = { Text("EmbyPlayerNext 2.3.20") }',
    'headlineContent = { Text("EmbyPlayerNext 2.3.21") }',
)
settings.write_text(s)
