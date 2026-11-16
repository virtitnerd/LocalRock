package com.kodraliu.localrock.shared.webrtc

import com.kodraliu.localrock.shared.http.newPlatformHttpClient
import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocketSession
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import io.ktor.network.selector.SelectorManager
import io.ktor.network.sockets.InetSocketAddress
import io.ktor.network.sockets.ServerSocket
import io.ktor.network.sockets.Socket
import io.ktor.network.sockets.aSocket
import io.ktor.network.sockets.openReadChannel
import io.ktor.network.sockets.openWriteChannel
import io.ktor.utils.io.readAvailable
import io.ktor.utils.io.writeFully
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readBytes
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * EXPERIMENTAL. Carries libwebrtc's TURN-over-TCP traffic through a binary WebSocket to [socketUrl],
 * so the relay is reachable behind an HTTP-only tunnel. Each connection accepted on 127.0.0.1 becomes one WebSocket.
 * [headersProvider] is called per connection (fresh Hawk nonce; extension point for access tokens).
 */
class TurnTunnel(
    private val socketUrl: String,
    private val headersProvider: () -> Map<String, String>,
    private val scope: CoroutineScope,
) {
    private var selector: SelectorManager? = null
    private var server: ServerSocket? = null
    private var client: HttpClient? = null
    private var acceptJob: Job? = null

    /** Checks the server accepts the WebSocket, binds 127.0.0.1 on an ephemeral port and returns it; throws when unreachable. */
    suspend fun start(): Int {
        val http = newPlatformHttpClient { install(WebSockets) { pingIntervalMillis = 0 } }
        try {
            openSocket(http).close()
        } catch (e: Throwable) {
            runCatching { http.close() }
            throw e
        }
        val sel = SelectorManager(Dispatchers.Default)
        val srv = aSocket(sel).tcp().bind(InetSocketAddress("127.0.0.1", 0))
        selector = sel
        server = srv
        client = http
        acceptJob = scope.launch {
            while (isActive) {
                val socket = try { srv.accept() } catch (e: CancellationException) { throw e } catch (e: Throwable) { break }
                launch { runCatching { handle(socket) } }
            }
        }
        return (srv.localAddress as InetSocketAddress).port
    }

    fun close() {
        acceptJob?.cancel()
        runCatching { server?.close() }
        runCatching { client?.close() }
        runCatching { selector?.close() }
        acceptJob = null
        server = null
        client = null
        selector = null
    }

    private suspend fun openSocket(http: HttpClient): DefaultClientWebSocketSession {
        val headers = headersProvider()
        return http.webSocketSession(socketUrl) {
            headers.forEach { (k, v) -> header(k, v) }
            header(HttpHeaders.SecWebSocketProtocol, SUBPROTOCOL)
        }
    }

    private suspend fun handle(socket: Socket) {
        val http = client ?: return
        try {
            val ws = openSocket(http)
            val input = socket.openReadChannel()
            val output = socket.openWriteChannel(autoFlush = true)
            try {
                pumpTunnel(
                    readSocket = { buf -> input.readAvailable(buf, 0, buf.size) },
                    writeSocket = { buf, n -> output.writeFully(buf, 0, n) },
                    receiveWs = {
                        var data: ByteArray? = null
                        while (data == null) {
                            val frame = ws.incoming.receiveCatching().getOrNull() ?: break
                            if (frame is Frame.Binary) data = frame.readBytes()
                        }
                        data
                    },
                    sendWs = { ws.send(Frame.Binary(true, it)) },
                )
            } finally {
                runCatching { ws.close() }
            }
        } finally {
            runCatching { socket.close() }
        }
    }

    companion object {
        const val SUBPROTOCOL = "turn"
    }
}

/** Copies bytes both ways until either side ends, then returns; [readSocket] gives -1 at end of stream, [receiveWs] null when the socket closes. */
internal suspend fun pumpTunnel(
    readSocket: suspend (ByteArray) -> Int,
    writeSocket: suspend (ByteArray, Int) -> Unit,
    receiveWs: suspend () -> ByteArray?,
    sendWs: suspend (ByteArray) -> Unit,
) = coroutineScope {
    val up = launch {
        val buf = ByteArray(PUMP_BUFFER)
        while (true) {
            val n = readSocket(buf)
            if (n < 0) break
            if (n > 0) sendWs(buf.copyOf(n))
        }
    }
    val down = launch {
        while (true) {
            val data = receiveWs() ?: break
            writeSocket(data, data.size)
        }
    }
    // Whichever direction ends first tears the other down.
    val first = launch { up.join(); down.cancel() }
    val second = launch { down.join(); up.cancel() }
    up.join()
    down.join()
    first.cancel()
    second.cancel()
}

private const val PUMP_BUFFER = 16 * 1024
