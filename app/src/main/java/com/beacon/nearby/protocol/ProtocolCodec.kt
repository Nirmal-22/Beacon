package com.beacon.nearby.protocol

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/**
 * Envelope <-> bytes. Pure JVM, no Android imports.
 *
 * Decoding is deliberately forgiving: unknown JSON fields are ignored so a
 * newer client can extend the envelope without breaking older ones, and any
 * undecodable payload returns null rather than throwing — the mesh may
 * contain non-Beacon or newer-protocol endpoints.
 */
object ProtocolCodec {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun encode(envelope: BeaconEnvelope): ByteArray =
        json.encodeToString(BeaconEnvelope.serializer(), envelope).encodeToByteArray()

    fun decode(bytes: ByteArray): BeaconEnvelope? = try {
        json.decodeFromString(BeaconEnvelope.serializer(), bytes.decodeToString())
    } catch (_: SerializationException) {
        null
    } catch (_: IllegalArgumentException) {
        null
    }
}
