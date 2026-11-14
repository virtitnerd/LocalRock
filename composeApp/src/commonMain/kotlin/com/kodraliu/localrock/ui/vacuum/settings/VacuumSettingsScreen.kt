package com.kodraliu.localrock.ui.vacuum

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.unit.dp
import com.kodraliu.localrock.shared.vacuum.FeatureSection
import com.kodraliu.localrock.shared.vacuum.inSection
import com.kodraliu.localrock.shared.vacuum.DockSettings
import com.kodraliu.localrock.shared.vacuum.RobotSettings
import com.kodraliu.localrock.ui.AppColors
import com.kodraliu.localrock.ui.adaptive.CenteredContent
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue
import kotlin.math.roundToInt
import com.kodraliu.localrock.ui.components.SettingRowInfo
import com.kodraliu.localrock.ui.components.SettingsCategoryRow

// ── Navigation ────────────────────────────────────────────────────────────────

private sealed interface SettingsRoute {
    data object Root : SettingsRoute
    data object Maintenance : SettingsRoute
    data object DockSettings : SettingsRoute
    data object RobotSettings : SettingsRoute
    data object DndMode : SettingsRoute
    data object Carpet : SettingsRoute
    data object Floor : SettingsRoute
    data object Obstacle : SettingsRoute
    data object Valley : SettingsRoute
    data object BetaFeatures : SettingsRoute
    data object History : SettingsRoute
    data object Matter : SettingsRoute
    data object Firmware : SettingsRoute
}

// ── Entry point ───────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
fun VacuumSettingsScreen(
    viewModel: VacuumViewModel,
    onBack: () -> Unit,
) {
    val repository = viewModel.repository
    val mapRotation by viewModel.mapRotation.collectAsState()
    var route by remember { mutableStateOf<SettingsRoute>(SettingsRoute.Root) }
    // Sub-pages are local state, not nav destinations, so system back would leave Settings (#52).
    BackHandler(enabled = route != SettingsRoute.Root) {
        route = if (route == SettingsRoute.Valley) SettingsRoute.DockSettings else SettingsRoute.Root
    }
    val scope = rememberCoroutineScope()
    val consumables by repository.consumableStatus.collectAsState()
    val robotSettings by repository.robotSettings.collectAsState()
    val dockSettings by repository.dockSettings.collectAsState()
    val status by viewModel.status.collectAsState()
    val busy by viewModel.busy.collectAsState()
    val dndSettings by repository.dndSettings.collectAsState()
    val valleySettings by repository.valleySettings.collectAsState()
    val cleanHistory by repository.cleanHistory.collectAsState()
    val cleanSummary by repository.cleanSummary.collectAsState()
    val rooms by repository.rooms.collectAsState()
    val firmwareUpdate by viewModel.firmwareUpdate.collectAsState()
    val betaFeatures by repository.betaFeatures.collectAsState()
    val betaFeaturesLoading by repository.betaFeaturesLoading.collectAsState()

    // Every feature the robot reports is read once, so a page with none is not offered.
    LaunchedEffect(Unit) {
        if (betaFeatures.isEmpty()) runCatching { repository.fetchBetaFeatures(viewModel.betaFeatureDefs) }
    }

    when (route) {
        SettingsRoute.Root -> SettingsRootScreen(
            modelName = viewModel.modelName,
            firmwareVersion = viewModel.firmwareVersion,
            firmwareUpdate = firmwareUpdate,
            onCheckUpdate = { route = SettingsRoute.Firmware },
            onBack = onBack,
            onHistory = { route = SettingsRoute.History },
            onMaintenance = {
                scope.launch { runCatching { repository.fetchConsumableStatus() } }
                route = SettingsRoute.Maintenance
            },
            onCarpet = {
                scope.launch { runCatching { repository.fetchRobotSettings() } }
                route = SettingsRoute.Carpet
            },
            onFloor = if (betaFeatures.inSection(FeatureSection.FLOOR).isEmpty()) null else ({ route = SettingsRoute.Floor }),
            onObstacle = if (betaFeatures.inSection(FeatureSection.OBSTACLE).isEmpty()) null else ({ route = SettingsRoute.Obstacle }),
            onDockSettings = {
                scope.launch {
                    runCatching { repository.fetchDockSettings() }
                    runCatching { repository.fetchValleySettings() }
                }
                route = SettingsRoute.DockSettings
            },
            onRobotSettings = {
                scope.launch { runCatching { repository.fetchRobotSettings() } }
                route = SettingsRoute.RobotSettings
            },
            onDndMode = {
                scope.launch { runCatching { repository.fetchDndSettings() } }
                route = SettingsRoute.DndMode
            },
            onMatter = if (!viewModel.supportsMatter) null else ({ route = SettingsRoute.Matter }),
            onBetaFeatures = if (!viewModel.showsBetaFeatures) null else ({
                route = SettingsRoute.BetaFeatures
            }),
        )
        SettingsRoute.Carpet -> CarpetScreen(
            carpetMode = robotSettings.carpetMode,
            features = betaFeatures.inSection(FeatureSection.CARPET),
            onCarpetModeChange = { mode -> viewModel.report { it.setCarpetMode(mode) } },
            onToggle = { id, on -> viewModel.report { it.setBetaFeature(id, on) } },
            onBack = { route = SettingsRoute.Root },
        )
        SettingsRoute.Floor -> FeatureSectionScreen(
            title = "Floor cleaning",
            features = betaFeatures.inSection(FeatureSection.FLOOR),
            onToggle = { id, on -> viewModel.report { it.setBetaFeature(id, on) } },
            onChoice = { id, value -> viewModel.report { it.setBetaChoice(id, value) } },
            onBack = { route = SettingsRoute.Root },
        )
        SettingsRoute.Obstacle -> FeatureSectionScreen(
            title = "Obstacle avoidance",
            features = betaFeatures.inSection(FeatureSection.OBSTACLE),
            onToggle = { id, on -> viewModel.report { it.setBetaFeature(id, on) } },
            onBack = { route = SettingsRoute.Root },
        )
        SettingsRoute.BetaFeatures -> BetaFeaturesScreen(
            features = betaFeatures.inSection(FeatureSection.BETA),
            loading = betaFeaturesLoading,
            unlisted = viewModel.unlistedFeatures,
            onToggle = { id, on -> viewModel.report { it.setBetaFeature(id, on) } },
            onBack = { route = SettingsRoute.Root },
        )
        SettingsRoute.Maintenance -> MaintenanceScreen(
            consumables = consumables,
            onRefresh = { scope.launch { runCatching { repository.fetchConsumableStatus() } } },
            onReset = { field -> scope.launch { runCatching { repository.resetConsumable(field) } } },
            onBack = { route = SettingsRoute.Root },
        )
        SettingsRoute.DockSettings -> DockSettingsScreen(
            dockSettings = dockSettings,
            dockCapabilities = viewModel.dockCapabilities,
            dockType = status.dockType,
            dockStatus = status.dockStatus,
            busy = busy,
            onWashModeChange = { mode -> viewModel.run { it.setWashMode(mode) } },
            onWashFrequencyChange = { mode, minutes -> viewModel.run { it.setWashFrequency(mode, minutes) } },
            onAutoEmptyModeChange = { mode -> viewModel.run { it.setAutoEmptyMode(mode) } },
            onEmptyWaterTank = { viewModel.run("Emptying the water tank") { it.emptyInbuiltWaterTank() } },
            features = betaFeatures.inSection(FeatureSection.DOCK),
            onFeatureToggle = { id, on -> viewModel.report { it.setBetaFeature(id, on) } },
            valleySettings = valleySettings,
            onMopDryingChange = { on -> viewModel.run { it.setMopDrying(on) } },
            onDryTimeChange = { seconds -> viewModel.run { it.setDryTime(seconds) } },
            onWashWaterTemperatureChange = { value -> viewModel.run { it.setWashWaterTemperature(value) } },
            onOpenValley = { route = SettingsRoute.Valley },
            onBack = { route = SettingsRoute.Root },
        )
        SettingsRoute.Valley -> ValleyScreen(
            valleySettings = valleySettings,
            onEnable = { sh, sm, eh, em -> viewModel.run { it.setValley(sh, sm, eh, em) } },
            onDisable = { viewModel.run { it.disableValley() } },
            onBack = { route = SettingsRoute.DockSettings },
        )
        SettingsRoute.RobotSettings -> RobotSettingsScreen(
            robotSettings = robotSettings,
            onFindRobot = { scope.launch { runCatching { repository.findMe() } } },
            onVolumeChange = { volume -> viewModel.report { it.setVolume(volume) } },
            onChildLockChange = { locked -> viewModel.report { it.setChildLock(locked) } },
            onLedChange = { on -> viewModel.report { it.setLed(on) } },
            features = betaFeatures.inSection(FeatureSection.ROBOT),
            onFeatureToggle = { id, on -> viewModel.report { it.setBetaFeature(id, on) } },
            onBack = { route = SettingsRoute.Root },
        )
        SettingsRoute.DndMode -> DndScreen(
            dndSettings = dndSettings,
            onEnable = { sh, sm, eh, em -> scope.launch { runCatching { repository.setDnd(sh, sm, eh, em) } } },
            onDisable = { scope.launch { runCatching { repository.disableDnd() } } },
            onBack = { route = SettingsRoute.Root },
        )
        SettingsRoute.Firmware -> FirmwareUpdateScreen(viewModel = viewModel, onBack = { route = SettingsRoute.Root })
        SettingsRoute.Matter -> MatterScreen(
            loadStatus = { repository.fetchMatterStatus() },
            requestKey = { repository.requestMatterKey() },
            reset = { repository.resetMatter() },
            onBack = { route = SettingsRoute.Root },
        )
        SettingsRoute.History -> {

            LaunchedEffect(Unit) {
                runCatching { repository.fetchCleanHistory() }
                runCatching { repository.fetchCleanSummary() }
            }
            HistoryScreen(
                records = cleanHistory,
                summary = cleanSummary,
                loadMap = { repository.fetchCleanRecordMap(it) },
                loadThumbnails = { task, x, y -> repository.fetchPatrolThumbnails(task, x, y) },
                mapRotation = mapRotation,
                loadTimeline = repository::fetchCleanTimeline,
                roomName = { id -> rooms.firstOrNull { it.id == id }?.name },
                onRefresh = {
                    scope.launch {
                        runCatching { repository.fetchCleanHistory() }
                        runCatching { repository.fetchCleanSummary() }
                    }
                },
                onBack = { route = SettingsRoute.Root },
            )
        }
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsRootScreen(
    modelName: String?,
    firmwareVersion: String?,
    firmwareUpdate: FirmwareUpdateState,
    onCheckUpdate: () -> Unit,
    onBack: () -> Unit,
    onHistory: () -> Unit,
    onMaintenance: () -> Unit,
    onCarpet: () -> Unit,
    onFloor: (() -> Unit)?,
    onObstacle: (() -> Unit)?,
    onDockSettings: () -> Unit,
    onRobotSettings: () -> Unit,
    onDndMode: () -> Unit,
    onMatter: (() -> Unit)?,
    onBetaFeatures: (() -> Unit)?,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        CenteredContent {
            LazyColumn(contentPadding = padding) {
                item {
                    SettingsCategoryRow(
                        icon = Icons.Default.History,
                        iconTint = AppColors.AccentCyan,
                        title = "Cleaning history",
                        subtitle = "Past cleaning sessions and totals",
                        onClick = onHistory,
                    )
                }
                item { HorizontalDivider(Modifier.padding(start = 72.dp)) }
                item {
                    SettingsCategoryRow(
                        icon = Icons.Default.Build,
                        iconTint = AppColors.Good,
                        title = "Maintenance",
                        subtitle = "Brush life and consumables",
                        onClick = onMaintenance,
                    )
                }
                item { HorizontalDivider(Modifier.padding(start = 72.dp)) }
                item {
                    SettingsCategoryRow(
                        icon = Icons.Default.GridView,
                        iconTint = AppColors.Good,
                        title = "Carpet cleaning",
                        subtitle = "Carpet mode and deep cleaning",
                        onClick = onCarpet,
                    )
                }
                if (onFloor != null) {
                    item { HorizontalDivider(Modifier.padding(start = 72.dp)) }
                    item {
                        SettingsCategoryRow(
                            icon = Icons.Default.CleaningServices,
                            iconTint = AppColors.AccentCyan,
                            title = "Floor cleaning",
                            subtitle = "Floor types, FlexiArm and pet areas",
                            onClick = onFloor,
                        )
                    }
                }
                item { HorizontalDivider(Modifier.padding(start = 72.dp)) }
                item {
                    SettingsCategoryRow(
                        icon = Icons.Default.Home,
                        iconTint = AppColors.Water,
                        title = "Dock settings",
                        subtitle = "Auto-empty, wash and dry options",
                        onClick = onDockSettings,
                    )
                }
                if (onObstacle != null) {
                    item { HorizontalDivider(Modifier.padding(start = 72.dp)) }
                    item {
                        SettingsCategoryRow(
                            icon = Icons.Default.Sensors,
                            iconTint = AppColors.AccentYellow,
                            title = "Obstacle avoidance",
                            subtitle = "Collision and object detection",
                            onClick = onObstacle,
                        )
                    }
                }
                item { HorizontalDivider(Modifier.padding(start = 72.dp)) }
                item {
                    SettingsCategoryRow(
                        icon = Icons.Default.Settings,
                        iconTint = AppColors.AccentPurple,
                        title = "Robot settings",
                        subtitle = "Volume, child lock and more",
                        onClick = onRobotSettings,
                    )
                }
                item { HorizontalDivider(Modifier.padding(start = 72.dp)) }
                item {
                    SettingsCategoryRow(
                        icon = Icons.Default.Schedule,
                        iconTint = AppColors.AccentIndigo,
                        title = "Do Not Disturb",
                        subtitle = "Set quiet hours for cleaning",
                        onClick = onDndMode,
                    )
                }
                if (onMatter != null) {
                    item { HorizontalDivider(Modifier.padding(start = 72.dp)) }
                    item {
                        SettingsCategoryRow(
                            icon = Icons.Default.Link,
                            iconTint = AppColors.AccentIndigo,
                            title = "Matter",
                            subtitle = "Add the robot to Apple Home, Google Home and more",
                            onClick = onMatter,
                        )
                    }
                }
                if (onBetaFeatures != null) {
                    item { HorizontalDivider(Modifier.padding(start = 72.dp)) }
                    item {
                        SettingsCategoryRow(
                            icon = Icons.Default.Science,
                            iconTint = AppColors.AccentCyan,
                            title = "Beta features",
                            subtitle = "Other options your robot reports",
                            onClick = onBetaFeatures,
                        )
                    }
                }

                item {
                    Text(
                        "Firmware",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 16.dp, top = 20.dp, bottom = 4.dp),
                    )
                }
                item {
                    SettingRowInfo(
                        icon = Icons.Default.Router,
                        iconTint = AppColors.AccentIndigo,
                        title = "Model",
                        value = modelName ?: "Unknown",
                    )
                }
                item { HorizontalDivider(Modifier.padding(start = 72.dp)) }
                item {
                    SettingRowInfo(
                        icon = Icons.Default.Memory,
                        iconTint = AppColors.AccentCyan,
                        title = "Firmware version",
                        value = firmwareVersion ?: "Unknown",
                    )
                }
                // The update check is hidden for now; FirmwareUpdateRow and the view model stay in place.
                if (SHOW_FIRMWARE_UPDATE_CHECK) {
                    item { HorizontalDivider(Modifier.padding(start = 72.dp)) }
                    item {
                        FirmwareUpdateRow(state = firmwareUpdate, onCheck = onCheckUpdate)
                    }
                }
            }
        }
    }
}


private const val SHOW_FIRMWARE_UPDATE_CHECK = true

private fun oneDecimal(value: Float): String {
    val scaled = (value * 10f).roundToInt()
    return "${scaled / 10}.${(scaled % 10).absoluteValue}"
}
