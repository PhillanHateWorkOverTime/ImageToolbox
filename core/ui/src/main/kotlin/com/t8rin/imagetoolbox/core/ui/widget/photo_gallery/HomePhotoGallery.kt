/*
 * [MOD] 首页相册：直接列出手机相册照片，点一张就进编辑器
 *
 * 用法：当作「一级首页」的全屏内容使用（父级必须是有限高度，例如 fillMaxSize 的 Box/Column）。
 * 不要把它塞进 LazyColumn/LazyVerticalStaggeredGrid 的 item 里 —— 纵向滚动组件套在无限高度
 * 约束下会抛 IllegalStateException。
 */

package com.t8rin.imagetoolbox.core.ui.widget.photo_gallery

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val MAX_PHOTOS = 300

@Composable
fun HomePhotoGallery(
    onPick: (Uri) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp)
) {
    val context = LocalContext.current
    val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_IMAGES
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, permission) ==
                    PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasPermission = granted
    }

    LaunchedEffect(Unit) {
        if (!hasPermission) {
            runCatching { permissionLauncher.launch(permission) }
        }
    }

    var photos by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var loaded by remember { mutableStateOf(false) }

    LaunchedEffect(hasPermission) {
        photos = if (hasPermission) {
            withContext(Dispatchers.IO) { queryGalleryPhotos(context) }
        } else {
            emptyList()
        }
        loaded = true
    }

    if (loaded && photos.isEmpty()) {
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = if (hasPermission) {
                    "相册里没有找到照片"
                } else {
                    "需要「照片 / 存储」权限才能显示相册\n请在系统设置里允许本应用读取照片"
                },
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(24.dp)
            )
        }
        return
    }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(110.dp),
        modifier = modifier.fillMaxSize(),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(2.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        items(
            items = photos,
            key = { it.toString() }
        ) { uri ->
            GalleryThumbnail(
                context = context,
                uri = uri,
                onPick = onPick
            )
        }
    }
}

@Composable
private fun GalleryThumbnail(
    context: Context,
    uri: Uri,
    onPick: (Uri) -> Unit
) {
    var bitmap by remember(uri) { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(uri) {
        bitmap = withContext(Dispatchers.IO) { loadThumbnail(context, uri) }
    }

    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .clickable { onPick(uri) }
    ) {
        bitmap?.let {
            Image(
                bitmap = it.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }
    }
}

private fun queryGalleryPhotos(context: Context): List<Uri> {
    val result = mutableListOf<Uri>()
    runCatching {
        val projection = arrayOf(MediaStore.Images.Media._ID)
        val order = MediaStore.Images.Media.DATE_ADDED + " DESC"
        context.contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            projection,
            null,
            null,
            order
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            while (cursor.moveToNext() && result.size < MAX_PHOTOS) {
                result += ContentUris.withAppendedId(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    cursor.getLong(idColumn)
                )
            }
        }
    }
    return result
}

private fun loadThumbnail(context: Context, uri: Uri): Bitmap? = runCatching {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    context.contentResolver.openInputStream(uri)?.use {
        BitmapFactory.decodeStream(it, null, bounds)
    }
    var sample = 1
    while (bounds.outWidth / sample > 360) sample *= 2
    val opts = BitmapFactory.Options().apply { inSampleSize = sample }
    context.contentResolver.openInputStream(uri)?.use {
        BitmapFactory.decodeStream(it, null, opts)
    }
}.getOrNull()
