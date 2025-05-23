package com.auto_sms.callsms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
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
    
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) {
            return
        }
        
        // Check if AI SMS is enabled
        val sharedPrefs = context.getSharedPreferences("AutoSmsPrefs", Context.MODE_PRIVATE)
        val aiEnabled = sharedPrefs.getBoolean(AI_SMS_ENABLED_KEY, false)
        
        if (!aiEnabled) {
            Log.d(TAG, "AI SMS is disabled. Ignoring incoming message.")
            return
        }
        
        // Process incoming SMS
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.KITKAT) {
            for (smsMessage in Telephony.Sms.Intents.getMessagesFromIntent(intent)) {
                val phoneNumber = smsMessage.originatingAddress ?: continue
                val messageBody = smsMessage.messageBody ?: continue
                
                Log.d(TAG, "Received SMS from $phoneNumber: $messageBody")
                
                // Emit event to React Native
                emitSmsReceivedEvent(context, phoneNumber, messageBody)
            }
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