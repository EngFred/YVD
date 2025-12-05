package com.engfred.yvd.domain.repository

import com.engfred.yvd.common.Resource
import com.engfred.yvd.domain.model.DownloadStatus
import com.engfred.yvd.domain.model.VideoMetadata
import kotlinx.coroutines.flow.Flow

interface YoutubeRepository {
    fun getVideoMetadata(url: String): Flow<Resource<VideoMetadata>>

    // Added isAudio flag
    fun downloadVideo(url: String, formatId: String, title: String, isAudio: Boolean): Flow<DownloadStatus>
}