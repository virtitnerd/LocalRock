package com.kodraliu.localrock.shared.http

import io.ktor.http.Url
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

@Serializable
data class ServerHeader(val name: String, val value: String)

/** Extra request headers for a server behind a gateway such as Cloudflare Access. */
object ServerHeaders {
    const val MAX_HEADERS = 10
    const val MAX_VALUE_LENGTH = 2048

    private val blocked = setOf(
        "authorization", "host", "content-length", "content-type", "upgrade", "connection", "user-agent",
    )
    private const val TOKEN_SYMBOLS = "!#$%&'*+-.^_`|~"

    /** The first problem with [headers], or null when they are all acceptable. */
    fun validate(headers: List<ServerHeader>): String? {
        if (headers.size > MAX_HEADERS) return "At most $MAX_HEADERS headers"
        for (h in headers) validateOne(h)?.let { return it }
        return null
    }

    fun validateOne(h: ServerHeader): String? {
        val name = h.name
        if (name.isEmpty()) return "Name is required"
        if (!name.all { it.code < 128 && (it.isLetterOrDigit() || it in TOKEN_SYMBOLS) }) {
            return "Name has characters a header cannot use"
        }
        val lower = name.lowercase()
        if (lower in blocked || lower.startsWith("sec-websocket-")) return "$name is set by the app and cannot be changed"
        if (h.value.length > MAX_VALUE_LENGTH) return "Value is longer than $MAX_VALUE_LENGTH characters"
        if (h.value.any { it == '\r' || it == '\n' }) return "Value cannot contain line breaks"
        return null
    }

    internal fun encode(headers: List<ServerHeader>): String = Json.encodeToString(LIST, headers)

    /** Stored headers that still pass validation, in order; anything unreadable gives an empty list. */
    internal fun decode(json: String?): List<ServerHeader> {
        if (json.isNullOrBlank()) return emptyList()
        val list = runCatching { Json.decodeFromString(LIST, json) }.getOrNull() ?: return emptyList()
        return if (validate(list) == null) list else emptyList()
    }

    /** The [headers] as a map when [url] points at the host of [serverBaseUrl], otherwise empty. */
    fun headersFor(
        headers: List<ServerHeader>,
        url: String,
        serverBaseUrl: String?,
    ): Map<String, String> =
        if (headers.isNotEmpty() && isServerHost(url, serverBaseUrl)) headers.associate { it.name to it.value }
        else emptyMap()

    internal fun isServerHost(url: String, serverBaseUrl: String?): Boolean {
        val server = hostOf(serverBaseUrl ?: return false) ?: return false
        return hostOf(url) == server
    }

    private fun hostOf(url: String): String? {
        val withScheme = if ("://" in url) url else "https://$url"
        return runCatching { Url(withScheme).host.lowercase() }.getOrNull()?.takeIf { it.isNotEmpty() }
    }

    private val LIST = ListSerializer(ServerHeader.serializer())
}
