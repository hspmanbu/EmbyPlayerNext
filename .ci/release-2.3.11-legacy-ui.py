from pathlib import Path

def replace_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{label}: expected 1 match, found {count}")
    return text.replace(old, new, 1)

build = Path("app/build.gradle.kts")
s = build.read_text()
s = replace_once(s, 'versionCode = 133', 'versionCode = 134', "versionCode")
s = replace_once(s, 'versionName = "2.3.10"', 'versionName = "2.3.11"', "versionName")
build.write_text(s)

player = Path("app/src/main/java/com/embyplayernext/he/ui/screens/PlayerScreen.kt")
s = player.read_text()
start = s.find("        SharedPlayerControls(")
if start < 0: raise SystemExit("shared controls call not found")
end = s.find("        error?.let", start)
if end < 0: raise SystemExit("shared controls end not found")
block = s[start:end]
visible_name = "controlsVisible" if "visible = controlsVisible" in block else "controls"
block = replace_once(block, "            forwardSeconds = config.forwardSeconds,\n", "            forwardSeconds = config.forwardSeconds,\n            gestureSeekSeconds = config.gestureSeekSeconds,\n", "gestureSeekSeconds")
needle = "            onAspect = { aspect = AspectMode.entries[(aspect.ordinal + 1) % AspectMode.entries.size] },\n"
block = replace_once(block, needle, f"            onSetVisible = {{ {visible_name} = it }},\n            surfaceGesturesEnabled = false,\n" + needle, "legacy controls visibility")
s = s[:start] + block + s[end:]
player.write_text(s)

settings = Path("app/src/main/java/com/embyplayernext/he/ui/screens/SettingsScreen.kt")
s = settings.read_text().replace('headlineContent = { Text("EmbyPlayerNext 2.3.10") }', 'headlineContent = { Text("EmbyPlayerNext 2.3.11") }')
settings.write_text(s)