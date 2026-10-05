/*
 * ImageToolbox is an image editor for android
 * Copyright (c) 2026 T8RIN (Malik Mukhametzyanov)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * You should have received a copy of the Apache License
 * along with this program.  If not, see <http://www.apache.org/licenses/LICENSE-2.0>.
 */

package com.t8rin.imagetoolbox.feature.filters.data.model

import android.graphics.Bitmap
import com.t8rin.imagetoolbox.core.domain.model.IntegerSize
import com.t8rin.imagetoolbox.core.domain.transformation.Transformation
import com.t8rin.imagetoolbox.core.filters.domain.model.Filter
import com.t8rin.imagetoolbox.core.ksp.annotations.FilterInject
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlin.math.abs
import kotlin.math.log2
import kotlin.math.pow
import kotlin.math.sqrt

private const val ALPHA_MASK = -0x1000000
private const val TONE_ROW_BLOCK_SIZE = 64

private const val LUMA_R = 0.2126f
private const val LUMA_G = 0.7152f
private const val LUMA_B = 0.0722f

/**
 * Lightroom-style "auto tone".
 *
 * Analyzes the image luminance distribution (percentiles + clipping) and derives
 * six tonal amounts — exposure / contrast / highlights / shadows / whites / blacks —
 * then applies each one weighted to its own tonal region, so highlights are pulled
 * back instead of blowing out and shadows are lifted instead of crushing.
 *
 * [value] is the overall strength in 0..1.
 */
@FilterInject
internal class AutoToneFilter(
    override val value: Float = 1f
) : Transformation<Bitmap>, Filter.AutoTone {

    override val cacheKey: String
        get() = value.hashCode().toString()

    override suspend fun transform(
        input: Bitmap,
        size: IntegerSize
    ): Bitmap {
        val width = input.width
        val height = input.height
        val strength = value.coerceIn(0f, 1f)
        if (width <= 0 || height <= 0 || strength <= 0f) return input

        val params = analyzeTone(
            input = input,
            width = width,
            height = height,
            strength = strength
        )

        return applyTone(
            input = input,
            width = width,
            height = height,
            params = params
        )
    }
}

private data class ToneParams(
    val exposure: Float,
    val contrast: Float,
    val highlights: Float,
    val shadows: Float,
    val whites: Float,
    val blacks: Float
)

private suspend fun analyzeTone(
    input: Bitmap,
    width: Int,
    height: Int,
    strength: Float
): ToneParams {
    val histogram = IntArray(256)
    var total = 0

    val buffer = IntArray(width * TONE_ROW_BLOCK_SIZE)
    var top = 0
    while (top < height) {
        currentCoroutineContext().ensureActive()
        val rows = minOf(TONE_ROW_BLOCK_SIZE, height - top)
        input.getPixels(buffer, 0, width, 0, top, width, rows)

        var y = 0
        while (y < rows) {
            val base = y * width
            var x = 0
            while (x < width) {
                val pixel = buffer[base + x]
                val red = pixel ushr 16 and 0xFF
                val green = pixel ushr 8 and 0xFF
                val blue = pixel and 0xFF
                val luma = (red * 77 + green * 150 + blue * 29) ushr 8
                histogram[luma]++
                total++
                x += 2
            }
            y += 2
        }
        top += rows
    }

    if (total == 0) {
        return ToneParams(0f, 0f, 0f, 0f, 0f, 0f)
    }

    fun quantile(fraction: Float): Float {
        val target = fraction * total
        var accumulated = 0
        for (i in 0..255) {
            accumulated += histogram[i]
            if (accumulated >= target) return i / 255f
        }
        return 1f
    }

    var clippedHigh = 0
    for (i in 248..255) clippedHigh += histogram[i]
    var clippedLow = 0
    for (i in 0..7) clippedLow += histogram[i]
    val clipHigh = clippedHigh.toFloat() / total
    val clipLow = clippedLow.toFloat() / total

    val median = quantile(0.5f)
    val p01 = quantile(0.01f)
    val p05 = quantile(0.05f)
    val p95 = quantile(0.95f)
    val p99 = quantile(0.99f)
    val spread = p95 - p05

    val exposure = clampTone(log2(0.44f / maxOf(median, 1e-3f)) * 0.55f, -0.8f, 0.8f)

    val highlights = if (p99 > 0.92f || clipHigh > 0.0015f) {
        -clampTone((p99 - 0.90f) * 260f + clipHigh * 2200f, 0f, 55f)
    } else {
        0f
    }

    val shadows = if (p01 < 0.14f || clipLow > 0.01f) {
        clampTone((0.14f - p01) * 230f + clipLow * 500f, 0f, 45f)
    } else {
        0f
    }

    val whites = clampTone((0.93f - p95) * 180f - clipHigh * 1600f, -30f, 30f)

    val blacks = clampTone((0.035f - p05) * 240f + clipLow * 400f, -35f, 25f)

    val contrast = clampTone(14f - spread * 22f, -8f, 18f)

    return ToneParams(
        exposure = exposure * strength,
        contrast = contrast * strength,
        highlights = highlights * strength,
        shadows = shadows * strength,
        whites = whites * strength,
        blacks = blacks * strength
    )
}

private suspend fun applyTone(
    input: Bitmap,
    width: Int,
    height: Int,
    params: ToneParams
): Bitmap {
    val output = input.copy(Bitmap.Config.ARGB_8888, true)

    val gain = 2.0.pow(params.exposure.toDouble()).toFloat()
    val highlightsAmount = abs(params.highlights) / 100f * 0.55f
    val shadowsAmount = params.shadows / 100f * 0.5f
    val whitesAmount = params.whites / 100f * 0.30f
    val blacksDown = params.blacks < 0f
    val blacksAmount = abs(params.blacks) / 100f * (if (blacksDown) 0.22f else 0.30f)
    val contrastFactor = 1f + params.contrast / 100f * 0.5f

    val hasHighlights = params.highlights != 0f
    val hasShadows = params.shadows != 0f
    val hasWhites = params.whites != 0f
    val hasBlacks = params.blacks != 0f
    val hasContrast = params.contrast != 0f

    val buffer = IntArray(width * TONE_ROW_BLOCK_SIZE)
    var top = 0
    while (top < height) {
        currentCoroutineContext().ensureActive()
        val rows = minOf(TONE_ROW_BLOCK_SIZE, height - top)
        val count = width * rows
        input.getPixels(buffer, 0, width, 0, top, width, rows)

        var index = 0
        while (index < count) {
            val pixel = buffer[index]
            var red = (pixel ushr 16 and 0xFF) / 255f
            var green = (pixel ushr 8 and 0xFF) / 255f
            var blue = (pixel and 0xFF) / 255f

            red *= gain
            green *= gain
            blue *= gain

            var luma = clampTone(lumaOf(red, green, blue), 0f, 1f)

            if (hasHighlights) {
                val mask = regionMask(luma, 0.55f, 0.98f)
                val amount = highlightsAmount * mask
                red -= amount * red
                green -= amount * green
                blue -= amount * blue
            }

            if (hasShadows) {
                val mask = regionMask(1f - luma, 0.55f, 0.98f)
                val amount = shadowsAmount * mask
                red += amount * (1f - red)
                green += amount * (1f - green)
                blue += amount * (1f - blue)
            }

            luma = clampTone(lumaOf(red, green, blue), 0f, 1f)

            if (hasWhites) {
                val mask = regionMask(luma, 0.75f, 1f)
                val amount = whitesAmount * mask
                red += amount * (1f - red)
                green += amount * (1f - green)
                blue += amount * (1f - blue)
            }

            if (hasBlacks) {
                val mask = regionMask(1f - luma, 0.75f, 1f)
                val amount = blacksAmount * mask
                if (blacksDown) {
                    red -= amount * red
                    green -= amount * green
                    blue -= amount * blue
                } else {
                    red += amount * (1f - red)
                    green += amount * (1f - green)
                    blue += amount * (1f - blue)
                }
            }

            if (hasContrast) {
                red = clampTone(0.5f + (red - 0.5f) * contrastFactor, 0f, 1f)
                green = clampTone(0.5f + (green - 0.5f) * contrastFactor, 0f, 1f)
                blue = clampTone(0.5f + (blue - 0.5f) * contrastFactor, 0f, 1f)
            }

            buffer[index] = (pixel and ALPHA_MASK) or
                    (toByteValue(red) shl 16) or
                    (toByteValue(green) shl 8) or
                    toByteValue(blue)
            index++
        }

        output.setPixels(buffer, 0, width, 0, top, width, rows)
        top += rows
    }

    return output
}

private fun lumaOf(red: Float, green: Float, blue: Float): Float =
    red * LUMA_R + green * LUMA_G + blue * LUMA_B

private fun regionMask(value: Float, lower: Float, upper: Float): Float {
    val t = ((value - lower) / (upper - lower)).coerceIn(0f, 1f)
    return t * sqrt(t)
}

private fun clampTone(value: Float, lower: Float, upper: Float): Float =
    maxOf(lower, minOf(upper, value))

private fun toByteValue(value: Float): Int {
    val scaled = value * 255f + 0.5f
    return when {
        scaled <= 0f -> 0
        scaled >= 255f -> 255
        else -> scaled.toInt()
    }
}
