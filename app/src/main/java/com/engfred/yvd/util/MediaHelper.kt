package com.engfred.yvd.util

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.media.ThumbnailUtils
import android.provider.MediaStore
import androidx.core.content.FileProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Shared utility for handling media file actions (Open/Play, Share).
 * Injected into ViewModels to keep UI logic clean and DRY.
 */
@Singleton
class MediaHelper @Inject constructor(
    @ApplicationContext private val context: Context
) {

    fun openMediaFile(file: File) {
        if (!file.exists()) throw Exception("File not found")

        try {
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
            val extension = file.extension.lowercase()
            val mimeType = when(extension) {
                "m4a", "mp3", "wav", "ogg" -> "audio/*"
                else -> "video/*"
            }

            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mimeType)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) // Required when starting from App Context
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
            val mimeType = when(extension) {
                "m4a", "mp3", "wav", "ogg" -> "audio/*"
                else -> "video/*"
            }

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
        /**
         * Extracts album art from an audio file.
         * Returns ByteArray (which Coil can load directly).
         */
        suspend fun getAudioArtwork(file: File): ByteArray? = withContext(Dispatchers.IO) {
            val retriever = MediaMetadataRetriever()
            return@withContext try {
                retriever.setDataSource(file.absolutePath)
                retriever.embeddedPicture
            } catch (e: Exception) {
                e.printStackTrace()
                null
            } finally {
                try {
                    retriever.release()
                } catch (e: Exception) {
                    // Ignore release errors
                }
            }
        }

        /**
         * Extracts a thumbnail from a video file.
         * Returns a Bitmap.
         */
        suspend fun getVideoThumbnail(file: File): Bitmap? = withContext(Dispatchers.IO) {
            return@withContext try {
                // MINI_KIND corresponds to 512 x 384
                ThumbnailUtils.createVideoThumbnail(
                    file.absolutePath,
                    MediaStore.Video.Thumbnails.MINI_KIND
                )
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
    }
}
