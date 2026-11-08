package com.vibe.player.downloads

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import com.vibe.player.data.LibraryRepository
import com.vibe.player.data.db.TrackSource
import com.vibe.player.settings.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

enum class ConflictPolicy { ASK, OVERWRITE, KEEP_BOTH }

data class SavedFile(val uri: Uri, val displayName: String, val bytes: Long, val location: String, val isVideo: Boolean = false)

/**
 * Persists finished downloads / conversions. Uses the user-picked SAF folder when one is set,
 * otherwise MediaStore's public Music/Vibe folder. Never writes outside scoped storage.
 */
class OutputStore(private val context: Context, private val settings: SettingsRepository, private val library: LibraryRepository) {

    private val subfolder = "Cwave"

    suspend fun destinationLabel(): String {
        val tree = settings.current().destinationTreeUri ?: return "Music/$subfolder"
        return DocumentFile.fromTreeUri(context, tree.toUri())?.name ?: "Custom folder"
    }

    suspend fun exists(displayName: String): Boolean = withContext(Dispatchers.IO) {
        val tree = settings.current().destinationTreeUri
        if (tree != null) {
            DocumentFile.fromTreeUri(context, tree.toUri())?.findFile(displayName) != null
        } else {
            queryMediaStore(displayName) != null
        }
    }

    suspend fun uniqueName(displayName: String): String {
        if (!exists(displayName)) return displayName
        val base = displayName.substringBeforeLast('.')
        val ext = displayName.substringAfterLast('.', "")
        var n = 1
        while (true) {
            val candidate = if (ext.isEmpty()) "$base ($n)" else "$base ($n).$ext"
            if (!exists(candidate)) return candidate
            n++
        }
    }

    /** Copies [file] into the destination and indexes it in the library. Throws on failure. */
    suspend fun save(file: File, displayName: String, mimeType: String, policy: ConflictPolicy, source: String = TrackSource.DOWNLOADED): SavedFile =
        withContext(Dispatchers.IO) {
            val safeName = sanitize(displayName)
            val finalName = when (policy) {
                ConflictPolicy.KEEP_BOTH, ConflictPolicy.ASK -> uniqueName(safeName)
                ConflictPolicy.OVERWRITE -> safeName
            }
            val tree = settings.current().destinationTreeUri
            val saved = if (tree != null) saveToTree(tree.toUri(), file, finalName, mimeType, policy == ConflictPolicy.OVERWRITE)
            else saveToMediaStore(file, finalName, mimeType, policy == ConflictPolicy.OVERWRITE)
            index(saved, tree != null, source)
            saved
        }

    private suspend fun index(saved: SavedFile, isTree: Boolean, source: String) {
        if (saved.isVideo) return // videos are not part of the music library
        if (isTree) {
            runCatching { library.importFiles(listOf(saved.uri)) }
        } else {
            runCatching { library.rescan() }
        }
    }

    private fun saveToTree(tree: Uri, file: File, name: String, mime: String, overwrite: Boolean): SavedFile {
        val dir = DocumentFile.fromTreeUri(context, tree) ?: throw IOException("Destination folder is no longer accessible. Pick it again in Settings.")
        if (overwrite) dir.findFile(name)?.delete()
        val target = dir.createFile(mime, name) ?: throw IOException("Couldn't create file in destination folder")
        context.contentResolver.openOutputStream(target.uri)?.use { out -> file.inputStream().use { it.copyTo(out) } }
            ?: throw IOException("Couldn't open destination for writing")
        return SavedFile(target.uri, target.name ?: name, file.length(), dir.name ?: "Folder", isVideo = mime.startsWith("video/"))
    }

    /** Audio goes to Music/Cwave, video to Movies/Cwave; both through MediaStore's pending-file protocol. */
    private fun saveToMediaStore(file: File, name: String, mime: String, overwrite: Boolean): SavedFile {
        val resolver = context.contentResolver
        val video = mime.startsWith("video/")
        if (overwrite) queryMediaStore(name)?.let { runCatching { resolver.delete(it, null, null) } }
        val relative = "${if (video) Environment.DIRECTORY_MOVIES else Environment.DIRECTORY_MUSIC}/$subfolder"
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, mime)
            put(MediaStore.MediaColumns.RELATIVE_PATH, relative)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val collection = if (video) MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY) else MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val uri = resolver.insert(collection, values) ?: throw IOException("MediaStore refused to create the file")
        try {
            resolver.openOutputStream(uri)?.use { out -> file.inputStream().use { it.copyTo(out) } } ?: throw IOException("Couldn't open MediaStore file")
            resolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
        } catch (e: Exception) {
            runCatching { resolver.delete(uri, null, null) }
            throw e
        }
        return SavedFile(uri, name, file.length(), relative, isVideo = video)
    }

    private fun queryMediaStore(name: String): Uri? {
        val collection = MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        context.contentResolver.query(
            collection, arrayOf(MediaStore.Audio.Media._ID),
            "${MediaStore.Audio.Media.DISPLAY_NAME} = ? AND ${MediaStore.Audio.Media.RELATIVE_PATH} LIKE ?",
            arrayOf(name, "${Environment.DIRECTORY_MUSIC}/$subfolder%"), null,
        )?.use { c ->
            if (c.moveToFirst()) return android.content.ContentUris.withAppendedId(collection, c.getLong(0))
        }
        return null
    }

    companion object {
        fun sanitize(name: String): String =
            name.replace(Regex("[\\\\/:*?\"<>|\\x00-\\x1F]"), "_").trim().trimEnd('.').take(180).ifBlank { "audio" }

        fun mimeFor(ext: String): String = when (ext.lowercase()) {
            "mp3" -> "audio/mpeg"; "wav" -> "audio/wav"; "flac" -> "audio/flac"; "m4a" -> "audio/mp4"; "aac" -> "audio/aac"
            "ogg" -> "audio/ogg"; "opus" -> "audio/opus"; "webm" -> "audio/webm"; else -> "audio/*"
        }
    }
}
