package com.kodraliu.localrock.ui.vacuum

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kodraliu.localrock.shared.vacuum.BetaFeatureState
import com.kodraliu.localrock.shared.vacuum.DockCapabilities
import com.kodraliu.localrock.shared.vacuum.DockOptions
import com.kodraliu.localrock.shared.vacuum.DockSettings
import com.kodraliu.localrock.shared.vacuum.ValleySettings
import com.kodraliu.localrock.shared.vacuum.dryTimeLabel
import com.kodraliu.localrock.shared.vacuum.labelFor
import com.kodraliu.localrock.shared.vacuum.washFrequencyLabel
import com.kodraliu.localrock.ui.AppColors
import com.kodraliu.localrock.ui.adaptive.CenteredContent

@OptIn(ExperimentalMaterial3Api::class)
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
    valleySettings: ValleySettings?,
    onMopDryingChange: (Boolean) -> Unit,
    onDryTimeChange: (Int) -> Unit,
    onWashWaterTemperatureChange: (Int) -> Unit,
    onOpenValley: () -> Unit,
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Dock Settings") },
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
                        icon = Icons.Default.WaterDrop,
                        iconTint = AppColors.Water,
                        title = "Mop wash frequency",
                        subtitle = washFrequencyLabel(smartWash),
                        enabled = washFrequencyEnabled,
                        onClick = { dialogTarget = "wash_freq" },
                    )
                }
                item { HorizontalDivider(Modifier.padding(start = 72.dp)) }
                item {
                    SettingRowClickable(
                        icon = Icons.Default.WaterDrop,
                        iconTint = AppColors.AccentCyan,
                        title = "Washing mode",
                        subtitle = dockCapabilities.washModeLabel(dockSettings.washMode, dockType),
                        enabled = washModeEnabled,
                        onClick = { dialogTarget = "wash_mode" },
                    )
                }
                dockSettings.washWaterTemperature?.let { temperature ->
                    item { HorizontalDivider(Modifier.padding(start = 72.dp)) }
                    item {
                        SettingRowClickable(
                            icon = Icons.Default.Thermostat,
                            iconTint = AppColors.Warn,
                            title = "Washing water temperature",
                            subtitle = DockOptions.washWaterTemperature.labelFor(temperature),
                            enabled = !busy,
                            onClick = { dialogTarget = "wash_temp" },
                        )
                    }
                }
                dockSettings.mopDrying?.let { drying ->
                    item { HorizontalDivider(Modifier.padding(start = 72.dp)) }
                    item {
                        SettingRowSwitch(
                            icon = Icons.Default.Air,
                            iconTint = AppColors.AccentCyan,
                            title = "Mop drying",
                            subtitle = if (drying) "Dries the mops on the dock after cleaning" else "Mops are left wet on the dock",
                            checked = drying,
                            enabled = !busy,
                            onCheckedChange = onMopDryingChange,
                        )
                    }
                    dockSettings.dryTimeSeconds?.let { seconds ->
                        item { HorizontalDivider(Modifier.padding(start = 72.dp)) }
                        item {
                            SettingRowClickable(
                                icon = Icons.Default.Schedule,
                                iconTint = AppColors.AccentCyan,
                                title = "Drying time",
                                subtitle = dryTimeLabel(seconds),
                                enabled = !busy,
                                onClick = { dialogTarget = "dry_time" },
                            )
                        }
                    }
                }
                item { HorizontalDivider(Modifier.padding(start = 72.dp)) }
                item {
                    SettingRowClickable(
                        icon = Icons.Default.Delete,
                        iconTint = AppColors.Good,
                        title = "Auto-empty mode",
                        subtitle = dockCapabilities.autoEmptyModeLabel(dockSettings.autoEmptyMode),
                        enabled = autoEmptyEnabled,
                        onClick = { dialogTarget = "auto_empty" },
                    )
                }
                valleySettings?.let { valley ->
                    item { HorizontalDivider(Modifier.padding(start = 72.dp)) }
                    item {
                        SettingRowClickable(
                            icon = Icons.Default.Schedule,
                            iconTint = AppColors.Good,
                            title = "Off-peak electricity",
                            subtitle = if (valley.enabled) "${clockText(valley.startHour, valley.startMinute)} to ${clockText(valley.endHour, valley.endMinute)}" else "Off",
                            enabled = !busy,
                            onClick = onOpenValley,
                        )
                    }
                }
                item { HorizontalDivider(Modifier.padding(start = 72.dp)) }
                item {
                    SettingRowClickable(
                        icon = Icons.Default.WaterDrop,
                        iconTint = AppColors.Water,
                        title = "Internal water tank",
                        subtitle = "Empty through the dock",
                        enabled = !busy,
                        onClick = { confirmEmptyTank = true },
                    )
                }
                items(features, key = { it.def.id }) { f ->
                    HorizontalDivider(Modifier.padding(start = 72.dp))
                    FeatureToggleRow(f, onFeatureToggle)
                }
            }
        }
    }

    if (confirmEmptyTank) {
        AlertDialog(
            onDismissRequest = { confirmEmptyTank = false },
            title = { Text("Empty the water tank?") },
            text = { Text("The dock pumps the water out of the robot's built-in tank. Make sure the robot is on the dock.") },
            confirmButton = {
                TextButton(onClick = { confirmEmptyTank = false; onEmptyWaterTank() }) { Text("Empty") }
            },
            dismissButton = { TextButton(onClick = { confirmEmptyTank = false }) { Text("Cancel") } },
        )
    }

    when (dialogTarget) {
        "wash_freq" -> SimplePickerDialog(
            title = "Mop wash frequency",
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
        "wash_mode" -> SimplePickerDialog(
            title = "Washing mode",
            options = washModes.map { it.second },
            selected = washModeIndex,
            onSelect = { onWashModeChange(washModes[it].first); dialogTarget = null },
            enabled = washModeEnabled,
            onDismiss = { dialogTarget = null },
        )
        "wash_temp" -> SimplePickerDialog(
            title = "Washing water temperature",
            options = DockOptions.washWaterTemperature.map { it.label },
            selected = DockOptions.washWaterTemperature.indexOfFirst { it.code == dockSettings.washWaterTemperature },
            onSelect = { onWashWaterTemperatureChange(DockOptions.washWaterTemperature[it].code); dialogTarget = null },
            enabled = !busy,
            onDismiss = { dialogTarget = null },
        )
        "dry_time" -> SimplePickerDialog(
            title = "Drying time",
            options = dryTimeOptions.map { it.label },
            selected = dryTimeOptions.indexOfFirst { it.code == dockSettings.dryTimeSeconds },
            onSelect = { onDryTimeChange(dryTimeOptions[it].code); dialogTarget = null },
            enabled = !busy,
            onDismiss = { dialogTarget = null },
        )
        "auto_empty" -> SimplePickerDialog(
            title = "Auto-empty mode",
            options = autoEmptyModes.map { it.second },
            selected = autoEmptyModes.indexOfFirst { it.first == dockSettings.autoEmptyMode },
            onSelect = { onAutoEmptyModeChange(autoEmptyModes[it].first); dialogTarget = null },
            enabled = autoEmptyEnabled,
            onDismiss = { dialogTarget = null },
        )
    }
}

private fun clockText(hour: Int, minute: Int) = "${hour.toString().padStart(2, '0')}:${minute.toString().padStart(2, '0')}"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ValleyScreen(
    valleySettings: ValleySettings?,
    onEnable: (startHour: Int, startMinute: Int, endHour: Int, endMinute: Int) -> Unit,
    onDisable: () -> Unit,
    onBack: () -> Unit,
) {
    val settings = valleySettings ?: ValleySettings()
    var enabled by remember(valleySettings?.enabled) { mutableStateOf(settings.enabled) }
    var startHour by remember(valleySettings) { mutableStateOf(settings.startHour) }
    var startMinute by remember(valleySettings) { mutableStateOf(settings.startMinute) }
    var endHour by remember(valleySettings) { mutableStateOf(settings.endHour) }
    var endMinute by remember(valleySettings) { mutableStateOf(settings.endMinute) }

    fun commit() = if (enabled) onEnable(startHour, startMinute, endHour, endMinute) else onDisable()

    // Each tap on a stepper changes a time, so the robot is written once they stop, not on every tap.
    var timesTouched by remember { mutableStateOf(false) }
    LaunchedEffect(startHour, startMinute, endHour, endMinute) {
        if (!timesTouched) return@LaunchedEffect
        kotlinx.coroutines.delay(800)
        commit()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Off-peak electricity") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            )
        },
    ) { padding ->
        CenteredContent {
            Column(
                modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(padding).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Limit charging and drying to off-peak hours", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            if (enabled) "${clockText(startHour, startMinute)} to ${clockText(endHour, endMinute)}" else "Off",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = enabled, onCheckedChange = { enabled = it; commit() })
                }
                if (enabled) {
                    HorizontalDivider()
                    Text("Start time", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    ClockStepper(startHour, startMinute, onHourChange = { startHour = it; timesTouched = true }, onMinuteChange = { startMinute = it; timesTouched = true })
                    Text("End time", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    ClockStepper(endHour, endMinute, onHourChange = { endHour = it; timesTouched = true }, onMinuteChange = { endMinute = it; timesTouched = true })
                }
            }
        }
    }
}
