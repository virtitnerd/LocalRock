package com.kodraliu.localrock.ui.vacuum

import com.kodraliu.localrock.ui.strings.RemoteStrings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RemoteJoystickTest {
    @Test
    fun timingMatchesTheCameraJoystick() {
        assertEquals(25L, JoystickDrive.POLL_MS)
        assertEquals(200L, JoystickDrive.REPEAT_MS)
        assertEquals(1500, JoystickDrive.MOVE_MS)
        assertEquals(0.3, JoystickDrive.MAX_SPEED)
        assertEquals(0.4, JoystickDrive.MAX_TURN)
    }

    @Test
    fun deadZoneSendsNothing() {
        assertNull(JoystickDrive.command(0f, 0f))
        assertNull(JoystickDrive.command(0.1f, -0.14f))
    }

    @Test
    fun fullUpDrivesForwardAndSidewaysTurns() {
        val (fwd, straight) = JoystickDrive.command(0f, -1f)!!
        assertEquals(0.3, fwd, 1e-6)
        assertEquals(0.0, straight, 1e-9)
        val (v, o) = JoystickDrive.command(1f, 0f)!!
        assertEquals(0.0, v, 1e-9)
        assertEquals(-0.4, o, 1e-9)
        val (back, left) = JoystickDrive.command(-0.5f, 1f)!!
        assertEquals(-0.3, back, 1e-6)
        assertEquals(0.2, left, 1e-6)
    }

    @Test
    fun oneAxisInsideTheDeadZoneIsZeroed() {
        val (v, o) = JoystickDrive.command(0.1f, -1f)!!
        assertEquals(0.3, v, 1e-9)
        assertEquals(0.0, o, 1e-9)
    }

    @Test
    fun changeNeedsOnePercentOfFullScale() {
        assertFalse(JoystickDrive.changed(0.3, 0.0, 0.3, 0.0))
        assertFalse(JoystickDrive.changed(0.302, 0.0, 0.3, 0.0))
        assertTrue(JoystickDrive.changed(0.304, 0.0, 0.3, 0.0))
        assertTrue(JoystickDrive.changed(0.3, 0.005, 0.3, 0.0))
    }

    @Test
    fun buttonsKeepTodaysSpeeds() {
        assertEquals(0.3 to 0.0, RemoteButtons.FORWARD)
        assertEquals(-0.3 to 0.0, RemoteButtons.BACKWARD)
        assertEquals(0.0 to 0.3, RemoteButtons.LEFT)
        assertEquals(0.0 to -0.3, RemoteButtons.RIGHT)
        assertEquals(1500, RemoteButtons.MOVE_MS)
    }

    @Test
    fun segmentIsJoystickThenButtons() {
        assertEquals(listOf(RemoteStrings.JOYSTICK, RemoteStrings.BUTTONS), RemoteMode.entries.map { it.label })
    }
}
