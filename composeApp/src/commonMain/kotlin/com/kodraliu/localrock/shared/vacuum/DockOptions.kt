package com.kodraliu.localrock.shared.vacuum

/** One value a dock setting can have: the code the robot uses, and what to call it. */
data class DockChoice(val code: Int, val label: String)

/**
 * The values of the dock settings, keyed by the code the robot reports and accepts.
 *
 * A picker has to match on the code, not on its position in a list: docks report codes outside any
 * short fixed list (a Qrevo-class dock answers 4 for auto-empty and 10 for wash mode), and a
 * position match then selects nothing at all.
 */
object DockOptions {
    /** set_dust_collection_mode. Codes and names follow python-roborock's dust collection modes. */
    val autoEmpty = listOf(
        DockChoice(0, "Smart"),
        DockChoice(1, "Light"),
        DockChoice(2, "Balanced"),
        DockChoice(4, "Max"),
    )

    /** set_wash_towel_mode. 0-2 keep the names this app always used; 8 and 10 are python-roborock's. */
    val washMode = listOf(
        DockChoice(10, "Smart"),
        DockChoice(0, "Light"),
        DockChoice(1, "Standard"),
        DockChoice(2, "Intensive"),
        DockChoice(8, "Super intensive"),
    )

    /** Older docks count trips: `wash_interval` 1, 2 or 3. */
    val washTrips = listOf(
        DockChoice(1, "Every trip"),
        DockChoice(2, "Every 2 trips"),
        DockChoice(3, "Every 3 trips"),
    )

    /** Docks that report `wash_interval` in seconds (a Qrevo answers 1200 for every 20 minutes). */
    val washMinutes = listOf(10, 15, 20, 25, 30).map { DockChoice(it * 60, "Every $it min") }

    /** Anything below this is a trip count, not seconds. */
    private const val SMALLEST_SECONDS_INTERVAL = 60

    fun isSecondsInterval(washInterval: Int): Boolean = washInterval >= SMALLEST_SECONDS_INTERVAL

    /**
     * set_wash_water_temperature. Names match the official app's "Washing Water Temperature"
     * screen; the codes are inferred from a robot reporting 2 while "Hot Water" was selected there,
     * not independently confirmed.
     */
    val washWaterTemperature = listOf(
        DockChoice(0, "Room temperature"),
        DockChoice(1, "Warm water"),
        DockChoice(2, "Hot water"),
    )

    /**
     * set_collision_avoid_status. The official app's on/off switch and its "Less Collision Mode"
     * submenu (None/Balanced/Sensitive) both read and write this one code. 0 as "off" and the
     * Balanced/Sensitive order are inferred, not independently confirmed; a robot reporting 1 was
     * seen with Balanced selected.
     */
    val collisionAvoidance = listOf(
        DockChoice(0, "Off"),
        DockChoice(1, "Balanced"),
        DockChoice(2, "Sensitive"),
    )

    /**
     * set_dust_collection_switch_status, the dock's auto-empty frequency (separate from the
     * suction-strength Empty Mode). Only the top value is confirmed: a robot reporting 2 was seen
     * with "High" selected. The rest of the scale and its exact codes are a guess pending capture.
     */
    val dustCollectionFrequency = listOf(
        DockChoice(0, "Low"),
        DockChoice(1, "Medium"),
        DockChoice(2, "High"),
    )

    /** app_set_dryer_setting's on.dry_time, in seconds. A robot reporting 7200 was seen with "2 h". */
    val dryTime = listOf(1, 2, 3, 4).map { DockChoice(it * 3600, "$it h") }
}

/** The label for [code], or "Other (code)" so a value this app has no name for is still shown. */
fun List<DockChoice>.labelFor(code: Int): String =
    firstOrNull { it.code == code }?.label ?: "Other ($code)"

/**
 * Which washing modes the robot offers, as the official app does: Light, Standard and Intensive
 * always, then Smart where the robot supports dirty-replenish cleaning, otherwise Super intensive
 * where it supports super-deep wash. With nothing known yet every mode is offered, as before.
 */
fun washModeChoices(features: DeviceFeatures?): List<DockChoice> {
    val basic = DockOptions.washMode.filter { it.code in 0..2 }
    val smart = DockOptions.washMode.first { it.code == 10 }
    val superIntensive = DockOptions.washMode.first { it.code == 8 }
    return when {
        features == null -> listOf(smart) + basic + superIntensive
        features.dirtyReplenishClean -> basic + smart
        features.superDeepWash -> basic + superIntensive
        else -> basic
    }
}

/** The choices for the mop wash frequency, in the unit this dock reports. */
fun washIntervalChoices(washInterval: Int?): List<DockChoice> =
    if (washInterval != null && DockOptions.isSecondsInterval(washInterval)) DockOptions.washMinutes
    else DockOptions.washTrips

/**
 * Whether the picker may write the mop wash frequency. It needs the robot's own value first, in a
 * unit this app recognises (a trip count or a seconds interval) — not that `smart_wash` is already
 * 0: choosing any interval always sends `smart_wash: 0` itself (confirmed from the official app's
 * own source), so this also doubles as the way to turn Smart wash back off.
 */
fun washFrequencyEditable(washInterval: Int?): Boolean =
    washInterval != null && (washInterval in 1..DockOptions.washTrips.size || DockOptions.isSecondsInterval(washInterval))

/** The label for the mop wash frequency, from the robot's raw `wash_interval` and `smart_wash`. */
fun washFrequencyLabel(washInterval: Int?, smartWash: Int): String = when {
    washInterval == null -> "Unknown"
    smartWash != 0 -> "Smart wash"
    DockOptions.isSecondsInterval(washInterval) ->
        if (washInterval % 60 == 0) "Every ${washInterval / 60} min" else "Every $washInterval s"
    else -> DockOptions.washTrips.labelFor(washInterval)
}

/** How long a drying run lasts, from the robot's seconds: "2 h", "90 min". */
fun dryTimeLabel(seconds: Int): String = when {
    seconds % 3600 == 0 -> "${seconds / 3600} h"
    seconds % 60 == 0 -> "${seconds / 60} min"
    else -> "$seconds s"
}
