package com.anujp.yearn.capture

import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Process
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.anujp.yearn.YearnApp.Companion.yearnGraph
import kotlinx.coroutines.launch
import java.util.function.Consumer

/**
 * Share target. Transparent over the sending app: saves the share first, shows the glass sheet
 * while files copy, then gets out of the way. It never finishes while files are still copying,
 * because finishing ends the sender's read grant on the shared URIs.
 */
class ShareActivity : ComponentActivity() {
    internal val viewModel: CaptureViewModel by viewModels {
        viewModelFactory { initializer { CaptureViewModel(yearnGraph.service) } }
    }

    private val blurEnabled = mutableStateOf(false)
    private var blurListener: Consumer<Boolean>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            overrideActivityTransition(OVERRIDE_TRANSITION_CLOSE, 0, 0)
        }
        // Re-delivered from Recents: the original share was already caught.
        if (intent.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY != 0) {
            finishQuietly()
            return
        }
        enableEdgeToEdge(navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT))
        setUpBlur()

        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() = viewModel.onBack()
            },
        )

        viewModel.start(intent, ::sourceHints)
        lifecycleScope.launch {
            viewModel.state.collect { if (it is SheetState.Finished) finishQuietly() }
        }

        setContent {
            val state by viewModel.state.collectAsStateWithLifecycle()
            CaptureScreen(
                state = state,
                blurEnabled = blurEnabled.value,
                onNoteDot = viewModel::onNoteDot,
                onDraftChange = viewModel::onDraftChange,
                onNoteDone = viewModel::onNoteDone,
                onDragDismiss = viewModel::onDragDismiss,
            )
        }
    }

    override fun onDestroy() {
        blurListener?.let { l -> runCatching { windowManager.removeCrossWindowBlurEnabledListener(l) } }
        blurListener = null
        super.onDestroy()
    }

    /** Cross-window blur behind the sheet; the scrim takes over whenever the system turns blur off. */
    private fun setUpBlur() {
        window.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
        window.attributes = window.attributes.also { it.blurBehindRadius = BLUR_BEHIND_RADIUS }
        blurEnabled.value = runCatching { windowManager.isCrossWindowBlurEnabled }.getOrDefault(false)
        // Battery saver and similar toggle this live; the listener also fires once on registration.
        val listener = Consumer<Boolean> { enabled -> blurEnabled.value = enabled }
        if (runCatching { windowManager.addCrossWindowBlurEnabledListener(mainExecutor, listener) }.isSuccess) {
            blurListener = listener
        }
    }

    private fun sourceHints(): SourceHints {
        val modern = Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE
        return SourceHints(
            referrer = runCatching { referrer?.toString() }.getOrNull(),
            extraReferrer = runCatching {
                intent.getParcelableExtra(Intent.EXTRA_REFERRER, Uri::class.java)?.toString()
            }.getOrNull(),
            extraReferrerName = runCatching { intent.getStringExtra(Intent.EXTRA_REFERRER_NAME) }.getOrNull(),
            launchedFromPackage = if (modern) runCatching { launchedFromPackage }.getOrNull() else null,
            launchedFromUid = if (modern) {
                runCatching { launchedFromUid }.getOrNull()?.takeIf { it != Process.INVALID_UID }
            } else {
                null
            },
            callingPackage = runCatching { callingPackage }.getOrNull(),
        )
    }

    private fun finishQuietly() {
        if (isFinishing) return
        finish()
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            @Suppress("DEPRECATION")
            overridePendingTransition(0, 0)
        }
    }

    private companion object {
        const val BLUR_BEHIND_RADIUS = 40
    }
}
