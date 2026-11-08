package com.vibe.player.convert

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.core.net.toUri
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.FFmpegKitConfig
import com.arthenica.ffmpegkit.FFmpegSession
import com.arthenica.ffmpegkit.ReturnCode
import com.vibe.player.data.LibraryRepository
import com.vibe.player.data.db.ConversionDao
import com.vibe.player.data.db.ConversionJob
import com.vibe.player.data.db.ConversionStatus
import com.vibe.player.data.db.TrackSource
import com.vibe.player.downloads.ConflictPolicy
import com.vibe.player.downloads.OutputStore
import com.vibe.player.metadata.AudioFormats
import com.vibe.player.metadata.MetadataExtractor
import com.vibe.player.settings.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume

enum class TargetFormat(val ext: String, val label: String, val mime: String, val lossless: Boolean) {
    MP3("mp3", "MP3", "audio/mpeg", false),
    WAV("wav", "WAV", "audio/wav", true),
    FLAC("flac", "FLAC", "audio/flac", true),
}

data class EncoderSupport(val checked: Boolean = false, val mp3: Boolean = false, val wav: Boolean = false, val flac: Boolean = false, val ffmpegVersion: String? = null)

/** FFmpegKit-backed converter. Runs jobs sequentially off the main thread and reports progress via Room. */
class AudioConverter(
    private val context: Context,
    private val dao: ConversionDao,
    private val outputStore: OutputStore,
    private val library: LibraryRepository,
    private val settings: SettingsRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val queueLock = Mutex()
    private val activeSessions = HashMap<Long, Long>() // jobId -> ffmpeg sessionId

    private val _encoders = MutableStateFlow(EncoderSupport())
    val encoders: StateFlow<EncoderSupport> = _encoders.asStateFlow()

    val history: Flow<List<ConversionJob>> = dao.all()

    init {
        scope.launch {
            // Anything left RUNNING from a previous process is dead.
            dao.failStaleRunning(System.currentTimeMillis())
            runCatching { probeEncoders() }
        }
    }

    /** Asks the bundled FFmpeg which encoders it actually has, instead of assuming. */
    suspend fun probeEncoders(): EncoderSupport = withContext(Dispatchers.IO) {
        val session = FFmpegKit.execute("-hide_banner -encoders")
        val out = session.allLogsAsString
        val version = runCatching { FFmpegKitConfig.getFFmpegVersion() }.getOrNull()
        EncoderSupport(
            checked = true,
            mp3 = Regex("""\bA\S*\s+libmp3lame\b""").containsMatchIn(out),
            wav = Regex("""\bA\S*\s+pcm_s16le\b""").containsMatchIn(out),
            flac = Regex("""\bA\S*\s+flac\b""").containsMatchIn(out),
            ffmpegVersion = version,
        ).also {
            _encoders.value = it
            android.util.Log.i("VibeConvert", "FFmpeg $version encoders: libmp3lame=${it.mp3} pcm_s16le=${it.wav} flac=${it.flac}")
        }
    }

    fun outputNameFor(sourceName: String, format: TargetFormat) = "${sourceName.substringBeforeLast('.')}.${format.ext}"

    suspend fun outputExists(sourceName: String, format: TargetFormat) = outputStore.exists(OutputStore.sanitize(outputNameFor(sourceName, format)))

    suspend fun enqueue(sourceUri: Uri, format: TargetFormat, bitrateKbps: Int, deleteSource: Boolean, policy: ConflictPolicy): Long {
        val name = MetadataExtractor.displayName(context, sourceUri) ?: "audio"
        val mime = context.contentResolver.getType(sourceUri)
        val job = ConversionJob(
            sourceUri = sourceUri.toString(), sourceName = name, sourceFormat = AudioFormats.detect(name, mime),
            targetFormat = format.name, bitrateKbps = if (format == TargetFormat.MP3) bitrateKbps else 0,
            status = ConversionStatus.QUEUED, deleteSource = deleteSource, createdAt = System.currentTimeMillis(),
        )
        val id = dao.insert(job)
        scope.launch { drain(policy) }
        return id
    }

    fun cancel(jobId: Long) {
        scope.launch {
            val sessionId = synchronized(activeSessions) { activeSessions[jobId] }
            if (sessionId != null) FFmpegKit.cancel(sessionId)
            dao.get(jobId)?.let { if (it.status == ConversionStatus.QUEUED) dao.update(it.copy(status = ConversionStatus.CANCELLED, finishedAt = System.currentTimeMillis())) }
        }
    }

    suspend fun remove(jobId: Long) { cancel(jobId); dao.delete(jobId) }
    suspend fun clearFinished() = dao.clearFinished()

    private suspend fun drain(policy: ConflictPolicy) = queueLock.withLock {
        while (true) {
            val next = dao.nextQueued() ?: break
            run(next, policy)
        }
    }

    private suspend fun run(job: ConversionJob, policy: ConflictPolicy) {
        val format = TargetFormat.valueOf(job.targetFormat)
        val srcUri = job.sourceUri.toUri()
        dao.update(job.copy(status = ConversionStatus.RUNNING, progress = 0f, error = null))

        val durationMs = MetadataExtractor.extract(context, srcUri)?.durationMs ?: 0L
        val input = if (srcUri.scheme == "content") FFmpegKitConfig.getSafParameterForRead(context, srcUri) else srcUri.path
        val workDir = File(context.cacheDir, "convert").apply { mkdirs() }
        val outFile = File(workDir, "${job.id}.${format.ext}")

        val codec = when (format) {
            TargetFormat.MP3 -> "-c:a libmp3lame -b:a ${job.bitrateKbps.takeIf { it > 0 } ?: 320}k"
            TargetFormat.WAV -> "-c:a pcm_s16le"
            TargetFormat.FLAC -> "-c:a flac -compression_level 5"
        }
        // Video sources (MP4/MKV/WebM…): take the first audio track only — the picture stream must not be copied into an
        // audio file. Audio sources: -map_metadata 0 keeps tags; WAV can't carry a cover so -vn; MP3/FLAC keep cover via -c:v copy.
        val fromVideo = AudioFormats.isVideo(job.sourceName, runCatching { context.contentResolver.getType(srcUri) }.getOrNull())
        val coverArgs = when {
            fromVideo -> "-vn -sn -dn -map 0:a:0"
            format == TargetFormat.WAV -> "-vn"
            else -> "-map 0:a -map 0:v? -c:v copy -disposition:v attached_pic"
        }
        val cmd = "-y -hide_banner -i \"$input\" $coverArgs -map_metadata 0 $codec \"${outFile.absolutePath}\""

        val session: FFmpegSession = suspendCancellableCoroutine { cont ->
            val s = FFmpegKit.executeAsync(
                cmd,
                { completed -> if (cont.isActive) cont.resume(completed) },
                null,
                { stats ->
                    if (durationMs > 0) {
                        val pct = (stats.time.toFloat() / durationMs * 100f).coerceIn(0f, 99.5f)
                        scope.launch { dao.progress(job.id, pct) }
                    }
                },
            )
            synchronized(activeSessions) { activeSessions[job.id] = s.sessionId }
            cont.invokeOnCancellation { FFmpegKit.cancel(s.sessionId) }
        }
        synchronized(activeSessions) { activeSessions.remove(job.id) }

        val now = System.currentTimeMillis()
        android.util.Log.i("VibeConvert", "job ${job.id} ${job.sourceFormat}->${format.label} rc=${session.returnCode} out=${outFile.length()}B")
        when {
            ReturnCode.isCancel(session.returnCode) -> {
                outFile.delete()
                dao.update(job.copy(status = ConversionStatus.CANCELLED, finishedAt = now))
            }
            !ReturnCode.isSuccess(session.returnCode) -> {
                outFile.delete()
                val log = session.allLogsAsString.lines().filter { it.isNotBlank() }
                val reason = log.lastOrNull { l ->
                    val x = l.lowercase()
                    "error" in x || "invalid" in x || "unknown encoder" in x || "matches no streams" in x || "does not contain any stream" in x
                } ?: log.lastOrNull() ?: "FFmpeg returned ${session.returnCode}"
                dao.update(job.copy(status = ConversionStatus.FAILED, error = friendly(reason), finishedAt = now))
            }
            else -> try {
                val saved = outputStore.save(outFile, outputNameFor(job.sourceName, format), format.mime, policy, source = TrackSource.IMPORTED)
                var deleteNote: String? = null
                if (job.deleteSource) deleteNote = deleteSource(srcUri)
                dao.update(
                    job.copy(
                        status = ConversionStatus.DONE, progress = 100f, outputUri = saved.uri.toString(), outputName = saved.displayName,
                        outputBytes = saved.bytes, error = deleteNote, finishedAt = now,
                    ),
                )
            } catch (e: Exception) {
                dao.update(job.copy(status = ConversionStatus.FAILED, error = e.message ?: "Couldn't save output", finishedAt = now))
            } finally {
                outFile.delete()
            }
        }
    }

    /** Returns a note when deletion wasn't possible (e.g. MediaStore items owned by other apps on Android 11+). */
    private fun deleteSource(uri: Uri): String? = try {
        val ok = if (DocumentsContract.isDocumentUri(context, uri)) DocumentsContract.deleteDocument(context.contentResolver, uri)
        else context.contentResolver.delete(uri, null, null) > 0
        if (ok) { scope.launch { library.rescan() }; null } else "Converted, but the original couldn't be deleted."
    } catch (e: SecurityException) {
        "Converted, but Android didn't allow deleting the original (owned by another app)."
    } catch (e: Exception) {
        "Converted, but deleting the original failed: ${e.message}"
    }

    private fun friendly(raw: String): String = when {
        "unknown encoder" in raw.lowercase() -> "This FFmpeg build has no encoder for the chosen format."
        "matches no streams" in raw.lowercase() || "does not contain any stream" in raw.lowercase() -> "This file has no audio track to extract."
        "invalid data found" in raw.lowercase() -> "The source file isn't a readable audio or video file."
        "no such file" in raw.lowercase() || "permission denied" in raw.lowercase() -> "Source file is missing or not accessible anymore."
        else -> raw.take(240)
    }

    companion object {
        val MP3_BITRATES = listOf(128, 192, 256, 320)
        const val QUALITY_NOTE = "Converting a lossy file (MP3, AAC, OGG…) to 320 kbps or FLAC does not restore quality that was already lost. " +
            "FLAC made from MP3 is just a bigger copy of the same MP3 audio."
    }
}
