package com.example.offgridmesh.data

import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import java.util.UUID

/**
 * Represents a single message packet in the mesh network.
 *
 * @param messageId Unique identifier for deduplication (UUID).
 * @param senderName Display name of the original sender.
 * @param text The message body.
 * @param ttl Time-To-Live — decremented on each hop. Packet is dropped when TTL reaches 0.
 * @param timestamp Unix epoch millis when the message was originally created.
 * @param initialTtl The TTL value when the packet was first created (used to compute hop count).
 */
data class MeshPacket(
    val messageId: String = UUID.randomUUID().toString(),
    val senderName: String,
    val text: String,
    var ttl: Int = 3,
    val timestamp: Long = System.currentTimeMillis(),
    val initialTtl: Int = 3
) {
    /**
     * Serialize this packet to a UTF-8 JSON byte array for transmission via Nearby Connections.
     */
    fun toBytes(): ByteArray {
        return gson.toJson(this).toByteArray(Charsets.UTF_8)
    }

    /**
     * Compute how many hops this message has traveled.
     */
    val hopCount: Int
        get() = initialTtl - ttl

    companion object {
        private val gson = Gson()

        /**
         * Deserialize a byte array back into a [MeshPacket].
         * Returns null if the data is malformed.
         */
        fun fromBytes(bytes: ByteArray): MeshPacket? {
            return try {
                val json = String(bytes, Charsets.UTF_8)
                gson.fromJson(json, MeshPacket::class.java)
            } catch (e: JsonSyntaxException) {
                null
            } catch (e: Exception) {
                null
            }
        }
    }
}
