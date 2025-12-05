package com.engfred.yvd.domain.model

data class VideoMetadata(
    val id: String,
    val title: String,
    val thumbnailUrl: String,
    val duration: String,
    val videoFormats: List<VideoFormat>,
    val audioFormats: List<AudioFormat>
)

data class VideoFormat(
    val formatId: String,
    val ext: String,          // mp4, webm
    val resolution: String,   // 1080p, 720p
    val fileSize: String,     // "12 MB"
    val fps: Int
)

data class AudioFormat(
    val formatId: String,
    val ext: String,          // m4a, webm
    val bitrate: String,      // "128kbps"
    val fileSize: String
)