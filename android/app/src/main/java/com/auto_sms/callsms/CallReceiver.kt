package com.auto_sms.callsms

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.database.Cursor
import android.provider.CallLog
import android.telephony.SmsManager
import android.telephony.TelephonyManager
import android.util.Log
import androidx.core.content.ContextCompat
import java.util.*
import android.app.PendingIntent
import android.os.Build

/**
 * BroadcastReceiver to handle call events even when the app is killed
 */
class CallReceiver : BroadcastReceiver() {
    private val TAG = "CallReceiver"
    private var lastPhoneState = TelephonyManager.CALL_STATE_IDLE
    private var latestIncomingNumber: String? = null
    private var callStart: Long = 0L
    private val DEFAULT_MESSAGE = "I missed your call. I'll get back to you as soon as possible."
    
    // Constants for AsyncStorage keys
    private val AUTO_SMS_ENABLED_KEY = "@AutoSMS:Enabled"
    private val AI_SMS_ENABLED_KEY = "@AutoSMS:AIEnabled"
    private val INITIAL_SMS_MESSAGE_KEY = "@AutoSMS:InitialMessage"
    private val SMS_HISTORY_STORAGE_KEY = "@AutoSMS:SmsHistory"

    override fun onReceive(context: Context, intent: Intent) {
        // Check if auto SMS is enabled
        if (!isAutoSmsEnabled(context)) {
            Log.d(TAG, "Auto SMS is disabled. Ignoring call event.")
            return
        }

        if (intent.action == TelephonyManager.ACTION_PHONE_STATE_CHANGED) {
            handleCallState(context, intent)
        }
    }

    /**
     * Handle call state changes
     */
    private fun handleCallState(context: Context, intent: Intent) {
        if (!hasRequiredPermissions(context)) {
            Log.e(TAG, "Missing required permissions for call handling")
            return
        }

        val stateStr = intent.getStringExtra(TelephonyManager.EXTRA_STATE)
        val phoneNumber = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER)
        
        when (stateStr) {
            TelephonyManager.EXTRA_STATE_RINGING -> {
                if (phoneNumber != null) {
                    Log.d(TAG, "Incoming call from: $phoneNumber")
                    latestIncomingNumber = phoneNumber
                    callStart = System.currentTimeMillis()
                    lastPhoneState = TelephonyManager.CALL_STATE_RINGING
                }
            }
            TelephonyManager.EXTRA_STATE_OFFHOOK -> {
                lastPhoneState = TelephonyManager.CALL_STATE_OFFHOOK
            }
            TelephonyManager.EXTRA_STATE_IDLE -> {
                // Only process as missed call if:
                // 1. Last state was RINGING (not from app startup)
                // 2. We have a phone number
                // 3. The call started after the app was running (not old calls)
                if (lastPhoneState == TelephonyManager.CALL_STATE_RINGING && 
                    latestIncomingNumber != null && 
                    callStart > 0) {
                    
                    // Calculate how long the call was ringing
                    val missedCallDuration = System.currentTimeMillis() - callStart
                    
                    Log.d(TAG, "Missed call detected from: $latestIncomingNumber, duration: $missedCallDuration ms")
                    
                    // Send SMS only for real missed calls (not app startup events)
                    if (missedCallDuration > 1000) { // Only if call rang for at least 1 second
                        // Send SMS for the missed call
                        sendSmsForMissedCall(context, latestIncomingNumber!!)
                        
                        // Also check call log to confirm it was missed (belt and suspenders approach)
                        scheduleCallLogCheck(context)
                    } else {
                        Log.d(TAG, "Ignoring potential false missed call (too short duration: $missedCallDuration ms)")
                    }
                }
                lastPhoneState = TelephonyManager.CALL_STATE_IDLE
            }
        }
    }

    /**
     * Schedule a call log check to verify missed calls
     * This is an additional check in case the PHONE_STATE broadcast is unreliable
     */
    private fun scheduleCallLogCheck(context: Context) {
        // Start the CallLogCheckService to check for missed calls
        val serviceIntent = Intent(context, CallLogCheckService::class.java)
        context.startService(serviceIntent)
    }

    /**
     * Send SMS for a missed call
     */
    private fun sendSmsForMissedCall(context: Context, phoneNumber: String) {
        if (!hasRequiredPermissions(context)) {
            Log.e(TAG, "Missing permissions for sending SMS")
            return
        }

        try {
            Log.d(TAG, "📞 Sending static missed call SMS to $phoneNumber")
            
            // Always use the static message for missed calls
            val message = "I missed your call. I'll get back to you as soon as possible."
            
            val smsManager = SmsManager.getDefault()
            
            // Split message if it's too long
            val parts = smsManager.divideMessage(message)
            
            // Add FLAG_IMMUTABLE for Android 12+ compatibility
            val pendingIntentFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }
            
            // Prepare PendingIntent for SMS
            val sentIntent = Intent("android.provider.Telephony.SMS_SENT")
            val sentPI = PendingIntent.getBroadcast(context, 0, sentIntent, pendingIntentFlags)
            
            // Send SMS
            if (parts.size > 1) {
                // Create PendingIntent array for multipart SMS
                val sentIntents = ArrayList<PendingIntent>().apply {
                    repeat(parts.size) { i ->
                        add(PendingIntent.getBroadcast(context, i, sentIntent, pendingIntentFlags))
                    }
                }
                smsManager.sendMultipartTextMessage(phoneNumber, null, parts, sentIntents, null)
            } else {
                smsManager.sendTextMessage(phoneNumber, null, message, sentPI, null)
            }
            
            Log.d(TAG, "✅ Missed call SMS sent successfully to $phoneNumber")
            
            // Store the number for tracking
            storeMissedCallNumber(context, phoneNumber)
            
            // Save to history in shared preferences
            saveSmsToHistory(context, phoneNumber, message, true)
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error sending SMS for missed call: ${e.message}", e)
            saveSmsToHistory(context, phoneNumber, DEFAULT_MESSAGE, false, e.message)
        }
    }
    
    /**
     * Store the missed call number for tracking
     */
    private fun storeMissedCallNumber(context: Context, phoneNumber: String) {
        try {
            val sharedPrefs = context.getSharedPreferences("AutoSmsPrefs", Context.MODE_PRIVATE)
            val missedCallNumbers = sharedPrefs.getStringSet("missedCallNumbers", HashSet()) ?: HashSet()
            
            // Add timestamp to track when the missed call happened
            val timestamp = System.currentTimeMillis()
            val newMissedCallNumbers = HashSet(missedCallNumbers)
            
            newMissedCallNumbers.add("$phoneNumber:$timestamp")
            
            // Save the updated set
            sharedPrefs.edit().putStringSet("missedCallNumbers", newMissedCallNumbers).apply()
            
            Log.d(TAG, "📞 Stored missed call number for auto-reply tracking: $phoneNumber")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error storing missed call number: ${e.message}")
        }
    }

    /**
     * Save SMS to history using SharedPreferences
     */
    private fun saveSmsToHistory(
        context: Context, 
        phoneNumber: String, 
        message: String, 
        success: Boolean, 
        errorMsg: String? = null
    ) {
        try {
            val sharedPrefs = context.getSharedPreferences("AutoSmsPrefs", Context.MODE_PRIVATE)
            val historyJson = sharedPrefs.getString(SMS_HISTORY_STORAGE_KEY, "[]") ?: "[]"
            
            // Create new history entry
            val timestamp = System.currentTimeMillis()
            val errorPart = if (errorMsg != null) ", \"error\": \"${errorMsg.replace("\"", "\\\"")}\"" else ""
            val newEntryJson = """
                {
                    "id": "$phoneNumber-$timestamp",
                    "phoneNumber": "$phoneNumber",
                    "message": "${message.replace("\"", "\\\"")}",
                    "status": "${if (success) "SENT" else "FAILED"}",
                    "timestamp": $timestamp$errorPart
                }
            """.trimIndent()
            
            // Add to existing history (simple approach - prepend)
            val updatedHistoryJson = if (historyJson == "[]") {
                "[$newEntryJson]"
            } else {
                "[${newEntryJson},${historyJson.substring(1)}"
            }
            
            // Save updated history
            sharedPrefs.edit().putString(SMS_HISTORY_STORAGE_KEY, updatedHistoryJson).apply()
            
        } catch (e: Exception) {
            Log.e(TAG, "Error saving SMS to history: ${e.message}", e)
        }
    }

    /**
     * Check if Auto SMS is enabled using SharedPreferences
     */
    private fun isAutoSmsEnabled(context: Context): Boolean {
        val sharedPrefs = context.getSharedPreferences("AutoSmsPrefs", Context.MODE_PRIVATE)
        // Default to true if not set
        return sharedPrefs.getBoolean(AUTO_SMS_ENABLED_KEY, true)
    }

    /**
     * Check if all required permissions are granted
     */
    private fun hasRequiredPermissions(context: Context): Boolean {
        val readCallLog = ContextCompat.checkSelfPermission(context, 
            Manifest.permission.READ_CALL_LOG) == PackageManager.PERMISSION_GRANTED
        val readPhoneState = ContextCompat.checkSelfPermission(context, 
            Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED
        val sendSms = ContextCompat.checkSelfPermission(context, 
            Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED

        return readCallLog && readPhoneState && sendSms
    }
} 