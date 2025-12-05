package com.engfred.yvd.util

import android.content.Context
import android.content.Intent
import androidx.core.net.toUri

fun openYoutube(
    context: Context
) {
    try {
        val packageName = "com.google.android.youtube"
        val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName)
        if (launchIntent != null) {
            context.startActivity(launchIntent)
        } else {
            // Fallback to browser
            val webIntent = Intent(Intent.ACTION_VIEW, "https://www.youtube.com".toUri())
            context.startActivity(webIntent)
        }
    } catch (e: Exception) {
        // Ultimate fallback
        val webIntent = Intent(Intent.ACTION_VIEW, "https://www.youtube.com".toUri())
        context.startActivity(webIntent)
    }
}