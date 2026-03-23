# Native methods
-keepclasseswithmembernames class * {
    native <methods>;
}

# JNI bridge
-keep class com.example.stablecamera.NativeLib { *; }

# Compose rules
-keepclassmembers class androidx.compose.ui.platform.AndroidComposeView {
    void onConfigurationChanged(android.content.res.Configuration);
}
