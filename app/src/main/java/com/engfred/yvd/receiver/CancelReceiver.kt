package com.engfred.yvd.receiver


import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.WorkManager

/**
 * Handles "Cancel" actions triggered from the Notification bar.
 */
class CancelReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == "CANCEL_DOWNLOAD") {
            // Cancel any work tagged with "download_job"
            WorkManager.getInstance(context).cancelAllWorkByTag("download_job")
        }
    }
}