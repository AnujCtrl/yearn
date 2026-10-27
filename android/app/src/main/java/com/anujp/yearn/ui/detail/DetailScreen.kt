package com.anujp.yearn.ui.detail

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.anujp.yearn.data.CaptureEntity
import com.anujp.yearn.data.FileEntity
import com.anujp.yearn.data.FileState
import com.anujp.yearn.data.SyncStatus
import com.anujp.yearn.ui.glass.GlassCircleButton
import com.anujp.yearn.ui.glass.GlassIcon
import com.anujp.yearn.ui.glass.GlassIcons
import com.anujp.yearn.ui.glass.GlassStyle
import com.anujp.yearn.ui.glass.LocalYearnColors
import com.anujp.yearn.ui.glass.YearnColors
import com.anujp.yearn.ui.glass.YearnType
import com.anujp.yearn.ui.glass.blocksTouchesBelow
import com.anujp.yearn.ui.glass.bottomHairline
import com.anujp.yearn.ui.glass.cssBlur
import com.anujp.yearn.ui.glass.glass
import com.anujp.yearn.ui.glass.pressable
import com.anujp.yearn.ui.home.TilePalette
import com.anujp.yearn.ui.home.decodeThumb
import com.anujp.yearn.ui.home.safeInsets
import com.anujp.yearn.ui.home.statusWord
import com.anujp.yearn.ui.home.tileColorIndex
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import kotlinx.coroutines.delay
import java.io.File
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

// Values from boards/App-Detail.dc.html (phone: a glass sheet over the media) and
// boards/App-Inner.dc.html (inner screen: the same content in the pane beside the sidebar).

const val FileAuthority = "com.anujp.yearn.files"

private val SheetRadius = 38.dp
private val SheetInset = 8.dp
private val FieldRadius = 18.dp
private val GroupRadius = 22.dp
private val PaneMediaFade = 100.dp

/** Glass over the media, the same in both themes: the circles are white glyphs on a 16 % frost. */
private val MediaButton = GlassStyle(
    fill = Color.White.copy(alpha = 0.16f),
    border = Color.White.copy(alpha = 0.45f),
    highlight = Color.White.copy(alpha = 0.6f),
    shadow = Color.Black.copy(alpha = 0.18f),
    shadowOffsetY = 6.dp,
    shadowBlur = cssBlur(18f),
    blurRadius = 12.dp,
)

/** The sheet's own colours: the board's light values, and a dark sheet extrapolated from them. */
private data class SheetColors(
    val glass: GlassStyle,
    val grabber: Color,
    val field: Color,
    val fieldBorder: Color,
    val group: Color,
    val separator: Color,
    val body: Color,
)

private fun sheetColors(c: YearnColors, pane: Boolean): SheetColors = when {
    pane -> SheetColors(
        glass = MediaButton,
        grabber = Color.Transparent,
        field = c.groupFill,
        fieldBorder = if (c.isDark) Color.White.copy(alpha = 0.14f) else Color(0x2E3C3C43),
        group = c.groupFill,
        separator = c.separator,
        body = if (c.isDark) Color(0xFFE5E5EA) else c.label,
    )
    c.isDark -> SheetColors(
        glass = GlassStyle(
            fill = Color(0xB81C1C1E),
            border = Color.White.copy(alpha = 0.18f),
            highlight = Color.White.copy(alpha = 0.3f),
            shadow = Color.Black.copy(alpha = 0.35f),
            shadowOffsetY = (-6).dp,
            shadowBlur = cssBlur(30f),
            blurRadius = 30.dp,
        ),
        grabber = Color(0x4DEBEBF5),
        field = Color.White.copy(alpha = 0.08f),
        fieldBorder = Color.White.copy(alpha = 0.14f),
        group = Color.White.copy(alpha = 0.08f),
        separator = Color.White.copy(alpha = 0.12f),
        body = c.label,
    )
    else -> SheetColors(
        glass = GlassStyle(
            fill = Color.White.copy(alpha = 0.74f),
            border = Color.White.copy(alpha = 0.7f),
            highlight = Color.White.copy(alpha = 0.95f),
            shadow = Color.Black.copy(alpha = 0.18f),
            shadowOffsetY = (-6).dp,
            shadowBlur = cssBlur(30f),
            blurRadius = 30.dp,
        ),
        grabber = Color(0x4D3C3C43),
        field = Color.White.copy(alpha = 0.7f),
        fieldBorder = Color(0x2E3C3C43),
        group = Color.White.copy(alpha = 0.7f),
        separator = Color(0x333C3C43),
        body = c.label,
    )
}

@Composable
fun DetailScreen(
    vm: DetailViewModel,
    pane: Boolean,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    paneStart: Dp = 0.dp,
) {
    val capture by vm.capture.collectAsStateWithLifecycle()
    val files by vm.files.collectAsStateWithLifecycle()
    val raw by vm.rawJson.collectAsStateWithLifecycle()
    val fields by vm.rawFieldCount.collectAsStateWithLifecycle()
    DetailScreen(capture, files, raw, fields, pane, onBack, onRetry, { vm.saveNote(it) }, modifier, paneStart)
}

/**
 * One save. On the phone ([pane] false) the media fills the top and the text sits on a frosted
 * sheet; in the inner screen's pane the media runs along the top and the text is bottom-aligned
 * from [paneStart], beside the sidebar.
 */
@Composable
fun DetailScreen(
    capture: CaptureEntity?,
    files: List<FileEntity>,
    rawJson: String,
    rawFieldCount: Int,
    pane: Boolean,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onSaveNote: (String) -> Unit,
    modifier: Modifier = Modifier,
    paneStart: Dp = 0.dp,
    now: () -> ZonedDateTime = { ZonedDateTime.now() },
) {
    val colors = LocalYearnColors.current
    val context = LocalContext.current
    val c = capture
    if (c == null) {
        Box(modifier.fillMaxSize().blocksTouchesBelow().background(colors.background))
        return
    }

    // Keyed by save: switching saves in the pane disposes the old body with its own draft and save callback.
    key(c.id) {
        // The note draft survives folding and process death; it is saved on Done, on leaving and on stop.
        var draft by rememberSaveable(c.id) { mutableStateOf<String?>(null) }
        val latestDraft by rememberUpdatedState(draft)
        val save by rememberUpdatedState(onSaveNote)
        DisposableEffect(c.id) { onDispose { latestDraft?.let(save) } }
        LifecycleEventEffect(Lifecycle.Event.ON_STOP) { draft?.let(onSaveNote) }

        val openFile: (FileEntity) -> Unit = { f -> openLocalFile(context, f) }
        val firstFile = files.firstOrNull { it.state == FileState.DONE && it.localPath != null }
        val content: @Composable (SheetColors) -> Unit = { sheet ->
            DetailContent(
                c, files, rawJson, rawFieldCount, pane, sheet, now(),
                note = draft ?: c.note.orEmpty(),
                onNoteChange = { draft = it },
                onNoteDone = { onSaveNote(draft ?: c.note.orEmpty()) },
                onOpenFile = openFile,
                onRetry = onRetry,
            )
        }

        BoxWithConstraints(
            modifier.fillMaxSize().blocksTouchesBelow().background(if (pane) colors.background else Color.Black),
        ) {
            val insets = safeInsets.asPaddingValues()
            val top = insets.calculateTopPadding()
            if (pane) {
                val fullHeight = maxHeight
                val mediaHeight = fullHeight * 0.48f
                Media(c, files, Modifier.fillMaxWidth().height(mediaHeight), onClick = firstFile?.let { { openFile(it) } })
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = mediaHeight - PaneMediaFade)
                        .height(PaneMediaFade)
                        .background(Brush.verticalGradient(listOf(colors.background.copy(alpha = 0f), colors.background))),
                )
                val scroll = rememberScrollState()
                Column(
                    Modifier
                        .fillMaxSize()
                        .windowInsetsPadding(WindowInsets.ime)
                        .verticalScroll(scroll)
                        .padding(start = paneStart, end = 24.dp),
                ) {
                    Column(
                        Modifier.heightIn(min = fullHeight).padding(top = top + 20.dp, bottom = insets.calculateBottomPadding() + 24.dp),
                    ) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            c.previewUrl?.let { url ->
                                GlassCircleButton({ openUrl(context, url) }, "Open link", style = MediaButton) {
                                    GlassIcon(GlassIcons.OpenOut, Color.White, 20.dp)
                                }
                            }
                        }
                        // Long content scrolls rather than climbing into the media: it starts on the faded part.
                        Spacer(Modifier.height(max(24.dp, mediaHeight - top - 20.dp - 44.dp - 8.dp)))
                        Spacer(Modifier.weight(1f))
                        content(sheetColors(colors, pane = true))
                    }
                }
            } else {
                val haze = rememberHazeState()
                val sheetTop = maxHeight * 0.4f
                Box(Modifier.fillMaxSize().hazeSource(haze)) {
                    // As on the board the media runs under the whole sheet, so the glass is tinted by it all the way down.
                    Media(c, files, Modifier.fillMaxSize(), onClick = firstFile?.let { { openFile(it) } }, focusHeight = sheetTop + SheetRadius)
                }
                Row(
                    Modifier.fillMaxWidth().padding(top = top + 4.dp, start = 16.dp, end = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    GlassCircleButton(onBack, "Back", style = MediaButton, hazeState = haze) {
                        GlassIcon(GlassIcons.Back, Color.White, 20.dp)
                    }
                    Spacer(Modifier.weight(1f))
                    c.previewUrl?.let { url ->
                        GlassCircleButton({ openUrl(context, url) }, "Open link", style = MediaButton, hazeState = haze) {
                            GlassIcon(GlassIcons.OpenOut, Color.White, 20.dp)
                        }
                    }
                }
                val sheet = sheetColors(colors, pane = false)
                val scroll = rememberScrollState()
                Column(
                    Modifier
                        .padding(start = SheetInset, end = SheetInset, bottom = SheetInset, top = sheetTop)
                        .fillMaxSize()
                        .glass(SheetRadius, sheet.glass, haze, Color.Black)
                        .windowInsetsPadding(WindowInsets.ime)
                        .verticalScroll(scroll)
                        .padding(start = 20.dp, end = 20.dp, top = 10.dp)
                        .windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets(bottom = 20.dp))),
                ) {
                    Box(
                        Modifier
                            .align(Alignment.CenterHorizontally)
                            .size(36.dp, 5.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(sheet.grabber),
                    )
                    Spacer(Modifier.height(14.dp))
                    content(sheet)
                }
            }
        }
    }
}

@Composable
private fun DetailContent(
    c: CaptureEntity,
    files: List<FileEntity>,
    rawJson: String,
    rawFieldCount: Int,
    pane: Boolean,
    sheet: SheetColors,
    now: ZonedDateTime,
    note: String,
    onNoteChange: (String) -> Unit,
    onNoteDone: () -> Unit,
    onOpenFile: (FileEntity) -> Unit,
    onRetry: () -> Unit,
) {
    val colors = LocalYearnColors.current
    val context = LocalContext.current
    val focus = LocalFocusManager.current
    val title = c.previewTitle ?: c.sourceLabel
    val captured = Instant.ofEpochMilli(c.capturedAtMillis).atZone(now.zone)
    val meta = listOfNotNull(c.sourceLabel.takeIf { it != title }, dayTime(captured, now)).joinToString(" · ")
    var rawOpen by rememberSaveable(c.id) { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            BasicText(meta, style = YearnType.Caption.copy(color = colors.secondaryLabel))
            BasicText(title, style = (if (pane) YearnType.DetailTitle else YearnType.SheetTitle).copy(color = colors.label))
        }
        c.previewText?.let {
            BasicText(it, style = YearnType.Body.copy(color = sheet.body, lineHeight = if (pane) 23.sp else 22.sp))
        }

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (!pane) FieldLabel("Note")
            NoteField(note, sheet, onNoteChange, onDone = {
                onNoteDone()
                focus.clearFocus()
            })
        }

        if (files.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                FieldLabel(if (files.size == 1) "File" else "${files.size} files")
                Group(sheet) {
                    files.forEachIndexed { i, f ->
                        FileRow(f, last = i == files.lastIndex, sheet, pane) { onOpenFile(f) }
                    }
                }
            }
        }

        Group(sheet) {
            c.previewUrl?.let { url ->
                ValueRow("Link", shortUrl(url), sheet, pane, chevron = true, onClick = { openUrl(context, url) })
            }
            DesktopRow(c, now, sheet, pane, onRetry)
            ValueRow(
                "Raw share", fieldsLabel(rawFieldCount), sheet, pane,
                chevron = true, chevronDown = rawOpen, last = true, onClick = { rawOpen = !rawOpen },
            )
        }
        if (rawOpen) {
            val requester = remember { BringIntoViewRequester() }
            LaunchedEffect(Unit) {
                withFrameNanos { } // laid out first, or there is nothing to bring into view
                requester.bringIntoView()
            }
            RawBlock(rawJson, sheet, Modifier.bringIntoViewRequester(requester))
        }
    }
}

@Composable
private fun FieldLabel(text: String) {
    val colors = LocalYearnColors.current
    BasicText(text, Modifier.padding(start = 4.dp), style = YearnType.Caption.copy(color = colors.secondaryLabel))
}

@Composable
private fun NoteField(value: String, sheet: SheetColors, onChange: (String) -> Unit, onDone: () -> Unit) {
    val colors = LocalYearnColors.current
    val shape = RoundedCornerShape(FieldRadius)
    BasicTextField(
        value = value,
        onValueChange = onChange,
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentDescription = "Note" },
        textStyle = YearnType.Body.copy(color = colors.label),
        cursorBrush = SolidColor(colors.accent),
        minLines = 2,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { onDone() }),
        decorationBox = { inner ->
            Box(
                Modifier
                    .clip(shape)
                    .background(sheet.field)
                    .border(0.5.dp, sheet.fieldBorder, shape)
                    .padding(horizontal = 14.dp, vertical = 12.dp),
            ) {
                if (value.isEmpty()) {
                    BasicText("Add a note", style = YearnType.Body.copy(color = colors.secondaryLabel.copy(alpha = 0.7f)))
                }
                inner()
            }
        },
    )
}

@Composable
private fun Group(sheet: SheetColors, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(GroupRadius)).background(sheet.group)) { content() }
}

/** A row's own press state: a soft darkening, no ripple. */
@Composable
private fun Modifier.rowClick(onClick: (() -> Unit)?, label: String): Modifier {
    if (onClick == null) return this
    val colors = LocalYearnColors.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    return this
        .background(if (pressed) colors.label.copy(alpha = 0.06f) else Color.Transparent)
        .clickable(interaction, indication = null, role = Role.Button, onClickLabel = label, onClick = onClick)
}

@Composable
private fun ValueRow(
    label: String,
    value: String,
    sheet: SheetColors,
    pane: Boolean,
    chevron: Boolean = false,
    chevronDown: Boolean = false,
    last: Boolean = false,
    valueColor: Color = LocalYearnColors.current.secondaryLabel,
    onClick: (() -> Unit)? = null,
) {
    val colors = LocalYearnColors.current
    val pad = if (pane) 16.dp else 14.dp
    Row(
        Modifier
            .fillMaxWidth()
            .rowClick(onClick, label)
            .padding(start = pad),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            Modifier
                .weight(1f)
                .heightIn(min = if (pane) 46.dp else 48.dp)
                .then(if (last) Modifier else Modifier.bottomHairline(sheet.separator, 0.dp))
                .padding(end = pad),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            BasicText(label, style = YearnType.Body.copy(color = colors.label))
            Row(
                Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BasicText(
                    value,
                    Modifier.weight(1f, fill = false),
                    style = YearnType.RowValue.copy(color = valueColor),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (chevron) {
                    GlassIcon(GlassIcons.Chevron, Color(0xFFAEAEB2), 14.dp, Modifier.rotate(if (chevronDown) 90f else 0f))
                }
            }
        }
    }
}

/** `On your desktop`: the status word; when it failed, the reason and a full-size Retry. */
@Composable
private fun DesktopRow(c: CaptureEntity, now: ZonedDateTime, sheet: SheetColors, pane: Boolean, onRetry: () -> Unit) {
    val colors = LocalYearnColors.current
    if (c.syncStatus != SyncStatus.FAILED) {
        val value = when (c.syncStatus) {
            SyncStatus.UPLOADED -> listOfNotNull("Uploaded", c.uploadedAt?.let { uploadedWhen(it, now) }).joinToString(" ")
            else -> statusWord(c.syncStatus)
        }
        ValueRow("On your desktop", value, sheet, pane, valueColor = if (c.syncStatus == SyncStatus.UPLOADING) colors.active else colors.secondaryLabel)
        return
    }
    val pad = if (pane) 16.dp else 14.dp
    Row(Modifier.fillMaxWidth().padding(start = pad), verticalAlignment = Alignment.CenterVertically) {
        Row(
            Modifier
                .weight(1f)
                .heightIn(min = 56.dp)
                .bottomHairline(sheet.separator, 0.dp)
                .padding(end = pad, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Column(Modifier.weight(1f)) {
                BasicText("On your desktop", style = YearnType.Body.copy(color = colors.label))
                BasicText(
                    listOfNotNull("Failed", c.lastError).joinToString(" · "),
                    style = YearnType.Caption.copy(color = colors.danger),
                )
            }
            BasicText(
                "Retry",
                Modifier
                    .pressable(onRetry, label = "Retry upload", pressedScale = 0.96f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(colors.accent.copy(alpha = 0.14f))
                    .padding(horizontal = 14.dp, vertical = 7.dp),
                style = YearnType.Button.copy(color = colors.accent),
            )
        }
    }
}

@Composable
private fun FileRow(f: FileEntity, last: Boolean, sheet: SheetColors, pane: Boolean, onOpen: () -> Unit) {
    val colors = LocalYearnColors.current
    val pad = if (pane) 16.dp else 14.dp
    val openable = f.state == FileState.DONE && f.localPath != null
    val name = f.displayName?.ifBlank { null } ?: "File ${f.index + 1}"
    val (sub, subColor) = when (f.state) {
        FileState.ERROR -> (f.error ?: "Copy failed") to colors.danger
        FileState.PENDING -> "Copying…" to colors.secondaryLabel
        FileState.DONE -> listOfNotNull(f.mimeType, f.size?.let(::sizeLabel)).joinToString(" · ") to colors.secondaryLabel
    }
    Row(
        Modifier.fillMaxWidth().rowClick(if (openable) onOpen else null, "Open $name").padding(start = pad),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        GlassIcon(GlassIcons.File, if (f.state == FileState.ERROR) colors.danger else colors.secondaryLabel, 20.dp)
        Row(
            Modifier
                .weight(1f)
                .heightIn(min = 52.dp)
                .then(if (last) Modifier else Modifier.bottomHairline(sheet.separator, 0.dp))
                .padding(end = pad, top = 7.dp, bottom = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Column(Modifier.weight(1f)) {
                BasicText(name, style = YearnType.Body.copy(color = colors.label), maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (sub.isNotEmpty()) BasicText(sub, style = YearnType.Caption.copy(color = subColor), maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            if (openable) GlassIcon(GlassIcons.Chevron, Color(0xFFAEAEB2), 14.dp)
        }
    }
}

/** The pretty record in a monospace block, with Copy. */
@Composable
private fun RawBlock(json: String, sheet: SheetColors, modifier: Modifier = Modifier) {
    val colors = LocalYearnColors.current
    val context = LocalContext.current
    var copied by remember { mutableStateOf(false) }
    LaunchedEffect(copied) {
        if (copied) {
            delay(1_500)
            copied = false
        }
    }
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(FieldRadius))
            .background(sheet.group)
            .padding(start = 14.dp, end = 10.dp, top = 10.dp, bottom = 14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BasicText("As your desktop receives it", Modifier.weight(1f), style = YearnType.Caption.copy(color = colors.secondaryLabel))
            BasicText(
                if (copied) "Copied" else "Copy",
                Modifier
                    .pressable({
                        copyText(context, json)
                        copied = true
                    }, label = "Copy raw share", pressedScale = 0.96f)
                    .clip(RoundedCornerShape(14.dp))
                    .background(colors.accent.copy(alpha = 0.14f))
                    .padding(horizontal = 12.dp, vertical = 5.dp),
                style = YearnType.Button.copy(color = colors.accent, fontSize = 14.sp),
            )
        }
        BasicText(
            json,
            style = YearnType.Caption.copy(
                color = colors.label,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Normal,
                fontSize = 12.sp,
                lineHeight = 17.sp,
                letterSpacing = 0.sp,
            ),
        )
    }
}

/** The save's picture: an image file, else the thumbnail (a video frame), else a glow in the source's colours. */
@Composable
private fun Media(
    c: CaptureEntity,
    files: List<FileEntity>,
    modifier: Modifier,
    onClick: (() -> Unit)?,
    focusHeight: Dp? = null,
) {
    val image = files.firstOrNull { it.state == FileState.DONE && it.localPath != null && it.mimeType?.startsWith("image/") == true }
    val path = image?.localPath ?: c.thumbPath
    val density = LocalDensity.current
    BoxWithConstraints(modifier) {
        val targetPx = with(density) { (maxWidth / 2).roundToPx() }
        val bitmap by produceState<ImageBitmap?>(null, path, targetPx) {
            value = path?.let { decodeThumb(it, targetPx) }
        }
        Glow(c.sourceLabel, Modifier.matchParentSize())
        bitmap?.let {
            val open = if (onClick != null) Modifier.clickable(onClickLabel = "Open the file", onClick = onClick) else Modifier
            if (focusHeight == null) {
                Image(it, "The saved picture", Modifier.matchParentSize().then(open), contentScale = ContentScale.Crop)
            } else {
                // The picture is framed for the visible top; a full-bleed copy behind it is what the sheet frosts.
                Image(it, null, Modifier.matchParentSize(), contentScale = ContentScale.Crop)
                Image(
                    it,
                    "The saved picture",
                    Modifier
                        .fillMaxWidth()
                        .height(focusHeight)
                        .fadeBottom(64.dp)
                        .then(open),
                    contentScale = ContentScale.Crop,
                )
            }
        }
    }
}

/** Fades the last [length] of the content to transparent, so the framed picture melts into the full-bleed one. */
private fun Modifier.fadeBottom(length: Dp): Modifier = this
    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        val l = length.toPx()
        drawRect(
            Brush.verticalGradient(listOf(Color.Black, Color.Transparent), startY = size.height - l, endY = size.height),
            blendMode = BlendMode.DstIn,
        )
    }

/** The board's radial-gradient backdrop, tinted from the source like the list's placeholder tile. */
@Composable
private fun Glow(source: String, modifier: Modifier) {
    val i = tileColorIndex(source)
    val a = TilePalette[i]
    val b = TilePalette[(i + 2) % TilePalette.size]
    val d = TilePalette[(i + 5) % TilePalette.size]
    Box(
        modifier.drawBehind {
            drawRect(Color(0xFF1A1030))
            val m = kotlin.math.max(size.width, size.height)
            fun glow(color: Color, x: Float, y: Float, r: Float) = drawRect(
                Brush.radialGradient(listOf(color, color.copy(alpha = 0f)), Offset(size.width * x, size.height * y), m * r),
            )
            glow(a, 0.30f, 0.22f, 0.42f)
            glow(b, 0.75f, 0.55f, 0.48f)
            glow(d, 0.20f, 0.70f, 0.38f)
        },
    )
}

private val ClockTime: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.ENGLISH)
private val MonthDay: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM d", Locale.ENGLISH)

/** `Today 12:41`, `Yesterday 08:30`, `Oct 24 19:02`. */
internal fun dayTime(then: ZonedDateTime, now: ZonedDateTime): String {
    val local = then.withZoneSameInstant(now.zone)
    val day = when (local.toLocalDate()) {
        now.toLocalDate() -> "Today"
        now.toLocalDate().minusDays(1) -> "Yesterday"
        else -> MonthDay.format(local)
    }
    return "$day ${ClockTime.format(local)}"
}

/** `12:43` today, else `Oct 24`. */
internal fun uploadedWhen(timestamp: String, now: ZonedDateTime): String? {
    val t = runCatching { OffsetDateTime.parse(timestamp).atZoneSameInstant(now.zone) }.getOrNull() ?: return null
    return if (t.toLocalDate() == now.toLocalDate()) ClockTime.format(t) else MonthDay.format(t)
}

/** `instagram.com/reel/abc`: no scheme, no `www.`, no trailing slash. */
internal fun shortUrl(url: String): String =
    url.substringAfter("://").removePrefix("www.").removeSuffix("/")

internal fun fieldsLabel(n: Int): String = when (n) {
    0 -> "No fields"
    1 -> "1 field"
    else -> "$n fields"
}

internal fun sizeLabel(bytes: Long): String = when {
    bytes < 1_000 -> "$bytes B"
    bytes < 1_000_000 -> "%.0f KB".format(Locale.ENGLISH, bytes / 1_000.0)
    bytes < 1_000_000_000 -> "%.1f MB".format(Locale.ENGLISH, bytes / 1_000_000.0)
    else -> "%.2f GB".format(Locale.ENGLISH, bytes / 1_000_000_000.0)
}

private fun Context.startSafely(intent: Intent) {
    try {
        startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        // Nothing on the phone opens it; the row simply does nothing.
    }
}

private fun openUrl(context: Context, url: String) =
    context.startSafely(Intent(Intent.ACTION_VIEW, Uri.parse(url)))

/** Hands the copied file to another app through the FileProvider, read-only. */
private fun openLocalFile(context: Context, f: FileEntity) {
    val path = f.localPath ?: return
    val uri = runCatching { FileProvider.getUriForFile(context, FileAuthority, File(path)) }.getOrNull() ?: return
    context.startSafely(
        Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, f.mimeType ?: "*/*")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
    )
}

private fun copyText(context: Context, text: String) {
    context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText("yearn raw share", text))
}
