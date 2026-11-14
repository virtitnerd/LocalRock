package com.kodraliu.localrock.shared.vacuum

import com.kodraliu.localrock.shared.protocol.V1Response
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

// "Vac followed by Mop" (bits 93, 102, 114): get_status.seq_type reads it, this writes it. Verified 0 -> 1 -> 0 on an a245.

/** `{"type":1,"fan_power":102,"water_box_mode":235,"mop_mode":300,"repeat":1}`; repeat only for type 1 on bit-102 robots. */
internal fun cleanSequenceTypeParams(
    vacThenMop: Boolean,
    fanPower: Int,
    waterBoxMode: Int,
    mopMode: Int,
    withRepeat: Boolean,
): JsonObject {
    val body = mutableMapOf<String, JsonElement>(
        "type" to JsonPrimitive(if (vacThenMop) 1 else 0),
        "fan_power" to JsonPrimitive(fanPower),
        "water_box_mode" to JsonPrimitive(waterBoxMode),
        "mop_mode" to JsonPrimitive(mopMode),
    )
    if (vacThenMop && withRepeat) body["repeat"] = JsonPrimitive(1)
    return JsonObject(body)
}

/** get_status.seq_type as a boolean; null when the robot does not report it. */
internal fun parseSeqType(status: JsonElement?): Boolean? =
    ((status as? JsonObject)?.get("seq_type") as? JsonPrimitive)?.content?.toIntOrNull()?.let { it == 1 }

suspend fun VacuumSession.setCleanSequenceType(
    vacThenMop: Boolean,
    fanPower: Int,
    waterBoxMode: Int,
    mopMode: Int,
    withRepeat: Boolean,
): V1Response = sendCommandRaw(
    "app_set_clean_sequence_type",
    cleanSequenceTypeParams(vacThenMop, fanPower, waterBoxMode, mopMode, withRepeat),
)
