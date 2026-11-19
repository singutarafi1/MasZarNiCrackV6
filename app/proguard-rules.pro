# Add project specific ProGuard rules here.
# Keep JNI bridge class and methods for dynamic RegisterNatives in libjin_usb.so
-keep class com.example.protocol.JinUsbNative { *; }
-keepclasseswithmembernames class * {
    native <methods>;
}
-keep class com.example.security.** { *; }
