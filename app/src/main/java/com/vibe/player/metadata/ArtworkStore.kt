package com.vibe.player.metadata

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import java.io.File

/**
 * User-chosen cover art, stored as JPEGs in the app's private files dir. The audio files are never touched.
 * Lookup is by deterministic file name so the Coil fetcher needs no database access.
 */
class ArtworkStore(private val context: Context) {

    private val dir get() = File(context.filesDir, "artwork").apply { mkdirs() }

    /** Bumped whenever art changes so Coil's memory cache keys roll over. */
    val version: StateFlow<Int> get() = _version
    private val _version = MutableStateFlow(0)

    private val noEmbeddedArt = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
    fun knownNoEmbeddedArt(trackUri: String) = trackUri in noEmbeddedArt
    fun rememberNoEmbeddedArt(trackUri: String) { noEmbeddedArt += trackUri }

    fun trackFile(trackUri: String): File = File(dir, "t_${stableHash(trackUri)}.jpg")
    fun albumFile(albumKey: String): File = File(dir, "a_${stableHash(albumKey)}.jpg")

    fun hasTrackArt(trackUri: String) = trackFile(trackUri).exists()
    fun hasAlbumArt(albumKey: String) = albumFile(albumKey).exists()

    suspend fun setTrackArt(trackUri: String, image: Uri) = write(image, trackFile(trackUri))
    suspend fun setAlbumArt(albumKey: String, image: Uri) = write(image, albumFile(albumKey))

    /** Used by backup restore when a track's URI changes on the new device. */
    fun renameTrackArt(oldUri: String, newUri: String) { trackFile(oldUri).takeIf { it.exists() }?.renameTo(trackFile(newUri)) }
    fun artworkDir(): File = dir
    fun removeTrackArt(trackUri: String) { trackFile(trackUri).delete(); bump() }
    fun removeAlbumArt(albumKey: String) { albumFile(albumKey).delete(); bump() }

    private suspend fun write(image: Uri, target: File): Boolean = withContext(Dispatchers.IO) {
        val bitmap = decodeScaled(image, 1024) ?: return@withContext false
        target.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        bitmap.recycle()
        bump()
        true
    }

    private fun decodeScaled(uri: Uri, maxSide: Int): Bitmap? {
        // Bounds-only decode always returns null; only a missing stream or empty bounds is an error.
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        val probe = context.contentResolver.openInputStream(uri) ?: return null
        probe.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (bounds.outWidth / sample > maxSide * 2 || bounds.outHeight / sample > maxSide * 2) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        val raw = context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) } ?: return null
        val scale = maxSide.toFloat() / maxOf(raw.width, raw.height)
        return if (scale < 1f) Bitmap.createScaledBitmap(raw, (raw.width * scale).toInt(), (raw.height * scale).toInt(), true).also { raw.recycle() } else raw
    }

    private fun bump() { _version.value = _version.value + 1 }

    fun stableHash(s: String): String {
        var h = 1125899906842597L
        for (ch in s) h = 31 * h + ch.code
        return java.lang.Long.toHexString(h)
    }
}
