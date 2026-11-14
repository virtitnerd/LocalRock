package com.kodraliu.localrock.shared.webrtc

import com.kodraliu.localrock.shared.protocol.appContextOrNull
import android.content.Context
import android.media.AudioManager
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import org.webrtc.AudioSource
import org.webrtc.AudioTrack
import org.webrtc.DataChannel
import org.webrtc.DefaultVideoDecoderFactory
import org.webrtc.DefaultVideoEncoderFactory
import org.webrtc.EglBase
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.MediaStream
import org.webrtc.MediaStreamTrack
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.RTCStats
import org.webrtc.RTCStatsReport
import org.webrtc.RtpReceiver
import org.webrtc.RtpTransceiver
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import org.webrtc.VideoTrack
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

actual val liveViewSupported: Boolean = true

actual fun createRtcPeer(iceServers: List<RtcIceServer>, experiments: VideoExperiments): RtcPeer =
    AndroidRtcPeer(iceServers, experiments)

internal val rtcEglBase: EglBase by lazy { EglBase.create() }

private val peerConnectionFactory: PeerConnectionFactory by lazy {
    val ctx = requireNotNull(appContextOrNull()) { "Android context not initialized" }
    PeerConnectionFactory.initialize(
        PeerConnectionFactory.InitializationOptions.builder(ctx)
            // A live view wants the newest frame, not a smooth one: no extra playout delay.
            .setFieldTrials("WebRTC-ForcePlayoutDelay/min_ms:0,max_ms:0/")
            .createInitializationOptions()
    )
    PeerConnectionFactory.builder()
        .setVideoDecoderFactory(DefaultVideoDecoderFactory(rtcEglBase.eglBaseContext))
        .setVideoEncoderFactory(DefaultVideoEncoderFactory(rtcEglBase.eglBaseContext, true, true))
        .createPeerConnectionFactory()
}

internal class AndroidRtcPeer(iceServers: List<RtcIceServer>, private val experiments: VideoExperiments) : RtcPeer {

    private val _localCandidates = MutableSharedFlow<RtcIceCandidate>(replay = 32, extraBufferCapacity = 32)
    override val localCandidates: SharedFlow<RtcIceCandidate> = _localCandidates

    private val _connectionState = MutableStateFlow(RtcConnectionState.NEW)
    override val connectionState: StateFlow<RtcConnectionState> = _connectionState

    val videoTrack = MutableStateFlow<VideoTrack?>(null)

    private val _stats = MutableStateFlow<RtcStats?>(null)
    override val stats: StateFlow<RtcStats?> = _stats
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private var audioTransceiver: RtpTransceiver? = null
    private var micSource: AudioSource? = null
    private var micTrack: AudioTrack? = null
    private var remoteAudio: AudioTrack? = null
    private var speakerWanted = true
    private var lastBytes: Double? = null
    private var lastBytesAtUs = 0L

    private val audioManager = appContextOrNull()?.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    private var savedMode: Int? = null
    private var savedSpeaker: Boolean? = null

    // A call is played through the loudspeaker, not the earpiece; put the phone's audio back afterwards.
    private fun routeToSpeaker(on: Boolean) {
        val am = audioManager ?: return
        if (on) {
            if (savedMode == null) {
                savedMode = am.mode
                savedSpeaker = am.isSpeakerphoneOn
            }
            am.mode = AudioManager.MODE_IN_COMMUNICATION
            am.isSpeakerphoneOn = true
        } else {
            savedMode?.let { am.mode = it }
            savedSpeaker?.let { am.isSpeakerphoneOn = it }
            savedMode = null
            savedSpeaker = null
        }
    }

    override fun setMicEnabled(enabled: Boolean) {
        val transceiver = audioTransceiver ?: return
        if (enabled) {
            if (micTrack == null) {
                val source = peerConnectionFactory.createAudioSource(MediaConstraints())
                micSource = source
                micTrack = peerConnectionFactory.createAudioTrack("mic", source)
                transceiver.sender.setTrack(micTrack, false)
            }
            micTrack?.setEnabled(true)
        } else {
            micTrack?.setEnabled(false)
        }
    }

    override fun setSpeakerEnabled(enabled: Boolean) {
        speakerWanted = enabled
        remoteAudio?.setEnabled(enabled)
    }

    private val pc: PeerConnection

    init {
        val servers = iceServers.map { s ->
            PeerConnection.IceServer.builder(s.url)
                .apply {
                    if (s.username.isNotEmpty()) setUsername(s.username)
                    if (s.password.isNotEmpty()) setPassword(s.password)
                }
                .createIceServer()
        }
        val config = PeerConnection.RTCConfiguration(servers).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
        }
        pc = requireNotNull(peerConnectionFactory.createPeerConnection(config, Observer())) {
            "createPeerConnection returned null"
        }
        scope.launch {
            while (true) {
                delay(2000)
                if (_connectionState.value == RtcConnectionState.CONNECTED) runCatching { pc.getStats { report -> readStats(report) } }
            }
        }
    }

    private fun readStats(report: RTCStatsReport) {
        val all = report.statsMap
        fun num(o: RTCStats?, key: String): Double? = (o?.members?.get(key) as? Number)?.toDouble()
        fun str(o: RTCStats?, key: String): String? = o?.members?.get(key) as? String
        val transport = all.values.firstOrNull { it.type == "transport" }
        val pair = all[str(transport, "selectedCandidatePairId")]
            ?: all.values.firstOrNull { it.type == "candidate-pair" && str(it, "state") == "succeeded" && it.members["nominated"] == true }
        val local = all[str(pair, "localCandidateId")]
        val remote = all[str(pair, "remoteCandidateId")]
        val relayed = str(local, "candidateType") == "relay" || str(remote, "candidateType") == "relay"
        val video = all.values.firstOrNull { it.type == "inbound-rtp" && (str(it, "kind") ?: str(it, "mediaType")) == "video" }
        val emitted = num(video, "jitterBufferEmittedCount") ?: 0.0
        val buffer = if (emitted > 0) (num(video, "jitterBufferDelay") ?: 0.0) / emitted * 1000 else null
        val snapshot = RtcStats(
            relayed = relayed,
            rttMs = num(pair, "currentRoundTripTime")?.let { (it * 1000).toInt() },
            width = num(video, "frameWidth")?.toInt(),
            height = num(video, "frameHeight")?.toInt(),
            fps = num(video, "framesPerSecond")?.toInt(),
            bufferMs = buffer?.toInt(),
        )
        val codec = all[str(video, "codecId")]
        val kbps = num(video, "bytesReceived")?.let { bytes ->
            val last = lastBytes
            val dtUs = report.timestampUs.toLong() - lastBytesAtUs
            lastBytes = bytes
            lastBytesAtUs = report.timestampUs.toLong()
            if (last != null && dtUs > 0) ((bytes - last) * 8.0 / (dtUs / 1000.0)).toInt() else null
        }
        val inbound = "codec=${str(codec, "mimeType")} pt=${num(codec, "payloadType")?.toInt()} fmtp=${str(codec, "sdpFmtpLine")} " +
            "in=${num(video, "frameWidth")?.toInt()}x${num(video, "frameHeight")?.toInt()}@${num(video, "framesPerSecond")?.toInt()} kbps=$kbps " +
            "decoded=${num(video, "framesDecoded")?.toLong()} dropped=${num(video, "framesDropped")?.toLong()} " +
            "nack=${num(video, "nackCount")?.toLong()} pli=${num(video, "pliCount")?.toLong()} decoder=${str(video, "decoderImplementation")}"
        val audioIn = all.values.firstOrNull { it.type == "inbound-rtp" && (str(it, "kind") ?: str(it, "mediaType")) == "audio" }
        val audioOut = all.values.firstOrNull { it.type == "outbound-rtp" && (str(it, "kind") ?: str(it, "mediaType")) == "audio" }
        Log.i(
            "RtcAudio",
            "in pkts=${num(audioIn, "packetsReceived")?.toLong()} bytes=${num(audioIn, "bytesReceived")?.toLong()} " +
                "level=${num(audioIn, "audioLevel")} codec=${str(audioIn, "codecId")} | " +
                "out pkts=${num(audioOut, "packetsSent")?.toLong()} bytes=${num(audioOut, "bytesSent")?.toLong()} mic=${micTrack?.enabled()}",
        )
        Log.i("RtcStats", "$snapshot local=${str(local, "candidateType")} remote=${str(remote, "candidateType")} $inbound")
        _stats.value = snapshot
    }

    override suspend fun createOffer(): String {
        // The robot only answers the official app's offer: audio first and sendrecv (a transceiver without a
        // track is enough), then receive-only video. The codecs are trimmed below.
        audioTransceiver = pc.addTransceiver(
            MediaStreamTrack.MediaType.MEDIA_TYPE_AUDIO,
            RtpTransceiver.RtpTransceiverInit(RtpTransceiver.RtpTransceiverDirection.SEND_RECV),
        )
        pc.addTransceiver(
            MediaStreamTrack.MediaType.MEDIA_TYPE_VIDEO,
            RtpTransceiver.RtpTransceiverInit(if (experiments.direction == VideoDirection.SEND_RECV) RtpTransceiver.RtpTransceiverDirection.SEND_RECV else RtpTransceiver.RtpTransceiverDirection.RECV_ONLY),
        )
        val created = suspendCoroutine<SessionDescription> { cont ->
            pc.createOffer(object : SdpObserverAdapter() {
                override fun onCreateSuccess(desc: SessionDescription) = cont.resume(desc)
                override fun onCreateFailure(error: String?) =
                    cont.resumeWithException(RuntimeException("createOffer failed: $error"))
            }, MediaConstraints())
        }
        val offer = SessionDescription(created.type, buildRobotOffer(created.description, experiments))
        suspendCoroutine<Unit> { cont ->
            pc.setLocalDescription(object : SdpObserverAdapter() {
                override fun onSetSuccess() = cont.resume(Unit)
                override fun onSetFailure(error: String?) =
                    cont.resumeWithException(RuntimeException("setLocalDescription failed: $error"))
            }, offer)
        }
        if (experiments.dumpSdp) dumpSdp("offer", offer.description)
        return offer.description
    }

    override suspend fun setRemoteAnswer(sdp: String) {
        if (experiments.dumpSdp) dumpSdp("answer", sdp)
        Log.i("RtcAudio", "answer audio section: " + sdp.split(Regex("\r?\n")).dropWhile { !it.startsWith("m=audio") }.takeWhile { !it.startsWith("m=video") }.filter { it.startsWith("m=") || it.startsWith("a=send") || it.startsWith("a=recv") || it.startsWith("a=inactive") || it.startsWith("a=rtpmap") || it.startsWith("a=ssrc") || it.startsWith("a=msid") }.joinToString(" ; "))
        suspendCoroutine<Unit> { cont ->
            pc.setRemoteDescription(object : SdpObserverAdapter() {
                override fun onSetSuccess() = cont.resume(Unit)
                override fun onSetFailure(error: String?) =
                    cont.resumeWithException(RuntimeException("setRemoteDescription failed: $error"))
            }, SessionDescription(SessionDescription.Type.ANSWER, sdp))
        }
    }

    private fun dumpSdp(label: String, sdp: String) {
        Log.i("SdpDump", "--- $label (${experiments.encode()}) ---")
        redactSdp(sdp).lines().forEach { Log.i("SdpDump", "$label| $it") }
    }

    override fun addRemoteCandidate(candidate: RtcIceCandidate) {
        pc.addIceCandidate(IceCandidate(candidate.sdpMid ?: "0", candidate.sdpMLineIndex, candidate.candidate))
    }

    override fun close() {
        scope.cancel()
        routeToSpeaker(false)
        micTrack?.setEnabled(false)
        videoTrack.value = null
        _connectionState.value = RtcConnectionState.CLOSED
        runCatching { pc.close() }
        runCatching { pc.dispose() }
        runCatching { micTrack?.dispose() }
        runCatching { micSource?.dispose() }
    }

    private inner class Observer : PeerConnection.Observer {
        override fun onIceCandidate(candidate: IceCandidate) {
            _localCandidates.tryEmit(
                RtcIceCandidate(candidate.sdpMid, candidate.sdpMLineIndex, candidate.sdp)
            )
        }

        override fun onTrack(transceiver: RtpTransceiver) {
            (transceiver.receiver.track() as? VideoTrack)?.let { videoTrack.value = it }
            (transceiver.receiver.track() as? AudioTrack)?.let {
                remoteAudio = it
                it.setEnabled(speakerWanted)
            }
        }

        override fun onAddTrack(receiver: RtpReceiver, streams: Array<out MediaStream>) {
            (receiver.track() as? VideoTrack)?.let { videoTrack.value = it }
        }

        override fun onConnectionChange(newState: PeerConnection.PeerConnectionState) {
            // Don't clobber the terminal CLOSED state set by close().
            if (_connectionState.value == RtcConnectionState.CLOSED) return
            _connectionState.value = when (newState) {
                PeerConnection.PeerConnectionState.NEW -> RtcConnectionState.NEW
                PeerConnection.PeerConnectionState.CONNECTING -> RtcConnectionState.CONNECTING
                PeerConnection.PeerConnectionState.CONNECTED -> {
                    routeToSpeaker(true)
                    RtcConnectionState.CONNECTED
                }
                PeerConnection.PeerConnectionState.DISCONNECTED -> RtcConnectionState.DISCONNECTED
                PeerConnection.PeerConnectionState.FAILED -> RtcConnectionState.FAILED
                PeerConnection.PeerConnectionState.CLOSED -> RtcConnectionState.CLOSED
            }
        }

        override fun onSignalingChange(state: PeerConnection.SignalingState) {}
        override fun onIceConnectionChange(state: PeerConnection.IceConnectionState) {}
        override fun onIceConnectionReceivingChange(receiving: Boolean) {}
        override fun onIceGatheringChange(state: PeerConnection.IceGatheringState) {}
        override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>) {}
        override fun onAddStream(stream: MediaStream) {}
        override fun onRemoveStream(stream: MediaStream) {}
        override fun onDataChannel(channel: DataChannel) {}
        override fun onRenegotiationNeeded() {}
    }
}

private abstract class SdpObserverAdapter : SdpObserver {
    override fun onCreateSuccess(desc: SessionDescription) {}
    override fun onSetSuccess() {}
    override fun onCreateFailure(error: String?) {}
    override fun onSetFailure(error: String?) {}
}
