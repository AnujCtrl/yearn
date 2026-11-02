package com.anujp.yearn.sync

import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.net.ServerSocket
import java.util.concurrent.TimeUnit
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

class HomeFirstTest {
    private val home = MockWebServer()
    private val tailnet = MockWebServer()
    private val http = OkHttpClient.Builder().connectTimeout(2, TimeUnit.SECONDS).readTimeout(2, TimeUnit.SECONDS).build()

    @Before
    fun setUp() {
        home.start()
        tailnet.start()
        tailnet.dispatcher = desktop("tok")
    }

    @After
    fun tearDown() {
        runCatching { home.shutdown() }
        runCatching { tailnet.shutdown() }
    }

    /** The receiver's answer, worked out here independently of the app's code. */
    private fun proofFor(token: String, nonce: String): String {
        val mac = Mac.getInstance("HmacSHA256").apply { init(SecretKeySpec(token.toByteArray(), "HmacSHA256")) }
        return mac.doFinal("yearn-prove-v1:$nonce".toByteArray()).joinToString("") { "%02x".format(it) }
    }

    private fun json(code: Int, body: String) =
        MockResponse().setResponseCode(code).setHeader("Content-Type", "application/json").setBody(body)

    /** A receiver holding [token]: answers prove, and health only with that token. */
    private fun desktop(token: String) = object : Dispatcher() {
        override fun dispatch(request: RecordedRequest): MockResponse {
            val url = request.requestUrl!!
            return when {
                url.encodedPath == "/v1/prove" -> json(200, """{"proof":"${proofFor(token, url.queryParameter("nonce")!!)}"}""")
                request.getHeader("Authorization") != "Bearer $token" -> json(401, """{"error":"unauthorized"}""")
                else -> json(200, """{"service":"yearn-receiver","api":1}""")
            }
        }
    }

    private fun answering(response: MockResponse) = object : Dispatcher() {
        override fun dispatch(request: RecordedRequest) = response
    }

    private suspend fun pick(homeUrl: Boolean = true) =
        pickReceiver(if (homeUrl) home.url("/") else null, tailnet.url("/"), "tok", homeHttp = http, tailnetHttp = http)

    /** Which server the picked client talks to. */
    private suspend fun ReceiverClient.servedBy(): MockWebServer {
        val before = home.requestCount
        health()
        return if (home.requestCount > before) home else tailnet
    }

    @Test
    fun proves_checksTheAnswerAndNeverSendsTheToken() = runBlocking {
        home.dispatcher = desktop("tok")
        val c = ReceiverClient.create(home.url("/"), "tok", http)!!
        assertTrue(c.proves())
        val req = home.takeRequest()
        assertEquals("/v1/prove", req.requestUrl!!.encodedPath)
        assertNull(req.getHeader("Authorization"))
        assertTrue(Regex("[0-9a-f]{32}").matches(req.requestUrl!!.queryParameter("nonce")!!))

        home.dispatcher = desktop("someone-else")
        assertFalse(c.proves())
        home.dispatcher = answering(json(404, """{"error":"not_found"}"""))
        assertFalse(c.proves())
        home.dispatcher = answering(json(200, "not json"))
        assertFalse(c.proves())
        home.dispatcher = answering(json(200, """{"proof":12}"""))
        assertFalse(c.proves())
    }

    @Test
    fun proves_usesAFreshNonceEveryTime() = runBlocking {
        home.dispatcher = desktop("tok")
        val c = ReceiverClient.create(home.url("/"), "tok", http)!!
        c.proves()
        c.proves()
        assertNotEquals(
            home.takeRequest().requestUrl!!.queryParameter("nonce"),
            home.takeRequest().requestUrl!!.queryParameter("nonce"),
        )
    }

    @Test
    fun theHomeAddressWinsWhenYourDesktopAnswersThere() = runBlocking {
        home.dispatcher = desktop("tok")
        assertEquals(home, pick()!!.servedBy())
        assertEquals(0, tailnet.requestCount)
    }

    @Test
    fun anImpostorAtTheHomeAddressNeverGetsTheToken() = runBlocking {
        val seen = mutableListOf<RecordedRequest>()
        home.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                seen += request
                return json(200, """{"proof":"${"0".repeat(64)}"}""")
            }
        }
        assertEquals(tailnet, pick()!!.servedBy())
        assertTrue(seen.isNotEmpty())
        assertTrue(seen.none { it.getHeader("Authorization") != null })
    }

    @Test
    fun nothingAtTheHomeAddressFallsBackToTailscale() = runBlocking {
        val dead = ServerSocket(0).use { it.localPort }
        val c = pickReceiver(home.url("/").newBuilder().port(dead).build(), tailnet.url("/"), "tok", homeHttp = http, tailnetHttp = http)
        assertEquals(tailnet, c!!.servedBy())
    }

    @Test
    fun noHomeAddressGoesStraightToTailscale() = runBlocking {
        assertEquals(tailnet, pick(homeUrl = false)!!.servedBy())
        assertEquals(0, home.requestCount)
    }

    @Test
    fun noTailscaleAddressAndNoHomeAnswerMeansNotSetUp() = runBlocking {
        home.dispatcher = desktop("someone-else")
        assertNull(pickReceiver(home.url("/"), null, "tok", homeHttp = http, tailnetHttp = http))
    }
}
