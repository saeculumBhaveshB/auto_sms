package com.auto_sms.callsms

import android.util.Log
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactContextBaseJavaModule
import com.facebook.react.bridge.ReactMethod
import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.WritableMap
import com.facebook.react.bridge.Arguments

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
            promise.resolve(isEnabled)
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error checking notification listener: ${e.message}")
            promise.reject("PERMISSION_ERROR", "Failed to check notification listener: ${e.message}")
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
     * Check all required permissions for RCS functionality
     */
    @ReactMethod
    fun checkRcsPermissions(promise: Promise) {
        try {
            val result = Arguments.createMap()
            val notificationListenerEnabled = RcsPermissionHelper.isNotificationListenerEnabled(reactApplicationContext)
            val isDefaultSmsApp = RcsPermissionHelper.isDefaultSmsApp(reactApplicationContext)
            
            result.putBoolean("notificationListenerEnabled", notificationListenerEnabled)
            result.putBoolean("isDefaultSmsApp", isDefaultSmsApp)
            result.putBoolean("allPermissionsGranted", notificationListenerEnabled)
            
            promise.resolve(result)
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error checking RCS permissions: ${e.message}")
            promise.reject("PERMISSION_ERROR", "Failed to check RCS permissions: ${e.message}")
        }
    }
} 