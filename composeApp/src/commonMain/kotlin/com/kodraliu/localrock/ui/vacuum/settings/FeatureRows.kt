package com.kodraliu.localrock.ui.vacuum

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kodraliu.localrock.shared.vacuum.BetaFeatureState
import com.kodraliu.localrock.shared.vacuum.FeatureChoice
import com.kodraliu.localrock.shared.vacuum.choiceLabelFor
import com.kodraliu.localrock.shared.vacuum.FeatureSection
import com.kodraliu.localrock.ui.AppColors
import com.kodraliu.localrock.ui.components.SettingRowSwitch
import com.kodraliu.localrock.ui.components.SettingRowClickable
import androidx.compose.runtime.collectAsState
import com.kodraliu.localrock.shared.vacuum.inSection
import com.kodraliu.localrock.ui.components.SettingGroup
import com.kodraliu.localrock.ui.strings.SettingsStrings

/** One feature the robot reports, as a switch under the page it belongs to. */
@Composable
internal fun FeatureToggleRow(
    f: BetaFeatureState,
    onToggle: (String, Boolean) -> Unit,
    onChoice: (String, Int) -> Unit = { _, _ -> },
) {
    val icon = when (f.def.section) {
        FeatureSection.CARPET -> Icons.Default.GridView
        FeatureSection.FLOOR -> Icons.Default.CleaningServices
        FeatureSection.OBSTACLE -> Icons.Default.Sensors
        FeatureSection.DOCK -> Icons.Default.Home
        FeatureSection.ROBOT -> Icons.Default.Settings
        FeatureSection.BETA -> Icons.Default.Science
    }
    val choices = f.def.choices
    if (choices != null) {
        var showPicker by remember { mutableStateOf(false) }
        SettingRowClickable(
            icon = icon,
            iconTint = AppColors.AccentCyan,
            title = f.def.title,
            subtitle = f.choiceLabel ?: f.def.description,
            onClick = { showPicker = true },
        )
        if (showPicker) {
            ChoicePickerDialog(
                title = f.def.title,
                choices = choices,
                selected = f.value,
                onSelect = { onChoice(f.def.id, it); showPicker = false },
                onDismiss = { showPicker = false },
            )
        }
        return
    }
    SettingRowSwitch(
        icon = icon,
        iconTint = AppColors.AccentCyan,
        title = f.def.title,
        subtitle = f.def.description,
        checked = f.enabled,
        onCheckedChange = { onToggle(f.def.id, it) },
    )
}

/** Radio list of a multi-choice setting's values; a current value with no listed choice shows as an extra "Other (n)" line. */
@Composable
private fun ChoicePickerDialog(
    title: String,
    choices: List<FeatureChoice>,
    selected: Int?,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val unknown = selected?.takeIf { v -> choices.none { it.value == v } }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                choices.forEach { choice ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = choice.value == selected, onClick = { onSelect(choice.value) })
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text(choice.label, style = MaterialTheme.typography.bodyMedium)
                            choice.description?.let {
                                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
                if (unknown != null) {
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = true, enabled = false, onClick = {})
                        Spacer(Modifier.width(8.dp))
                        Text(choiceLabelFor(choices, unknown), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Floor Cleaning Setting: the robot's floor rows in the official groups, one card each. */
@Composable
fun VacuumFloorSettingsScreen(viewModel: VacuumViewModel, onBack: () -> Unit) {
    val betaFeatures by viewModel.repository.betaFeatures.collectAsState()
    LoadFeatureRowsOnEntry(viewModel)
    val onToggle: (String, Boolean) -> Unit = { id, on -> viewModel.report { it.setBetaFeature(id, on) } }
    val onChoice: (String, Int) -> Unit = { id, value -> viewModel.report { it.setBetaChoice(id, value) } }
    SettingsPage(title = SettingsStrings.FLOOR, onBack = onBack) {
        floorFeatureGroups(betaFeatures).forEach { group ->
            item(key = group.first().def.id) { FeatureCard(group, onToggle, onChoice) }
        }
    }
}

/** Reactive Obstacle Avoidance: collision rows first, as the official page orders them. */
@Composable
fun VacuumObstacleSettingsScreen(viewModel: VacuumViewModel, onBack: () -> Unit) {
    val betaFeatures by viewModel.repository.betaFeatures.collectAsState()
    LoadFeatureRowsOnEntry(viewModel)
    val rows = betaFeatures.inSection(FeatureSection.OBSTACLE)
        .sortedBy { listOf("avoid_collision", "follow_low_obstacles").indexOf(it.def.id).let { i -> if (i < 0) Int.MAX_VALUE else i } }
    SettingsPage(title = SettingsStrings.OBSTACLE, onBack = onBack) {
        if (rows.isNotEmpty()) item { FeatureCard(rows, { id, on -> viewModel.report { it.setBetaFeature(id, on) } }) }
    }
}

/** A card of feature rows with dividers between them. */
@Composable
internal fun FeatureCard(
    rows: List<BetaFeatureState>,
    onToggle: (String, Boolean) -> Unit,
    onChoice: (String, Int) -> Unit = { _, _ -> },
) {
    SettingGroup {
        rows.forEachIndexed { i, f ->
            if (i > 0) RowDivider()
            FeatureToggleRow(f, onToggle, onChoice)
        }
    }
}
