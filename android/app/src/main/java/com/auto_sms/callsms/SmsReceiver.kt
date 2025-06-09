package com.auto_sms.callsms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.telephony.SmsMessage
import android.util.Log
import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.WritableMap
import com.facebook.react.modules.core.DeviceEventManagerModule

/**
 * SmsReceiver - required component for being a default SMS app
 * Handles incoming SMS messages and forwards them to the React Native app
 */
class SmsReceiver : BroadcastReceiver() {
    private val TAG = "SmsReceiver"

    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "SMS received: ${intent.action}")

        if (intent.action == Telephony.Sms.Intents.SMS_RECEIVED_ACTION ||
            intent.action == Telephony.Sms.Intents.SMS_DELIVER_ACTION) {
            
            val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
            
            messages?.let { smsMessages ->
                if (smsMessages.isNotEmpty()) {
                    val sms = smsMessages[0]
                    
                    // Build the message
                    val senderPhone = sms.originatingAddress ?: ""
                    val messageBody = smsMessages.joinToString("") { it.messageBody }
                    
                    Log.d(TAG, "SMS from: $senderPhone, body: $messageBody")
                    
                    // Try to send event to React Native
                    try {
                        val reactContext = CallSmsModule.getReactContextInstance()
                        reactContext?.let {
                            sendSmsReceivedEvent(it, senderPhone, messageBody)
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error sending event to React Native: ${e.message}")
                    }
                }
            }
        }
    }
    
    /**
     * Send SMS received event to React Native
     */
    private fun sendSmsReceivedEvent(
        reactContext: ReactApplicationContext,
        phoneNumber: String,
        message: String
    ) {
        val params = Arguments.createMap().apply {
            putString("phoneNumber", phoneNumber)
            putString("message", message)
            putString("timestamp", System.currentTimeMillis().toString())
        }
        
        reactContext.getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter::class.java)
            .emit("onSmsReceived", params)
    }
} 