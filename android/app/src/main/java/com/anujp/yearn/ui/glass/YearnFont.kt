package com.anujp.yearn.ui.glass

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.FontWeight
import com.anujp.yearn.R

/**
 * Inter (variable, opsz + wght axes), from https://github.com/google/fonts/tree/main/ofl/inter.
 * SIL Open Font License 1.1; the license text ships at assets/licenses/LICENSE-Inter.txt.
 */
@OptIn(ExperimentalTextApi::class)
val InterFamily: FontFamily = FontFamily(
    listOf(400, 500, 600, 700).map { w ->
        Font(
            R.font.inter_variable,
            weight = FontWeight(w),
            variationSettings = FontVariation.Settings(FontVariation.weight(w)),
        )
    },
)
