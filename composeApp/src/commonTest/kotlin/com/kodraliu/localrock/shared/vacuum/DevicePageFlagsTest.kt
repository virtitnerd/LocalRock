package com.kodraliu.localrock.shared.vacuum

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

// new_feature_info_str read from the two test robots with app_get_init_status (2026-11-13).
private const val CURV = "00000000000202058CC57FFDA86836DD5BBFAF7F7EFEFFFF"
private const val PRO = "42BA8F5C7EDAFFFE"

class DevicePageFlagsTest {

    @Test
    fun the_curv_has_vac_followed_by_mop_with_repeat_no_pure_mop_and_halfway_docking() {
        val flags = devicePageFlags(CURV)
        assertTrue(flags.cleanThenMop)
        assertTrue(flags.cleanThenMopRepeat)
        assertFalse(flags.pureMop)
        assertTrue(flags.midwayDocking)
        assertFalse(flags.routineTabKnown)
    }

    @Test
    fun the_qrevo_pro_has_none_of_them_but_allows_pure_mop() {
        val flags = devicePageFlags(PRO)
        assertFalse(flags.cleanThenMop)
        assertFalse(flags.cleanThenMopRepeat)
        assertTrue(flags.pureMop)
        assertFalse(flags.midwayDocking)
    }

    @Test
    fun clean_then_mop_needs_bit_114_as_well_as_93() {
        // Bit 93 alone: hex digit 23 from the end, value 2.
        val only93 = "2" + "0".repeat(23)
        assertFalse(devicePageFlags(only93).cleanThenMop)
    }

    @Test
    fun capabilities_carry_the_device_flags() {
        val caps = buildCapabilities("roborock.vacuum.a245", null, null, CURV)
        assertTrue(caps.cleanThenMop)
        assertTrue(caps.midwayDocking)
        assertFalse(caps.pureMop)
    }

    @Test
    fun routine_tab_shows_once_routines_exist() {
        val caps = buildCapabilities(null, null, null, PRO)
        assertEquals(listOf(MainTab.FULL, MainTab.ROOM, MainTab.ZONE), visibleMainTabs(caps, 0))
        assertEquals(listOf(MainTab.FULL, MainTab.ROOM, MainTab.ZONE, MainTab.ROUTINE), visibleMainTabs(caps, 2))
    }

    @Test
    fun sequence_type_bodies_match_the_official_capture() {
        // Captured on the Curv: on with repeat 1, off without it.
        assertEquals(
            Json.parseToJsonElement("""{"type":1,"water_box_mode":235,"mop_mode":300,"fan_power":102,"repeat":1}"""),
            cleanSequenceTypeBody(true, 102, 235, 300, withRepeat = true),
        )
        assertEquals(
            Json.parseToJsonElement("""{"water_box_mode":235,"mop_mode":300,"fan_power":102,"type":0}"""),
            cleanSequenceTypeBody(false, 102, 235, 300, withRepeat = true),
        )
        assertFalse("repeat" in cleanSequenceTypeBody(true, 102, 235, 300, withRepeat = false))
    }

    @Test
    fun seq_type_is_read_from_a_get_status_answer() {
        val answer = Json.parseToJsonElement("""[{"state":8,"seq_type":0,"battery":100}]""")
        assertEquals(0, parseSeqType(answer))
        assertEquals(1, parseSeqType(Json.parseToJsonElement("""{"seq_type":1}""")))
        assertNull(parseSeqType(Json.parseToJsonElement("""[{"state":8}]""")))
        assertNull(parseSeqType(JsonObject(emptyMap())))
    }

    @Test
    fun back_type_is_parsed_from_the_status() {
        val status = Json { ignoreUnknownKeys = true }.decodeFromString(VacuumStatus.serializer(), """{"state":26,"back_type":4}""")
        assertEquals(4, status.backType)
    }
}
