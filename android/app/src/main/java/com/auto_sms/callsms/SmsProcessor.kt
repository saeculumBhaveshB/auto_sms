package com.auto_sms.callsms

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SmsProcessor {
    companion object {
        private const val TAG = "SmsProcessor"
        
        // Check if the phone number is in contacts
        private fun isNumberInContacts(context: Context, phoneNumber: String): Boolean {
            // Implementation for checking contacts
            return true // Placeholder implementation
        }
        
        // Determine if we should reply to this SMS
        private fun shouldReplyToSms(context: Context, phoneNumber: String, messageBody: String): Boolean {
            // Always reply during testing for RCS issues
            return true // Force reply for testing
        }
        
        // Generate response using LLM
        private suspend fun generateLlmResponse(context: Context, phoneNumber: String, messageBody: String): String {
            // Use RcsAutoReplyManager to generate document-based responses
            try {
                Log.e(TAG, "🧠 Using RcsAutoReplyManager to generate LLM response")
                val rcsManager = RcsAutoReplyManager(context)
                
                // Try to get a document-based response
                val response = rcsManager.getDefaultMessage(phoneNumber, messageBody)
                if (response.isNotBlank()) {
                    Log.e(TAG, "✅ Generated document-based response: $response")
                    return response
                }
                
                // If that fails, try direct document processing
                Log.e(TAG, "🔄 Trying direct document processing")
                val documentResponse = rcsManager.generateLLMResponseWithDocuments(phoneNumber, messageBody)
                if (documentResponse.isNotBlank()) {
                    Log.e(TAG, "✅ Generated document-based response: $documentResponse")
                    return documentResponse
                }
                
                // If all approaches fail, return empty string to prevent static responses
                Log.e(TAG, "⚠️ No document-based response could be generated")
                return ""
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error generating LLM response: ${e.message}")
                return ""
            }
        }
        
        // Save conversation to database
        private fun saveConversation(context: Context, phoneNumber: String, incomingMessage: String, outgoingMessage: String) {
            // Implementation for saving to database
            Log.e(TAG, "Saving conversation with $phoneNumber to database")
        }
        
        suspend fun processIncomingSms(context: Context, phoneNumber: String, messageBody: String) {
            Log.e(TAG, "⚙️⚙️⚙️ PROCESSING INCOMING SMS - START ⚙️⚙️⚙️")
            Log.e(TAG, "📱 From: $phoneNumber")
            Log.e(TAG, "📝 Message: $messageBody")
            
            // Check if this might be an RCS message based on phone number format or content
            val possiblyRcsMessage = phoneNumber.contains("+") || 
                                   messageBody.contains("RCS", ignoreCase = true) ||
                                   phoneNumber.contains("rcs", ignoreCase = true)
            Log.e(TAG, "🔍 Possibly RCS message: $possiblyRcsMessage")
            
            try {
                // Normalize the phone number for consistent comparison
                val normalizedPhoneNumber = SmsSender.normalizePhoneNumber(phoneNumber)
                Log.e(TAG, "📱 Original number: $phoneNumber, Normalized: $normalizedPhoneNumber")
                
                // Check if the sender is in our contacts
                val isInContacts = isNumberInContacts(context, normalizedPhoneNumber)
                Log.e(TAG, "👤 Is in contacts: $isInContacts")
                
                // Check if we should reply to this message
                val shouldReply = shouldReplyToSms(context, normalizedPhoneNumber, messageBody)
                Log.e(TAG, "💬 Should reply: $shouldReply")
                
                if (shouldReply) {
                    Log.e(TAG, "🤖 Generating reply message...")
                    
                    // Try to generate a response using LLM
                    val responseMessage = try {
                        // Log the LLM request
                        Log.e(TAG, "🧠 Requesting LLM response for message from $normalizedPhoneNumber")
                        
                        val response = generateLlmResponse(context, normalizedPhoneNumber, messageBody)
                        if (response.isBlank()) {
                            Log.e(TAG, "⚠️ No document-based response generated, skipping reply")
                            return
                        }
                        
                        Log.e(TAG, "✅ LLM response generated successfully: $response")
                        response
                    } catch (e: Exception) {
                        // Log the LLM failure
                        Log.e(TAG, "❌ Failed to generate LLM response: ${e.message}")
                        Log.e(TAG, "Stack trace: ${e.stackTraceToString()}")
                        Log.e(TAG, "⚠️ No response will be sent")
                        return
                    }
                    
                    // Send the response
                    Log.e(TAG, "📤 Sending response to $normalizedPhoneNumber: $responseMessage")
                    SmsSender.sendSms(context, normalizedPhoneNumber, responseMessage)
                    
                    // Save the conversation
                    saveConversation(context, normalizedPhoneNumber, messageBody, responseMessage)
                    Log.e(TAG, "💾 Conversation saved to database")
                } else {
                    Log.e(TAG, "⏭️ Skipping reply for this message")
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌❌❌ ERROR PROCESSING SMS: ${e.message}")
                Log.e(TAG, "Stack trace: ${e.stackTraceToString()}")
            }
            
            Log.e(TAG, "⚙️⚙️⚙️ PROCESSING INCOMING SMS - COMPLETED ⚙️⚙️⚙️")
        }
    }
} 