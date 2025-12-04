package com.engfred.yvd

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.engfred.yvd.data.network.DownloaderImpl
import dagger.hilt.android.HiltAndroidApp
import org.schabi.newpipe.extractor.NewPipe
import javax.inject.Inject

@HiltAndroidApp
class YVDApplication : Application(), Configuration.Provider {

    private val TAG = "YVD_APP"

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "YVD APPLICATION STARTING")

        initEngines()
        createNotificationChannel()
    }

    private fun initEngines() {
        try {
            // NewPipe initialization is lightweight and instant.
            // We provide it with our OkHttp implementation.
            NewPipe.init(DownloaderImpl())
            Log.d(TAG, "NewPipe Extractor Initialized")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize engines", e)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager: NotificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            // 1. Progress Channel
            val name = "Download Progress"
            val descriptionText = "Shows progress of video downloads"
            val importance = NotificationManager.IMPORTANCE_LOW
            val channel = NotificationChannel("download_channel", name, importance).apply {
                description = descriptionText
            }

            // 2. Completion Channel
            val completeName = "Download Completed"
            val completeChannel = NotificationChannel("download_completed", completeName, NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Notifications when download finishes"
                enableVibration(true)
            }

            notificationManager.createNotificationChannel(channel)
            notificationManager.createNotificationChannel(completeChannel)
        }
    }
}