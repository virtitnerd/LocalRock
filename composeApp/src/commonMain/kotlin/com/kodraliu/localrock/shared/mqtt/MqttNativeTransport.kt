package com.kodraliu.localrock.shared.mqtt


interface MqttNativeConnection {
    val isConnected: Boolean
    fun subscribe(topic: String, qos: Int)
    fun unsubscribe(topic: String)
    fun publish(topic: String, payload: ByteArray, qos: Int, retain: Boolean)
    fun close()
}


interface MqttNativeTransport {
    fun open(
        host: String,
        port: Int,
        tls: Boolean,
        username: String,
        password: String,
        clientId: String,
        keepAliveSeconds: Int,
        onConnected: () -> Unit,
        onFailed: (reason: String) -> Unit,
        onMessage: (topic: String, payload: ByteArray) -> Unit,
        onDisconnected: () -> Unit,
    ): MqttNativeConnection
}


/** A transport that can also reach the broker as MQTT over WebSocket. */
interface MqttWebSocketTransport {
    /**
     * Whether [host] resolves to a private network address, which means the phone is at home on a
     * server with split DNS. Null when it cannot tell, such as when the name does not resolve.
     */
    fun resolvesToPrivateAddress(host: String): Boolean?

    fun openWebSocket(
        url: String,
        username: String,
        password: String,
        clientId: String,
        keepAliveSeconds: Int,
        onConnected: () -> Unit,
        onFailed: (reason: String) -> Unit,
        onMessage: (topic: String, payload: ByteArray) -> Unit,
        onDisconnected: () -> Unit,
    ): MqttNativeConnection
}


expect fun defaultMqttTransport(): MqttNativeTransport?
