package com.kodraliu.localrock.ui.vacuum

import com.kodraliu.localrock.shared.vacuum.VacuumSession
import com.kodraliu.localrock.shared.vacuum.appStop
import com.kodraliu.localrock.shared.vacuum.camera.CameraLiveSession
import com.kodraliu.localrock.shared.vacuum.camera.CameraStatus
import com.kodraliu.localrock.shared.vacuum.camera.PatternCheck
import com.kodraliu.localrock.shared.vacuum.camera.passwordEnabledFrom
import com.kodraliu.localrock.shared.vacuum.camera.patternCheckFrom
import com.kodraliu.localrock.shared.vacuum.camera.robotStateFrom
import com.kodraliu.localrock.shared.vacuum.camera.updateHomesecPassword
import com.kodraliu.localrock.shared.vacuum.checkHomesecPassword
import com.kodraliu.localrock.shared.vacuum.getCameraStatus
import com.kodraliu.localrock.shared.vacuum.resetHomesecPassword
import com.kodraliu.localrock.shared.vacuum.setCameraStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** What the Remote Viewing page shows, read from get_camera_status and get_status. */
data class RemoteViewingSnapshot(val camera: CameraStatus, val passwordEnabled: Boolean?, val robotState: Int?)

/** Robot reads and writes for the Remote Viewing settings page; every camera_status write is read-modify-write. */
class RemoteViewingController(private val session: VacuumSession) {
    private val _snapshot = MutableStateFlow<RemoteViewingSnapshot?>(null)
    val snapshot: StateFlow<RemoteViewingSnapshot?> = _snapshot.asStateFlow()

    private val _readFailed = MutableStateFlow(false)
    val readFailed: StateFlow<Boolean> = _readFailed.asStateFlow()

    private suspend fun readCamera(): CameraStatus? =
        runCatching { CameraLiveSession.firstInt(session.getCameraStatus().result) }.getOrNull()?.let { CameraStatus(it) }

    suspend fun refresh() {
        val camera = readCamera()
        if (camera == null) {
            _readFailed.value = _snapshot.value == null
            return
        }
        val status = runCatching { session.sendCommand("get_status").result }.getOrNull()
        _readFailed.value = false
        _snapshot.value = RemoteViewingSnapshot(camera, passwordEnabledFrom(status), robotStateFrom(status))
    }

    private suspend fun write(change: (CameraStatus) -> CameraStatus): Boolean {
        val now = readCamera() ?: return false
        val next = change(now)
        if (next.raw != now.raw && session.setCameraStatus(next.raw).error != null) return false
        delay(WRITE_SETTLE_MS)
        refresh()
        return true
    }

    /** True when the robot now reports [level]; false when it refused or kept its own value. */
    suspend fun setAlertLevel(level: Int): Boolean =
        write { it.withAlertLevel(level) } && _snapshot.value?.camera?.alertLevel == level

    suspend fun setPrivacy(agreed: Boolean): Boolean =
        write { it.withPrivacyAgreed(agreed) } && _snapshot.value?.camera?.privacyAgreed == agreed

    suspend fun deactivate(): Boolean = write { it.deactivated() } && _snapshot.value?.camera?.activated == false

    suspend fun stopTask(): Boolean = runCatching { session.appStop().error == null }.getOrDefault(false)

    /**
     * Two-key robots get the official writes (pet alert shown, privacy, state 1) and wait for the Power
     * press; three-key robots wait for the button hold. Polls once a second; true once activated.
     */
    suspend fun activate(twoKey: Boolean, onSecondsLeft: (Int) -> Unit): Boolean {
        val start = readCamera() ?: return false
        if (start.activated) return true
        try {
            if (twoKey) {
                for (step in start.twoKeyActivationSteps()) {
                    if (session.setCameraStatus(step.raw).error != null) return false
                    delay(STEP_GAP_MS)
                }
            }
            for (left in ACTIVATION_SECONDS downTo 1) {
                onSecondsLeft(left)
                delay(1_000)
                if (readCamera()?.activated == true) {
                    refresh()
                    return true
                }
            }
            cancelActivation(twoKey)
            return false
        } catch (e: CancellationException) {
            // Leaving the dialog writes state 0, as the official Cancel does.
            withContext(NonCancellable) { cancelActivation(twoKey) }
            throw e
        }
    }

    private suspend fun cancelActivation(twoKey: Boolean) {
        if (!twoKey) return
        runCatching { readCamera()?.let { session.setCameraStatus(it.cancellingTwoKey().raw) } }
        runCatching { refresh() }
    }

    suspend fun checkPattern(pin: String): PatternCheck =
        runCatching { patternCheckFrom(session.checkHomesecPassword(CameraLiveSession.md5Hex(pin))) }
            .getOrDefault(PatternCheck.TIMEOUT)

    suspend fun submitPattern(oldPin: String?, newPin: String): Boolean = runCatching {
        session.updateHomesecPassword(
            enable = true,
            oldMd5 = oldPin?.let { CameraLiveSession.md5Hex(it) },
            newMd5 = CameraLiveSession.md5Hex(newPin),
        ).error == null
    }.getOrDefault(false).also { refresh() }

    suspend fun resetPattern(): Boolean =
        runCatching { session.resetHomesecPassword().error == null }.getOrDefault(false).also { refresh() }

    companion object {
        const val ACTIVATION_SECONDS = 60
        const val WRITE_SETTLE_MS = 500L
        const val STEP_GAP_MS = 300L
    }
}
