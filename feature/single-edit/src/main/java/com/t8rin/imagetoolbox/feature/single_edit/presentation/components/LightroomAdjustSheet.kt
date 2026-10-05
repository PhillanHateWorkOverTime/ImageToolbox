/*
 * [MOD] Lightroom 风格「编辑」调节面板（v1：光 + 颜色）
 * 默认自动（滑杆全 0 = 纯自动影调），推滑杆 = 在自动结果上微调。
 */

package com.t8rin.imagetoolbox.feature.single_edit.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private data class ToneSlider(
    val label: String,
    val min: Float,
    val max: Float
)

private val TONE_SLIDERS = listOf(
    ToneSlider("曝光", -1f, 1f),
    ToneSlider("对比度", -100f, 100f),
    ToneSlider("高光", -100f, 100f),
    ToneSlider("阴影", -100f, 100f),
    ToneSlider("白色色阶", -100f, 100f),
    ToneSlider("黑色色阶", -100f, 100f)
)

@Composable
fun LightroomAdjustSheet(
    visible: Boolean,
    onDismiss: () -> Unit,
    values: FloatArray,
    onValuesChange: (FloatArray) -> Unit,
    onAuto: () -> Unit,
    onReset: () -> Unit
) {
    if (!visible) return
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "光",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.weight(1f))
                OutlinedButton(onClick = onReset) { Text("重置") }
                Spacer(Modifier.height(4.dp))
                Button(onClick = onAuto) { Text("自动") }
            }
            Spacer(Modifier.height(8.dp))

            TONE_SLIDERS.forEachIndexed { index, item ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = item.label,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                    Slider(
                        value = values.getOrElse(index) { 0f },
                        onValueChange = { v ->
                            val next = values.copyOf(6)
                            next[index] = v
                            onValuesChange(next)
                        },
                        valueRange = item.min..item.max,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = values.getOrElse(index) { 0f }.let {
                            if (item.max <= 1f) String.format("%+.2f", it)
                            else String.format("%+d", it.toInt())
                        },
                        fontSize = 12.sp
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(
                text = "滑杆全 0 = 纯自动影调（自动压制高光、提亮暗部）",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
