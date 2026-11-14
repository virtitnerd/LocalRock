package com.kodraliu.localrock.ui.vacuum

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.BatteryUnknown
import androidx.compose.material.icons.filled.Battery1Bar
import androidx.compose.material.icons.filled.Battery2Bar
import androidx.compose.material.icons.filled.Battery3Bar
import androidx.compose.material.icons.filled.Battery4Bar
import androidx.compose.material.icons.filled.Battery5Bar
import androidx.compose.material.icons.filled.Battery6Bar
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kodraliu.localrock.shared.vacuum.VacuumErrorCodes
import com.kodraliu.localrock.shared.vacuum.VacuumStateCodes
import com.kodraliu.localrock.shared.vacuum.VacuumStatus

@Composable
internal fun StatusBar(
    status: VacuumStatus,
    lastCleanArea: Long?,
    onHistory: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // clean_area is reported in mm²; show m² with one decimal.
    val area = lastCleanArea?.let { mm2 ->
        val tenths = mm2 / 100_000
        "${tenths / 10}.${tenths % 10} m²"
    }
    val time = status.cleanTime?.takeIf { it > 0 }?.let { s ->
        val m = s / 60
        if (m < 60) "${m}m" else "${m / 60}h ${m % 60}m"
    }

    val errorCode = status.errorCode ?: 0
    val drying = status.dryStatus == 1 || status.state == VacuumStateCodes.DRYING_MOP
    val active = drying || isBusyState(status.state)
    // The robot's own error is the most important thing it can tell you, so it replaces the state
    // label in place rather than only living in the app-wide banner.
    val headline = when {
        errorCode != 0 -> VacuumErrorCodes.describe(errorCode)
        drying -> dryingLabel(status.remainingDryTimeSec)
        else -> stateLabel(status.state)
    }
    val headlineColor = when {
        errorCode != 0 -> MaterialTheme.colorScheme.error
        drying -> MaterialTheme.colorScheme.tertiary
        active -> stateTint(status.state)
        else -> MaterialTheme.colorScheme.onSurface
    }

    val cleaningNow = status.state in ACTIVE_CLEANING_STATES
    val detail = buildList {
        if (area != null) add(if (cleaningNow) "$area so far" else "$area last clean")
        if (time != null) add(time)
    }.joinToString(" · ").ifEmpty { "No cleaning recorded yet" }

    val charging = status.chargeStatus == 1 || status.state == VacuumStateCodes.CHARGING
    val battery = status.battery
    val batteryIcon = when {
        charging -> Icons.Default.BatteryChargingFull
        battery == null -> Icons.AutoMirrored.Filled.BatteryUnknown
        battery >= 95 -> Icons.Default.BatteryFull
        battery >= 80 -> Icons.Default.Battery6Bar
        battery >= 65 -> Icons.Default.Battery5Bar
        battery >= 50 -> Icons.Default.Battery4Bar
        battery >= 35 -> Icons.Default.Battery3Bar
        battery >= 20 -> Icons.Default.Battery2Bar
        else -> Icons.Default.Battery1Bar
    }

    Surface(
        onClick = onHistory,
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = MaterialTheme.shapes.large,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    when {
                        errorCode != 0 -> Icon(
                            Icons.Default.Error,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.error,
                        )
                        active -> PulsingDot(headlineColor)
                    }
                    Text(
                        headline,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = headlineColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    detail,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(
                    batteryIcon,
                    contentDescription = if (charging) "Battery, charging" else "Battery",
                    modifier = Modifier.size(20.dp),
                    tint = batteryColor(battery),
                )
                Text(
                    battery?.let { "$it%" } ?: "—",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}
