package com.kodraliu.localrock.shared.vacuum.camera

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CameraStatusTest {
    // Values read from a Qrevo Curv 2 Flow: 259 with remote viewing off, 8615 once the cloud enabled it.
    private val off = CameraStatus(259)
    private val cloudEnabled = CameraStatus(8615)

    @Test
    fun readsTheFieldsTheOfficialAppReads() {
        assertTrue(off.cameraEnabled)
        assertTrue(off.petMode)
        assertFalse(off.realTimeMonitor)
        assertEquals(0, off.twoKeyState)
        assertFalse(off.activated)

        assertTrue(cloudEnabled.privacyAgreed)
        assertTrue(cloudEnabled.realTimeMonitor)
        assertEquals(2, cloudEnabled.twoKeyState)
        assertTrue(cloudEnabled.activated)
    }

    @Test
    fun activationFollowsTheSequenceAnA226Captured() {
        // The official app wrote 385, 417 and 4513 starting from 257.
        assertEquals(listOf(385, 417, 4513), CameraStatus(257).twoKeyActivationSteps().map { it.raw })
    }

    @Test
    fun activationStartsFromWhereTheRobotIs() {
        assertEquals(listOf(387, 419, 4515), off.twoKeyActivationSteps().map { it.raw })
        assertEquals(listOf(4513), CameraStatus(417).twoKeyActivationSteps().map { it.raw })
        val last = off.twoKeyActivationSteps().last()
        assertTrue(last.twoKeyRequested)
        assertFalse(last.activated)
    }

    @Test
    fun anActiveStateIsReplacedByTheRequest() {
        assertEquals(1, CameraStatus(2 shl 12).twoKeyActivationSteps().last().twoKeyState)
    }

    @Test
    fun cancellingClearsOnlyTheTwoKeyState() {
        val pending = CameraStatus(4513)
        assertEquals(417, pending.cancellingTwoKey().raw)
        assertEquals(0, pending.cancellingTwoKey().twoKeyState)
    }

    @Test
    fun threeKeyActivationShowsInBitTwo() {
        assertTrue(CameraStatus(4).activated)
        assertFalse(CameraStatus(4).twoKeyActive)
    }
}
