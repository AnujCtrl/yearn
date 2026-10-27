package com.anujp.yearn.ui.settings

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.anujp.yearn.ui.glass.GlassCircleButton
import com.anujp.yearn.ui.glass.GlassIcon
import com.anujp.yearn.ui.glass.GlassIcons
import com.anujp.yearn.ui.glass.LargeTitle
import com.anujp.yearn.ui.glass.LocalYearnColors
import com.anujp.yearn.ui.glass.YearnType
import com.anujp.yearn.ui.glass.bottomHairline
import com.anujp.yearn.ui.home.safeInsets
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import java.time.ZonedDateTime

// Values from boards/App-Settings.dc.html.

/** What the screen shows, already worded. */
data class SettingsUi(
    val address: String?,
    val addressError: String?,
    val hasToken: Boolean?,
    val tokenMask: String?,
    val tokenError: String?,
    val lastSync: String,
    val connection: String,
    val connectionFailed: Boolean,
    val batteryUnrestricted: Boolean,
)

@Composable
fun SettingsScreen(vm: SettingsViewModel, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val address by vm.address.collectAsStateWithLifecycle()
    val addressError by vm.addressError.collectAsStateWithLifecycle()
    val hasToken by vm.hasToken.collectAsStateWithLifecycle()
    val mask by vm.tokenMask.collectAsStateWithLifecycle()
    val tokenError by vm.tokenError.collectAsStateWithLifecycle()
    val lastSync by vm.lastSuccessfulSync.collectAsStateWithLifecycle()
    val connection by vm.connection.collectAsStateWithLifecycle()
    val checkedAt by vm.checkedAt.collectAsStateWithLifecycle()
    val testing by vm.testing.collectAsStateWithLifecycle()
    val battery by vm.batteryUnrestricted.collectAsStateWithLifecycle()
    // Back from the system's battery dialog: read the answer again.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.refreshBattery() }

    val now = ZonedDateTime.now()
    val ui = SettingsUi(
        address = address,
        addressError = addressError,
        hasToken = hasToken,
        tokenMask = mask,
        tokenError = tokenError,
        lastSync = lastSyncLabel(lastSync, now),
        connection = connectionLabel(connection, checkedAt, testing, now),
        connectionFailed = !testing && connection.isNotEmpty() && connection != SettingsViewModel.REACHABLE,
        batteryUnrestricted = battery,
    )
    SettingsScreen(
        ui,
        onBack = onBack,
        onAddress = { vm.setAddress(it) },
        onToken = { vm.setToken(it) },
        onTest = { vm.testConnection() },
        onBattery = { openBatterySettings(context, battery) },
        modifier = modifier,
    )
}

@Composable
fun SettingsScreen(
    ui: SettingsUi,
    onBack: () -> Unit,
    onAddress: (String) -> Unit,
    onToken: (String) -> Unit,
    onTest: () -> Unit,
    onBattery: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalYearnColors.current
    val haze = rememberHazeState()
    val insets = safeInsets.asPaddingValues()
    val top = insets.calculateTopPadding()

    Box(modifier.fillMaxSize().background(colors.background)) {
        Column(
            Modifier
                .fillMaxSize()
                .hazeSource(haze)
                .background(colors.background)
                .windowInsetsPadding(WindowInsets.ime)
                .verticalScroll(rememberScrollState())
                .windowInsetsPadding(safeInsets)
                .padding(start = 16.dp, end = 16.dp, top = 4.dp + 44.dp + 14.dp, bottom = 24.dp),
        ) {
            LargeTitle("Settings", Modifier.padding(start = 4.dp, bottom = 6.dp))

            Section(
                "Your desktop",
                "Reached over Tailscale only. The pairing token stays in the phone’s secure storage.",
            ) {
                AddressRow(ui.address, ui.addressError, onAddress)
                PairingRow(ui.hasToken, ui.tokenMask, ui.tokenError, onToken)
                SettingRow(Tile.Glyph(Color(0xFF34C759), GlassIcons.Check), "Last successful sync", ui.lastSync)
                SettingRow(
                    Tile.Glyph(Color(0xFF5E5CE6), GlassIcons.Swap),
                    "Test connection",
                    ui.connection,
                    titleColor = colors.active,
                    subColor = if (ui.connectionFailed) colors.danger else colors.secondaryLabel,
                    last = true,
                    onClick = onTest,
                )
            }

            Section(
                "In the background",
                "Samsung puts unused apps to sleep. Keeping yearn awake lets the daily sync run.",
            ) {
                SettingRow(
                    Tile.Glyph(Color(0xFFFF9500), GlassIcons.Moon),
                    "Keep yearn awake",
                    if (ui.batteryUnrestricted) "Battery: Unrestricted" else "Battery: Optimized. Turn this on so syncing isn’t paused",
                    highlight = !ui.batteryUnrestricted,
                    trailing = { Switch(ui.batteryUnrestricted) },
                    onClick = onBattery,
                )
                SettingRow(
                    Tile.Glyph(Color(0xFFFF2D55), GlassIcons.Sync),
                    "Daily sync",
                    "Once a day, and whenever a connection appears",
                    last = true,
                )
            }
        }

        GlassCircleButton(
            onBack,
            "Back",
            Modifier.padding(start = 16.dp, top = top + 4.dp),
            hazeState = haze,
        ) {
            GlassIcon(GlassIcons.Back, colors.label, 20.dp)
        }
    }
}

@Composable
private fun Section(name: String, footer: String, rows: @Composable ColumnScope.() -> Unit) {
    val colors = LocalYearnColors.current
    Column(Modifier.padding(top = 22.dp)) {
        BasicText(
            name.uppercase(),
            Modifier.padding(start = 20.dp, end = 20.dp, bottom = 7.dp),
            style = YearnType.SectionHeader.copy(color = colors.secondaryLabel),
        )
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(26.dp)).background(colors.groupFill),
            content = rows,
        )
        BasicText(
            footer,
            Modifier.padding(start = 20.dp, end = 20.dp, top = 7.dp),
            style = YearnType.Caption.copy(color = colors.secondaryLabel),
        )
    }
}

private sealed interface Tile {
    val color: Color

    data class Letter(override val color: Color, val text: String) : Tile
    data class Glyph(override val color: Color, val icon: ImageVector) : Tile
}

@Composable
private fun TileBox(tile: Tile) {
    Box(
        Modifier.size(30.dp).clip(RoundedCornerShape(8.dp)).background(tile.color),
        contentAlignment = Alignment.Center,
    ) {
        when (tile) {
            is Tile.Letter -> BasicText(
                tile.text,
                style = YearnType.Caption.copy(color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp),
            )
            is Tile.Glyph -> GlassIcon(tile.icon, Color.White, 17.dp)
        }
    }
}

/** One iOS settings row: a coloured tile, a title over a subtitle, an optional trailing control. */
@Composable
private fun SettingRow(
    tile: Tile,
    title: String,
    sub: String?,
    titleColor: Color = LocalYearnColors.current.label,
    subColor: Color = LocalYearnColors.current.secondaryLabel,
    last: Boolean = false,
    highlight: Boolean = false,
    trailing: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    below: (@Composable () -> Unit)? = null,
) {
    val colors = LocalYearnColors.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val fill = when {
        pressed && onClick != null -> colors.rowPressed
        highlight -> Color(0xFFFF9500).copy(alpha = if (colors.isDark) 0.2f else 0.12f)
        else -> Color.Transparent
    }
    Row(
        Modifier
            .fillMaxWidth()
            .background(fill)
            .then(
                if (onClick != null) {
                    Modifier.clickable(interaction, indication = null, role = Role.Button, onClickLabel = title, onClick = onClick)
                } else {
                    Modifier
                },
            )
            .padding(start = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        TileBox(tile)
        Row(
            Modifier
                .weight(1f)
                .heightIn(min = 52.dp)
                .then(if (last) Modifier else Modifier.bottomHairline(colors.separator, 0.dp))
                .padding(top = 8.dp, bottom = 8.dp, end = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Column(Modifier.weight(1f)) {
                BasicText(title, style = YearnType.Body.copy(color = titleColor, lineHeight = 21.sp))
                if (!sub.isNullOrEmpty()) BasicText(sub, style = YearnType.Caption.copy(color = subColor))
                below?.invoke()
            }
            trailing?.invoke()
        }
    }
}

/** Address: the subtitle is the field. Saved on Done or when focus leaves with a change. */
@Composable
private fun AddressRow(address: String?, error: String?, onSave: (String) -> Unit) {
    val colors = LocalYearnColors.current
    val focus = LocalFocusManager.current
    val requester = remember { FocusRequester() }
    var text by rememberSaveable(address) { mutableStateOf(address.orEmpty()) }
    var focused by remember { mutableStateOf(false) }
    SettingRow(
        Tile.Letter(Color(0xFF0A84FF), "@"),
        "Address",
        sub = null,
        onClick = { requester.requestFocus() },
        below = {
            InlineField(
                text,
                { text = it },
                placeholder = "desktop.tailnet.ts.net:8787",
                style = YearnType.Caption.copy(color = if (focused) colors.label else colors.secondaryLabel),
                keyboard = KeyboardOptions(keyboardType = KeyboardType.Uri, autoCorrectEnabled = false, imeAction = ImeAction.Done),
                onDone = {
                    onSave(text)
                    focus.clearFocus()
                },
                modifier = Modifier
                    .focusRequester(requester)
                    .onFocusChanged {
                        if (focused && !it.isFocused && text != address.orEmpty()) onSave(text)
                        focused = it.isFocused
                    }
                    .semantics { contentDescription = "Desktop address" },
            )
            if (error != null) BasicText(error, style = YearnType.Caption.copy(color = colors.danger))
        },
    )
}

/** Pairing: shows `Paired · ••••wxyz`; tapping opens a field for a new token, masked but for its last four. */
@Composable
private fun PairingRow(hasToken: Boolean?, mask: String?, error: String?, onSave: (String) -> Unit) {
    val colors = LocalYearnColors.current
    val focus = LocalFocusManager.current
    val requester = remember { FocusRequester() }
    var editing by rememberSaveable { mutableStateOf(false) }
    var draft by remember { mutableStateOf("") }
    LaunchedEffect(editing) { if (editing) requester.requestFocus() }
    val sub = when {
        hasToken == null -> ""
        mask != null -> "Paired · $mask"
        else -> "Not paired yet. Run yearn-receiver pair on your desktop and type the token here"
    }
    SettingRow(
        Tile.Letter(Color(0xFF8E8E93), "K"),
        "Pairing",
        sub = if (editing) null else sub,
        subColor = if (hasToken == false) colors.danger else colors.secondaryLabel,
        onClick = { editing = true },
        below = {
            if (editing) {
                InlineField(
                    draft,
                    { draft = it },
                    placeholder = "Pairing token",
                    style = YearnType.Caption.copy(color = colors.label),
                    keyboard = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false, imeAction = ImeAction.Done),
                    visual = MaskAllButLast4,
                    onDone = {
                        onSave(draft)
                        draft = ""
                        editing = false
                        focus.clearFocus()
                    },
                    modifier = Modifier
                        .focusRequester(requester)
                        .onFocusChanged { if (!it.isFocused && editing && draft.isEmpty()) editing = false }
                        .semantics { contentDescription = "Pairing token" },
                )
            }
            if (error != null) BasicText(error, style = YearnType.Caption.copy(color = colors.danger))
        },
    )
}

@Composable
private fun InlineField(
    value: String,
    onChange: (String) -> Unit,
    placeholder: String,
    style: TextStyle,
    keyboard: KeyboardOptions,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    visual: VisualTransformation = VisualTransformation.None,
) {
    val colors = LocalYearnColors.current
    BasicTextField(
        value = value,
        onValueChange = onChange,
        modifier = modifier.fillMaxWidth(),
        textStyle = style,
        singleLine = true,
        cursorBrush = SolidColor(colors.accent),
        keyboardOptions = keyboard,
        keyboardActions = KeyboardActions(onDone = { onDone() }),
        visualTransformation = visual,
        decorationBox = { inner ->
            Box {
                if (value.isEmpty()) BasicText(placeholder, style = style.copy(color = colors.secondaryLabel.copy(alpha = 0.6f)))
                inner()
            }
        },
    )
}

/** Shows `•` for every character but the last four, so the tail can be checked against the desktop. */
private object MaskAllButLast4 : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val n = text.length
        val shown = "•".repeat((n - 4).coerceAtLeast(0)) + text.text.takeLast(minOf(4, n))
        return TransformedText(AnnotatedString(shown), OffsetMapping.Identity)
    }
}

/** The iOS switch, drawn only: the whole row is the control. */
@Composable
private fun Switch(on: Boolean) {
    val colors = LocalYearnColors.current
    val track by animateColorAsState(
        if (on) Color(0xFF34C759) else if (colors.isDark) Color(0x52787880) else Color(0x29787880),
        label = "track",
    )
    val knob by animateDpAsState(if (on) 20.dp else 0.dp, label = "knob")
    Box(
        Modifier
            .size(51.dp, 31.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(track)
            .padding(2.dp),
    ) {
        Box(
            Modifier
                .offset(x = knob)
                .size(27.dp)
                .shadow(3.dp, CircleShape)
                .background(Color.White, CircleShape),
        )
    }
}

/** Asks for the exemption when yearn is optimised; otherwise opens the list where it can be taken back. */
private fun openBatterySettings(context: Context, unrestricted: Boolean) {
    val intent = if (unrestricted) {
        Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
    } else {
        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${context.packageName}"))
    }
    try {
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        runCatching { context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) }
    }
}
