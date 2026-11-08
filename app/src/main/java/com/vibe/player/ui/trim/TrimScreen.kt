package com.vibe.player.ui.trim

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.navigation.NavController
import com.vibe.player.container
import com.vibe.player.convert.TrimMode
import com.vibe.player.convert.TrimOutcome
import com.vibe.player.data.db.Track
import com.vibe.player.ui.components.ArtworkImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs

private fun fmt(ms: Long): String {
    val t = ms.coerceAtLeast(0); val m = t / 60000; val s = (t % 60000) / 1000; val d = (t % 1000) / 100
    return "%d:%02d.%d".format(m, s, d)
}

@Composable
fun TrimScreen(nav: NavController, trackId: Long, snackbar: SnackbarHostState) {
    val context = LocalContext.current
    val container = context.container
    val scope = rememberCoroutineScope()
    var track by remember { mutableStateOf<Track?>(null) }
    LaunchedEffect(trackId) { track = container.library.track(trackId) }
    val t = track ?: return

    val durationMs = t.durationMs.coerceAtLeast(1000)
    var startMs by remember { mutableLongStateOf(0L) }
    var endMs by remember { mutableLongStateOf(durationMs) }
    var mode by remember { mutableStateOf(TrimMode.NEW) }
    var saving by remember { mutableStateOf(false) }
    var confirmReplace by remember { mutableStateOf(false) }

    // ---- preview: a private ExoPlayer clipped to the selection ----
    val preview = remember { ExoPlayer.Builder(context).build() }
    var previewing by remember { mutableStateOf(false) }
    var previewPos by remember { mutableLongStateOf(0L) }
    DisposableEffect(Unit) {
        val l = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) { previewing = isPlaying }
            override fun onPlaybackStateChanged(state: Int) { if (state == Player.STATE_ENDED) { previewing = false; preview.pause(); preview.seekTo(0) } }
        }
        preview.addListener(l)
        onDispose { preview.removeListener(l); preview.release() }
    }
    fun loadClip() {
        preview.setMediaItem(
            MediaItem.Builder().setUri(t.uri.toUri())
                .setClippingConfiguration(MediaItem.ClippingConfiguration.Builder().setStartPositionMs(startMs).setEndPositionMs(endMs).build()).build(),
        )
        preview.prepare()
    }
    LaunchedEffect(Unit) { loadClip() }
    LaunchedEffect(previewing) { while (isActive && previewing) { previewPos = startMs + preview.currentPosition; delay(100) } }
    fun togglePreview() { if (preview.isPlaying) preview.pause() else { if (preview.playbackState == Player.STATE_ENDED || preview.playbackState == Player.STATE_IDLE) loadClip(); preview.play() } }
    fun selectionChanged() { val was = preview.isPlaying; preview.pause(); loadClip(); if (was) preview.play() }

    val consentLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { res ->
        scope.launch {
            if (res.resultCode == android.app.Activity.RESULT_OK) handle(container.trimmer.completeReplace(), snackbar, nav) { saving = false }
            else { container.trimmer.cancelPending(); saving = false; snackbar.showSnackbar("Replace cancelled") }
        }
    }
    fun save() {
        saving = true; preview.pause()
        scope.launch {
            when (val r = container.trimmer.trim(t, startMs, endMs, mode)) {
                is TrimOutcome.NeedsConsent -> consentLauncher.launch(IntentSenderRequest.Builder(r.intentSender).build())
                else -> handle(r, snackbar, nav) { saving = false }
            }
        }
    }

    Column(Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).padding(bottom = 160.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
            Text("Trim", style = MaterialTheme.typography.headlineSmall)
        }
        Row(Modifier.padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
            ArtworkImage(t.uri, t.artworkUri, Modifier.size(64.dp), albumKey = t.albumKey)
            Spacer(Modifier.width(14.dp))
            Column {
                Text(t.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text("${t.artist} · ${t.format} · ${fmt(durationMs)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        Spacer(Modifier.height(20.dp))
        Card(Modifier.padding(horizontal = 16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
            Column(Modifier.padding(16.dp)) {
                // Selection strip: dim outside, accent inside, playhead while previewing.
                val accent = MaterialTheme.colorScheme.primary; val dim = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f); val head = MaterialTheme.colorScheme.onSurface
                Canvas(Modifier.fillMaxWidth().height(56.dp).clip(MaterialTheme.shapes.small)) {
                    val w = size.width; val h = size.height
                    drawRoundRect(dim, Offset.Zero, Size(w, h), CornerRadius(8f))
                    val x0 = w * startMs / durationMs; val x1 = w * endMs / durationMs
                    drawRoundRect(accent.copy(alpha = 0.35f), Offset(x0, 0f), Size((x1 - x0).coerceAtLeast(2f), h), CornerRadius(8f))
                    // bars for texture (deterministic, decorative)
                    val n = 60; val bw = w / n
                    for (i in 0 until n) {
                        val v = 0.25f + 0.7f * abs(kotlin.math.sin(i * 1.7f + (t.id % 7)) * kotlin.math.cos(i * 0.37f))
                        val cx = i * bw + bw / 2; val inSel = cx in x0..x1
                        drawRoundRect(if (inSel) accent else dim, Offset(cx - bw * 0.25f, h * (1 - v) / 2), Size(bw * 0.5f, h * v), CornerRadius(4f))
                    }
                    if (previewing) { val px = w * previewPos / durationMs; drawRoundRect(head, Offset(px - 1.5f, 0f), Size(3f, h), CornerRadius(2f)) }
                }
                RangeSlider(
                    value = startMs.toFloat()..endMs.toFloat(),
                    onValueChange = { r -> startMs = r.start.toLong().coerceIn(0, durationMs - 500); endMs = r.endInclusive.toLong().coerceIn(startMs + 500, durationMs) },
                    onValueChangeFinished = { selectionChanged() },
                    valueRange = 0f..durationMs.toFloat(),
                    colors = SliderDefaults.colors(activeTrackColor = accent, inactiveTrackColor = dim, thumbColor = accent),
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    TimeLabel("Start", startMs)
                    TimeLabel("Length", endMs - startMs, center = true)
                    TimeLabel("End", endMs, alignEnd = true)
                }
                Spacer(Modifier.height(6.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    NudgeRow { d -> startMs = (startMs + d).coerceIn(0, endMs - 500); selectionChanged() }
                    NudgeRow { d -> endMs = (endMs + d).coerceIn(startMs + 500, durationMs); selectionChanged() }
                }
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    FilledIconButton(onClick = { togglePreview() }, Modifier.size(52.dp)) { Icon(if (previewing) Icons.Default.Pause else Icons.Default.PlayArrow, "Preview") }
                    Spacer(Modifier.width(12.dp))
                    Text(if (previewing) "Previewing · ${fmt(previewPos)}" else "Preview the selection", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        Text("Save as", Modifier.padding(horizontal = 20.dp), style = MaterialTheme.typography.labelLarge)
        SingleChoiceSegmentedButtonRow(Modifier.padding(horizontal = 20.dp, vertical = 8.dp).fillMaxWidth()) {
            SegmentedButton(selected = mode == TrimMode.NEW, onClick = { mode = TrimMode.NEW }, shape = SegmentedButtonDefaults.itemShape(0, 2)) { Text("New song") }
            SegmentedButton(selected = mode == TrimMode.REPLACE, onClick = { mode = TrimMode.REPLACE }, shape = SegmentedButtonDefaults.itemShape(1, 2)) { Text("Replace original") }
        }
        Text(
            if (mode == TrimMode.NEW) "A new file \"${t.title} (cut)\" is added to your library; the original is untouched."
            else "The original file is overwritten with the trimmed version. Playlists and favourites keep working. This can't be undone.",
            Modifier.padding(horizontal = 20.dp), style = MaterialTheme.typography.bodySmall, color = if (mode == TrimMode.REPLACE) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))
        Row(Modifier.padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = { if (mode == TrimMode.REPLACE) confirmReplace = true else save() }, enabled = !saving && endMs - startMs >= 500) {
                if (saving) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Icon(Icons.Default.ContentCut, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp)); Text(if (saving) "Cutting…" else "Cut & save")
            }
            Spacer(Modifier.width(12.dp))
            Text("Lossless (no re-encode) when the format allows; cut points land on the nearest audio frame (~25 ms).", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }

    if (confirmReplace) AlertDialog(
        onDismissRequest = { confirmReplace = false },
        title = { Text("Replace the original?") },
        text = { Text("\"${t.title}\" will be overwritten with the ${fmt(endMs - startMs)} selection. The cut-off parts are gone for good.") },
        confirmButton = { TextButton(onClick = { confirmReplace = false; save() }) { Text("Replace", color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = { confirmReplace = false }) { Text("Cancel") } },
    )
}

private suspend fun handle(r: TrimOutcome, snackbar: SnackbarHostState, nav: NavController, done: () -> Unit) {
    done()
    when (r) {
        is TrimOutcome.Done -> { nav.popBackStack(); snackbar.showSnackbar(r.message) }
        is TrimOutcome.Failed -> snackbar.showSnackbar(r.reason)
        is TrimOutcome.NeedsConsent -> Unit
    }
}

@Composable
private fun TimeLabel(label: String, valueMs: Long, alignEnd: Boolean = false, center: Boolean = false) {
    Column(horizontalAlignment = if (center) Alignment.CenterHorizontally else if (alignEnd) Alignment.End else Alignment.Start) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(fmt(valueMs), style = MaterialTheme.typography.titleMedium, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun NudgeRow(onDelta: (Long) -> Unit) {
    Row { Nudge("−1s") { onDelta(-1000) }; Nudge("−.1") { onDelta(-100) }; Nudge("+.1") { onDelta(100) }; Nudge("+1s") { onDelta(1000) } }
}

@Composable
private fun Nudge(label: String, onClick: () -> Unit) {
    Box(
        Modifier.padding(end = 4.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHighest).clickable(onClick = onClick).padding(horizontal = 8.dp, vertical = 4.dp),
    ) { Text(label, style = MaterialTheme.typography.labelSmall, maxLines = 1, softWrap = false) }
}
