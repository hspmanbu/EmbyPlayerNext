from pathlib import Path

def replace_once(path: Path, old: str, new: str, label: str):
    s = path.read_text()
    count = s.count(old)
    if count != 1:
        raise SystemExit(f'{label}: expected 1 match, found {count}')
    path.write_text(s.replace(old, new, 1))

api = Path('app/src/main/java/com/embyplayernext/he/data/network/EmbyApiClient.kt')
replace_once(api, 'Version=\\\"2.3.4\\\"', 'Version=\\\"2.3.24\\\"', 'auth version')
replace_once(api, 'EmbyPlayerNext/2.3.4 Android', 'EmbyPlayerNext/2.3.24 Android', 'api user agent')

service = Path('app/src/main/java/com/embyplayernext/he/playback/PlaybackService.kt')
replace_once(
    service,
    '''        val trackSelector = DefaultTrackSelector(this).apply {
            parameters = buildUponParameters()
                .setExceedRendererCapabilitiesIfNecessary(hybridDirectVideoEnabled)
                .setExceedVideoConstraintsIfNecessary(hybridDirectVideoEnabled)
                .setTunnelingEnabled(false)
                .build()
        }
        logger.log(
            "Codec",
            "trackSelector exceedRendererCapabilities=$hybridDirectVideoEnabled exceedVideoConstraints=$hybridDirectVideoEnabled tunneling=false",
        )
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
    'track selector exact defaults',
)

player = Path('app/src/main/java/com/embyplayernext/he/ui/screens/PlayerScreen.kt')
s = player.read_text()
old = '''        val startedAt = SystemClock.elapsedRealtime()
        var lastHeartbeat = 0L
        while (isActive && !recoveryInProgress) {
'''
new = '''        val startedAt = SystemClock.elapsedRealtime()
        var noFirstFrameClockStart = startedAt
        var lastHeartbeat = 0L
        while (isActive && !recoveryInProgress) {
'''
if s.count(old) != 1:
    raise SystemExit(f'watchdog start count={s.count(old)}')
s = s.replace(old, new, 1)
old = '''            val noFirstFrameTimeout = if (recoveryAttempt == 0) 7_000L else 9_000L
            val noFirstFrame = !renderedFirstFrame && player.playWhenReady && elapsed >= noFirstFrameTimeout
'''
new = '''            if (!player.playWhenReady) {
                noFirstFrameClockStart = now
            }
            val noFirstFrameTimeout = if (recoveryAttempt == 0) 7_000L else 9_000L
            val noFirstFrame = !renderedFirstFrame && player.playWhenReady && now - noFirstFrameClockStart >= noFirstFrameTimeout
'''
if s.count(old) != 1:
    raise SystemExit(f'watchdog condition count={s.count(old)}')
player.write_text(s.replace(old, new, 1))

detail = Path('app/src/main/java/com/embyplayernext/he/ui/screens/DetailScreen.kt')
replace_once(detail, 'val scale by animateFloatAsState(if (focusActive) 1.035f else 1f, label = "detailActionScale")', 'val scale by animateFloatAsState(if (focusActive) 1.03f else 1f, label = "detailActionScale")', 'detail action scale')
