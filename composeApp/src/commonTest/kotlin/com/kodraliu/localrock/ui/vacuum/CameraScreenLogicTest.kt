package com.kodraliu.localrock.ui.vacuum

import com.kodraliu.localrock.shared.vacuum.camera.CameraStartErrors
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CameraScreenLogicTest {
    @Test
    fun menuHasTheOfficialRowsThenExperiments() {
        assertEquals(
            listOf(
                CameraMenuItem.RemoteViewingSettings,
                CameraMenuItem.ScheduledCruise,
                CameraMenuItem.History,
                CameraMenuItem.VideoExperiments,
            ),
            CameraMenu.items(canNavigate = true, cruiseSupported = true),
        )
        assertEquals("Remote Viewing Settings", CameraMenuItem.RemoteViewingSettings.label)
        assertEquals("Scheduled Cruise", CameraMenuItem.ScheduledCruise.label)
        assertTrue(CameraMenuItem.VideoExperiments.beta)
        assertFalse(CameraMenuItem.History.beta)
    }

    @Test
    fun menuDropsCruiseWithoutSupportAndNavigationWithoutHost() {
        assertEquals(
            listOf(CameraMenuItem.RemoteViewingSettings, CameraMenuItem.History, CameraMenuItem.VideoExperiments),
            CameraMenu.items(canNavigate = true, cruiseSupported = false),
        )
        assertEquals(listOf(CameraMenuItem.VideoExperiments), CameraMenu.items(canNavigate = false, cruiseSupported = true))
    }

    @Test
    fun autoHideCountsDownFromTenSeconds() {
        assertEquals(10_000L, CameraAutoHide.delayLeftMs(0))
        assertEquals(4_000L, CameraAutoHide.delayLeftMs(6_000))
        assertEquals(0L, CameraAutoHide.delayLeftMs(12_000))
    }

    @Test
    fun autoHideOnlyWhileStreamingWithNothingOpen() {
        assertTrue(CameraAutoHide.allowed(streaming = true, panelOpen = false, menuOpen = false, dialogOpen = false))
        assertFalse(CameraAutoHide.allowed(streaming = false, panelOpen = false, menuOpen = false, dialogOpen = false))
        assertFalse(CameraAutoHide.allowed(streaming = true, panelOpen = true, menuOpen = false, dialogOpen = false))
        assertFalse(CameraAutoHide.allowed(streaming = true, panelOpen = false, menuOpen = true, dialogOpen = false))
        assertFalse(CameraAutoHide.allowed(streaming = true, panelOpen = false, menuOpen = false, dialogOpen = true))
    }

    @Test
    fun appCodeIsReadFromTheDisplayedMessage() {
        val shown = CameraStartErrors.fromRobotCode(-10011).display()
        assertEquals(CameraStartErrors.IN_CHARGE, CameraHelp.appCodeOf(shown))
        assertNull(CameraHelp.appCodeOf("Network down"))
    }

    @Test
    fun helpPutsTheMatchingFixFirstAndHasNoDuplicates() {
        val tips = CameraHelp.tips(CameraStartErrors.ALREADY_IN_PREVIEW)
        assertEquals(CameraStartErrors.fromRobotCode(-10013).message, tips.first())
        assertEquals(tips.distinct(), tips)
        assertTrue(CameraStartErrors.fromRobotCode(-10021).message in tips)
        assertTrue(CameraHelp.hasMatch(CameraStartErrors.ALREADY_IN_PREVIEW))
    }

    @Test
    fun helpWithoutAKnownCodeListsEveryFix() {
        val tips = CameraHelp.tips(null)
        assertEquals(CameraHelp.tips(CameraStartErrors.RETRIES_EXHAUSTED), tips)
        assertEquals(9, tips.size)
        assertFalse(CameraHelp.hasMatch(null))
        assertFalse(CameraHelp.hasMatch(CameraStartErrors.RETRIES_EXHAUSTED))
    }
}
