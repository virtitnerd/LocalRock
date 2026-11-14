package com.kodraliu.localrock.shared.vacuum

import com.kodraliu.localrock.shared.vacuum.camera.CAMERA_FEATURE_BITS
import com.kodraliu.localrock.shared.vacuum.camera.TWO_KEY_VIDEO_FEATURE_BIT

/** The device page's bottom tabs, in official order. */
enum class MainTab { FULL, ROOM, ZONE, ROUTINE }

/** Manage Maps tool tiles, in official order. */
enum class MapTool { DIVIDE, COMBINE, REMOVE, NAME_ROOM, NO_GO, THRESHOLD, FLOOR_TYPE, CARPET, FURNITURE, SEQUENCE }

// What one robot supports, as the redesigned screens ask it; screens test these flags, never bit numbers.
// A flag marked TODO returns a placeholder until the named track fills in the official gate.
data class Capabilities(
    // Device page (B2).
    val mainTabs: List<MainTab>,
    val hasRoutineTab: Boolean,
    val smartPlan: Boolean,
    val waterSlide: Boolean,
    val fastRoute: Boolean,
    val routes: RouteGates,
    // Camera, patrol and pets (B5).
    val camera: Boolean,
    val twoKeyVideo: Boolean,
    val remoteViewing: Boolean,
    val remoteControl: Boolean,
    val pinNGo: Boolean,
    val patrol: Boolean,
    val petSearch: Boolean,
    // Settings (B1).
    val hasCarpetSettings: Boolean,
    val hasFloorSettings: Boolean,
    val hasFloorPattern: Boolean,
    val hasDockSettings: Boolean,
    val hasAutoEmptySettings: Boolean,
    val hasObstacleSettings: Boolean,
    val chargingMode: Boolean,
    val adjustedBattery: Boolean,
    val offlineMap: Boolean,
    val customDnd: Boolean,
    val multiSlotDnd: Boolean,
    val voiceAssistant: Boolean,
    val childLock: Boolean,
    val matter: Boolean,
    val dock: DockCapabilities,
    // Manage Maps (B3).
    val mapTools: Set<MapTool>,
    val mapBackup: Boolean,
    // History, firmware, maintenance (B4).
    val cruiseHistory: Boolean,
    val firmwareUpdates: Boolean,
    val autoFirmwareUpdates: Boolean,
    val dirtMap: Boolean,
    // Beta features page (B6): switches the robot reports, and the advertised bits with no control yet (ghost rows).
    val betaFeatures: List<BetaFeatureDef>,
    val unlistedFeatures: List<AdvertisedFeature>,
    val showsBetaFeatures: Boolean,
    val advertised: AdvertisedCapabilities = AdvertisedCapabilities(),
)

/** Today's map editor tools; the official gates per tool come with Manage Maps. */
private val CurrentMapTools = setOf(MapTool.DIVIDE, MapTool.COMBINE, MapTool.NAME_ROOM, MapTool.NO_GO, MapTool.THRESHOLD, MapTool.CARPET)

// Built from the model, the server's decimal featureSet and hex newFeatureSet, and the robot's own
// new_feature_info_str (robotFeatureSet), which wins once the robot has answered.
fun buildCapabilities(
    model: String?,
    featureSet: String?,
    serverFeatureSet: String?,
    robotFeatureSet: String?,
    robotIsFcc: Boolean? = null,
): Capabilities {
    val nfs = robotFeatureSet ?: serverFeatureSet
    fun bit(n: Int) = newFeatureBit(nfs, n)
    val betaFeatures = supportedBetaFeatures(nfs)
    val unlisted = unlistedAdvertisedFeatures(nfs)
    val camera = CAMERA_FEATURE_BITS.any { bit(it) }
    val patrol = bit(VIDEO_PATROL_FEATURE_BIT)
    val settings = buildSettingsCapabilities(featureSet, nfs)
    val device = devicePageFlags(nfs)
    val hasRoutineTab = device.routineTabKnown
    val secondary = secondaryFlags(nfs, patrol)
    return Capabilities(
        mainTabs = listOf(MainTab.FULL, MainTab.ROOM, MainTab.ZONE) + if (hasRoutineTab) listOf(MainTab.ROUTINE) else emptyList(),
        hasRoutineTab = hasRoutineTab,
        smartPlan = isSmartPlanSupported(nfs),
        waterSlide = isWaterSlideSupported(nfs),
        fastRoute = isFastRouteSupported(nfs),
        routes = routeGates(model, featureSet, nfs),
        camera = camera,
        twoKeyVideo = bit(TWO_KEY_VIDEO_FEATURE_BIT),
        remoteViewing = camera, // TODO(B5): official gate RNF 3 + RealTimeVideoPhoto
        remoteControl = true, // TODO(B5): official gate apiLevel >= 10003
        pinNGo = true,
        patrol = patrol,
        petSearch = isPetSearchSupported(nfs),
        hasCarpetSettings = settings.hasCarpetSettings,
        hasFloorSettings = settings.hasFloorSettings,
        hasFloorPattern = settings.hasFloorPattern,
        hasDockSettings = settings.hasDockSettings,
        hasAutoEmptySettings = settings.hasAutoEmptySettings,
        hasObstacleSettings = settings.hasObstacleSettings,
        chargingMode = settings.chargingMode,
        adjustedBattery = settings.adjustedBattery,
        offlineMap = settings.offlineMap,
        customDnd = settings.customDnd,
        multiSlotDnd = settings.multiSlotDnd,
        voiceAssistant = settings.voiceAssistant,
        childLock = settings.childLock,
        matter = bit(MATTER_FEATURE_BIT),
        dock = DockCapabilities(model, featureSet, nfs),
        mapTools = CurrentMapTools, // TODO(B3): official per-tool gates (Remove NFS 71, Floor Type RN 35, Furniture RN 36)
        mapBackup = false, // TODO(B3): isSupportBackupMap
        cruiseHistory = secondary.cruiseHistory,
        firmwareUpdates = secondary.firmwareUpdates,
        autoFirmwareUpdates = secondary.autoFirmwareUpdates,
        dirtMap = secondary.dirtMap,
        betaFeatures = betaFeatures,
        unlistedFeatures = unlisted,
        showsBetaFeatures = betaFeatures.any { it.section == FeatureSection.BETA && it.bit != null } || unlisted.isNotEmpty(),
        advertised = buildAdvertisedCapabilities(nfs, robotIsFcc),
    )
}
