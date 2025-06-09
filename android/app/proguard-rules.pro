# Add project specific ProGuard rules here.
# By default, the flags in this file are appended to flags specified
# in /usr/local/Cellar/android-sdk/24.3.3/tools/proguard/proguard-android.txt
# You can edit the include path and order by changing the proguardFiles
# directive in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# Add any project specific keep options here:

# React Native
-keep class com.facebook.react.** { *; }
-keep class com.facebook.hermes.** { *; }
-keep class com.facebook.jni.** { *; }

# Native Modules
-keep class com.auto_sms.** { *; }

# Keep all native methods
-keepclasseswithmembernames class * {
    native <methods>;
}

# Apache POI - keep entire package rather than specific classes
-keep class org.apache.poi.** { *; }

# TensorFlow Lite 
-keep class org.tensorflow.lite.** { *; }

# Keep important annotations
-keepattributes *Annotation*,Signature,InnerClasses

# Keep JavaScript interfaces
-keepclassmembers class * {
    @com.facebook.react.uimanager.annotations.ReactProp <methods>;
}

# Avoid warnings from OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**

# Ignore missing classes warnings
-dontwarn org.bouncycastle.**
-dontwarn org.etsi.**
-dontwarn org.openxmlformats.**
-dontwarn org.w3.**
-dontwarn org.osgi.**
