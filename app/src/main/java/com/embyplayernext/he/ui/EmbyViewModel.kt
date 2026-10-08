package com.embyplayernext.he.ui

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.embyplayernext.he.data.model.*
import com.embyplayernext.he.data.network.EmbyApiClient
import com.embyplayernext.he.data.prefs.AppPreferences
import com.embyplayernext.he.playback.PlaybackService
import com.embyplayernext.he.data.network.NetworkSupport
import com.embyplayernext.he.util.DiagnosticsLogger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

enum class AppScreen { HOME, LIBRARIES, SEARCH, LIBRARY, DETAIL, SETTINGS, DYNAMIC_PORT, PLAYER }
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
    private val localRecentPlayed = ConcurrentHashMap<String, EmbyItem>()

    val config: StateFlow<EmbyServerConfig> = prefs.config
    private val _savedServers = MutableStateFlow<List<SavedServerProfile>>(prefs.getSavedServers())
    val savedServers: StateFlow<List<SavedServerProfile>> = _savedServers.asStateFlow()
    private val _screen = MutableStateFlow(AppScreen.HOME); val screen = _screen.asStateFlow()
    private val _views = MutableStateFlow<List<EmbyView>>(emptyList()); val views = _views.asStateFlow()
    private val _resumeItems = MutableStateFlow<List<EmbyItem>>(emptyList()); val resumeItems = _resumeItems.asStateFlow()
    private val _nextUpItems = MutableStateFlow<List<EmbyItem>>(emptyList()); val nextUpItems = _nextUpItems.asStateFlow()
    private val _recentPlayedItems = MutableStateFlow<List<EmbyItem>>(emptyList()); val recentPlayedItems = _recentPlayedItems.asStateFlow()
    private val _latestItems = MutableStateFlow<List<EmbyItem>>(emptyList()); val latestItems = _latestItems.asStateFlow()
    private val _favoriteItems = MutableStateFlow<List<EmbyItem>>(emptyList()); val favoriteItems = _favoriteItems.asStateFlow()
    private val _items = MutableStateFlow<List<EmbyItem>>(emptyList()); val items = _items.asStateFlow()
    private val _totalItems = MutableStateFlow(0); val totalItems = _totalItems.asStateFlow()
    private val _libraryLoading = MutableStateFlow(false); val libraryLoading = _libraryLoading.asStateFlow()
    private val _libraryLoadingMore = MutableStateFlow(false); val libraryLoadingMore = _libraryLoadingMore.asStateFlow()
    private val _breadcrumbs = MutableStateFlow<List<Breadcrumb>>(emptyList()); val breadcrumbs = _breadcrumbs.asStateFlow()
    private val _currentLibrary = MutableStateFlow<EmbyView?>(null); val currentLibrary = _currentLibrary.asStateFlow()
    private val _selectedItem = MutableStateFlow<EmbyItem?>(null); val selectedItem = _selectedItem.asStateFlow()
    private val _seasons = MutableStateFlow<List<EmbyItem>>(emptyList()); val seasons = _seasons.asStateFlow()
    private val _selectedSeasonId = MutableStateFlow<String?>(null); val selectedSeasonId = _selectedSeasonId.asStateFlow()
    private val _episodes = MutableStateFlow<List<EmbyItem>>(emptyList()); val episodes = _episodes.asStateFlow()
    private val _personWorks = MutableStateFlow<List<EmbyItem>>(emptyList()); val personWorks = _personWorks.asStateFlow()
    private val _selectedPerson = MutableStateFlow<EmbyPerson?>(null); val selectedPerson = _selectedPerson.asStateFlow()
    private val _similarItems = MutableStateFlow<List<EmbyItem>>(emptyList()); val similarItems = _similarItems.asStateFlow()
    private val _activePlayback = MutableStateFlow<PlaybackDescriptor?>(null); val activePlayback = _activePlayback.asStateFlow()
    private val _displayMode = MutableStateFlow(EmbyDisplayMode.ALL); val displayMode = _displayMode.asStateFlow()
    private val _libraryColumns = MutableStateFlow(0); val libraryColumns = _libraryColumns.asStateFlow()
    private val _libraryLayoutMode = MutableStateFlow(LibraryLayoutMode.GRID); val libraryLayoutMode = _libraryLayoutMode.asStateFlow()
    private val _libraryRestoreItemId = MutableStateFlow<String?>(null); val libraryRestoreItemId = _libraryRestoreItemId.asStateFlow()
    private val _sortField = MutableStateFlow(EmbySortField.SORT_NAME); val sortField = _sortField.asStateFlow()
    private val _sortOrder = MutableStateFlow(EmbySortOrder.ASCENDING); val sortOrder = _sortOrder.asStateFlow()
    private val _searchQuery = MutableStateFlow(""); val searchQuery = _searchQuery.asStateFlow()
    private val _globalSearchQuery = MutableStateFlow(""); val globalSearchQuery = _globalSearchQuery.asStateFlow()
    private val _globalSearchResults = MutableStateFlow<List<EmbyItem>>(emptyList()); val globalSearchResults = _globalSearchResults.asStateFlow()
    private val _recentSearches = MutableStateFlow(prefs.getRecentSearches()); val recentSearches = _recentSearches.asStateFlow()
    private val _searchLoading = MutableStateFlow(false); val searchLoading = _searchLoading.asStateFlow()
    private val _loading = MutableStateFlow(false); val loading = _loading.asStateFlow()
    private val _message = MutableStateFlow<String?>(null); val message = _message.asStateFlow()
    private val _dynamicPortStatus = MutableStateFlow<DynamicPortStatus>(DynamicPortStatus.Idle); val dynamicPortStatus = _dynamicPortStatus.asStateFlow()

    private var detailReturnScreen: AppScreen = AppScreen.HOME
    private var libraryReturnScreen: AppScreen = AppScreen.LIBRARIES
    private var globalSearchJob: Job? = null
    private var globalSearchGeneration = 0L
    private var librarySearchJob: Job? = null
    private var libraryLoadJob: Job? = null
    private var libraryGeneration = 0L
    private var detailLoadJob: Job? = null
    private var detailGeneration = 0L
    private var seasonLoadJob: Job? = null
    private var seasonGeneration = 0L
    private var busyOperations = 0
    private val detailHistory = ArrayDeque<EmbyItem>()
    private val _episodePlaybackQueue = MutableStateFlow<List<EmbyItem>>(emptyList())
    val episodePlaybackQueue: StateFlow<List<EmbyItem>> = _episodePlaybackQueue

    init { if (prefs.config.value.accessToken.isNotBlank()) loadHome() }

    fun clearMessage() { _message.value = null }

    fun goHome() {
        _screen.value = AppScreen.HOME
        _currentLibrary.value = null
        _breadcrumbs.value = emptyList()
        loadHome()
    }

    fun openLibraries() {
        _screen.value = AppScreen.LIBRARIES
        _currentLibrary.value = null
        _breadcrumbs.value = emptyList()
        if (_views.value.isEmpty()) loadHome()
    }

    fun openSearch() {
        _screen.value = AppScreen.SEARCH
        _currentLibrary.value = null
        _breadcrumbs.value = emptyList()
    }

    fun openSettings() { _screen.value = AppScreen.SETTINGS }
    fun openDynamicPort() { _screen.value = AppScreen.DYNAMIC_PORT }

    fun refreshSavedServers() {
        _savedServers.value = prefs.getSavedServers()
    }

    fun switchServer(profile: SavedServerProfile) {
        if (prefs.switchToServer(profile.id)) {
            NetworkSupport.clearCachedClients()
            refreshSavedServers()
            clearAllServerContent()
            _message.value = "✓ 已切换至 ${profile.serverName} (${profile.username.ifBlank { "未命名" }})"
            _screen.value = AppScreen.HOME
            if (profile.accessToken.isNotBlank()) {
                loadHome()
            }
        }
    }

    fun removeServer(serverId: String) {
        prefs.removeServerProfile(serverId)
        NetworkSupport.clearCachedClients()
        refreshSavedServers()
        clearAllServerContent()
        if (prefs.config.value.accessToken.isNotBlank()) {
            loadHome()
        }
    }

    fun login(server: String, username: String, password: String, done: (Boolean) -> Unit = {}) = launchBusy {
        api.login(server, username, password).onSuccess {
            NetworkSupport.clearCachedClients()
            refreshSavedServers()
            clearAllServerContent()
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
        viewModelScope.launch { done(api.testConnection(server)) }
    }

    fun logout() {
        prefs.clearLogin()
        NetworkSupport.clearCachedClients()
        refreshSavedServers()
        clearAllServerContent()
        _screen.value = AppScreen.HOME
    }

    fun loadHome() = launchBusy {
        coroutineScope {
            val viewsRequest = async { runCatching { api.getViews() } }
            val resumeRequest = async { runCatching { api.getResumeItems() } }
            val recentRequest = async { runCatching { api.getRecentlyPlayedItems() } }
            val latestRequest = async { runCatching { api.getLatestItems() } }
            val favoriteRequest = async {
                runCatching {
                    api.getItems(
                        null, EmbyDisplayMode.FAVORITES, sortField = EmbySortField.DATE_CREATED,
                        sortOrder = EmbySortOrder.DESCENDING, recursive = true, limit = 40,
                    ).filter { it.type !in setOf("Season", "Studio", "Genre", "Person") }
                }
            }

            viewsRequest.await()
                .onSuccess { _views.value = it }
                .onFailure { _message.value = "媒体库加载失败: ${it.message}" }
            val resumeResult = resumeRequest.await()
            val recentResult = recentRequest.await()
            recentResult.onSuccess { _recentPlayedItems.value = it }
            resumeResult.onSuccess { serverResume ->
                val recentPlayed = recentResult.getOrNull().orEmpty()
                val localItems = localRecentPlayed.values.toList()

                val allCandidates = mutableListOf<EmbyItem>()
                allCandidates.addAll(localItems)
                allCandidates.addAll(serverResume)
                allCandidates.addAll(recentPlayed.filter { !it.isPlayed })

                val mergedCandidates = allCandidates.map { item ->
                    val local = localRecentPlayed[item.id]
                    if (local != null && local.userData.playbackPositionTicks > item.userData.playbackPositionTicks) {
                        item.copy(
                            userData = item.userData.copy(
                                playbackPositionTicks = local.userData.playbackPositionTicks,
                                played = local.userData.played,
                                playedPercentage = local.userData.playedPercentage ?: item.userData.playedPercentage,
                                lastPlayedDate = local.userData.lastPlayedDate ?: item.userData.lastPlayedDate,
                            )
                        )
                    } else item
                }.filter { !it.isPlayed }

                val (episodes, others) = mergedCandidates.partition { it.type == "Episode" || !it.seriesId.isNullOrBlank() }

                val seriesEpisodes = episodes.groupBy { it.seriesId ?: it.name }
                    .values
                    .mapNotNull { epList ->
                        epList.maxWithOrNull(
                            compareBy<EmbyItem> { it.userData.lastPlayedDate ?: "" }
                                .thenBy { it.userData.playbackPositionTicks }
                                .thenBy { it.indexNumber ?: 0 }
                        )
                    }

                val distinctOthers = others.distinctBy { it.id }

                val finalResume = (seriesEpisodes + distinctOthers)
                    .sortedByDescending { it.userData.lastPlayedDate ?: "" }
                    .take(24)

                _resumeItems.value = finalResume
            }
            latestRequest.await().onSuccess { _latestItems.value = it }
            favoriteRequest.await().onSuccess { _favoriteItems.value = it }
        }
    }

    fun setGlobalSearch(query: String) {
        _globalSearchQuery.value = query
        val generation = ++globalSearchGeneration
        globalSearchJob?.cancel()
        if (query.isBlank()) {
            _globalSearchResults.value = emptyList()
            _searchLoading.value = false
            return
        }
        globalSearchJob = viewModelScope.launch {
            try {
                delay(300)
                if (generation != globalSearchGeneration) return@launch
                _searchLoading.value = true
                val results = api.getItems(
                    null, EmbyDisplayMode.ALL, query.trim(), EmbySortField.SORT_NAME,
                    EmbySortOrder.ASCENDING, recursive = true, limit = 200,
                )
                if (generation == globalSearchGeneration) {
                    _globalSearchResults.value = results.distinctBy(EmbyItem::id)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (t: Throwable) {
                if (generation == globalSearchGeneration) _message.value = "搜索失败: ${t.message}"
            } finally {
                if (generation == globalSearchGeneration) _searchLoading.value = false
            }
        }
    }

    fun commitGlobalSearch(query: String = _globalSearchQuery.value) {
        val value = query.trim()
        if (value.isBlank()) return
        _recentSearches.value = prefs.saveRecentSearch(value)
    }

    fun clearRecentSearches() {
        prefs.clearRecentSearches()
        _recentSearches.value = emptyList()
    }

    fun openLibrary(view: EmbyView) {
        libraryReturnScreen = AppScreen.LIBRARIES
        _currentLibrary.value = view
        val (f, o) = prefs.getLibrarySort(view.id)
        _sortField.value = f
        _sortOrder.value = o
        _libraryColumns.value = prefs.getLibraryColumns(view.id)
        _libraryLayoutMode.value = prefs.getLibraryLayoutMode(view.id)
        _libraryRestoreItemId.value = null
        _displayMode.value = defaultModeFor(view)
        _searchQuery.value = ""
        _breadcrumbs.value = listOf(Breadcrumb(view.id, view.name))
        _items.value = emptyList()
        _totalItems.value = 0
        _screen.value = AppScreen.LIBRARY
        loadCurrentLibrary(reset = true)
    }

    fun setDisplayMode(mode: EmbyDisplayMode) {
        if (_displayMode.value == mode) return
        _displayMode.value = mode
        loadCurrentLibrary(reset = true)
    }

    fun setLibraryLayoutMode(mode: LibraryLayoutMode) {
        if (_libraryLayoutMode.value == mode) return
        _libraryLayoutMode.value = mode
        _currentLibrary.value?.let { prefs.saveLibraryLayoutMode(it.id, mode) }
    }

    fun setSort(field: EmbySortField, order: EmbySortOrder) {
        _sortField.value = field
        _sortOrder.value = order
        _currentLibrary.value?.let { prefs.saveLibrarySort(it.id, field, order) }
        loadCurrentLibrary(reset = true)
    }

    fun setLibraryColumns(columns: Int) {
        val value = columns.coerceIn(0, 8)
        _libraryColumns.value = value
        _currentLibrary.value?.let { prefs.saveLibraryColumns(it.id, value) }
    }

    fun consumeLibraryRestoreItem() { _libraryRestoreItemId.value = null }

    fun setSearch(query: String) {
        _searchQuery.value = query
        librarySearchJob?.cancel()
        librarySearchJob = viewModelScope.launch {
            delay(if (query.isBlank()) 0 else 280)
            loadCurrentLibrary(reset = true)
        }
    }

    fun refreshLibrary() = loadCurrentLibrary(reset = true)

    fun loadMoreLibrary() {
        if (_libraryLoading.value || _libraryLoadingMore.value) return
        if (_items.value.size >= _totalItems.value) return
        loadCurrentLibrary(reset = false)
    }

    fun openFolder(item: EmbyItem) {
        _breadcrumbs.value = _breadcrumbs.value + Breadcrumb(item.id, item.name)
        _displayMode.value = EmbyDisplayMode.ALL
        _searchQuery.value = ""
        loadCurrentLibrary(reset = true)
    }

    fun navigateToBreadcrumb(index: Int) {
        _breadcrumbs.value = _breadcrumbs.value.take(index + 1)
        _searchQuery.value = ""
        _displayMode.value = if (index == 0) _currentLibrary.value?.let(::defaultModeFor) ?: EmbyDisplayMode.ALL else EmbyDisplayMode.ALL
        loadCurrentLibrary(reset = true)
    }

    fun backFromLibrary(): Boolean {
        if (_breadcrumbs.value.size > 1) {
            val newCrumbs = _breadcrumbs.value.dropLast(1)
            _breadcrumbs.value = newCrumbs
            _displayMode.value = if (newCrumbs.size == 1) _currentLibrary.value?.let(::defaultModeFor) ?: EmbyDisplayMode.ALL else EmbyDisplayMode.ALL
            _searchQuery.value = ""
            loadCurrentLibrary(reset = true)
            return true
        }
        _screen.value = libraryReturnScreen
        _currentLibrary.value = null
        _breadcrumbs.value = emptyList()
        return false
    }

    private fun loadCurrentLibrary(reset: Boolean) {
        val parent = _breadcrumbs.value.lastOrNull()?.id ?: _currentLibrary.value?.id ?: return
        val generation = ++libraryGeneration
        libraryLoadJob?.cancel()
        libraryLoadJob = viewModelScope.launch {
            if (reset) _libraryLoading.value = true else _libraryLoadingMore.value = true
            val start = if (reset) 0 else _items.value.size
            runCatching {
                api.getItemsPage(
                    parentId = parent,
                    mode = _displayMode.value,
                    search = _searchQuery.value,
                    sortField = _sortField.value,
                    sortOrder = _sortOrder.value,
                    recursive = _searchQuery.value.isNotBlank() || _displayMode.value in setOf(
                        EmbyDisplayMode.MOVIES, EmbyDisplayMode.SERIES, EmbyDisplayMode.EPISODES,
                        EmbyDisplayMode.VIDEOS, EmbyDisplayMode.SONGS, EmbyDisplayMode.ALBUMS,
                        EmbyDisplayMode.ARTISTS, EmbyDisplayMode.PHOTOS, EmbyDisplayMode.BOOKS,
                        EmbyDisplayMode.FAVORITES, EmbyDisplayMode.UNPLAYED, EmbyDisplayMode.RESUMABLE
                    ),
                    startIndex = start,
                    limit = 120
                )
            }.onSuccess { page ->
                if (generation == libraryGeneration) {
                    _items.value = if (reset) page.items else (_items.value + page.items).distinctBy(EmbyItem::id)
                    _totalItems.value = page.totalRecordCount
                }
            }.onFailure {
                if (generation == libraryGeneration) _message.value = "加载项目失败: ${it.message}"
            }
            if (generation == libraryGeneration) {
                _libraryLoading.value = false
                _libraryLoadingMore.value = false
            }
        }
    }

    fun openItem(item: EmbyItem) {
        if (_screen.value == AppScreen.SEARCH && _globalSearchQuery.value.isNotBlank()) commitGlobalSearch()
        if (item.isBrowsableContainer) {
            if (_screen.value == AppScreen.LIBRARY) {
                openFolder(item)
            } else {
                libraryReturnScreen = _screen.value
                _currentLibrary.value = EmbyView(item.id, item.name, null, item.primaryImageTag)
                _breadcrumbs.value = listOf(Breadcrumb(item.id, item.name))
                _displayMode.value = EmbyDisplayMode.ALL
                _searchQuery.value = ""
                _items.value = emptyList()
                _totalItems.value = 0
                _screen.value = AppScreen.LIBRARY
                loadCurrentLibrary(reset = true)
            }
            return
        }
        closePersonWorks()
        if (_screen.value == AppScreen.LIBRARY) _libraryRestoreItemId.value = item.id
        if (_screen.value == AppScreen.DETAIL) {
            _selectedItem.value?.let { current ->
                if (current.id != item.id) detailHistory.addLast(current)
            }
        } else {
            detailReturnScreen = _screen.value
            detailHistory.clear()
        }
        _selectedItem.value = item
        _screen.value = AppScreen.DETAIL
        refreshDetail(item.id)
    }

    fun openEpisode(item: EmbyItem) {
        if (item.type == "Episode" && _episodePlaybackQueue.value.none { it.id == item.id }) {
            _episodePlaybackQueue.value = _episodes.value.ifEmpty { _episodePlaybackQueue.value }
        }
        openItem(item)
    }

    fun playEpisode(item: EmbyItem, resume: Boolean = item.hasResumePosition) {
        if (item.type == "Episode" && _episodePlaybackQueue.value.none { it.id == item.id }) {
            _episodePlaybackQueue.value = _episodes.value.ifEmpty { _episodePlaybackQueue.value }
        }
        play(item, resume)
    }

    fun refreshDetail(id: String? = null) {
        val targetId = id ?: _selectedItem.value?.id ?: return
        val generation = ++detailGeneration
        detailLoadJob?.cancel()
        seasonLoadJob?.cancel()
        detailLoadJob = viewModelScope.launch {
            busyOperations += 1
            _loading.value = true
            try {
                _similarItems.value = emptyList()
                val previous = _selectedItem.value?.takeIf { it.id == targetId }
                var detail = api.getItemDetails(targetId)
                if (generation != detailGeneration) return@launch
                if (previous != null) {
                    val previousHasResume = previous.hasResumePosition
                    val detailHasResume = detail.hasResumePosition
                    if (previousHasResume && (!detailHasResume || previous.resumePositionMs > detail.resumePositionMs)) {
                        val previousPercent = previous.userData.playedPercentage
                            ?: previous.durationMs.takeIf { it > 0 }?.let { previous.resumePositionMs * 100.0 / it }
                        detail = detail.copy(
                            userData = detail.userData.copy(
                                playbackPositionTicks = previous.userData.playbackPositionTicks,
                                played = detail.userData.played || previous.userData.played,
                                playedPercentage = listOfNotNull(detail.userData.playedPercentage, previousPercent).maxOrNull(),
                                lastPlayedDate = detail.userData.lastPlayedDate ?: previous.userData.lastPlayedDate,
                            )
                        )
                    }
                }
                _selectedItem.value = detail
                if (detail.type == "Series") {
                    val seasons = api.getSeasons(detail.id)
                    if (generation != detailGeneration) return@launch
                    val first = seasons.firstOrNull()
                    val episodes = api.getEpisodes(detail.id, first?.id)
                    if (generation != detailGeneration) return@launch
                    _seasons.value = seasons
                    _selectedSeasonId.value = first?.id
                    _episodes.value = episodes
                } else if (detail.type == "Episode") {
                    val seriesId = detail.seriesId?.takeIf { it.isNotBlank() }
                    val allEpisodes = if (seriesId != null) api.getEpisodes(seriesId, null) else emptyList()
                    val sortedEpisodes = allEpisodes.sortedWith(compareBy({ it.parentIndexNumber ?: 0 }, { it.indexNumber ?: 0 }))
                    if (generation != detailGeneration) return@launch
                    val seasonNumber = detail.parentIndexNumber
                    val sameSeason = if (seasonNumber == null) sortedEpisodes else {
                        sortedEpisodes.filter { it.parentIndexNumber == seasonNumber }
                    }.orEmpty()
                    _seasons.value = emptyList()
                    _selectedSeasonId.value = null
                    _episodes.value = sortedEpisodes.ifEmpty { sameSeason }
                    if (sortedEpisodes.any { it.id == detail.id }) _episodePlaybackQueue.value = sortedEpisodes
                } else {
                    _seasons.value = emptyList()
                    _selectedSeasonId.value = null
                    _episodes.value = emptyList()
                }
                runCatching { api.getSimilarItems(detail.id) }
                    .onSuccess { if (generation == detailGeneration) _similarItems.value = it.filter { candidate -> candidate.id != detail.id && candidate.type in setOf("Movie", "Series") } }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (t: Throwable) {
                if (generation == detailGeneration) _message.value = "详情加载失败: ${t.message}"
            } finally {
                busyOperations = (busyOperations - 1).coerceAtLeast(0)
                _loading.value = busyOperations > 0
            }
        }
    }

    fun selectSeason(season: EmbyItem) {
        val series = _selectedItem.value ?: return
        val generation = ++seasonGeneration
        seasonLoadJob?.cancel()
        seasonLoadJob = viewModelScope.launch {
            busyOperations += 1
            _loading.value = true
            try {
                val episodes = api.getEpisodes(series.id, season.id)
                if (generation == seasonGeneration && _selectedItem.value?.id == series.id) {
                    _selectedSeasonId.value = season.id
                    _episodes.value = episodes
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (t: Throwable) {
                if (generation == seasonGeneration) _message.value = "暂无集数信息: ${t.message}"
            } finally {
                busyOperations = (busyOperations - 1).coerceAtLeast(0)
                _loading.value = busyOperations > 0
            }
        }
    }

    fun selectPerson(person: EmbyPerson) = launchBusy {
        _selectedPerson.value = person
        runCatching { api.getItemsByPerson(person.id) }
            .onSuccess { _personWorks.value = it.filter { work -> work.type in setOf("Movie", "Series") } }
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

    fun deleteItem(item: EmbyItem, done: () -> Unit = {}) = launchBusy {
        if (item.id.isBlank()) {
            _message.value = "删除失败: 无效的媒体ID"
            return@launchBusy
        }
        val safeTypes = setOf("Movie", "Episode", "Video", "Audio", "MusicVideo", "Trailer")
        if (item.isFolder || item.type !in safeTypes) {
            _message.value = "安全防护：客户端仅允许删除单个媒体文件，禁止删除文件夹或整部剧集"
            return@launchBusy
        }
        runCatching { api.deleteItem(item.id) }.onSuccess {
            _message.value = "《${item.name}》已从服务器删除"
            done()
            if (detailHistory.isNotEmpty()) {
                val previous = detailHistory.removeLast()
                _selectedItem.value = previous
                _screen.value = AppScreen.DETAIL
                refreshDetail(previous.id)
            } else {
                _screen.value = detailReturnScreen
                when (detailReturnScreen) {
                    AppScreen.LIBRARY -> loadCurrentLibrary(reset = true)
                    AppScreen.SEARCH -> setGlobalSearch(_globalSearchQuery.value)
                    else -> loadHome()
                }
            }
        }.onFailure { _message.value = "删除失败: ${it.message}" }
    }

    fun deleteSelected(done: () -> Unit = {}) {
        val current = _selectedItem.value ?: return
        deleteItem(current, done)
    }

    fun play(item: EmbyItem? = null, resume: Boolean = true) = launchBusy {
        val target = item ?: _selectedItem.value ?: return@launchBusy
        if (!target.isPlayable) {
            _message.value = "该项目不是可直接播放的媒体"
            return@launchBusy
        }
        if (target.type == "Episode") {
            val seriesId = target.seriesId?.takeIf { it.isNotBlank() }
            if (seriesId != null) {
                val currentQueue = _episodePlaybackQueue.value
                val needsFullQueue = currentQueue.none { it.id == target.id } ||
                    currentQueue.any { it.seriesId != seriesId } ||
                    (_seasons.value.size > 1 && currentQueue.mapNotNull { it.parentIndexNumber }.distinct().size <= 1)
                if (needsFullQueue) {
                    runCatching { api.getEpisodes(seriesId, null) }
                        .onSuccess { queue ->
                            val sorted = queue.sortedWith(compareBy({ it.parentIndexNumber ?: 0 }, { it.indexNumber ?: 0 }))
                            if (sorted.any { it.id == target.id }) _episodePlaybackQueue.value = sorted
                        }
                }
            }
        } else if (target.type != "Episode") {
            _episodePlaybackQueue.value = emptyList()
        }
        runCatching { api.preparePlayback(target, resume) }
            .onSuccess { _activePlayback.value = it; _screen.value = AppScreen.PLAYER }
            .onFailure { _message.value = "播放准备失败: ${it.message}" }
    }

    fun playAdjacent(item: EmbyItem) {
        play(item, resume = item.hasResumePosition)
    }

    fun previousPlaybackItem(currentId: String): EmbyItem? {
        val queue = _episodePlaybackQueue.value
        val index = queue.indexOfFirst { it.id == currentId }
        return if (index > 0) queue[index - 1] else null
    }

    fun nextPlaybackItem(currentId: String): EmbyItem? {
        val queue = _episodePlaybackQueue.value
        val index = queue.indexOfFirst { it.id == currentId }
        return if (index >= 0 && index < queue.lastIndex) queue[index + 1] else null
    }

    fun backFromDetail() {
        closePersonWorks()
        if (detailHistory.isNotEmpty()) {
            val previous = detailHistory.removeLast()
            _selectedItem.value = previous
            refreshDetail(previous.id)
        } else {
            detailGeneration += 1
            detailLoadJob?.cancel()
            seasonGeneration += 1
            seasonLoadJob?.cancel()
            _screen.value = detailReturnScreen
        }
    }

    fun closePlayer() {
        val id = _activePlayback.value?.item?.id
        _activePlayback.value = null
        _screen.value = AppScreen.DETAIL
        if (id != null) refreshDetail(id)
        loadHome()
    }

    private fun updateLocalPlayback(item: EmbyItem, positionMs: Long, durationMs: Long?) {
        val ticks = positionMs.coerceAtLeast(0) * 10_000L
        val dur = durationMs?.takeIf { it > 0 } ?: item.durationMs
        val isPlayed = if (dur > 0) (positionMs.toDouble() / dur) >= 0.92 else false
        val pct = if (dur > 0) (positionMs * 100.0 / dur).coerceIn(0.0, 100.0) else null
        val now = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.US).apply {
            timeZone = java.util.TimeZone.getTimeZone("UTC")
        }.format(java.util.Date())

        val updated = item.copy(
            userData = item.userData.copy(
                playbackPositionTicks = ticks,
                played = isPlayed,
                playedPercentage = pct,
                lastPlayedDate = now,
            )
        )
        localRecentPlayed[item.id] = updated
    }

    suspend fun reportStart(d: PlaybackDescriptor, position: Long, duration: Long?) {
        updateLocalPlayback(d.item, position, duration)
        runCatching { api.reportPlaybackStart(d, position, duration) }.onFailure { logger.log("Playback", "start error ${it.message}") }
    }

    suspend fun reportProgress(d: PlaybackDescriptor, position: Long, duration: Long?, paused: Boolean, event: String) {
        updateLocalPlayback(d.item, position, duration)
        runCatching { api.reportPlaybackProgress(d, position, duration, paused, event) }.onFailure { logger.log("Playback", "progress error ${it.message}") }
    }

    suspend fun reportStopped(d: PlaybackDescriptor, position: Long, duration: Long?) {
        updateLocalPlayback(d.item, position, duration)
        runCatching { api.reportPlaybackStopped(d, position, duration) }.onFailure { logger.log("Playback", "stop error ${it.message}") }
    }

    suspend fun recoverPlayback(d: PlaybackDescriptor, positionMs: Long): PlaybackDescriptor? {
        val recoveryConfig = prefs.config.value
        val recoveryRockchip = listOf(
            android.os.Build.MANUFACTURER,
            android.os.Build.BRAND,
            android.os.Build.HARDWARE,
            android.os.Build.BOARD,
            android.os.Build.DEVICE,
            android.os.Build.PRODUCT,
        ).any { value ->
            value.contains("rockchip", ignoreCase = true) ||
                value.contains("rk35", ignoreCase = true) ||
                value.contains("rk30", ignoreCase = true)
        }
        val recoveryHevc = d.item.mediaStreams.any { stream ->
            stream.type.equals("Video", ignoreCase = true) &&
                stream.codec.orEmpty().let { codec ->
                    codec.equals("hevc", ignoreCase = true) || codec.equals("h265", ignoreCase = true)
                }
        }
        if (
            recoveryConfig.hardwareCompatibilityMode &&
            recoveryRockchip &&
            recoveryHevc &&
            d.playMethod.equals("DirectPlay", ignoreCase = true)
        ) {
            logger.log(
                "PlaybackRecovery",
                "suppressed=rk-hevc-diagnostic item=${d.item.id} positionMs=$positionMs method=${d.playMethod} hwCompat=true",
            )
            return null
        }
        val resumeItem = d.item.copy(userData = d.item.userData.copy(playbackPositionTicks = positionMs * 10_000L))
        logger.log("PlaybackRecovery", "begin item=${d.item.id} method=${d.playMethod} positionMs=$positionMs codec=${d.item.mediaStreams.firstOrNull { it.type.equals("Video", true) }?.codec}")

        // Decoder/container failures on older TVs should fall back to server transcoding before giving up.
        if (d.playMethod != "Transcode") {
            runCatching { api.preparePlayback(resumeItem, true, forceTranscode = true) }
                .getOrNull()
                ?.takeIf { it.playMethod == "Transcode" }
                ?.let { recovered ->
                    logger.log("PlaybackRecovery", "DirectPlay -> Transcode item=${d.item.id} url=${recovered.streamUrl.substringBefore('?')}")
                    _activePlayback.value = recovered
                    return recovered
                }
        }

        // Dynamic-port recovery remains a second, network-specific fallback.
        if (config.value.dynamicPortEnabled) {
            val result = api.resolveDynamicPort(force = false)
            if (result.isSuccess) {
                return runCatching { api.preparePlayback(resumeItem, true, forceTranscode = d.playMethod == "Transcode") }
                    .getOrNull()?.also { _activePlayback.value = it }
            }
        }
        logger.log("PlaybackRecovery", "failed item=${d.item.id} method=${d.playMethod} positionMs=$positionMs")
        return null
    }

    fun testDynamicPort() {
        _dynamicPortStatus.value = DynamicPortStatus.Resolving
        viewModelScope.launch {
            api.resolveDynamicPort(true)
                .onSuccess { p -> _dynamicPortStatus.value = DynamicPortStatus.Success(p); _message.value = "成功解析并切换端口: $p" }
                .onFailure { _dynamicPortStatus.value = DynamicPortStatus.Error(it.message ?: "拉取失败") }
        }
    }

    fun updateConfig(transform: (EmbyServerConfig) -> EmbyServerConfig) {
        val before = prefs.config.value
        val after = transform(before)
        prefs.update { after }
        val playbackConfigChanged = before.hardwareDecoding != after.hardwareDecoding ||
            before.cacheMb != after.cacheMb ||
            before.allowInsecureHttps != after.allowInsecureHttps ||
            before.dynamicPortTimeoutSeconds != after.dynamicPortTimeoutSeconds
        if (before.allowInsecureHttps != after.allowInsecureHttps) NetworkSupport.clearCachedClients()
        if (playbackConfigChanged && _screen.value != AppScreen.PLAYER) {
            getApplication<Application>().stopService(Intent(getApplication(), PlaybackService::class.java))
        }
    }
    fun imageUrl(item: EmbyItem, type: String = "Primary", width: Int = 600) = api.imageUrl(item, type, width)
    fun seriesImageUrl(item: EmbyItem, type: String = "Backdrop", width: Int = 800) = api.seriesImageUrl(item, type, width)
    fun imageUrl(view: EmbyView, width: Int = 900) = api.imageUrl(view, width)
    fun imageUrl(person: EmbyPerson, width: Int = 360) = api.imageUrl(person, width)
    fun exportDiagnostics() {
        val result = logger.exportTextFile()
        _message.value = result.fold(
            onSuccess = { "运行日志已导出：$it" },
            onFailure = { "导出运行日志失败：${it.message ?: "未知错误"}" },
        )
    }
    fun clearDiagnostics() = logger.clear()

    private fun clearAllServerContent() {
        detailLoadJob?.cancel()
        libraryLoadJob?.cancel()
        seasonLoadJob?.cancel()
        globalSearchJob?.cancel()
        librarySearchJob?.cancel()
        localRecentPlayed.clear()
        detailHistory.clear()
        _views.value = emptyList()
        _resumeItems.value = emptyList()
        _nextUpItems.value = emptyList()
        _recentPlayedItems.value = emptyList()
        _latestItems.value = emptyList()
        _favoriteItems.value = emptyList()
        _items.value = emptyList()
        _totalItems.value = 0
        _currentLibrary.value = null
        _breadcrumbs.value = emptyList()
        _selectedItem.value = null
        _seasons.value = emptyList()
        _episodes.value = emptyList()
        _episodePlaybackQueue.value = emptyList()
        _globalSearchResults.value = emptyList()
        _similarItems.value = emptyList()
        _searchQuery.value = ""
        _globalSearchQuery.value = ""
    }

    private fun defaultModeFor(view: EmbyView): EmbyDisplayMode = when (view.collectionType?.lowercase()) {
        "movies" -> EmbyDisplayMode.MOVIES
        "tvshows" -> EmbyDisplayMode.SERIES
        "music" -> EmbyDisplayMode.ALBUMS
        "books" -> EmbyDisplayMode.BOOKS
        "musicvideos" -> EmbyDisplayMode.VIDEOS
        "homevideos", "photos" -> EmbyDisplayMode.ALL
        else -> EmbyDisplayMode.ALL
    }

    private fun launchBusy(block: suspend () -> Unit) {
        viewModelScope.launch {
            busyOperations += 1
            _loading.value = true
            try {
                block()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (t: Throwable) {
                _message.value = t.message ?: "操作失败"
            } finally {
                busyOperations = (busyOperations - 1).coerceAtLeast(0)
                _loading.value = busyOperations > 0
            }
        }
    }
}
