package com.kodraliu.localrock.shared.http

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ServerHeadersTest {

    private fun h(name: String, value: String = "v") = ServerHeader(name, value)

    @Test
    fun cloudflare_access_headers_are_valid() {
        assertNull(ServerHeaders.validate(listOf(h("CF-Access-Client-Id", "abc.access"), h("CF-Access-Client-Secret", "s3cret"))))
    }

    @Test
    fun reserved_names_are_refused_in_any_case() {
        for (n in listOf("Authorization", "host", "CONTENT-LENGTH", "Content-Type", "Upgrade", "Connection", "user-agent", "Sec-WebSocket-Key", "sec-websocket-protocol")) {
            assertNotNull(ServerHeaders.validateOne(h(n)), n)
        }
    }

    @Test
    fun bad_names_and_values_are_refused() {
        assertNotNull(ServerHeaders.validateOne(h("")))
        assertNotNull(ServerHeaders.validateOne(h("Has Space")))
        assertNotNull(ServerHeaders.validateOne(h("Bad:Name")))
        assertNotNull(ServerHeaders.validateOne(h("X-A\r\nB")))
        assertNotNull(ServerHeaders.validateOne(h("X-A", "one\ntwo")))
        assertNotNull(ServerHeaders.validateOne(h("X-A", "a\rb")))
        assertNotNull(ServerHeaders.validateOne(h("X-A", "x".repeat(2049))))
        assertNull(ServerHeaders.validateOne(h("X-A", "x".repeat(2048))))
    }

    @Test
    fun at_most_ten_headers() {
        assertNull(ServerHeaders.validate((1..10).map { h("X-$it") }))
        assertNotNull(ServerHeaders.validate((1..11).map { h("X-$it") }))
    }

    @Test
    fun json_round_trip_keeps_order_and_drops_invalid_stored_data() {
        val list = listOf(h("B-One", "2"), h("A-Two", "1"))
        assertEquals(list, ServerHeaders.decode(ServerHeaders.encode(list)))
        assertEquals(emptyList(), ServerHeaders.decode("not json"))
        assertEquals(emptyList(), ServerHeaders.decode(ServerHeaders.encode(listOf(h("Authorization")))))
        assertEquals(emptyList(), ServerHeaders.decode(null))
    }

    @Test
    fun host_check_matches_only_the_server_host() {
        val base = "https://Robot.Example.com"
        assertTrue(ServerHeaders.isServerHost("https://robot.example.com/api/x", base))
        assertTrue(ServerHeaders.isServerHost("wss://robot.example.com/mqtt", base))
        assertTrue(ServerHeaders.isServerHost("https://robot.example.com:8443/x", base))
        assertFalse(ServerHeaders.isServerHost("https://other.example.com/x", base))
        assertFalse(ServerHeaders.isServerHost("https://robot.example.com.evil.net/x", base))
        assertFalse(ServerHeaders.isServerHost("https://evil.net/robot.example.com", base))
        assertFalse(ServerHeaders.isServerHost("https://robot.example.com/x", null))
        assertFalse(ServerHeaders.isServerHost("https://robot.example.com/x", ""))
    }

    @Test
    fun headers_for_returns_nothing_off_host() {
        val list = listOf(h("CF-Access-Client-Id", "id"))
        assertEquals(mapOf("CF-Access-Client-Id" to "id"), ServerHeaders.headersFor(list, "https://a.com/x", "https://a.com"))
        assertEquals(emptyMap(), ServerHeaders.headersFor(list, "https://b.com/x", "https://a.com"))
    }
}
