package com.anujp.yearn.sync

import okhttp3.Dns
import okhttp3.Interceptor
import okhttp3.Response
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.UnknownHostException

/**
 * Keeps the Bearer token inside the tailnet. A name resolves only to Tailscale addresses (100.64.0.0/10,
 * fd7a:115c:a1e0::/48) or loopback; anything else throws [UnknownHostException], which the client maps to
 * Unreachable, so a capture waits instead of sending its token in cleartext to whatever the local network's
 * DNS points at when Tailscale is off. IP literals get the same check without a lookup.
 */
class TailnetDns(private val delegate: Dns = Dns.SYSTEM) : Dns {
    override fun lookup(hostname: String): List<InetAddress> {
        val literal = literalOrNull(hostname)
        val all = if (literal != null) listOf(literal) else delegate.lookup(hostname)
        val allowed = all.filter(::isAllowed)
        if (allowed.isEmpty()) throw UnknownHostException("$hostname is not on your tailnet")
        return allowed
    }

    /**
     * OkHttp never consults [Dns] for an IP-literal URL, so this runs the same check before any connection
     * is opened for one.
     */
    class LiteralGuard(private val dns: TailnetDns) : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            val host = chain.request().url.host
            if (literalOrNull(host) != null) dns.lookup(host)
            return chain.proceed(chain.request())
        }
    }

    companion object {
        private val IPV4 = Regex("""\d{1,3}(\.\d{1,3}){3}""")

        /** Parses an IPv4 or IPv6 literal without touching DNS; null for a name. */
        fun literalOrNull(host: String): InetAddress? {
            if (!host.contains(':') && !IPV4.matches(host)) return null
            return runCatching { InetAddress.getByName(host.removePrefix("[").removeSuffix("]")) }.getOrNull()
        }

        fun isAllowed(a: InetAddress): Boolean {
            if (a.isLoopbackAddress) return true
            val b = a.address
            return when (a) {
                is Inet4Address -> (b[0].toInt() and 0xff) == 100 && (b[1].toInt() and 0xc0) == 64
                is Inet6Address -> b[0] == 0xfd.toByte() && b[1] == 0x7a.toByte() && b[2] == 0x11.toByte() &&
                    b[3] == 0x5c.toByte() && b[4] == 0xa1.toByte() && b[5] == 0xe0.toByte()
                else -> false
            }
        }
    }
}
