package com.opdownloader.app.worker

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.opdownloader.app.MainActivity
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Handles persistent foreground download notifications and completion alerts.
 */
@Singleton
class DownloadNotificationManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        const val CHANNEL_DOWNLOAD_PROGRESS = "op_download_progress_channel"
        const val CHANNEL_DOWNLOAD_COMPLETE = "op_download_complete_channel"
        const val ACTION_PAUSE = "com.opdownloader.app.ACTION_PAUSE"
        const val ACTION_CANCEL = "com.opdownloader.app.ACTION_CANCEL"
        const val EXTRA_JOB_ID = "extra_job_id"
    }

    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    init {
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val progressChannel = NotificationChannel(
                CHANNEL_DOWNLOAD_PROGRESS,
                "Active Downloads",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows live download progress, speed, and time remaining."
                setShowBadge(false)
            }

            val completeChannel = NotificationChannel(
                CHANNEL_DOWNLOAD_COMPLETE,
                "Download Completed",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Notifications for successfully saved media files."
                setShowBadge(true)
            }

            notificationManager.createNotificationChannel(progressChannel)
            notificationManager.createNotificationChannel(completeChannel)
        }
    }

    /**
     * Builds ongoing foreground notification with progress bar, metrics, and actions
     */
    fun buildProgressNotification(
        jobId: String,
        filename: String,
        progressPct: Int,
        speedText: String,
        etaText: String,
        downloadedText: String,
        totalText: String,
        isPaused: Boolean = false
    ): Notification {
        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentPendingIntent = PendingIntent.getActivity(
            context,
            jobId.hashCode(),
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val pauseIntent = Intent(context, ResumableDownloadWorker::class.java).apply {
            action = ACTION_PAUSE
            putExtra(EXTRA_JOB_ID, jobId)
        }
        val pausePendingIntent = PendingIntent.getService(
            context,
            (jobId + "_pause").hashCode(),
            pauseIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val cancelIntent = Intent(context, ResumableDownloadWorker::class.java).apply {
            action = ACTION_CANCEL
            putExtra(EXTRA_JOB_ID, jobId)
        }
        val cancelPendingIntent = PendingIntent.getService(
            context,
            (jobId + "_cancel").hashCode(),
            cancelIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = if (isPaused) "Paused - $filename" else "OP Downloader • $filename"
        val subtitle = if (isPaused) "Download paused" else "$downloadedText / $totalText • $speedText"

        return NotificationCompat.Builder(context, CHANNEL_DOWNLOAD_PROGRESS)
            .setContentTitle(title)
            .setContentText(subtitle)
            .setSubText(if (isPaused) "Paused" else "$progressPct% • $etaText")
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(contentPendingIntent)
            .setProgress(100, progressPct, false)
            .addAction(
                android.R.drawable.ic_media_pause,
                if (isPaused) "Resume" else "Pause",
                pausePendingIntent
            )
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "Cancel",
                cancelPendingIntent
            )
            .build()
    }

    /**
     * Posts completion notification alerting that media was saved to Gallery
     */
    fun showCompletionNotification(jobId: String, filename: String) {
        val openAppIntent = Intent(context, MainActivity::class.java)
        val contentPendingIntent = PendingIntent.getActivity(
            context,
            jobId.hashCode(),
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_DOWNLOAD_COMPLETE)
            .setContentTitle("Download Complete")
            .setContentText("$filename saved to Gallery")
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setAutoCancel(true)
            .setContentIntent(contentPendingIntent)
            .build()

        notificationManager.notify(jobId.hashCode(), notification)
    }

    fun cancelNotification(jobId: String) {
        notificationManager.cancel(jobId.hashCode())
    }
}
