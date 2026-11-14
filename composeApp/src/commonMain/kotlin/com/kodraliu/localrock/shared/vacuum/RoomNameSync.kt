package com.kodraliu.localrock.shared.vacuum

import com.kodraliu.localrock.shared.protocol.V1Response
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

// Bit 98: what the official app sends after a room rename, from the server's MQTT log. Not yet sent by LocalRock.

/** A home room as sync_rooms_info names it: [iotId] is the iot room id string from home data or get_room_mapping. */
data class HomeRoomName(val iotId: String, val name: String)

/** One mapped segment: robot segment id, its iot room id and its room type tag. */
data class SegmentRoomLink(val robotRoomId: Int, val iotRoomId: String, val robotTagId: Int)

/** `[{"name":"Study1","id":"23000782"},...]`, every home room. */
internal fun syncRoomsInfoParams(rooms: List<HomeRoomName>): JsonArray = JsonArray(
    rooms.map { JsonObject(mapOf("name" to JsonPrimitive(it.name), "id" to JsonPrimitive(it.iotId))) },
)

/** `{"data":[{"iotRoomId":"23000492","robotTagId":6,"robotRoomId":1},...]}`, one entry per mapped segment. */
internal fun nameSegmentParams(links: List<SegmentRoomLink>): JsonObject = JsonObject(
    mapOf(
        "data" to JsonArray(
            links.map {
                JsonObject(
                    mapOf(
                        "iotRoomId" to JsonPrimitive(it.iotRoomId),
                        "robotTagId" to JsonPrimitive(it.robotTagId),
                        "robotRoomId" to JsonPrimitive(it.robotRoomId),
                    ),
                )
            },
        ),
    ),
)

suspend fun VacuumSession.syncRoomsInfo(rooms: List<HomeRoomName>): V1Response =
    sendCommandRaw("sync_rooms_info", syncRoomsInfoParams(rooms))

// The robot answers ["retry"] first, then applies the mapping.
suspend fun VacuumSession.nameSegments(links: List<SegmentRoomLink>): V1Response =
    sendCommandRaw("name_segment", nameSegmentParams(links), timeoutMs = 20_000L)
