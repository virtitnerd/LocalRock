package com.kodraliu.localrock.shared.mqtt

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.concurrent.Volatile
import kotlin.coroutines.coroutineContext
import kotlin.time.TimeMark
import kotlin.time.TimeSource

data class MqttMessage(val topic: String, val payload: ByteArray) {
    override fun equals(other: Any?): Boolean =
        other is MqttMessage && other.topic == topic && other.payload.contentEquals(payload)
    override fun hashCode(): Int = 31 * topic.hashCode() + payload.contentHashCode()
}

class MqttClientException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)


class MqttClient(
    private val transport: MqttNativeTransport,
    private val homeCheckIntervalMs: Long = 15_000L,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _messages = MutableSharedFlow<MqttMessage>(extraBufferCapacity = 64)
    val messages: SharedFlow<MqttMessage> = _messages

    private val _connected = MutableStateFlow(false)
    val connected: StateFlow<Boolean> = _connected

    /** Extra WebSocket handshake headers, read for every attempt; the direct route never sends them. */
    @Volatile
    var extraHeaders: () -> Map<String, String> = { emptyMap() }

    private val mutex = Mutex()

    /**
     * One socket operation at a time. Several sessions share this connection and call in from
     * different threads, and the transport underneath makes no promise about that. The link was
     * seen to drop right after two sessions subscribed and published at the same moment.
     */
    private val ioMutex = Mutex()
    private var conn: MqttNativeConnection? = null
    private var superviseJob: Job? = null
    private var closed = false
    private var params: Params? = null

    /**
     * Called when a reconnect attempt fails, with the number of consecutive failures so far.
     * Returning fresh credentials swaps them in for the next attempt; returning null keeps the
     * current ones. Lets a session that the server has expired recover without a manual re-login.
     */
    var credentialRefresher: (suspend (attempt: Int, error: Throwable) -> MqttCreds?)? = null

    @Volatile
    private var lastMessageMark: TimeMark? = null

    /**
     * Milliseconds since anything was last received, or [Long.MAX_VALUE] if nothing ever was.
     * A socket can stay "open" long after it stops delivering, so callers use this to tell a
     * genuinely dead link from one that is merely slow.
     */
    fun millisSinceLastMessage(): Long =
        lastMessageMark?.elapsedNow()?.inWholeMilliseconds ?: Long.MAX_VALUE

    private var currentDisconnect: CompletableDeferred<Unit>? = null

    /** Cuts the wait between connect attempts short, see [retryNow]. */
    private val retrySignal = Channel<Unit>(Channel.CONFLATED)

    /**
     * Counted per topic: more than one session can listen to the same robot (the detail screen and
     * a reachability probe), and one of them leaving must not silence the other.
     */
    private class Subscription(val qos: Int, var holders: Int)

    private val subscriptions = mutableMapOf<String, Subscription>()

    private data class Params(
        val creds: MqttCreds,
        val clientId: String,
        val keepAliveSeconds: Int,
        val connectTimeoutMs: Long,
    )


    /**
     * Start the connection supervisor if it is not running, and return without waiting for the
     * broker. The supervisor keeps trying until [disconnect], the first attempt included: right
     * after an app start the network is often not up yet, and giving up there left the app offline
     * until the vacuum screen was opened again (issue #20). Watch [connected] for the outcome.
     */
    suspend fun start(
        creds: MqttCreds,
        clientId: String,
        connectTimeoutMs: Long = 15_000L,
        keepAliveSeconds: Int = 30,
    ) {
        mutex.withLock {
            // A supervisor that ended on an unexpected error must not keep a new one from starting.
            if (superviseJob?.isActive == true) return
            closed = false
            params = Params(creds, clientId, keepAliveSeconds, connectTimeoutMs)
            superviseJob = scope.launch { superviseLoop() }
        }
    }

    /**
     * Try to connect now instead of sitting out the rest of the backoff, which grows to 30 s.
     * For moments when the network has probably just come back, like the app returning to the
     * foreground. Does nothing while connected.
     */
    fun retryNow() {
        if (!_connected.value) retrySignal.trySend(Unit)
    }


    suspend fun subscribe(topic: String, qos: Int = QOS_AT_LEAST_ONCE) {
        val c = mutex.withLock {
            val existing = subscriptions[topic]
            if (existing != null) existing.holders++ else subscriptions[topic] = Subscription(qos, holders = 1)
            conn
        }
        // Sent even when another holder already subscribed: it is idempotent on the broker, and it
        // repairs a first attempt that failed.
        if (c != null && c.isConnected) {
            withContext(Dispatchers.Default) {
                runCatching { ioMutex.withLock { c.subscribe(topic, qos) } }
                    .onFailure { println("[VacLocal] MQTT subscribe $topic failed (will retry on reconnect): ${it.message}") }
            }
        }
    }

    suspend fun unsubscribe(topic: String) {
        val c = mutex.withLock {
            val existing = subscriptions[topic] ?: return
            if (--existing.holders > 0) return
            subscriptions.remove(topic)
            conn
        }
        if (c != null && c.isConnected) {
            withContext(Dispatchers.Default) { runCatching { ioMutex.withLock { c.unsubscribe(topic) } } }
        }
    }

    suspend fun publish(topic: String, payload: ByteArray, qos: Int = QOS_AT_LEAST_ONCE, retain: Boolean = false) {
        val c = mutex.withLock { conn }
        if (c == null || !c.isConnected) throw MqttClientException("MQTT not connected (reconnecting)")
        withContext(Dispatchers.Default) {
            try {
                ioMutex.withLock { c.publish(topic, payload, qos, retain) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                throw MqttClientException("MQTT publish failed: ${e.message}", e)
            }
        }
    }


    suspend fun forceReconnect() {
        val (c, d) = mutex.withLock {
            if (!_connected.value) return
            conn to currentDisconnect
        }
        if (d == null) return
        println("[VacLocal] MQTT forceReconnect — tearing down session to reconnect")
        runCatching { withContext(Dispatchers.Default) { c?.close() } }
        if (!d.isCompleted) d.complete(Unit)
    }

    suspend fun disconnect() {
        val c = mutex.withLock {
            closed = true
            subscriptions.clear()
            val existing = conn
            conn = null
            currentDisconnect = null
            existing
        }
        mutex.withLock { superviseJob }?.cancel()
        mutex.withLock { superviseJob = null }
        runCatching { c?.close() }
        _connected.value = false
    }

    private suspend fun superviseLoop() {
        var backoffMs = INITIAL_BACKOFF_MS
        var failedAttempts = 0
        var preferWebSocket = false
        var endpoint = ""
        var webSocketFailures = 0
        while (coroutineContext.isActive && !isClosed()) {
            // Re-read every iteration: credentials can be swapped out between attempts, and the
            // old code captured them once so a refreshed session could never take effect.
            val p = mutex.withLock { params } ?: return
            val hintTransport = transport as? MqttWebSocketTransport
            if (failedAttempts == 0 && p.creds.webSocketUrl != null && hintTransport != null) {
                // Split DNS tells us where we are: a private answer means home, a public one means away.
                val atHome = withContext(Dispatchers.Default) {
                    withTimeoutOrNull(HOME_LOOKUP_MS) { hintTransport.resolvesToPrivateAddress(p.creds.host) }
                }
                if (atHome != null) preferWebSocket = !atHome
                println("[VacLocal] MQTT route: home=$atHome -> ${if (preferWebSocket) "websocket" else "direct"}")
            }
            val connectResult = CompletableDeferred<Unit>()
            val disconnected = CompletableDeferred<Unit>()
            var c: MqttNativeConnection? = null
            var switchedRoute = false
            var viaWebSocket = false
            try {
                // transport.open may open the socket synchronously and throw, so keep it in the try.
                val onConnected = { if (!connectResult.isCompleted) connectResult.complete(Unit) }
                val onFailed = { reason: String ->
                    if (!connectResult.isCompleted) {
                        connectResult.completeExceptionally(MqttClientException("MQTT connect failed: $reason"))
                    }
                    if (!disconnected.isCompleted) disconnected.complete(Unit)
                }
                val onMessage = { topic: String, payload: ByteArray ->
                    lastMessageMark = TimeSource.Monotonic.markNow()
                    _messages.tryEmit(MqttMessage(topic, payload))
                    Unit
                }
                val onDisconnected = {
                    if (!connectResult.isCompleted) {
                        connectResult.completeExceptionally(MqttClientException("MQTT dropped before connect"))
                    }
                    if (!disconnected.isCompleted) disconnected.complete(Unit)
                }
                val wsUrl = p.creds.webSocketUrl
                val wsTransport = transport as? MqttWebSocketTransport
                viaWebSocket = preferWebSocket && wsUrl != null && wsTransport != null
                endpoint = if (viaWebSocket) wsUrl!! else "${p.creds.host}:${p.creds.port}"
                c = if (viaWebSocket) {
                    wsTransport!!.openWebSocket(
                        wsUrl!!, extraHeaders(), p.creds.username, p.creds.password, p.clientId, p.keepAliveSeconds,
                        onConnected, onFailed, onMessage, onDisconnected,
                    )
                } else {
                    transport.open(
                        p.creds.host, p.creds.port, p.creds.tls, p.creds.username, p.creds.password,
                        p.clientId, p.keepAliveSeconds, onConnected, onFailed, onMessage, onDisconnected,
                    )
                }
                mutex.withLock {
                    conn = c
                    currentDisconnect = disconnected
                }
                // withTimeout's exception is a CancellationException and would end this loop for good.
// A direct attempt only needs a short look when the WebSocket route is the fallback.
                val timeoutMs = if (!viaWebSocket && wsUrl != null && wsTransport != null) {
                    minOf(p.connectTimeoutMs, DIRECT_PROBE_MS)
                } else {
                    p.connectTimeoutMs
                }
                withTimeoutOrNull(timeoutMs) { connectResult.await() }
                    ?: throw MqttClientException("MQTT connect timed out after $timeoutMs ms")

                _connected.value = true
                backoffMs = INITIAL_BACKOFF_MS
                failedAttempts = 0
                if (viaWebSocket) webSocketFailures = 0
                println("[VacLocal] MQTT connected via $endpoint user=${p.creds.username}")
                resubscribeAll(c)

                // The network can change under a live connection; re-check where we are now.
                val watcher = if (p.creds.webSocketUrl != null && hintTransport != null) {
                    scope.launch {
                        while (isActive) {
                            delay(homeCheckIntervalMs)
                            val atHome = withTimeoutOrNull(HOME_LOOKUP_MS) {
                                withContext(Dispatchers.Default) { hintTransport.resolvesToPrivateAddress(p.creds.host) }
                            }
                            if (atHome != null && atHome == viaWebSocket) {
                                println("[VacLocal] MQTT network changed (home=$atHome), switching route")
                                forceReconnect()
                                break
                            }
                        }
                    }
                } else {
                    null
                }
                try {
                    disconnected.await()
                } finally {
                    watcher?.cancel()
                }
                _connected.value = false
                println("[VacLocal] MQTT disconnected — will reconnect")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                _connected.value = false
                failedAttempts++
                // Away from home the direct port is unreachable; try the other route next, without waiting.
                if (p.creds.webSocketUrl != null && transport is MqttWebSocketTransport) {
                    if (!viaWebSocket) {
                        preferWebSocket = true
                        switchedRoute = true
                    } else if (++webSocketFailures >= WEBSOCKET_FAILURES_BEFORE_DIRECT) {
                        // Probably home again, where the direct port works; look once more.
                        preferWebSocket = false
                        webSocketFailures = 0
                    }
                }
                println("[VacLocal] MQTT connect attempt failed (#$failedAttempts) via $endpoint: ${e::class.simpleName}: ${e.message}")
                // The server mints a fresh session per login and rejects credentials from an
                // expired one, so repeated failures are more likely stale creds than a dead network.
                if (failedAttempts >= RELOGIN_AFTER_ATTEMPTS) {
                    val refreshed = runCatching { credentialRefresher?.invoke(failedAttempts, e) }
                        .onFailure { println("[VacLocal] MQTT credential refresh threw: ${it.message}") }
                        .getOrNull()
                    if (refreshed != null) {
                        println("[VacLocal] MQTT credentials refreshed — retrying as user=${refreshed.username}")
                        mutex.withLock { params = params?.copy(creds = refreshed) }
                        backoffMs = INITIAL_BACKOFF_MS
                        failedAttempts = 0
                    }
                }
            } finally {
                val closing = c
                mutex.withLock {
                    if (conn === closing) conn = null
                    if (currentDisconnect === disconnected) currentDisconnect = null
                }
                closing?.let { runCatching { it.close() } }
            }

            if (isClosed()) break
            // A signal left over from before this attempt must not skip the wait.
            retrySignal.tryReceive()
            if (switchedRoute) continue
            val hurried = withTimeoutOrNull(backoffMs) { retrySignal.receive() } != null
            backoffMs = if (hurried) INITIAL_BACKOFF_MS else (backoffMs * 2).coerceAtMost(MAX_BACKOFF_MS)
        }
    }

    private suspend fun resubscribeAll(c: MqttNativeConnection) {
        val subs = mutex.withLock { subscriptions.mapValues { it.value.qos } }
        if (subs.isEmpty()) return
        for ((topic, qos) in subs) {
            runCatching { ioMutex.withLock { c.subscribe(topic, qos) } }
                .onFailure { println("[VacLocal] MQTT resubscribe $topic failed: ${it.message}") }
        }
        println("[VacLocal] MQTT (re)subscribed ${subs.size} topic(s)")
    }

    private suspend fun isClosed(): Boolean = mutex.withLock { closed }

    private companion object {
        const val QOS_AT_LEAST_ONCE = 1
        const val INITIAL_BACKOFF_MS = 1_000L
        /** Consecutive reconnect failures before asking for fresh credentials. */
        const val RELOGIN_AFTER_ATTEMPTS = 3
        const val DIRECT_PROBE_MS = 2_500L
        const val HOME_LOOKUP_MS = 1_500L
        const val WEBSOCKET_FAILURES_BEFORE_DIRECT = 3
        const val MAX_BACKOFF_MS = 30_000L
    }
}
