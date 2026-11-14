package com.kodraliu.localrock.shared.vacuum

import com.kodraliu.localrock.shared.protocol.V1Response
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive

// How a feature whose RPCs do not fit the plain get/set pair is read and written.
sealed interface FeatureCodec {
    // A bit of get_status.switch_status, which plain get_status leaves out; only get_prop ["get_status"] carries it.
    data class StatusSwitchBit(val mask: Int) : FeatureCodec

    // The set command takes only the bare object; other shapes time out instead of being refused.
    data object BareObjectFirst : FeatureCodec

    // One bit of the camera_status word, written read-modify-write; [requiredBits] must be on to turn it on.
    data class CameraStatusBit(val bit: Int, val requiredBits: List<Int>, val blockedReason: String) : FeatureCodec

    // The status field of app_get/set_dryer_setting: 0 manual, 1 auto, 2 scheduled.
    data object DryerStartMethod : FeatureCodec
}

private val GetStatusParams = JsonArray(listOf(JsonPrimitive("get_status")))

// Default time for Scheduled Drying when the robot holds none, as cron; the official app asks for one.
internal const val DEFAULT_DRY_CRON = "0 3 * * *"

private fun JsonElement?.single(): JsonElement? = if (this is JsonArray && size == 1) this[0] else this

private fun JsonElement?.intField(name: String): Int? = ((this as? JsonObject)?.get(name) as? JsonPrimitive)?.intOrNull

/** Reads the feature's value from its get reply; the key is the field the generic writer would use. */
fun parseFeatureReply(def: BetaFeatureDef, result: JsonElement?): Pair<String?, Int>? = when (val c = def.codec) {
    is FeatureCodec.StatusSwitchBit -> result.single().intField("switch_status")?.let { "status" to if (it and c.mask != 0) 1 else 0 }
    is FeatureCodec.CameraStatusBit -> cameraWord(result)?.let { null to ((it shr c.bit) and 1) }
    FeatureCodec.DryerStartMethod -> result.single().intField("status")?.let { "status" to it }
    else -> parseBetaReply(result)
}

internal fun cameraWord(result: JsonElement?): Int? = (result.single() as? JsonPrimitive)?.intOrNull

/** Why the switch cannot be turned on now (shown as the disabled row's tip), from the raw get reply. */
fun featureBlockedReason(def: BetaFeatureDef, raw: String?): String? {
    val c = def.codec as? FeatureCodec.CameraStatusBit ?: return null
    val word = runCatching { cameraWord(Json.parseToJsonElement(raw ?: return null)) }.getOrNull() ?: return null
    return c.blockedReason.takeIf { cameraBitBlocked(c, word) }
}

internal fun cameraBitBlocked(c: FeatureCodec.CameraStatusBit, word: Int): Boolean = c.requiredBits.any { (word shr it) and 1 == 0 }

/** The camera_status word with [bit] set to [on], every other bit kept. */
internal fun cameraWordWith(word: Int, bit: Int, on: Boolean): Int = if (on) word or (1 shl bit) else word and (1 shl bit).inv()

/** Minimal app_set_dryer_setting body the official app sends: {on:{dry_time}, status, cron}. */
internal fun dryerStartMethodBody(reported: JsonObject?, status: Int, cron: String? = null): JsonObject {
    val dryTime = (reported?.get("on") as? JsonObject).intField("dry_time")
    val held = (reported?.get("cron") as? JsonPrimitive)?.contentOrNull.orEmpty()
    val sentCron = cron ?: if (status == 2 && held.isBlank()) DEFAULT_DRY_CRON else held
    val body = mutableMapOf<String, JsonElement>()
    if (dryTime != null) body["on"] = JsonObject(mapOf("dry_time" to JsonPrimitive(dryTime)))
    body["status"] = JsonPrimitive(status)
    body["cron"] = JsonPrimitive(sentCron)
    return JsonObject(body)
}

/** Param shapes to try for [def]'s set command, in order; the robot's -10007/-10005 moves on to the next. */
fun featureSetCandidates(def: BetaFeatureDef, key: String?, value: Int): List<JsonElement> {
    val v = JsonPrimitive(value)
    def.settingName?.let { return listOf(robotSettingWriteParams(it, value)) }
    if (def.codec is FeatureCodec.StatusSwitchBit) return listOf(JsonObject(mapOf("status" to v)))
    val obj = key?.let { JsonObject(mapOf(it to v)) }
    if (obj != null && (def.codec == FeatureCodec.BareObjectFirst || def.choices != null)) {
        return listOf(obj, JsonArray(listOf(obj)), JsonArray(listOf(v)))
    }
    return listOfNotNull(JsonArray(listOf(obj ?: v)), obj, JsonArray(listOf(v)).takeIf { obj != null })
}

/** The get command for [def], with the params its codec needs. */
suspend fun VacuumSession.readFeatureRaw(def: BetaFeatureDef): V1Response = when {
    def.settingName != null -> getRobotSetting(def.settingName)
    def.codec is FeatureCodec.StatusSwitchBit -> sendCommandRaw("get_prop", GetStatusParams)
    else -> sendCommand(def.getMethod)
}

/** The last reply of a write, and the [featureSetCandidates] index the robot took, if any. */
data class FeatureWrite(val method: String, val reply: V1Response?, val acceptedShape: Int?)

/** Writes [value] for [def], trying shapes from [lastAccepted] first; reads fresh state first where the write needs it. */
suspend fun VacuumSession.writeFeature(def: BetaFeatureDef, key: String?, value: Int, lastAccepted: Int?): FeatureWrite {
    when (val c = def.codec) {
        is FeatureCodec.CameraStatusBit -> {
            val word = cameraWord(getCameraStatus().requireOk("get_camera_status").result)
                ?: error("get_camera_status gave no word")
            if (value == 1 && cameraBitBlocked(c, word)) error(c.blockedReason)
            return FeatureWrite(def.setMethod, setCameraStatus(cameraWordWith(word, c.bit, value == 1)), null)
        }
        FeatureCodec.DryerStartMethod -> {
            val reported = getDryerSetting().requireOk(def.getMethod).result.single() as? JsonObject
            return FeatureWrite(def.setMethod, setDryerSetting(dryerStartMethodBody(reported, value)), null)
        }
        else -> Unit
    }
    val candidates = featureSetCandidates(def, key, value)
    var last: V1Response? = null
    var accepted: Int? = null
    for (i in betaSetTryOrder(candidates.size, lastAccepted)) {
        last = sendCommandRaw(def.setMethod, candidates[i])
        if (last.error == null) accepted = i
        if (last.error?.get("code")?.jsonPrimitive?.intOrNull !in BetaShapeRefusalCodes) break
    }
    return FeatureWrite(def.setMethod, last, accepted)
}
