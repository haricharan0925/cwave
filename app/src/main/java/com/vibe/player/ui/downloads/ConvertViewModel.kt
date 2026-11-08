package com.vibe.player.ui.downloads

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vibe.player.AppContainer
import com.vibe.player.convert.TargetFormat
import com.vibe.player.downloads.ConflictPolicy
import com.vibe.player.metadata.AudioFormats
import com.vibe.player.metadata.MetadataExtractor
import kotlinx.coroutines.launch

/** Holds the conversion form so it survives tab switches, navigation and rotation. */
class ConvertViewModel(private val container: AppContainer) : ViewModel() {
    private val converter = container.converter

    var source by mutableStateOf<Uri?>(null); private set
    var sourceName by mutableStateOf(""); private set
    var sourceFormat by mutableStateOf(""); private set
    var isVideo by mutableStateOf(false); private set
    var formatIndex by mutableIntStateOf(0)
    var bitrate by mutableIntStateOf(0) // 0 = not chosen yet → falls back to the settings default
    var deleteSource by mutableStateOf(false)
    var pendingConflict by mutableStateOf(false)
    var destination by mutableStateOf("Music/Cwave"); private set

    val target: TargetFormat get() = TargetFormat.entries[formatIndex]

    init {
        viewModelScope.launch { container.settings.settings.collect { s -> if (bitrate == 0) bitrate = s.mp3Bitrate; destination = container.outputStore.destinationLabel() } }
    }

    fun pick(context: Context, uri: Uri) {
        runCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        source = uri
        sourceName = MetadataExtractor.displayName(context, uri) ?: "audio"
        val mime = context.contentResolver.getType(uri)
        sourceFormat = AudioFormats.detect(sourceName, mime)
        isVideo = AudioFormats.isVideo(sourceName, mime)
    }

    fun outputName() = converter.outputNameFor(sourceName, target)

    /** Returns true if the caller must ask about an existing file first. */
    suspend fun requestConvert(): Boolean {
        if (source == null) return false
        if (converter.outputExists(sourceName, target)) { pendingConflict = true; return true }
        start(ConflictPolicy.KEEP_BOTH)
        return false
    }

    fun start(policy: ConflictPolicy) {
        val uri = source ?: return
        pendingConflict = false
        viewModelScope.launch { converter.enqueue(uri, target, bitrate, deleteSource, policy) }
    }
}
