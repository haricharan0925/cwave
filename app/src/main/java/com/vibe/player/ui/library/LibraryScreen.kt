package com.vibe.player.ui.library

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.background
import androidx.compose.ui.draw.clip
import androidx.compose.material3.Checkbox
import com.vibe.player.ui.components.LocalSelection
import com.vibe.player.ui.components.LocalSelectionScope
import com.vibe.player.ui.components.SelectionKind
import com.vibe.player.ui.components.SelectionScope
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.vibe.player.container
import com.vibe.player.settings.LibrarySort
import com.vibe.player.ui.appViewModel
import com.vibe.player.ui.components.ArtworkImage
import com.vibe.player.ui.components.EmptyState
import com.vibe.player.ui.components.LoadingBar
import com.vibe.player.ui.components.LocalTrackMenu
import com.vibe.player.ui.components.TrackRow
import com.vibe.player.ui.components.listBottomPadding
import com.vibe.player.ui.formatDurationLong
import com.vibe.player.ui.navigation.Routes
import com.vibe.player.ui.pluralize
import kotlinx.coroutines.launch

private val tabs = listOf("Songs", "Albums", "Artists", "Folders")
const val PAGE = 100

/** Long lists stop after [PAGE] rows; the footer loads more or everything. Search is the fast path for big libraries. */
@Composable
fun ShowMoreFooter(remaining: Int, onMore: () -> Unit, onAll: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("$remaining more", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        TextButton(onClick = onMore) { Text("Show ${minOf(PAGE, remaining)} more") }
        TextButton(onClick = onAll) { Text("Show all") }
    }
}

@Composable
fun LibraryScreen(nav: NavController) {
    val context = LocalContext.current
    val container = context.container
    val library = container.library
    val player = container.player
    val vm = appViewModel { LibraryViewModel(it) }
    val scope = rememberCoroutineScope()
    val menu = LocalTrackMenu.current
    val sel = LocalSelection.current

    var tab by remember { mutableIntStateOf(vm.selectedTab) }
    LaunchedEffect(tab) { vm.selectedTab = tab }
    val songs by vm.songs.collectAsStateWithLifecycle()
    val albums by library.albums().collectAsState(emptyList())
    val artists by library.artists().collectAsState(emptyList())
    val folders by library.folders().collectAsState(emptyList())
    val scanning by library.scanning.collectAsStateWithLifecycle()
    val songsListState = androidx.compose.foundation.lazy.rememberLazyListState(vm.songsFirstVisible, vm.songsFirstVisibleOffset)
    LaunchedEffect(songsListState) {
        snapshotFlow { songsListState.firstVisibleItemIndex to songsListState.firstVisibleItemScrollOffset }
            .collect { (i, o) -> vm.songsFirstVisible = i; vm.songsFirstVisibleOffset = o }
    }
    val indicator by player.indicator.collectAsStateWithLifecycle()
    val currentId = indicator.trackId
    val snackbar = remember { SnackbarHostState() }
    var importMenu by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }

    val pickFiles = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) scope.launch {
            status = "Importing ${uris.size} file(s)…"
            val r = library.importFiles(uris)
            status = "Imported ${r.imported}" + (if (r.skipped > 0) ", ${r.skipped} already in library" else "") + (if (r.failed > 0) ", ${r.failed} unreadable" else "")
        }
    }
    val pickTree = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) scope.launch {
            status = "Scanning folder…"
            val r = library.importTree(uri)
            status = "Imported ${r.imported} from folder" + (if (r.skipped > 0) ", ${r.skipped} already in library" else "")
        }
    }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Library", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
            IconButton(onClick = { nav.navigate(Routes.SEARCH) }) { Icon(Icons.Default.Search, "Search") }
            IconButton(onClick = { scope.launch { status = "Rescanning…"; val r = library.rescan(); status = "Found ${r.found} songs, ${r.added} new" + (if (r.missing > 0) ", ${r.missing} missing" else "") } }) {
                Icon(Icons.Default.Refresh, "Rescan")
            }
            Box {
                IconButton(onClick = { importMenu = true }) { Icon(Icons.Default.Add, "Import") }
                DropdownMenu(importMenu, { importMenu = false }) {
                    DropdownMenuItem(text = { Text("Import audio files") }, leadingIcon = { Icon(Icons.Default.AudioFile, null) }, onClick = { importMenu = false; pickFiles.launch(arrayOf("audio/*")) })
                    DropdownMenuItem(text = { Text("Import a folder") }, leadingIcon = { Icon(Icons.Default.CreateNewFolder, null) }, onClick = { importMenu = false; pickTree.launch(null) })
                }
            }
        }
        status?.let { s ->
            Row(Modifier.padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(s, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
                TextButton(onClick = { status = null }) { Text("Dismiss") }
            }
        }
        LoadingBar(scanning)

        TabRow(tab, containerColor = Color.Transparent, divider = {}) {
            tabs.forEachIndexed { i, label -> Tab(selected = tab == i, onClick = { tab = i }, text = { Text(label) }) }
        }

        when (tab) {
            0 -> SongsTab(songs, currentId, indicator.playing, vm, listState = songsListState, onPlay = { list, i -> player.playTracks(list, i) }, onShuffle = { player.playShuffled(songs.tracks) }, onMore = { menu.open(it) }, onImport = { pickFiles.launch(arrayOf("audio/*")) })
            1 -> if (albums.isEmpty()) EmptyState(Icons.Default.LibraryMusic, "No albums", "Albums appear once songs are in your library.") else
                LazyVerticalGrid(GridCells.Adaptive(minSize = 150.dp), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 160.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    items(albums, key = { it.album + it.artist }) { a ->
                        val albumKey = com.vibe.player.data.db.albumKeyOf(a.album)
                        val albumMode = sel.inMode(SelectionKind.ALBUMS)
                        val albumPicked = albumMode && sel.isSelected(albumKey)
                        Box {
                        Column(
                            Modifier.clip(MaterialTheme.shapes.medium)
                                .background(if (albumPicked) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f) else Color.Transparent)
                                .combinedClickable(
                                    onClick = { if (albumMode) sel.toggle(albumKey) else nav.navigate(Routes.album(a.album)) },
                                    onLongClick = { if (!sel.active) sel.start(albumKey, SelectionKind.ALBUMS, { albums.map { com.vibe.player.data.db.albumKeyOf(it.album) } }) else if (albumMode) sel.toggle(albumKey) },
                                )
                                .padding(6.dp),
                        ) {
                            ArtworkImage(a.firstTrackUri, a.artworkUri, Modifier.fillMaxWidth().aspectRatio(1f), shape = MaterialTheme.shapes.medium, iconSize = 40.dp)
                            Spacer(Modifier.height(8.dp))
                            Text(a.album, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("${a.artist} · ${pluralize(a.trackCount, "song")}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        if (albumMode) Checkbox(albumPicked, onCheckedChange = { sel.toggle(albumKey) }, modifier = Modifier.align(Alignment.TopEnd).padding(2.dp))
                        }
                    }
                }
            2 -> if (artists.isEmpty()) EmptyState(Icons.Default.Person, "No artists", "Artists appear once songs are in your library.") else
                LazyColumn(contentPadding = listBottomPadding) {
                    items(artists, key = { it.artist }) { a ->
                        ListItem(
                            headlineContent = { Text(a.artist, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            supportingContent = { Text("${pluralize(a.albumCount, "album")} · ${pluralize(a.trackCount, "song")}") },
                            leadingContent = { ArtworkImage(a.firstTrackUri, a.artworkUri, Modifier.size(52.dp), shape = androidx.compose.foundation.shape.CircleShape) },
                            modifier = Modifier.clickable { nav.navigate(Routes.artist(a.artist)) },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        )
                    }
                }
            3 -> if (folders.isEmpty()) EmptyState(Icons.Default.Folder, "No folders", "Folders appear once songs are in your library.") else
                LazyColumn(contentPadding = listBottomPadding) {
                    items(folders, key = { it.folder }) { f ->
                        var folderMenu by remember { mutableStateOf(false) }
                        ListItem(
                            headlineContent = { Text(f.folderName, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            supportingContent = { Text(if (f.folder.startsWith("import:")) "Imported · ${pluralize(f.trackCount, "song")}" else "${f.folder} · ${pluralize(f.trackCount, "song")}", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            leadingContent = { Icon(Icons.Default.Folder, null, Modifier.size(32.dp), tint = MaterialTheme.colorScheme.primary) },
                            trailingContent = {
                                Box {
                                    IconButton(onClick = { folderMenu = true }) { Icon(Icons.Default.MoreVert, "Folder options") }
                                    DropdownMenu(folderMenu, { folderMenu = false }) {
                                        DropdownMenuItem(
                                            text = { Text("Hide this folder") }, leadingIcon = { Icon(Icons.Default.VisibilityOff, null) },
                                            onClick = { folderMenu = false; scope.launch { library.hideFolder(f.folder); status = "Hidden \"${f.folderName}\" — unhide from Settings" } },
                                        )
                                    }
                                }
                            },
                            modifier = Modifier.clickable { nav.navigate(Routes.folder(f.folder)) },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        )
                    }
                }
        }
    }
}

@Composable
private fun SongsTab(
    state: SongsUiState, currentId: Long?, isPlaying: Boolean, vm: LibraryViewModel, listState: androidx.compose.foundation.lazy.LazyListState,
    onPlay: (List<com.vibe.player.data.db.Track>, Int) -> Unit, onShuffle: () -> Unit, onMore: (com.vibe.player.data.db.Track) -> Unit, onImport: () -> Unit,
) {
    var sortMenu by remember { mutableStateOf(false) }
    var shownCount by remember { mutableIntStateOf(vm.shownCount) }
    LaunchedEffect(shownCount) { vm.shownCount = shownCount }
    LaunchedEffect(state.sort, state.formatFilter) { if (vm.shownCount == PAGE) shownCount = PAGE }
    Column {
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            item {
                Box {
                    FilterChip(
                        selected = true, onClick = { sortMenu = true },
                        label = { Text(state.sort.label) },
                        trailingIcon = { Icon(if (state.desc) Icons.Default.ArrowDownward else Icons.Default.ArrowUpward, null, Modifier.size(16.dp)) },
                    )
                    DropdownMenu(sortMenu, { sortMenu = false }) {
                        LibrarySort.entries.forEach { s -> DropdownMenuItem(text = { Text(s.label) }, onClick = { vm.setSort(s); sortMenu = false }) }
                    }
                }
            }
            item { FilterChip(selected = state.formatFilter == null, onClick = { vm.setFormatFilter(null) }, label = { Text("All") }) }
            items(state.formats) { f -> FilterChip(selected = state.formatFilter == f, onClick = { vm.setFormatFilter(if (state.formatFilter == f) null else f) }, label = { Text(f) }) }
        }
        if (state.total == 0) {
            EmptyState(Icons.Default.LibraryMusic, "No songs yet", "Grant media access, or import files and folders with the + button.", action = "Import files", onAction = onImport)
            return
        }
        CompositionLocalProvider(LocalSelectionScope provides SelectionScope({ state.tracks.map { it.id.toString() } })) {
        LazyColumn(state = listState, contentPadding = listBottomPadding) {
            item {
                Row(Modifier.padding(horizontal = 20.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("${pluralize(state.tracks.size, "song")} · ${formatDurationLong(state.tracks.sumOf { it.durationMs })}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                    TextButton(onClick = onShuffle) { Icon(Icons.Default.Shuffle, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Shuffle") }
                }
            }
            val visible = state.tracks.take(shownCount)
            items(visible, key = { it.id }) { t ->
                TrackRow(t, Modifier.padding(horizontal = 8.dp), isCurrent = t.id == currentId, isPlaying = t.id == currentId && isPlaying, onClick = { onPlay(state.tracks, state.tracks.indexOf(t)) }, onMore = { onMore(t) })
            }
            if (state.tracks.size > shownCount) item {
                ShowMoreFooter(remaining = state.tracks.size - shownCount, onMore = { shownCount += PAGE }, onAll = { shownCount = state.tracks.size })
            }
        }
        }
    }
}
