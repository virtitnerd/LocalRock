package com.kodraliu.localrock.ui.vacuum

import com.kodraliu.localrock.shared.vacuum.VacuumStateCodes
import com.kodraliu.localrock.shared.vacuum.camera.AlertLevel
import com.kodraliu.localrock.ui.strings.RemoteStrings

fun AlertLevel.label(): String = when (this) {
    AlertLevel.FLASH -> RemoteStrings.ALERT_FLASH
    AlertLevel.GENTLE -> RemoteStrings.ALERT_GENTLE
    AlertLevel.STRONG -> RemoteStrings.ALERT_STRONG
}

fun AlertLevel.description(): String = when (this) {
    AlertLevel.FLASH -> RemoteStrings.ALERT_FLASH_DESC
    AlertLevel.GENTLE -> RemoteStrings.ALERT_GENTLE_DESC
    AlertLevel.STRONG -> RemoteStrings.ALERT_STRONG_DESC
}

// RSM.isRunning was not read; these are the task states a revoke would interrupt (inferred).
private val TASK_STATES = setOf(
    VacuumStateCodes.CLEANING, VacuumStateCodes.SPOT_CLEANING, VacuumStateCodes.ZONED_CLEANING,
    VacuumStateCodes.SEGMENT_CLEANING, VacuumStateCodes.GOING_TO_TARGET, VacuumStateCodes.PATROLLING,
    VacuumStateCodes.MAPPING, VacuumStateCodes.PAUSED, VacuumStateCodes.RETURNING_HOME,
)

fun isTaskRunning(state: Int?): Boolean = state in TASK_STATES

/** Call volume slider bounds: 0..100 in steps of 5 (TopazSV starts at 5, not modelled). */
object CallVolume {
    const val DEFAULT = 50
    const val STEP = 5
    const val MAX = 100

    fun snap(value: Float): Int = ((value / STEP).let { kotlin.math.round(it) } * STEP).toInt().coerceIn(0, MAX)
}

/** Pattern pages of GesturePasswordPage that this app builds: mode 0 (set) and mode 3 (change). */
enum class PatternMode(val title: String) {
    SET(RemoteStrings.PATTERN_PASSWORD),
    CHANGE(RemoteStrings.CHANGE_PASSWORD),
}

sealed interface PatternAction {
    data class Show(val message: String, val wrong: Boolean = false) : PatternAction
    data class VerifyOld(val pin: String) : PatternAction
    data class Submit(val oldPin: String?, val newPin: String) : PatternAction
}

// The official draw sequence: change verifies the old pattern first, then the new one is drawn twice.
class PatternFlow(val mode: PatternMode) {
    var oldPin: String? = null
        private set
    private var firstNew: String? = null

    val initialMessage: String = if (mode == PatternMode.SET) RemoteStrings.DRAW_NEW else RemoteStrings.DRAW_EXISTING

    private val oldVerified: Boolean get() = mode == PatternMode.SET || oldPin != null

    /** "Forgot password" shows until the old pattern is verified, never while setting a first one. */
    val showsForgot: Boolean get() = !oldVerified

    fun onDrawn(pin: String): PatternAction {
        if (pin.length < MIN_DOTS) return PatternAction.Show(RemoteStrings.PATTERN_TOO_SHORT, wrong = true)
        if (!oldVerified) return PatternAction.VerifyOld(pin)
        val first = firstNew
        if (first == null) {
            firstNew = pin
            return PatternAction.Show(RemoteStrings.DRAW_AGAIN)
        }
        if (pin != first) return PatternAction.Show(RemoteStrings.PATTERNS_DIFFER, wrong = true)
        return PatternAction.Submit(oldPin, pin)
    }

    fun oldAccepted(pin: String): PatternAction.Show {
        oldPin = pin
        return PatternAction.Show(RemoteStrings.NEW_PASSWORD)
    }

    private companion object {
        const val MIN_DOTS = 4
    }
}
