package com.kodraliu.localrock.shared.vacuum

import com.kodraliu.localrock.shared.protocol.V1Response
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

// Bit 71, the Manage Maps "Remove" tool, from the server's MQTT log of the official app. The list replaces every eraser zone.

/** An axis-aligned eraser rectangle in robot millimetres. */
data class EraserRect(val id: Int, val x1: Int, val y1: Int, val x2: Int, val y2: Int)

/** `{"map_index":0,"data":[[id, x1,y1, x2,y1, x2,y2, x1,y2],...]}`; an empty list clears all zones. */
internal fun beautifyBlocksParams(mapIndex: Int, zones: List<EraserRect>): JsonObject = JsonObject(
    mapOf(
        "map_index" to JsonPrimitive(mapIndex),
        "data" to JsonArray(
            zones.map { z ->
                JsonArray(listOf(z.id, z.x1, z.y1, z.x2, z.y1, z.x2, z.y2, z.x1, z.y2).map { JsonPrimitive(it) })
            },
        ),
    ),
)

suspend fun VacuumSession.setEraserZones(mapIndex: Int, zones: List<EraserRect>): V1Response =
    sendCommandRaw("app_set_beautify_blocks", beautifyBlocksParams(mapIndex, zones))
