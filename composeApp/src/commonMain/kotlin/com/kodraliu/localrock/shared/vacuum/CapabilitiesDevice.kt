package com.kodraliu.localrock.shared.vacuum

// Device page flags (track B2), read from new_feature_info_str bits the plugin names.
private const val CLEAN_THEN_MOP_BIT = 93 // CleanThenMopMode
private const val OVERSEA_CTM_BIT = 114 // OverSeaCTM
private const val CTM_WITH_REPEAT_BIT = 102 // CtmWithRepeat
private const val FORBID_PURE_MOP_BIT = 130 // ForbidPureMop
private const val MIDWAY_BACK_TO_DOCK_BIT = 85 // MidwayBackToDock

data class DevicePageFlags(
    // Official gate isSupportMainPageSmartCommand needs apiLevel and dynamic-zone flags LocalRock cannot read.
    val routineTabKnown: Boolean,
    val cleanThenMop: Boolean,
    val cleanThenMopRepeat: Boolean,
    val pureMop: Boolean,
    val midwayDocking: Boolean,
)

// Clean-then-mop needs bit 93; FCC robots also need 114, and as LocalRock cannot read the FCC flag it always asks for both.
fun devicePageFlags(newFeatureSet: String?): DevicePageFlags {
    fun bit(n: Int) = newFeatureBit(newFeatureSet, n)
    val ctm = bit(CLEAN_THEN_MOP_BIT) && bit(OVERSEA_CTM_BIT)
    return DevicePageFlags(
        routineTabKnown = false,
        cleanThenMop = ctm,
        cleanThenMopRepeat = ctm && bit(CTM_WITH_REPEAT_BIT),
        pureMop = !bit(FORBID_PURE_MOP_BIT),
        midwayDocking = bit(MIDWAY_BACK_TO_DOCK_BIT),
    )
}

/** Device page tabs: Routine shows when the official gate is known true or, failing that, when routines exist. */
fun visibleMainTabs(capabilities: Capabilities, routineCount: Int): List<MainTab> =
    if (capabilities.hasRoutineTab || routineCount > 0) listOf(MainTab.FULL, MainTab.ROOM, MainTab.ZONE, MainTab.ROUTINE)
    else listOf(MainTab.FULL, MainTab.ROOM, MainTab.ZONE)
