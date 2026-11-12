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
    fun requestingActivationWritesStateOneAndKeepsTheRest() {
        val request = off.requestingTwoKey()
        assertEquals(259 or (1 shl 12) or (1 shl 5), request.raw)
        assertTrue(request.twoKeyRequested)
        assertFalse(request.activated)
    }

    @Test
    fun requestingReplacesAStaleActiveState() {
        val request = CameraStatus(2 shl 12).requestingTwoKey()
        assertEquals(1, request.twoKeyState)
    }

    @Test
    fun cancellingClearsOnlyTheTwoKeyState() {
        val pending = off.requestingTwoKey()
        assertEquals(259 or (1 shl 5), pending.cancellingTwoKey().raw)
        assertEquals(0, pending.cancellingTwoKey().twoKeyState)
    }

    @Test
    fun threeKeyActivationShowsInBitTwo() {
        assertTrue(CameraStatus(4).activated)
        assertFalse(CameraStatus(4).twoKeyActive)
    }
}
