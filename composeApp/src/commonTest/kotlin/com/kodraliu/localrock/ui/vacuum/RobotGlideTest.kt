package com.kodraliu.localrock.ui.vacuum

import com.kodraliu.localrock.shared.vacuum.map.ParsedMapPoint
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RobotGlideTest {
    @Test
    fun glides_position_in_a_straight_line() {
        val p = glide(ParsedMapPoint(1000, 2000, 0), ParsedMapPoint(2000, 1000, 0), 0.5f)
        assertEquals(1500, p.x)
        assertEquals(1500, p.y)
    }

    @Test
    fun turns_the_short_way_across_zero() {
        assertEquals(355, glide(ParsedMapPoint(0, 0, 350), ParsedMapPoint(0, 0, 10), 0.25f).angle.mod(360))
        assertEquals(0, glide(ParsedMapPoint(0, 0, 10), ParsedMapPoint(0, 0, 350), 0.5f).angle.mod(360))
    }

    @Test
    fun ends_on_the_new_fix() {
        val to = ParsedMapPoint(1234, 4321, 90)
        assertEquals(to, glide(ParsedMapPoint(0, 0, 270), to, 1f).let { it.copy(angle = it.angle.mod(360)) })
    }

    @Test
    fun the_glide_takes_about_as_long_as_the_fixes_were_apart() {
        assertEquals(3_000L, glideDurationMs(null))
        assertEquals(2_500L, glideDurationMs(2_500L))
        assertEquals(600L, glideDurationMs(50L))
        assertEquals(6_000L, glideDurationMs(60_000L))
    }

    @Test
    fun a_far_move_is_a_jump_not_a_drive() {
        assertFalse(isJump(ParsedMapPoint(0, 0), ParsedMapPoint(1000, 1000)))
        assertTrue(isJump(ParsedMapPoint(0, 0), ParsedMapPoint(5000, 0)))
    }
}
