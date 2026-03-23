package com.example.stablecamera.camera

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.view.Surface
import androidx.lifecycle.LifecycleOwner
import java.util.Collections

class CameraController(private val context: Context) {
    private val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    private var cameraDevice: CameraDevice? = null
    private var captureSession: CameraCaptureSession? = null

    @SuppressLint("MissingPermission")
    fun openCamera(surfaceTexture: SurfaceTexture) {
        if (cameraManager.cameraIdList.isEmpty()) return

        val cameraId = cameraManager.cameraIdList[0]
        try {
            cameraManager.openCamera(cameraId, object : CameraDevice.StateCallback() {
                override fun onOpened(camera: CameraDevice) {
                    cameraDevice = camera
                    startPreview(surfaceTexture)
                }
                override fun onDisconnected(camera: CameraDevice) {
                    close()
                }
                override fun onError(camera: CameraDevice, error: Int) {
                    close()
                }
            }, null)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun startPreview(surfaceTexture: SurfaceTexture) {
        val device = cameraDevice ?: return
        val surface = Surface(surfaceTexture)

        try {
            device.createCaptureSession(
                listOf(surface),
                object : CameraCaptureSession.StateCallback() {
                    override fun onConfigured(session: CameraCaptureSession) {
                        captureSession = session
                        try {
                            val requestBuilder = device.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
                            requestBuilder.addTarget(surface)
                            session.setRepeatingRequest(requestBuilder.build(), null, null)
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                    override fun onConfigureFailed(session: CameraCaptureSession) {}
                },
                null
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun close() {
        try {
            captureSession?.stopRepeating()
            captureSession?.close()
            captureSession = null
            cameraDevice?.close()
            cameraDevice = null
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
