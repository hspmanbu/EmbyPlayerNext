from pathlib import Path

def replace_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{label}: expected 1 match, found {count}")
    return text.replace(old, new, 1)

build = Path("app/build.gradle.kts")
s = build.read_text()
s = replace_once(s, 'versionCode = 138', 'versionCode = 139', "versionCode")
s = replace_once(s, 'versionName = "2.3.15"', 'versionName = "2.3.16"', "versionName")
build.write_text(s)

settings = Path("app/src/main/java/com/embyplayernext/he/ui/screens/SettingsScreen.kt")
s = settings.read_text()
s = s.replace(
    '"5/5 · DirectCodec · Dangbei-like · 仅视频诊断"',
    '"5/5 · DirectCodec · Dangbei-like · 稳定性/Seek/倍速诊断"',
)
s = s.replace(
    'headlineContent = { Text("EmbyPlayerNext 2.3.15") }',
    'headlineContent = { Text("EmbyPlayerNext 2.3.16") }',
)
settings.write_text(s)