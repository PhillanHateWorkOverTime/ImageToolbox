/*
 * [MOD] Lightroom 风格调节面板 —— 浮在照片上的半透明面板
 * 照片铺满全屏；面板贴底、半透明，调节时不挡画面。
 */

package com.t8rin.imagetoolbox.feature.single_edit.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Lightroom 调节值 */
data class LightroomValues(
    val exposure: Float = 0f,
    val contrast: Float = 0f,
    val highlights: Float = 0f,
    val shadows: Float = 0f,
    val whites: Float = 0f,
    val blacks: Float = 0f,
    val temperature: Float = 0f,
    val tint: Float = 0f,
    val vibrance: Float = 0f,
    val saturation: Float = 0f,
    val clarity: Float = 0f,
    val grain: Float = 0f
) {
    fun tone(): FloatArray = floatArrayOf(exposure, contrast, highlights, shadows, whites, blacks)
    val isDefault: Boolean
        get() = exposure == 0f && contrast == 0f && highlights == 0f && shadows == 0f &&
                whites == 0f && blacks == 0f && temperature == 0f && tint == 0f &&
                vibrance == 0f && saturation == 0f && clarity == 0f && grain == 0f
}

private data class SliderSpec(
    val label: String,
    val min: Float,
    val max: Float,
    val get: (LightroomValues) -> Float,
    val set: (LightroomValues, Float) -> LightroomValues
)

private val LIGHT_SLIDERS = listOf(
    SliderSpec("曝光度", -1f, 1f, { it.exposure }, { v, x -> v.copy(exposure = x) }),
    SliderSpec("对比度", -100f, 100f, { it.contrast }, { v, x -> v.copy(contrast = x) }),
    SliderSpec("高光", -100f, 100f, { it.highlights }, { v, x -> v.copy(highlights = x) }),
    SliderSpec("阴影", -100f, 100f, { it.shadows }, { v, x -> v.copy(shadows = x) }),
    SliderSpec("白色色阶", -100f, 100f, { it.whites }, { v, x -> v.copy(whites = x) }),
    SliderSpec("黑色色阶", -100f, 100f, { it.blacks }, { v, x -> v.copy(blacks = x) })
)

private val COLOR_SLIDERS = listOf(
    SliderSpec("色温", -100f, 100f, { it.temperature }, { v, x -> v.copy(temperature = x) }),
    SliderSpec("色调", -100f, 100f, { it.tint }, { v, x -> v.copy(tint = x) }),
    SliderSpec("自然饱和度", -5f, 5f, { it.vibrance }, { v, x -> v.copy(vibrance = x) }),
    SliderSpec("饱和度", -100f, 100f, { it.saturation }, { v, x -> v.copy(saturation = x) })
)

private val EFFECTS_SLIDERS = listOf(
    SliderSpec("清晰度", -100f, 100f, { it.clarity }, { v, x -> v.copy(clarity = x) }),
    SliderSpec("颗粒", 0f, 2f, { it.grain }, { v, x -> v.copy(grain = x) })
)

@Composable
fun LightroomAdjustSheet(
    visible: Boolean,
    onDismiss: () -> Unit,
    values: LightroomValues,
    onValuesChange: (LightroomValues) -> Unit,
    onCommit: () -> Unit,
    onCancel: () -> Unit
) {
    if (!visible) return
    var group by remember { mutableStateOf(0) }
    val groups = listOf("亮度" to LIGHT_SLIDERS, "颜色" to COLOR_SLIDERS, "效果" to EFFECTS_SLIDERS)

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .fillMaxHeight(0.46f)
                .clip(RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp))
                .background(Color.Black.copy(alpha = 0.62f))
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp)
                .padding(top = 10.dp, bottom = 20.dp)
        ) {
            // 分组切换
            Row(
                modifier = Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(bottom = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                groups.forEachIndexed { index, (name, _) ->
                    Text(
                        text = name,
                        fontSize = 15.sp,
                        fontWeight = if (group == index) FontWeight.Bold else FontWeight.Normal,
                        color = if (group == index) Color(0xFF64B5F6) else Color(0xFFDDDDDD),
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { group = index }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (group == 0) {
                    Button(
                        onClick = { onValuesChange(LightroomValues()) }
                    ) { Text("自动") }
                }
                Spacer(Modifier.weight(1f))
                OutlinedButton(
                    onClick = {
                        val zero = LightroomValues()
                        val reset = when (group) {
                            0 -> values.copy(
                                exposure = zero.exposure, contrast = zero.contrast,
                                highlights = zero.highlights, shadows = zero.shadows,
                                whites = zero.whites, blacks = zero.blacks
                            )

                            1 -> values.copy(
                                temperature = zero.temperature, tint = zero.tint,
                                vibrance = zero.vibrance, saturation = zero.saturation
                            )

                            else -> values.copy(clarity = zero.clarity, grain = zero.grain)
                        }
                        onValuesChange(reset)
                    }
                ) { Text("重置") }
            }

            Spacer(Modifier.height(4.dp))

            groups[group].second.forEach { spec ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = spec.label,
                        fontSize = 13.sp,
                        color = Color(0xFFEEEEEE),
                        modifier = Modifier.width(70.dp)
                    )
                    Slider(
                        value = spec.get(values),
                        onValueChange = { x -> onValuesChange(spec.set(values, x)) },
                        valueRange = spec.min..spec.max,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = if (spec.max <= 5f) String.format("%+.2f", spec.get(values))
                        else String.format("%+d", spec.get(values).toInt()),
                        fontSize = 12.sp,
                        color = Color(0xFFBBBBBB),
                        modifier = Modifier.width(48.dp)
                    )
                }
            }

            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = onCancel,
                    modifier = Modifier.weight(1f)
                ) { Text("取消") }
                Button(
                    onClick = onCommit,
                    modifier = Modifier.weight(1f)
                ) { Text("✓ 应用") }
            }
        }
    }
}
