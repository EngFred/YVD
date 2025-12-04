package com.engfred.yvd.data.repository

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.os.Build
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
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.services.youtube.extractors.YoutubeStreamExtractor
import org.schabi.newpipe.extractor.stream.VideoStream
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import javax.inject.Inject

class YoutubeRepositoryImpl @Inject constructor(
    private val context: Context
) : YoutubeRepository {

    private val TAG = "YVD_REPO"

    init {
        // Initialize NewPipe with custom downloader
        NewPipe.init(com.engfred.yvd.data.network.DownloaderImpl())
    }

    // Separate client for downloading large files
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

            // 3. Process Streams - Include both muxed and video-only
            val allVideoStreams = extractor.videoStreams + extractor.videoOnlyStreams
            Log.d(TAG, "Step 4: Processing video streams. Total found: ${allVideoStreams.size}")

            val sortedFormats = allVideoStreams.sortedWith(
                compareByDescending<VideoStream> {
                    // Extract resolution number (e.g., 1080 from "1080p" or "1080p60")
                    it.resolution.replace(Regex("p.*"), "").toIntOrNull() ?: 0
                }.thenByDescending {
                    // Extract FPS if present (e.g., 60 from "1080p60")
                    it.resolution.substringAfter("p", "30").replace(Regex("\\D+"), "").toIntOrNull() ?: 30
                }.thenBy {
                    // Prefer muxed streams (false before true)
                    it.isVideoOnly
                }
            )

            val mappedFormats = sortedFormats.map { stream ->
                val sizeMb = "Unknown" // Calculation logic placeholder

                // Label video-only streams so user knows there is no audio
                val resolutionLabel = if (stream.isVideoOnly) {
                    "${stream.resolution} (Video Only)"
                } else {
                    stream.resolution
                }

                // Parse FPS from resolution string
                val fps = stream.resolution.substringAfter("p", "30").replace(Regex("\\D+"), "").toIntOrNull() ?: 30

                VideoFormat(
                    formatId = stream.itag.toString(),
                    ext = stream.format?.suffix ?: "mp4",
                    resolution = resolutionLabel,
                    fileSize = sizeMb,
                    fps = fps
                )
            }

            Log.d(TAG, "Step 5: Mapped ${mappedFormats.size} valid formats for UI.")

            val metadata = VideoMetadata(
                id = extractor.url,
                title = extractor.name,
                thumbnailUrl = extractor.thumbnails?.firstOrNull()?.url ?: "",
                duration = extractor.length.toString(),
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

            // Fetch fresh extractor for up-to-date URLs
            val extractor = ServiceList.YouTube.getStreamExtractor(url) as YoutubeStreamExtractor
            extractor.fetchPage()

            val allVideoStreams = extractor.videoStreams + extractor.videoOnlyStreams
            val targetStream = allVideoStreams.find { it.itag.toString() == formatId }

            if (targetStream == null) {
                Log.e(TAG, "Stream ID $formatId not found in fresh fetch.")
                trySend(DownloadStatus.Error("Format link expired or not found"))
                close()
                return@callbackFlow
            }

            Log.d(TAG, "Found target stream URL: ${targetStream.content}")

            val cleanTitle = title.replace("[^a-zA-Z0-9.-]".toRegex(), "_")
            val videoExt = targetStream.format?.suffix ?: "mp4"
            val fileName = "${cleanTitle}_${targetStream.resolution}.$videoExt"

            val downloadDir = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                "YVD_Downloads"
            )
            if (!downloadDir.exists()) downloadDir.mkdirs()

            val outputFile = File(downloadDir, fileName)
            Log.d(TAG, "Final output: ${outputFile.absolutePath}")

            if (!targetStream.isVideoOnly) {
                Log.d(TAG, "Muxed stream: Downloading both video and audio...")
                // Muxed stream: Download directly
                downloadStream(targetStream, outputFile, this@callbackFlow, "Downloading video...")
                trySend(DownloadStatus.Success(outputFile))
            } else {
                Log.d(TAG, "Video-only stream: Downloading audio and video separately...")

                // 1. Download the Video Track
                val videoTemp = File(downloadDir, "video_temp.$videoExt")
                downloadStream(targetStream, videoTemp, this@callbackFlow, "Downloading video...")

                // 2. Select Compatible Audio
                // FIX: Strictly match audio container to video container to prevent MediaMuxer crash.
                // If video is mp4, we MUST find m4a. If video is webm, we MUST find webm.
                val targetAudioSuffix = if (videoExt == "mp4") "m4a" else "webm"

                Log.d(TAG, "Looking for audio with suffix: $targetAudioSuffix to match video container.")

                val validAudioStreams = extractor.audioStreams.filter {
                    it.format?.suffix == targetAudioSuffix
                }

                // Sort by bitrate to get best quality audio
                val bestAudio = validAudioStreams.sortedByDescending { it.averageBitrate }.firstOrNull()

                if (bestAudio == null) {
                    trySend(DownloadStatus.Error("No compatible audio ($targetAudioSuffix) found."))
                    videoTemp.delete() // Clean up video since we can't merge
                    close()
                    return@callbackFlow
                }

                val audioExt = bestAudio.format?.suffix ?: "m4a"
                val audioTemp = File(downloadDir, "audio_temp.$audioExt")

                downloadStream(bestAudio, audioTemp, this@callbackFlow, "Downloading audio...")

                // 3. Merge
                trySend(DownloadStatus.Progress(0f, "Merging audio and video..."))
                try {
                    // Determine correct Muxer Output Format
                    val muxerFormat = if (videoExt == "mp4")
                        MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4
                    else
                        MediaMuxer.OutputFormat.MUXER_OUTPUT_WEBM

                    muxAudioVideo(
                        audioFilePath = audioTemp.absolutePath,
                        videoFilePath = videoTemp.absolutePath,
                        outputFilePath = outputFile.absolutePath,
                        outputFormat = muxerFormat
                    )

                    Log.d(TAG, "Merge successful.")
                    trySend(DownloadStatus.Success(outputFile))

                    // Clean up temps
                    videoTemp.delete()
                    audioTemp.delete()
                } catch (e: Exception) {
                    Log.e(TAG, "Merge failed", e)
                    trySend(DownloadStatus.Error("Failed to merge audio and video: ${e.message}"))
                }
            }
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

    // Helper to download any stream (VideoStream or AudioStream)
    private suspend fun downloadStream(
        stream: org.schabi.newpipe.extractor.stream.Stream,
        file: File,
        flow: kotlinx.coroutines.channels.ProducerScope<DownloadStatus>,
        statusPrefix: String
    ) {
        try {
            val request = Request.Builder().url(stream.content).build()
            val response = downloadClient.newCall(request).execute()

            if (!response.isSuccessful) {
                Log.e(TAG, "Download request failed: ${response.code}")
                flow.trySend(DownloadStatus.Error("Network error: ${response.code}"))
                return
            }

            val body = response.body ?: run {
                flow.trySend(DownloadStatus.Error("Server returned empty file"))
                return
            }

            val totalLength = body.contentLength()
            Log.d(TAG, "File size to download: $totalLength bytes for ${file.name}")

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
                            if (progress > lastProgress) {
                                lastProgress = progress
                                flow.trySend(DownloadStatus.Progress(progress.toFloat(), "$statusPrefix $progress%"))
                            }
                        }
                        bytes = input.read(buffer)
                    }
                }
            }
            Log.d(TAG, "Download finished for ${file.name}")

        } catch (e: Exception) {
            Log.e(TAG, "Exception downloading stream ${file.name}", e)
            flow.trySend(DownloadStatus.Error("Failed to download: ${e.message}"))
        }
    }

    // Mux function using MediaMuxer and MediaExtractor
    private fun muxAudioVideo(
        audioFilePath: String,
        videoFilePath: String,
        outputFilePath: String,
        outputFormat: Int // Added parameter for correct container format
    ) {
        // Init extractors
        val videoExtractor = MediaExtractor()
        videoExtractor.setDataSource(videoFilePath)
        val videoTrackIndex = findTrackIndex(videoExtractor, "video/")
        videoExtractor.selectTrack(videoTrackIndex)
        val videoFormat = videoExtractor.getTrackFormat(videoTrackIndex)

        val audioExtractor = MediaExtractor()
        audioExtractor.setDataSource(audioFilePath)
        val audioTrackIndex = findTrackIndex(audioExtractor, "audio/")
        audioExtractor.selectTrack(audioTrackIndex)
        val audioFormat = audioExtractor.getTrackFormat(audioTrackIndex)

        // Init muxer with the specific format (MPEG4 or WEBM)
        val muxer = MediaMuxer(outputFilePath, outputFormat)

        val muxerVideoTrackIndex = muxer.addTrack(videoFormat)
        val muxerAudioTrackIndex = muxer.addTrack(audioFormat)

        muxer.start()

        // Prepare buffer
        val maxChunkSize = 1024 * 1024
        val buffer = ByteBuffer.allocate(maxChunkSize)
        val bufferInfo = MediaCodec.BufferInfo()

        // Copy Video
        while (true) {
            val chunkSize = videoExtractor.readSampleData(buffer, 0)
            if (chunkSize > 0) {
                bufferInfo.presentationTimeUs = videoExtractor.sampleTime
                val sampleFlags = videoExtractor.sampleFlags
                var flags = 0

                if (sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC != 0) {
                    flags = flags or MediaCodec.BUFFER_FLAG_KEY_FRAME
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && (sampleFlags and MediaExtractor.SAMPLE_FLAG_PARTIAL_FRAME != 0)) {
                    flags = flags or MediaCodec.BUFFER_FLAG_PARTIAL_FRAME
                }
                if ((sampleFlags and MediaExtractor.SAMPLE_FLAG_ENCRYPTED) != 0) {
                    throw IllegalStateException("Encrypted samples not supported")
                }

                bufferInfo.flags = flags
                bufferInfo.size = chunkSize
                muxer.writeSampleData(muxerVideoTrackIndex, buffer, bufferInfo)
                videoExtractor.advance()
            } else {
                break
            }
        }

        // Copy Audio
        while (true) {
            val chunkSize = audioExtractor.readSampleData(buffer, 0)
            if (chunkSize > 0) {
                bufferInfo.presentationTimeUs = audioExtractor.sampleTime
                val sampleFlags = audioExtractor.sampleFlags
                var flags = 0

                if (sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC != 0) {
                    flags = flags or MediaCodec.BUFFER_FLAG_KEY_FRAME
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && (sampleFlags and MediaExtractor.SAMPLE_FLAG_PARTIAL_FRAME != 0)) {
                    flags = flags or MediaCodec.BUFFER_FLAG_PARTIAL_FRAME
                }
                if ((sampleFlags and MediaExtractor.SAMPLE_FLAG_ENCRYPTED) != 0) {
                    throw IllegalStateException("Encrypted samples not supported")
                }

                bufferInfo.flags = flags
                bufferInfo.size = chunkSize
                muxer.writeSampleData(muxerAudioTrackIndex, buffer, bufferInfo)
                audioExtractor.advance()
            } else {
                break
            }
        }

        // Cleanup
        muxer.stop()
        muxer.release()
        videoExtractor.release()
        audioExtractor.release()
    }

    // Helper to find track index by MIME type prefix
    private fun findTrackIndex(extractor: MediaExtractor, mimePrefix: String): Int {
        for (i in 0 until extractor.trackCount) {
            val format = extractor.getTrackFormat(i)
            val mime = format.getString(MediaFormat.KEY_MIME)
            if (mime?.startsWith(mimePrefix) == true) {
                return i
            }
        }
        throw IllegalArgumentException("No track found with MIME prefix: $mimePrefix")
    }
}
sealed class DownloadStatus {
    data class Progress(val progress: Float, val text: String) : DownloadStatus()
    data class Success(val file: File) : DownloadStatus()
    data class Error(val message: String) : DownloadStatus()
}