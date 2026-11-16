package com.kodraliu.localrock.shared.vacuum.camera

import com.kodraliu.localrock.shared.auth.HawkCreds
import com.kodraliu.localrock.shared.webrtc.RtcIceServer
import com.kodraliu.localrock.shared.webrtc.VideoExperiments
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TurnRouteTest {
    @Test
    fun https_becomes_wss_and_http_does_too() {
        assertEquals("wss://srv.example.com/turn", turnSocketUrl("https://srv.example.com"))
        assertEquals("wss://srv.example.com/turn", turnSocketUrl("https://srv.example.com/"))
        assertEquals("wss://srv.example.com:8443/turn", turnSocketUrl("https://srv.example.com:8443/"))
        assertEquals("wss://srv.example.com/turn", turnSocketUrl("http://srv.example.com"))
    }

    @Test
    fun an_unusable_base_gives_no_url() {
        assertNull(turnSocketUrl(null))
        assertNull(turnSocketUrl(""))
        assertNull(turnSocketUrl("srv.example.com"))
        assertNull(turnSocketUrl("https://"))
    }

    @Test
    fun the_host_drops_scheme_port_and_path() {
        assertEquals("srv.example.com", serverHost("https://srv.example.com:8443/api"))
        assertEquals("srv.example.com", serverHost("https://srv.example.com"))
        assertNull(serverHost(null))
    }

    @Test
    fun the_rewritten_relay_keeps_its_login() {
        val out = tunnelledIceServer(RtcIceServer("turn:1.2.3.4:3478", "u", "p"), 40123)
        assertEquals("turn:127.0.0.1:40123?transport=tcp", out.url)
        assertEquals("u", out.username)
        assertEquals("p", out.password)
    }

    @Test
    fun route_decision_table() {
        assertFalse(shouldTunnelTurn(atHome = true, forced = false))
        assertFalse(shouldTunnelTurn(atHome = null, forced = false))
        assertTrue(shouldTunnelTurn(atHome = false, forced = false))
        assertTrue(shouldTunnelTurn(atHome = true, forced = true))
        assertTrue(shouldTunnelTurn(atHome = null, forced = true))
    }

    @Test
    fun socket_headers_sign_the_turn_path_and_carry_a_user_agent() {
        val creds = HawkCreds(id = "id", session = "s", key = "k")
        val h = turnSocketHeaders(creds, 1700000000L, "abc")
        assertTrue(h.getValue("Authorization").startsWith("Hawk id=\"id\",s=\"s\",ts=\"1700000000\",nonce=\"abc\",mac=\""))
        assertTrue(h.getValue("User-Agent").startsWith("LocalRock/"))
        assertTrue(turnSocketHeaders(null, 1L, "n").isEmpty())
    }

    @Test
    fun the_experiment_is_off_by_default_and_survives_encoding() {
        assertFalse(VideoExperiments().cameraOverWebSocket)
        assertTrue(VideoExperiments().isDefault)
        val on = VideoExperiments(cameraOverWebSocket = true)
        assertTrue(VideoExperiments.decode(on.encode()).cameraOverWebSocket)
    }

    @Test
    fun relay_only_is_off_by_default_and_survives_a_round_trip() {
        assertFalse(VideoExperiments().relayOnly)
        assertFalse(VideoExperiments.decode(VideoExperiments().encode()).relayOnly)
        val on = VideoExperiments(relayOnly = true, cameraOverWebSocket = true)
        val back = VideoExperiments.decode(on.encode())
        assertTrue(back.relayOnly)
        assertTrue(back.cameraOverWebSocket)
    }
}
