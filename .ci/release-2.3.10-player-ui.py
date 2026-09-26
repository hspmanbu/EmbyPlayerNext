from pathlib import Path

def replace_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{label}: expected 1 match, found {count}")
    return text.replace(old, new, 1)

build = Path("app/build.gradle.kts")
s = build.read_text()
s = replace_once(s, 'versionCode = 132', 'versionCode = 133', "versionCode")
s = replace_once(s, 'versionName = "2.3.9"', 'versionName = "2.3.10"', "versionName")
build.write_text(s)

player = Path("app/src/main/java/com/embyplayernext/he/ui/screens/PlayerScreen.kt")
s = player.read_text()
start = s.find("        SharedPlayerControls(")
if start < 0: raise SystemExit("shared controls call not found")
end = s.find("        error?.let", start)
if end < 0: raise SystemExit("shared controls call end not found")
visible_name = "controlsVisible" if "controlsVisible" in s[start:end] else "controls"
new = f'''        val sharedAudioTracks = controller?.currentTracks?.groups.orEmpty()
            .filter {{ it.type == C.TRACK_TYPE_AUDIO }}
            .flatMap {{ group ->
                (0 until group.length).map {{ index ->
                    val format = group.getTrackFormat(index)
                    PlayerTrackOption(
                        id = group.mediaTrackGroup.id + ":" + index,
                        label = format.label ?: format.language ?: "音轨 " + (index + 1),
                        selected = group.isTrackSelected(index),
                    )
                }}
            }}
        val sharedSubtitleTracks = controller?.currentTracks?.groups.orEmpty()
            .filter {{ it.type == C.TRACK_TYPE_TEXT }}
            .flatMap {{ group ->
                (0 until group.length).map {{ index ->
                    val format = group.getTrackFormat(index)
                    PlayerTrackOption(
                        id = group.mediaTrackGroup.id + ":" + index,
                        label = format.label ?: format.language ?: "字幕 " + (index + 1),
                        selected = group.isTrackSelected(index),
                    )
                }}
            }}
        SharedPlayerControls(
            title = descriptor.item.name,
            visible = {visible_name},
            locked = locked,
            positionMs = controller?.currentPosition ?: 0L,
            durationMs = controller?.duration?.takeIf {{ it > 0 && it != C.TIME_UNSET }} ?: durationMs ?: 0L,
            isPlaying = controller?.isPlaying == true,
            networkSpeed = networkSpeed,
            rewindSeconds = config.rewindSeconds,
            forwardSeconds = config.forwardSeconds,
            playbackSpeed = controller?.playbackParameters?.speed ?: 1f,
            audioTracks = sharedAudioTracks,
            subtitleTracks = sharedSubtitleTracks,
            onBack = {{ exit() }},
            onToggleLock = {{ locked = !locked }},
            onSeek = {{ controller?.seekTo(it) }},
            onTogglePlay = {{ if (controller?.isPlaying == true) controller.pause() else controller?.play() }},
            onSetSpeed = {{ value -> controller?.setPlaybackSpeed(value) }},
            onSelectAudio = {{ id ->
                val split = id.lastIndexOf(\":\")
                if (split > 0) {{
                    val groupId = id.substring(0, split)
                    val index = id.substring(split + 1).toIntOrNull()
                    val group = controller?.currentTracks?.groups?.firstOrNull {{
                        it.type == C.TRACK_TYPE_AUDIO && it.mediaTrackGroup.id == groupId
                    }}
                    if (controller != null && group != null && index != null) {{
                        val override = TrackSelectionOverride(group.mediaTrackGroup, listOf(index))
                        controller.trackSelectionParameters = controller.trackSelectionParameters.buildUpon()
                            .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, false)
                            .clearOverridesOfType(C.TRACK_TYPE_AUDIO)
                            .addOverride(override)
                            .build()
                    }}
                }}
            }},
            onSelectSubtitle = {{ id ->
                if (id == "__off__") {{
                    controller?.let {{ p ->
                        p.trackSelectionParameters = p.trackSelectionParameters.buildUpon()
                            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                            .build()
                    }}
                }} else {{
                    val split = id.lastIndexOf(\":\")
                    if (split > 0) {{
                        val groupId = id.substring(0, split)
                        val index = id.substring(split + 1).toIntOrNull()
                        val group = controller?.currentTracks?.groups?.firstOrNull {{
                            it.type == C.TRACK_TYPE_TEXT && it.mediaTrackGroup.id == groupId
                        }}
                        if (controller != null && group != null && index != null) {{
                            val override = TrackSelectionOverride(group.mediaTrackGroup, listOf(index))
                            controller.trackSelectionParameters = controller.trackSelectionParameters.buildUpon()
                                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                                .clearOverridesOfType(C.TRACK_TYPE_TEXT)
                                .addOverride(override)
                                .build()
                        }}
                    }}
                }}
            }},
            onAspect = {{ aspect = AspectMode.entries[(aspect.ordinal + 1) % AspectMode.entries.size] }},
        )
'''
s = s[:start] + new + "\n" + s[end:]
player.write_text(s)

settings = Path("app/src/main/java/com/embyplayernext/he/ui/screens/SettingsScreen.kt")
s = settings.read_text().replace('headlineContent = { Text("EmbyPlayerNext 2.3.9") }', 'headlineContent = { Text("EmbyPlayerNext 2.3.10") }')
settings.write_text(s)