package com.kodraliu.localrock.ui.vacuum

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.kodraliu.localrock.ui.AppColors
import com.kodraliu.localrock.ui.strings.SecondaryStrings

@Composable
internal fun FirmwareUpdateRow(state: FirmwareUpdateState, onCheck: () -> Unit) {
    val checking = state is FirmwareUpdateState.Checking
    val value: String
    val supporting: String?
    val tint: Color
    when (state) {
        FirmwareUpdateState.Idle -> {
            value = "Check for updates"
            supporting = "Ask the cloud whether newer firmware is available."
            tint = MaterialTheme.colorScheme.primary
        }
        FirmwareUpdateState.Checking -> {
            value = "Checking…"
            supporting = null
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        }
        is FirmwareUpdateState.UpToDate -> {
            value = "Up to date"
            supporting = state.current?.let { "Installed: $it" }
            tint = AppColors.Good
        }
        is FirmwareUpdateState.Available -> {
            value = SecondaryStrings.NEW_VERSION
            supporting = buildString {
                append(state.current ?: "?"); append("  →  "); append(state.latest ?: "?")
                state.notes?.takeIf { it.isNotBlank() }?.let { append("\n"); append(it) }
            }
            tint = MaterialTheme.colorScheme.primary
        }
        is FirmwareUpdateState.Unavailable -> {
            value = "Check unavailable"
            supporting = state.reason
            tint = AppColors.Warn
        }
    }
    Surface(onClick = onCheck, enabled = !checking, color = Color.Transparent) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Surface(shape = RoundedCornerShape(12.dp), color = tint.copy(alpha = 0.12f), modifier = Modifier.size(44.dp)) {
                Box(contentAlignment = Alignment.Center) { Icon(Icons.Default.SystemUpdate, null, Modifier.size(22.dp), tint) }
            }
            Column(Modifier.weight(1f)) {
                Text(SecondaryStrings.FIRMWARE_UPDATES, style = MaterialTheme.typography.bodyLarge)
                Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (supporting != null) {
                    Text(supporting, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (checking) {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            } else {
                Icon(Icons.Default.Refresh, null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
            }
        }
    }
}
