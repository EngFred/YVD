package com.engfred.yvd.worker

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.MediaScannerConnection
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.FileProvider
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.engfred.yvd.domain.model.DownloadStatus
import com.engfred.yvd.domain.repository.YoutubeRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.collectLatest
import java.io.File

@HiltWorker
class DownloadWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted workerParams: WorkerParameters,
    private val repository: YoutubeRepository
) : CoroutineWorker(context, workerParams) {

    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    override suspend fun doWork(): Result {
        val url = inputData.getString("url")
        val formatId = inputData.getString("formatId")
        val title = inputData.getString("title") ?: "Media"
        val isAudio = inputData.getBoolean("isAudio", false)

        if (url == null || formatId == null) return Result.failure()

        val notificationId = System.currentTimeMillis().toInt()
        val typeLabel = if (isAudio) "Audio" else "Video"

        try {
            // Initial Notification
            setForeground(createForegroundInfo(notificationId, title, 0, true, typeLabel))

            var resultFile: File? = null

            repository.downloadVideo(url, formatId, title, isAudio).collectLatest { status ->
                when (status) {
                    is DownloadStatus.Progress -> {
                        // FIX 1: Send Progress to ViewModel
                        setProgress(
                            workDataOf(
                                "progress" to status.progress,
                                "status" to status.text
                            )
                        )

                        // Update Notification
                        if (status.progress > 0) {
                            setForeground(createForegroundInfo(notificationId, title, status.progress.toInt(), false, typeLabel))
                        }
                    }
                    is DownloadStatus.Success -> resultFile = status.file
                    is DownloadStatus.Error -> throw Exception(status.message)
                }
            }

            return if (resultFile != null && resultFile!!.exists()) {
                // FIX 2: Trigger Media Scanner so Gallery/Music apps see the file
                MediaScannerConnection.scanFile(
                    context,
                    arrayOf(resultFile!!.absolutePath),
                    null // MimeType null lets Android detect it automatically
                ) { _, uri ->
                    // Log.d("MediaScanner", "Scanned $path:")
                }

                showCompletionNotification(notificationId + 1, title, resultFile!!, isAudio)
                Result.success(workDataOf("filePath" to resultFile!!.absolutePath))
            } else {
                throw Exception("File verification failed")
            }
        } catch (e: Exception) {
            e.printStackTrace()
            showFailureNotification(notificationId + 1, title, e.message ?: "Error")
            return Result.failure(workDataOf("error" to e.message))
        }
    }

    private fun createForegroundInfo(id: Int, title: String, progress: Int, indeterminate: Boolean, typeLabel: String): ForegroundInfo {
        // Create an Intent to launch the app when notification is clicked
        val intent = Intent(context, com.engfred.yvd.MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE)

        val notification = NotificationCompat.Builder(context, "download_channel")
            .setContentTitle("Downloading $typeLabel")
            .setContentText("$title ($progress%)")
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true) // Prevents sound/vibration on every progress update
            .setProgress(100, progress, indeterminate)
            .build()

        // Android 14 (API 34) requires specifying foreground service type
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return ForegroundInfo(id, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        }
        return ForegroundInfo(id, notification)
    }

    private fun showCompletionNotification(id: Int, title: String, file: File, isAudio: Boolean) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
        val mimeType = if (isAudio) "audio/*" else "video/*"

        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mimeType)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        val pendingIntent = PendingIntent.getActivity(context, id, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_CANCEL_CURRENT)

        val notification = NotificationCompat.Builder(context, "download_completed")
            .setContentTitle("Download Complete")
            .setContentText(title)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        notificationManager.notify(id, notification)
    }

    private fun showFailureNotification(id: Int, title: String, error: String) {
        val notification = NotificationCompat.Builder(context, "download_completed")
            .setContentTitle("Download Failed")
            .setContentText("Error: $error") // Show error detail
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setStyle(NotificationCompat.BigTextStyle().bigText("Error: $error"))
            .build()
        notificationManager.notify(id, notification)
    }
}