package com.embyplayernext.he.data.network

import android.content.Context
import com.embyplayernext.he.data.model.*
import com.embyplayernext.he.data.prefs.AppPreferences
import com.embyplayernext.he.util.DeviceId
import com.embyplayernext.he.util.DiagnosticsLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.SocketTimeoutException
import java.util.UUID

class EmbyApiClient(
    private val context: Context,
    private val prefs: AppPreferences,
    private val logger: DiagnosticsLogger,
) {
    private val dynamicResolver = DynamicPortResolver(prefs, logger)
    private val jsonType = "application/json; charset=utf-8".toMediaType()
    private val listFields = "RunTimeTicks,ProductionYear,PremiereDate,DateCreated,CommunityRating,OfficialRating,MediaType,SortName,Width,Height,ImageTags,BackdropImageTags,ParentBackdropImageTags,ParentBackdropItemId,SeriesPrimaryImageTag,SeriesThumbImageTag,SeriesId,SeriesName,SeasonName,Album,AlbumArtist,Artists,IndexNumber,ParentIndexNumber,ChildCount,RecursiveItemCount,RecursiveUnplayedItemCount"
    private val detailFields = "$listFields,Overview,MediaStreams,Path,Genres,People,Taglines"

    private fun authHeader(): String = "MediaBrowser Client=\"EmbyPlayerNext\", Device=\"Android\", DeviceId=\"${DeviceId.get(context)}\", Version=\"2.3.31\""
    private fun config() = prefs.config.value
    private fun base() = config().serverUrl.trimEnd('/')

    private fun requestBuilder(url: String, includeToken: Boolean = true): Request.Builder = Request.Builder().url(url)
        .header("X-Emby-Authorization", authHeader())
        .header("User-Agent", "EmbyPlayerNext/2.3.31 Android")
        .apply { if (includeToken && config().accessToken.isNotBlank()) header("X-Emby-Token", config().accessToken) }

    private suspend fun execute(requestFactory: () -> Request): String = withContext(Dispatchers.IO) {
        suspend fun runOnce(): String {
            val c = config()
            val req = requestFactory()
            logger.log("HTTP", "${req.method} ${req.url}")
            return NetworkSupport.apiClient(c, if (c.dynamicPortEnabled) c.dynamicPortTimeoutSeconds else 20).newCall(req).execute().use { r ->
                val body = r.body?.string().orEmpty()
                logger.log("HTTP", "${r.code} ${req.url.encodedPath} ${body.take(800)}")
                if (!r.isSuccessful) throw HttpStatusException(r.code, body)
                body
            }
        }
        try {
            runOnce()
        } catch (first: Throwable) {
            val c = config()
            val retryable = first is IOException || first is SocketTimeoutException || (first is HttpStatusException && first.code in setOf(502, 503, 504))
            val eligible = c.dynamicPortEnabled && (c.dynamicPortTargetDomain.isBlank() || c.serverUrl.contains(c.dynamicPortTargetDomain, true))
            if (retryable && eligible && c.dynamicPortFetchUrl.isNotBlank() && c.dynamicPortServiceName.isNotBlank()) {
                logger.log("DynamicPort", "request failed: ${first.message}; resolving...")
                val switchResult = dynamicResolver.resolveAndSwitch()
                if (switchResult.isSuccess) {
                    return@withContext runOnce()
                } else {
                    logger.log("DynamicPort", "resolve failed: ${switchResult.exceptionOrNull()?.message}")
                }
            }
            if (first is IOException) {
                try {
                    logger.log("HTTP", "transient IO error (${first.message}), retrying once...")
                    return@withContext runOnce()
                } catch (retryEx: Throwable) {
                    logger.log("HTTP", "retry failed: ${retryEx.message}")
                }
            }
            throw first
        }
    }

    private suspend fun executeDirect(candidate: EmbyServerConfig, requestFactory: () -> Request): String = withContext(Dispatchers.IO) {
        suspend fun runDirect(): String {
            val req = requestFactory()
            logger.log("HTTP", "${req.method} ${req.url}")
            return NetworkSupport.apiClient(candidate, if (candidate.dynamicPortEnabled) candidate.dynamicPortTimeoutSeconds else 20)
                .newCall(req).execute().use { r ->
                    val body = r.body?.string().orEmpty()
                    logger.log("HTTP", "${r.code} ${req.url.encodedPath} ${body.take(800)}")
                    if (!r.isSuccessful) throw HttpStatusException(r.code, body)
                    body
                }
        }
        try {
            runDirect()
        } catch (first: IOException) {
            logger.log("HTTP", "direct call failed: ${first.message}; retrying once...")
            runDirect()
        }
    }

    suspend fun testConnection(serverUrl: String? = null, onUpdatedUrl: ((String) -> Unit)? = null): Result<String> = runCatching {
        var rawUrl = (serverUrl ?: base()).trim().trimEnd('/')
        require(rawUrl.isNotBlank()) { "服务器地址不能为空" }
        var normalized = if (rawUrl.startsWith("http://") || rawUrl.startsWith("https://")) rawUrl else "http://$rawUrl"
        val c = config()
        var candidate = c.copy(serverUrl = normalized)
        var updatedPort: Int? = null
        val body = try {
            executeDirect(candidate) {
                requestBuilder("$normalized/System/Info/Public", includeToken = false).get().build()
            }
        } catch (first: Throwable) {
            if (dynamicResolver.matchesDynamicPort(normalized, c)) {
                logger.log("DynamicPort", "测试连接失败(${first.message})，符合动态端口配置，自动拉取最新端口...")
                val portResult = dynamicResolver.fetchPort(
                    fetchUrl = c.dynamicPortFetchUrl,
                    serviceName = c.dynamicPortServiceName,
                    timeoutSeconds = c.dynamicPortTimeoutSeconds,
                    allowInsecureHttps = c.allowInsecureHttps,
                )
                if (portResult.isSuccess) {
                    val port = portResult.getOrThrow()
                    updatedPort = port
                    normalized = dynamicResolver.replacePort(normalized, port)
                    candidate = candidate.copy(serverUrl = normalized)
                    onUpdatedUrl?.invoke(normalized)
                    logger.log("DynamicPort", "使用拉取的动态端口重新测试连接: $normalized")
                    executeDirect(candidate) {
                        requestBuilder("$normalized/System/Info/Public", includeToken = false).get().build()
                    }
                } else {
                    throw first
                }
            } else {
                throw first
            }
        }
        val sName = JSONObject(body).optString("ServerName", "Emby Server")
        if (updatedPort != null) "$sName (已自动识别并切换为动态端口: $updatedPort)" else sName
    }

    suspend fun login(serverUrl: String, username: String, password: String): Result<EmbyServerConfig> = runCatching {
        var rawUrl = serverUrl.trim().trimEnd('/')
        require(rawUrl.isNotBlank()) { "服务器地址不能为空" }
        var normalized = if (rawUrl.startsWith("http://") || rawUrl.startsWith("https://")) rawUrl else "http://$rawUrl"
        val c = config()
        var candidate = c.copy(serverUrl = normalized, username = username)
        val payload = JSONObject().put("Username", username).put("Pw", password)
        var body: String
        try {
            body = executeDirect(candidate) {
                requestBuilder("$normalized/Users/AuthenticateByName", includeToken = false)
                    .post(payload.toString().toRequestBody(jsonType)).build()
            }
        } catch (first: Throwable) {
            if (dynamicResolver.matchesDynamicPort(normalized, c)) {
                logger.log("DynamicPort", "登录首次连接失败(${first.message})，符合动态端口配置，自动拉取最新端口...")
                val portResult = dynamicResolver.fetchPort(
                    fetchUrl = c.dynamicPortFetchUrl,
                    serviceName = c.dynamicPortServiceName,
                    timeoutSeconds = c.dynamicPortTimeoutSeconds,
                    allowInsecureHttps = c.allowInsecureHttps,
                )
                if (portResult.isSuccess) {
                    val port = portResult.getOrThrow()
                    normalized = dynamicResolver.replacePort(normalized, port)
                    candidate = candidate.copy(serverUrl = normalized)
                    logger.log("DynamicPort", "使用拉取的动态端口重新登录: $normalized")
                    body = executeDirect(candidate) {
                        requestBuilder("$normalized/Users/AuthenticateByName", includeToken = false)
                            .post(payload.toString().toRequestBody(jsonType)).build()
                    }
                } else {
                    throw first
                }
            } else {
                throw first
            }
        }
        val root = JSONObject(body)
        val user = root.getJSONObject("User")
        val token = root.getString("AccessToken")
        val sName = root.optJSONObject("Server")?.optString("Name")?.takeIf { it.isNotBlank() } ?: "Emby Server"
        val cfg = prefs.saveLogin(
            serverUrl = normalized,
            serverName = sName,
            username = username,
            userId = user.getString("Id"),
            accessToken = token,
        )
        cfg
    }

    suspend fun getViews(): List<EmbyView> {
        val uid = requireUser()
        val root = JSONObject(execute { requestBuilder("${base()}/Users/$uid/Views").get().build() })
        return jsonItems(root).map { o -> EmbyView(o.optString("Id"), o.optString("Name"), o.optString("CollectionType").ifBlank { null }, imageTag(o, "Primary")) }
    }

    suspend fun getResumeItems(limit: Int = 24): List<EmbyItem> {
        val uid = requireUser()
        val url = "${base()}/Users/$uid/Items/Resume".toHttpUrlOrNull()!!.newBuilder()
            .addQueryParameter("Limit", limit.toString()).addQueryParameter("Fields", listFields)
            .addQueryParameter("EnableImageTypes", "Primary,Backdrop,Thumb")
            .addQueryParameter("EnableUserData", "true").build().toString()
        return parseItems(JSONObject(execute { requestBuilder(url).get().build() }))
    }

    suspend fun getNextUpItems(limit: Int = 24): List<EmbyItem> {
        val uid = requireUser()
        val url = "${base()}/Shows/NextUp".toHttpUrlOrNull()!!.newBuilder()
            .addQueryParameter("UserId", uid)
            .addQueryParameter("Limit", limit.coerceIn(1, 100).toString())
            .addQueryParameter("Fields", listFields)
            .addQueryParameter("EnableImageTypes", "Primary,Backdrop,Thumb")
            .addQueryParameter("EnableUserData", "true")
            .build().toString()
        return parseItems(JSONObject(execute { requestBuilder(url).get().build() }))
    }

    suspend fun getLatestItems(limit: Int = 36): List<EmbyItem> {
        val uid = requireUser()
        val b = "${base()}/Users/$uid/Items".toHttpUrlOrNull()!!.newBuilder()
            .addQueryParameter("Fields", listFields)
            .addQueryParameter("EnableImageTypes", "Primary,Backdrop,Thumb")
            .addQueryParameter("EnableUserData", "true")
            .addQueryParameter("Recursive", "true")
            .addQueryParameter("IncludeItemTypes", "Movie,Episode,Video,MusicVideo,Audio,Photo,Book")
            .addQueryParameter("SortBy", "DateCreated")
            .addQueryParameter("SortOrder", "Descending")
            .addQueryParameter("Limit", limit.coerceIn(1, 100).toString())
        return parseItems(JSONObject(execute { requestBuilder(b.build().toString()).get().build() }))
            .filter { it.id.isNotBlank() }
            .distinctBy(EmbyItem::id)
            .take(limit)
    }

    suspend fun getRecentlyPlayedItems(limit: Int = 24): List<EmbyItem> {
        val uid = requireUser()
        val fetchLimit = (limit * 3).coerceIn(48, 120)
        val b = "${base()}/Users/$uid/Items".toHttpUrlOrNull()!!.newBuilder()
            .addQueryParameter("Fields", listFields)
            .addQueryParameter("EnableImageTypes", "Primary,Backdrop,Thumb")
            .addQueryParameter("EnableUserData", "true")
            .addQueryParameter("Recursive", "true")
            .addQueryParameter("IncludeItemTypes", "Movie,Episode,Video,MusicVideo,Audio")
            .addQueryParameter("SortBy", "DatePlayed")
            .addQueryParameter("SortOrder", "Descending")
            .addQueryParameter("Limit", fetchLimit.toString())
        return parseItems(JSONObject(execute { requestBuilder(b.build().toString()).get().build() }))
            .asSequence()
            .filter { it.id.isNotBlank() && (it.userData.lastPlayedDate != null || it.resumePositionMs > 0 || it.isPlayed) }
            .distinctBy(EmbyItem::id)
            .take(limit)
            .toList()
    }

    suspend fun getItems(
        parentId: String?, mode: EmbyDisplayMode, search: String = "", sortField: EmbySortField = EmbySortField.SORT_NAME,
        sortOrder: EmbySortOrder = EmbySortOrder.ASCENDING, recursive: Boolean = false, limit: Int = 500
    ): List<EmbyItem> = getItemsPage(parentId, mode, search, sortField, sortOrder, recursive, 0, limit).items

    suspend fun getItemsPage(
        parentId: String?, mode: EmbyDisplayMode, search: String = "", sortField: EmbySortField = EmbySortField.SORT_NAME,
        sortOrder: EmbySortOrder = EmbySortOrder.ASCENDING, recursive: Boolean = false, startIndex: Int = 0, limit: Int = 120
    ): EmbyItemsPage {
        val uid = requireUser()
        val b = "${base()}/Users/$uid/Items".toHttpUrlOrNull()!!.newBuilder()
            .addQueryParameter("Fields", listFields)
            .addQueryParameter("EnableImageTypes", "Primary,Backdrop,Thumb,Banner")
            .addQueryParameter("EnableUserData", "true")
            .addQueryParameter("Recursive", recursive.toString())
            .addQueryParameter("SortBy", sortField.apiValue)
            .addQueryParameter("SortOrder", sortOrder.apiValue)
            .addQueryParameter("StartIndex", startIndex.coerceAtLeast(0).toString())
            .addQueryParameter("Limit", limit.coerceIn(1, 500).toString())
        parentId?.takeIf { it.isNotBlank() }?.let { b.addQueryParameter("ParentId", it) }
        search.trim().takeIf { it.isNotBlank() }?.let { b.addQueryParameter("SearchTerm", it) }
        when (mode) {
            EmbyDisplayMode.ALL -> Unit
            EmbyDisplayMode.MOVIES -> b.addQueryParameter("IncludeItemTypes", "Movie")
            EmbyDisplayMode.SERIES -> b.addQueryParameter("IncludeItemTypes", "Series")
            EmbyDisplayMode.EPISODES -> b.addQueryParameter("IncludeItemTypes", "Episode")
            EmbyDisplayMode.VIDEOS -> b.addQueryParameter("IncludeItemTypes", "Video,Movie,Episode,MusicVideo,Trailer")
            EmbyDisplayMode.SONGS -> b.addQueryParameter("IncludeItemTypes", "Audio")
            EmbyDisplayMode.ALBUMS -> b.addQueryParameter("IncludeItemTypes", "MusicAlbum")
            EmbyDisplayMode.ARTISTS -> b.addQueryParameter("IncludeItemTypes", "MusicArtist")
            EmbyDisplayMode.PHOTOS -> b.addQueryParameter("IncludeItemTypes", "Photo")
            EmbyDisplayMode.BOOKS -> b.addQueryParameter("IncludeItemTypes", "Book")
            EmbyDisplayMode.FOLDERS -> b.addQueryParameter("IncludeItemTypes", "Folder,CollectionFolder,BoxSet,Season,MusicAlbum,MusicArtist,Playlist")
            EmbyDisplayMode.FAVORITES -> b.addQueryParameter("Filters", "IsFavorite")
            EmbyDisplayMode.UNPLAYED -> b.addQueryParameter("Filters", "IsUnplayed")
            EmbyDisplayMode.RESUMABLE -> b.addQueryParameter("Filters", "IsResumable")
        }
        val root = JSONObject(execute { requestBuilder(b.build().toString()).get().build() })
        val items = parseItems(root)
        return EmbyItemsPage(items, root.optInt("TotalRecordCount", startIndex + items.size))
    }

    suspend fun getItemDetails(id: String): EmbyItem {
        val uid = requireUser()
        val url = "${base()}/Users/$uid/Items/$id".toHttpUrlOrNull()!!.newBuilder()
            .addQueryParameter("Fields", detailFields)
            .addQueryParameter("EnableImageTypes", "Primary,Backdrop,Thumb")
            .addQueryParameter("EnableUserData", "true")
            .build().toString()
        return parseSingleItem(JSONObject(execute { requestBuilder(url).get().build() }))
    }

    suspend fun getSeasons(seriesId: String): List<EmbyItem> {
        val uid = requireUser()
        val b = "${base()}/Shows/$seriesId/Seasons".toHttpUrlOrNull()!!.newBuilder().addQueryParameter("UserId", uid).addQueryParameter("Fields", listFields)
        return parseItems(JSONObject(execute { requestBuilder(b.build().toString()).get().build() }))
    }

    suspend fun getEpisodes(seriesId: String, seasonId: String?): List<EmbyItem> {
        val uid = requireUser()
        val b = "${base()}/Shows/$seriesId/Episodes".toHttpUrlOrNull()!!.newBuilder().addQueryParameter("UserId", uid).addQueryParameter("Fields", listFields)
        seasonId?.let { b.addQueryParameter("SeasonId", it) }
        return parseItems(JSONObject(execute { requestBuilder(b.build().toString()).get().build() }))
    }

    suspend fun getItemsByPerson(personId: String): List<EmbyItem> {
        val uid = requireUser()
        val b = "${base()}/Users/$uid/Items".toHttpUrlOrNull()!!.newBuilder()
            .addQueryParameter("PersonIds", personId).addQueryParameter("Recursive", "true")
            .addQueryParameter("IncludeItemTypes", "Movie,Series").addQueryParameter("Fields", listFields)
            .addQueryParameter("SortBy", "SortName").addQueryParameter("SortOrder", "Ascending")
        return parseItems(JSONObject(execute { requestBuilder(b.build().toString()).get().build() })).distinctBy { it.id }
    }

    suspend fun getSimilarItems(itemId: String, limit: Int = 18): List<EmbyItem> {
        val uid = requireUser()
        val url = "${base()}/Items/$itemId/Similar".toHttpUrlOrNull()!!.newBuilder()
            .addQueryParameter("UserId", uid)
            .addQueryParameter("Limit", limit.coerceIn(1, 60).toString())
            .addQueryParameter("Fields", listFields)
            .build().toString()
        return parseItems(JSONObject(execute { requestBuilder(url).get().build() })).filter { it.type == "Movie" || it.type == "Series" }.distinctBy { it.id }
    }

    suspend fun toggleFavorite(item: EmbyItem): EmbyItem {
        val uid = requireUser(); val target = !item.userData.isFavorite
        val url = "${base()}/Users/$uid/FavoriteItems/${item.id}"
        execute { requestBuilder(url).method(if (target) "POST" else "DELETE", if (target) ByteArray(0).toRequestBody() else null).build() }
        return getItemDetails(item.id)
    }

    suspend fun togglePlayed(item: EmbyItem): EmbyItem {
        val uid = requireUser(); val target = !item.userData.played
        val url = "${base()}/Users/$uid/PlayedItems/${item.id}"
        execute { requestBuilder(url).method(if (target) "POST" else "DELETE", if (target) ByteArray(0).toRequestBody() else null).build() }
        return getItemDetails(item.id)
    }

    suspend fun deleteItem(itemId: String) {
        require(itemId.isNotBlank()) { "itemId cannot be blank" }
        execute { requestBuilder("${base()}/Items/$itemId").delete().build() }
    }

    suspend fun preparePlayback(item: EmbyItem, resume: Boolean, forceTranscode: Boolean = false): PlaybackDescriptor {
        val uid = requireUser()
        val startTicks = if (resume) item.userData.playbackPositionTicks else 0L
        val url = "${base()}/Items/${item.id}/PlaybackInfo".toHttpUrlOrNull()!!.newBuilder()
            .addQueryParameter("UserId", uid).addQueryParameter("StartTimeTicks", startTicks.toString())
            .addQueryParameter("IsPlayback", "true").addQueryParameter("AutoOpenLiveStream", "true").build().toString()
        val playbackRequest = if (forceTranscode) {
            JSONObject()
                .put("EnableDirectPlay", false)
                .put("EnableDirectStream", false)
                .put("EnableTranscoding", true)
                .put("AllowVideoStreamCopy", false)
                .put("AllowAudioStreamCopy", false)
                .put("StartTimeTicks", startTicks)
                .put("DeviceProfile", JSONObject()
                    .put("Name", "EmbyPlayerNext forced transcode")
                    .put("SupportedMediaTypes", "Video,Audio")
                    .put("MaxStreamingBitrate", 100_000_000L)
                    .put("MusicStreamingTranscodingBitrate", 320_000)
                    .put("DirectPlayProfiles", JSONArray())
                    .put("ContainerProfiles", JSONArray())
                    .put("CodecProfiles", JSONArray())
                    .put("ResponseProfiles", JSONArray())
                    .put("SubtitleProfiles", JSONArray())
                    .put("TranscodingProfiles", JSONArray().put(
                        JSONObject()
                            .put("Container", "ts")
                            .put("Type", "Video")
                            .put("VideoCodec", "h264")
                            .put("AudioCodec", "aac")
                            .put("Protocol", "hls")
                            .put("Context", "Streaming")
                            .put("TranscodeSeekInfo", "Auto")
                            .put("CopyTimestamps", false)
                            .put("EstimateContentLength", false)
                            .put("MaxAudioChannels", "6")
                            .put("MinSegments", 1)
                            .put("SegmentLength", 3)
                            .put("BreakOnNonKeyFrames", true)
                    ))
                )
        } else {
            JSONObject()
                .put("EnableDirectPlay", true)
                .put("EnableDirectStream", true)
                .put("EnableTranscoding", true)
                .put("AllowVideoStreamCopy", true)
                .put("AllowAudioStreamCopy", true)
        }
        logger.log("PlaybackInfo", "request item=${item.id} resume=$resume startTicks=$startTicks forceTranscode=$forceTranscode body=${playbackRequest}")
        val info = runCatching {
            val body = execute { requestBuilder(url).post(playbackRequest.toString().toRequestBody(jsonType)).build() }
            parsePlaybackInfo(JSONObject(body))
        }.getOrElse {
            logger.log("PlaybackInfo", "fallback: ${it.message}")
            PlaybackInfo(UUID.randomUUID().toString(), emptyList())
        }
        val source = if (forceTranscode) {
            info.mediaSources.firstOrNull { !it.transcodingUrl.isNullOrBlank() }
                ?: info.mediaSources.firstOrNull { it.supportsDirectPlay }
                ?: info.mediaSources.firstOrNull()
        } else {
            info.mediaSources.firstOrNull { it.supportsDirectPlay } ?: info.mediaSources.firstOrNull()
        }
        val serverTranscodeUrl = source?.transcodingUrl?.takeIf { it.isNotBlank() }
        val useTranscode = forceTranscode || (source != null && serverTranscodeUrl != null && !source.supportsDirectPlay)
        val stream = if (forceTranscode && serverTranscodeUrl == null) {
            buildForcedTranscodeUrl(item.id, source, info.playSessionId, startTicks)
        } else {
            buildStreamUrl(item.id, source, info.playSessionId, useTranscode)
        }
        logger.log(
            "PlaybackPrepare",
            "item=${item.id} resume=$resume requestedMs=${startTicks / 10_000L} forceTranscode=$forceTranscode method=${if (useTranscode) "Transcode" else "DirectPlay"} source=${source?.id} container=${source?.container} direct=${source?.supportsDirectPlay} hasDirectUrl=${!source?.directStreamUrl.isNullOrBlank()} hasTranscodeUrl=${serverTranscodeUrl != null} streamPath=${runCatching { stream.toHttpUrlOrNull()?.encodedPath }.getOrNull()}"
        )
        return PlaybackDescriptor(item, stream, info.playSessionId, source?.id, startTicks / 10_000L, source?.runTimeTicks ?: item.runTimeTicks, if (useTranscode) "Transcode" else "DirectPlay")
    }

    private fun buildForcedTranscodeUrl(itemId: String, source: MediaSourceInfo?, playSessionId: String, startTicks: Long): String {
        val b = "${base()}/Videos/$itemId/stream.ts".toHttpUrlOrNull()!!.newBuilder()
            .addQueryParameter("MediaSourceId", source?.id ?: "")
            .addQueryParameter("PlaySessionId", playSessionId)
            .addQueryParameter("StartTimeTicks", startTicks.toString())
            .addQueryParameter("VideoCodec", "h264")
            .addQueryParameter("AudioCodec", "aac")
            .addQueryParameter("MaxAudioChannels", "6")
            .addQueryParameter("Static", "false")
        if (config().accessToken.isNotBlank()) b.addQueryParameter("api_key", config().accessToken)
        return b.build().toString()
    }

    private fun buildStreamUrl(itemId: String, source: MediaSourceInfo?, playSessionId: String, useTranscode: Boolean): String {
        fun absolutize(path: String): String {
            if (path.startsWith("http://") || path.startsWith("https://")) return path
            val slash = if (path.startsWith('/')) "" else "/"
            return base() + slash + path
        }
        val preferred = (if (useTranscode) source?.transcodingUrl else source?.directStreamUrl)?.takeIf { it.isNotBlank() }
        val raw = (preferred?.let(::absolutize) ?: "${base()}/Videos/$itemId/stream")
            .replace("{token}", config().accessToken, true)
        val http = raw.toHttpUrlOrNull()!!.newBuilder()
        if (preferred == null) {
            http.addQueryParameter("static", "true")
            source?.id?.let { http.addQueryParameter("MediaSourceId", it) }
            http.addQueryParameter("PlaySessionId", playSessionId)
        }
        if (config().accessToken.isNotBlank() && raw.toHttpUrlOrNull()?.queryParameter("api_key") == null) http.addQueryParameter("api_key", config().accessToken)
        return http.build().toString()
    }

    suspend fun reportPlaybackStart(d: PlaybackDescriptor, positionMs: Long, runTimeMs: Long?) = playbackCheckin("/Sessions/Playing", d, positionMs, runTimeMs, false, null)
    suspend fun reportPlaybackProgress(d: PlaybackDescriptor, positionMs: Long, runTimeMs: Long?, paused: Boolean, eventName: String = if (paused) "Pause" else "TimeUpdate") = playbackCheckin("/Sessions/Playing/Progress", d, positionMs, runTimeMs, paused, eventName)
    suspend fun reportPlaybackStopped(d: PlaybackDescriptor, positionMs: Long, runTimeMs: Long?) = playbackCheckin("/Sessions/Playing/Stopped", d, positionMs, runTimeMs, true, null)

    private suspend fun playbackCheckin(path: String, d: PlaybackDescriptor, positionMs: Long, runTimeMs: Long?, paused: Boolean, eventName: String?) {
        val c = config()
        val runtimeTicks = (runTimeMs?.takeIf { it > 0 }?.times(10_000L)) ?: d.serverRunTimeTicks?.takeIf { it > 0 }
        val json = JSONObject()
            .put("ItemId", d.item.id).put("UserId", c.userId).put("PlaySessionId", d.playSessionId)
            .put("PositionTicks", positionMs.coerceAtLeast(0) * 10_000L).put("IsPaused", paused)
            .put("CanSeek", true).put("PlayMethod", d.playMethod)
        d.mediaSourceId?.let { json.put("MediaSourceId", it) }
        runtimeTicks?.let { json.put("RunTimeTicks", it) }
        eventName?.let { json.put("EventName", it) }
        logger.log("Playback", "$path ${json}")
        execute { requestBuilder(base() + path).post(json.toString().toRequestBody(jsonType)).build() }
    }

    suspend fun resolveDynamicPort(force: Boolean = true): Result<Int> = dynamicResolver.resolveAndSwitch(force)

    fun imageUrl(view: EmbyView, width: Int = 900): String? {
        val tag = view.primaryImageTag ?: return null
        val b = "${base()}/Items/${view.id}/Images/Primary".toHttpUrlOrNull()!!.newBuilder()
            .addQueryParameter("maxWidth", width.toString()).addQueryParameter("quality", "90").addQueryParameter("tag", tag)
        if (config().accessToken.isNotBlank()) b.addQueryParameter("api_key", config().accessToken)
        return b.build().toString()
    }

    fun imageUrl(person: EmbyPerson, width: Int = 360): String? {
        val tag = person.primaryImageTag ?: return null
        val b = "${base()}/Items/${person.id}/Images/Primary".toHttpUrlOrNull()!!.newBuilder()
            .addQueryParameter("maxWidth", width.toString())
            .addQueryParameter("quality", "90")
            .addQueryParameter("tag", tag)
        if (config().accessToken.isNotBlank()) b.addQueryParameter("api_key", config().accessToken)
        return b.build().toString()
    }

    fun imageUrl(item: EmbyItem, type: String = "Primary", width: Int = 600): String? {
        val (tag, imageItemId) = when (type) {
            "Backdrop" -> when {
                item.backdropImageTag != null -> item.backdropImageTag to item.id
                item.parentBackdropImageTag != null -> item.parentBackdropImageTag to (item.parentBackdropItemId ?: item.seriesId ?: item.id)
                else -> null
            }
            "Thumb" -> item.thumbImageTag?.let { it to (item.seriesId ?: item.id) }
            else -> when {
                item.primaryImageTag != null -> item.primaryImageTag to item.id
                item.seriesPrimaryImageTag != null -> item.seriesPrimaryImageTag to (item.seriesId ?: item.id)
                else -> null
            }
        } ?: return null
        val b = "${base()}/Items/$imageItemId/Images/$type".toHttpUrlOrNull()!!.newBuilder()
            .addQueryParameter("maxWidth", width.toString()).addQueryParameter("quality", "90").addQueryParameter("tag", tag)
        if (config().accessToken.isNotBlank()) b.addQueryParameter("api_key", config().accessToken)
        return b.build().toString()
    }

    fun seriesImageUrl(item: EmbyItem, type: String = "Backdrop", width: Int = 800): String? {
        if (item.seriesId.isNullOrBlank()) return imageUrl(item, type, width)
        val sId = item.seriesId
        if (type == "Backdrop") {
            val tag = item.parentBackdropImageTag ?: item.backdropImageTag
            val targetId = item.parentBackdropItemId ?: sId
            if (tag != null) {
                val b = "${base()}/Items/$targetId/Images/Backdrop".toHttpUrlOrNull()!!.newBuilder()
                    .addQueryParameter("maxWidth", width.toString()).addQueryParameter("quality", "90").addQueryParameter("tag", tag)
                if (config().accessToken.isNotBlank()) b.addQueryParameter("api_key", config().accessToken)
                return b.build().toString()
            }
        }
        val pTag = item.seriesPrimaryImageTag ?: item.primaryImageTag
        if (pTag != null) {
            val b = "${base()}/Items/$sId/Images/Primary".toHttpUrlOrNull()!!.newBuilder()
                .addQueryParameter("maxWidth", width.toString()).addQueryParameter("quality", "90").addQueryParameter("tag", pTag)
            if (config().accessToken.isNotBlank()) b.addQueryParameter("api_key", config().accessToken)
            return b.build().toString()
        }
        return imageUrl(item, type, width)
    }

    private fun parsePlaybackInfo(root: JSONObject): PlaybackInfo {
        val session = root.optString("PlaySessionId").ifBlank { UUID.randomUUID().toString() }
        val arr = root.optJSONArray("MediaSources") ?: JSONArray()
        val sources = (0 until arr.length()).mapNotNull { i -> arr.optJSONObject(i)?.let { o ->
            MediaSourceInfo(
                id = o.optString("Id").ifBlank { null }, runTimeTicks = optLongOrNull(o, "RunTimeTicks"),
                directStreamUrl = o.optString("DirectStreamUrl").ifBlank { null }, transcodingUrl = o.optString("TranscodingUrl").ifBlank { null },
                path = o.optString("Path").ifBlank { null }, container = o.optString("Container").ifBlank { null },
                supportsDirectPlay = o.optBoolean("SupportsDirectPlay", true), mediaStreams = parseStreams(o.optJSONArray("MediaStreams"))
            )
        } }
        return PlaybackInfo(session, sources)
    }

    private fun parseItems(root: JSONObject): List<EmbyItem> = jsonItems(root).map(::parseSingleItem)
    private fun jsonItems(root: JSONObject): List<JSONObject> {
        val arr = root.optJSONArray("Items") ?: JSONArray()
        return (0 until arr.length()).mapNotNull { arr.optJSONObject(it) }
    }

    private fun parseSingleItem(o: JSONObject): EmbyItem {
        val imageTags = o.optJSONObject("ImageTags")
        val backdropTags = o.optJSONArray("BackdropImageTags")
        val parentBackdropTags = o.optJSONArray("ParentBackdropImageTags")
        val user = o.optJSONObject("UserData")
        val runTimeTicks = optLongOrNull(o, "RunTimeTicks")
        val rawPositionTicks = user?.optLong("PlaybackPositionTicks", 0L) ?: 0L
        val playedPercentage = user?.let { optDoubleOrNull(it, "PlayedPercentage") }
        val normalizedPositionTicks = when {
            rawPositionTicks > 0L -> rawPositionTicks
            runTimeTicks != null && runTimeTicks > 0L && playedPercentage != null && playedPercentage > 0.0 && playedPercentage < 99.5 ->
                (runTimeTicks * (playedPercentage / 100.0)).toLong()
            else -> 0L
        }
        return EmbyItem(
            id = o.optString("Id"), name = o.optString("Name"), type = o.optString("Type"), mediaType = o.optString("MediaType").ifBlank { null },
            parentId = o.optString("ParentId").ifBlank { null },
            overview = o.optString("Overview").ifBlank { null }, runTimeTicks = runTimeTicks, productionYear = optIntOrNull(o, "ProductionYear"),
            premiereDate = o.optString("PremiereDate").ifBlank { null }, dateCreated = o.optString("DateCreated").ifBlank { null },
            communityRating = optDoubleOrNull(o, "CommunityRating"), officialRating = o.optString("OfficialRating").ifBlank { null },
            path = o.optString("Path").ifBlank { null }, sortName = o.optString("SortName").ifBlank { null }, width = optIntOrNull(o, "Width"), height = optIntOrNull(o, "Height"),
            primaryImageTag = imageTags?.optString("Primary")?.ifBlank { null }, backdropImageTag = backdropTags?.optString(0)?.ifBlank { null },
            parentBackdropImageTag = parentBackdropTags?.optString(0)?.ifBlank { null }, parentBackdropItemId = o.optString("ParentBackdropItemId").ifBlank { null },
            seriesPrimaryImageTag = o.optString("SeriesPrimaryImageTag").ifBlank { null }, thumbImageTag = o.optString("SeriesThumbImageTag").ifBlank { null },
            seriesId = o.optString("SeriesId").ifBlank { null }, seriesName = o.optString("SeriesName").ifBlank { null }, seasonName = o.optString("SeasonName").ifBlank { null },
            album = o.optString("Album").ifBlank { null }, albumArtist = o.optString("AlbumArtist").ifBlank { null }, artists = stringList(o.optJSONArray("Artists")),
            indexNumber = optIntOrNull(o, "IndexNumber"), parentIndexNumber = optIntOrNull(o, "ParentIndexNumber"), childCount = optIntOrNull(o, "ChildCount"),
            recursiveItemCount = optIntOrNull(o, "RecursiveItemCount"), recursiveUnplayedItemCount = optIntOrNull(o, "RecursiveUnplayedItemCount"),
            genres = stringList(o.optJSONArray("Genres")), taglines = stringList(o.optJSONArray("Taglines")), people = parsePeople(o.optJSONArray("People")), mediaStreams = parseStreams(o.optJSONArray("MediaStreams")),
            userData = if (user == null) UserDataItem() else UserDataItem(
                playbackPositionTicks = normalizedPositionTicks, played = user.optBoolean("Played", false),
                isFavorite = user.optBoolean("IsFavorite", false), playedPercentage = playedPercentage,
                unplayedItemCount = optIntOrNull(user, "UnplayedItemCount"), lastPlayedDate = user.optString("LastPlayedDate").ifBlank { null }
            )
        )
    }

    private fun parsePeople(a: JSONArray?): List<EmbyPerson> = if (a == null) emptyList() else (0 until a.length()).mapNotNull { a.optJSONObject(it) }.map { o ->
        EmbyPerson(o.optString("Id"), o.optString("Name"), o.optString("Type").ifBlank { null }, o.optString("Role").ifBlank { null }, o.optString("PrimaryImageTag").ifBlank { null })
    }
    private fun parseStreams(a: JSONArray?): List<MediaStreamItem> = if (a == null) emptyList() else (0 until a.length()).mapNotNull { a.optJSONObject(it) }.map { o ->
        MediaStreamItem(o.optInt("Index"), o.optString("Type"), o.optString("Codec").ifBlank { null }, o.optString("Language").ifBlank { null }, o.optString("DisplayTitle").ifBlank { null }, o.optBoolean("IsDefault"), o.optBoolean("IsExternal"))
    }
    private fun stringList(a: JSONArray?): List<String> = if (a == null) emptyList() else (0 until a.length()).mapNotNull { a.optString(it).takeIf(String::isNotBlank) }
    private fun imageTag(o: JSONObject, type: String): String? = o.optJSONObject("ImageTags")?.optString(type)?.ifBlank { null }
    private fun optLongOrNull(o: JSONObject, key: String): Long? = if (!o.has(key) || o.isNull(key)) null else o.optLong(key)
    private fun optIntOrNull(o: JSONObject, key: String): Int? = if (!o.has(key) || o.isNull(key)) null else o.optInt(key)
    private fun optDoubleOrNull(o: JSONObject, key: String): Double? = if (!o.has(key) || o.isNull(key)) null else o.optDouble(key)
    private fun requireUser(): String = config().userId.takeIf { it.isNotBlank() } ?: error("未登录 Emby")
}

class HttpStatusException(val code: Int, val responseBody: String) : IOException("HTTP $code: ${responseBody.take(300)}")
