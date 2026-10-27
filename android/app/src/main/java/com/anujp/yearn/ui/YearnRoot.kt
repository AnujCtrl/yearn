package com.anujp.yearn.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.anujp.yearn.ui.detail.DetailScreen
import com.anujp.yearn.ui.detail.DetailViewModel
import com.anujp.yearn.ui.glass.YearnTheme
import com.anujp.yearn.ui.home.HomeViewModel
import com.anujp.yearn.ui.home.SaveRow
import com.anujp.yearn.ui.home.SavesList
import com.anujp.yearn.ui.home.SavesTwoPane
import com.anujp.yearn.ui.settings.SettingsScreen
import com.anujp.yearn.ui.settings.SettingsViewModel

/** At this width (the Fold's inner screen) the list moves into a floating glass sidebar. */
val TwoPaneMinWidth = 600.dp

@Composable
fun YearnRoot(
    home: HomeViewModel,
    settings: SettingsViewModel,
    detailFor: @Composable (id: String) -> DetailViewModel,
) {
    val rows by home.rows.collectAsStateWithLifecycle()
    val waiting by home.waitingCount.collectAsStateWithLifecycle()
    val header by home.header.collectAsStateWithLifecycle()
    val hasToken by settings.hasToken.collectAsStateWithLifecycle()
    val hasAddress by settings.hasAddress.collectAsStateWithLifecycle()
    YearnRoot(
        rows,
        waiting,
        header,
        needsSetup = hasToken == false || hasAddress == false,
        onSync = home::syncNow,
        onRetry = home::retry,
        detail = { id, pane, paneStart, onBack ->
            DetailScreen(detailFor(id), pane, onBack, onRetry = { home.retry(id) }, paneStart = paneStart)
        },
        settings = { onBack -> SettingsScreen(settings, onBack) },
    )
}

/**
 * Navigation is plain state: list → detail → settings, with Back walking it in reverse. On the
 * inner screen the detail lives in the pane beside the sidebar; Settings always covers the whole
 * window (it is about the app, not about a save). The first launch without a pairing token opens
 * Settings once.
 */
@Composable
fun YearnRoot(
    rows: List<SaveRow>,
    waitingCount: Int,
    header: String,
    needsSetup: Boolean,
    onSync: () -> Unit,
    onRetry: (String) -> Unit,
    detail: @Composable (id: String, pane: Boolean, paneStart: Dp, onBack: () -> Unit) -> Unit,
    settings: @Composable (onBack: () -> Unit) -> Unit,
) {
    YearnTheme {
        var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
        var detailOpen by rememberSaveable { mutableStateOf(false) }
        var settingsOpen by rememberSaveable { mutableStateOf(false) }
        var setupShown by rememberSaveable { mutableStateOf(false) }
        LaunchedEffect(needsSetup) {
            if (needsSetup && !setupShown) {
                setupShown = true
                settingsOpen = true
            }
        }

        BoxWithConstraints(Modifier.fillMaxSize()) {
            val twoPane = maxWidth >= TwoPaneMinWidth
            BackHandler(enabled = !settingsOpen && !twoPane && detailOpen) { detailOpen = false }
            BackHandler(enabled = settingsOpen) { settingsOpen = false }
            // What an overlay covers stays composed (it keeps its scroll) but leaves the semantics tree,
            // so TalkBack can't walk into it. Touches are stopped by the overlays themselves.
            val detailCovers = !twoPane && detailOpen && selectedId != null
            fun covered(by: Boolean) = if (by) Modifier.clearAndSetSemantics {} else Modifier

            if (twoPane) {
                // The pane is never empty while there are saves: default to the newest.
                val shown = selectedId?.takeIf { id -> rows.any { it.id == id } } ?: rows.firstOrNull()?.id
                SavesTwoPane(
                    rows,
                    selectedId = shown,
                    onSelect = { selectedId = it },
                    onSync = onSync,
                    onRetry = onRetry,
                    onOpenSettings = { settingsOpen = true },
                    modifier = covered(settingsOpen),
                    detail = { id, paneStart -> detail(id, true, paneStart) {} },
                )
            } else {
                SavesList(
                    rows,
                    waitingCount,
                    header,
                    onSync = onSync,
                    onRetry = onRetry,
                    onOpenSettings = { settingsOpen = true },
                    onOpen = {
                        selectedId = it
                        detailOpen = true
                    },
                    modifier = covered(settingsOpen || detailCovers),
                )
                val id = selectedId
                AnimatedVisibility(
                    visible = detailOpen && id != null,
                    enter = slideInHorizontally(PushSpring) { it },
                    exit = slideOutHorizontally(tween(260)) { it } + fadeOut(tween(260)),
                ) {
                    if (id != null) detail(id, false, 0.dp) { detailOpen = false }
                }
            }

            AnimatedVisibility(
                visible = settingsOpen,
                enter = slideInHorizontally(PushSpring) { it },
                exit = slideOutHorizontally(tween(260)) { it } + fadeOut(tween(260)),
            ) {
                settings { settingsOpen = false }
            }
        }
    }
}

private val PushSpring = spring<IntOffset>(dampingRatio = 1f, stiffness = Spring.StiffnessMediumLow)
