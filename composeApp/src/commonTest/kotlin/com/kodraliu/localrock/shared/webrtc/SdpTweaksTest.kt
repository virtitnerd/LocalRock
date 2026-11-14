package com.kodraliu.localrock.shared.webrtc

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SdpTweaksTest {
    private val stock = listOf(
        "v=0",
        "o=- 4611731400430051336 2 IN IP4 127.0.0.1",
        "s=-",
        "t=0 0",
        "a=group:BUNDLE 0 1",
        "a=extmap-allow-mixed",
        "m=audio 9 UDP/TLS/RTP/SAVPF 111 110",
        "c=IN IP4 0.0.0.0",
        "a=ice-ufrag:abcd",
        "a=ice-pwd:secretsecretsecret",
        "a=fingerprint:sha-256 AA:BB:CC",
        "a=mid:0",
        "a=sendrecv",
        "a=rtpmap:111 opus/48000/2",
        "a=rtpmap:110 telephone-event/48000",
        "m=video 9 UDP/TLS/RTP/SAVPF 96 97 102 103 104 105 106 107 127 125 39 40 45",
        "c=IN IP4 0.0.0.0",
        "a=mid:1",
        "a=recvonly",
        "a=rtcp-fb:96 nack",
        "a=rtpmap:96 VP8/90000",
        "a=rtpmap:97 rtx/90000",
        "a=fmtp:97 apt=96",
        "a=rtpmap:102 H264/90000",
        "a=rtcp-fb:102 nack",
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
    ).joinToString("\r\n") + "\r\n"

    private fun lines(sdp: String) = sdp.split("\r\n").filter { it.isNotEmpty() }
    private fun mline(sdp: String) = lines(sdp).first { it.startsWith("m=video") }.split(" ").drop(3)
    private fun videoLines(sdp: String) = lines(sdp).dropWhile { !it.startsWith("m=video") }
    private fun fmtpOf(sdp: String, pt: String) = videoLines(sdp).first { it.startsWith("a=fmtp:$pt ") }
    private fun nameOf(sdp: String, pt: String) =
        videoLines(sdp).first { it.startsWith("a=rtpmap:$pt ") }.substringAfter(' ').substringBefore('/')

    // Every listed payload has an rtpmap, no line refers to an unlisted payload and every rtx points at a listed one.
    private fun assertConsistent(sdp: String) {
        val pts = mline(sdp)
        val v = videoLines(sdp)
        for (pt in pts) assertTrue(v.any { it.startsWith("a=rtpmap:$pt ") }, "rtpmap for $pt")
        for (l in v.filter { it.startsWith("a=rtpmap:") || it.startsWith("a=fmtp:") }) {
            assertTrue(l.substringAfter(':').substringBefore(' ') in pts, "line for unlisted payload: $l")
        }
        for (l in v.filter { it.startsWith("a=fmtp:") && "apt=" in it }) {
            assertTrue(l.substringAfter("apt=") in pts, "dangling apt: $l")
        }
        assertEquals(pts.size, pts.toSet().size)
    }

    private val shaped get() = shapeOfferForRobot(stock)
    private val shapedKeep get() = shapeOfferForRobot(stock, keepVp8Vp9 = true)

    @Test
    fun defaultExperimentsLeaveTheOfferUnchanged() {
        assertEquals(shapeOfferForRobot(stock), buildRobotOffer(stock, VideoExperiments()))
        assertTrue(VideoExperiments().isDefault)
    }

    @Test
    fun keepVp8StripsOnlyAv1AndNonPacketizationModeOne() {
        assertFalse("a=rtpmap:96 VP8/90000" in shaped)
        val kept = shapedKeep
        assertTrue("a=rtpmap:96 VP8/90000" in kept && "a=fmtp:97 apt=96" in kept)
        assertFalse("AV1" in kept)
        assertFalse("packetization-mode=0" in kept)
        assertConsistent(kept)
    }

    @Test
    fun directionIsRewrittenOnVideoOnly() {
        val out = setVideoDirection(shaped, VideoDirection.SEND_RECV)
        val v = videoLines(out)
        assertTrue("a=sendrecv" in v && "a=recvonly" !in v)
        assertTrue(lines(out).takeWhile { !it.startsWith("m=video") }.contains("a=sendrecv"))
        assertEquals(shaped, setVideoDirection(shaped, VideoDirection.RECV_ONLY))
    }

    @Test
    fun directionIsAddedWhenMissing() {
        val without = shaped.replace("a=recvonly\r\n", "")
        val v = videoLines(setVideoDirection(without, VideoDirection.SEND_RECV))
        assertEquals("a=sendrecv", v[v.indexOf("a=mid:1") + 1])
    }

    @Test
    fun officialOrderPutsHighThenExtendedBaselineFirstEachWithItsOwnRtx() {
        val out = reorderVideoCodecs(shaped, CodecOrder.OFFICIAL_H264)
        assertConsistent(out)
        val pts = mline(out)
        assertTrue(fmtpOf(out, pts[0]).endsWith("profile-level-id=640c29"))
        assertEquals("rtx", nameOf(out, pts[1]))
        assertTrue(fmtpOf(out, pts[1]).endsWith("apt=${pts[0]}"))
        assertTrue(fmtpOf(out, pts[2]).endsWith("profile-level-id=42e029"))
        assertTrue(fmtpOf(out, pts[3]).endsWith("apt=${pts[2]}"))
        assertTrue("profile-level-id=42e01f" in out && "profile-level-id=42001f" in out)
        assertTrue(videoLines(out).any { it == "a=rtcp-fb:${pts[0]} nack" })
    }

    @Test
    fun officialOrderAddsFourFreshPayloadTypes() {
        val out = reorderVideoCodecs(shaped, CodecOrder.OFFICIAL_H264)
        val added = mline(out).filter { it !in mline(shaped) }
        assertEquals(4, added.size)
        assertTrue(added.all { it.toInt() in 96..127 })
    }

    @Test
    fun officialOrderReusesAProfileTheOfferAlreadyHas() {
        val withHigh = shaped.replace("profile-level-id=42e01f", "profile-level-id=640c29")
        val out = reorderVideoCodecs(withHigh, CodecOrder.OFFICIAL_H264)
        assertEquals("106", mline(out)[0])
        assertEquals(2, mline(out).size - mline(withHigh).size)
        assertConsistent(out)
    }

    @Test
    fun vp8FirstKeepsRtxPairsAndNeedsVp8Present() {
        val out = reorderVideoCodecs(shapedKeep, CodecOrder.VP8_FIRST)
        assertConsistent(out)
        assertEquals(listOf("96", "97", "102"), mline(out).take(3))
        assertEquals(shaped, reorderVideoCodecs(shaped, CodecOrder.VP8_FIRST))
        assertEquals(shapedKeep, reorderVideoCodecs(shapedKeep, CodecOrder.CURRENT))
    }

    @Test
    fun baselineOnlyKeepsOneH264PlusRedAndUlpfec() {
        val out = reorderVideoCodecs(shapedKeep, CodecOrder.BASELINE_ONLY)
        assertConsistent(out)
        assertEquals(listOf("106", "107", "127", "125", "45"), mline(out))
        assertFalse("VP8" in out || "42001f" in out)
    }

    @Test
    fun bandwidthGoesOnVideoSectionOnly() {
        val out = addVideoBandwidth(shaped, 4000)
        assertEquals(1, lines(out).count { it == "b=AS:4000" })
        val v = videoLines(out)
        assertEquals("b=AS:4000", v[v.indexOf("c=IN IP4 0.0.0.0") + 1])
        assertFalse("b=AS:4000" in lines(out).takeWhile { !it.startsWith("m=video") })
        assertEquals(shaped, addVideoBandwidth(shaped, 0))
        assertEquals(1, lines(addVideoBandwidth(out, 8000)).count { it.startsWith("b=AS:") })
    }

    @Test
    fun googleBitrateGoesOnCodecFmtpOnly() {
        val out = addGoogleBitrate(shapedKeep, 2000)
        assertTrue(fmtpOf(out, "102").endsWith("profile-level-id=42001f;x-google-start-bitrate=2000;x-google-min-bitrate=1000"))
        assertTrue("a=fmtp:96 x-google-start-bitrate=2000;x-google-min-bitrate=1000" in lines(out))
        assertFalse("x-google" in fmtpOf(out, "103"))
        assertEquals(out, addGoogleBitrate(out, 2000))
        assertConsistent(out)
    }

    @Test
    fun imageAttrIsAddedForEachH264AndVp8Payload() {
        val out = addImageAttr(shapedKeep, IMAGE_ATTR_SIZES)
        val attrs = lines(out).filter { it.startsWith("a=imageattr:") }
        assertEquals(listOf("96", "102", "106"), attrs.map { it.substringAfter(':').substringBefore(' ') })
        assertTrue(attrs.all { it.endsWith("recv [x=1280,y=720] [x=1920,y=1080]") })
        assertEquals(mline(shapedKeep), mline(out))
    }

    @Test
    fun fullPipelineStaysConsistent() {
        val e = VideoExperiments(VideoDirection.SEND_RECV, CodecOrder.OFFICIAL_H264, true, 4000, 2000, true, false)
        val out = buildRobotOffer(stock, e)
        assertConsistent(out)
        assertTrue("a=sendrecv" in videoLines(out) && "b=AS:4000" in out && "a=imageattr:" in out)
    }

    @Test
    fun redactionHidesCredentialsFingerprintAndAddresses() {
        val raw = listOf(
            "o=- 1 2 IN IP4 192.168.1.20",
            "c=IN IP4 10.0.0.5",
            "a=ice-ufrag:abcd",
            "a=ice-pwd:secret",
            "a=fingerprint:sha-256 AA:BB:CC:DD",
            "a=candidate:1 1 udp 2113937151 192.168.1.20 54321 typ host generation 0 ufrag abcd",
            "a=candidate:2 1 udp 1 2001:db8::1 9 typ host",
            "a=candidate:3 1 udp 1 5f1e2d3c-1111-2222-3333-444455556666.local 9 typ host",
            "a=rtpmap:111 opus/48000/2",
            "a=extmap:3 http://www.webrtc.org/experiments/rtp-hdrext/abs-send-time",
        ).joinToString("\r\n") + "\r\n"
        val out = redactSdp(raw)
        assertFalse("192.168" in out || "10.0.0.5" in out || "abcd" in out || "secret" in out || "AA:BB" in out || "2001" in out || ".local" in out)
        assertTrue("a=ice-ufrag:<ufrag>" in out && "a=ice-pwd:<pwd>" in out && "<ip>" in out && "<ip6>" in out)
        assertTrue("a=rtpmap:111 opus/48000/2" in out && "http://www.webrtc.org/experiments/rtp-hdrext/abs-send-time" in out)
    }

    @Test
    fun experimentsRoundTripThroughSettingsText() {
        val e = VideoExperiments(VideoDirection.SEND_RECV, CodecOrder.OFFICIAL_H264, true, 4000, 2000, true, true)
        assertEquals(e, VideoExperiments.decode(e.encode()))
        assertEquals(VideoExperiments(), VideoExperiments.decode(null))
        assertEquals(VideoExperiments(), VideoExperiments.decode("garbage"))
    }
}
