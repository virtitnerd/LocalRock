package com.kodraliu.localrock.ui.navigation

import kotlinx.serialization.Serializable

// Top-level destinations
@Serializable data object Splash
@Serializable data object Welcome
@Serializable data object ServerSetup
@Serializable data object Login
@Serializable data object DeviceList
@Serializable data object AppSettings
@Serializable data class AddVacuum(val duid: String? = null)

@Serializable data class VacuumGraph(val duid: String)
@Serializable data object VacuumMain
@Serializable data object VacuumSettings
@Serializable data object VacuumZoneClean
@Serializable data object VacuumMapEdit
@Serializable data object VacuumSchedule
@Serializable data object VacuumCamera
@Serializable data object VacuumRemote
@Serializable data object VacuumPinGo
@Serializable data object VacuumHistory

// Settings pages inside VacuumGraph. Pages the redesign has not built yet open today's equivalent or a placeholder.
@Serializable data object VacuumMaintenance
@Serializable data class VacuumMaintenanceDetail(val consumable: String)
@Serializable data object VacuumSettingsDock
@Serializable data object VacuumSettingsFloor
@Serializable data object VacuumSettingsCarpet
@Serializable data object VacuumSettingsObstacle
@Serializable data object VacuumSettingsRobot
@Serializable data object VacuumSettingsDnd
@Serializable data object VacuumSettingsValley
@Serializable data object VacuumSettingsBeta
@Serializable data object VacuumSettingsMatter
@Serializable data object VacuumSettingsMoreOptions
@Serializable data object VacuumSettingsChargingMode
@Serializable data object VacuumSettingsRobotVoice
@Serializable data object VacuumSettingsRemoteViewing
@Serializable data object VacuumSettingsProductInfo
@Serializable data object VacuumFirmware
@Serializable data object VacuumManageMaps
@Serializable data object VacuumMapDetails
