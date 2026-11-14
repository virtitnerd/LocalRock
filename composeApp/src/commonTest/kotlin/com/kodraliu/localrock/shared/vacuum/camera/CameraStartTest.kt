package com.kodraliu.localrock.shared.vacuum.camera

import com.kodraliu.localrock.shared.vacuum.homesecPasswordParams
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class CameraStartTest {
    private fun answer(text: String) = Json.parseToJsonElement(text)

    @Test
    fun a_retry_answer_is_not_a_relay() {
        assertNull(CameraLiveSession.turnServerFrom(answer("""{"url":"retry"}""")))
        assertNull(CameraLiveSession.turnServerFrom(answer("""[{"url":""}]""")))
        assertNull(CameraLiveSession.turnServerFrom(answer("""{}""")))
        assertNull(CameraLiveSession.turnServerFrom(null))
    }

    @Test
    fun a_real_answer_gives_the_relay_with_its_login() {
        val server = assertNotNull(
            CameraLiveSession.turnServerFrom(answer("""[{"url":"turn:1.2.3.4:3478","user":"u","pwd":"p"}]""")),
        )
        assertEquals("turn:1.2.3.4:3478", server.url)
        assertEquals("u", server.username)
        assertEquals("p", server.password)
    }

    @Test
    fun the_pin_body_matches_the_official_one() {
        assertEquals(
            """{"enable_password":1,"new_password":"abc"}""",
            homesecPasswordParams("abc").toString(),
        )
        assertEquals(
            """{"enable_password":1,"old_password":"old","new_password":"abc"}""",
            homesecPasswordParams("abc", "old").toString(),
        )
    }
}
