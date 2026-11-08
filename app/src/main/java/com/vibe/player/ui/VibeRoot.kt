package com.vibe.player.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import kotlinx.coroutines.flow.first
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.vibe.player.container
import com.vibe.player.data.db.Track
import com.vibe.player.ui.components.AddToPlaylistDialog
import com.vibe.player.ui.components.LocalTrackMenu
import com.vibe.player.ui.components.TrackMenuActions
import com.vibe.player.ui.components.TrackMenuController
import com.vibe.player.ui.components.TrackMenuSheet
import com.vibe.player.ui.downloads.DownloadsScreen
import com.vibe.player.ui.home.HomeScreen
import com.vibe.player.ui.library.AlbumScreen
import com.vibe.player.ui.library.ArtistScreen
import com.vibe.player.ui.library.FolderScreen
import com.vibe.player.ui.library.LibraryScreen
import com.vibe.player.ui.library.SearchScreen
import com.vibe.player.ui.navigation.Routes
import com.vibe.player.ui.navigation.topLevelDestinations
import com.vibe.player.ui.player.MiniPlayer
import com.vibe.player.ui.player.NowPlayingScreen
import com.vibe.player.ui.playlists.PlaylistDetailScreen
import com.vibe.player.ui.playlists.PlaylistsScreen
import com.vibe.player.ui.settings.LicensesScreen
import com.vibe.player.ui.settings.SettingsScreen
import com.vibe.player.ui.theme.VibeTheme
import kotlinx.coroutines.launch

@Composable
fun VibeRoot() {
    val context = LocalContext.current
    val container = context.container
    val settings by container.settings.settings.collectAsState(initial = null)
    val mode = settings?.themeMode ?: com.vibe.player.ui.theme.ThemeMode.DARK
    val accent = settings?.accentArgb ?: com.vibe.player.ui.theme.DEFAULT_ACCENT_ARGB

    VibeTheme(mode, accent) {
        val navController = rememberNavController()
        val player = container.player
        val playerState by player.state.collectAsStateWithLifecycle()
        val scope = rememberCoroutineScope()
        val snackbar = remember { SnackbarHostState() }
        val trackMenu = remember { TrackMenuController() }
        val selection = remember { com.vibe.player.ui.components.SelectionController() }
        var nowPlayingOpen by rememberSaveable { mutableStateOf(false) } // survives rotation
        var addToPlaylistIds by remember { mutableStateOf<List<Long>?>(null) }
        val favoriteIds by container.library.favoriteIds().collectAsState(initial = emptyList())
        val playlists by container.library.playlistSummaries().collectAsState(initial = emptyList())
        val artVersion by container.artworkStore.version.collectAsState()

        // Custom album art: pick an image, then ask whether it applies to the song or the whole album.
        var artTarget by remember { mutableStateOf<Track?>(null) }
        var pickedImage by remember { mutableStateOf<android.net.Uri?>(null) }
        val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            if (uri != null && artTarget != null) pickedImage = uri else artTarget = null
        }

        // Permissions: media read (library scan) + notifications (playback / download progress).
        val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
            if (result.values.any { it }) scope.launch { runCatching { container.library.rescan() } }
        }
        LaunchedEffect(Unit) {
            player.connect()
            val wanted = buildList {
                add(if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE)
                if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
            }
            if (!container.library.hasMediaPermission()) permissionLauncher.launch(wanted.toTypedArray())
            else scope.launch { runCatching { container.library.rescan() } }
        }
        LaunchedEffect(Unit) { player.errors.collect { snackbar.showSnackbar(it) } }
        LaunchedEffect(Unit) { runCatching { container.downloads.resumePending() } }
        // "Open with Cwave": import the file if it isn't in the library yet, then play it full-screen.
        LaunchedEffect(Unit) {
            container.externalOpen.collect { uri ->
                if (uri == null) return@collect
                container.externalOpen.value = null
                runCatching { context.contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) }
                player.state.first { it.connected }
                val track = container.library.trackByUri(uri.toString())
                    ?: run { runCatching { container.library.importFiles(listOf(uri)) }; container.library.trackByUri(uri.toString()) }
                if (track != null) { player.playTracks(listOf(track)); nowPlayingOpen = true }
                else snackbar.showSnackbar("Cwave couldn't read that file")
            }
        }

        val backStack by navController.currentBackStackEntryAsState()
        val currentRoute = backStack?.destination?.route
        // Tabs live outside the NavController entirely: switching is a state change, and each tab's
        // rememberSaveable state (scroll positions, selected sub-tab) is kept by the state holder.
        var tab by rememberSaveable { mutableIntStateOf(0) }
        val tabStateHolder = rememberSaveableStateHolder()
        val onMain = currentRoute == Routes.MAIN
        val showBars = onMain
        val cfg = androidx.compose.ui.platform.LocalConfiguration.current
        // Landscape: the bottom bar + mini-player would eat most of the height, so tabs move to a side rail.
        val landscape = cfg.screenWidthDp > cfg.screenHeightDp
        val tabBar: @Composable () -> Unit = {
            if (landscape) {
                NavigationRail(containerColor = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.navigationBarsPadding()) {
                    Spacer(Modifier.height(8.dp))
                    topLevelDestinations.forEachIndexed { index, dest ->
                        val selected = tab == index
                        NavigationRailItem(selected = selected, onClick = { tab = index }, icon = { Icon(if (selected) dest.selected else dest.unselected, dest.label) }, label = { Text(dest.label) })
                    }
                }
            } else {
                NavigationBar(tonalElevation = 0.dp) {
                    topLevelDestinations.forEachIndexed { index, dest ->
                        val selected = tab == index
                        NavigationBarItem(selected = selected, onClick = { tab = index }, icon = { Icon(if (selected) dest.selected else dest.unselected, dest.label) }, label = { Text(dest.label) })
                    }
                }
            }
        }
        BackHandler(enabled = onMain && tab != 0) { tab = 0 }
        LaunchedEffect(currentRoute, tab) { selection.clear() }
        val switchTab: (String) -> Unit = { route ->
            topLevelDestinations.indexOfFirst { it.route == route }.takeIf { it >= 0 }?.let { i ->
                if (!onMain) navController.popBackStack(Routes.MAIN, inclusive = false)
                tab = i
            }
        }

        var editTarget by remember { mutableStateOf<Track?>(null) }
        var deleteTarget by remember { mutableStateOf<Track?>(null) }
        var consentTrack by remember { mutableStateOf<Track?>(null) }
        val consentLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { res ->
            val t = consentTrack; consentTrack = null
            if (res.resultCode == android.app.Activity.RESULT_OK && t != null) scope.launch {
                container.deleter.afterConsent(t); player.removeTrack(t.id); snackbar.showSnackbar("Deleted \"${t.title}\"")
            }
        }
        fun performDelete(t: Track) = scope.launch {
            when (val r = container.deleter.delete(t)) {
                is com.vibe.player.library.DeleteOutcome.Deleted -> { player.removeTrack(t.id); snackbar.showSnackbar("Deleted \"${t.title}\"") }
                is com.vibe.player.library.DeleteOutcome.NeedsConsent -> { consentTrack = t; consentLauncher.launch(androidx.activity.result.IntentSenderRequest.Builder(r.intentSender).build()) }
                is com.vibe.player.library.DeleteOutcome.Failed -> snackbar.showSnackbar(r.reason)
            }
        }
        // ---- multi-select actions ----
        var batchDeleteTracks by remember { mutableStateOf<List<Track>?>(null) }
        var batchDeletePlaylists by remember { mutableStateOf<List<Long>?>(null) }
        var moveToAlbumIds by remember { mutableStateOf<List<Long>?>(null) }
        var removeDownloadIds by remember { mutableStateOf<List<Long>?>(null) }
        val batchConsentLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { res ->
            scope.launch {
                if (res.resultCode == android.app.Activity.RESULT_OK) { val n = container.deleter.afterBatchConsent(); snackbar.showSnackbar("Deleted $n file(s)") }
                else { container.deleter.cancelBatch(); snackbar.showSnackbar("Delete cancelled") }
            }
        }
        suspend fun selectedTracks(): List<Track> = when (selection.kind) {
            com.vibe.player.ui.components.SelectionKind.TRACKS -> container.library.tracksByIds(selection.selected.mapNotNull { it.toLongOrNull() })
            com.vibe.player.ui.components.SelectionKind.ALBUMS -> container.library.allTracksNow().filter { it.albumKey in selection.selected }
            else -> emptyList()
        }
        fun runBatchDelete(tracks: List<Track>) = scope.launch {
            val r = container.deleter.deleteMany(tracks)
            tracks.forEach { player.removeTrack(it.id) }
            selection.clear()
            if (r.consent != null) batchConsentLauncher.launch(androidx.activity.result.IntentSenderRequest.Builder(r.consent).build())
            else snackbar.showSnackbar("Deleted ${r.deleted} file(s)" + if (r.failed > 0) " · ${r.failed} couldn't be deleted" else "")
        }
        val artistNames by container.library.artistNames().collectAsState(initial = emptyList())
        val albumNames by container.library.albumNames().collectAsState(initial = emptyList())

        val menuActions = remember(favoriteIds, artVersion) {
            TrackMenuActions(
                onEdit = { editTarget = it },
                onDelete = { deleteTarget = it },
                onTrim = { nowPlayingOpen = false; navController.navigate(Routes.trim(it.id)) },
                hasCustomArt = { container.artworkStore.hasTrackArt(it.uri) || container.artworkStore.hasAlbumArt(it.albumKey) },
                onSetArt = { t -> artTarget = t; imagePicker.launch(androidx.activity.result.PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                onRemoveArt = { t -> container.artworkStore.removeTrackArt(t.uri); container.artworkStore.removeAlbumArt(t.albumKey); scope.launch { snackbar.showSnackbar("Custom art removed") } },
                isFavorite = { it in favoriteIds },
                onPlayNext = { player.playNext(listOf(it)); scope.launch { snackbar.showSnackbar("Playing next") } },
                onAddToQueue = { player.addToQueue(listOf(it)); scope.launch { snackbar.showSnackbar("Added to queue") } },
                onToggleFavorite = { t -> scope.launch { container.library.toggleFavorite(t.id) } },
                onAddToPlaylist = { addToPlaylistIds = listOf(it.id) },
                onRemoveFromPlaylist = { pid, t -> scope.launch { container.library.removeFromPlaylist(pid, t.id) } },
                onGoToAlbum = { nowPlayingOpen = false; navController.navigate(Routes.album(it.album)) },
                onGoToArtist = { nowPlayingOpen = false; navController.navigate(Routes.artist(it.artist)) },
            )
        }

        CompositionLocalProvider(LocalTrackMenu provides trackMenu, com.vibe.player.ui.navigation.LocalSwitchTab provides switchTab, com.vibe.player.ui.components.LocalSelection provides selection) {
            Scaffold(
                snackbarHost = { SnackbarHost(snackbar) },
                bottomBar = {
                    AnimatedVisibility(showBars && !landscape, enter = slideInVertically { it } + fadeIn(), exit = slideOutVertically { it } + fadeOut()) { tabBar() }
                },
            ) { padding ->
                Row(Modifier.fillMaxSize()) {
                if (showBars && landscape) tabBar()
                Box(Modifier.weight(1f).fillMaxSize().windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal)).padding(bottom = if (showBars && !landscape) padding.calculateBottomPadding() else 0.dp)) {
                    NavHost(
                        navController, startDestination = Routes.MAIN,
                        // No cross-fades between destinations: the AnimatedContent inside NavHost is what got
                        // stuck mid-transition (blank content + stale selected tab). Screens appear instantly.
                        enterTransition = { androidx.compose.animation.EnterTransition.None },
                        exitTransition = { androidx.compose.animation.ExitTransition.None },
                        popEnterTransition = { androidx.compose.animation.EnterTransition.None },
                        popExitTransition = { androidx.compose.animation.ExitTransition.None },
                    ) {
                        composable(Routes.MAIN) {
                            tabStateHolder.SaveableStateProvider(tab) {
                                when (tab) {
                                    0 -> HomeScreen(navController)
                                    1 -> LibraryScreen(navController)
                                    2 -> PlaylistsScreen(navController)
                                    3 -> DownloadsScreen(snackbar, onSignIn = { navController.navigate(Routes.YT_SIGNIN) }, onPlayTrack = { t -> player.playTracks(listOf(t)); nowPlayingOpen = true })
                                    else -> SettingsScreen(navController, snackbar)
                                }
                            }
                        }
                        composable(Routes.SEARCH) { SearchScreen(navController) }
                        composable(Routes.LICENSES) { LicensesScreen(navController) }
                        composable(Routes.TRIM) { com.vibe.player.ui.trim.TrimScreen(navController, it.arguments?.getString("id")?.toLongOrNull() ?: -1L, snackbar) }
                        composable(Routes.YT_SIGNIN) { com.vibe.player.ui.settings.YouTubeSignInScreen(navController) { msg -> scope.launch { snackbar.showSnackbar(msg) } } }
                        composable(Routes.ALBUM) { AlbumScreen(navController, Routes.dec(it.arguments?.getString("key").orEmpty())) }
                        composable(Routes.ARTIST) { ArtistScreen(navController, Routes.dec(it.arguments?.getString("name").orEmpty())) }
                        composable(Routes.FOLDER) { FolderScreen(navController, Routes.dec(it.arguments?.getString("path").orEmpty())) }
                        composable(Routes.PLAYLIST) { PlaylistDetailScreen(navController, it.arguments?.getString("id")?.toLongOrNull() ?: -1L) }
                    }

                    androidx.compose.animation.AnimatedVisibility(selection.active, Modifier.align(Alignment.TopCenter), enter = slideInVertically { -it } + fadeIn(), exit = slideOutVertically { -it } + fadeOut()) {
                        com.vibe.player.ui.components.SelectionBar(
                            selection,
                            onPlay = { scope.launch { val ts = selectedTracks(); if (ts.isNotEmpty()) player.playTracks(ts); selection.clear() } },
                            onQueue = { scope.launch { val ts = selectedTracks(); if (ts.isNotEmpty()) { player.addToQueue(ts); snackbar.showSnackbar("Added ${ts.size} to queue") }; selection.clear() } },
                            onAddToPlaylist = { scope.launch { addToPlaylistIds = selectedTracks().map { it.id }; selection.clear() } },
                            onMoveToAlbum = { scope.launch { moveToAlbumIds = selectedTracks().map { it.id }; selection.clear() } },
                            onRemoveFromPlaylist = {
                                val pid = selection.playlistId; val alb = selection.albumName
                                scope.launch {
                                    val ids = selection.selected.mapNotNull { it.toLongOrNull() }
                                    if (pid != null) { ids.forEach { container.library.removeFromPlaylist(pid, it) }; snackbar.showSnackbar("Removed ${ids.size} from playlist") }
                                    else if (alb != null) { container.library.setAlbumForTracks(ids, ""); snackbar.showSnackbar("Removed ${ids.size} from \"$alb\"") }
                                    selection.clear()
                                }
                            },
                            onDelete = {
                                scope.launch {
                                    when (selection.kind) {
                                        com.vibe.player.ui.components.SelectionKind.PLAYLISTS -> batchDeletePlaylists = selection.selected.mapNotNull { it.toLongOrNull() }
                                        com.vibe.player.ui.components.SelectionKind.DOWNLOADS -> removeDownloadIds = selection.selected.mapNotNull { it.toLongOrNull() }
                                        else -> batchDeleteTracks = selectedTracks()
                                    }
                                }
                            },
                        )
                    }
                    // With the nav bar hidden (detail screens) the mini-player must clear the system gesture area itself.
                    Column(Modifier.align(Alignment.BottomCenter).then(if (showBars && !landscape) Modifier else Modifier.navigationBarsPadding()).padding(bottom = if (landscape) 4.dp else 8.dp)) {
                        AnimatedVisibility(playerState.current != null && !nowPlayingOpen, enter = slideInVertically { it * 2 } + fadeIn(), exit = slideOutVertically { it * 2 } + fadeOut()) {
                            MiniPlayer(playerState, onOpen = { nowPlayingOpen = true }, onTogglePlay = player::togglePlayPause, onNext = player::next)
                        }
                    }
                }
                }  // end tab Row
            }

            // The sheet animates itself fully off screen before calling onClose, so removal here must be instant
            // (any exit animation would show the sheet again for a frame).
            AnimatedVisibility(nowPlayingOpen && playerState.current != null, enter = slideInVertically(tween(320)) { it } + fadeIn(tween(200)), exit = fadeOut(androidx.compose.animation.core.snap())) {
                NowPlayingScreen(
                    state = playerState,
                    isFavorite = playerState.current?.trackId in favoriteIds,
                    onClose = { nowPlayingOpen = false },
                    onTogglePlay = player::togglePlayPause, onNext = player::next, onPrevious = player::previous, onSeek = player::seekTo,
                    onShuffle = player::toggleShuffle, onRepeat = player::cycleRepeat,
                    onToggleFavorite = { playerState.current?.let { c -> scope.launch { container.library.toggleFavorite(c.trackId) } } },
                    onMore = { playerState.current?.let { c -> scope.launch { container.library.track(c.trackId)?.let(trackMenu::open) } } },
                    onSkipTo = player::skipToQueueItem, onRemoveFromQueue = player::removeQueueItem, onMoveInQueue = player::moveQueueItem, onClearQueue = player::clearQueue,
                )
            }

            TrackMenuSheet(trackMenu, menuActions)

            removeDownloadIds?.let { ids ->
                androidx.compose.material3.AlertDialog(
                    onDismissRequest = { removeDownloadIds = null },
                    title = { Text("Remove ${ids.size} download${if (ids.size == 1) "" else "s"}?") },
                    text = { Text("Active downloads are cancelled. Files that already finished stay in your Music/Movies folder — use the library to delete those.") },
                    confirmButton = { androidx.compose.material3.TextButton(onClick = { scope.launch { ids.forEach { container.downloads.remove(it) }; snackbar.showSnackbar("Removed ${ids.size} download(s)") }; selection.clear(); removeDownloadIds = null }) { Text("Remove", color = androidx.compose.material3.MaterialTheme.colorScheme.error) } },
                    dismissButton = { androidx.compose.material3.TextButton(onClick = { removeDownloadIds = null }) { Text("Cancel") } },
                )
            }
            moveToAlbumIds?.let { ids ->
                com.vibe.player.ui.components.RenameDialog(
                    "Move ${ids.size} song${if (ids.size == 1) "" else "s"} to album", "Album", "",
                    "Pick an existing album or type a new name to create one. Only Cwave's library changes; the files' tags are untouched.",
                    albumNames, confirmLabel = "Move",
                    onDismiss = { moveToAlbumIds = null },
                    onSave = { name -> scope.launch { container.library.setAlbumForTracks(ids, name); snackbar.showSnackbar("Moved ${ids.size} to \"${name.trim()}\"") }; moveToAlbumIds = null },
                )
            }
            batchDeleteTracks?.let { ts ->
                androidx.compose.material3.AlertDialog(
                    onDismissRequest = { batchDeleteTracks = null },
                    icon = { Icon(androidx.compose.material.icons.Icons.Default.DeleteForever, null, tint = androidx.compose.material3.MaterialTheme.colorScheme.error) },
                    title = { Text("Delete ${ts.size} file${if (ts.size == 1) "" else "s"}?") },
                    text = { Text("These files will be permanently deleted from your device storage and removed from all playlists. This can't be undone." + if (ts.size <= 5) "\n\n" + ts.joinToString("\n") { "• " + it.title } else "") },
                    confirmButton = { androidx.compose.material3.TextButton(onClick = { runBatchDelete(ts); batchDeleteTracks = null }) { Text("Delete", color = androidx.compose.material3.MaterialTheme.colorScheme.error) } },
                    dismissButton = { androidx.compose.material3.TextButton(onClick = { batchDeleteTracks = null }) { Text("Cancel") } },
                )
            }
            batchDeletePlaylists?.let { ids ->
                androidx.compose.material3.AlertDialog(
                    onDismissRequest = { batchDeletePlaylists = null },
                    title = { Text("Delete ${ids.size} playlist${if (ids.size == 1) "" else "s"}?") },
                    text = { Text("The playlists are removed. Your music files are not touched.") },
                    confirmButton = { androidx.compose.material3.TextButton(onClick = { scope.launch { ids.forEach { container.library.deletePlaylist(it) }; snackbar.showSnackbar("Deleted ${ids.size} playlist(s)") }; selection.clear(); batchDeletePlaylists = null }) { Text("Delete", color = androidx.compose.material3.MaterialTheme.colorScheme.error) } },
                    dismissButton = { androidx.compose.material3.TextButton(onClick = { batchDeletePlaylists = null }) { Text("Cancel") } },
                )
            }

            deleteTarget?.let { t ->
                androidx.compose.material3.AlertDialog(
                    onDismissRequest = { deleteTarget = null },
                    icon = { Icon(androidx.compose.material.icons.Icons.Default.DeleteForever, null, tint = androidx.compose.material3.MaterialTheme.colorScheme.error) },
                    title = { Text("Delete this file?") },
                    text = { Text("\"${t.title}\" will be permanently deleted from your device storage and removed from all playlists. This can't be undone.\n\n${t.displayName}") },
                    confirmButton = { androidx.compose.material3.TextButton(onClick = { performDelete(t); deleteTarget = null }) { Text("Delete", color = androidx.compose.material3.MaterialTheme.colorScheme.error) } },
                    dismissButton = { androidx.compose.material3.TextButton(onClick = { deleteTarget = null }) { Text("Cancel") } },
                )
            }

            editTarget?.let { t ->
                com.vibe.player.ui.components.EditTrackDialog(
                    t, artistNames, albumNames, onDismiss = { editTarget = null },
                    onSave = { title, artist, album -> scope.launch { container.library.updateTags(t.id, title, artist, album); snackbar.showSnackbar("Saved") }; editTarget = null },
                )
            }

            val target = artTarget; val image = pickedImage
            if (target != null && image != null) {
                com.vibe.player.ui.components.ArtScopeDialog(
                    target, image,
                    onDismiss = { artTarget = null; pickedImage = null },
                    onSong = { scope.launch { container.artworkStore.setTrackArt(target.uri, image); snackbar.showSnackbar("Art updated for \"${target.title}\"") }; artTarget = null; pickedImage = null },
                    onAlbum = { scope.launch { container.artworkStore.setAlbumArt(target.albumKey, image); snackbar.showSnackbar("Art updated for album \"${target.album}\"") }; artTarget = null; pickedImage = null },
                )
            }

            addToPlaylistIds?.let { ids ->
                AddToPlaylistDialog(
                    playlists = playlists,
                    onDismiss = { addToPlaylistIds = null },
                    onPick = { pid -> scope.launch { val n = container.library.addToPlaylist(pid, ids); snackbar.showSnackbar(if (n > 0) "Added ${n} song(s) to playlist" else "Already in that playlist") }; addToPlaylistIds = null },
                    onCreate = { name -> scope.launch { val pid = container.library.createPlaylist(name); container.library.addToPlaylist(pid, ids); snackbar.showSnackbar("Created \"$name\"") }; addToPlaylistIds = null },
                )
            }
        }
    }
}
