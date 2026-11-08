package com.vibe.player.ui.player

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.vibe.player.container
import androidx.compose.runtime.collectAsState
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.displayCutoutPadding
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import coil3.compose.AsyncImage
import com.vibe.player.metadata.Artwork
import com.vibe.player.player.NowPlayingItem
import com.vibe.player.player.PlayerState
import com.vibe.player.ui.components.ArtworkImage
import com.vibe.player.ui.components.FormatChip
import com.vibe.player.ui.formatDuration
import kotlinx.coroutines.launch
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

@Composable
fun NowPlayingScreen(
    state: PlayerState,
    isFavorite: Boolean,
    onClose: () -> Unit,
    onTogglePlay: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSeek: (Long) -> Unit,
    onShuffle: () -> Unit,
    onRepeat: () -> Unit,
    onToggleFavorite: () -> Unit,
    onMore: () -> Unit,
    onSkipTo: (Int) -> Unit,
    onRemoveFromQueue: (Int) -> Unit,
    onMoveInQueue: (Int, Int) -> Unit,
    onClearQueue: () -> Unit,
) {
    val item = state.current ?: return
    var showQueue by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    // ---- swipe-down-to-dismiss ------------------------------------------------------------------
    // While the finger is down we follow it synchronously (plain state, no coroutine per frame).
    // On release we hand the current offset to an Animatable that either springs back or slides off
    // screen; onClose() is only called once the sheet is fully off screen, so the parent's exit
    // transition has nothing left to animate and there is no visible jump.
    val density = LocalDensity.current
    val screenHeightPx = with(density) { LocalConfiguration.current.screenHeightDp.dp.toPx() }
    val dismissPx = with(density) { 140.dp.toPx() }
    var dragging by remember { mutableStateOf(false) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    val settle = remember { Animatable(0f) }
    val shownOffset = if (dragging) dragOffset else settle.value
    val dragState = rememberDraggableState { delta -> dragOffset = (dragOffset + delta).coerceAtLeast(0f) }

    var closing by remember { mutableStateOf(false) }
    fun slideOutAndClose(from: Float, velocity: Float = 0f) {
        if (closing) return
        closing = true
        scope.launch {
            settle.snapTo(from)
            dragging = false
            // Spring with the release velocity: continues the finger's motion, frame-rate independent (60/90/120 Hz).
            settle.animateTo(screenHeightPx, spring(dampingRatio = 1f, stiffness = 300f, visibilityThreshold = 0.5f), initialVelocity = velocity.coerceAtLeast(1500f))
            onClose() // the sheet is removed from composition here; no reset so nothing can flash back
        }
    }
    fun springBack(from: Float, velocity: Float) = scope.launch {
        settle.snapTo(from)
        dragging = false
        settle.animateTo(0f, spring(dampingRatio = 0.85f, stiffness = 450f), initialVelocity = velocity)
        dragOffset = 0f
    }
    BackHandler { slideOutAndClose(shownOffset) }

    Surface(
        Modifier.fillMaxSize()
            .graphicsLayer { translationY = shownOffset }
            .draggable(
                dragState, Orientation.Vertical,
                enabled = !closing,
                onDragStarted = { dragging = true; dragOffset = settle.value },
                onDragStopped = { velocity -> if (dragOffset > dismissPx || velocity > 2200f) slideOutAndClose(dragOffset, velocity) else springBack(dragOffset, velocity) },
            ),
        color = MaterialTheme.colorScheme.background,
    ) {
        Box(Modifier.fillMaxSize()) {
            ArtworkPrefetch(state.queue, state.currentIndex)
            val artVersion by LocalContext.current.container.artworkStore.version.collectAsState()
            // Keyed on the track id: seeking must not re-run the backdrop fade either. Same cache key as the card/prefetch.
            androidx.compose.animation.Crossfade(item.trackId, animationSpec = tween(600), label = "backdrop") { _ ->
                AsyncImage(
                    model = Artwork(item.uri, item.artworkUri, item.albumKey, artVersion), contentDescription = null, contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().blur(48.dp).scale(1.2f).alpha(0.55f),
                )
            }
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background.copy(alpha = 0.70f)))
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, MaterialTheme.colorScheme.background.copy(alpha = 0.97f)))))

            val cfg = LocalConfiguration.current
            val landscape = cfg.screenWidthDp > cfg.screenHeightDp

            val header: @Composable () -> Unit = {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { slideOutAndClose(shownOffset) }) { Icon(Icons.Default.KeyboardArrowDown, "Close", Modifier.size(30.dp)) }
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("NOW PLAYING", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, letterSpacing = androidx.compose.ui.unit.TextUnit(2f, androidx.compose.ui.unit.TextUnitType.Sp))
                        if (item.album.isNotBlank()) Text(item.album, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    IconButton(onClick = onMore) { Icon(Icons.Default.MoreVert, "More") }
                }
            }
            val art: @Composable (Modifier) -> Unit = { m -> ArtworkStage(item, state.currentIndex, state.playWhenReady, m) }
            val info: @Composable () -> Unit = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        // Long titles scroll instead of being cut off; marquee only animates when the text overflows.
                        Text(item.title, style = MaterialTheme.typography.headlineSmall, maxLines = 1, softWrap = false, modifier = Modifier.basicMarquee(iterations = Int.MAX_VALUE))
                        Spacer(Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (item.format.isNotBlank()) { FormatChip(item.format); Spacer(Modifier.width(8.dp)) }
                            Text(item.artist, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, softWrap = false, modifier = Modifier.weight(1f, fill = false).basicMarquee(iterations = Int.MAX_VALUE))
                        }
                    }
                    IconButton(onClick = onToggleFavorite) {
                        AnimatedContent(isFavorite, transitionSpec = { scaleIn(initialScale = 0.5f) togetherWith scaleOut() }, label = "fav") { fav ->
                            Icon(if (fav) Icons.Default.Favorite else Icons.Default.FavoriteBorder, "Favorite", Modifier.size(28.dp), tint = if (fav) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }
            }
            val controls: @Composable () -> Unit = {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onShuffle) {
                        Icon(Icons.Default.Shuffle, "Shuffle", tint = if (state.shuffle) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = onPrevious, Modifier.size(56.dp)) { Icon(Icons.Default.SkipPrevious, "Previous", Modifier.size(40.dp)) }
                    FilledIconButton(
                        onClick = onTogglePlay, modifier = Modifier.size(if (landscape) 64.dp else 76.dp),
                        colors = IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.primary),
                    ) {
                        AnimatedContent(state.isPlaying, transitionSpec = { scaleIn() togetherWith scaleOut() }, label = "pp") { playing ->
                            Icon(if (playing) Icons.Default.Pause else Icons.Default.PlayArrow, null, Modifier.size(40.dp))
                        }
                    }
                    IconButton(onClick = onNext, Modifier.size(56.dp), enabled = state.hasNext || state.repeatMode != Player.REPEAT_MODE_OFF) { Icon(Icons.Default.SkipNext, "Next", Modifier.size(40.dp)) }
                    IconButton(onClick = onRepeat) {
                        val on = state.repeatMode != Player.REPEAT_MODE_OFF
                        Icon(
                            if (state.repeatMode == Player.REPEAT_MODE_ONE) Icons.Default.RepeatOne else Icons.Default.Repeat, "Repeat",
                            tint = if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            val queueButton: @Composable ColumnScope.() -> Unit = {
                TextButton(onClick = { showQueue = true }, Modifier.align(Alignment.CenterHorizontally)) {
                    Icon(Icons.AutoMirrored.Filled.QueueMusic, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Queue · ${state.queue.size}")
                }
            }

            if (landscape) {
                // Two panes: artwork fills the height on the left, everything else stacks on the right.
                Row(
                    Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().displayCutoutPadding().padding(start = 24.dp, end = 16.dp, top = 4.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.fillMaxHeight().padding(vertical = 14.dp).aspectRatio(1f, matchHeightConstraintsFirst = true)) { art(Modifier.fillMaxSize()) }
                    Spacer(Modifier.width(28.dp))
                    Column(Modifier.weight(1f).fillMaxHeight()) {
                        header()
                        Spacer(Modifier.weight(1f))
                        info()
                        Spacer(Modifier.height(6.dp))
                        SeekBar(state.positionMs, state.durationMs, onSeek)
                        controls()
                        queueButton()
                    }
                }
            } else {
                Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 24.dp)) {
                    // drag handle hint
                    Box(Modifier.align(Alignment.CenterHorizontally).padding(top = 8.dp).width(40.dp).height(4.dp).background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f), CircleShape))
                    header()
                    Spacer(Modifier.weight(1f))
                    art(Modifier.fillMaxWidth().aspectRatio(1f))
                    Spacer(Modifier.weight(1f))
                    info()
                    Spacer(Modifier.height(20.dp))
                    SeekBar(state.positionMs, state.durationMs, onSeek)
                    Spacer(Modifier.height(12.dp))
                    controls()
                    Spacer(Modifier.height(12.dp))
                    queueButton()
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
    }

    if (showQueue) {
        QueueSheet(state, onDismiss = { showQueue = false }, onSkipTo = onSkipTo, onRemove = onRemoveFromQueue, onMove = onMoveInQueue, onClear = { onClearQueue(); showQueue = false; onClose() })
    }
}

/** Material 3 slider with explicit colours so the inactive track is visible in both themes. */
@Composable
private fun SeekBar(positionMs: Long, durationMs: Long, onSeek: (Long) -> Unit) {
    var dragging by remember { mutableStateOf(false) }
    var dragValue by remember { mutableFloatStateOf(0f) }
    val shown = (if (dragging) dragValue else if (durationMs > 0) positionMs.toFloat() / durationMs else 0f).coerceIn(0f, 1f)

    Column {
        Slider(
            value = shown,
            onValueChange = { dragging = true; dragValue = it },
            onValueChangeFinished = { onSeek((dragValue * durationMs).toLong()); dragging = false },
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary,
                inactiveTrackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.22f),
                activeTickColor = Color.Transparent,
                inactiveTickColor = Color.Transparent,
            ),
            modifier = Modifier.fillMaxWidth(),
        )
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(formatDuration((shown * durationMs).toLong()), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(formatDuration(durationMs), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun QueueSheet(state: PlayerState, onDismiss: () -> Unit, onSkipTo: (Int) -> Unit, onRemove: (Int) -> Unit, onMove: (Int, Int) -> Unit, onClear: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var items by remember(state.queue) { mutableStateOf(state.queue) }
    val listState = rememberLazyListState()
    val reorderState = rememberReorderableLazyListState(listState) { from, to ->
        items = items.toMutableList().apply { add(to.index, removeAt(from.index)) }
        onMove(from.index, to.index)
    }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Row(Modifier.padding(horizontal = 20.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Up next", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            TextButton(onClick = onClear) { Text("Clear") }
        }
        androidx.compose.runtime.CompositionLocalProvider(androidx.compose.foundation.LocalOverscrollFactory provides null) {
        LazyColumn(state = listState, modifier = Modifier.fillMaxWidth().fillMaxHeight(0.75f)) {
            itemsIndexed(items, key = { i, it -> "${it.uri}#$i" }) { index, q ->
                ReorderableItem(reorderState, key = "${q.uri}#$index") { isDragging ->
                    QueueRow(q, isCurrent = index == state.currentIndex, isDragging = isDragging,
                        dragHandle = { Icon(Icons.Default.DragHandle, "Reorder", Modifier.draggableHandle(), tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                        onClick = { onSkipTo(index) }, onRemove = { onRemove(index) })
                }
            }
        }
        }
    }
}

@Composable
private fun QueueRow(q: NowPlayingItem, isCurrent: Boolean, isDragging: Boolean, dragHandle: @Composable () -> Unit, onClick: () -> Unit, onRemove: () -> Unit) {
    Surface(color = if (isDragging) MaterialTheme.colorScheme.surfaceContainerHighest else Color.Transparent, tonalElevation = if (isDragging) 4.dp else 0.dp) {
        Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            dragHandle()
            Spacer(Modifier.width(8.dp))
            ArtworkImage(q.uri, q.artworkUri, Modifier.size(44.dp), albumKey = q.albumKey)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(q.title, maxLines = 1, overflow = TextOverflow.Ellipsis, color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface, fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Normal)
                Text(q.artist, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text(formatDuration(q.durationMs), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            IconButton(onClick = onRemove) { Icon(Icons.Default.Close, "Remove", Modifier.size(18.dp)) }
        }
    }
}
