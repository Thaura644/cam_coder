package com.example.stablecamera.camera

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.*
import androidx.camera.video.VideoCapture
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.example.stablecamera.NativeLib
import java.io.File
import java.nio.ByteBuffer
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class CameraController(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner,
    private val nativeLib: NativeLib? = null
) {
    private var cameraProvider: ProcessCameraProvider? = null
    private var imageCapture: ImageCapture? = null
    private var videoCapture: VideoCapture<Recorder>? = null
    private var imageAnalysis: ImageAnalysis? = null
    private var recording: Recording? = null
    private var camera: Camera? = null
    private val cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private var frameProcessingEnabled = false

    fun openCamera(surfaceTexture: android.graphics.SurfaceTexture) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            cameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder().build()
            preview.setSurfaceProvider { request ->
                val surface = android.view.Surface(surfaceTexture)
                request.provideSurface(surface, cameraExecutor) {
                    surface.release()
                }
            }

            imageCapture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .build()

            val recorder = Recorder.Builder()
                .setQualitySelector(QualitySelector.from(Quality.HIGHEST))
                .build()
            videoCapture = VideoCapture.withOutput(recorder)

            imageAnalysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also { analysis ->
                    analysis.setAnalyzer(cameraExecutor) { imageProxy ->
                        if (frameProcessingEnabled && nativeLib != null) {
                            processImageProxy(imageProxy, nativeLib)
                        }
                        imageProxy.close()
                    }
                }

            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                cameraProvider?.unbindAll()
                camera = cameraProvider?.bindToLifecycle(
                    lifecycleOwner,
                    cameraSelector,
                    preview,
                    imageCapture,
                    videoCapture,
                    imageAnalysis
                )
            } catch (exc: Exception) {
                Log.e("CameraController", "Use case binding failed", exc)
            }
        }, ContextCompat.getMainExecutor(context))
    }

    fun takePhoto(onSaved: (File) -> Unit) {
        val imageCapture = imageCapture ?: return
        val photoFile = File(context.externalCacheDir, SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date()) + ".jpg")
        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

        imageCapture.takePicture(outputOptions, cameraExecutor, object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                onSaved(photoFile)
            }
            override fun onError(exc: ImageCaptureException) {
                Log.e("CameraController", "Photo capture failed: ${exc.message}", exc)
            }
        })
    }

    fun toggleRecording(onStarted: () -> Unit, onStopped: (File?) -> Unit) {
        val videoCapture = videoCapture ?: return
        val currentRecording = recording
        if (currentRecording != null) {
            currentRecording.stop()
            recording = null
            return
        }

        val videoFile = File(context.externalCacheDir, SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date()) + ".mp4")
        val outputOptions = FileOutputOptions.Builder(videoFile).build()

        recording = videoCapture.output
            .prepareRecording(context, outputOptions)
            .start(ContextCompat.getMainExecutor(context)) { event ->
                when (event) {
                    is VideoRecordEvent.Start -> onStarted()
                    is VideoRecordEvent.Finalize -> {
                        if (!event.hasError()) {
                            onStopped(videoFile)
                        } else {
                            Log.e("CameraController", "Video capture failed: ${event.error}")
                            onStopped(null)
                        }
                    }
                }
            }
    }

    fun setZoom(ratio: Float) {
        camera?.cameraControl?.setZoomRatio(ratio)
    }

    fun setFrameProcessingEnabled(enabled: Boolean) {
        frameProcessingEnabled = enabled
    }

    @SuppressLint("UnsafeOptInUsageError")
    private fun processImageProxy(imageProxy: ImageProxy, nativeLib: NativeLib) {
        val image = imageProxy.image ?: return
        val width = image.width
        val height = image.height

        val yPlane = image.planes[0]
        val uPlane = image.planes[1]
        val vPlane = image.planes[2]

        val yBuffer = yPlane.buffer
        val uBuffer = uPlane.buffer
        val vBuffer = vPlane.buffer

        val ySize = yBuffer.remaining()
        val uSize = uBuffer.remaining()
        val vSize = vBuffer.remaining()

        val nv21Buffer = ByteArray(ySize + uSize + vSize)

        yBuffer.get(nv21Buffer, 0, ySize)

        val vOffset = ySize
        vBuffer.get(nv21Buffer, vOffset, vSize)

        val uOffset = ySize + vSize
        uBuffer.get(nv21Buffer, uOffset, uSize)

        try {
            nativeLib.processFrame(width, height, nv21Buffer)
        } catch (e: Exception) {
            Log.e("CameraController", "Frame processing failed: ${e.message}")
        }
    }

    fun close() {
        cameraExecutor.shutdown()
        cameraProvider?.unbindAll()
    }
}
