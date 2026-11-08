package com.vibe.player.library

import android.app.RecoverableSecurityException
import android.content.Context
import android.content.IntentSender
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import android.provider.MediaStore
import androidx.core.net.toUri
import com.vibe.player.data.db.Track
import com.vibe.player.data.db.TrackDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

sealed class DeleteOutcome {
    object Deleted : DeleteOutcome()
    /** Android wants the user to confirm in a system dialog (files owned by other apps on Android 10+). */
    data class NeedsConsent(val intentSender: IntentSender) : DeleteOutcome()
    data class Failed(val reason: String) : DeleteOutcome()
}

/** Result of a multi-delete: what's already gone, what failed, and one consent request covering the rest. */
data class DeleteManyResult(val deleted: Int, val failed: Int, val consent: IntentSender?, val pending: Int)

/** Deletes the audio file(s) behind tracks, then purges them from the library. */
class TrackDeleter(private val context: Context, private val dao: TrackDao) {

    private var pendingBatch: List<Track> = emptyList()

    /** True if removed, false if there was nothing to remove. Throws SecurityException when Android wants consent. */
    private fun deleteRaw(uri: Uri): Boolean = when {
        uri.scheme == "file" -> File(uri.path!!).delete()
        DocumentsContract.isDocumentUri(context, uri) -> DocumentsContract.deleteDocument(context.contentResolver, uri)
        uri.authority == MediaStore.AUTHORITY -> context.contentResolver.delete(uri, null, null) > 0
        else -> throw IllegalStateException("This kind of file can't be deleted from here")
    }

    suspend fun delete(track: Track): DeleteOutcome = withContext(Dispatchers.IO) {
        val uri = track.uri.toUri()
        val outcome = try {
            if (deleteRaw(uri)) DeleteOutcome.Deleted else DeleteOutcome.Failed("File not found (it may already be gone)")
        } catch (e: SecurityException) {
            // Android 10: RecoverableSecurityException carries the consent intent. Android 11+: ask MediaStore for one.
            val sender = when {
                Build.VERSION.SDK_INT >= 30 -> runCatching { MediaStore.createDeleteRequest(context.contentResolver, listOf(uri)).intentSender }.getOrNull()
                e is RecoverableSecurityException -> e.userAction.actionIntent.intentSender
                else -> null
            }
            if (sender != null) DeleteOutcome.NeedsConsent(sender) else DeleteOutcome.Failed("Android didn't allow deleting this file")
        } catch (e: Exception) {
            DeleteOutcome.Failed(e.message ?: "Couldn't delete the file")
        }
        if (outcome is DeleteOutcome.Deleted) dao.purge(track.id)
        outcome
    }

    /** Call after the single-file consent dialog returned OK: the file is gone, drop it from the library. */
    suspend fun afterConsent(track: Track) = withContext(Dispatchers.IO) { dao.purge(track.id) }

    /**
     * Deletes everything it can directly; files that need the user's permission are bundled into ONE
     * MediaStore consent request (Android 11+) so the user confirms once, not per file.
     */
    suspend fun deleteMany(tracks: List<Track>): DeleteManyResult = withContext(Dispatchers.IO) {
        var deleted = 0; var failed = 0
        val needConsent = ArrayList<Track>()
        for (t in tracks) {
            val uri = t.uri.toUri()
            try {
                if (deleteRaw(uri)) { dao.purge(t.id); deleted++ } else failed++
            } catch (e: SecurityException) {
                if (uri.authority == MediaStore.AUTHORITY && Build.VERSION.SDK_INT >= 30) needConsent += t else failed++
            } catch (e: Exception) {
                failed++
            }
        }
        var sender: IntentSender? = null
        if (needConsent.isNotEmpty()) {
            sender = runCatching { MediaStore.createDeleteRequest(context.contentResolver, needConsent.map { it.uri.toUri() }).intentSender }.getOrNull()
            if (sender == null) failed += needConsent.size else pendingBatch = needConsent
        }
        DeleteManyResult(deleted, failed, sender, if (sender != null) needConsent.size else 0)
    }

    /** After the batch consent dialog returned OK: Android deleted the files, drop them from the library. */
    suspend fun afterBatchConsent(): Int = withContext(Dispatchers.IO) {
        val n = pendingBatch.size
        pendingBatch.forEach { dao.purge(it.id) }
        pendingBatch = emptyList()
        n
    }

    fun cancelBatch() { pendingBatch = emptyList() }
}
