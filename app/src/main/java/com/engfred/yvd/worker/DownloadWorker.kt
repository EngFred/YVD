package com.engfred.yvd.worker

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.FileProvider
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.engfred.yvd.R
import com.engfred.yvd.data.repository.DownloadStatus
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

    private val TAG = "YVD_WORKER"
    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    override suspend fun doWork(): Result {
        val url = inputData.getString("url")
        val formatId = inputData.getString("formatId")
        val title = inputData.getString("title") ?: "Video"

        if (url == null || formatId == null) {
            return Result.failure()
        }

        // Base ID for this job
        val notificationId = System.currentTimeMillis().toInt()

        try {
            // 1. Start Foreground (Progress Notification) using Base ID
            setForeground(createForegroundInfo(notificationId, title, 0, true))

            var resultFile: File? = null

            repository.downloadVideo(url, formatId, title).collectLatest { status ->
                when (status) {
                    is DownloadStatus.Progress -> {
                        if (status.progress > 0) {
                            // Update Base ID
                            setForeground(createForegroundInfo(notificationId, title, status.progress.toInt(), false))
                        }
                        setProgress(workDataOf("progress" to status.progress, "status" to status.text))
                    }
                    is DownloadStatus.Success -> {
                        resultFile = status.file
                    }
                    is DownloadStatus.Error -> {
                        throw Exception(status.message)
                    }
                }
            }

            return if (resultFile != null) {
                // 2. SUCCESS: Use (Base ID + 1) so it survives
                showCompletionNotification(notificationId + 1, title, resultFile!!)
                Result.success(workDataOf("filePath" to resultFile!!.absolutePath))
            } else {
                throw Exception("File verification failed")
            }

        } catch (e: Exception) {
            Log.e(TAG, "❌ Download Failed", e)

            // 3. FAILURE: Use (Base ID + 1) so it survives
            // We use the same +1 slot as success because a job can't succeed AND fail
            showFailureNotification(notificationId + 1, title, e.message ?: "Unknown Error")

            return Result.failure(workDataOf("error" to e.message))
        }
    }

    private fun createForegroundInfo(id: Int, title: String, progress: Int, indeterminate: Boolean): ForegroundInfo {
        val intent = Intent(context, com.engfred.yvd.MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE)

        val notification = NotificationCompat.Builder(context, "download_channel")
            .setContentTitle("Downloading: $title")
            .setContentText("$progress%")
            .setSmallIcon(R.mipmap.ic_launcher_round)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(100, progress, indeterminate)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return ForegroundInfo(id, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        }
        return ForegroundInfo(id, notification)
    }

    private fun showCompletionNotification(id: Int, title: String, file: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)

        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "video/*")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }

        val pendingIntent = PendingIntent.getActivity(
            context, id, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_CANCEL_CURRENT
        )

        val notification = NotificationCompat.Builder(context, "download_completed")
            .setContentTitle("Download Complete")
            .setContentText(title)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(id, notification)
    }

    private fun showFailureNotification(id: Int, title: String, error: String) {
        // Intent opens the App (MainActivity) to retry
        val intent = Intent(context, com.engfred.yvd.MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }

        val pendingIntent = PendingIntent.getActivity(
            context, id, intent, PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, "download_completed") // Use High Importance Channel
            .setContentTitle("Download Failed")
            .setContentText("Tap to retry: $title")
            .setStyle(NotificationCompat.BigTextStyle().bigText("Error: $error")) // Expandable text for long errors
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH) // Make it pop up
            .setAutoCancel(true)
            .build()

        notificationManager.notify(id, notification)
    }
}