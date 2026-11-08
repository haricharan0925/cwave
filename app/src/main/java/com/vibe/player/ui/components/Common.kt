package com.vibe.player.ui.components

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.foundation.combinedClickable
import androidx.compose.material3.Checkbox
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.vibe.player.container
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.vibe.player.data.db.Track
import com.vibe.player.metadata.Artwork
import com.vibe.player.ui.formatDuration

/** Artwork with a deterministic gradient placeholder per track so lists never look empty. */
@Composable
fun ArtworkImage(
    trackUri: String?,
    artworkUri: String?,
    modifier: Modifier = Modifier,
    shape: Shape = MaterialTheme.shapes.small,
    iconSize: Dp = 24.dp,
    seed: String = trackUri ?: artworkUri ?: "",
    albumKey: String? = null,
) {
    val hue = (seed.hashCode().toUInt() % 360u).toFloat()
    val c1 = Color.hsl(hue, 0.45f, 0.32f)
    val c2 = Color.hsl((hue + 40f) % 360f, 0.55f, 0.18f)
    val artVersion by androidx.compose.ui.platform.LocalContext.current.container.artworkStore.version.collectAsState()
    Box(modifier.clip(shape).background(Brush.linearGradient(listOf(c1, c2))), contentAlignment = Alignment.Center) {
        Icon(Icons.Rounded.MusicNote, null, Modifier.size(iconSize), tint = Color.White.copy(alpha = 0.55f))
        if (trackUri != null || artworkUri != null) {
            AsyncImage(
                model = Artwork(trackUri ?: "", artworkUri, albumKey, artVersion),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
fun TrackRow(
    track: Track,
    modifier: Modifier = Modifier,
    isPlaying: Boolean = false,
    isCurrent: Boolean = false,
    trailing: @Composable (() -> Unit)? = null,
    subtitle: String = "${track.artist} · ${track.album}",
    showFormat: Boolean = true,
    onClick: () -> Unit,
    onMore: (() -> Unit)? = null,
) {
    val bg by animateFloatAsState(if (isCurrent) 1f else 0f, tween(300), label = "rowBg")
    val sel = LocalSelection.current
    val selScope = LocalSelectionScope.current
    val key = track.id.toString()
    val selecting = sel.inMode(SelectionKind.TRACKS)
    val picked = selecting && sel.isSelected(key)
    Row(
        modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(if (picked) MaterialTheme.colorScheme.primary.copy(alpha = 0.20f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.10f * bg))
            .combinedClickable(
                onClick = { if (selecting) sel.toggle(key) else onClick() },
                onLongClick = { if (!sel.active) sel.start(key, SelectionKind.TRACKS, selScope.ids, selScope.playlistId, selScope.albumName) else if (selecting) sel.toggle(key) },
            )
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(52.dp)) {
            ArtworkImage(track.uri, track.artworkUri, Modifier.fillMaxSize(), albumKey = track.albumKey)
            if (isPlaying) {
                Box(Modifier.fillMaxSize().clip(MaterialTheme.shapes.small).background(Color.Black.copy(alpha = 0.45f)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Equalizer, null, tint = MaterialTheme.colorScheme.primary)
                }
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                track.title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis,
                color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Normal,
            )
            Spacer(Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (showFormat) { FormatChip(track.format); Spacer(Modifier.width(6.dp)) }
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Spacer(Modifier.width(8.dp))
        Text(formatDuration(track.durationMs), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (selecting) Checkbox(picked, onCheckedChange = { sel.toggle(key) })
        else if (trailing != null) trailing()
        else if (onMore != null) IconButton(onClick = onMore) { Icon(Icons.Default.MoreVert, "More", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

@Composable
fun FormatChip(format: String, modifier: Modifier = Modifier) {
    Surface(modifier, shape = RoundedCornerShape(4.dp), color = MaterialTheme.colorScheme.surfaceContainerHighest) {
        Text(format, Modifier.padding(horizontal = 5.dp, vertical = 1.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier, action: String? = null, onAction: (() -> Unit)? = null) {
    Row(modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
        if (action != null && onAction != null) TextButton(onClick = onAction) { Text(action) }
    }
}

@Composable
fun EmptyState(icon: ImageVector, title: String, message: String, modifier: Modifier = Modifier, action: String? = null, onAction: (() -> Unit)? = null) {
    Column(modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Box(Modifier.size(88.dp).clip(RoundedCornerShape(28.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh), contentAlignment = Alignment.Center) {
            Icon(icon, null, Modifier.size(40.dp), tint = MaterialTheme.colorScheme.primary)
        }
        Spacer(Modifier.height(20.dp))
        Text(title, style = MaterialTheme.typography.titleLarge, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        if (action != null && onAction != null) {
            Spacer(Modifier.height(20.dp))
            androidx.compose.material3.FilledTonalButton(onClick = onAction) { Text(action) }
        }
    }
}

@Composable
fun LoadingBar(visible: Boolean, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(3.dp).animateContentSize()) {
        if (visible) LinearProgressIndicator(Modifier.fillMaxWidth().height(3.dp))
    }
}

/** Soft translucent card used for "glass" surfaces over blurred backgrounds. */
@Composable
fun GlassSurface(modifier: Modifier = Modifier, shape: Shape = MaterialTheme.shapes.large, alpha: Float = 0.55f, content: @Composable () -> Unit) {
    Box(
        modifier
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = alpha))
            .background(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.06f), Color.Transparent))),
    ) { content() }
}

val listBottomPadding = PaddingValues(bottom = 160.dp)
