package com.anujp.yearn.capture

import android.graphics.BitmapFactory
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.anujp.yearn.ui.glass.GlassStyle
import com.anujp.yearn.ui.glass.InterFamily
import com.anujp.yearn.ui.glass.glass
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
import kotlin.math.hypot
import kotlin.math.max

// docs/design/capture-poc.html, scaled to the phone (about 1.5x the 250 px mock).
/** Blur-off fallback (spec §6): darkened, tinted, ARGB as written. */
private val Scrim = Color(0x9E0E0822)
private val GlassFill = Color.White.copy(alpha = 0.14f)
private val GlassBorder = Color.White.copy(alpha = 0.30f)
private val GlassHighlight = Color.White.copy(alpha = 0.40f) // inset 0 1px 0 rgba(255,255,255,.4)
private val GlassShadow = Color.Black.copy(alpha = 0.30f) // 0 12px 34px rgba(0,0,0,.3)
private val Secondary = Color.White.copy(alpha = 0.80f)
private val BarTrack = Color.White.copy(alpha = 0.18f)
private val BarFill = Brush.horizontalGradient(listOf(Color.White.copy(alpha = 0x88 / 255f), Color.White))
private val DotFill = Color.White.copy(alpha = 0.20f)
private val DotBorder = Color.White.copy(alpha = 0.50f)

private val SheetRadius = 40.dp
private val SheetPadding = 24.dp
private val SideInset = 14.dp
private val BottomInset = 18.dp
private val MaxSheetWidth = 480.dp // keeps the phone proportions on the Fold's inner screen
private val ShadowOffsetY = 12.dp
private val ShadowBlur = 20.dp // BlurMaskFilter radius ~ a CSS 34 px blur
private val SectionGap = 18.dp
private val ThumbWidth = 84.dp
private val ThumbHeight = 112.dp
private val ThumbRadius = 18.dp
private val BarHeight = 5.dp
private val DotRing = 34.dp
private val DotCore = 12.dp
private val TouchTarget = 48.dp
/** The 48 dp touch box sticks out this far past the 34 dp ring on each side. */
private val TouchSlack = (TouchTarget - DotRing) / 2
private val DismissDrag = 80.dp
private val SheetGlass = GlassStyle(
    fill = GlassFill, border = GlassBorder, borderWidth = 1.dp, highlight = GlassHighlight,
    shadow = GlassShadow, shadowOffsetY = ShadowOffsetY, shadowBlur = ShadowBlur,
)

private val LabelText = TextStyle(
    fontFamily = InterFamily, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, lineHeight = 22.sp,
    color = Color.White,
)
private val SecondaryText = TextStyle(
    fontFamily = InterFamily, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 18.sp,
    color = Secondary,
)
private val SavedText = TextStyle(
    fontFamily = InterFamily, fontWeight = FontWeight.SemiBold, fontSize = 18.sp, lineHeight = 24.sp,
    color = Color.White,
)
private val NoteText = TextStyle(
    fontFamily = InterFamily, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 21.sp,
    color = Color.White,
)
private val ButtonText = NoteText.copy(fontWeight = FontWeight.SemiBold)

@Composable
fun CaptureScreen(
    state: SheetState,
    blurEnabled: Boolean,
    onNoteDot: () -> Unit,
    onDraftChange: (String) -> Unit,
    onNoteDone: () -> Unit,
    onDragDismiss: () -> Unit,
) {
    Box(
        Modifier
            .fillMaxSize()
            .then(if (blurEnabled) Modifier else Modifier.background(Scrim)),
        contentAlignment = Alignment.BottomCenter,
    ) {
        when (state) {
            SheetState.Catching, SheetState.Finished -> Unit
            is SheetState.StorageError -> GlassSheet(dragEnabled = false, onDragDismiss = {}) {
                BasicText(state.message, style = LabelText, modifier = Modifier.padding(bottom = TouchSlack))
            }
            is SheetState.Saving -> GlassSheet(dragEnabled = false, onDragDismiss = {}) {
                PreviewBlock(state.progress, fraction = fractionOf(state.progress))
                StatusRow {
                    BasicText(
                        if (state.stillSaving) "Still saving…" else progressLine(state.progress),
                        style = SecondaryText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            is SheetState.Saved -> GlassSheet(dragEnabled = true, onDragDismiss = onDragDismiss) {
                PreviewBlock(state.progress, fraction = 1f)
                StatusRow {
                    BasicText("Saved to yearn", style = SavedText, modifier = Modifier.weight(1f))
                    NoteDot(onNoteDot)
                }
            }
            is SheetState.Note -> GlassSheet(dragEnabled = false, onDragDismiss = {}) {
                PreviewBlock(state.progress, fraction = 1f)
                NoteRow(state.draft, onDraftChange, onNoteDone)
            }
        }
    }
}

/**
 * The see-through white glass sheet. A backdrop blur can't reach across windows here, so the
 * frost is faked as in the POC: translucent fill, hairline border, top highlight, soft shadow.
 * The window behind gets the real cross-window blur (or the scrim).
 */
@Composable
private fun GlassSheet(
    dragEnabled: Boolean,
    onDragDismiss: () -> Unit,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    val drag = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val threshold = with(LocalDensity.current) { DismissDrag.toPx() }
    Column(
        Modifier
            .windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.ime))
            .padding(start = SideInset, end = SideInset, bottom = BottomInset)
            .widthIn(max = MaxSheetWidth)
            .fillMaxWidth()
            // Outside the translation, so drag deltas aren't skewed by the moving sheet.
            .pointerInput(dragEnabled) {
                if (!dragEnabled) return@pointerInput
                detectVerticalDragGestures(
                    onDragEnd = {
                        if (drag.value > threshold) onDragDismiss()
                        else scope.launch { drag.animateTo(0f, spring(Spring.DampingRatioMediumBouncy)) }
                    },
                    onDragCancel = { scope.launch { drag.animateTo(0f, spring(Spring.DampingRatioMediumBouncy)) } },
                ) { change, dy ->
                    change.consume()
                    scope.launch { drag.snapTo(max(0f, drag.value + dy)) }
                }
            }
            .graphicsLayer { translationY = drag.value }
            .glass(SheetRadius, SheetGlass)
            .animateContentSize(spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow))
            // Bottom padding is short by the touch slack: every last row is 48 dp tall but reads as 34.
            .padding(start = SheetPadding, top = SheetPadding, end = SheetPadding, bottom = SheetPadding - TouchSlack),
        content = content,
    )
}

@Composable
private fun PreviewBlock(progress: CaptureProgress, fraction: Float) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Thumb(progress.thumbPath)
        Spacer(Modifier.size(16.dp))
        Column(Modifier.weight(1f)) {
            val p = progress.preview
            BasicText(p.sourceLabel, style = LabelText, maxLines = 1, overflow = TextOverflow.Ellipsis)
            p.url?.let { BasicText(it, style = SecondaryText, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            (p.text ?: p.title)?.let {
                BasicText(it, style = SecondaryText, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
    Spacer(Modifier.height(SectionGap))
    ProgressBar(fraction)
    // The next row is 48 dp tall for touch but draws 34 dp, so 7 dp of this gap lives inside it.
    Spacer(Modifier.height(SectionGap - TouchSlack))
}

@Composable
private fun Thumb(path: String?) {
    val bitmap by produceState<ImageBitmap?>(null, path) {
        value = path?.let { p ->
            withContext(Dispatchers.IO) {
                runCatching { BitmapFactory.decodeFile(File(p).path)?.asImageBitmap() }.getOrNull()
            }
        }
    }
    Box(
        Modifier
            .size(ThumbWidth, ThumbHeight)
            .clip(RoundedCornerShape(ThumbRadius))
            .drawBehind { placeholder(size) },
    ) {
        bitmap?.let {
            Image(it, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize())
        }
    }
}

/** The POC's .thumb: two radial glows on a deep violet, used until (or instead of) a real thumbnail. */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.placeholder(size: Size) {
    drawRect(Color(0xFF2A1846))
    fun glow(color: Color, fx: Float, fy: Float, stop: Float) {
        val c = Offset(size.width * fx, size.height * fy)
        val farthest = hypot(max(c.x, size.width - c.x), max(c.y, size.height - c.y))
        drawRect(Brush.radialGradient(listOf(color, Color.Transparent), center = c, radius = farthest * stop))
    }
    glow(Color(0xFF7A3CFF), 0.75f, 0.70f, 0.55f)
    glow(Color(0xFFFF9A5A), 0.30f, 0.25f, 0.50f)
}

@Composable
private fun ProgressBar(fraction: Float) {
    val shown by animateFloatAsState(fraction.coerceIn(0f, 1f), spring(stiffness = Spring.StiffnessLow), label = "bar")
    Box(
        Modifier
            .fillMaxWidth()
            .height(BarHeight)
            .clip(RoundedCornerShape(BarHeight / 2))
            .background(BarTrack),
    ) {
        Box(Modifier.fillMaxWidth(shown).fillMaxHeight().background(BarFill))
    }
}

@Composable
private fun StatusRow(content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = TouchTarget),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

/** 34 dp ring with a 12 dp dot, inside a 48 dp touch box nudged so the ring sits on the content edge. */
@Composable
private fun NoteDot(onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Box(
        Modifier
            .offset(x = TouchSlack)
            .size(TouchTarget)
            .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = "Add a note" },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(DotRing)
                .graphicsLayer { alpha = if (pressed) 0.6f else 1f }
                .clip(CircleShape)
                .background(DotFill)
                .border(1.dp, DotBorder, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Box(Modifier.size(DotCore).clip(CircleShape).background(Color.White))
        }
    }
}

@Composable
private fun NoteRow(initial: String, onDraftChange: (String) -> Unit, onDone: () -> Unit) {
    var draft by rememberSaveable { mutableStateOf(initial) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    val fieldShape = RoundedCornerShape(ThumbRadius)
    Row(
        // Real 48 dp controls: give back the slack PreviewBlock borrowed for the 34 dp ring row.
        Modifier.fillMaxWidth().padding(top = TouchSlack, bottom = TouchSlack),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        BasicTextField(
            value = draft,
            onValueChange = {
                draft = it
                onDraftChange(it)
            },
            modifier = Modifier.weight(1f).focusRequester(focus),
            textStyle = NoteText,
            cursorBrush = SolidColor(Color.White),
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Sentences,
                imeAction = ImeAction.Done,
            ),
            keyboardActions = KeyboardActions(onDone = { onDone() }),
            maxLines = 4,
            decorationBox = { inner ->
                Box(
                    Modifier
                        .heightIn(min = TouchTarget)
                        .clip(fieldShape)
                        .background(GlassFill)
                        .border(1.dp, GlassBorder, fieldShape)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    if (draft.isEmpty()) BasicText("Add a note", style = NoteText.copy(color = Secondary))
                    inner()
                }
            },
        )
        val interaction = remember { MutableInteractionSource() }
        val pressed by interaction.collectIsPressedAsState()
        val capsule = RoundedCornerShape(TouchTarget / 2)
        Box(
            Modifier
                .height(TouchTarget)
                .graphicsLayer { alpha = if (pressed) 0.6f else 1f }
                .clip(capsule)
                .background(DotFill)
                .border(1.dp, DotBorder, capsule)
                .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onDone)
                .padding(horizontal = 20.dp),
            contentAlignment = Alignment.Center,
        ) {
            BasicText("Done", style = ButtonText)
        }
    }
}

private fun fractionOf(p: CaptureProgress): Float =
    if (p.filesTotal <= 0) 0f else p.filesDone.toFloat() / p.filesTotal

/** "2 of 3 files · 12.4 MB". There is no byte total, so no percentage. */
internal fun progressLine(p: CaptureProgress): String {
    val noun = if (p.filesTotal == 1) "file" else "files"
    val files = "${p.filesDone} of ${p.filesTotal} $noun"
    return if (p.bytesCopied > 0) "$files · ${formatBytes(p.bytesCopied)}" else files
}

internal fun formatBytes(n: Long): String = when {
    n < 1_000 -> "$n B"
    n < 1_000_000 -> String.format(Locale.ROOT, "%.1f KB", n / 1e3)
    n < 1_000_000_000 -> String.format(Locale.ROOT, "%.1f MB", n / 1e6)
    else -> String.format(Locale.ROOT, "%.1f GB", n / 1e9)
}
