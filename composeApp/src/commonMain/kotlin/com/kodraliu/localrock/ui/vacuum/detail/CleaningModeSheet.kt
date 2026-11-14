package com.kodraliu.localrock.ui.vacuum

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kodraliu.localrock.shared.vacuum.MopRoute
import com.kodraliu.localrock.shared.vacuum.RoomCleanMode
import com.kodraliu.localrock.shared.vacuum.VacuumFanPower
import com.kodraliu.localrock.shared.vacuum.WaterBoxMode
import com.kodraliu.localrock.shared.vacuum.isCustomizeModeSet
import com.kodraliu.localrock.shared.vacuum.isSmartModeSet
import com.kodraliu.localrock.shared.vacuum.roomCleanModesFor
import com.kodraliu.localrock.shared.vacuum.map.ParsedMapRoom

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CleaningModeSheetContent(
    currentFanPower: Int?,
    currentWaterMode: Int?,
    currentRoute: Int?,
    cleaningCount: Int,
    busy: Boolean,
    supportsSmartPlan: Boolean,
    rooms: List<ParsedMapRoom>,
    roomCleanModes: List<RoomCleanMode>,
    roomModesLoaded: Boolean?,
    error: String?,
    onPlanSelected: (CleanPlan) -> Unit,
    onSaveRoomModes: (List<RoomCleanMode>) -> Unit,
    onFanPowerSelected: (Int) -> Unit,
    onWaterModeSelected: (Int) -> Unit,
    onRouteSelected: (Int) -> Unit,
    onCountChange: (Int) -> Unit,
) {
    val initialTab = when {
        (currentWaterMode ?: WaterBoxMode.OFF) == WaterBoxMode.OFF -> 2
        currentFanPower == VacuumFanPower.OFF -> 1
        else -> 0
    }
    var selectedTab by remember { mutableIntStateOf(initialTab) }
    // SmartPlan owns suction, water and route; the robot rejects manual changes until it is left.
    val smart = isSmartModeSet(currentFanPower, currentWaterMode, currentRoute)
    // Per-room settings report their own codes for all three as well.
    val perRoom = isCustomizeModeSet(currentFanPower, currentWaterMode, currentRoute)
    val plan = when {
        smart -> CleanPlan.SMART
        perRoom -> CleanPlan.PER_ROOM
        else -> CleanPlan.MANUAL
    }
    val plans = buildList {
        if (supportsSmartPlan || smart) add(CleanPlan.SMART)
        add(CleanPlan.MANUAL)
        if (rooms.isNotEmpty() || perRoom) add(CleanPlan.PER_ROOM)
    }

    Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = 16.dp)) {
        Spacer(Modifier.height(12.dp))
        if (plans.size > 1) {
            Box(Modifier.padding(horizontal = 24.dp).padding(bottom = 12.dp)) {
                ModeOptionSelector("Mode", plans.map { it.label }, plans.indexOf(plan), !busy) {
                    if (plans[it] != plan) onPlanSelected(plans[it])
                }
            }
        }
        error?.let {
            Text(
                it,
                modifier = Modifier.padding(horizontal = 24.dp).padding(bottom = 8.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
        if (smart) {
            Column(Modifier.padding(horizontal = 24.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                Text("SmartPlan picks suction, water and route for each room.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                CountSelector(cleaningCount, onCountChange)
            }
        } else if (perRoom) {
            if (roomModesLoaded == true) {
                RoomCleanModesEditor(rooms, roomCleanModes, busy, onSaveRoomModes)
            } else {
                Text(
                    if (roomModesLoaded == null) "Reading the room settings from the robot."
                    else "Couldn't read the room settings from the robot. Close this sheet and open it again.",
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            SecondaryTabRow(selectedTabIndex = selectedTab) {
                listOf("Vac & Mop", "Mop", "Vacuum").forEachIndexed { i, title ->
                    Tab(
                        selected = selectedTab == i,
                        onClick = {
                            selectedTab = i
                            val waterOff = (currentWaterMode ?: WaterBoxMode.OFF) == WaterBoxMode.OFF
                            val fanOff = currentFanPower == VacuumFanPower.OFF
                            when (i) {
                                0 -> { if (waterOff) onWaterModeSelected(WaterBoxMode.LOW); if (fanOff) onFanPowerSelected(VacuumFanPower.BALANCED) }
                                1 -> { if (waterOff) onWaterModeSelected(WaterBoxMode.LOW); onFanPowerSelected(VacuumFanPower.OFF) }
                                2 -> { if (fanOff) onFanPowerSelected(VacuumFanPower.BALANCED); onWaterModeSelected(WaterBoxMode.OFF) }
                            }
                        },
                        text = { Text(title) },
                    )
                }
            }

            Box(Modifier.fillMaxWidth().heightIn(min = 380.dp)) {
                Column(
                    modifier = Modifier
                        .padding(horizontal = 24.dp, vertical = 16.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    val subtitle = when (selectedTab) {
                        0 -> "Vac & Mop for daily clean"
                        1 -> "Mop only for delicate floors"
                        else -> "Vacuum only mode"
                    }
                    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

                    val suction = @Composable {
                        ModeOptionSelector("Suction power", FAN_OPTIONS.map { it.second }, FAN_OPTIONS.indexOfFirst { it.first == currentFanPower }, !busy) { onFanPowerSelected(FAN_OPTIONS[it].first) }
                    }
                    val water = @Composable {
                        ModeOptionSelector("Water flow", WATER_OPTIONS.map { it.second }, WATER_OPTIONS.indexOfFirst { it.first == WaterBoxMode.normalize(currentWaterMode) }, !busy) { onWaterModeSelected(WATER_OPTIONS[it].first) }
                    }
                    val route = @Composable {
                        ModeOptionSelector("Route", ROUTE_OPTIONS.map { it.second }, ROUTE_OPTIONS.indexOfFirst { it.first == currentRoute }, !busy) { onRouteSelected(ROUTE_OPTIONS[it].first) }
                    }

                    when (selectedTab) {
                        0 -> { suction(); water(); CountSelector(cleaningCount, onCountChange); route() }
                        1 -> { water(); CountSelector(cleaningCount, onCountChange); route() }
                        2 -> { suction(); CountSelector(cleaningCount, onCountChange); route() }
                    }
                }
            }
        }
    }
}

internal enum class CleanPlan(val label: String) {
    SMART("SmartPlan"),
    MANUAL("Manual"),
    PER_ROOM("Per room"),
}

private fun roomModeSummary(mode: RoomCleanMode): String {
    val fan = ROOM_FAN_OPTIONS.firstOrNull { it.first == mode.fanPower }?.second ?: "Other"
    val water = WATER_OPTIONS.firstOrNull { it.first == WaterBoxMode.normalize(mode.waterBoxMode) }?.second ?: "Other"
    return when {
        mode.waterBoxMode == WaterBoxMode.OFF -> "Vacuum $fan"
        mode.fanPower == VacuumFanPower.OFF -> "Mop $water"
        else -> "Vacuum $fan, mop $water"
    }
}

/**
 * One row per room on the map; a tapped room opens its suction, water and route. Nothing is sent
 * until Save, which writes every room in one command.
 */
@Composable
private fun RoomCleanModesEditor(
    rooms: List<ParsedMapRoom>,
    stored: List<RoomCleanMode>,
    busy: Boolean,
    onSave: (List<RoomCleanMode>) -> Unit,
) {
    val saved = remember(rooms, stored) { roomCleanModesFor(rooms.map { it.id }, stored) }
    var draft by remember(saved) { mutableStateOf(saved) }
    var openSegment by remember { mutableStateOf<Int?>(null) }
    // Rooms the robot has no settings for yet still need the first save.
    val unsaved = draft != saved || rooms.any { room -> stored.none { it.segment == room.id } }

    Column(Modifier.padding(horizontal = 24.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(
            if (rooms.isEmpty()) "Load the map to set up each room."
            else "Each room is cleaned with its own suction, water and route.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
            rooms.forEachIndexed { index, room ->
                val mode = draft.firstOrNull { it.segment == room.id } ?: return@forEachIndexed
                val open = openSegment == room.id
                val update = { changed: RoomCleanMode ->
                    draft = draft.map { if (it.segment == room.id) changed else it }
                }
                if (index > 0) HorizontalDivider()
                Surface(onClick = { openSegment = if (open) null else room.id }, color = Color.Transparent) {
                    Row(Modifier.fillMaxWidth().padding(vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            room.name,
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(roomModeSummary(mode), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                    }
                }
                if (open) {
                    Column(Modifier.padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                        ModeOptionSelector("Suction power", ROOM_FAN_OPTIONS.map { it.second }, ROOM_FAN_OPTIONS.indexOfFirst { it.first == mode.fanPower }, !busy) {
                            val fan = ROOM_FAN_OPTIONS[it].first
                            // A room with neither suction nor water would not be cleaned at all.
                            val water = if (fan == VacuumFanPower.OFF && mode.waterBoxMode == WaterBoxMode.OFF) WaterBoxMode.LOW else mode.waterBoxMode
                            update(mode.copy(fanPower = fan, waterBoxMode = water))
                        }
                        ModeOptionSelector("Water flow", WATER_OPTIONS.map { it.second }, WATER_OPTIONS.indexOfFirst { it.first == WaterBoxMode.normalize(mode.waterBoxMode) }, !busy) {
                            val water = WATER_OPTIONS[it].first
                            val fan = if (water == WaterBoxMode.OFF && mode.fanPower == VacuumFanPower.OFF) VacuumFanPower.BALANCED else mode.fanPower
                            // The route is hidden without water. A Qrevo S Pro (02.33.88) refuses Deep and Deep+ in a
                            // room that vacuums and a Qrevo Curv 2 Flow refuses Deep in any room, so a hidden one
                            // would make the whole save fail.
                            val route = if (water == WaterBoxMode.OFF && mode.mopMode in DEEP_ROUTES) MopRoute.STANDARD else mode.mopMode
                            update(mode.copy(fanPower = fan, waterBoxMode = water, mopMode = route))
                        }
                        if (mode.waterBoxMode != WaterBoxMode.OFF) {
                            ModeOptionSelector("Route", ROUTE_OPTIONS.map { it.second }, ROUTE_OPTIONS.indexOfFirst { it.first == mode.mopMode }, !busy) {
                                update(mode.copy(mopMode = ROUTE_OPTIONS[it].first))
                            }
                        }
                    }
                }
            }
        }
        if (rooms.isNotEmpty()) {
            Button(onClick = { onSave(draft) }, enabled = !busy && unsaved, modifier = Modifier.fillMaxWidth()) {
                Text("Save room settings")
            }
        }
    }
}

@Composable
private fun ModeOptionSelector(label: String, options: List<String>, selectedIndex: Int, enabled: Boolean, onSelect: (Int) -> Unit) {
    val currentValue = options.getOrElse(selectedIndex) { "" }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            Text(currentValue, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEachIndexed { index, option ->
                val selected = selectedIndex == index
                Surface(
                    onClick = { if (enabled) onSelect(index) },
                    modifier = Modifier.weight(1f),
                    shape = CircleShape,
                    color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                ) {
                    Text(option, modifier = Modifier.padding(vertical = 14.dp), style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                        color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center)
                }
            }
        }
    }
}

@Composable
private fun CountSelector(selected: Int, onSelect: (Int) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Cleaning count", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            Text(if (selected == 1) "once" else "twice", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(1, 2).forEach { count ->
                val isSelected = selected == count
                Surface(onClick = { onSelect(count) }, modifier = Modifier.weight(1f), shape = CircleShape,
                    color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh) {
                    Text("×$count", modifier = Modifier.padding(vertical = 14.dp), style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center)
                }
            }
        }
    }
}
