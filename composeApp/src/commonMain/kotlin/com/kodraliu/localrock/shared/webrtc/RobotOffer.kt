package com.kodraliu.localrock.shared.webrtc

/**
 * Reshapes a stock WebRTC offer into the one the official Roborock app sends.
 *
 * A robot is strict about the offer: it accepts `send_sdp_to_robot` for a stock libwebrtc offer and then answers
 * `get_device_sdp` with `{"dev_sdp":"retry"}` for ever. What it answers is an offer with
 * - audio first, `sendrecv`, Opus and telephone-event only;
 * - video `recvonly`, H.264 with packetization-mode=1 only, plus rtx, red and ulpfec;
 * - no `a=extmap-allow-mixed`.
 * The audio-first order and the directions come from how the peer's transceivers are added; this function removes
 * the codecs and the attribute. A section it cannot make sense of is returned unchanged.
 */
fun shapeOfferForRobot(sdp: String): String {
    val eol = if (sdp.contains("\r\n")) "\r\n" else "\n"
    val lines = sdp.split(Regex("\r?\n")).filter { it.isNotEmpty() }
    val sections = mutableListOf(mutableListOf<String>())
    for (line in lines) {
        if (line.startsWith("m=")) sections.add(mutableListOf())
        sections.last().add(line)
    }
    val shaped = sections.mapIndexed { index, section ->
        when {
            index == 0 -> section.filterNot { it.trim() == "a=extmap-allow-mixed" }
            section.first().startsWith("m=audio") -> keepPayloads(section) { audioKeep(it) }
            section.first().startsWith("m=video") -> keepPayloads(section) { videoKeep(it) }
            else -> section
        }
    }
    return shaped.flatten().joinToString(eol) + eol
}

private class Payload(val type: String, val name: String, val fmtp: String)

private fun payloads(section: List<String>): List<Payload> {
    val mline = section.first().split(" ")
    if (mline.size < 4) return emptyList()
    val fmtp = section.filter { it.startsWith("a=fmtp:") }.associate {
        val rest = it.removePrefix("a=fmtp:")
        rest.substringBefore(' ') to rest.substringAfter(' ', "")
    }
    val names = section.filter { it.startsWith("a=rtpmap:") }.associate {
        val rest = it.removePrefix("a=rtpmap:")
        rest.substringBefore(' ') to rest.substringAfter(' ', "").substringBefore('/')
    }
    return mline.drop(3).map { Payload(it, names[it].orEmpty(), fmtp[it].orEmpty()) }
}

private fun audioKeep(all: List<Payload>): Set<String> =
    all.filter { it.name.equals("opus", true) || it.name.equals("telephone-event", true) }.map { it.type }.toSet()

private fun videoKeep(all: List<Payload>): Set<String> {
    val h264 = all.filter { it.name.equals("H264", true) && "packetization-mode=1" in it.fmtp }.map { it.type }.toSet()
    if (h264.isEmpty()) return emptySet()
    val base = h264 + all.filter { it.name.equals("red", true) || it.name.equals("ulpfec", true) }.map { it.type }
    val rtx = all.filter { it.name.equals("rtx", true) && aptOf(it.fmtp) in base }.map { it.type }
    return base + rtx
}

private fun aptOf(fmtp: String): String? =
    fmtp.split(';').map { it.trim() }.firstOrNull { it.startsWith("apt=") }?.removePrefix("apt=")

private fun keepPayloads(section: MutableList<String>, choose: (List<Payload>) -> Set<String>): MutableList<String> {
    val all = payloads(section)
    val keep = choose(all)
    if (keep.isEmpty() || keep.size == all.size) return section
    val mline = section.first().split(" ")
    val header = mline.take(3) + all.map { it.type }.filter { it in keep }
    val result = mutableListOf(header.joinToString(" "))
    for (line in section.drop(1)) {
        val pt = Regex("^a=(?:rtpmap|fmtp|rtcp-fb):(\\d+)").find(line)?.groupValues?.get(1)
        if (pt != null && pt !in keep) continue
        result.add(line)
    }
    return result
}
