package com.kodraliu.localrock.ui.vacuum

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.kodraliu.localrock.shared.vacuum.BetaFeatureState
import com.kodraliu.localrock.shared.vacuum.DockCapabilities
import com.kodraliu.localrock.shared.vacuum.DockOptions
import com.kodraliu.localrock.shared.vacuum.DockSettings
import com.kodraliu.localrock.shared.vacuum.FeatureSection
import com.kodraliu.localrock.shared.vacuum.dryTimeLabel
import com.kodraliu.localrock.shared.vacuum.inSection
import com.kodraliu.localrock.shared.vacuum.labelFor
import com.kodraliu.localrock.shared.vacuum.washFrequencyLabel
import com.kodraliu.localrock.ui.AppColors
import com.kodraliu.localrock.ui.components.PickerDialog
import com.kodraliu.localrock.ui.components.SettingGroup
import com.kodraliu.localrock.ui.components.SettingRowClickable
import com.kodraliu.localrock.ui.components.SettingRowSwitch
import com.kodraliu.localrock.ui.strings.SettingsStrings

/** Dock Settings in the official four groups: washing, emptying, drying, dock maintenance. */
@Composable
internal fun DockSettingsScreen(
    dockSettings: DockSettings,
    dockCapabilities: DockCapabilities,
    dockType: Int?,
    dockStatus: Int?,
    busy: Boolean,
    onWashModeChange: (Int) -> Unit,
    onWashFrequencyChange: (Int, Int?) -> Unit,
    onAutoEmptyModeChange: (Int) -> Unit,
    onEmptyWaterTank: () -> Unit,
    features: List<BetaFeatureState>,
    onFeatureToggle: (String, Boolean) -> Unit,
    onFeatureChoice: (String, Int) -> Unit,
    onMopDryingChange: (Boolean) -> Unit,
    onDryTimeChange: (Int) -> Unit,
    onWashWaterTemperatureChange: (Int) -> Unit,
    onBack: () -> Unit,
) {
    var dialogTarget by remember { mutableStateOf<String?>(null) }
    var confirmEmptyTank by remember { mutableStateOf(false) }

    val washFrequencyOptions = dockCapabilities.washFrequencyOptions
    val dryTimeOptions = dockCapabilities.dryTimeOptions(dockSettings.dryTimeSeconds)
    val smartWash = dockSettings.smartWash
    val washFrequencyIndex = washFrequencyOptions.indexOfFirst {
        smartWash != null && it.mode == smartWash.mode && (it.mode != 0 || it.intervalMinutes?.times(60) == smartWash.intervalSeconds)
    }
    val washModes = dockCapabilities.washModes(dockType, dockStatus)
    val washModeIndex = washModes.indexOfFirst { it.first == dockCapabilities.washModeCode(dockSettings.washMode, dockType) }
    val autoEmptyModes = dockCapabilities.autoEmptyModes
    val dockKnown = dockType != null && dockType != 0
    val washFrequencyEnabled = !busy && dockKnown && smartWash != null && washFrequencyOptions.isNotEmpty()
    val washModeEnabled = !busy && dockKnown && dockSettings.washMode != null && washModes.isNotEmpty()
    val autoEmptyEnabled = !busy && dockSettings.autoEmptyMode != null && autoEmptyModes.isNotEmpty()
    val rowsBy = features.groupBy { dockGroupOf(it.def) }
    fun rows(group: DockGroup) = rowsBy[group].orEmpty()
    val dryRows = rows(DockGroup.DRY)
    val otherRows = rows(DockGroup.OTHER)

    SettingsPage(title = SettingsStrings.DOCK, onBack = onBack) {
        item {
            SettingGroup {
                SettingRowClickable(Icons.Default.WaterDrop, AppColors.Water, SettingsStrings.MOP_WASH_FREQUENCY,
                    washFrequencyLabel(smartWash), enabled = washFrequencyEnabled) { dialogTarget = "wash_freq" }
                RowDivider()
                SettingRowClickable(Icons.Default.WaterDrop, AppColors.AccentCyan, SettingsStrings.WASHING_MODE,
                    dockCapabilities.washModeLabel(dockSettings.washMode, dockType), enabled = washModeEnabled) { dialogTarget = "wash_mode" }
                dockSettings.washWaterTemperature?.let { temperature ->
                    RowDivider()
                    SettingRowClickable(Icons.Default.Thermostat, AppColors.Warn, SettingsStrings.MOP_WASH_TEMPERATURE,
                        DockOptions.washWaterTemperature.labelFor(temperature), enabled = !busy) { dialogTarget = "wash_temp" }
                }
                rows(DockGroup.WASH).forEach { RowDivider(); FeatureToggleRow(it, onFeatureToggle, onFeatureChoice) }
            }
        }
        item {
            SettingGroup {
                rows(DockGroup.EMPTY).forEach { FeatureToggleRow(it, onFeatureToggle, onFeatureChoice); RowDivider() }
                SettingRowClickable(Icons.Default.Delete, AppColors.Good, SettingsStrings.EMPTY_MODE,
                    dockCapabilities.autoEmptyModeLabel(dockSettings.autoEmptyMode), enabled = autoEmptyEnabled) { dialogTarget = "auto_empty" }
            }
        }
        if (dockSettings.mopDrying != null || dryRows.isNotEmpty()) {
            item {
                SettingGroup {
                    dockSettings.mopDrying?.let { drying ->
                        SettingRowSwitch(
                            icon = Icons.Default.Air, iconTint = AppColors.AccentCyan, title = SettingsStrings.AUTO_DRYING,
                            subtitle = if (drying) SettingsStrings.MOP_DRYING_ON else SettingsStrings.MOP_DRYING_OFF,
                            checked = drying, enabled = !busy, onCheckedChange = onMopDryingChange,
                        )
                        dockSettings.dryTimeSeconds?.let { seconds ->
                            RowDivider()
                            SettingRowClickable(Icons.Default.Schedule, AppColors.AccentCyan, SettingsStrings.DRYING_DURATION,
                                dryTimeLabel(seconds), enabled = !busy) { dialogTarget = "dry_time" }
                        }
                    }
                    dryRows.forEachIndexed { i, f ->
                        if (i > 0 || dockSettings.mopDrying != null) RowDivider()
                        FeatureToggleRow(f, onFeatureToggle, onFeatureChoice)
                    }
                }
            }
        }
        item { GroupCaption(SettingsStrings.DOCK_MAINTENANCE) }
        item {
            SettingGroup {
                SettingRowClickable(Icons.Default.WaterDrop, AppColors.Water, SettingsStrings.DRAIN_ONBOARD_TANK,
                    SettingsStrings.DRAIN_ONBOARD_TANK_DETAIL, enabled = !busy) { confirmEmptyTank = true }
                rows(DockGroup.MAINTENANCE).forEach { RowDivider(); FeatureToggleRow(it, onFeatureToggle, onFeatureChoice) }
            }
        }
        if (otherRows.isNotEmpty()) item { FeatureCard(otherRows, onFeatureToggle, onFeatureChoice) }
    }

    if (confirmEmptyTank) {
        AlertDialog(
            onDismissRequest = { confirmEmptyTank = false },
            title = { Text(SettingsStrings.DRAIN_CONFIRM_TITLE) },
            text = { Text(SettingsStrings.DRAIN_CONFIRM_TEXT) },
            confirmButton = {
                TextButton(onClick = { confirmEmptyTank = false; onEmptyWaterTank() }) { Text(SettingsStrings.DRAIN_CONFIRM) }
            },
            dismissButton = { TextButton(onClick = { confirmEmptyTank = false }) { Text(SettingsStrings.CANCEL) } },
        )
    }

    when (dialogTarget) {
        "wash_freq" -> PickerDialog(
            title = SettingsStrings.MOP_WASH_FREQUENCY,
            options = washFrequencyOptions.map { it.label },
            selected = washFrequencyIndex,
            onSelect = {
                val option = washFrequencyOptions[it]
                onWashFrequencyChange(option.mode, option.intervalMinutes)
                dialogTarget = null
            },
            enabled = washFrequencyEnabled,
            onDismiss = { dialogTarget = null },
        )
        "wash_mode" -> PickerDialog(
            title = SettingsStrings.WASHING_MODE,
            options = washModes.map { it.second },
            selected = washModeIndex,
            onSelect = { onWashModeChange(washModes[it].first); dialogTarget = null },
            enabled = washModeEnabled,
            onDismiss = { dialogTarget = null },
        )
        "wash_temp" -> PickerDialog(
            title = SettingsStrings.MOP_WASH_TEMPERATURE,
            options = DockOptions.washWaterTemperature.map { it.label },
            selected = DockOptions.washWaterTemperature.indexOfFirst { it.code == dockSettings.washWaterTemperature },
            onSelect = { onWashWaterTemperatureChange(DockOptions.washWaterTemperature[it].code); dialogTarget = null },
            enabled = !busy,
            onDismiss = { dialogTarget = null },
        )
        "dry_time" -> PickerDialog(
            title = SettingsStrings.DRYING_DURATION,
            options = dryTimeOptions.map { it.label },
            selected = dryTimeOptions.indexOfFirst { it.code == dockSettings.dryTimeSeconds },
            onSelect = { onDryTimeChange(dryTimeOptions[it].code); dialogTarget = null },
            enabled = !busy,
            onDismiss = { dialogTarget = null },
        )
        "auto_empty" -> PickerDialog(
            title = SettingsStrings.EMPTY_MODE,
            options = autoEmptyModes.map { it.second },
            selected = autoEmptyModes.indexOfFirst { it.first == dockSettings.autoEmptyMode },
            onSelect = { onAutoEmptyModeChange(autoEmptyModes[it].first); dialogTarget = null },
            enabled = autoEmptyEnabled,
            onDismiss = { dialogTarget = null },
        )
    }
}

/** Off-peak charging moved to More Options > Charging Mode, so this page no longer reads it. */
@Composable
fun VacuumDockSettingsScreen(viewModel: VacuumViewModel, onBack: () -> Unit) {
    val repository = viewModel.repository
    val dockSettings by repository.dockSettings.collectAsState()
    val betaFeatures by repository.betaFeatures.collectAsState()
    val status by viewModel.status.collectAsState()
    val busy by viewModel.busy.collectAsState()
    LoadFeatureRowsOnEntry(viewModel)
    LaunchedOnEntry { runCatching { repository.fetchDockSettings() } }
    DockSettingsScreen(
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
        // Drying Start Method writes the dryer setting, so the Mop drying switch re-reads it.
        onFeatureChoice = { id, value ->
            viewModel.report { it.setBetaChoice(id, value); if (id == "drying_start_method") runCatching { it.fetchDockSettings() } }
        },
        onMopDryingChange = { on -> viewModel.run { it.setMopDrying(on) } },
        onDryTimeChange = { seconds -> viewModel.run { it.setDryTime(seconds) } },
        onWashWaterTemperatureChange = { value -> viewModel.run { it.setWashWaterTemperature(value) } },
        onBack = onBack,
    )
}
