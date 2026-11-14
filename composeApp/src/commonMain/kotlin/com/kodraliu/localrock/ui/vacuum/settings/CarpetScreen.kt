package com.kodraliu.localrock.ui.vacuum

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.kodraliu.localrock.shared.vacuum.BetaFeatureState
import com.kodraliu.localrock.shared.vacuum.FeatureSection
import com.kodraliu.localrock.shared.vacuum.inSection
import com.kodraliu.localrock.ui.components.SettingGroup
import com.kodraliu.localrock.ui.strings.SettingsStrings

/** Carpet Cleaning Setting: one card per carpet mode, then the robot's carpet switches. */
@Composable
internal fun CarpetScreen(
    carpetMode: Int?,
    features: List<BetaFeatureState>,
    onCarpetModeChange: (Int) -> Unit,
    onToggle: (String, Boolean) -> Unit,
    onBack: () -> Unit,
) {
    SettingsPage(title = SettingsStrings.CARPET, onBack = onBack) {
        carpetModeCards(carpetMode).forEach { card ->
            item(key = "mode-${card.value}") {
                SettingGroup {
                    Surface(onClick = { if (card.value != carpetMode) onCarpetModeChange(card.value) }, color = Color.Transparent) {
                        Row(
                            Modifier.fillMaxWidth().padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(card.title, style = MaterialTheme.typography.titleMedium)
                                Text(card.detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            RadioButton(selected = card.value == carpetMode, onClick = { if (card.value != carpetMode) onCarpetModeChange(card.value) })
                        }
                    }
                }
            }
        }
        if (features.isNotEmpty()) {
            item { GroupCaption(SettingsStrings.CARPET_ADVANCED) }
            item {
                SettingGroup {
                    features.forEachIndexed { i, f ->
                        if (i > 0) RowDivider()
                        FeatureToggleRow(f, onToggle)
                    }
                }
            }
        }
        item { GroupFootnote(SettingsStrings.CARPET_FOOTER) }
    }
}

@Composable
fun VacuumCarpetScreen(viewModel: VacuumViewModel, onBack: () -> Unit) {
    val repository = viewModel.repository
    val robotSettings by repository.robotSettings.collectAsState()
    val betaFeatures by repository.betaFeatures.collectAsState()
    LoadFeatureRowsOnEntry(viewModel)
    LaunchedOnEntry { runCatching { repository.fetchRobotSettings() } }
    CarpetScreen(
        carpetMode = robotSettings.carpetMode,
        features = betaFeatures.inSection(FeatureSection.CARPET),
        onCarpetModeChange = { mode -> viewModel.report { it.setCarpetMode(mode) } },
        onToggle = { id, on -> viewModel.report { it.setBetaFeature(id, on) } },
        onBack = onBack,
    )
}
