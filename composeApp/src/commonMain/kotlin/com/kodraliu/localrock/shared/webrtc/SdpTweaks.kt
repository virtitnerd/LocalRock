package com.kodraliu.localrock.shared.webrtc

enum class VideoDirection(val attribute: String) { RECV_ONLY("recvonly"), SEND_RECV("sendrecv") }

enum class CodecOrder { CURRENT, OFFICIAL_H264, VP8_FIRST, BASELINE_ONLY }

/** Debug switches that change what the live view offers the robot; the defaults are the shipped behaviour. */
data class VideoExperiments(
    val direction: VideoDirection = VideoDirection.RECV_ONLY,
    val codecOrder: CodecOrder = CodecOrder.CURRENT,
    val keepVp8Vp9: Boolean = false,
    val bandwidthKbps: Int = 0,
    val googleBitrateKbps: Int = 0,
    val imageAttr: Boolean = false,
    val dumpSdp: Boolean = false,
) {
    val isDefault: Boolean get() = this == VideoExperiments()

    fun encode(): String = listOf(
        "dir=${direction.name}", "order=${codecOrder.name}", "keep=$keepVp8Vp9", "bw=$bandwidthKbps",
        "goog=$googleBitrateKbps", "attr=$imageAttr", "dump=$dumpSdp",
    ).joinToString(";")

    companion object {
        fun decode(text: String?): VideoExperiments {
            if (text.isNullOrBlank()) return VideoExperiments()
            val map = text.split(';').mapNotNull { p -> p.split('=', limit = 2).takeIf { it.size == 2 }?.let { it[0] to it[1] } }.toMap()
            val base = VideoExperiments()
            return VideoExperiments(
                direction = VideoDirection.entries.firstOrNull { it.name == map["dir"] } ?: base.direction,
                codecOrder = CodecOrder.entries.firstOrNull { it.name == map["order"] } ?: base.codecOrder,
                keepVp8Vp9 = map["keep"] == "true",
                bandwidthKbps = map["bw"]?.toIntOrNull()?.coerceAtLeast(0) ?: 0,
                googleBitrateKbps = map["goog"]?.toIntOrNull()?.coerceAtLeast(0) ?: 0,
                imageAttr = map["attr"] == "true",
                dumpSdp = map["dump"] == "true",
            )
        }
    }
}

/** The offer sent to the robot: the shaped stock offer with the [experiments] applied on top. */
fun buildRobotOffer(stock: String, experiments: VideoExperiments): String {
    val keep = experiments.keepVp8Vp9 || experiments.codecOrder == CodecOrder.VP8_FIRST
    var sdp = shapeOfferForRobot(stock, keepVp8Vp9 = keep)
    sdp = reorderVideoCodecs(sdp, experiments.codecOrder)
    sdp = setVideoDirection(sdp, experiments.direction)
    sdp = addVideoBandwidth(sdp, experiments.bandwidthKbps)
    sdp = addGoogleBitrate(sdp, experiments.googleBitrateKbps)
    if (experiments.imageAttr) sdp = addImageAttr(sdp, IMAGE_ATTR_SIZES)
    return sdp
}

val IMAGE_ATTR_SIZES = listOf(1280 to 720, 1920 to 1080)

val OFFICIAL_H264_PROFILES = listOf("640c29", "42e029")
private const val BASELINE_PROFILE = "42e01f"

private class Sdp(val eol: String, val sections: List<MutableList<String>>)

private fun parse(sdp: String): Sdp {
    val eol = if (sdp.contains("\r\n")) "\r\n" else "\n"
    val sections = mutableListOf(mutableListOf<String>())
    for (line in sdp.split(Regex("\r?\n")).filter { it.isNotEmpty() }) {
        if (line.startsWith("m=")) sections.add(mutableListOf())
        sections.last().add(line)
    }
    return Sdp(eol, sections)
}

private fun Sdp.mapVideo(change: (MutableList<String>) -> List<String>): String {
    val out = sections.map { if (it.firstOrNull()?.startsWith("m=video") == true) change(it) else it }
    return out.flatten().joinToString(eol) + eol
}

private val PT_LINE = Regex("^a=(rtpmap|fmtp|rtcp-fb):(\\d+)( .*)?$")

private class Block(val pt: String, val name: String, val lines: List<String>) {
    val fmtp: String get() = lines.firstOrNull { it.startsWith("a=fmtp:") }?.substringAfter(' ', "").orEmpty()
    val apt: String? get() = fmtp.split(';').map { it.trim() }.firstOrNull { it.startsWith("apt=") }?.removePrefix("apt=")
    val profile: String?
        get() = fmtp.split(';').map { it.trim() }.firstOrNull { it.startsWith("profile-level-id=") }?.removePrefix("profile-level-id=")?.lowercase()
    val isH264 get() = name.equals("H264", true)
    val isRtx get() = name.equals("rtx", true)
}

private class VideoParts(val header: List<String>, val blocks: List<Block>)

private fun splitVideo(section: List<String>): VideoParts {
    val pts = section.first().split(" ").drop(3)
    val header = mutableListOf(section.first())
    val byPt = pts.associateWith { mutableListOf<String>() }
    for (line in section.drop(1)) {
        val pt = PT_LINE.find(line)?.groupValues?.get(2)
        if (pt != null && pt in byPt) byPt.getValue(pt).add(line) else header.add(line)
    }
    val blocks = pts.map { pt ->
        val lines = byPt.getValue(pt)
        val name = lines.firstOrNull { it.startsWith("a=rtpmap:") }?.substringAfter(' ', "")?.substringBefore('/').orEmpty()
        Block(pt, name, lines)
    }
    return VideoParts(header, blocks)
}

private fun joinVideo(parts: VideoParts, order: List<Block>): List<String> {
    val mline = parts.header.first().split(" ").take(3) + order.map { it.pt }
    return listOf(mline.joinToString(" ")) + parts.header.drop(1) + order.flatMap { it.lines }
}

/** Rewrites the video section's direction attribute, adding it when missing. */
fun setVideoDirection(sdp: String, direction: VideoDirection): String = parse(sdp).mapVideo { section ->
    val dirs = setOf("a=sendrecv", "a=recvonly", "a=sendonly", "a=inactive")
    val line = "a=${direction.attribute}"
    val l = section.toMutableList()
    val at = l.indexOfFirst { it.trim() in dirs }
    if (at >= 0) l[at] = line else l.add((l.indexOfFirst { it.startsWith("a=mid:") } + 1).coerceAtLeast(1), line)
    l
}

private fun usedPayloadTypes(doc: Sdp): Set<Int> =
    doc.sections.flatMap { it.first().split(" ").drop(3) }.mapNotNull { it.toIntOrNull() }.toSet()

private fun clone(block: Block, pt: String, profile: String?, apt: String? = null): Block {
    val lines = block.lines.map { line ->
        val m = PT_LINE.find(line)!!
        var out = "a=${m.groupValues[1]}:$pt${m.groupValues[3]}"
        if (m.groupValues[1] == "fmtp") {
            if (profile != null) out = out.replace(Regex("profile-level-id=[0-9a-fA-F]{6}"), "profile-level-id=$profile")
            if (apt != null) out = out.replace(Regex("apt=\\d+"), "apt=$apt")
        }
        out
    }
    return Block(pt, block.name, lines)
}

/**
 * Reorders the video codecs keeping each rtx next to its codec with a consistent apt. A wanted H.264 profile that the
 * offer lacks is added as a copy of an existing H.264 entry (and its rtx) under an unused payload type.
 */
fun reorderVideoCodecs(sdp: String, order: CodecOrder): String {
    if (order == CodecOrder.CURRENT) return sdp
    val doc = parse(sdp)
    val taken = usedPayloadTypes(doc).toMutableSet()
    return doc.mapVideo { section ->
        val parts = splitVideo(section)
        val rtxOf = parts.blocks.filter { it.isRtx }.associateBy { it.apt }
        val codecs = parts.blocks.filter { !it.isRtx }.toMutableList()
        val extraRtx = mutableMapOf<String, Block>()
        fun freePt(): Int? = (96..127).firstOrNull { it !in taken }?.also { taken.add(it) }
        fun h264(profile: String) = codecs.firstOrNull { it.isH264 && it.profile == profile && "packetization-mode=1" in it.fmtp }
        fun ensureProfile(profile: String) {
            if (h264(profile) != null) return
            val source = codecs.firstOrNull { it.isH264 && "packetization-mode=1" in it.fmtp } ?: return
            val pt = freePt() ?: return
            val copy = clone(source, pt.toString(), profile)
            codecs.add(copy)
            val srcRtx = rtxOf[source.pt] ?: return
            val rtxPt = freePt() ?: return
            extraRtx[copy.pt] = clone(srcRtx, rtxPt.toString(), null, apt = copy.pt)
        }
        fun withRtx(c: Block) = listOfNotNull(c, rtxOf[c.pt] ?: extraRtx[c.pt])
        val ordered: List<Block> = when (order) {
            CodecOrder.OFFICIAL_H264 -> {
                if (codecs.none { it.isH264 }) return@mapVideo section
                OFFICIAL_H264_PROFILES.forEach { ensureProfile(it) }
                val first = OFFICIAL_H264_PROFILES.mapNotNull { h264(it) }
                (first + codecs.filter { it !in first }).flatMap { withRtx(it) }
            }
            CodecOrder.VP8_FIRST -> {
                val vp8 = codecs.filter { it.name.equals("VP8", true) }
                if (vp8.isEmpty()) return@mapVideo section
                (vp8 + codecs.filter { it !in vp8 }).flatMap { withRtx(it) }
            }
            CodecOrder.BASELINE_ONLY -> {
                if (codecs.none { it.isH264 }) return@mapVideo section
                ensureProfile(BASELINE_PROFILE)
                val support = codecs.filter { it.name.equals("red", true) || it.name.equals("ulpfec", true) }
                (listOf(h264(BASELINE_PROFILE)!!) + support).flatMap { withRtx(it) }
            }
            CodecOrder.CURRENT -> parts.blocks
        }
        joinVideo(parts, ordered)
    }
}

/** Puts `b=AS:<kbps>` on the video section only (after its c= line), replacing an existing one. No-op for 0. */
fun addVideoBandwidth(sdp: String, kbps: Int): String {
    if (kbps <= 0) return sdp
    return parse(sdp).mapVideo { section ->
        val l = section.filterNot { it.startsWith("b=AS:") }.toMutableList()
        l.add(l.indexOfFirst { it.startsWith("c=") }.let { if (it >= 0) it + 1 else 1 }, "b=AS:$kbps")
        l
    }
}

private val HINT_CODECS = setOf("h264", "vp8", "vp9")

/** Appends x-google-start-bitrate and x-google-min-bitrate (half the start) to each H264, VP8 and VP9 fmtp. */
fun addGoogleBitrate(sdp: String, startKbps: Int): String {
    if (startKbps <= 0) return sdp
    val hint = "x-google-start-bitrate=$startKbps;x-google-min-bitrate=${startKbps / 2}"
    return parse(sdp).mapVideo { section ->
        val parts = splitVideo(section)
        val blocks = parts.blocks.map { b ->
            if (b.name.lowercase() !in HINT_CODECS) return@map b
            val lines = b.lines.toMutableList()
            val at = lines.indexOfFirst { it.startsWith("a=fmtp:") }
            if (at >= 0) {
                if ("x-google-start-bitrate" !in lines[at]) lines[at] = lines[at] + ";" + hint
            } else {
                lines.add((lines.indexOfFirst { it.startsWith("a=rtpmap:") } + 1).coerceAtLeast(0), "a=fmtp:${b.pt} $hint")
            }
            Block(b.pt, b.name, lines)
        }
        joinVideo(parts, blocks)
    }
}

/** Adds `a=imageattr:<pt> recv [x=W,y=H] ...` for every H264, VP8 and VP9 payload of the video section. */
fun addImageAttr(sdp: String, sizes: List<Pair<Int, Int>>): String {
    if (sizes.isEmpty()) return sdp
    val sets = sizes.joinToString(" ") { "[x=${it.first},y=${it.second}]" }
    return parse(sdp).mapVideo { section ->
        val parts = splitVideo(section)
        val extra = parts.blocks.filter { it.name.lowercase() in HINT_CODECS }.map { "a=imageattr:${it.pt} recv $sets" }
        joinVideo(parts, parts.blocks) + extra
    }
}

private val IPV4 = Regex("\\b\\d{1,3}(\\.\\d{1,3}){3}\\b")
private val IPV6 = Regex("(?<![0-9A-Za-z])(?:[0-9A-Fa-f]{1,4}:){2,7}[0-9A-Fa-f]{0,4}(?![0-9A-Za-z])|(?<![0-9A-Za-z:])::[0-9A-Fa-f:]*")
private val MDNS = Regex("[0-9a-fA-F-]{36}\\.local")

/** The SDP with ICE credentials, the DTLS fingerprint and every IP address replaced by placeholders. */
fun redactSdp(sdp: String): String = sdp.split(Regex("\r?\n")).filter { it.isNotEmpty() }.joinToString("\n") { line ->
    when {
        line.startsWith("a=ice-ufrag:") -> "a=ice-ufrag:<ufrag>"
        line.startsWith("a=ice-pwd:") -> "a=ice-pwd:<pwd>"
        line.startsWith("a=fingerprint:") -> "a=fingerprint:<alg> <fingerprint>"
        else -> line
            .replace(Regex("ufrag \\S+"), "ufrag <ufrag>")
            .replace(MDNS, "<host>")
            .replace(IPV4, "<ip>")
            .replace(IPV6, "<ip6>")
    }
}
