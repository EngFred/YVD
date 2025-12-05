package com.engfred.yvd.data.repository

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
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
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.ProducerScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.services.youtube.extractors.YoutubeStreamExtractor
import org.schabi.newpipe.extractor.stream.Stream
import org.schabi.newpipe.extractor.stream.VideoStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject

class YoutubeRepositoryImpl @Inject constructor(
    private val context: Context
) : YoutubeRepository {

    private val TAG = "YVD_REPO"
    private val downloaderImpl = DownloaderImpl()

    init {
        NewPipe.init(downloaderImpl)
        Log.d(TAG, "NewPipe Initialized")
    }

    private val downloadClient: OkHttpClient = downloaderImpl.getOkHttpClient()

    override fun getVideoMetadata(url: String): Flow<Resource<VideoMetadata>> = flow {
        Log.d(TAG, "Fetching metadata for: $url")
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
        Log.d(TAG, "Starting download: $title (AudioOnly: $isAudio)")
        try {
            trySend(DownloadStatus.Progress(0f, "Initializing..."))

            val extractor = ServiceList.YouTube.getStreamExtractor(url) as YoutubeStreamExtractor
            extractor.fetchPage()

            val downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val appDir = File(downloadDir, "YVDownloader")
            if (!appDir.exists()) appDir.mkdirs()

            val cleanTitle = title.replace("[^a-zA-Z0-9.-]".toRegex(), "_").take(50)

            if (isAudio) {
                // AUDIO LOGIC
                val targetStream = extractor.audioStreams.find { it.itag.toString() == formatId }
                    ?: throw Exception("Audio stream not found")

                val fileName = "${cleanTitle}.${targetStream.format?.suffix}"
                val outputFile = File(appDir, fileName)

                Log.d(TAG, "Downloading Audio Stream to ${outputFile.name}")
                downloadStreamParallel(targetStream, outputFile, this@callbackFlow, "Downloading Audio...")
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
                    Log.d(TAG, "Downloading Standard Video to ${outputFile.name}")
                    downloadStreamParallel(targetStream, outputFile, this@callbackFlow, "Downloading Video...")
                    trySend(DownloadStatus.Success(outputFile))
                } else {
                    // MUXING LOGIC
                    Log.d(TAG, "Downloading Video-Only Stream (Muxing required)")
                    val videoTemp = File(appDir, "temp_v_${System.currentTimeMillis()}.$videoExt")

                    try {
                        downloadStreamParallel(targetStream, videoTemp, this@callbackFlow, "Downloading Video Track...")

                        val targetAudioSuffix = if (videoExt == "mp4") "m4a" else "webm"
                        val bestAudio = extractor.audioStreams
                            .filter { it.format?.suffix == targetAudioSuffix }
                            .maxByOrNull { it.averageBitrate }
                            ?: throw Exception("No compatible audio found")

                        val audioTemp = File(appDir, "temp_a_${System.currentTimeMillis()}.${bestAudio.format?.suffix}")

                        try {
                            downloadStreamParallel(bestAudio, audioTemp, this@callbackFlow, "Downloading Audio Track...")

                            trySend(DownloadStatus.Progress(0f, "Merging Audio & Video..."))
                            Log.d(TAG, "Starting Muxing process")

                            val muxerFormat = if (videoExt == "mp4") MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4 else MediaMuxer.OutputFormat.MUXER_OUTPUT_WEBM
                            muxAudioVideo(audioTemp.absolutePath, videoTemp.absolutePath, outputFile.absolutePath, muxerFormat)

                            Log.d(TAG, "Muxing complete")
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
            Log.e(TAG, "Download failed: ${e.message}")
            trySend(DownloadStatus.Error(e.message ?: "Unknown error"))
            close()
        }
        awaitClose { }
    }.flowOn(Dispatchers.IO)

    private fun calculateFileSize(bitrate: Long, duration: Long): String {
        val bytes = if (bitrate > 0 && duration > 0) (bitrate * duration) / 8 else -1L
        return if (bytes > 0) "%.1f MB".format(bytes.toDouble() / (1024 * 1024)) else "Unknown"
    }

    private suspend fun downloadStreamParallel(
        stream: Stream,
        file: File,
        flow: ProducerScope<DownloadStatus>,
        statusPrefix: String
    ) = withContext(Dispatchers.IO) {

        var totalLength = -1L

        // Retry loop for getting file size
        for (i in 0..2) {
            try {
                val headRequest = Request.Builder().url(stream.content).head().build()
                val response = downloadClient.newCall(headRequest).execute()
                totalLength = response.header("Content-Length")?.toLongOrNull() ?: -1L
                response.close()
                if (totalLength > 0) break
            } catch (e: Exception) {
                Log.w(TAG, "Failed to get size (attempt $i): ${e.message}")
            }
        }

        Log.d(TAG, "Starting download for ${file.name}. Size: $totalLength bytes")

        // FIX: Lowered Threshold to 1MB (1024 * 1024).
        // This ensures audio files (approx 3MB-5MB) utilize multi-threading.
        if (totalLength < 1 * 1024 * 1024) {
            Log.d(TAG, "File extremely small (<1MB) or unknown size. Using Single Thread.")
            downloadStreamSingle(stream.content, file, totalLength, flow, statusPrefix)
            return@withContext
        }

        val threadCount = 4
        val partSize = totalLength / threadCount
        val downloadedBytes = AtomicLong(0)

        val raf = RandomAccessFile(file, "rw")
        raf.setLength(totalLength)
        raf.close()

        Log.d(TAG, "Using $threadCount threads. Part size: $partSize")

        try {
            coroutineScope {
                val jobs = (0 until threadCount).map { index ->
                    async(Dispatchers.IO) {
                        val start = index * partSize
                        val end = if (index == threadCount - 1) totalLength - 1 else (start + partSize - 1)

                        // Added Retry Logic inside
                        retryOperation(3) {
                            downloadChunk(
                                url = stream.content,
                                file = file,
                                start = start,
                                end = end,
                                totalFileLength = totalLength,
                                atomicProgress = downloadedBytes,
                                flow = flow,
                                statusPrefix = statusPrefix
                            )
                        }
                    }
                }
                jobs.awaitAll()
            }
            Log.d(TAG, "Parallel download complete for ${file.name}")
        } catch (e: Exception) {
            Log.e(TAG, "Parallel download failed: ${e.message}")
            throw e
        }
    }

    // Helper to retry network operations
    private suspend fun retryOperation(times: Int, block: () -> Unit) {
        var currentAttempt = 0
        while (true) {
            try {
                block()
                return
            } catch (e: Exception) {
                currentAttempt++
                if (currentAttempt >= times) throw e
                Log.w("YVD_REPO", "Retrying chunk... ($currentAttempt/$times)")
                delay(1000) // Wait 1 second before retry
            }
        }
    }

    private fun downloadChunk(
        url: String,
        file: File,
        start: Long,
        end: Long,
        totalFileLength: Long,
        atomicProgress: AtomicLong,
        flow: ProducerScope<DownloadStatus>,
        statusPrefix: String
    ) {
        val request = Request.Builder()
            .url(url)
            .header("Range", "bytes=$start-$end")
            .build()

        val raf = RandomAccessFile(file, "rw")
        raf.seek(start)

        try {
            val response = downloadClient.newCall(request).execute()
            if (!response.isSuccessful) throw IOException("Chunk download failed: ${response.code}")

            val body = response.body ?: throw IOException("Empty body")
            val inputStream = body.byteStream()
            val buffer = ByteArray(64 * 1024)
            var bytesRead: Int

            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                raf.write(buffer, 0, bytesRead)
                val currentTotal = atomicProgress.addAndGet(bytesRead.toLong())

                if (totalFileLength > 0) {
                    val progress = ((currentTotal.toFloat() / totalFileLength.toFloat()) * 100).toInt()
                    if (currentTotal % (512 * 1024) < 65536) {
                        flow.trySend(DownloadStatus.Progress(progress.toFloat(), "$statusPrefix $progress%"))
                    }
                }
            }
            response.close()
        } finally {
            try { raf.close() } catch (e: Exception) {}
        }
    }

    private fun downloadStreamSingle(
        url: String,
        file: File,
        knownLength: Long,
        flow: ProducerScope<DownloadStatus>,
        statusPrefix: String
    ) {
        val request = Request.Builder().url(url).build()
        val response = downloadClient.newCall(request).execute()
        if (!response.isSuccessful) throw Exception("Network error: ${response.code}")

        val body = response.body ?: throw Exception("Empty body")
        val totalLength = if (knownLength > 0) knownLength else body.contentLength()

        var bytesCopied: Long = 0
        val buffer = ByteArray(32 * 1024)
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

            val buffer = ByteBuffer.allocate(2 * 1024 * 1024)
            val bufferInfo = MediaCodec.BufferInfo()

            Log.d(TAG, "Muxing Video Track...")
            copyTrack(videoExtractor, muxer, muxerVideoTrackIndex, buffer, bufferInfo)

            Log.d(TAG, "Muxing Audio Track...")
            copyTrack(audioExtractor, muxer, muxerAudioTrackIndex, buffer, bufferInfo)

        } catch (e: Exception) {
            Log.e(TAG, "Muxing failed: ${e.message}")
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
                bufferInfo.flags = extractor.sampleFlags
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