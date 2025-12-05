package com.engfred.yvd.data.repository

import android.os.Environment
import com.engfred.yvd.domain.model.DownloadItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

class DownloadsRepository @Inject constructor() {

    /**
     * Fetches files on the IO thread.
     * Pre-calculates file metadata to prevent UI stuttering during scroll.
     */
    suspend fun getDownloadedFiles(): List<DownloadItem> = withContext(Dispatchers.IO) {
        val downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val appDir = File(downloadDir, "YVDownloader")

        if (!appDir.exists()) return@withContext emptyList()

        // 1. Get raw files
        val rawFiles = appDir.listFiles() ?: return@withContext emptyList()

        // 2. Filter, Sort, and Map to lightweight UI model
        return@withContext rawFiles
            .filter {
                it.isFile &&
                        (it.extension == "mp4" || it.extension == "m4a" || it.extension == "webm") &&
                        !it.name.startsWith("temp_")
            }
            .map { file ->
                val sizeMb = if (file.length() > 0) file.length() / (1024 * 1024) else 0
                val isAudio = file.extension == "m4a"

                DownloadItem(
                    file = file,
                    fileName = file.name,
                    sizeLabel = "$sizeMb MB",
                    isAudio = isAudio,
                    lastModified = file.lastModified()
                )
            }
            .sortedByDescending { it.lastModified }
    }
}