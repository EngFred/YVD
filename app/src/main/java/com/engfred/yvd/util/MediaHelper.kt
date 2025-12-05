package com.engfred.yvd.util

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.media.ThumbnailUtils
import android.provider.MediaStore
import android.util.LruCache
import androidx.core.content.FileProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MediaHelper @Inject constructor(
    @ApplicationContext private val context: Context
) {

    fun openMediaFile(file: File) {
        if (!file.exists()) throw Exception("File not found")
        try {
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
            val extension = file.extension.lowercase()
            val mimeType = if (extension in listOf("m4a", "mp3", "wav", "ogg")) "audio/*" else "video/*"

            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mimeType)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            throw Exception("Could not open file: ${e.message}")
        }
    }

    fun shareMediaFile(file: File) {
        if (!file.exists()) throw Exception("File not found")
        try {
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
            val extension = file.extension.lowercase()
            val mimeType = if (extension in listOf("m4a", "mp3", "wav", "ogg")) "audio/*" else "video/*"

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = mimeType
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            val chooser = Intent.createChooser(shareIntent, "Share Media").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
        } catch (e: Exception) {
            throw Exception("Could not share file: ${e.message}")
        }
    }

    companion object {
        // Simple cache to store up to 50 thumbnails/artworks in memory
        private val thumbnailCache = LruCache<String, Any>(50)

        /**
         * Extracts album art from an audio file.
         * Checks cache first.
         */
        suspend fun getAudioArtwork(file: File): ByteArray? = withContext(Dispatchers.IO) {
            val key = file.absolutePath

            // 1. Check Cache
            thumbnailCache.get(key)?.let { return@withContext it as ByteArray }

            // 2. If not in cache, extract it
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(file.absolutePath)
                val art = retriever.embeddedPicture

                // 3. Save to cache if found
                if (art != null) {
                    thumbnailCache.put(key, art)
                }
                return@withContext art
            } catch (e: Exception) {
                e.printStackTrace()
                return@withContext null
            } finally {
                try { retriever.release() } catch (_: Exception) {}
            }
        }

        /**
         * Extracts a thumbnail from a video file.
         * Checks cache first.
         */
        suspend fun getVideoThumbnail(file: File): Bitmap? = withContext(Dispatchers.IO) {
            val key = file.absolutePath

            // 1. Check Cache
            thumbnailCache.get(key)?.let { return@withContext it as Bitmap }

            // 2. If not in cache, generate it
            try {
                val bitmap = ThumbnailUtils.createVideoThumbnail(
                    file.absolutePath,
                    MediaStore.Video.Thumbnails.MINI_KIND
                )

                // 3. Save to cache if found
                if (bitmap != null) {
                    thumbnailCache.put(key, bitmap)
                }
                return@withContext bitmap
            } catch (e: Exception) {
                e.printStackTrace()
                return@withContext null
            }
        }
    }
}