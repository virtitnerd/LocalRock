package com.kodraliu.localrock.shared.vacuum

/** The official 1-30 Water Flow slider of water slide robots (NFS 106): water_box_mode = 220 + level. */
object WaterFlow {
    const val MIN_LEVEL = 1
    const val MAX_LEVEL = 30
    /** PureWaterSuperBegin 248: from here the official slider shows the slipping warning. */
    const val WARNING_LEVEL = 28
    /** Levels that carry the 1, 2 and 3 droplet icons on the official track. */
    val DROPLET_LEVELS = listOf(5, 15, 25)
    private const val CODE_BASE = 220

    fun codeFor(level: Int): Int = CODE_BASE + level.coerceIn(MIN_LEVEL, MAX_LEVEL)

    /** The slider level for a reported code; the plugin's convertWaterMode maps 201/202/203/208 onto 225/235/245/250. */
    fun levelOf(code: Int?): Int? = when (code) {
        null -> null
        in (CODE_BASE + MIN_LEVEL)..(CODE_BASE + MAX_LEVEL) -> code - CODE_BASE
        WaterBoxMode.LOW -> 5
        WaterBoxMode.MEDIUM -> 15
        WaterBoxMode.HIGH -> 25
        208 -> MAX_LEVEL
        else -> null
    }

    fun band(level: Int): WaterFlowBand = when (level.coerceIn(MIN_LEVEL, MAX_LEVEL)) {
        in 1..3 -> WaterFlowBand.SLIGHT
        in 4..7 -> WaterFlowBand.LOW
        in 8..12 -> WaterFlowBand.GENTLE
        in 13..17 -> WaterFlowBand.MEDIUM
        in 18..22 -> WaterFlowBand.MODERATE
        in 23..27 -> WaterFlowBand.HIGH
        else -> WaterFlowBand.EXTREME
    }

    fun showsWarning(level: Int): Boolean = level >= WARNING_LEVEL
}

/** Bands of getPureWaterModeText, keys mode_setting_panel_custom_water_box_7 (Slight) down to _1 (Extreme). */
enum class WaterFlowBand { SLIGHT, LOW, GENTLE, MEDIUM, MODERATE, HIGH, EXTREME }

/** The word a route shows: clean-efficiency robots (NFS 108) call 303 "Deep" and have no 301. */
enum class RouteName { FAST, STANDARD, DEEP, DEEP_PLUS }

data class RouteOption(val code: Int, val name: RouteName)

/** The plugin gates behind MopMethods and getCleanRouteItems. */
data class RouteGates(
    val cleanEfficiency: Boolean,
    val carefulSlow: Boolean,
    val fast: Boolean,
    val twoRoutesOnly: Boolean,
)

private const val CLEAN_EFFICIENCY_BIT = 108 // NFS CleanEfficiency
private const val CAREFUL_SLOW_RNF_BIT = 41 // robotNewFeatures, (n / 2^32) >> 9 & 1
private val TWO_ROUTE_MODELS = setOf("a72", "a84") // MF CleanMode_NonePureCleanMopWithMaxPlus

/**
 * Bit [bit] of the decimal robot feature number (new_feature_info, the server's featureSet); null when the
 * number is missing or has no high word, which some reports leave as a small placeholder.
 */
fun robotNewFeatureBit(featureSet: String?, bit: Int): Boolean? {
    val n = featureSet?.trim()?.toULongOrNull() ?: return null
    if (n < (1uL shl 32)) return null
    return (n shr bit) and 1uL == 1uL
}

fun routeGates(model: String?, featureSet: String?, newFeatureSet: String?): RouteGates = RouteGates(
    cleanEfficiency = newFeatureBit(newFeatureSet, CLEAN_EFFICIENCY_BIT),
    // Unknown counts as supported: every robot with a route picker in LocalRock so far has it.
    carefulSlow = robotNewFeatureBit(featureSet, CAREFUL_SLOW_RNF_BIT) ?: true,
    fast = isFastRouteSupported(newFeatureSet),
    twoRoutesOnly = model?.substringAfterLast('.') in TWO_ROUTE_MODELS,
)

/**
 * Routes the official mode sheet offers, in its order. Without clean efficiency, Deep and Deep+ show
 * only on the Mop tab (a Qrevo Pro switches suction off when sent 301 or 303).
 */
fun routeOptions(gates: RouteGates, mopOnly: Boolean): List<RouteOption> {
    val fast = RouteOption(MopRoute.FAST, RouteName.FAST)
    val standard = RouteOption(MopRoute.STANDARD, RouteName.STANDARD)
    if (gates.twoRoutesOnly) return listOf(fast, standard)
    if (!gates.cleanEfficiency && !mopOnly) return listOfNotNull(fast.takeIf { gates.fast }, standard)
    return buildList {
        if (gates.fast) add(fast)
        add(standard)
        if (!gates.cleanEfficiency) add(RouteOption(MopRoute.DEEP, RouteName.DEEP))
        if (gates.carefulSlow) add(RouteOption(MopRoute.DEEP_PLUS, if (gates.cleanEfficiency) RouteName.DEEP else RouteName.DEEP_PLUS))
    }
}
