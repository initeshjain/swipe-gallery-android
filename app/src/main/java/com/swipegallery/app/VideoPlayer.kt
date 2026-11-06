package com.swipegallery.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.annotation.OptIn
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.media3.common.AudioAttributes
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView

/** Full-screen player shown above the gallery. Back (button or system) closes it. */
@OptIn(UnstableApi::class)
@Composable
internal fun VideoPlayer(video: Photo, onClose: () -> Unit) {
    val context = LocalContext.current
    val player = remember(video.id) {
        ExoPlayer.Builder(context)
            .setAudioAttributes(AudioAttributes.DEFAULT, /* handleAudioFocus = */ true)
            .setHandleAudioBecomingNoisy(true)
            .build()
            .apply {
                setMediaItem(MediaItem.fromUri(video.uri))
                prepare()
                playWhenReady = true
            }
    }
    DisposableEffect(player) { onDispose { player.release() } }
    LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) { player.pause() }

    // The pointerInput claims touches in the overlay's empty areas so they don't reach the gallery underneath.
    Box(Modifier.fillMaxSize().background(Color.Black).pointerInput(Unit) { awaitPointerEventScope { while (true) awaitPointerEvent() } }) {
        AndroidView(
            factory = { PlayerView(it).apply { useController = true } },
            update = { it.player = player },
            modifier = Modifier.fillMaxSize().systemBarsPadding()
        )
        IconButton(
            onClick = onClose,
            modifier = Modifier.align(Alignment.TopStart).statusBarsPadding().padding(10.dp).clip(CircleShape).background(Color.Black.copy(.38f))
        ) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Close video", tint = Color.White) }
    }
}
