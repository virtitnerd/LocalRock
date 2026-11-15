package com.kodraliu.localrock.shared.mqtt

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The supervisor's own clock is real time, so these wait on [MqttClient.connected], not on delays. */
class MqttClientStartTest {

    private class FakeConnection : MqttNativeConnection {
        override val isConnected: Boolean = true
        override fun subscribe(topic: String, qos: Int) {}
        override fun unsubscribe(topic: String) {}
        override fun publish(topic: String, payload: ByteArray, qos: Int, retain: Boolean) {}
        override fun close() {}
    }

    /** Fails the first [failures] opens the way an unreachable host does, then connects. */
    private class FlakyTransport(private val failures: Int) : MqttNativeTransport {
        var opens = 0
        override fun open(
            host: String,
            port: Int,
            tls: Boolean,
            username: String,
            password: String,
            clientId: String,
            keepAliveSeconds: Int,
            onConnected: () -> Unit,
            onFailed: (reason: String) -> Unit,
            onMessage: (topic: String, payload: ByteArray) -> Unit,
            onDisconnected: () -> Unit,
        ): MqttNativeConnection {
            if (++opens <= failures) throw RuntimeException("Unable to resolve host")
            onConnected()
            return FakeConnection()
        }
    }

    /** Never answers the first [silent] opens, like a broker that accepts the socket and stalls. */
    private class StallingTransport(private val silent: Int) : MqttNativeTransport {
        var opens = 0
        override fun open(
            host: String,
            port: Int,
            tls: Boolean,
            username: String,
            password: String,
            clientId: String,
            keepAliveSeconds: Int,
            onConnected: () -> Unit,
            onFailed: (reason: String) -> Unit,
            onMessage: (topic: String, payload: ByteArray) -> Unit,
            onDisconnected: () -> Unit,
        ): MqttNativeConnection {
            if (++opens > silent) onConnected()
            return FakeConnection()
        }
    }

    /** The direct port fails, as it does away from home; the WebSocket route connects. */
    private class AwayTransport : MqttNativeTransport, MqttWebSocketTransport {
        var directOpens = 0
        var webSocketOpens = 0
        var dropFirstWebSocket = false
        var atHome: Boolean? = null
        var directWorks = false
        var webSocketUrl: String? = null
        override fun open(
            host: String,
            port: Int,
            tls: Boolean,
            username: String,
            password: String,
            clientId: String,
            keepAliveSeconds: Int,
            onConnected: () -> Unit,
            onFailed: (reason: String) -> Unit,
            onMessage: (topic: String, payload: ByteArray) -> Unit,
            onDisconnected: () -> Unit,
        ): MqttNativeConnection {
            directOpens++
            if (directWorks) {
                onConnected()
                return FakeConnection()
            }
            throw RuntimeException("connect timed out")
        }

        override fun resolvesToPrivateAddress(host: String): Boolean? = atHome

        override fun openWebSocket(
            url: String,
            username: String,
            password: String,
            clientId: String,
            keepAliveSeconds: Int,
            onConnected: () -> Unit,
            onFailed: (reason: String) -> Unit,
            onMessage: (topic: String, payload: ByteArray) -> Unit,
            onDisconnected: () -> Unit,
        ): MqttNativeConnection {
            webSocketUrl = url
            webSocketOpens++
            onConnected()
            if (dropFirstWebSocket && webSocketOpens == 1) onDisconnected()
            return FakeConnection()
        }
    }

    private val creds = MqttCreds(host = "localhost", port = 8883, tls = true, username = "u", password = "p")

    @Test
    fun a_failed_first_attempt_is_retried_until_the_link_is_up() = runTest {
        val transport = FlakyTransport(failures = 1)
        val client = MqttClient(transport)
        try {
            // Returns although the first attempt fails; it used to throw and stop trying.
            client.start(creds, clientId = "test")
            client.connected.first { it }
            assertEquals(2, transport.opens)
        } finally {
            client.disconnect()
        }
    }

    @Test
    fun starting_twice_keeps_the_one_connection() = runTest {
        val transport = FlakyTransport(failures = 0)
        val client = MqttClient(transport)
        try {
            client.start(creds, clientId = "test")
            client.start(creds, clientId = "test")
            client.connected.first { it }
            assertEquals(1, transport.opens)
        } finally {
            client.disconnect()
        }
    }

    @Test
    fun the_client_can_be_started_again_after_a_disconnect() = runTest {
        val transport = FlakyTransport(failures = 0)
        val client = MqttClient(transport)
        try {
            client.start(creds, clientId = "first")
            client.connected.first { it }
            client.disconnect()
            assertFalse(client.connected.value)

            client.start(creds, clientId = "second")
            client.connected.first { it }
            assertTrue(client.connected.value)
            assertEquals(2, transport.opens)
        } finally {
            client.disconnect()
        }
    }

    @Test
    fun a_connect_that_times_out_is_retried() = runTest {
        val transport = StallingTransport(silent = 1)
        val client = MqttClient(transport)
        try {
            // A timed-out attempt used to end the supervisor, leaving the app offline until restarted.
            client.start(creds, clientId = "test", connectTimeoutMs = 200)
            client.connected.first { it }
            assertEquals(2, transport.opens)
        } finally {
            client.disconnect()
        }
    }

    @Test
    fun an_unreachable_direct_port_falls_back_to_the_websocket() = runTest {
        val transport = AwayTransport()
        val client = MqttClient(transport)
        try {
            client.start(creds.copy(webSocketUrl = "wss://api.example.com/mqtt"), clientId = "test")
            client.connected.first { it }
            assertEquals(1, transport.directOpens)
            assertEquals("wss://api.example.com/mqtt", transport.webSocketUrl)
        } finally {
            client.disconnect()
        }
    }

    @Test
    fun the_websocket_url_follows_the_api_base_url() {
        assertEquals("wss://api-r.example.io/mqtt", mqttWebSocketUrl("https://api-r.example.io"))
        assertEquals("wss://api-r.example.io:555/mqtt", mqttWebSocketUrl("https://api-r.example.io:555/"))
        assertEquals("ws://10.0.0.5:8080/mqtt", mqttWebSocketUrl("http://10.0.0.5:8080"))
        assertEquals(null, mqttWebSocketUrl(""))
    }

    @Test
    fun a_dropped_websocket_is_reopened_without_going_back_to_the_direct_port() = runBlocking {
        val transport = AwayTransport().also { it.dropFirstWebSocket = true }
        val client = MqttClient(transport)
        try {
            client.start(creds.copy(webSocketUrl = "wss://api.example.com/mqtt"), clientId = "test")
            withTimeout(10_000) { while (transport.webSocketOpens < 2) delay(20) }
            assertEquals(1, transport.directOpens)
        } finally {
            client.disconnect()
        }
    }

    @Test
    fun a_public_dns_answer_goes_straight_to_the_websocket() = runTest {
        val transport = AwayTransport().also { it.atHome = false }
        val client = MqttClient(transport)
        try {
            client.start(creds.copy(webSocketUrl = "wss://api.example.com/mqtt"), clientId = "test")
            client.connected.first { it }
            assertEquals(0, transport.directOpens)
        } finally {
            client.disconnect()
        }
    }

    @Test
    fun private_addresses_are_recognised() {
        fun ip(vararg v: Int) = ByteArray(v.size) { v[it].toByte() }
        assertTrue(isPrivateNetworkAddress(ip(10, 0, 30, 15)))
        assertTrue(isPrivateNetworkAddress(ip(192, 168, 1, 4)))
        assertTrue(isPrivateNetworkAddress(ip(172, 20, 0, 1)))
        assertTrue(isPrivateNetworkAddress(ip(100, 101, 2, 3)))
        assertFalse(isPrivateNetworkAddress(ip(172, 32, 0, 1)))
        assertFalse(isPrivateNetworkAddress(ip(104, 21, 46, 237)))
        assertTrue(isPrivateNetworkAddress(ip(0xFD, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1)))
        assertFalse(isPrivateNetworkAddress(ip(0x26, 0x06, 0x47, 0, 0x30, 0x37, 0, 0, 0, 0, 0, 0, 0xAC, 0x43, 0x8F, 0x29)))
    }

    @Test
    fun coming_home_while_on_the_websocket_switches_to_the_direct_port() = runBlocking {
        val transport = AwayTransport().also { it.atHome = false; it.directWorks = true }
        val client = MqttClient(transport, homeCheckIntervalMs = 50)
        try {
            client.start(creds.copy(webSocketUrl = "wss://api.example.com/mqtt"), clientId = "test")
            withTimeout(10_000) { while (transport.webSocketOpens < 1) delay(20) }
            transport.atHome = true
            withTimeout(10_000) { while (transport.directOpens < 1) delay(20) }
            assertEquals(1, transport.webSocketOpens)
        } finally {
            client.disconnect()
        }
    }
}
