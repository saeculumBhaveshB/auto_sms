package com.auto_sms.callsms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import android.os.Build
import android.provider.Settings

/**
 * BroadcastReceiver to start our CallLogCheckService and initialize RCS when device boots up
 */
class BootReceiver : BroadcastReceiver() {
    private val TAG = "BootReceiver"

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            Log.d(TAG, "Boot completed, initializing services")
            
            // Check if auto SMS is enabled
            val sharedPrefs = context.getSharedPreferences("AutoSmsPrefs", Context.MODE_PRIVATE)
            val autoSmsEnabled = sharedPrefs.getBoolean("@AutoSMS:Enabled", true)
            
            // Check if RCS auto-reply is enabled
            val rcsAutoReplyEnabled = sharedPrefs.getBoolean("@AutoSMS:RcsAutoReplyEnabled", false)
            
            if (autoSmsEnabled) {
                // Start the CallLog service
                val serviceIntent = Intent(context, CallLogCheckService::class.java)
                
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        context.startForegroundService(serviceIntent)
                    } else {
                        context.startService(serviceIntent)
                    }
                    Log.d(TAG, "CallLogCheckService started after boot")
                } catch (e: Exception) {
                    Log.e(TAG, "Error starting CallLogCheckService after boot: ${e.message}", e)
                }
            } else {
                Log.d(TAG, "Auto SMS is disabled, not starting service")
            }
            
            // Notification listener service will be initialized by the system automatically
            // if it's enabled in system settings, but we can check status
            if (rcsAutoReplyEnabled) {
                checkNotificationListenerStatus(context)
            }
        }
    }
    
    /**
     * Check if the notification listener service is enabled
     * This is just for logging purposes as we can't directly start a NotificationListenerService
     */
    private fun checkNotificationListenerStatus(context: Context) {
        val packageName = context.packageName
        val serviceString = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
        
        if (serviceString != null && serviceString.contains(packageName)) {
            Log.d(TAG, "RCS Notification listener is enabled")
        } else {
            Log.w(TAG, "RCS Notification listener is not enabled in system settings")
            
            // We can't enable it automatically, but we can mark it as disabled
            val sharedPrefs = context.getSharedPreferences("AutoSmsPrefs", Context.MODE_PRIVATE)
            sharedPrefs.edit().putBoolean("@AutoSMS:RcsAutoReplyEnabled", false).apply()
        }
    }
} 