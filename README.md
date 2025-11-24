# YVD - Modern Android YouTube Downloader

![Android](https://img.shields.io/badge/Android-3DDC84?style=for-the-badge&logo=android&logoColor=white)
![Kotlin](https://img.shields.io/badge/Kotlin-0095D5?style=for-the-badge&logo=kotlin&logoColor=white)
![Jetpack Compose](https://img.shields.io/badge/Jetpack%20Compose-4285F4?style=for-the-badge&logo=android&logoColor=white)
![Hilt](https://img.shields.io/badge/Hilt-Dependency%20Injection-orange?style=for-the-badge)
![WorkManager](https://img.shields.io/badge/WorkManager-Background%20Sync-red?style=for-the-badge&logo=android&logoColor=white)

**YVD** is a robust, native Android application built with **Kotlin** and **Jetpack Compose** that allows users to download YouTube videos in various resolutions (360p to 1080p+).

It leverages the power of `yt-dlp` (via JNI) to handle complex media extraction, **FFmpeg** for audio/video merging, and **Aria2c** for high-speed multi-threaded downloading. The app follows **Clean Architecture**, **MVVM**, and utilizes **WorkManager** to ensure downloads continue reliably in the background.

---

## 📱 Screenshots

### App Interface
| Home Screen | Quality Selection | In-App Progress | Download Notification | Download Complete Screen |
|:-----------:|:-----------------:|:-----------------:|:-----------:|:-----------------:|:-----------------:|
| ![Home](https://github.com/user-attachments/assets/bc704ec6-0a91-45db-9675-939d630fcedd) | ![Quality](https://github.com/user-attachments/assets/69d49b42-1784-4118-9a86-cec2e2fd476d) | ![Progress](https://github.com/user-attachments/assets/d64a57c5-9afd-4937-927e-3c804dc8e5f5) | ![Notification](https://github.com/user-attachments/assets/a18fca2c-9fb8-4443-b539-50f25b119929) | ![Complete](https://github.com/user-attachments/assets/09455682-1c92-44b2-9429-8d4b6d4e12da) |

---

## 🛠 Tech Stack & Architecture

This project was built to demonstrate modern Android development best practices.

### 🏗 Architecture
The app follows **Clean Architecture** with the **MVVM (Model-View-ViewModel)** pattern:

* **Presentation Layer:** Jetpack Compose (UI) + ViewModels (State Management).
* **Domain Layer:** Data models and Repository interfaces (Pure Kotlin).
* **Data Layer:** Repository implementations, Native library wrappers, and File handling.
* **Worker Layer:** Android WorkManager for guaranteed background execution.

### 📚 Libraries & Tools
* **Language:** [Kotlin](https://kotlinlang.org/) (100%)
* **UI:** [Jetpack Compose](https://developer.android.com/jetpack/compose) (Material 3 Design)
* **Dependency Injection:** [Hilt](https://dagger.dev/hilt/)
* **Asynchronous Processing:** [Coroutines & Flow](https://kotlinlang.org/docs/coroutines-overview.html)
* **Background Tasks:** [WorkManager](https://developer.android.com/topic/libraries/architecture/workmanager)
* **Image Loading:** [Coil](https://coil-kt.github.io/coil/)
* **Navigation:** Compose Navigation
* **Core Engine:**
    * `youtubedl-android` (Native wrapper for yt-dlp)
    * `ffmpeg` (Media merging)
    * `aria2c` (High-speed downloader)

---

## 🚀 Key Features

* **Smart Clipboard Detection:** Automatically detects copied YouTube links and loads metadata immediately.
* **Background Downloading:** Downloads continue seamlessly even when the app is closed or the screen is off using **WorkManager**.
* **Smart Notifications:** Persistent progress bars in the notification tray and actionable "Success" alerts that play the video instantly when tapped.
* **Format Selection:** Parses available video/audio streams and presents user-friendly resolution options (1080p, 720p, 480p, etc.).
* **High-Performance Downloading:** Uses **Aria2c** external downloader for multi-connection downloading speeds.
* **Audio/Video Merging:** Automatically merges high-quality video streams (which usually lack audio) with the best audio track using **FFmpeg**.
* **Built-in Preview:** Leverages Android `FileProvider` to securely open and play downloaded files immediately within the app.

---

## 🔧 Technical Highlights (Under the Hood)

For recruiters and developers, here are specific technical challenges solved in this project:

### 1. Robust Background Execution
Implemented **WorkManager** with **Foreground Services** to ensure downloads survive app termination.
* **Challenge:** Android 14+ restricts background work and requires strict Foreground Service Types.
* **Solution:** Configured `ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC` and managed Notification lifecycles manually within the Worker to prevent system kills during large file downloads.

### 2. NDK & Native Library Management
Because `yt-dlp` requires Python and native Shared Objects (`.so`), the project manages ABI filters (`x86`, `arm64-v8a`, etc.) to ensure compatibility across emulators and physical devices.
* **Challenge:** Android Gradle Plugin 8.0+ compresses native libs by default, breaking Python extraction.
* **Solution:** Implemented `useLegacyPackaging = true` in Gradle to force native library extraction at install time.

### 3. State Management
The UI is driven by a `Sealed Class` state machine to ensure the UI is always in a valid state.
```kotlin
data class HomeState(
    val isLoading: Boolean = false,
    val videoMetadata: VideoMetadata? = null,
    val downloadProgress: Float = 0f,
    val isDownloading: Boolean = false, // Synced with WorkManager
    val downloadStatusText: String = "",
    val downloadedFile: File? = null
)
```
### 3. Granular Flow Updates
The Repository uses callbackFlow to emit a sealed DownloadStatus class. This allows the UI to reactively differentiate between continuous numeric progress updates, distinct success events (returning a File object), and error states without callback hell.

### 4. Secure File Access
The app targets Android 10+ (Scoped Storage) but utilizes FileProvider to securely share the downloaded file Uri with external video player apps. It grants temporary read permissions via Intent.FLAG_GRANT_READ_URI_PERMISSION, ensuring the app remains secure while interacting with the Android ecosystem.

## 📥 Installation
Clone the repository

Bash

git clone [https://github.com/EngFred/YVD.git](https://github.com/EngFred/YVD.git)
Open in Android Studio (Ladybug or newer recommended).

Sync Gradle to download dependencies.

Run on an Emulator or Physical Device.

Note: If running on an Emulator, ensure it is an x86_64 image with Google Play Services.

## 📝 License
This project is open source and available under the MIT License.

Disclaimer: This application is for educational purposes only. Downloading copyrighted content from YouTube without permission may violate their Terms of Service. Please use responsibly.
