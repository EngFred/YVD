# Hilt
-keep class com.engfred.yvd.YVDApplication{ *; }
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }

# Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}

# YouTubeDL-Android (Crucial to prevent crashes)
-keep class com.yausername.youtubedl_android.** { *; }
-keep class com.yausername.aria2c.** { *; }
-keep class com.yausername.ffmpeg.** { *; }
-keep interface com.yausername.youtubedl_android.** { *; }