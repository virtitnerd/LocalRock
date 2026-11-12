package com.kodraliu.localrock.shared.vacuum.camera

/** `newFeatureSet` bit for two-key real-time video (python-roborock NewFeatureStrBit). */
const val TWO_KEY_VIDEO_FEATURE_BIT = 32

/** Bits that mark a robot with real-time video: two-key (32), two-key while charging (33), video patrol (48), video while charging and standby (73). */
val CAMERA_FEATURE_BITS = listOf(32, 33, 48, 73)

/**
 * The robot's `camera_status` integer, laid out as the official app packs it (decoded from its
 * CameraStatusManager). Bits 12-13 hold a two-bit state for two-key remote viewing: the app
 * writes 1 to ask for activation, the user presses Power within a minute, and the robot sets 2.
 */
data class CameraStatus(val raw: Int) {
    private fun bit(n: Int) = (raw shr n) and 1 == 1

    val cameraEnabled: Boolean get() = bit(0)
    val petMode: Boolean get() = bit(1)

    /** Set by the robot once remote viewing is activated with the three-button hold. */
    val realTimeMonitor: Boolean get() = bit(2)
    val privacyAgreed: Boolean get() = bit(5)
    val twoKeyState: Int get() = (raw shr TWO_KEY_SHIFT) and 3

    val twoKeyRequested: Boolean get() = twoKeyState == TWO_KEY_REQUESTED
    val twoKeyActive: Boolean get() = twoKeyState == TWO_KEY_ACTIVE

    /** Remote viewing is on, by either activation method. */
    val activated: Boolean get() = realTimeMonitor || twoKeyActive

    /**
     * The writes the official app makes to ask for two-key activation, in its order: the pet-mode alert
     * is marked shown, the privacy policy is agreed, then state 1 is set. An a226 captured them as
     * 257 -> 385 -> 417 -> 4513. Steps the robot already reports are left out.
     */
    fun twoKeyActivationSteps(): List<CameraStatus> {
        val shown = CameraStatus(raw or PET_ALERT_SHOWN_BIT)
        val agreed = CameraStatus(shown.raw or PRIVACY_AGREED_BIT)
        val requested = agreed.withTwoKey(TWO_KEY_REQUESTED)
        return listOf(shown, agreed, requested).filter { it.raw != raw }.distinct()
    }

    /** The value the app writes when the user cancels or the wait runs out. */
    fun cancellingTwoKey(): CameraStatus = withTwoKey(0)

    private fun withTwoKey(state: Int) = CameraStatus((raw and (3 shl TWO_KEY_SHIFT).inv()) or (state shl TWO_KEY_SHIFT))

    companion object {
        const val TWO_KEY_SHIFT = 12
        const val TWO_KEY_REQUESTED = 1
        const val TWO_KEY_ACTIVE = 2
        const val PRIVACY_AGREED_BIT = 1 shl 5
        const val PET_ALERT_SHOWN_BIT = 1 shl 7
    }
}
