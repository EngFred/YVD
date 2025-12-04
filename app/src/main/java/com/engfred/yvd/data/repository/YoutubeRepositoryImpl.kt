package com.engfred.yvd.data.repository

import android.content.Context
import android.os.Environment
import android.util.Log
import com.engfred.yvd.common.Resource
import com.engfred.yvd.domain.model.VideoFormat
import com.engfred.yvd.domain.model.VideoMetadata
import com.engfred.yvd.domain.repository.YoutubeRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import okhttp3.OkHttpClient
import okhttp3.Request
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.services.youtube.extractors.YoutubeStreamExtractor
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject

class YoutubeRepositoryImpl @Inject constructor(
    private val context: Context
) : YoutubeRepository {

    private val TAG = "YVD_REPO"

    // Separate client for downloading large files (Keep simple, cookies not strictly needed for direct stream download usually, but good practice)
    private val downloadClient = OkHttpClient()

    override fun getVideoMetadata(url: String): Flow<Resource<VideoMetadata>> = flow {
        Log.d(TAG, "--------------------------------------------------")
        Log.d(TAG, "Step 1: Start fetching metadata for: $url")
        emit(Resource.Loading())

        try {
            // 1. Get the extractor
            val extractor = ServiceList.YouTube.getStreamExtractor(url) as YoutubeStreamExtractor

            // 2. Fetch data
            Log.d(TAG, "Step 2: Executing Network Call (fetchPage)...")
            extractor.fetchPage()
            Log.d(TAG, "Step 3: Network Call Success. Video Name: ${extractor.name}")

            // 3. Process Streams
            Log.d(TAG, "Step 4: Processing video streams. Total found: ${extractor.videoStreams.size}")

            val allFormats = extractor.videoStreams
                // REMOVED: .filter { it.isVideoOnly == false } -> This was hiding 1080p+
                .sortedWith(compareByDescending<org.schabi.newpipe.extractor.stream.VideoStream> {
                    // Sort by resolution number (1080 > 720)
                    it.resolution.replace("p", "").toIntOrNull() ?: 0
                }.thenBy {
                    // If resolutions are equal, prioritize ones WITH audio (isVideoOnly = false)
                    it.isVideoOnly
                })

            val mappedFormats = allFormats.map { stream ->
                // Calculate rough size if available (NewPipe often returns -1 for DASH)
                val sizeMb = if (stream.format != null) {
                    // Try to guess size or leave as unknown
                    "Unknown"
                } else "Unknown"

                // Label video-only streams so user knows there is no audio
                val resolutionLabel = if (stream.isVideoOnly) {
                    "${stream.resolution} (Video Only)"
                } else {
                    stream.resolution
                }

                VideoFormat(
                    formatId = stream.itag.toString(),
                    ext = stream.format?.suffix ?: "mp4",
                    resolution = resolutionLabel,
                    fileSize = sizeMb,
                    fps = 30 // NewPipe usually doesn't give FPS easily in basic streams, defaulting to 30
                )
            }

            Log.d(TAG, "Step 5: Mapped ${mappedFormats.size} valid formats for UI.")

            val metadata = VideoMetadata(
                id = extractor.url,
                title = extractor.name,
                thumbnailUrl = extractor.thumbnails?.firstOrNull()?.url ?: "",
                duration = extractor.length.toString(), // Returns seconds
                formats = mappedFormats
            )

            emit(Resource.Success(metadata))
            Log.d(TAG, "Step 6: Emit Success")

        } catch (e: Exception) {
            Log.e(TAG, "CRITICAL ERROR in getVideoMetadata", e)
            e.printStackTrace()
            emit(Resource.Error("Error: ${e.message}"))
        }
    }.flowOn(Dispatchers.IO)

    override fun downloadVideo(url: String, formatId: String, title: String): Flow<DownloadStatus> = callbackFlow {
        Log.d(TAG, "Starting download process for: $title (itag: $formatId)")

        try {
            trySend(DownloadStatus.Progress(0f, "Initializing..."))

            // We must fetch page again to get fresh download URL (URLs expire)
            val extractor = ServiceList.YouTube.getStreamExtractor(url) as YoutubeStreamExtractor
            extractor.fetchPage()

            val targetStream = extractor.videoStreams.find { it.itag.toString() == formatId }

            if (targetStream == null) {
                Log.e(TAG, "Stream ID $formatId not found in fresh fetch.")
                trySend(DownloadStatus.Error("Format link expired or not found"))
                close()
                return@callbackFlow
            }

            Log.d(TAG, "Found target stream URL: ${targetStream.content}")

            val cleanTitle = title.replace("[^a-zA-Z0-9.-]".toRegex(), "_")
            val ext = targetStream.format?.suffix ?: "mp4"
            val fileName = "${cleanTitle}_${targetStream.resolution}.$ext"

            val downloadDir = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                "YVD_Downloads"
            )
            if (!downloadDir.exists()) downloadDir.mkdirs()

            val file = File(downloadDir, fileName)
            Log.d(TAG, "Saving to: ${file.absolutePath}")

            val request = Request.Builder().url(targetStream.content).build()
            val response = downloadClient.newCall(request).execute()

            if (!response.isSuccessful) {
                Log.e(TAG, "Download request failed: ${response.code}")
                trySend(DownloadStatus.Error("Network error: ${response.code}"))
                close()
                return@callbackFlow
            }

            val body = response.body
            if (body == null) {
                trySend(DownloadStatus.Error("Server returned empty file"))
                close()
                return@callbackFlow
            }

            val totalLength = body.contentLength()
            Log.d(TAG, "File size to download: $totalLength bytes")

            var bytesCopied: Long = 0
            val buffer = ByteArray(8 * 1024)
            val inputStream = body.byteStream()
            val outputStream = FileOutputStream(file)

            var lastProgress = 0

            inputStream.use { input ->
                outputStream.use { output ->
                    var bytes = input.read(buffer)
                    while (bytes >= 0) {
                        output.write(buffer, 0, bytes)
                        bytesCopied += bytes

                        if (totalLength > 0) {
                            val progress = ((bytesCopied.toFloat() / totalLength.toFloat()) * 100).toInt()
                            // Only emit if progress changed to avoid flooding UI
                            if (progress > lastProgress) {
                                lastProgress = progress
                                trySend(DownloadStatus.Progress(progress.toFloat(), "Downloading $progress%"))
                            }
                        }
                        bytes = input.read(buffer)
                    }
                }
            }

            Log.d(TAG, "Download finished successfully.")
            trySend(DownloadStatus.Success(file))
            close()

        } catch (e: Exception) {
            Log.e(TAG, "Exception during download", e)
            trySend(DownloadStatus.Error("Download Failed: ${e.message}"))
            close()
        }

        awaitClose {
            Log.d(TAG, "Download flow closed.")
        }
    }.flowOn(Dispatchers.IO)
}

sealed class DownloadStatus {
    data class Progress(val progress: Float, val text: String) : DownloadStatus()
    data class Success(val file: File) : DownloadStatus()
    data class Error(val message: String) : DownloadStatus()
}