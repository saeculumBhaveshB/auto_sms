package com.auto_sms.callsms

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.text.TextUtils
import android.util.Log
import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactContextBaseJavaModule
import com.facebook.react.bridge.ReactMethod

class PermissionsManager(reactContext: ReactApplicationContext) : ReactContextBaseJavaModule(reactContext) {
    private val TAG = "PermissionsManager"
    
    override fun getName(): String {
        return "PermissionsManager"
    }
    
    /**
     * Check if a specific permission is granted
     */
    @ReactMethod
    fun checkPermission(permissionType: String, promise: Promise) {
        try {
            when (permissionType) {
                "NOTIFICATION_LISTENER" -> {
                    val isEnabled = isNotificationListenerEnabled()
                    promise.resolve(isEnabled)
                }
                "RECEIVE_SENSITIVE_NOTIFICATIONS" -> {
                    // This would require Android 15+ specific code
                    // For now, assume it's granted on older Android versions
                    promise.resolve(true)
                }
                else -> {
                    Log.w(TAG, "Unknown permission type: $permissionType")
                    promise.resolve(false)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking permission: ${e.message}")
            promise.reject("CHECK_PERMISSION_ERROR", "Failed to check permission: ${e.message}")
        }
    }
    
    /**
     * Request a specific permission
     */
    @ReactMethod
    fun requestPermission(permissionType: String, promise: Promise) {
        try {
            when (permissionType) {
                "NOTIFICATION_LISTENER" -> {
                    openNotificationListenerSettings()
                    promise.resolve(true)
                }
                "RECEIVE_SENSITIVE_NOTIFICATIONS" -> {
                    // This would require Android 15+ specific code
                    // For now, just open notification settings
                    openNotificationSettings()
                    promise.resolve(true)
                }
                else -> {
                    Log.w(TAG, "Unknown permission type: $permissionType")
                    promise.resolve(false)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error requesting permission: ${e.message}")
            promise.reject("REQUEST_PERMISSION_ERROR", "Failed to request permission: ${e.message}")
        }
    }
    
    /**
     * Open notification listener settings
     */
    @ReactMethod
    fun openNotificationListenerSettings(promise: Promise) {
        try {
            openNotificationListenerSettings()
            promise.resolve(true)
        } catch (e: Exception) {
            Log.e(TAG, "Error opening notification listener settings: ${e.message}")
            promise.reject("OPEN_NOTIFICATION_LISTENER_SETTINGS_ERROR", "Failed to open notification listener settings: ${e.message}")
        }
    }
    
    /**
     * Check if notification listener is enabled
     */
    private fun isNotificationListenerEnabled(): Boolean {
        val context = reactApplicationContext
        val cn = ComponentName(context, RcsNotificationListener::class.java)
        val flat = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
        return flat != null && flat.contains(cn.flattenToString())
    }
    
    /**
     * Open notification listener settings
     */
    private fun openNotificationListenerSettings() {
        val intent = Intent()
        intent.action = Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        reactApplicationContext.startActivity(intent)
    }
    
    /**
     * Open general notification settings
     */
    private fun openNotificationSettings() {
        val intent = Intent()
        intent.action = Settings.ACTION_APP_NOTIFICATION_SETTINGS
        intent.putExtra(Settings.EXTRA_APP_PACKAGE, reactApplicationContext.packageName)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        reactApplicationContext.startActivity(intent)
    }
} 