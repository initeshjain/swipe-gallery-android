package com.swipegallery.app

import android.Manifest
import android.app.Activity
import android.content.ContentUris
import android.content.ContentValues
import android.widget.Toast
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private fun requiredPermissions(): Array<String> = when {
    Build.VERSION.SDK_INT >= 34 -> arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
    Build.VERSION.SDK_INT >= 33 -> arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO)
    else -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
}

private fun isGranted(context: Context, permission: String) =
    ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

/** True when the app can read at least some media (full, images-only, videos-only, or Android 14 partial access). */
internal fun hasMediaAccess(context: Context) = requiredPermissions().any { isGranted(context, it) }

internal fun openAppSettings(context: Context) {
    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    context.startActivity(intent)
}

private fun queryMedia(context: Context, isVideo: Boolean): List<Photo> {
    val collection = if (isVideo) MediaStore.Video.Media.EXTERNAL_CONTENT_URI else MediaStore.Images.Media.EXTERNAL_CONTENT_URI
    // _ID, DISPLAY_NAME, BUCKET_DISPLAY_NAME and DATE_ADDED share the same column names for images and videos.
    val projection = buildList {
        add(MediaStore.Images.Media._ID)
        add(MediaStore.Images.Media.DISPLAY_NAME)
        add(MediaStore.Images.Media.BUCKET_DISPLAY_NAME)
        add(MediaStore.Images.Media.DATE_ADDED)
        if (isVideo) add(MediaStore.Video.Media.DURATION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) add(MediaStore.MediaColumns.RELATIVE_PATH)
    }.toTypedArray()
    val result = mutableListOf<Photo>()
    try {
        context.contentResolver.query(collection, projection, null, null, "${MediaStore.Images.Media.DATE_ADDED} DESC")?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
            val albumCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_DISPLAY_NAME)
            val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
            val durationCol = if (isVideo) cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION) else -1
            val pathCol = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.RELATIVE_PATH) else -1
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                result += Photo(
                    id = id,
                    uri = ContentUris.withAppendedId(collection, id),
                    name = cursor.getString(nameCol) ?: if (isVideo) "Video" else "Photo",
                    album = cursor.getString(albumCol) ?: "Other",
                    date = cursor.getLong(dateCol),
                    isVideo = isVideo,
                    durationMs = if (durationCol >= 0) cursor.getLong(durationCol) else 0,
                    relativePath = if (pathCol >= 0) cursor.getString(pathCol) else null,
                )
            }
        }
    } catch (_: SecurityException) {
        // Access to this media type was not granted; show what we can.
    }
    return result
}

@Composable
fun GalleryApp() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var photos by remember { mutableStateOf<List<Photo>>(emptyList()) }
    var page by remember { mutableStateOf(Page.Photos) }
    var selectedAlbum by remember { mutableStateOf<String?>(null) }
    var selectedPhoto by remember { mutableStateOf<Photo?>(null) }
    var playingVideo by remember { mutableStateOf<Photo?>(null) }
    var showSettings by remember { mutableStateOf(false) }
    var mediaFilter by remember { mutableStateOf(MediaFilter.All) }
    var selectedIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var moveDialogOpen by remember { mutableStateOf(false) }
    var pendingMove by remember { mutableStateOf<Pair<List<Photo>, String>?>(null) }
    var previewPhoto by remember { mutableStateOf<Photo?>(null) }
    var loading by remember { mutableStateOf(false) }
    var hasAccess by remember { mutableStateOf(false) }
    var accessBlocked by remember { mutableStateOf(false) }
    var pendingDeleteIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    val prefs = remember { context.getSharedPreferences("gallery_state", Context.MODE_PRIVATE) }
    var trashIds by remember { mutableStateOf(prefs.getStringSet("trash", emptySet())?.toSet() ?: emptySet()) }
    fun updateTrash(ids: Set<String>) { trashIds = ids; prefs.edit().putStringSet("trash", ids).apply() }

    suspend fun loadPhotos() {
        loading = true
        photos = withContext(Dispatchers.IO) {
            (queryMedia(context, isVideo = false) + queryMedia(context, isVideo = true)).sortedByDescending { it.date }
        }
        loading = false
    }
    fun onAccessGranted() {
        hasAccess = true
        accessBlocked = false
        scope.launch { loadPhotos() }
    }

    val permissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted ->
        when {
            granted.values.any { it } || hasMediaAccess(context) -> onAccessGranted()
            !hasAccess -> {
                // When Android stops showing a rationale after a denial, the dialog will not appear again:
                // the user has to enable access from the system app settings.
                val activity = context as? Activity
                accessBlocked = activity != null && granted.keys.none { ActivityCompat.shouldShowRequestPermissionRationale(activity, it) }
            }
        }
    }
    val deleteLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        val deletedIds = pendingDeleteIds
        pendingDeleteIds = emptySet()
        if (result.resultCode == Activity.RESULT_OK && deletedIds.isNotEmpty()) {
            updateTrash(trashIds - deletedIds)
            scope.launch { loadPhotos() }
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
            scope.launch { loadPhotos() }
        }
    }
    fun applyMove(items: List<Photo>, targetPath: String) {
        scope.launch {
            val failed = withContext(Dispatchers.IO) {
                items.count { item ->
                    try {
                        val values = ContentValues().apply { put(MediaStore.MediaColumns.RELATIVE_PATH, targetPath) }
                        context.contentResolver.update(item.uri, values, null, null) <= 0
                    } catch (_: Exception) {
                        // e.g. Android only allows photos under DCIM/ or Pictures/, and videos under DCIM/, Movies/ or Pictures/.
                        true
                    }
                }
            }
            val moved = items.size - failed
            val message = when {
                failed == 0 -> "Moved $moved ${if (moved == 1) "item" else "items"}"
                moved == 0 -> "Couldn't move these items to that folder"
                else -> "Moved $moved, couldn't move $failed"
            }
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            selectedIds = emptySet()
            loadPhotos()
        }
    }
    val writeLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        val move = pendingMove
        pendingMove = null
        if (result.resultCode == Activity.RESULT_OK && move != null) applyMove(move.first, move.second)
    }
    fun requestMove(items: List<Photo>, targetPath: String) {
        val toMove = items.filter { it.relativePath != targetPath }
        if (toMove.isEmpty()) { selectedIds = emptySet(); return }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Android asks the user once to allow editing all of these files.
            pendingMove = toMove to targetPath
            val request = MediaStore.createWriteRequest(context.contentResolver, toMove.map { it.uri })
            writeLauncher.launch(IntentSenderRequest.Builder(request).build())
        }
    }
    fun share(items: List<Photo>) {
        if (items.isEmpty()) return
        val uris = ArrayList(items.map { it.uri })
        val type = when {
            items.all { it.isVideo } -> "video/*"
            items.none { it.isVideo } -> "image/*"
            else -> "*/*"
        }
        val send = (
            if (uris.size == 1) Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_STREAM, uris.first())
            else Intent(Intent.ACTION_SEND_MULTIPLE).putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
        ).setType(type).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(Intent.createChooser(send, null))
    }
    fun requestAccess() {
        val all = requiredPermissions()
        if (hasMediaAccess(context)) {
            onAccessGranted()
            // Users upgrading from the photos-only version have images but not videos granted: ask once for the rest.
            val missingVideo = Build.VERSION.SDK_INT >= 33 && !isGranted(context, Manifest.permission.READ_MEDIA_VIDEO) &&
                !isGranted(context, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
            if (missingVideo && !prefs.getBoolean("video_access_asked", false)) {
                prefs.edit().putBoolean("video_access_asked", true).apply()
                permissions.launch(all)
            }
        } else permissions.launch(all)
    }
    LaunchedEffect(Unit) { requestAccess() }
    // Pick up access granted from the system settings screen when the user comes back to the app.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        if (!hasAccess && hasMediaAccess(context)) onAccessGranted()
    }

    val visiblePhotos = photos.filter { it.id.toString() !in trashIds }
    val trashed = photos.filter { it.id.toString() in trashIds }
    val albumPhotos = visiblePhotos.filter { selectedAlbum == null || it.album == selectedAlbum }
    val gridItems = albumPhotos.filter {
        when (mediaFilter) {
            MediaFilter.All -> true
            MediaFilter.Photos -> !it.isVideo
            MediaFilter.Videos -> it.isVideo
        }
    }
    // Only items still visible under the current album/filter count as selected.
    val selectedItems = gridItems.filter { it.id in selectedIds }
    val selecting = selectedIds.isNotEmpty() && page == Page.Photos && selectedPhoto == null
    fun clearSelection() { selectedIds = emptySet() }
    val canNavigateBack = playingVideo != null || previewPhoto != null || selecting || showSettings || selectedPhoto != null || selectedAlbum != null || page != Page.Photos
    BackHandler(enabled = canNavigateBack) {
        when {
            playingVideo != null -> playingVideo = null
            previewPhoto != null -> previewPhoto = null
            selecting -> clearSelection()
            showSettings -> showSettings = false
            selectedPhoto != null -> selectedPhoto = null
            selectedAlbum != null -> { selectedAlbum = null; page = Page.Albums }
            page != Page.Photos -> page = Page.Photos
        }
    }

    MaterialTheme(colorScheme = lightColorScheme(background = Canvas, surface = Card, primary = Green, onBackground = Ink, onSurface = Ink)) {
        Surface(Modifier.fillMaxSize(), color = Canvas) {
            AnimatedContent(
                targetState = showSettings,
                transitionSpec = {
                    (fadeIn(tween(200)) + slideInHorizontally(tween(240, easing = FastOutSlowInEasing)) { if (targetState) it / 8 else -it / 8 }) togetherWith
                        fadeOut(tween(140))
                },
                label = "settings navigation"
            ) { settingsOpen ->
                if (settingsOpen) {
                    SettingsPage(hasAccess = hasAccess, onBack = { showSettings = false })
                } else {
                    Column(Modifier.fillMaxSize()) {
                        if (selecting) {
                            SelectionTopBar(
                                count = selectedItems.size,
                                allSelected = selectedItems.size == gridItems.size,
                                onClose = { clearSelection() },
                                onToggleAll = { selectedIds = if (selectedItems.size == gridItems.size) emptySet() else gridItems.map { it.id }.toSet() }
                            )
                        } else if (selectedPhoto == null) {
                            Header(page, gridItems.size, selectedAlbum, onSettings = { showSettings = true }) {
                                clearSelection()
                                if (selectedAlbum != null) { selectedAlbum = null; page = Page.Albums }
                                else page = Page.Photos
                            }
                        }
                        Box(Modifier.weight(1f).fillMaxWidth()) {
                            when {
                                loading && photos.isEmpty() -> CircularProgressIndicator(Modifier.align(Alignment.Center), color = Green)
                                !hasAccess && page != Page.Trash -> PermissionEmpty(
                                    blocked = accessBlocked,
                                    onGrant = { requestAccess() },
                                    onOpenSettings = { openAppSettings(context) }
                                )
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
                                            // Only animate opening/closing the viewer; switching photos inside it keeps the same viewer.
                                            contentKey = { it != null },
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
                                            if (openingPhoto == null) {
                                                PhotoGrid(
                                                    gridItems, mediaFilter,
                                                    onFilter = { mediaFilter = it },
                                                    selectedIds = if (selecting) selectedIds else emptySet(),
                                                    onPhoto = { photo ->
                                                        if (selecting) selectedIds = if (photo.id in selectedIds) selectedIds - photo.id else selectedIds + photo.id
                                                        else selectedPhoto = photo
                                                    },
                                                    onLongPress = { photo -> selectedIds = selectedIds + photo.id },
                                                    empty = !loading
                                                )
                                            } else {
                                                PhotoDetail(
                                                    openingPhoto,
                                                    gridItems,
                                                    onClose = { selectedPhoto = null },
                                                    onPlay = { playingVideo = it },
                                                    onTrash = { photo ->
                                                        val index = gridItems.indexOfFirst { it.id == photo.id }
                                                        val remaining = gridItems.filter { it.id != photo.id }
                                                        updateTrash(trashIds + photo.id.toString())
                                                        // Stay in the viewer on the next item (or the previous one if this was the last).
                                                        selectedPhoto = remaining.getOrNull(index) ?: remaining.lastOrNull()
                                                    }
                                                )
                                            }
                                        }
                                        Page.Albums -> Albums(visiblePhotos, onAlbum = { clearSelection(); selectedAlbum = it; page = Page.Photos })
                                        Page.Swipe -> SwipePage(
                                            visiblePhotos,
                                            onKeep = {},
                                            onTrash = { photo -> updateTrash(trashIds + photo.id.toString()) },
                                            onOpen = { photo -> if (photo.isVideo) playingVideo = photo else previewPhoto = photo }
                                        )
                                        Page.Trash -> TrashPage(trashed, onRestore = { photo -> updateTrash(trashIds - photo.id.toString()) }, onDelete = { photo -> permanentlyDelete(listOf(photo)) }, onEmpty = { ids -> permanentlyDelete(trashed.filter { it.id.toString() in ids }) })
                                    }
                                }
                            }
                        }
                        if (selecting) {
                            SelectionActionBar(
                                enabled = selectedItems.isNotEmpty(),
                                canMove = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R,
                                onShare = { share(selectedItems) },
                                onMove = { moveDialogOpen = true },
                                onDelete = {
                                    updateTrash(trashIds + selectedItems.map { it.id.toString() })
                                    val count = selectedItems.size
                                    clearSelection()
                                    Toast.makeText(context, "Moved $count ${if (count == 1) "item" else "items"} to the bin", Toast.LENGTH_SHORT).show()
                                }
                            )
                        } else if (selectedPhoto == null) {
                            BottomBar(page, onPage = { clearSelection(); page = it; selectedAlbum = null })
                        }
                    }
                }
            }
            if (moveDialogOpen) {
                val albums = visiblePhotos.filter { it.relativePath != null }
                    .groupBy { it.relativePath!! }
                    .map { (path, items) -> AlbumTarget(items.first().album, path, items.size) }
                    .sortedByDescending { it.count }
                MoveToAlbumDialog(
                    count = selectedItems.size,
                    albums = albums,
                    onDismiss = { moveDialogOpen = false },
                    onMove = { path -> moveDialogOpen = false; requestMove(selectedItems, path) }
                )
            }
            previewPhoto?.let { item -> MediaPreview(item, onClose = { previewPhoto = null }, onPlay = { playingVideo = it }) }
            playingVideo?.let { video -> VideoPlayer(video, onClose = { playingVideo = null }) }
        }
    }
}
