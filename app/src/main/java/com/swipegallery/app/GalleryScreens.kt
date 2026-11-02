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
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Swipe
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
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
internal fun Header(page: Page, count: Int, album: String?, onBack: () -> Unit) {
    Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 22.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        if (album != null || page == Page.Albums) IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") }
        Column(Modifier.weight(1f)) {
            Text(when { album != null -> album; page == Page.Photos -> "Your gallery"; page == Page.Albums -> "Albums"; page == Page.Swipe -> "Quick sort"; else -> "Recently deleted" }, fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Ink)
            Text(when { album != null -> "ALBUM"; page == Page.Photos -> "$count photos · all memories"; page == Page.Albums -> "YOUR COLLECTIONS"; page == Page.Swipe -> "A LITTLE TIDY, ONE SWIPE AT A TIME"; else -> "RESTORE WITHIN 30 DAYS" }, fontSize = 10.sp, letterSpacing = 1.2.sp, color = Muted, fontWeight = FontWeight.SemiBold)
        }
        Box(Modifier.size(42.dp).clip(CircleShape).background(Color(0xFFE6EFE9)), contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Security, "On device", tint = Green, modifier = Modifier.size(21.dp)) }
    }
}

@Composable
internal fun PhotoGrid(photos: List<Photo>, onPhoto: (Photo) -> Unit, empty: Boolean) {
    if (photos.isEmpty() && empty) { EmptyState(Icons.Rounded.Image, "A fresh start", "Your photos will show up here once gallery access is on.", null); return }
    LazyVerticalGrid(columns = GridCells.Fixed(3), contentPadding = PaddingValues(start = 3.dp, end = 3.dp, bottom = 18.dp), horizontalArrangement = Arrangement.spacedBy(3.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
        item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) { Text("ALL PHOTOS  ·  ${photos.size} ITEMS", Modifier.padding(horizontal = 18.dp, vertical = 9.dp), fontSize = 10.sp, letterSpacing = 1.3.sp, fontWeight = FontWeight.Bold, color = Muted) }
        items(photos, key = { it.id }) { photo -> GalleryImage(photo.uri, photo.name, Modifier.aspectRatio(1f).clip(RoundedCornerShape(5.dp)).clickable { onPhoto(photo) }) }
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
                    Column(Modifier.weight(1f)) { Text(name, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis); Text("${items.size} photos", fontSize = 12.sp, color = Muted) }
                    Icon(Icons.Rounded.ChevronRight, null, tint = Muted)
                }
            }
        }
    }
}

@Composable
internal fun SwipePage(photos: List<Photo>, onKeep: (Photo) -> Unit, onTrash: (Photo) -> Unit) {
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
                Box(Modifier.fillMaxSize().offset { IntOffset(drag.roundToInt(), 0) }.rotate(rotation).clip(RoundedCornerShape(30.dp)).background(Card).pointerInput(current.id) {
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
                    if (drag > 35) SwipeStamp("KEEP", Green, Modifier.align(Alignment.TopStart).padding(22.dp).rotate(-12f))
                    if (drag < -35) SwipeStamp("TRASH", Coral, Modifier.align(Alignment.TopEnd).padding(22.dp).rotate(12f))
                    Box(Modifier.fillMaxWidth().align(Alignment.BottomCenter).height(130.dp).background(Color.Black.copy(alpha = .42f)))
                    Column(Modifier.align(Alignment.BottomStart).padding(22.dp)) {
                        Text(SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date(current.date * 1000)), color = Color.White.copy(.75f), fontSize = 12.sp)
                        Text(current.name, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            Text("SWIPE RIGHT TO KEEP  ·  LEFT TO TRASH", fontSize = 9.sp, letterSpacing = 1.1.sp, color = Muted, fontWeight = FontWeight.Bold)
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
                    GalleryImage(photo.uri, photo.name, Modifier.fillMaxWidth().aspectRatio(1f))
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
internal fun PhotoDetail(initialPhoto: Photo, photos: List<Photo>, onClose: () -> Unit, onTrash: (Photo) -> Unit) {
    var index by remember(photos, initialPhoto.id) {
        mutableIntStateOf(photos.indexOfFirst { it.id == initialPhoto.id }.coerceAtLeast(0))
    }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    var settling by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    BoxWithConstraints(Modifier.fillMaxSize().background(Color.Black)) {
        val widthPx = with(LocalDensity.current) { maxWidth.toPx() }
        val photo = photos.getOrNull(index) ?: initialPhoto
        val previous = photos.getOrNull(index - 1)
        val next = photos.getOrNull(index + 1)

        fun settleTo(direction: Int) {
            if (settling) return
            val targetIndex = (index + direction).takeIf { direction != 0 && it in photos.indices } ?: index
            val targetOffset = when {
                targetIndex > index -> -widthPx
                targetIndex < index -> widthPx
                else -> 0f
            }
            settling = true
            scope.launch {
                val motion = Animatable(dragOffset)
                motion.animateTo(
                    targetOffset,
                    spring(dampingRatio = 1f, stiffness = Spring.StiffnessMediumLow)
                ) { dragOffset = value }
                if (targetIndex != index) index = targetIndex
                dragOffset = 0f
                settling = false
            }
        }

        Box(
            Modifier.fillMaxSize().pointerInput(index, widthPx) {
                detectHorizontalDragGestures(
                    onDragStart = { if (!settling) dragOffset = 0f },
                    onDragEnd = {
                        if (!settling) {
                            val threshold = maxOf(72f, widthPx * .18f)
                            when {
                                dragOffset < -threshold && next != null -> settleTo(1)
                                dragOffset > threshold && previous != null -> settleTo(-1)
                                else -> settleTo(0)
                            }
                        }
                    },
                    onDragCancel = { if (!settling) settleTo(0) },
                    onHorizontalDrag = { change, amount ->
                        change.consume()
                        if (!settling) {
                            val pullingPastEdge = (dragOffset > 0f && previous == null) || (dragOffset < 0f && next == null)
                            val resistance = if (pullingPastEdge) .22f else 1f
                            dragOffset = (dragOffset + amount * resistance).coerceIn(-widthPx, widthPx)
                        }
                    }
                )
            }
        ) {
            if (previous != null) {
                GalleryImage(
                    previous.uri,
                    previous.name,
                    Modifier.fillMaxSize().offset { IntOffset((dragOffset - widthPx).roundToInt(), 0) },
                    ContentScale.Fit
                )
            }
            if (next != null) {
                GalleryImage(
                    next.uri,
                    next.name,
                    Modifier.fillMaxSize().offset { IntOffset((widthPx + dragOffset).roundToInt(), 0) },
                    ContentScale.Fit
                )
            }
            GalleryImage(
                photo.uri,
                photo.name,
                Modifier.fillMaxSize().offset { IntOffset(dragOffset.roundToInt(), 0) },
                ContentScale.Fit
            )
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
                Text("${index + 1} of ${photos.size}", color = Color.White.copy(.72f), fontSize = 12.sp)
            }
            IconButton(onClick = { onTrash(photo) }, modifier = Modifier.clip(CircleShape).background(Color.Black.copy(.38f))) {
                Icon(Icons.Rounded.DeleteOutline, "Move to trash", tint = Color.White)
            }
        }
        if (index > 0) {
            IconButton(onClick = { settleTo(-1) }, modifier = Modifier.align(Alignment.CenterStart).padding(8.dp).clip(CircleShape).background(Color.Black.copy(.38f))) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Previous photo", tint = Color.White)
            }
        }
        if (index < photos.lastIndex) {
            IconButton(onClick = { settleTo(1) }, modifier = Modifier.align(Alignment.CenterEnd).padding(8.dp).clip(CircleShape).background(Color.Black.copy(.38f))) {
                Icon(Icons.AutoMirrored.Rounded.ArrowForward, "Next photo", tint = Color.White)
            }
        }
        Text(
            SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date(photo.date * 1000)),
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding().background(Color.Black.copy(.35f)).padding(vertical = 16.dp),
            color = Color.White.copy(.85f), fontSize = 12.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
}

@Composable
internal fun BottomBar(page: Page, onPage: (Page) -> Unit) {
    val context = LocalContext.current
    @Suppress("DEPRECATION")
    val appVersion = remember { context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "1.0" }
    Column(Modifier.fillMaxWidth().background(Card)) {
        Column(Modifier.fillMaxWidth().padding(top = 7.dp, bottom = 3.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Version $appVersion", fontSize = 9.sp, color = Muted)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Made with", fontSize = 10.sp, color = Muted)
                Icon(Icons.Rounded.Favorite, "Love", tint = Color(0xFFE45167), modifier = Modifier.padding(horizontal = 4.dp).size(12.dp))
                Text("by Nitesh K Jain", fontSize = 10.sp, color = Muted)
            }
        }
        NavigationBar(containerColor = Card, tonalElevation = 0.dp, windowInsets = WindowInsets.navigationBars) {
            val entries = listOf(Triple(Page.Photos, Icons.Rounded.GridView, "Photos"), Triple(Page.Albums, Icons.Rounded.Album, "Albums"), Triple(Page.Swipe, Icons.Rounded.Swipe, "Sort"), Triple(Page.Trash, Icons.Rounded.DeleteOutline, "Bin"))
            entries.forEach { (target, icon, label) ->
                NavigationBarItem(selected = page == target, onClick = { onPage(target) }, icon = { Icon(icon, label) }, label = { Text(label, fontSize = 10.sp) }, colors = NavigationBarItemDefaults.colors(selectedIconColor = Green, selectedTextColor = Green, indicatorColor = Color(0xFFE7F1EC), unselectedIconColor = Muted, unselectedTextColor = Muted))
            }
        }
    }
}

@Composable
internal fun PermissionEmpty(onGrant: () -> Unit) {
    EmptyState(Icons.Rounded.Image, "Your memories, together", "Allow access to your photos to browse albums, sort favorites, and tidy up.", onGrant)
}

@Composable
internal fun EmptyState(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, body: String, action: (() -> Unit)?) {
    Column(Modifier.fillMaxSize().padding(30.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Box(Modifier.size(78.dp).clip(CircleShape).background(Color(0xFFE6EFE9)), contentAlignment = Alignment.Center) { Icon(icon, null, tint = Green, modifier = Modifier.size(34.dp)) }
        Spacer(Modifier.height(20.dp)); Text(title, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Ink)
        Spacer(Modifier.height(8.dp)); Text(body, color = Muted, fontSize = 14.sp, lineHeight = 21.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        if (action != null) { Spacer(Modifier.height(18.dp)); Button(onClick = action, colors = ButtonDefaults.buttonColors(containerColor = Green), shape = RoundedCornerShape(14.dp)) { Text("Allow photo access") } }
    }
}
