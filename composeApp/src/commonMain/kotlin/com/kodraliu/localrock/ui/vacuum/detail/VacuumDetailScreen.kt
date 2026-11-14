package com.kodraliu.localrock.ui.vacuum

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Dock
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp
import com.kodraliu.localrock.shared.http.currentEpochSeconds
import kotlinx.datetime.Instant
import com.kodraliu.localrock.shared.vacuum.FloorMap
import com.kodraliu.localrock.shared.vacuum.MopRoute
import com.kodraliu.localrock.shared.vacuum.SWITCH_MAP_MODE_SMART
import com.kodraliu.localrock.shared.vacuum.VacuumFanPower
import com.kodraliu.localrock.shared.vacuum.VacuumRepository
import com.kodraliu.localrock.shared.vacuum.VacuumStateCodes
import com.kodraliu.localrock.shared.vacuum.WaterBoxMode
import com.kodraliu.localrock.shared.vacuum.isCustomizeModeSet
import com.kodraliu.localrock.shared.vacuum.map.ParsedMap
import com.kodraliu.localrock.shared.vacuum.map.MapObstacle
import com.kodraliu.localrock.shared.vacuum.modelSubtitle
import com.kodraliu.localrock.ui.adaptive.LocalWindowLayout
import kotlinx.coroutines.delay


internal val FAN_OPTIONS = listOf(
    VacuumFanPower.QUIET to "Quiet",
    VacuumFanPower.BALANCED to "Standard",
    VacuumFanPower.TURBO to "Strong",
    VacuumFanPower.MAX to "MAX",
)

/** A room can also go without suction, the official app's mop-only setting. */
internal val ROOM_FAN_OPTIONS = listOf(VacuumFanPower.OFF to "Off") + FAN_OPTIONS

internal val WATER_OPTIONS = listOf(
    WaterBoxMode.OFF to "Off",
    WaterBoxMode.LOW to "Low",
    WaterBoxMode.MEDIUM to "Med",
    WaterBoxMode.HIGH to "High",
)

internal val ROUTE_OPTIONS = listOf(
    MopRoute.STANDARD to "Standard",
    MopRoute.DEEP to "Deep",
    MopRoute.DEEP_PLUS to "Deep+",
    MopRoute.FAST to "Fast",
)

internal val DEEP_ROUTES = setOf(MopRoute.DEEP, MopRoute.DEEP_PLUS)

internal val ACTIVE_CLEANING_STATES = setOf(
    VacuumStateCodes.CLEANING, VacuumStateCodes.SEGMENT_CLEANING,
    VacuumStateCodes.ZONED_CLEANING, VacuumStateCodes.SPOT_CLEANING,
)


@OptIn(ExperimentalMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
fun VacuumDetailScreen(
    viewModel: VacuumViewModel,
    onBack: () -> Unit,
    onSettings: () -> Unit,
    onZoneClean: () -> Unit,
    onMapEdit: () -> Unit,
    onSchedule: () -> Unit,
    onCamera: () -> Unit,
    onRemote: () -> Unit,
    onPinGo: () -> Unit,
    onHistory: () -> Unit,
) {
    val status by viewModel.status.collectAsState()
    DisposableEffect(viewModel) {
        viewModel.repository.setLiveMapRate(true)
        onDispose { viewModel.repository.setLiveMapRate(false) }
    }
    val busy by viewModel.busy.collectAsState()
    val mapRotation by viewModel.mapRotation.collectAsState()
    val map3DYaw by viewModel.map3DYaw.collectAsState()
    val map3DTilt by viewModel.map3DTilt.collectAsState()
    val map3D by viewModel.map3D.collectAsState()
    val supportsSmartPlan by viewModel.supportsSmartPlan.collectAsState()
    val supportsWaterSlide by viewModel.supportsWaterSlide.collectAsState()
    val liveMap by viewModel.repository.parsedMap.collectAsState()
    val lastKnown by viewModel.lastKnown.collectAsState()
    val lastKnownMap by viewModel.lastKnownMap.collectAsState()
    // While the robot does not answer, its last map (with where it was last seen) stands in.
    // A robot carried to its dock keeps its old map position until it re-localizes, so draw it at the charger.
    val parsedMap = remember(liveMap, lastKnownMap, status.state) { (liveMap ?: lastKnownMap)?.withRobotOnDock(isDocked(status.state)) }
    val offlineLines = remember(lastKnown) {
        lastKnown?.let { lastKnownLines(it, Instant.fromEpochSeconds(currentEpochSeconds())) } ?: emptyList()
    }
    val floorMaps by viewModel.repository.floorMaps.collectAsState()
    val currentFloorFlag by viewModel.repository.currentFloorFlag.collectAsState()
    val maxFloorMaps by viewModel.repository.maxFloorMaps.collectAsState()
    val unsavedMap by viewModel.repository.unsavedMapPresent.collectAsState()
    val mopMode by viewModel.repository.mopMode.collectAsState()
    val roomCleanModes by viewModel.repository.roomCleanModes.collectAsState()
    val dockSettings by viewModel.repository.dockSettings.collectAsState()
    var selectedRoomIds by remember { mutableStateOf(emptyList<Int>()) }

    var sessionRoomIds by remember { mutableStateOf(emptyList<Int>()) }
    var panel by remember { mutableStateOf<DetailPanel?>(null) }
    var openObstacle by remember { mutableStateOf<MapObstacle?>(null) }
    val twoPane = LocalWindowLayout.current.twoPane
    BackHandler(enabled = twoPane && sidePaneShowsPanel(panel)) { panel = null }
    val routines by viewModel.routines.collectAsState()
    var recoverLoading by remember { mutableStateOf(false) }
    var recoverTick by remember { mutableIntStateOf(0) }
    var renamingMap by remember { mutableStateOf<FloorMap?>(null) }
    var deletingMap by remember { mutableStateOf<FloorMap?>(null) }
    var backingUpMap by remember { mutableStateOf<FloorMap?>(null) }
    var backingUpPicture by remember { mutableStateOf<ParsedMap?>(null) }
    var restoringMap by remember { mutableStateOf<FloorMap?>(null) }
    var restoringPicture by remember { mutableStateOf<ParsedMap?>(null) }
    var choosingMapToKeep by remember { mutableStateOf(false) }
    var lastCleanArea by remember { mutableStateOf<Long?>(null) }
    var cleaningCount by remember { mutableIntStateOf(1) }
    // The map's pan/zoom lives here so it survives the compact ↔ two-pane switch.
    val mapViewport = remember { LiveMapViewport() }


    val sessionRoomNames = remember(sessionRoomIds, status.state, parsedMap) {
        if (status.state == VacuumStateCodes.SEGMENT_CLEANING && sessionRoomIds.isNotEmpty()) {
            val byId = parsedMap?.rooms?.associateBy { it.id } ?: emptyMap()
            sessionRoomIds.map { id -> byId[id]?.name ?: "Room $id" }
        } else {
            emptyList()
        }
    }

    LaunchedEffect(status.cleanArea) {
        if ((status.cleanArea ?: 0L) > 0L) lastCleanArea = status.cleanArea
    }
    // The first request can go out before the robot link is up, so retry until the list arrives.
    // map_status and lab_status change when a map is saved, deleted or switched (also from the
    // official app), so the list is read again then and the floor picker stays current.
    LaunchedEffect(status.mapStatus, status.labStatus) {
        do {
            runCatching { viewModel.repository.fetchFloorMaps() }
            if (!viewModel.repository.floorMapsLoaded.value) delay(MAP_LIST_RETRY_MS)
        } while (!viewModel.repository.floorMapsLoaded.value)
    }
    LaunchedEffect(panel == DetailPanel.CleaningMode) {
        if (panel == DetailPanel.CleaningMode) runCatching { viewModel.repository.fetchMopMode() }
    }
    val perRoomMode = isCustomizeModeSet(status.fanPower, status.waterBoxCustomMode, mopMode)
    // Null while the robot's room settings are being read, false when that failed. Saving is only
    // offered once they are known, or the defaults shown meanwhile would overwrite them.
    var roomModesLoaded by remember { mutableStateOf<Boolean?>(null) }
    LaunchedEffect(panel == DetailPanel.CleaningMode, perRoomMode) {
        roomModesLoaded = null
        if (panel == DetailPanel.CleaningMode && perRoomMode) {
            roomModesLoaded = runCatching { viewModel.repository.fetchRoomCleanModes() }.isSuccess
        }
    }
    LaunchedEffect(panel == DetailPanel.Dock) {
        if (panel == DetailPanel.Dock) runCatching { viewModel.repository.fetchDockSettings() }
    }
    LaunchedEffect(panel == DetailPanel.Routines) {
        if (panel == DetailPanel.Routines) viewModel.loadRoutines()
    }

    LaunchedEffect(panel == DetailPanel.Maps, recoverTick) {
        if (panel == DetailPanel.Maps) {
            recoverLoading = true
            runCatching { viewModel.repository.fetchFloorMaps() }
            recoverLoading = false
        }
    }

    val onMoreAction: (MoreAction) -> Unit = { action ->
        when (action) {
            MoreAction.ROOMS -> panel = DetailPanel.Rooms
            MoreAction.DOCK -> panel = DetailPanel.Dock
            MoreAction.MAPS -> panel = DetailPanel.Maps
            MoreAction.ROUTINES -> panel = DetailPanel.Routines
            MoreAction.ZONES -> { panel = null; onZoneClean() }
            MoreAction.PIN_GO -> { panel = null; onPinGo() }
            MoreAction.REMOTE -> { panel = null; onRemote() }
            MoreAction.SCHEDULE -> { panel = null; onSchedule() }
            MoreAction.CAMERA -> { panel = null; onCamera() }
            MoreAction.HISTORY -> { panel = null; onHistory() }
            MoreAction.EDIT_MAP -> { panel = null; onMapEdit() }
            MoreAction.FIND_PET -> { panel = null; viewModel.run("Looking for your pet") { it.startPetSearch() } }
        }
    }

    val onLoadMap: () -> Unit = { viewModel.refreshMap() }
    val onRecover: () -> Unit = { panel = DetailPanel.Maps }
    val onRoomTap: (Int) -> Unit = { roomId ->
        selectedRoomIds = if (roomId in selectedRoomIds)
            selectedRoomIds.filter { it != roomId }
        else selectedRoomIds + roomId
    }
    val onFloorSwitch: (Int) -> Unit = { flag -> viewModel.run { it.switchFloor(flag) } }

    val PanelBody: @Composable (DetailPanel) -> Unit = { currentPanel ->
        when (currentPanel) {
            DetailPanel.More -> MoreActionsSheetContent(showFindPet = viewModel.supportsPetSearch, showCamera = viewModel.supportsCamera, onAction = onMoreAction)

            DetailPanel.Routines -> RoutinesSheetContent(
                state = routines,
                busy = busy,
                onRun = { scene ->
                    panel = null
                    viewModel.runRoutine(scene)
                },
                onRetry = { viewModel.loadRoutines() },
            )

            DetailPanel.Rooms -> RoomsSheetContent(
                rooms = parsedMap?.rooms ?: emptyList(),
                selectedRoomIds = selectedRoomIds,
                busy = busy,
                onRoomToggle = { roomId ->
                    selectedRoomIds = if (roomId in selectedRoomIds)
                        selectedRoomIds.filter { it != roomId }
                    else selectedRoomIds + roomId
                },
                onCleanRooms = {
                    val toClean = selectedRoomIds
                    sessionRoomIds = toClean
                    selectedRoomIds = emptyList()
                    panel = null
                    viewModel.run { it.cleanRooms(toClean, cleaningCount) }
                },
                onLoadMap = {
                    panel = null
                    viewModel.refreshMap()
                },
                onDefaultOrder = {
                    panel = DetailPanel.Order
                },
            )

            DetailPanel.Order -> CleanOrderSheetContent(
                rooms = parsedMap?.rooms ?: emptyList(),
                savedOrder = remember(currentFloorFlag) { viewModel.defaultCleanOrder() },
                busy = busy,
                onSave = { order ->
                    panel = null
                    viewModel.saveDefaultCleanOrder(order)
                },
                onRemove = {
                    panel = null
                    viewModel.removeDefaultCleanOrder()
                },
            )

            DetailPanel.Dock -> DockSheetContent(
                status = status,
                busy = busy,
                dockSettings = dockSettings,
                dockCapabilities = viewModel.dockCapabilities,
                onEmptyDustbin = { panel = null; viewModel.run { it.collectDust() } },
                onWashMop = { panel = null; viewModel.run { it.washMop() } },
                // Dry keeps the sheet open so the drying status row appears in place.
                onDryMop = { viewModel.run { it.startDryer() } },
                onStopDry = { viewModel.run { it.stopDryer() } },
                onWashModeChange = { mode -> viewModel.run { repo -> repo.setWashMode(mode) } },
                onWashFrequencyChange = { mode, minutes -> viewModel.run { repo -> repo.setWashFrequency(mode, minutes) } },
                onAutoEmptyModeChange = { mode -> viewModel.run { repo -> repo.setAutoEmptyMode(mode) } },
            )

            DetailPanel.CleaningMode -> {
                // A refusal is shown in the sheet; the screen's own message would be behind it.
                var modeError by remember { mutableStateOf<String?>(null) }
                fun runMode(successMessage: String? = null, action: suspend (VacuumRepository) -> Unit) {
                    modeError = null
                    viewModel.run(successMessage, onError = { modeError = it }, action = action)
                }
                CleaningModeSheetContent(
                    currentFanPower = status.fanPower,
                    currentWaterMode = status.waterBoxCustomMode,
                    currentRoute = mopMode,
                    cleaningCount = cleaningCount,
                    busy = busy,
                    supportsSmartPlan = supportsSmartPlan,
                    rooms = parsedMap?.rooms ?: emptyList(),
                    roomCleanModes = roomCleanModes,
                    roomModesLoaded = roomModesLoaded,
                    error = modeError,
                    onPlanSelected = { plan ->
                        runMode {
                            when (plan) {
                                CleanPlan.SMART -> it.setCleanMotorMode(VacuumFanPower.SMART, WaterBoxMode.SMART, MopRoute.SMART)
                                CleanPlan.MANUAL -> {
                                    // Water slide robots get a slide code, as python-roborock does.
                                    val water = if (supportsWaterSlide) WaterBoxMode.SLIDE_MEDIUM else WaterBoxMode.LOW
                                    it.setCleanMotorMode(VacuumFanPower.BALANCED, water, MopRoute.STANDARD)
                                }
                                // The three codes the official app sent for its Customize tab.
                                CleanPlan.PER_ROOM -> it.setCleanMotorMode(VacuumFanPower.CUSTOM, WaterBoxMode.CUSTOM, MopRoute.CUSTOM)
                            }
                        }
                    },
                    onSaveRoomModes = { modes -> runMode("Room settings saved") { it.saveRoomCleanModes(modes) } },
                    onFanPowerSelected = { level -> runMode { it.setFanPower(level) } },
                    onWaterModeSelected = { level -> runMode { it.setWaterBoxMode(level) } },
                    onRouteSelected = { mode -> runMode { it.setMopMode(mode) } },
                    onCountChange = { cleaningCount = it },
                )
            }

            DetailPanel.Maps -> MapsSheetContent(
                floorMaps = floorMaps,
                currentFloorFlag = currentFloorFlag,
                loading = recoverLoading,
                busy = busy,
                blockedReason = viewModel.repository.mapManageBlockedReason(status.state),
                canAddMap = floorMaps.size < (maxFloorMaps ?: 1),
                unsavedMap = unsavedMap,
                mapSaving = status.mapSavingEnabled,
                multiLevel = status.multiLevelEnabled,
                smartSwitching = status.switchMapMode?.let { it == SWITCH_MAP_MODE_SMART },
                onMultiLevelChange = { on ->
                    when {
                        on -> viewModel.run("Multi-level maps on") { it.setMultiLevel(true, null) }
                        floorMaps.size > 1 -> choosingMapToKeep = true
                        else -> viewModel.run("Multi-level maps off") { it.setMultiLevel(false, null) }
                    }
                },
                onSmartSwitchingChange = { smart -> viewModel.run { it.setSmartMapSwitching(smart) } },
                onEnableMapSaving = { viewModel.run("Map saving on") { it.enableMapSaving() } },
                onLoad = { flag ->
                    panel = null
                    viewModel.run { it.switchFloor(flag) }
                },
                onRename = { renamingMap = it },
                onDelete = { deletingMap = it },
                onBackup = { floor, picture ->
                    backingUpPicture = picture
                    backingUpMap = floor
                },
                onRestore = { floor, picture ->
                    restoringPicture = picture
                    restoringMap = floor
                },
                loadSavedMap = { flag, backup -> viewModel.repository.fetchSavedMap(flag, backup) },
                onStartMapping = {
                    panel = null
                    viewModel.run("Mapping started") { it.startMapping() }
                },
                onSaveNewMap = { viewModel.run("New map saved") { it.saveNewMap() } },
                onRecheck = { recoverTick++ },
            )
        }
    }

    // Status + controls live in one band so the phone and two-pane layouts cannot silently diverge.
    val ControlBand: @Composable () -> Unit = {
        StatusBar(
            status = status,
            lastCleanArea = lastCleanArea,
            onHistory = onHistory,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 10.dp),
        )

        ControlBar(
            status = status,
            busy = busy,
            selectedRoomCount = selectedRoomIds.size,
            onClean = {
                sessionRoomIds = emptyList()
                val order = viewModel.defaultCleanOrder()
                viewModel.run("Cleaning started") { it.clean(order) }
            },
            onCleanRooms = {
                val toClean = selectedRoomIds
                sessionRoomIds = toClean
                selectedRoomIds = emptyList()
                viewModel.run { it.cleanRooms(toClean, cleaningCount) }
            },
            onClearSelection = { selectedRoomIds = emptyList() },
            onPause = { viewModel.run { it.pause() } },
            onResume = { viewModel.run("Cleaning resumed") { it.resumeCleaning() } },
            onStop = {
                sessionRoomIds = emptyList()
                viewModel.run { it.stopCleaning() }
            },
            onDock = {
                sessionRoomIds = emptyList()
                viewModel.run("Returning to dock") { it.dock() }
            },
            onCleaningMode = { panel = DetailPanel.CleaningMode },
            onMore = { panel = DetailPanel.More },
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(viewModel.deviceName, style = MaterialTheme.typography.titleLarge)
                        modelSubtitle(viewModel.deviceName, viewModel.modelDisplayName)?.let {
                            Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Go back")
                    }
                },
                actions = {
                    IconButton(onClick = onSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                },
            )
        },
    ) { paddingValues ->
        val dividerColor = MaterialTheme.colorScheme.outlineVariant
        val MapHero: @Composable (Modifier) -> Unit = { modifier ->
            MapHeroSection(
                deviceId = viewModel.repository.duid,
                parsedMap = parsedMap,
                offlineLines = offlineLines,
                busy = busy,
                selectedRoomIds = selectedRoomIds,
                sessionRoomNames = sessionRoomNames,
                floorMaps = floorMaps,
                currentFloorFlag = currentFloorFlag,
                rotationDegrees = mapRotation,
                yaw3D = map3DYaw,
                onYawBy = viewModel::turnMap3DBy,
                tilt3D = map3DTilt,
                onTiltBy = viewModel::tiltMap3DBy,
                is3D = map3D,
                onLoadMap = onLoadMap,
                onRecover = onRecover,
                onRoomTap = onRoomTap,
                onObstacleTap = { openObstacle = it },
                onFloorSwitch = onFloorSwitch,
                onEditMap = onMapEdit,
                // Each view keeps its own heading: this button turns whichever one is showing.
                onRotate = { if (map3D) viewModel.turnMap3DBy(90f) else viewModel.rotateMap90() },
                onToggle3D = { viewModel.setMap3D(!map3D) },
                mapViewport = mapViewport,
                faintRoute = status.state !in ACTIVE_CLEANING_STATES && status.state != VacuumStateCodes.PAUSED,
                modifier = modifier,
            )
        }

        if (twoPane) {
            Row(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
                MapHero(Modifier.weight(1f).fillMaxSize())

                Column(
                    modifier = Modifier
                        .widthIn(min = 320.dp, max = 480.dp)
                        .fillMaxWidth(0.42f)
                        .fillMaxHeight()
                        .background(MaterialTheme.colorScheme.surface)
                        .drawBehind {
                            drawLine(dividerColor, Offset.Zero, Offset(0f, size.height), 1.dp.toPx())
                        },
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .consumeWindowInsets(WindowInsets.navigationBars),
                    ) {
                        val headed = panel.takeIf { sidePaneShowsPanel(it) }
                        if (headed != null) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                IconButton(onClick = { panel = null }) {
                                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                                }
                                Text(headed.title(), style = MaterialTheme.typography.titleMedium)
                            }
                            PanelBody(headed)
                        } else {
                            MoreActionsSheetContent(showFindPet = viewModel.supportsPetSearch, showCamera = viewModel.supportsCamera, onAction = onMoreAction)
                        }
                    }

                    ControlBand()
                }
            }
        } else {
            // The map is the screen. Status and controls sit in a fixed band beneath it so the hero
            // never scrolls out of view and the primary action never moves under the thumb.
            Column(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
                MapHero(Modifier.weight(1f).fillMaxSize())

                ControlBand()
            }
        }
    }

    openObstacle?.let { obstacle ->
        ModalBottomSheet(onDismissRequest = { openObstacle = null }) {
            ObstacleSheetContent(obstacle, loadPhoto = { viewModel.repository.fetchObstaclePhoto(it) })
        }
    }

    if (!twoPane) panel?.let { current ->
        // The Maps sheet has map pictures and buttons below the fold, so it opens at full height.
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = current == DetailPanel.Maps)
        ModalBottomSheet(onDismissRequest = { panel = null }, sheetState = sheetState) { PanelBody(current) }
    }

    renamingMap?.let { floor ->
        RenameMapDialog(
            floor = floor,
            onConfirm = { name ->
                renamingMap = null
                viewModel.run("Map renamed") { it.renameFloorMap(floor.mapFlag, name) }
            },
            onDismiss = { renamingMap = null },
        )
    }

    if (choosingMapToKeep && floorMaps.isNotEmpty()) {
        KeepMapDialog(
            floorMaps = floorMaps,
            currentFloorFlag = currentFloorFlag,
            onConfirm = { keep ->
                choosingMapToKeep = false
                viewModel.run("Multi-level maps off") { it.setMultiLevel(false, keep) }
            },
            onDismiss = { choosingMapToKeep = false },
        )
    }

    deletingMap?.let { floor ->
        DeleteMapDialog(
            floor = floor,
            onConfirm = {
                deletingMap = null
                viewModel.run("Map deleted") { it.deleteFloorMap(floor.mapFlag) }
            },
            onDismiss = { deletingMap = null },
        )
    }

    backingUpMap?.let { floor ->
        BackupMapDialog(
            floor = floor,
            picture = backingUpPicture,
            onConfirm = {
                backingUpMap = null
                viewModel.run("Map backed up") { it.backupFloorMap(floor.mapFlag) }
            },
            onDismiss = { backingUpMap = null },
        )
    }

    restoringMap?.let { floor ->
        RestoreBackupDialog(
            floor = floor,
            picture = restoringPicture,
            onConfirm = {
                restoringMap = null
                viewModel.run("Map restored from backup") { it.restoreFloorMapBackup(floor.mapFlag) }
            },
            onDismiss = { restoringMap = null },
        )
    }
}
