/*
 * [MOD] Lightroom 风格底部编辑栏
 * 动作 · 预设 · 裁剪 · 编辑 · 蒙版 · 移除
 */

package com.t8rin.imagetoolbox.feature.single_edit.presentation.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun LightroomEditBar(
    onAction: () -> Unit,
    onPresets: () -> Unit,
    onCrop: () -> Unit,
    onEdit: () -> Unit,
    onMask: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier
) {
    val items = listOf<Pair<String, () -> Unit>>(
        "动作" to onAction,
        "预设" to onPresets,
        "裁剪" to onCrop,
        "编辑" to onEdit,
        "蒙版" to onMask,
        "移除" to onRemove
    )
    Row(
        modifier = modifier
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 4.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        items.forEach { (label, action) ->
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { action() }
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = label,
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Spacer(Modifier.height(2.dp))
            }
        }
    }
}
