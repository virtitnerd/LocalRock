package com.kodraliu.localrock.shared.vacuum

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AdvertisedRequestsTest {
    @Test
    fun clean_then_mop_body_adds_repeat_only_for_type_1() {
        assertEquals(
            """{"type":1,"fan_power":102,"water_box_mode":235,"mop_mode":300,"repeat":1}""",
            cleanSequenceTypeParams(true, 102, 235, 300, withRepeat = true).toString(),
        )
        assertEquals(
            """{"type":0,"fan_power":102,"water_box_mode":235,"mop_mode":300}""",
            cleanSequenceTypeParams(false, 102, 235, 300, withRepeat = true).toString(),
        )
        assertEquals(
            """{"type":1,"fan_power":102,"water_box_mode":235,"mop_mode":300}""",
            cleanSequenceTypeParams(true, 102, 235, 300, withRepeat = false).toString(),
        )
    }

    @Test
    fun seq_type_reads_from_status() {
        assertEquals(false, parseSeqType(Json.parseToJsonElement("""{"state":8,"seq_type":0}""")))
        assertEquals(true, parseSeqType(Json.parseToJsonElement("""{"seq_type":1}""")))
        assertNull(parseSeqType(Json.parseToJsonElement("""{"state":8}""")))
    }

    @Test
    fun sync_rooms_info_matches_the_capture() {
        assertEquals(
            """[{"name":"Study1","id":"23000782"},{"name":"Hall","id":"23000771"}]""",
            syncRoomsInfoParams(listOf(HomeRoomName("23000782", "Study1"), HomeRoomName("23000771", "Hall"))).toString(),
        )
    }

    @Test
    fun name_segment_matches_the_capture() {
        assertEquals(
            """{"data":[{"iotRoomId":"23000492","robotTagId":6,"robotRoomId":1}]}""",
            nameSegmentParams(listOf(SegmentRoomLink(robotRoomId = 1, iotRoomId = "23000492", robotTagId = 6))).toString(),
        )
    }

    @Test
    fun beautify_blocks_matches_the_capture() {
        assertEquals("""{"map_index":0,"data":[]}""", beautifyBlocksParams(0, emptyList()).toString())
        assertEquals(
            """{"map_index":0,"data":[[0,19673,35541,22756,35541,22756,32139,19673,32139]]}""",
            beautifyBlocksParams(0, listOf(EraserRect(0, 19673, 35541, 22756, 32139))).toString(),
        )
    }
}
