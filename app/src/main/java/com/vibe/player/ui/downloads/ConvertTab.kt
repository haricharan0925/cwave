package com.vibe.player.ui.downloads

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Transform
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.material.icons.filled.Movie
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vibe.player.container
import com.vibe.player.convert.AudioConverter
import com.vibe.player.convert.TargetFormat
import com.vibe.player.data.db.ConversionJob
import com.vibe.player.data.db.ConversionStatus
import com.vibe.player.downloads.ConflictPolicy
import com.vibe.player.metadata.AudioFormats
import com.vibe.player.ui.appViewModel
import com.vibe.player.ui.components.EmptyState
import com.vibe.player.ui.formatBytes
import kotlinx.coroutines.launch

@Composable
fun ConvertTab(snackbar: SnackbarHostState) {
    val context = LocalContext.current
    val container = context.container
    val converter = container.converter
    val vm = appViewModel { ConvertViewModel(it) }
    val scope = rememberCoroutineScope()
    val encoders by converter.encoders.collectAsStateWithLifecycle()
    val history by converter.history.collectAsState(emptyList())

    val target = vm.target
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) vm.pick(context, uri) }
    val supported = when (target) { TargetFormat.MP3 -> encoders.mp3; TargetFormat.WAV -> encoders.wav; TargetFormat.FLAC -> encoders.flac }
    val lossyToLossless = vm.sourceFormat.isNotBlank() && !AudioFormats.isLossless(vm.sourceFormat) && (target.lossless || (target == TargetFormat.MP3 && vm.bitrate >= 256))

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 160.dp)) {
        item {
            Card(Modifier.padding(horizontal = 16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                Column(Modifier.padding(16.dp).animateContentSize()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(if (vm.isVideo) Icons.Default.Movie else Icons.Default.AudioFile, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(if (vm.source == null) "No file selected" else vm.sourceName, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text(
                                when {
                                    vm.source == null -> "Audio file, or a video (MP4, MKV, WebM, MOV, AVI…) to pull the sound from"
                                    vm.isVideo -> "Source: ${vm.sourceFormat} video · the audio track is extracted, the picture is dropped"
                                    else -> "Source: ${vm.sourceFormat}"
                                },
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        OutlinedButton(onClick = { pick.launch(arrayOf("audio/*", "video/*")) }) { Text(if (vm.source == null) "Choose" else "Change") }
                    }
                    Spacer(Modifier.height(16.dp))
                    Text("Output format", style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.height(6.dp))
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        TargetFormat.entries.forEachIndexed { i, f ->
                            val ok = when (f) { TargetFormat.MP3 -> encoders.mp3; TargetFormat.WAV -> encoders.wav; TargetFormat.FLAC -> encoders.flac }
                            SegmentedButton(selected = vm.formatIndex == i, onClick = { vm.formatIndex = i }, shape = SegmentedButtonDefaults.itemShape(i, TargetFormat.entries.size), enabled = ok || !encoders.checked) { Text(f.label) }
                        }
                    }
                    AnimatedVisibility(target == TargetFormat.MP3) {
                        Column {
                            Spacer(Modifier.height(12.dp))
                            Text("Bitrate", style = MaterialTheme.typography.labelLarge)
                            Spacer(Modifier.height(6.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                AudioConverter.MP3_BITRATES.forEach { b -> FilterChip(selected = vm.bitrate == b, onClick = { vm.bitrate = b }, label = { Text("$b") }) }
                            }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Delete original after converting", style = MaterialTheme.typography.bodyMedium)
                            Text("Off by default. Files owned by other apps may not be deletable.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(vm.deleteSource, { vm.deleteSource = it })
                    }
                    AnimatedVisibility(lossyToLossless) {
                        Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.Top) {
                            Icon(Icons.Default.Info, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.tertiary)
                            Spacer(Modifier.width(8.dp))
                            Text(AudioConverter.QUALITY_NOTE, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Saves to ${vm.destination}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                        Button(
                            onClick = {
                                scope.launch {
                                    val asked = vm.requestConvert()
                                    if (!asked) snackbar.showSnackbar("Converting ${vm.sourceName.substringBeforeLast('.')} → ${target.label}")
                                }
                            },
                            enabled = vm.source != null && (supported || !encoders.checked),
                        ) { Icon(Icons.Default.Transform, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Start conversion") }
                    }
                    if (encoders.checked && !supported) {
                        Spacer(Modifier.height(6.dp))
                        Text("This FFmpeg build has no ${target.label} encoder.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
        item {
            Row(Modifier.padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("History", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                if (history.any { it.status !in setOf(ConversionStatus.QUEUED, ConversionStatus.RUNNING) }) TextButton(onClick = { scope.launch { converter.clearFinished() } }) { Text("Clear finished") }
            }
        }
        if (history.isEmpty()) item { EmptyState(Icons.Default.SwapHoriz, "No conversions yet", "Converted files are saved next to your downloads and added to the library.") }
        items(history, key = { it.id }) { job -> ConversionRow(job, onCancel = { converter.cancel(job.id) }, onRemove = { scope.launch { converter.remove(job.id) } }) }
    }

    if (vm.pendingConflict) {
        AlertDialog(
            onDismissRequest = { vm.pendingConflict = false },
            title = { Text("File already exists") },
            text = { Text("\"${vm.outputName()}\" is already in ${vm.destination}.") },
            confirmButton = { TextButton(onClick = { vm.start(ConflictPolicy.OVERWRITE) }) { Text("Overwrite") } },
            dismissButton = {
                Row {
                    TextButton(onClick = { vm.pendingConflict = false }) { Text("Cancel") }
                    TextButton(onClick = { vm.start(ConflictPolicy.KEEP_BOTH) }) { Text("Keep both") }
                }
            },
        )
    }
}

@Composable
private fun ConversionRow(job: ConversionJob, onCancel: () -> Unit, onRemove: () -> Unit) {
    Card(Modifier.padding(horizontal = 16.dp, vertical = 5.dp).animateContentSize(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val (icon, tint) = when (job.status) {
                    ConversionStatus.DONE -> Icons.Default.CheckCircle to MaterialTheme.colorScheme.secondary
                    ConversionStatus.FAILED -> Icons.Default.ErrorOutline to MaterialTheme.colorScheme.error
                    ConversionStatus.CANCELLED -> Icons.Default.Cancel to MaterialTheme.colorScheme.onSurfaceVariant
                    else -> Icons.Default.Transform to MaterialTheme.colorScheme.primary
                }
                Icon(icon, null, Modifier.size(26.dp), tint = tint)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(job.outputName ?: job.sourceName, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        "${job.sourceFormat} → ${job.targetFormat}" + (if (job.bitrateKbps > 0) " ${job.bitrateKbps} kbps" else "") + (if (job.outputBytes > 0) " · ${formatBytes(job.outputBytes)}" else ""),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                when (job.status) {
                    ConversionStatus.QUEUED, ConversionStatus.RUNNING -> IconButton(onClick = onCancel) { Icon(Icons.Default.Cancel, "Cancel") }
                    else -> IconButton(onClick = onRemove) { Icon(Icons.Default.Delete, "Remove") }
                }
            }
            when (job.status) {
                ConversionStatus.RUNNING -> {
                    Spacer(Modifier.height(10.dp))
                    LinearProgressIndicator(progress = { job.progress / 100f }, Modifier.fillMaxWidth().height(6.dp).clip(MaterialTheme.shapes.extraSmall))
                    Spacer(Modifier.height(4.dp))
                    Text("${job.progress.toInt()}%", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                }
                ConversionStatus.QUEUED -> { Spacer(Modifier.height(6.dp)); Text("Queued", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                ConversionStatus.FAILED -> { Spacer(Modifier.height(6.dp)); Text(job.error ?: "Failed", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
                ConversionStatus.DONE -> job.error?.let { Spacer(Modifier.height(6.dp)); Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary) }
                else -> {}
            }
        }
    }
}
