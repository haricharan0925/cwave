package com.vibe.player.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RemoveCircleOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

enum class SelectionKind { TRACKS, PLAYLISTS, ALBUMS, DOWNLOADS }

/**
 * App-wide multi-select state. One selection at a time; a long-press on any row starts it, taps toggle,
 * the bar at the top acts on it. Keys are strings so tracks (id), playlists (id) and albums (albumKey) share it.
 */
class SelectionController {
    var active by mutableStateOf(false); private set
    var kind by mutableStateOf(SelectionKind.TRACKS); private set
    var selected by mutableStateOf(setOf<String>()); private set
    var playlistId by mutableStateOf<Long?>(null); private set
    var albumName by mutableStateOf<String?>(null); private set
    private var allIds: () -> List<String> = { emptyList() }

    fun start(id: String, kind: SelectionKind, all: () -> List<String>, playlistId: Long? = null, albumName: String? = null) {
        this.kind = kind; allIds = all; this.playlistId = playlistId; this.albumName = albumName; selected = setOf(id); active = true
    }
    fun toggle(id: String) { selected = if (id in selected) selected - id else selected + id }
    fun isSelected(id: String) = id in selected
    val allSelected: Boolean get() = allIds().let { it.isNotEmpty() && selected.containsAll(it) }
    fun selectAll() { selected = allIds().toSet() }
    fun deselectAll() { selected = emptySet() }
    fun clear() { selected = emptySet(); active = false; playlistId = null; albumName = null }
    fun inMode(k: SelectionKind) = active && kind == k
}

val LocalSelection = compositionLocalOf { SelectionController() }

/** What "Select all" means inside the current list, plus playlist context for "remove from playlist". */
data class SelectionScope(val ids: () -> List<String>, val playlistId: Long? = null, val albumName: String? = null)
val LocalSelectionScope = compositionLocalOf { SelectionScope({ emptyList() }) }

@Composable
fun SelectionBar(
    sel: SelectionController,
    onPlay: () -> Unit,
    onQueue: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onMoveToAlbum: () -> Unit,
    onRemoveFromPlaylist: () -> Unit,
    onDelete: () -> Unit,
) {
    BackHandler { sel.clear() }
    val n = sel.selected.size
    Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, tonalElevation = 3.dp, shadowElevation = 6.dp) {
        Column(Modifier.fillMaxWidth().statusBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { sel.clear() }) { Icon(Icons.Default.Close, "Exit selection") }
                Text(
                    when (sel.kind) { SelectionKind.TRACKS -> "$n selected"; SelectionKind.PLAYLISTS -> "$n playlist${if (n == 1) "" else "s"}"; SelectionKind.ALBUMS -> "$n album${if (n == 1) "" else "s"}"; SelectionKind.DOWNLOADS -> "$n download${if (n == 1) "" else "s"}" },
                    style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { if (sel.allSelected) sel.deselectAll() else sel.selectAll() }) { Text(if (sel.allSelected) "Deselect all" else "Select all") }
            }
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 4.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                val enabled = n > 0
                if (sel.kind == SelectionKind.TRACKS || sel.kind == SelectionKind.ALBUMS) {
                    Action(Icons.Default.PlayArrow, "Play", enabled, onPlay)
                    Action(Icons.AutoMirrored.Filled.QueueMusic, "Queue", enabled, onQueue)
                    Action(Icons.AutoMirrored.Filled.PlaylistAdd, "Playlist", enabled, onAddToPlaylist)
                    if (sel.kind == SelectionKind.TRACKS) Action(Icons.Default.Album, "Album", enabled, onMoveToAlbum)
                    if (sel.playlistId != null || sel.albumName != null) Action(Icons.Default.RemoveCircleOutline, "Remove", enabled, onRemoveFromPlaylist)
                }
                Spacer(Modifier.width(12.dp))
                Action(Icons.Default.DeleteForever, when (sel.kind) { SelectionKind.PLAYLISTS -> "Delete"; SelectionKind.DOWNLOADS -> "Remove"; else -> "Delete files" }, enabled, onDelete, danger = true)
            }
        }
    }
}

@Composable
private fun Action(icon: ImageVector, label: String, enabled: Boolean, onClick: () -> Unit, danger: Boolean = false) {
    TextButton(onClick = onClick, enabled = enabled) {
        Icon(icon, null, Modifier.size(20.dp), tint = if (!enabled) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f) else if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(6.dp))
        Text(label, color = if (!enabled) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f) else if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
    }
}

@Suppress("unused") private val keepHeight = 0.dp.also { Modifier.height(it) }
