package com.swipegallery.app

import android.net.Uri

data class Photo(val id: Long, val uri: Uri, val name: String, val album: String, val date: Long)
enum class Page { Photos, Albums, Swipe, Trash }
