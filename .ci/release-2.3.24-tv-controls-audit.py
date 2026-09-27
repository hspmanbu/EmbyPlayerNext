from pathlib import Path

root = Path('app')

def replace_once(path: Path, old: str, new: str, label: str):
    s = path.read_text()
    count = s.count(old)
    if count != 1:
        raise SystemExit(f'{label}: expected 1 match, found {count}')
    path.write_text(s.replace(old, new, 1))

build = root / 'build.gradle.kts'
replace_once(build, 'versionCode = 146', 'versionCode = 147', 'versionCode')
replace_once(build, 'versionName = "2.3.23"', 'versionName = "2.3.24"', 'versionName')

api = root / 'src/main/java/com/embyplayernext/he/data/network/EmbyApiClient.kt'
replace_once(api, 'Version=\\\"2.3.4\\\"', 'Version=\\\"2.3.24\\\"', 'auth version')
replace_once(api, 'EmbyPlayerNext/2.3.4 Android', 'EmbyPlayerNext/2.3.24 Android', 'api user agent')

service = root / 'src/main/java/com/embyplayernext/he/playback/PlaybackService.kt'
replace_once(service, '.setUserAgent("EmbyPlayerNext/2.3.4")', '.setUserAgent("EmbyPlayerNext/2.3.24")', 'playback user agent')
replace_once(
    service,
    '''        val trackSelector = DefaultTrackSelector(this).apply {
            parameters = buildUponParameters()
                .setExceedRendererCapabilitiesIfNecessary(true)
                .setExceedVideoConstraintsIfNecessary(true)
                .setTunnelingEnabled(false)
                .build()
        }
        logger.log("Codec", "trackSelector exceedRendererCapabilities=true exceedVideoConstraints=true tunneling=false")
''',
    '''        val trackSelector = DefaultTrackSelector(this).apply {
            if (hybridDirectVideoEnabled) {
                parameters = buildUponParameters()
                    .setExceedRendererCapabilitiesIfNecessary(true)
                    .setExceedVideoConstraintsIfNecessary(true)
                    .setTunnelingEnabled(false)
                    .build()
            }
        }
        logger.log(
            "Codec",
            if (hybridDirectVideoEnabled)
                "trackSelector hybridCompat=true exceedRendererCapabilities=true exceedVideoConstraints=true tunneling=false"
            else
                "trackSelector hybridCompat=false media3-defaults=true",
        )
''',
    'track selector defaults',
)

player = root / 'src/main/java/com/embyplayernext/he/ui/screens/PlayerScreen.kt'
s = player.read_text()

def prepl(old: str, new: str, label: str):
    global s
    count = s.count(old)
    if count != 1:
        raise SystemExit(f'{label}: expected 1 match, found {count}')
    s = s.replace(old, new, 1)

prepl(
    '    var remoteActivityTick by remember { mutableLongStateOf(0L) }\n',
    '    var remoteActivityTick by remember { mutableLongStateOf(0L) }\n    var remoteSeekDirection by remember { mutableIntStateOf(0) }\n    var lastRemoteSeekRealtimeMs by remember { mutableLongStateOf(0L) }\n',
    'remote seek state',
)
prepl(
    '        val startedAt = SystemClock.elapsedRealtime()\n        var lastHeartbeat = 0L\n',
    '        val startedAt = SystemClock.elapsedRealtime()\n        var noFirstFrameClockStart = startedAt\n        var lastHeartbeat = 0L\n',
    'watchdog clock',
)
prepl(
    '''            val noFirstFrameTimeout = if (recoveryAttempt == 0) 7_000L else 9_000L
            val noFirstFrame = !renderedFirstFrame && elapsed >= noFirstFrameTimeout
''',
    '''            if (!player.playWhenReady) {
                noFirstFrameClockStart = now
            }
            val noFirstFrameTimeout = if (recoveryAttempt == 0) 7_000L else 9_000L
            val noFirstFrame = !renderedFirstFrame && player.playWhenReady && now - noFirstFrameClockStart >= noFirstFrameTimeout
''',
    'watchdog pause guard',
)
prepl('    fun toggleOrientation() {\n', '    fun revealControls() {\n        controlsVisible = true\n        remoteActivityTick += 1L\n    }\n\n    fun toggleOrientation() {\n', 'reveal helper')
prepl('        controlsVisible = true\n    }\n\n    BackHandler {\n', '        revealControls()\n    }\n\n    BackHandler {\n', 'orientation reveal')
prepl('            locked -> { locked = false; controlsVisible = true }\n', '            locked -> { locked = false; revealControls() }\n', 'unlock reveal')
prepl(
    '''    LaunchedEffect(controlsVisible, locked, settingsVisible, controller?.isPlaying, ended, remoteActivityTick, isTv) {
        if (controlsVisible && !locked && !settingsVisible && !ended && controller?.isPlaying == true) {
            delay(if (isTv) 7000 else 4200)
            controlsVisible = false
        }
    }
''',
    '''    LaunchedEffect(controlsVisible, locked, settingsVisible, controller?.playWhenReady, ended, remoteActivityTick, isTv) {
        if (controlsVisible && !locked && !settingsVisible && !ended && controller?.playWhenReady == true) {
            delay(if (isTv) 7000 else 4200)
            controlsVisible = false
        }
    }
''',
    'auto hide play intent',
)
old_key = '''            .onPreviewKeyEvent { keyEvent ->
                if (keyEvent.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                if (isTv) remoteActivityTick++
                when (keyEvent.key) {
                    Key.MediaPlayPause -> {
                        controller?.let { if (it.isPlaying) it.pause() else it.play() }; controlsVisible = true; true
                    }
                    Key.MediaFastForward -> {
                        controller?.let { p -> p.seekTo((p.currentPosition + config.forwardSeconds * 1000L).coerceAtMost(p.duration.takeIf { it > 0 && it != C.TIME_UNSET } ?: Long.MAX_VALUE)) }
                        controlsVisible = true; true
                    }
                    Key.MediaRewind -> {
                        controller?.let { p -> p.seekTo((p.currentPosition - config.rewindSeconds * 1000L).coerceAtLeast(0L)) }
                        controlsVisible = true; true
                    }
                    Key.MediaNext -> { nextItem?.let(::switchTo); true }
                    Key.MediaPrevious -> { previousItem?.let(::switchTo); true }
                    Key.DirectionLeft -> {
                        if (!controlsVisible) {
                            controller?.let { p -> p.seekTo((p.currentPosition - config.rewindSeconds * 1000L).coerceAtLeast(0L)) }
                            controlsVisible = true
                            true
                        } else false
                    }
                    Key.DirectionRight -> {
                        if (!controlsVisible) {
                            controller?.let { p -> p.seekTo((p.currentPosition + config.forwardSeconds * 1000L).coerceAtMost(p.duration.takeIf { it > 0 && it != C.TIME_UNSET } ?: Long.MAX_VALUE)) }
                            controlsVisible = true
                            true
                        } else false
                    }
                    Key.DirectionUp, Key.DirectionDown, Key.Enter, Key.NumPadEnter -> {
                        if (!controlsVisible) { controlsVisible = true; true } else false
                    }
                    else -> false
                }
            },
'''
new_key = '''            .onPreviewKeyEvent { keyEvent ->
                val native = keyEvent.nativeKeyEvent
                if (keyEvent.type == KeyEventType.KeyUp) {
                    if (keyEvent.key == Key.DirectionLeft || keyEvent.key == Key.DirectionRight ||
                        keyEvent.key == Key.MediaFastForward || keyEvent.key == Key.MediaRewind
                    ) {
                        val hadRemoteSeek = remoteSeekDirection != 0
                        remoteSeekDirection = 0
                        return@onPreviewKeyEvent hadRemoteSeek
                    }
                    return@onPreviewKeyEvent false
                }
                if (keyEvent.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                if (isTv) remoteActivityTick++

                fun seekBy(direction: Int, repeatCount: Int, configuredSeconds: Int): Boolean {
                    val p = controller ?: return true
                    val now = SystemClock.elapsedRealtime()
                    if (repeatCount > 0 && now - lastRemoteSeekRealtimeMs < 140L) return true
                    lastRemoteSeekRealtimeMs = now
                    val seconds = when {
                        repeatCount == 0 -> configuredSeconds
                        repeatCount < 8 -> 2
                        repeatCount < 20 -> 5
                        else -> 10
                    }
                    val durationLimit = p.duration.takeIf { it > 0 && it != C.TIME_UNSET } ?: Long.MAX_VALUE
                    val target = (p.currentPosition + direction * seconds * 1000L).coerceIn(0L, durationLimit)
                    p.seekTo(target)
                    revealControls()
                    return true
                }

                when (keyEvent.key) {
                    Key.MediaPlayPause -> {
                        controller?.let { if (it.isPlaying) it.pause() else it.play() }
                        revealControls()
                        true
                    }
                    Key.MediaFastForward -> {
                        remoteSeekDirection = 1
                        seekBy(1, native.repeatCount, config.forwardSeconds)
                    }
                    Key.MediaRewind -> {
                        remoteSeekDirection = -1
                        seekBy(-1, native.repeatCount, config.rewindSeconds)
                    }
                    Key.MediaNext -> { nextItem?.let(::switchTo); true }
                    Key.MediaPrevious -> { previousItem?.let(::switchTo); true }
                    Key.DirectionLeft -> {
                        if (remoteSeekDirection == -1 && native.repeatCount > 0) {
                            seekBy(-1, native.repeatCount, config.rewindSeconds)
                        } else if (!controlsVisible) {
                            remoteSeekDirection = -1
                            seekBy(-1, native.repeatCount, config.rewindSeconds)
                        } else false
                    }
                    Key.DirectionRight -> {
                        if (remoteSeekDirection == 1 && native.repeatCount > 0) {
                            seekBy(1, native.repeatCount, config.forwardSeconds)
                        } else if (!controlsVisible) {
                            remoteSeekDirection = 1
                            seekBy(1, native.repeatCount, config.forwardSeconds)
                        } else false
                    }
                    Key.Enter, Key.NumPadEnter -> {
                        if (!controlsVisible && !locked && !settingsVisible) {
                            controller?.let { if (it.isPlaying) it.pause() else it.play() }
                            revealControls()
                            true
                        } else false
                    }
                    Key.DirectionUp, Key.DirectionDown -> {
                        if (!controlsVisible) { revealControls(); true } else false
                    }
                    else -> false
                }
            },
'''
prepl(old_key, new_key, 'TV key handler')
s = s.replace('onToggleLock = { locked = !locked; controlsVisible = true },', 'onToggleLock = { locked = !locked; revealControls() },')
s = s.replace('onRewind = { player?.seekTo((position - config.rewindSeconds * 1000L).coerceAtLeast(0)) },', 'onRewind = { player?.seekTo((position - config.rewindSeconds * 1000L).coerceAtLeast(0)); revealControls() },')
s = s.replace('onTogglePlay = { if (player?.isPlaying == true) player.pause() else player?.play() },', 'onTogglePlay = { if (player?.isPlaying == true) player.pause() else player?.play(); revealControls() },')
s = s.replace('onForward = { player?.seekTo((position + config.forwardSeconds * 1000L).coerceAtMost(if (duration > 0) duration else Long.MAX_VALUE)) },', 'onForward = { player?.seekTo((position + config.forwardSeconds * 1000L).coerceAtMost(if (duration > 0) duration else Long.MAX_VALUE)); revealControls() },')
s = s.replace('onScrub = { scrubFraction = it; controlsVisible = true },', 'onScrub = { scrubFraction = it; revealControls() },')
s = s.replace('onSpeed = { settingsPage = PlayerSettingsPage.SPEED; settingsVisible = true },', 'onSpeed = { settingsPage = PlayerSettingsPage.SPEED; settingsVisible = true; revealControls() },')
s = s.replace('onAudio = { settingsPage = PlayerSettingsPage.AUDIO; settingsVisible = true },', 'onAudio = { settingsPage = PlayerSettingsPage.AUDIO; settingsVisible = true; revealControls() },')
s = s.replace('onSubtitle = { settingsPage = PlayerSettingsPage.SUBTITLE; settingsVisible = true },', 'onSubtitle = { settingsPage = PlayerSettingsPage.SUBTITLE; settingsVisible = true; revealControls() },')
s = s.replace('onMore = { settingsPage = PlayerSettingsPage.MAIN; settingsVisible = true },', 'onMore = { settingsPage = PlayerSettingsPage.MAIN; settingsVisible = true; revealControls() },')
player.write_text(s)

detail = root / 'src/main/java/com/embyplayernext/he/ui/screens/DetailScreen.kt'
replace_once(detail, 'Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {', 'Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(if (isTv) 18.dp else 10.dp)) {', 'detail action spacing')
replace_once(detail, 'val scale by animateFloatAsState(if (focusActive) 1.06f else 1f, label = "detailActionScale")', 'val scale by animateFloatAsState(if (focusActive) 1.03f else 1f, label = "detailActionScale")', 'detail action scale')

settings = root / 'src/main/java/com/embyplayernext/he/ui/screens/SettingsScreen.kt'
replace_once(settings, 'EmbyPlayerNext 2.3.23', 'EmbyPlayerNext 2.3.24', 'settings version')

shared = root / 'src/main/java/com/embyplayernext/he/ui/screens/SharedPlayerControls.kt'
if shared.exists():
    shared.unlink()
