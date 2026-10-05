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
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.pow

/**
 * [MOD] 一键调色 / Lightroom 风格自动影调
 *
 * value[0] exposure   : 曝光微调 (EV, -1..1)
 * value[1] contrast   : 对比度微调 (-100..100)
 * value[2] highlights : 高光微调 (-100..100)
 * value[3] shadows    : 阴影微调 (-100..100)
 * value[4] whites     : 白色色阶微调 (-100..100)
 * value[5] blacks     : 黑色色阶微调 (-100..100)
 *
 * 全为 0 时 = 纯自动：分析直方图算出 6 个影调值后直接套用。
 * 非 0 时作为"在自动结果基础上的微调偏移"，和 Lightroom 的 Auto + 手动修一个思路。
 */
@FilterInject
internal class AutoToneFilter(
    override val value: FloatArray = floatArrayOf(0f, 0f, 0f, 0f, 0f, 0f)
) : Transformation<Bitmap>, Filter.AutoTone {

    override val cacheKey: String
        get() = value.contentHashCode().toString()

    override suspend fun transform(
        input: Bitmap,
        size: IntegerSize
    ): Bitmap {
        val w = input.width
        val h = input.height
        if (w <= 0 || h <= 0) return input

        val src: Bitmap = if (input.config == Bitmap.Config.ARGB_8888) {
            input
        } else {
            input.copy(Bitmap.Config.ARGB_8888, false) ?: return input
        }

        val px = IntArray(w * h)
        src.getPixels(px, 0, w, 0, 0, w, h)

        // ---------- 1) 统计直方图（降采样） ----------
        val step = maxOf(1, px.size / 60000)
        val lums = FloatArray(px.size / step + 1)
        var count = 0
        var i = 0
        while (i < px.size && count < lums.size) {
            val c = px[i]
            val r = ((c shr 16) and 0xFF) / 255f
            val g = ((c shr 8) and 0xFF) / 255f
            val b = (c and 0xFF) / 255f
            lums[count++] = 0.2126f * r + 0.7152f * g + 0.0722f * b
            i += step
        }
        if (count < 8) return input

        val sorted = lums.copyOf(count)
        sorted.sort()
        fun q(p: Float): Float = sorted[((count - 1) * p).toInt().coerceIn(0, count - 1)]

        var hiCount = 0
        var loCount = 0
        for (k in 0 until count) {
            if (sorted[k] > 0.97f) hiCount++
            if (sorted[k] < 0.03f) loCount++
        }
        val clipHi = hiCount.toFloat() / count
        val clipLo = loCount.toFloat() / count

        val median = q(0.50f)
        val p01 = q(0.01f)
        val p05 = q(0.05f)
        val p95 = q(0.95f)
        val p99 = q(0.99f)
        val spread = p95 - p05

        // ---------- 2) 自动影调（Lightroom Auto 的算法近似） ----------
        val ev = ((ln(0.44f / maxOf(median, 1e-3f)) / ln(2f)) * 0.55f)
            .coerceIn(-0.8f, 0.8f)

        var hl = 0f
        if (p99 > 0.92f || clipHi > 0.0015f) {
            hl = -((p99 - 0.90f) * 260f + clipHi * 2200f).coerceIn(0f, 55f)
        }
        var sh = 0f
        if (p01 < 0.14f || clipLo > 0.01f) {
            sh = ((0.14f - p01) * 230f + clipLo * 500f).coerceIn(0f, 45f)
        }
        val wh = ((0.93f - p95) * 180f - clipHi * 1600f).coerceIn(-30f, 30f)
        val bl = ((0.035f - p05) * 240f + clipLo * 400f).coerceIn(-35f, 25f)
        val ct = (14f - spread * 22f).coerceIn(-8f, 18f)

        val off = floatArrayOf(
            value.getOrElse(0) { 0f },
            value.getOrElse(1) { 0f },
            value.getOrElse(2) { 0f },
            value.getOrElse(3) { 0f },
            value.getOrElse(4) { 0f },
            value.getOrElse(5) { 0f }
        )

        val expo = (ev + off[0]).coerceIn(-1.5f, 1.5f)
        val contr = (ct + off[1]).coerceIn(-100f, 100f)
        val hlv = (hl + off[2]).coerceIn(-100f, 100f)
        val shv = (sh + off[3]).coerceIn(-100f, 100f)
        val whv = (wh + off[4]).coerceIn(-100f, 100f)
        val blv = (bl + off[5]).coerceIn(-100f, 100f)

        val gain = 2f.pow(expo)

        // ---------- 3) 逐像素应用（各项只作用于对应影调区域） ----------
        val out = IntArray(px.size)
        for (idx in px.indices) {
            val c = px[idx]
            val alpha = (c ushr 24) and 0xFF

            var r = ((c shr 16) and 0xFF) / 255f * gain
            var g = ((c shr 8) and 0xFF) / 255f * gain
            var b = (c and 0xFF) / 255f * gain
            var lum = (0.2126f * r + 0.7152f * g + 0.0722f * b).coerceIn(0f, 1f)

            if (hlv != 0f) {                                   // 高光：亮部回收
                val m = mask(lum, 0.55f, 0.98f)
                if (hlv < 0f) {
                    val f = 1f - abs(hlv) / 100f * 0.55f * m
                    r *= f; g *= f; b *= f
                }
            }
            if (shv != 0f) {                                   // 阴影：暗部提升
                val m = mask(1f - lum, 0.55f, 0.98f)
                if (shv > 0f) {
                    val add = shv / 100f * 0.5f * m
                    r += add * (1f - r); g += add * (1f - g); b += add * (1f - b)
                } else {
                    val f = 1f + shv / 100f * 0.5f * m
                    r *= f; g *= f; b *= f
                }
            }
            lum = (0.2126f * r + 0.7152f * g + 0.0722f * b).coerceIn(0f, 1f)

            if (whv != 0f) {                                   // 白色色阶：最亮端
                val m = mask(lum, 0.75f, 1f)
                if (whv > 0f) {
                    val add = whv / 100f * 0.30f * m
                    r += add * (1f - r); g += add * (1f - g); b += add * (1f - b)
                } else {
                    val f = 1f - abs(whv) / 100f * 0.30f * m
                    r *= f; g *= f; b *= f
                }
            }
            if (blv != 0f) {                                   // 黑色色阶：最暗端
                val m = mask(1f - lum, 0.75f, 1f)
                if (blv < 0f) {
                    val f = 1f - abs(blv) / 100f * 0.22f * m
                    r *= f; g *= f; b *= f
                } else {
                    val add = blv / 100f * 0.30f * m
                    r += add * (1f - r); g += add * (1f - g); b += add * (1f - b)
                }
            }
            if (contr != 0f) {                                 // 对比度：绕中灰 S
                val k = 1f + contr / 100f * 0.5f
                r = 0.5f + (r - 0.5f) * k
                g = 0.5f + (g - 0.5f) * k
                b = 0.5f + (b - 0.5f) * k
            }

            val ri = (r.coerceIn(0f, 1f) * 255f + 0.5f).toInt()
            val gi = (g.coerceIn(0f, 1f) * 255f + 0.5f).toInt()
            val bi = (b.coerceIn(0f, 1f) * 255f + 0.5f).toInt()
            out[idx] = (alpha shl 24) or (ri shl 16) or (gi shl 8) or bi
        }

        val result = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        result.setPixels(out, 0, w, 0, 0, w, h)
        return result
    }

    /** 影调区域加权遮罩：值越靠近 [hi] 权重越大，t^1.5 让过渡自然 */
    private fun mask(v: Float, lo: Float, hi: Float): Float {
        val t = ((v - lo) / (hi - lo)).coerceIn(0f, 1f)
        return t.pow(1.5f)
    }
}
