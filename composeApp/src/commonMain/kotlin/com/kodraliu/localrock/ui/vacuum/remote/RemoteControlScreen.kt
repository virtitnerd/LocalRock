package com.kodraliu.localrock.ui.vacuum

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.kodraliu.localrock.ui.adaptive.CenteredContent
import com.kodraliu.localrock.ui.strings.RemoteStrings
import kotlinx.coroutines.launch

enum class RemoteMode(val label: String) {
    Joystick(RemoteStrings.JOYSTICK),
    Buttons(RemoteStrings.BUTTONS),
}

// The Buttons side: one 1.5 s move per tap, same speeds as before the redesign.
object RemoteButtons {
    const val SPEED = 0.3
    const val TURN = 0.3
    const val MOVE_MS = 1500
    val FORWARD = SPEED to 0.0
    val BACKWARD = -SPEED to 0.0
    val LEFT = 0.0 to TURN
    val RIGHT = 0.0 to -TURN
}

@Composable
fun VacuumRemoteScreen(viewModel: VacuumViewModel, onBack: () -> Unit) {
    // Started on first use, ended with app_rc_end when the page leaves (back or system back).
    val link = rememberRemoteControlLink(viewModel)
    RemoteControlScreen(link, onBack)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RemoteControlScreen(link: RemoteControlLink, onBack: () -> Unit) {
    var mode by remember { mutableStateOf(RemoteMode.Joystick) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(RemoteStrings.REMOTE_CONTROL) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, RemoteStrings.BACK) }
                },
            )
        },
    ) { padding ->
        CenteredContent {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    RemoteMode.entries.forEachIndexed { index, m ->
                        SegmentedButton(
                            selected = mode == m,
                            onClick = { mode = m },
                            shape = SegmentedButtonDefaults.itemShape(index, RemoteMode.entries.size),
                        ) { Text(m.label) }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Info, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.size(12.dp))
                        Text(
                            RemoteStrings.ENTRY_REMINDER,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Column(
                    Modifier.weight(1f).fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    when (mode) {
                        RemoteMode.Joystick -> JoystickPane(link)
                        RemoteMode.Buttons -> ButtonsPane(link)
                    }
                }
            }
        }
    }
}

@Composable
private fun JoystickPane(link: RemoteControlLink) {
    Text(
        RemoteStrings.JOYSTICK_HINT,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(32.dp))
    val scheme = MaterialTheme.colorScheme
    RemoteJoystick(
        link,
        colors = JoystickColors(
            base = scheme.surfaceVariant,
            border = scheme.outlineVariant,
            thumbIdle = scheme.primary.copy(alpha = 0.6f),
            thumbActive = scheme.primary,
        ),
        size = 220.dp,
        thumb = 84.dp,
    )
}

@Composable
private fun ButtonsPane(link: RemoteControlLink) {
    val scope = rememberCoroutineScope()
    val onMove: (Pair<Double, Double>) -> Unit = { (v, o) ->
        scope.launch {
            link.ensureStarted()
            if (!link.move(v, o, RemoteButtons.MOVE_MS)) link.restart()
        }
    }
    Text(
        RemoteStrings.BUTTONS_HINT,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(48.dp))
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        RcButton(Icons.Default.KeyboardArrowUp, RemoteStrings.FORWARD) { onMove(RemoteButtons.FORWARD) }
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RcButton(Icons.AutoMirrored.Filled.KeyboardArrowLeft, RemoteStrings.TURN_LEFT) { onMove(RemoteButtons.LEFT) }
            FilledIconButton(
                // Stops the move but keeps remote control on; leaving the page ends it.
                onClick = { scope.launch { if (link.started) link.stopMove() } },
                modifier = Modifier.size(72.dp),
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                ),
            ) {
                Icon(Icons.Default.Stop, RemoteStrings.STOP, Modifier.size(32.dp))
            }
            RcButton(Icons.AutoMirrored.Filled.KeyboardArrowRight, RemoteStrings.TURN_RIGHT) { onMove(RemoteButtons.RIGHT) }
        }
        RcButton(Icons.Default.KeyboardArrowDown, RemoteStrings.BACKWARD) { onMove(RemoteButtons.BACKWARD) }
    }
    Spacer(Modifier.height(32.dp))
    Text(
        RemoteStrings.BUTTONS_DURATION,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun RcButton(icon: ImageVector, contentDescription: String, onClick: () -> Unit) {
    FilledTonalIconButton(
        onClick = onClick,
        modifier = Modifier.size(72.dp),
    ) {
        Icon(icon, contentDescription, Modifier.size(32.dp))
    }
}
