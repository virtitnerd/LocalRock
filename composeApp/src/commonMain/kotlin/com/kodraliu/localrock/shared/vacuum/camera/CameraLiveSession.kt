package com.kodraliu.localrock.shared.vacuum.camera

import com.kodraliu.localrock.shared.vacuum.VacuumSession
import com.kodraliu.localrock.shared.vacuum.checkHomesecPassword
import com.kodraliu.localrock.shared.vacuum.getCameraStatus
import com.kodraliu.localrock.shared.vacuum.getDeviceIce
import com.kodraliu.localrock.shared.vacuum.getDeviceSdp
import com.kodraliu.localrock.shared.vacuum.getHomesecConnectStatus
import com.kodraliu.localrock.shared.vacuum.getTurnServer
import com.kodraliu.localrock.shared.vacuum.resetHomesecPassword
import com.kodraliu.localrock.shared.vacuum.sendIceToRobot
import com.kodraliu.localrock.shared.vacuum.setCameraStatus
import com.kodraliu.localrock.shared.vacuum.setHomesecPassword
import com.kodraliu.localrock.shared.vacuum.sendSdpToRobot
import com.kodraliu.localrock.shared.vacuum.startCameraPreview
import com.kodraliu.localrock.shared.vacuum.stopCameraPreview
import com.kodraliu.localrock.shared.webrtc.RtcConnectionState
import com.kodraliu.localrock.shared.webrtc.RtcIceCandidate
import com.kodraliu.localrock.shared.webrtc.RtcIceServer
import com.kodraliu.localrock.shared.webrtc.RtcPeer
import com.kodraliu.localrock.shared.webrtc.createRtcPeer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.kotlincrypto.hash.md.MD5
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi


@OptIn(ExperimentalEncodingApi::class)
class CameraLiveSession(private val session: VacuumSession) {

    sealed interface State {
        data object Idle : State
        data class Connecting(val stage: String) : State
        data class Activating(val secondsLeft: Int, val twoKey: Boolean) : State
        data object Streaming : State
        data class Failed(val message: String, val canActivate: Boolean = false) : State
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var connectJob: Job? = null

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state

    private val _peer = MutableStateFlow<RtcPeer?>(null)
    val peer: StateFlow<RtcPeer?> = _peer

    /** SD, HD, FHD or AUTO, asked for when the stream starts like the official app does. */
    var quality: String = "HD"
        private set

    /** Restarts the stream asking the robot for [newQuality]. */
    fun restartWithQuality(pin: String, newQuality: String) {
        quality = newQuality
        connectJob?.cancel()
        closePeer()
        _state.value = State.Connecting("Switching to $newQuality…")
        connectJob = scope.launch {
            try {
                runCatching { session.stopCameraPreview(CLIENT_ID) }
                delay(800)
                connect(pin)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                fail(e)
            }
        }
    }

    /** Whether the last attempt was to register a new PIN, so activation can finish that step. */
    private var registeringPin = false

    fun start(pin: String) {
        if (connectJob?.isActive == true) return
        registeringPin = false
        _state.value = State.Connecting("Preparing…")
        connectJob = scope.launch {
            try {
                connect(pin)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                fail(e)
            }
        }
    }


    fun setPinAndStart(pin: String) {
        if (connectJob?.isActive == true) return
        registeringPin = true
        _state.value = State.Connecting("Setting PIN on robot…")
        connectJob = scope.launch {
            try {
                registerPin(pin)
                connect(pin)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                fail(e)
            }
        }
    }

    /** Turns remote viewing on with the robot's own activation, then connects. */
    fun activate(pin: String, twoKey: Boolean) {
        if (connectJob?.isActive == true) return
        _state.value = State.Activating(ACTIVATION_SECONDS, twoKey)
        connectJob = scope.launch {
            try {
                activateRemoteViewing(twoKey)
                if (registeringPin) registerPin(pin)
                connect(pin)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                fail(e)
            }
        }
    }

    private suspend fun registerPin(pin: String) {
        val passwordMd5 = md5Hex(pin)
        val first = runCatching { session.setHomesecPassword(passwordMd5) }
        if (first.isFailure || first.getOrNull()?.error != null) {
            runCatching { session.resetHomesecPassword() }
            val retry = session.setHomesecPassword(passwordMd5)
            if (retry.error != null) {
                if (errorCode(retry.error) == ERR_PASSWORD_DISABLED) throw ActivationRequired(PASSWORD_DISABLED_HINT)
                throw IllegalStateException("Robot refused to set the PIN: ${retry.error}")
            }
        }
    }

    private fun fail(e: Throwable) {
        _state.value = State.Failed(e.message ?: e::class.simpleName ?: "Unknown error", canActivate = e is ActivationRequired)
        closePeer()
    }

    /**
     * Two-key robots are asked to wait for the Power press by writing state 1 into `camera_status`;
     * three-key robots just wait for the button hold. Either way the robot sets the bit itself.
     */
    private suspend fun activateRemoteViewing(twoKey: Boolean) {
        suspend fun read() = runCatching { firstInt(session.getCameraStatus().result) }.getOrNull()?.let { CameraStatus(it) }
        val start = read() ?: throw IllegalStateException("The robot did not report its camera status")
        if (start.activated) return
        if (twoKey) {
            for (step in start.twoKeyActivationSteps()) {
                val write = session.setCameraStatus(step.raw)
                if (write.error != null) throw IllegalStateException("The robot refused to start activation: ${write.error}")
                delay(300)
            }
        }
        repeat(ACTIVATION_SECONDS) { elapsed ->
            _state.value = State.Activating(ACTIVATION_SECONDS - elapsed, twoKey)
            delay(1_000)
            val now = read() ?: return@repeat
            if (now.activated) return
        }
        if (twoKey) runCatching { session.setCameraStatus((read() ?: start).cancellingTwoKey().raw) }
        throw IllegalStateException("Activation timed out. Tap Activate again and press the button within a minute.")
    }

    fun stop() {
        connectJob?.cancel()
        connectJob = null
        closePeer()
        scope.launch { runCatching { session.stopCameraPreview(CLIENT_ID) } }
        _state.value = State.Idle
    }

    fun dispose() {
        connectJob?.cancel()
        closePeer()
        val s = session
        scope.launch(NonCancellable) { runCatching { s.stopCameraPreview(CLIENT_ID) } }
            .invokeOnCompletion { scope.cancel() }
    }

    private fun closePeer() {
        _peer.value?.close()
        _peer.value = null
    }

    private suspend fun connect(pin: String) {
        val passwordMd5 = md5Hex(pin)

        _state.value = State.Connecting("Checking PIN…")
        val check = session.checkHomesecPassword(passwordMd5)
        if (check.error != null) {
            if (errorCode(check.error) == ERR_PASSWORD_DISABLED) throw ActivationRequired(PASSWORD_DISABLED_HINT)
            throw IllegalStateException("PIN check failed: ${check.error}")
        }
        val ok = firstInt(check.result)
        if (ok != null && ok != 1) throw IllegalStateException("Wrong camera PIN")

        // If another client (e.g. the official app) holds the camera, ask it to let go.
        _state.value = State.Connecting("Claiming camera…")
        var kicks = 0
        while (kicks < 5) {
            val status = runCatching { session.getHomesecConnectStatus() }.getOrNull()
            val other = firstObject(status?.result)?.get("client_id")?.jsonPrimitive?.contentOrNull
            // The robot reports the literal "none" when no client holds the camera. Treating that
            // as a rival made us fire stop_camera_preview five times before every start.
            if (other.isNullOrBlank() || other == NO_CLIENT || other == CLIENT_ID) break
            runCatching { session.stopCameraPreview(other) }
            delay(1_000)
            kicks++
        }

        _state.value = State.Connecting("Starting camera…")
        val startResp = session.startCameraPreview(CLIENT_ID, passwordMd5, quality)
        println("CameraQuality asked=$quality reply=${startResp.result ?: startResp.error}")
        if (startResp.error != null) {
            if (errorCode(startResp.error) == ERR_CAMERA_PREVIEW_DENIED) {
                val status = runCatching { firstInt(session.getCameraStatus().result) }.getOrNull()
                throw ActivationRequired(
                    "Remote viewing is not activated on this robot (-10012, camera_status=" +
                        "${status ?: "unknown"}). Activate it here, then press the button on the robot when asked."
                )
            }
            if (errorCode(startResp.error) == ERR_TURN_SERVER_FAILED) throw IllegalStateException(TURN_SERVER_HINT)
            throw IllegalStateException("Robot refused camera preview: ${startResp.error}")
        }

        _state.value = State.Connecting("Negotiating…")
        val iceServers = mutableListOf<RtcIceServer>()
        runCatching {
            val turn = firstObject(session.getTurnServer().result) ?: return@runCatching
            val url = turn["url"]?.jsonPrimitive?.contentOrNull ?: return@runCatching
            iceServers += RtcIceServer(
                url = url,
                username = turn["user"]?.jsonPrimitive?.contentOrNull ?: "",
                password = turn["pwd"]?.jsonPrimitive?.contentOrNull ?: "",
            )
        }

        val peer = createRtcPeer(iceServers)
        _peer.value = peer

        coroutineScope {
            val localIceJob = launch {
                peer.localCandidates.collect { c ->
                    val json = buildJsonObject {
                        put("candidate", c.candidate)
                        put("sdpMLineIndex", c.sdpMLineIndex)
                        put("sdpMid", c.sdpMid ?: "0")
                    }
                    runCatching { session.sendIceToRobot(b64(json)) }
                }
            }

            val offerSdp = peer.createOffer()
            val offerJson = buildJsonObject {
                put("type", "offer")
                put("sdp", offerSdp)
            }
            session.sendSdpToRobot(b64(offerJson))

            var answerSdp: String? = null
            var tries = 0
            while (answerSdp == null && tries < 15) {
                val devSdp = firstObject(runCatching { session.getDeviceSdp() }.getOrNull()?.result)
                    ?.get("dev_sdp")?.jsonPrimitive?.contentOrNull
                if (!devSdp.isNullOrBlank() && devSdp != "retry") {
                    val decoded = runCatching { Base64.decode(devSdp).decodeToString() }.getOrDefault(devSdp)
                    answerSdp = runCatching {
                        SignalingJson.parseToJsonElement(decoded).jsonObject["sdp"]?.jsonPrimitive?.contentOrNull
                    }.getOrNull()
                }
                if (answerSdp == null) {
                    delay(1_000)
                    tries++
                }
            }
            if (answerSdp == null) {
                localIceJob.cancel()
                throw IllegalStateException("Robot did not answer — does this model have a camera?")
            }
            peer.setRemoteAnswer(answerSdp)

            val remoteIceJob = launch {
                val seen = mutableSetOf<String>()
                repeat(30) {
                    val result = runCatching { session.getDeviceIce() }.getOrNull()?.result
                    val entries = firstObject(result)?.get("dev_ice") as? JsonArray
                    entries?.forEach { entry ->
                        val raw = (entry as? JsonPrimitive)?.contentOrNull ?: return@forEach
                        if (!seen.add(raw)) return@forEach
                        val decoded = runCatching { Base64.decode(raw).decodeToString() }.getOrNull() ?: return@forEach
                        runCatching {
                            val o = SignalingJson.parseToJsonElement(decoded).jsonObject
                            peer.addRemoteCandidate(
                                RtcIceCandidate(
                                    sdpMid = o["sdpMid"]?.jsonPrimitive?.contentOrNull,
                                    sdpMLineIndex = o["sdpMLineIndex"]?.jsonPrimitive?.intOrNull ?: 0,
                                    candidate = o["candidate"]?.jsonPrimitive?.contentOrNull ?: return@runCatching,
                                )
                            )
                        }
                    }
                    if (peer.connectionState.value == RtcConnectionState.CONNECTED) return@launch
                    delay(1_000)
                }
            }

            peer.connectionState.collect { st ->
                when (st) {
                    RtcConnectionState.CONNECTED -> {
                        _state.value = State.Streaming
                        localIceJob.cancel()
                        remoteIceJob.cancel()
                    }
                    RtcConnectionState.FAILED ->
                        throw IllegalStateException("Video connection failed")
                    RtcConnectionState.DISCONNECTED ->
                        if (_state.value is State.Streaming) throw IllegalStateException("Video connection lost")
                    else -> Unit
                }
            }
        }
    }

    private fun b64(json: JsonElement): String =
        Base64.encode(SignalingJson.encodeToString(JsonElement.serializer(), json).encodeToByteArray())

    /** The robot refused the preview because remote viewing has not been activated on it. */
    private class ActivationRequired(message: String) : IllegalStateException(message)

    private companion object {
        const val ACTIVATION_SECONDS = 60
        const val CLIENT_ID = "7661636c6f63616c"
        val SignalingJson = Json { ignoreUnknownKeys = true; explicitNulls = false }

        /** Robot error for "password is disable": the homesec feature is off on the device. */
        const val ERR_PASSWORD_DISABLED = -1007

        /** Robot error for "camera status denied preview": the camera flag forbids streaming. */
        const val ERR_CAMERA_PREVIEW_DENIED = -10012

        /**
         * `camera_status` as a Qrevo Curv 2 Flow reports it: 259 with remote viewing off, 8615 once
         * the cloud activated it. See [CameraStatus] for the bit layout. Bit 2 is set by the robot
         * itself after a three-button hold and rejects writes; two-key robots are activated by
         * writing state 1 into bits 12-13 and pressing Power (the robot then sets state 2).
         */
        const val CAMERA_STATUS_OFF = 259

        /**
         * Robot error for "request turnserver failed": before streaming the robot asks its server
         * for a relay and got no answer (seen on an S7 MaxV, local_roborock_server discussion #140).
         */
        const val ERR_TURN_SERVER_FAILED = -10014

        const val TURN_SERVER_HINT =
            "The robot couldn't get a video relay from the server (-10014). The camera needs a local " +
                "server with camera (TURN) support set up, and the robot paired so that it can find it. " +
                "See the server's camera documentation."

        /** `client_id` the robot returns when the camera is free. */
        const val NO_CLIENT = "none"

        const val PASSWORD_DISABLED_HINT =
            "The camera PIN is switched off on this robot (-1007) until remote viewing is activated. " +
                "Activate it here, then draw your pattern."

        private fun errorCode(error: JsonObject?): Int? =
            error?.get("code")?.jsonPrimitive?.intOrNull

        fun md5Hex(input: String): String {
            val digest = MD5().digest(input.encodeToByteArray())
            val sb = StringBuilder(digest.size * 2)
            for (b in digest) {
                val v = b.toInt() and 0xff
                sb.append("0123456789abcdef"[v ushr 4])
                sb.append("0123456789abcdef"[v and 0x0f])
            }
            return sb.toString()
        }

        fun firstObject(result: JsonElement?): JsonObject? = when (result) {
            is JsonObject -> result
            is JsonArray -> result.firstOrNull() as? JsonObject
            else -> null
        }

        fun firstInt(result: JsonElement?): Int? = when (result) {
            is JsonPrimitive -> result.intOrNull
            is JsonArray -> (result.firstOrNull() as? JsonPrimitive)?.intOrNull
            else -> null
        }
    }
}
