package com.auto_sms.callsms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.TelephonyManager
import android.util.Log
import android.telephony.SmsMessage
import android.provider.Telephony
import android.provider.CallLog
import android.content.ContentValues
import java.util.*

class CallReceiver : BroadcastReceiver() {
    private val TAG = "CallReceiver"
    private var lastState = TelephonyManager.CALL_STATE_IDLE
    private var lastIncomingNumber: String? = null
    private var callStartTime: Long = 0

    companion object {
        const val DEFAULT_MESSAGE = "I am busy, please give me some time, I will contact you."
        const val PREF_NAME = "AutoSmsPrefs"
        const val KEY_AUTO_SMS_ENABLED = "autoSmsEnabled"
    }

    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "Received intent: ${intent.action}")

        when (intent.action) {
            TelephonyManager.ACTION_PHONE_STATE_CHANGED -> {
                handleCallState(context, intent)
            }
            Telephony.Sms.Intents.SMS_RECEIVED_ACTION -> {
                // Handle incoming SMS if needed
            }
        }
    }

    private fun handleCallState(context: Context, intent: Intent) {
        val stateStr = intent.getStringExtra(TelephonyManager.EXTRA_STATE)
        val number = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER)
        
        val state = when (stateStr) {
            TelephonyManager.EXTRA_STATE_RINGING -> TelephonyManager.CALL_STATE_RINGING
            TelephonyManager.EXTRA_STATE_OFFHOOK -> TelephonyManager.CALL_STATE_OFFHOOK
            TelephonyManager.EXTRA_STATE_IDLE -> TelephonyManager.CALL_STATE_IDLE
            else -> TelephonyManager.CALL_STATE_IDLE
        }
        
        onCallStateChanged(context, state, number)
    }

    private fun onCallStateChanged(context: Context, state: Int, number: String?) {
        if (number == null) return
        
        Log.d(TAG, "Call state changed to: $state, number: $number")
        
        when (state) {
            TelephonyManager.CALL_STATE_RINGING -> {
                // Phone is ringing
                lastIncomingNumber = number
                callStartTime = System.currentTimeMillis()
            }
            TelephonyManager.CALL_STATE_OFFHOOK -> {
                // Call was answered
                lastState = state
            }
            TelephonyManager.CALL_STATE_IDLE -> {
                // Call ended or missed
                if (lastState == TelephonyManager.CALL_STATE_RINGING) {
                    // This was a missed call
                    Log.d(TAG, "Detected missed call from: $lastIncomingNumber")
                    
                    // Check if auto SMS is enabled
                    val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                    val isAutoSmsEnabled = prefs.getBoolean(KEY_AUTO_SMS_ENABLED, true)
                    
                    if (isAutoSmsEnabled && lastIncomingNumber != null) {
                        // Send SMS for the missed call
                        sendSmsForMissedCall(context, lastIncomingNumber!!)
                        
                        // Mark this call as handled in the call log
                        markMissedCallAsHandled(context, lastIncomingNumber!!)
                    }
                }
            }
        }
        
        lastState = state
    }

    private fun sendSmsForMissedCall(context: Context, phoneNumber: String) {
        Log.d(TAG, "Sending auto SMS to: $phoneNumber")
        try {
            val smsManager = android.telephony.SmsManager.getDefault()
            smsManager.sendTextMessage(phoneNumber, null, DEFAULT_MESSAGE, null, null)
            
            // Store this information for the app to retrieve
            val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            val historyJson = prefs.getString("sms_history", "[]")
            
            // We'll just store the basic info here, the app itself will handle more detailed storage
            val timeStamp = System.currentTimeMillis()
            val newEntry = "{\"phoneNumber\":\"$phoneNumber\",\"timestamp\":$timeStamp,\"status\":\"SENT\"}"
            val updatedHistory = historyJson!!.dropLast(1) + (if (historyJson == "[]") "" else ",") + newEntry + "]"
            
            prefs.edit().putString("sms_history", updatedHistory).apply()
            
            Log.d(TAG, "Auto SMS sent successfully to $phoneNumber")
        } catch (e: Exception) {
            Log.e(TAG, "Error sending SMS: ${e.message}", e)
            
            // Store failure info
            val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            val historyJson = prefs.getString("sms_history", "[]")
            
            val timeStamp = System.currentTimeMillis()
            val newEntry = "{\"phoneNumber\":\"$phoneNumber\",\"timestamp\":$timeStamp,\"status\":\"FAILED\",\"error\":\"${e.message}\"}"
            val updatedHistory = historyJson!!.dropLast(1) + (if (historyJson == "[]") "" else ",") + newEntry + "]"
            
            prefs.edit().putString("sms_history", updatedHistory).apply()
        }
    }

    private fun markMissedCallAsHandled(context: Context, phoneNumber: String) {
        // We'll mark this in our own preferences to avoid duplicates
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val handledCalls = prefs.getStringSet("handled_missed_calls", HashSet()) ?: HashSet()
        
        val newHandledCalls = HashSet(handledCalls)
        newHandledCalls.add("$phoneNumber:${System.currentTimeMillis()}")
        
        prefs.edit().putStringSet("handled_missed_calls", newHandledCalls).apply()
    }
} 