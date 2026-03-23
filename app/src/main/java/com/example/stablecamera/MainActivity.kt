package com.example.stablecamera

import android.Manifest
import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Process
import android.provider.Settings
import android.opengl.GLSurfaceView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material.icons.filled.Check
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.core.content.ContextCompat
import com.example.stablecamera.camera.CameraController
import com.example.stablecamera.camera.StabilizationRenderer
import com.example.stablecamera.ui.theme.StableCameraTheme
import com.example.stablecamera.privacy.PrivacyFilterService

class MainActivity : ComponentActivity() {
    private val nativeLib = NativeLib()
    private lateinit var sensorFusionManager: SensorFusionManager
    private lateinit var cameraController: CameraController
    private var stabilizationRenderer: StabilizationRenderer? = null

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            // Camera permission granted
        }
    }

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
                        handlePrivacyToggle(enabled)
                    },
                    checkPermissions = { checkAndRequestPermissions() }
                )
            }
        }
    }

    private fun handlePrivacyToggle(enabled: Boolean) {
        if (enabled) {
            if (!Settings.canDrawOverlays(this)) {
                val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
                startActivity(intent)
                return
            }
            if (!hasUsageStatsPermission()) {
                val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
                startActivity(intent)
                return
            }
            startService(Intent(this, PrivacyFilterService::class.java).apply {
                action = PrivacyFilterService.ACTION_SHOW
            })
        } else {
            startService(Intent(this, PrivacyFilterService::class.java).apply {
                action = PrivacyFilterService.ACTION_HIDE
            })
        }
    }

    private fun hasUsageStatsPermission(): Boolean {
        val appOps = getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = appOps.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(), packageName
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }

    private fun checkAndRequestPermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    override fun onResume() {
        super.onResume()
        sensorFusionManager.start()
        checkAndRequestPermissions()
    }

    override fun onPause() {
        super.onPause()
        sensorFusionManager.stop()
        cameraController.close()
    }

    override fun onDestroy() {
        stabilizationRenderer?.release()
        super.onDestroy()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CameraScreen(
    nativeLib: NativeLib,
    cameraController: CameraController,
    onTogglePrivacy: (Boolean) -> Unit,
    checkPermissions: () -> Unit
) {
    var isSuperSteadyEnabled by remember { mutableStateOf(true) }
    var isPrivacyFilterEnabled by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        checkPermissions()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            factory = { context ->
                GLSurfaceView(context).apply {
                    setEGLContextClientVersion(2)
                    val renderer = StabilizationRenderer(nativeLib)
                    renderer.onSurfaceTextureAvailable = { surfaceTexture ->
                        cameraController.openCamera(surfaceTexture)
                    }
                    setRenderer(renderer)
                    renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
                }
            },
            modifier = Modifier.fillMaxSize()
        )

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
            ) { }
        }

        IconButton(
            modifier = Modifier.align(Alignment.TopStart).padding(16.dp),
            onClick = { checkPermissions() }
        ) {
            Icon(imageVector = Icons.Default.Check, contentDescription = "Refresh Permissions", tint = Color.White)
        }
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
