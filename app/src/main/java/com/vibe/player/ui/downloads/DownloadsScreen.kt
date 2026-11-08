package com.vibe.player.ui.downloads

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.PlayCircleOutline
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material3.FilterChip
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.foundation.combinedClickable
import androidx.compose.material3.Checkbox
import com.vibe.player.data.db.Track
import com.vibe.player.ui.components.LocalSelection
import com.vibe.player.ui.components.LocalSelectionScope
import com.vibe.player.ui.components.SelectionKind
import com.vibe.player.ui.components.SelectionScope
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.vibe.player.container
import com.vibe.player.data.db.DownloadItem
import com.vibe.player.data.db.DownloadStatus
import com.vibe.player.downloads.UrlValidator
import com.vibe.player.ui.components.EmptyState
import com.vibe.player.ui.formatDuration
import com.vibe.player.ui.pluralize
import kotlinx.coroutines.launch

@Composable
fun DownloadsScreen(snackbar: SnackbarHostState, onSignIn: (() -> Unit)? = null, onPlayTrack: ((Track) -> Unit)? = null) {
    var mode by rememberSaveable { mutableIntStateOf(0) }
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Text(if (mode == 0) "Downloads" else "Convert", Modifier.padding(start = 20.dp, top = 12.dp), style = MaterialTheme.typography.headlineMedium)
        SingleChoiceSegmentedButtonRow(Modifier.padding(horizontal = 20.dp, vertical = 12.dp).fillMaxWidth()) {
            SegmentedButton(selected = mode == 0, onClick = { mode = 0 }, shape = SegmentedButtonDefaults.itemShape(0, 2)) { Text("Download") }
            SegmentedButton(selected = mode == 1, onClick = { mode = 1 }, shape = SegmentedButtonDefaults.itemShape(1, 2)) { Text("Convert") }
        }
        if (mode == 0) DownloadTab(snackbar, onSignIn, onPlayTrack) else ConvertTab(snackbar)
    }
}

/** Max video height per chip; 0 means whatever is best. Stored per queue item so retries keep the choice. */
private val videoQualities = listOf(0 to "Best", 2160 to "4K", 1440 to "1440p", 1080 to "1080p", 720 to "720p", 480 to "480p", 360 to "360p")

@Composable
private fun DownloadTab(snackbar: SnackbarHostState, onSignIn: (() -> Unit)?, onPlayTrack: ((Track) -> Unit)?) {
    val context = LocalContext.current
    val container = context.container
    val repo = container.downloads
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    val items by repo.all.collectAsState(emptyList())
    val active by repo.activeCount.collectAsState(0)
    val yt by repo.ytdlp.status.collectAsStateWithLifecycle()
    val settings by container.settings.settings.collectAsState(null)
    var input by rememberSaveable { mutableStateOf("") }
    var adding by remember { mutableStateOf(false) }
    var asVideo by rememberSaveable { mutableStateOf(false) }
    var videoHeight by rememberSaveable { mutableStateOf(0) }   // 0 = best available
    val qualityLabel = videoQualities.firstOrNull { it.first == videoHeight }?.second ?: "Best"
    val preview = remember(input) { UrlValidator.extractAll(input) }
    val knownKeys = remember(items) { items.map { "${it.url.trimEnd('/')}|${it.audioFormat}" }.toSet() }
    val dupes = preview.count { "${it.trimEnd('/')}|${if (asVideo) com.vibe.player.downloads.YtDlpAdapter.VIDEO_FORMAT else "mp3"}" in knownKeys }
    LaunchedEffect(Unit) { runCatching { repo.ytdlp.ensureInitialized() } }
    val sel = LocalSelection.current

    CompositionLocalProvider(LocalSelectionScope provides SelectionScope({ items.map { it.id.toString() } })) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 160.dp)) {
        item {
            Card(Modifier.padding(horizontal = 16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                Column(Modifier.padding(16.dp)) {
                    OutlinedTextField(
                        input, { input = it }, Modifier.fillMaxWidth().heightIn(min = 96.dp),
                        placeholder = { Text("Paste one or more links — YouTube videos, playlists, YouTube Music, SoundCloud, or direct .mp3/.flac URLs. One per line.") },
                        minLines = 3, maxLines = 8,
                        trailingIcon = {
                            IconButton(onClick = { clipboard.getText()?.text?.let { t -> input = if (input.isBlank()) t else input.trimEnd() + "\n" + t } }) { Icon(Icons.Default.ContentPaste, "Paste") }
                        },
                    )
                    Spacer(Modifier.height(12.dp))
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        SegmentedButton(selected = !asVideo, onClick = { asVideo = false }, shape = SegmentedButtonDefaults.itemShape(0, 2)) { Text("Audio · MP3 ${settings?.mp3Bitrate ?: 320}k") }
                        SegmentedButton(selected = asVideo, onClick = { asVideo = true }, shape = SegmentedButtonDefaults.itemShape(1, 2)) { Text("Video · $qualityLabel") }
                    }
                    AnimatedVisibility(asVideo) {
                        Column {
                            Spacer(Modifier.height(12.dp))
                            Text("Video quality (max)", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.height(2.dp))
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(videoQualities) { (h, label) -> FilterChip(selected = videoHeight == h, onClick = { videoHeight = h }, label = { Text(label) }) }
                            }
                            Text("If the video isn't offered at that size, the next lower one is used.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    AnimatedVisibility(preview.isNotEmpty()) {
                        Text(
                            "${pluralize(preview.size, "link")} detected" + (if (dupes > 0) " · $dupes already queued" else "") +
                                (if (asVideo) (if (videoHeight > 0) " · MP4 up to ${videoHeight}p" else " · MP4, highest resolution offered") else " · MP3 ${settings?.mp3Bitrate ?: 320} kbps"),
                            style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(
                        buildString {
                            append("yt-dlp "); append(yt.version ?: settings?.ytdlpVersion ?: if (yt.initialized) "(bundled)" else "starting…")
                            if (yt.updating) append(" · updating…")
                            append(if (asVideo) " · saves to Movies/Cwave" else " · saves to Music/Cwave")
                        },
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(10.dp))
                    Row {
                        Button(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = {
                                adding = true
                                scope.launch {
                                    val r = repo.enqueueText(input, if (asVideo) com.vibe.player.downloads.YtDlpAdapter.VIDEO_FORMAT else "mp3", if (asVideo) videoHeight else 0)
                                    input = ""
                                    adding = false
                                    snackbar.showSnackbar(
                                        buildList {
                                            if (r.added > 0) add("${r.added} queued")
                                            if (r.duplicates > 0) add("${r.duplicates} duplicate")
                                            if (r.unsupported > 0) add("${r.unsupported} unsupported")
                                            if (r.invalid > 0) add("${r.invalid} not a URL")
                                        }.ifEmpty { listOf("Nothing to add") }.joinToString(" · "),
                                    )
                                }
                            },
                            enabled = preview.isNotEmpty() && !adding,
                        ) { Icon(Icons.Default.Download, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text(if (asVideo) "Download video" else "Download audio") }
                    }
                }
            }
        }
        item {
            Row(Modifier.padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(if (active > 0) "Queue · $active active" else "Queue", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                if (items.any { it.status !in setOf(DownloadStatus.PENDING, DownloadStatus.RUNNING) }) TextButton(onClick = { scope.launch { repo.clearFinished() } }) { Text("Clear finished") }
            }
        }
        if (items.isEmpty()) item { EmptyState(Icons.Default.Download, "Queue is empty", "Finished downloads are saved to your Music folder and appear in the library automatically.") }
        items(items, key = { it.id }) { d ->
            DownloadRow(
                d, onCancel = { scope.launch { repo.cancel(d.id) } }, onRetry = { scope.launch { repo.retry(d.id) } }, onRemove = { scope.launch { repo.remove(d.id) } },
                onOpen = {
                    d.outputUri?.let { u ->
                        if (d.audioFormat != com.vibe.player.downloads.YtDlpAdapter.VIDEO_FORMAT && onPlayTrack != null) {
                            scope.launch {
                                val t = container.library.trackByUri(u) ?: run { runCatching { container.library.importFiles(listOf(android.net.Uri.parse(u))) }; container.library.trackByUri(u) }
                                if (t != null) onPlayTrack(t) else snackbar.showSnackbar("File not in the library yet — try Rescan in Settings")
                            }
                            return@let
                        }
                        runCatching {
                            context.startActivity(
                                android.content.Intent(android.content.Intent.ACTION_VIEW).setDataAndType(android.net.Uri.parse(u), if (d.audioFormat == com.vibe.player.downloads.YtDlpAdapter.VIDEO_FORMAT) "video/mp4" else "audio/*")
                                    .addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_ACTIVITY_NEW_TASK),
                            )
                        }.onFailure { scope.launch { snackbar.showSnackbar("No app can open this file") } }
                    }
                },
                onSignIn = onSignIn,
            )
        }
    }
    }
}

@Composable
private fun DownloadRow(d: DownloadItem, onCancel: () -> Unit, onRetry: () -> Unit, onRemove: () -> Unit, onOpen: () -> Unit, onSignIn: (() -> Unit)? = null) {
    val sel = LocalSelection.current
    val selScope = LocalSelectionScope.current
    val key = d.id.toString()
    val selecting = sel.inMode(SelectionKind.DOWNLOADS)
    val picked = selecting && sel.isSelected(key)
    val (icon, tint) = when (d.status) {
        DownloadStatus.DONE -> Icons.Default.CheckCircle to MaterialTheme.colorScheme.secondary
        DownloadStatus.FAILED, DownloadStatus.UNSUPPORTED -> Icons.Default.ErrorOutline to MaterialTheme.colorScheme.error
        DownloadStatus.CANCELLED -> Icons.Default.Cancel to MaterialTheme.colorScheme.onSurfaceVariant
        DownloadStatus.RUNNING -> Icons.Default.Download to MaterialTheme.colorScheme.primary
        else -> Icons.Default.Schedule to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Card(
        Modifier.padding(horizontal = 16.dp, vertical = 5.dp).animateContentSize()
            .combinedClickable(
                onClick = { if (selecting) sel.toggle(key) },
                onLongClick = { if (!sel.active) sel.start(key, SelectionKind.DOWNLOADS, selScope.ids) else if (selecting) sel.toggle(key) },
            ),
        colors = CardDefaults.cardColors(containerColor = if (picked) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f) else MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (selecting) { Checkbox(picked, onCheckedChange = { sel.toggle(key) }); Spacer(Modifier.width(4.dp)) }
                if (d.thumbnailUrl != null) {
                    AsyncImage(d.thumbnailUrl, null, Modifier.size(width = 72.dp, height = 48.dp).clip(MaterialTheme.shapes.small), contentScale = ContentScale.Crop)
                } else {
                    Icon(icon, null, Modifier.size(28.dp), tint = tint)
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(d.title ?: UrlValidator.host(d.url).ifBlank { d.url }, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    val sub = listOfNotNull(d.uploader, d.durationSec?.let { formatDuration(it * 1000) }, d.url.takeIf { d.title == null }).joinToString(" · ")
                    if (sub.isNotBlank()) Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                when (d.status) {
                    DownloadStatus.PENDING, DownloadStatus.RUNNING -> IconButton(onClick = onCancel) { Icon(Icons.Default.Cancel, "Cancel") }
                    DownloadStatus.FAILED, DownloadStatus.CANCELLED -> { IconButton(onClick = onRetry) { Icon(Icons.Default.Refresh, "Retry") }; IconButton(onClick = onRemove) { Icon(Icons.Default.Delete, "Remove") } }
                    DownloadStatus.DONE -> {
                        if (d.outputUri != null) IconButton(onClick = onOpen) { Icon(Icons.Default.PlayCircleOutline, "Open") }
                        IconButton(onClick = onRemove) { Icon(Icons.Default.Delete, "Remove") }
                    }
                    else -> IconButton(onClick = onRemove) { Icon(Icons.Default.Delete, "Remove") }
                }
            }
            if (d.audioFormat == com.vibe.player.downloads.YtDlpAdapter.VIDEO_FORMAT) {
                Spacer(Modifier.height(4.dp))
                Text(if (d.bitrateKbps > 0) "Video · up to ${d.bitrateKbps}p" else "Video · best quality", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            }
            when (d.status) {
                DownloadStatus.RUNNING -> {
                    Spacer(Modifier.height(10.dp))
                    // Determinate only once yt-dlp reports real progress; otherwise an indeterminate bar. No percentage text.
                    if (d.progress > 0f) LinearProgressIndicator(progress = { d.progress / 100f }, Modifier.fillMaxWidth().height(6.dp).clip(MaterialTheme.shapes.extraSmall), drawStopIndicator = {})
                    else LinearProgressIndicator(Modifier.fillMaxWidth().height(6.dp).clip(MaterialTheme.shapes.extraSmall))
                    Spacer(Modifier.height(6.dp))
                    Text(
                        d.statusLine?.replace("[download]", "")?.replace(Regex("^\\s*[0-9.]+%\\s*(of)?\\s*"), "")?.trim()?.ifBlank { null } ?: "Downloading…",
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
                DownloadStatus.PENDING -> { Spacer(Modifier.height(6.dp)); Text("Waiting in queue", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                DownloadStatus.DONE -> { Spacer(Modifier.height(6.dp)); Text(d.statusLine ?: "Done", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary) }
                DownloadStatus.FAILED, DownloadStatus.UNSUPPORTED -> {
                    Spacer(Modifier.height(6.dp)); Text(d.error ?: "Failed", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    if (d.error?.contains("bot check", ignoreCase = true) == true && onSignIn != null) {
                        TextButton(onClick = onSignIn, contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) { Text("Sign in to YouTube") }
                    }
                }
                DownloadStatus.CANCELLED -> { Spacer(Modifier.height(6.dp)); Text("Cancelled", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }
    }
}
