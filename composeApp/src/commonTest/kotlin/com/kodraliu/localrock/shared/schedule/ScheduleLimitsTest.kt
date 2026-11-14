package com.kodraliu.localrock.shared.schedule

import kotlin.test.Test
import kotlin.test.assertEquals

class ScheduleLimitsTest {
    @Test
    fun tenSchedulesIsTheCap() {
        assertEquals(ScheduleLimit.NONE, scheduleLimitFor(9, 0, addingCruise = false))
        assertEquals(ScheduleLimit.SCHEDULES, scheduleLimitFor(10, 0, addingCruise = false))
        assertEquals(ScheduleLimit.SCHEDULES, scheduleLimitFor(12, 5, addingCruise = true))
    }

    @Test
    fun fiveCruiseSchedulesIsTheCruiseCap() {
        assertEquals(ScheduleLimit.NONE, scheduleLimitFor(6, 4, addingCruise = true))
        assertEquals(ScheduleLimit.CRUISE, scheduleLimitFor(6, 5, addingCruise = true))
        assertEquals(ScheduleLimit.NONE, scheduleLimitFor(6, 5, addingCruise = false))
    }

    @Test
    fun cruiseTabShowsDisabledOnlyOnCruiseRobots() {
        assertEquals(
            listOf(ScheduleTaskTab(ScheduleTask.FULL, true), ScheduleTaskTab(ScheduleTask.ROOMS, true), ScheduleTaskTab(ScheduleTask.CRUISE, false)),
            scheduleTaskTabs(patrolRobot = true),
        )
        assertEquals(listOf(ScheduleTask.FULL, ScheduleTask.ROOMS), scheduleTaskTabs(patrolRobot = false).map { it.task })
        assertEquals(
            listOf(ScheduleTask.FULL, ScheduleTask.ROOMS, ScheduleTask.ROUTINE, ScheduleTask.CRUISE),
            scheduleTaskTabs(patrolRobot = true, routineSchedules = true, cruiseSchedules = true).map { it.task },
        )
    }
}
