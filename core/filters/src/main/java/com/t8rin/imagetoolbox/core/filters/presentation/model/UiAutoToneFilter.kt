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

package com.t8rin.imagetoolbox.core.filters.presentation.model

import com.t8rin.imagetoolbox.core.filters.domain.model.Filter
import com.t8rin.imagetoolbox.core.filters.domain.model.FilterParam
import com.t8rin.imagetoolbox.core.ksp.annotations.UiFilterInject
import com.t8rin.imagetoolbox.core.resources.R

/**
 * [MOD] 一键调色 (Auto Tone)
 *
 * 默认全 0 = 纯自动：分析直方图自动定曝光/对比度/高光/阴影/白色/黑色六个影调。
 * 想微调就把对应滑杆推离 0，会叠加在自动结果之上。
 */
@UiFilterInject(group = UiFilterInject.Groups.ENHANCEMENT)
class UiAutoToneFilter(
    override val value: FloatArray = floatArrayOf(0f, 0f, 0f, 0f, 0f, 0f)
) : UiFilter<FloatArray>(
    title = R.string.auto_tone,
    value = value,
    paramsInfo = listOf(
        FilterParam(
            title = R.string.exposure,
            valueRange = -1f..1f,
            roundTo = 2
        ),
        FilterParam(
            title = R.string.contrast,
            valueRange = -100f..100f,
            roundTo = 0
        ),
        FilterParam(
            title = R.string.highlights,
            valueRange = -100f..100f,
            roundTo = 0
        ),
        FilterParam(
            title = R.string.shadows,
            valueRange = -100f..100f,
            roundTo = 0
        ),
        FilterParam(
            title = R.string.tone_whites,
            valueRange = -100f..100f,
            roundTo = 0
        ),
        FilterParam(
            title = R.string.tone_blacks,
            valueRange = -100f..100f,
            roundTo = 0
        )
    )
), Filter.AutoTone
