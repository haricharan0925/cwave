package com.vibe.player.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import com.vibe.player.ui.components.LocalSelectionScope
import com.vibe.player.ui.components.SelectionScope
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
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
import com.vibe.player.ui.components.LoadingBar
import com.vibe.player.ui.components.LocalTrackMenu
import com.vibe.player.ui.components.SectionHeader
import com.vibe.player.ui.components.TrackRow
import com.vibe.player.ui.navigation.Routes
import java.util.Calendar

@Composable
fun HomeScreen(nav: NavController) {
    val container = LocalContext.current.container
    val library = container.library
    val player = container.player
    val menu = LocalTrackMenu.current
    val switchTab = com.vibe.player.ui.navigation.LocalSwitchTab.current

    val recentlyPlayed by library.recentlyPlayed(12).collectAsState(emptyList())
    val recentlyAdded by library.recentlyAdded(15).collectAsState(emptyList())
    val mostPlayed by library.mostPlayed(10).collectAsState(emptyList())
    val all by library.allTracks().collectAsState(emptyList())
    val count by library.trackCount().collectAsState(0)
    val scanning by library.scanning.collectAsStateWithLifecycle()
    val indicator by player.indicator.collectAsStateWithLifecycle()
    val currentId = indicator.trackId

    val greeting = when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
        in 5..11 -> "Good morning"; in 12..16 -> "Good afternoon"; in 17..21 -> "Good evening"; else -> "Late night vibes"
    }

    CompositionLocalProvider(LocalSelectionScope provides SelectionScope({ recentlyAdded.map { it.id.toString() } })) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 150.dp)) {
        item {
            Box(
                Modifier.fillMaxWidth().background(
                    Brush.verticalGradient(listOf(MaterialTheme.colorScheme.primary.copy(alpha = 0.22f), Color.Transparent)),
                ),
            ) {
                Row(Modifier.statusBarsPadding().padding(start = 20.dp, end = 8.dp, top = 16.dp, bottom = 8.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(greeting, style = MaterialTheme.typography.headlineMedium)
                        Text(if (count > 0) "$count songs in your library" else "Your library is empty", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = { nav.navigate(Routes.SEARCH) }) { Icon(Icons.Default.Search, "Search") }
                }
            }
            LoadingBar(scanning)
        }

        item {
            LazyRow(contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item { QuickChip("Shuffle all", Icons.Default.Shuffle, enabled = all.isNotEmpty()) { player.playShuffled(all) } }
                item { QuickChip("Favorites", Icons.Default.Favorite) { nav.navigate(Routes.playlist(-1L)) } }
                item { QuickChip("Library", Icons.Default.LibraryMusic) { switchTab(Routes.LIBRARY) } }
                item { QuickChip("Download", Icons.Default.Download) { switchTab(Routes.DOWNLOADS) } }
            }
        }

        if (count == 0 && !scanning) {
            item {
                EmptyState(
                    Icons.Default.LibraryMusic, "Nothing here yet",
                    "Music on this device shows up automatically. You can also import files or download from YouTube.",
                    action = "Import files", onAction = { switchTab(Routes.LIBRARY) },
                )
            }
        }

        if (recentlyPlayed.isNotEmpty()) {
            item { SectionHeader("Continue listening") }
            item {
                LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(recentlyPlayed, key = { it.id }) { t -> BigCard(t, isCurrent = t.id == currentId) { player.playTracks(recentlyPlayed, recentlyPlayed.indexOf(t)) } }
                }
            }
            item { Spacer(Modifier.height(12.dp)) }
        }

        if (recentlyAdded.isNotEmpty()) {
            item { SectionHeader("Recently added", action = "See all", onAction = { switchTab(Routes.LIBRARY) }) }
            items(recentlyAdded, key = { "ra${it.id}" }) { t ->
                TrackRow(
                    t, Modifier.padding(horizontal = 8.dp), isCurrent = indicator.isCurrent(t.id), isPlaying = indicator.isPlaying(t.id),
                    onClick = { player.playTracks(recentlyAdded, recentlyAdded.indexOf(t)) }, onMore = { menu.open(t) },
                )
            }
        }

        if (mostPlayed.size >= 3) {
            item { Spacer(Modifier.height(8.dp)); SectionHeader("On repeat") }
            item {
                LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(mostPlayed, key = { "mp${it.id}" }) { t -> SmallCard(t) { player.playTracks(mostPlayed, mostPlayed.indexOf(t)) } }
                }
            }
        }
    }
    }
}

@Composable
private fun QuickChip(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, enabled: Boolean = true, onClick: () -> Unit) {
    AssistChip(
        onClick = onClick, enabled = enabled,
        label = { Text(label) }, leadingIcon = { Icon(icon, null, Modifier.size(AssistChipDefaults.IconSize)) },
        colors = AssistChipDefaults.assistChipColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        border = null,
    )
}

@Composable
private fun BigCard(t: Track, isCurrent: Boolean, onClick: () -> Unit) {
    Column(Modifier.width(150.dp).clip(MaterialTheme.shapes.medium).clickable(onClick = onClick)) {
        ArtworkImage(t.uri, t.artworkUri, Modifier.size(150.dp), shape = MaterialTheme.shapes.medium, iconSize = 44.dp)
        Spacer(Modifier.height(8.dp))
        Text(t.title, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
        Text(t.artist, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(4.dp))
    }
}

@Composable
private fun SmallCard(t: Track, onClick: () -> Unit) {
    Row(
        Modifier.width(220.dp).clip(MaterialTheme.shapes.medium).background(MaterialTheme.colorScheme.surfaceContainer).clickable(onClick = onClick).padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ArtworkImage(t.uri, t.artworkUri, Modifier.size(48.dp))
        Spacer(Modifier.width(10.dp))
        Column {
            Text(t.title, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.History, null, Modifier.size(12.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(4.dp))
                Text(t.artist, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}
