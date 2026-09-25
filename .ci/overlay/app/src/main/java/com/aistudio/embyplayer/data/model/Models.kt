package com.aistudio.embyplayer.data.model

data class EmbyServerConfig(
    val serverUrl: String = "http://jia.hesip.cn:8096",
    val username: String = "",
    val userId: String = "",
    val accessToken: String = "",
    val serverName: String = "Emby Server",
    val dynamicPortEnabled: Boolean = false,
    val dynamicPortTargetDomain: String = "jia.hesip.cn",
    val dynamicPortTimeoutSeconds: Int = 5,
    val dynamicPortFetchUrl: String = "http://lac.hesip.top:8080/port",
    val dynamicPortServiceName: String = "emby-nginx",
    val uiScale: Float = 1.25f,
    val hardwareDecoding: Boolean = true,
    val seekSeconds: Int = 10,
    val rewindSeconds: Int = 10,
    val forwardSeconds: Int = 30,
    val cacheMb: Int = 128,
    val gestureSeekSeconds: Int = 180,
    val allowInsecureHttps: Boolean = true,
)

data class UserDataItem(
    val playbackPositionTicks: Long = 0L,
    val played: Boolean = false,
    val isFavorite: Boolean = false,
    val playedPercentage: Double? = null,
    val unplayedItemCount: Int? = null,
)

data class MediaStreamItem(
    val index: Int,
    val type: String,
    val codec: String? = null,
    val language: String? = null,
    val displayTitle: String? = null,
    val isDefault: Boolean = false,
    val isExternal: Boolean = false,
)

data class EmbyPerson(
    val id: String,
    val name: String,
    val type: String? = null,
    val role: String? = null,
    val primaryImageTag: String? = null,
)

data class EmbyItem(
    val id: String,
    val name: String,
    val type: String,
    val mediaType: String? = null,
    val parentId: String? = null,
    val overview: String? = null,
    val runTimeTicks: Long? = null,
    val productionYear: Int? = null,
    val premiereDate: String? = null,
    val dateCreated: String? = null,
    val communityRating: Double? = null,
    val officialRating: String? = null,
    val path: String? = null,
    val sortName: String? = null,
    val width: Int? = null,
    val height: Int? = null,
    val primaryImageTag: String? = null,
    val backdropImageTag: String? = null,
    val parentBackdropImageTag: String? = null,
    val parentBackdropItemId: String? = null,
    val seriesPrimaryImageTag: String? = null,
    val thumbImageTag: String? = null,
    val seriesId: String? = null,
    val seriesName: String? = null,
    val seasonName: String? = null,
    val album: String? = null,
    val albumArtist: String? = null,
    val artists: List<String> = emptyList(),
    val indexNumber: Int? = null,
    val parentIndexNumber: Int? = null,
    val childCount: Int? = null,
    val recursiveItemCount: Int? = null,
    val recursiveUnplayedItemCount: Int? = null,
    val genres: List<String> = emptyList(),
    val taglines: List<String> = emptyList(),
    val people: List<EmbyPerson> = emptyList(),
    val mediaStreams: List<MediaStreamItem> = emptyList(),
    val userData: UserDataItem = UserDataItem(),
) {
    val isFolder: Boolean get() = type in setOf(
        "Folder", "CollectionFolder", "Series", "Season", "BoxSet", "MusicAlbum", "MusicArtist", "Playlist"
    )
    val isBrowsableContainer: Boolean get() = type in setOf(
        "Folder", "CollectionFolder", "BoxSet", "MusicAlbum", "MusicArtist", "Season", "Playlist"
    )
    val isPlayable: Boolean get() = mediaType in setOf("Video", "Audio") || type in setOf(
        "Movie", "Episode", "Video", "Audio", "MusicVideo", "Trailer"
    )
    val isPlayed: Boolean get() = userData.played
    val resumePositionMs: Long get() = userData.playbackPositionTicks / 10_000L
    val durationMs: Long get() = (runTimeTicks ?: 0L) / 10_000L
    val tagline: String? get() = taglines.firstOrNull()
}

data class EmbyView(
    val id: String,
    val name: String,
    val collectionType: String? = null,
    val primaryImageTag: String? = null,
)

enum class EmbyDisplayMode(val label: String) {
    ALL("全部"),
    MOVIES("电影"),
    SERIES("剧集"),
    EPISODES("单集"),
    VIDEOS("视频"),
    SONGS("歌曲"),
    ALBUMS("专辑"),
    ARTISTS("艺术家"),
    PHOTOS("照片"),
    BOOKS("图书"),
    FOLDERS("文件夹"),
    FAVORITES("收藏"),
    UNPLAYED("未看"),
    RESUMABLE("继续观看")
}

enum class EmbySortField(val apiValue: String, val label: String) {
    SORT_NAME("SortName", "名称"),
    DATE_CREATED("DateCreated", "入库时间"),
    PREMIERE_DATE("PremiereDate", "首播时间"),
    PRODUCTION_YEAR("ProductionYear", "年份"),
    COMMUNITY_RATING("CommunityRating", "评分"),
    DATE_PLAYED("DatePlayed", "最近播放")
}

enum class EmbySortOrder(val apiValue: String, val label: String) {
    ASCENDING("Ascending", "升序"),
    DESCENDING("Descending", "降序")
}

data class EmbyItemsPage(
    val items: List<EmbyItem>,
    val totalRecordCount: Int,
)

data class PlaybackDescriptor(
    val item: EmbyItem,
    val streamUrl: String,
    val playSessionId: String,
    val mediaSourceId: String?,
    val initialPositionMs: Long,
    val serverRunTimeTicks: Long?,
    val playMethod: String = "DirectPlay",
)

data class PlaybackInfo(
    val playSessionId: String,
    val mediaSources: List<MediaSourceInfo>,
)

data class MediaSourceInfo(
    val id: String?,
    val runTimeTicks: Long?,
    val directStreamUrl: String?,
    val transcodingUrl: String?,
    val path: String?,
    val container: String?,
    val supportsDirectPlay: Boolean,
    val mediaStreams: List<MediaStreamItem> = emptyList(),
)
