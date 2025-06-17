package com.auto_sms.callsms

import android.util.Log
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactContextBaseJavaModule
import com.facebook.react.bridge.ReactMethod
import com.facebook.react.bridge.Promise
import android.provider.Settings
import android.content.ComponentName
import android.app.NotificationManager
import android.os.Handler
import android.os.Looper
import android.content.Intent
import android.content.IntentFilter
import android.content.BroadcastReceiver
import android.content.Context
import com.facebook.react.bridge.Arguments

/**
 * LogTestModule - A React Native module for testing SMS and RCS logging functionality
 */
class LogTestModule(reactContext: ReactApplicationContext) : ReactContextBaseJavaModule(reactContext) {
    private val TAG = "LogTestModule"
    private lateinit var rcsManager: RcsAutoReplyManager
    
    init {
        rcsManager = RcsAutoReplyManager(reactContext)
    }
    
    override fun getName(): String {
        return "LogTestModule"
    }
    
    /**
     * Generate a test SMS message to verify logging functionality
     * This method doesn't actually send an SMS, but triggers the SmsReceiver
     * to simulate an incoming SMS and test the logging
     */
    @ReactMethod
    fun generateTestSms(senderNumber: String, message: String, promise: Promise) {
        Log.e(TAG, "🧪 Generating test SMS for logging from React Native")
        
        try {
            val success = rcsManager.generateTestSmsForLogging(reactApplicationContext, senderNumber, message)
            
            if (success) {
                Log.e(TAG, "✅ Test SMS generated successfully")
                promise.resolve(true)
            } else {
                Log.e(TAG, "❌ Failed to generate test SMS")
                promise.reject("ERROR", "Failed to generate test SMS")
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Exception generating test SMS: ${e.message}")
            promise.reject("EXCEPTION", e.message)
        }
    }
    
    /**
     * Output a test SMS log directly to logcat for testing
     * This is useful for testing log filtering without actually sending SMS
     */
    @ReactMethod
    fun logTestSms(senderNumber: String, message: String, promise: Promise) {
        try {
            Log.e("SmsReceiver", "LOGTAG_SMS_DETAILS: 📩📩📩 SMS MESSAGE DETAILS 📩📩📩")
            Log.e("SmsReceiver", "LOGTAG_SMS_DETAILS: ↘️ From: $senderNumber")
            Log.e("SmsReceiver", "LOGTAG_SMS_DETAILS: ↘️ Message: $message")
            Log.e("SmsReceiver", "LOGTAG_SMS_DETAILS: ↘️ Timestamp: ${System.currentTimeMillis()}")
            Log.e("SmsReceiver", "LOGTAG_SMS_DETAILS: ↘️ Message Length: ${message.length} characters")
            Log.e("SmsReceiver", "LOGTAG_SMS_DETAILS: ↘️ Display Originating Address: $senderNumber")
            Log.e("SmsReceiver", "LOGTAG_SMS_DETAILS: ↘️ Message Class: UNKNOWN")
            Log.e("SmsReceiver", "LOGTAG_SMS_DETAILS: ↘️ Message ID: 0")
            
            promise.resolve(true)
        } catch (e: Exception) {
            Log.e(TAG, "❌ Exception logging test SMS: ${e.message}")
            promise.reject("EXCEPTION", e.message)
        }
    }
    
    /**
     * Output a test RCS log directly to logcat for testing
     * This is useful for testing log filtering without actually receiving RCS
     */
    @ReactMethod
    fun logTestRcs(sender: String, message: String, promise: Promise) {
        try {
            Log.e("RcsNotification", "LOGTAG_RCS_DETAILS: 📨📨📨 RCS MESSAGE DETAILS 📨📨📨")
            Log.e("RcsNotification", "LOGTAG_RCS_DETAILS: ↘️ Sender: $sender")
            Log.e("RcsNotification", "LOGTAG_RCS_DETAILS: ↘️ Message: $message")
            Log.e("RcsNotification", "LOGTAG_RCS_DETAILS: ↘️ Package: com.google.android.apps.messaging")
            Log.e("RcsNotification", "LOGTAG_RCS_DETAILS: ↘️ Timestamp: ${System.currentTimeMillis()}")
            Log.e("RcsNotification", "LOGTAG_RCS_DETAILS: ↘️ Actions count: 3")
            Log.e("RcsNotification", "LOGTAG_RCS_DETAILS: ↘️ Notification ID: 123")
            Log.e("RcsNotification", "LOGTAG_RCS_DETAILS: ↘️ Notification Key: 0|com.google.android.apps.messaging|123|null|10123")
            Log.e("RcsNotification", "LOGTAG_RCS_DETAILS: ↘️ Is RCS: true")
            Log.e("RcsNotification", "LOGTAG_RCS_DETAILS: ↘️ Conversation ID: 0|com.google.android.apps.messaging|123|null|10123")
            
            promise.resolve(true)
        } catch (e: Exception) {
            Log.e(TAG, "❌ Exception logging test RCS: ${e.message}")
            promise.reject("EXCEPTION", e.message)
        }
    }
    
    /**
     * Test RCS auto-reply directly by simulating a message through the RcsNotificationListener
     * This bypasses the notification system and helps diagnose issues
     */
    @ReactMethod
    fun testRcsAutoReply(sender: String, message: String, promise: Promise) {
        try {
            Log.e(TAG, "🧪 Testing RCS auto-reply with direct call to RcsAutoReplyManager")
            Log.e(TAG, "   • Sender: $sender")
            Log.e(TAG, "   • Message: $message")
            
            val rcsManager = RcsAutoReplyManager(reactApplicationContext)
            
            // Process the message directly with the RcsAutoReplyManager
            val directReply = rcsManager.processMessage(sender, message)
            
            if (directReply != null) {
                Log.e(TAG, "✅ RcsAutoReplyManager generated reply: $directReply")
                rcsManager.addLogEntry(sender, message, directReply, true, true) // Set isLLM to true
                promise.resolve(directReply)
            } else {
                Log.e(TAG, "ℹ️ RcsAutoReplyManager decided not to reply")
                promise.resolve("No reply generated")
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error testing RCS auto-reply: ${e.message}")
            promise.reject("TEST_RCS_AUTO_REPLY_ERROR", "Failed to test RCS auto-reply: ${e.message}")
        }
    }
    
    /**
     * Test the MLC LLM directly for RCS
     * This function is useful for debugging RCS auto-reply with MLC LLM
     */
    @ReactMethod
    fun testRcsMLCLLM(message: String, promise: Promise) {
        try {
            Log.e(TAG, "🧪🧪🧪 TESTING RCS MLC LLM DIRECTLY 🧪🧪🧪")
            Log.e(TAG, "   • Message: $message")
            
            val rcsManager = RcsAutoReplyManager(reactApplicationContext)
            
            // First ensure RCS auto-reply is enabled
            rcsManager.setEnabled(true)
            
            // Also force LLM to be enabled
            rcsManager.setLLMEnabled(true)
            
            // Test with direct message generation
            val directResponse = rcsManager.getDefaultMessage("Test User", message)
            
            Log.e(TAG, "📝 Direct RCS LLM response: $directResponse")
            
            // Process message as if it came from notification
            val processedResponse = rcsManager.processMessage("Test User", message)
            
            Log.e(TAG, "📝 Processed RCS LLM response: $processedResponse")
            
            // Create a result object with both responses
            val resultMap = Arguments.createMap().apply {
                putString("directResponse", directResponse)
                putString("processedResponse", processedResponse ?: "No response generated")
                putBoolean("isLLMEnabled", rcsManager.isLLMEnabled())
                putBoolean("isRcsEnabled", rcsManager.isEnabled())
            }
            
            promise.resolve(resultMap)
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error testing RCS MLC LLM: ${e.message}")
            promise.reject("TEST_RCS_MLC_LLM_ERROR", "Failed to test RCS MLC LLM: ${e.message}")
        }
    }
    
    /**
     * Test forcing a dynamic RCS response with MLC LLM
     * This ensures we never get static responses
     */
    @ReactMethod
    fun testForceDynamicRcsResponse(sender: String, message: String, promise: Promise) {
        try {
            Log.e(TAG, "🔥🔥🔥 TESTING FORCED DYNAMIC RCS RESPONSE 🔥🔥🔥")
            Log.e(TAG, "   • Sender: $sender")
            Log.e(TAG, "   • Message: $message")
            
            val rcsManager = RcsAutoReplyManager(reactApplicationContext)
            
            // First ensure RCS auto-reply is enabled
            rcsManager.setEnabled(true)
            
            // Also force LLM to be enabled
            rcsManager.setLLMEnabled(true)
            
            // Force a truly dynamic response
            val dynamicResponse = rcsManager.forceDynamicMlcResponse(sender, message)
            
            Log.e(TAG, "📝 Forced dynamic response: $dynamicResponse")
            
            // Create a broadcast to test the notification listener
            val intent = Intent("com.auto_sms.TEST_RCS_AUTO_REPLY")
            intent.putExtra("sender", sender)
            intent.putExtra("message", message)
            intent.putExtra("force_dynamic", true)
            reactApplicationContext.sendBroadcast(intent)
            
            promise.resolve(dynamicResponse)
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error testing forced dynamic RCS response: ${e.message}")
            promise.reject("TEST_FORCE_DYNAMIC_ERROR", "Failed to test forced dynamic response: ${e.message}")
        }
    }
    
} 