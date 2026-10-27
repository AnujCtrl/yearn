package com.anujp.yearn.sync

import android.content.Context
import android.util.Base64
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.File

/** Receiver address, the Keystore-sealed pairing token and the last successful sync time. */
class SettingsStore(context: Context, private val box: SecretBox) {
    private val store: DataStore<Preferences> = dataStoreFor(File(context.applicationContext.filesDir, "datastore/settings.preferences_pb"))

    /** Null until the user types the desktop address in Settings; there is no built-in default. */
    val baseUrl: Flow<String?> = store.data.map { it[BASE_URL] }
    val lastSuccessfulSync: Flow<String?> = store.data.map { it[LAST_SYNC] }

    suspend fun setBaseUrl(url: String) {
        store.edit { it[BASE_URL] = url }
    }

    suspend fun setLastSuccessfulSync(timestamp: String) {
        store.edit { it[LAST_SYNC] = timestamp }
    }

    suspend fun setToken(token: String) {
        val sealed = Base64.encodeToString(box.seal(token.toByteArray(Charsets.UTF_8)), Base64.NO_WRAP)
        store.edit { it[TOKEN_SEALED] = sealed }
    }

    /** Null when unset, or when it cannot be opened (e.g. the Keystore key was wiped). Never throws. */
    suspend fun token(): String? {
        val sealed = try {
            store.data.first()[TOKEN_SEALED]
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        } ?: return null
        return try {
            String(box.open(Base64.decode(sealed, Base64.NO_WRAP)), Charsets.UTF_8)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
    }

    companion object {
        private val BASE_URL = stringPreferencesKey("base_url")
        private val TOKEN_SEALED = stringPreferencesKey("token_sealed")
        private val LAST_SYNC = stringPreferencesKey("last_successful_sync")

        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        private val stores = HashMap<String, DataStore<Preferences>>()

        /** One DataStore per file per process; a second instance on the same file would throw. */
        private fun dataStoreFor(file: File): DataStore<Preferences> = synchronized(stores) {
            stores.getOrPut(file.absolutePath) {
                // A corrupt file (torn write, bad flash) starts over empty instead of throwing on every read;
                // the user re-enters the token and captures wait as "Desktop not set up yet" meanwhile.
                PreferenceDataStoreFactory.create(
                    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
                    scope = scope,
                ) { file }
            }
        }
    }
}
