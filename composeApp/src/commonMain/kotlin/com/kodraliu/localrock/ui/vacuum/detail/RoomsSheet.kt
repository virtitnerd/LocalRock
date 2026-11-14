package com.kodraliu.localrock.ui.vacuum

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kodraliu.localrock.shared.vacuum.map.ParsedMapRoom

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun RoomsSheetContent(
    rooms: List<ParsedMapRoom>,
    selectedRoomIds: List<Int>,
    busy: Boolean,
    onRoomToggle: (Int) -> Unit,
    onCleanRooms: () -> Unit,
    onLoadMap: () -> Unit,
    onDefaultOrder: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).navigationBarsPadding().padding(bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Select rooms", style = MaterialTheme.typography.headlineSmall)
        if (rooms.isEmpty()) {
            Text("Load the map first to discover your rooms.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedButton(onClick = onLoadMap, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Refresh, null); Spacer(Modifier.width(8.dp)); Text("Load map")
            }
        } else {
            val hint = if (selectedRoomIds.isEmpty())
                "Tap rooms to select — they'll be cleaned in the order you pick."
            else
                "${selectedRoomIds.size} selected — tap to reorder or remove."
            Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                rooms.forEach { room ->
                    val order = selectedRoomIds.indexOf(room.id).takeIf { it >= 0 }?.plus(1)
                    FilterChip(
                        selected = room.id in selectedRoomIds,
                        onClick = { onRoomToggle(room.id) },
                        enabled = !busy,
                        label = {
                            if (order != null) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp)) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text("$order", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimary)
                                        }
                                    }
                                    Text(room.name)
                                }
                            } else {
                                Text(room.name)
                            }
                        },
                    )
                }
            }
            Button(onClick = onCleanRooms, enabled = !busy && selectedRoomIds.isNotEmpty(), modifier = Modifier.fillMaxWidth().height(56.dp), shape = MaterialTheme.shapes.extraLarge) {
                Icon(Icons.Default.PlayArrow, null)
                Spacer(Modifier.width(8.dp))
                Text("Clean selected rooms")
            }
            TextButton(onClick = onDefaultOrder, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                Text("Default cleaning order")
            }
        }
    }
}
