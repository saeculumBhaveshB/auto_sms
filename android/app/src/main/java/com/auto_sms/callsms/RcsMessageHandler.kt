package com.auto_sms.callsms

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log

/**
 * Handler for RCS messages which may be delivered differently than standard SMS
 */
class RcsMessageHandler {
    companion object {
        private const val TAG = "RcsMessageHandler"
        
        /**
         * Check if an intent might contain an RCS message
         */
        fun isRcsIntent(intent: Intent): Boolean {
            val action = intent.action ?: return false
            
            // Log all intent data for debugging
            Log.e(TAG, "🔍 Checking if intent is RCS: $action")
            
            // Check action for RCS indicators
            val isRcsAction = action.contains("RCS", ignoreCase = true) || 
                             action.contains("rcs", ignoreCase = true) ||
                             action.contains("message", ignoreCase = true) && !action.contains("SMS", ignoreCase = true)
            
            Log.e(TAG, "🔍 Action contains RCS indicators: $isRcsAction")
            
            // Check extras for RCS indicators
            val extras = intent.extras
            var hasRcsExtras = false
            
            extras?.let { bundle ->
                Log.e(TAG, "🔍 Examining intent extras:")
                for (key in bundle.keySet()) {
                    val value = bundle.get(key)
                    Log.e(TAG, "   • $key = $value")
                    
                    if (key.contains("rcs", ignoreCase = true) || 
                        (value != null && value.toString().contains("rcs", ignoreCase = true))) {
                        hasRcsExtras = true
                        Log.e(TAG, "⚠️ FOUND RCS-RELATED EXTRA: $key = $value")
                    }
                }
            }
            
            Log.e(TAG, "🔍 Has RCS extras: $hasRcsExtras")
            
            return isRcsAction || hasRcsExtras
        }
        
        /**
         * Extract phone number from RCS intent
         */
        fun extractPhoneNumber(intent: Intent): String? {
            Log.e(TAG, "📱 Attempting to extract phone number from RCS intent")
            
            val extras = intent.extras ?: return null
            
            // Try common extra keys that might contain sender info
            val possibleKeys = listOf(
                "sender", "from", "originator", "address", "sender_address", 
                "originating_address", "phone", "number", "contact"
            )
            
            for (key in extras.keySet()) {
                val value = extras.get(key)?.toString() ?: continue
                
                // Check if this key might contain a phone number
                if (possibleKeys.any { key.contains(it, ignoreCase = true) }) {
                    Log.e(TAG, "📱 Potential phone number found in key '$key': $value")
                    
                    // Basic validation - if it contains digits and special chars like + it might be a phone number
                    if (value.contains(Regex("[0-9]")) && (value.contains("+") || value.contains(" ") || value.length > 5)) {
                        Log.e(TAG, "✅ Extracted phone number: $value")
                        return value
                    }
                }
            }
            
            Log.e(TAG, "❌ Failed to extract phone number from RCS intent")
            return null
        }
        
        /**
         * Extract message body from RCS intent
         */
        fun extractMessageBody(intent: Intent): String? {
            Log.e(TAG, "📝 Attempting to extract message body from RCS intent")
            
            val extras = intent.extras ?: return null
            
            // Try common extra keys that might contain message content
            val possibleKeys = listOf(
                "message", "body", "text", "content", "data", "payload", "msg"
            )
            
            for (key in extras.keySet()) {
                val value = extras.get(key)?.toString() ?: continue
                
                // Check if this key might contain a message
                if (possibleKeys.any { key.contains(it, ignoreCase = true) }) {
                    Log.e(TAG, "📝 Potential message found in key '$key': $value")
                    
                    // Basic validation - if it's longer than a few chars, it might be a message
                    if (value.length > 3) {
                        Log.e(TAG, "✅ Extracted message body: $value")
                        return value
                    }
                }
            }
            
            Log.e(TAG, "❌ Failed to extract message body from RCS intent")
            return null
        }
        
        /**
         * Process a potential RCS message
         */
        suspend fun processRcsMessage(context: Context, intent: Intent) {
            Log.e(TAG, "⚙️⚙️⚙️ PROCESSING RCS MESSAGE - START ⚙️⚙️⚙️")
            
            try {
                // Extract sender phone number
                val phoneNumber = extractPhoneNumber(intent)
                if (phoneNumber == null) {
                    Log.e(TAG, "❌ Could not extract phone number from RCS intent")
                    return
                }
                
                // Extract message body
                val messageBody = extractMessageBody(intent)
                if (messageBody == null) {
                    Log.e(TAG, "❌ Could not extract message body from RCS intent")
                    return
                }
                
                Log.e(TAG, "✅ Successfully extracted RCS message:")
                Log.e(TAG, "📱 From: $phoneNumber")
                Log.e(TAG, "📝 Message: $messageBody")
                
                // CRITICAL FIX: Process the message and send reply directly
                try {
                    // Process the message using the standard SMS processor
                    SmsProcessor.processIncomingSms(context, phoneNumber, messageBody)
                    
                    // Generate a reply message
                    val replyMessage = "Thank you for your message. I'll get back to you soon."
                    
                    // If we have a reply message, send it directly using SmsSender
                    if (replyMessage.isNotBlank()) {
                        Log.e(TAG, "📤 Sending direct reply to RCS message")
                        SmsSender.sendSms(context, phoneNumber, replyMessage)
                        Log.e(TAG, "✅ Direct reply to RCS message sent successfully")
                    } else {
                        Log.e(TAG, "ℹ️ No reply needed for this RCS message")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Error processing or replying to RCS message: ${e.message}")
                    
                    // Try fallback approach - just send a generic reply
                    try {
                        Log.e(TAG, "🔄 Trying fallback reply for RCS message")
                        val fallbackMessage = "I received your message and will get back to you soon."
                        SmsSender.sendSms(context, phoneNumber, fallbackMessage)
                        Log.e(TAG, "✅ Fallback reply sent successfully")
                    } catch (e2: Exception) {
                        Log.e(TAG, "❌ Fallback reply also failed: ${e2.message}")
                    }
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "❌❌❌ ERROR PROCESSING RCS MESSAGE: ${e.message}")
                Log.e(TAG, "Stack trace: ${e.stackTraceToString()}")
            }
            
            Log.e(TAG, "⚙️⚙️⚙️ PROCESSING RCS MESSAGE - COMPLETED ⚙️⚙️⚙️")
        }
    }
} 