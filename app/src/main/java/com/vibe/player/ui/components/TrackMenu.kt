package com.vibe.player.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.HideImage
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material.icons.filled.RemoveCircleOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.clickable
import com.vibe.player.data.db.PlaylistSummary
import com.vibe.player.data.db.Track
import com.vibe.player.ui.formatBytes
import com.vibe.player.ui.formatDuration

/** Custom playlist covers reuse the album-art store under a reserved key. */
fun playlistCoverKey(playlistId: Long) = "playlist\u0001$playlistId"

/** Screens call `LocalTrackMenu.current.open(track)`; the root hosts the sheet so it floats above everything. */
class TrackMenuController {
    var track by mutableStateOf<Track?>(null)
        private set
    var removeFromPlaylistId by mutableStateOf<Long?>(null)
        private set

    fun open(track: Track, inPlaylistId: Long? = null) { this.track = track; removeFromPlaylistId = inPlaylistId }
    fun close() { track = null; removeFromPlaylistId = null }
}

val LocalTrackMenu = compositionLocalOf { TrackMenuController() }

data class TrackMenuActions(
    val isFavorite: (Long) -> Boolean,
    val onPlayNext: (Track) -> Unit,
    val onAddToQueue: (Track) -> Unit,
    val onToggleFavorite: (Track) -> Unit,
    val onAddToPlaylist: (Track) -> Unit,
    val onRemoveFromPlaylist: (playlistId: Long, Track) -> Unit,
    val onGoToAlbum: (Track) -> Unit,
    val onGoToArtist: (Track) -> Unit,
    val hasCustomArt: (Track) -> Boolean,
    val onSetArt: (Track) -> Unit,
    val onRemoveArt: (Track) -> Unit,
    val onEdit: (Track) -> Unit,
    val onDelete: (Track) -> Unit,
    val onTrim: (Track) -> Unit,
)

@Composable
fun TrackMenuSheet(controller: TrackMenuController, actions: TrackMenuActions) {
    val track = controller.track ?: return
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showDetails by remember { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = controller::close, sheetState = sheetState) {
        Row(Modifier.padding(horizontal = 20.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            ArtworkImage(track.uri, track.artworkUri, Modifier.size(56.dp), albumKey = track.albumKey)
            Spacer(Modifier.width(14.dp))
            Column {
                Text(track.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(track.artist, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Spacer(Modifier.height(8.dp))
        Divider(color = MaterialTheme.colorScheme.outlineVariant)
        val fav = actions.isFavorite(track.id)
        MenuItem(Icons.Default.PlaylistPlay, "Play next") { actions.onPlayNext(track); controller.close() }
        MenuItem(Icons.AutoMirrored.Filled.QueueMusic, "Add to queue") { actions.onAddToQueue(track); controller.close() }
        MenuItem(if (fav) Icons.Default.Favorite else Icons.Default.FavoriteBorder, if (fav) "Remove from favorites" else "Add to favorites") { actions.onToggleFavorite(track) }
        MenuItem(Icons.AutoMirrored.Filled.PlaylistAdd, "Add to playlist") { actions.onAddToPlaylist(track); controller.close() }
        controller.removeFromPlaylistId?.let { pid ->
            MenuItem(Icons.Default.RemoveCircleOutline, "Remove from this playlist") { actions.onRemoveFromPlaylist(pid, track); controller.close() }
        }
        MenuItem(Icons.Default.Album, "Go to album") { actions.onGoToAlbum(track); controller.close() }
        MenuItem(Icons.Default.Person, "Go to artist") { actions.onGoToArtist(track); controller.close() }
        MenuItem(Icons.Default.ContentCut, "Trim / cut") { actions.onTrim(track); controller.close() }
        MenuItem(Icons.Default.Edit, "Edit details (title, artist, album)") { actions.onEdit(track); controller.close() }
        MenuItem(Icons.Default.Image, "Set album art") { actions.onSetArt(track); controller.close() }
        if (actions.hasCustomArt(track)) MenuItem(Icons.Default.HideImage, "Remove custom art") { actions.onRemoveArt(track); controller.close() }
        MenuItem(Icons.Default.Info, "Details") { showDetails = true }
        MenuItem(Icons.Default.DeleteForever, "Delete file from device") { actions.onDelete(track); controller.close() }
        Spacer(Modifier.navigationBarsPadding().height(16.dp))
    }

    if (showDetails) {
        AlertDialog(
            onDismissRequest = { showDetails = false },
            confirmButton = { TextButton(onClick = { showDetails = false }) { Text("Close") } },
            title = { Text("Details") },
            text = {
                Column {
                    DetailLine("Title", track.title)
                    DetailLine("Artist", track.artist)
                    DetailLine("Album", track.album)
                    DetailLine("Format", track.format + if (track.mimeType.isNotBlank()) " (${track.mimeType})" else "")
                    DetailLine("Duration", formatDuration(track.durationMs))
                    DetailLine("Size", formatBytes(track.sizeBytes))
                    if (track.year > 0) DetailLine("Year", track.year.toString())
                    DetailLine("Folder", track.folderName)
                    DetailLine("File", track.displayName)
                    DetailLine("Source", track.source.lowercase().replaceFirstChar { it.uppercase() })
                }
            },
        )
    }
}

@Composable
private fun DetailLine(label: String, value: String) {
    Row(Modifier.padding(vertical = 3.dp)) {
        Text(label, Modifier.width(80.dp), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun MenuItem(icon: ImageVector, label: String, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(label) },
        leadingContent = { Icon(icon, null) },
        colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
        modifier = Modifier.clickable(onClick = onClick),
    )
}

/** Pick an existing playlist or create a new one. */
@Composable
fun AddToPlaylistDialog(
    playlists: List<PlaylistSummary>,
    onDismiss: () -> Unit,
    onPick: (Long) -> Unit,
    onCreate: (String) -> Unit,
) {
    var creating by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (creating) "New playlist" else "Add to playlist") },
        text = {
            if (creating) {
                OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            } else {
                LazyColumn {
                    item {
                        ListItem(
                            headlineContent = { Text("Create new playlist") },
                            leadingContent = { Icon(Icons.AutoMirrored.Filled.PlaylistAdd, null) },
                            modifier = Modifier.clickable { creating = true },
                            colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
                        )
                    }
                    items(playlists, key = { it.id }) { p ->
                        ListItem(
                            headlineContent = { Text(p.name) },
                            supportingContent = { Text("${p.trackCount} songs") },
                            leadingContent = { ArtworkImage(p.firstTrackUri, p.artworkUri, Modifier.size(40.dp), albumKey = playlistCoverKey(p.id)) },
                            modifier = Modifier.clickable { onPick(p.id) },
                            colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
                        )
                    }
                }
            }
        },
        confirmButton = {
            if (creating) TextButton(onClick = { if (name.isNotBlank()) onCreate(name) }, enabled = name.isNotBlank()) { Text("Create & add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
