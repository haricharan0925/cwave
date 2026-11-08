package com.vibe.player.ui.player

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.size.Size
import com.vibe.player.container
import com.vibe.player.metadata.Artwork
import com.vibe.player.player.NowPlayingItem
import com.vibe.player.ui.components.ArtworkImage
import kotlinx.coroutines.withTimeoutOrNull

private val slideEasing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)

/**
 * Now Playing artwork with a track-change slide.
 *
 * Rules that keep it smooth and honest:
 *  - The transition is keyed on [NowPlayingItem.trackId] only. Seeking, buffering, position ticks, metadata
 *    refreshes and artwork edits never restart it.
 *  - The pause "breath" follows [playWhenReady] (user intent), not `isPlaying`, which drops to false for a few
 *    frames after every seek and used to make the art pop.
 *  - The old artwork stays on screen until the new one is actually decoded (bounded wait), so the slide never
 *    starts with the coloured placeholder — that placeholder was the "green flash" users saw between songs.
 *    [ArtworkPrefetch] warms the neighbours' art so this wait is normally zero.
 *  - The swap of layers and the reset of progress happen in one snapshot inside the effect, so there is no
 *    first frame where the new item is drawn un-animated.
 *  - All per-frame motion is read inside `graphicsLayer` blocks: a running slide only re-draws the layer.
 *
 * Direction follows the skip: next -> new art slides in from the right, previous -> from the left.
 */
@Composable
fun ArtworkStage(item: NowPlayingItem, queueIndex: Int, playWhenReady: Boolean, modifier: Modifier = Modifier) {
    val shape = MaterialTheme.shapes.extraLarge
    val context = LocalContext.current
    val artVersion by context.container.artworkStore.version.collectAsState()
    var shown by remember { mutableStateOf(item) }                 // the layer on top
    var outgoing by remember { mutableStateOf<NowPlayingItem?>(null) }
    var progress by remember { mutableFloatStateOf(1f) }           // 0 = incoming just started, 1 = settled
    var direction by remember { mutableIntStateOf(1) }
    val lastIndex = remember { intArrayOf(queueIndex) }

    LaunchedEffect(item.trackId, item.artworkUri, artVersion) {
        if (item.trackId == shown.trackId) { shown = item; return@LaunchedEffect }
        // Decode (or hit the memory cache for) the incoming art first; give up after 400 ms and slide anyway.
        withTimeoutOrNull(400) {
            context.imageLoader.execute(
                ImageRequest.Builder(context).data(Artwork(item.uri, item.artworkUri, item.albumKey, artVersion)).size(Size.ORIGINAL).build(),
            )
        }
        direction = if (queueIndex >= lastIndex[0]) 1 else -1
        lastIndex[0] = queueIndex
        progress = 0f
        outgoing = shown
        shown = item
        animate(0f, 1f, animationSpec = tween(durationMillis = 420, easing = slideEasing)) { v, _ -> progress = v }
        outgoing = null
    }

    // Gentle breath: the whole card eases down a touch while paused (spring, so play/pause feels soft).
    val rest by animateFloatAsState(if (playWhenReady) 1f else 0.94f, spring(dampingRatio = 0.75f, stiffness = 260f), label = "rest")

    Box(
        modifier
            .graphicsLayer { scaleX = rest; scaleY = rest }
            // One static shadow on the frame; the frame also clips, so the slide happens inside a rounded window.
            .shadow(18.dp, shape, clip = true, spotColor = Color.Black.copy(alpha = 0.45f)),
    ) {
        outgoing?.let { old ->
            ArtworkImage(
                old.uri, old.artworkUri, albumKey = old.albumKey, shape = shape, iconSize = 96.dp,
                modifier = Modifier.fillMaxSize().graphicsLayer {
                    // Parallax: the old art drifts away at a third of the speed and stays opaque until it's covered.
                    translationX = -direction * size.width * 0.33f * progress
                },
            )
        }
        ArtworkImage(
            shown.uri, shown.artworkUri, albumKey = shown.albumKey, shape = shape, iconSize = 96.dp,
            modifier = Modifier.fillMaxSize().graphicsLayer {
                val p = if (outgoing == null) 1f else progress
                // New art slides in over the old one and settles from a slight zoom.
                translationX = direction * size.width * (1f - p)
                val s = 1f + 0.05f * (1f - p)
                scaleX = s; scaleY = s
            },
        )
    }
}

/** Warms Coil's memory cache with the artwork of the tracks around [index], so skipping never waits on a decode. */
@Composable
fun ArtworkPrefetch(queue: List<NowPlayingItem>, index: Int) {
    val context = LocalContext.current
    val artVersion by context.container.artworkStore.version.collectAsState()
    LaunchedEffect(index, queue.size, artVersion) {
        val loader = context.imageLoader
        for (i in listOf(index + 1, index - 1, index + 2)) {
            val t = queue.getOrNull(i) ?: continue
            loader.enqueue(ImageRequest.Builder(context).data(Artwork(t.uri, t.artworkUri, t.albumKey, artVersion)).size(Size.ORIGINAL).build())
        }
    }
}
