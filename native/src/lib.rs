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
            if dt > 0.0 && dt < 1.1 {
                let rotation_vector = Vec3::new(x, y, z) * dt;
                let angle = rotation_vector.length();
                if angle > 0.0001 {
                    let axis = rotation_vector / angle;
                    let incremental_rotation = Quat::from_axis_angle(axis, angle);
                    self.orientation = (self.orientation * incremental_rotation).normalize();
                }
            }
        }
        self.last_gyro_timestamp = timestamp;
    }

    fn update_accel(&mut self, _timestamp: jlong, ax: f32, ay: f32, az: f32) {
        let _gravity = Vec3::new(ax, ay, az).normalize();
        let roll = ay.atan2(ax) - std::f32::consts::PI / 2.0;
        let pitch = az.atan2((ax*ax + ay*ay).sqrt());
        let accel_orientation = Quat::from_euler(glam::EulerRot::YXZ, 0.0, pitch, roll);
        self.orientation = self.orientation.slerp(accel_orientation, 1.0 - self.alpha).normalize();
    }

    fn get_matrix(&self) -> Mat4 {
        let (_, pitch, roll) = self.orientation.to_euler(glam::EulerRot::YXZ);
        let stabilization_rot = Quat::from_euler(glam::EulerRot::YXZ, 0.0, -pitch, -roll);
        let crop_factor = 0.85;
        let crop_scale = Vec3::new(crop_factor, crop_factor, 1.0);
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
    _width: jint,
    _height: jint,
    _data: *mut u8, // Use raw pointer for FFI safety
) {
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
