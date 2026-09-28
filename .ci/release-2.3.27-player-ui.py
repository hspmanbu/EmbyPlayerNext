from pathlib import Path

ROOT = Path('.')

def replace_once(path: Path, old: str, new: str, label: str):
    s = path.read_text()
    n = s.count(old)
    if n != 1:
        raise SystemExit(f'{label}: expected 1 match, found {n}')
    path.write_text(s.replace(old, new, 1))

build = ROOT/'app/build.gradle.kts'
replace_once(build, 'versionCode = 149', 'versionCode = 150', 'versionCode')
replace_once(build, 'versionName = "2.3.26"', 'versionName = "2.3.27"', 'versionName')

settings = ROOT/'app/src/main/java/com/embyplayernext/he/ui/screens/SettingsScreen.kt'
replace_once(settings, 'EmbyPlayerNext 2.3.26', 'EmbyPlayerNext 2.3.27', 'settings version')

api = ROOT/'app/src/main/java/com/embyplayernext/he/data/network/EmbyApiClient.kt'
replace_once(api, 'Version=\\"2.3.26\\"', 'Version=\\"2.3.27\\"', 'api version')
replace_once(api, 'EmbyPlayerNext/2.3.26 Android', 'EmbyPlayerNext/2.3.27 Android', 'api ua')
service = ROOT/'app/src/main/java/com/embyplayernext/he/playback/PlaybackService.kt'
replace_once(service, 'setUserAgent("EmbyPlayerNext/2.3.26")', 'setUserAgent("EmbyPlayerNext/2.3.27")', 'playback ua')

player = ROOT/'app/src/main/java/com/embyplayernext/he/ui/screens/PlayerScreen.kt'
s = player.read_text()

def prepl(old, new, label):
    global s
    n=s.count(old)
    if n != 1:
        raise SystemExit(f'{label}: expected 1 match, found {n}')
    s=s.replace(old,new,1)

prepl('import androidx.compose.foundation.horizontalScroll\n', 'import androidx.compose.foundation.horizontalScroll\nimport androidx.compose.foundation.interaction.MutableInteractionSource\nimport androidx.compose.foundation.interaction.collectIsPressedAsState\n', 'interaction imports')
prepl('                            markPlayerInteraction()\n                        },\n                        onDrag = { change, drag ->', '                            markPlayerInteraction(showControls = false)\n                        },\n                        onDrag = { change, drag ->', 'gesture down no controls')
prepl('                                if (mode != GestureMode.NONE) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)\n', '                                if (mode != GestureMode.NONE) {\n                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)\n                                    controlsVisible = false\n                                    markPlayerInteraction(showControls = false)\n                                }\n', 'gesture mode hide controls')
prepl('                                    markPlayerInteraction()\n                                }\n                            }\n                        },\n                    )', '                                    markPlayerInteraction(showControls = false)\n                                }\n                            }\n                        },\n                    )', 'gesture seek end no controls')
prepl('                                markPlayerInteraction()\n                            }\n                        },\n                    )\n                },', '                                controlsVisible = false\n                                markPlayerInteraction(showControls = false)\n                            }\n                        },\n                    )\n                },', 'double tap no controls')
prepl('            delay(850)\n', '            delay(1000)\n', 'hud duration')

old_hud_call='''        gestureHud?.let { hud ->
            GestureHudCard(
                hud = hud,
                modifier = Modifier.align(Alignment.Center).padding(horizontal = 24.dp),
            )
        }
'''
new_hud_call='''        gestureHud?.let { hud ->
            when (hud.title) {
                "亮度" -> GestureEdgeBar(
                    hud = hud,
                    modifier = Modifier.align(Alignment.CenterStart).padding(start = 18.dp),
                )
                "音量" -> GestureEdgeBar(
                    hud = hud,
                    modifier = Modifier.align(Alignment.CenterEnd).padding(end = 18.dp),
                )
                else -> GestureSeekHint(
                    hud = hud,
                    modifier = Modifier.align(Alignment.Center).offset(y = (-56).dp),
                )
            }
        }
'''
prepl(old_hud_call,new_hud_call,'hud overlay layout')

start=s.index('@Composable\nfun PlayerCenterControls(')
end=s.index('@Composable\nfun PlayerBottomChrome(', start)
if start<0 or end<0:
    raise SystemExit('center controls bounds')
new_center='''@Composable
fun PlayerCenterControls(
    compact: Boolean,
    locked: Boolean,
    isPlaying: Boolean,
    previousEnabled: Boolean,
    nextEnabled: Boolean,
    onToggleLock: () -> Unit,
    onPrevious: () -> Unit,
    onRewind: () -> Unit,
    onTogglePlay: () -> Unit,
    onForward: () -> Unit,
    onNext: () -> Unit,
    playFocusRequester: FocusRequester,
    remoteMode: Boolean,
    modifier: Modifier = Modifier,
) {
    val small = if (remoteMode) 54.dp else 48.dp
    val side = if (remoteMode) 62.dp else if (compact) 52.dp else 56.dp
    val play = if (remoteMode) 82.dp else if (compact) 68.dp else 74.dp
    Box(modifier.fillMaxWidth().padding(horizontal = if (compact) 14.dp else 24.dp)) {
        PlayerRoundIcon(
            if (locked) Icons.Default.Lock else Icons.Default.LockOpen,
            if (locked) "解锁" else "锁定",
            onToggleLock,
            size = small,
            modifier = Modifier.align(Alignment.CenterStart),
        )
        if (!locked) {
            Row(
                modifier = Modifier.align(Alignment.Center),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                if (previousEnabled) {
                    PlayerRoundIcon(Icons.Default.SkipPrevious, "上一集", onPrevious, size = small)
                    Spacer(Modifier.width(if (compact) 8.dp else 14.dp))
                }
                PlayerRoundIcon(Icons.Default.FastRewind, "后退", onRewind, size = side)
                Spacer(Modifier.width(if (compact) 12.dp else 18.dp))
                PlayerRoundIcon(
                    if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    if (isPlaying) "暂停" else "播放",
                    onTogglePlay,
                    size = play,
                    modifier = Modifier.focusRequester(playFocusRequester),
                )
                Spacer(Modifier.width(if (compact) 12.dp else 18.dp))
                PlayerRoundIcon(Icons.Default.FastForward, "快进", onForward, size = side)
                if (nextEnabled) {
                    Spacer(Modifier.width(if (compact) 8.dp else 14.dp))
                    PlayerRoundIcon(Icons.Default.SkipNext, "下一集", onNext, size = small)
                }
            }
        }
    }
}

'''
s=s[:start]+new_center+s[end:]

old_quick='''@Composable
private fun PlayerQuickIcon(icon: ImageVector, description: String, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Surface(
        onClick = onClick,
        modifier = Modifier
            .size(42.dp)
            .onFocusChanged { focused = it.isFocused }
            .scale(if (focused) 1.12f else 1f)
            .border(if (focused) 3.dp else 0.dp, MaterialTheme.colorScheme.primary, CircleShape),
        shape = CircleShape,
        color = Color.White.copy(alpha = if (focused) .20f else .08f),
    ) {
        Icon(icon, description, tint = Color.White, modifier = Modifier.padding(10.dp))
    }
}
'''
new_quick='''@Composable
private fun PlayerQuickIcon(icon: ImageVector, description: String, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val active = focused || pressed
    Surface(
        onClick = onClick,
        interactionSource = interactionSource,
        modifier = Modifier
            .size(48.dp)
            .onFocusChanged { focused = it.isFocused }
            .scale(if (focused) 1.06f else if (pressed) .94f else 1f),
        shape = CircleShape,
        color = if (active) Color.White.copy(alpha = .18f) else Color.Transparent,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, description, tint = Color.White.copy(alpha = if (active) 1f else .92f), modifier = Modifier.size(24.dp))
        }
    }
}
'''
prepl(old_quick,new_quick,'quick icon style')

start=s.index('@Composable\nprivate fun PlayerRoundIcon(')
end=s.index('@Composable\nfun GestureHudCard(', start)
if start<0 or end<0:
    raise SystemExit('round/hud bounds')
new_round_hud='''@Composable
private fun PlayerRoundIcon(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
    size: Dp = 48.dp,
    modifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val active = focused || pressed
    Surface(
        modifier = modifier
            .size(size)
            .onFocusChanged { focused = it.isFocused }
            .scale(if (focused) 1.06f else if (pressed) .94f else 1f),
        shape = CircleShape,
        color = if (active) Color.White.copy(alpha = .18f) else Color.Transparent,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        interactionSource = interactionSource,
        onClick = onClick,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                icon,
                description,
                tint = Color.White.copy(alpha = if (active) 1f else .94f),
                modifier = Modifier.size(size * .48f),
            )
        }
    }
}

@Composable
private fun GestureEdgeBar(hud: GestureHud, modifier: Modifier = Modifier) {
    val progress = (hud.progress ?: 0f).coerceIn(0f, 1f)
    Surface(
        modifier = modifier.width(46.dp),
        shape = RoundedCornerShape(23.dp),
        color = Color.Black.copy(alpha = .46f),
        tonalElevation = 0.dp,
    ) {
        Column(
            Modifier.padding(horizontal = 10.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(hud.icon, null, tint = Color.White, modifier = Modifier.size(20.dp))
            Box(
                Modifier
                    .width(6.dp)
                    .height(112.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(Color.White.copy(alpha = .22f)),
            ) {
                Box(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .fillMaxHeight(progress)
                        .background(Color.White),
                )
            }
            Text(hud.value, color = Color.White, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun GestureSeekHint(hud: GestureHud, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(999.dp),
        color = Color.Black.copy(alpha = .56f),
        tonalElevation = 0.dp,
    ) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(hud.icon, null, tint = Color.White, modifier = Modifier.size(24.dp))
            Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text(hud.title, color = Color.White, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                Text(hud.value, color = Color.White.copy(alpha = .78f), style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

'''
s=s[:start]+new_round_hud+s[end:]
old_start=s.index('@Composable\nfun GestureHudCard(', s.index('private fun GestureSeekHint'))
old_end=s.index('@Composable\nfun PlaybackEndedCard(', old_start)
s=s[:old_start]+s[old_end:]

s=s.replace('color = Color.Black.copy(alpha = .48f),', 'color = Color.Black.copy(alpha = .40f),', 1)

player.write_text(s)
