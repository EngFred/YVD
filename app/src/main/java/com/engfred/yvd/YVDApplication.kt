package com.engfred.yvd

import android.app.Application
import android.util.Log
import com.yausername.aria2c.Aria2c
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class YVDApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        try {
            // 1. Initialize the Core Engine
            YoutubeDL.getInstance().init(this)

            // 2. Initialize FFmpeg (Required for merging Audio+Video in 1080p+)
            FFmpeg.getInstance().init(this)

            // 3. Initialize Aria2c (Required for high-speed connectivity)
            Aria2c.getInstance().init(this)

            Log.d("YVD_INIT", "Engines initialized successfully")
        } catch (e: Exception) {
            Log.e("YVD_INIT", "Failed to initialize dependencies", e)
        }
    }
}