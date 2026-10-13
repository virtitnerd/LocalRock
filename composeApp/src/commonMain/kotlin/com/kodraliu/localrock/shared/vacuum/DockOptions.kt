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

    /** "Every trip" and so on, indexed by `wash_interval - 1`; the robot's own numbers can be larger. */
    val washFrequency = listOf("Every trip", "Every 2 trips", "Every 3 trips")
}

/** The label for [code], or "Other (code)" so a value this app has no name for is still shown. */
fun List<DockChoice>.labelFor(code: Int): String =
    firstOrNull { it.code == code }?.label ?: "Other ($code)"

/** Whether the trip-count picker may write [washInterval]; outside 1-3 its meaning is not known. */
fun washFrequencyEditable(washInterval: Int?): Boolean =
    washInterval == null || washInterval in 1..DockOptions.washFrequency.size

/**
 * The label for the mop wash frequency. [washInterval] is the robot's raw `wash_interval`; a Qrevo
 * reports 1200 where older docks report 1-3, and that must not be mistaken for "every trip".
 */
fun washFrequencyLabel(washFreq: Int, washInterval: Int?): String {
    val options = DockOptions.washFrequency
    if (washInterval != null && washInterval !in 1..options.size) return "Other ($washInterval)"
    return options.getOrElse(washFreq) { options.first() }
}
