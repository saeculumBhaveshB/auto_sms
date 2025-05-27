package com.auto_sms.callsms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.telephony.SmsManager
import android.util.Log
import com.auto_sms.llm.LocalLLMModule
import com.facebook.react.ReactApplication
import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.ReactContext
import com.facebook.react.modules.core.DeviceEventManagerModule
import org.json.JSONArray
import org.json.JSONObject

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
        
        Log.d(TAG, "SMS received with action: ${intent.action}")
        
        // Check if AI SMS is enabled
        val sharedPrefs = context.getSharedPreferences("AutoSmsPrefs", Context.MODE_PRIVATE)
        val aiEnabled = sharedPrefs.getBoolean(AI_SMS_ENABLED_KEY, false)
        
        Log.d(TAG, "AI SMS enabled: $aiEnabled")
        
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
                
                // Generate response using local LLM
                val response = generateLLMResponse(context, phoneNumber, messageBody)
                
                // Send auto-reply if we have a response
                if (response.isNotEmpty()) {
                    sendAutoReply(context, phoneNumber, response)
                    
                    // Save to history for tracking
                    saveMessageToHistory(context, phoneNumber, messageBody, response)
                }
                
                // Emit event to React Native
                emitSmsReceivedEvent(context, phoneNumber, messageBody)
            }
        }
    }
    
    /**
     * Generate a response using the local LLM
     */
    private fun generateLLMResponse(context: Context, phoneNumber: String, message: String): String {
        try {
            Log.d(TAG, "Attempting to generate LLM response for: $message")
            
            // Get the React Native context to access native modules
            val reactApp = context.applicationContext as? ReactApplication
            
            if (reactApp == null) {
                Log.e(TAG, "Cannot access ReactApplication")
                return getDefaultResponse()
            }
            
            // Try to get the current React context
            val reactContext = reactApp.reactNativeHost.reactInstanceManager.currentReactContext
            
            if (reactContext == null) {
                Log.e(TAG, "React context is null, using default response")
                return getDefaultResponse()
            }

            // Get the LocalLLMModule native module
            val localLLMModule = reactContext.getNativeModules().find { it is LocalLLMModule } as? LocalLLMModule
            
            if (localLLMModule == null) {
                Log.e(TAG, "LocalLLM module not found, using default response")
                return getDefaultResponse()
            }
            
            // Check if a model is loaded
            val isModelLoaded = localLLMModule.isModelLoadedSync()
            Log.d(TAG, "Model loaded: $isModelLoaded")
            
            if (!isModelLoaded) {
                Log.d(TAG, "Model is not loaded, using default response")
                return getDefaultResponse()
            }
            
            // Generate response with local LLM
            val response = localLLMModule.generateAnswerSync(message, 0.7f, 200)
            Log.d(TAG, "Generated response: $response")
            
            // Ensure response starts with "AI:" prefix
            return if (response.startsWith("AI:")) {
                response
            } else {
                "AI: $response"
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error generating LLM response: ${e.message}", e)
            return getDefaultResponse()
        }
    }
    
    /**
     * Get the default response when LLM fails
     */
    private fun getDefaultResponse(): String {
        return "AI: Sorry, I am not capable of giving this answer. Wait for a call or try with a different question."
    }
    
    /**
     * Send auto-reply SMS
     */
    private fun sendAutoReply(context: Context, phoneNumber: String, message: String) {
        try {
            Log.d(TAG, "Sending auto-reply to $phoneNumber: $message")
            val smsManager = SmsManager.getDefault()
            
            // Split message if it's too long
            val parts = smsManager.divideMessage(message)
            
            // Send SMS
            if (parts.size > 1) {
                smsManager.sendMultipartTextMessage(phoneNumber, null, parts, null, null)
            } else {
                smsManager.sendTextMessage(phoneNumber, null, message, null, null)
            }
            
            Log.d(TAG, "Auto-reply sent successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error sending auto-reply: ${e.message}", e)
        }
    }
    
    /**
     * Save message exchange to history
     */
    private fun saveMessageToHistory(context: Context, phoneNumber: String, received: String, sent: String) {
        try {
            val sharedPrefs = context.getSharedPreferences("AutoSmsPrefs", Context.MODE_PRIVATE)
            val historyJson = sharedPrefs.getString(SMS_HISTORY_STORAGE_KEY, "[]") ?: "[]"
            
            // Parse existing history
            val jsonArray = JSONArray(historyJson)
            
            // Add new exchange
            val exchange = JSONObject().apply {
                put("phoneNumber", phoneNumber)
                put("received", received)
                put("sent", sent)
                put("timestamp", System.currentTimeMillis())
            }
            
            // Add to history
            jsonArray.put(exchange)
            
            // Save updated history (keep last 100 exchanges maximum)
            val updatedJson = if (jsonArray.length() > 100) {
                val trimmedArray = JSONArray()
                for (i in (jsonArray.length() - 100) until jsonArray.length()) {
                    trimmedArray.put(jsonArray.get(i))
                }
                trimmedArray.toString()
            } else {
                jsonArray.toString()
            }
            
            sharedPrefs.edit().putString(SMS_HISTORY_STORAGE_KEY, updatedJson).apply()
            Log.d(TAG, "Saved message exchange to history")
        } catch (e: Exception) {
            Log.e(TAG, "Error saving message to history: ${e.message}", e)
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