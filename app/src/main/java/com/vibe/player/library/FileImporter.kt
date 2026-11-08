package com.vibe.player.library

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.vibe.player.data.db.Track
import com.vibe.player.data.db.TrackDao
import com.vibe.player.data.db.TrackSource
import com.vibe.player.library.MediaStoreScanner.Companion.orUnknownAlbum
import com.vibe.player.library.MediaStoreScanner.Companion.orUnknownArtist
import com.vibe.player.metadata.AudioFormats
import com.vibe.player.metadata.MetadataExtractor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class ImportResult(val imported: Int, val skipped: Int, val failed: Int)

/** Imports audio picked through the Storage Access Framework. Files are referenced by URI, never copied. */
class FileImporter(private val context: Context, private val dao: TrackDao) {

    private val audioExtensions = setOf("mp3", "flac", "wav", "m4a", "aac", "ogg", "oga", "opus", "wma", "aif", "aiff", "mka", "webm", "amr")

    suspend fun importFiles(uris: List<Uri>, source: String = TrackSource.IMPORTED, folderLabel: String = "Imported"): ImportResult = withContext(Dispatchers.IO) {
        var imported = 0; var skipped = 0; var failed = 0
        for (uri in uris) {
            persist(uri)
            when (importOne(uri, source, folderLabel)) {
                ImportOutcome.ADDED -> imported++
                ImportOutcome.EXISTS -> skipped++
                ImportOutcome.FAILED -> failed++
            }
        }
        ImportResult(imported, skipped, failed)
    }

    suspend fun importTree(treeUri: Uri): ImportResult = withContext(Dispatchers.IO) {
        persist(treeUri)
        val root = DocumentFile.fromTreeUri(context, treeUri) ?: return@withContext ImportResult(0, 0, 1)
        val files = ArrayList<Pair<Uri, String>>()
        walk(root, root.name ?: "Folder", files, depth = 0)
        var imported = 0; var skipped = 0; var failed = 0
        for ((uri, folder) in files) {
            when (importOne(uri, TrackSource.IMPORTED, folder)) {
                ImportOutcome.ADDED -> imported++
                ImportOutcome.EXISTS -> skipped++
                ImportOutcome.FAILED -> failed++
            }
        }
        ImportResult(imported, skipped, failed)
    }

    private fun walk(dir: DocumentFile, label: String, out: MutableList<Pair<Uri, String>>, depth: Int) {
        if (depth > 6) return
        for (f in dir.listFiles()) {
            if (f.isDirectory) walk(f, f.name ?: label, out, depth + 1)
            else if (f.isFile && (f.type?.startsWith("audio/") == true || f.name?.substringAfterLast('.', "")?.lowercase() in audioExtensions)) {
                out += f.uri to label
            }
        }
    }

    private enum class ImportOutcome { ADDED, EXISTS, FAILED }

    private suspend fun importOne(uri: Uri, source: String, folderLabel: String): ImportOutcome {
        if (dao.byUri(uri.toString()) != null) return ImportOutcome.EXISTS
        val name = MetadataExtractor.displayName(context, uri) ?: "audio"
        val meta = MetadataExtractor.extract(context, uri) ?: return ImportOutcome.FAILED
        val mime = meta.mimeType ?: context.contentResolver.getType(uri) ?: ""
        val track = Track(
            uri = uri.toString(),
            title = meta.title ?: MetadataExtractor.titleFromName(name),
            artist = meta.artist.orUnknownArtist(),
            album = meta.album.orUnknownAlbum(),
            albumId = 0,
            durationMs = meta.durationMs,
            sizeBytes = MetadataExtractor.size(context, uri),
            mimeType = mime,
            format = AudioFormats.detect(name, mime),
            folder = "import:$folderLabel",
            folderName = folderLabel,
            displayName = name,
            dateAdded = System.currentTimeMillis(),
            source = source,
            artworkUri = null,
            year = meta.year,
            trackNumber = meta.trackNumber,
        )
        return if (dao.insertIgnore(track) > 0) ImportOutcome.ADDED else ImportOutcome.EXISTS
    }

    private fun persist(uri: Uri) {
        runCatching {
            context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }
}
