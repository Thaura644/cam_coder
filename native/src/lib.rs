use jni::JNIEnv;
use jni::objects::JClass;
use jni::sys::{jfloatArray, jlong, jfloat, jint};
use android_logger::Config;
use log::LevelFilter;
use std::sync::Mutex;
use lazy_static::lazy_static;
use glam::{Mat4, Quat, Vec3};

lazy_static! {
    static ref FUSION_ENGINE: Mutex<SensorFusionEngine> = Mutex::new(SensorFusionEngine::new());
}

struct SensorFusionEngine {
    orientation: Quat,
    last_gyro_timestamp: jlong,
    alpha: f32,
}

impl SensorFusionEngine {
    fn new() -> Self {
        Self {
            orientation: Quat::IDENTITY,
            last_gyro_timestamp: 0,
            alpha: 0.98,
        }
    }

    fn update_gyro(&mut self, timestamp: jlong, x: f32, y: f32, z: f32) {
        if self.last_gyro_timestamp > 0 {
            let dt = (timestamp - self.last_gyro_timestamp) as f32 / 1_000_000_000.0;
            // Filter out outliers or large gaps
            if dt > 0.0 && dt < 0.1 {
                // Gyro vector is in radians/s. Multiply by dt to get incremental rotation.
                // Note: Android coordinates (x: right, y: up, z: out)
                let rotation_vector = Vec3::new(x, y, z) * dt;
                let angle = rotation_vector.length();
                if angle > 0.00001 {
                    let axis = rotation_vector / angle;
                    let incremental_rotation = Quat::from_axis_angle(axis, angle);
                    self.orientation = (self.orientation * incremental_rotation).normalize();
                }
            }
        }
        self.last_gyro_timestamp = timestamp;
    }

    fn update_accel(&mut self, _timestamp: jlong, ax: f32, ay: f32, az: f32) {
        let gravity = Vec3::new(ax, ay, az);
        if gravity.length() < 0.1 { return; }
        let gravity = gravity.normalize();

        // Calculate pitch and roll from gravity vector
        // roll = atan2(x, sqrt(y^2 + z^2))
        // pitch = atan2(-y, z)
        let roll = ax.atan2((ay * ay + az * az).sqrt());
        let pitch = (-ay).atan2(az);

        // This gives us the rotation needed to align the device with gravity
        let accel_orientation = Quat::from_euler(glam::EulerRot::YXZ, 0.0, pitch, roll);
        
        // Complementary filter: adjust the gyro-integrated orientation towards the accel-based tilt
        self.orientation = self.orientation.slerp(accel_orientation, 1.0 - self.alpha).normalize();
    }

    fn get_matrix(&self) -> Mat4 {
        // We want to counteract the current orientation to keep the image "level"
        let (_, pitch, roll) = self.orientation.to_euler(glam::EulerRot::YXZ);
        
        // Stabilization rotation is the inverse of the current tilt
        let stabilization_rot = Quat::from_euler(glam::EulerRot::YXZ, 0.0, -pitch, -roll);
        
        // Dynamic crop: calculate how much we've deviated from center
        // The more we rotate, the more "black borders" would appear without cropping.
        // For a 90% crop, we can handle ~15-20 degrees of tilt.
        let deviation = (pitch.abs().max(roll.abs())).to_degrees();
        let base_crop = 0.95;
        let dynamic_crop = (base_crop - (deviation / 100.0).min(0.15)) as f32;
        
        let crop_scale = Vec3::new(dynamic_crop, dynamic_crop, 1.0);
        Mat4::from_scale(crop_scale) * Mat4::from_quat(stabilization_rot)
    }
}

#[no_mangle]
pub extern "system" fn Java_com_example_stablecamera_NativeLib_initSensorFusion(
    _env: JNIEnv,
    _class: JClass,
) {
    android_logger::init_once(
        Config::default().with_max_level(LevelFilter::Trace),
    );
    let mut engine = FUSION_ENGINE.lock().unwrap();
    *engine = SensorFusionEngine::new();
}

#[no_mangle]
pub extern "system" fn Java_com_example_stablecamera_NativeLib_feedGyro(
    _env: JNIEnv,
    _class: JClass,
    timestamp: jlong,
    x: jfloat,
    y: jfloat,
    z: jfloat,
) {
    if let Ok(mut engine) = FUSION_ENGINE.lock() {
        engine.update_gyro(timestamp, x, y, z);
    }
}

#[no_mangle]
pub extern "system" fn Java_com_example_stablecamera_NativeLib_feedAccel(
    _env: JNIEnv,
    _class: JClass,
    timestamp: jlong,
    x: jfloat,
    y: jfloat,
    z: jfloat,
) {
    if let Ok(mut engine) = FUSION_ENGINE.lock() {
        engine.update_accel(timestamp, x, y, z);
    }
}

#[no_mangle]
pub extern "system" fn Java_com_example_stablecamera_NativeLib_getStabilizationMatrix(
    env: JNIEnv,
    _class: JClass,
    _timestamp: jlong,
) -> jfloatArray {
    let array = if let Ok(engine) = FUSION_ENGINE.lock() {
        engine.get_matrix().to_cols_array()
    } else {
        Mat4::IDENTITY.to_cols_array()
    };

    let output = env.new_float_array(16).unwrap();
    env.set_float_array_region(&output, 0, &array).unwrap();
    output.into_raw()
}

#[no_mangle]
pub extern "system" fn Java_com_example_stablecamera_NativeLib_processFrame(
    _env: JNIEnv,
    _class: JClass,
    width: jint,
    height: jint,
    data: *mut u8,
) {
    // Basic Grayscale Filter implementation for NV21/YUV_420_888 (just process the Y plane)
    // The Y plane is the first width * height bytes.
    let size = (width * height) as usize;
    unsafe {
        let pixels = std::slice::from_raw_parts_mut(data, size);
        for pixel in pixels.iter_mut() {
            // In a real app, we might do more complex processing here, 
            // but for now, we'll just demonstrate writing to the buffer.
            // Let's slightly increase brightness as a "filter"
            *pixel = (*pixel as u16 + 10).min(255) as u8;
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_initial_matrix() {
        let engine = SensorFusionEngine::new();
        let mat = engine.get_matrix();
        assert!((mat.col(0).x - 0.85).abs() < 0.001);
        assert!((mat.col(1).y - 0.85).abs() < 0.001);
        assert!((mat.col(2).z - 1.0).abs() < 0.001);
    }

    #[test]
    fn test_gyro_integration() {
        let mut engine = SensorFusionEngine::new();
        engine.update_gyro(1, 0.0, 0.0, std::f32::consts::PI);
        engine.update_gyro(1_000_000_001, 0.0, 0.0, std::f32::consts::PI);

        let (_, _, roll) = engine.orientation.to_euler(glam::EulerRot::YXZ);
        println!("Roll: {}", roll);
        assert!((roll.abs() - std::f32::consts::PI).abs() < 0.1);
    }
}
