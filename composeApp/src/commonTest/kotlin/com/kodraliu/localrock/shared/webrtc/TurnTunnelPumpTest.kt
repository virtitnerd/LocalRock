package com.kodraliu.localrock.shared.webrtc

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class TurnTunnelPumpTest {
    @Test
    fun bytes_flow_both_ways_and_a_closed_socket_ends_the_pump() = runTest {
        val fromPhone = Channel<ByteArray>(Channel.UNLIMITED)
        val fromServer = Channel<ByteArray>(Channel.UNLIMITED)
        val toServer = mutableListOf<ByteArray>()
        val toPhone = mutableListOf<ByteArray>()
        fromPhone.send(byteArrayOf(1, 2, 3))
        fromPhone.close()
        fromServer.send(byteArrayOf(9, 8))
        fromServer.close()
        pumpTunnel(
            readSocket = { buf ->
                val next = fromPhone.receiveCatching().getOrNull()
                if (next == null) -1 else { next.copyInto(buf); next.size }
            },
            writeSocket = { buf, n -> toPhone += buf.copyOf(n) },
            receiveWs = { fromServer.receiveCatching().getOrNull() },
            sendWs = { toServer += it },
        )
        assertContentEquals(byteArrayOf(1, 2, 3), toServer.single())
        assertContentEquals(byteArrayOf(9, 8), toPhone.single())
    }

    @Test
    fun the_server_closing_stops_the_phone_side_too() = runTest {
        val toPhone = mutableListOf<ByteArray>()
        val never = Channel<ByteArray>()
        val server = Channel<ByteArray>(Channel.UNLIMITED)
        server.send(byteArrayOf(7))
        server.close()
        pumpTunnel(
            readSocket = { never.receive().size },
            writeSocket = { buf, n -> toPhone += buf.copyOf(n) },
            receiveWs = { server.receiveCatching().getOrNull() },
            sendWs = {},
        )
        assertEquals(1, toPhone.size)
        assertContentEquals(byteArrayOf(7), toPhone[0])
    }

    @Test
    fun the_phone_closing_stops_the_server_side_too() = runTest {
        val toServer = mutableListOf<ByteArray>()
        val phone = Channel<ByteArray>(Channel.UNLIMITED)
        val never = Channel<ByteArray>()
        phone.send(byteArrayOf(4, 5))
        phone.close()
        pumpTunnel(
            readSocket = { buf ->
                val next = phone.receiveCatching().getOrNull()
                if (next == null) -1 else { next.copyInto(buf); next.size }
            },
            writeSocket = { _, _ -> },
            receiveWs = { never.receive() },
            sendWs = { toServer += it },
        )
        assertEquals(1, toServer.size)
        assertContentEquals(byteArrayOf(4, 5), toServer[0])
    }
}
