package com.vibe.player

import android.content.Context
import com.vibe.player.convert.AudioConverter
import com.vibe.player.data.LibraryRepository
import com.vibe.player.data.db.VibeDatabase
import com.vibe.player.downloads.DownloadRepository
import com.vibe.player.downloads.OutputStore
import com.vibe.player.downloads.YtDlpManager
import com.vibe.player.library.FileImporter
import com.vibe.player.library.MediaStoreScanner
import com.vibe.player.player.PlayerController
import com.vibe.player.player.QueueRestorer
import com.vibe.player.settings.SettingsRepository

/** Hand-rolled dependency graph. Everything here is a process-wide singleton. */
class AppContainer(context: Context) {
    val appContext: Context = context.applicationContext
    val db: VibeDatabase = VibeDatabase.create(appContext)
    val settings = SettingsRepository(appContext)
    /** Audio URI handed to us by another app ("Open with Cwave"); consumed by the UI, then reset to null. */
    val externalOpen = kotlinx.coroutines.flow.MutableStateFlow<android.net.Uri?>(null)
    val artworkStore = com.vibe.player.metadata.ArtworkStore(appContext)
    val library = LibraryRepository(db, MediaStoreScanner(appContext, db.tracks()), FileImporter(appContext, db.tracks()), settings)
    val queueRestorer = QueueRestorer(library, settings)
    val player = PlayerController(appContext, queueRestorer)
    val outputStore = OutputStore(appContext, settings, library)
    val ytdlp = YtDlpManager(appContext, settings)
    val youTubeAuth = com.vibe.player.downloads.YouTubeAuth(appContext)
    val downloads = DownloadRepository(appContext, db.downloads(), ytdlp, outputStore, library, settings, youTubeAuth)
    val converter = AudioConverter(appContext, db.conversions(), outputStore, library, settings)
    val deleter = com.vibe.player.library.TrackDeleter(appContext, db.tracks())
    val trimmer = com.vibe.player.convert.AudioTrimmer(appContext, outputStore, db.tracks())
    val backup = com.vibe.player.backup.BackupManager(appContext, db, artworkStore)
}

val Context.container: AppContainer get() = (applicationContext as VibeApp).container
