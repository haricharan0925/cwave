package com.vibe.player.convert

import android.app.RecoverableSecurityException
import android.content.Context
import android.content.IntentSender
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.core.net.toUri
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.FFmpegKitConfig
import com.arthenica.ffmpegkit.ReturnCode
import com.vibe.player.data.db.Track
import com.vibe.player.data.db.TrackDao
import com.vibe.player.data.db.TrackSource
import com.vibe.player.downloads.ConflictPolicy
import com.vibe.player.downloads.OutputStore
import com.vibe.player.metadata.MetadataExtractor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

enum class TrimMode { NEW, REPLACE }

sealed class TrimOutcome {
    data class Done(val message: String) : TrimOutcome()
    /** Android wants the user's consent before we may overwrite a file owned by another app. */
    data class NeedsConsent(val intentSender: IntentSender) : TrimOutcome()
    data class Failed(val reason: String) : TrimOutcome()
}

/**
 * Cuts [startMs, endMs] out of a track with FFmpeg. Stream copy first (lossless, instant); if the container
 * refuses, re-encodes to 320 kbps MP3. "Replace" overwrites the original bytes in place so playlists,
 * favourites and custom art keep pointing at the same track.
 */
class AudioTrimmer(private val context: Context, private val outputStore: OutputStore, private val dao: TrackDao) {

    private var pendingReplace: Pair<Track, File>? = null

    suspend fun trim(track: Track, startMs: Long, endMs: Long, mode: TrimMode): TrimOutcome = withContext(Dispatchers.IO) {
        if (endMs - startMs < 500) return@withContext TrimOutcome.Failed("Selection is too short")
        val srcUri = track.uri.toUri()
        val input = if (srcUri.scheme == "content") FFmpegKitConfig.getSafParameterForRead(context, srcUri) else srcUri.path
        val ext = track.displayName.substringAfterLast('.', "").lowercase().ifBlank { track.format.lowercase() }
        val workDir = File(context.cacheDir, "trim").apply { mkdirs() }
        var out = File(workDir, "${track.id}_${System.currentTimeMillis()}.$ext")
        val ss = "%.3f".format(Locale.US, startMs / 1000.0); val to = "%.3f".format(Locale.US, endMs / 1000.0)

        // 1. lossless cut
        var session = FFmpegKit.execute("-y -hide_banner -i \"$input\" -ss $ss -to $to -map 0:a -map 0:v? -c copy -map_metadata 0 -avoid_negative_ts make_zero \"${out.absolutePath}\"")
        var reencoded = false
        if (!ReturnCode.isSuccess(session.returnCode) || out.length() == 0L) {
            // 2. fallback: re-encode (only sensible for a new file; replacing in place must keep the format)
            if (mode == TrimMode.REPLACE) { out.delete(); return@withContext TrimOutcome.Failed("This file's format can't be cut without re-encoding. Save as a new song instead.") }
            out.delete(); out = File(workDir, "${track.id}_${System.currentTimeMillis()}.mp3")
            session = FFmpegKit.execute("-y -hide_banner -i \"$input\" -ss $ss -to $to -vn -c:a libmp3lame -b:a 320k -map_metadata 0 \"${out.absolutePath}\"")
            reencoded = true
            if (!ReturnCode.isSuccess(session.returnCode) || out.length() == 0L) {
                out.delete()
                val log = session.allLogsAsString.lines().lastOrNull { "error" in it.lowercase() || "invalid" in it.lowercase() }?.trim()
                return@withContext TrimOutcome.Failed(log?.take(200) ?: "FFmpeg couldn't cut this file")
            }
        }

        when (mode) {
            TrimMode.NEW -> try {
                val base = track.title.ifBlank { track.displayName.substringBeforeLast('.') }
                val name = "$base (cut).${out.extension}"
                val saved = outputStore.save(out, name, OutputStore.mimeFor(out.extension), ConflictPolicy.KEEP_BOTH, source = TrackSource.IMPORTED)
                TrimOutcome.Done("Saved \"${saved.displayName}\"" + if (reencoded) " (re-encoded to MP3)" else "")
            } catch (e: Exception) {
                TrimOutcome.Failed(e.message ?: "Couldn't save the new file")
            } finally { out.delete() }
            TrimMode.REPLACE -> replaceInto(track, out)
        }
    }

    /** Overwrites the original file's bytes with [cut]. Keeps the temp file around if consent is needed first. */
    private suspend fun replaceInto(track: Track, cut: File): TrimOutcome = withContext(Dispatchers.IO) {
        val uri = track.uri.toUri()
        try {
            val os = if (uri.scheme == "file") File(uri.path!!).outputStream() else context.contentResolver.openOutputStream(uri, "wt")
            os?.use { cut.inputStream().use { input -> input.copyTo(it) } } ?: return@withContext TrimOutcome.Failed("Couldn't open the original for writing")
        } catch (e: SecurityException) {
            val sender = when {
                Build.VERSION.SDK_INT >= 30 -> runCatching { MediaStore.createWriteRequest(context.contentResolver, listOf(uri)).intentSender }.getOrNull()
                e is RecoverableSecurityException -> e.userAction.actionIntent.intentSender
                else -> null
            }
            return@withContext if (sender != null) { pendingReplace = track to cut; TrimOutcome.NeedsConsent(sender) }
            else { cut.delete(); TrimOutcome.Failed("Android didn't allow overwriting this file. Save as a new song instead.") }
        } catch (e: Exception) {
            cut.delete(); return@withContext TrimOutcome.Failed(e.message ?: "Couldn't overwrite the original")
        }
        val meta = MetadataExtractor.extract(context, uri)
        dao.update(track.copy(durationMs = meta?.durationMs ?: track.durationMs, sizeBytes = MetadataExtractor.size(context, uri)))
        cut.delete(); pendingReplace = null
        TrimOutcome.Done("Replaced \"${track.title}\" with the trimmed version")
    }

    /** Call after the system consent dialog returned OK. */
    suspend fun completeReplace(): TrimOutcome {
        val (track, cut) = pendingReplace ?: return TrimOutcome.Failed("Nothing to replace")
        return replaceInto(track, cut)
    }

    fun cancelPending() { pendingReplace?.second?.delete(); pendingReplace = null }
}
