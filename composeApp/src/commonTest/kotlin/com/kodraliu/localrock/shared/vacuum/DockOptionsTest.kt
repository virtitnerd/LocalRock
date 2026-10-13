package com.kodraliu.localrock.shared.vacuum

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DockOptionsTest {

    // Read from a real Qrevo's app_get_init_status.
    private val qrevo = DeviceFeatures.fromInitStatus(2247397454282751L, "42BA8F5C7EDAFFFE")

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
        for (list in listOf(DockOptions.autoEmpty, DockOptions.washMode, DockOptions.washTrips, DockOptions.washMinutes)) {
            assertEquals(list.size, list.map { it.code }.toSet().size)
        }
    }

    @Test
    fun a_qrevos_wash_interval_is_minutes_not_a_trip_count() {
        assertEquals("Every 20 min", washFrequencyLabel(1200, 0))
        assertEquals("Every 15 min", washFrequencyLabel(900, 0))
        assertEquals("Every trip", washFrequencyLabel(1, 0))
        assertEquals("Every 3 trips", washFrequencyLabel(3, 0))
        assertEquals("Unknown", washFrequencyLabel(null, 0))
    }

    @Test
    fun a_wash_interval_in_the_middle_is_shown_as_it_is() {
        assertEquals("Every 90 s", washFrequencyLabel(90, 0))
        assertEquals("Other (7)", washFrequencyLabel(7, 0))
    }

    @Test
    fun smart_wash_is_not_labelled_as_an_interval() {
        assertEquals("Smart wash", washFrequencyLabel(1200, 1))
    }

    @Test
    fun the_picker_matches_the_unit_the_dock_reports() {
        assertEquals(DockOptions.washMinutes, washIntervalChoices(1200))
        assertEquals(DockOptions.washTrips, washIntervalChoices(2))
        assertEquals(DockOptions.washTrips, washIntervalChoices(null))
        assertEquals(1200, washIntervalChoices(1200).first { it.label == "Every 20 min" }.code)
    }

    @Test
    fun the_frequency_is_only_written_over_a_value_that_is_understood() {
        assertEquals(true, washFrequencyEditable(1200, 0))
        assertEquals(true, washFrequencyEditable(2, 0))
        assertEquals(false, washFrequencyEditable(null, 0))
        assertEquals(false, washFrequencyEditable(1200, 1))
        assertEquals(false, washFrequencyEditable(0, 0))
        assertEquals(false, washFrequencyEditable(7, 0))
    }

    @Test
    fun drying_time_reads_in_hours_or_minutes() {
        assertEquals("2 h", dryTimeLabel(7200))
        assertEquals("90 min", dryTimeLabel(5400))
        assertEquals("45 s", dryTimeLabel(45))
    }

    @Test
    fun a_qrevo_is_offered_smart_washing_and_not_super_intensive() {
        val labels = washModeChoices(qrevo).map { it.label }

        assertEquals(listOf("Light", "Standard", "Intensive", "Smart"), labels)
    }

    @Test
    fun super_intensive_is_offered_only_where_the_robot_has_it_without_smart() {
        val superDeepOnly = qrevo.copy(dirtyReplenishClean = false, superDeepWash = true)
        val neither = qrevo.copy(dirtyReplenishClean = false, superDeepWash = false)

        assertEquals(listOf("Light", "Standard", "Intensive", "Super intensive"), washModeChoices(superDeepOnly).map { it.label })
        assertEquals(listOf("Light", "Standard", "Intensive"), washModeChoices(neither).map { it.label })
    }

    @Test
    fun before_the_features_are_read_every_wash_mode_is_still_offered() {
        assertEquals(5, washModeChoices(null).size)
    }

    @Test
    fun the_features_of_a_real_qrevo_decode_from_its_flags() {
        assertEquals(true, qrevo.drying)
        assertEquals(true, qrevo.valleyElectricity)
        assertEquals(true, qrevo.superDeepWash)
        assertEquals(true, qrevo.dirtyReplenishClean)
        assertEquals(true, qrevo.dustCollectionSetting)
        assertEquals(true, qrevo.hotWashTowel)
        assertEquals(true, qrevo.carpetDeepClean)
    }

    @Test
    fun a_robot_reporting_no_flags_supports_nothing() {
        val none = DeviceFeatures.fromInitStatus(0L, "")

        assertEquals(false, none.drying)
        assertEquals(false, none.valleyElectricity)
        assertEquals(false, none.dirtyReplenishClean)
        assertEquals(false, none.dustCollectionSetting)
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
