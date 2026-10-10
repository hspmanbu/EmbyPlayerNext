package com.embyplayernext.he.data.prefs

import android.content.Context
import android.content.res.Configuration
import android.content.pm.PackageManager
import com.embyplayernext.he.data.model.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

class AppPreferences(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences("emby_player_prefs", Context.MODE_PRIVATE)
    private val _config = MutableStateFlow(loadConfig())
    val config: StateFlow<EmbyServerConfig> = _config

    fun loadConfig(): EmbyServerConfig = runCatching {
        val servers = getSavedServers()
        val activeId = prefs.getString("active_server_id", null)
        val activeServer = servers.firstOrNull { it.id == activeId } ?: servers.firstOrNull()

        val legacyUrl = prefs.getString("server_url", "") ?: ""
        val legacyToken = prefs.getString("access_token", "") ?: ""
        val effectiveServer = activeServer ?: if (legacyUrl.isNotBlank() && legacyToken.isNotBlank()) {
            val initial = SavedServerProfile(
                serverUrl = legacyUrl,
                username = prefs.getString("username", "") ?: "",
                userId = prefs.getString("user_id", "") ?: "",
                accessToken = legacyToken,
                serverName = prefs.getString("server_name", "Emby Server") ?: "Emby Server",
                dynamicPortEnabled = prefs.getBoolean("dynamic_port_enabled", false),
                dynamicPortTargetDomain = prefs.getString("dynamic_port_domain", "") ?: "",
                dynamicPortTimeoutSeconds = safeInt("dynamic_port_timeout", 5),
                dynamicPortFetchUrl = prefs.getString("dynamic_port_fetch_url", "") ?: "",
                dynamicPortServiceName = prefs.getString("dynamic_port_service_name", "") ?: "",
                allowInsecureHttps = prefs.getBoolean("allow_insecure_https", false),
                deepBufferMode = prefs.getBoolean("deep_buffer_mode", false),
                preferIpv4Dns = prefs.getBoolean("prefer_ipv4_dns", true),
                imageCacheMb = safeInt("image_cache_mb", 256),
            )
            saveServerProfile(initial)
            initial
        } else null

        EmbyServerConfig(
            serverUrl = effectiveServer?.serverUrl ?: legacyUrl,
            username = effectiveServer?.username ?: (prefs.getString("username", "") ?: ""),
            userId = effectiveServer?.userId ?: (prefs.getString("user_id", "") ?: ""),
            accessToken = effectiveServer?.accessToken ?: legacyToken,
            serverName = effectiveServer?.serverName ?: (prefs.getString("server_name", "Emby Server") ?: "Emby Server"),
            dynamicPortEnabled = effectiveServer?.dynamicPortEnabled ?: prefs.getBoolean("dynamic_port_enabled", false),
            dynamicPortTargetDomain = effectiveServer?.dynamicPortTargetDomain ?: (prefs.getString("dynamic_port_domain", "") ?: ""),
            dynamicPortTimeoutSeconds = effectiveServer?.dynamicPortTimeoutSeconds ?: safeInt("dynamic_port_timeout", 5),
            dynamicPortFetchUrl = effectiveServer?.dynamicPortFetchUrl ?: (prefs.getString("dynamic_port_fetch_url", "") ?: ""),
            dynamicPortServiceName = effectiveServer?.dynamicPortServiceName ?: (prefs.getString("dynamic_port_service_name", "") ?: ""),
            uiScale = safeFloat("ui_scale", defaultUiScale()),
            hardwareDecoding = prefs.getBoolean("hardware_decoding", true),
            hardwareCompatibilityMode = prefs.getBoolean("hardware_compatibility_mode", false),
            seekSeconds = safeInt("seek_seconds", 10),
            rewindSeconds = safeInt("rewind_seconds", 10),
            forwardSeconds = safeInt("forward_seconds", 30),
            cacheMb = safeInt("disk_cache_mb", 128),
            gestureSeekSeconds = safeInt("gesture_seek_seconds", 180),
            allowInsecureHttps = effectiveServer?.allowInsecureHttps ?: prefs.getBoolean("allow_insecure_https", false),
            autoPlayNextEpisode = prefs.getBoolean("auto_play_next_episode", true),
            rememberPlaybackSpeed = prefs.getBoolean("remember_playback_speed", true),
            lastPlaybackSpeed = safeFloat("last_playback_speed", 1.0f),
            deepBufferMode = effectiveServer?.deepBufferMode ?: prefs.getBoolean("deep_buffer_mode", false),
            preferIpv4Dns = effectiveServer?.preferIpv4Dns ?: prefs.getBoolean("prefer_ipv4_dns", true),
            imageCacheMb = safeInt("image_cache_mb", 256),
        )
    }.getOrElse {
        EmbyServerConfig(uiScale = defaultUiScale())
    }

    private fun defaultUiScale(): Float {
        val configuration = appContext.resources.configuration
        val isTv = (configuration.uiMode and Configuration.UI_MODE_TYPE_MASK) == Configuration.UI_MODE_TYPE_TELEVISION ||
            appContext.packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)
        val isTablet = configuration.smallestScreenWidthDp >= 600
        return if (isTv || isTablet) 1.4f else 1f
    }

    private fun safeInt(key: String, default: Int): Int = try { prefs.getInt(key, default) } catch (_: ClassCastException) {
        prefs.getString(key, null)?.toIntOrNull() ?: default
    }
    private fun safeFloat(key: String, default: Float): Float = try { prefs.getFloat(key, default) } catch (_: ClassCastException) {
        prefs.getString(key, null)?.toFloatOrNull() ?: default
    }

    fun getSavedServers(): List<SavedServerProfile> {
        val raw = prefs.getString("saved_server_profiles", null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                val url = o.optString("serverUrl")
                if (url.isBlank()) return@mapNotNull null
                SavedServerProfile(
                    id = o.optString("id").ifBlank { java.util.UUID.randomUUID().toString() },
                    serverUrl = url,
                    serverName = o.optString("serverName", "Emby Server").ifBlank { "Emby Server" },
                    username = o.optString("username"),
                    userId = o.optString("userId"),
                    accessToken = o.optString("accessToken"),
                    lastConnected = o.optLong("lastConnected", 0L),
                    dynamicPortEnabled = o.optBoolean("dynamicPortEnabled", false),
                    dynamicPortTargetDomain = o.optString("dynamicPortTargetDomain"),
                    dynamicPortTimeoutSeconds = o.optInt("dynamicPortTimeoutSeconds", 5).coerceIn(2, 60),
                    dynamicPortFetchUrl = o.optString("dynamicPortFetchUrl"),
                    dynamicPortServiceName = o.optString("dynamicPortServiceName"),
                    allowInsecureHttps = o.optBoolean("allowInsecureHttps", false),
                    deepBufferMode = o.optBoolean("deepBufferMode", false),
                    preferIpv4Dns = o.optBoolean("preferIpv4Dns", true),
                    imageCacheMb = o.optInt("imageCacheMb", 256),
                )
            }.sortedByDescending { it.lastConnected }
        }.getOrDefault(emptyList())
    }

    fun extractHost(url: String): String {
        val normalized = if (url.startsWith("http://") || url.startsWith("https://")) url else "http://$url"
        return normalized.toHttpUrlOrNull()?.host.orEmpty()
    }

    fun saveServerProfiles(profiles: List<SavedServerProfile>, activeProfileId: String? = null) {
        val arr = JSONArray()
        for (p in profiles) {
            arr.put(
                JSONObject()
                    .put("id", p.id)
                    .put("serverUrl", p.serverUrl.trimEnd('/'))
                    .put("serverName", p.serverName)
                    .put("username", p.username)
                    .put("userId", p.userId)
                    .put("accessToken", p.accessToken)
                    .put("lastConnected", p.lastConnected)
                    .put("dynamicPortEnabled", p.dynamicPortEnabled)
                    .put("dynamicPortTargetDomain", p.dynamicPortTargetDomain)
                    .put("dynamicPortTimeoutSeconds", p.dynamicPortTimeoutSeconds)
                    .put("dynamicPortFetchUrl", p.dynamicPortFetchUrl)
                    .put("dynamicPortServiceName", p.dynamicPortServiceName)
                    .put("allowInsecureHttps", p.allowInsecureHttps)
                    .put("deepBufferMode", p.deepBufferMode)
                    .put("preferIpv4Dns", p.preferIpv4Dns)
                    .put("imageCacheMb", p.imageCacheMb)
            )
        }
        val editor = prefs.edit().putString("saved_server_profiles", arr.toString())
        if (activeProfileId != null) {
            editor.putString("active_server_id", activeProfileId)
        }
        editor.apply()
    }

    fun saveServerProfile(profile: SavedServerProfile, makeActive: Boolean = false) {
        val current = getSavedServers().filterNot {
            it.id == profile.id || (it.serverUrl.trimEnd('/') == profile.serverUrl.trimEnd('/') && it.username.equals(profile.username, ignoreCase = true))
        }
        val target = if (makeActive) profile.copy(lastConnected = System.currentTimeMillis()) else profile
        val updated = (listOf(target) + current).sortedByDescending { it.lastConnected }
        saveServerProfiles(updated, activeProfileId = if (makeActive) target.id else null)
    }

    fun saveLogin(
        serverUrl: String,
        serverName: String,
        username: String,
        userId: String,
        accessToken: String,
    ): EmbyServerConfig {
        val normalizedUrl = serverUrl.trim().trimEnd('/')
        val currentServers = getSavedServers()
        val existing = currentServers.firstOrNull {
            it.serverUrl.trimEnd('/') == normalizedUrl && it.username.equals(username, ignoreCase = true)
        }
        val sameServerAnotherAccount = currentServers.firstOrNull {
            val h1 = extractHost(it.serverUrl)
            val h2 = extractHost(normalizedUrl)
            h1.isNotBlank() && h1.equals(h2, ignoreCase = true)
        }
        val globalConfig = _config.value
        val profileId = existing?.id ?: java.util.UUID.randomUUID().toString()
        val defaultDynamicPortEnabled = sameServerAnotherAccount?.dynamicPortEnabled ?: globalConfig.dynamicPortEnabled
        val defaultDynamicDomain = (sameServerAnotherAccount?.dynamicPortTargetDomain ?: globalConfig.dynamicPortTargetDomain).ifBlank { extractHost(normalizedUrl) }
        val defaultDynamicTimeout = sameServerAnotherAccount?.dynamicPortTimeoutSeconds ?: globalConfig.dynamicPortTimeoutSeconds
        val defaultDynamicFetchUrl = sameServerAnotherAccount?.dynamicPortFetchUrl ?: globalConfig.dynamicPortFetchUrl
        val defaultDynamicServiceName = sameServerAnotherAccount?.dynamicPortServiceName ?: globalConfig.dynamicPortServiceName
        val defaultInsecure = sameServerAnotherAccount?.allowInsecureHttps ?: globalConfig.allowInsecureHttps
        val defaultDeepBuffer = sameServerAnotherAccount?.deepBufferMode ?: globalConfig.deepBufferMode
        val defaultPreferIpv4 = sameServerAnotherAccount?.preferIpv4Dns ?: globalConfig.preferIpv4Dns
        val defaultImageCacheMb = sameServerAnotherAccount?.imageCacheMb ?: globalConfig.imageCacheMb
        val resolvedServerName = serverName.ifBlank {
            existing?.serverName ?: sameServerAnotherAccount?.serverName ?: "Emby Server"
        }

        val newProfile = (existing ?: SavedServerProfile(
            id = profileId,
            serverUrl = normalizedUrl,
            username = username,
            dynamicPortEnabled = defaultDynamicPortEnabled,
            dynamicPortTargetDomain = defaultDynamicDomain,
            dynamicPortTimeoutSeconds = defaultDynamicTimeout,
            dynamicPortFetchUrl = defaultDynamicFetchUrl,
            dynamicPortServiceName = defaultDynamicServiceName,
            allowInsecureHttps = defaultInsecure,
            deepBufferMode = defaultDeepBuffer,
            preferIpv4Dns = defaultPreferIpv4,
            imageCacheMb = defaultImageCacheMb,
        )).copy(
            id = profileId,
            serverUrl = normalizedUrl,
            serverName = resolvedServerName,
            username = username,
            userId = userId,
            accessToken = accessToken,
            lastConnected = System.currentTimeMillis(),
        )
        saveServerProfile(newProfile, makeActive = true)

        val updated = _config.value.copy(
            serverUrl = newProfile.serverUrl,
            username = newProfile.username,
            userId = newProfile.userId,
            accessToken = newProfile.accessToken,
            serverName = newProfile.serverName,
            dynamicPortEnabled = newProfile.dynamicPortEnabled,
            dynamicPortTargetDomain = newProfile.dynamicPortTargetDomain,
            dynamicPortTimeoutSeconds = newProfile.dynamicPortTimeoutSeconds,
            dynamicPortFetchUrl = newProfile.dynamicPortFetchUrl,
            dynamicPortServiceName = newProfile.dynamicPortServiceName,
            allowInsecureHttps = newProfile.allowInsecureHttps,
            deepBufferMode = newProfile.deepBufferMode,
            preferIpv4Dns = newProfile.preferIpv4Dns,
            imageCacheMb = newProfile.imageCacheMb,
        )
        applyConfigToSharedPreferences(updated)
        _config.value = updated
        return updated
    }

    fun removeServerProfile(serverId: String) {
        val current = getSavedServers().filterNot { it.id == serverId }
        val isActive = prefs.getString("active_server_id", "") == serverId
        saveServerProfiles(current, activeProfileId = if (isActive) current.firstOrNull()?.id else null)

        if (isActive) {
            val next = current.firstOrNull()
            if (next != null) {
                switchToServer(next.id)
            } else {
                prefs.edit().remove("active_server_id").apply()
                clearLogin()
            }
        }
    }

    fun switchToServer(serverId: String): Boolean {
        val target = getSavedServers().firstOrNull { it.id == serverId } ?: return false
        val updatedTarget = target.copy(lastConnected = System.currentTimeMillis())
        saveServerProfile(updatedTarget, makeActive = true)
        val updated = _config.value.copy(
            serverUrl = updatedTarget.serverUrl,
            username = updatedTarget.username,
            userId = updatedTarget.userId,
            accessToken = updatedTarget.accessToken,
            serverName = updatedTarget.serverName,
            dynamicPortEnabled = updatedTarget.dynamicPortEnabled,
            dynamicPortTargetDomain = updatedTarget.dynamicPortTargetDomain,
            dynamicPortTimeoutSeconds = updatedTarget.dynamicPortTimeoutSeconds,
            dynamicPortFetchUrl = updatedTarget.dynamicPortFetchUrl,
            dynamicPortServiceName = updatedTarget.dynamicPortServiceName,
            allowInsecureHttps = updatedTarget.allowInsecureHttps,
            deepBufferMode = updatedTarget.deepBufferMode,
            preferIpv4Dns = updatedTarget.preferIpv4Dns,
            imageCacheMb = updatedTarget.imageCacheMb,
        )
        applyConfigToSharedPreferences(updated)
        _config.value = updated
        return true
    }

    private fun applyConfigToSharedPreferences(c: EmbyServerConfig) {
        prefs.edit()
            .putString("server_url", c.serverUrl.trimEnd('/'))
            .putString("username", c.username)
            .putString("user_id", c.userId)
            .putString("access_token", c.accessToken)
            .putString("server_name", c.serverName)
            .putBoolean("dynamic_port_enabled", c.dynamicPortEnabled)
            .putString("dynamic_port_domain", c.dynamicPortTargetDomain)
            .putInt("dynamic_port_timeout", c.dynamicPortTimeoutSeconds)
            .putString("dynamic_port_fetch_url", c.dynamicPortFetchUrl)
            .putString("dynamic_port_service_name", c.dynamicPortServiceName)
            .putFloat("ui_scale", c.uiScale)
            .putBoolean("hardware_decoding", c.hardwareDecoding)
            .putBoolean("hardware_compatibility_mode", c.hardwareCompatibilityMode)
            .putInt("seek_seconds", c.seekSeconds)
            .putInt("rewind_seconds", c.rewindSeconds)
            .putInt("forward_seconds", c.forwardSeconds)
            .putInt("disk_cache_mb", c.cacheMb)
            .putInt("image_cache_mb", c.imageCacheMb)
            .putInt("gesture_seek_seconds", c.gestureSeekSeconds)
            .putBoolean("allow_insecure_https", c.allowInsecureHttps)
            .putBoolean("auto_play_next_episode", c.autoPlayNextEpisode)
            .putBoolean("remember_playback_speed", c.rememberPlaybackSpeed)
            .putFloat("last_playback_speed", c.lastPlaybackSpeed)
            .putBoolean("deep_buffer_mode", c.deepBufferMode)
            .putBoolean("prefer_ipv4_dns", c.preferIpv4Dns)
            .apply()
    }

    fun update(transform: (EmbyServerConfig) -> EmbyServerConfig) {
        val c = transform(_config.value)
        applyConfigToSharedPreferences(c)

        val activeId = prefs.getString("active_server_id", null)
        val servers = getSavedServers()
        val currentProfile = servers.firstOrNull { it.id == activeId && it.username.equals(c.username, ignoreCase = true) }
            ?: servers.firstOrNull { it.serverUrl.trimEnd('/') == c.serverUrl.trimEnd('/') && it.username.equals(c.username, ignoreCase = true) }
            ?: servers.firstOrNull { it.id == activeId }

        if (currentProfile != null) {
            val host = extractHost(c.serverUrl)
            val updatedServers = servers.map { p ->
                if (p.id == currentProfile.id) {
                    p.copy(
                        serverUrl = c.serverUrl,
                        serverName = c.serverName,
                        username = c.username,
                        userId = c.userId,
                        accessToken = c.accessToken,
                        dynamicPortEnabled = c.dynamicPortEnabled,
                        dynamicPortTargetDomain = c.dynamicPortTargetDomain,
                        dynamicPortTimeoutSeconds = c.dynamicPortTimeoutSeconds,
                        dynamicPortFetchUrl = c.dynamicPortFetchUrl,
                        dynamicPortServiceName = c.dynamicPortServiceName,
                        allowInsecureHttps = c.allowInsecureHttps,
                        deepBufferMode = c.deepBufferMode,
                        preferIpv4Dns = c.preferIpv4Dns,
                        imageCacheMb = c.imageCacheMb,
                    )
                } else if (host.isNotBlank() && extractHost(p.serverUrl).equals(host, ignoreCase = true)) {
                    // 同服务器下的其他账号仅同步主机级网络参数，绝不覆盖账号标识和凭据！
                    p.copy(
                        dynamicPortEnabled = c.dynamicPortEnabled,
                        dynamicPortTargetDomain = c.dynamicPortTargetDomain,
                        dynamicPortTimeoutSeconds = c.dynamicPortTimeoutSeconds,
                        dynamicPortFetchUrl = c.dynamicPortFetchUrl,
                        dynamicPortServiceName = c.dynamicPortServiceName,
                        allowInsecureHttps = c.allowInsecureHttps,
                        deepBufferMode = c.deepBufferMode,
                        preferIpv4Dns = c.preferIpv4Dns,
                        imageCacheMb = c.imageCacheMb,
                    )
                } else {
                    p
                }
            }
            saveServerProfiles(updatedServers, activeProfileId = currentProfile.id)
        }

        _config.value = c
    }

    fun updateServerPort(serverUrl: String) {
        val normalized = serverUrl.trim().trimEnd('/')
        val host = extractHost(normalized)
        val newPort = (if (normalized.startsWith("http://") || normalized.startsWith("https://")) normalized else "http://$normalized").toHttpUrlOrNull()?.port
        if (host.isNotBlank() && newPort != null) {
            val servers = getSavedServers()
            val updatedServers = servers.map { p ->
                if (extractHost(p.serverUrl).equals(host, ignoreCase = true)) {
                    val pHttp = (if (p.serverUrl.startsWith("http://") || p.serverUrl.startsWith("https://")) p.serverUrl else "http://${p.serverUrl}").toHttpUrlOrNull()
                    if (pHttp != null && pHttp.port != newPort) {
                        val newPUrl = pHttp.newBuilder().port(newPort).build().toString().trimEnd('/')
                        p.copy(serverUrl = newPUrl)
                    } else p
                } else p
            }
            saveServerProfiles(updatedServers)
        }
        update { it.copy(serverUrl = normalized) }
    }
    fun clearLogin() {
        val activeId = prefs.getString("active_server_id", null)
        if (activeId != null) {
            val servers = getSavedServers()
            val target = servers.firstOrNull { it.id == activeId }
            if (target != null) {
                saveServerProfile(target.copy(accessToken = ""))
            }
        }
        update { it.copy(userId = "", accessToken = "") }
    }

    fun getRecentSearches(): List<String> = prefs.getString("recent_searches", "").orEmpty()
        .split('\u001F')
        .map(String::trim)
        .filter(String::isNotBlank)
        .distinct()
        .take(10)

    fun saveRecentSearch(query: String): List<String> {
        val value = query.trim().replace("\u001F", " ")
        if (value.isBlank()) return getRecentSearches()
        val next = (listOf(value) + getRecentSearches().filterNot { it.equals(value, true) }).take(10)
        prefs.edit().putString("recent_searches", next.joinToString("\u001F")).apply()
        return next
    }

    fun clearRecentSearches() { prefs.edit().remove("recent_searches").apply() }

    fun saveLibrarySort(libraryId: String, field: EmbySortField, order: EmbySortOrder) {
        prefs.edit().putString("lib_sort_field_$libraryId", field.name).putString("lib_sort_order_$libraryId", order.name).apply()
    }
    fun getLibrarySort(libraryId: String): Pair<EmbySortField, EmbySortOrder> {
        val f = runCatching { EmbySortField.valueOf(prefs.getString("lib_sort_field_$libraryId", EmbySortField.SORT_NAME.name)!!) }.getOrDefault(EmbySortField.SORT_NAME)
        val o = runCatching { EmbySortOrder.valueOf(prefs.getString("lib_sort_order_$libraryId", EmbySortOrder.ASCENDING.name)!!) }.getOrDefault(EmbySortOrder.ASCENDING)
        return f to o
    }

    fun saveLibraryColumns(libraryId: String, columns: Int) {
        prefs.edit().putInt("lib_columns_$libraryId", columns.coerceIn(0, 8)).apply()
    }
    fun getLibraryColumns(libraryId: String): Int = safeInt("lib_columns_$libraryId", 0).coerceIn(0, 8)

    fun saveLibraryLayoutMode(libraryId: String, mode: LibraryLayoutMode) {
        prefs.edit().putString("lib_layout_mode_$libraryId", mode.name).apply()
    }
    fun getLibraryLayoutMode(libraryId: String): LibraryLayoutMode {
        val name = prefs.getString("lib_layout_mode_$libraryId", LibraryLayoutMode.GRID.name)
        return runCatching { LibraryLayoutMode.valueOf(name!!) }.getOrDefault(LibraryLayoutMode.GRID)
    }
}
