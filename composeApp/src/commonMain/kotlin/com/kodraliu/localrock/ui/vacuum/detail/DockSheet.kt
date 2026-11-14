package com.kodraliu.localrock.ui.vacuum

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Dock
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kodraliu.localrock.shared.vacuum.DockCapabilities
import com.kodraliu.localrock.shared.vacuum.DockErrorCodes
import com.kodraliu.localrock.shared.vacuum.DockSettings
import com.kodraliu.localrock.shared.vacuum.VacuumStateCodes
import com.kodraliu.localrock.shared.vacuum.VacuumStatus
import com.kodraliu.localrock.shared.vacuum.washFrequencyLabel
import com.kodraliu.localrock.ui.AppColors
import com.kodraliu.localrock.ui.components.PickerDialog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DockSheetContent(
    status: VacuumStatus,
    busy: Boolean,
    dockSettings: DockSettings,
    dockCapabilities: DockCapabilities,
    onEmptyDustbin: () -> Unit,
    onWashMop: () -> Unit,
    onDryMop: () -> Unit,
    onStopDry: () -> Unit,
    onWashModeChange: (Int) -> Unit,
    onWashFrequencyChange: (Int, Int?) -> Unit,
    onAutoEmptyModeChange: (Int) -> Unit,
) {
    var selectedTab by remember { mutableIntStateOf(0) }

    Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = 16.dp)) {
        Spacer(Modifier.height(12.dp))
        SecondaryTabRow(selectedTabIndex = selectedTab) {
            listOf("Dock control", "Dock settings").forEachIndexed { i, title ->
                Tab(selected = selectedTab == i, onClick = { selectedTab = i }, text = { Text(title) })
            }
        }
        Box(Modifier.fillMaxWidth().heightIn(min = 340.dp)) {
            when (selectedTab) {
                0 -> DockControlTab(status, busy, onEmptyDustbin, onWashMop, onDryMop, onStopDry)
                1 -> DockSettingsTab(
                    dockSettings, dockCapabilities, status.dockType, status.dockStatus, busy,
                    onWashModeChange, onWashFrequencyChange, onAutoEmptyModeChange,
                )
            }
        }
    }
}

@Composable
private fun DockControlTab(
    status: VacuumStatus,
    busy: Boolean,
    onEmptyDustbin: () -> Unit,
    onWashMop: () -> Unit,
    onDryMop: () -> Unit,
    onStopDry: () -> Unit,
) {
    val waterAttached = status.waterBoxStatus == 1
    val dockError = status.dockErrorStatus
    val drying = status.dryStatus == 1 || status.state == VacuumStateCodes.DRYING_MOP

    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            DockStatusRow(
                label = "Clean water tank",
                value = when {
                    dockError == null -> "Unknown"
                    dockError == DockErrorCodes.WATER_EMPTY -> "Empty — refill"
                    else -> "OK"
                },
                color = when {
                    dockError == null -> MaterialTheme.colorScheme.onSurfaceVariant
                    dockError == DockErrorCodes.WATER_EMPTY -> MaterialTheme.colorScheme.error
                    else -> AppColors.Good
                },
            )
            DockStatusRow(
                label = "Dirty water tank",
                value = when (dockError) {
                    null -> "Unknown"
                    DockErrorCodes.DIRTY_TANK_FULL -> "Full — empty it"
                    DockErrorCodes.DIRTY_TANK_LATCH_OPEN -> "Latch open"
                    else -> "OK"
                },
                color = when (dockError) {
                    null -> MaterialTheme.colorScheme.onSurfaceVariant
                    DockErrorCodes.DIRTY_TANK_FULL, DockErrorCodes.DIRTY_TANK_LATCH_OPEN -> MaterialTheme.colorScheme.error
                    else -> AppColors.Good
                },
            )
            DockStatusRow(
                label = "Dust bag",
                value = when (dockError) {
                    null -> "Unknown"
                    DockErrorCodes.NO_DUSTBIN -> "Missing"
                    DockErrorCodes.DUCT_BLOCKAGE -> "Duct blocked"
                    else -> "OK"
                },
                color = when (dockError) {
                    null -> MaterialTheme.colorScheme.onSurfaceVariant
                    DockErrorCodes.NO_DUSTBIN, DockErrorCodes.DUCT_BLOCKAGE -> MaterialTheme.colorScheme.error
                    else -> AppColors.Good
                },
            )
            if (drying) {
                DockStatusRow(
                    label = "Mop dryer",
                    value = status.remainingDryTimeSec
                        ?.takeIf { it > 0 }
                        ?.let { sec -> "Drying — ${((sec + 59) / 60)}m left" }
                        ?: "Drying",
                    color = AppColors.Warn,
                )
            }
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DockActionButton(Modifier.weight(1f), Icons.Default.Delete, AppColors.Good, "Empty", !busy, onEmptyDustbin)
            DockActionButton(Modifier.weight(1f), Icons.Default.WaterDrop, AppColors.Water, "Wash", !busy && waterAttached, onWashMop)
            if (drying) {
                DockActionButton(Modifier.weight(1f), Icons.Default.Stop, AppColors.Warn, "Stop dry", !busy, onStopDry)
            } else {
                DockActionButton(Modifier.weight(1f), Icons.Default.Air, AppColors.Warn, "Dry", !busy, onDryMop)
            }
        }
    }
}

@Composable
private fun DockStatusRow(label: String, value: String, color: Color) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(Modifier.size(8.dp).background(color, CircleShape))
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyMedium, color = color, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun DockActionButton(modifier: Modifier = Modifier, icon: ImageVector, iconTint: Color, label: String, enabled: Boolean, onClick: () -> Unit) {
    Surface(onClick = onClick, modifier = modifier, enabled = enabled, shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Column(modifier = Modifier.padding(vertical = 14.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Surface(shape = CircleShape, color = if (enabled) iconTint.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.size(40.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, null, modifier = Modifier.size(20.dp), tint = if (enabled) iconTint else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f))
                }
            }
            Text(label, style = MaterialTheme.typography.labelMedium, color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f))
        }
    }
}

@Composable
private fun DockSettingsTab(
    dockSettings: DockSettings,
    dockCapabilities: DockCapabilities,
    dockType: Int?,
    dockStatus: Int?,
    busy: Boolean,
    onWashModeChange: (Int) -> Unit,
    onWashFrequencyChange: (Int, Int?) -> Unit,
    onAutoEmptyModeChange: (Int) -> Unit,
) {
    var dialogTarget by remember { mutableStateOf<String?>(null) }
    val washFrequencyOptions = dockCapabilities.washFrequencyOptions
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

    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DockSettingCard(Modifier.weight(1f), Icons.Default.Repeat, AppColors.Water, "Mop wash frequency", washFrequencyLabel(smartWash), washFrequencyEnabled) { dialogTarget = "wash_freq" }
            DockSettingCard(Modifier.weight(1f), Icons.Default.WaterDrop, AppColors.Water, "Washing mode", dockCapabilities.washModeLabel(dockSettings.washMode, dockType), washModeEnabled) { dialogTarget = "wash_mode" }
        }
        DockSettingCard(Modifier.fillMaxWidth(), Icons.Default.Delete, MaterialTheme.colorScheme.primary, "Auto-empty mode", dockCapabilities.autoEmptyModeLabel(dockSettings.autoEmptyMode), autoEmptyEnabled) { dialogTarget = "auto_empty" }
    }

    when (dialogTarget) {
        "wash_freq" -> PickerDialog(
            title = "Mop wash frequency",
            options = washFrequencyOptions.map { it.label },
            selected = washFrequencyIndex,
            onSelect = { washFrequencyOptions[it].let { option -> onWashFrequencyChange(option.mode, option.intervalMinutes) }; dialogTarget = null },
            enabled = washFrequencyEnabled,
            onDismiss = { dialogTarget = null },
        )
        "wash_mode" -> PickerDialog(
            title = "Washing mode",
            options = washModes.map { it.second },
            selected = washModeIndex,
            onSelect = { onWashModeChange(washModes[it].first); dialogTarget = null },
            enabled = washModeEnabled,
            onDismiss = { dialogTarget = null },
        )
        "auto_empty" -> PickerDialog(
            title = "Auto-empty mode",
            options = autoEmptyModes.map { it.second },
            selected = autoEmptyModes.indexOfFirst { it.first == dockSettings.autoEmptyMode },
            onSelect = { onAutoEmptyModeChange(autoEmptyModes[it].first); dialogTarget = null },
            enabled = autoEmptyEnabled,
            onDismiss = { dialogTarget = null },
        )
    }
}

@Composable
private fun DockSettingCard(modifier: Modifier, icon: ImageVector, iconTint: Color, title: String, value: String, enabled: Boolean, onClick: () -> Unit) {
    Surface(onClick = onClick, modifier = modifier, enabled = enabled, shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Surface(shape = CircleShape, color = iconTint.copy(alpha = 0.15f), modifier = Modifier.size(36.dp)) {
                Box(contentAlignment = Alignment.Center) { Icon(icon, null, Modifier.size(18.dp), iconTint) }
            }
            Text(title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(value, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f), maxLines = 1)
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, Modifier.size(14.dp), MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
