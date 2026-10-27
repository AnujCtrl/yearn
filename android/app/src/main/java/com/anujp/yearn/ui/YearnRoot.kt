package com.anujp.yearn.ui

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.anujp.yearn.ui.glass.YearnTheme
import com.anujp.yearn.ui.home.HomeViewModel
import com.anujp.yearn.ui.home.SaveRow
import com.anujp.yearn.ui.home.SavesList
import com.anujp.yearn.ui.home.SavesTwoPane

/** At this width (the Fold's inner screen) the list moves into a floating glass sidebar. */
val TwoPaneMinWidth = 600.dp

@Composable
fun YearnRoot(home: HomeViewModel, onOpenSettings: () -> Unit = {}) {
    val rows by home.rows.collectAsStateWithLifecycle()
    val waiting by home.waitingCount.collectAsStateWithLifecycle()
    YearnRoot(rows, waiting, onSync = home::syncNow, onRetry = home::retry, onOpenSettings = onOpenSettings)
}

@Composable
fun YearnRoot(
    rows: List<SaveRow>,
    waitingCount: Int,
    onSync: () -> Unit,
    onRetry: (String) -> Unit,
    onOpenSettings: () -> Unit,
) {
    YearnTheme {
        var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
        BoxWithConstraints(Modifier.fillMaxSize()) {
            if (maxWidth >= TwoPaneMinWidth) {
                // The pane is never empty while there are saves: default to the newest.
                val shown = selectedId?.takeIf { id -> rows.any { it.id == id } } ?: rows.firstOrNull()?.id
                SavesTwoPane(
                    rows,
                    selectedId = shown,
                    onSelect = { selectedId = it },
                    onSync = onSync,
                    onRetry = onRetry,
                    onOpenSettings = onOpenSettings,
                )
            } else {
                // Task 10 navigates to the detail screen here; for now a tap only selects.
                SavesList(
                    rows,
                    waitingCount,
                    onSync = onSync,
                    onRetry = onRetry,
                    onOpenSettings = onOpenSettings,
                    onOpen = { selectedId = it },
                )
            }
        }
    }
}
