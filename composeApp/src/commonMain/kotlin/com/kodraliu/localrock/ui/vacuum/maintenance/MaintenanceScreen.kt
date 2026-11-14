package com.kodraliu.localrock.ui.vacuum

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Cached
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FilterAlt
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kodraliu.localrock.shared.vacuum.ConsumableStatus
import com.kodraliu.localrock.ui.AppColors
import com.kodraliu.localrock.ui.adaptive.CenteredContent

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MaintenanceScreen(
    consumables: ConsumableStatus?,
    onRefresh: () -> Unit,
    onReset: (String) -> Unit,
    onBack: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Maintenance") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                },
                actions = {
                    IconButton(onClick = onRefresh) { Icon(Icons.Default.Refresh, "Refresh") }
                },
            )
        },
    ) { padding ->
        CenteredContent {
            if (consumables == null) {
                Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Icon(
                            Icons.Default.Build,
                            null,
                            Modifier.size(48.dp),
                            MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text("Could not load consumable data", style = MaterialTheme.typography.titleMedium)
                        OutlinedButton(onClick = onRefresh) { Text("Retry") }
                    }
                }
            } else {
                LazyColumn(contentPadding = padding) {
                    item {
                        val used = consumables.mainBrushWorkTime ?: 0L
                        ConsumableItem(
                            icon = Icons.Default.Cached,
                            iconTint = AppColors.Good,
                            title = "Main Brush",
                            percent = ConsumableStatus.percentLeft(used, ConsumableStatus.MAIN_BRUSH_LIFETIME_S),
                            detail = hoursLeft(used, ConsumableStatus.MAIN_BRUSH_LIFETIME_S),
                            onReset = { onReset("main_brush_work_time") },
                        )
                    }
                    item { HorizontalDivider(Modifier.padding(start = 76.dp)) }
                    item {
                        val used = consumables.sideBrushWorkTime ?: 0L
                        ConsumableItem(
                            icon = Icons.Default.Autorenew,
                            iconTint = AppColors.Water,
                            title = "Side Brush",
                            percent = ConsumableStatus.percentLeft(used, ConsumableStatus.SIDE_BRUSH_LIFETIME_S),
                            detail = hoursLeft(used, ConsumableStatus.SIDE_BRUSH_LIFETIME_S),
                            onReset = { onReset("side_brush_work_time") },
                        )
                    }
                    item { HorizontalDivider(Modifier.padding(start = 76.dp)) }
                    item {
                        val used = consumables.filterWorkTime ?: 0L
                        ConsumableItem(
                            icon = Icons.Default.Air,
                            iconTint = AppColors.AccentPurple,
                            title = "Filter",
                            percent = ConsumableStatus.percentLeft(used, ConsumableStatus.FILTER_LIFETIME_S),
                            detail = hoursLeft(used, ConsumableStatus.FILTER_LIFETIME_S),
                            onReset = { onReset("filter_work_time") },
                        )
                    }
                    item { HorizontalDivider(Modifier.padding(start = 76.dp)) }
                    item {
                        val percent = ConsumableStatus.percentLeft(
                            consumables.sensorDirtyTime ?: 0L,
                            ConsumableStatus.SENSOR_LIFETIME_S,
                        )
                        ConsumableItem(
                            icon = Icons.Default.Sensors,
                            iconTint = AppColors.Warn,
                            title = "Sensors",
                            percent = percent,
                            detail = cleanState(percent),
                            showPercent = false,
                            onReset = { onReset("sensor_dirty_time") },
                        )
                    }
                    item { HorizontalDivider(Modifier.padding(start = 76.dp)) }
                    item {
                        ConsumableItem(
                            icon = Icons.Default.Build,
                            iconTint = AppColors.AccentCyan,
                            title = "Mop",
                            detail = "Replace as needed.",
                        )
                    }
                    consumables.strainerWorkTime?.let { used ->
                        item { HorizontalDivider(Modifier.padding(start = 76.dp)) }
                        item {
                            val percent = ConsumableStatus.percentLeft(used, ConsumableStatus.STRAINER_LIFETIME_RUNS)
                            ConsumableItem(
                                icon = Icons.Default.FilterAlt,
                                iconTint = AppColors.AccentIndigo,
                                title = "Cleaning Tray",
                                percent = percent,
                                detail = cleanState(percent),
                                showPercent = false,
                                onReset = { onReset("strainer_work_times") },
                            )
                        }
                    }
                    consumables.cleaningBrushWorkTime?.let { used ->
                        item { HorizontalDivider(Modifier.padding(start = 76.dp)) }
                        item {
                            val percent = ConsumableStatus.percentLeft(used, ConsumableStatus.CLEANING_BRUSH_LIFETIME_RUNS)
                            ConsumableItem(
                                icon = Icons.Default.CleaningServices,
                                iconTint = AppColors.AccentCyan,
                                title = "Maintenance Brush",
                                percent = percent,
                                detail = cleanState(percent),
                                showPercent = false,
                                onReset = { onReset("cleaning_brush_work_times") },
                            )
                        }
                    }
                    item { HorizontalDivider(Modifier.padding(start = 76.dp)) }
                    item {
                        ConsumableItem(
                            icon = Icons.Default.Delete,
                            iconTint = AppColors.AccentCyan,
                            title = "Dust Bag",
                            detail = "Replace as needed.",
                        )
                    }
                }
            }
        }
    }
}

private fun hoursLeft(usedSeconds: Long, maxSeconds: Long): String {
    val remaining = (maxSeconds - usedSeconds).coerceAtLeast(0L)
    val hours = remaining / 3600L
    val minutes = (remaining % 3600L) / 60L
    return if (hours > 0) "${hours}h ${minutes}m remaining" else "${minutes}m remaining"
}

private fun cleanState(percent: Int): String = when {
    percent <= 0 -> "Must clean"
    percent <= 20 -> "Clean soon"
    else -> "Normal"
}

@Composable
private fun ConsumableItem(
    icon: ImageVector,
    iconTint: Color,
    title: String,
    detail: String,
    percent: Int? = null,
    showPercent: Boolean = true,
    onReset: (() -> Unit)? = null,
) {
    val progressColor = when {
        percent == null -> MaterialTheme.colorScheme.onSurfaceVariant
        percent > 20 -> AppColors.Good
        percent > 5 -> AppColors.Warn
        else -> MaterialTheme.colorScheme.error
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = iconTint.copy(alpha = 0.12f),
            modifier = Modifier.size(44.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, null, Modifier.size(22.dp), iconTint)
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                if (percent != null && showPercent) {
                    Text("$percent%", style = MaterialTheme.typography.labelMedium, color = progressColor, fontWeight = FontWeight.Bold)
                }
            }
            if (percent != null) {
                LinearProgressIndicator(
                    progress = { percent / 100f },
                    modifier = Modifier.fillMaxWidth(),
                    color = progressColor,
                    trackColor = if (percent <= 0) progressColor else MaterialTheme.colorScheme.surfaceVariant,
                )
            }
            Text(
                detail,
                style = MaterialTheme.typography.bodySmall,
                color = if (percent != null && percent <= 0 && !showPercent) progressColor else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (onReset != null) {
            TextButton(onClick = onReset, modifier = Modifier.padding(top = 4.dp)) {
                Text("Reset", color = MaterialTheme.colorScheme.error)
            }
        }
    }
}
