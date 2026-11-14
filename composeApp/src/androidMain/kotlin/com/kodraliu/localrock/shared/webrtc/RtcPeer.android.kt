package com.kodraliu.localrock.shared.webrtc

import com.kodraliu.localrock.shared.protocol.appContextOrNull
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

actual fun createRtcPeer(iceServers: List<RtcIceServer>): RtcPeer = AndroidRtcPeer(iceServers)

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

internal class AndroidRtcPeer(iceServers: List<RtcIceServer>) : RtcPeer {

    private val _localCandidates = MutableSharedFlow<RtcIceCandidate>(replay = 32, extraBufferCapacity = 32)
    override val localCandidates: SharedFlow<RtcIceCandidate> = _localCandidates

    private val _connectionState = MutableStateFlow(RtcConnectionState.NEW)
    override val connectionState: StateFlow<RtcConnectionState> = _connectionState

    val videoTrack = MutableStateFlow<VideoTrack?>(null)

    private val _stats = MutableStateFlow<RtcStats?>(null)
    override val stats: StateFlow<RtcStats?> = _stats
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

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
        Log.i("RtcStats", "$snapshot local=${str(local, "candidateType")} remote=${str(remote, "candidateType")}")
        _stats.value = snapshot
    }

    override suspend fun createOffer(): String {
        // The robot only answers the official app's offer: audio first and sendrecv (a transceiver without a
        // track is enough), then receive-only video. The codecs are trimmed below.
        pc.addTransceiver(
            MediaStreamTrack.MediaType.MEDIA_TYPE_AUDIO,
            RtpTransceiver.RtpTransceiverInit(RtpTransceiver.RtpTransceiverDirection.SEND_RECV),
        )
        pc.addTransceiver(
            MediaStreamTrack.MediaType.MEDIA_TYPE_VIDEO,
            RtpTransceiver.RtpTransceiverInit(RtpTransceiver.RtpTransceiverDirection.RECV_ONLY),
        )
        val created = suspendCoroutine<SessionDescription> { cont ->
            pc.createOffer(object : SdpObserverAdapter() {
                override fun onCreateSuccess(desc: SessionDescription) = cont.resume(desc)
                override fun onCreateFailure(error: String?) =
                    cont.resumeWithException(RuntimeException("createOffer failed: $error"))
            }, MediaConstraints())
        }
        val offer = SessionDescription(created.type, shapeOfferForRobot(created.description))
        suspendCoroutine<Unit> { cont ->
            pc.setLocalDescription(object : SdpObserverAdapter() {
                override fun onSetSuccess() = cont.resume(Unit)
                override fun onSetFailure(error: String?) =
                    cont.resumeWithException(RuntimeException("setLocalDescription failed: $error"))
            }, offer)
        }
        return offer.description
    }

    override suspend fun setRemoteAnswer(sdp: String) {
        suspendCoroutine<Unit> { cont ->
            pc.setRemoteDescription(object : SdpObserverAdapter() {
                override fun onSetSuccess() = cont.resume(Unit)
                override fun onSetFailure(error: String?) =
                    cont.resumeWithException(RuntimeException("setRemoteDescription failed: $error"))
            }, SessionDescription(SessionDescription.Type.ANSWER, sdp))
        }
    }

    override fun addRemoteCandidate(candidate: RtcIceCandidate) {
        pc.addIceCandidate(IceCandidate(candidate.sdpMid ?: "0", candidate.sdpMLineIndex, candidate.candidate))
    }

    override fun close() {
        scope.cancel()
        videoTrack.value = null
        _connectionState.value = RtcConnectionState.CLOSED
        runCatching { pc.close() }
        runCatching { pc.dispose() }
    }

    private inner class Observer : PeerConnection.Observer {
        override fun onIceCandidate(candidate: IceCandidate) {
            _localCandidates.tryEmit(
                RtcIceCandidate(candidate.sdpMid, candidate.sdpMLineIndex, candidate.sdp)
            )
        }

        override fun onTrack(transceiver: RtpTransceiver) {
            (transceiver.receiver.track() as? VideoTrack)?.let { videoTrack.value = it }
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
                PeerConnection.PeerConnectionState.CONNECTED -> RtcConnectionState.CONNECTED
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
