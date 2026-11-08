package com.vibe.player.backup

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import androidx.core.net.toUri
import com.vibe.player.data.db.VibeDatabase
import com.vibe.player.metadata.ArtworkStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipException
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

data class BackupProgress(val title: String, val detail: String, val done: Int, val total: Int) {
    val fraction get() = if (total > 0) done.toFloat() / total else -1f
}

sealed class BackupResult {
    data class Done(val message: String, val restartRequired: Boolean) : BackupResult()
    data class Error(val message: String) : BackupResult()
}

/**
 * Full-app backup: one zip holding the Room database, DataStore settings, custom artwork, the YouTube
 * cookie file and every audio file in the library. Import validates the archive (first entry must be the
 * manifest), restores the songs through MediaStore, remaps their URIs inside a staged copy of the
 * database, then swaps the files in and asks for a restart.
 */
class BackupManager(private val context: Context, private val db: VibeDatabase, private val artwork: ArtworkStore) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _progress = MutableStateFlow<BackupProgress?>(null)
    val progress: StateFlow<BackupProgress?> = _progress.asStateFlow()
    private val _result = MutableStateFlow<BackupResult?>(null)
    val result: StateFlow<BackupResult?> = _result.asStateFlow()
    fun clearResult() { _result.value = null }

    val busy get() = _progress.value != null

    private val dbFile get() = context.getDatabasePath("vibe.db")
    private val prefsFile get() = File(context.filesDir, "datastore/settings.preferences_pb")
    private val extraFiles get() = listOf("yt_cookies.txt", "yt_cookies.meta").map { File(context.filesDir, it) }

    fun exportTo(target: Uri) {
        if (busy) return
        scope.launch {
            _result.value = try { BackupResult.Done(doExport(target), restartRequired = false) } catch (e: Throwable) { BackupResult.Error(e.message ?: "Export failed") } finally { _progress.value = null }
        }
    }

    fun importFrom(source: Uri) {
        if (busy) return
        scope.launch {
            _result.value = try { doImport(source) } catch (e: Throwable) { BackupResult.Error(e.message ?: "Import failed") } finally { _progress.value = null }
        }
    }

    // ------------------------------------------------------------------ export

    private suspend fun doExport(target: Uri): String {
        _progress.value = BackupProgress("Exporting", "Preparing database…", 0, 1)
        // Fold the WAL into the main file so the copy is self-contained.
        db.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(TRUNCATE)").use { it.moveToFirst() }

        val tracks = db.tracks().allIncludingMissing().filter { !it.missing }
        val manifest = JSONObject().apply {
            put("app", APP_ID); put("format", FORMAT_VERSION); put("created", System.currentTimeMillis())
            put("schema", db.openHelper.readableDatabase.version)
            put("tracks", JSONArray().apply {
                tracks.forEach { t -> put(JSONObject().apply { put("id", t.id); put("uri", t.uri); put("name", t.displayName); put("zip", "songs/${t.id}/${safeName(t.displayName)}") }) }
            })
        }

        val out = context.contentResolver.openOutputStream(target, "w") ?: throw IOException("Couldn't open the destination file")
        var songsDone = 0; var songsSkipped = 0
        ZipOutputStream(BufferedOutputStream(out, 1 shl 16)).use { zip ->
            zip.setLevel(1) // audio doesn't compress; keep it fast
            zip.putText("manifest.json", manifest.toString())
            zip.putFile("db/vibe.db", dbFile)
            if (prefsFile.exists()) zip.putFile("prefs/settings.preferences_pb", prefsFile)
            extraFiles.filter { it.exists() }.forEach { zip.putFile("files/${it.name}", it) }
            artwork.artworkDir().listFiles()?.forEach { zip.putFile("artwork/${it.name}", it) }

            tracks.forEachIndexed { i, t ->
                _progress.value = BackupProgress("Exporting songs", t.title, i, tracks.size)
                val input = runCatching { context.contentResolver.openInputStream(t.uri.toUri()) }.getOrNull()
                if (input == null) { songsSkipped++; return@forEachIndexed }
                input.use { zip.putStream("songs/${t.id}/${safeName(t.displayName)}", it) }
                songsDone++
            }
        }
        return "Exported $songsDone song(s), playlists, settings and artwork." + if (songsSkipped > 0) " $songsSkipped file(s) were unreadable and skipped." else ""
    }

    // ------------------------------------------------------------------ import

    private suspend fun doImport(source: Uri): BackupResult {
        _progress.value = BackupProgress("Importing", "Reading backup…", 0, 1)
        val stage = File(context.cacheDir, "restore").apply { deleteRecursively(); mkdirs() }
        val input = context.contentResolver.openInputStream(source) ?: return BackupResult.Error("Couldn't open that file")

        var manifest: JSONObject? = null
        val remap = HashMap<Long, String>()   // old track id -> new uri
        var restored = 0; var kept = 0
        val stagedDb = File(stage, "vibe.db")
        val stagedArt = File(stage, "artwork").apply { mkdirs() }

        try {
            ZipInputStream(BufferedInputStream(input, 1 shl 16)).use { zip ->
                var entry: ZipEntry? = zip.nextEntry ?: return BackupResult.Error("That file is empty or not a zip archive")
                if (entry!!.name != "manifest.json") return BackupResult.Error("Not a Cwave backup (no manifest found)")
                manifest = runCatching { JSONObject(zip.readText()) }.getOrNull() ?: return BackupResult.Error("Not a Cwave backup (manifest unreadable)")
                val m = manifest!!
                if (m.optString("app") != APP_ID) return BackupResult.Error("Not a Cwave backup")
                if (m.optInt("format", 0) > FORMAT_VERSION) return BackupResult.Error("This backup was made by a newer Cwave; update the app first")
                if (m.optInt("schema", 0) > db.openHelper.readableDatabase.version) return BackupResult.Error("This backup needs a newer version of Cwave")
                val byId = HashMap<Long, JSONObject>()
                val arr = m.optJSONArray("tracks") ?: JSONArray()
                for (i in 0 until arr.length()) arr.getJSONObject(i).let { byId[it.getLong("id")] = it }
                val total = arr.length()

                while (true) {
                    entry = zip.nextEntry ?: break
                    val name = entry!!.name
                    when {
                        name == "db/vibe.db" -> stagedDb.outputStream().use { zip.copyTo(it) }
                        name == "prefs/settings.preferences_pb" -> File(stage, "settings.preferences_pb").outputStream().use { zip.copyTo(it) }
                        name.startsWith("files/") -> File(stage, name.removePrefix("files/")).outputStream().use { zip.copyTo(it) }
                        name.startsWith("artwork/") -> File(stagedArt, name.removePrefix("artwork/")).outputStream().use { zip.copyTo(it) }
                        name.startsWith("songs/") -> {
                            val id = name.removePrefix("songs/").substringBefore('/').toLongOrNull()
                            val meta = id?.let { byId[it] }
                            if (meta == null) { zip.skipAll(); continue }
                            _progress.value = BackupProgress("Restoring songs", meta.optString("name"), restored + kept, total)
                            val oldUri = meta.optString("uri")
                            if (isReadable(oldUri)) { kept++; zip.skipAll(); continue }   // same device: file still there, keep the reference
                            val newUri = saveToMusic(meta.optString("name"), zip)
                            remap[id] = newUri.toString(); restored++
                        }
                        else -> zip.skipAll()
                    }
                }
            }
        } catch (e: ZipException) {
            return BackupResult.Error("That file isn't a valid zip archive")
        }
        if (!stagedDb.exists()) return BackupResult.Error("Backup is missing its database")

        // Point restored tracks at their new files inside the staged database.
        _progress.value = BackupProgress("Importing", "Updating library…", 0, 1)
        val m = manifest!!
        SQLiteDatabase.openDatabase(stagedDb.absolutePath, null, SQLiteDatabase.OPEN_READWRITE).use { sdb ->
            val arr = m.optJSONArray("tracks") ?: JSONArray()
            for (i in 0 until arr.length()) {
                val t = arr.getJSONObject(i); val id = t.getLong("id"); val oldUri = t.optString("uri")
                val newUri = remap[id]
                if (newUri != null) {
                    sdb.execSQL(
                        "UPDATE tracks SET uri=?, source='DOWNLOADED', folder=?, folderName='Restored', missing=0, hidden=0, artworkUri=NULL, albumId=0 WHERE id=?",
                        arrayOf(newUri, "/storage/emulated/0/Music/Cwave/Restored", id),
                    )
                    val from = File(stagedArt, "t_${artwork.stableHash(oldUri)}.jpg"); if (from.exists()) from.renameTo(File(stagedArt, "t_${artwork.stableHash(newUri)}.jpg"))
                } else if (!isReadable(oldUri)) {
                    sdb.execSQL("UPDATE tracks SET missing=1 WHERE id=?", arrayOf(id))
                }
            }
        }

        // Swap everything in. Room is closed first; the app restarts right after.
        _progress.value = BackupProgress("Importing", "Finishing…", 1, 1)
        db.close()
        File(dbFile.path + "-wal").delete(); File(dbFile.path + "-shm").delete()
        stagedDb.copyTo(dbFile, overwrite = true)
        File(stage, "settings.preferences_pb").takeIf { it.exists() }?.let { prefsFile.parentFile?.mkdirs(); it.copyTo(prefsFile, overwrite = true) }
        extraFiles.forEach { f -> File(stage, f.name).takeIf { it.exists() }?.copyTo(f, overwrite = true) }
        artwork.artworkDir().deleteRecursively(); stagedArt.copyRecursively(artwork.artworkDir(), overwrite = true)
        stage.deleteRecursively()
        return BackupResult.Done("Imported ${restored + kept} song(s), playlists, settings and artwork." + (if (restored > 0) " $restored file(s) were copied to Music/Cwave/Restored." else ""), restartRequired = true)
    }

    private fun isReadable(uri: String): Boolean = uri.isNotBlank() && runCatching { context.contentResolver.openFileDescriptor(uri.toUri(), "r")?.use { true } ?: false }.getOrDefault(false)

    private fun saveToMusic(name: String, stream: InputStream): Uri {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, safeName(name))
            put(MediaStore.MediaColumns.MIME_TYPE, mimeFor(name))
            put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_MUSIC}/Cwave/Restored")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), values) ?: throw IOException("MediaStore refused to create $name")
        try {
            resolver.openOutputStream(uri)?.use { stream.copyTo(it, 1 shl 16) } ?: throw IOException("Couldn't write $name")
            resolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
        } catch (e: Exception) { runCatching { resolver.delete(uri, null, null) }; throw e }
        return uri
    }

    private fun mimeFor(name: String) = when (name.substringAfterLast('.', "").lowercase()) {
        "mp3" -> "audio/mpeg"; "flac" -> "audio/flac"; "wav" -> "audio/wav"; "m4a" -> "audio/mp4"; "aac" -> "audio/aac"; "ogg" -> "audio/ogg"; "opus" -> "audio/opus"; else -> "audio/*"
    }
    private fun safeName(n: String) = n.replace(Regex("[\\\\/:*?\"<>|\\x00-\\x1F]"), "_").take(180).ifBlank { "audio" }

    private fun ZipOutputStream.putText(name: String, text: String) { putNextEntry(ZipEntry(name)); write(text.toByteArray()); closeEntry() }
    private fun ZipOutputStream.putFile(name: String, file: File) { file.inputStream().use { putStream(name, it) } }
    private fun ZipOutputStream.putStream(name: String, input: InputStream) { putNextEntry(ZipEntry(name)); input.copyTo(this, 1 shl 16); closeEntry() }
    private fun ZipInputStream.readText(): String = readBytes().toString(Charsets.UTF_8)
    private fun ZipInputStream.skipAll() { val buf = ByteArray(1 shl 16); while (read(buf) >= 0) { /* drain */ } }

    companion object {
        const val APP_ID = "cwave"
        const val FORMAT_VERSION = 1
        fun suggestedFileName(): String = "Cwave-backup-" + java.text.SimpleDateFormat("yyyyMMdd-HHmm", java.util.Locale.US).format(java.util.Date()) + ".zip"
    }
}
