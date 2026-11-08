package com.vibe.player.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.vibe.player.data.db.Track

/** After an image is picked: apply it to the one song or to every song in its album. */
@Composable
fun ArtScopeDialog(track: Track, image: android.net.Uri, onDismiss: () -> Unit, onSong: () -> Unit, onAlbum: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Use this picture for…") },
        text = {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AsyncImage(image, null, Modifier.size(72.dp).clip(MaterialTheme.shapes.small), contentScale = ContentScale.Crop)
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Text(track.title, style = MaterialTheme.typography.titleSmall, maxLines = 2)
                        Text(track.album, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text("The audio file itself is not modified; the picture is kept inside Cwave.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = { TextButton(onClick = onAlbum) { Text("Whole album") } },
        dismissButton = {
            Row(Modifier.fillMaxWidth()) {
                TextButton(onClick = onDismiss) { Text("Cancel") }
                TextButton(onClick = onSong) { Text("This song only") }
            }
        },
    )
}
