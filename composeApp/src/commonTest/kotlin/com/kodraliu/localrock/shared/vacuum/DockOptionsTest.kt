package com.kodraliu.localrock.shared.vacuum

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DockOptionsTest {

    @Test
    fun the_codes_a_qrevo_dock_reports_have_names() {
        // Seen from a real Qrevo-class dock (dock_type 9): auto-empty answered 4, wash mode 10.
        assertEquals("Max", DockOptions.autoEmpty.labelFor(4))
        assertEquals("Smart", DockOptions.washMode.labelFor(10))
    }

    @Test
    fun codes_the_app_always_knew_keep_their_names() {
        assertEquals("Smart", DockOptions.autoEmpty.labelFor(0))
        assertEquals("Light", DockOptions.washMode.labelFor(0))
        assertEquals("Standard", DockOptions.washMode.labelFor(1))
        assertEquals("Intensive", DockOptions.washMode.labelFor(2))
    }

    @Test
    fun an_unknown_code_is_shown_not_dropped() {
        assertEquals("Other (7)", DockOptions.autoEmpty.labelFor(7))
        assertEquals("Other (99)", DockOptions.washMode.labelFor(99))
    }

    @Test
    fun codes_are_unique_so_a_selection_matches_exactly_one_option() {
        for (list in listOf(DockOptions.autoEmpty, DockOptions.washMode)) {
            assertEquals(list.size, list.map { it.code }.toSet().size)
        }
    }

    @Test
    fun a_qrevos_wash_interval_is_not_mistaken_for_every_trip() {
        assertEquals("Every trip", washFrequencyLabel(washFreq = 0, washInterval = 1))
        assertEquals("Every 3 trips", washFrequencyLabel(washFreq = 2, washInterval = 3))
        assertEquals("Other (1200)", washFrequencyLabel(washFreq = 0, washInterval = 1200))
        // Before anything has been read, fall back to the default rather than "Other".
        assertEquals("Every trip", washFrequencyLabel(washFreq = 0, washInterval = null))
    }

    @Test
    fun the_trip_count_picker_only_writes_where_the_interval_is_a_trip_count() {
        assertEquals(true, washFrequencyEditable(null))
        assertEquals(true, washFrequencyEditable(2))
        assertEquals(false, washFrequencyEditable(1200))
        assertEquals(false, washFrequencyEditable(0))
    }

    @Test
    fun answers_are_read_whether_the_robot_wraps_them_in_a_list_or_not() {
        // Both shapes occur; python-roborock unwraps either.
        val bare = Json.parseToJsonElement("""{"mode":4}""").asAnswerObject()
        val wrapped = Json.parseToJsonElement("""[{"mode":4}]""").asAnswerObject()

        assertEquals(4, bare?.get("mode")?.jsonPrimitive?.intOrNull)
        assertEquals(4, wrapped?.get("mode")?.jsonPrimitive?.intOrNull)
    }

    @Test
    fun an_answer_that_is_not_an_object_reads_as_nothing_instead_of_throwing() {
        assertNull(Json.parseToJsonElement("[]").asAnswerObject())
        assertNull(Json.parseToJsonElement("""["ok"]""").asAnswerObject())
        assertNull(Json.parseToJsonElement("5").asAnswerObject())
        assertNull((null as JsonElement?).asAnswerObject())
    }
}
