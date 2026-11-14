package com.kodraliu.localrock.ui.vacuum

import com.kodraliu.localrock.shared.vacuum.BetaFeatureDef
import com.kodraliu.localrock.shared.vacuum.BetaFeatureDefs
import com.kodraliu.localrock.shared.vacuum.BetaFeatureState
import com.kodraliu.localrock.shared.vacuum.CleanRecord
import com.kodraliu.localrock.shared.vacuum.CleanTimer
import com.kodraliu.localrock.shared.vacuum.ConsumableStatus
import com.kodraliu.localrock.shared.vacuum.FeatureSection
import com.kodraliu.localrock.shared.vacuum.TimerDate
import com.kodraliu.localrock.ui.strings.SettingsStrings
import kotlinx.datetime.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SettingsLogicTest {
    // 2026-11-13 was a Friday (weekday 5 in CleanTimer's 0 = Sunday numbering).
    private val friday1000 = LocalDateTime(2026, 11, 13, 10, 0)

    private fun timer(id: String, h: Int, m: Int, days: Set<Int> = emptySet(), enabled: Boolean = true, date: TimerDate? = null) =
        CleanTimer(id, enabled, h, m, days, date = date)

    @Test
    fun the_next_cleaning_is_the_earliest_enabled_timer_still_ahead_today() {
        val timers = listOf(
            timer("past", 9, 0),
            timer("off", 11, 0, enabled = false),
            timer("monday", 10, 30, setOf(1)),
            timer("friday", 14, 15, setOf(5)),
            timer("daily", 12, 5),
        )
        assertEquals(12 to 5, nextCleaningToday(timers, friday1000))
        assertEquals("The next cleaning will start at 12:05 today.", schedulesDetail(12 to 5))
    }

    @Test
    fun a_one_time_timer_counts_only_on_its_day() {
        assertEquals(18 to 0, nextCleaningToday(listOf(timer("once", 18, 0, date = TimerDate(11, 13))), friday1000))
        assertNull(nextCleaningToday(listOf(timer("once", 18, 0, date = TimerDate(11, 14))), friday1000))
        assertEquals(SettingsStrings.SCHEDULES_DETAIL, schedulesDetail(null))
    }

    @Test
    fun maintenance_reads_normal_until_a_supply_runs_out() {
        assertEquals("Normal", maintenanceDetail(null))
        assertEquals("Normal", maintenanceDetail(ConsumableStatus(mainBrushWorkTime = 3600)))
        assertTrue(suppliesDue(ConsumableStatus(sensorDirtyTime = ConsumableStatus.SENSOR_LIFETIME_S)))
        assertTrue(suppliesDue(ConsumableStatus(strainerWorkTime = 200)))
        assertEquals(SettingsStrings.MAINTENANCE_DUE, maintenanceDetail(ConsumableStatus(filterWorkTime = ConsumableStatus.FILTER_LIFETIME_S + 1)))
    }

    @Test
    fun the_header_card_shows_the_newest_run_in_minutes_and_square_metres() {
        val older = CleanRecord(1000, 2000, 600, 10_000_000, 0, 1)
        val newer = CleanRecord(5000, 6000, 1801, 92_852_500, 0, 1, cleanedAreaMm2 = 122_650_000)
        assertEquals(newer, lastCleanRecord(listOf(older, newer)))
        assertEquals("31 min" to "122.6 m²", lastCleanFigures(newer))
        assertEquals("10 min" to "10 m²", lastCleanFigures(older))
        assertEquals("--" to "--", lastCleanFigures(null))
    }

    @Test
    fun carpet_cards_use_the_official_labels_and_wire_values() {
        val cards = carpetModeCards(1)
        assertEquals(listOf(1, 0, 2), cards.map { it.value })
        assertEquals(listOf("Vacuum Only", "Avoid Carpet", "Vac & Mop"), cards.map { it.title })
        assertEquals(3, carpetModeCards(3).last().value)
        assertEquals("Dynamic Lift", carpetModeCards(3).last().title)
    }

    private fun state(id: String) = BetaFeatureState(BetaFeatureDefs.first { it.id == id }, value = 1)

    @Test
    fun floor_rows_fall_into_the_official_groups_in_order() {
        val rows = listOf("pet_supplies_deep_clean", "gap_deep_clean", "cleaning_pattern", "right_brush_stretch", "corner_mop_stretch", "climbing_arms_high_sill")
            .map(::state)
        val groups = floorFeatureGroups(rows).map { g -> g.map { it.def.id } }
        assertEquals(
            listOf(
                listOf("cleaning_pattern"),
                listOf("right_brush_stretch", "corner_mop_stretch", "gap_deep_clean"),
                listOf("pet_supplies_deep_clean"),
                listOf("climbing_arms_high_sill"),
            ),
            groups,
        )
    }

    @Test
    fun floor_groups_ignore_rows_from_other_sections() {
        assertTrue(floorFeatureGroups(listOf(state("avoid_collision"), state("optimize_battery"))).isEmpty())
    }

    @Test
    fun dock_rows_join_the_official_groups() {
        assertEquals(DockGroup.WASH, dockGroupOf(BetaFeatureDefs.first { it.id == "clean_fluid_delivery" }))
        assertEquals(DockGroup.EMPTY, dockGroupOf(BetaFeatureDefs.first { it.id == "dust_collection_switch" }))
        assertEquals(DockGroup.OTHER, dockGroupOf(BetaFeatureDefs.first { it.id == "avoid_collision" }))
    }

    @Test
    fun robot_rows_split_between_more_options_and_robot_voice() {
        val rows = listOf("water_leak_check", "voice_control_led", "optimize_battery", "full_duplex", "avoid_collision").map(::state)
        assertEquals(listOf("voice_control_led"), rows.robotRows(RobotRowPlace.LIGHTS).map { it.def.id })
        assertEquals(listOf("optimize_battery"), rows.robotRows(RobotRowPlace.ADJUSTED_BATTERY).map { it.def.id })
        assertEquals(listOf("full_duplex"), rows.robotRows(RobotRowPlace.VOICE).map { it.def.id })
        assertEquals(listOf("water_leak_check"), rows.robotRows(RobotRowPlace.OTHER).map { it.def.id })
        assertFalse(rows.robotRows(RobotRowPlace.OTHER).any { it.def.id == "avoid_collision" })
    }

    @Test
    fun voice_assistant_switch_sits_on_the_voice_page_not_more_options() {
        // Built here because the voice_assistant definition lives on the advertised-features branch.
        val def = BetaFeatureDef("voice_assistant", "Voice Assistant", "", 37, "get_voice_service_switch", "set_voice_service_switch", FeatureSection.ROBOT)
        val rows = listOf(BetaFeatureState(def, value = 1), state("water_leak_check"))
        assertEquals(listOf("voice_assistant"), rows.robotRows(RobotRowPlace.VOICE_SWITCH).map { it.def.id })
        assertEquals(listOf("water_leak_check"), rows.robotRows(RobotRowPlace.OTHER).map { it.def.id })
    }

    @Test
    fun clock_text_pads_hours_and_minutes() {
        assertEquals("07:05", clockText(7, 5))
    }

    @Test
    fun robot_voice_row_shows_the_volume_level() {
        assertEquals("Volume 75%", robotVoiceDetail(75))
        assertEquals("Volume 100%", robotVoiceDetail(130))
        assertEquals("Volume", robotVoiceDetail(null))
    }
}
