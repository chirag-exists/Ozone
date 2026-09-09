package com.example.offgridmesh.mesh

import android.content.Context
import android.util.Log
import com.example.offgridmesh.data.MeshPacket
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap

/**
 * Thread-safe mesh networking manager wrapping Google Nearby Connections API.
 *
 * Responsibilities:
 * - Simultaneous advertising and discovery using P2P_CLUSTER.
 * - Zero-click automatic connection acceptance.
 * - Flood-routing with TTL-based hop limiting and LRU deduplication.
 * - Exposes incoming messages as a SharedFlow and peer state as StateFlows.
 */
class MeshManager(private val context: Context) {

    companion object {
        private const val TAG = "MeshManager"
        private const val SERVICE_ID = "com.offgrid.mesh.chat"
        private val STRATEGY = Strategy.P2P_CLUSTER
        private const val MAX_SEEN_IDS = 500
    }

    private val connectionsClient by lazy { Nearby.getConnectionsClient(context) }

    // --- Observable State ---

    /** Map of endpoint ID to peer display name. */
    private val _connectedEndpoints = ConcurrentHashMap<String, String>()

    private val _peerMap = MutableStateFlow<Map<String, String>>(emptyMap())
    val peerMap: StateFlow<Map<String, String>> = _peerMap.asStateFlow()

    private val _peerCount = MutableStateFlow(0)
    val peerCount: StateFlow<Int> = _peerCount.asStateFlow()

    private val _isMeshActive = MutableStateFlow(false)
    val isMeshActive: StateFlow<Boolean> = _isMeshActive.asStateFlow()

    private val _incomingMessages = MutableSharedFlow<MeshPacket>(extraBufferCapacity = 64)
    val incomingMessages: SharedFlow<MeshPacket> = _incomingMessages.asSharedFlow()

    // --- Deduplication Cache ---

    /** Circular LRU set of seen message IDs (capped at MAX_SEEN_IDS). */
    private val seenMessageIds: MutableSet<String> =
        Collections.synchronizedSet(LinkedHashSet<String>())

    private fun markSeen(messageId: String): Boolean {
        synchronized(seenMessageIds) {
            if (seenMessageIds.contains(messageId)) return false
            if (seenMessageIds.size >= MAX_SEEN_IDS) {
                // Evict the oldest entry
                val iterator = seenMessageIds.iterator()
                if (iterator.hasNext()) {
                    iterator.next()
                    iterator.remove()
                }
            }
            seenMessageIds.add(messageId)
            return true
        }
    }

    // --- Connection Callbacks ---

    private val connectionLifecycleCallback = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(endpointId: String, info: ConnectionInfo) {
            Log.d(TAG, "Connection initiated with ${info.endpointName} ($endpointId) — auto-accepting")
            // Zero-click: automatically accept all connections
            connectionsClient.acceptConnection(endpointId, payloadCallback)
        }

        override fun onConnectionResult(endpointId: String, result: ConnectionResolution) {
            when (result.status.statusCode) {
                ConnectionsStatusCodes.STATUS_OK -> {
                    Log.d(TAG, "Connected to $endpointId")
                    // We store the endpoint; the name was captured during onConnectionInitiated
                    // but ConnectionResolution doesn't carry it. We use a fallback name.
                    if (!_connectedEndpoints.containsKey(endpointId)) {
                        _connectedEndpoints[endpointId] = "Peer-${endpointId.takeLast(4)}"
                    }
                    updatePeerState()
                }
                ConnectionsStatusCodes.STATUS_CONNECTION_REJECTED -> {
                    Log.w(TAG, "Connection rejected by $endpointId")
                }
                else -> {
                    Log.w(TAG, "Connection failed with $endpointId: ${result.status}")
                }
            }
        }

        override fun onDisconnected(endpointId: String) {
            Log.d(TAG, "Disconnected from $endpointId")
            _connectedEndpoints.remove(endpointId)
            updatePeerState()
        }
    }

    /** Separate callback that also captures the peer name from ConnectionInfo. */
    private val connectionLifecycleCallbackWithName = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(endpointId: String, info: ConnectionInfo) {
            Log.d(TAG, "Connection initiated with ${info.endpointName} ($endpointId) — auto-accepting")
            // Store the name early so we have it when onConnectionResult fires
            _connectedEndpoints[endpointId] = info.endpointName
            connectionsClient.acceptConnection(endpointId, payloadCallback)
        }

        override fun onConnectionResult(endpointId: String, result: ConnectionResolution) {
            connectionLifecycleCallback.onConnectionResult(endpointId, result)
        }

        override fun onDisconnected(endpointId: String) {
            connectionLifecycleCallback.onDisconnected(endpointId)
        }
    }

    // --- Payload Handling ---

    private val payloadCallback = object : PayloadCallback() {
        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            if (payload.type != Payload.Type.BYTES) return
            val bytes = payload.asBytes() ?: return

            val packet = MeshPacket.fromBytes(bytes)
            if (packet == null) {
                Log.w(TAG, "Received malformed packet from $endpointId")
                return
            }

            // Deduplication: if we've already seen this message, drop it
            if (!markSeen(packet.messageId)) {
                Log.d(TAG, "Duplicate packet ${packet.messageId.take(8)}… — discarding")
                return
            }

            Log.d(TAG, "New packet from ${packet.senderName}: \"${packet.text}\" (TTL=${packet.ttl})")

            // Emit to UI
            _incomingMessages.tryEmit(packet)

            // Flood-forward: if TTL allows, decrement and re-broadcast to all peers except sender
            if (packet.ttl > 1) {
                val forwarded = packet.copy(ttl = packet.ttl - 1)
                _connectedEndpoints.keys.forEach { targetId ->
                    if (targetId != endpointId) {
                        connectionsClient.sendPayload(targetId, Payload.fromBytes(forwarded.toBytes()))
                            .addOnFailureListener { e ->
                                Log.w(TAG, "Failed to forward to $targetId: ${e.message}")
                            }
                    }
                }
                Log.d(TAG, "Forwarded packet ${packet.messageId.take(8)}… with TTL=${forwarded.ttl}")
            }
        }

        override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) {
            // No-op for BYTES payloads (transfer is instant)
        }
    }

    // --- Public API ---

    /**
     * Start the mesh: begin advertising this device and discovering nearby peers simultaneously.
     */
    fun startMesh(userName: String) {
        Log.d(TAG, "Starting mesh as '$userName'")

        val advertisingOptions = AdvertisingOptions.Builder()
            .setStrategy(STRATEGY)
            .build()

        connectionsClient.startAdvertising(
            userName,
            SERVICE_ID,
            connectionLifecycleCallbackWithName,
            advertisingOptions
        ).addOnSuccessListener {
            Log.d(TAG, "Advertising started")
        }.addOnFailureListener { e ->
            Log.e(TAG, "Advertising failed: ${e.message}")
        }

        val discoveryOptions = DiscoveryOptions.Builder()
            .setStrategy(STRATEGY)
            .build()

        connectionsClient.startDiscovery(
            SERVICE_ID,
            endpointDiscoveryCallback,
            discoveryOptions
        ).addOnSuccessListener {
            Log.d(TAG, "Discovery started")
        }.addOnFailureListener { e ->
            Log.e(TAG, "Discovery failed: ${e.message}")
        }

        _isMeshActive.value = true
    }

    /**
     * Stop the mesh: cease advertising, discovery, and disconnect from all peers.
     */
    fun stopMesh() {
        Log.d(TAG, "Stopping mesh")
        connectionsClient.stopAdvertising()
        connectionsClient.stopDiscovery()
        connectionsClient.stopAllEndpoints()
        _connectedEndpoints.clear()
        updatePeerState()
        _isMeshActive.value = false
    }

    /**
     * Send a text message to all connected peers.
     */
    fun broadcastMessage(text: String, senderName: String) {
        val packet = MeshPacket(
            senderName = senderName,
            text = text,
            ttl = 3,
            initialTtl = 3
        )

        // Mark as seen locally (so we don't re-display our own broadcast if it loops back)
        markSeen(packet.messageId)

        val bytes = packet.toBytes()
        _connectedEndpoints.keys.forEach { endpointId ->
            connectionsClient.sendPayload(endpointId, Payload.fromBytes(bytes))
                .addOnFailureListener { e ->
                    Log.w(TAG, "Failed to send to $endpointId: ${e.message}")
                }
        }

        // Also emit to our own UI
        _incomingMessages.tryEmit(packet)

        Log.d(TAG, "Broadcast message: \"$text\" to ${_connectedEndpoints.size} peers")
    }

    // --- Endpoint Discovery ---

    private val endpointDiscoveryCallback = object : EndpointDiscoveryCallback() {
        override fun onEndpointFound(endpointId: String, info: DiscoveredEndpointInfo) {
            Log.d(TAG, "Discovered endpoint: ${info.endpointName} ($endpointId) — requesting connection")
            connectionsClient.requestConnection(
                _currentUserName ?: "Unknown",
                endpointId,
                connectionLifecycleCallbackWithName
            ).addOnFailureListener { e ->
                Log.w(TAG, "Failed to request connection to $endpointId: ${e.message}")
            }
        }

        override fun onEndpointLost(endpointId: String) {
            Log.d(TAG, "Lost endpoint: $endpointId")
        }
    }

    private var _currentUserName: String? = null

    fun setUserName(name: String) {
        _currentUserName = name
    }

    // --- Internal Helpers ---

    private fun updatePeerState() {
        val snapshot = _connectedEndpoints.toMap()
        _peerMap.update { snapshot }
        _peerCount.value = snapshot.size
    }
}
