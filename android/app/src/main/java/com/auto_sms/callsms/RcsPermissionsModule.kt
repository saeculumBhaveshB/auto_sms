package com.auto_sms.callsms

import android.util.Log
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactContextBaseJavaModule
import com.facebook.react.bridge.ReactMethod
import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.WritableMap
import com.facebook.react.bridge.Arguments
import android.content.Intent
import android.provider.Settings
import android.app.Activity
import android.os.Build
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

/**
 * React Native module for managing RCS permissions
 */
class RcsPermissionsModule(reactContext: ReactApplicationContext) : ReactContextBaseJavaModule(reactContext) {
    private val TAG = "RcsPermissionsModule"
    
    override fun getName(): String {
        return "RcsPermissions"
    }
    
    /**
     * Check if notification listener permission is granted
     */
    @ReactMethod
    fun isNotificationListenerEnabled(promise: Promise) {
        try {
            val isEnabled = RcsPermissionHelper.isNotificationListenerEnabled(reactApplicationContext)
            
            // Double check by directly querying the system settings
            val packageName = reactApplicationContext.packageName
            val listenerString = Settings.Secure.getString(
                reactApplicationContext.contentResolver,
                "enabled_notification_listeners"
            )
            
            // If the package name is in the enabled listeners string, it's definitely enabled
            val isDefinitelyEnabled = listenerString?.contains(packageName) == true
            
            // Log the results for debugging
            Log.e(TAG, "📊 Notification listener check results:")
            Log.e(TAG, "📊 Helper method result: $isEnabled")
            Log.e(TAG, "📊 Direct settings check: $isDefinitelyEnabled")
            Log.e(TAG, "📊 Enabled listeners: $listenerString")
            
            // Return true if either check passes
            promise.resolve(isEnabled || isDefinitelyEnabled)
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error checking notification listener: ${e.message}")
            promise.reject("PERMISSION_ERROR", "Failed to check notification listener: ${e.message}")
        }
    }
    
    /**
     * Check if notification listener is enabled directly from system settings
     */
    @ReactMethod
    fun isNotificationListenerEnabledDirect(promise: Promise) {
        try {
            val packageName = reactApplicationContext.packageName
            val listenerString = Settings.Secure.getString(
                reactApplicationContext.contentResolver,
                "enabled_notification_listeners"
            )
            
            Log.e(TAG, "📊 Direct notification listener check")
            Log.e(TAG, "📊 Package name: $packageName")
            Log.e(TAG, "📊 Enabled listeners: $listenerString")
            
            val isEnabled = listenerString?.contains(packageName) == true
            promise.resolve(isEnabled)
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error checking notification listener directly: ${e.message}")
            promise.reject("PERMISSION_ERROR", "Failed to check notification listener directly: ${e.message}")
        }
    }
    
    /**
     * Open notification listener settings
     */
    @ReactMethod
    fun openNotificationListenerSettings(promise: Promise) {
        try {
            RcsPermissionHelper.openNotificationListenerSettings(reactApplicationContext)
            promise.resolve(true)
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error opening notification listener settings: ${e.message}")
            promise.reject("SETTINGS_ERROR", "Failed to open notification settings: ${e.message}")
        }
    }
    
    /**
     * Check if app is default SMS app
     */
    @ReactMethod
    fun isDefaultSmsApp(promise: Promise) {
        try {
            val isDefault = RcsPermissionHelper.isDefaultSmsApp(reactApplicationContext)
            promise.resolve(isDefault)
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error checking default SMS app: ${e.message}")
            promise.reject("PERMISSION_ERROR", "Failed to check default SMS app: ${e.message}")
        }
    }
    
    /**
     * Open default SMS app settings
     */
    @ReactMethod
    fun openDefaultSmsSettings(promise: Promise) {
        try {
            RcsPermissionHelper.openDefaultSmsSettings(reactApplicationContext)
            promise.resolve(true)
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error opening default SMS app settings: ${e.message}")
            promise.reject("SETTINGS_ERROR", "Failed to open default SMS settings: ${e.message}")
        }
    }
    
    /**
     * Check if notification permission is granted (for Android 13+)
     */
    @ReactMethod
    fun isNotificationPermissionGranted(promise: Promise) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val granted = ContextCompat.checkSelfPermission(
                    reactApplicationContext,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
                promise.resolve(granted)
            } else {
                // Notification permission is implicitly granted on older Android versions
                promise.resolve(true)
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error checking notification permission: ${e.message}")
            promise.reject("PERMISSION_ERROR", "Failed to check notification permission: ${e.message}")
        }
    }
    
    /**
     * Request notification permission (for Android 13+)
     */
    @ReactMethod
    fun requestNotificationPermission(promise: Promise) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val activity = currentActivity
                if (activity != null) {
                    ActivityCompat.requestPermissions(
                        activity,
                        arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                        NOTIFICATION_PERMISSION_REQUEST_CODE
                    )
                    promise.resolve(true)
                } else {
                    Log.e(TAG, "❌ Activity is null when requesting notification permission")
                    promise.reject("ACTIVITY_ERROR", "Activity is null when requesting notification permission")
                }
            } else {
                // Notification permission is implicitly granted on older Android versions
                promise.resolve(true)
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error requesting notification permission: ${e.message}")
            promise.reject("PERMISSION_ERROR", "Failed to request notification permission: ${e.message}")
        }
    }
    
    /**
     * Check all required permissions for RCS functionality
     */
    @ReactMethod
    fun checkRcsPermissions(promise: Promise) {
        try {
            val result = Arguments.createMap()
            val notificationListenerEnabled = RcsPermissionHelper.isNotificationListenerEnabled(reactApplicationContext)
            val isDefaultSmsApp = RcsPermissionHelper.isDefaultSmsApp(reactApplicationContext)
            
            // Check notification permission for Android 13+
            val notificationPermissionGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(
                    reactApplicationContext,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
            } else {
                true // Implicitly granted on older Android versions
            }
            
            result.putBoolean("notificationListenerEnabled", notificationListenerEnabled)
            result.putBoolean("isDefaultSmsApp", isDefaultSmsApp)
            result.putBoolean("notificationPermissionGranted", notificationPermissionGranted)
            result.putBoolean("allPermissionsGranted", 
                notificationListenerEnabled && notificationPermissionGranted)
            
            promise.resolve(result)
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error checking RCS permissions: ${e.message}")
            promise.reject("PERMISSION_ERROR", "Failed to check RCS permissions: ${e.message}")
        }
    }
    
    companion object {
        private const val NOTIFICATION_PERMISSION_REQUEST_CODE = 1001
    }
} 