package com.yoreader.app.ui

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.yoreader.app.YoReaderApp
import com.yoreader.app.data.FeedUrl
import com.yoreader.app.tts.ArticleSpeaker
import com.yoreader.app.tts.TtsLanguages
import com.yoreader.app.ui.articles.ArticleScope
import com.yoreader.app.ui.articles.ArticlesScreen
import com.yoreader.app.ui.articles.ArticlesViewModel
import com.yoreader.app.ui.components.AddSourceDialog
import com.yoreader.app.ui.components.TextInputDialog
import com.yoreader.app.ui.groups.GroupDetailScreen
import com.yoreader.app.ui.options.OptionsScreen
import com.yoreader.app.ui.reader.ReaderScreen
import com.yoreader.app.ui.reader.ReaderViewModel
import com.yoreader.app.ui.reader.WebPageScreen
import com.yoreader.app.ui.reader.WebPageViewModel
import com.yoreader.app.ui.search.SearchScreen
import com.yoreader.app.ui.search.SearchViewModel
import com.yoreader.app.ui.shell.ALL
import com.yoreader.app.ui.shell.AppDrawer
import com.yoreader.app.ui.shell.BottomBar
import com.yoreader.app.ui.shell.BottomTab
import com.yoreader.app.ui.shell.NowPlayingBar
import com.yoreader.app.ui.sources.SourcesScreen
import kotlinx.coroutines.launch

private const val ROUTE_LATEST = "latest"
private const val ROUTE_SAVED = "saved"
private const val ROUTE_SEARCH = "search"
private const val ROUTE_SOURCES = "sources"
private const val ROUTE_OPTIONS = "options"
private const val ROUTE_GROUP_EDIT = "group/{groupId}/edit"
private const val ROUTE_ARTICLES = "articles/{${ArticlesViewModel.ARG_KIND}}/{${ArticlesViewModel.ARG_ID}}"
private const val ROUTE_READER = "article/{${ReaderViewModel.ARG_ARTICLE_ID}}"
private const val ROUTE_WEB = "web?${WebPageViewModel.ARG_URL}={${WebPageViewModel.ARG_URL}}"

private fun webRoute(url: String) = "web?${WebPageViewModel.ARG_URL}=" + Uri.encode(url)

private fun articlesRoute(kind: String, id: Long = 0) = "articles/$kind/$id"

/**
 * App shell: a side drawer (menu), an always-visible bottom bar (menu, saved, latest,
 * search) and the screens between them. Latest is the start screen.
 */
@Composable
fun YoReaderRoot(vm: LibraryViewModel = viewModel()) {
    val navController = rememberNavController()
    val groups by vm.groups.collectAsStateWithLifecycle()
    val sources by vm.sources.collectAsStateWithLifecycle()
    val refreshing by vm.isRefreshing.collectAsStateWithLifecycle()
    val groupUnread by vm.groupUnread.collectAsStateWithLifecycle()
    val totalUnread by vm.totalUnread.collectAsStateWithLifecycle()
    val allFeedKeys = sources.orEmpty().map { FeedUrl.key(it.feed.url) }.toSet()

    val context = LocalContext.current
    val speaker = remember(context) { (context.applicationContext as YoReaderApp).speaker }
    val speech by speaker.state.collectAsStateWithLifecycle()
    val speechRate by speaker.rate.collectAsStateWithLifecycle()
    // Languages to switch between while reading: turned on in Options, with a voice.
    val appSettings = remember(context) { (context.applicationContext as YoReaderApp).settings }
    val ttsLanguages by appSettings.ttsLanguages.collectAsStateWithLifecycle()
    val voices by speaker.voices.collectAsStateWithLifecycle()
    val readingLanguages = remember(ttsLanguages, voices) {
        speaker.readingLanguages().mapNotNull { TtsLanguages.byId(it) }
    }
    val snackbar = remember { SnackbarHostState() }
    // Read-aloud errors (e.g. no voice for the language) can happen on any screen.
    // Android 13+: the read-aloud controls (notification, lock screen) need permission
    // to show notifications. Asked the first time reading starts in this session.
    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { /* reading works either way; only the controls are hidden without it */ }
    var askedNotifications by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(speech.isActive) {
        if (speech.isActive && !askedNotifications && Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            askedNotifications = true
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    LaunchedEffect(speech.error) {
        speech.error?.let {
            speaker.clearError()
            snackbar.showSnackbar(it)
        }
    }

    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var showNewGroup by rememberSaveable { mutableStateOf(false) }
    var showAddSource by rememberSaveable { mutableStateOf(false) }

    val backStackEntry by navController.currentBackStackEntryAsState()
    val route = backStackEntry?.destination?.route
    val selectedTab = when (route) {
        ROUTE_LATEST -> BottomTab.LATEST
        ROUTE_SAVED -> BottomTab.SAVED
        ROUTE_SEARCH -> BottomTab.SEARCH
        else -> null
    }
    val selectedGroupId = backStackEntry?.takeIf { route == ROUTE_ARTICLES }?.let { entry ->
        when (entry.arguments?.getString(ArticlesViewModel.ARG_KIND)) {
            ArticleScope.ALL -> ALL
            ArticleScope.GROUP -> entry.arguments?.getLong(ArticlesViewModel.ARG_ID)
            else -> null
        }
    }

    val selectedFeedId = backStackEntry
        ?.takeIf { route == ROUTE_ARTICLES && it.arguments?.getString(ArticlesViewModel.ARG_KIND) == ArticleScope.FEED }
        ?.arguments?.getLong(ArticlesViewModel.ARG_ID)

    fun closeDrawerThen(action: () -> Unit) {
        scope.launch { drawerState.close() }
        action()
    }

    BackHandler(enabled = drawerState.isOpen) { scope.launch { drawerState.close() } }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        // Screens handle the status bar themselves; this only reserves the bottom bar.
        contentWindowInsets = WindowInsets(0),
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            Column {
                if (speech.isActive) {
                    NowPlayingBar(
                        state = speech,
                        rate = speechRate,
                        onSlower = speaker::slower,
                        onFaster = speaker::faster,
                        languages = readingLanguages,
                        onSetLanguage = speaker::setLanguage,
                        onOpen = {
                            val page = speech.pageUrl
                            val route = if (page != null) webRoute(page) else speech.articleId?.let { "article/$it" }
                            route?.let { navController.navigate(it) { launchSingleTop = true } }
                        },
                        onPlayPause = {
                            if (speech.status == ArticleSpeaker.Status.PAUSED) speaker.resume() else speaker.pause()
                        },
                        onStop = speaker::stop,
                    )
                }
                BottomBar(
                    selected = selectedTab,
                    onMenu = { scope.launch { if (drawerState.isOpen) drawerState.close() else drawerState.open() } },
                    // Saved, Latest and Search always hide the side menu (it sits above
                    // the bar, so the bar stays tappable while the menu is open).
                    onSelect = { tab ->
                        closeDrawerThen {
                            when (tab) {
                                BottomTab.LATEST -> navController.openFromMenu(ROUTE_LATEST)
                                BottomTab.SAVED -> navController.openFromMenu(ROUTE_SAVED)
                                BottomTab.SEARCH -> navController.openFromMenu(ROUTE_SEARCH)
                            }
                        }
                    },
                )
            }
        },
    ) { padding ->
        // The drawer sits above the bottom bar, so the bar stays visible.
        ModalNavigationDrawer(
            modifier = Modifier
                .padding(padding)
                .consumeWindowInsets(padding),
            drawerState = drawerState,
            // Opening only from the menu icon, so swiping articles right never opens it.
            gesturesEnabled = drawerState.isOpen,
            drawerContent = {
                AppDrawer(
                    groups = groups.orEmpty(),
                    groupUnread = groupUnread,
                    totalUnread = totalUnread,
                    selectedGroupId = selectedGroupId,
                    feedUnread = sources.orEmpty().associate { it.feed.id to it.unreadCount },
                    selectedFeedId = selectedFeedId,
                    onOpenFeed = { id ->
                        closeDrawerThen { navController.openFromMenu(articlesRoute(ArticleScope.FEED, id)) }
                    },
                    onOpenAll = { closeDrawerThen { navController.openFromMenu(articlesRoute(ArticleScope.ALL)) } },
                    onOpenGroup = { id ->
                        closeDrawerThen { navController.openFromMenu(articlesRoute(ArticleScope.GROUP, id)) }
                    },
                    onNewGroup = { closeDrawerThen { showNewGroup = true } },
                    onManageSources = { closeDrawerThen { navController.openFromMenu(ROUTE_SOURCES) } },
                    onOptions = { closeDrawerThen { navController.openFromMenu(ROUTE_OPTIONS) } },
                    onAddSource = { closeDrawerThen { showAddSource = true } },
                )
            },
        ) {
            NavHost(
                navController = navController,
                startDestination = ROUTE_LATEST,
            ) {
                composable(
                    route = ROUTE_LATEST,
                    arguments = scopeArguments(ArticleScope.LATEST),
                ) {
                    ArticleList(
                        title = "Latest",
                        emptyText = { noSources ->
                            if (noSources) {
                                "No sources yet.\nOpen the menu (bottom left) and tap \"Add source\"."
                            } else {
                                "You're all caught up.\nPull down to check for new articles."
                            }
                        },
                        topLevel = true,
                        noSources = sources?.isEmpty() == true,
                        refreshing = refreshing,
                        navController = navController,
                    )
                }

                composable(
                    route = ROUTE_SAVED,
                    arguments = scopeArguments(ArticleScope.SAVED),
                ) {
                    ArticleList(
                        title = "Saved for later",
                        emptyText = { "Nothing saved yet.\nSwipe an article to the right to save it for later." },
                        topLevel = true,
                        noSources = false,
                        refreshing = refreshing,
                        navController = navController,
                    )
                }

                composable(ROUTE_SEARCH) {
                    val searchVm: SearchViewModel = viewModel()
                    val query by searchVm.query.collectAsStateWithLifecycle()
                    val results by searchVm.results.collectAsStateWithLifecycle()
                    SearchScreen(
                        query = query,
                        results = results,
                        onQueryChange = searchVm::setQuery,
                        onOpenArticle = { navController.navigate("article/$it") },
                        onOpenFeed = { navController.navigate(articlesRoute(ArticleScope.FEED, it)) },
                    )
                }

                composable(
                    route = ROUTE_ARTICLES,
                    arguments = listOf(
                        navArgument(ArticlesViewModel.ARG_KIND) { type = NavType.StringType },
                        navArgument(ArticlesViewModel.ARG_ID) { type = NavType.LongType },
                    ),
                ) { entry ->
                    val kind = entry.arguments?.getString(ArticlesViewModel.ARG_KIND)
                    val id = entry.arguments?.getLong(ArticlesViewModel.ARG_ID) ?: 0L
                    val group = groups?.firstOrNull { kind == ArticleScope.GROUP && it.group.id == id }
                    var showAddToGroup by rememberSaveable { mutableStateOf(false) }

                    ArticleList(
                        title = when (kind) {
                            ArticleScope.GROUP -> group?.group?.name.orEmpty()
                            ArticleScope.FEED -> sources?.firstOrNull { it.feed.id == id }?.feed?.title.orEmpty()
                            else -> "All articles"
                        },
                        emptyText = { noSources ->
                            when {
                                noSources && kind == ArticleScope.GROUP ->
                                    "This group has no sources yet.\n" +
                                        "Tap \"Add source\" and paste a feed or website address."
                                noSources -> "No sources yet."
                                else -> "No articles here.\nPull down to refresh."
                            }
                        },
                        topLevel = false,
                        noSources = when (kind) {
                            ArticleScope.GROUP -> group?.feeds?.isEmpty() == true
                            ArticleScope.FEED -> false
                            else -> sources?.isEmpty() == true
                        },
                        refreshing = refreshing,
                        navController = navController,
                        onEditGroup = group?.let { g -> { navController.navigate("group/${g.group.id}/edit") } },
                        onAddSource = group?.let { { showAddToGroup = true } },
                    )

                    if (showAddToGroup && group != null) {
                        AddSourceDialog(
                            intoGroup = group.group,
                            groupKeys = group.feeds.map { FeedUrl.key(it.url) }.toSet(),
                            existingKeys = allFeedKeys,
                            onDismiss = { showAddToGroup = false },
                            onAdd = { url, groupIds ->
                                vm.addFeed(url, groupIds)
                                showAddToGroup = false
                            },
                        )
                    }
                }

                composable(
                    route = ROUTE_READER,
                    arguments = listOf(navArgument(ReaderViewModel.ARG_ARTICLE_ID) { type = NavType.LongType }),
                ) {
                    val readerVm: ReaderViewModel = viewModel()
                    val article by readerVm.article.collectAsStateWithLifecycle()
                    val cleaning by readerVm.cleaning.collectAsStateWithLifecycle()
                    val showFeedVersion by readerVm.showFeedVersion.collectAsStateWithLifecycle()
                    val canShareOnNostr by readerVm.canShareOnNostr.collectAsStateWithLifecycle()
                    val sharingOnNostr by readerVm.sharingOnNostr.collectAsStateWithLifecycle()
                    val nostrAlsoNote by readerVm.nostrAlsoNote.collectAsStateWithLifecycle()
                    ReaderScreen(
                        item = article,
                        speech = speech,
                        cleaning = cleaning,
                        showFeedVersion = showFeedVersion,
                        messages = readerVm.messages,
                        onBack = { navController.popBackStack() },
                        onReadAloud = readerVm::toggleReadAloud,
                        onClean = readerVm::clean,
                        onSetShowFeedVersion = readerVm::setShowFeedVersion,
                        onSetSaved = readerVm::setStarred,
                        onSetRead = readerVm::setRead,
                        onOpenLink = { navController.navigate(webRoute(it)) },
                        canShareOnNostr = canShareOnNostr,
                        sharingOnNostr = sharingOnNostr,
                        nostrAlsoNote = nostrAlsoNote,
                        onShareOnNostr = readerVm::shareOnNostr,
                    )
                }

                composable(
                    route = ROUTE_WEB,
                    arguments = listOf(navArgument(WebPageViewModel.ARG_URL) { type = NavType.StringType }),
                ) {
                    val webVm: WebPageViewModel = viewModel()
                    val url by webVm.url.collectAsStateWithLifecycle()
                    val pageTitle by webVm.pageTitle.collectAsStateWithLifecycle()
                    val cleaned by webVm.cleaned.collectAsStateWithLifecycle()
                    val showCleaned by webVm.showCleaned.collectAsStateWithLifecycle()
                    val cleaning by webVm.cleaning.collectAsStateWithLifecycle()
                    WebPageScreen(
                        url = url,
                        pageTitle = pageTitle,
                        cleaned = cleaned,
                        showCleaned = showCleaned,
                        cleaning = cleaning,
                        speech = speech,
                        messages = webVm.messages,
                        onBack = { navController.popBackStack() },
                        onPageChanged = webVm::onPageChanged,
                        onClean = { webVm.clean(it) },
                        onSetShowCleaned = webVm::setShowCleaned,
                        onReadAloud = webVm::toggleReadAloud,
                        onOpenLink = { navController.navigate(webRoute(it)) },
                    )
                }

                composable(
                    route = ROUTE_GROUP_EDIT,
                    arguments = listOf(navArgument("groupId") { type = NavType.LongType }),
                ) { entry ->
                    val groupId = entry.arguments?.getLong("groupId") ?: return@composable
                    val group = groups?.firstOrNull { it.group.id == groupId }
                    GroupDetailScreen(
                        group = group,
                        allFeedKeys = allFeedKeys,
                        otherGroupNames = groups.orEmpty()
                            .filter { it.group.id != groupId }
                            .map { it.group.name },
                        onBack = { navController.popBackStack() },
                        onRename = { name -> group?.let { vm.renameGroup(it.group, name) } },
                        onDelete = {
                            group?.let { vm.deleteGroup(it.group) }
                            // The group's article list is gone too: back to Latest.
                            navController.popBackStack(ROUTE_LATEST, inclusive = false)
                        },
                        onSetMembership = { feedId, member -> vm.setMembership(feedId, groupId, member) },
                        onAddFeed = { url -> vm.addFeed(url, setOf(groupId)) },
                    )
                }

                composable(ROUTE_OPTIONS) {
                    val settings = remember(context) { (context.applicationContext as YoReaderApp).settings }
                    val textZoom by settings.textZoom.collectAsStateWithLifecycle()
                    val keepReadDays by settings.keepReadDays.collectAsStateWithLifecycle()
                    val app = context.applicationContext as YoReaderApp
                    val sync by app.laptopSync.state.collectAsStateWithLifecycle()
                    val nostrKey by app.nostrKeys.key.collectAsStateWithLifecycle()
                    val nostrRelays by settings.nostrRelays.collectAsStateWithLifecycle()
                    // Asked again on every return, e.g. after adding voices in Android settings.
                    LifecycleResumeEffect(Unit) {
                        speaker.checkVoices()
                        onPauseOrDispose { }
                    }
                    OptionsScreen(
                        voices = voices?.let { ids -> TtsLanguages.ALL.filter { it.id in ids } },
                        enabledLanguages = ttsLanguages,
                        onSetLanguage = settings::setTtsLanguage,
                        textZoom = textZoom,
                        onSetTextZoom = settings::setTextZoom,
                        keepReadDays = keepReadDays,
                        onSetKeepReadDays = settings::setKeepReadDays,
                        sync = sync,
                        onUseSyncFile = { uri -> app.appScope.launch { app.laptopSync.useFile(uri) } },
                        onSyncNow = { app.syncLaptopSoon() },
                        onStopSync = { app.laptopSync.stop() },
                        nostrKey = nostrKey,
                        onCreateNostrKey = { app.nostrKeys.create() },
                        onRestoreNostrKey = { app.nostrKeys.restore(it) },
                        onRemoveNostrKey = { app.nostrKeys.remove() },
                        relays = nostrRelays,
                        onSetRelays = settings::setNostrRelays,
                        onCheckRelays = { app.nostr.check() },
                        onBack = { navController.popBackStack() },
                    )
                }

                composable(ROUTE_SOURCES) {
                    SourcesScreen(
                        sources = sources,
                        groups = groups.orEmpty().map { it.group },
                        onBack = { navController.popBackStack() },
                        onOpenFeed = { navController.navigate(articlesRoute(ArticleScope.FEED, it)) },
                        onAddFeed = vm::addFeed,
                        onSetMembership = vm::setMembership,
                        onDeleteFeed = vm::deleteFeed,
                        onRenameFeed = vm::renameFeed,
                        refreshing = refreshing,
                        onRefreshAll = vm::refreshAll,
                        onRefreshFeed = vm::refreshFeed,
                        onExport = vm::exportOpml,
                        onImport = vm::importOpml,
                        messages = vm.messages,
                    )
                }
            }
        }
    }

    if (showNewGroup) {
        val existing = groups.orEmpty().map { it.group.name.lowercase() }
        TextInputDialog(
            title = "New group",
            label = "Group name",
            confirmLabel = "Create",
            validate = { input ->
                val name = input.trim()
                when {
                    name.isEmpty() -> "Enter a name"
                    name.lowercase() in existing -> "A group with this name already exists"
                    else -> null
                }
            },
            onDismiss = { showNewGroup = false },
            onConfirm = {
                vm.addGroup(it)
                showNewGroup = false
            },
        )
    }

    if (showAddSource) {
        AddSourceDialog(
            groups = groups.orEmpty().map { it.group },
            existingKeys = allFeedKeys,
            onDismiss = { showAddSource = false },
            onAdd = { url, groupIds ->
                vm.addFeed(url, groupIds)
                showAddSource = false
            },
        )
    }
}

/** Fixed route arguments, so Latest and Saved get their scope like other lists. */
private fun scopeArguments(kind: String) = listOf(
    navArgument(ArticlesViewModel.ARG_KIND) { type = NavType.StringType; defaultValue = kind },
    navArgument(ArticlesViewModel.ARG_ID) { type = NavType.LongType; defaultValue = 0L },
)

/**
 * Goes to a top-level place (bottom bar or menu) without piling up screens:
 * everything above Latest is cleared first.
 */
private fun NavHostController.openFromMenu(route: String) {
    navigate(route) {
        popUpTo(ROUTE_LATEST)
        launchSingleTop = true
    }
}

/** An article list screen backed by its own [ArticlesViewModel] (scope from the route). */
@Composable
private fun ArticleList(
    title: String,
    emptyText: (noSources: Boolean) -> String,
    topLevel: Boolean,
    noSources: Boolean,
    refreshing: Boolean,
    navController: NavHostController,
    onEditGroup: (() -> Unit)? = null,
    onAddSource: (() -> Unit)? = null,
) {
    val listVm: ArticlesViewModel = viewModel()
    val articles by listVm.articles.collectAsStateWithLifecycle()
    val unreadOnly by listVm.unreadOnly.collectAsStateWithLifecycle()
    val isSaved = listVm.scope == ArticleScope.Saved
    LifecycleResumeEffect(listVm) {
        listVm.onShown()
        onPauseOrDispose {}
    }
    ArticlesScreen(
        title = title,
        articles = articles,
        emptyText = emptyText(noSources),
        refreshing = refreshing,
        onOpenArticle = {
            listVm.onOpen(it)
            navController.navigate("article/$it")
        },
        onSwipe = listVm::swipe,
        onUndo = listVm::undo,
        onBack = if (topLevel) null else ({ navController.popBackStack() }),
        onRefresh = if (isSaved) null else listVm::refresh,
        unreadOnly = if (listVm.hasUnreadFilter) unreadOnly else null,
        onSetUnreadOnly = listVm::setUnreadOnly,
        onMarkAllRead = if (listVm.hasUnreadFilter) listVm::markAllRead else null,
        onEditGroup = onEditGroup,
        onAddSource = onAddSource,
    )
}
