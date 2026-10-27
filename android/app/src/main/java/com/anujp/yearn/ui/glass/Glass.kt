package com.anujp.yearn.ui.glass

import android.graphics.BlurMaskFilter
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect

// iOS Liquid Glass, after the approved boards (.superpowers/sdd/…/boards/App-*.dc.html). Never Material.

/**
 * One glass recipe: translucent [fill] over a backdrop blur, a hairline [border], an inner top
 * highlight (CSS `inset 0 1px 1px`), an optional inner bottom highlight and a soft drop shadow.
 * [shadowBlur] is a BlurMaskFilter radius; [cssBlur] converts a CSS box-shadow blur to one.
 */
@Immutable
data class GlassStyle(
    val fill: Color,
    val border: Color,
    val borderWidth: Dp = 0.5.dp,
    val highlight: Color,
    val bottomHighlight: Color = Color.Transparent,
    val shadow: Color = Color.Transparent,
    val shadowOffsetY: Dp = 0.dp,
    val shadowBlur: Dp = 0.dp,
    val blurRadius: Dp = 12.dp,
)

/** CSS box-shadow blur B has sigma B/2; BlurMaskFilter's radius r has sigma ~ 0.577 r. */
fun cssBlur(px: Float): Dp = (px * 0.866f).dp

private val White = Color.White
private val Black = Color.Black

@Immutable
data class YearnColors(
    val isDark: Boolean,
    val background: Color,
    val groupFill: Color,
    val rowPressed: Color,
    val label: Color,
    val secondaryLabel: Color,
    val separator: Color,
    /** The blue of "Sync now"; the same in both themes. */
    val accent: Color,
    /** "Uploading": a link-blue status word. */
    val active: Color,
    val danger: Color,
    /** The floating capsule; the brief's glass values. */
    val glass: GlassStyle,
    /** 44 dp circle buttons in the title bar. */
    val glassButton: GlassStyle,
    /** Smaller circle buttons that sit on a glass surface already (the sidebar header). */
    val glassButtonOnGlass: GlassStyle,
    val sidebarGlass: GlassStyle,
    val sidebarSecondary: Color,
    val sidebarDanger: Color,
    val sidebarSelected: Color,
) {
    companion object {
        val Light = YearnColors(
            isDark = false,
            background = Color(0xFFF2F2F7),
            groupFill = Color(0xFFFFFFFF),
            rowPressed = Color(0xFFE5E5EA),
            label = Color(0xFF000000),
            secondaryLabel = Color(0xFF6C6C70),
            separator = Color(0xFFC6C6C8),
            accent = Color(0xFF0A84FF),
            active = Color(0xFF0064D2),
            danger = Color(0xFFD70015),
            glass = GlassStyle(
                fill = White.copy(alpha = 0.32f),
                border = White.copy(alpha = 0.75f),
                highlight = White.copy(alpha = 0.95f),
                bottomHighlight = White.copy(alpha = 0.35f),
                shadow = Black.copy(alpha = 0.14f),
                shadowOffsetY = 10.dp,
                shadowBlur = cssBlur(30f),
                blurRadius = 12.dp,
            ),
            glassButton = GlassStyle(
                fill = White.copy(alpha = 0.5f),
                border = White.copy(alpha = 0.7f),
                highlight = White.copy(alpha = 0.9f),
                shadow = Black.copy(alpha = 0.1f),
                shadowOffsetY = 6.dp,
                shadowBlur = cssBlur(18f),
                blurRadius = 14.dp,
            ),
            glassButtonOnGlass = GlassStyle(
                fill = White.copy(alpha = 0.5f),
                border = White.copy(alpha = 0.7f),
                highlight = White.copy(alpha = 0.9f),
            ),
            sidebarGlass = GlassStyle(
                fill = White.copy(alpha = 0.55f),
                border = White.copy(alpha = 0.75f),
                highlight = White.copy(alpha = 0.95f),
                shadow = Black.copy(alpha = 0.14f),
                shadowOffsetY = 16.dp,
                shadowBlur = cssBlur(40f),
                blurRadius = 24.dp,
            ),
            sidebarSecondary = Color(0xFF6C6C70),
            sidebarDanger = Color(0xFFD70015),
            sidebarSelected = Color(0x29787880),
        )

        val Dark = YearnColors(
            isDark = true,
            background = Color(0xFF000000),
            groupFill = Color(0xFF1C1C1E),
            rowPressed = Color(0xFF2C2C2E),
            label = Color(0xFFFFFFFF),
            secondaryLabel = Color(0xFFAEAEB2),
            separator = White.copy(alpha = 0.12f),
            accent = Color(0xFF0A84FF),
            active = Color(0xFF0A84FF),
            danger = Color(0xFFFF6961),
            glass = GlassStyle(
                fill = White.copy(alpha = 0.14f),
                border = White.copy(alpha = 0.35f),
                highlight = White.copy(alpha = 0.5f),
                bottomHighlight = White.copy(alpha = 0.15f),
                shadow = Black.copy(alpha = 0.4f),
                shadowOffsetY = 10.dp,
                shadowBlur = cssBlur(30f),
                blurRadius = 12.dp,
            ),
            glassButton = GlassStyle(
                fill = White.copy(alpha = 0.12f),
                border = White.copy(alpha = 0.3f),
                highlight = White.copy(alpha = 0.4f),
                blurRadius = 14.dp,
            ),
            glassButtonOnGlass = GlassStyle(
                fill = White.copy(alpha = 0.12f),
                border = White.copy(alpha = 0.3f),
                highlight = Color.Transparent,
            ),
            sidebarGlass = GlassStyle(
                fill = Color(0x6B2C2C30), // rgba(44,44,48,0.42)
                border = White.copy(alpha = 0.22f),
                highlight = White.copy(alpha = 0.35f),
                shadow = Black.copy(alpha = 0.45f),
                shadowOffsetY = 16.dp,
                shadowBlur = cssBlur(40f),
                blurRadius = 24.dp,
            ),
            sidebarSecondary = Color(0xFFC7C7CC),
            sidebarDanger = Color(0xFFFF6961),
            sidebarSelected = White.copy(alpha = 0.18f),
        )
    }
}

val LocalYearnColors = staticCompositionLocalOf { YearnColors.Light }

/** Light or dark, following the system setting. */
@Composable
fun YearnTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalYearnColors provides if (dark) YearnColors.Dark else YearnColors.Light, content = content)
}

/** Inter everywhere; sizes and weights from the boards. Colours are applied at the call site. */
object YearnType {
    private fun inter(size: Int, line: Int?, weight: FontWeight, tracking: Float = 0f) = TextStyle(
        fontFamily = InterFamily,
        fontWeight = weight,
        fontSize = size.sp,
        lineHeight = line?.sp ?: TextUnit.Unspecified,
        letterSpacing = if (tracking == 0f) TextUnit.Unspecified else tracking.em,
    )

    val LargeTitle = inter(34, 41, FontWeight.Bold, -0.02f)
    val SidebarTitle = inter(26, 32, FontWeight.Bold, -0.02f)
    val DetailTitle = inter(28, 34, FontWeight.Bold, -0.02f)
    val RowTitle = inter(16, 21, FontWeight.Medium)
    val RowSubtitle = inter(14, 19, FontWeight.Normal)
    val Status = inter(13, 18, FontWeight.Normal)
    val SectionHeader = inter(13, 18, FontWeight.Normal, 0.02f)
    val SidebarRowTitle = inter(15, 20, FontWeight.Medium)
    val SidebarRowSubtitle = inter(13, 17, FontWeight.Normal)
    val Caption = inter(13, 18, FontWeight.Normal)
    val Body = inter(16, 22, FontWeight.Normal)
    val Subhead = inter(15, 20, FontWeight.Normal)
    val SheetTitle = inter(26, 32, FontWeight.Bold, -0.02f)
    val RowValue = inter(15, 20, FontWeight.Normal)
    val CapsuleLabel = inter(15, 20, FontWeight.Medium)
    val Button = inter(15, 20, FontWeight.SemiBold)
    val TileLetter = inter(22, 26, FontWeight.SemiBold)
}

/**
 * Draws [style] as glass in a [radius]-rounded shape and clips the content to it. With a
 * [hazeState], the fill tints a real backdrop blur of that state's source; without one (or where
 * a blur can't reach, e.g. across windows) the fill is drawn flat and the frost is implied.
 * [backdrop] is the colour Haze assumes behind the blurred content.
 */
fun Modifier.glass(
    radius: Dp,
    style: GlassStyle,
    hazeState: HazeState? = null,
    backdrop: Color = Color.Unspecified,
): Modifier {
    val shape = RoundedCornerShape(radius)
    val shadowed = if (style.shadow.alpha == 0f) this else this.drawWithCache {
        val r = radius.toPx()
        val outer = RoundRect(Rect(Offset.Zero, size), CornerRadius(r))
        val body = Path().apply { addRoundRect(outer) }
        val shadow = Path().apply { addRoundRect(outer.shiftedDown(style.shadowOffsetY.toPx())) }.asAndroidPath()
        val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color = style.shadow.toArgb()
            if (style.shadowBlur > 0.dp) maskFilter = BlurMaskFilter(style.shadowBlur.toPx(), BlurMaskFilter.Blur.NORMAL)
        }
        onDrawBehind {
            // Like CSS box-shadow, the shadow never shows through the translucent fill.
            clipPath(body, ClipOp.Difference) { drawIntoCanvas { it.nativeCanvas.drawPath(shadow, paint) } }
        }
    }
    val filled = if (hazeState != null) {
        shadowed.clip(shape).hazeEffect(
            hazeState,
            HazeStyle(
                backgroundColor = backdrop,
                tint = HazeTint(style.fill),
                blurRadius = style.blurRadius,
                noiseFactor = 0f,
            ),
        )
    } else {
        shadowed.clip(shape).background(style.fill)
    }
    return filled
        .drawWithCache {
            val r = radius.toPx()
            val b = style.borderWidth.toPx()
            val h = 1.dp.toPx()
            val inner = RoundRect(b, b, size.width - b, size.height - b, CornerRadius((r - b).coerceAtLeast(0f)))
            val innerPath = Path().apply { addRoundRect(inner) }
            // inset 0 1px 1px: a 1 dp crescent just inside the border along the top curve…
            val top = Path().apply {
                op(innerPath, Path().apply { addRoundRect(inner.shiftedDown(h)) }, PathOperation.Difference)
            }
            // …and inset 0 -1px 1px along the bottom.
            val bottom = Path().apply {
                op(innerPath, Path().apply { addRoundRect(inner.shiftedDown(-h)) }, PathOperation.Difference)
            }
            onDrawBehind {
                if (style.highlight.alpha > 0f) drawPath(top, style.highlight)
                if (style.bottomHighlight.alpha > 0f) drawPath(bottom, style.bottomHighlight)
            }
        }
        .border(style.borderWidth, style.border, shape)
}

private fun RoundRect.shiftedDown(dy: Float) =
    RoundRect(left, top + dy, right, bottom + dy, CornerRadius(topLeftCornerRadius.x, topLeftCornerRadius.y))

/**
 * For a full-screen layer drawn over another screen that stays composed (the list under the
 * detail): every touch inside lands here, so empty areas never fall through to what is underneath.
 * Descendants still get their touches first.
 */
fun Modifier.blocksTouchesBelow(): Modifier = this.pointerInput(Unit) {
    awaitPointerEventScope {
        while (true) awaitPointerEvent()
    }
}

/** Click without a Material ripple: the target dims and shrinks a touch while pressed. */
@Composable
fun Modifier.pressable(
    onClick: () -> Unit,
    role: Role = Role.Button,
    label: String? = null,
    pressedScale: Float = 0.94f,
): Modifier {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) pressedScale else 1f, spring(dampingRatio = 0.6f, stiffness = 900f), label = "press")
    return this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
            alpha = if (pressed) 0.75f else 1f
        }
        .clickable(interactionSource = interaction, indication = null, role = role, onClick = onClick)
        .then(if (label != null) Modifier.semantics { contentDescription = label } else Modifier)
}

/**
 * A touch area at least [size] wide and tall around small inline text or a capsule, without moving
 * anything: the content (put it in a centring Box with this first, then [pressable]) is measured with
 * the minimum, but reports its natural size to the parent and is centred over it, so the hit area
 * overflows evenly instead of pushing the row apart. Compose hit-tests a node's own bounds.
 */
fun Modifier.minTouchTarget(size: Dp = 48.dp): Modifier = layout { measurable, constraints ->
    val minPx = size.roundToPx()
    val placeable = measurable.measure(
        constraints.copy(
            minWidth = maxOf(constraints.minWidth, minPx).coerceAtMost(constraints.maxWidth),
            minHeight = maxOf(constraints.minHeight, minPx).coerceAtMost(constraints.maxHeight),
        ),
    )
    val w = measurable.maxIntrinsicWidth(placeable.height).coerceIn(constraints.minWidth, placeable.width)
    val h = measurable.minIntrinsicHeight(w).coerceIn(constraints.minHeight, placeable.height)
    layout(w, h) { placeable.place((w - placeable.width) / 2, (h - placeable.height) / 2) }
}

/** A round glass button (44 dp in the title bar) holding one glyph. */
@Composable
fun GlassCircleButton(
    onClick: () -> Unit,
    contentDescription: String,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    style: GlassStyle = LocalYearnColors.current.glassButton,
    hazeState: HazeState? = null,
    content: @Composable () -> Unit,
) {
    val colors = LocalYearnColors.current
    Box(
        modifier
            .pressable(onClick, label = contentDescription)
            .size(size)
            .glass(size / 2, style, hazeState, colors.background),
        contentAlignment = Alignment.Center,
    ) { content() }
}

/** The floating glass capsule (60 dp, fully rounded) that sits over the bottom of the list. */
@Composable
fun GlassCapsule(
    modifier: Modifier = Modifier,
    height: Dp = 60.dp,
    style: GlassStyle = LocalYearnColors.current.glass,
    hazeState: HazeState? = null,
    content: @Composable RowScope.() -> Unit,
) {
    val colors = LocalYearnColors.current
    Row(
        modifier
            .height(height)
            .glass(height / 2, style, hazeState, colors.background),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

/** The blue pill inside a capsule ("Sync now"). */
@Composable
fun AccentPill(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = LocalYearnColors.current
    Box(
        modifier
            .pressable(onClick, pressedScale = 0.96f)
            .height(44.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(colors.accent)
            .padding(horizontal = 18.dp),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(text, style = YearnType.Button.copy(color = Color.White))
    }
}

/** iOS inset grouped section: a white (or #1C1C1E) block with 26 dp corners. */
val InsetGroupRadius = 26.dp

@Composable
fun InsetGroup(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val colors = LocalYearnColors.current
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(InsetGroupRadius))
            .background(colors.groupFill),
        content = content,
    )
}

/**
 * The same group, one lazy item at a time: only the first item rounds its top corners and only
 * the last its bottom ones, so a long section never has to be one giant item.
 */
fun Modifier.insetGroupItem(first: Boolean, last: Boolean, fill: Color): Modifier {
    val top = if (first) InsetGroupRadius else 0.dp
    val bottom = if (last) InsetGroupRadius else 0.dp
    return this.clip(RoundedCornerShape(top, top, bottom, bottom)).background(fill)
}

/** A 0.5 dp hairline along the bottom edge, starting [startInset] in from the left. */
fun Modifier.bottomHairline(color: Color, startInset: Dp, endInset: Dp = 0.dp): Modifier = drawBehind {
    val w = 0.5.dp.toPx()
    val y = size.height - w / 2
    drawLine(color, Offset(startInset.toPx(), y), Offset(size.width - endInset.toPx(), y), strokeWidth = w)
}

/** "Saves": 34 sp bold, tracking −0.02 em. */
@Composable
fun LargeTitle(text: String, modifier: Modifier = Modifier) {
    val colors = LocalYearnColors.current
    BasicText(text, modifier.semantics { heading() }, style = YearnType.LargeTitle.copy(color = colors.label))
}
