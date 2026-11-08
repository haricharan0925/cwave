package com.vibe.player.ui.player

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.foundation.basicMarquee
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.vibe.player.metadata.Artwork
import com.vibe.player.player.PlayerState
import com.vibe.player.ui.components.ArtworkImage

@Composable
fun MiniPlayer(state: PlayerState, onOpen: () -> Unit, onTogglePlay: () -> Unit, onNext: () -> Unit, modifier: Modifier = Modifier) {
    val item = state.current ?: return
    val progress = if (state.durationMs > 0) (state.positionMs.toFloat() / state.durationMs).coerceIn(0f, 1f) else 0f

    Box(
        modifier
            .padding(horizontal = 12.dp)
            .fillMaxWidth()
            .height(if (androidx.compose.ui.platform.LocalConfiguration.current.let { it.screenWidthDp > it.screenHeightDp }) 56.dp else 66.dp)
            .clip(MaterialTheme.shapes.large)
            .clickable(onClick = onOpen),
    ) {
        // Blurred artwork backdrop gives the "glass" look; scrim keeps text legible.
        AsyncImage(
            model = Artwork(item.uri, item.artworkUri, item.albumKey), contentDescription = null, contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize().blur(24.dp),
        )
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.90f)))
        Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(listOf(Color.White.copy(alpha = 0.05f), Color.Transparent))))

        Row(Modifier.fillMaxSize().padding(start = 8.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            ArtworkImage(item.uri, item.artworkUri, Modifier.size(48.dp), albumKey = item.albumKey)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(item.title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false, modifier = Modifier.basicMarquee(iterations = Int.MAX_VALUE))
                Text(item.artist, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, softWrap = false, modifier = Modifier.basicMarquee(iterations = Int.MAX_VALUE))
            }
            IconButton(onClick = onTogglePlay) {
                AnimatedContent(state.isPlaying, transitionSpec = { scaleIn() togetherWith scaleOut() }, label = "playpause") { playing ->
                    Icon(if (playing) Icons.Default.Pause else Icons.Default.PlayArrow, if (playing) "Pause" else "Play", Modifier.size(30.dp))
                }
            }
            IconButton(onClick = onNext, enabled = state.hasNext) { Icon(Icons.Default.SkipNext, "Next") }
        }
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(3.dp),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
            drawStopIndicator = {},
        )
    }
}
