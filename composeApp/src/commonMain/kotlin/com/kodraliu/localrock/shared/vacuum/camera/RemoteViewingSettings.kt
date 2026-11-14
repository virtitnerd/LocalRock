package com.kodraliu.localrock.shared.vacuum.camera

import com.kodraliu.localrock.shared.protocol.V1Response
import com.kodraliu.localrock.shared.vacuum.VacuumSession
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive

/** Alert Level choices in the official order, with their camera_status bits 8-9 value (VideoSettingMap). */
enum class AlertLevel(val wire: Int) {
    FLASH(2),
    GENTLE(0),
    STRONG(1);

    companion object {
        fun fromWire(value: Int): AlertLevel = entries.firstOrNull { it.wire == value } ?: GENTLE
    }
}

/** Which optional rows the Remote Viewing page shows for this robot. */
data class RemoteViewingFeatures(val alertLevel: Boolean, val callVolume: Boolean) {
    companion object {
        val NONE = RemoteViewingFeatures(alertLevel = false, callVolume = false)
    }
}

private fun numericFeature(featureSet: String?, mask: ULong): Boolean? =
    featureSet?.trim()?.toULongOrNull()?.let { it and mask != 0uL }

// isVideoMonitorSupported: robotNewFeatures & 8; null when the server has no featureSet for the robot.
fun isVideoMonitorSupported(featureSet: String?): Boolean? = numericFeature(featureSet, 8uL)

// isVideoSettingSupported: robotNewFeatures & 64 (camera_status bits 8-9 carry the alert level).
fun isVideoSettingSupported(featureSet: String?): Boolean = numericFeature(featureSet, 64uL) == true

// isSupportSetVolumeInCall: info string length a multiple of 8 and bit 0 of its last 8 hex digits.
fun isSupportSetVolumeInCall(newFeatureSet: String?): Boolean {
    val s = newFeatureSet?.trim() ?: return false
    if (s.isEmpty() || s.length % 8 != 0) return false
    val last = s.takeLast(8).toLongOrNull(16) ?: return false
    return last and 1L == 1L
}

/** Official page gate is RNF 3 plus MF.RealTimeVideoPhoto; LocalRock stands in the camera bits for the model table. */
fun remoteViewingSupported(featureSet: String?, camera: Boolean): Boolean =
    camera && (isVideoMonitorSupported(featureSet) ?: true)

fun remoteViewingFeatures(featureSet: String?, newFeatureSet: String?, page: Boolean): RemoteViewingFeatures =
    if (!page) RemoteViewingFeatures.NONE
    else RemoteViewingFeatures(alertLevel = isVideoSettingSupported(featureSet), callVolume = isSupportSetVolumeInCall(newFeatureSet))

/** The official set_homesec_password body: all three keys every time, empty strings when absent. */
fun officialHomesecPasswordParams(enable: Boolean, oldMd5: String?, newMd5: String?): JsonObject =
    JsonObject(mapOf(
        "enable_password" to JsonPrimitive(if (enable) 1 else 0),
        "old_password" to JsonPrimitive(oldMd5.orEmpty()),
        "new_password" to JsonPrimitive(newMd5.orEmpty()),
    ))

suspend fun VacuumSession.updateHomesecPassword(enable: Boolean, oldMd5: String?, newMd5: String?): V1Response =
    sendCommandRaw("set_homesec_password", officialHomesecPasswordParams(enable, oldMd5, newMd5))

/** get_status `home_sec_enable_password`, 1 when a pattern is required. */
fun passwordEnabledFrom(statusResult: JsonElement?): Boolean? =
    CameraLiveSession.firstObject(statusResult)?.get("home_sec_enable_password")?.jsonPrimitive?.intOrNull?.let { it == 1 }

fun robotStateFrom(statusResult: JsonElement?): Int? =
    CameraLiveSession.firstObject(statusResult)?.get("state")?.jsonPrimitive?.intOrNull

/** How a check_homesec_password reply reads to the pattern page. */
enum class PatternCheck { OK, WRONG, LOCKED, TIMEOUT }

fun patternCheckFrom(reply: V1Response): PatternCheck {
    val error = reply.error ?: return if (CameraLiveSession.firstInt(reply.result).let { it == null || it == 1 }) PatternCheck.OK else PatternCheck.WRONG
    if (error["code"]?.jsonPrimitive?.intOrNull == PinLockPolicy.ROBOT_CODE) return PatternCheck.LOCKED
    val text = (error["message"] as? JsonPrimitive)?.content ?: error.toString()
    return if (text.contains("timeout", ignoreCase = true)) PatternCheck.TIMEOUT else PatternCheck.WRONG
}
