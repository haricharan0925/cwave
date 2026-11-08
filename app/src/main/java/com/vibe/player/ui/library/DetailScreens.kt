package com.vibe.player.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.HideImage
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.rememberCoroutineScope
import com.vibe.player.ui.components.RenameDialog
import com.vibe.player.ui.components.rememberImagePicker
import com.vibe.player.ui.navigation.Routes
import kotlinx.coroutines.launch
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.material3.FilterChip
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Add
import androidx.compose.runtime.CompositionLocalProvider
import com.vibe.player.ui.components.LocalSelectionScope
import com.vibe.player.ui.components.SelectionScope
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import com.vibe.player.container
import com.vibe.player.data.db.Track
import com.vibe.player.metadata.Artwork
import com.vibe.player.ui.components.ArtworkImage
import com.vibe.player.ui.components.EmptyState
import com.vibe.player.ui.components.LocalTrackMenu
import com.vibe.player.ui.components.TrackRow
import com.vibe.player.ui.components.listBottomPadding
import com.vibe.player.ui.formatDurationLong
import com.vibe.player.ui.pluralize
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import androidx.compose.runtime.snapshotFlow

@Composable
fun AlbumScreen(nav: NavController, name: String) {
    val container = LocalContext.current.container
    val library = container.library
    val scope = rememberCoroutineScope()
    var menu by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    var adding by remember { mutableStateOf(false) }
    val albums by library.albumNames().collectAsState(emptyList())
    val tracksFlow = remember(name) { library.albumTracks(name) }
    val albumTracks by tracksFlow.collectAsState(emptyList())
    val artists = remember(albumTracks) { albumTracks.map { it.artist }.distinct() }
    val subtitle = when (artists.size) { 0 -> "Album"; 1 -> artists[0]; else -> "Various artists · ${artists.size}" }
    val coverKey = com.vibe.player.data.db.albumKeyOf(name)
    val pickCover = rememberImagePicker { uri -> scope.launch { container.artworkStore.setAlbumArt(coverKey, uri) } }
    val hasCover = container.artworkStore.version.collectAsState().value.let { container.artworkStore.hasAlbumArt(coverKey) }

    TrackCollectionScreen(
        nav, title = name, subtitle = subtitle, tracks = tracksFlow, numbered = artists.size <= 1, coverKey = coverKey, albumName = name, sortKey = "album",
        headerActions = {
            IconButton(onClick = { adding = true }) { Icon(Icons.Default.Add, "Add songs to album") }
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "Album options") }
                DropdownMenu(menu, { menu = false }) {
                    DropdownMenuItem(text = { Text("Add songs") }, leadingIcon = { Icon(Icons.Default.Add, null) }, onClick = { menu = false; adding = true })
                    DropdownMenuItem(text = { Text("Set album cover") }, leadingIcon = { Icon(Icons.Default.Image, null) }, onClick = { menu = false; pickCover() })
                    if (hasCover) DropdownMenuItem(text = { Text("Remove custom cover") }, leadingIcon = { Icon(Icons.Default.HideImage, null) }, onClick = { menu = false; container.artworkStore.removeAlbumArt(coverKey) })
                    DropdownMenuItem(text = { Text("Rename album") }, leadingIcon = { Icon(Icons.Default.Edit, null) }, onClick = { menu = false; renaming = true })
                }
            }
        },
    )
    if (adding) com.vibe.player.ui.playlists.AddSongsSheet(
        library, alreadyIn = albumTracks.map { it.id }.toSet(), onDismiss = { adding = false },
        onAdd = { ids -> scope.launch { library.setAlbumForTracks(ids, name) }; adding = false },
        title = "Add songs to \"$name\"", inLabel = "Already in this album",
    )
    if (renaming) RenameDialog(
        "Rename album", "Album", name, "Applies to every song in this album. Pick an existing name to merge into that album.", albums,
        onDismiss = { renaming = false },
        onSave = { new -> scope.launch { library.renameAlbum(name, new) }; renaming = false; nav.popBackStack(); nav.navigate(Routes.album(new)) },
    )
}

@Composable
fun ArtistScreen(nav: NavController, name: String) {
    val library = LocalContext.current.container.library
    val scope = rememberCoroutineScope()
    var menu by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    val artists by library.artistNames().collectAsState(emptyList())
    TrackCollectionScreen(
        nav, title = name, subtitle = "Artist", tracks = library.artistTracks(name), round = true, sortKey = "artist",
        headerActions = {
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "Artist options") }
                DropdownMenu(menu, { menu = false }) {
                    DropdownMenuItem(text = { Text("Rename artist") }, leadingIcon = { Icon(Icons.Default.Edit, null) }, onClick = { menu = false; renaming = true })
                }
            }
        },
    )
    if (renaming) RenameDialog(
        "Rename artist", "Artist", name, "Applies to every song by this artist. Pick an existing name to merge into that artist.", artists,
        onDismiss = { renaming = false },
        onSave = { new -> scope.launch { library.renameArtist(name, new) }; renaming = false; nav.popBackStack(); nav.navigate(Routes.artist(new)) },
    )
}

@Composable
fun FolderScreen(nav: NavController, path: String) {
    val library = LocalContext.current.container.library
    TrackCollectionScreen(nav, title = path.removePrefix("import:").substringAfterLast('/').ifBlank { path }, subtitle = if (path.startsWith("import:")) "Imported folder" else path, tracks = library.folderTracks(path), sortKey = "folder")
}

/** Shared header + list for album / artist / folder / playlist-like collections. */
@Composable
fun TrackCollectionScreen(
    nav: NavController,
    title: String,
    subtitle: String,
    tracks: Flow<List<Track>>,
    numbered: Boolean = false,
    round: Boolean = false,
    headerActions: @Composable (() -> Unit)? = null,
    playlistId: Long? = null,
    /** Album-art store key whose custom cover (if any) should be shown in the header. */
    coverKey: String? = null,
    /** Set on the album screen so multi-select offers "Remove from album". */
    albumName: String? = null,
    /** When set, rows get a drag handle and this is called with the new track-id order after each drop. */
    onReorder: ((List<Long>) -> Unit)? = null,
    /** Settings key for the saved sort ("album", "playlist", …). Null hides the sort control. */
    sortKey: String? = null,
) {
    val container = LocalContext.current.container
    val player = container.player
    val menu = LocalTrackMenu.current
    val raw by tracks.collectAsState(emptyList())
    val settings by container.settings.settings.collectAsState(null)
    val scope = rememberCoroutineScope()
    // Saved per collection type: "DEFAULT" keeps the natural order (track number / playlist position).
    val savedSort = sortKey?.let { settings?.collectionSorts?.get(it) } ?: "DEFAULT"
    val sortMode = savedSort.substringBefore(':')
    val sortDesc = savedSort.endsWith(":desc")
    val source = remember(raw, sortMode, sortDesc) {
        val sorted = when (sortMode) {
            "NAME" -> raw.sortedBy { it.title.lowercase() }
            "DATE_ADDED" -> raw.sortedBy { it.dateAdded }
            else -> raw
        }
        if (sortDesc) sorted.reversed() else sorted
    }
    fun setSort(mode: String, desc: Boolean) { sortKey?.let { k -> scope.launch { container.settings.setCollectionSort(k, if (desc) "$mode:desc" else mode) } } }
    // Local copy so a drag feels instant; the repository write catches up through the flow.
    var list by remember(source) { mutableStateOf(source) }
    val indicator by player.indicator.collectAsStateWithLifecycle()
    val currentId = indicator.trackId
    val cover = list.firstOrNull { it.artworkUri != null } ?: list.firstOrNull()
    val listState = rememberLazyListState()
    val reorderState = rememberReorderableLazyListState(listState) { from, to ->
        val fromIdx = list.indexOfFirst { it.id == from.key }; val toIdx = list.indexOfFirst { it.id == to.key }
        if (fromIdx >= 0 && toIdx >= 0) list = list.toMutableList().apply { add(toIdx, removeAt(fromIdx)) }
    }

    CompositionLocalProvider(LocalSelectionScope provides SelectionScope({ list.map { it.id.toString() } }, playlistId, albumName)) {
    Box(Modifier.fillMaxSize()) {
        if (cover != null) {
            AsyncImage(model = Artwork(cover.uri, cover.artworkUri), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxWidth().height(320.dp).blur(40.dp).alpha(0.45f))
            Box(Modifier.fillMaxWidth().height(320.dp).background(Brush.verticalGradient(listOf(MaterialTheme.colorScheme.background.copy(alpha = 0.6f), MaterialTheme.colorScheme.background))))
        }
        LazyColumn(Modifier.fillMaxSize(), state = listState, contentPadding = listBottomPadding) {
            item {
                Column(Modifier.statusBarsPadding()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                        Spacer(Modifier.weight(1f))
                        headerActions?.invoke()
                    }
                    Row(Modifier.padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                        ArtworkImage(cover?.uri ?: "", cover?.artworkUri, Modifier.size(132.dp), shape = if (round) androidx.compose.foundation.shape.CircleShape else MaterialTheme.shapes.large, iconSize = 48.dp, seed = title, albumKey = coverKey ?: cover?.albumKey)
                        Spacer(Modifier.width(18.dp))
                        Column {
                            Text(title, style = MaterialTheme.typography.headlineSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Spacer(Modifier.height(4.dp))
                            Text("${pluralize(list.size, "song")} · ${formatDurationLong(list.sumOf { it.durationMs })}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Row(Modifier.padding(horizontal = 20.dp, vertical = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(onClick = { player.playTracks(list) }, enabled = list.isNotEmpty(), modifier = Modifier.weight(1f)) { Icon(Icons.Default.PlayArrow, null); Spacer(Modifier.width(6.dp)); Text("Play") }
                        FilledTonalButton(onClick = { player.playShuffled(list) }, enabled = list.isNotEmpty(), modifier = Modifier.weight(1f)) { Icon(Icons.Default.Shuffle, null); Spacer(Modifier.width(6.dp)); Text("Shuffle") }
                    }
                    if (sortKey != null && list.size > 1) {
                        var sortMenu by remember { mutableStateOf(false) }
                        Row(Modifier.padding(horizontal = 20.dp).padding(bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("Sort", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.width(10.dp))
                            Box {
                                FilterChip(
                                    selected = sortMode != "DEFAULT", onClick = { sortMenu = true },
                                    label = { Text(when (sortMode) { "NAME" -> "Name"; "DATE_ADDED" -> "Date added"; else -> if (onReorder != null) "Custom order" else "Default" }) },
                                    trailingIcon = { if (sortMode != "DEFAULT") Icon(if (sortDesc) Icons.Default.ArrowDownward else Icons.Default.ArrowUpward, null, Modifier.size(16.dp)) },
                                )
                                DropdownMenu(sortMenu, { sortMenu = false }) {
                                    DropdownMenuItem(text = { Text(if (onReorder != null) "Custom order" else "Default") }, onClick = { setSort("DEFAULT", false); sortMenu = false })
                                    DropdownMenuItem(text = { Text("Name" + if (sortMode == "NAME") (if (sortDesc) "  (Z→A)" else "  (A→Z)") else "") }, onClick = { setSort("NAME", sortMode == "NAME" && !sortDesc); sortMenu = false })
                                    DropdownMenuItem(text = { Text("Date added" + if (sortMode == "DATE_ADDED") (if (sortDesc) "  (newest first)" else "  (oldest first)") else "") }, onClick = { setSort("DATE_ADDED", sortMode == "DATE_ADDED" && !sortDesc); sortMenu = false })
                                }
                            }
                            if (onReorder != null && sortMode != "DEFAULT") {
                                Spacer(Modifier.width(8.dp))
                                Text("Drag to reorder is off while sorted", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
            if (list.isEmpty()) item { EmptyState(Icons.Default.PlayArrow, "Nothing here", if (onReorder != null) "Tap + at the top to add songs to this playlist." else "No playable songs in this collection.") }
            itemsIndexed(list, key = { _, t -> t.id }) { index, t ->
                val row: @Composable (Modifier, (@Composable () -> Unit)?) -> Unit = { mod, handle ->
                    Row(mod.padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        handle?.invoke()
                        TrackRow(
                            t, Modifier.weight(1f), isCurrent = t.id == currentId, isPlaying = indicator.isPlaying(t.id),
                            subtitle = when {
                                onReorder != null -> "${index + 1} · ${t.artist}"
                                numbered && t.trackNumber > 0 -> "${t.trackNumber} · ${t.artist}"
                                else -> t.artist
                            },
                            onClick = { player.playTracks(list, index) }, onMore = { menu.open(t, playlistId) },
                        )
                    }
                }
                if (onReorder == null || sortMode != "DEFAULT") row(Modifier.animateItem(), null)
                else ReorderableItem(reorderState, key = t.id) { dragging ->
                    row(Modifier.background(if (dragging) MaterialTheme.colorScheme.surfaceContainerHighest else Color.Transparent)) {
                        Icon(
                            Icons.Default.DragHandle, "Reorder", tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 4.dp).draggableHandle(onDragStopped = { onReorder(list.map { it.id }) }),
                        )
                    }
                }
            }
        }
    }
    }
}

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class, kotlinx.coroutines.FlowPreview::class)
@Composable
fun SearchScreen(nav: NavController) {
    val container = LocalContext.current.container
    val library = container.library
    val player = container.player
    val menu = LocalTrackMenu.current
    var query by rememberSaveable { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    val results by remember { snapshotFlow { query }.debounce(150).flatMapLatest { q -> if (q.isBlank()) flowOf(emptyList()) else library.search(q) } }.collectAsState(emptyList())
    val indicator by player.indicator.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { focus.requestFocus() }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(Modifier.padding(start = 4.dp, end = 12.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
            OutlinedTextField(
                query, { query = it }, Modifier.weight(1f).focusRequester(focus), singleLine = true,
                placeholder = { Text("Search songs, artists, albums, files") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Default.Close, "Clear") } },
                shape = MaterialTheme.shapes.extraLarge,
            )
        }
        when {
            query.isBlank() -> EmptyState(Icons.Default.Search, "Search your library", "Matches titles, artists, albums and file names.")
            results.isEmpty() -> EmptyState(Icons.Default.Search, "No matches", "Nothing in your library matches \"$query\".")
            else -> CompositionLocalProvider(LocalSelectionScope provides SelectionScope({ results.map { it.id.toString() } })) { LazyColumn(contentPadding = listBottomPadding) {
                item { Text(pluralize(results.size, "result"), Modifier.padding(horizontal = 20.dp, vertical = 8.dp), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                items(results, key = { it.id }) { t ->
                    TrackRow(t, Modifier.padding(horizontal = 8.dp), isCurrent = indicator.isCurrent(t.id), isPlaying = indicator.isPlaying(t.id),
                        onClick = { player.playTracks(results, results.indexOf(t)) }, onMore = { menu.open(t) })
                }
            } }
        }
    }
}
