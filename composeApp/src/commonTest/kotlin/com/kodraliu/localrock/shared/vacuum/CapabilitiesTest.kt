package com.kodraliu.localrock.shared.vacuum

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

// app_get_init_status replies read from the two test robots (new_feature_info and new_feature_info_str).
private const val CURV_MODEL = "roborock.vacuum.a245"
private const val CURV_FEATURE_SET = "4499197267967999"
private const val CURV_NEW_FEATURE_SET = "00000000000202058CC57FFDA86836DD5BBFAF7F7EFEFFFF"
private const val PRO_MODEL = "roborock.vacuum.a101"
private const val PRO_FEATURE_SET = "2247397454282751"
private const val PRO_NEW_FEATURE_SET = "42BA8F5C7EDAFFFE"

class CapabilitiesTest {
    private val curv = buildCapabilities(CURV_MODEL, CURV_FEATURE_SET, CURV_NEW_FEATURE_SET, null)
    private val pro = buildCapabilities(PRO_MODEL, PRO_FEATURE_SET, PRO_NEW_FEATURE_SET, null)

    @Test
    fun the_curv_2_flow_has_camera_patrol_pets_and_matter() {
        assertTrue(curv.camera)
        assertTrue(curv.twoKeyVideo)
        assertTrue(curv.remoteViewing)
        assertTrue(curv.patrol)
        assertTrue(curv.petSearch)
        assertTrue(curv.cruiseHistory)
        assertTrue(curv.matter)
    }

    @Test
    fun the_qrevo_pro_has_no_camera_patrol_or_matter() {
        assertFalse(pro.camera)
        assertFalse(pro.twoKeyVideo)
        assertFalse(pro.remoteViewing)
        assertFalse(pro.patrol)
        assertFalse(pro.petSearch)
        assertFalse(pro.cruiseHistory)
        assertFalse(pro.matter)
    }

    @Test
    fun cleaning_flags_follow_the_feature_bits() {
        assertTrue(curv.smartPlan)
        assertTrue(curv.waterSlide)
        assertTrue(curv.fastRoute)
        assertTrue(curv.hasFloorPattern)
        assertTrue(pro.smartPlan)
        assertFalse(pro.waterSlide)
        assertTrue(pro.fastRoute)
        assertFalse(pro.hasFloorPattern)
    }

    @Test
    fun settings_rows_both_robots_show() {
        for (c in listOf(curv, pro)) {
            assertTrue(c.hasCarpetSettings)
            assertTrue(c.hasFloorSettings)
            assertTrue(c.hasDockSettings)
            assertTrue(c.offlineMap)
            assertTrue(c.customDnd)
            assertFalse(c.multiSlotDnd)
            assertTrue(c.remoteControl)
            assertTrue(c.pinNGo)
            assertTrue(c.firmwareUpdates)
            assertTrue(c.autoFirmwareUpdates)
        }
    }

    @Test
    fun flags_waiting_for_their_track_stay_off() {
        for (c in listOf(curv, pro)) {
            assertFalse(c.hasRoutineTab)
            assertFalse(c.hasAutoEmptySettings)
            assertFalse(c.hasObstacleSettings)
            assertFalse(c.chargingMode)
            assertFalse(c.adjustedBattery)
            assertFalse(c.voiceAssistant)
            assertFalse(c.mapBackup)
            assertEquals(listOf(MainTab.FULL, MainTab.ROOM, MainTab.ZONE), c.mainTabs)
        }
    }

    @Test
    fun beta_features_match_the_existing_helpers() {
        assertEquals(supportedBetaFeatures(CURV_NEW_FEATURE_SET), curv.betaFeatures)
        assertEquals(unlistedAdvertisedFeatures(CURV_NEW_FEATURE_SET), curv.unlistedFeatures)
        assertTrue(curv.showsBetaFeatures)
        assertEquals(listOf(34), pro.unlistedFeatures.map { it.bit })
        assertTrue(pro.showsBetaFeatures)
    }

    @Test
    fun dock_capabilities_use_the_model_and_feature_sets() {
        assertEquals(listOf("3 h", "4 h", "5 h"), curv.dock.dryTimeOptions(null).map { it.label })
        assertEquals(listOf("2 h", "3 h", "4 h"), pro.dock.dryTimeOptions(null).map { it.label })
    }

    @Test
    fun the_robots_own_feature_set_wins_over_the_servers() {
        val local = buildCapabilities(CURV_MODEL, null, null, CURV_NEW_FEATURE_SET)
        assertTrue(local.matter)
        assertTrue(local.camera)
        val stale = buildCapabilities(CURV_MODEL, null, PRO_NEW_FEATURE_SET, CURV_NEW_FEATURE_SET)
        assertTrue(stale.matter)
        val none = buildCapabilities(null, null, null, null)
        assertFalse(none.matter)
        assertFalse(none.smartPlan)
        assertFalse(none.showsBetaFeatures)
    }
}
