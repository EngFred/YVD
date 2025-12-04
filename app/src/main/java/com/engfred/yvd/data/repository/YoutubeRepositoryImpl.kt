package com.engfred.yvd.data.repository

import android.content.Context
import android.os.Environment
import android.util.Log
import com.engfred.yvd.common.Resource
import com.engfred.yvd.domain.model.VideoFormat
import com.engfred.yvd.domain.model.VideoMetadata
import com.engfred.yvd.domain.repository.YoutubeRepository
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import javax.inject.Inject

sealed class DownloadStatus {
    data class Progress(val progress: Float, val text: String) : DownloadStatus()
    data class Success(val file: File) : DownloadStatus()
    data class Error(val message: String) : DownloadStatus()
}

class YoutubeRepositoryImpl @Inject constructor(
    private val context: Context
) : YoutubeRepository {

    private val TAG = "YVD_REPO"

    // FIX: Static companion object to track initialization across the entire app lifecycle
    companion object {
        @Volatile
        private var isInitialized = false
        private val initMutex = Mutex()
    }

    private suspend fun ensureInitialized() {
        if (!isInitialized) {
            initMutex.withLock {
                if (!isInitialized) {
                    try {
                        Log.d(TAG, "🔧 Initializing engines (One-time setup)...")
                        YoutubeDL.getInstance().init(context)
                        FFmpeg.getInstance().init(context)
                        // Aria2c.getInstance().init(context) // Keep disabled as per your logic
                        isInitialized = true
                        Log.d(TAG, "Engines initialized successfully")
                    } catch (e: Exception) {
                        Log.e(TAG, "CRITICAL: Failed to initialize engines", e)
                        throw e
                    }
                }
            }
        }
    }

    override fun getVideoMetadata(url: String): Flow<Resource<VideoMetadata>> = flow {
        Log.d(TAG, "Fetching metadata for URL: $url")
        emit(Resource.Loading())
        try {
            ensureInitialized()

            val request = YoutubeDLRequest(url)
            request.addOption("--dump-json")

            Log.d(TAG, "Requesting video info...")
            val info = YoutubeDL.getInstance().getInfo(request)
            Log.d(TAG, "Video info received: ${info.title}")

            val rawFormats = info.formats ?: emptyList()
            Log.d(TAG, "Total formats available: ${rawFormats.size}")

            val validFormats = rawFormats
                .filter { it.vcodec != "none" && it.height != 0 }
                .distinctBy { "${it.height}p" }
                .sortedByDescending { it.height }
                .map { format ->
                    VideoFormat(
                        formatId = format.formatId ?: "",
                        ext = format.ext ?: "mp4",
                        resolution = "${format.height}p",
                        fileSize = format.fileSizeApproximate?.let {
                            "%.1f MB".format(it / 1024.0 / 1024.0)
                        } ?: "Unknown",
                        fps = format.fps?.toInt() ?: 30
                    )
                }

            Log.d(TAG, "Valid formats parsed: ${validFormats.size}")
            validFormats.forEach {
                Log.d(TAG, " ➤ ${it.resolution} (${it.formatId}) - ${it.fileSize}")
            }

            val metadata = VideoMetadata(
                id = info.id ?: "",
                title = info.title ?: "Unknown Title",
                thumbnailUrl = info.thumbnail ?: "",
                duration = info.duration.toString(),
                formats = validFormats
            )
            emit(Resource.Success(metadata))
            Log.d(TAG, "Metadata emitted successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching metadata", e)
            emit(Resource.Error("Error: ${e.message}"))
        }
    }.flowOn(Dispatchers.IO)

    override fun downloadVideo(url: String, formatId: String, title: String): Flow<DownloadStatus> = callbackFlow {
        Log.d(TAG, "")
        Log.d(TAG, "========================================")
        Log.d(TAG, "DOWNLOAD STARTED")
        Log.d(TAG, "========================================")
        Log.d(TAG, "Title: $title")
        Log.d(TAG, "Format ID: $formatId")
        Log.d(TAG, "URL: $url")
        Log.d(TAG, "========================================")

        try {
            // IMMEDIATELY emit starting status
            Log.d(TAG, "Emitting initial progress (0%)")
            trySend(DownloadStatus.Progress(0f, "Preparing download..."))

            ensureInitialized()

            val cleanTitle = title.replace("[^a-zA-Z0-9.-]".toRegex(), "_")
            val fileNameBase = "${cleanTitle}_${formatId}"
            Log.d(TAG, "Filename base: $fileNameBase")

            val downloadDir = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                "YVD_Downloads"
            )

            if (!downloadDir.exists()) {
                Log.d(TAG, "Creating download directory: ${downloadDir.absolutePath}")
                downloadDir.mkdirs()
            } else {
                Log.d(TAG, "Download directory exists: ${downloadDir.absolutePath}")
            }

            val request = YoutubeDLRequest(url)
            request.addOption("-f", "$formatId+bestaudio/best")
            request.addOption("-o", "${downloadDir.absolutePath}/$fileNameBase.%(ext)s")

            // Use concurrent-fragments instead of aria2c
            // This ensures multi-threaded speed (4 connections) while maintaining proper
            // new-line characters in logs so the app receives progress in real-time.
            request.addOption("--concurrent-fragments", "4")

            Log.d(TAG, "Executing YoutubeDL download request...")
            Log.d(TAG, "Waiting for progress callbacks...")

            var lastProgress = 0f
            var progressCallbackCount = 0

            YoutubeDL.getInstance().execute(request, null) { progress, etaInSeconds, line ->
                progressCallbackCount++

                val safeProgress = when {
                    progress < 0 -> 0f
                    progress > 100 -> 100f
                    else -> progress
                }

                // Log every progress update
                if (safeProgress != lastProgress || progressCallbackCount % 10 == 1) {
                    Log.d(TAG, "📊 Progress callback #$progressCallbackCount: $safeProgress% | ETA: ${etaInSeconds}s | Line: $line")
                    lastProgress = safeProgress
                }

                val statusText = when {
                    line?.contains("Merging", ignoreCase = true) == true -> {
                        Log.d(TAG, "🔄 Status: Merging detected")
                        "Finalizing & Merging..."
                    }
                    line?.contains("ffmpeg", ignoreCase = true) == true -> {
                        Log.d(TAG, "🎞️ Status: FFmpeg processing")
                        "Processing video..."
                    }
                    safeProgress > 98f -> {
                        Log.d(TAG, "✨ Status: Near completion")
                        "Finalizing..."
                    }
                    else -> {
                        "Downloading ${safeProgress.toInt()}%"
                    }
                }

                val sendResult = trySend(DownloadStatus.Progress(safeProgress, statusText))
                if (sendResult.isFailure) {
                    Log.w(TAG, "Failed to send progress update: ${sendResult.exceptionOrNull()}")
                }
            }

            Log.d(TAG, "YoutubeDL execution completed")
            Log.d(TAG, "Total progress callbacks received: $progressCallbackCount")
            Log.d(TAG, "Searching for downloaded file matching: $fileNameBase*")

            // Search for the downloaded file
            val filesInDir = downloadDir.listFiles()
            Log.d(TAG, "Files in download directory: ${filesInDir?.size ?: 0}")
            filesInDir?.forEach { file ->
                Log.d(TAG, " ➤ ${file.name} (${file.length() / 1024 / 1024} MB)")
            }

            val downloadedFile = filesInDir?.find {
                it.name.startsWith(fileNameBase)
            }

            if (downloadedFile != null && downloadedFile.exists()) {
                val fileSizeMB = downloadedFile.length() / 1024 / 1024
                Log.d(TAG, "")
                Log.d(TAG, "========================================")
                Log.d(TAG, "DOWNLOAD SUCCESS")
                Log.d(TAG, "========================================")
                Log.d(TAG, "File: ${downloadedFile.name}")
                Log.d(TAG, "Size: $fileSizeMB MB")
                Log.d(TAG, "Path: ${downloadedFile.absolutePath}")
                Log.d(TAG, "========================================")
                Log.d(TAG, "")

                trySend(DownloadStatus.Success(downloadedFile))
            } else {
                Log.e(TAG, "")
                Log.e(TAG, "========================================")
                Log.e(TAG, "DOWNLOAD FAILED")
                Log.e(TAG, "========================================")
                Log.e(TAG, "Expected file pattern: $fileNameBase*")
                Log.e(TAG, "Search directory: ${downloadDir.absolutePath}")
                Log.e(TAG, "File not found")
                Log.e(TAG, "========================================")
                Log.e(TAG, "")

                trySend(DownloadStatus.Error("Download finished but file not found."))
            }

            close()
            Log.d(TAG, "🔒 Download flow closed")

        } catch (e: Exception) {
            Log.e(TAG, "")
            Log.e(TAG, "========================================")
            Log.e(TAG, "DOWNLOAD EXCEPTION")
            Log.e(TAG, "========================================")
            Log.e(TAG, "Exception type: ${e.javaClass.simpleName}")
            Log.e(TAG, "Message: ${e.message}")
            Log.e(TAG, "Stack trace:", e)
            Log.e(TAG, "========================================")
            Log.e(TAG, "")

            trySend(DownloadStatus.Error("Failed: ${e.message}"))
            close()
        }

        awaitClose {
            Log.d(TAG, "Download flow awaitClose called")
        }
    }.flowOn(Dispatchers.IO)
}