package com.anujp.yearn.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.anujp.yearn.data.CaptureDao
import com.anujp.yearn.data.CaptureEntity
import com.anujp.yearn.data.SyncStatus
import com.anujp.yearn.sync.SyncScheduler
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * One line of the saves list, ready to draw. Beyond the brief's seven fields: [source] (the
 * placeholder tile's tint and letter, and the sidebar's subtitle) and [today] (the "Today" or
 * "Earlier" group of the board). Both default so the seven-field constructor still works.
 */
data class SaveRow(
    val id: String,
    val title: String,
    val subtitle: String,
    val thumbPath: String?,
    val status: String,
    val failedReason: String?,
    val hasNote: Boolean,
    val source: String = "",
    val today: Boolean = false,
)

class HomeViewModel(
    private val dao: CaptureDao,
    private val scheduler: SyncScheduler,
    /** `SettingsStore.lastSuccessfulSync`: an ISO-8601 timestamp, or null if never synced. */
    private val lastSync: Flow<String?> = flowOf(null),
    private val clock: () -> ZonedDateTime,
) : ViewModel() {
    /** Emits the clock's time now and once a minute, so "5 min" keeps counting while the list is open. */
    private val ticker = flow {
        while (true) {
            emit(clock())
            delay(TICK_MILLIS)
        }
    }

    val rows: StateFlow<List<SaveRow>> = combine(dao.observeAll(), ticker) { captures, now ->
        captures.map { it.toRow(now) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), emptyList())

    /** What the capsule counts: everything not yet on the desktop and not failed. */
    val waitingCount: StateFlow<Int> = dao.observeAll()
        .map { list -> list.count { it.syncStatus == SyncStatus.WAITING || it.syncStatus == SyncStatus.UPLOADING } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), 0)

    /** The line under the title: `Synced 14 min ago · 2 waiting`; either half drops out when it has nothing to say. */
    val header: StateFlow<String> = combine(lastSync, waitingCount, ticker) { last, waiting, now ->
        val synced = last?.let { runCatching { OffsetDateTime.parse(it).atZoneSameInstant(now.zone) }.getOrNull() }
            ?.let { "Synced " + ago(it, now) }
        listOfNotNull(synced, if (waiting > 0) "$waiting waiting" else null).joinToString(" · ")
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), "")

    fun syncNow() = scheduler.enqueueNow()

    /** A failed save starts over as Waiting with a fresh retry budget; anything else is left alone. */
    fun retry(id: String) {
        viewModelScope.launch {
            if (dao.get(id)?.syncStatus != SyncStatus.FAILED) return@launch
            dao.setSync(id, SyncStatus.WAITING, attempts = 0, lastError = null, uploadedAt = null)
            scheduler.enqueueNow()
        }
    }

    private companion object {
        const val TICK_MILLIS = 60_000L
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}

internal fun CaptureEntity.toRow(now: ZonedDateTime): SaveRow {
    val then = Instant.ofEpochMilli(capturedAtMillis).atZone(now.zone)
    return SaveRow(
        id = id,
        title = previewTitle ?: previewText ?: previewUrl ?: sourceLabel,
        subtitle = "$sourceLabel · ${relativeTime(then, now)}",
        thumbPath = thumbPath,
        status = statusWord(syncStatus),
        failedReason = if (syncStatus == SyncStatus.FAILED) lastError else null,
        hasNote = !note.isNullOrBlank(),
        source = sourceLabel,
        today = then.toLocalDate() == now.toLocalDate(),
    )
}

/** The four status words, exactly as the spec spells them. */
fun statusWord(s: SyncStatus): String = when (s) {
    SyncStatus.WAITING -> "Waiting"
    SyncStatus.UPLOADING -> "Uploading"
    SyncStatus.UPLOADED -> "Uploaded"
    SyncStatus.FAILED -> "Failed"
}

private val MonthDay: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM d", Locale.ENGLISH)

/**
 * `now` under a minute (or in the future, on clock skew), `N min` under an hour, `N h` under a day,
 * then `Yesterday` if [then] falls on the calendar day before [now] (in now's zone), else `Oct 24`.
 */
fun relativeTime(then: ZonedDateTime, now: ZonedDateTime): String {
    val age = Duration.between(then, now)
    return when {
        age < Duration.ofMinutes(1) -> "now"
        age < Duration.ofHours(1) -> "${age.toMinutes()} min"
        age < Duration.ofDays(1) -> "${age.toHours()} h"
        else -> {
            val local = then.withZoneSameInstant(now.zone)
            if (local.toLocalDate() == now.toLocalDate().minusDays(1)) "Yesterday" else MonthDay.format(local)
        }
    }
}

/** [relativeTime] as a phrase: `just now`, `5 min ago`, `3 h ago`, `yesterday`, `on Oct 24`. */
fun ago(then: ZonedDateTime, now: ZonedDateTime): String = when (val r = relativeTime(then, now)) {
    "now" -> "just now"
    "Yesterday" -> "yesterday"
    else -> if (r.endsWith(" min") || r.endsWith(" h")) "$r ago" else "on $r"
}
