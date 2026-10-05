package com.embyplayernext.he.ui

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.embyplayernext.he.ui.screens.*

@Composable
fun EmbyApp(vm: EmbyViewModel = viewModel()) {
    val context = LocalContext.current
    val config by vm.config.collectAsState()
    val savedServers by vm.savedServers.collectAsState()
    val screen by vm.screen.collectAsState()
    val views by vm.views.collectAsState()
    val resume by vm.resumeItems.collectAsState()
    val recentPlayed by vm.recentPlayedItems.collectAsState()
    val latest by vm.latestItems.collectAsState()
    val favorites by vm.favoriteItems.collectAsState()
    val items by vm.items.collectAsState()
    val totalItems by vm.totalItems.collectAsState()
    val libraryLoading by vm.libraryLoading.collectAsState()
    val libraryLoadingMore by vm.libraryLoadingMore.collectAsState()
    val crumbs by vm.breadcrumbs.collectAsState()
    val library by vm.currentLibrary.collectAsState()
    val selected by vm.selectedItem.collectAsState()
    val seasons by vm.seasons.collectAsState()
    val selectedSeasonId by vm.selectedSeasonId.collectAsState()
    val episodes by vm.episodes.collectAsState()
    val selectedPerson by vm.selectedPerson.collectAsState()
    val personWorks by vm.personWorks.collectAsState()
    val similarItems by vm.similarItems.collectAsState()
    val playback by vm.activePlayback.collectAsState()
    val mode by vm.displayMode.collectAsState()
    val libraryColumns by vm.libraryColumns.collectAsState()
    val libraryLayoutMode by vm.libraryLayoutMode.collectAsState()
    val libraryRestoreItemId by vm.libraryRestoreItemId.collectAsState()
    val sortField by vm.sortField.collectAsState()
    val sortOrder by vm.sortOrder.collectAsState()
    val search by vm.searchQuery.collectAsState()
    val globalSearch by vm.globalSearchQuery.collectAsState()
    val globalResults by vm.globalSearchResults.collectAsState()
    val recentSearches by vm.recentSearches.collectAsState()
    val searchLoading by vm.searchLoading.collectAsState()
    val loading by vm.loading.collectAsState()
    val message by vm.message.collectAsState()
    val dynamicPortStatus by vm.dynamicPortStatus.collectAsState()
    val episodeQueue by vm.episodePlaybackQueue.collectAsState()

    var loginVisible by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val stateHolder = rememberSaveableStateHolder()
    val baseDensity = androidx.compose.ui.platform.LocalDensity.current
    val scaledDensity = remember(baseDensity, config.uiScale) {
        Density(baseDensity.density * config.uiScale, baseDensity.fontScale)
    }

    LaunchedEffect(message) {
        message?.let {
            snackbar.showSnackbar(it)
            vm.clearMessage()
        }
    }
    if (config.accessToken.isBlank() && screen == AppScreen.HOME) {
        LaunchedEffect(Unit) { loginVisible = true }
    }

    val effectiveDensity = scaledDensity
    CompositionLocalProvider(androidx.compose.ui.platform.LocalDensity provides effectiveDensity) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val mainScreens = setOf(AppScreen.HOME, AppScreen.LIBRARIES, AppScreen.SEARCH, AppScreen.SETTINGS)
            val useRail = screen in mainScreens && maxWidth >= 720.dp
            val stateKey = when (screen) {
                AppScreen.DETAIL -> "detail:${selected?.id.orEmpty()}"
                AppScreen.LIBRARY -> "library:${library?.id.orEmpty()}"
                else -> screen.name
            }

            Row(Modifier.fillMaxSize()) {
                if (useRail) {
                    PrimaryNavigationRail(
                        screen = screen,
                        onHome = vm::goHome,
                        onLibraries = vm::openLibraries,
                        onSearch = vm::openSearch,
                        onSettings = vm::openSettings,
                    )
                }

                Scaffold(
                    modifier = Modifier.weight(1f),
                    contentWindowInsets = WindowInsets(0, 0, 0, 0),
                    snackbarHost = { SnackbarHost(snackbar) },
                    bottomBar = {
                        if (screen in mainScreens && !useRail) {
                            PrimaryNavigationBar(
                                screen = screen,
                                onHome = vm::goHome,
                                onLibraries = vm::openLibraries,
                                onSearch = vm::openSearch,
                                onSettings = vm::openSettings,
                            )
                        }
                    },
                ) { padding ->
                    Box(Modifier.fillMaxSize().padding(padding)) {
                        stateHolder.SaveableStateProvider(stateKey) {
                            when (screen) {
                                AppScreen.HOME -> HomeScreen(
                                    config = config,
                                    views = views,
                                    resume = resume,
                                    recentPlayed = recentPlayed,
                                    latest = latest,
                                    favorites = favorites,
                                    savedServers = savedServers,
                                    loading = loading,
                                    imageFor = { item, type -> vm.imageUrl(item, type, 1000) },
                                    seriesImageFor = { item, type -> vm.seriesImageUrl(item, type, 1000) },
                                    viewImageFor = { view -> vm.imageUrl(view, 1000) },
                                    onOpenItem = vm::openItem,
                                    onResumeItem = { vm.play(it, resume = true) },
                                    onOpenView = vm::openLibrary,
                                    onLibraries = vm::openLibraries,
                                    onSearch = vm::openSearch,
                                    onRefresh = vm::loadHome,
                                    onLogin = { loginVisible = true },
                                    onSwitchServer = vm::switchServer,
                                )

                                AppScreen.LIBRARIES -> LibrariesScreen(
                                    views = views,
                                    loading = loading,
                                    imageFor = { vm.imageUrl(it, 1000) },
                                    onOpen = vm::openLibrary,
                                    onRefresh = vm::loadHome,
                                )

                                AppScreen.SEARCH -> {
                                    BackHandler { vm.goHome() }
                                    SearchScreen(
                                        query = globalSearch,
                                        results = globalResults,
                                        recentSearches = recentSearches,
                                        loading = searchLoading,
                                        imageFor = { vm.imageUrl(it, "Primary", 650) },
                                        onQuery = vm::setGlobalSearch,
                                        onSubmit = vm::commitGlobalSearch,
                                        onClearHistory = vm::clearRecentSearches,
                                        onItem = vm::openItem,
                                        onBack = vm::goHome,
                                    )
                                }

                                AppScreen.LIBRARY -> {
                                    BackHandler { vm.backFromLibrary() }
                                    LibraryScreen(
                                        title = library?.name ?: "媒体库",
                                        collectionType = library?.collectionType,
                                        items = items,
                                        totalItems = totalItems,
                                        crumbs = crumbs,
                                        mode = mode,
                                        sortField = sortField,
                                        sortOrder = sortOrder,
                                        search = search,
                                        columns = libraryColumns,
                                        layoutMode = libraryLayoutMode,
                                        restoreItemId = libraryRestoreItemId,
                                        loading = libraryLoading,
                                        loadingMore = libraryLoadingMore,
                                        imageFor = { vm.imageUrl(it, "Primary", 650) },
                                        onBack = { vm.backFromLibrary() },
                                        onCrumb = vm::navigateToBreadcrumb,
                                        onItem = vm::openItem,
                                        onMode = vm::setDisplayMode,
                                        onSearch = vm::setSearch,
                                        onSort = vm::setSort,
                                        onColumns = vm::setLibraryColumns,
                                        onLayoutMode = vm::setLibraryLayoutMode,
                                        onRestoreConsumed = vm::consumeLibraryRestoreItem,
                                        onRefresh = vm::refreshLibrary,
                                        onLoadMore = vm::loadMoreLibrary,
                                    )
                                }

                                AppScreen.DETAIL -> {
                                    BackHandler { vm.backFromDetail() }
                                    DetailScreen(
                                        item = selected,
                                        seasons = seasons,
                                        selectedSeasonId = selectedSeasonId,
                                        episodes = episodes,
                                        loading = loading,
                                        selectedPerson = selectedPerson,
                                        personWorks = personWorks,
                                        similarItems = similarItems,
                                        imageFor = { item, type -> vm.imageUrl(item, type, 1200) },
                                        personImageFor = { person -> vm.imageUrl(person, 420) },
                                        onBack = vm::backFromDetail,
                                        onPlay = { vm.play(resume = it) },
                                        onFavorite = vm::toggleFavorite,
                                        onPlayed = vm::togglePlayed,
                                        onDelete = { vm.deleteSelected() },
                                        onSeason = vm::selectSeason,
                                        onEpisode = vm::openEpisode,
                                        onPlayEpisode = vm::playEpisode,
                                        onPerson = vm::selectPerson,
                                        onClosePerson = vm::closePersonWorks,
                                    )
                                }

                                AppScreen.SETTINGS -> {
                                    BackHandler { vm.goHome() }
                                    SettingsScreen(
                                        config = config,
                                        savedServers = savedServers,
                                        onSwitchServer = vm::switchServer,
                                        onDeleteServer = vm::removeServer,
                                        onUpdate = { next -> vm.updateConfig { next } },
                                        onDynamic = vm::openDynamicPort,
                                        onLogin = { loginVisible = true },
                                        onLogout = vm::logout,
                                        onExportLog = vm::exportDiagnostics,
                                        onClearLog = vm::clearDiagnostics,
                                        onBack = vm::goHome,
                                    )
                                }

                                AppScreen.DYNAMIC_PORT -> {
                                    BackHandler { vm.openSettings() }
                                    DynamicPortScreen(
                                        config,
                                        dynamicPortStatus,
                                        vm::openSettings,
                                        { next -> vm.updateConfig { next } },
                                        vm::testDynamicPort,
                                    )
                                }

                                AppScreen.PLAYER -> playback?.let { descriptor ->
                                    val previousItem = remember(episodeQueue, descriptor.item.id) { vm.previousPlaybackItem(descriptor.item.id) }
                                    val nextItem = remember(episodeQueue, descriptor.item.id) { vm.nextPlaybackItem(descriptor.item.id) }
                                    PlayerScreen(
                                        descriptor = descriptor,
                                        config = config,
                                        previousItem = previousItem,
                                        nextItem = nextItem,
                                        onStart = vm::reportStart,
                                        onProgress = vm::reportProgress,
                                        onStopped = vm::reportStopped,
                                        onRecover = vm::recoverPlayback,
                                        onPlayAdjacent = vm::playAdjacent,
                                        onExit = vm::closePlayer,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (loginVisible) {
        LoginDialog(
            config = config,
            savedServers = savedServers,
            onDismiss = { loginVisible = false },
            onLogin = { server, user, pass -> vm.login(server, user, pass) { ok -> if (ok) loginVisible = false } },
            onQuickSwitch = { server ->
                vm.switchServer(server)
                loginVisible = false
            },
            onDeleteServer = vm::removeServer,
            onTest = { server ->
                vm.testConnection(server) { result ->
                    Toast.makeText(
                        context,
                        result.fold({ "✓ 连接成功: $it" }, { "✗ 连接失败: ${it.message}" }),
                        Toast.LENGTH_LONG,
                    ).show()
                }
            },
        )
    }
}

@Composable
private fun PrimaryNavigationBar(
    screen: AppScreen,
    onHome: () -> Unit,
    onLibraries: () -> Unit,
    onSearch: () -> Unit,
    onSettings: () -> Unit,
) {
    NavigationBar {
        NavigationBarItem(screen == AppScreen.HOME, onHome, { Icon(Icons.Default.Home, null) }, label = { Text("首页") })
        NavigationBarItem(screen == AppScreen.LIBRARIES, onLibraries, { Icon(Icons.Default.VideoLibrary, null) }, label = { Text("媒体库") })
        NavigationBarItem(screen == AppScreen.SEARCH, onSearch, { Icon(Icons.Default.Search, null) }, label = { Text("搜索") })
        NavigationBarItem(screen == AppScreen.SETTINGS, onSettings, { Icon(Icons.Default.Settings, null) }, label = { Text("设置") })
    }
}

@Composable
private fun PrimaryNavigationRail(
    screen: AppScreen,
    onHome: () -> Unit,
    onLibraries: () -> Unit,
    onSearch: () -> Unit,
    onSettings: () -> Unit,
) {
    NavigationRail(
        modifier = Modifier.fillMaxHeight(),
        header = {
            Surface(
                modifier = Modifier.padding(vertical = 16.dp).size(44.dp),
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.primaryContainer,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.VideoLibrary, null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                }
            }
        },
    ) {
        Spacer(Modifier.weight(1f))
        NavigationRailItem(screen == AppScreen.HOME, onHome, { Icon(Icons.Default.Home, null) }, label = { Text("首页") })
        NavigationRailItem(screen == AppScreen.LIBRARIES, onLibraries, { Icon(Icons.Default.VideoLibrary, null) }, label = { Text("媒体库") })
        NavigationRailItem(screen == AppScreen.SEARCH, onSearch, { Icon(Icons.Default.Search, null) }, label = { Text("搜索") })
        NavigationRailItem(screen == AppScreen.SETTINGS, onSettings, { Icon(Icons.Default.Settings, null) }, label = { Text("设置") })
        Spacer(Modifier.weight(1f))
    }
}
