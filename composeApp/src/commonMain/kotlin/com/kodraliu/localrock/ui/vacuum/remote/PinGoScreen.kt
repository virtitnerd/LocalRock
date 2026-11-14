package com.kodraliu.localrock.ui.vacuum

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Map
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.kodraliu.localrock.shared.vacuum.map.ParsedMap

@Composable
fun VacuumPinGoScreen(viewModel: VacuumViewModel, onBack: () -> Unit) {
    val parsedMap by viewModel.repository.parsedMap.collectAsState()
    val busy by viewModel.busy.collectAsState()
    PinGoScreen(
        parsedMap = parsedMap,
        busy = busy,
        // A refusal is shown by run(); onSent only fires once the robot has accepted the target.
        onGoto = { x, y, onSent ->
            viewModel.run {
                it.gotoTarget(x, y)
                onSent()
            }
        },
        onBack = onBack,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PinGoScreen(
    parsedMap: ParsedMap?,
    busy: Boolean,
    onGoto: (x: Int, y: Int, onSent: () -> Unit) -> Unit,
    onBack: () -> Unit,
) {
    // The pin is kept as a map cell, so it stays on the same spot whatever size the map is drawn at.
    var pinCell by remember { mutableStateOf<Offset?>(null) }
    var sent by remember { mutableStateOf(false) }
    var outsideRoom by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Pin & Go") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                },
                actions = {
                    if (pinCell != null && parsedMap != null && !sent) {
                        TextButton(enabled = !busy, onClick = {
                            val cell = pinCell ?: return@TextButton
                            val pm = parsedMap ?: return@TextButton
                            // app_goto_target takes robot millimetres, not bitmap cells (#34).
                            val (xMm, yMm) = pm.cellToMm(cell)
                            onGoto(xMm, yMm) { if (pinCell == cell) sent = true }
                        }) { Text("Go") }
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (parsedMap == null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Icon(Icons.Default.Map, null, Modifier.size(48.dp), MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("Map not loaded", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Go back to the main screen and wait for the map to load",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 32.dp),
                        )
                    }
                }
            } else {
                PinGoMap(
                    map = parsedMap,
                    pinCell = pinCell,
                    onTapCell = { cell ->
                        if (parsedMap.isRoomFloorAt(cell, parsedMap.width.toFloat(), parsedMap.height.toFloat())) {
                            pinCell = cell
                            sent = false
                            outsideRoom = false
                        } else {
                            outsideRoom = true
                        }
                    },
                    modifier = Modifier.fillMaxWidth().weight(1f),
                )
                Text(
                    text = when {
                        outsideRoom -> "Pins can only go inside a room."
                        sent -> "Command sent! Robot is heading there."
                        pinCell != null -> "Target set. Tap Go to send the robot there."
                        else -> "Tap on the map to set a destination."
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (sent) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}
