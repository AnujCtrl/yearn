package com.anujp.yearn.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.anujp.yearn.data.CaptureDao
import com.anujp.yearn.data.TOKEN_REJECTED_REASON
import com.anujp.yearn.sync.ReceiverClient
import com.anujp.yearn.sync.ReceiverError
import com.anujp.yearn.sync.SettingsStore
import com.anujp.yearn.sync.SyncScheduler
import com.anujp.yearn.ui.home.ago
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.time.OffsetDateTime
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * The receiver address, the pairing token (typed in, only ever shown masked and never logged),
 * the connection check and the battery state. [clientFactory] is the app's own: null means
 * "not set up". [power] answers "is yearn exempt from battery optimisation?".
 */
class SettingsViewModel(
    private val store: SettingsStore,
    private val dao: CaptureDao,
    private val clientFactory: suspend () -> ReceiverClient?,
    private val power: () -> Boolean,
    private val scheduler: SyncScheduler,
    private val clock: () -> ZonedDateTime = { ZonedDateTime.now() },
) : ViewModel() {
    val address: StateFlow<String?> = store.baseUrl
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), null)

    /** Null until the store has been read, then whether a desktop address is saved (there is no default). */
    val hasAddress: StateFlow<Boolean?> = store.baseUrl.map { it != null }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), null)

    val lastSuccessfulSync: StateFlow<String?> = store.lastSuccessfulSync
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), null)

    private val _hasToken = MutableStateFlow<Boolean?>(null)

    /** Null until the store has been read, then whether a usable token is saved. */
    val hasToken: StateFlow<Boolean?> = _hasToken.asStateFlow()

    private val _tokenMask = MutableStateFlow<String?>(null)

    /** `••••` plus the token's last four characters; null when there is no token. */
    val tokenMask: StateFlow<String?> = _tokenMask.asStateFlow()

    private val _connection = MutableStateFlow("")

    /** Empty until tested, then one of [REACHABLE], [UNREACHABLE], [UNAUTHORIZED], [NOT_SET_UP]. */
    val connection: StateFlow<String> = _connection.asStateFlow()

    private val _checkedAt = MutableStateFlow<ZonedDateTime?>(null)
    val checkedAt: StateFlow<ZonedDateTime?> = _checkedAt.asStateFlow()

    private val _testing = MutableStateFlow(false)
    val testing: StateFlow<Boolean> = _testing.asStateFlow()

    private val _addressError = MutableStateFlow<String?>(null)
    val addressError: StateFlow<String?> = _addressError.asStateFlow()

    private val _tokenError = MutableStateFlow<String?>(null)
    val tokenError: StateFlow<String?> = _tokenError.asStateFlow()

    private val _battery = MutableStateFlow(power())
    val batteryUnrestricted: StateFlow<Boolean> = _battery.asStateFlow()

    private var test: Job? = null

    init {
        viewModelScope.launch { showToken(store.token()) }
    }

    /** Re-reads the battery state, e.g. when the user comes back from the system dialog. */
    fun refreshBattery() {
        _battery.value = power()
    }

    /** One `GET /v1/health` with the saved address and token. A second tap while it runs waits for the first. */
    fun testConnection(): Job {
        test?.takeIf { it.isActive }?.let { return it }
        return viewModelScope.launch {
            _testing.value = true
            try {
                _connection.value = check()
                _checkedAt.value = clock()
            } finally {
                _testing.value = false
            }
        }.also { test = it }
    }

    private suspend fun check(): String {
        val client = try {
            clientFactory()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        } ?: return NOT_SET_UP
        return try {
            client.health()
            REACHABLE
        } catch (_: ReceiverError.Unauthorized) {
            UNAUTHORIZED
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            UNREACHABLE
        }
    }

    /** `desk:8787` and `http://desk:8787/` both save as `http://desk:8787`; waiting saves then go. */
    fun setAddress(raw: String): Job = viewModelScope.launch {
        val url = normalizeAddress(raw)
        if (url == null) {
            _addressError.value = BAD_ADDRESS
            return@launch
        }
        _addressError.value = null
        if (url == store.baseUrl.first()) return@launch
        store.setBaseUrl(url)
        retryTokenFailures()
        scheduler.enqueueNow()
    }

    /** Trims and saves the token sealed, then checks it against the desktop. Blank input changes nothing. */
    fun setToken(raw: String): Job = viewModelScope.launch {
        val token = raw.trim()
        if (token.isEmpty()) return@launch
        if (token.any { it.code !in 0x21..0x7e }) {
            _tokenError.value = BAD_TOKEN
            return@launch
        }
        _tokenError.value = null
        store.setToken(token)
        showToken(token)
        retryTokenFailures()
        scheduler.enqueueNow()
        testConnection()
    }

    /** Saves the old token was refused for get another go now that the token or address changed. */
    private suspend fun retryTokenFailures() {
        try {
            dao.resetFailed(TOKEN_REJECTED_REASON)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // The setting is saved; the saves stay Failed and the user can still retry them by hand.
        }
    }

    private fun showToken(token: String?) {
        _hasToken.value = token != null
        _tokenMask.value = token?.let(::maskToken)
    }

    companion object {
        const val REACHABLE = "Reachable"
        const val UNREACHABLE = "Couldn't reach your desktop"
        const val UNAUTHORIZED = TOKEN_REJECTED_REASON
        const val NOT_SET_UP = "Desktop not set up yet"
        const val BAD_ADDRESS = "That isn't an address yearn can reach"
        const val BAD_TOKEN = "A pairing token has no spaces or accents"
        private const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}

/** `••••` plus the last four characters; a token of four or fewer shows only the dots. */
fun maskToken(token: String): String = "••••" + if (token.length > 4) token.takeLast(4) else ""

/** Adds `http://` when no scheme is given and drops a bare trailing slash; null if it can't be a URL. */
fun normalizeAddress(raw: String): String? {
    val t = raw.trim()
    if (t.isEmpty()) return null
    val withScheme = if (t.contains("://")) t else "http://$t"
    val url = withScheme.toHttpUrlOrNull() ?: return null
    val s = url.toString()
    return if (url.encodedPath == "/" && url.query == null) s.removeSuffix("/") else s
}

private val ClockTime: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.ENGLISH)
private val DayTime: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM d, HH:mm", Locale.ENGLISH)

/** `Today, 12:43`, `Yesterday, 09:10`, `Oct 24, 12:43`; `Not yet` when never synced. */
fun lastSyncLabel(timestamp: String?, now: ZonedDateTime): String {
    val then = timestamp?.let { runCatching { OffsetDateTime.parse(it).atZoneSameInstant(now.zone) }.getOrNull() }
        ?: return "Not yet"
    val day = then.toLocalDate()
    return when (day) {
        now.toLocalDate() -> "Today, ${ClockTime.format(then)}"
        now.toLocalDate().minusDays(1) -> "Yesterday, ${ClockTime.format(then)}"
        else -> DayTime.format(then)
    }
}

/** The Test connection row's subtitle: `Reachable a minute ago`, a failure in full, or a hint. */
fun connectionLabel(connection: String, checkedAt: ZonedDateTime?, testing: Boolean, now: ZonedDateTime): String = when {
    testing -> "Checking…"
    connection.isEmpty() -> "Ask your desktop if it can hear yearn"
    connection == SettingsViewModel.REACHABLE && checkedAt != null -> "Reachable " + ago(checkedAt, now)
    else -> connection
}
