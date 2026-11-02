package com.swipegallery.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage
import coil.request.ImageRequest

internal val Ink = Color(0xFF17191F)
internal val Muted = Color(0xFF858894)
internal val Canvas = Color(0xFFF6F6F4)
internal val Green = Color(0xFF237A61)
internal val Coral = Color(0xFFE66C5B)
internal val Card = Color.White

@Composable
internal fun GalleryImage(model: Any, description: String, modifier: Modifier = Modifier, scale: ContentScale = ContentScale.Crop) {
    val context = LocalContext.current
    val request = remember(model) { ImageRequest.Builder(context).data(model).crossfade(180).build() }
    AsyncImage(model = request, contentDescription = description, modifier = modifier, contentScale = scale)
}
