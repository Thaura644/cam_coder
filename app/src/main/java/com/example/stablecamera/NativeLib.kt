package com.example.stablecamera

class NativeLib {
    companion object {
        init {
            System.loadLibrary("stable_camera_native")
        }
    }

    /**
     * Initializes the native sensor fusion engine.
     */
    external fun initSensorFusion()

    /**
     * Feeds gyroscope data into the fusion engine.
     * @param timestamp Nanoseconds
     * @param x, y, z Angular velocity in rad/s
     */
    external fun feedGyro(timestamp: Long, x: Float, y: Float, z: Float)

    /**
     * Feeds accelerometer data into the fusion engine.
     * @param timestamp Nanoseconds
     * @param x, y, z Acceleration in m/s^2
     */
    external fun feedAccel(timestamp: Long, x: Float, y: Float, z: Float)

    /**
     * Retrieves the current stabilization matrix (4x4) for the given timestamp.
     * This matrix should be passed to the vertex shader.
     */
    external fun getStabilizationMatrix(timestamp: Long): FloatArray

    /**
     * Processes a camera frame (placeholder for future use).
     */
    external fun processFrame(width: Int, height: Int, data: ByteArray)
}
