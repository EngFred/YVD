package com.engfred.yvd

import android.app.Application
import android.util.Log
import com.yausername.aria2c.Aria2c
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class YVDApplication : Application() {

    private val TAG = "YVD_APP"

    override fun onCreate() {
        super.onCreate()

        Log.d(TAG, "")
        Log.d(TAG, "========================================")
        Log.d(TAG, "🚀 YVD APPLICATION STARTING")
        Log.d(TAG, "========================================")

        try {
            Log.d(TAG, "🔧 Initializing YoutubeDL...")
            YoutubeDL.getInstance().init(this)
            Log.d(TAG, "✅ YoutubeDL initialized")

            Log.d(TAG, "🔧 Initializing FFmpeg...")
            FFmpeg.getInstance().init(this)
            Log.d(TAG, "✅ FFmpeg initialized")

            Log.d(TAG, "🔧 Initializing Aria2c...")
            Aria2c.getInstance().init(this)
            Log.d(TAG, "✅ Aria2c initialized")

            Log.d(TAG, "")
            Log.d(TAG, "========================================")
            Log.d(TAG, "✅ ALL ENGINES INITIALIZED SUCCESSFULLY")
            Log.d(TAG, "========================================")
            Log.d(TAG, "")

        } catch (e: Exception) {
            Log.e(TAG, "")
            Log.e(TAG, "========================================")
            Log.e(TAG, "❌ CRITICAL FAILURE")
            Log.e(TAG, "========================================")
            Log.e(TAG, "Failed to initialize engines")
            Log.e(TAG, "Exception: ${e.javaClass.simpleName}")
            Log.e(TAG, "Message: ${e.message}")
            Log.e(TAG, "Stack trace:", e)
            Log.e(TAG, "========================================")
            Log.e(TAG, "")
        }
    }
}