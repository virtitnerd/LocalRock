package com.kodraliu.localrock.shared.vacuum.camera

/** `newFeatureSet` bit for two-key real-time video (python-roborock NewFeatureStrBit). */
const val TWO_KEY_VIDEO_FEATURE_BIT = 32

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

    /** The value that asks the robot to wait for the Power press; the privacy bit is set as the app does. */
    fun requestingTwoKey(): CameraStatus = withTwoKey(TWO_KEY_REQUESTED).let { CameraStatus(it.raw or PRIVACY_AGREED_BIT) }

    /** The value the app writes when the user cancels or the wait runs out. */
    fun cancellingTwoKey(): CameraStatus = withTwoKey(0)

    private fun withTwoKey(state: Int) = CameraStatus((raw and (3 shl TWO_KEY_SHIFT).inv()) or (state shl TWO_KEY_SHIFT))

    companion object {
        const val TWO_KEY_SHIFT = 12
        const val TWO_KEY_REQUESTED = 1
        const val TWO_KEY_ACTIVE = 2
        const val PRIVACY_AGREED_BIT = 1 shl 5
    }
}
