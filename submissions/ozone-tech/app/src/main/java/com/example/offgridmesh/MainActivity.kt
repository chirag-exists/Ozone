package com.example.offgridmesh

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.offgridmesh.theme.OffGridMeshTheme
import com.example.offgridmesh.ui.ChatScreen
import com.example.offgridmesh.ui.ChatViewModel

class MainActivity : ComponentActivity() {

    private var allPermissionsGranted by mutableStateOf(false)

    private val requiredPermissions: Array<String>
        get() {
            val perms = mutableListOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                perms.addAll(
                    listOf(
                        Manifest.permission.BLUETOOTH_SCAN,
                        Manifest.permission.BLUETOOTH_ADVERTISE,
                        Manifest.permission.BLUETOOTH_CONNECT
                    )
                )
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                perms.add(Manifest.permission.NEARBY_WIFI_DEVICES)
            }
            return perms.toTypedArray()
        }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        allPermissionsGranted = grants.values.all { it }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Check permissions on launch
        allPermissionsGranted = checkAllPermissions()

        setContent {
            OffGridMeshTheme {
                val chatViewModel: ChatViewModel = viewModel()
                ChatScreen(
                    viewModel = chatViewModel,
                    allPermissionsGranted = allPermissionsGranted,
                    onRequestPermissions = { permissionLauncher.launch(requiredPermissions) }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Re-check in case user granted via settings
        allPermissionsGranted = checkAllPermissions()
    }

    private fun checkAllPermissions(): Boolean {
        return requiredPermissions.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
    }
}
