# Stable Camera with Privacy Filter

This project aims to replicate flagship-level camera stabilization and privacy features (inspired by the Samsung S23 Ultra) using a modern Android stack combined with high-performance Rust for sensor fusion and image processing.

## Key Features

1.  **Super Steady Stabilization:**
    *   Powered by a high-performance **Rust** sensor fusion engine.
    *   Uses a **Complementary Filter** with quaternion math to track device orientation (roll and pitch) in real-time.
    *   Applies a real-time **GLSL vertex shader** transformation to the camera preview stream, providing horizon leveling and shake reduction.
    *   Implements a dynamic crop factor to ensure smooth, black-border-free stabilization.

2.  **Privacy Filter Overlay:**
    *   **Narrow Viewing Angle:** A software-based radial gradient effect that darkens screen edges, making it harder for bystanders to see sensitive content.
    *   **Rainbow Lights:** An aesthetic, flagship-inspired indicator strip at the top of the screen when privacy mode is active.
    *   **Per-App Protection:** Includes an automated monitor that activates the privacy filter when the user switches to specific "protected" apps (e.g., banking or settings).

3.  **Modern Architecture:**
    *   **UI:** Built entirely with **Jetpack Compose**.
    *   **NDK Integration:** Seamlessly bridges Kotlin and Rust using JNI and `jniLibs`.
    *   **Lifecycle Awareness:** Robust management of camera, sensors, and background services.

## Project Structure

*   `app/`: Android application source (Kotlin + Compose).
*   `native/`: Rust library source for sensor fusion and math.
*   `app/src/main/jniLibs/`: Compiled Rust binaries for Android.

## Setup & Development

### Prerequisites
*   Android SDK (Target API 35)
*   Rust + Cargo
*   `cargo-ndk` (for compiling the native library)
*   Android NDK (r27 or later)

### Building
1.  **Native Library:**
    ```bash
    cd native
    cargo ndk -t aarch64-linux-android build
    cp target/aarch64-linux-android/debug/libstable_camera_native.so ../app/src/main/jniLibs/arm64-v8a/
    ```
2.  **Android App:**
    ```bash
    ./gradlew assembleDebug
    ```

## Future Roadmap
*   Support for optical flow-based translational stabilization.
*   Advanced AI super-resolution for zoom using LiteRT (TensorFlow Lite).
*   Pro Mode features: Manual ISO, Shutter Speed, and RAW (DNG) capture.
