package com.kodraliu.localrock.shared.vacuum

import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals

class HistoryLabelsTest {
    // get_clean_summary from the Curv 2 Flow on 2026-11-13; the official header read 112 min and 6 time(s).
    private val curv = CleanSummary(totalTimeSec = 6700, totalAreaMm2 = 93_592_500, totalCount = 2, patrolCount = 4)

    @Test
    fun headerUsesMinutesAndAddsCruiseRuns() {
        assertEquals(112, totalMinutes(curv.totalTimeSec))
        assertEquals(6, workingTimes(curv))
        assertEquals("Working times 2, cruise times 4", workingTimesHelp(curv.totalCount, curv.patrolCount))
        assertEquals("Working times 3", workingTimesHelp(3, 0))
    }

    @Test
    fun stampsAreMonthDayAndTwentyFourHourClock() {
        // 1794587027 is 2026-11-13 16:23:47 UTC.
        assertEquals("11/13  16:23", historyStamp(1794587027, TimeZone.UTC))
        assertEquals("11/13  11:23", historyStamp(1794587027, TimeZone.of("America/New_York")))
        assertEquals("16:23", timelineClock(1794587027, TimeZone.UTC))
        assertEquals("01/02  03:04", historyStamp(1767323040, TimeZone.UTC))
    }

    @Test
    fun areaSplitsIntoValueAndUnit() {
        assertEquals("94" to "m²", areaParts(cleanAreaLabel(93_592_500)))
        assertEquals("29.9k" to "m²", areaParts(totalAreaLabel(29_927_845_000)))
        assertEquals("x" to "", areaParts("x"))
    }
}
