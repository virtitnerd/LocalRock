package com.kodraliu.localrock.shared.webrtc

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

data class RtcIceServer(
    val url: String,
    val username: String = "",
    val password: String = "",
)

data class RtcIceCandidate(
    val sdpMid: String?,
    val sdpMLineIndex: Int,
    val candidate: String,
)

/** A snapshot of how the video is arriving: [relayed] is true when it goes through the TURN server. */
data class RtcStats(
    val relayed: Boolean,
    val rttMs: Int?,
    val width: Int?,
    val height: Int?,
    val fps: Int?,
    val bufferMs: Int?,
)

enum class RtcConnectionState { NEW, CONNECTING, CONNECTED, DISCONNECTED, FAILED, CLOSED }


interface RtcPeer {
    val localCandidates: SharedFlow<RtcIceCandidate>
    val connectionState: StateFlow<RtcConnectionState>
    val stats: StateFlow<RtcStats?> get() = MutableStateFlow(null)

    suspend fun createOffer(): String
    suspend fun setRemoteAnswer(sdp: String)
    fun addRemoteCandidate(candidate: RtcIceCandidate)
    fun close()
}

expect val liveViewSupported: Boolean

expect fun createRtcPeer(iceServers: List<RtcIceServer>): RtcPeer
