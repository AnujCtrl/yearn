package com.anujp.yearn.sync

import kotlinx.coroutines.runBlocking
import okhttp3.Dns
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.InetAddress
import java.net.UnknownHostException

class TailnetDnsTest {
    private val dns = TailnetDns()

    private fun addr(s: String) = InetAddress.getByName(s) // literals only: no lookup happens

    @Test
    fun acceptsTailnetAndLoopbackAddresses() {
        assertEquals(listOf(addr("100.101.102.103")), dns.lookup("100.101.102.103"))
        assertEquals(listOf(addr("127.0.0.1")), dns.lookup("127.0.0.1"))
        assertEquals(listOf(addr("100.64.0.1")), dns.lookup("100.64.0.1"))
        assertEquals(listOf(addr("100.127.255.254")), dns.lookup("100.127.255.254"))
        assertEquals(listOf(addr("fd7a:115c:a1e0::1")), dns.lookup("fd7a:115c:a1e0::1"))
        assertEquals(listOf(addr("::1")), dns.lookup("::1"))
    }

    @Test
    fun rejectsPublicAndLanAddresses() {
        for (a in listOf("8.8.8.8", "192.168.0.1", "100.128.0.1", "100.63.255.255", "10.0.0.5", "fd7a:115c:a1e1::1", "2001:4860:4860::8888")) {
            assertThrows(a, UnknownHostException::class.java) { dns.lookup(a) }
        }
    }

    @Test
    fun namesAreResolvedThenFiltered() {
        val fake = Dns { name ->
            when (name) {
                "desktop" -> listOf(addr("100.101.102.103"))
                "mixed" -> listOf(addr("203.0.113.9"), addr("100.100.1.1"))
                else -> listOf(addr("203.0.113.9"))
            }
        }
        val guarded = TailnetDns(fake)
        assertEquals(listOf(addr("100.101.102.103")), guarded.lookup("desktop"))
        assertEquals(listOf(addr("100.100.1.1")), guarded.lookup("mixed"))
        assertThrows(UnknownHostException::class.java) { guarded.lookup("desktop.lan") }
    }

    @Test
    fun literalPublicIpUrl_isRefusedBeforeConnecting() = runBlocking {
        val c = ReceiverClient.create("http://203.0.113.1:8787/".toHttpUrl(), "tok")!!
        val e = runCatching { c.status("11111111-2222-4333-8444-555555555555") }.exceptionOrNull()
        assertTrue("$e", e is ReceiverError.Unreachable)
        assertTrue("${e?.cause}", e?.cause is UnknownHostException)
    }
}
