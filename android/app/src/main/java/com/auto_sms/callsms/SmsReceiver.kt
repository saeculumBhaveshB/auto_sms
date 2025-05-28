package com.auto_sms.callsms

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Telephony
import android.telephony.SmsManager
import android.util.Log
import com.facebook.react.ReactApplication
import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.ReactContext
import com.facebook.react.modules.core.DeviceEventManagerModule

/**
 * BroadcastReceiver to handle incoming SMS messages for AI responses
 */
class SmsReceiver : BroadcastReceiver() {
    private val TAG = "SmsReceiver"
    
    // Constants for AsyncStorage keys
    private val AI_SMS_ENABLED_KEY = "@AutoSMS:AIEnabled"
    private val SMS_HISTORY_STORAGE_KEY = "@AutoSMS:SmsHistory"
    
    // Auto-reply feature keys
    private val AUTO_REPLY_ENABLED_KEY = "@AutoSMS:AutoReplyEnabled"
    private val MISSED_CALL_NUMBERS_KEY = "missedCallNumbers"
    private val AUTO_REPLY_MESSAGE = "Yes I am"
    
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) {
            return
        }
        
        // Check if AI SMS or Auto-Reply is enabled
        val sharedPrefs = context.getSharedPreferences("AutoSmsPrefs", Context.MODE_PRIVATE)
        val aiEnabled = sharedPrefs.getBoolean(AI_SMS_ENABLED_KEY, false)
        val autoReplyEnabled = sharedPrefs.getBoolean(AUTO_REPLY_ENABLED_KEY, false)
        
        if (!aiEnabled && !autoReplyEnabled) {
            Log.d(TAG, "Neither AI SMS nor Auto-Reply is enabled. Ignoring incoming message.")
            return
        }
        
        // Process incoming SMS
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.KITKAT) {
            for (smsMessage in Telephony.Sms.Intents.getMessagesFromIntent(intent)) {
                val phoneNumber = smsMessage.originatingAddress ?: continue
                val messageBody = smsMessage.messageBody ?: continue
                
                Log.d(TAG, "Received SMS from $phoneNumber: $messageBody")
                
                // Check if this is from a number we recently sent a missed call SMS to
                if (autoReplyEnabled && wasRecentMissedCallNumber(context, phoneNumber)) {
                    Log.d(TAG, "This is a response to a missed call SMS. Sending auto-reply: $AUTO_REPLY_MESSAGE")
                    sendAutoReply(context, phoneNumber)
                }
                
                // Emit event to React Native for AI processing or display
                emitSmsReceivedEvent(context, phoneNumber, messageBody)
            }
        }
    }
    
    /**
     * Send auto-reply message "Yes I am" to a phone number
     */
    private fun sendAutoReply(context: Context, phoneNumber: String) {
        try {
            val smsManager = SmsManager.getDefault()
            
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
            smsManager.sendTextMessage(phoneNumber, null, AUTO_REPLY_MESSAGE, sentPI, null)
            
            // Save to history
            val historyItem = org.json.JSONObject().apply {
                put("phoneNumber", phoneNumber)
                put("message", AUTO_REPLY_MESSAGE)
                put("status", "SENT")
                put("type", "AUTO_REPLY")
                put("timestamp", System.currentTimeMillis())
            }
            
            saveSmsToHistory(context, historyItem)
            
            Log.d(TAG, "Auto-reply SMS sent successfully to $phoneNumber")
        } catch (e: Exception) {
            Log.e(TAG, "Error sending auto-reply SMS: ${e.message}", e)
        }
    }
    
    /**
     * Check if number was recently sent a missed call SMS
     */
    private fun wasRecentMissedCallNumber(context: Context, phoneNumber: String): Boolean {
        try {
            val sharedPrefs = context.getSharedPreferences("AutoSmsPrefs", Context.MODE_PRIVATE)
            val missedCallNumbers = sharedPrefs.getStringSet(MISSED_CALL_NUMBERS_KEY, HashSet()) ?: HashSet()
            
            // Look for the number in our stored set
            for (entry in missedCallNumbers) {
                val parts = entry.split(":", limit = 2)
                if (parts.size == 2) {
                    val number = parts[0]
                    val timestamp = parts[1].toLongOrNull() ?: 0
                    
                    // Check if number matches and it's within the last 24 hours
                    val twentyFourHoursAgo = System.currentTimeMillis() - (24 * 60 * 60 * 1000)
                    if (number == phoneNumber && timestamp > twentyFourHoursAgo) {
                        Log.d(TAG, "Found recent missed call from $phoneNumber")
                        return true
                    }
                }
            }
            
            return false
        } catch (e: Exception) {
            Log.e(TAG, "Error checking recent missed call numbers: ${e.message}", e)
            return false
        }
    }
    
    /**
     * Save SMS to history in SharedPreferences
     */
    private fun saveSmsToHistory(context: Context, historyItem: org.json.JSONObject) {
        try {
            val sharedPrefs = context.getSharedPreferences("AutoSmsPrefs", Context.MODE_PRIVATE)
            val historyJson = sharedPrefs.getString(SMS_HISTORY_STORAGE_KEY, "[]") ?: "[]"
            
            val historyArray = org.json.JSONArray(historyJson)
            historyArray.put(historyItem)
            
            // Limit history size to 100 items
            val updatedArray = org.json.JSONArray()
            val startIdx = Math.max(0, historyArray.length() - 100)
            
            for (i in startIdx until historyArray.length()) {
                updatedArray.put(historyArray.get(i))
            }
            
            sharedPrefs.edit().putString(SMS_HISTORY_STORAGE_KEY, updatedArray.toString()).apply()
        } catch (e: Exception) {
            Log.e(TAG, "Error saving SMS to history: ${e.message}", e)
        }
    }
    
    /**
     * Emit SMS received event to React Native
     */
    private fun emitSmsReceivedEvent(context: Context, phoneNumber: String, message: String) {
        try {
            val reactContext = (context.applicationContext as ReactApplication)
                .reactNativeHost
                .reactInstanceManager
                .currentReactContext
                
            if (reactContext != null) {
                val eventData = Arguments.createMap().apply {
                    putString("phoneNumber", phoneNumber)
                    putString("message", message)
                    putDouble("timestamp", System.currentTimeMillis().toDouble())
                }
                
                reactContext
                    .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter::class.java)
                    .emit("onSmsReceived", eventData)
                
                Log.d(TAG, "Emitted onSmsReceived event to React Native")
            } else {
                Log.d(TAG, "React context is null, storing message for later processing")
                // Store the message for later processing when the app is active
                saveIncomingSms(context, phoneNumber, message)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error emitting SMS received event: ${e.message}", e)
            // Store the message for later processing
            saveIncomingSms(context, phoneNumber, message)
        }
    }
    
    /**
     * Save incoming SMS to SharedPreferences for later processing
     */
    private fun saveIncomingSms(context: Context, phoneNumber: String, message: String) {
        try {
            val sharedPrefs = context.getSharedPreferences("AutoSmsPrefs", Context.MODE_PRIVATE)
            val pendingMessages = sharedPrefs.getStringSet("pendingIncomingSms", HashSet()) ?: HashSet()
            
            val newPendingMessages = HashSet(pendingMessages)
            newPendingMessages.add("$phoneNumber:$message:${System.currentTimeMillis()}")
            
            sharedPrefs.edit().putStringSet("pendingIncomingSms", newPendingMessages).apply()
            Log.d(TAG, "Saved incoming SMS for later processing")
        } catch (e: Exception) {
            Log.e(TAG, "Error saving incoming SMS: ${e.message}", e)
        }
    }
} 