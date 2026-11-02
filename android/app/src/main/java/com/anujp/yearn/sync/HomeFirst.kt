package com.anujp.yearn.sync

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import kotlinx.coroutines.CancellationException
import okhttp3.HttpUrl
import okhttp3.OkHttpClient

/**
 * The client for this sync run: the [home] Wi-Fi address when the desktop there proves it holds the token,
 * otherwise the [tailnet] one. Null when neither can be used (not set up). The token is never sent to the
 * home address before the proof.
 */
suspend fun pickReceiver(
    home: HttpUrl?,
    tailnet: HttpUrl?,
    token: String,
    homeHttp: OkHttpClient = ReceiverClient.httpClient(home = true),
    tailnetHttp: OkHttpClient = ReceiverClient.httpClient(),
): ReceiverClient? {
    if (home != null) {
        val c = ReceiverClient.create(home, token, homeHttp)
        val proven = try {
            c?.proves() == true
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            false
        }
        if (proven) return c
    }
    return tailnet?.let { ReceiverClient.create(it, token, tailnetHttp) }
}

/** Whether any Wi-Fi network is up, Tailscale's VPN or not; only then is the home address worth trying. */
fun wifiCheck(context: Context): () -> Boolean {
    val cm = context.getSystemService(ConnectivityManager::class.java)
    return {
        @Suppress("DEPRECATION") // allNetworks still lists Wi-Fi under a VPN; the default network is the VPN.
        cm?.allNetworks.orEmpty().any { cm?.getNetworkCapabilities(it)?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true }
    }
}
