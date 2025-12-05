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
import com.engfred.yvd.data.network.DownloaderImpl
import com.engfred.yvd.domain.model.AudioFormat
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
import org.schabi.newpipe.extractor.stream.Stream
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
        NewPipe.init(DownloaderImpl())
    }

    private val downloadClient = OkHttpClient()

    override fun getVideoMetadata(url: String): Flow<Resource<VideoMetadata>> = flow {
        Log.d(TAG, "Step 1: Start fetching metadata for: $url")
        emit(Resource.Loading())
        try {
            val extractor = ServiceList.YouTube.getStreamExtractor(url) as YoutubeStreamExtractor
            extractor.fetchPage()

            // --- 1. Process Video Streams ---
            val allVideoStreams = extractor.videoStreams + extractor.videoOnlyStreams
            Log.d(TAG, "Fetched ${allVideoStreams.size} total video streams (muxed + video-only)")

            // Filter and Sort Video
            val sortedVideoFormats = allVideoStreams
                .filter { it.format?.suffix == "mp4" } // Prefer MP4 for compatibility
                .sortedWith(
                    compareByDescending<VideoStream> {
                        it.resolution.replace(Regex("p.*"), "").toIntOrNull() ?: 0
                    }.thenByDescending {
                        it.resolution.substringAfter("p", "30").replace(Regex("\\D+"), "").toIntOrNull() ?: 30
                    }
                )

            val durationSeconds = extractor.length

            val videoFormats = sortedVideoFormats.map { stream ->
                VideoFormat(
                    formatId = stream.itag.toString(),
                    ext = stream.format?.suffix ?: "mp4",
                    resolution = stream.resolution,
                    // FIX: Convert Int bitrate to Long
                    fileSize = calculateFileSize(stream.bitrate.toLong(), durationSeconds),
                    fps = stream.resolution.substringAfter("p", "30").replace(Regex("\\D+"), "").toIntOrNull() ?: 30
                )
            }
            Log.d(TAG, "Processed ${videoFormats.size} valid MP4 video formats for UI")

            // --- 2. Process Audio Streams ---
            val audioStreams = extractor.audioStreams
            Log.d(TAG, "Fetched ${audioStreams.size} audio streams")

            // Sort Audio by bitrate (quality)
            val sortedAudioFormats = audioStreams
                .filter { it.format?.suffix == "m4a" } // M4A is best for Android native playback
                .sortedByDescending { it.averageBitrate }

            val audioFormats = sortedAudioFormats.map { stream ->
                AudioFormat(
                    formatId = stream.itag.toString(),
                    ext = stream.format?.suffix ?: "m4a",
                    bitrate = "${stream.averageBitrate}kbps",
                    // FIX: Convert Int bitrate to Long
                    fileSize = calculateFileSize(stream.bitrate.toLong(), durationSeconds)
                )
            }
            Log.d(TAG, "Processed ${audioFormats.size} valid M4A audio formats for UI")

            val metadata = VideoMetadata(
                id = extractor.url,
                title = extractor.name,
                thumbnailUrl = extractor.thumbnails.firstOrNull()?.url ?: "",
                duration = extractor.length.toString(),
                videoFormats = videoFormats,
                audioFormats = audioFormats
            )

            Log.d(TAG, "Metadata extraction success: ${extractor.name}")
            emit(Resource.Success(metadata))

        } catch (e: Exception) {
            Log.e(TAG, "Error fetching metadata", e)
            emit(Resource.Error("Error: ${e.message}"))
        }
    }.flowOn(Dispatchers.IO)

    override fun downloadVideo(
        url: String,
        formatId: String,
        title: String,
        isAudio: Boolean
    ): Flow<DownloadStatus> = callbackFlow {
        Log.d(TAG, "Starting download request. Title: $title, isAudio: $isAudio, FormatID: $formatId")
        try {
            trySend(DownloadStatus.Progress(0f, "Initializing..."))

            // Fetch fresh extractor for up-to-date URLs
            val extractor = ServiceList.YouTube.getStreamExtractor(url) as YoutubeStreamExtractor
            extractor.fetchPage()
            Log.d(TAG, "Refreshed stream info successfully")

            val downloadDir = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                "YVD_Downloads"
            )
            if (!downloadDir.exists()) downloadDir.mkdirs()
            val cleanTitle = title.replace("[^a-zA-Z0-9.-]".toRegex(), "_")
            Log.d(TAG, "Download directory: ${downloadDir.absolutePath}")

            if (isAudio) {
                // --- AUDIO ONLY DOWNLOAD LOGIC ---
                Log.d(TAG, "Mode: Audio Only")
                val targetStream = extractor.audioStreams.find { it.itag.toString() == formatId }
                if (targetStream == null) {
                    Log.e(TAG, "Audio stream ID $formatId not found")
                    throw Exception("Audio stream not found")
                }

                val fileName = "${cleanTitle}.${targetStream.format?.suffix}"
                val outputFile = File(downloadDir, fileName)

                Log.d(TAG, "Downloading audio to: ${outputFile.name}")
                downloadStream(targetStream, outputFile, this@callbackFlow, "Downloading Audio...")
                trySend(DownloadStatus.Success(outputFile))
                Log.d(TAG, "Audio download success")

            } else {
                // --- VIDEO DOWNLOAD LOGIC (Existing Muxing Logic) ---
                Log.d(TAG, "Mode: Video")
                val allVideoStreams = extractor.videoStreams + extractor.videoOnlyStreams
                val targetStream = allVideoStreams.find { it.itag.toString() == formatId }
                if (targetStream == null) {
                    Log.e(TAG, "Video stream ID $formatId not found")
                    throw Exception("Video stream not found")
                }

                val videoExt = targetStream.format?.suffix ?: "mp4"
                val fileName = "${cleanTitle}_${targetStream.resolution}.$videoExt"
                val outputFile = File(downloadDir, fileName)
                Log.d(TAG, "Target Video File: ${outputFile.name} (Res: ${targetStream.resolution}, IsVideoOnly: ${targetStream.isVideoOnly})")

                if (!targetStream.isVideoOnly) {
                    // Muxed stream (Video+Audio included)
                    Log.d(TAG, "Stream is muxed. Downloading directly...")
                    downloadStream(targetStream, outputFile, this@callbackFlow, "Downloading Video...")
                    trySend(DownloadStatus.Success(outputFile))
                } else {
                    // Video-only stream: Needs Muxing
                    Log.d(TAG, "Stream is video-only. Split download required.")
                    val videoTemp = File(downloadDir, "video_temp.$videoExt")
                    Log.d(TAG, "Downloading video track to temp file...")
                    downloadStream(targetStream, videoTemp, this@callbackFlow, "Downloading Video Track...")

                    // Find matching audio
                    val targetAudioSuffix = if (videoExt == "mp4") "m4a" else "webm"
                    Log.d(TAG, "Looking for best audio track with suffix: $targetAudioSuffix")

                    val bestAudio = extractor.audioStreams
                        .filter { it.format?.suffix == targetAudioSuffix }
                        .maxByOrNull { it.averageBitrate }
                        ?: throw Exception("No compatible audio found")

                    Log.d(TAG, "Selected audio track: ${bestAudio.averageBitrate}kbps")

                    val audioTemp = File(downloadDir, "audio_temp.${bestAudio.format?.suffix}")
                    Log.d(TAG, "Downloading audio track to temp file...")
                    downloadStream(bestAudio, audioTemp, this@callbackFlow, "Downloading Audio Track...")

                    // Mux
                    trySend(DownloadStatus.Progress(0f, "Merging..."))
                    Log.d(TAG, "Starting Muxer...")
                    val muxerFormat = if (videoExt == "mp4") MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4 else MediaMuxer.OutputFormat.MUXER_OUTPUT_WEBM

                    muxAudioVideo(audioTemp.absolutePath, videoTemp.absolutePath, outputFile.absolutePath, muxerFormat)

                    Log.d(TAG, "Muxing complete. Deleting temp files.")
                    videoTemp.delete()
                    audioTemp.delete()
                    trySend(DownloadStatus.Success(outputFile))
                }
            }
            close()
        } catch (e: Exception) {
            Log.e(TAG, "Download/Mux failed", e)
            trySend(DownloadStatus.Error(e.message ?: "Unknown error"))
            close()
        }
        awaitClose { Log.d(TAG, "Download flow closed") }
    }.flowOn(Dispatchers.IO)

    // --- Helpers ---

    private fun calculateFileSize(bitrate: Long, duration: Long): String {
        val bytes = if (bitrate > 0 && duration > 0) (bitrate * duration) / 8 else -1L
        return if (bytes > 0) "%.1f MB".format(bytes.toDouble() / (1024 * 1024)) else "Unknown"
    }

    private suspend fun downloadStream(
        stream: Stream,
        file: File,
        flow: kotlinx.coroutines.channels.ProducerScope<DownloadStatus>,
        statusPrefix: String
    ) {
        Log.d(TAG, "Starting stream download: ${file.name} from URL")
        val request = Request.Builder().url(stream.content).build()
        val response = downloadClient.newCall(request).execute()

        if (!response.isSuccessful) {
            Log.e(TAG, "Network error: ${response.code}")
            throw Exception("Network error: ${response.code}")
        }

        // FIX: Ensure body is not null before proceeding
        val body = response.body ?: throw Exception("Response body is null")

        val totalLength = body.contentLength()
        Log.d(TAG, "Content Length: $totalLength bytes")

        var bytesCopied: Long = 0
        val buffer = ByteArray(8 * 1024)

        // FIX: Safe access to byteStream
        val inputStream = body.byteStream()

        val outputStream = FileOutputStream(file)
        var lastProgress = 0

        inputStream.use { input ->
            outputStream.use { output ->
                // FIX: Input cannot be null here due to 'use' block on non-null inputStream
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
        Log.d(TAG, "Stream download finished: ${file.name}")
    }

    private fun muxAudioVideo(audioPath: String, videoPath: String, outPath: String, format: Int) {
        Log.d(TAG, "Muxing inputs: Video=$videoPath, Audio=$audioPath")
        val videoExtractor = MediaExtractor()
        videoExtractor.setDataSource(videoPath)
        val videoTrackIndex = findTrackIndex(videoExtractor, "video/")
        videoExtractor.selectTrack(videoTrackIndex)
        val videoFormat = videoExtractor.getTrackFormat(videoTrackIndex)

        val audioExtractor = MediaExtractor()
        audioExtractor.setDataSource(audioPath)
        val audioTrackIndex = findTrackIndex(audioExtractor, "audio/")
        audioExtractor.selectTrack(audioTrackIndex)
        val audioFormat = audioExtractor.getTrackFormat(audioTrackIndex)

        val muxer = MediaMuxer(outPath, format)
        val muxerVideoTrackIndex = muxer.addTrack(videoFormat)
        val muxerAudioTrackIndex = muxer.addTrack(audioFormat)
        muxer.start()
        Log.d(TAG, "Muxer started")

        val buffer = ByteBuffer.allocate(1024 * 1024)
        val bufferInfo = MediaCodec.BufferInfo()

        copyTrack(videoExtractor, muxer, muxerVideoTrackIndex, buffer, bufferInfo)
        copyTrack(audioExtractor, muxer, muxerAudioTrackIndex, buffer, bufferInfo)

        muxer.stop()
        muxer.release()
        videoExtractor.release()
        audioExtractor.release()
        Log.d(TAG, "Muxing complete")
    }

    private fun copyTrack(extractor: MediaExtractor, muxer: MediaMuxer, trackIndex: Int, buffer: ByteBuffer, bufferInfo: MediaCodec.BufferInfo) {
        while (true) {
            val chunkSize = extractor.readSampleData(buffer, 0)
            if (chunkSize > 0) {
                bufferInfo.presentationTimeUs = extractor.sampleTime

                // FIX: Map MediaExtractor flags to MediaCodec flags manually
                val sampleFlags = extractor.sampleFlags
                var codecFlags = 0

                if (sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC != 0) {
                    codecFlags = codecFlags or MediaCodec.BUFFER_FLAG_KEY_FRAME
                }

                // Use a version check for PARTIAL_FRAME if necessary, or just map it if available
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && (sampleFlags and MediaExtractor.SAMPLE_FLAG_PARTIAL_FRAME != 0)) {
                    codecFlags = codecFlags or MediaCodec.BUFFER_FLAG_PARTIAL_FRAME
                }

                bufferInfo.flags = codecFlags
                bufferInfo.size = chunkSize

                muxer.writeSampleData(trackIndex, buffer, bufferInfo)
                extractor.advance()
            } else {
                break
            }
        }
    }

    private fun findTrackIndex(extractor: MediaExtractor, mimePrefix: String): Int {
        for (i in 0 until extractor.trackCount) {
            if (extractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME)?.startsWith(mimePrefix) == true) return i
        }
        throw IllegalArgumentException("No track found with prefix $mimePrefix")
    }
}

sealed class DownloadStatus {
    data class Progress(val progress: Float, val text: String) : DownloadStatus()
    data class Success(val file: File) : DownloadStatus()
    data class Error(val message: String) : DownloadStatus()
}