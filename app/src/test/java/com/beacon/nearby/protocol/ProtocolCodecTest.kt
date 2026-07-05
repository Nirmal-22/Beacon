package com.beacon.nearby.protocol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProtocolCodecTest {

    private val envelope = BeaconEnvelope(
        type = BeaconEnvelope.TYPE_CHAT_MSG,
        msgId = "msg-1",
        senderId = "session-1",
        senderName = "Nirmal",
        roomCode = "dm:a:b",
        ts = 1_720_000_000_000,
        body = "hello there",
    )

    @Test
    fun `round trip preserves every field`() {
        assertEquals(envelope, ProtocolCodec.decode(ProtocolCodec.encode(envelope)))
    }

    @Test
    fun `null roomCode and body survive the round trip`() {
        val hello = envelope.copy(type = BeaconEnvelope.TYPE_HELLO, roomCode = null, body = null)
        assertEquals(hello, ProtocolCodec.decode(ProtocolCodec.encode(hello)))
    }

    @Test
    fun `unknown fields from newer clients are ignored`() {
        val futureJson = """
            {"v":2,"type":"CHAT_MSG","msgId":"m","senderId":"s","senderName":"n",
             "ts":1,"body":"b","someFutureField":{"nested":true}}
        """.trimIndent()
        val decoded = ProtocolCodec.decode(futureJson.encodeToByteArray())!!
        assertEquals("CHAT_MSG", decoded.type)
        assertEquals(2, decoded.v)
    }

    @Test
    fun `unknown envelope types still decode`() {
        val decoded = ProtocolCodec.decode(
            ProtocolCodec.encode(envelope.copy(type = "HOLOGRAM_CALL"))
        )
        assertEquals("HOLOGRAM_CALL", decoded!!.type)
    }

    @Test
    fun `garbage bytes decode to null instead of throwing`() {
        assertNull(ProtocolCodec.decode(byteArrayOf(0x00, 0x7F, -1, 42)))
        assertNull(ProtocolCodec.decode("not json at all".encodeToByteArray()))
        assertNull(ProtocolCodec.decode("""{"type":"CHAT_MSG"}""".encodeToByteArray()))
    }
}
