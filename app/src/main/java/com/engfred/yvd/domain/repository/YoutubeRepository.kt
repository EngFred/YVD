package com.engfred.yvd.domain.repository

import com.engfred.yvd.common.Resource
import com.engfred.yvd.data.repository.DownloadStatus
import com.engfred.yvd.domain.model.VideoMetadata
import kotlinx.coroutines.flow.Flow

interface YoutubeRepository {
    fun getVideoMetadata(url: String): Flow<Resource<VideoMetadata>>
    fun downloadVideo(url: String, formatId: String, title: String): Flow<DownloadStatus>
}