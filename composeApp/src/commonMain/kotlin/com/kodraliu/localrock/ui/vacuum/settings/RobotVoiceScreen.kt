package com.kodraliu.localrock.ui.vacuum

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kodraliu.localrock.ui.components.SettingGroup
import com.kodraliu.localrock.ui.strings.SettingsStrings
import kotlin.math.roundToInt

/** Robot Voice (Voice Assistant on voice-control robots): the on/off switch and volume; LocalRock has no sound pack calls yet. */
@Composable
fun VacuumRobotVoiceScreen(viewModel: VacuumViewModel, onBack: () -> Unit) {
    val repository = viewModel.repository
    val caps by viewModel.capabilities.collectAsState()
    val robotSettings by repository.robotSettings.collectAsState()
    val betaFeatures by repository.betaFeatures.collectAsState()
    LoadFeatureRowsOnEntry(viewModel)
    LaunchedOnEntry { runCatching { repository.fetchRobotSettings() } }
    val voiceSwitch = betaFeatures.robotRows(RobotRowPlace.VOICE_SWITCH)
    val voiceRows = betaFeatures.robotRows(RobotRowPlace.VOICE)
    val onToggle: (String, Boolean) -> Unit = { id, on -> viewModel.report { it.setBetaFeature(id, on) } }
    var dragging by remember { mutableStateOf<Float?>(null) }
    val shown = dragging ?: robotSettings.volume.toFloat()

    SettingsPage(
        title = if (caps.voiceAssistant) SettingsStrings.VOICE_ASSISTANT else SettingsStrings.ROBOT_VOICE,
        onBack = onBack,
    ) {
        // The official Voice Assistant page puts its on/off switch above the volume.
        if (voiceSwitch.isNotEmpty()) item { FeatureCard(voiceSwitch, onToggle) }
        item {
            SettingGroup {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(SettingsStrings.VOLUME, style = MaterialTheme.typography.bodyLarge)
                        Text("${shown.roundToInt()}%", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Slider(
                        value = shown,
                        onValueChange = { dragging = it },
                        valueRange = 0f..100f,
                        onValueChangeFinished = {
                            val volume = (dragging ?: return@Slider).roundToInt()
                            dragging = null
                            viewModel.report { it.setVolume(volume) }
                        },
                    )
                    Text(SettingsStrings.VOLUME_HINT, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        if (voiceRows.isNotEmpty()) {
            item {
                SettingGroup {
                    voiceRows.forEachIndexed { i, f ->
                        if (i > 0) RowDivider()
                        FeatureToggleRow(f, onToggle)
                    }
                }
            }
        }
    }
}
