package com.kodraliu.localrock.ui.vacuum

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kodraliu.localrock.shared.vacuum.FIRMWARE_MIN_BATTERY
import com.kodraliu.localrock.shared.vacuum.FirmwareProgress
import com.kodraliu.localrock.ui.adaptive.CenteredContent
import com.kodraliu.localrock.ui.components.BonusBadge
import com.kodraliu.localrock.ui.components.BonusKind
import com.kodraliu.localrock.ui.strings.SecondaryStrings

/** The text shown for each stage of an install. */
internal fun firmwareProgressText(progress: FirmwareProgress): String = when (progress) {
    FirmwareProgress.Idle -> ""
    FirmwareProgress.Starting -> "Asking the server to start the update…"
    is FirmwareProgress.Downloading -> progress.percent?.let { "Downloading the update: $it%" } ?: "Downloading the update…"
    FirmwareProgress.Installing -> "Installing. This takes a few minutes and the robot will restart."
    FirmwareProgress.Restarting -> "The robot is restarting. It will be back in about a minute."
    is FirmwareProgress.Done -> progress.version?.let { "Updated. The robot now runs $it." } ?: "The update finished."
    is FirmwareProgress.Failed -> progress.reason
}

/** The official prompt's second button: "Exit" when the update is forced, else "Later". */
internal fun firmwareDismissLabel(force: Boolean): String = if (force) SecondaryStrings.EXIT else SecondaryStrings.LATER

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FirmwareUpdateScreen(viewModel: VacuumViewModel, onBack: () -> Unit) {
    val state by viewModel.firmwareUpdate.collectAsState()
    val progress by viewModel.firmwareProgress.collectAsState()
    val robotStatus by viewModel.status.collectAsState()
    var confirm by remember { mutableStateOf(false) }
    // Like the official app, a found update opens the "New Version Available" prompt once per visit.
    var prompted by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { if (state is FirmwareUpdateState.Idle) viewModel.checkFirmwareUpdate() }
    LaunchedEffect(state) {
        if (state is FirmwareUpdateState.Available && !prompted && !progress.running) { prompted = true; confirm = true }
    }

    // Recomputed whenever the robot's status changes, since it decides if the button works.
    val blocker = remember(robotStatus) { viewModel.firmwareBlocker() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(SecondaryStrings.FIRMWARE_UPDATES) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
            )
        },
    ) { padding ->
        CenteredContent {
            Column(
                Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                when (val s = state) {
                    FirmwareUpdateState.Idle, FirmwareUpdateState.Checking -> Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                        Text(SecondaryStrings.CHECKING)
                    }
                    is FirmwareUpdateState.UpToDate -> VersionCard(SecondaryStrings.UP_TO_DATE, s.current, null, null)
                    is FirmwareUpdateState.Available -> VersionCard(SecondaryStrings.NEW_VERSION, s.current, s.latest, s.notes)
                    is FirmwareUpdateState.Unavailable -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("The update check is not available.", style = MaterialTheme.typography.titleMedium)
                        Text(s.reason, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                    }
                }

                val available = state is FirmwareUpdateState.Available
                if (available && !progress.running) {
                    Button(onClick = { confirm = true }, enabled = blocker == null, modifier = Modifier.fillMaxWidth()) {
                        Text(SecondaryStrings.UPDATE_NOW)
                    }
                }

                // LocalRock extra: the install runs through your server, with its own progress and re-check.
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(SecondaryStrings.LOCAL_INSTALL, style = MaterialTheme.typography.titleSmall)
                    BonusBadge(BonusKind.LocalRock)
                }
                if (progress != FirmwareProgress.Idle) ProgressCard(progress)
                if (available && !progress.running) {
                    Text(
                        "The update is downloaded from your server and installed by the robot. It needs the robot on its dock " +
                            "with at least $FIRMWARE_MIN_BATTERY% battery, and it restarts when it is done.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (blocker != null) {
                        Text(blocker, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                    }
                }
                if (state !is FirmwareUpdateState.Checking && !progress.running) {
                    OutlinedButton(onClick = { viewModel.checkFirmwareUpdate() }, modifier = Modifier.fillMaxWidth()) {
                        Text(SecondaryStrings.CHECK_AGAIN)
                    }
                }
            }
        }
    }

    val offer = state as? FirmwareUpdateState.Available
    if (confirm && offer != null) {
        AlertDialog(
            onDismissRequest = { if (!offer.force) confirm = false },
            title = { Text(SecondaryStrings.NEW_VERSION) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("${SecondaryStrings.CURRENT_VERSION}: ${offer.current ?: "-"}")
                    Text("${SecondaryStrings.LATEST_VERSION}: ${offer.latest ?: "-"}")
                    offer.notes?.takeIf { it.isNotBlank() }?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (blocker != null) Text(blocker, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            },
            confirmButton = {
                TextButton(enabled = blocker == null, onClick = { confirm = false; viewModel.startFirmwareUpdate() }) {
                    Text(SecondaryStrings.UPDATE_NOW)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirm = false; if (offer.force) onBack() }) { Text(firmwareDismissLabel(offer.force)) }
            },
        )
    }
}

@Composable
private fun VersionCard(title: String, current: String?, latest: String?, notes: String?) {
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text("${SecondaryStrings.CURRENT_VERSION}: ${current ?: "-"}", style = MaterialTheme.typography.bodyMedium)
            latest?.let { Text("${SecondaryStrings.LATEST_VERSION}: $it", style = MaterialTheme.typography.bodyMedium) }
            notes?.takeIf { it.isNotBlank() }?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun ProgressCard(progress: FirmwareProgress) {
    val failed = progress is FirmwareProgress.Failed
    Surface(
        shape = MaterialTheme.shapes.large,
        color = if (failed) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                firmwareProgressText(progress),
                style = MaterialTheme.typography.bodyMedium,
                color = if (failed) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurface,
            )
            if (progress.running) {
                val percent = (progress as? FirmwareProgress.Downloading)?.percent
                if (percent != null) {
                    LinearProgressIndicator(progress = { percent / 100f }, modifier = Modifier.fillMaxWidth())
                } else {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
                Text(
                    "You can leave this screen. The update keeps going on the robot.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
