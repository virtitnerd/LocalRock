package com.kodraliu.localrock.shared.schedule

/** The official app's caps: 10 schedules in all, 5 of them cruise schedules. */
const val MAX_SCHEDULES = 10
const val MAX_CRUISE_SCHEDULES = 5

enum class ScheduleLimit { NONE, SCHEDULES, CRUISE }

/** Which official limit adding one more schedule would break, given what the robot already holds. */
fun scheduleLimitFor(existing: Int, existingCruise: Int, addingCruise: Boolean): ScheduleLimit = when {
    existing >= MAX_SCHEDULES -> ScheduleLimit.SCHEDULES
    addingCruise && existingCruise >= MAX_CRUISE_SCHEDULES -> ScheduleLimit.CRUISE
    else -> ScheduleLimit.NONE
}

/** Task tabs the editor offers; Routine and Cruise need a schedule action the server job cannot carry yet. */
enum class ScheduleTask { FULL, ROOMS, ROUTINE, CRUISE }

data class ScheduleTaskTab(val task: ScheduleTask, val enabled: Boolean)

// server_scheduled_start only takes clean settings (ServerSchedule.EDITABLE_SETTINGS), so neither a routine
// nor patrol points can be scheduled. Routine stays hidden; Cruise shows disabled on cruise robots.
fun scheduleTaskTabs(patrolRobot: Boolean, routineSchedules: Boolean = false, cruiseSchedules: Boolean = false): List<ScheduleTaskTab> =
    buildList {
        add(ScheduleTaskTab(ScheduleTask.FULL, true))
        add(ScheduleTaskTab(ScheduleTask.ROOMS, true))
        if (routineSchedules) add(ScheduleTaskTab(ScheduleTask.ROUTINE, true))
        if (patrolRobot) add(ScheduleTaskTab(ScheduleTask.CRUISE, cruiseSchedules))
    }
