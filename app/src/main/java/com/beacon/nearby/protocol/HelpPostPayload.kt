package com.beacon.nearby.protocol

import kotlinx.serialization.SerializationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Body of a TYPE_HELP_POST envelope. */
@Serializable
data class HelpPostPayload(
    val id: String,
    val category: String,
    val text: String,
    val expiresAt: Long,
) {
    fun encode(): String = json.encodeToString(serializer(), this)

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        fun decode(body: String?): HelpPostPayload? = body?.let {
            try {
                json.decodeFromString(serializer(), it)
            } catch (_: SerializationException) {
                null
            } catch (_: IllegalArgumentException) {
                null
            }
        }
    }
}
