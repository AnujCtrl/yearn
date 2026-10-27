package com.anujp.yearn.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.anujp.yearn.capture.CaptureService
import com.anujp.yearn.data.CaptureDao
import com.anujp.yearn.data.CaptureEntity
import com.anujp.yearn.data.CaptureRecord
import com.anujp.yearn.data.FileEntity
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

/** One save: the row, its files, and the full record exactly as the desktop receives it. */
class DetailViewModel(
    private val id: String,
    private val dao: CaptureDao,
    private val service: CaptureService,
) : ViewModel() {
    val capture: StateFlow<CaptureEntity?> = dao.observe(id)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), null)

    val files: StateFlow<List<FileEntity>> = dao.observeFiles(id)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), emptyList())

    private val record = combine(dao.observe(id), dao.observeFiles(id)) { c, f ->
        if (c == null) null else runCatching {
            CaptureRecord.full(Json.parseToJsonElement(c.baseRecordJson).jsonObject, f, c.note, c.noteUpdatedAt)
        }.getOrNull()
    }

    /** `CaptureRecord.full`, pretty-printed; empty until the row has loaded. */
    val rawJson: StateFlow<String> = record
        .map { r -> r?.let { PrettyJson.encodeToString(JsonObject.serializer(), it) } ?: "" }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), "")

    /** How many extras the share carried: the "7 fields" of the Raw share row. */
    val rawFieldCount: StateFlow<Int> = record
        .map { (it?.get("extras") as? JsonObject)?.size ?: 0 }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), 0)

    /**
     * Saves [text] (trimmed) as the note when it differs from the stored one. A blank note is saved
     * only to clear one that exists. Not tied to the screen: leaving it mid-save still finishes.
     */
    fun saveNote(text: String): Job = viewModelScope.launch(NonCancellable) {
        val current = dao.get(id) ?: return@launch
        val next = text.trim()
        val stored = current.note.orEmpty()
        if (next == stored) return@launch
        if (next.isEmpty() && current.note == null) return@launch
        service.saveNote(id, next)
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
        val PrettyJson = Json { prettyPrint = true }
    }
}
