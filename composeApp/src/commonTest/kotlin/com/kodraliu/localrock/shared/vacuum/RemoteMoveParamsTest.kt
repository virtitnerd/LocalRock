package com.kodraliu.localrock.shared.vacuum

import kotlin.test.Test
import kotlin.test.assertEquals

class RemoteMoveParamsTest {
    @Test
    fun a_move_carries_the_sequence_number() {
        assertEquals(
            """[{"velocity":0.2,"omega":-0.5,"seqnum":3,"duration":700}]""",
            rcMoveParams(0.2, -0.5, 700, 3).toString(),
        )
    }

    @Test
    fun without_a_sequence_number_the_body_is_the_old_one() {
        assertEquals(
            """[{"velocity":0.0,"omega":0.0,"duration":200}]""",
            rcMoveParams(0.0, 0.0, 200, null).toString(),
        )
    }

    @Test
    fun action_locked_is_the_robot_leaving_remote_control() {
        fun error(text: String) = kotlinx.serialization.json.Json.parseToJsonElement(text) as kotlinx.serialization.json.JsonObject
        assertEquals(true, isActionLocked(error("""{"code":-10003,"message":"action locked"}""")))
        assertEquals(false, isActionLocked(error("""{"code":-10005,"message":"x"}""")))
        assertEquals(false, isActionLocked(null))
    }
}
