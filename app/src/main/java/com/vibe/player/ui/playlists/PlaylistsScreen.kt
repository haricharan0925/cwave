package com.vibe.player.ui.playlists

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.HideImage
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MoreVert
import com.vibe.player.ui.components.playlistCoverKey
import com.vibe.player.ui.components.rememberImagePicker
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.foundation.combinedClickable
import androidx.compose.material3.Checkbox
import com.vibe.player.ui.components.LocalSelection
import com.vibe.player.ui.components.SelectionKind
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.vibe.player.container
import com.vibe.player.data.db.Track
import com.vibe.player.ui.components.ArtworkImage
import com.vibe.player.ui.components.EmptyState
import com.vibe.player.ui.components.LocalTrackMenu
import com.vibe.player.ui.components.TrackRow
import com.vibe.player.ui.components.listBottomPadding
import com.vibe.player.ui.formatDurationLong
import com.vibe.player.ui.library.TrackCollectionScreen
import com.vibe.player.ui.navigation.Routes
import com.vibe.player.ui.pluralize
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

const val FAVORITES_ID = -1L

@Composable
fun PlaylistsScreen(nav: NavController) {
    val library = LocalContext.current.container.library
    val scope = rememberCoroutineScope()
    val sel = LocalSelection.current
    val playlists by library.playlistSummaries().collectAsState(emptyList())
    val favorites by library.favoriteTracks().collectAsState(emptyList())
    var creating by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<Pair<Long, String>?>(null) }
    var deleting by remember { mutableStateOf<Pair<Long, String>?>(null) }
    val artworkStore = LocalContext.current.container.artworkStore
    var coverTarget by remember { mutableStateOf<Long?>(null) }
    val pickCover = rememberImagePicker { uri -> coverTarget?.let { id -> scope.launch { artworkStore.setAlbumArt(playlistCoverKey(id), uri) } } }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Playlists", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
            IconButton(onClick = { creating = true }) { Icon(Icons.Default.Add, "New playlist") }
        }
        LazyColumn(contentPadding = listBottomPadding) {
            item {
                ListItem(
                    headlineContent = { Text("Favorites") },
                    supportingContent = { Text("${pluralize(favorites.size, "song")} · ${formatDurationLong(favorites.sumOf { it.durationMs })}") },
                    leadingContent = {
                        androidx.compose.foundation.layout.Box(Modifier.size(56.dp).clip(MaterialTheme.shapes.small).background(MaterialTheme.colorScheme.tertiaryContainer), contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Favorite, null, tint = MaterialTheme.colorScheme.onTertiaryContainer)
                        }
                    },
                    modifier = Modifier.clickable { nav.navigate(Routes.playlist(FAVORITES_ID)) },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                )
            }
            if (playlists.isEmpty()) item { EmptyState(Icons.AutoMirrored.Filled.QueueMusic, "No playlists yet", "Create one with +, or use a song's menu → Add to playlist.", action = "Create playlist", onAction = { creating = true }) }
            items(playlists, key = { it.id }) { p ->
                var menu by remember { mutableStateOf(false) }
                val pKey = p.id.toString()
                val plMode = sel.inMode(SelectionKind.PLAYLISTS)
                val plPicked = plMode && sel.isSelected(pKey)
                ListItem(
                    headlineContent = { Text(p.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    supportingContent = { Text("${pluralize(p.trackCount, "song")} · ${formatDurationLong(p.durationMs)}") },
                    leadingContent = { if (plMode) Checkbox(plPicked, onCheckedChange = { sel.toggle(pKey) }) else ArtworkImage(p.firstTrackUri ?: "", p.artworkUri, Modifier.size(56.dp), seed = p.name, albumKey = playlistCoverKey(p.id)) },
                    trailingContent = {
                        androidx.compose.foundation.layout.Box {
                            IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "Options") }
                            DropdownMenu(menu, { menu = false }) {
                                DropdownMenuItem(text = { Text("Set cover") }, leadingIcon = { Icon(Icons.Default.Image, null) }, onClick = { menu = false; coverTarget = p.id; pickCover() })
                                DropdownMenuItem(text = { Text("Rename") }, leadingIcon = { Icon(Icons.Default.Edit, null) }, onClick = { menu = false; renaming = p.id to p.name })
                                DropdownMenuItem(text = { Text("Delete") }, leadingIcon = { Icon(Icons.Default.Delete, null) }, onClick = { menu = false; deleting = p.id to p.name })
                            }
                        }
                    },
                    modifier = Modifier.combinedClickable(
                        onClick = { if (plMode) sel.toggle(pKey) else nav.navigate(Routes.playlist(p.id)) },
                        onLongClick = { if (!sel.active) sel.start(pKey, SelectionKind.PLAYLISTS, { playlists.map { it.id.toString() } }) else if (plMode) sel.toggle(pKey) },
                    ),
                    colors = ListItemDefaults.colors(containerColor = if (plPicked) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f) else Color.Transparent),
                )
            }
        }
    }

    if (creating) NameDialog("New playlist", "", onDismiss = { creating = false }) { name -> scope.launch { library.createPlaylist(name) }; creating = false }
    renaming?.let { (id, old) -> NameDialog("Rename playlist", old, onDismiss = { renaming = null }) { name -> scope.launch { library.renamePlaylist(id, name) }; renaming = null } }
    deleting?.let { (id, name) ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Delete \"$name\"?") },
            text = { Text("The playlist is removed. Your music files are not touched.") },
            confirmButton = { TextButton(onClick = { scope.launch { library.deletePlaylist(id) }; deleting = null }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel") } },
        )
    }
}

@Composable
fun NameDialog(title: String, initial: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var name by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { OutlinedTextField(name, { name = it }, singleLine = true, label = { Text("Name") }, modifier = Modifier.fillMaxWidth()) },
        confirmButton = { TextButton(onClick = { onConfirm(name) }, enabled = name.isNotBlank()) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun PlaylistDetailScreen(nav: NavController, id: Long) {
    val container = LocalContext.current.container
    val library = container.library
    val player = container.player
    val menu = LocalTrackMenu.current
    val scope = rememberCoroutineScope()

    if (id == FAVORITES_ID) {
        TrackCollectionScreen(nav, title = "Favorites", subtitle = "Songs you've hearted", tracks = library.favoriteTracks(), sortKey = "favorites")
        return
    }

    val playlist by library.playlist(id).collectAsState(null)
    val tracksFlow = remember(id) { library.playlistTracks(id) }
    val inPlaylist by tracksFlow.collectAsState(emptyList())
    var adding by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    val artworkStore = container.artworkStore
    val coverKey = playlistCoverKey(id)
    val pickCover = rememberImagePicker { uri -> scope.launch { artworkStore.setAlbumArt(coverKey, uri) } }
    val hasCover = artworkStore.version.collectAsState().value.let { artworkStore.hasAlbumArt(coverKey) }
    var deleting by remember { mutableStateOf(false) }
    var optionsMenu by remember { mutableStateOf(false) }

    TrackCollectionScreen(
        nav, title = playlist?.name ?: "Playlist", subtitle = "Playlist · drag ≡ to reorder",
        tracks = tracksFlow,
        playlistId = id,
        coverKey = coverKey,
        onReorder = { ids -> scope.launch { library.reorderPlaylist(id, ids) } },
        sortKey = "playlist",
        headerActions = {
            IconButton(onClick = { adding = true }) { Icon(Icons.Default.Add, "Add songs") }
            androidx.compose.foundation.layout.Box {
                IconButton(onClick = { optionsMenu = true }) { Icon(Icons.Default.MoreVert, "Options") }
                DropdownMenu(optionsMenu, { optionsMenu = false }) {
                    DropdownMenuItem(text = { Text("Set cover") }, leadingIcon = { Icon(Icons.Default.Image, null) }, onClick = { optionsMenu = false; pickCover() })
                    if (hasCover) DropdownMenuItem(text = { Text("Remove cover") }, leadingIcon = { Icon(Icons.Default.HideImage, null) }, onClick = { optionsMenu = false; artworkStore.removeAlbumArt(coverKey) })
                    DropdownMenuItem(text = { Text("Rename") }, leadingIcon = { Icon(Icons.Default.Edit, null) }, onClick = { optionsMenu = false; renaming = true })
                    DropdownMenuItem(text = { Text("Delete playlist") }, leadingIcon = { Icon(Icons.Default.Delete, null) }, onClick = { optionsMenu = false; deleting = true })
                }
            }
        },
    )

    if (adding) AddSongsSheet(
        library, alreadyIn = inPlaylist.map { it.id }.toSet(), onDismiss = { adding = false },
        onAdd = { ids -> scope.launch { library.addToPlaylist(id, ids) }; adding = false },
    )
    if (renaming) NameDialog("Rename playlist", playlist?.name ?: "", onDismiss = { renaming = false }) { scope.launch { library.renamePlaylist(id, it) }; renaming = false }
    if (deleting) AlertDialog(
        onDismissRequest = { deleting = false },
        title = { Text("Delete this playlist?") },
        text = { Text("Your music files are not touched.") },
        confirmButton = { TextButton(onClick = { scope.launch { library.deletePlaylist(id) }; deleting = false; nav.popBackStack() }) { Text("Delete") } },
        dismissButton = { TextButton(onClick = { deleting = false }) { Text("Cancel") } },
    )
}
