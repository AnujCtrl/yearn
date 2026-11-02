package com.anujp.yearn.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.anujp.yearn.data.CaptureDao
import com.anujp.yearn.data.TOKEN_REJECTED_REASON
import com.anujp.yearn.sync.ReceiverClient
import com.anujp.yearn.sync.ReceiverError
import com.anujp.yearn.sync.SettingsStore
import com.anujp.yearn.sync.SyncScheduler
import com.anujp.yearn.sync.TailnetDns
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
 * the connection check and the battery state. [clientFactory] gives the Tailscale client and
 * [homeClientFactory] the home Wi-Fi one, each on its own; null means "not set up". [power] answers
 * "is yearn exempt from battery optimisation?".
 */
class SettingsViewModel(
    private val store: SettingsStore,
    private val dao: CaptureDao,
    private val clientFactory: suspend () -> ReceiverClient?,
    private val homeClientFactory: suspend () -> ReceiverClient? = { null },
    private val power: () -> Boolean,
    private val scheduler: SyncScheduler,
    private val clock: () -> ZonedDateTime = { ZonedDateTime.now() },
) : ViewModel() {
    val address: StateFlow<String?> = store.baseUrl
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), null)

    /** Null until the store has been read, then whether a desktop address is saved (there is no default). */
    val hasAddress: StateFlow<Boolean?> = store.baseUrl.map { it != null }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), null)

    val homeAddress: StateFlow<String?> = store.homeUrl
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

    private val _homeConnection = MutableStateFlow("")

    /** Empty without a home address, else [REACHABLE], [UNREACHABLE] or [NOT_YOUR_DESKTOP]. */
    val homeConnection: StateFlow<String> = _homeConnection.asStateFlow()

    private val _checkedAt = MutableStateFlow<ZonedDateTime?>(null)
    val checkedAt: StateFlow<ZonedDateTime?> = _checkedAt.asStateFlow()

    private val _testing = MutableStateFlow(false)
    val testing: StateFlow<Boolean> = _testing.asStateFlow()

    private val _addressError = MutableStateFlow<String?>(null)
    val addressError: StateFlow<String?> = _addressError.asStateFlow()

    private val _homeAddressError = MutableStateFlow<String?>(null)
    val homeAddressError: StateFlow<String?> = _homeAddressError.asStateFlow()

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

    /**
     * One `GET /v1/health` over Tailscale and, when a home address is saved, the token proof there. A second
     * tap while it runs waits for the first.
     */
    fun testConnection(): Job {
        test?.takeIf { it.isActive }?.let { return it }
        return viewModelScope.launch {
            _testing.value = true
            try {
                _connection.value = check()
                _homeConnection.value = checkHome()
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

    /** Empty without a home address; the token is never sent there unless the desktop proves it has it. */
    private suspend fun checkHome(): String {
        val client = try {
            homeClientFactory()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        } ?: return ""
        return try {
            if (client.proves()) REACHABLE else NOT_YOUR_DESKTOP
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            UNREACHABLE
        }
    }

    /**
     * The desktop's home Wi-Fi IP, like `192.168.1.20:8787`; blank removes it. An IP outside the home
     * ranges is refused here, since the client would refuse to connect to it anyway.
     */
    fun setHomeAddress(raw: String): Job = viewModelScope.launch {
        if (raw.isBlank()) {
            _homeAddressError.value = null
            store.setHomeUrl(null)
            return@launch
        }
        val url = normalizeAddress(raw)
        val literal = url?.toHttpUrlOrNull()?.host?.let(TailnetDns::literalOrNull)
        if (url == null || (literal != null && !TailnetDns.isHomeNetwork(literal))) {
            _homeAddressError.value = BAD_HOME_ADDRESS
            return@launch
        }
        _homeAddressError.value = null
        if (url == store.homeUrl.first()) return@launch
        store.setHomeUrl(url)
        scheduler.enqueueNow()
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
        const val NOT_YOUR_DESKTOP = "Something else answered at the home address"
        const val BAD_ADDRESS = "That isn't an address yearn can reach"
        const val BAD_HOME_ADDRESS = "Use your desktop’s Wi-Fi IP, like 192.168.1.20:8787"
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

/**
 * The Test connection row's subtitle: `Reachable a minute ago`, a failure in full, or a hint. With a home
 * address both routes are named: `Wi-Fi: reachable · Tailscale: can't reach`.
 */
fun connectionLabel(
    connection: String,
    checkedAt: ZonedDateTime?,
    testing: Boolean,
    now: ZonedDateTime,
    home: String = "",
): String = when {
    testing -> "Checking…"
    connection.isEmpty() -> "Ask your desktop if it can hear yearn"
    home.isNotEmpty() -> "Wi-Fi: ${shortResult(home)} · Tailscale: ${shortResult(connection)}"
    connection == SettingsViewModel.REACHABLE && checkedAt != null -> "Reachable " + ago(checkedAt, now)
    else -> connection
}

private fun shortResult(result: String): String = when (result) {
    SettingsViewModel.REACHABLE -> "reachable"
    SettingsViewModel.UNREACHABLE -> "can't reach"
    SettingsViewModel.UNAUTHORIZED -> "token refused"
    SettingsViewModel.NOT_SET_UP -> "not set up"
    SettingsViewModel.NOT_YOUR_DESKTOP -> "not your desktop"
    else -> result
}
