package com.kodraliu.localrock.shared.mqtt

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/** True at home (host resolves to a private address), false away, null when the transport cannot tell. */
suspend fun isAtHome(
    host: String,
    transport: MqttNativeTransport? = defaultMqttTransport(),
    timeoutMs: Long = HOME_CHECK_TIMEOUT_MS,
): Boolean? {
    val resolver = transport as? MqttWebSocketTransport ?: return null
    return withContext(Dispatchers.Default) { withTimeoutOrNull(timeoutMs) { resolver.resolvesToPrivateAddress(host) } }
}

private const val HOME_CHECK_TIMEOUT_MS = 3_000L
