package com.vibe.player.downloads

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.vibe.player.MainActivity
import com.vibe.player.R
import com.vibe.player.container

/** Drains the PENDING download queue one item at a time while showing a progress notification. */
class DownloadWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    private val repo get() = applicationContext.container.downloads

    override suspend fun doWork(): Result {
        ensureChannel()
        runCatching { setForeground(foregroundInfo("Preparing downloads…", 0)) }
        repo.markStaleRunningAsPending()
        runCatching { repo.ytdlp.ensureInitialized() }.onFailure { return Result.failure() }
        runCatching { setForegroundAsync(foregroundInfo("Checking for yt-dlp updates…", 0)) }
        repo.ytdlp.updateIfStale()
        while (true) {
            val next = repo.nextPending() ?: break
            repo.process(next) { title, pct -> runCatching { setForegroundAsync(foregroundInfo(title, pct)) } }
        }
        return Result.success()
    }

    override suspend fun getForegroundInfo(): ForegroundInfo = foregroundInfo("Downloading…", 0)

    private fun foregroundInfo(title: String, pct: Int): ForegroundInfo {
        val tap = PendingIntent.getActivity(
            applicationContext, 1, Intent(applicationContext, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Downloading")
            .setContentText(title)
            .setProgress(100, pct, pct <= 0)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(tap)
            .build()
        return if (Build.VERSION.SDK_INT >= 29) ForegroundInfo(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        else ForegroundInfo(NOTIFICATION_ID, notification)
    }

    private fun ensureChannel() {
        val nm = applicationContext.getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL) == null) {
            nm.createNotificationChannel(NotificationChannel(CHANNEL, "Downloads", NotificationManager.IMPORTANCE_LOW).apply { setShowBadge(false) })
        }
    }

    companion object {
        const val UNIQUE_NAME = "vibe-downloads"
        private const val CHANNEL = "downloads"
        private const val NOTIFICATION_ID = 4101
    }
}
