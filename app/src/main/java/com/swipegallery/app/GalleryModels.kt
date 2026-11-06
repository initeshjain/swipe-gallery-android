package com.swipegallery.app

import android.net.Uri

data class Photo(
    val id: Long,
    val uri: Uri,
    val name: String,
    val album: String,
    val date: Long,
    val isVideo: Boolean = false,
    val durationMs: Long = 0,
    /** MediaStore RELATIVE_PATH (e.g. "DCIM/Camera/"); null before Android 10. */
    val relativePath: String? = null,
)

enum class Page { Photos, Albums, Swipe, Trash }

enum class MediaFilter(val label: String) { All("All"), Photos("Photos"), Videos("Videos") }

internal const val SUPPORT_EMAIL = "jay@noobgeek.in"

internal fun formatDuration(ms: Long): String {
    val totalSeconds = ms / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds) else "%d:%02d".format(minutes, seconds)
}
