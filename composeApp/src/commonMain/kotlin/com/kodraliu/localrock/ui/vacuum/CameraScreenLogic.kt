package com.kodraliu.localrock.ui.vacuum

import com.kodraliu.localrock.shared.vacuum.camera.CameraStartErrors
import com.kodraliu.localrock.ui.strings.RemoteStrings

/** Rows of the live view's top bar popup, in the official order, with our Video experiments last. */
enum class CameraMenuItem(val label: String, val beta: Boolean = false) {
    RemoteViewingSettings(RemoteStrings.REMOTE_VIEWING_SETTINGS),
    ScheduledCruise(RemoteStrings.SCHEDULED_CRUISE),
    History(RemoteStrings.HISTORY),
    VideoExperiments(RemoteStrings.VIDEO_EXPERIMENTS, beta = true),
}

object CameraMenu {
    /** Navigation rows need a navigator from the host; Scheduled Cruise also needs cruise support. */
    fun items(canNavigate: Boolean, cruiseSupported: Boolean): List<CameraMenuItem> = buildList {
        if (canNavigate) {
            add(CameraMenuItem.RemoteViewingSettings)
            if (cruiseSupported) add(CameraMenuItem.ScheduledCruise)
            add(CameraMenuItem.History)
        }
        add(CameraMenuItem.VideoExperiments)
    }
}

// The official live view hides its controls after 10 s without a touch; a tap brings them back.
object CameraAutoHide {
    const val HIDE_AFTER_MS = 10_000L

    fun delayLeftMs(idleMs: Long): Long = (HIDE_AFTER_MS - idleMs).coerceAtLeast(0)

    /** Never hides while a panel, menu or dialog is open, since touches there do not reach the timer. */
    fun allowed(streaming: Boolean, panelOpen: Boolean, menuOpen: Boolean, dialogOpen: Boolean): Boolean =
        streaming && !panelOpen && !menuOpen && !dialogOpen
}

/** Fix texts for the Help page, reusing the official start error table. */
object CameraHelp {
    private val ROBOT_CODES = listOf(-10011, -10013, -10021, -10012, -10014, -10016, -10015, -10009, null)
    private val APP_CODE = Regex("""\(error (-\d+)\)""")

    /** The app code from a failure message built by CameraStartError.display(), if there is one. */
    fun appCodeOf(message: String): Int? = APP_CODE.find(message)?.groupValues?.get(1)?.toIntOrNull()

    /** Every distinct fix text, the one matching [appCode] first. */
    fun tips(appCode: Int?): List<String> {
        val errors = ROBOT_CODES.map { CameraStartErrors.fromRobotCode(it) }
        val first = errors.firstOrNull { it.appCode == appCode }?.message
        return (listOfNotNull(first) + errors.map { it.message }).distinct()
    }

    /** True when [appCode] has its own fix text, so the Help page can mark the first tip as the likely one. */
    fun hasMatch(appCode: Int?): Boolean =
        appCode != null && ROBOT_CODES.any { CameraStartErrors.fromRobotCode(it).appCode == appCode }
}
