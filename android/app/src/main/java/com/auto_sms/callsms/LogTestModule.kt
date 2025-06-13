package com.auto_sms.callsms

import android.util.Log
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactContextBaseJavaModule
import com.facebook.react.bridge.ReactMethod
import com.facebook.react.bridge.Promise

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
} 