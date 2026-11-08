package com.vibe.player.ui.components

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.vibe.player.data.db.Track

/** One-shot system photo picker; call the returned lambda to open it. */
@Composable
fun rememberImagePicker(onPicked: (android.net.Uri) -> Unit): () -> Unit {
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> if (uri != null) onPicked(uri) }
    return { launcher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
}

/** Text field with a dropdown of existing values; typing a new value is fine too. */
@Composable
fun SuggestField(label: String, value: String, suggestions: List<String>, onChange: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val matches = remember(value, suggestions) {
        val q = value.trim().lowercase()
        suggestions.filter { it.lowercase().contains(q) && it != value }.take(8)
    }
    ExposedDropdownMenuBox(expanded = expanded && matches.isNotEmpty(), onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value, { onChange(it); expanded = true }, label = { Text(label) }, singleLine = true,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded && matches.isNotEmpty()) },
            modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryEditable),
        )
        ExposedDropdownMenu(expanded && matches.isNotEmpty(), onDismissRequest = { expanded = false }) {
            matches.forEach { s -> DropdownMenuItem(text = { Text(s) }, onClick = { onChange(s); expanded = false }) }
        }
    }
}

/** Edit title / artist / album of one song. Artist and album offer existing names so songs can be grouped into them. */
@Composable
fun EditTrackDialog(track: Track, artists: List<String>, albums: List<String>, onDismiss: () -> Unit, onSave: (title: String, artist: String, album: String) -> Unit) {
    var title by remember { mutableStateOf(track.title) }
    var artist by remember { mutableStateOf(track.artist) }
    var album by remember { mutableStateOf(track.album) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit details") },
        text = {
            Column {
                OutlinedTextField(title, { title = it }, label = { Text("Title") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(10.dp))
                SuggestField("Artist", artist, artists) { artist = it }
                Spacer(Modifier.height(10.dp))
                SuggestField("Album", album, albums) { album = it }
                Spacer(Modifier.height(10.dp))
                Text("Changes apply inside Cwave only; the audio file's tags are not rewritten.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = { TextButton(onClick = { onSave(title, artist, album) }, enabled = title.isNotBlank()) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Rename an artist or album across every song that has it. */
@Composable
fun RenameDialog(title: String, label: String, initial: String, note: String, suggestions: List<String> = emptyList(), confirmLabel: String = "Rename", onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var value by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                if (suggestions.isEmpty()) OutlinedTextField(value, { value = it }, label = { Text(label) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                else SuggestField(label, value, suggestions) { value = it }
                Spacer(Modifier.height(10.dp))
                Text(note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = { TextButton(onClick = { onSave(value) }, enabled = value.isNotBlank() && value.trim() != initial) { Text(confirmLabel) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
