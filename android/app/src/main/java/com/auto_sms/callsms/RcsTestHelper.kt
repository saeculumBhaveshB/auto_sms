package com.auto_sms.callsms

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

/**
 * Helper class for testing RCS SMS reply functionality
 */
class RcsTestHelper {
    companion object {
        private const val TAG = "RcsTestHelper"
        
        /**
         * Test RCS message handling with a simulated RCS message
         * This method creates a test RCS message and processes it
         */
        fun testRcsMessageHandling(context: Context, phoneNumber: String = "+1234567890", messageBody: String = "Test RCS message") {
            Log.e(TAG, "🧪🧪🧪 STARTING RCS SMS REPLY TEST 🧪🧪🧪")
            Log.e(TAG, "📱 Test phone number: $phoneNumber")
            Log.e(TAG, "📝 Test message: $messageBody")
            
            // Create a simulated RCS intent
            val intent = Intent("com.google.android.apps.messaging.RCS_RECEIVED")
            val extras = Bundle()
            extras.putString("sender", phoneNumber)
            extras.putString("message", messageBody)
            extras.putLong("timestamp", System.currentTimeMillis())
            extras.putBoolean("rcs_message", true)
            intent.putExtras(extras)
            
            // Process the simulated RCS message
            GlobalScope.launch(Dispatchers.IO) {
                try {
                    Log.e(TAG, "🔄 Processing simulated RCS message")
                    RcsMessageHandler.processRcsMessage(context, intent)
                    Log.e(TAG, "✅ RCS message processing completed")
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Error processing RCS message: ${e.message}")
                    e.printStackTrace()
                }
            }
            
            // Also test direct SMS sending
            try {
                Log.e(TAG, "📤 Testing direct SMS sending")
                SmsSender.sendSms(context, phoneNumber, "This is a test SMS reply")
                Log.e(TAG, "✅ Direct SMS test completed")
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error sending direct SMS: ${e.message}")
                e.printStackTrace()
            }
            
            // Test RCS notification handling if available
            try {
                val notificationListener = context.applicationContext.getSystemService(Context.NOTIFICATION_SERVICE)
                if (notificationListener != null) {
                    Log.e(TAG, "📲 Testing notification listener")
                    
                    // Try to get the RcsNotificationListener instance
                    val intent = Intent("com.auto_sms.TEST_RCS_AUTO_REPLY")
                    intent.putExtra("sender", phoneNumber)
                    intent.putExtra("message", messageBody)
                    intent.putExtra("force_dynamic", true)
                    context.sendBroadcast(intent)
                    
                    Log.e(TAG, "✅ RCS notification test broadcast sent")
                } else {
                    Log.e(TAG, "⚠️ Notification service not available for testing")
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error testing notification listener: ${e.message}")
                e.printStackTrace()
            }
            
            Log.e(TAG, "🧪🧪🧪 RCS SMS REPLY TEST COMPLETED 🧪🧪🧪")
        }
        
        /**
         * Reset RCS state to ensure fresh testing
         */
        fun resetRcsState(context: Context) {
            try {
                Log.e(TAG, "🔄 Resetting RCS state")
                val intent = Intent("com.auto_sms.RESET_RCS_STATE")
                context.sendBroadcast(intent)
                Log.e(TAG, "✅ RCS state reset broadcast sent")
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error resetting RCS state: ${e.message}")
                e.printStackTrace()
            }
        }
        
        /**
         * Set testing-friendly rate limits
         */
        fun setTestingRateLimit(context: Context) {
            try {
                Log.e(TAG, "⏱️ Setting testing rate limit")
                val intent = Intent("com.auto_sms.SET_TESTING_RATE_LIMIT")
                context.sendBroadcast(intent)
                Log.e(TAG, "✅ Testing rate limit broadcast sent")
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error setting testing rate limit: ${e.message}")
                e.printStackTrace()
            }
        }
    }
} 