package com.kodraliu.localrock.ui.strings

// History, Schedules, Maintenance and Firmware wording for the redesign (track B4). verified = official
// wording (PROPOSAL.md section 6), inferred = our guess. {n}, {hour} mark values the caller fills in.
object SecondaryStrings {
    // History.
    const val HISTORY = "History" // verified
    const val TOTAL_AREA = "Total Area" // verified
    const val TOTAL_TIME = "Total Time" // verified
    const val WORKING_TIMES = "Working Times" // verified
    const val TIMES_UNIT = "time(s)" // verified
    const val MIN_UNIT = "min" // verified
    const val TAB_CLEANING = "Cleaning" // verified
    const val TAB_CRUISE = "Cruise" // verified
    const val DELETE_RECORD = "Delete this record" // verified
    const val NO_RECORDS = "No records" // verified
    const val FINISHED_CLEANING = "Finished cleaning" // verified
    const val HIDE = "Hide" // verified
    const val DETAILS = "Details" // verified
    const val ERROR_OCCURS = "An error occurs." // verified
    const val PATH_ABNORMAL = "Cleaning path abnormal." // verified
    const val CLEANING_TIME = "Cleaning Time" // verified
    const val CLEANING_AREA = "Cleaning Area" // verified
    const val CRUISE_DETAILS = "Cruise details" // verified
    const val CRUISE_POINT = "Cruise point {n}" // verified
    const val PET_FINDING_DETAILS = "Pet finding details" // verified
    const val PET_PHOTOS = "Pet photos" // verified
    const val CRUISE_COMPLETED = "Cruise completed" // verified
    const val CRUISE_FAILED = "Cruise failed" // verified
    const val PET_FOUND = "Pet found" // verified
    const val PET_FOUND_FAILED = "Pet found failed" // verified
    const val RECENT_CRUISE_TIP = "Only the most recent cruise and pet-finding record is saved." // verified
    const val SAVE_PICTURE = "Save Picture" // verified
    const val LOAD_HISTORY = "Load history" // inferred
    const val MAP_FAILED = "Couldn't load this run's map" // inferred
    const val NO_TIMELINE = "No timeline for this run" // inferred
    const val NO_CRUISE_POINTS = "The robot sent no cruise points for this run." // inferred
    const val NO_POINT_PHOTOS = "No photos for this point." // inferred
    const val CLOSE = "Close" // inferred
    const val RETRY = "Retry" // verified

    // Schedules list and editor.
    const val SCHEDULES = "Schedules" // verified
    const val ADD_SCHEDULE = "Add Schedule" // verified
    const val NO_SCHEDULES = "No cleaning schedules set" // verified
    const val NO_SCHEDULES_DESC = "You can create schedules to set when the robot starts." // verified
    const val MAX_SCHEDULES = "Maximum number of schedules reached." // verified
    const val MAX_CRUISE_SCHEDULES = "The maximum number of scheduled cruise tasks has been reached." // verified
    const val DELETE_SCHEDULE = "Delete this schedule?" // verified
    const val SCHEDULE_INVALID = "Invalid" // verified
    const val SCHEDULE_ADJUST = "Adjust" // verified
    const val SCHEDULING = "Scheduling" // verified
    const val START = "Start" // verified
    const val NOT_SET = "Not Set" // verified
    const val REPEAT = "Repeat" // verified
    const val ONCE = "Once" // verified
    const val EVERYDAY = "Everyday" // verified
    const val WEEKDAYS = "Weekdays" // verified
    const val WEEKENDS = "Weekends" // verified
    const val CUSTOM = "Custom" // verified
    const val TASK = "Task" // verified
    const val TASK_FULL = "Full" // verified
    const val TASK_ROOMS = "Rooms" // verified
    const val TASK_ROUTINE = "Routine" // verified
    const val TASK_CRUISE = "Cruise" // verified
    const val CLEANING_MODE = "Cleaning Mode" // verified
    const val CRUISE_POINT_TIP = "Tap on map to set the cruise point" // verified
    const val NO_CRUISE_POINT = "No cruising point set." // verified
    const val CRUISE_SCHEDULE_UNSUPPORTED = "Scheduled cruise needs server support that LocalRock does not have yet." // inferred

    // Routines.
    const val MY_ROUTINES = "My Routines" // inferred
    const val RECOMMENDED_ROUTINES = "Recommended Routines" // inferred

    // Maintenance.
    const val MAINTENANCE = "Maintenance" // verified
    const val HOURS_REMAINING = "Estimated {hour} hours remaining" // verified
    const val USED = "Used" // verified
    const val REMAINING = "Remaining" // verified
    const val RESET = "Reset" // verified
    const val RESET_FAILED = "Reset Failed" // verified
    const val NORMAL = "Normal" // verified
    const val REPLACE_AS_NEEDED = "Replace as needed." // verified
    const val MUST_REPLACE = "Must replace" // verified
    const val REPLACEMENT_SUGGESTED = "Replacement suggested" // verified
    const val CONSUMABLE_LOAD_FAILED = "Could not load consumable data" // inferred
    const val MAIN_BRUSH = "Main Brush" // verified
    const val SIDE_BRUSH = "Side Brush" // verified
    const val FILTER = "Filter" // verified
    const val SENSORS = "Sensors" // verified
    const val MOP = "Mop" // verified
    const val DUST_BAG = "Dust Bag" // verified
    const val WATER_FILTER = "Water Filter" // verified
    const val CLEANING_TRAY = "Cleaning Tray" // verified
    const val MAINTENANCE_BRUSH = "Maintenance Brush" // verified
    const val WATER_TANK_FILTER = "Water Tank Filter" // verified
    const val DIRTY_WATER_TANK = "On-Board Dirty Water Tank" // verified
    const val DUSTBIN = "Dustbin" // verified
    const val TIMES_LEFT = "{n} time(s)" // inferred
    const val CONFIRM_MAIN_BRUSH = "Confirm if the main brush has been replaced." // verified
    const val CONFIRM_SIDE_BRUSH = "Confirm if side brush has been replaced." // verified
    const val CONFIRM_FILTER = "Confirm if filter has been replaced." // verified
    const val CONFIRM_SENSORS = "Confirm if sensors have been cleaned." // verified
    const val CONFIRM_SENSORS_BUTTON = "Cleaned (counter reset)" // verified
    const val CONFIRM_WATER_FILTER = "Water filter has been cleaned. OK?" // verified
    const val CONFIRM_CLEANING_TRAY = "Cleaning tray rinsed. OK?" // verified
    const val CONFIRM_MAINTENANCE_BRUSH = "Maintenance brush has been replaced. OK?" // verified
    const val CONFIRM_WATER_TANK_FILTER = "Water filters replaced?" // verified
    const val CONFIRM_DIRTY_WATER_TANK = "Confirm if the on-board dirty water tank has been cleaned." // verified
    const val CONFIRM_DUSTBIN = "Dustbin emptied. OK?" // verified
    const val DESC_MAIN_BRUSH = "The main brush directs dirt into the vacuum. Its bristles soften over time. Replace it after approximately 300 hours of use." // verified
    const val DESC_SIDE_BRUSH = "The side brush directs edge dirt to the main brush. Its bristles soften over time. Replace it after approximately 200 hours of use." // verified
    const val DESC_FILTER = "Air filter will clog over time. Rinse it every 2-weeks and replace it after approx. 150 hours." // verified
    const val DESC_SENSORS = "Sensors will get dusty after extended use. Wipe clean after approx. 30 hours of use." // verified
    const val DESC_WATER_TANK_FILTER = "Water filter will clog over time. Replace after approx. 100 hours." // verified
    const val DESC_WATER_FILTER = "The dock water filter separates particles from dirty water to prevent blockages. Rinse out the water filter at least once a month." // verified
    const val DESC_CLEANING_TRAY = "Large particles found during mop washing will be left in the cleaning tray to prevent water pipe blockage. Rinse the cleaning tray at least once a month." // verified
    const val OK = "OK" // verified
    const val CANCEL = "Cancel" // verified

    // Firmware.
    const val FIRMWARE_UPDATES = "Firmware Updates" // verified
    const val NEW_VERSION = "New Version Available" // verified
    const val CURRENT_VERSION = "Current Version" // verified
    const val LATEST_VERSION = "Latest Version" // verified
    const val UPDATE_NOW = "Update Now" // verified
    const val LATER = "Later" // verified
    const val EXIT = "Exit" // verified
    const val AUTO_UPDATES = "Enable automatic updates" // verified
    const val UP_TO_DATE = "Up to date" // inferred
    const val CHECKING = "Checking for updates…" // inferred
    const val CHECK_AGAIN = "Check again" // inferred
    const val LOCAL_INSTALL = "Install from your server" // inferred
}
