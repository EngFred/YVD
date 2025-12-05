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
import com.engfred.yvd.domain.model.DownloadStatus
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
        NewPipe.init(DownloaderImpl())
    }

    private val downloadClient = OkHttpClient()

    override fun getVideoMetadata(url: String): Flow<Resource<VideoMetadata>> = flow {
        emit(Resource.Loading())
        try {
            val extractor = ServiceList.YouTube.getStreamExtractor(url) as YoutubeStreamExtractor
            extractor.fetchPage()

            val allVideoStreams = extractor.videoStreams + extractor.videoOnlyStreams

            val sortedVideoFormats = allVideoStreams
                .filter { it.format?.suffix == "mp4" }
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
                    fileSize = calculateFileSize(stream.bitrate.toLong(), durationSeconds),
                    fps = stream.resolution.substringAfter("p", "30").replace(Regex("\\D+"), "").toIntOrNull() ?: 30
                )
            }

            val audioStreams = extractor.audioStreams
            val sortedAudioFormats = audioStreams
                .filter { it.format?.suffix == "m4a" }
                .sortedByDescending { it.averageBitrate }

            val audioFormats = sortedAudioFormats.map { stream ->
                AudioFormat(
                    formatId = stream.itag.toString(),
                    ext = stream.format?.suffix ?: "m4a",
                    bitrate = "${stream.averageBitrate}kbps",
                    fileSize = calculateFileSize(stream.bitrate.toLong(), durationSeconds)
                )
            }

            val metadata = VideoMetadata(
                id = extractor.url,
                title = extractor.name,
                thumbnailUrl = extractor.thumbnails.firstOrNull()?.url ?: "",
                duration = extractor.length.toString(),
                videoFormats = videoFormats,
                audioFormats = audioFormats
            )

            emit(Resource.Success(metadata))

        } catch (e: Exception) {
            e.printStackTrace()
            emit(Resource.Error("Error: ${e.message}"))
        }
    }.flowOn(Dispatchers.IO)

    override fun downloadVideo(
        url: String,
        formatId: String,
        title: String,
        isAudio: Boolean
    ): Flow<DownloadStatus> = callbackFlow {
        try {
            trySend(DownloadStatus.Progress(0f, "Initializing..."))

            val extractor = ServiceList.YouTube.getStreamExtractor(url) as YoutubeStreamExtractor
            extractor.fetchPage()

            // Standard Downloads folder
            val downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            // Create a subfolder to keep things tidy
            val appDir = File(downloadDir, "YVDownloader")
            if (!appDir.exists()) appDir.mkdirs()

            // Sanitize filename
            val cleanTitle = title.replace("[^a-zA-Z0-9.-]".toRegex(), "_").take(50)

            if (isAudio) {
                // AUDIO LOGIC
                val targetStream = extractor.audioStreams.find { it.itag.toString() == formatId }
                    ?: throw Exception("Audio stream not found")

                val fileName = "${cleanTitle}.${targetStream.format?.suffix}"
                val outputFile = File(appDir, fileName)

                downloadStream(targetStream, outputFile, this@callbackFlow, "Downloading Audio...")
                trySend(DownloadStatus.Success(outputFile))

            } else {
                // VIDEO LOGIC
                val allVideoStreams = extractor.videoStreams + extractor.videoOnlyStreams
                val targetStream = allVideoStreams.find { it.itag.toString() == formatId }
                    ?: throw Exception("Video stream not found")

                val videoExt = targetStream.format?.suffix ?: "mp4"
                val fileName = "${cleanTitle}_${targetStream.resolution}.$videoExt"
                val outputFile = File(appDir, fileName)

                if (!targetStream.isVideoOnly) {
                    downloadStream(targetStream, outputFile, this@callbackFlow, "Downloading Video...")
                    trySend(DownloadStatus.Success(outputFile))
                } else {
                    // MUXING LOGIC
                    val videoTemp = File(appDir, "temp_v_${System.currentTimeMillis()}.$videoExt")

                    try {
                        downloadStream(targetStream, videoTemp, this@callbackFlow, "Downloading Video Track...")

                        val targetAudioSuffix = if (videoExt == "mp4") "m4a" else "webm"
                        val bestAudio = extractor.audioStreams
                            .filter { it.format?.suffix == targetAudioSuffix }
                            .maxByOrNull { it.averageBitrate }
                            ?: throw Exception("No compatible audio found")

                        val audioTemp = File(appDir, "temp_a_${System.currentTimeMillis()}.${bestAudio.format?.suffix}")

                        try {
                            downloadStream(bestAudio, audioTemp, this@callbackFlow, "Downloading Audio Track...")

                            trySend(DownloadStatus.Progress(0f, "Merging Audio & Video..."))

                            val muxerFormat = if (videoExt == "mp4") MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4 else MediaMuxer.OutputFormat.MUXER_OUTPUT_WEBM
                            muxAudioVideo(audioTemp.absolutePath, videoTemp.absolutePath, outputFile.absolutePath, muxerFormat)

                            trySend(DownloadStatus.Success(outputFile))
                        } finally {
                            audioTemp.delete()
                        }
                    } finally {
                        videoTemp.delete()
                    }
                }
            }
            close()
        } catch (e: Exception) {
            e.printStackTrace()
            trySend(DownloadStatus.Error(e.message ?: "Unknown error"))
            close()
        }
        awaitClose { }
    }.flowOn(Dispatchers.IO)

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
        val request = Request.Builder().url(stream.content).build()
        val response = downloadClient.newCall(request).execute()

        if (!response.isSuccessful) throw Exception("Network error: ${response.code}")
        val body = response.body ?: throw Exception("Empty body")

        val totalLength = body.contentLength()
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
                        // Update less frequently to save UI performance (every 2%)
                        if (progress >= lastProgress + 2) {
                            lastProgress = progress
                            flow.trySend(DownloadStatus.Progress(progress.toFloat(), "$statusPrefix $progress%"))
                        }
                    }
                    bytes = input.read(buffer)
                }
            }
        }
    }

    private fun muxAudioVideo(audioPath: String, videoPath: String, outPath: String, format: Int) {
        val videoExtractor = MediaExtractor()
        val audioExtractor = MediaExtractor()
        val muxer = MediaMuxer(outPath, format)

        try {
            videoExtractor.setDataSource(videoPath)
            val videoTrackIndex = findTrackIndex(videoExtractor, "video/")
            videoExtractor.selectTrack(videoTrackIndex)
            val videoFormat = videoExtractor.getTrackFormat(videoTrackIndex)
            val muxerVideoTrackIndex = muxer.addTrack(videoFormat)

            audioExtractor.setDataSource(audioPath)
            val audioTrackIndex = findTrackIndex(audioExtractor, "audio/")
            audioExtractor.selectTrack(audioTrackIndex)
            val audioFormat = audioExtractor.getTrackFormat(audioTrackIndex)
            val muxerAudioTrackIndex = muxer.addTrack(audioFormat)

            muxer.start()

            val buffer = ByteBuffer.allocate(1024 * 1024)
            val bufferInfo = MediaCodec.BufferInfo()

            copyTrack(videoExtractor, muxer, muxerVideoTrackIndex, buffer, bufferInfo)
            copyTrack(audioExtractor, muxer, muxerAudioTrackIndex, buffer, bufferInfo)
        } catch (e: Exception) {
            // Delete output file if muxing fails so we don't have a corrupt file
            File(outPath).delete()
            throw e
        } finally {
            try { muxer.stop(); muxer.release() } catch (e: Exception) {}
            try { videoExtractor.release() } catch (e: Exception) {}
            try { audioExtractor.release() } catch (e: Exception) {}
        }
    }

    private fun copyTrack(extractor: MediaExtractor, muxer: MediaMuxer, trackIndex: Int, buffer: ByteBuffer, bufferInfo: MediaCodec.BufferInfo) {
        while (true) {
            val chunkSize = extractor.readSampleData(buffer, 0)
            if (chunkSize > 0) {
                bufferInfo.presentationTimeUs = extractor.sampleTime
                bufferInfo.flags = extractor.sampleFlags // Flags map 1:1 usually
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
