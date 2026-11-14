package com.kodraliu.localrock.shared.webrtc

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RobotOfferTest {
    private val stock = listOf(
        "v=0",
        "o=- 4611731400430051336 2 IN IP4 127.0.0.1",
        "s=-",
        "t=0 0",
        "a=group:BUNDLE 0 1",
        "a=extmap-allow-mixed",
        "m=audio 9 UDP/TLS/RTP/SAVPF 111 63 9 0 8 13 110 126",
        "c=IN IP4 0.0.0.0",
        "a=mid:0",
        "a=sendrecv",
        "a=rtcp-fb:111 transport-cc",
        "a=fmtp:111 minptime=10;useinbandfec=1",
        "a=rtpmap:111 opus/48000/2",
        "a=rtpmap:63 red/48000/2",
        "a=rtpmap:9 G722/8000",
        "a=rtpmap:0 PCMU/8000",
        "a=rtpmap:8 PCMA/8000",
        "a=rtpmap:13 CN/8000",
        "a=rtpmap:110 telephone-event/48000",
        "a=rtpmap:126 telephone-event/8000",
        "m=video 9 UDP/TLS/RTP/SAVPF 96 97 102 103 104 105 106 107 127 125 39 40 45 46",
        "c=IN IP4 0.0.0.0",
        "a=mid:1",
        "a=recvonly",
        "a=rtcp-fb:96 nack",
        "a=rtcp-fb:102 nack",
        "a=rtpmap:96 VP8/90000",
        "a=rtpmap:97 rtx/90000",
        "a=fmtp:97 apt=96",
        "a=rtpmap:102 H264/90000",
        "a=fmtp:102 level-asymmetry-allowed=1;packetization-mode=1;profile-level-id=42001f",
        "a=rtpmap:103 rtx/90000",
        "a=fmtp:103 apt=102",
        "a=rtpmap:104 H264/90000",
        "a=fmtp:104 level-asymmetry-allowed=1;packetization-mode=0;profile-level-id=42001f",
        "a=rtpmap:105 rtx/90000",
        "a=fmtp:105 apt=104",
        "a=rtpmap:106 H264/90000",
        "a=fmtp:106 level-asymmetry-allowed=1;packetization-mode=1;profile-level-id=42e01f",
        "a=rtpmap:107 rtx/90000",
        "a=fmtp:107 apt=106",
        "a=rtpmap:127 red/90000",
        "a=rtpmap:125 rtx/90000",
        "a=fmtp:125 apt=127",
        "a=rtpmap:39 AV1/90000",
        "a=rtpmap:40 rtx/90000",
        "a=fmtp:40 apt=39",
        "a=rtpmap:45 ulpfec/90000",
        "a=rtpmap:46 flexfec-03/90000",
    ).joinToString("\r\n") + "\r\n"

    @Test
    fun audioKeepsOnlyOpusAndTelephoneEvent() {
        val out = shapeOfferForRobot(stock)
        assertTrue("m=audio 9 UDP/TLS/RTP/SAVPF 111 110 126\r\n" in out)
        assertFalse("G722" in out || "PCMU" in out || "PCMA" in out || "CN/8000" in out || "red/48000" in out)
    }

    @Test
    fun videoKeepsOnlyH264PacketizationModeOneWithItsRtxRedAndUlpfec() {
        val out = shapeOfferForRobot(stock)
        assertTrue("m=video 9 UDP/TLS/RTP/SAVPF 102 103 106 107 127 125 45\r\n" in out)
        assertFalse("VP8" in out || "AV1" in out || "flexfec" in out || "packetization-mode=0" in out)
        assertTrue("a=fmtp:103 apt=102" in out && "a=fmtp:125 apt=127" in out)
        assertFalse("a=rtcp-fb:96" in out)
    }

    @Test
    fun theMixedExtmapAttributeGoesAndEverythingElseStays() {
        val out = shapeOfferForRobot(stock)
        assertFalse("extmap-allow-mixed" in out)
        assertTrue("a=group:BUNDLE 0 1\r\n" in out && "a=mid:1\r\n" in out && "a=recvonly\r\n" in out && "a=sendrecv\r\n" in out)
        assertTrue(out.startsWith("v=0\r\n") && out.endsWith("\r\n"))
    }

    @Test
    fun aSectionWithoutH264IsLeftAlone() {
        val noH264 = "v=0\r\ns=-\r\nm=video 9 UDP/TLS/RTP/SAVPF 96 97\r\na=rtpmap:96 VP8/90000\r\na=rtpmap:97 rtx/90000\r\na=fmtp:97 apt=96\r\n"
        assertEquals(noH264, shapeOfferForRobot(noH264))
    }

    @Test
    fun runningItTwiceChangesNothingMore() {
        val once = shapeOfferForRobot(stock)
        assertEquals(once, shapeOfferForRobot(once))
    }
}
