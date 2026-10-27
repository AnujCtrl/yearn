package com.anujp.yearn.capture

import android.content.Intent
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference

sealed interface SheetState {
    /** Before the capture row is committed. */
    data object Catching : SheetState

    /** Row committed, files still copying. [stillSaving] is set once Back was pressed. */
    data class Saving(val progress: CaptureProgress, val stillSaving: Boolean = false) : SheetState

    data class Saved(val progress: CaptureProgress) : SheetState

    data class Note(val progress: CaptureProgress, val draft: String) : SheetState

    data class StorageError(val message: String) : SheetState

    data object Finished : SheetState
}

/**
 * Drives one share. The capture runs in [viewModelScope], so it survives a fold/unfold recreate,
 * and [start] only ever captures once per ViewModel. Nothing here finishes while [SheetState.Saving]:
 * finishing the activity ends the sender's read grant on its content URIs.
 *
 * [saved] carries the capture id, picked here and saved before [CaptureService.begin], across process
 * death. A restored activity whose capture row exists finishes instead of capturing the share again
 * (startup recovery has already marked its unfinished copies as errors); if the old process died before
 * the row committed, the share is begun again under the same id.
 *
 * If this ViewModel is cleared for good (not a fold/unfold recreate) before its copy finished, the files
 * still pending are marked interrupted and the capture is queued, so it still uploads.
 */
class CaptureViewModel(
    private val service: CaptureService,
    private val saved: SavedStateHandle = SavedStateHandle(),
    private val savedHoldMillis: Long = SAVED_HOLD_MILLIS,
    private val retryMillis: Long = RETRY_MILLIS,
    private val newId: () -> String = { UUID.randomUUID().toString() },
) : ViewModel() {
    private val _state = MutableStateFlow<SheetState>(SheetState.Catching)
    val state: StateFlow<SheetState> = _state.asStateFlow()

    private var started = false
    private var hold: Job? = null
    private var closing = false

    /** The capture this ViewModel is saving, until its files are all copied. */
    @Volatile private var copying: String? = null

    /** Captures [intent] once; later calls (a recreated activity) do nothing. */
    fun start(intent: Intent, hints: () -> SourceHints) {
        if (started) return
        started = true
        val restored = saved.get<String>(KEY_CAPTURE_ID)
        val sourceHints = hints()
        if (restored == null) {
            val id = newId()
            saved[KEY_CAPTURE_ID] = id
            capture(intent, sourceHints, id)
            return
        }
        // Process-death restore: the previous process either committed this share or died first.
        viewModelScope.launch {
            val committed = try {
                service.exists(restored)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                false // begin is idempotent per id: it returns the row if there is one after all
            }
            if (committed) _state.value = SheetState.Finished else capture(intent, sourceHints, restored)
        }
    }

    private fun capture(intent: Intent, hints: SourceHints, id: String) {
        copying = id
        viewModelScope.launch {
            val first = beginUntilCommitted(intent, hints, id)
            val done = if (first.complete) first else copyUntilDone(first)
            copying = null
            move(SheetState.Saved(done))
            hold = launch {
                delay(savedHoldMillis)
                if (_state.value is SheetState.Saved) _state.value = SheetState.Finished
            }
        }
    }

    /** Keeps the intent in memory and retries until the row is committed (e.g. disk full). */
    private suspend fun beginUntilCommitted(intent: Intent, hints: SourceHints, id: String): CaptureProgress {
        while (true) {
            try {
                return service.begin(intent, hints, id)
            } catch (e: CaptureFailed) {
                move(SheetState.StorageError(STORAGE_ERROR))
                delay(retryMillis)
            }
        }
    }

    private suspend fun copyUntilDone(first: CaptureProgress): CaptureProgress {
        val latest = AtomicReference(first)
        move(SheetState.Saving(first))
        while (true) {
            try {
                // onProgress arrives on IO threads: only touch the (thread-safe) StateFlow.
                service.copyFiles(first.captureId) { p ->
                    latest.set(p)
                    _state.update { s -> if (s is SheetState.Saving) s.copy(progress = p) else s }
                }
                val last = latest.get()
                return if (last.complete) last else last.copy(filesDone = last.filesTotal, complete = true)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                move(SheetState.StorageError(STORAGE_ERROR))
                delay(retryMillis)
                move(SheetState.Saving(latest.get()))
            }
        }
    }

    /** Background transitions never resurrect a sheet the user already left. */
    private fun move(to: SheetState) = _state.update { s -> if (s is SheetState.Finished) s else to }

    fun onNoteDot() {
        val s = _state.value as? SheetState.Saved ?: return
        hold?.cancel()
        _state.value = SheetState.Note(s.progress, "")
    }

    fun onDraftChange(text: String) {
        _state.update { s -> if (s is SheetState.Note) s.copy(draft = text) else s }
    }

    /** Done (or Back) in the note: save a non-blank draft, then finish. */
    fun onNoteDone() {
        val s = _state.value as? SheetState.Note ?: return
        if (closing) return
        closing = true
        viewModelScope.launch {
            if (s.draft.isNotBlank()) {
                try {
                    service.saveNote(s.progress.captureId, s.draft)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // The capture itself is safe; only the note is lost. Leave anyway.
                }
            }
            _state.value = SheetState.Finished
        }
    }

    /** Swipe down past the threshold: only while showing Saved. */
    fun onDragDismiss() {
        if (_state.value !is SheetState.Saved) return
        hold?.cancel()
        _state.value = SheetState.Finished
    }

    fun onBack() {
        when (val s = _state.value) {
            SheetState.Catching -> Unit // nothing committed yet: leaving now could drop the share
            is SheetState.Saving -> _state.value = s.copy(stillSaving = true)
            is SheetState.Note -> onNoteDone()
            is SheetState.Saved -> {
                hold?.cancel()
                _state.value = SheetState.Finished
            }
            // The only place a share is given up: the user chose to leave while storage is failing.
            is SheetState.StorageError -> _state.value = SheetState.Finished
            SheetState.Finished -> Unit
        }
    }

    /** Not called on a fold/unfold recreate: only when the sheet is gone for good. */
    override fun onCleared() {
        copying?.let { service.interruptCopy(it) }
        super.onCleared()
    }

    companion object {
        const val SAVED_HOLD_MILLIS = 1000L
        const val RETRY_MILLIS = 2000L
        const val STORAGE_ERROR = "Couldn't save on this phone. Trying again…"
        const val KEY_CAPTURE_ID = "capture.id"
    }
}
