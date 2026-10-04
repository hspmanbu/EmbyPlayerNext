package com.embyplayernext.he.data.prefs

import android.content.Context
import android.content.res.Configuration
import android.content.pm.PackageManager
import com.embyplayernext.he.data.model.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class AppPreferences(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences("emby_player_prefs", Context.MODE_PRIVATE)
    private val _config = MutableStateFlow(loadConfig())
    val config: StateFlow<EmbyServerConfig> = _config

    fun loadConfig(): EmbyServerConfig = runCatching {
        EmbyServerConfig(
            serverUrl = prefs.getString("server_url", "") ?: "",
            username = prefs.getString("username", "") ?: "",
            userId = prefs.getString("user_id", "") ?: "",
            accessToken = prefs.getString("access_token", "") ?: "",
            serverName = prefs.getString("server_name", "Emby Server") ?: "Emby Server",
            dynamicPortEnabled = prefs.getBoolean("dynamic_port_enabled", false),
            dynamicPortTargetDomain = prefs.getString("dynamic_port_domain", "") ?: "",
            dynamicPortTimeoutSeconds = safeInt("dynamic_port_timeout", 5),
            dynamicPortFetchUrl = prefs.getString("dynamic_port_fetch_url", "") ?: "",
            dynamicPortServiceName = prefs.getString("dynamic_port_service_name", "") ?: "",
            uiScale = safeFloat("ui_scale", defaultUiScale()),
            hardwareDecoding = prefs.getBoolean("hardware_decoding", true),
            hardwareCompatibilityMode = prefs.getBoolean("hardware_compatibility_mode", false),
            seekSeconds = safeInt("seek_seconds", 10),
            rewindSeconds = safeInt("rewind_seconds", 10),
            forwardSeconds = safeInt("forward_seconds", 30),
            cacheMb = safeInt("disk_cache_mb", 128),
            gestureSeekSeconds = safeInt("gesture_seek_seconds", 180),
            allowInsecureHttps = prefs.getBoolean("allow_insecure_https", false),
            autoPlayNextEpisode = prefs.getBoolean("auto_play_next_episode", true),
        )
    }.getOrElse {
        // Preserve startup even if an older build stored a key with a different primitive type.
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

    fun update(transform: (EmbyServerConfig) -> EmbyServerConfig) {
        val c = transform(_config.value)
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
            .putInt("gesture_seek_seconds", c.gestureSeekSeconds)
            .putBoolean("allow_insecure_https", c.allowInsecureHttps)
            .putBoolean("auto_play_next_episode", c.autoPlayNextEpisode)
            .apply()
        _config.value = c
    }

    fun updateServerPort(serverUrl: String) = update { it.copy(serverUrl = serverUrl) }
    fun clearLogin() = update { it.copy(userId = "", accessToken = "", username = "") }

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
