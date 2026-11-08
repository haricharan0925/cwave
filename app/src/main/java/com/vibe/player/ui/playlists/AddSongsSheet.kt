package com.vibe.player.ui.playlists

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.animation.core.AnimationState
import androidx.compose.animation.core.animateDecay
import androidx.compose.animation.rememberSplineBasedDecay
import androidx.compose.foundation.gestures.FlingBehavior
import androidx.compose.foundation.gestures.ScrollScope
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.foundation.LocalOverscrollFactory
import com.vibe.player.ui.library.PAGE
import com.vibe.player.ui.library.ShowMoreFooter
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vibe.player.data.LibraryRepository
import com.vibe.player.data.db.Track
import com.vibe.player.ui.components.ArtworkImage
import com.vibe.player.ui.components.EmptyState
import com.vibe.player.ui.pluralize

/** Multi-select picker over the whole library, used from inside a playlist. Songs already in it are shown ticked and disabled. */
@Composable
fun AddSongsSheet(library: LibraryRepository, alreadyIn: Set<Long>, onDismiss: () -> Unit, onAdd: (List<Long>) -> Unit, title: String = "Add songs", inLabel: String = "Already in playlist") {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val all by library.allTracks().collectAsState(emptyList())
    var query by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf(setOf<Long>()) }
    val shown = remember(all, query) {
        val q = query.trim().lowercase()
        if (q.isBlank()) all else all.filter { it.title.lowercase().contains(q) || it.artist.lowercase().contains(q) || it.album.lowercase().contains(q) || it.displayName.lowercase().contains(q) }
    }

    // Overscroll is disabled for the entire sheet: with it on, a fast fling into the end of the list
    // bounced between the LazyColumn's edge effect and the sheet's drag handler indefinitely.
    CompositionLocalProvider(LocalOverscrollFactory provides null) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(Modifier.fillMaxHeight(0.92f)) {
            Row(Modifier.padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                TextButton(onClick = { selected = if (selected.size == shown.count { it.id !in alreadyIn }) emptySet() else shown.map { it.id }.filter { it !in alreadyIn }.toSet() }) {
                    Text(if (shown.isNotEmpty() && selected.size == shown.count { it.id !in alreadyIn }) "Clear" else "Select all")
                }
            }
            OutlinedTextField(
                query, { query = it }, Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), singleLine = true,
                placeholder = { Text("Search songs, artists, albums") }, leadingIcon = { Icon(Icons.Default.Search, null) },
                trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Default.Close, "Clear") } },
                shape = MaterialTheme.shapes.extraLarge,
            )
            if (shown.isEmpty()) EmptyState(Icons.Default.Search, "No matches", "Nothing in your library matches \"$query\".")
            var limit by remember(query) { mutableStateOf(PAGE) }
            val swallowLeftovers = remember {
                object : androidx.compose.ui.input.nestedscroll.NestedScrollConnection {
                    override fun onPostScroll(consumed: androidx.compose.ui.geometry.Offset, available: androidx.compose.ui.geometry.Offset, source: androidx.compose.ui.input.nestedscroll.NestedScrollSource) = available
                    override suspend fun onPostFling(consumed: androidx.compose.ui.unit.Velocity, available: androidx.compose.ui.unit.Velocity) = available
                }
            }
            val page = shown.take(limit)
            LazyColumn(Modifier.weight(1f).nestedScroll(swallowLeftovers), flingBehavior = noBounceFling()) {
                items(page, key = { it.id }) { t ->
                    val inList = t.id in alreadyIn
                    val checked = inList || t.id in selected
                    Row(
                        Modifier.fillMaxWidth().clickable(enabled = !inList) { selected = if (t.id in selected) selected - t.id else selected + t.id }.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked, onCheckedChange = null, enabled = !inList)
                        Spacer(Modifier.width(8.dp))
                        ArtworkImage(t.uri, t.artworkUri, Modifier.size(44.dp), albumKey = t.albumKey)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(t.title, maxLines = 1, overflow = TextOverflow.Ellipsis, color = if (inList) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface)
                            Text(if (inList) inLabel else "${t.artist} · ${t.album}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
                if (shown.size > limit) item { ShowMoreFooter(remaining = shown.size - limit, onMore = { limit += PAGE }, onAll = { limit = shown.size }) }
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp).navigationBarsPadding(), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onDismiss) { Text("Cancel") }
                Spacer(Modifier.width(8.dp))
                Button(onClick = { onAdd(selected.toList()) }, enabled = selected.isNotEmpty()) { Text(if (selected.isEmpty()) "Add" else "Add ${pluralize(selected.size, "song")}") }
            }
            Spacer(Modifier.height(4.dp))
        }
    }
    }
}

/** Standard fling decay but with the edge bounce removed: stops dead at the list boundary. */
@Composable
private fun noBounceFling(): FlingBehavior {
    val decay = rememberSplineBasedDecay<Float>()
    return remember(decay) {
        object : FlingBehavior {
            override suspend fun ScrollScope.performFling(initialVelocity: Float): Float {
                var velocityLeft = initialVelocity
                var lastValue = 0f
                AnimationState(initialValue = 0f, initialVelocity = initialVelocity).animateDecay(decay) {
                    val delta = value - lastValue
                    val consumed = scrollBy(delta)
                    lastValue = value
                    velocityLeft = this.velocity
                    if (kotlin.math.abs(delta - consumed) > 0.5f) cancelAnimation() // hit the edge: stop, don't bounce
                }
                return 0f // report nothing left over: any remainder would be handed to the sheet and bounce it
            }
        }
    }
}

@Suppress("unused") private fun keepTrackImport(t: Track) = t
