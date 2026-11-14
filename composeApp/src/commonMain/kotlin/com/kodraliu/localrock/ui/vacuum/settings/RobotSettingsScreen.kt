package com.kodraliu.localrock.ui.vacuum

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kodraliu.localrock.shared.vacuum.BetaFeatureState
import com.kodraliu.localrock.shared.vacuum.RobotSettings
import com.kodraliu.localrock.shared.vacuum.VolumeLevels
import com.kodraliu.localrock.shared.vacuum.volumeLabel
import com.kodraliu.localrock.shared.vacuum.volumeLevelIndex
import com.kodraliu.localrock.ui.AppColors
import com.kodraliu.localrock.ui.adaptive.CenteredContent
import com.kodraliu.localrock.ui.components.PickerDialog
import com.kodraliu.localrock.ui.components.SettingRowSwitch
import com.kodraliu.localrock.ui.components.SettingRowClickable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import com.kodraliu.localrock.shared.vacuum.FeatureSection
import com.kodraliu.localrock.shared.vacuum.inSection

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RobotSettingsScreen(
    robotSettings: RobotSettings,
    onFindRobot: () -> Unit,
    onVolumeChange: (Int) -> Unit,
    onChildLockChange: (Boolean) -> Unit,
    onLedChange: (Boolean) -> Unit,
    features: List<BetaFeatureState>,
    onFeatureToggle: (String, Boolean) -> Unit,
    onBack: () -> Unit,
) {
    var findTriggered by remember { mutableStateOf(false) }
    var showVolumeDialog by remember { mutableStateOf(false) }

    val volumeLabel = volumeLabel(robotSettings.volume)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Robot Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                },
            )
        },
    ) { padding ->
        CenteredContent {
            LazyColumn(contentPadding = padding) {
                item {
                    SettingRowClickable(
                        icon = Icons.Default.Sensors,
                        iconTint = MaterialTheme.colorScheme.primary,
                        title = "Find Robot",
                        subtitle = if (findTriggered) "Beeping…" else "Make the robot beep to locate it",
                        onClick = { onFindRobot(); findTriggered = true },
                    )
                }
                item { HorizontalDivider(Modifier.padding(start = 72.dp)) }
                item {
                    SettingRowClickable(
                        icon = Icons.AutoMirrored.Filled.VolumeUp,
                        iconTint = AppColors.Water,
                        title = "Volume",
                        subtitle = volumeLabel,
                        onClick = { showVolumeDialog = true },
                    )
                }
                item { HorizontalDivider(Modifier.padding(start = 72.dp)) }
                item {
                    SettingRowSwitch(
                        icon = Icons.Default.Lock,
                        iconTint = AppColors.Warn,
                        title = "Child Lock",
                        subtitle = if (robotSettings.childLock) "Buttons locked on robot" else "Buttons active on robot",
                        checked = robotSettings.childLock,
                        onCheckedChange = onChildLockChange,
                    )
                }
                item { HorizontalDivider(Modifier.padding(start = 72.dp)) }
                item {
                    SettingRowSwitch(
                        icon = Icons.Default.Lightbulb,
                        iconTint = AppColors.AccentYellow,
                        title = "Status LED",
                        subtitle = if (robotSettings.ledEnabled) "LED indicator on" else "LED indicator off",
                        checked = robotSettings.ledEnabled,
                        onCheckedChange = onLedChange,
                    )
                }
                items(features, key = { it.def.id }) { f ->
                    HorizontalDivider(Modifier.padding(start = 72.dp))
                    FeatureToggleRow(f, onFeatureToggle)
                }
            }
        }
    }

    if (showVolumeDialog) {
        PickerDialog(
            title = "Volume",
            options = VolumeLevels.map { it.second },
            selected = volumeLevelIndex(robotSettings.volume),
            onSelect = { onVolumeChange(VolumeLevels[it].first); showVolumeDialog = false },
            onDismiss = { showVolumeDialog = false },
        )
    }
}

@Composable
fun VacuumRobotSettingsScreen(viewModel: VacuumViewModel, onBack: () -> Unit) {
    val repository = viewModel.repository
    val scope = rememberCoroutineScope()
    val robotSettings by repository.robotSettings.collectAsState()
    val betaFeatures by repository.betaFeatures.collectAsState()
    LaunchedOnEntry { runCatching { repository.fetchRobotSettings() } }
    RobotSettingsScreen(
        robotSettings = robotSettings,
        onFindRobot = { scope.launch { runCatching { repository.findMe() } } },
        onVolumeChange = { volume -> viewModel.report { it.setVolume(volume) } },
        onChildLockChange = { locked -> viewModel.report { it.setChildLock(locked) } },
        onLedChange = { on -> viewModel.report { it.setLed(on) } },
        features = betaFeatures.inSection(FeatureSection.ROBOT),
        onFeatureToggle = { id, on -> viewModel.report { it.setBetaFeature(id, on) } },
        onBack = onBack,
    )
}
