package com.auto_sms.callsms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import android.os.Build

/**
 * BroadcastReceiver to start our CallLogCheckService when device boots up
 */
class BootReceiver : BroadcastReceiver() {
    private val TAG = "BootReceiver"

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            Log.d(TAG, "Boot completed, starting service")
            
            // Check if auto SMS is enabled first
            val sharedPrefs = context.getSharedPreferences("AutoSmsPrefs", Context.MODE_PRIVATE)
            val autoSmsEnabled = sharedPrefs.getBoolean("@AutoSMS:Enabled", true)
            
            if (autoSmsEnabled) {
                // Start the service
                val serviceIntent = Intent(context, CallLogCheckService::class.java)
                
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        context.startForegroundService(serviceIntent)
                    } else {
                        context.startService(serviceIntent)
                    }
                    Log.d(TAG, "Service started after boot")
                } catch (e: Exception) {
                    Log.e(TAG, "Error starting service after boot: ${e.message}", e)
                }
            } else {
                Log.d(TAG, "Auto SMS is disabled, not starting service")
            }
        }
    }
} 