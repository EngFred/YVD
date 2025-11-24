package com.engfred.yvd.data.repository

import android.content.Context
import android.os.Environment
import android.util.Log
import com.engfred.yvd.common.Resource
import com.engfred.yvd.domain.model.VideoFormat
import com.engfred.yvd.domain.model.VideoMetadata
import com.engfred.yvd.domain.repository.YoutubeRepository
import com.yausername.aria2c.Aria2c
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
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

    private fun initEngine() {
        try {
            YoutubeDL.getInstance().init(context)
            FFmpeg.getInstance().init(context)
            Aria2c.getInstance().init(context)
        } catch (e: Exception) {
            Log.e(TAG, "CRITICAL FAILURE: Failed to initialize engines", e)
        }
    }

    override fun getVideoMetadata(url: String): Flow<Resource<VideoMetadata>> = flow {
        emit(Resource.Loading())
        try {
            initEngine()
            val request = YoutubeDLRequest(url)
            request.addOption("--dump-json")

            val info = YoutubeDL.getInstance().getInfo(request)
            val rawFormats = info.formats ?: emptyList()

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

            val metadata = VideoMetadata(
                id = info.id ?: "",
                title = info.title ?: "Unknown Title",
                thumbnailUrl = info.thumbnail ?: "",
                duration = info.duration.toString(),
                formats = validFormats
            )
            emit(Resource.Success(metadata))
        } catch (e: Exception) {
            Log.e(TAG, "Error metadata", e)
            emit(Resource.Error("Error: ${e.message}"))
        }
    }.flowOn(Dispatchers.IO)

    override fun downloadVideo(url: String, formatId: String, title: String): Flow<DownloadStatus> = callbackFlow {
        Log.d(TAG, "--- STARTING DOWNLOAD ---")
        try {
            initEngine()

            // [FIX] Append FormatID/Resolution to filename to allow multiple qualities of same video
            val cleanTitle = title.replace("[^a-zA-Z0-9.-]".toRegex(), "_")
            val fileNameBase = "${cleanTitle}_${formatId}"

            Log.d(TAG, "Target Filename Base: $fileNameBase")

            val downloadDir = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                "YVD_Downloads"
            )
            if (!downloadDir.exists()) downloadDir.mkdirs()

            val request = YoutubeDLRequest(url)
            request.addOption("-f", "$formatId+bestaudio/best")

            // Use the specific unique filename
            request.addOption("-o", "${downloadDir.absolutePath}/$fileNameBase.%(ext)s")

            request.addOption("--downloader", "libaria2c.so")
            request.addOption("--external-downloader-args", "aria2c:--min-split-size=1M --max-connection-per-server=16 --max-concurrent-downloads=16 --split=16")

            YoutubeDL.getInstance().execute(request, null) { progress, _, line ->
                val safeProgress = if (progress < 0) 0f else progress
                val statusText = if (safeProgress > 98f || line?.contains("Merging") == true) {
                    "Finalizing & Merging..."
                } else {
                    "Downloading... ${safeProgress.toInt()}%"
                }
                trySend(DownloadStatus.Progress(safeProgress, statusText))
            }

            // [FIX] Search for the specific unique file we just created
            val downloadedFile = downloadDir.listFiles()?.find {
                it.name.startsWith(fileNameBase)
            }

            if (downloadedFile != null && downloadedFile.exists()) {
                Log.d(TAG, "SUCCESS: File found: ${downloadedFile.name} (${downloadedFile.length() / 1024 / 1024} MB)")
                trySend(DownloadStatus.Success(downloadedFile))
            } else {
                Log.e(TAG, "FAILURE: File not found matching $fileNameBase")
                trySend(DownloadStatus.Error("Download finished but file not found."))
            }
            close()
        } catch (e: Exception) {
            Log.e(TAG, "Download Exception", e)
            trySend(DownloadStatus.Error("Failed: ${e.message}"))
            close()
        }
        awaitClose { }
    }.flowOn(Dispatchers.IO)
}