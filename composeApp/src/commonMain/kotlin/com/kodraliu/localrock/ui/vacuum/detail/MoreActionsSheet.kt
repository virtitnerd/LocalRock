package com.kodraliu.localrock.ui.vacuum

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Dock
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Gamepad
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kodraliu.localrock.ui.AppColors

internal enum class MoreAction { ROOMS, ZONES, PIN_GO, REMOTE, SCHEDULE, CAMERA, DOCK, HISTORY, MAPS, EDIT_MAP, ROUTINES, FIND_PET }

@Composable
internal fun MoreActionsSheetContent(showFindPet: Boolean, showCamera: Boolean, onAction: (MoreAction) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            // Scrolls on short screens where the half-expanded sheet can't show every row.
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding()
            .padding(horizontal = 16.dp)
            .padding(bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            "More actions",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(start = 4.dp, bottom = 4.dp),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MoreActionItem(Modifier.weight(1f), Icons.Default.GridView, "Rooms", MaterialTheme.colorScheme.primary) { onAction(MoreAction.ROOMS) }
            MoreActionItem(Modifier.weight(1f), Icons.Default.SelectAll, "Zone clean", AppColors.AccentIndigo) { onAction(MoreAction.ZONES) }
            MoreActionItem(Modifier.weight(1f), Icons.Default.Place, "Pin & Go", AppColors.AccentPurple) { onAction(MoreAction.PIN_GO) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MoreActionItem(Modifier.weight(1f), Icons.Default.Gamepad, "Remote", AppColors.AccentCyan) { onAction(MoreAction.REMOTE) }
            MoreActionItem(Modifier.weight(1f), Icons.Default.Schedule, "Schedule", AppColors.Water) { onAction(MoreAction.SCHEDULE) }
            MoreActionItem(Modifier.weight(1f), Icons.Default.Edit, "Edit map", AppColors.AccentRed) { onAction(MoreAction.EDIT_MAP) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MoreActionItem(Modifier.weight(1f), Icons.Default.Dock, "Dock & care", AppColors.Good) { onAction(MoreAction.DOCK) }
            MoreActionItem(Modifier.weight(1f), Icons.Default.History, "History", MaterialTheme.colorScheme.tertiary) { onAction(MoreAction.HISTORY) }
            MoreActionItem(Modifier.weight(1f), Icons.Default.Map, "Maps", AppColors.Warn) { onAction(MoreAction.MAPS) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MoreActionItem(Modifier.weight(1f), Icons.AutoMirrored.Filled.PlaylistPlay, "Routines", MaterialTheme.colorScheme.secondary) { onAction(MoreAction.ROUTINES) }
            if (showFindPet) {
                MoreActionItem(Modifier.weight(1f), Icons.Default.Pets, "Find my pet", AppColors.AccentYellow) { onAction(MoreAction.FIND_PET) }
            } else {
                Spacer(Modifier.weight(1f))
            }
            if (showCamera) {
                MoreActionItem(Modifier.weight(1f), Icons.Default.Videocam, "Camera (beta)", AppColors.AccentRed) { onAction(MoreAction.CAMERA) }
            } else {
                Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun MoreActionItem(
    modifier: Modifier,
    icon: ImageVector,
    label: String,
    iconTint: Color,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column(
            modifier = Modifier.padding(vertical = 16.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Surface(shape = CircleShape, color = iconTint.copy(alpha = 0.15f), modifier = Modifier.size(40.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp), tint = iconTint)
                }
            }
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
