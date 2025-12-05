package com.engfred.yvd.domain.model

/**
 * Represents the UI-ready data for a YouTube video.
 * This model contains the high-level details and available formats
 * for the user to select.
 *
 * @property id The unique YouTube Video ID.
 * @property title The video title.
 * @property thumbnailUrl The URL of the highest resolution thumbnail available.
 * @property duration The length of the video formatted as a string.
 * @property videoFormats List of available video streams (MP4/WebM).
 * @property audioFormats List of available audio-only streams (M4A/WebM).
 */
data class VideoMetadata(
    val id: String,
    val title: String,
    val thumbnailUrl: String,
    val duration: String,
    val videoFormats: List<VideoFormat>,
    val audioFormats: List<AudioFormat>
)

/**
 * Represents a specific video quality option (e.g., 1080p, 720p).
 */
data class VideoFormat(
    val formatId: String, // Internal ITAG used by YouTube
    val ext: String,
    val resolution: String, // Display string (1080p, 720p)
    val fileSize: String, // Pre-calculated file size string
    val fps: Int          // Frames per second (30, 60)
)

/**
 * Represents a specific audio quality option.
 */
data class AudioFormat(
    val formatId: String,
    val ext: String,
    val bitrate: String,  // Audio quality (e.g., "128kbps")
    val fileSize: String
)