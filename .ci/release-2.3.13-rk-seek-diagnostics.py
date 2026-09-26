from pathlib import Path

def replace_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{label}: expected 1 match, found {count}")
    return text.replace(old, new, 1)

build = Path("app/build.gradle.kts")
s = build.read_text()
s = replace_once(s, 'versionCode = 135', 'versionCode = 136', "versionCode")
s = replace_once(s, 'versionName = "2.3.12"', 'versionName = "2.3.13"', "versionName")
build.write_text(s)

vm = Path("app/src/main/java/com/embyplayernext/he/ui/EmbyViewModel.kt")
s = vm.read_text()
needle = "    suspend fun recoverPlayback(d: PlaybackDescriptor, positionMs: Long): PlaybackDescriptor? {\n"
guard = '''    suspend fun recoverPlayback(d: PlaybackDescriptor, positionMs: Long): PlaybackDescriptor? {
        val recoveryConfig = prefs.config.value
        val recoveryRockchip = listOf(
            android.os.Build.MANUFACTURER,
            android.os.Build.BRAND,
            android.os.Build.HARDWARE,
            android.os.Build.BOARD,
            android.os.Build.DEVICE,
            android.os.Build.PRODUCT,
        ).any { value ->
            value.contains("rockchip", ignoreCase = true) ||
                value.contains("rk35", ignoreCase = true) ||
                value.contains("rk30", ignoreCase = true)
        }
        val recoveryHevc = d.item.mediaStreams.any { stream ->
            stream.type.equals("Video", ignoreCase = true) &&
                stream.codec.orEmpty().let { codec ->
                    codec.equals("hevc", ignoreCase = true) || codec.equals("h265", ignoreCase = true)
                }
        }
        if (
            recoveryConfig.hardwareCompatibilityMode &&
            recoveryRockchip &&
            recoveryHevc &&
            d.playMethod.equals("DirectPlay", ignoreCase = true)
        ) {
            logger.log(
                "PlaybackRecovery",
                "suppressed=rk-hevc-diagnostic item=${d.item.id} positionMs=$positionMs method=${d.playMethod} hwCompat=true",
            )
            return null
        }
'''
if needle not in s:
    raise SystemExit("recoverPlayback signature not found")
s = s.replace(needle, guard, 1)
vm.write_text(s)

settings = Path("app/src/main/java/com/embyplayernext/he/ui/screens/SettingsScreen.kt")
s = settings.read_text().replace('headlineContent = { Text("EmbyPlayerNext 2.3.12") }', 'headlineContent = { Text("EmbyPlayerNext 2.3.13") }')
settings.write_text(s)