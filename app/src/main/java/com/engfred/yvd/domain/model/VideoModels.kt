package com.engfred.yvd.domain.model

data class VideoMetadata(
    val id: String,
    val title: String,
    val thumbnailUrl: String,
    val duration: String,
    val formats: List<VideoFormat>
)

data class VideoFormat(
    val formatId: String,
    val ext: String,          // mp4, webm
    val resolution: String,   // 1080p, 720p
    val fileSize: String,     // "12 MB"
    val fps: Int
)