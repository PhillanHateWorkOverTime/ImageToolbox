/*
 * [MOD] 首页图库：直接列出手机相册照片，点一张就进编辑
 *
 * 注意：这里**不能**用 LazyVerticalGrid/LazyColumn 这类纵向滚动容器。
 * 本组件被放在 AdaptiveLayoutScreen 的 LazyColumn item 里（noDataControls），
 * 父级给的高度约束是 Infinity → 再嵌套可纵向滚动组件会抛
 * IllegalStateException("Vertically scrollable component was measured with
 * an infinity maximum height constraints") 直接崩。所以改成普通 Column 行排布，
 * 由外层 LazyColumn 负责滚动。
 */

package com.t8rin.imagetoolbox.feature.single_edit.presentation.components

import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.MediaStore
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val COLUMNS = 3
private const val MAX_PHOTOS = 60

@Composable
fun PhotoGrid(
    onPick: (Uri) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var photos by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var loaded by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        photos = withContext(Dispatchers.IO) { queryPhotos(context) }
        loaded = true
    }

    if (loaded && photos.isEmpty()) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = "相册里没有找到照片\n（若提示无权限，请在系统设置里允许本应用读取照片）",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
        return
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        photos.chunked(COLUMNS).forEach { rowUris ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                rowUris.forEach { uri ->
                    PhotoCell(
                        context = context,
                        uri = uri,
                        onPick = onPick,
                        modifier = Modifier.weight(1f)
                    )
                }
                // 最后一行不满时补空位，保持单元格等宽
                repeat(COLUMNS - rowUris.size) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun PhotoCell(
    context: Context,
    uri: Uri,
    onPick: (Uri) -> Unit,
    modifier: Modifier = Modifier
) {
    var bitmap by remember(uri) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(uri) {
        bitmap = withContext(Dispatchers.IO) { loadThumbnail(context, uri) }
    }
    Box(
        modifier = modifier
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

private fun queryPhotos(context: Context): List<Uri> {
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
