package com.engfred.yvd.data.repository

import android.os.Environment
import java.io.File
import javax.inject.Inject

class DownloadsRepository @Inject constructor() {

    fun getDownloadedFiles(): List<File> {
        val downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val appDir = File(downloadDir, "YVDownloader")

        if (!appDir.exists()) return emptyList()

        return appDir.listFiles()
            ?.filter {
                it.isFile &&
                        (it.extension == "mp4" || it.extension == "m4a" || it.extension == "webm") &&
                        !it.name.startsWith("temp_") // Exclude temporary muxing files
            }
            ?.sortedByDescending { it.lastModified() } // Show newest first
            ?: emptyList()
    }
}