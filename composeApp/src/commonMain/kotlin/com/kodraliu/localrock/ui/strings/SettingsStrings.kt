package com.kodraliu.localrock.ui.strings

// Settings wording for the redesign (track B1). Each entry is tagged: verified = official wording seen in
// the plugin code or a screenshot (PROPOSAL.md section 4), inferred = our guess or a LocalRock-only label.
object SettingsStrings {
    const val TITLE = "Settings" // verified
    const val COMING_SOON = "Coming soon in the redesign" // inferred
    const val REMOVE_DEVICE = "Remove from LocalRock" // inferred

    // Root rows, in the official group order.
    const val SCHEDULES = "Schedules" // verified
    const val SCHEDULES_NEXT = "The next cleaning will start at {time} today." // verified
    const val MAINTENANCE = "Maintenance" // verified
    const val MAINTENANCE_NORMAL = "Normal" // verified
    const val CARPET = "Carpet Cleaning Setting" // verified
    const val FLOOR = "Floor Cleaning Setting" // verified
    const val DOCK = "Dock Settings" // verified
    const val AUTO_EMPTY = "Auto-Empty Settings" // verified
    const val OBSTACLE = "Reactive Obstacle Avoidance" // verified
    const val MANAGE_MAPS = "Manage Maps" // verified
    const val ROBOT_VOICE = "Robot Voice" // verified
    const val VOICE_ASSISTANT = "Voice Assistant" // verified
    const val REMOTE_VIEWING = "Remote Viewing" // verified
    const val MATTER = "Connect to the Matter Network" // verified
    const val MORE_OPTIONS = "More Options" // verified
    const val PIN_N_GO = "Pin n Go" // verified
    const val REMOTE_CONTROL = "Remote Control" // verified
    const val USER_MANUAL = "User Manual" // verified
    const val PRODUCT_INFO = "Product Info" // verified
    const val FIRMWARE_UPDATES = "Firmware Updates" // verified
    const val BETA_FEATURES = "Beta features" // inferred
    const val BETA_FEATURES_DETAIL = "Other options your robot reports" // inferred
    const val HISTORY_LAST_DURATION = "Last cleaning duration" // verified
    const val HISTORY_LAST_AREA = "Last cleaning area" // verified
    const val HISTORY = "History" // verified
    const val MAINTENANCE_DUE = "Some supplies need cleaning or replacing" // inferred
    const val NO_VALUE = "--" // inferred
    const val SCHEDULES_DETAIL = "Clean on a timetable" // inferred
    const val CARPET_DETAIL = "Adjust settings to improve carpet cleaning performance." // verified
    const val FLOOR_DETAIL = "Floor types, edges and deep cleaning" // inferred
    const val DOCK_DETAIL = "Wash, empty and dry options" // inferred
    const val OBSTACLE_DETAIL = "Intelligently recognizes and avoids obstacles that may block its way." // verified
    const val MANAGE_MAPS_DETAIL = "Edit rooms, zones and saved maps" // inferred
    const val ROBOT_VOICE_DETAIL = "Volume" // inferred
    const val ROBOT_VOICE_DETAIL_LEVEL = "Volume {level}%" // inferred
    const val REMOTE_VIEWING_DETAIL = "Camera and video settings" // inferred
    const val MATTER_DETAIL = "Add the robot to Apple Home, Google Home and more" // inferred
    const val MORE_OPTIONS_DETAIL = "Do Not Disturb, charging, lights and child lock" // inferred
    const val PIN_N_GO_DETAIL = "Send the robot to a spot on the map" // inferred
    const val REMOTE_CONTROL_DETAIL = "Drive the robot by hand" // inferred
    const val PRODUCT_INFO_DETAIL = "Name, model, firmware and serial number" // inferred
    const val FIRMWARE_DETAIL = "Check for and install updates" // inferred

    // More Options page.
    const val DND = "Do Not Disturb Mode" // verified
    const val DND_CUSTOMIZE = "Customize Do Not Disturb Mode" // verified
    const val CHARGING_MODE = "Charging Mode" // verified
    const val SMART_CHARGING = "Smart Charging" // verified
    const val OFF_PEAK_CHARGING = "Off-Peak Charging" // verified
    const val BUTTON_LIGHTS = "Button Lights" // verified
    const val STATUS_INDICATOR_LIGHT = "Status Indicator Light" // verified
    const val VOICE_ASSISTANT_INDICATOR_LIGHT = "Voice Assistant Indicator Light" // verified
    const val CHILD_LOCK = "Child Lock" // verified
    const val OFFLINE_MAP = "Offline Map" // verified
    const val ADJUSTED_BATTERY_LEVEL = "Adjusted Battery Level" // verified
    const val FIND_ROBOT = "Find Robot" // verified
    const val SHOW_LAST_KNOWN_OFFLINE = "Show last-known state offline" // inferred
    const val ON = "On" // verified
    const val OFF = "Off" // verified
    const val DND_DETAIL = "To minimize disturbance, some automatic operations will not be performed during the DND period." // verified
    const val BUTTON_LIGHTS_DETAIL = "Lights on the robot's buttons" // inferred
    const val CHILD_LOCK_DETAIL = "When the robot is stationary, the buttons on it are locked. When the robot is in motion, press any button to stop it." // verified
    const val ADJUSTED_BATTERY_LEVEL_DETAIL = "The battery level will be calculated more accurately. Runtime may be slightly reduced to accommodate this adjustment." // verified
    const val FIND_ROBOT_RINGING = "The robot is playing a sound" // inferred
    const val MORE_ROBOT_OPTIONS = "Other options your robot reports" // inferred
    const val SERVER_CONNECTION_CAPTION = "Server connection" // inferred

    // Charging Mode page.
    const val SMART_CHARGING_DETAIL = "During charging, the robot will typically charge to 80% before slowly trickle charging to 100% to optimize battery performance." // verified
    const val OFF_PEAK_CHARGING_DETAIL = "The robot will only fully charge during off-peak hours, while minimum power will be maintained at other times." // verified
    const val OFF_PEAK_NOT_SET = "* No charging time set. Off-peak charging inactive." // verified
    const val START = "Start" // verified
    const val END = "End" // verified
    const val SAME_TIME_WARNING = "The start and end time are the same. Please adjust them." // verified

    // Product Info page.
    const val NAME = "Name" // verified
    const val RENAME = "Rename" // verified
    const val DEVICE_MODEL = "Device Model" // verified
    const val MODEL = "Model" // verified
    const val SERIAL_NUMBER = "Serial Number" // verified
    const val FIRMWARE_VERSION = "Firmware Version" // verified
    const val SERVER = "Server" // inferred
    const val DEVICE_ID = "Device ID" // inferred
    const val UNKNOWN = "Unknown" // inferred
    const val SAVE = "Save" // inferred
    const val CANCEL = "Cancel" // verified
    const val RENAMED = "Robot renamed" // inferred
    const val RENAME_FAILED = "The robot could not be renamed" // inferred
    const val RENAME_DEMO = "Robots cannot be renamed in demo mode" // inferred

    // Carpet Cleaning Setting mode cards (wire 0 Avoid, 1 Rise, 2 Vac & Mop, 3 Dynamic Lift).
    const val CARPET_VACUUM_ONLY = "Vacuum Only" // verified
    const val CARPET_AVOID = "Avoid Carpet" // verified
    const val CARPET_CROSS = "Cross" // verified
    const val CARPET_VAC_AND_MOP = "Vac & Mop" // verified
    const val CARPET_DYNAMIC_LIFT = "Dynamic Lift" // verified
    const val CARPET_VACUUM_ONLY_DETAIL = "Suitable for low pile carpets or mats. When the mop mount is attached, the robot will lift the mopping module and vacuum the carpet." // verified
    const val CARPET_AVOID_DETAIL = "Suitable for low to medium pile carpets. The robot will avoid carpets altogether." // verified
    const val CARPET_VAC_AND_MOP_DETAIL = "Suitable for environments where there is no carpet or where mats can be mopped (i.e. rubber mats)." // verified
    const val CARPET_DYNAMIC_LIFT_DETAIL = "Applicable to mats or small-sized carpets. When a carpet is detected, the mop is lifted and water flow is stopped for carpet vacuuming." // inferred
    const val CARPET_ADVANCED = "More Advanced Settings" // verified
    const val CARPET_FOOTER = "* Carpet cleaning settings do not apply to custom carpets." // verified

    // Floor Cleaning Setting rows.
    const val SEQUENCE = "Sequence" // verified
    const val CLEAN_ALONG_FLOOR_DIRECTION = "Clean along floor direction" // verified
    const val CLEANING_PATTERN = "Cleaning Pattern" // verified
    const val PATTERN_SMART = "Smart" // verified
    const val PATTERN_ALONG_FLOOR = "Along floor direction" // verified
    const val PATTERN_MIXED = "Mixed" // verified
    const val MOP_MOUNTS = "Auto-Detach/Reinstall Mop Cloth Mounts" // verified
    const val LIDAR_LOWERING = "LiDAR Sensor Lowering and Under-Furniture Cleaning" // verified
    const val EXTENDED_CORNER = "Extended Corner Cleaning" // verified
    const val EXTENDED_EDGE_MOPPING = "Extended Edge Mopping" // verified
    const val EXTENDED_CREVICE = "Extended Crevice Cleaning (Beta)" // verified
    const val AUTO_REMOPPING = "Automatic Re-Mopping" // verified
    const val DEEP_CLEAN_HEAVY_DIRT = "Deep Cleaning for Heavy Dirt or Stains (Beta)" // verified
    const val PET_AREA_DEEP_CLEAN = "Pet Area Deep Cleaning" // verified
    const val CLEAN_BLOCKED_AREAS = "Clean Areas Blocked by People/Pets" // verified
    const val UNCLEANED_AREA_REMINDER = "Uncleaned Area Reminder" // verified

    // Dock Settings rows, four groups.
    const val MOP_WASH_FREQUENCY = "Mop Wash Frequency" // verified
    const val WASHING_MODE = "Washing Mode" // verified
    const val MOP_WASH_TEMPERATURE = "Mop Washing Temperature" // verified
    const val SMART_SOLUTION_FILLING = "Smart Cleaning Solution Filling" // verified
    const val SOLUTION_FILLING = "Cleaning Solution Filling" // verified
    const val AUTONOMOUS_MOP_WASHING = "Autonomous Mop Washing" // verified
    const val AUTO_EMPTYING = "Auto Emptying" // verified
    const val AUTO_EMPTY_FREQUENCY = "Dustbin Auto-Empty Frequency" // verified
    const val EMPTY_MODE = "Empty Mode" // verified
    const val AUTO_DRYING = "Auto Drying" // verified
    const val DRYING_START_METHOD = "Drying Start Method" // verified
    const val DRYING_DURATION = "Duration" // verified
    const val UVC_STERILIZATION = "UVC Sterilization" // verified
    const val DOCK_MAINTENANCE = "Dock Maintenance" // verified (official spells it Maintanence)
    const val FILL_AND_DRAIN = "Fill&Drain Settings" // verified
    const val DRAIN_ONBOARD_TANK = "Drain On-Board Water Tank" // verified
    const val DRAIN_ONBOARD_TANK_DETAIL = "Empty through the dock" // inferred
    const val DRAIN_CONFIRM_TITLE = "Empty the water tank?" // inferred
    const val DRAIN_CONFIRM_TEXT = "The dock pumps the water out of the robot's built-in tank. Make sure the robot is on the dock." // inferred
    const val DRAIN_CONFIRM = "Empty" // inferred
    const val MOP_DRYING_ON = "Dries the mops on the dock after cleaning" // inferred
    const val MOP_DRYING_OFF = "Mops are left wet on the dock" // inferred

    // Reactive Obstacle Avoidance page.
    const val OBSTACLE_PHOTOS = "Obstacle Photos" // verified
    const val ITEM_RECOGNITION = "Manage Item Recognition" // verified
    const val LESS_COLLISION = "Less Collision Mode" // verified
    const val PET = "Pet" // verified
    const val PET_SNAPS = "Pet Snaps" // verified
    const val AI_ENVIRONMENT = "AI Environment Recognition" // verified
    const val FILL_LIGHT = "Fill Light" // verified
    const val FILL_LIGHT_OFF = "Off" // verified
    const val FILL_LIGHT_AUTOMATIC = "Automatic" // verified

    // Robot Voice and Voice Assistant pages.
    const val VOLUME = "Volume" // verified
    const val VOLUME_HINT = "Slide to adjust volume" // verified
    const val LANGUAGE = "Language" // verified
    const val CUSTOM_TONES = "Custom Tones" // verified
    const val VOICE_USE = "Use" // verified
    const val VOICE_CURRENT = "Current" // verified
    const val VOICE_UPDATE = "Update" // verified
    const val CONTINUOUS_DIALOGUE = "Continuous dialogue" // verified
    const val CHAT_HISTORY = "Chat History" // verified
    const val VOICE_COMMANDS = "Voice Commands" // verified

    // Remote Viewing settings page.
    const val REMOTE_VIEWING_SETTINGS = "Remote Viewing Settings" // verified
    const val CALL_VOLUME = "Call volume" // inferred
    const val SECURITY_SETTINGS = "Security settings" // inferred
}
