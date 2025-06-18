package com.auto_sms.callsms

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.util.Log
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactMethod
import com.facebook.react.bridge.Promise

/**
 * Helper class for checking and requesting RCS-related permissions
 */
class RcsPermissionHelper {
    companion object {
        private const val TAG = "RcsPermissionHelper"
        
        /**
         * Check if notification listener permission is granted
         */
        fun isNotificationListenerEnabled(context: Context): Boolean {
            val packageName = context.packageName
            val listenerString = Settings.Secure.getString(
                context.contentResolver,
                "enabled_notification_listeners"
            )
            
            Log.e(TAG, "📊 Checking notification listener status")
            Log.e(TAG, "📊 Package name: $packageName")
            Log.e(TAG, "📊 Enabled listeners: $listenerString")
            
            return listenerString?.contains(packageName) == true
        }
        
        /**
         * Open notification listener settings
         */
        fun openNotificationListenerSettings(context: Context) {
            try {
                Log.e(TAG, "🔑 Opening notification listener settings")
                val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                context.startActivity(intent)
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error opening notification listener settings: ${e.message}")
            }
        }
        
        /**
         * Check if app is default SMS app
         */
        fun isDefaultSmsApp(context: Context): Boolean {
            val defaultSmsPackage = Settings.Secure.getString(
                context.contentResolver,
                "sms_default_application"
            )
            
            Log.e(TAG, "📊 Checking default SMS app")
            Log.e(TAG, "📊 Package name: ${context.packageName}")
            Log.e(TAG, "📊 Default SMS app: $defaultSmsPackage")
            
            return defaultSmsPackage == context.packageName
        }
        
        /**
         * Open default SMS app settings
         */
        fun openDefaultSmsSettings(context: Context) {
            try {
                Log.e(TAG, "🔑 Opening default SMS app settings")
                val intent = Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                context.startActivity(intent)
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error opening default SMS app settings: ${e.message}")
            }
        }
        
        /**
         * Check all required permissions for RCS functionality
         * @return true if all permissions are granted
         */
        fun checkRcsPermissions(context: Context): Boolean {
            val notificationListenerEnabled = isNotificationListenerEnabled(context)
            
            Log.e(TAG, "📊 RCS Permissions Check:")
            Log.e(TAG, "📊 Notification Listener: $notificationListenerEnabled")
            
            return notificationListenerEnabled
        }
    }
} 