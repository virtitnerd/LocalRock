package com.kodraliu.localrock.ui.vacuum

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kodraliu.localrock.shared.vacuum.VacuumFanPower
import com.kodraliu.localrock.shared.vacuum.VacuumStateCodes
import com.kodraliu.localrock.shared.vacuum.VacuumStatus
import com.kodraliu.localrock.shared.vacuum.WaterBoxMode
import com.kodraliu.localrock.shared.vacuum.isCustomizeModeSet
import com.kodraliu.localrock.shared.vacuum.isSmartModeSet

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ControlBar(
    status: VacuumStatus,
    busy: Boolean,
    selectedRoomCount: Int,
    onClean: () -> Unit,
    onCleanRooms: () -> Unit,
    onClearSelection: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onStop: () -> Unit,
    onDock: () -> Unit,
    onCleaningMode: () -> Unit,
    onMore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isCleaning = status.state in ACTIVE_CLEANING_STATES
    val isPaused = status.state == VacuumStateCodes.PAUSED
    val mopping = (status.waterBoxCustomMode ?: WaterBoxMode.OFF) != WaterBoxMode.OFF

    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AssistChip(
                onClick = onCleaningMode,
                label = {
                    Text(
                        when {
                            isSmartModeSet(status.fanPower, status.waterBoxCustomMode, null) -> CleanPlan.SMART.label
                            isCustomizeModeSet(status.fanPower, status.waterBoxCustomMode, null) -> CleanPlan.PER_ROOM.label
                            mopping && status.fanPower == VacuumFanPower.OFF -> "Mop"
                            mopping -> "Vac & Mop"
                            else -> "Vacuum"
                        }
                    )
                },
                leadingIcon = {
                    Icon(Icons.Default.Tune, contentDescription = null, modifier = Modifier.size(18.dp))
                },
            )
            // Tapping rooms on the map used to have no visible consequence. The selection now
            // surfaces here and retargets the primary action.
            if (selectedRoomCount > 0) {
                InputChip(
                    selected = true,
                    onClick = onClearSelection,
                    label = {
                        Text(if (selectedRoomCount == 1) "1 room" else "$selectedRoomCount rooms")
                    },
                    trailingIcon = {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Clear room selection",
                            modifier = Modifier.size(18.dp),
                        )
                    },
                )
            }
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            // One prominent button per view: its label and target follow the robot's state.
            val primaryLabel = when {
                isCleaning -> "Pause"
                selectedRoomCount > 0 -> "Clean rooms"
                isPaused -> "Resume"
                else -> "Clean"
            }
            Button(
                onClick = {
                    when {
                        isCleaning -> onPause()
                        selectedRoomCount > 0 -> onCleanRooms()
                        isPaused -> onResume()
                        else -> onClean()
                    }
                },
                enabled = !busy,
                modifier = Modifier.weight(1f).height(56.dp),
                shape = MaterialTheme.shapes.extraLarge,
            ) {
                Icon(
                    if (isCleaning) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(22.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    primaryLabel,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (isCleaning || isPaused) {
                FilledTonalIconButton(
                    onClick = onStop,
                    enabled = !busy,
                    modifier = Modifier.size(56.dp),
                ) {
                    Icon(Icons.Default.Stop, contentDescription = "Stop cleaning", modifier = Modifier.size(24.dp))
                }
            }
            FilledTonalIconButton(
                onClick = onDock,
                enabled = !busy,
                modifier = Modifier.size(56.dp),
            ) {
                Icon(Icons.Default.Home, contentDescription = "Return to dock", modifier = Modifier.size(24.dp))
            }
            FilledTonalIconButton(onClick = onMore, modifier = Modifier.size(56.dp)) {
                Icon(Icons.Default.MoreHoriz, contentDescription = "More actions", modifier = Modifier.size(24.dp))
            }
        }
    }
}
