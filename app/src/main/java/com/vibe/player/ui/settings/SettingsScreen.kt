package com.vibe.player.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import com.vibe.player.R
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.vibe.player.container
import com.vibe.player.convert.AudioConverter
import com.vibe.player.ui.navigation.Routes
import com.vibe.player.ui.theme.ThemeMode
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

@Composable
fun SettingsScreen(nav: NavController, snackbar: SnackbarHostState) {
    val context = LocalContext.current
    val container = context.container
    val scope = rememberCoroutineScope()
    val settings by container.settings.settings.collectAsState(null)
    val yt by container.downloads.ytdlp.status.collectAsStateWithLifecycle()
    val encoders by container.converter.encoders.collectAsStateWithLifecycle()
    val scanning by container.library.scanning.collectAsStateWithLifecycle()
    val count by container.library.trackCount().collectAsState(0)
    var destination by remember { mutableStateOf("Music/Cwave") }
    LaunchedEffect(settings?.destinationTreeUri) { destination = container.outputStore.destinationLabel() }
    LaunchedEffect(Unit) { runCatching { container.downloads.ytdlp.ensureInitialized() } }

    val pickTree = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            runCatching { context.contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION) }
            scope.launch { container.settings.setDestination(uri.toString()) }
        }
    }

    Column(Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).padding(bottom = 160.dp)) {
        Text("Settings", Modifier.padding(start = 20.dp, top = 12.dp, bottom = 8.dp), style = MaterialTheme.typography.headlineMedium)

        Group("Appearance")
        SingleChoiceSegmentedButtonRow(Modifier.padding(horizontal = 20.dp).fillMaxWidth()) {
            ThemeMode.entries.forEachIndexed { i, m ->
                SegmentedButton(selected = settings?.themeMode == m, onClick = { scope.launch { container.settings.setThemeMode(m) } }, shape = SegmentedButtonDefaults.itemShape(i, 3)) {
                    Text(m.name.lowercase().replaceFirstChar { it.uppercase() })
                }
            }
        }

        Text("Accent colour", Modifier.padding(start = 20.dp, top = 18.dp, bottom = 8.dp), style = MaterialTheme.typography.bodyLarge)
        AccentRow(current = settings?.accentArgb ?: com.vibe.player.ui.theme.DEFAULT_ACCENT_ARGB) { argb -> scope.launch { container.settings.setAccent(argb) } }

        Group("Library")
        val hiddenCount by container.library.hiddenCount().collectAsState(0)
        ListItem(
            headlineContent = { Text("Hide recordings & voice memos") },
            supportingContent = { Text("Skips call recordings, voice notes, WhatsApp audio and recorder folders. $hiddenCount file(s) currently hidden.") },
            trailingContent = { androidx.compose.material3.Switch(settings?.hideRecordings ?: true, { on -> scope.launch { container.library.setHideRecordings(on) } }) },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        )
        settings?.hiddenFolders?.takeIf { it.isNotEmpty() }?.let { hidden ->
            ListItem(
                headlineContent = { Text("Hidden folders") },
                supportingContent = {
                    Column {
                        hidden.sorted().forEach { folder ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(folder.removePrefix("import:").substringAfterLast('/').ifBlank { folder }, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                                TextButton(onClick = { scope.launch { container.library.unhideFolder(folder) } }) { Text("Unhide") }
                            }
                        }
                    }
                },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            )
        }
        ListItem(
            headlineContent = { Text("Rescan device music") },
            supportingContent = { Text("$count songs · last scan " + (settings?.lastScanAt?.takeIf { it > 0 }?.let { DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(it)) } ?: "never")) },
            trailingContent = { if (scanning) CircularProgressIndicator(Modifier.width(22.dp).height(22.dp), strokeWidth = 2.dp) },
            modifier = Modifier.clickable(enabled = !scanning) { scope.launch { val r = container.library.rescan(); snackbar.showSnackbar("Found ${r.found} songs, ${r.added} new" + (if (r.missing > 0) ", ${r.missing} missing" else "")) } },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        )

        Group("Downloads & conversion")
        ListItem(
            headlineContent = { Text("Destination folder") },
            supportingContent = { Text(destination + if (settings?.destinationTreeUri == null) " (default, public Music folder)" else "") },
            trailingContent = {
                Row {
                    if (settings?.destinationTreeUri != null) TextButton(onClick = { scope.launch { container.settings.setDestination(null) } }) { Text("Reset") }
                    OutlinedButton(onClick = { pickTree.launch(null) }) { Text("Change") }
                }
            },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        )
        ListItem(
            headlineContent = { Text("Default MP3 bitrate (kbps)") },
            supportingContent = {
                Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AudioConverter.MP3_BITRATES.forEach { b -> FilterChip(selected = settings?.mp3Bitrate == b, onClick = { scope.launch { container.settings.setMp3Bitrate(b) } }, label = { Text("$b") }) }
                }
            },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        )

        Group("YouTube")
        val ytAccount by container.youTubeAuth.account.collectAsStateWithLifecycle()
        ListItem(
            headlineContent = { Text(if (ytAccount.signedIn) "Signed in to YouTube" else "YouTube account (optional)") },
            supportingContent = {
                Text(
                    if (ytAccount.signedIn) "Downloads use your session, which stops the \"confirm you're not a bot\" check. Saved ${DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(ytAccount.savedAt))}."
                    else "Only needed if YouTube keeps asking you to prove you're not a bot. Use a spare Google account, not your main one.",
                )
            },
            trailingContent = {
                if (ytAccount.signedIn) TextButton(onClick = { container.youTubeAuth.signOut(); scope.launch { snackbar.showSnackbar("Signed out of YouTube") } }) { Text("Sign out") }
                else OutlinedButton(onClick = { nav.navigate(Routes.YT_SIGNIN) }) { Text("Sign in") }
            },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        )

        Group("Engines")
        ListItem(
            headlineContent = { Text("yt-dlp") },
            supportingContent = {
                Column {
                    Text("Version: ${yt.version ?: settings?.ytdlpVersion ?: if (yt.initialized) "bundled build" else "starting…"}" + (yt.lastError?.let { " · $it" } ?: ""))
                    Text("Auto-updates once a day before downloading. If downloads start failing, update manually first.", style = MaterialTheme.typography.bodySmall)
                }
            },
            trailingContent = {
                if (yt.updating) CircularProgressIndicator(Modifier.width(22.dp).height(22.dp), strokeWidth = 2.dp)
                else OutlinedButton(onClick = { scope.launch { snackbar.showSnackbar(container.downloads.ytdlp.update()) } }) { Text("Update") }
            },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        )
        ListItem(
            headlineContent = { Text("FFmpeg (conversion)") },
            supportingContent = {
                Column {
                    Text("FFmpegKit audio ${encoders.ffmpegVersion ?: "…"} · LGPL-3.0")
                    Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Enc("MP3 (libmp3lame)", encoders.mp3, encoders.checked); Enc("WAV", encoders.wav, encoders.checked); Enc("FLAC", encoders.flac, encoders.checked)
                    }
                }
            },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        )

        Group("Backup")
        val backup = container.backup
        val backupProgress by backup.progress.collectAsStateWithLifecycle()
        val backupResult by backup.result.collectAsStateWithLifecycle()
        val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri -> if (uri != null) backup.exportTo(uri) }
        val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) backup.importFrom(uri) }
        ListItem(
            headlineContent = { Text("Export everything") },
            supportingContent = { Text("One zip with your songs, playlists, favourites, settings, custom artwork and YouTube sign-in. Can be large.") },
            trailingContent = { OutlinedButton(onClick = { exportLauncher.launch(com.vibe.player.backup.BackupManager.suggestedFileName()) }, enabled = backupProgress == null) { Text("Export") } },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        )
        ListItem(
            headlineContent = { Text("Import a backup") },
            supportingContent = { Text("Replaces this app's library, playlists and settings with the backup. Songs missing on this device are copied to Music/Cwave/Restored.") },
            trailingContent = { OutlinedButton(onClick = { importLauncher.launch(arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream")) }, enabled = backupProgress == null) { Text("Import") } },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        )
        backupProgress?.let { p ->
            androidx.compose.material3.AlertDialog(
                onDismissRequest = {}, confirmButton = {},
                title = { Text(p.title) },
                text = {
                    Column {
                        Text(p.detail, maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                        Spacer(Modifier.height(12.dp))
                        if (p.fraction >= 0f) androidx.compose.material3.LinearProgressIndicator(progress = { p.fraction }, Modifier.fillMaxWidth()) else androidx.compose.material3.LinearProgressIndicator(Modifier.fillMaxWidth())
                        if (p.total > 1) Text("${p.done} / ${p.total}", style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 6.dp))
                    }
                },
            )
        }
        backupResult?.let { r ->
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { if (r !is com.vibe.player.backup.BackupResult.Done || !r.restartRequired) backup.clearResult() },
                title = { Text(when (r) { is com.vibe.player.backup.BackupResult.Done -> if (r.restartRequired) "Import complete" else "Export complete"; is com.vibe.player.backup.BackupResult.Error -> "Couldn't import" }) },
                text = { Text(when (r) { is com.vibe.player.backup.BackupResult.Done -> r.message + if (r.restartRequired) "\n\nCwave needs to restart to load the imported library." else ""; is com.vibe.player.backup.BackupResult.Error -> r.message }) },
                confirmButton = {
                    TextButton(onClick = {
                        if (r is com.vibe.player.backup.BackupResult.Done && r.restartRequired) restartApp(context) else backup.clearResult()
                    }) { Text(if (r is com.vibe.player.backup.BackupResult.Done && r.restartRequired) "Restart now" else "OK") }
                },
            )
        }

        Group("About")
        ListItem(headlineContent = { Text("Open-source licenses") }, supportingContent = { Text("FFmpeg, LAME, yt-dlp, Media3 and others") }, modifier = Modifier.clickable { nav.navigate(Routes.LICENSES) }, colors = ListItemDefaults.colors(containerColor = Color.Transparent))
        ListItem(
            headlineContent = { Text("Cwave 1.0") },
            supportingContent = { Text("Personal-use music player. No accounts, ads or analytics.") },
            leadingContent = { Image(painterResource(R.drawable.ic_app_icon), null, Modifier.width(44.dp).height(44.dp).clip(MaterialTheme.shapes.small)) },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        )
        ListItem(
            headlineContent = { Text("Made by Haricharan") },
            supportingContent = {
                Column {
                    Text("haricharan7701@gmail.com", color = MaterialTheme.colorScheme.primary, modifier = Modifier.clickable {
                        runCatching { context.startActivity(android.content.Intent(android.content.Intent.ACTION_SENDTO, android.net.Uri.parse("mailto:haricharan7701@gmail.com"))) }
                    })
                    Text("Instagram @charan.25_", color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 4.dp).clickable {
                        runCatching { context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://instagram.com/charan.25_"))) }
                    })
                }
            },
            leadingContent = { Icon(Icons.Default.Person, null, tint = MaterialTheme.colorScheme.primary) },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        )
    }
}

/** Relaunch from the launcher intent after the process exits; used after a backup import swaps the database. */
private fun restartApp(context: android.content.Context) = com.vibe.player.RestartActivity.trigger(context)

@Composable
private fun Group(title: String) {
    Text(title.uppercase(), Modifier.padding(start = 20.dp, top = 22.dp, bottom = 6.dp), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
}

@Composable
private fun Enc(label: String, ok: Boolean, checked: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (!checked) Text("…") else Icon(if (ok) Icons.Default.Check else Icons.Default.Close, null, Modifier.width(14.dp).height(14.dp), tint = if (ok) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.error)
        Spacer(Modifier.width(3.dp))
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}

private data class Notice(val name: String, val license: String, val rawResource: String? = null, val url: String? = null)

@Composable
fun LicensesScreen(nav: NavController) {
    val context = LocalContext.current
    val notices = remember {
        listOf(
            Notice("FFmpeg (via FFmpegKit maintained, audio build)", "LGPL-3.0", "license", "https://github.com/ffmpegkit-maintained/ffmpeg"),
            Notice("LAME MP3 encoder", "LGPL-2.0", "license_lame"),
            Notice("libvorbis / libogg", "BSD-3-Clause", "license_libvorbis"),
            Notice("Opus", "BSD-3-Clause", "license_opus"),
            Notice("Shine", "LGPL-2.0", "license_shine"),
            Notice("TwoLAME", "LGPL-2.0", "license_twolame"),
            Notice("SoX Resampler (soxr)", "LGPL-2.1", "license_soxr"),
            Notice("Speex", "BSD-3-Clause", "license_speex"),
            Notice("libsndfile", "LGPL-2.1", "license_libsndfile"),
            Notice("opencore-amr", "Apache-2.0", "license_opencore_amr"),
            Notice("vo-amrwbenc", "Apache-2.0", "license_vo_amrwbenc"),
            Notice("libilbc", "BSD-3-Clause", "license_libilbc"),
            Notice("libiconv", "LGPL-2.1", "license_libiconv"),
            Notice("cpu_features", "Apache-2.0", "license_cpu_features"),
            Notice("yt-dlp", "Unlicense (public domain)", url = "https://github.com/yt-dlp/yt-dlp"),
            Notice("youtubedl-android (bundled Python runtime + yt-dlp + ffmpeg)", "GPL-3.0 — acceptable for personal use; redistribution requires GPL compliance", url = "https://github.com/yausername/youtubedl-android"),
            Notice("AndroidX Media3 (ExoPlayer), Room, Compose, WorkManager", "Apache-2.0"),
            Notice("Coil", "Apache-2.0", url = "https://github.com/coil-kt/coil"),
            Notice("Reorderable (sh.calvin.reorderable)", "Apache-2.0", url = "https://github.com/Calvin-LL/Reorderable"),
        )
    }
    var open by remember { mutableStateOf<Notice?>(null) }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
            Text("Licenses", style = MaterialTheme.typography.headlineSmall)
        }
        if (open == null) {
            LazyColumn(contentPadding = PaddingValues(bottom = 160.dp)) {
                item { Text("This app bundles the following third-party software. Tap an entry to read its license text.", Modifier.padding(20.dp), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                items(notices.size) { i ->
                    val n = notices[i]
                    ListItem(
                        headlineContent = { Text(n.name) }, supportingContent = { Text(n.license + (n.url?.let { "\n$it" } ?: "")) },
                        modifier = Modifier.clickable(enabled = n.rawResource != null) { open = n },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    )
                }
            }
        } else {
            val n = open!!
            val text = remember(n) {
                val id = context.resources.getIdentifier(n.rawResource, "raw", context.packageName)
                if (id == 0) "License text not bundled." else runCatching { context.resources.openRawResource(id).bufferedReader().readText() }.getOrDefault("Couldn't read license text.")
            }
            Column(Modifier.verticalScroll(rememberScrollState()).padding(20.dp)) {
                TextButton(onClick = { open = null }) { Text("← All licenses") }
                Text(n.name, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                Text(text, style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(160.dp))
            }
        }
    }
}
