package com.kodraliu.localrock.shared.http

import com.kodraliu.localrock.shared.auth.HawkCreds
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.DefaultRequest
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import io.ktor.http.Headers
import io.ktor.http.takeFrom
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CustomHeadersPluginTest {

    private val server = "https://robot.example.com"
    private val list = listOf(ServerHeader("CF-Access-Client-Id", "id"), ServerHeader("CF-Access-Client-Secret", "sec"))

    private fun client(seen: MutableList<Headers>): HttpClient {
        val config = HttpClientConfig(
            baseUrlProvider = { server },
            credsProvider = { HawkCreds("rrid", "sess", "key") },
            customHeadersFor = { url -> ServerHeaders.headersFor(list, url, server) },
        )
        return HttpClient(MockEngine { request ->
            seen += request.headers
            respond("{}")
        }) {
            install(HawkAuth) { credsProvider = config.credsProvider }
            install(CustomHeaders) {
                headersFor = config.customHeadersFor
                names = { list.map { it.name }.toSet() }
            }
            install(DefaultRequest) { url.takeFrom(server) }
        }
    }

    @Test
    fun server_host_requests_carry_the_headers() = runTest {
        val seen = mutableListOf<Headers>()
        client(seen).get("/user/homes/1")
        assertEquals("id", seen.single()["CF-Access-Client-Id"])
        assertEquals("sec", seen.single()["CF-Access-Client-Secret"])
    }

    @Test
    fun other_hosts_get_nothing() = runTest {
        val seen = mutableListOf<Headers>()
        client(seen).get("https://other.example.org/x")
        assertNull(seen.single()["CF-Access-Client-Id"])
        assertNull(seen.single()["CF-Access-Client-Secret"])
    }

    @Test
    fun authorization_is_not_overwritten() = runTest {
        val seen = mutableListOf<Headers>()
        val c = HttpClient(MockEngine { request -> seen += request.headers; respond("{}") }) {
            install(CustomHeaders) { headersFor = { mapOf(HttpHeaders.Authorization to "evil", "X-A" to "1") } }
        }
        c.get("https://robot.example.com/x") { header(HttpHeaders.Authorization, "Hawk real") }
        assertEquals(listOf("Hawk real"), seen.single().getAll(HttpHeaders.Authorization))
        assertEquals("1", seen.single()["X-A"])
    }

    @Test
    fun a_redirect_to_another_host_does_not_carry_the_headers() = runTest {
        val seen = mutableListOf<Pair<String, Headers>>()
        val c = HttpClient(MockEngine { request ->
            seen += request.url.host to request.headers
            if (request.url.host == "robot.example.com") {
                respond("", io.ktor.http.HttpStatusCode.Found, Headers.build { append(HttpHeaders.Location, "https://other.example.org/x") })
            } else {
                respond("{}")
            }
        }) {
            install(CustomHeaders) {
                headersFor = { url -> ServerHeaders.headersFor(list, url, server) }
                names = { list.map { it.name }.toSet() }
            }
        }
        c.get("$server/start")
        assertEquals("id", seen.first { it.first == "robot.example.com" }.second["CF-Access-Client-Id"])
        assertNull(seen.first { it.first == "other.example.org" }.second["CF-Access-Client-Id"])
        assertNull(seen.first { it.first == "other.example.org" }.second["CF-Access-Client-Secret"])
    }
}
