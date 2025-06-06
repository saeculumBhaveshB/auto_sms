package com.auto_sms.callsms

import android.content.Context
import android.util.Log
import com.facebook.react.bridge.*

class AutoReplyModule(reactContext: ReactApplicationContext) : ReactContextBaseJavaModule(reactContext) {
    private val TAG = "AutoReplyModule"
    
    // Auto-reply feature keys
    private val AUTO_REPLY_ENABLED_KEY = "@AutoSMS:AutoReplyEnabled"
    
    override fun getName(): String {
        return "AutoReplyModule"
    }
    
    @ReactMethod
    fun isAutoReplyEnabled(promise: Promise) {
        try {
            val sharedPrefs = reactApplicationContext.getSharedPreferences("AutoSmsPrefs", Context.MODE_PRIVATE)
            val enabled = sharedPrefs.getBoolean(AUTO_REPLY_ENABLED_KEY, false)
            Log.d(TAG, "Auto-reply enabled check: $enabled")
            promise.resolve(enabled)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting auto-reply enabled: ${e.message}")
            promise.reject("GET_AUTO_REPLY_ERROR", "Failed to get auto-reply enabled: ${e.message}")
        }
    }

    @ReactMethod
    fun setAutoReplyEnabled(enabled: Boolean, promise: Promise) {
        try {
            // Save setting to SharedPreferences for use when app is killed
            val sharedPrefs = reactApplicationContext.getSharedPreferences("AutoSmsPrefs", Context.MODE_PRIVATE)
            sharedPrefs.edit().putBoolean(AUTO_REPLY_ENABLED_KEY, enabled).apply()
            Log.d(TAG, "Auto-reply feature ${if (enabled) "enabled" else "disabled"}")
            promise.resolve(true)
        } catch (e: Exception) {
            Log.e(TAG, "Error setting auto-reply enabled: ${e.message}")
            promise.reject("SET_AUTO_REPLY_ERROR", "Failed to set auto-reply enabled: ${e.message}")
        }
    }
    
    @ReactMethod
    fun clearMissedCallNumbers(promise: Promise) {
        try {
            val sharedPrefs = reactApplicationContext.getSharedPreferences("AutoSmsPrefs", Context.MODE_PRIVATE)
            sharedPrefs.edit().putStringSet("missedCallNumbers", HashSet()).apply()
            Log.d(TAG, "Cleared missed call numbers")
            promise.resolve(true)
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing missed call numbers: ${e.message}")
            promise.reject("CLEAR_MISSED_CALLS_ERROR", "Failed to clear missed call numbers: ${e.message}")
        }
    }
    
    @ReactMethod
    fun getMissedCallNumbers(promise: Promise) {
        try {
            val sharedPrefs = reactApplicationContext.getSharedPreferences("AutoSmsPrefs", Context.MODE_PRIVATE)
            val missedCallNumbers = sharedPrefs.getStringSet("missedCallNumbers", HashSet()) ?: HashSet()
            
            val resultArray = Arguments.createArray()
            val currentTime = System.currentTimeMillis()
            val twentyFourHoursAgo = currentTime - (24 * 60 * 60 * 1000)
            
            for (entry in missedCallNumbers) {
                val parts = entry.split(":", limit = 2)
                if (parts.size == 2) {
                    val number = parts[0]
                    val timestamp = parts[1].toLongOrNull() ?: 0
                    
                    // Only include numbers from the last 24 hours
                    if (timestamp > twentyFourHoursAgo) {
                        val item = Arguments.createMap()
                        item.putString("phoneNumber", number)
                        item.putDouble("timestamp", timestamp.toDouble())
                        resultArray.pushMap(item)
                    }
                }
            }
            
            Log.d(TAG, "Got ${resultArray.size()} recent missed call numbers")
            promise.resolve(resultArray)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting missed call numbers: ${e.message}")
            promise.reject("GET_MISSED_CALLS_ERROR", "Failed to get missed call numbers: ${e.message}")
        }
    }
    
    @ReactMethod
    fun addListener(eventName: String) {
        // Keep track of listeners if needed
    }

    @ReactMethod
    fun removeListeners(count: Int) {
        // Remove listeners if needed
    }
} 