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
fun GalleryApp() {
    val context = LocalContext.current
    var photos by remember { mutableStateOf<List<Photo>>(emptyList()) }
    var page by remember { mutableStateOf(Page.Photos) }
    var selectedAlbum by remember { mutableStateOf<String?>(null) }
    var selectedPhoto by remember { mutableStateOf<Photo?>(null) }
    var loading by remember { mutableStateOf(false) }
    var hasAccess by remember { mutableStateOf(false) }
    var pendingDeleteIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    val prefs = remember { context.getSharedPreferences("gallery_state", Context.MODE_PRIVATE) }
    var trashIds by remember { mutableStateOf(prefs.getStringSet("trash", emptySet())?.toSet() ?: emptySet()) }
    fun updateTrash(ids: Set<String>) { trashIds = ids; prefs.edit().putStringSet("trash", ids).apply() }

    suspend fun loadPhotos() {
        loading = true
        photos = withContext(Dispatchers.IO) {
            val result = mutableListOf<Photo>()
            val collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            val projection = arrayOf(MediaStore.Images.Media._ID, MediaStore.Images.Media.DISPLAY_NAME, MediaStore.Images.Media.BUCKET_DISPLAY_NAME, MediaStore.Images.Media.DATE_ADDED)
            context.contentResolver.query(collection, projection, null, null, "${MediaStore.Images.Media.DATE_ADDED} DESC")?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
                val albumCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_DISPLAY_NAME)
                val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    result += Photo(id, ContentUris.withAppendedId(collection, id), cursor.getString(nameCol) ?: "Photo", cursor.getString(albumCol) ?: "Other", cursor.getLong(dateCol))
                }
            }
            result
        }
        loading = false
    }

    val permissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted ->
        if (granted.values.any { it }) { hasAccess = true; kotlinx.coroutines.MainScope().launch { loadPhotos() } }
    }
    val deleteLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        val deletedIds = pendingDeleteIds
        pendingDeleteIds = emptySet()
        if (result.resultCode == android.app.Activity.RESULT_OK && deletedIds.isNotEmpty()) {
            updateTrash(trashIds - deletedIds)
            kotlinx.coroutines.MainScope().launch { loadPhotos() }
        }
    }
    fun permanentlyDelete(items: List<Photo>) {
        if (items.isEmpty()) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            pendingDeleteIds = items.map { it.id.toString() }.toSet()
            val request = MediaStore.createDeleteRequest(context.contentResolver, items.map { it.uri })
            deleteLauncher.launch(IntentSenderRequest.Builder(request).build())
        } else {
            val removed = mutableSetOf<String>()
            items.forEach { photo ->
                try {
                    context.contentResolver.delete(photo.uri, null, null)
                    removed += photo.id.toString()
                } catch (_: SecurityException) {
                    // Keep items the platform did not let us delete in the app's recoverable bin.
                }
            }
            updateTrash(trashIds - removed)
            kotlinx.coroutines.MainScope().launch { loadPhotos() }
        }
    }
    fun requestAccess() {
        val permissionsToRequest = when {
            Build.VERSION.SDK_INT >= 34 -> arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
            Build.VERSION.SDK_INT >= 33 -> arrayOf(Manifest.permission.READ_MEDIA_IMAGES)
            else -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        val permission = permissionsToRequest.first()
        val selectedAccess = Build.VERSION.SDK_INT >= 34 && ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED) == PackageManager.PERMISSION_GRANTED
        if (ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED || selectedAccess) {
            hasAccess = true
            kotlinx.coroutines.MainScope().launch { loadPhotos() }
        } else permissions.launch(permissionsToRequest)
    }
    LaunchedEffect(Unit) { requestAccess() }

    val visiblePhotos = photos.filter { it.id.toString() !in trashIds }
    val trashed = photos.filter { it.id.toString() in trashIds }
    val albumPhotos = visiblePhotos.filter { selectedAlbum == null || it.album == selectedAlbum }
    val canNavigateBack = selectedPhoto != null || selectedAlbum != null || page != Page.Photos
    BackHandler(enabled = canNavigateBack) {
        when {
            selectedPhoto != null -> selectedPhoto = null
            selectedAlbum != null -> { selectedAlbum = null; page = Page.Albums }
            page != Page.Photos -> page = Page.Photos
        }
    }

    MaterialTheme(colorScheme = lightColorScheme(background = Canvas, surface = Card, primary = Green, onBackground = Ink, onSurface = Ink)) {
        Surface(Modifier.fillMaxSize(), color = Canvas) {
            Column(Modifier.fillMaxSize()) {
                if (selectedPhoto == null) {
                    Header(page, albumPhotos.size, selectedAlbum) {
                        if (selectedAlbum != null) { selectedAlbum = null; page = Page.Albums }
                        else page = Page.Photos
                    }
                }
                Box(Modifier.weight(1f)) {
                    when {
                        loading && photos.isEmpty() -> CircularProgressIndicator(Modifier.align(Alignment.Center), color = Green)
                        !hasAccess && page != Page.Trash -> PermissionEmpty(onGrant = { requestAccess() })
                        else -> AnimatedContent(
                        targetState = page,
                        transitionSpec = {
                            (fadeIn(tween(180)) + slideInHorizontally(tween(220, easing = FastOutSlowInEasing)) { it / 18 }) togetherWith
                                (fadeOut(tween(140)) + slideOutHorizontally(tween(220, easing = FastOutSlowInEasing)) { -it / 18 })
                        },
                        label = "section navigation"
                    ) { visiblePage ->
                        when (visiblePage) {
                            Page.Photos -> AnimatedContent(
                                targetState = selectedPhoto,
                                transitionSpec = {
                                    if (targetState == null) {
                                        (fadeIn(tween(220)) + slideInVertically(tween(260, easing = FastOutSlowInEasing)) { -it / 8 }) togetherWith
                                            (fadeOut(tween(180)) + slideOutVertically(tween(240)) { it / 5 })
                                    } else {
                                        (fadeIn(tween(220)) + slideInVertically(tween(280, easing = FastOutSlowInEasing)) { it / 5 }) togetherWith
                                            (fadeOut(tween(180)) + slideOutVertically(tween(220)) { -it / 6 })
                                    }
                                },
                                label = "photo viewer navigation"
                            ) { openingPhoto ->
                                if (openingPhoto == null) PhotoGrid(albumPhotos, onPhoto = { selectedPhoto = it }, empty = !loading)
                                else PhotoDetail(openingPhoto, albumPhotos, onClose = { selectedPhoto = null }, onTrash = { photo -> updateTrash(trashIds + photo.id.toString()); selectedPhoto = null })
                            }
                            Page.Albums -> Albums(visiblePhotos, onAlbum = { selectedAlbum = it; page = Page.Photos })
                            Page.Swipe -> SwipePage(visiblePhotos, onKeep = {}, onTrash = { photo -> updateTrash(trashIds + photo.id.toString()) })
                            Page.Trash -> TrashPage(trashed, onRestore = { photo -> updateTrash(trashIds - photo.id.toString()) }, onDelete = { photo -> permanentlyDelete(listOf(photo)) }, onEmpty = { ids -> permanentlyDelete(trashed.filter { it.id.toString() in ids }) })
                        }
                    }
                    }
                }
                if (selectedPhoto == null) BottomBar(page, onPage = { page = it; selectedAlbum = null })
            }
        }
    }
}
