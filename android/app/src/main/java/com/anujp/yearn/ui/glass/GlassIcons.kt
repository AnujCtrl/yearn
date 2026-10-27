package com.anujp.yearn.ui.glass

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// Our own stroke glyphs, traced from the boards' inline SVGs (24-unit viewBox). No Material icons.

private fun strokeIcon(name: String, stroke: Float, vararg paths: String): ImageVector =
    ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
        paths.forEach { d ->
            addPath(
                pathData = addPathNodes(d),
                stroke = SolidColor(Color.Black),
                strokeLineWidth = stroke,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            )
        }
    }.build()

object GlassIcons {
    /** Circular arrow: "Sync now". */
    val Sync: ImageVector = strokeIcon("sync", 1.9f, "M20 11a8 8 0 1 0-2.3 5.7", "M20 4v7h-7")

    /** Hub and eight spokes: "Settings". */
    val Gear: ImageVector = strokeIcon(
        "gear", 1.8f,
        "M15.2 12a3.2 3.2 0 1 1-6.4 0a3.2 3.2 0 1 1 6.4 0",
        "M12 2.8v2.6M12 18.6v2.6M2.8 12h2.6M18.6 12h2.6M5.5 5.5l1.8 1.8M16.7 16.7l1.8 1.8M5.5 18.5l1.8-1.8M16.7 7.3l1.8-1.8",
    )

    /** A page with two lines: the save has a note. */
    val Note: ImageVector = strokeIcon("note", 2f, "M6 3.5h12v17H6z", "M9 9h6M9 13h4")
}

@Composable
fun GlassIcon(icon: ImageVector, tint: Color, size: Dp, modifier: Modifier = Modifier) {
    Image(
        icon,
        contentDescription = null,
        modifier = modifier.size(size),
        colorFilter = ColorFilter.tint(tint),
    )
}
