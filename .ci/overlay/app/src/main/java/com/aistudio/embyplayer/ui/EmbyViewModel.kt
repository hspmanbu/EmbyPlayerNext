package com.aistudio.embyplayer.ui

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aistudio.embyplayer.data.model.*
import com.aistudio.embyplayer.data.network.EmbyApiClient
import com.aistudio.embyplayer.data.prefs.AppPreferences
import com.aistudio.embyplayer.util.DiagnosticsLogger
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class AppScreen { HOME, SEARCH, LIBRARY, DETAIL, SETTINGS, DYNAMIC_PORT, PLAYER }
sealed interface DynamicPortStatus {
    data object Idle : DynamicPortStatus
    data object Resolving : DynamicPortStatus
    data class Success(val port: Int) : DynamicPortStatus
    data class Error(val message: String) : DynamicPortStatus
}

data class Breadcrumb(val id: String, val name: String)

class EmbyViewModel(app: Application) : AndroidViewModel(app) {
    private val prefs = AppPreferences(app)
    private val logger = DiagnosticsLogger(app)
    private val api = EmbyApiClient(app, prefs, logger)

    val config: StateFlow<EmbyServerConfig> = prefs.config
    private val _screen = MutableStateFlow(AppScreen.HOME); val screen = _screen.asStateFlow()
    private val _views = MutableStateFlow<List<EmbyView>>(emptyList()); val views = _views.asStateFlow()
    private val _resumeItems = MutableStateFlow<List<EmbyItem>>(emptyList()); val resumeItems = _resumeItems.asStateFlow()
    private val _latestItems = MutableStateFlow<List<EmbyItem>>(emptyList()); val latestItems = _latestItems.asStateFlow()
    private val _favoriteItems = MutableStateFlow<List<EmbyItem>>(emptyList()); val favoriteItems = _favoriteItems.asStateFlow()
    private val _items = MutableStateFlow<List<EmbyItem>>(emptyList()); val items = _items.asStateFlow()
    private val _breadcrumbs = MutableStateFlow<List<Breadcrumb>>(emptyList()); val breadcrumbs = _breadcrumbs.asStateFlow()
    private val _currentLibrary = MutableStateFlow<EmbyView?>(null); val currentLibrary = _currentLibrary.asStateFlow()
    private val _selectedItem = MutableStateFlow<EmbyItem?>(null); val selectedItem = _selectedItem.asStateFlow()
    private val _seasons = MutableStateFlow<List<EmbyItem>>(emptyList()); val seasons = _seasons.asStateFlow()
    private val _episodes = MutableStateFlow<List<EmbyItem>>(emptyList()); val episodes = _episodes.asStateFlow()
    private val _personWorks = MutableStateFlow<List<EmbyItem>>(emptyList()); val personWorks = _personWorks.asStateFlow()
    private val _selectedPerson = MutableStateFlow<EmbyPerson?>(null); val selectedPerson = _selectedPerson.asStateFlow()
    private val _activePlayback = MutableStateFlow<PlaybackDescriptor?>(null); val activePlayback = _activePlayback.asStateFlow()
    private val _displayMode = MutableStateFlow(EmbyDisplayMode.MOVIES); val displayMode = _displayMode.asStateFlow()
    private val _sortField = MutableStateFlow(EmbySortField.SORT_NAME); val sortField = _sortField.asStateFlow()
    private val _sortOrder = MutableStateFlow(EmbySortOrder.ASCENDING); val sortOrder = _sortOrder.asStateFlow()
    private val _searchQuery = MutableStateFlow(""); val searchQuery = _searchQuery.asStateFlow()
    private val _globalSearchQuery = MutableStateFlow(""); val globalSearchQuery = _globalSearchQuery.asStateFlow()
    private val _globalSearchResults = MutableStateFlow<List<EmbyItem>>(emptyList()); val globalSearchResults = _globalSearchResults.asStateFlow()
    private val _searchLoading = MutableStateFlow(false); val searchLoading = _searchLoading.asStateFlow()
    private val _loading = MutableStateFlow(false); val loading = _loading.asStateFlow()
    private val _message = MutableStateFlow<String?>(null); val message = _message.asStateFlow()
    private val _dynamicPortStatus = MutableStateFlow<DynamicPortStatus>(DynamicPortStatus.Idle); val dynamicPortStatus = _dynamicPortStatus.asStateFlow()

    private var detailReturnScreen: AppScreen = AppScreen.HOME
    private var globalSearchJob: Job? = null

    init { if (prefs.config.value.accessToken.isNotBlank()) loadHome() }

    fun clearMessage() { _message.value = null }

    fun goHome() {
        _screen.value = AppScreen.HOME
        _currentLibrary.value = null
        _breadcrumbs.value = emptyList()
        loadHome()
    }

    fun openSearch() {
        _screen.value = AppScreen.SEARCH
        _currentLibrary.value = null
        _breadcrumbs.value = emptyList()
    }

    fun openSettings() { _screen.value = AppScreen.SETTINGS }
    fun openDynamicPort() { _screen.value = AppScreen.DYNAMIC_PORT }

    fun login(server: String, username: String, password: String, done: (Boolean) -> Unit = {}) = launchBusy {
        api.login(server, username, password).onSuccess {
            _message.value = "✓ 已连接 ${it.serverName}"
            _screen.value = AppScreen.HOME
            loadHome()
            done(true)
        }.onFailure {
            _message.value = "登录失败: ${it.message}"
            done(false)
        }
    }

    fun testConnection(server: String, done: (Result<String>) -> Unit) {
        prefs.update { it.copy(serverUrl = server.trimEnd('/')) }
        viewModelScope.launch { done(api.testConnection()) }
    }

    fun logout() {
        prefs.clearLogin()
        _views.value = emptyList()
        _resumeItems.value = emptyList()
        _latestItems.value = emptyList()
        _favoriteItems.value = emptyList()
        _globalSearchResults.value = emptyList()
        _screen.value = AppScreen.HOME
    }

    fun loadHome() = launchBusy {
        runCatching { api.getViews() }
            .onSuccess { _views.value = it }
            .onFailure { _message.value = "媒体库加载失败: ${it.message}" }
        runCatching { api.getResumeItems() }
            .onSuccess { _resumeItems.value = it }
        runCatching {
            api.getItems(null, EmbyDisplayMode.MOVIES, sortField = EmbySortField.DATE_CREATED, sortOrder = EmbySortOrder.DESCENDING, recursive = true, limit = 36)
        }.onSuccess { _latestItems.value = it }
        runCatching {
            api.getItems(null, EmbyDisplayMode.FAVORITES, sortField = EmbySortField.DATE_CREATED, sortOrder = EmbySortOrder.DESCENDING, recursive = true, limit = 30)
        }.onSuccess { _favoriteItems.value = it }
    }

    fun setGlobalSearch(query: String) {
        _globalSearchQuery.value = query
        globalSearchJob?.cancel()
        if (query.isBlank()) {
            _globalSearchResults.value = emptyList()
            _searchLoading.value = false
            return
        }
        globalSearchJob = viewModelScope.launch {
            delay(320)
            _searchLoading.value = true
            runCatching { api.getItems(null, EmbyDisplayMode.MOVIES, query, EmbySortField.SORT_NAME, EmbySortOrder.ASCENDING, recursive = true, limit = 120) }
                .onSuccess { _globalSearchResults.value = it }
                .onFailure { _message.value = "搜索失败: ${it.message}" }
            _searchLoading.value = false
        }
    }

    fun openLibrary(view: EmbyView) {
        _currentLibrary.value = view
        val (f, o) = prefs.getLibrarySort(view.id)
        _sortField.value = f
        _sortOrder.value = o
        _displayMode.value = EmbyDisplayMode.MOVIES
        _searchQuery.value = ""
        _breadcrumbs.value = listOf(Breadcrumb(view.id, view.name))
        _screen.value = AppScreen.LIBRARY
        loadCurrentLibrary()
    }

    fun setDisplayMode(mode: EmbyDisplayMode) { _displayMode.value = mode; loadCurrentLibrary() }

    fun setSort(field: EmbySortField, order: EmbySortOrder) {
        _sortField.value = field
        _sortOrder.value = order
        _currentLibrary.value?.let { prefs.saveLibrarySort(it.id, field, order) }
        loadCurrentLibrary()
    }

    fun setSearch(query: String) { _searchQuery.value = query; loadCurrentLibrary() }

    fun openFolder(item: EmbyItem) {
        _breadcrumbs.value = _breadcrumbs.value + Breadcrumb(item.id, item.name)
        _searchQuery.value = ""
        loadCurrentLibrary()
    }

    fun navigateToBreadcrumb(index: Int) {
        _breadcrumbs.value = _breadcrumbs.value.take(index + 1)
        _searchQuery.value = ""
        loadCurrentLibrary()
    }

    fun backFromLibrary(): Boolean {
        if (_breadcrumbs.value.size > 1) {
            _breadcrumbs.value = _breadcrumbs.value.dropLast(1)
            loadCurrentLibrary()
            return true
        }
        goHome()
        return false
    }

    private fun loadCurrentLibrary() = launchBusy {
        val parent = _breadcrumbs.value.lastOrNull()?.id ?: _currentLibrary.value?.id ?: return@launchBusy
        runCatching {
            api.getItems(parent, _displayMode.value, _searchQuery.value, _sortField.value, _sortOrder.value, recursive = _searchQuery.value.isNotBlank())
        }.onSuccess { _items.value = it }
            .onFailure { _message.value = "加载项目失败: ${it.message}" }
    }

    fun openItem(item: EmbyItem) {
        if (_screen.value == AppScreen.LIBRARY && item.type in setOf("Folder", "CollectionFolder")) {
            openFolder(item)
            return
        }
        if (_screen.value != AppScreen.DETAIL) detailReturnScreen = _screen.value
        _selectedItem.value = item
        _screen.value = AppScreen.DETAIL
        refreshDetail(item.id)
    }

    fun refreshDetail(id: String? = null) = launchBusy {
        val targetId = id ?: _selectedItem.value?.id ?: return@launchBusy
        runCatching {
            val detail = api.getItemDetails(targetId)
            _selectedItem.value = detail
            if (detail.type == "Series") {
                _seasons.value = api.getSeasons(detail.id)
                val first = _seasons.value.firstOrNull()
                _episodes.value = api.getEpisodes(detail.id, first?.id)
            } else {
                _seasons.value = emptyList()
                _episodes.value = emptyList()
            }
        }.onFailure { _message.value = "详情加载失败: ${it.message}" }
    }

    fun selectSeason(season: EmbyItem) = launchBusy {
        val series = _selectedItem.value ?: return@launchBusy
        runCatching { api.getEpisodes(series.id, season.id) }
            .onSuccess { _episodes.value = it }
            .onFailure { _message.value = "暂无集数信息: ${it.message}" }
    }

    fun selectPerson(person: EmbyPerson) = launchBusy {
        _selectedPerson.value = person
        runCatching { api.getItemsByPerson(person.id) }
            .onSuccess { _personWorks.value = it }
            .onFailure { _message.value = "演职人员作品加载失败: ${it.message}" }
    }

    fun closePersonWorks() { _selectedPerson.value = null; _personWorks.value = emptyList() }

    fun toggleFavorite() = launchBusy {
        val i = _selectedItem.value ?: return@launchBusy
        runCatching { api.toggleFavorite(i) }
            .onSuccess { _selectedItem.value = it; loadHome() }
            .onFailure { _message.value = "操作失败: ${it.message}" }
    }

    fun togglePlayed() = launchBusy {
        val i = _selectedItem.value ?: return@launchBusy
        runCatching { api.togglePlayed(i) }
            .onSuccess { _selectedItem.value = it; loadHome() }
            .onFailure { _message.value = "操作失败: ${it.message}" }
    }

    fun deleteSelected(done: () -> Unit = {}) = launchBusy {
        val i = _selectedItem.value ?: return@launchBusy
        runCatching { api.deleteItem(i.id) }.onSuccess {
            _message.value = "《${i.name}》已从服务器删除"
            done()
            _screen.value = detailReturnScreen
            when (detailReturnScreen) {
                AppScreen.LIBRARY -> loadCurrentLibrary()
                AppScreen.SEARCH -> setGlobalSearch(_globalSearchQuery.value)
                else -> loadHome()
            }
        }.onFailure { _message.value = "删除失败: ${it.message}" }
    }

    fun play(item: EmbyItem? = null, resume: Boolean = true) = launchBusy {
        val target = item ?: _selectedItem.value ?: return@launchBusy
        runCatching { api.preparePlayback(target, resume) }
            .onSuccess { _activePlayback.value = it; _screen.value = AppScreen.PLAYER }
            .onFailure { _message.value = "播放准备失败: ${it.message}" }
    }

    fun backFromDetail() { _screen.value = detailReturnScreen }

    fun closePlayer() {
        val id = _activePlayback.value?.item?.id
        _activePlayback.value = null
        _screen.value = AppScreen.DETAIL
        if (id != null) refreshDetail(id)
        loadHome()
    }

    suspend fun reportStart(d: PlaybackDescriptor, position: Long, duration: Long?) {
        runCatching { api.reportPlaybackStart(d, position, duration) }.onFailure { logger.log("Playback", "start error ${it.message}") }
    }

    suspend fun reportProgress(d: PlaybackDescriptor, position: Long, duration: Long?, paused: Boolean, event: String) {
        runCatching { api.reportPlaybackProgress(d, position, duration, paused, event) }.onFailure { logger.log("Playback", "progress error ${it.message}") }
    }

    suspend fun reportStopped(d: PlaybackDescriptor, position: Long, duration: Long?) {
        runCatching { api.reportPlaybackStopped(d, position, duration) }.onFailure { logger.log("Playback", "stop error ${it.message}") }
    }

    suspend fun recoverPlayback(d: PlaybackDescriptor, positionMs: Long): PlaybackDescriptor? {
        val result = api.resolveDynamicPort(force = false)
        if (result.isFailure) return null
        return runCatching {
            api.preparePlayback(d.item.copy(userData = d.item.userData.copy(playbackPositionTicks = positionMs * 10_000L)), true)
        }.getOrNull()?.also { _activePlayback.value = it }
    }

    fun testDynamicPort() {
        _dynamicPortStatus.value = DynamicPortStatus.Resolving
        viewModelScope.launch {
            api.resolveDynamicPort(true)
                .onSuccess { p -> _dynamicPortStatus.value = DynamicPortStatus.Success(p); _message.value = "成功解析并切换端口: $p" }
                .onFailure { _dynamicPortStatus.value = DynamicPortStatus.Error(it.message ?: "拉取失败") }
        }
    }

    fun updateConfig(transform: (EmbyServerConfig) -> EmbyServerConfig) = prefs.update(transform)
    fun imageUrl(item: EmbyItem, type: String = "Primary", width: Int = 600) = api.imageUrl(item, type, width)
    fun diagnosticsShareIntent(): Intent = logger.shareIntent()
    fun clearDiagnostics() = logger.clear()

    private fun launchBusy(block: suspend () -> Unit) {
        viewModelScope.launch {
            _loading.value = true
            try { block() }
            catch (t: Throwable) { _message.value = t.message ?: "操作失败" }
            finally { _loading.value = false }
        }
    }
}
