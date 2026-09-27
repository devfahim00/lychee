package com.devfahim00.lychee.service

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.FileProvider
import com.devfahim00.lychee.LycheeApp
import com.devfahim00.lychee.MainActivity
import com.devfahim00.lychee.R
import com.devfahim00.lychee.core.DownloadEngine
import com.devfahim00.lychee.core.DownloadStatus
import com.devfahim00.lychee.core.DownloadTask
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Foreground service that keeps the app alive while downloads are running
 * and maintains the progress notification.
 */
class DownloadService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startAsForeground()
        scope.launch {
            DownloadEngine.tasks.collect { tasks ->
                val active = tasks.filter { it.status == DownloadStatus.ACTIVE || it.status == DownloadStatus.QUEUED }
                if (active.isEmpty()) {
                    val finished = tasks.filter {
                        it.finishedAt > 0 && System.currentTimeMillis() - it.finishedAt < 60_000
                    }
                    if (finished.isNotEmpty()) {
                        notifyFinished(finished)
                    }
                    stopSelf()
                    return@collect
                }
                updateProgressNotification(active)
            }
        }
        return START_NOT_STICKY
    }

    private fun startAsForeground() {
        val notification = progressNotification(
            "Preparing download…",
            "Lychee is starting your download",
            -1f
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIF_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(NOTIF_ID, notification)
        }
    }

    private fun contentIntent(): PendingIntent {
        val intent = Intent(this, MainActivity::class.java)
        return PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun progressNotification(title: String, text: String, progress: Float): Notification {
        val builder = NotificationCompat.Builder(this, LycheeApp.DOWNLOAD_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setContentIntent(contentIntent())
        if (progress >= 0) {
            builder.setProgress(100, progress.toInt().coerceIn(0, 100), false)
        } else {
            builder.setProgress(0, 0, true)
        }
        return builder.build()
    }

    private fun updateProgressNotification(active: List<DownloadTask>) {
        val current = active.firstOrNull { it.status == DownloadStatus.ACTIVE } ?: active.first()
        val title = current.title.ifBlank { getString(R.string.downloading_notification) }
        val speed = formatSpeed(current.speedBps)
        val text = when {
            current.stage.isNotBlank() -> current.stage
            else -> getString(R.string.downloading_notification)
        } + if (speed != null) " • $speed" else ""
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIF_ID, progressNotification(title, text, current.progress))
    }

    private fun notifyFinished(finished: List<DownloadTask>) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val completed = finished.filter { it.status == DownloadStatus.COMPLETED }
        val failed = finished.filter { it.status == DownloadStatus.ERROR }
        if (completed.isNotEmpty()) {
            val t = completed.last()
            val builder = NotificationCompat.Builder(this, LycheeApp.DOWNLOAD_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(getString(R.string.download_complete))
                .setContentText(t.title)
                .setAutoCancel(true)
                .setContentIntent(contentIntent())
            t.filePath?.let { path ->
                try {
                    val file = File(path)
                    if (file.exists()) {
                        val uri: Uri = FileProvider.getUriForFile(
                            this, "$packageName.fileprovider", file
                        )
                        val mime = mimeFor(file.extension)
                        val viewIntent = Intent(Intent.ACTION_VIEW).apply {
                            setDataAndType(uri, mime)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        val pi = PendingIntent.getActivity(
                            this, 1, viewIntent,
                            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                        )
                        builder.setContentIntent(pi)
                    }
                } catch (_: Exception) {
                }
            }
            manager.notify(FINISH_NOTIF_ID, builder.build())
        }
        if (failed.isNotEmpty()) {
            val t = failed.last()
            val builder = NotificationCompat.Builder(this, LycheeApp.DOWNLOAD_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(getString(R.string.download_failed))
                .setContentText(t.error ?: t.title)
                .setAutoCancel(true)
                .setContentIntent(contentIntent())
            manager.notify(FINISH_NOTIF_ID + 1, builder.build())
        }
    }

    private fun formatSpeed(bps: Long): String? {
        if (bps <= 0) return null
        return when {
            bps > 1_000_000 -> String.format("%.1f MB/s", bps / 1_000_000.0)
            bps > 1_000 -> String.format("%.0f KB/s", bps / 1_000.0)
            else -> "$bps B/s"
        }
    }

    private fun mimeFor(ext: String): String = when (ext.lowercase()) {
        "mp4", "m4v" -> "video/mp4"
        "webm" -> "video/webm"
        "mkv" -> "video/x-matroska"
        "flv" -> "video/x-flv"
        "mp3" -> "audio/mpeg"
        "m4a" -> "audio/mp4"
        "opus" -> "audio/opus"
        "ogg" -> "audio/ogg"
        "flac" -> "audio/flac"
        "wav" -> "audio/wav"
        "jpg", "jpeg" -> "image/jpeg"
        "png" -> "image/png"
        "webp" -> "image/webp"
        else -> "*/*"
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val NOTIF_ID = 1001
        private const val FINISH_NOTIF_ID = 1002

        fun start(context: Context) {
            val intent = Intent(context, DownloadService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }
}
