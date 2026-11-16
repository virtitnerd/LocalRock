package com.kodraliu.localrock.shared.http

import io.ktor.client.plugins.api.Send
import io.ktor.client.plugins.api.createClientPlugin

class CustomHeadersConfig {
    /** The headers to send to the request URL; empty for any host but the configured server. */
    var headersFor: (url: String) -> Map<String, String> = { emptyMap() }

    /** Every custom header name, so a redirect to another host cannot carry the copied headers along. */
    var names: () -> Set<String> = { emptySet() }
}

/** Adds the user's custom headers to requests for the server host, never replacing a header already set. */
val CustomHeaders = createClientPlugin("CustomHeaders", ::CustomHeadersConfig) {
    val headersFor = pluginConfig.headersFor
    val names = pluginConfig.names

    onRequest { request, _ ->
        headersFor(request.url.buildString()).forEach { (name, value) ->
            if (!request.headers.contains(name)) request.headers.append(name, value)
        }
    }

    // Sees every request sent, including the ones a redirect creates, which skip onRequest.
    on(Send) { request ->
        if (headersFor(request.url.buildString()).isEmpty()) names().forEach { request.headers.remove(it) }
        proceed(request)
    }
}
