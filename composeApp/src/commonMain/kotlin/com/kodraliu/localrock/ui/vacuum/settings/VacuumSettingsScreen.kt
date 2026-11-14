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
import androidx.compose.ui.Modifier
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
import com.kodraliu.localrock.ui.navigation.VacuumFirmware
import com.kodraliu.localrock.ui.navigation.VacuumHistory
import com.kodraliu.localrock.ui.navigation.VacuumMaintenance
import com.kodraliu.localrock.ui.navigation.VacuumSettingsBeta
import com.kodraliu.localrock.ui.navigation.VacuumSettingsCarpet
import com.kodraliu.localrock.ui.navigation.VacuumSettingsDnd
import com.kodraliu.localrock.ui.navigation.VacuumSettingsDock
import com.kodraliu.localrock.ui.navigation.VacuumSettingsFloor
import com.kodraliu.localrock.ui.navigation.VacuumSettingsMatter
import com.kodraliu.localrock.ui.navigation.VacuumSettingsObstacle
import com.kodraliu.localrock.ui.navigation.VacuumSettingsRobot

// ── Entry point ───────────────────────────────────────────────────────────────

/** Settings root. Each row opens its own route in VacuumGraph through [onNavigate]. */
@Composable
fun VacuumSettingsScreen(
    viewModel: VacuumViewModel,
    onBack: () -> Unit,
    onNavigate: (Any) -> Unit,
) {
    val repository = viewModel.repository
    val firmwareUpdate by viewModel.firmwareUpdate.collectAsState()
    val betaFeatures by repository.betaFeatures.collectAsState()

    // Every feature the robot reports is read once, so a page with none is not offered.
    LaunchedOnEntry {
        if (betaFeatures.isEmpty()) runCatching { repository.fetchBetaFeatures(viewModel.betaFeatureDefs) }
    }

    SettingsRootScreen(
        modelName = viewModel.modelName,
        firmwareVersion = viewModel.firmwareVersion,
        firmwareUpdate = firmwareUpdate,
        onCheckUpdate = { onNavigate(VacuumFirmware) },
        onBack = onBack,
        onHistory = { onNavigate(VacuumHistory) },
        onMaintenance = { onNavigate(VacuumMaintenance) },
        onCarpet = { onNavigate(VacuumSettingsCarpet) },
        onFloor = if (betaFeatures.inSection(FeatureSection.FLOOR).isEmpty()) null else ({ onNavigate(VacuumSettingsFloor) }),
        onObstacle = if (betaFeatures.inSection(FeatureSection.OBSTACLE).isEmpty()) null else ({ onNavigate(VacuumSettingsObstacle) }),
        onDockSettings = { onNavigate(VacuumSettingsDock) },
        onRobotSettings = { onNavigate(VacuumSettingsRobot) },
        onDndMode = { onNavigate(VacuumSettingsDnd) },
        onMatter = if (!viewModel.supportsMatter) null else ({ onNavigate(VacuumSettingsMatter) }),
        onBetaFeatures = if (!viewModel.showsBetaFeatures) null else ({ onNavigate(VacuumSettingsBeta) }),
    )
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
