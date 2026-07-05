package com.beacon.nearby.protocol

import kotlinx.serialization.Serializable

/**
 * The single wire format for every Payload.BYTES message.
 *
 * [type] is a plain string (not an enum) so that older clients skip envelope
 * types they don't know instead of failing to deserialize. [msgId] is the
 * idempotency key — receivers must treat a repeated msgId as a no-op.
 */
@Serializable
data class BeaconEnvelope(
    val v: Int = PROTOCOL_VERSION,
    val type: String,
    val msgId: String,
    val senderId: String,
    val senderName: String,
    val roomCode: String? = null,
    val ts: Long,
    val body: String? = null,
) {
    companion object {
        const val PROTOCOL_VERSION = 1

        /** Identity exchange, sent by both sides immediately after connecting. */
        const val TYPE_HELLO = "HELLO"

        /** Chat text; [roomCode] is set (dm:* codes for 1:1 chats). */
        const val TYPE_CHAT_MSG = "CHAT_MSG"

        /** Delivery receipt: [body] is the msgId of the CHAT_MSG being acknowledged. */
        const val TYPE_ACK = "ACK"

        /**
         * Read receipt: sender has *seen* [roomCode] on screen. Everything the
         * receiver sent in that room up to [ts] counts as read.
         */
        const val TYPE_READ = "READ"

        /** Sender is typing in [roomCode]; receivers show it briefly, no reply needed. */
        const val TYPE_TYPING = "TYPING"

        /** Full snapshot of the sender's room memberships (M3). */
        const val TYPE_ROOM_ANNOUNCE = "ROOM_ANNOUNCE"

        /** Sender's current intent tag key; empty body clears it (M5). */
        const val TYPE_INTENT = "INTENT"

        /** Icebreaker tap: [body] is the chosen emoji (M5). */
        const val TYPE_ICEBREAKER = "ICEBREAKER"

        /** Reply to an icebreaker: [body] is "accept" or "decline" (M5). */
        const val TYPE_ICEBREAKER_REPLY = "ICEBREAKER_REPLY"

        /** Help request: [body] is a HelpPostPayload JSON (M6). */
        const val TYPE_HELP_POST = "HELP_POST"

        /** Poster withdrew a help request: [body] is its id (M6). */
        const val TYPE_HELP_CANCEL = "HELP_CANCEL"

        /**
         * Opt-in map position: [body] is "lat,lon"; empty/null body means the
         * sender stopped sharing and their pin must be removed (map feature).
         */
        const val TYPE_LOCATION = "LOCATION"
    }
}
