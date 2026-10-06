/*
 * [MOD] 影调分析：算出「自动」应该给出的 6 个影调值
 *
 * 公式与 AutoToneFilter 里的自动部分保持一致，这样：
 *  - 「自动」按钮把结果填进滑杆（所见即所得）；
 *  - 自定义滤镜链时传「目标值 - 自动值」作为偏移，最终效果正好等于滑杆显示的值。
 */

package com.t8rin.imagetoolbox.feature.single_edit.presentation.components

import android.graphics.Bitmap
import kotlin.math.ln
import kotlin.math.max

object LightroomToneAnalyzer {

    /** 返回 [exposure, contrast, highlights, shadows, whites, blacks]，与 LightroomValues.tone() 同序 */
    fun autoValues(bitmap: Bitmap): FloatArray {
        val fallback = floatArrayOf(0f, 0f, 0f, 0f, 0f, 0f)
        val w = bitmap.width
        val h = bitmap.height
        if (w <= 0 || h <= 0) return fallback

        val src: Bitmap = if (bitmap.config == Bitmap.Config.ARGB_8888) {
            bitmap
        } else {
            bitmap.copy(Bitmap.Config.ARGB_8888, false) ?: return fallback
        }

        val px = IntArray(w * h)
        src.getPixels(px, 0, w, 0, 0, w, h)

        val step = max(1, px.size / 60000)
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
        if (count < 8) return fallback

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

        val ev = ((ln(0.44f / max(median, 1e-3f)) / ln(2f)) * 0.55f)
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

        return floatArrayOf(ev, ct, hl, sh, wh, bl)
    }
}

private fun Float.coerceIn(min: Float, max: Float): Float =
    if (this < min) min else if (this > max) max else this
