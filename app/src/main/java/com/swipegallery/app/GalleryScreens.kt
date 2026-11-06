package com.swipegallery.app

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.IntentSenderRequest
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Swipe
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import coil.request.ImageRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

@Composable
internal fun Header(page: Page, count: Int, album: String?, onSettings: () -> Unit, onBack: () -> Unit) {
    Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 22.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        if (album != null || page == Page.Albums) IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") }
        Column(Modifier.weight(1f)) {
            Text(when { album != null -> album; page == Page.Photos -> "Your gallery"; page == Page.Albums -> "Albums"; page == Page.Swipe -> "Quick sort"; else -> "Recently deleted" }, fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Ink)
            Text(when { album != null -> "ALBUM"; page == Page.Photos -> "$count items · all memories"; page == Page.Albums -> "YOUR COLLECTIONS"; page == Page.Swipe -> "A LITTLE TIDY, ONE SWIPE AT A TIME"; else -> "RESTORE WITHIN 30 DAYS" }, fontSize = 10.sp, letterSpacing = 1.2.sp, color = Muted, fontWeight = FontWeight.SemiBold)
        }
        IconButton(onClick = onSettings, modifier = Modifier.size(42.dp).clip(CircleShape).background(Color(0xFFE6EFE9))) {
            Icon(Icons.Rounded.Settings, "Settings", tint = Green, modifier = Modifier.size(21.dp))
        }
    }
}

@Composable
internal fun MediaFilterBar(selected: MediaFilter, count: Int, onFilter: (MediaFilter) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(start = 18.dp, end = 12.dp, top = 2.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("$count ITEMS", Modifier.weight(1f), fontSize = 10.sp, letterSpacing = 1.3.sp, fontWeight = FontWeight.Bold, color = Muted)
        Row(Modifier.clip(RoundedCornerShape(50)).background(Color(0xFFEAEAE6)).padding(3.dp)) {
            MediaFilter.entries.forEach { filter ->
                val active = filter == selected
                Text(
                    filter.label,
                    Modifier.clip(RoundedCornerShape(50)).background(if (active) Card else Color.Transparent).clickable { onFilter(filter) }.padding(horizontal = 13.dp, vertical = 6.dp),
                    fontSize = 12.sp,
                    fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
                    color = if (active) Green else Muted
                )
            }
        }
    }
}

@Composable
internal fun VideoBadge(durationMs: Long, modifier: Modifier = Modifier) {
    Row(modifier.clip(RoundedCornerShape(6.dp)).background(Color.Black.copy(.55f)).padding(horizontal = 5.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Rounded.PlayArrow, "Video", tint = Color.White, modifier = Modifier.size(13.dp))
        if (durationMs > 0) Text(formatDuration(durationMs), color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
internal fun PhotoGrid(
    photos: List<Photo>,
    filter: MediaFilter,
    onFilter: (MediaFilter) -> Unit,
    selectedIds: Set<Long>,
    onPhoto: (Photo) -> Unit,
    onLongPress: (Photo) -> Unit,
    empty: Boolean,
) {
    val selecting = selectedIds.isNotEmpty()
    Column(Modifier.fillMaxSize()) {
        MediaFilterBar(filter, photos.size, onFilter)
        if (photos.isEmpty() && empty) {
            when (filter) {
                MediaFilter.All -> EmptyState(Icons.Rounded.Image, "A fresh start", "Your photos and videos will show up here once gallery access is on.", null)
                MediaFilter.Photos -> EmptyState(Icons.Rounded.Image, "No photos here", "Photos you take or save will show up here.", null)
                MediaFilter.Videos -> EmptyState(Icons.Rounded.Videocam, "No videos here", "Videos you record or save will show up here.", null)
            }
        } else {
            LazyVerticalGrid(columns = GridCells.Fixed(3), contentPadding = PaddingValues(start = 3.dp, end = 3.dp, bottom = 18.dp), horizontalArrangement = Arrangement.spacedBy(3.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                items(photos, key = { it.id }) { photo ->
                    val selected = photo.id in selectedIds
                    val inset by animateFloatAsState(if (selected) .86f else 1f, label = "selection inset")
                    Box(Modifier.aspectRatio(1f).background(if (selected) Color(0xFFE6EFE9) else Color.Transparent).combinedClickable(onLongClick = { onLongPress(photo) }) { onPhoto(photo) }) {
                        Box(Modifier.fillMaxSize().graphicsLayer { scaleX = inset; scaleY = inset }.clip(RoundedCornerShape(if (selected) 10.dp else 5.dp))) {
                            GalleryImage(photo.uri, photo.name, Modifier.fillMaxSize())
                            if (photo.isVideo) VideoBadge(photo.durationMs, Modifier.align(Alignment.BottomEnd).padding(5.dp))
                        }
                        if (selecting) SelectionMark(selected, Modifier.align(Alignment.TopEnd).padding(6.dp))
                    }
                }
            }
        }
    }
}

@Composable
internal fun Albums(photos: List<Photo>, onAlbum: (String) -> Unit) {
    val albums = photos.groupBy { it.album }.toList().sortedByDescending { it.second.size }
    if (albums.isEmpty()) { EmptyState(Icons.Rounded.Folder, "No albums yet", "Albums from your device will appear here.", null); return }
    LazyVerticalGrid(columns = GridCells.Fixed(2), contentPadding = PaddingValues(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        items(albums, key = { it.first }) { (name, items) ->
            Column(Modifier.clickable { onAlbum(name) }) {
                GalleryImage(items.first().uri, name, Modifier.fillMaxWidth().aspectRatio(0.92f).clip(RoundedCornerShape(18.dp)))
                Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) { Text(name, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis); Text("${items.size} items", fontSize = 12.sp, color = Muted) }
                    Icon(Icons.Rounded.ChevronRight, null, tint = Muted)
                }
            }
        }
    }
}

@Composable
internal fun SwipePage(photos: List<Photo>, onKeep: (Photo) -> Unit, onTrash: (Photo) -> Unit, onOpen: (Photo) -> Unit) {
    var selectedAlbum by remember { mutableStateOf<String?>(null) }
    var albumMenuOpen by remember { mutableStateOf(false) }
    val albums = remember(photos) { photos.groupBy { it.album }.toList().sortedByDescending { it.second.size } }
    val albumPhotos = remember(photos, selectedAlbum) { photos.filter { selectedAlbum == null || it.album == selectedAlbum } }
    val queue = remember(selectedAlbum) { mutableStateListOf<Photo>().apply { addAll(albumPhotos) } }
    val total = remember(selectedAlbum) { albumPhotos.size }
    var drag by remember { mutableFloatStateOf(0f) }
    var settling by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val rotation by animateFloatAsState(drag / 28f, label = "card rotation")
    val current = queue.firstOrNull()
    Column(Modifier.fillMaxSize().padding(horizontal = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("SORT ALBUM", fontSize = 10.sp, letterSpacing = 1.1.sp, color = Muted, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            Box {
                OutlinedButton(onClick = { albumMenuOpen = true }, shape = RoundedCornerShape(14.dp), contentPadding = PaddingValues(horizontal = 12.dp, vertical = 7.dp)) {
                    Icon(Icons.Rounded.Folder, null, tint = Green, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(7.dp))
                    Text(selectedAlbum ?: "All albums", maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 12.sp)
                    Spacer(Modifier.width(2.dp))
                    Icon(Icons.Rounded.ChevronRight, null, modifier = Modifier.size(18.dp).rotate(90f))
                }
                DropdownMenu(expanded = albumMenuOpen, onDismissRequest = { albumMenuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text("All albums (${photos.size})", maxLines = 1) },
                        leadingIcon = { Icon(Icons.Rounded.GridView, null) },
                        trailingIcon = { if (selectedAlbum == null) Icon(Icons.Rounded.Check, null, tint = Green) },
                        onClick = { selectedAlbum = null; albumMenuOpen = false }
                    )
                    albums.forEach { (name, items) ->
                        DropdownMenuItem(
                            text = { Text("$name (${items.size})", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            leadingIcon = { Icon(Icons.Rounded.Folder, null) },
                            trailingIcon = { if (selectedAlbum == name) Icon(Icons.Rounded.Check, null, tint = Green) },
                            onClick = { selectedAlbum = name; albumMenuOpen = false }
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(9.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Swipe, null, tint = Green, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(8.dp))
            Text(if (current == null) "ALL CAUGHT UP" else "${queue.size} LEFT TO SORT", fontSize = 10.sp, letterSpacing = 1.2.sp, color = Muted, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f)); Text("${(total - queue.size).coerceAtMost(total)} / $total", color = Muted, fontSize = 12.sp)
        }
        Spacer(Modifier.height(13.dp))
        if (current != null) {
            Box(Modifier.weight(1f).fillMaxWidth().padding(bottom = 14.dp), contentAlignment = Alignment.Center) {
                Box(Modifier.fillMaxSize().padding(horizontal = 10.dp, vertical = 8.dp).clip(RoundedCornerShape(30.dp)).background(Color(0xFFE6E5DF)))
                Box(Modifier.fillMaxSize().offset { IntOffset(drag.roundToInt(), 0) }.rotate(rotation).clip(RoundedCornerShape(30.dp)).background(Card).pointerInput(current.id, "tap") {
                    // A drag consumes the pointer, so this only fires for a clean tap.
                    detectTapGestures(onTap = { if (!settling) onOpen(current) })
                }.pointerInput(current.id) {
                    detectDragGestures(onDragEnd = {
                        if (settling) return@detectDragGestures
                        val decision = when { drag > 120 -> 1; drag < -120 -> -1; else -> 0 }
                        settling = true
                        scope.launch {
                            val motion = Animatable(drag)
                            if (decision == 0) {
                                motion.animateTo(0f, spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)) { drag = value }
                            } else {
                                val exitX = size.width.toFloat() * if (decision > 0) 1.35f else -1.35f
                                motion.animateTo(exitX, tween(260, easing = FastOutSlowInEasing)) { drag = value }
                                if (decision > 0) onKeep(current) else onTrash(current)
                                drag = 0f
                                queue.remove(current)
                            }
                            settling = false
                        }
                    }, onDragCancel = {
                        if (!settling) {
                            scope.launch {
                                val motion = Animatable(drag)
                                motion.animateTo(0f, spring(dampingRatio = Spring.DampingRatioNoBouncy)) { drag = value }
                            }
                        }
                    }) { change, amount ->
                        change.consume()
                        if (!settling) drag += amount.x
                    }
                }) {
                    GalleryImage(current.uri, current.name, Modifier.fillMaxSize())
                    if (current.isVideo) VideoBadge(current.durationMs, Modifier.align(Alignment.BottomEnd).padding(22.dp))
                    if (drag > 35) SwipeStamp("KEEP", Green, Modifier.align(Alignment.TopStart).padding(22.dp).rotate(-12f))
                    if (drag < -35) SwipeStamp("TRASH", Coral, Modifier.align(Alignment.TopEnd).padding(22.dp).rotate(12f))
                    Box(Modifier.fillMaxWidth().align(Alignment.BottomCenter).height(130.dp).background(Color.Black.copy(alpha = .42f)))
                    Column(Modifier.align(Alignment.BottomStart).padding(22.dp)) {
                        Text(SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date(current.date * 1000)), color = Color.White.copy(.75f), fontSize = 12.sp)
                        Text(current.name, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            Text("TAP TO VIEW  ·  RIGHT TO KEEP  ·  LEFT TO TRASH", fontSize = 9.sp, letterSpacing = 1.1.sp, color = Muted, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(14.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                RoundAction(Icons.Rounded.DeleteOutline, "Move to trash", Coral) { onTrash(current); queue.remove(current) }
                Spacer(Modifier.width(34.dp))
                RoundAction(Icons.Rounded.Check, "Keep photo", Green, large = true) { onKeep(current); queue.remove(current) }
            }
            Spacer(Modifier.height(14.dp))
        } else {
            val title = selectedAlbum?.let { "All sorted in $it" } ?: "Nicely done"
            val body = selectedAlbum?.let { "You’re all caught up in this album. Choose another album above to keep sorting." }
                ?: "You’re all caught up. Your keepers are right where they belong."
            EmptyState(Icons.Rounded.Check, title, body, null)
        }
    }
}

@Composable
internal fun SwipeStamp(label: String, color: Color, modifier: Modifier = Modifier) {
    Text(label, modifier.clip(RoundedCornerShape(9.dp)).background(Color.White.copy(.9f)).padding(horizontal = 14.dp, vertical = 8.dp), color = color, fontWeight = FontWeight.ExtraBold, fontSize = 22.sp, letterSpacing = 1.sp)
}

@Composable
internal fun RoundAction(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, color: Color, large: Boolean = false, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        FilledIconButton(onClick = onClick, modifier = Modifier.size(if (large) 66.dp else 54.dp), shape = CircleShape, colors = IconButtonDefaults.filledIconButtonColors(containerColor = if (large) Green else Card, contentColor = if (large) Color.White else color)) { Icon(icon, label, modifier = Modifier.size(if (large) 29.dp else 23.dp)) }
        Spacer(Modifier.height(5.dp)); Text(label, fontSize = 10.sp, color = Muted)
    }
}

@Composable
internal fun TrashPage(photos: List<Photo>, onRestore: (Photo) -> Unit, onDelete: (Photo) -> Unit, onEmpty: (Set<String>) -> Unit) {
    if (photos.isEmpty()) { EmptyState(Icons.Rounded.DeleteOutline, "Your bin is empty", "Photos you swipe away will wait here in case you change your mind.", null); return }
    Column {
        Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("${photos.size} items", color = Muted, fontSize = 13.sp, modifier = Modifier.weight(1f))
            TextButton(onClick = { onEmpty(photos.map { it.id.toString() }.toSet()) }) { Icon(Icons.Rounded.DeleteOutline, null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(5.dp)); Text("Empty bin", color = Coral) }
        }
        LazyVerticalGrid(columns = GridCells.Fixed(2), contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(photos, key = { it.id }) { photo ->
                Column(Modifier.clip(RoundedCornerShape(16.dp)).background(Card)) {
                    Box(Modifier.fillMaxWidth().aspectRatio(1f)) {
                        GalleryImage(photo.uri, photo.name, Modifier.fillMaxSize())
                        if (photo.isVideo) VideoBadge(photo.durationMs, Modifier.align(Alignment.BottomEnd).padding(6.dp))
                    }
                    Text(photo.name, Modifier.padding(start = 11.dp, end = 11.dp, top = 9.dp), maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    Row(Modifier.fillMaxWidth().padding(horizontal = 5.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                        TextButton(onClick = { onRestore(photo) }) { Icon(Icons.Rounded.Restore, null, modifier = Modifier.size(17.dp)); Spacer(Modifier.width(3.dp)); Text("Restore", fontSize = 11.sp) }
                        TextButton(onClick = { onDelete(photo) }) { Icon(Icons.Rounded.DeleteOutline, null, tint = Coral, modifier = Modifier.size(17.dp)); Text("Delete", color = Coral, fontSize = 11.sp) }
                    }
                }
            }
        }
    }
}

@Composable
internal fun ViewerItem(item: Photo, modifier: Modifier, onPlay: (() -> Unit)?) {
    Box(modifier, contentAlignment = Alignment.Center) {
        GalleryImage(item.uri, item.name, Modifier.fillMaxSize(), ContentScale.Fit)
        if (item.isVideo) {
            FilledIconButton(
                onClick = { onPlay?.invoke() },
                enabled = onPlay != null,
                modifier = Modifier.size(72.dp),
                shape = CircleShape,
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = Color.Black.copy(.5f), contentColor = Color.White,
                    disabledContainerColor = Color.Black.copy(.5f), disabledContentColor = Color.White
                )
            ) { Icon(Icons.Rounded.PlayArrow, "Play video", modifier = Modifier.size(40.dp)) }
        }
    }
}

@Composable
internal fun PhotoDetail(initialPhoto: Photo, photos: List<Photo>, onClose: () -> Unit, onPlay: (Photo) -> Unit, onTrash: (Photo) -> Unit) {
    val pagerState = rememberPagerState(initialPage = photos.indexOfFirst { it.id == initialPhoto.id }.coerceAtLeast(0)) { photos.size }
    val scope = rememberCoroutineScope()
    // Vertical offset of the current item while it is being swiped up to the bin.
    var liftY by remember { mutableFloatStateOf(0f) }
    var removing by remember { mutableStateOf(false) }

    BoxWithConstraints(Modifier.fillMaxSize().background(Color.Black)) {
        val heightPx = with(LocalDensity.current) { maxHeight.toPx() }
        val currentIndex = pagerState.currentPage.coerceIn(0, (photos.size - 1).coerceAtLeast(0))
        val photo = photos.getOrNull(currentIndex) ?: initialPhoto

        fun springBack() {
            scope.launch { Animatable(liftY).animateTo(0f, spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)) { liftY = value } }
        }
        fun trashCurrent() {
            if (removing) return
            val target = photo
            removing = true
            scope.launch {
                Animatable(liftY).animateTo(-heightPx, tween(220, easing = FastOutSlowInEasing)) { liftY = value }
                onTrash(target)
                // The pager keeps its index, so the next item slides into this slot (or the previous one if this was the last).
                liftY = 0f
                removing = false
            }
        }

        Box(
            Modifier.fillMaxSize().pointerInput(photo.id, heightPx) {
                val tracker = VelocityTracker()
                detectVerticalDragGestures(
                    onDragStart = { tracker.resetTracking() },
                    onDragEnd = {
                        if (!removing) {
                            val velocityY = tracker.calculateVelocity().y
                            if (liftY < -heightPx * .22f || (velocityY < -1800f && liftY < -48f)) trashCurrent() else springBack()
                        }
                    },
                    onDragCancel = { if (!removing) springBack() },
                    onVerticalDrag = { change, amount ->
                        if (!removing && !pagerState.isScrollInProgress) {
                            change.consume()
                            tracker.addPosition(change.uptimeMillis, change.position)
                            // Upward drags follow the finger; downward drags get heavy resistance.
                            liftY = (liftY + if (liftY + amount > 0f) amount * .2f else amount).coerceIn(-heightPx, heightPx * .15f)
                        }
                    }
                )
            }
        ) {
            HorizontalPager(
                state = pagerState,
                key = { photos.getOrNull(it)?.id ?: it },
                beyondViewportPageCount = 1,
                userScrollEnabled = !removing && liftY == 0f,
                modifier = Modifier.fillMaxSize()
            ) { page ->
                photos.getOrNull(page)?.let { item ->
                    ViewerItem(
                        item,
                        Modifier.fillMaxSize().graphicsLayer {
                            if (page == pagerState.currentPage) {
                                val progress = (-liftY / heightPx).coerceIn(0f, 1f)
                                translationY = liftY
                                alpha = 1f - progress * .6f
                                scaleX = 1f - progress * .12f
                                scaleY = 1f - progress * .12f
                            }
                        },
                        onPlay = { onPlay(item) }
                    )
                }
            }
        }
        val liftProgress = (-liftY / (heightPx * .22f)).coerceIn(0f, 1f)
        if (liftProgress > 0f) {
            Box(
                Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 72.dp).size(64.dp)
                    .graphicsLayer { alpha = liftProgress; scaleX = .7f + liftProgress * .3f; scaleY = .7f + liftProgress * .3f }
                    .clip(CircleShape).background(if (liftProgress >= 1f) Coral else Color.Black.copy(.5f)),
                contentAlignment = Alignment.Center
            ) { Icon(Icons.Rounded.DeleteOutline, "Release to move to bin", tint = Color.White, modifier = Modifier.size(30.dp)) }
        }
        Row(
            Modifier.fillMaxWidth().align(Alignment.TopCenter).statusBarsPadding().padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onClose, modifier = Modifier.clip(CircleShape).background(Color.Black.copy(.38f))) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back", tint = Color.White)
            }
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(photo.name, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                Text("${currentIndex + 1} of ${photos.size}", color = Color.White.copy(.72f), fontSize = 12.sp)
            }
            IconButton(onClick = { if (!pagerState.isScrollInProgress) trashCurrent() }, modifier = Modifier.clip(CircleShape).background(Color.Black.copy(.38f))) {
                Icon(Icons.Rounded.DeleteOutline, "Move to trash", tint = Color.White)
            }
        }
        if (currentIndex > 0) {
            IconButton(onClick = { scope.launch { pagerState.animateScrollToPage(currentIndex - 1) } }, modifier = Modifier.align(Alignment.CenterStart).padding(8.dp).clip(CircleShape).background(Color.Black.copy(.38f))) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Previous", tint = Color.White)
            }
        }
        if (currentIndex < photos.lastIndex) {
            IconButton(onClick = { scope.launch { pagerState.animateScrollToPage(currentIndex + 1) } }, modifier = Modifier.align(Alignment.CenterEnd).padding(8.dp).clip(CircleShape).background(Color.Black.copy(.38f))) {
                Icon(Icons.AutoMirrored.Rounded.ArrowForward, "Next", tint = Color.White)
            }
        }
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(Color.Black.copy(.35f)).navigationBarsPadding().padding(vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date(photo.date * 1000)) +
                    if (photo.isVideo && photo.durationMs > 0) "  ·  ${formatDuration(photo.durationMs)}" else "",
                color = Color.White.copy(.85f), fontSize = 12.sp
            )
            Text("SWIPE UP TO DELETE", color = Color.White.copy(.5f), fontSize = 9.sp, letterSpacing = 1.1.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
internal fun BottomBar(page: Page, onPage: (Page) -> Unit) {
    Column(Modifier.fillMaxWidth().background(Card)) {
        NavigationBar(containerColor = Card, tonalElevation = 0.dp, windowInsets = WindowInsets.navigationBars) {
            val entries = listOf(Triple(Page.Photos, Icons.Rounded.GridView, "Photos"), Triple(Page.Albums, Icons.Rounded.Album, "Albums"), Triple(Page.Swipe, Icons.Rounded.Swipe, "Sort"), Triple(Page.Trash, Icons.Rounded.DeleteOutline, "Bin"))
            entries.forEach { (target, icon, label) ->
                NavigationBarItem(selected = page == target, onClick = { onPage(target) }, icon = { Icon(icon, label) }, label = { Text(label, fontSize = 10.sp) }, colors = NavigationBarItemDefaults.colors(selectedIconColor = Green, selectedTextColor = Green, indicatorColor = Color(0xFFE7F1EC), unselectedIconColor = Muted, unselectedTextColor = Muted))
            }
        }
    }
}

@Composable
internal fun PermissionEmpty(blocked: Boolean, onGrant: () -> Unit, onOpenSettings: () -> Unit) {
    if (blocked) {
        EmptyState(
            Icons.Rounded.Image,
            "Photo access is off",
            "Android won't ask again from inside the app. Open Settings, tap Permissions › Photos and videos, and choose Allow.",
            onOpenSettings,
            actionLabel = "Open settings",
            secondaryLabel = "Try again",
            secondaryAction = onGrant
        )
    } else {
        EmptyState(Icons.Rounded.Image, "Your memories, together", "Allow access to your photos and videos to browse albums, sort favorites, and tidy up.", onGrant)
    }
}

@Composable
internal fun EmptyState(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    body: String,
    action: (() -> Unit)?,
    actionLabel: String = "Allow photo access",
    secondaryLabel: String? = null,
    secondaryAction: (() -> Unit)? = null,
) {
    Column(Modifier.fillMaxSize().padding(30.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Box(Modifier.size(78.dp).clip(CircleShape).background(Color(0xFFE6EFE9)), contentAlignment = Alignment.Center) { Icon(icon, null, tint = Green, modifier = Modifier.size(34.dp)) }
        Spacer(Modifier.height(20.dp)); Text(title, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Ink)
        Spacer(Modifier.height(8.dp)); Text(body, color = Muted, fontSize = 14.sp, lineHeight = 21.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        if (action != null) { Spacer(Modifier.height(18.dp)); Button(onClick = action, colors = ButtonDefaults.buttonColors(containerColor = Green), shape = RoundedCornerShape(14.dp)) { Text(actionLabel) } }
        if (secondaryLabel != null && secondaryAction != null) { TextButton(onClick = secondaryAction) { Text(secondaryLabel, color = Green) } }
    }
}
