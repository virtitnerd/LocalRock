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

    // Carpet Cleaning Setting mode cards (wire 0 Avoid, 1 Rise, 2 Vac & Mop, 3 Dynamic Lift).
    const val CARPET_VACUUM_ONLY = "Vacuum Only" // verified
    const val CARPET_AVOID = "Avoid Carpet" // verified
    const val CARPET_CROSS = "Cross" // verified
    const val CARPET_VAC_AND_MOP = "Vac & Mop" // verified

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
    const val DOCK_MAINTENANCE = "Dock Maintanence" // verified
    const val FILL_AND_DRAIN = "Fill&Drain Settings" // verified
    const val DRAIN_ONBOARD_TANK = "Drain On-Board Water Tank" // verified

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
