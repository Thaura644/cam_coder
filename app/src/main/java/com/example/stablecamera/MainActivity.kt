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
        cameraController = CameraController(this)

        setContent {
            StableCameraTheme {
                CameraScreen(
                    nativeLib = nativeLib,
                    cameraController = cameraController,
                    onTogglePrivacy = { enabled ->
                        val intent = Intent(this, PrivacyFilterService::class.java).apply {
                            action = if (enabled) PrivacyFilterService.ACTION_SHOW else PrivacyFilterService.ACTION_HIDE
                        }
                        startService(intent)
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

    Box(modifier = Modifier.fillMaxSize()) {
        // Camera Preview with Stabilization
        AndroidView(
            factory = { context ->
                GLSurfaceView(context).apply {
                    setEGLContextClientVersion(2)
                    val renderer = StabilizationRenderer(nativeLib)
                    renderer.onSurfaceTextureAvailable = { surfaceTexture ->
                        cameraController.openCamera(surfaceTexture)
                    }
                    setRenderer(renderer)
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // UI Controls
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                FilterChip(
                    selected = isSuperSteadyEnabled,
                    onClick = { isSuperSteadyEnabled = !isSuperSteadyEnabled },
                    label = { Text("Super Steady", color = if (isSuperSteadyEnabled) Color.Black else Color.White) }
                )
                FilterChip(
                    selected = isPrivacyFilterEnabled,
                    onClick = {
                        isPrivacyFilterEnabled = !isPrivacyFilterEnabled
                        onTogglePrivacy(isPrivacyFilterEnabled)
                    },
                    label = { Text("Privacy Filter", color = if (isPrivacyFilterEnabled) Color.Black else Color.White) }
                )
            }

            Button(
                onClick = { /* Capture Logic */ },
                modifier = Modifier.size(80.dp),
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(containerColor = Color.White)
            ) {
                // Shutter button center
            }
        }

        // Settings / Pro Controls
        IconButton(
            onClick = { /* Settings Logic */ },
            modifier = Modifier.align(Alignment.TopEnd).padding(16.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Settings,
                contentDescription = "Settings",
                tint = Color.White
            )
        }
    }
}
