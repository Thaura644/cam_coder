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
import com.example.stablecamera.privacy.AppUsageMonitor

class MainActivity : ComponentActivity() {
    private val nativeLib = NativeLib()
    private lateinit var sensorFusionManager: SensorFusionManager
    private lateinit var cameraController: CameraController
    private lateinit var appUsageMonitor: AppUsageMonitor
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
        cameraController = CameraController(this, this, nativeLib)
        appUsageMonitor = AppUsageMonitor(this)

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
        appUsageMonitor.start()
        checkAndRequestPermissions()
    }

    override fun onPause() {
        super.onPause()
        sensorFusionManager.stop()
        appUsageMonitor.stop()
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
    var isRecording by remember { mutableStateOf(false) }
    var showProMode by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var zoomValue by remember { mutableStateOf(1f) }
    var stabilizationStrength by remember { mutableStateOf(0.95f) }
    var frameProcessingEnabled by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        checkPermissions()
    }

    LaunchedEffect(frameProcessingEnabled) {
        cameraController.setFrameProcessingEnabled(frameProcessingEnabled)
    }

    if (showSettings) {
        AlertDialog(
            onDismissRequest = { showSettings = false },
            title = { Text("Camera Settings") },
            text = {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Stabilization Strength")
                        Slider(
                            value = stabilizationStrength,
                            onValueChange = { stabilizationStrength = it },
                            valueRange = 0.5f..1f
                        )
                    }
                    Text("Privacy Filter: ${if (isPrivacyFilterEnabled) "Active" else "Inactive"}")
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    nativeLib.setStabilizationStrength(stabilizationStrength)
                    showSettings = false
                }) { Text("OK") }
            }
        )
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
                FilterChip(
                    selected = frameProcessingEnabled,
                    onClick = { frameProcessingEnabled = !frameProcessingEnabled },
                    label = { Text("Frame Filter", color = if (frameProcessingEnabled) Color.Black else Color.White) }
                )
            }

            FloatingActionButton(
                onClick = {
                    if (isRecording) {
                        cameraController.toggleRecording(
                            onStarted = { isRecording = false },
                            onStopped = { isRecording = false }
                        )
                    } else {
                        cameraController.toggleRecording(
                            onStarted = { isRecording = true },
                            onStopped = { isRecording = false }
                        )
                    }
                },
                containerColor = if (isRecording) Color.Red else Color.White
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = if (isRecording) "Stop Recording" else "Record",
                    tint = Color.Black
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            FloatingActionButton(
                onClick = { cameraController.takePhoto { } },
                containerColor = Color.White,
                modifier = Modifier.size(80.dp)
            ) {
                Box(modifier = Modifier.size(60.dp))
            }

            if (showProMode) {
                ProModePanel(
                    onZoomChange = { zoom ->
                        zoomValue = zoom
                        cameraController.setZoom(zoom)
                    },
                    currentZoom = zoomValue
                )
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp),
            horizontalAlignment = Alignment.End
        ) {
            if (showProMode) {
                Text("PRO MODE", color = Color.Yellow, modifier = Modifier.padding(bottom = 8.dp))
            }
            Row {
                IconButton(onClick = { showProMode = !showProMode }) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Pro Mode",
                        tint = if (showProMode) Color.Yellow else Color.White
                    )
                }
                IconButton(onClick = { showSettings = true }) {
                    Icon(Icons.Default.Settings, contentDescription = "Settings", tint = Color.White)
                }
            }
        }

        IconButton(
            modifier = Modifier.align(Alignment.TopStart).padding(16.dp),
            onClick = { checkPermissions() }
        ) {
            Icon(imageVector = Icons.Default.Check, contentDescription = "Refresh Permissions", tint = Color.White)
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
