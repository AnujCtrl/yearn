package com.anujp.yearn.sync

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

/** Not secure; just proves the plain token never reaches disk. */
class XorBox : SecretBox {
    override fun seal(plain: ByteArray) = ByteArray(plain.size) { (plain[it].toInt() xor 0x5A).toByte() }
    override fun open(sealed: ByteArray) = seal(sealed)
}

@RunWith(RobolectricTestRunner::class)
class SettingsStoreTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Test
    fun token_isNeverStoredInPlainText() = runBlocking {
        val store = SettingsStore(context, XorBox())
        val token = "super-secret-pairing-token-12345"
        assertNull(store.token())
        store.setToken(token)
        assertEquals(token, store.token())
        val file = File(context.filesDir, "datastore/settings.preferences_pb")
        assertTrue(file.exists())
        assertFalse(String(file.readBytes(), Charsets.ISO_8859_1).contains(token))
    }

    @Test
    fun baseUrl_isUnsetUntilEntered() = runBlocking {
        val store = SettingsStore(context, XorBox())
        assertNull(store.baseUrl.first())
        store.setBaseUrl("http://x:1")
        assertEquals("http://x:1", store.baseUrl.first())
    }

    @Test
    fun token_isNullWhenOpeningFails() = runBlocking {
        val store = SettingsStore(context, XorBox())
        store.setToken("abc")
        val broken = SettingsStore(context, object : SecretBox {
            override fun seal(plain: ByteArray) = plain
            override fun open(sealed: ByteArray): ByteArray = throw java.security.GeneralSecurityException("key gone")
        })
        assertNull(broken.token())
    }

    @Test
    fun corruptFile_isReplacedWithEmptySettings() = runBlocking {
        val file = File(context.filesDir, "datastore/settings.preferences_pb")
        file.parentFile!!.mkdirs()
        file.writeBytes(ByteArray(64) { 0xFF.toByte() })
        val store = SettingsStore(context, XorBox())
        assertNull(store.baseUrl.first())
        assertNull(store.token())
        store.setBaseUrl("http://x:1")
        assertEquals("http://x:1", store.baseUrl.first())
    }
}
