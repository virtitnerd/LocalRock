package com.kodraliu.localrock.shared.vacuum

// Device page flags (track B2); clean-then-mop, pure mop and midway docking come from Capabilities.advertised.
data class DevicePageFlags(
    // Official gate isSupportMainPageSmartCommand needs apiLevel and dynamic-zone flags LocalRock cannot read.
    val routineTabKnown: Boolean,
)

@Suppress("UNUSED_PARAMETER")
fun devicePageFlags(newFeatureSet: String?): DevicePageFlags = DevicePageFlags(routineTabKnown = false)

/** Device page tabs: Routine shows when the official gate is known true or, failing that, when routines exist. */
fun visibleMainTabs(capabilities: Capabilities, routineCount: Int): List<MainTab> =
    if (capabilities.hasRoutineTab || routineCount > 0) listOf(MainTab.FULL, MainTab.ROOM, MainTab.ZONE, MainTab.ROUTINE)
    else listOf(MainTab.FULL, MainTab.ROOM, MainTab.ZONE)
