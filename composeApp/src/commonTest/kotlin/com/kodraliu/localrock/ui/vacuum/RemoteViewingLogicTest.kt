package com.kodraliu.localrock.ui.vacuum

import com.kodraliu.localrock.shared.vacuum.VacuumStateCodes
import com.kodraliu.localrock.shared.vacuum.camera.AlertLevel
import com.kodraliu.localrock.ui.strings.RemoteStrings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RemoteViewingLogicTest {
    @Test
    fun setting_a_pattern_draws_it_twice() {
        val flow = PatternFlow(PatternMode.SET)
        assertEquals(RemoteStrings.DRAW_NEW, flow.initialMessage)
        assertFalse(flow.showsForgot)
        assertEquals(PatternAction.Show(RemoteStrings.PATTERN_TOO_SHORT, wrong = true), flow.onDrawn("123"))
        assertEquals(PatternAction.Show(RemoteStrings.DRAW_AGAIN), flow.onDrawn("1235"))
        assertEquals(PatternAction.Show(RemoteStrings.PATTERNS_DIFFER, wrong = true), flow.onDrawn("1236"))
        // A mismatch keeps the first pattern, as the official page does.
        assertEquals(PatternAction.Submit(null, "1235"), flow.onDrawn("1235"))
    }

    @Test
    fun changing_a_pattern_verifies_the_old_one_first() {
        val flow = PatternFlow(PatternMode.CHANGE)
        assertEquals(RemoteStrings.DRAW_EXISTING, flow.initialMessage)
        assertTrue(flow.showsForgot)
        assertEquals(PatternAction.VerifyOld("1478"), flow.onDrawn("1478"))
        assertEquals(PatternAction.Show(RemoteStrings.NEW_PASSWORD), flow.oldAccepted("1478"))
        assertFalse(flow.showsForgot)
        assertEquals(PatternAction.Show(RemoteStrings.DRAW_AGAIN), flow.onDrawn("3698"))
        assertEquals(PatternAction.Submit("1478", "3698"), flow.onDrawn("3698"))
        assertEquals("1478", flow.oldPin)
    }

    @Test
    fun alert_levels_carry_the_official_texts() {
        assertEquals(listOf("Flash", "Gentle", "Strong"), AlertLevel.entries.map { it.label() })
        assertEquals(RemoteStrings.ALERT_STRONG_DESC, AlertLevel.STRONG.description())
    }

    @Test
    fun call_volume_snaps_to_steps_of_five() {
        assertEquals(50, CallVolume.snap(51.9f))
        assertEquals(55, CallVolume.snap(53f))
        assertEquals(0, CallVolume.snap(-3f))
        assertEquals(100, CallVolume.snap(140f))
    }

    @Test
    fun only_task_states_count_as_running() {
        assertTrue(isTaskRunning(VacuumStateCodes.SEGMENT_CLEANING))
        assertTrue(isTaskRunning(VacuumStateCodes.PATROLLING))
        assertFalse(isTaskRunning(VacuumStateCodes.CHARGING))
        assertFalse(isTaskRunning(null))
    }
}
