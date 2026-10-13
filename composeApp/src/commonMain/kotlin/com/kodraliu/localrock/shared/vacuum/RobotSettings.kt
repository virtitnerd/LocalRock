package com.kodraliu.localrock.shared.vacuum

data class RobotSettings(
    val volume: Int = 50,
    val childLock: Boolean = false,
    val ledEnabled: Boolean = true,
    val carpetMode: Int = 1, // 0=avoid, 1=standard, 2=boost
)

data class DockSettings(
    val washMode: Int = 1,      // robot code: 0=light, 1=standard, 2=intensive, 8=super, 10=smart (see DockOptions)
    val washInterval: Int? = null, // the robot's raw wash_interval: trips (1-3) on older docks, seconds on a Qrevo
    val smartWash: Int = 0,     // the robot's smart_wash; 0 means the interval above is what applies
    val autoEmptyMode: Int = 0, // robot code: 0=smart, 1=light, 2=balanced, 4=max (see DockOptions)
    val mopDrying: Boolean? = null, // automatic mop drying; null until the robot has answered
    val dryTimeSeconds: Int? = null, // how long a drying run lasts, as the robot reports it
    val features: DeviceFeatures? = null, // what the robot reports it supports; null until read
)

/** The valley (off-peak) electricity window the robot is limited to, in the robot's local time. */
data class ValleySettings(
    val enabled: Boolean = false,
    val startHour: Int = 22,
    val startMinute: Int = 0,
    val endHour: Int = 6,
    val endMinute: Int = 0,
)

data class CleanSummary(
    val totalTimeSec: Long,
    val totalAreaMm2: Long,
    val totalCount: Int,
)

data class DndSettings(
    val enabled: Boolean = false,
    val startHour: Int = 22,
    val startMinute: Int = 0,
    val endHour: Int = 8,
    val endMinute: Int = 0,
)

data class FloorMap(val name: String, val mapFlag: Int)
