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

/**
 * BroadcastReceiver to handle call events even when the app is killed
 */
class CallReceiver : BroadcastReceiver() {
    private val TAG = "CallReceiver"
    private var lastPhoneState = TelephonyManager.CALL_STATE_IDLE
    private var latestIncomingNumber: String? = null
    private var callStart: Long = 0L
    private val DEFAULT_MESSAGE = "I am busy, please give me some time, I will contact you."
    
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
            val sharedPrefs = context.getSharedPreferences("AutoSmsPrefs", Context.MODE_PRIVATE)
            val aiEnabled = sharedPrefs.getBoolean(AI_SMS_ENABLED_KEY, false)
            
            // Get the appropriate message
            val smsMessage = if (aiEnabled) {
                sharedPrefs.getString(INITIAL_SMS_MESSAGE_KEY, "AI: I am busy, available only for chat. How may I help you?") ?: DEFAULT_MESSAGE
            } else {
                DEFAULT_MESSAGE
            }
            
            val smsManager = SmsManager.getDefault()
            
            // Split message if it's too long
            val parts = smsManager.divideMessage(smsMessage)
            
            // Send SMS
            if (parts.size > 1) {
                smsManager.sendMultipartTextMessage(phoneNumber, null, parts, null, null)
            } else {
                smsManager.sendTextMessage(phoneNumber, null, smsMessage, null, null)
            }
            
            Log.d(TAG, "SMS sent successfully to missed call from $phoneNumber")
            
            // Save to history in shared preferences
            saveSmsToHistory(context, phoneNumber, smsMessage, true)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error sending SMS for missed call: ${e.message}", e)
            saveSmsToHistory(context, phoneNumber, DEFAULT_MESSAGE, false, e.message)
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