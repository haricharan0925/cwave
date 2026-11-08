package com.vibe.player.metadata

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import androidx.documentfile.provider.DocumentFile

data class AudioMetadata(
    val title: String?,
    val artist: String?,
    val album: String?,
    val durationMs: Long,
    val mimeType: String?,
    val year: Int,
    val trackNumber: Int,
    val hasEmbeddedArt: Boolean,
)

object AudioFormats {
    private val byExtension = mapOf(
        "mp3" to "MP3", "flac" to "FLAC", "wav" to "WAV", "m4a" to "M4A", "aac" to "AAC", "mp4" to "M4A",
        "ogg" to "OGG", "oga" to "OGG", "opus" to "OPUS", "wma" to "WMA", "aif" to "AIFF", "aiff" to "AIFF",
        "mka" to "MKA", "webm" to "WEBM", "amr" to "AMR", "mid" to "MIDI",
    )
    private val byMime = mapOf(
        "audio/mpeg" to "MP3", "audio/mp3" to "MP3", "audio/flac" to "FLAC", "audio/x-flac" to "FLAC",
        "audio/wav" to "WAV", "audio/x-wav" to "WAV", "audio/vnd.wave" to "WAV", "audio/mp4" to "M4A", "audio/m4a" to "M4A",
        "audio/x-m4a" to "M4A", "audio/aac" to "AAC", "audio/ogg" to "OGG", "audio/vorbis" to "OGG", "audio/opus" to "OPUS",
        "audio/x-ms-wma" to "WMA", "audio/aiff" to "AIFF", "audio/x-aiff" to "AIFF", "audio/webm" to "WEBM", "audio/x-matroska" to "MKA",
    )

    fun detect(displayName: String?, mimeType: String?): String {
        val ext = displayName?.substringAfterLast('.', "")?.lowercase().orEmpty()
        // A video container is labelled by its own extension (MP4/WEBM/MKV), not by the audio format that shares it (M4A).
        if (isVideo(displayName, mimeType)) return ext.uppercase().ifBlank { "VIDEO" }
        byExtension[ext]?.let { return it }
        byMime[mimeType?.lowercase()]?.let { return it }
        return if (ext.isNotBlank()) ext.uppercase() else "AUDIO"
    }

    fun isLossless(format: String) = format in setOf("FLAC", "WAV", "AIFF")

    private val videoExtensions = setOf("mp4", "m4v", "mkv", "webm", "mov", "avi", "3gp", "3g2", "ts", "mts", "m2ts", "flv", "wmv", "mpg", "mpeg", "ogv")

    /** True for video containers; the converter then extracts the first audio track and drops the picture. */
    fun isVideo(displayName: String?, mimeType: String?): Boolean {
        val mime = mimeType?.lowercase()
        if (mime != null && mime.startsWith("video/")) return true
        if (mime != null && mime.startsWith("audio/")) return false   // audio-only .mp4/.webm from MediaStore or yt-dlp
        val ext = displayName?.substringAfterLast('.', "")?.lowercase().orEmpty()
        return ext in videoExtensions
    }
}

object MetadataExtractor {

    fun extract(context: Context, uri: Uri): AudioMetadata? {
        val r = MediaMetadataRetriever()
        return try {
            r.setDataSource(context, uri)
            AudioMetadata(
                title = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)?.trim()?.ifBlank { null },
                artist = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)?.trim()?.ifBlank { null }
                    ?: r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST)?.trim()?.ifBlank { null },
                album = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)?.trim()?.ifBlank { null },
                durationMs = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L,
                mimeType = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE),
                year = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_YEAR)?.take(4)?.toIntOrNull() ?: 0,
                trackNumber = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CD_TRACK_NUMBER)?.substringBefore('/')?.toIntOrNull() ?: 0,
                hasEmbeddedArt = r.embeddedPicture != null,
            )
        } catch (e: Exception) {
            null
        } finally {
            runCatching { r.release() }
        }
    }

    fun embeddedArt(context: Context, uri: Uri): ByteArray? {
        val r = MediaMetadataRetriever()
        return try {
            r.setDataSource(context, uri)
            r.embeddedPicture
        } catch (e: Exception) {
            null
        } finally {
            runCatching { r.release() }
        }
    }

    fun displayName(context: Context, uri: Uri): String? {
        if (uri.scheme == "file") return uri.lastPathSegment
        runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) return c.getString(0)
            }
        }
        return DocumentFile.fromSingleUri(context, uri)?.name ?: uri.lastPathSegment
    }

    fun size(context: Context, uri: Uri): Long {
        runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { c ->
                if (c.moveToFirst() && !c.isNull(0)) return c.getLong(0)
            }
        }
        return runCatching { context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length } }.getOrNull() ?: 0L
    }

    fun titleFromName(name: String?): String =
        name?.substringBeforeLast('.')?.replace('_', ' ')?.trim()?.ifBlank { null } ?: "Unknown title"
}
