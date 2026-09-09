package com.example.offgridmesh.ui

import android.app.Application
import android.content.SharedPreferences
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.offgridmesh.data.MeshPacket
import com.example.offgridmesh.mesh.MeshManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * ViewModel bridging MeshManager state to the Compose UI.
 */
class ChatViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs: SharedPreferences =
        application.getSharedPreferences("offgrid_mesh_prefs", Application.MODE_PRIVATE)

    val meshManager = MeshManager(application.applicationContext)

    // --- User Name ---
    private val _userName = MutableStateFlow(prefs.getString("user_name", "") ?: "")
    val userName: StateFlow<String> = _userName.asStateFlow()

    // --- Messages ---
    private val _messages = MutableStateFlow<List<MeshPacket>>(emptyList())
    val messages: StateFlow<List<MeshPacket>> = _messages.asStateFlow()

    // --- Mesh State (delegated) ---
    val peerCount: StateFlow<Int> = meshManager.peerCount
    val isMeshActive: StateFlow<Boolean> = meshManager.isMeshActive

    init {
        // Collect incoming messages from the mesh and append them to the list
        viewModelScope.launch {
            meshManager.incomingMessages.collect { packet ->
                _messages.update { current -> current + packet }
            }
        }
    }

    /**
     * Set the user's display name (persisted to SharedPreferences).
     */
    fun setUserName(name: String) {
        _userName.value = name
        prefs.edit().putString("user_name", name).apply()
        meshManager.setUserName(name)
    }

    /**
     * Start the mesh network with the current user name.
     */
    fun startMesh() {
        val name = _userName.value.ifBlank { "Anonymous" }
        meshManager.setUserName(name)
        meshManager.startMesh(name)
    }

    /**
     * Stop the mesh network.
     */
    fun stopMesh() {
        meshManager.stopMesh()
    }

    /**
     * Send a text message via the mesh.
     */
    fun sendMessage(text: String) {
        if (text.isBlank()) return
        val name = _userName.value.ifBlank { "Anonymous" }
        meshManager.broadcastMessage(text.trim(), name)
    }

    override fun onCleared() {
        super.onCleared()
        meshManager.stopMesh()
    }
}
