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
                
                // Use RcsAutoReplyManager to process the message and generate a response
                try {
                    Log.e(TAG, "🧠 Using RcsAutoReplyManager to process message")
                    val rcsManager = RcsAutoReplyManager(context)
                    
                    // Check if auto-reply is enabled
                    if (!rcsManager.isEnabled()) {
                        Log.e(TAG, "⚠️ RCS auto-reply is disabled, enabling it for this message")
                        rcsManager.setEnabled(true)
                    }
                    
                    // Process the message using RcsAutoReplyManager
                    val replyMessage = rcsManager.processMessage(phoneNumber, messageBody)
                    
                    // If we have a reply message, send it
                    if (replyMessage != null && replyMessage.isNotBlank()) {
                        Log.e(TAG, "📤 Sending RCS reply: $replyMessage")
                        SmsSender.sendSms(context, phoneNumber, replyMessage)
                        Log.e(TAG, "✅ RCS reply sent successfully")
                    } else {
                        Log.e(TAG, "⚠️ No document-based reply generated, skipping response")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Error using RcsAutoReplyManager: ${e.message}")
                    Log.e(TAG, "⚠️ No response will be sent")
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌❌❌ ERROR PROCESSING RCS MESSAGE: ${e.message}")
                Log.e(TAG, "Stack trace: ${e.stackTraceToString()}")
            }
            
            Log.e(TAG, "⚙️⚙️⚙️ PROCESSING RCS MESSAGE - COMPLETED ⚙️⚙️⚙️")
        }
    }
} 