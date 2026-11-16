package com.kodraliu.localrock.shared.vacuum.camera

import com.kodraliu.localrock.shared.auth.Hawk
import com.kodraliu.localrock.shared.auth.HawkCreds
import com.kodraliu.localrock.shared.webrtc.RtcIceServer

private const val TURN_PATH = "/turn"

/** Where the experimental TURN WebSocket lives for [serverBaseUrl], or null when it is not a usable http(s) URL. */
fun turnSocketUrl(serverBaseUrl: String?): String? {
    val base = serverBaseUrl?.trim()?.trimEnd('/') ?: return null
    val rest = when {
        base.startsWith("https://", ignoreCase = true) -> "wss://" + base.substring(8)
        base.startsWith("http://", ignoreCase = true) -> "wss://" + base.substring(7)
        else -> return null
    }
    return if (rest.length > 6) rest + TURN_PATH else null
}

/** The host part of [serverBaseUrl] (no scheme, port or path), for the home DNS check. */
fun serverHost(serverBaseUrl: String?): String? {
    val base = serverBaseUrl?.trim() ?: return null
    val hostPort = base.substringAfter("://", "").substringBefore('/').substringAfterLast('@')
    val host = if (hostPort.startsWith("[")) hostPort.substringBefore(']') + "]" else hostPort.substringBefore(':')
    return host.ifBlank { null }
}

/** [server] pointed at the loopback listener on [port], credentials kept. */
fun tunnelledIceServer(server: RtcIceServer, port: Int): RtcIceServer =
    server.copy(url = "turn:127.0.0.1:$port?transport=tcp")

/** Tunnel when forced, or when the phone is known to be away from home; unknown location keeps the direct relay. */
fun shouldTunnelTurn(atHome: Boolean?, forced: Boolean): Boolean = forced || atHome == false

/** Handshake headers for the `/turn` WebSocket: Hawk for path `/turn` plus a User-Agent Cloudflare accepts. */
fun turnSocketHeaders(creds: HawkCreds?, ts: Long, nonce: String): Map<String, String> {
    if (creds == null) return emptyMap()
    return mapOf(
        "Authorization" to Hawk.authorizationHeader(creds, TURN_PATH, ts, nonce),
        "User-Agent" to "LocalRock/mobile",
    )
}
