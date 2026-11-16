package com.kodraliu.localrock.shared.mqtt

/** True for a LAN, loopback, link-local or Tailscale-style address, given its raw 4 or 16 bytes. */
fun isPrivateNetworkAddress(bytes: ByteArray): Boolean {
    fun b(i: Int) = bytes[i].toInt() and 0xFF
    return when (bytes.size) {
        4 -> b(0) == 10 ||
            b(0) == 127 ||
            (b(0) == 172 && b(1) in 16..31) ||
            (b(0) == 192 && b(1) == 168) ||
            (b(0) == 169 && b(1) == 254) ||
            (b(0) == 100 && b(1) in 64..127)
        16 -> (b(0) and 0xFE) == 0xFC ||
            (b(0) == 0xFE && (b(1) and 0xC0) == 0x80) ||
            bytes.take(15).all { it.toInt() == 0 } && b(15) == 1
        else -> false
    }
}
