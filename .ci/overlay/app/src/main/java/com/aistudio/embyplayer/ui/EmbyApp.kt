package com.aistudio.embyplayer.ui

import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Density
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aistudio.embyplayer.ui.screens.*

@Composable
fun EmbyApp(vm: EmbyViewModel = viewModel()) {
    val context = LocalContext.current
    val config by vm.config.collectAsState()
    val screen by vm.screen.collectAsState()
    val views by vm.views.collectAsState()
    val resume by vm.resumeItems.collectAsState()
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
    val playback by vm.activePlayback.collectAsState()
    val mode by vm.displayMode.collectAsState()
    val sortField by vm.sortField.collectAsState()
    val sortOrder by vm.sortOrder.collectAsState()
    val search by vm.searchQuery.collectAsState()
    val globalSearch by vm.globalSearchQuery.collectAsState()
    val globalResults by vm.globalSearchResults.collectAsState()
    val searchLoading by vm.searchLoading.collectAsState()
    val loading by vm.loading.collectAsState()
    val message by vm.message.collectAsState()
    val dynamicPortStatus by vm.dynamicPortStatus.collectAsState()

    var loginVisible by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
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

    CompositionLocalProvider(androidx.compose.ui.platform.LocalDensity provides scaledDensity) {
        val mainScreens = setOf(AppScreen.HOME, AppScreen.LIBRARIES, AppScreen.SEARCH, AppScreen.SETTINGS)
        Scaffold(
            snackbarHost = { SnackbarHost(snackbar) },
            bottomBar = {
                if (screen in mainScreens) {
                    NavigationBar {
                        NavigationBarItem(
                            selected = screen == AppScreen.HOME,
                            onClick = vm::goHome,
                            icon = { Icon(Icons.Default.Home, null) },
                            label = { Text("首页") },
                        )
                        NavigationBarItem(
                            selected = screen == AppScreen.LIBRARIES,
                            onClick = vm::openLibraries,
                            icon = { Icon(Icons.Default.VideoLibrary, null) },
                            label = { Text("媒体库") },
                        )
                        NavigationBarItem(
                            selected = screen == AppScreen.SEARCH,
                            onClick = vm::openSearch,
                            icon = { Icon(Icons.Default.Search, null) },
                            label = { Text("搜索") },
                        )
                        NavigationBarItem(
                            selected = screen == AppScreen.SETTINGS,
                            onClick = vm::openSettings,
                            icon = { Icon(Icons.Default.Settings, null) },
                            label = { Text("设置") },
                        )
                    }
                }
            },
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding)) {
                when (screen) {
                    AppScreen.HOME -> HomeScreen(
                        config = config,
                        views = views,
                        resume = resume,
                        latest = latest,
                        favorites = favorites,
                        loading = loading,
                        imageFor = { item, type -> vm.imageUrl(item, type, 1000) },
                        viewImageFor = { view -> vm.imageUrl(view, 1000) },
                        onOpenItem = vm::openItem,
                        onOpenView = vm::openLibrary,
                        onLibraries = vm::openLibraries,
                        onSearch = vm::openSearch,
                        onRefresh = vm::loadHome,
                        onLogin = { loginVisible = true },
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
                            loading = searchLoading,
                            imageFor = { vm.imageUrl(it, "Primary", 650) },
                            onQuery = vm::setGlobalSearch,
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
                            loading = libraryLoading,
                            loadingMore = libraryLoadingMore,
                            imageFor = { vm.imageUrl(it, "Primary", 650) },
                            onBack = { vm.backFromLibrary() },
                            onCrumb = vm::navigateToBreadcrumb,
                            onItem = vm::openItem,
                            onMode = vm::setDisplayMode,
                            onSearch = vm::setSearch,
                            onSort = vm::setSort,
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
                            imageFor = { item, type -> vm.imageUrl(item, type, 1200) },
                            onBack = vm::backFromDetail,
                            onPlay = { vm.play(resume = it) },
                            onFavorite = vm::toggleFavorite,
                            onPlayed = vm::togglePlayed,
                            onDelete = { vm.deleteSelected() },
                            onSeason = vm::selectSeason,
                            onEpisode = vm::openItem,
                            onPerson = vm::selectPerson,
                            onClosePerson = vm::closePersonWorks,
                        )
                    }

                    AppScreen.SETTINGS -> {
                        BackHandler { vm.goHome() }
                        SettingsScreen(
                            config = config,
                            onUpdate = { next -> vm.updateConfig { next } },
                            onDynamic = vm::openDynamicPort,
                            onLogin = { loginVisible = true },
                            onLogout = vm::logout,
                            onShareLog = {
                                context.startActivity(Intent.createChooser(vm.diagnosticsShareIntent(), "分享诊断日志"))
                            },
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
                        PlayerScreen(
                            descriptor,
                            config,
                            vm::reportStart,
                            vm::reportProgress,
                            vm::reportStopped,
                            vm::recoverPlayback,
                            vm::closePlayer,
                        )
                    }
                }
            }
        }
    }

    if (loginVisible) {
        LoginDialog(
            config,
            { loginVisible = false },
            { server, user, pass -> vm.login(server, user, pass) { ok -> if (ok) loginVisible = false } },
            { server ->
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
