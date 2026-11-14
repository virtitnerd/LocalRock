package com.kodraliu.localrock.ui.vacuum

import com.kodraliu.localrock.shared.vacuum.BetaFeatureDef
import com.kodraliu.localrock.shared.vacuum.BetaFeatureState
import com.kodraliu.localrock.shared.vacuum.CleanRecord
import com.kodraliu.localrock.shared.vacuum.CleanTimer
import com.kodraliu.localrock.shared.vacuum.ConsumableStatus
import com.kodraliu.localrock.shared.vacuum.FeatureSection
import com.kodraliu.localrock.shared.vacuum.inSection
import com.kodraliu.localrock.ui.strings.SettingsStrings
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.isoDayNumber

internal fun clockText(hour: Int, minute: Int) = "${hour.toString().padStart(2, '0')}:${minute.toString().padStart(2, '0')}"

/** The earliest enabled schedule still ahead today, as hour and minute; null when none is left today. */
internal fun nextCleaningToday(timers: List<CleanTimer>, now: LocalDateTime): Pair<Int, Int>? {
    val today = now.dayOfWeek.isoDayNumber % 7 // CleanTimer weekdays: 0 = Sunday
    val nowMinutes = now.hour * 60 + now.minute
    return timers.asSequence()
        .filter { it.enabled }
        .filter { t ->
            val date = t.date
            if (date != null) date.month == now.monthNumber && date.day == now.dayOfMonth
            else t.weekdays.isEmpty() || today in t.weekdays
        }
        .filter { it.hour * 60 + it.minute > nowMinutes }
        .minByOrNull { it.hour * 60 + it.minute }
        ?.let { it.hour to it.minute }
}

internal fun schedulesDetail(next: Pair<Int, Int>?): String =
    next?.let { (h, m) -> SettingsStrings.SCHEDULES_NEXT.replace("{time}", clockText(h, m)) } ?: SettingsStrings.SCHEDULES_DETAIL

/** True when any supply the robot reports has no life left, the official red-dot case. */
internal fun suppliesDue(status: ConsumableStatus?): Boolean {
    val s = status ?: return false
    fun due(used: Long?, total: Long) = used != null && ConsumableStatus.percentLeft(used, total) == 0
    return due(s.mainBrushWorkTime, ConsumableStatus.MAIN_BRUSH_LIFETIME_S) ||
        due(s.sideBrushWorkTime, ConsumableStatus.SIDE_BRUSH_LIFETIME_S) ||
        due(s.filterWorkTime, ConsumableStatus.FILTER_LIFETIME_S) ||
        due(s.sensorDirtyTime, ConsumableStatus.SENSOR_LIFETIME_S) ||
        due(s.cleaningBrushWorkTime, ConsumableStatus.CLEANING_BRUSH_LIFETIME_RUNS) ||
        due(s.strainerWorkTime, ConsumableStatus.STRAINER_LIFETIME_RUNS)
}

internal fun maintenanceDetail(status: ConsumableStatus?): String =
    if (suppliesDue(status)) SettingsStrings.MAINTENANCE_DUE else SettingsStrings.MAINTENANCE_NORMAL

/** The Robot Voice row subtitle: the volume level when known. */
internal fun robotVoiceDetail(volume: Int?): String =
    volume?.let { SettingsStrings.ROBOT_VOICE_DETAIL_LEVEL.replace("{level}", it.coerceIn(0, 100).toString()) }
        ?: SettingsStrings.ROBOT_VOICE_DETAIL

/** The newest run, which the header card sums up. */
internal fun lastCleanRecord(records: List<CleanRecord>): CleanRecord? = records.maxByOrNull { it.beginEpoch }

/** Duration in whole minutes and area in m2, the way the header card shows them. */
internal fun lastCleanFigures(record: CleanRecord?): Pair<String, String> {
    if (record == null) return SettingsStrings.NO_VALUE to SettingsStrings.NO_VALUE
    val minutes = (record.durationSeconds + 59) / 60
    val m2 = (record.cleanedAreaMm2 ?: record.areaMm2) / 1_000_000.0
    val area = ((m2 * 10).toLong() / 10.0).let { if (it == it.toLong().toDouble()) it.toLong().toString() else it.toString() }
    return "$minutes min" to "$area m²"
}

/** One choice on the Carpet Cleaning Setting page, with its set_carpet_clean_mode value. */
internal data class CarpetModeCard(val value: Int, val title: String, val detail: String)

// Wire values 0 Avoid, 1 Rise, 2 Vac & Mop (the plugin's "Ignore"), 3 Dynamic Lift; Rise is labelled Vacuum Only.
internal fun carpetModeCards(current: Int?): List<CarpetModeCard> = buildList {
    add(CarpetModeCard(1, SettingsStrings.CARPET_VACUUM_ONLY, SettingsStrings.CARPET_VACUUM_ONLY_DETAIL))
    add(CarpetModeCard(0, SettingsStrings.CARPET_AVOID, SettingsStrings.CARPET_AVOID_DETAIL))
    add(CarpetModeCard(2, SettingsStrings.CARPET_VAC_AND_MOP, SettingsStrings.CARPET_VAC_AND_MOP_DETAIL))
    if (current == 3) add(CarpetModeCard(3, SettingsStrings.CARPET_DYNAMIC_LIFT, SettingsStrings.CARPET_DYNAMIC_LIFT_DETAIL))
}

/** The four titled groups of the official Dock Settings page, plus OTHER for rows it has no place for yet. */
internal enum class DockGroup { WASH, EMPTY, DRY, MAINTENANCE, OTHER }

// By the plugin's DockSettingPage order: auto-delivery 112, autonomous wash 134, hot wash 41; UVC 84 and dry timer 83.
internal fun dockGroupOf(def: BetaFeatureDef): DockGroup = when {
    def.id == "dust_collection_switch" -> DockGroup.EMPTY
    def.bit in setOf(41, 112, 134) -> DockGroup.WASH
    def.bit in setOf(83, 84) -> DockGroup.DRY
    def.bit == 39 -> DockGroup.MAINTENANCE
    else -> DockGroup.OTHER
}

// Floor Cleaning Setting groups in official order, by feature bit; 42 is the floor-direction pair.
private val FloorGroupBits = listOf(
    listOf(11, 42, 70),
    listOf(80),
    listOf(79, 116),
    listOf(54, 40, 63),
    listOf(34, 56),
    listOf(43),
    listOf(131, 154),
)
private val FloorGroupIds = mapOf(
    "identify_ground_material" to 0,
    "follow_ground_material" to 0,
    "cleaning_pattern" to 0,
    "uncleaned_area_reminder" to 6,
)

/** The robot's floor rows split into the official groups, each in official order; unknown rows form a last group. */
internal fun floorFeatureGroups(features: List<BetaFeatureState>): List<List<BetaFeatureState>> {
    fun place(def: BetaFeatureDef): Pair<Int, Int> {
        FloorGroupBits.forEachIndexed { g, bits -> bits.indexOf(def.bit).takeIf { it >= 0 }?.let { return g to it } }
        FloorGroupIds[def.id]?.let { return it to Int.MAX_VALUE }
        return FloorGroupBits.size to Int.MAX_VALUE
    }
    val floor = features.inSection(FeatureSection.FLOOR)
    return floor.withIndex()
        .groupBy { place(it.value.def).first }
        .entries
        .sortedBy { it.key }
        .map { (_, group) -> group.sortedWith(compareBy({ place(it.value.def).second }, { it.index })).map { it.value } }
}

/** Where a ROBOT-section feature sits under More Options and Robot Voice. */
internal enum class RobotRowPlace { LIGHTS, ADJUSTED_BATTERY, VOICE, OTHER }

internal fun robotRowPlace(def: BetaFeatureDef): RobotRowPlace = when (def.id) {
    "voice_control_led" -> RobotRowPlace.LIGHTS
    "optimize_battery" -> RobotRowPlace.ADJUSTED_BATTERY
    "full_duplex" -> RobotRowPlace.VOICE
    else -> RobotRowPlace.OTHER
}

internal fun List<BetaFeatureState>.robotRows(place: RobotRowPlace): List<BetaFeatureState> =
    inSection(FeatureSection.ROBOT).filter { robotRowPlace(it.def) == place }
