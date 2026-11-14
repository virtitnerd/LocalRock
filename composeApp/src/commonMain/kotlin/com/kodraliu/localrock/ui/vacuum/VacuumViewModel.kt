package com.kodraliu.localrock.ui.vacuum

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kodraliu.localrock.shared.AppContainer
import com.kodraliu.localrock.shared.messages.MessageSeverity
import com.kodraliu.localrock.shared.device.currentDeviceName
import com.kodraliu.localrock.shared.model.Scene
import com.kodraliu.localrock.shared.model.SceneRunResult
import com.kodraliu.localrock.shared.model.LastKnown
import com.kodraliu.localrock.shared.vacuum.Capabilities
import com.kodraliu.localrock.shared.vacuum.DockCapabilities
import com.kodraliu.localrock.shared.vacuum.buildCapabilities
import com.kodraliu.localrock.shared.vacuum.FeatureSection
import com.kodraliu.localrock.shared.vacuum.FirmwareProgress
import com.kodraliu.localrock.shared.vacuum.firmwareInstallBlocker
import com.kodraliu.localrock.shared.vacuum.nextFirmwareStep
import kotlin.time.Duration.Companion.minutes
import com.kodraliu.localrock.shared.vacuum.MATTER_FEATURE_BIT
import com.kodraliu.localrock.shared.vacuum.isPetSearchSupported
import com.kodraliu.localrock.shared.vacuum.VIDEO_PATROL_FEATURE_BIT
import com.kodraliu.localrock.shared.vacuum.camera.CAMERA_FEATURE_BITS
import com.kodraliu.localrock.shared.vacuum.camera.CameraLiveSession
import com.kodraliu.localrock.shared.vacuum.camera.TWO_KEY_VIDEO_FEATURE_BIT
import com.kodraliu.localrock.shared.vacuum.MapEditException
import com.kodraliu.localrock.shared.vacuum.RoomType
import com.kodraliu.localrock.shared.vacuum.VacuumErrorCodes
import com.kodraliu.localrock.shared.vacuum.VacuumRepository
import com.kodraliu.localrock.shared.vacuum.productDisplayName
import com.kodraliu.localrock.shared.vacuum.isFastRouteSupported
import com.kodraliu.localrock.shared.vacuum.isSmartPlanSupported
import com.kodraliu.localrock.shared.vacuum.isWaterSlideSupported
import kotlinx.coroutines.Job
import com.kodraliu.localrock.shared.vacuum.newFeatureBit
import com.kodraliu.localrock.shared.vacuum.supportedBetaFeatures
import com.kodraliu.localrock.shared.vacuum.unlistedAdvertisedFeatures
import com.kodraliu.localrock.shared.vacuum.map.ParsedMap
import com.kodraliu.localrock.shared.vacuum.map.parseLegacyMap
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class VacuumViewModel(
    duid: String,
    private val container: AppContainer,
) : ViewModel() {

    private val device = requireNotNull(container.deviceRepository.devices.value.find { it.duid == duid }) {
        "Device $duid not found — navigate to DeviceList first to load devices"
    }

    val deviceName: String = device.name
    // Follows the device list, which Product Info reloads after a rename.
    val currentDeviceName: StateFlow<String> = container.deviceRepository.devices
        .map { currentDeviceName(it, device.duid, deviceName) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, deviceName)
    val modelName: String? = container.deviceRepository.productsByDuid.value[duid]?.model
    val modelDisplayName: String? = productDisplayName(container.deviceRepository.productsByDuid.value[duid])
    val firmwareVersion: String? = device.fv
    private val serverFeatureSet = device.newFeatureSet
    val supportsFastRoute: Boolean = isFastRouteSupported(device.newFeatureSet)
    val dockCapabilities = DockCapabilities(modelName, device.featureSet, device.newFeatureSet)
    val betaFeatureDefs = supportedBetaFeatures(device.newFeatureSet)
    val unlistedFeatures = unlistedAdvertisedFeatures(device.newFeatureSet)
    val supportsPatrol = newFeatureBit(device.newFeatureSet, VIDEO_PATROL_FEATURE_BIT)
    val supportsPetSearch = isPetSearchSupported(device.newFeatureSet)
    val supportsMatter = newFeatureBit(device.newFeatureSet, MATTER_FEATURE_BIT)
    val twoKeyVideo = newFeatureBit(device.newFeatureSet, TWO_KEY_VIDEO_FEATURE_BIT)
    // Real-time video is a beta feature: shown for robots that advertise any video bit.
    val supportsCamera = CAMERA_FEATURE_BITS.any { newFeatureBit(device.newFeatureSet, it) }
    val showsBetaFeatures = betaFeatureDefs.any { it.section == FeatureSection.BETA && it.bit != null } || unlistedFeatures.isNotEmpty()
    val repository: VacuumRepository = container.vacuumRepositoryFor(device)

    // The robot's own answer wins; the server's record is the fallback until it has replied.
    val supportsSmartPlan: StateFlow<Boolean> = repository.robotFeatureSet
        .map { isSmartPlanSupported(it ?: serverFeatureSet) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, isSmartPlanSupported(serverFeatureSet))
    val supportsWaterSlide: StateFlow<Boolean> = repository.robotFeatureSet
        .map { isWaterSlideSupported(it ?: serverFeatureSet) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, isWaterSlideSupported(serverFeatureSet))
    // The redesign's single source of what this robot supports; the flags above stay until screens move over.
    val capabilities: StateFlow<Capabilities> = combine(repository.robotFeatureSet, repository.robotIsFcc) { nfs, fcc ->
        buildCapabilities(modelName, device.featureSet, serverFeatureSet, nfs, fcc)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, buildCapabilities(modelName, device.featureSet, serverFeatureSet, null))
    val status = repository.status

    private val errorBannerKey = "robot-error-${device.duid}"

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()
    private val actionMutex = Mutex()
    private var pendingActions = 0

    // The map's rotation and 2D/3D view are a purely local rendering choice: the robot has no
    // concept of either, so these live in this device's app settings rather than any RPC.
    private val _mapRotation = MutableStateFlow(container.appSettings.mapRotation(device.duid))
    val mapRotation: StateFlow<Int> = _mapRotation.asStateFlow()

    /** The flat 2D map's orientation. The 3D view has its own heading, [map3DYaw]. */
    fun rotateMap90() {
        val next = (_mapRotation.value + 90) % 360
        container.appSettings.setMapRotation(device.duid, next)
        _mapRotation.value = next
    }

    private val _map3DYaw = MutableStateFlow(container.appSettings.map3DYaw(device.duid))
    val map3DYaw: StateFlow<Float> = _map3DYaw.asStateFlow()
    private val _map3DTilt = MutableStateFlow(container.appSettings.map3DTilt(device.duid))
    val map3DTilt: StateFlow<Float> = _map3DTilt.asStateFlow()
    private var map3DSaveJob: Job? = null

    /** Turning the 3D model fires on every frame of a twist; the view updates at once. */
    fun setMap3DYaw(degrees: Float) {
        _map3DYaw.value = ((degrees % 360f) + 360f) % 360f
        saveMap3DViewSoon()
    }

    /** A twist or drag arrives as a change, so two events in one frame both count. */
    fun turnMap3DBy(degrees: Float) = setMap3DYaw(_map3DYaw.value + degrees)

    fun tiltMap3DBy(degrees: Float) = setMap3DTilt(_map3DTilt.value + degrees)

    fun setMap3DTilt(degrees: Float) {
        _map3DTilt.value = degrees.coerceIn(MAP_3D_MIN_TILT, MAP_3D_MAX_TILT)
        saveMap3DViewSoon()
    }

    // Only where the model settles is written to storage, not every frame of getting there.
    private fun saveMap3DViewSoon() {
        map3DSaveJob?.cancel()
        map3DSaveJob = viewModelScope.launch {
            delay(500)
            container.appSettings.setMap3DYaw(device.duid, _map3DYaw.value)
            container.appSettings.setMap3DTilt(device.duid, _map3DTilt.value)
        }
    }

    private val _map3D = MutableStateFlow(container.appSettings.map3D(device.duid))
    val map3D: StateFlow<Boolean> = _map3D.asStateFlow()

    fun setMap3D(value: Boolean) {
        container.appSettings.setMap3D(device.duid, value)
        _map3D.value = value
    }

    private val _lastKnown = MutableStateFlow<LastKnown?>(null)

    /** What the server last heard from the robot; only set while the robot is not answering. */
    val lastKnown: StateFlow<LastKnown?> = _lastKnown.asStateFlow()

    private val _lastKnownMap = MutableStateFlow<ParsedMap?>(null)

    /** The last map the robot sent, to draw while it cannot be asked for a live one. */
    val lastKnownMap: StateFlow<ParsedMap?> = _lastKnownMap.asStateFlow()

    private val _firmwareUpdate = MutableStateFlow<FirmwareUpdateState>(FirmwareUpdateState.Idle)
    val firmwareUpdate: StateFlow<FirmwareUpdateState> = _firmwareUpdate.asStateFlow()

    private val _firmwareProgress = MutableStateFlow<FirmwareProgress>(FirmwareProgress.Idle)
    val firmwareProgress: StateFlow<FirmwareProgress> = _firmwareProgress.asStateFlow()
    private var firmwareJob: kotlinx.coroutines.Job? = null

    /** Why an install cannot start right now (null when it can), from the robot's last status. */
    fun firmwareBlocker(): String? {
        val s = status.value
        return firmwareInstallBlocker(s.state, s.battery, s.chargeStatus, s.inCleaning, s.errorCode)
    }

    /** Asks the server to install, then follows the robot through download, install and reboot. */
    fun startFirmwareUpdate() {
        if (firmwareJob?.isActive == true) return
        firmwareJob = viewModelScope.launch {
            firmwareBlocker()?.let { _firmwareProgress.value = FirmwareProgress.Failed(it); return@launch }
            _firmwareProgress.value = FirmwareProgress.Starting
            val started = try {
                container.deviceRepository.startFirmwareUpdate(device.duid)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Throwable) {
                _firmwareProgress.value = FirmwareProgress.Failed(describe(e)); return@launch
            }
            if (!started.started) {
                _firmwareProgress.value = FirmwareProgress.Failed(started.reason ?: "The server did not start the update"); return@launch
            }
            followFirmwareUpdate(started.version)
        }
    }

    private suspend fun followFirmwareUpdate(target: String?) {
        val clock = kotlin.time.TimeSource.Monotonic.markNow()
        var seenWorking = false
        while (clock.elapsedNow() < FIRMWARE_WATCH_LIMIT) {
            kotlinx.coroutines.delay(FIRMWARE_POLL_MS)
            val ota = runCatching { repository.fetchOtaState() }
            val percent = if (ota.isSuccess) runCatching { repository.fetchOtaProgress() }.getOrNull() else null
            val step = nextFirmwareStep(_firmwareProgress.value, seenWorking, ota.isSuccess, ota.getOrNull(), percent)
            seenWorking = step.seenWorking
            _firmwareProgress.value = step.progress
            if (step.finished) {
                if (step.progress is FirmwareProgress.Failed) return
                val info = runCatching { container.deviceRepository.checkFirmwareUpdate(device.duid) }.getOrNull()
                _firmwareProgress.value = FirmwareProgress.Done(info?.currentVersion ?: target)
                checkFirmwareUpdate()
                return
            }
            if (!seenWorking && clock.elapsedNow() > FIRMWARE_START_LIMIT) {
                _firmwareProgress.value = FirmwareProgress.Failed("The robot did not start the update.")
                return
            }
        }
        _firmwareProgress.value = FirmwareProgress.Failed("The update is taking longer than expected. Check the robot and try again.")
    }

    private val _routines = MutableStateFlow<RoutinesState>(RoutinesState.Loading)
    val routines: StateFlow<RoutinesState> = _routines.asStateFlow()


    fun checkFirmwareUpdate() {
        viewModelScope.launch {
            _firmwareUpdate.value = FirmwareUpdateState.Checking
            _firmwareUpdate.value = try {
                val info = container.deviceRepository.checkFirmwareUpdate(device.duid)
                when {
                    info == null -> FirmwareUpdateState.Unavailable("No firmware info returned")
                    info.updatable -> FirmwareUpdateState.Available(
                        current = info.currentVersion ?: firmwareVersion,
                        latest = info.version,
                        notes = info.desc,
                        force = info.forceUpdate,
                    )
                    else -> FirmwareUpdateState.UpToDate(info.currentVersion ?: firmwareVersion)
                }
            } catch (e: Throwable) {
                FirmwareUpdateState.Unavailable(describe(e))
            }
        }
    }

    init {
        viewModelScope.launch {
            pendingActions++
            _busy.value = true
            try {
                actionMutex.withLock {
                    try {
                        container.ensureMqttConnected()
                        repository.attach()
                    } catch (e: Throwable) {
                        coroutineContext.ensureActive()
                        container.messages.error(describe(e))
                    }
                }
            } finally {
                pendingActions--
                _busy.value = pendingActions > 0
            }
            // One-shot reads: the link may still be coming up, so wait for it rather than let
            // them fail and leave the consumables and room names empty.
            launch {
                container.awaitMqttConnected()
                runCatching { repository.fetchConsumableStatus() }
                runCatching { repository.rooms() }
                runCatching { repository.fetchRobotFeatureSet() }
            }
            // Preload every settings page so a page opens complete; the map goes first (up to 8 s) so it is not slowed.
            launch {
                container.awaitMqttConnected()
                withTimeoutOrNull(8_000) { repository.parsedMap.first { it != null } }
                launch { runCatching { repository.fetchBetaFeatures(betaFeatureDefs) } }
                launch { runCatching { repository.fetchRobotSettings() } }
                launch { runCatching { repository.fetchDockSettings() } }
                launch { runCatching { repository.fetchDndSettings() } }
                launch { runCatching { repository.fetchTimers() } }
                launch { runCatching { repository.fetchMopMode() } }
                launch { runCatching { NewestRunCache.load(repository) } }
                if (capabilities.value.chargingMode) launch { runCatching { repository.fetchValleySettings() } }
            }
            // A robot that never answers (dead battery, lost Wi-Fi, stuck) leaves the screen empty;
            // the server still holds where it was last seen and what it last reported.
            launch {
                delay(LAST_KNOWN_AFTER_MS)
                if (status.value.state == null && status.value.battery == null) loadLastKnown()
            }
        }

        viewModelScope.launch {
            status.collect { s ->
                if (s.state != null || s.battery != null) {
                    _lastKnown.value = null
                    _lastKnownMap.value = null
                }
                val code = s.errorCode ?: 0
                if (code != 0) {
                    container.messages.showBanner(
                        key = errorBannerKey,
                        text = "$deviceName: ${VacuumErrorCodes.describe(code)}",
                        severity = MessageSeverity.ERROR,
                    )
                } else {
                    container.messages.dismissBanner(errorBannerKey)
                }
            }
        }
    }

    @OptIn(ExperimentalEncodingApi::class)
    fun loadLastKnown() {
        viewModelScope.launch {
            val snapshot = runCatching { container.deviceRepository.lastKnown(device.duid) }.getOrNull() ?: return@launch
            if (status.value.state != null || status.value.battery != null) return@launch
            _lastKnown.value = snapshot
            _lastKnownMap.value = snapshot.map
                ?.let { runCatching { parseLegacyMap(Base64.decode(it)) }.getOrNull() }
                ?.withRoomTable(snapshot.rooms)
        }
    }

    private var cameraSessionHolder: CameraLiveSession? = null

    /** The live view's session lives here so it keeps streaming while another screen, such as Pin & Go, is open. */
    fun cameraSession(): CameraLiveSession =
        cameraSessionHolder ?: CameraLiveSession(repository.session).also { cameraSessionHolder = it }

    fun closeCamera() {
        cameraSessionHolder?.dispose()
        cameraSessionHolder = null
    }

    override fun onCleared() {
        closeCamera()
        container.messages.dismissBanner(errorBannerKey)
        repository.detach()
    }

    fun refreshMap() {
        viewModelScope.launch {
            pendingActions++
            _busy.value = true
            try {
                actionMutex.withLock {
                    try {
                        repository.fetchMap()
                        runCatching { repository.rooms() }
                        coroutineContext.ensureActive()
                    } catch (e: Throwable) {
                        coroutineContext.ensureActive()
                        container.messages.error(describe(e))
                        runCatching { repository.forceReconnect() }
                        coroutineContext.ensureActive()
                    }
                }
            } finally {
                pendingActions--
                _busy.value = pendingActions > 0
            }
        }
    }


    /**
     * Give a room a custom name the way the Roborock app does: the name becomes a cloud room and the
     * segment points at it, keeping the room's type tag so the robot still treats it as that type.
     */
    suspend fun renameRoom(segment: Int, name: String) {
        val validName = validateRoomName(name)
        repository.checkRoomTable(segment)
        val room = container.deviceRepository.createRoom(validName)
        repository.assignRoom(segment, room.id)
        container.deviceRepository.refresh()
        repository.setHomeRooms(container.deviceRepository.home.value?.rooms ?: emptyList())
    }

    /**
     * Set a room's type the way the Roborock app does: get a cloud room for the type's label from
     * the server, point the robot's segment at it with `name_segment` along with the type's tag
     * (which the robot uses to adjust its cleaning), then reload the home so the name resolves.
     * Throws with a user-facing message on any failure.
     */
    suspend fun setRoomType(segment: Int, type: RoomType) {
        repository.checkRoomTable(segment)
        val room = container.deviceRepository.createRoom(type.label)
        repository.assignRoom(segment, room.id, type.tag)
        container.deviceRepository.refresh()
        repository.setHomeRooms(container.deviceRepository.home.value?.rooms ?: emptyList())
    }

    fun loadRoutines() {
        viewModelScope.launch {
            _routines.value = RoutinesState.Loading
            _routines.value = try {
                RoutinesState.Loaded(container.deviceRepository.scenes(device.duid))
            } catch (e: Throwable) {
                RoutinesState.Failed(describe(e))
            }
        }
    }

    /** The server runs the routine's steps itself; the robot's status shows how far it got. */
    fun runRoutine(scene: Scene) = run {
        val result = container.deviceRepository.runScene(scene.id)
        container.messages.success(routineRunMessage(scene.name, result))
    }

    /** The room order a full clean follows on the loaded map; empty when none has been set. */
    fun defaultCleanOrder(): List<Int> =
        container.appSettings.cleanOrder(device.duid, repository.currentFloorFlag.value)

    /** Kept only once the robot has taken the order, so the app never remembers one it refused. */
    fun saveDefaultCleanOrder(order: List<Int>) = run("Cleaning order saved") {
        it.saveCleanSequence(order)
        container.appSettings.setCleanOrder(device.duid, it.currentFloorFlag.value, order)
    }

    fun removeDefaultCleanOrder() {
        container.appSettings.setCleanOrder(device.duid, repository.currentFloorFlag.value, emptyList())
        container.messages.success("Default order removed")
    }

    /**
     * Runs a settings change and shows the error if the robot refuses it. Unlike [run] it neither
     * marks the screen busy nor reads the status back, which would be too heavy for a slider; the
     * settings are read again on a failure so the screen goes back to what the robot really holds.
     */
    fun report(action: suspend (VacuumRepository) -> Unit) {
        viewModelScope.launch {
            try {
                action(repository)
            } catch (e: Throwable) {
                container.messages.error(describe(e))
                runCatching { repository.fetchRobotSettings() }
            }
        }
    }

    /**
     * Runs [action] with the screen marked busy and reads the status back. A failure goes to
     * [onError] when one is given, for a bottom sheet that would hide the screen's message.
     */
    fun run(
        successMessage: String? = null,
        onError: ((String) -> Unit)? = null,
        action: suspend (VacuumRepository) -> Unit,
    ) {
        viewModelScope.launch {
            pendingActions++
            _busy.value = true
            try {
                actionMutex.withLock {
                    try {
                        action(repository)

                        runCatching { repository.refreshStatus() }
                        coroutineContext.ensureActive()
                        successMessage?.let { container.messages.success(it) }
                    } catch (e: Throwable) {
                        coroutineContext.ensureActive()
                        val text = describe(e)
                        if (onError != null) onError(text) else container.messages.error(text)
                    }
                }
            } finally {
                pendingActions--
                _busy.value = pendingActions > 0
            }
        }
    }
}

private val FIRMWARE_POLL_MS = 3_000L
private val FIRMWARE_START_LIMIT = 2.minutes
private val FIRMWARE_WATCH_LIMIT = 25.minutes

sealed interface FirmwareUpdateState {
    data object Idle : FirmwareUpdateState
    data object Checking : FirmwareUpdateState
    data class UpToDate(val current: String?) : FirmwareUpdateState
    data class Available(
        val current: String?,
        val latest: String?,
        val notes: String?,
        val force: Boolean,
    ) : FirmwareUpdateState
    data class Unavailable(val reason: String) : FirmwareUpdateState
}

sealed interface RoutinesState {
    data object Loading : RoutinesState
    data class Loaded(val routines: List<Scene>) : RoutinesState
    data class Failed(val reason: String) : RoutinesState
}

/** What to tell the user once the server has answered a run request; a refusal is thrown. */
internal fun routineRunMessage(name: String, result: SceneRunResult): String {
    if (!result.accepted) {
        val active = result.activeSceneName
        throw IllegalStateException(
            if (result.status == "routine_in_progress" && active != null) {
                "\"$active\" is still running. Run it again to stop it first."
            } else {
                "The server did not start \"$name\"" + (result.status?.let { " ($it)" } ?: "")
            }
        )
    }
    return if (result.status == "cancel_requested") "Stopping \"$name\"" else "\"$name\" started"
}

/**
 * Longest room name allowed. The name is stored by the server, not the robot, and its real limit
 * is unknown; 23 keeps names short enough for the room labels on the map.
 */
const val MAX_ROOM_NAME_LENGTH = 23

/** How long a robot may stay silent before the server's last-known state is fetched. */
private const val LAST_KNOWN_AFTER_MS = 10_000L

/**
 * Trimmed name, or [MapEditException] if unusable. A leading `{` or `[` makes the server treat
 * the form body as JSON and verify the request signature differently, so it is rejected here.
 */
fun validateRoomName(name: String): String {
    val clean = name.trim()
    if (clean.isEmpty()) throw MapEditException("Enter a room name")
    if (clean.length > MAX_ROOM_NAME_LENGTH) throw MapEditException("Room names can be at most $MAX_ROOM_NAME_LENGTH characters")
    if (clean.startsWith("{") || clean.startsWith("[")) throw MapEditException("Room names can't start with { or [")
    return clean
}

private fun describe(e: Throwable): String {
    val parts = mutableListOf<String>()
    var current: Throwable? = e
    while (current != null) {
        val msg = current.message
        parts += when {
            current is TimeoutCancellationException -> "The robot didn't answer in time"
            msg.isNullOrBlank() -> current::class.simpleName ?: "Throwable"
            else -> msg
        }
        current = current.cause?.takeIf { it !== current }
    }
    return parts.distinct().joinToString(" → ")
}
