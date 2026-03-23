package com.example.stablecamera.camera

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import androidx.camera.camera2.interop.Camera2Interop
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.example.stablecamera.NativeLib
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class CameraController(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner,
    private val renderer: StabilizedRenderer
) {
    private var cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()

    @SuppressLint("UnsafeOptInUsageError")
    fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)

        cameraProviderFuture.addListener({
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            renderer.setOnSurfaceTextureAvailableListener { surfaceTexture ->
                val preview = Preview.Builder()
                    .build()

                preview.setSurfaceProvider { request ->
                    val surface = android.view.Surface(surfaceTexture)
                    request.provideSurface(surface, cameraExecutor) {
                        surface.release()
                    }
                }

                val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                try {
                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        cameraSelector,
                        preview
                    )
                } catch (exc: Exception) {
                    Log.e("CameraController", "Use case binding failed", exc)
                }
            }
        }, ContextCompat.getMainExecutor(context))
    }

    fun stop() {
        cameraExecutor.shutdown()
    }
}
