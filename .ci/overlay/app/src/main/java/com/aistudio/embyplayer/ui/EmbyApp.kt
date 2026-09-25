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
import androidx.compose.material.icons.filled.SettingsEthernet
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
    val crumbs by vm.breadcrumbs.collectAsState()
    val lib by vm.currentLibrary.collectAsState()
    val selected by vm.selectedItem.collectAsState()
    val seasons by vm.seasons.collectAsState()
    val episodes by vm.episodes.collectAsState()
    val person by vm.selectedPerson.collectAsState()
    val works by vm.personWorks.collectAsState()
    val playback by vm.activePlayback.collectAsState()
    val mode by vm.displayMode.collectAsState()
    val sf by vm.sortField.collectAsState()
    val so by vm.sortOrder.collectAsState()
    val search by vm.searchQuery.collectAsState()
    val globalSearch by vm.globalSearchQuery.collectAsState()
    val globalResults by vm.globalSearchResults.collectAsState()
    val searchLoading by vm.searchLoading.collectAsState()
    val loading by vm.loading.collectAsState()
    val message by vm.message.collectAsState()
    val dyn by vm.dynamicPortStatus.collectAsState()
    var login by remember { mutableStateOf(false) }
    val snack = remember { SnackbarHostState() }
    val baseDensity = androidx.compose.ui.platform.LocalDensity.current
    val scaled = remember(baseDensity, config.uiScale) { Density(baseDensity.density * config.uiScale, baseDensity.fontScale) }

    LaunchedEffect(message) { message?.let { snack.showSnackbar(it); vm.clearMessage() } }
    if (config.accessToken.isBlank() && screen == AppScreen.HOME) LaunchedEffect(Unit) { login = true }

    CompositionLocalProvider(androidx.compose.ui.platform.LocalDensity provides scaled) {
        val showNav = screen in setOf(AppScreen.HOME, AppScreen.SEARCH, AppScreen.DYNAMIC_PORT, AppScreen.SETTINGS)
        Scaffold(
            snackbarHost = { SnackbarHost(snack) },
            bottomBar = {
                if (showNav) {
                    NavigationBar {
                        NavigationBarItem(
                            selected = screen == AppScreen.HOME,
                            onClick = vm::goHome,
                            icon = { Icon(Icons.Default.Home, null) },
                            label = { Text("首页") }
                        )
                        NavigationBarItem(
                            selected = screen == AppScreen.SEARCH,
                            onClick = vm::openSearch,
                            icon = { Icon(Icons.Default.Search, null) },
                            label = { Text("搜索") }
                        )
                        NavigationBarItem(
                            selected = screen == AppScreen.DYNAMIC_PORT,
                            onClick = vm::openDynamicPort,
                            icon = { Icon(Icons.Default.SettingsEthernet, null) },
                            label = { Text("动态端口") }
                        )
                        NavigationBarItem(
                            selected = screen == AppScreen.SETTINGS,
                            onClick = vm::openSettings,
                            icon = { Icon(Icons.Default.Settings, null) },
                            label = { Text("设置") }
                        )
                    }
                }
            }
        ) { pad ->
            Box(Modifier.fillMaxSize().padding(pad)) {
                when (screen) {
                    AppScreen.HOME -> HomeScreen(
                        config = config,
                        views = views,
                        resume = resume,
                        latest = latest,
                        favorites = favorites,
                        loading = loading,
                        imageFor = { i, t -> vm.imageUrl(i, t, 1000) },
                        onOpenItem = vm::openItem,
                        onOpenView = vm::openLibrary,
                        onSearch = vm::openSearch,
                        onRefresh = vm::loadHome,
                        onLogin = { login = true }
                    )
                    AppScreen.SEARCH -> {
                        BackHandler { vm.goHome() }
                        SearchScreen(
                            query = globalSearch,
                            results = globalResults,
                            loading = searchLoading,
                            imageFor = { vm.imageUrl(it, "Primary", 500) },
                            onQuery = vm::setGlobalSearch,
                            onItem = vm::openItem,
                            onBack = vm::goHome
                        )
                    }
                    AppScreen.LIBRARY -> {
                        BackHandler { vm.backFromLibrary() }
                        LibraryScreen(
                            lib?.name ?: "媒体库",
                            items,
                            crumbs,
                            mode,
                            sf,
                            so,
                            search,
                            loading,
                            { vm.imageUrl(it, "Primary", 500) },
                            { vm.backFromLibrary() },
                            vm::navigateToBreadcrumb,
                            vm::openItem,
                            vm::setDisplayMode,
                            vm::setSearch,
                            vm::setSort,
                            { vm.setSearch(search) }
                        )
                    }
                    AppScreen.DETAIL -> {
                        BackHandler { vm.backFromDetail() }
                        DetailScreen(
                            selected,
                            seasons,
                            episodes,
                            loading,
                            person,
                            works,
                            { i, t -> vm.imageUrl(i, t, 1200) },
                            vm::backFromDetail,
                            { vm.play(resume = it) },
                            vm::toggleFavorite,
                            vm::togglePlayed,
                            { vm.deleteSelected() },
                            vm::selectSeason,
                            vm::openItem,
                            vm::selectPerson,
                            vm::closePersonWorks
                        )
                    }
                    AppScreen.SETTINGS -> {
                        BackHandler { vm.goHome() }
                        SettingsScreen(
                            config,
                            { c -> vm.updateConfig { c } },
                            vm::openDynamicPort,
                            { login = true },
                            vm::logout,
                            { context.startActivity(Intent.createChooser(vm.diagnosticsShareIntent(), "分享诊断日志")) },
                            vm::clearDiagnostics,
                            vm::goHome
                        )
                    }
                    AppScreen.DYNAMIC_PORT -> {
                        BackHandler { vm.goHome() }
                        DynamicPortScreen(config, dyn, vm::goHome, { c -> vm.updateConfig { c } }, vm::testDynamicPort)
                    }
                    AppScreen.PLAYER -> playback?.let { d ->
                        PlayerScreen(d, config, vm::reportStart, vm::reportProgress, vm::reportStopped, vm::recoverPlayback, vm::closePlayer)
                    }
                }
            }
        }
    }

    if (login) {
        LoginDialog(
            config,
            { login = false },
            { server, user, pass -> vm.login(server, user, pass) { ok -> if (ok) login = false } },
            { server ->
                vm.testConnection(server) { r ->
                    Toast.makeText(context, r.fold({ "✓ 连接成功: $it" }, { "✗ 连接失败: ${it.message}" }), Toast.LENGTH_LONG).show()
                }
            }
        )
    }
}
