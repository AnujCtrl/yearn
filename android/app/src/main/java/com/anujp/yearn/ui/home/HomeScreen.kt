package com.anujp.yearn.ui.home

import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
import com.anujp.yearn.ui.glass.AccentPill
import com.anujp.yearn.ui.glass.GlassCapsule
import com.anujp.yearn.ui.glass.GlassCircleButton
import com.anujp.yearn.ui.glass.GlassIcon
import com.anujp.yearn.ui.glass.GlassIcons
import com.anujp.yearn.ui.glass.LargeTitle
import com.anujp.yearn.ui.glass.LocalYearnColors
import com.anujp.yearn.ui.glass.YearnColors
import com.anujp.yearn.ui.glass.YearnType
import com.anujp.yearn.ui.glass.bottomHairline
import com.anujp.yearn.ui.glass.glass
import com.anujp.yearn.ui.glass.insetGroupItem
import com.anujp.yearn.ui.glass.minTouchTarget
import com.anujp.yearn.ui.glass.pressable
import dev.chrisbanes.haze.HazeProgressive
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.hypot
import kotlin.math.max

// Layout values from boards/App-Home.dc.html (list) and boards/App-Inner.dc.html (sidebar).
private val PageGutter = 16.dp
private val ButtonGap = 10.dp
private val TitleBelowButtons = 12.dp
private val ThumbSize = 52.dp
private val ThumbRadius = 13.dp
private val RowGap = 14.dp
private val CapsuleBottom = 26.dp

private val SidebarWidth = 284.dp
private val SidebarRadius = 32.dp
private val SidebarInset = 12.dp
private val SidebarThumb = 42.dp
private val SidebarThumbRadius = 11.dp
private val DetailGap = 16.dp

internal val safeInsets: WindowInsets
    @Composable get() = WindowInsets.systemBars.union(WindowInsets.displayCutout)

/** The phone (and folded) layout: large title, inset groups, glass buttons and capsule floating over the list. */
@Composable
fun SavesList(
    rows: List<SaveRow>,
    waitingCount: Int,
    header: String,
    onSync: () -> Unit,
    onRetry: (String) -> Unit,
    onOpenSettings: () -> Unit,
    onOpen: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalYearnColors.current
    val haze = rememberHazeState()
    val insets = safeInsets.asPaddingValues()
    val top = insets.calculateTopPadding()
    val bottom = insets.calculateBottomPadding()
    val capsuleBottom = max(CapsuleBottom, bottom + 8.dp)
    val listState = rememberLazyListState()

    Box(modifier.fillMaxSize().background(colors.background)) {
        LazyColumn(
            Modifier.fillMaxSize().hazeSource(haze).background(colors.background),
            state = listState,
            contentPadding = PaddingValues(
                start = PageGutter,
                end = PageGutter,
                top = top + 4.dp + 44.dp + TitleBelowButtons,
                bottom = if (waitingCount > 0) capsuleBottom + 60.dp + 24.dp else bottom + 24.dp,
            ),
        ) {
            item(key = "title", contentType = "title") {
                Column(Modifier.padding(start = 4.dp, bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    LargeTitle("Saves")
                    if (header.isNotEmpty()) {
                        BasicText(header, style = YearnType.Subhead.copy(color = colors.secondaryLabel), maxLines = 1)
                    }
                }
            }
            if (rows.isEmpty()) {
                item(key = "empty", contentType = "empty") { EmptyState(Modifier.padding(start = 4.dp, top = 24.dp)) }
            }
            val (today, earlier) = rows.partition { it.today }
            listOf("Today" to today, "Earlier" to earlier).forEach { (name, group) ->
                if (group.isEmpty()) return@forEach
                item(key = "h-$name", contentType = "header") { SectionHeader(name) }
                itemsIndexed(group, key = { _, r -> r.id }, contentType = { _, _ -> "row" }) { i, row ->
                    SaveRowItem(
                        row,
                        first = i == 0,
                        last = i == group.lastIndex,
                        onClick = { onOpen(row.id) },
                        onRetry = { onRetry(row.id) },
                    )
                }
            }
        }

        // iOS scroll-edge effect: once the list moves, a soft frost fades in under the status bar.
        if (listState.canScrollBackward) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(top + 4.dp + 44.dp + 8.dp)
                    .hazeEffect(haze, HazeStyle(colors.background, HazeTint(colors.background.copy(alpha = 0.55f)), 20.dp, 0f)) {
                        progressive = HazeProgressive.verticalGradient(startIntensity = 1f, endIntensity = 0f)
                    },
            )
        }

        Row(
            Modifier.align(Alignment.TopEnd).padding(top = top + 4.dp, end = PageGutter),
            horizontalArrangement = Arrangement.spacedBy(ButtonGap),
        ) {
            GlassCircleButton(onSync, "Sync now", hazeState = haze) {
                GlassIcon(GlassIcons.Sync, colors.label, 20.dp)
            }
            GlassCircleButton(onOpenSettings, "Settings", hazeState = haze) {
                GlassIcon(GlassIcons.Gear, colors.label, 21.dp)
            }
        }

        // Keep the last count on screen while the capsule slides away.
        val shownCount = remember { intArrayOf(0) }
        if (waitingCount > 0) shownCount[0] = waitingCount
        AnimatedVisibility(
            visible = waitingCount > 0,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = fadeIn() + slideInVertically(spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow)) { it },
            exit = fadeOut() + slideOutVertically { it },
        ) {
            GlassCapsule(
                Modifier
                    .padding(start = PageGutter, end = PageGutter, bottom = capsuleBottom)
                    .fillMaxWidth(),
                hazeState = haze,
            ) {
                BasicText(
                    "${shownCount[0]} waiting for your desktop",
                    Modifier.weight(1f).padding(start = 22.dp, end = 12.dp),
                    style = YearnType.CapsuleLabel.copy(color = colors.label),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                AccentPill("Sync now", onSync, Modifier.padding(end = 8.dp))
            }
        }
    }
}

@Composable
private fun SectionHeader(name: String) {
    val colors = LocalYearnColors.current
    BasicText(
        name.uppercase(),
        Modifier.padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 7.dp),
        style = YearnType.SectionHeader.copy(color = colors.secondaryLabel),
    )
}

@Composable
private fun EmptyState(modifier: Modifier = Modifier) {
    val colors = LocalYearnColors.current
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        BasicText("Nothing saved yet", style = YearnType.RowTitle.copy(color = colors.label))
        BasicText(
            "Share a link, a photo or some text to yearn and it shows up here.",
            style = YearnType.RowSubtitle.copy(color = colors.secondaryLabel),
        )
    }
}

private fun statusColor(status: String, c: YearnColors): Color = when (status) {
    "Uploading" -> c.active
    "Failed" -> c.danger
    else -> c.secondaryLabel
}

@Composable
private fun SaveRowItem(row: SaveRow, first: Boolean, last: Boolean, onClick: () -> Unit, onRetry: () -> Unit) {
    val colors = LocalYearnColors.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Row(
        Modifier
            .fillMaxWidth()
            .insetGroupItem(first, last, if (pressed) colors.rowPressed else colors.groupFill)
            .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick)
            .then(if (last) Modifier else Modifier.bottomHairline(colors.separator, PageGutter + ThumbSize + RowGap))
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(RowGap),
    ) {
        SaveThumb(row.thumbPath, row.source, ThumbSize, ThumbRadius)
        Column(Modifier.weight(1f).padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            BasicText(
                row.title,
                style = YearnType.RowTitle.copy(color = colors.label),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (row.status == "Failed") {
                RetryLine(row.failedReason ?: row.subtitle, "Retry", colors.danger, YearnType.RowSubtitle, onRetry)
            } else {
                BasicText(
                    row.subtitle,
                    style = YearnType.RowSubtitle.copy(color = colors.secondaryLabel),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            if (row.hasNote) GlassIcon(GlassIcons.Note, colors.secondaryLabel, 14.dp)
            BasicText(row.status, style = YearnType.Status.copy(color = statusColor(row.status, colors)))
        }
    }
}

/** "Desktop rejected the file · Retry", where only "Retry" is the button. */
@Composable
private fun RetryLine(
    lead: String,
    action: String,
    color: Color,
    style: androidx.compose.ui.text.TextStyle,
    onRetry: () -> Unit,
    separator: String = " · ",
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        BasicText(
            lead + separator,
            Modifier.weight(1f, fill = false),
            style = style.copy(color = color),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Box(
            Modifier.minTouchTarget().pressable(onRetry, label = "Retry upload", pressedScale = 1f),
            contentAlignment = Alignment.Center,
        ) {
            BasicText(action, style = style.copy(color = color, fontWeight = FontWeight.SemiBold), maxLines = 1)
        }
    }
}

private object ThumbCache {
    /** Decoded thumbnails, at most ~16 MB, so scrolling back doesn't decode again. */
    val bitmaps = object : LruCache<String, ImageBitmap>(16 * 1024 * 1024) {
        override fun sizeOf(key: String, value: ImageBitmap) = value.width * value.height * 4
    }
}

/** Decodes [path] off the main thread, subsampled to about twice [targetPx]; null if missing or unreadable. */
internal suspend fun decodeThumb(path: String, targetPx: Int): ImageBitmap? = withContext(Dispatchers.IO) {
    val key = "$path@$targetPx"
    ThumbCache.bitmaps.get(key)?.let { return@withContext it }
    runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@runCatching null
        var sample = 1
        while (minOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= targetPx * 2) sample *= 2
        BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })?.asImageBitmap()
    }.getOrNull()?.also { ThumbCache.bitmaps.put(key, it) }
}

/** The save's thumbnail, or a tile tinted from its source with the source's first letter. */
@Composable
private fun SaveThumb(path: String?, source: String, size: Dp, radius: Dp) {
    val px = with(LocalDensity.current) { size.roundToPx() }
    val bitmap by produceState<ImageBitmap?>(path?.let { ThumbCache.bitmaps.get("$it@$px") }, path, px) {
        if (value == null && path != null) value = decodeThumb(path, px)
    }
    Box(Modifier.size(size).clip(RoundedCornerShape(radius)), contentAlignment = Alignment.Center) {
        val b = bitmap
        if (b != null) {
            Image(b, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize())
        } else {
            PlaceholderTile(source, size)
        }
    }
}

// Tile bases taken from the boards' thumbnails, plus a few iOS system colours.
internal val TilePalette = listOf(
    0xFFFF5A1F, 0xFF0A66C2, 0xFF7A3CFF, 0xFF2A60D8, 0xFF2F7A5A, 0xFFFF2D55,
    0xFF5E5CE6, 0xFFFF9500, 0xFF34C759, 0xFF00A3A3, 0xFF6E7681, 0xFFAF52DE,
).map { Color(it) }

/** Same label, same tile: String.hashCode is fixed by the JVM spec. */
/** `Chrome · Failed: Desktop rejected the file`, as the phone list shows the reason; ellipsized by the row. */
internal fun sidebarFailedLead(row: SaveRow): String =
    "${row.source} · Failed" + (row.failedReason?.let { ": $it" } ?: "")

internal fun tileColorIndex(source: String): Int = Math.floorMod(source.trim().lowercase().hashCode(), TilePalette.size)

internal fun tileLetter(source: String): String =
    source.firstOrNull { it.isLetterOrDigit() }?.uppercaseChar()?.toString() ?: "•"

@Composable
private fun PlaceholderTile(source: String, size: Dp) {
    val base = TilePalette[tileColorIndex(source)]
    Box(
        Modifier
            .size(size)
            .drawBehind {
                drawRect(base)
                // A soft light from the top left, like the boards' radial thumbnails.
                val c = Offset(this.size.width * 0.3f, this.size.height * 0.25f)
                val r = hypot(max(c.x, this.size.width - c.x), max(c.y, this.size.height - c.y)) * 0.75f
                drawRect(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.32f), Color.Transparent), c, r))
            },
        contentAlignment = Alignment.Center,
    ) {
        BasicText(
            tileLetter(source),
            style = YearnType.TileLetter.copy(color = Color.White, fontSize = YearnType.TileLetter.fontSize * (size / ThumbSize)),
        )
    }
}

/**
 * The inner screen: the list in a floating glass sidebar (284 dp, 32 dp corners, 12 dp in from
 * the edges) over the detail pane, whose content blurs through it.
 */
@Composable
fun SavesTwoPane(
    rows: List<SaveRow>,
    selectedId: String?,
    onSelect: (String) -> Unit,
    onSync: () -> Unit,
    onRetry: (String) -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
    detail: @Composable (id: String, paneStart: Dp) -> Unit = { _, _ -> },
) {
    val colors = LocalYearnColors.current
    val haze = rememberHazeState()
    val insets = safeInsets.asPaddingValues()
    val start = insets.calculateStartPadding(LocalLayoutDirection.current)

    Box(modifier.fillMaxSize().background(colors.background)) {
        // The pane spans the whole width (its media runs under the sidebar, which blurs it).
        Box(Modifier.fillMaxSize().hazeSource(haze).background(colors.background)) {
            if (selectedId != null) detail(selectedId, start + SidebarInset + SidebarWidth + DetailGap)
        }
        Sidebar(
            rows, selectedId, onSelect, onSync, onRetry, onOpenSettings, haze,
            Modifier
                .windowInsetsPadding(safeInsets)
                .padding(SidebarInset)
                .width(SidebarWidth)
                .fillMaxHeight(),
        )
    }
}

@Composable
private fun Sidebar(
    rows: List<SaveRow>,
    selectedId: String?,
    onSelect: (String) -> Unit,
    onSync: () -> Unit,
    onRetry: (String) -> Unit,
    onOpenSettings: () -> Unit,
    haze: HazeState,
    modifier: Modifier,
) {
    val colors = LocalYearnColors.current
    Column(
        modifier
            .glass(SidebarRadius, colors.sidebarGlass, haze, colors.background)
            .padding(horizontal = 10.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(start = 10.dp, top = 4.dp, end = 4.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BasicText("Saves", Modifier.weight(1f), style = YearnType.SidebarTitle.copy(color = colors.label))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GlassCircleButton(onSync, "Sync now", size = 40.dp, style = colors.glassButtonOnGlass) {
                    GlassIcon(GlassIcons.Sync, colors.label, 18.dp)
                }
                GlassCircleButton(onOpenSettings, "Settings", size = 40.dp, style = colors.glassButtonOnGlass) {
                    GlassIcon(GlassIcons.Gear, colors.label, 19.dp)
                }
            }
        }
        if (rows.isEmpty()) {
            EmptyState(Modifier.padding(horizontal = 10.dp, vertical = 8.dp))
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            items(rows, key = { it.id }) { row ->
                SidebarRow(row, row.id == selectedId, onClick = { onSelect(row.id) }, onRetry = { onRetry(row.id) })
            }
        }
    }
}

@Composable
private fun SidebarRow(row: SaveRow, selected: Boolean, onClick: () -> Unit, onRetry: () -> Unit) {
    val colors = LocalYearnColors.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Row(
        Modifier
            .fillMaxWidth()
            // A shaped background, not a clip: Retry's 48 dp touch area may overhang the row's edge.
            .background(if (selected || pressed) colors.sidebarSelected else Color.Transparent, RoundedCornerShape(18.dp))
            .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SaveThumb(row.thumbPath, row.source, SidebarThumb, SidebarThumbRadius)
        Column(Modifier.weight(1f)) {
            BasicText(
                row.title,
                style = YearnType.SidebarRowTitle.copy(color = colors.label),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (row.status == "Failed") {
                RetryLine(
                    sidebarFailedLead(row), "retry", colors.sidebarDanger, YearnType.SidebarRowSubtitle, onRetry,
                    separator = ", ",
                )
            } else {
                BasicText(
                    "${row.source} · ${row.status}",
                    style = YearnType.SidebarRowSubtitle.copy(color = colors.sidebarSecondary),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
