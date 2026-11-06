package com.swipegallery.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import coil.Coil
import coil.ImageLoader
import coil.decode.VideoFrameDecoder

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Lets GalleryImage render a frame of a video as its thumbnail.
        Coil.setImageLoader(ImageLoader.Builder(applicationContext).components { add(VideoFrameDecoder.Factory()) }.build())
        window.statusBarColor = android.graphics.Color.rgb(246, 246, 244)
        window.navigationBarColor = android.graphics.Color.rgb(246, 246, 244)
        window.decorView.systemUiVisibility = android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or android.view.View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        setContent { GalleryApp() }
    }
}
