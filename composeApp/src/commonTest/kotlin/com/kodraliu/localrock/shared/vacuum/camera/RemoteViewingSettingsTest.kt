package com.kodraliu.localrock.shared.vacuum.camera

import com.kodraliu.localrock.shared.protocol.V1Response
import com.kodraliu.localrock.shared.vacuum.buildCapabilities
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

// Read from the Qrevo Curv 2 Flow on 2026-11-14: get_camera_status and app_get_init_status.
private const val CURV_CAMERA_STATUS = 28071
private const val CURV_FEATURE_SET = "4499197267967999"
private const val CURV_NEW_FEATURE_SET = "00000000000202058CC57FFDA86836DD5BBFAF7F7EFEFFFF"

class RemoteViewingSettingsTest {
    private val curv = CameraStatus(CURV_CAMERA_STATUS)

    @Test
    fun the_curv_word_decodes_as_active_agreed_and_strong() {
        assertTrue(curv.realTimeMonitor)
        assertTrue(curv.privacyAgreed)
        assertTrue(curv.twoKeyActive)
        assertEquals(1, curv.alertLevel)
        assertEquals(AlertLevel.STRONG, AlertLevel.fromWire(curv.alertLevel))
    }

    @Test
    fun alert_levels_keep_the_official_order_and_values() {
        assertEquals(listOf(AlertLevel.FLASH, AlertLevel.GENTLE, AlertLevel.STRONG), AlertLevel.entries)
        assertEquals(listOf(2, 0, 1), AlertLevel.entries.map { it.wire })
        assertEquals(AlertLevel.GENTLE, AlertLevel.fromWire(3))
    }

    @Test
    fun alert_level_writes_touch_only_bits_8_and_9() {
        // The values sent in the reversible test on the Curv.
        assertEquals(27815, curv.withAlertLevel(AlertLevel.GENTLE.wire).raw)
        assertEquals(28327, curv.withAlertLevel(AlertLevel.FLASH.wire).raw)
        assertEquals(CURV_CAMERA_STATUS, curv.withAlertLevel(AlertLevel.STRONG.wire).raw)
    }

    @Test
    fun privacy_and_deactivate_keep_the_other_bits() {
        val revoked = curv.withPrivacyAgreed(false)
        assertFalse(revoked.privacyAgreed)
        assertEquals(CURV_CAMERA_STATUS - 32, revoked.raw)
        assertEquals(CURV_CAMERA_STATUS, revoked.withPrivacyAgreed(true).raw)

        val off = curv.deactivated()
        assertFalse(off.activated)
        assertEquals(0, off.twoKeyState)
        assertEquals(CURV_CAMERA_STATUS - 4 - (2 shl 12), off.raw)
        assertEquals(curv.alertLevel, off.alertLevel)
    }

    @Test
    fun feature_gates_follow_the_plugin_getters() {
        assertEquals(true, isVideoMonitorSupported(CURV_FEATURE_SET))
        assertNull(isVideoMonitorSupported(null))
        assertEquals(false, isVideoMonitorSupported("7"))
        assertTrue(isVideoSettingSupported(CURV_FEATURE_SET))
        assertFalse(isVideoSettingSupported("63"))
        assertTrue(isSupportSetVolumeInCall(CURV_NEW_FEATURE_SET))
        assertFalse(isSupportSetVolumeInCall("7EFEFFFE"))
        // Length not a multiple of 8.
        assertFalse(isSupportSetVolumeInCall("42BA8F5C7EDAFFFFF"))
        assertFalse(isSupportSetVolumeInCall(null))
    }

    @Test
    fun the_page_needs_the_camera_and_falls_back_without_a_feature_set() {
        assertTrue(remoteViewingSupported(CURV_FEATURE_SET, camera = true))
        assertTrue(remoteViewingSupported(null, camera = true))
        assertFalse(remoteViewingSupported("7", camera = true))
        assertFalse(remoteViewingSupported(CURV_FEATURE_SET, camera = false))
        assertEquals(RemoteViewingFeatures.NONE, remoteViewingFeatures(CURV_FEATURE_SET, CURV_NEW_FEATURE_SET, page = false))

        val caps = buildCapabilities("roborock.vacuum.a245", CURV_FEATURE_SET, CURV_NEW_FEATURE_SET, null)
        assertTrue(caps.remoteViewing)
        assertEquals(RemoteViewingFeatures(alertLevel = true, callVolume = true), caps.remoteViewingFeatures)
        val pro = buildCapabilities("roborock.vacuum.a101", "2247397454282751", "42BA8F5C7EDAFFFE", null)
        assertFalse(pro.remoteViewing)
        assertEquals(RemoteViewingFeatures.NONE, pro.remoteViewingFeatures)
    }

    @Test
    fun password_body_always_carries_three_keys() {
        assertEquals(
            """{"enable_password":1,"old_password":"","new_password":"abc"}""",
            officialHomesecPasswordParams(enable = true, oldMd5 = null, newMd5 = "abc").toString(),
        )
        assertEquals(
            """{"enable_password":1,"old_password":"old","new_password":"new"}""",
            officialHomesecPasswordParams(enable = true, oldMd5 = "old", newMd5 = "new").toString(),
        )
        assertEquals(
            """{"enable_password":0,"old_password":"old","new_password":""}""",
            officialHomesecPasswordParams(enable = false, oldMd5 = "old", newMd5 = null).toString(),
        )
    }

    @Test
    fun status_fields_and_check_replies_decode() {
        val status = Json.parseToJsonElement("""[{"state":8,"home_sec_enable_password":1,"camera_status":28071}]""")
        assertEquals(true, passwordEnabledFrom(status))
        assertEquals(8, robotStateFrom(status))
        assertNull(passwordEnabledFrom(Json.parseToJsonElement("""[{"state":8}]""")))

        fun reply(result: String?, error: String? = null) = V1Response(
            id = 1,
            result = result?.let { Json.parseToJsonElement(it) },
            error = error?.let { Json.parseToJsonElement(it).jsonObject },
        )
        assertEquals(PatternCheck.OK, patternCheckFrom(reply("""["ok"]""")))
        assertEquals(PatternCheck.WRONG, patternCheckFrom(reply("[0]")))
        assertEquals(PatternCheck.LOCKED, patternCheckFrom(reply(null, """{"code":-10010,"message":"locked"}""")))
        assertEquals(PatternCheck.TIMEOUT, patternCheckFrom(reply(null, """{"code":-1,"message":"timeout"}""")))
        assertEquals(PatternCheck.WRONG, patternCheckFrom(reply(null, """{"code":-10009,"message":"bad"}""")))
    }
}
