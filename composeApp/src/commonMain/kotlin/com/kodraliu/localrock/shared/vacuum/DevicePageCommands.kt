package com.kodraliu.localrock.shared.vacuum

import com.kodraliu.localrock.shared.protocol.V1Response
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull

// Device page RPCs (track B2). Names and bodies from the plugin's dock bar and mode panel (rpc-catalog.md, cleaning-and-dock-settings.md 2.2).

/** Stops a running dock empty; the dock bar's "Stop Emptying". Not yet seen on a robot. */
suspend fun VacuumSession.appStopCollectDust(): V1Response =
    sendCommand("app_stop_collect_dust").requireOk("app_stop_collect_dust")

/** Continues a global clean after a halfway return, as captured from the official app: `app_start [{"clean_mop":0}]`. */
suspend fun VacuumSession.appStartContinue(): V1Response =
    sendCommand("app_start", listOf(JsonObject(mapOf("clean_mop" to JsonPrimitive(0))))).requireOk("app_start")

/** `{type, fan_power, water_box_mode, mop_mode}` plus `repeat: 1` only for type 1 on CtmWithRepeat robots. */
internal fun cleanSequenceTypeBody(
    cleanThenMop: Boolean,
    fanPower: Int,
    waterBoxMode: Int,
    mopMode: Int,
    withRepeat: Boolean,
): JsonObject = JsonObject(buildMap {
    put("type", JsonPrimitive(if (cleanThenMop) 1 else 0))
    put("fan_power", JsonPrimitive(fanPower))
    put("water_box_mode", JsonPrimitive(waterBoxMode))
    put("mop_mode", JsonPrimitive(mopMode))
    if (cleanThenMop && withRepeat) put("repeat", JsonPrimitive(1))
})

/** "Vac followed by Mop" on (type 1) or off (type 0) together with the three motor codes. */
suspend fun VacuumSession.setCleanSequenceType(
    cleanThenMop: Boolean,
    fanPower: Int,
    waterBoxMode: Int,
    mopMode: Int,
    withRepeat: Boolean,
): V1Response = sendCommandRaw(
    "app_set_clean_sequence_type",
    cleanSequenceTypeBody(cleanThenMop, fanPower, waterBoxMode, mopMode, withRepeat),
).requireOk("app_set_clean_sequence_type")

/** `seq_type` from a get_status answer (seen as 0 on a Qrevo Curv 2 Flow); null when absent. */
internal fun parseSeqType(result: JsonElement?): Int? {
    val status = (if (result is JsonArray) result.firstOrNull() else result) as? JsonObject ?: return null
    return (status["seq_type"] as? JsonPrimitive)?.intOrNull
}

/** Reads `seq_type`, which VacuumStatus does not carry, straight from get_status. */
suspend fun VacuumSession.readSeqType(): Int? =
    parseSeqType(sendCommand("get_status").requireOk("get_status").result)
