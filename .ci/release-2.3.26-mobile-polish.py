from pathlib import Path

def replace_once(path: Path, old: str, new: str, label: str):
    s = path.read_text()
    n = s.count(old)
    if n != 1:
        raise SystemExit(f"{label}: expected 1 match, found {n}")
    path.write_text(s.replace(old, new, 1))

root = Path("app")

build = root / "build.gradle.kts"
replace_once(build, 'versionCode = 148', 'versionCode = 149', 'versionCode')
replace_once(build, 'versionName = "2.3.25"', 'versionName = "2.3.26"', 'versionName')

settings = root / "src/main/java/com/embyplayernext/he/ui/screens/SettingsScreen.kt"
replace_once(settings, 'EmbyPlayerNext 2.3.25', 'EmbyPlayerNext 2.3.26', 'settings version')

api = root / "src/main/java/com/embyplayernext/he/data/network/EmbyApiClient.kt"
replace_once(api, 'Version=\\\"2.3.24\\\"', 'Version=\\\"2.3.26\\\"', 'api auth version')
replace_once(api, 'EmbyPlayerNext/2.3.24 Android', 'EmbyPlayerNext/2.3.26 Android', 'api user agent')

service = root / "src/main/java/com/embyplayernext/he/playback/PlaybackService.kt"
replace_once(service, 'setUserAgent("EmbyPlayerNext/2.3.24")', 'setUserAgent("EmbyPlayerNext/2.3.26")', 'playback user agent')

player = root / "src/main/java/com/embyplayernext/he/ui/screens/PlayerScreen.kt"
s = player.read_text()

def prepl(old: str, new: str, label: str):
    global s
    n = s.count(old)
    if n != 1:
        raise SystemExit(f"{label}: expected 1 match, found {n}")
    s = s.replace(old, new, 1)

prepl('import android.os.SystemClock\n', 'import android.os.SystemClock\nimport android.provider.Settings\n', 'Settings import')
prepl('import androidx.compose.ui.platform.LocalContext\n', 'import androidx.compose.ui.platform.LocalContext\nimport androidx.compose.ui.platform.LocalHapticFeedback\n', 'haptic local import')
prepl('import androidx.compose.ui.text.font.FontWeight\n', 'import androidx.compose.ui.text.font.FontWeight\nimport androidx.compose.ui.hapticfeedback.HapticFeedbackType\n', 'haptic type import')
prepl('    val scope = rememberCoroutineScope()\n', '    val scope = rememberCoroutineScope()\n    val haptic = LocalHapticFeedback.current\n', 'haptic state')

prepl(
'''                    var startBrightness = .5f
                    var startVolume = 0
                    val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
''',
'''                    var startBrightness = .5f
                    var startVolume = 0
                    var gestureStartPositionMs = 0L
                    val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
''',
'gesture start position state',
)

prepl(
'''                            val currentBrightness = activity?.window?.attributes?.screenBrightness ?: -1f
                            startBrightness = if (currentBrightness in 0f..1f) currentBrightness else .5f
                            startVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                            markPlayerInteraction()
''',
'''                            val currentBrightness = activity?.window?.attributes?.screenBrightness ?: -1f
                            val systemBrightness = runCatching {
                                Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS) / 255f
                            }.getOrDefault(.5f)
                            startBrightness = if (currentBrightness in 0f..1f) currentBrightness else systemBrightness.coerceIn(.05f, 1f)
                            startVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                            gestureStartPositionMs = controller?.currentPosition ?: 0L
                            markPlayerInteraction()
''',
'gesture baseline',
)

prepl(
'''                            if (mode == GestureMode.NONE && (abs(totalX) > 18f || abs(totalY) > 18f)) {
                                mode = if (abs(totalX) >= abs(totalY)) GestureMode.SEEK
                                else if (!verticalGestureAllowed) GestureMode.NONE
                                else if (startX < size.width / 2f) GestureMode.BRIGHTNESS else GestureMode.VOLUME
                            }
''',
'''                            if (mode == GestureMode.NONE && (abs(totalX) > 18f || abs(totalY) > 18f)) {
                                mode = if (abs(totalX) >= abs(totalY)) GestureMode.SEEK
                                else if (!verticalGestureAllowed) GestureMode.NONE
                                else if (startX < size.width / 2f) GestureMode.BRIGHTNESS else GestureMode.VOLUME
                                if (mode != GestureMode.NONE) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            }
''',
'gesture haptic',
)

prepl(
'''                                    val target = (player.currentPosition + delta).coerceIn(0L, if (duration > 0) duration else Long.MAX_VALUE)
                                    gestureHud = GestureHud(
                                        if (delta >= 0) Icons.Default.FastForward else Icons.Default.FastRewind,
                                        if (delta >= 0) "快进" else "后退",
                                        "${if (delta >= 0) "+" else ""}${delta / 1000} 秒 · ${time(target)}",
                                        if (duration > 0) target.toFloat() / duration else null,
                                    )
''',
'''                                    val target = (gestureStartPositionMs + delta).coerceIn(0L, if (duration > 0) duration else Long.MAX_VALUE)
                                    val seconds = abs(delta / 1000)
                                    gestureHud = GestureHud(
                                        if (delta >= 0) Icons.Default.FastForward else Icons.Default.FastRewind,
                                        if (delta >= 0) "快进 ${seconds} 秒" else "后退 ${seconds} 秒",
                                        if (duration > 0) "${time(target)} / ${time(duration)}" else time(target),
                                        if (duration > 0) target.toFloat() / duration else null,
                                    )
''',
'seek HUD preview',
)

prepl(
'''                                    val delta = (totalX / size.width * config.gestureSeekSeconds * 1000).toLong()
                                    val duration = player.duration.takeIf { it > 0 && it != C.TIME_UNSET } ?: durationMs ?: Long.MAX_VALUE
                                    player.seekTo((player.currentPosition + delta).coerceIn(0L, duration))
                                    markPlayerInteraction()
''',
'''                                    val delta = (totalX / size.width * config.gestureSeekSeconds * 1000).toLong()
                                    val duration = player.duration.takeIf { it > 0 && it != C.TIME_UNSET } ?: durationMs ?: Long.MAX_VALUE
                                    player.seekTo((gestureStartPositionMs + delta).coerceIn(0L, duration))
                                    markPlayerInteraction()
''',
'seek commit baseline',
)

prepl(
'''                                player.seekTo((player.currentPosition + seconds * 1000L).coerceIn(0L, duration))
                                gestureHud = GestureHud(
                                    if (seconds < 0) Icons.Default.Replay10 else Icons.Default.Forward30,
                                    if (seconds < 0) "后退" else "快进",
                                    "${abs(seconds)} 秒",
                                )
''',
'''                                val target = (player.currentPosition + seconds * 1000L).coerceIn(0L, duration)
                                player.seekTo(target)
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                gestureHud = GestureHud(
                                    if (seconds < 0) Icons.Default.Replay10 else Icons.Default.Forward30,
                                    if (seconds < 0) "后退 ${abs(seconds)} 秒" else "快进 ${abs(seconds)} 秒",
                                    if (duration != Long.MAX_VALUE) "${time(target)} / ${time(duration)}" else time(target),
                                    if (duration != Long.MAX_VALUE && duration > 0) target.toFloat() / duration else null,
                                )
''',
'double tap HUD',
)

prepl(
'''        if (controlsVisible || locked) {
''',
'''        gestureHud?.let { hud ->
            GestureHudCard(
                hud = hud,
                modifier = Modifier.align(Alignment.Center).padding(horizontal = 24.dp),
            )
        }

        if (controlsVisible || locked) {
''',
'HUD composition',
)

prepl(
'Modifier.widthIn(min = 170.dp, max = 260.dp).padding(horizontal = 22.dp, vertical = 16.dp)',
'Modifier.widthIn(min = 190.dp, max = 320.dp).padding(horizontal = 24.dp, vertical = 18.dp)',
'HUD size',
)
prepl(
'Icon(hud.icon, null, tint = Color.White, modifier = Modifier.size(30.dp))',
'Icon(hud.icon, null, tint = Color.White, modifier = Modifier.size(34.dp))',
'HUD icon',
)
prepl(
'hud.progress?.let { LinearProgressIndicator(progress = { it.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth().height(3.dp)) }',
'hud.progress?.let { LinearProgressIndicator(progress = { it.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth().height(4.dp)) }',
'HUD progress',
)
player.write_text(s)

cards = root / "src/main/java/com/embyplayernext/he/ui/components/MediaCards.kt"
s = cards.read_text()
if 'import androidx.compose.ui.text.style.TextAlign\n' not in s:
    s = s.replace('import androidx.compose.ui.text.style.TextOverflow\n', 'import androidx.compose.ui.text.style.TextOverflow\nimport androidx.compose.ui.text.style.TextAlign\n', 1)
s = s.replace(
'''        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
''',
'''        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
''',
1,
)
old = '''        Text(
            item.name,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold
        )
        subtitle(item).takeIf { it.isNotBlank() }?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
'''
new = '''        Text(
            item.name,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            minLines = 2,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
        )
        subtitle(item).takeIf { it.isNotBlank() }?.let {
            Text(
                it,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                minLines = 1,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
        }
'''
if s.count(old) != 1:
    raise SystemExit(f"media card text block: expected 1 match, found {s.count(old)}")
cards.write_text(s.replace(old, new, 1))
