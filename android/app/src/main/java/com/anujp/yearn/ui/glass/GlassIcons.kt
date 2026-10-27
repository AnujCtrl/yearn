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

    /** An eight-toothed gear around a hub: "Settings". Teeth are 14° wide on r 9.7, roots on r 7.3. */
    val Gear: ImageVector = strokeIcon(
        "gear", 1.7f,
        "M10.36 4.89L10.82 2.37A9.7 9.7 0 0 1 13.18 2.37L13.64 4.89A7.3 7.3 0 0 1 15.87 5.81L17.97 4.36" +
            "A9.7 9.7 0 0 1 19.64 6.03L18.19 8.13A7.3 7.3 0 0 1 19.11 10.36L21.63 10.82A9.7 9.7 0 0 1 21.63 13.18" +
            "L19.11 13.64A7.3 7.3 0 0 1 18.19 15.87L19.64 17.97A9.7 9.7 0 0 1 17.97 19.64L15.87 18.19" +
            "A7.3 7.3 0 0 1 13.64 19.11L13.18 21.63A9.7 9.7 0 0 1 10.82 21.63L10.36 19.11A7.3 7.3 0 0 1 8.13 18.19" +
            "L6.03 19.64A9.7 9.7 0 0 1 4.36 17.97L5.81 15.87A7.3 7.3 0 0 1 4.89 13.64L2.37 13.18" +
            "A9.7 9.7 0 0 1 2.37 10.82L4.89 10.36A7.3 7.3 0 0 1 5.81 8.13L4.36 6.03A9.7 9.7 0 0 1 6.03 4.36" +
            "L8.13 5.81A7.3 7.3 0 0 1 10.36 4.89Z",
        "M15 12a3 3 0 1 1-6 0a3 3 0 1 1 6 0",
    )

    /** "Back": the boards' chevron. */
    val Back: ImageVector = strokeIcon("back", 2.2f, "M15 5l-7 7 7 7")

    /** "Open link": the detail board's out-of-the-box arrow. */
    val OpenOut: ImageVector = strokeIcon("open", 1.9f, "M12 3v12M8 7l4-4 4 4", "M6 11v8.5h12V11")

    /** Disclosure chevron at the end of a tappable row. */
    val Chevron: ImageVector = strokeIcon("chevron", 2.6f, "M9 5l7 7-7 7")

    /** Settings tiles. */
    val Check: ImageVector = strokeIcon("check", 2.6f, "M5 12.5l4.5 4.5L19 7.5")
    val Swap: ImageVector = strokeIcon("swap", 2.2f, "M4 8h14M14 4l4 4-4 4", "M20 16H6M10 12l-4 4 4 4")
    val Moon: ImageVector = strokeIcon("moon", 2.2f, "M19.5 14.5A8 8 0 1 1 9.5 4.5a6.5 6.5 0 0 0 10 10z")

    /** A page with a folded corner: one file of a save. */
    val File: ImageVector = strokeIcon("file", 1.8f, "M6 3h8l4 4v14H6z", "M14 3v4h4")

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
