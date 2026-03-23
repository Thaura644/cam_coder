package com.example.stablecamera

import android.os.Bundle
import android.opengl.GLSurfaceView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.stablecamera.camera.CameraController
import com.example.stablecamera.camera.StabilizationRenderer
import com.example.stablecamera.ui.theme.StableCameraTheme
import android.content.Intent
import com.example.stablecamera.privacy.PrivacyFilterService

class MainActivity : ComponentActivity() {
    private val nativeLib = NativeLib()
    private lateinit var sensorFusionManager: SensorFusionManager
    private lateinit var cameraController: CameraController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sensorFusionManager = SensorFusionManager(this, nativeLib)
        cameraController = CameraController(this, this)

        setContent {
            StableCameraTheme {
                CameraScreen(
                    nativeLib = nativeLib,
                    cameraController = cameraController,
                    onTogglePrivacy = { enabled ->
                        val intent = Intent(this, PrivacyFilterService::class.java).apply {
                            action = if (enabled) PrivacyFilterService.ACTION_SHOW else PrivacyFilterService.ACTION_HIDE
                        }
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            startForegroundService(intent)
                        } else {
                            startService(intent)
                        }
                    }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        sensorFusionManager.start()
    }

    override fun onPause() {
        super.onPause()
        sensorFusionManager.stop()
        cameraController.close()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CameraScreen(
    nativeLib: NativeLib,
    cameraController: CameraController,
    onTogglePrivacy: (Boolean) -> Unit
) {
    var isSuperSteadyEnabled by remember { mutableStateOf(true) }
    var isPrivacyFilterEnabled by remember { mutableStateOf(false) }
    var isRecording by remember { mutableStateOf(false) }
    var showProMode by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var zoomValue by remember { mutableStateOf(1f) }

    if (showSettings) {
        AlertDialog(
            onDismissRequest = { showSettings = false },
            title = { Text("Camera Settings") },
            text = {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Stabilization Strength")
                        Slider(value = 0.85f, onValueChange = {}, valueRange = 0.5f..1f)
                    }
                    Text("Privacy Filter: ${if (isPrivacyFilterEnabled) "Active" else "Inactive"}")
                }
            },
            confirmButton = {
                TextButton(onClick = { showSettings = false }) { Text("OK") }
            }
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // ... (rest of the code)
        
        // Settings / Pro Controls Toggle
        Column(modifier = Modifier.align(Alignment.TopEnd).padding(16.dp)) {
            IconButton(
                onClick = { showSettings = true },
            ) {
                Icon(Icons.Default.Settings, contentDescription = "Settings", tint = Color.White)
            }
            // ...
        }
    }
}

@Composable
fun ProModePanel(onZoomChange: (Float) -> Unit, currentZoom: Float) {
    Surface(
        color = Color.Black.copy(alpha = 0.7f),
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.padding(horizontal = 16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("ZOOM: ${String.format("%.1f", currentZoom)}x", color = Color.White)
            Slider(
                value = currentZoom,
                onValueChange = onZoomChange,
                valueRange = 1f..10f,
                modifier = Modifier.width(200.dp)
            )
        }
    }
}
