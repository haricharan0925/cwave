package com.vibe.player.downloads

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLDecoder
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.coroutineContext

/** Plain HTTP(S) links that point straight at an audio file. Keeps the original format. */
class DirectUrlAdapter : SourceAdapter {

    override val id = "direct"
    override val displayName = "Direct audio links (.mp3, .flac, .wav, .m4a, .ogg, .opus)"

    private val audioExt = setOf("mp3", "flac", "wav", "m4a", "aac", "ogg", "oga", "opus", "wma", "aiff", "aif", "webm", "mka")
    private val cancelled = ConcurrentHashMap.newKeySet<String>()

    override fun canHandle(url: String): Boolean {
        val path = runCatching { URL(url).path }.getOrDefault("").lowercase()
        return path.substringAfterLast('.', "") in audioExt
    }

    /** Also accepts URLs whose server reports an audio Content-Type even without an extension. */
    suspend fun probe(url: String): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val c = (URL(url).openConnection() as HttpURLConnection).apply { requestMethod = "HEAD"; connectTimeout = 10_000; readTimeout = 10_000; instanceFollowRedirects = true }
            val ok = c.responseCode in 200..299 && (c.contentType?.startsWith("audio/") == true)
            c.disconnect(); ok
        }.getOrDefault(false)
    }

    override suspend fun resolve(url: String, processId: String?): List<ResolvedEntry> = withContext(Dispatchers.IO) {
        var title = fileNameFrom(url)
        runCatching {
            val c = (URL(url).openConnection() as HttpURLConnection).apply { requestMethod = "HEAD"; connectTimeout = 10_000; readTimeout = 10_000 }
            c.getHeaderField("Content-Disposition")?.let { cd -> parseDispositionName(cd)?.let { title = it } }
            c.disconnect()
        }
        listOf(ResolvedEntry(url, title.substringBeforeLast('.'), UrlValidator.host(url), null, null))
    }

    override suspend fun download(
        url: String, audioFormat: String, bitrateKbps: Int, workDir: File, processId: String,
        onProgress: (Float, Long?, String?) -> Unit,
    ): DownloadOutput = withContext(Dispatchers.IO) {
        workDir.mkdirs()
        cancelled.remove(processId)
        val conn = (URL(url).openConnection() as HttpURLConnection).apply { connectTimeout = 15_000; readTimeout = 30_000; instanceFollowRedirects = true }
        try {
            if (conn.responseCode !in 200..299) throw IllegalStateException("Server replied HTTP ${conn.responseCode}")
            val name = conn.getHeaderField("Content-Disposition")?.let { parseDispositionName(it) } ?: fileNameFrom(url)
            val mime = conn.contentType?.substringBefore(';')?.takeIf { it.startsWith("audio/") } ?: OutputStore.mimeFor(name.substringAfterLast('.', ""))
            val total = conn.contentLengthLong
            val out = File(workDir, OutputStore.sanitize(name))
            val start = System.currentTimeMillis()
            conn.inputStream.use { input ->
                out.outputStream().use { output ->
                    val buf = ByteArray(64 * 1024)
                    var read: Int
                    var done = 0L
                    var lastReport = 0L
                    while (input.read(buf).also { read = it } >= 0) {
                        coroutineContext.ensureActive()
                        if (processId in cancelled) throw InterruptedException("Cancelled")
                        output.write(buf, 0, read)
                        done += read
                        val now = System.currentTimeMillis()
                        if (now - lastReport > 400) {
                            lastReport = now
                            val pct = if (total > 0) done * 100f / total else -1f
                            val elapsed = (now - start) / 1000.0
                            val eta = if (total > 0 && done > 0) ((total - done) * elapsed / done).toLong() else null
                            onProgress(pct, eta, "${done / 1024} KiB" + if (total > 0) " of ${total / 1024} KiB" else "")
                        }
                    }
                }
            }
            DownloadOutput(out, out.name, mime)
        } finally {
            conn.disconnect()
        }
    }

    override fun cancel(processId: String) { cancelled.add(processId) }

    private fun fileNameFrom(url: String): String {
        val raw = runCatching { URL(url).path.substringAfterLast('/') }.getOrDefault("")
        val decoded = runCatching { URLDecoder.decode(raw, "UTF-8") }.getOrDefault(raw)
        return decoded.ifBlank { "audio.mp3" }
    }

    private fun parseDispositionName(cd: String): String? {
        Regex("filename\\*=UTF-8''([^;]+)", RegexOption.IGNORE_CASE).find(cd)?.groupValues?.get(1)?.let { return runCatching { URLDecoder.decode(it, "UTF-8") }.getOrNull() }
        Regex("filename=\"?([^\";]+)\"?", RegexOption.IGNORE_CASE).find(cd)?.groupValues?.get(1)?.let { return it.trim() }
        return null
    }
}
