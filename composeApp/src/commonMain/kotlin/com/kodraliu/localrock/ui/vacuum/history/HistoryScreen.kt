package com.kodraliu.localrock.ui.vacuum

import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.kodraliu.localrock.shared.vacuum.CleanRecord
import com.kodraliu.localrock.shared.vacuum.CleanTimelineLabels
import com.kodraliu.localrock.shared.vacuum.CleanTimelineStep
import com.kodraliu.localrock.shared.vacuum.CleanSummary
import com.kodraliu.localrock.shared.vacuum.PatrolThumbnail
import com.kodraliu.localrock.shared.vacuum.totalAreaLabel
import com.kodraliu.localrock.shared.vacuum.finishReasonLabel
import com.kodraliu.localrock.shared.vacuum.cleanTypeLabel
import com.kodraliu.localrock.shared.vacuum.cleaningRecords
import com.kodraliu.localrock.shared.vacuum.cruiseRecords
import com.kodraliu.localrock.shared.vacuum.cruiseTitle
import com.kodraliu.localrock.shared.vacuum.isCruise
import com.kodraliu.localrock.shared.vacuum.cleanRecordTags
import com.kodraliu.localrock.shared.vacuum.cleanDurationLabel
import com.kodraliu.localrock.shared.vacuum.cleanAreaLabel
import com.kodraliu.localrock.shared.vacuum.map.ParsedMap
import com.kodraliu.localrock.ui.AppColors
import com.kodraliu.localrock.ui.adaptive.CenteredContent
import kotlinx.coroutines.launch

@Composable
fun VacuumHistoryScreen(viewModel: VacuumViewModel, onBack: () -> Unit) {
    val repository = viewModel.repository
    val mapRotation by viewModel.mapRotation.collectAsState()
    val scope = rememberCoroutineScope()
    val cleanHistory by repository.cleanHistory.collectAsState()
    val cleanSummary by repository.cleanSummary.collectAsState()
    val rooms by repository.rooms.collectAsState()
    LaunchedEffect(Unit) {
        runCatching { repository.fetchCleanHistory() }
        runCatching { repository.fetchCleanSummary() }
    }
    HistoryScreen(
        records = cleanHistory,
        summary = cleanSummary,
        loadMap = { repository.fetchCleanRecordMap(it) },
        loadThumbnails = { task, x, y -> repository.fetchPatrolThumbnails(task, x, y) },
        mapRotation = mapRotation,
        loadTimeline = repository::fetchCleanTimeline,
        roomName = { id -> rooms.firstOrNull { it.id == id }?.name },
        onRefresh = {
            scope.launch {
                runCatching { repository.fetchCleanHistory() }
                runCatching { repository.fetchCleanSummary() }
            }
        },
        onBack = onBack,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun HistoryScreen(
    records: List<CleanRecord>,
    summary: CleanSummary?,
    loadMap: suspend (beginEpoch: Long) -> ParsedMap,
    loadThumbnails: suspend (taskId: Long, x: Int, y: Int) -> List<PatrolThumbnail>,
    mapRotation: Int,
    loadTimeline: suspend (CleanRecord) -> List<CleanTimelineStep>?,
    roomName: (Int) -> String?,
    onRefresh: () -> Unit,
    onBack: () -> Unit,
) {
    var selected by remember { mutableStateOf<CleanRecord?>(null) }
    var cruiseTab by remember { mutableStateOf(false) }
    val open = selected
    val hasCruise = (summary?.patrolCount ?: 0) > 0 || records.any { it.isCruise }
    val showCruise = hasCruise && cruiseTab
    val shown = when {
        !hasCruise -> records.sortedByDescending { it.beginEpoch }
        showCruise -> records.cruiseRecords()
        else -> records.cleaningRecords().sortedByDescending { it.beginEpoch }
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (open == null) "History" else formatCleanRecordTime(open.beginEpoch)) },
                navigationIcon = {
                    IconButton(onClick = { if (open == null) onBack() else selected = null }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    if (open == null) {
                        IconButton(onClick = onRefresh) { Icon(Icons.Default.Refresh, "Refresh") }
                    }
                },
            )
        },
    ) { padding ->
        CenteredContent {
            if (open != null) {
                if (open.isCruise) {
                    CruiseRecordDetail(open, loadMap, loadThumbnails, mapRotation, Modifier.padding(padding))
                } else {
                    CleanRecordDetail(open, loadMap, loadTimeline, roomName, mapRotation, Modifier.padding(padding))
                }
            } else if (records.isEmpty()) {
                Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Icon(Icons.Default.History, null, Modifier.size(48.dp), MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("No history yet", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Complete a cleaning session to see it here",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                        OutlinedButton(onClick = onRefresh) { Text("Load history") }
                    }
                }
            } else {
                LazyColumn(contentPadding = padding) {
                    if (summary != null) {
                        item { CleanSummaryCard(summary) }
                    }
                    if (hasCruise) {
                        item {
                            TabRow(selectedTabIndex = if (showCruise) 1 else 0) {
                                Tab(selected = !showCruise, onClick = { cruiseTab = false }, text = { Text("Cleaning") })
                                Tab(selected = showCruise, onClick = { cruiseTab = true }, text = { Text("Cruise") })
                            }
                        }
                    }
                    if (showCruise && shown.isEmpty()) {
                        item {
                            Text(
                                "No cruise runs on the robot",
                                modifier = Modifier.fillMaxWidth().padding(24.dp),
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    items(shown, key = { it.beginEpoch }) { record ->
                        if (record.isCruise) {
                            CruiseRecordRow(record, onClick = { selected = record })
                        } else {
                            CleanRecordRow(record, onClick = { selected = record })
                        }
                        HorizontalDivider(Modifier.padding(start = 72.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun CleanSummaryCard(summary: CleanSummary) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        shape = MaterialTheme.shapes.extraLarge,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(20.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            SummaryStatItem(totalAreaLabel(summary.totalAreaMm2), "Total Area")
            SummaryStatItem("${summary.totalTimeSec / 3600} h", "Total Time")
            SummaryStatItem("${summary.totalCount}", "Working Times")
        }
    }
}

@Composable
private fun SummaryStatItem(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun CleanRecordRow(record: CleanRecord, onClick: () -> Unit) {
    val done = record.complete == 1
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = if (done) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer,
            modifier = Modifier.size(44.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    if (done) Icons.Default.CheckCircle else Icons.Default.Warning,
                    null,
                    Modifier.size(22.dp),
                    if (done) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onErrorContainer,
                )
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                formatCleanRecordTime(record.beginEpoch),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
            )
            Text(
                listOfNotNull(
                    cleanTypeLabel(record.cleanType),
                    cleanDurationLabel(record.durationSeconds),
                    cleanAreaLabel(record.shownAreaMm2),
                ).joinToString("  |  "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun CruiseRecordRow(record: CleanRecord, onClick: () -> Unit) {
    val done = record.complete == 1
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = if (done) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer,
            modifier = Modifier.size(44.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    if (done) Icons.Default.CheckCircle else Icons.Default.Warning,
                    null,
                    Modifier.size(22.dp),
                    if (done) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onErrorContainer,
                )
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                "${record.cruiseTitle}, ${cleanDurationLabel(record.durationSeconds)}",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
            )
            Text(
                formatCleanRecordTime(record.beginEpoch),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun CleanRecordDetail(
    record: CleanRecord,
    loadMap: suspend (beginEpoch: Long) -> ParsedMap,
    loadTimeline: suspend (CleanRecord) -> List<CleanTimelineStep>?,
    roomName: (Int) -> String?,
    mapRotation: Int,
    modifier: Modifier = Modifier,
) {
    val done = record.complete == 1
    val tags = cleanRecordTags(record)
    var map by remember(record.beginEpoch) { mutableStateOf<ParsedMap?>(null) }
    var failed by remember(record.beginEpoch) { mutableStateOf(false) }
    LaunchedEffect(record.beginEpoch) {
        runCatching { loadMap(record.beginEpoch) }.onSuccess { map = it }.onFailure { failed = true }
    }
    Column(modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(MaterialTheme.shapes.extraLarge)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            contentAlignment = Alignment.Center,
        ) {
            when {
                map != null -> SavedMapImage(map!!, Modifier.fillMaxSize().padding(8.dp), mapRotation)
                failed -> Text("Couldn't load this run's map", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                else -> CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
            }
        }
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = MaterialTheme.shapes.extraLarge,
        ) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        if (done) Icons.Default.CheckCircle else Icons.Default.Warning,
                        null,
                        Modifier.size(24.dp),
                        if (done) AppColors.Good else AppColors.Warn,
                    )
                    Text(finishReasonLabel(record), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                }
                if (tags.isNotEmpty()) {
                    Text(
                        tags.joinToString(" | "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = MaterialTheme.shapes.extraLarge,
        ) {
            Row(Modifier.fillMaxWidth().padding(20.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                SummaryStatItem(cleanDurationLabel(record.durationSeconds), "Cleaning Time")
                SummaryStatItem(cleanAreaLabel(record.shownAreaMm2), "Cleaning Area")
            }
        }
        CleanTimelineCard(record, loadTimeline, roomName)
    }
}

@Composable
private fun CleanTimelineCard(
    record: CleanRecord,
    loadTimeline: suspend (CleanRecord) -> List<CleanTimelineStep>?,
    roomName: (Int) -> String?,
) {
    var loading by remember(record.beginEpoch) { mutableStateOf(true) }
    var steps by remember(record.beginEpoch) { mutableStateOf<List<CleanTimelineStep>?>(null) }
    LaunchedEffect(record.beginEpoch) {
        steps = runCatching { loadTimeline(record) }.getOrNull()
        loading = false
    }
    Surface(Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.extraLarge) {
        CleanTimelineSection(steps, record.beginEpoch, loading, roomName)
    }
}

@Composable
private fun CleanTimelineSection(steps: List<CleanTimelineStep>?, beginEpoch: Long, loading: Boolean, roomName: (Int) -> String?) {
    Column(Modifier.fillMaxWidth().padding(20.dp)) {
        when {
            loading -> CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            steps == null -> Text(
                "No timeline for this run",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            else -> steps.forEach { step ->
                val isError = step is CleanTimelineStep.Error
                Row(Modifier.padding(vertical = 5.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        timelineOffset(step.time - beginEpoch),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.width(56.dp),
                    )
                    Column(Modifier.weight(1f)) {
                        Text(
                            CleanTimelineLabels.title(step, roomName),
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                        )
                        CleanTimelineLabels.detail(step)?.let {
                            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

private fun timelineOffset(seconds: Long): String {
    val total = seconds.coerceAtLeast(0L)
    val hours = total / 3600
    val minutes = (total % 3600) / 60
    return if (hours > 0) "+$hours:${minutes.toString().padStart(2, '0')}h" else "+${minutes}m"
}
