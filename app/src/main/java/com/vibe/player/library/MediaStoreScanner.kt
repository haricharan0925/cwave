package com.vibe.player.library

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.core.net.toUri
import com.vibe.player.data.db.Track
import com.vibe.player.data.db.TrackDao
import com.vibe.player.data.db.TrackSource
import com.vibe.player.metadata.AudioFormats
import com.vibe.player.metadata.MetadataExtractor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

data class ScanResult(val found: Int, val added: Int, val missing: Int, val hidden: Int = 0)

data class ScanOptions(val hideRecordings: Boolean = true, val hiddenFolders: Set<String> = emptySet())

/** Folder names and file-name prefixes typical of call recorders, voice memo apps and chat-app audio (WhatsApp). */
object RecordingHeuristics {
    private val folderPattern = Regex(
        "(^|/)(call ?rec(ording)?s?|call|recordings?|recorder|voice ?recorder|voice ?notes?|voice ?memos?|sound ?recorder|" +
            "easy ?voice ?recorder|ptt|record|smart ?recorder|phone ?recordings?|" +
            // WhatsApp / WhatsApp Business: "WhatsApp/Media/WhatsApp Audio", ".../WhatsApp Voice Notes" and the
            // scoped-storage location "Android/media/com.whatsapp(.w4b)/WhatsApp/Media/...".
            "whatsapp[^/]*|com\\.whatsapp[^/]*)(/|$)",
        RegexOption.IGNORE_CASE,
    )
    // AUD-20240101-WA0001.mp3 / PTT-20240101-WA0001.opus are WhatsApp's names for received audio and voice notes.
    private val namePattern = Regex("^(call[ _-]?rec|call[ _@-]|voice[ _-]?\\d|recording[ _-]?\\d|rec[ _-]?\\d|ptt-|audio_\\d{4}|new recording|(aud|ptt)-\\d{8}-wa\\d+)", RegexOption.IGNORE_CASE)

    fun looksLikeRecording(folderPath: String, displayName: String, isRecordingFlag: Boolean): Boolean =
        isRecordingFlag || folderPattern.containsMatchIn(folderPath) || namePattern.containsMatchIn(displayName)
}

class MediaStoreScanner(private val context: Context, private val dao: TrackDao) {

    fun hasPermission(): Boolean {
        val perm = if (Build.VERSION.SDK_INT >= 33) android.Manifest.permission.READ_MEDIA_AUDIO else android.Manifest.permission.READ_EXTERNAL_STORAGE
        return context.checkSelfPermission(perm) == android.content.pm.PackageManager.PERMISSION_GRANTED
    }

    suspend fun scan(options: ScanOptions = ScanOptions()): ScanResult = withContext(Dispatchers.IO) {
        val existing = dao.allIncludingMissing().associateBy { it.uri }
        val seen = HashSet<String>()
        var added = 0
        var found = 0
        var hiddenCount = 0

        if (hasPermission()) {
            val collection = if (Build.VERSION.SDK_INT >= 29) MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL) else MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
            val hasRecordingColumn = Build.VERSION.SDK_INT >= 31
            val projection = buildList {
                addAll(
                    listOf(
                        MediaStore.Audio.Media._ID, MediaStore.Audio.Media.TITLE, MediaStore.Audio.Media.ARTIST, MediaStore.Audio.Media.ALBUM,
                        MediaStore.Audio.Media.ALBUM_ID, MediaStore.Audio.Media.DURATION, MediaStore.Audio.Media.SIZE, MediaStore.Audio.Media.MIME_TYPE,
                        MediaStore.Audio.Media.DATA, MediaStore.Audio.Media.DISPLAY_NAME, MediaStore.Audio.Media.DATE_ADDED, MediaStore.Audio.Media.YEAR,
                        MediaStore.Audio.Media.TRACK,
                    ),
                )
                if (hasRecordingColumn) add(MediaStore.Audio.Media.IS_RECORDING)
            }.toTypedArray()
            val selection = "(${MediaStore.Audio.Media.IS_MUSIC} != 0 OR ${MediaStore.Audio.Media.IS_PODCAST} != 0 OR ${MediaStore.Audio.Media.DURATION} > 0) " +
                "AND ${MediaStore.Audio.Media.IS_RINGTONE} = 0 AND ${MediaStore.Audio.Media.IS_NOTIFICATION} = 0 AND ${MediaStore.Audio.Media.IS_ALARM} = 0"
            context.contentResolver.query(collection, projection, selection, null, null)?.use { c ->
                val iId = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val iTitle = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val iArtist = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val iAlbum = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                val iAlbumId = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
                val iDur = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                val iSize = c.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)
                val iMime = c.getColumnIndexOrThrow(MediaStore.Audio.Media.MIME_TYPE)
                val iData = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
                val iName = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
                val iDate = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)
                val iYear = c.getColumnIndexOrThrow(MediaStore.Audio.Media.YEAR)
                val iTrack = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TRACK)
                val iRec = if (hasRecordingColumn) c.getColumnIndex(MediaStore.Audio.Media.IS_RECORDING) else -1

                while (c.moveToNext()) {
                    val id = c.getLong(iId)
                    val uri = ContentUris.withAppendedId(collection, id).toString()
                    val path = c.getString(iData) ?: ""
                    val folder = File(path).parent ?: "/"
                    val name = c.getString(iName) ?: File(path).name
                    val mime = c.getString(iMime) ?: ""
                    val albumId = c.getLong(iAlbumId)
                    val trackNo = c.getInt(iTrack).let { if (it > 1000) it % 1000 else it }
                    val album = c.getString(iAlbum).orUnknownAlbum()
                    val folderName = File(folder).name.ifBlank { "Storage" }
                    // MediaStore invents an "album" named after the folder for untagged files and shares one cover
                    // across all of them. Only trust album art when the file really has an album tag.
                    val albumArt = if (album == folderName || album == "Unknown album") null else albumArtUri(albumId)
                    val isRecordingFlag = iRec >= 0 && !c.isNull(iRec) && c.getInt(iRec) != 0
                    val hidden = folder in options.hiddenFolders ||
                        (options.hideRecordings && RecordingHeuristics.looksLikeRecording(folder, name, isRecordingFlag))
                    if (hidden) hiddenCount++
                    val track = Track(
                        uri = uri,
                        title = c.getString(iTitle)?.ifBlank { null } ?: MetadataExtractor.titleFromName(name),
                        artist = c.getString(iArtist).orUnknownArtist(),
                        album = album,
                        albumId = albumId,
                        durationMs = c.getLong(iDur),
                        sizeBytes = c.getLong(iSize),
                        mimeType = mime,
                        format = AudioFormats.detect(name, mime),
                        folder = folder,
                        folderName = folderName,
                        displayName = name,
                        dateAdded = c.getLong(iDate) * 1000,
                        source = TrackSource.MEDIASTORE,
                        artworkUri = albumArt,
                        year = c.getInt(iYear),
                        trackNumber = trackNo,
                        missing = false,
                        hidden = hidden,
                    )
                    seen += uri
                    found++
                    val prev = existing[uri]
                    if (prev == null) {
                        dao.insertIgnore(track); added++
                    } else {
                        // In-app tag edits win over whatever MediaStore reports.
                        val merged = if (prev.userEdited) track.copy(title = prev.title, artist = prev.artist, album = prev.album, userEdited = true) else track
                        if (prev.missing || prev.hidden != hidden || prev.title != merged.title || prev.artist != merged.artist || prev.album != merged.album || prev.durationMs != merged.durationMs || prev.artworkUri != merged.artworkUri) {
                            dao.update(merged.copy(id = prev.id, source = prev.source, dateAdded = prev.dateAdded))
                        }
                    }
                }
            }
        }

        var missing = 0
        for ((uri, track) in existing) {
            when (track.source) {
                TrackSource.MEDIASTORE -> if (uri !in seen && !track.missing) { dao.setMissing(track.id, true); missing++ }
                else -> {
                    val ok = isReadable(uri.toUri())
                    if (!ok && !track.missing) { dao.setMissing(track.id, true); missing++ }
                    if (ok && track.missing) dao.setMissing(track.id, false)
                    val hide = track.folder in options.hiddenFolders
                    if (hide != track.hidden) dao.setHidden(track.id, hide)
                }
            }
        }
        ScanResult(found, added, missing, hiddenCount)
    }

    private fun isReadable(uri: Uri): Boolean =
        runCatching { context.contentResolver.openFileDescriptor(uri, "r")?.use { true } ?: false }.getOrDefault(false)

    companion object {
        fun albumArtUri(albumId: Long): String? =
            if (albumId > 0) ContentUris.withAppendedId("content://media/external/audio/albumart".toUri(), albumId).toString() else null

        fun String?.orUnknownArtist() = this?.takeIf { it.isNotBlank() && it != "<unknown>" } ?: "Unknown artist"
        fun String?.orUnknownAlbum() = this?.takeIf { it.isNotBlank() && it != "<unknown>" } ?: "Unknown album"
    }
}
