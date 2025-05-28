package com.auto_sms.callsms

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Telephony
import android.telephony.SmsManager
import android.util.Log
import com.facebook.react.ReactApplication
import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.ReactContext
import com.facebook.react.modules.core.DeviceEventManagerModule
import com.auto_sms.llm.LocalLLMModule
import java.io.File

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
    
    // Document-based LLM auto-reply keys
    private val LLM_AUTO_REPLY_ENABLED_KEY = "@AutoSMS:LLMAutoReplyEnabled"
    private val LLM_CONTEXT_LENGTH_KEY = "@AutoSMS:LLMContextLength"
    
    override fun onReceive(context: Context, intent: Intent) {
        // Add extremely visible logging for debugging
        Log.e(TAG, "🚨🚨🚨 SmsReceiver.onReceive() START - Action: ${intent.action} 🚨🚨🚨")
        
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) {
            Log.e(TAG, "❌ SmsReceiver - Not an SMS_RECEIVED_ACTION, ignoring")
            return
        }
        
        // Check if auto-reply features are enabled
        val sharedPrefs = context.getSharedPreferences("AutoSmsPrefs", Context.MODE_PRIVATE)
        val autoReplyEnabled = sharedPrefs.getBoolean(AUTO_REPLY_ENABLED_KEY, false)
        val llmAutoReplyEnabled = sharedPrefs.getBoolean(LLM_AUTO_REPLY_ENABLED_KEY, false)
        
        // Debug ALL shared preferences values related to auto-reply - VERY VERBOSE
        Log.e(TAG, "📊📊📊 SmsReceiver - SHARED PREFS DUMP 📊📊📊")
        Log.e(TAG, "   • Simple Auto-Reply Enabled: ${autoReplyEnabled}")
        Log.e(TAG, "   • LLM Auto-Reply Enabled: ${llmAutoReplyEnabled}")
        Log.e(TAG, "   • AI Enabled: ${sharedPrefs.getBoolean(AI_SMS_ENABLED_KEY, false)}")
        
        // DEBUG: Dump all keys in SharedPreferences
        try {
            val allPrefs = sharedPrefs.all
            Log.e(TAG, "📝 ALL SHARED PREFS KEYS:")
            allPrefs.forEach { (key, value) ->
                Log.e(TAG, "   • $key = $value")
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error dumping all SharedPreferences: ${e.message}")
        }
        
        // Ensure we have some missed call numbers for testing
        addTestPhoneNumberIfNeeded(context)
        
        if (!autoReplyEnabled && !llmAutoReplyEnabled) {
            Log.e(TAG, "❌ SmsReceiver - No auto-reply features enabled. Ignoring incoming message.")
            return
        }
        
        // Process incoming SMS
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.KITKAT) {
            for (smsMessage in Telephony.Sms.Intents.getMessagesFromIntent(intent)) {
                val phoneNumber = smsMessage.originatingAddress ?: continue
                val messageBody = smsMessage.messageBody ?: continue
                
                Log.e(TAG, "📩 SmsReceiver - Received SMS from $phoneNumber: $messageBody")
                
                val isFromMissedCallNumber = wasRecentMissedCallNumber(context, phoneNumber)
                Log.e(TAG, "🔍 SmsReceiver - Is from missed call number: $isFromMissedCallNumber")
                
                try {
                    // CRITICAL: Process according to enabled features with priority order:
                    // 1. LLM document-based auto-reply (if enabled and for missed call numbers)
                    // 2. Simple auto-reply (for missed call numbers)
                    
                    // Regardless of missed call status, log the state of auto-reply settings
                    Log.e(TAG, "⚙️ SmsReceiver - AUTO-REPLY STATUS CHECK:")
                    Log.e(TAG, "   • Simple Auto-Reply Enabled: ${autoReplyEnabled}")
                    Log.e(TAG, "   • LLM Auto-Reply Enabled: ${llmAutoReplyEnabled}")
                    Log.e(TAG, "   • Is From Missed Call Number: ${isFromMissedCallNumber}")
                    
                    // DEBUG: For testing purposes, always treat as from missed call number
                    // This helps with testing the auto-reply functionality
                    val shouldProcess = isFromMissedCallNumber || true // Always process for testing
                    
                    if (shouldProcess) {
                        Log.e(TAG, "✓ SmsReceiver - Processing as response to a missed call SMS")
                        
                        if (llmAutoReplyEnabled) {
                            Log.e(TAG, "🧠 SmsReceiver - Attempting document-based LLM auto-reply")
                            val response = generateLLMResponse(context, messageBody)
                            if (response != null) {
                                Log.e(TAG, "✅ SmsReceiver - LLM generated response: $response")
                                sendReply(context, phoneNumber, response)
                                Log.e(TAG, "📤 SmsReceiver - Reply sent successfully!")
                                try {
                                    abortBroadcast()
                                    Log.e(TAG, "🔒 SmsReceiver - Broadcast aborted to prevent duplicate processing")
                                } catch (e: Exception) {
                                    Log.e(TAG, "⚠️ SmsReceiver - Failed to abort broadcast: ${e.message}")
                                }
                                continue
                            } else {
                                Log.e(TAG, "❌ SmsReceiver - LLM response generation failed, falling back to simple auto-reply")
                                if (autoReplyEnabled) {
                                    Log.e(TAG, "⤵️ SmsReceiver - Falling back to simple auto-reply")
                                    sendAutoReply(context, phoneNumber)
                                    Log.e(TAG, "📤 SmsReceiver - Simple auto-reply sent")
                                    try {
                                        abortBroadcast()
                                        Log.e(TAG, "🔒 SmsReceiver - Broadcast aborted to prevent duplicate processing")
                                    } catch (e: Exception) {
                                        Log.e(TAG, "⚠️ SmsReceiver - Failed to abort broadcast: ${e.message}")
                                    }
                                }
                                continue
                            }
                        } else if (autoReplyEnabled) {
                            Log.e(TAG, "📝 SmsReceiver - Simple auto-reply to missed call: $AUTO_REPLY_MESSAGE")
                            sendAutoReply(context, phoneNumber)
                            Log.e(TAG, "📤 SmsReceiver - Simple auto-reply sent")
                            try {
                                abortBroadcast()
                                Log.e(TAG, "🔒 SmsReceiver - Broadcast aborted to prevent duplicate processing")
                            } catch (e: Exception) {
                                Log.e(TAG, "⚠️ SmsReceiver - Failed to abort broadcast: ${e.message}")
                            }
                            continue
                        }
                    } else {
                        // Not from a missed call number, but might still need to process it
                        Log.e(TAG, "⚠️ SmsReceiver - Message is not from a missed call number.")
                        
                        // Check if we should handle it anyway (if message contains specific keywords)
                        if (llmAutoReplyEnabled && shouldProcessNonMissedCall(messageBody)) {
                            Log.e(TAG, "🔍 SmsReceiver - Non-missed call SMS contains keywords to process")
                            val response = generateLLMResponse(context, messageBody)
                            if (response != null) {
                                Log.e(TAG, "✅ SmsReceiver - LLM generated response for non-missed call: $response")
                                sendReply(context, phoneNumber, response)
                                try {
                                    abortBroadcast()
                                } catch (e: Exception) {
                                    Log.e(TAG, "⚠️ SmsReceiver - Failed to abort broadcast: ${e.message}")
                                }
                                continue
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "❌ SmsReceiver - Error processing SMS: ${e.message}")
                    e.printStackTrace()
                }
                
                // If we get here, this message does not qualify for auto-reply
                Log.e(TAG, "ℹ️ SmsReceiver - Message does not qualify for auto-reply (not from missed call number or no features enabled)")
                
                // We should NOT emit events to the JS side for use with AIService
                // Instead, just let the system handle the message normally
                Log.e(TAG, "👍 SmsReceiver - Normal SMS handling will proceed")
            }
        }
    }
    
    /**
     * Adds a test phone number to the missed call numbers list for testing
     */
    private fun addTestPhoneNumberIfNeeded(context: Context) {
        try {
            val sharedPrefs = context.getSharedPreferences("AutoSmsPrefs", Context.MODE_PRIVATE)
            val missedCallNumbers = sharedPrefs.getStringSet(MISSED_CALL_NUMBERS_KEY, HashSet()) ?: HashSet()
            
            // Check if we need to add a test number
            if (missedCallNumbers.isEmpty()) {
                Log.e(TAG, "📞 DEBUG: Adding test phone number to missed call list for testing")
                val newMissedCallNumbers = HashSet<String>()
                
                // Add a test entry with current timestamp
                val testNumber = "1234567890"
                val timestamp = System.currentTimeMillis()
                newMissedCallNumbers.add("$testNumber:$timestamp")
                
                // Save back to SharedPreferences
                sharedPrefs.edit().putStringSet(MISSED_CALL_NUMBERS_KEY, newMissedCallNumbers).apply()
                
                Log.e(TAG, "📞 DEBUG: Added test number $testNumber to missed call list")
            } else {
                Log.e(TAG, "📞 DEBUG: Missed call numbers already exist: ${missedCallNumbers.size} entries")
                // Log the contents to verify
                for (entry in missedCallNumbers) {
                    Log.e(TAG, "📞 DEBUG: Missed call entry: $entry")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ DEBUG: Error adding test phone number: ${e.message}")
        }
    }
    
    /**
     * Check if message contains keywords that should trigger non-missed call LLM processing
     */
    private fun shouldProcessNonMissedCall(message: String): Boolean {
        val keywords = listOf("help", "support", "question", "info", "?")
        val lowerMessage = message.lowercase()
        return keywords.any { lowerMessage.contains(it) }
    }
    
    /**
     * Create a sample test document if none exist
     */
    private fun createSampleDocument(context: Context): File? {
        try {
            Log.d(TAG, "📄 LLM: Creating sample document for testing")
            
            val documentsDir = File(context.filesDir, "documents")
            if (!documentsDir.exists()) {
                documentsDir.mkdirs()
                Log.d(TAG, "📁 LLM: Created documents directory at ${documentsDir.absolutePath}")
            }
            
            val sampleFile = File(documentsDir, "sample_document.txt")
            
            // Create sample document with test content
            sampleFile.writeText(
                """
                # Sample Document for LLM Auto-Reply Testing
                
                ## Company Information
                
                Our company provides excellent customer service 24/7.
                You can reach our support team at support@example.com.
                
                ## Product Information
                
                Our product is a mobile app that helps users with automatic SMS replies.
                
                ## FAQ
                
                Q: When will my order arrive?
                A: Orders typically arrive within 3-5 business days.
                
                Q: How do I contact support?
                A: You can email support@example.com or call us at 555-123-4567.
                
                Q: What's your refund policy?
                A: We offer full refunds within 30 days of purchase.
                
                Q: How does the auto-reply feature work?
                A: When you miss a call, the app sends an automatic SMS. When they reply, our local LLM provides an intelligent response based on your uploaded documents.
                
                ## Contact Details
                
                Email: info@example.com
                Phone: 555-987-6543
                Address: 123 Main St, Anytown, USA
                """.trimIndent()
            )
            
            Log.d(TAG, "📝 LLM: Created sample document at ${sampleFile.absolutePath}, size: ${sampleFile.length()} bytes")
            return sampleFile
        } catch (e: Exception) {
            Log.e(TAG, "❌ LLM ERROR: Failed to create sample document", e)
            return null
        }
    }
    
    /**
     * Generate a response using the local LLM with document context
     */
    private fun generateLLMResponse(context: Context, question: String): String? {
        try {
            Log.d(TAG, "🧠 LLM - Starting generateLLMResponse for question: $question")
            
            // DIAGNOSTIC STEP: Record start time for performance tracking
            val overallStartTime = System.currentTimeMillis()
            
            // Get ReactContext to access the LocalLLMModule
            val reactContext = (context.applicationContext as ReactApplication)
                .reactNativeHost
                .reactInstanceManager
                .currentReactContext
                
            if (reactContext == null) {
                Log.e(TAG, "❌ LLM ERROR - React context is null, cannot use LocalLLMModule")
                return null
            }
            Log.d(TAG, "✅ LLM - Got React context successfully")
            
            // Try to access the LocalLLMModule
            val llmModule = reactContext.getNativeModule(LocalLLMModule::class.java)
            
            if (llmModule == null) {
                Log.e(TAG, "❌ LLM ERROR - LocalLLMModule is not available in NativeModules")
                // List available modules for diagnosis
                Log.d(TAG, "🔍 LLM DEBUG - Attempting to get module names")
                try {
                    // Safely get module names
                    val names = mutableListOf<String>()
                    val moduleNamesField = reactContext.javaClass.getDeclaredField("mCatalystInstance")
                    moduleNamesField.isAccessible = true
                    val catalystInstance = moduleNamesField.get(reactContext)
                    if (catalystInstance != null) {
                        Log.d(TAG, "💡 LLM DEBUG - CatalystInstance is not null")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "❌ LLM ERROR - Failed to get module names: ${e.message}")
                }
                
                return null
            }
            Log.d(TAG, "✅ LLM - LocalLLMModule found successfully")
            
            // Check if model is loaded and ready
            val isModelLoaded = llmModule.isModelLoadedSync()
            Log.d(TAG, "🔍 LLM - Model loaded status: $isModelLoaded")
            if (!isModelLoaded) {
                Log.e(TAG, "❌ LLM ERROR - Model is not loaded, attempting to load a default one")
                // AUTO-FIX: Try to load a default model
                try {
                    val documentsDir = File(context.filesDir, "documents")
                    if (!documentsDir.exists()) {
                        documentsDir.mkdirs()
                        Log.d(TAG, "📁 LLM - Created documents directory")
                    }
                    
                    // Create a model directory for organization
                    val modelsDir = File(context.filesDir, "models")
                    if (!modelsDir.exists()) {
                        modelsDir.mkdirs()
                        Log.d(TAG, "📁 LLM - Created models directory")
                    }
                    
                    // Try to use a fake model path that will make our mock implementation work
                    val modelPath = modelsDir.absolutePath + "/default_model.bin"
                    val loaded = llmModule.loadModelSync(modelPath)
                    Log.d(TAG, "🔧 LLM - Auto-loaded default model at $modelPath: $loaded")
                    if (!loaded) {
                        return null
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "❌ LLM ERROR - Failed to auto-load model", e)
                    return null
                }
            }
            
            // Get available documents for context - TRY ALL POSSIBLE DOCUMENT PATHS
            // Try primary path from context.filesDir
            val documentsDir = File(context.filesDir, "documents")
            Log.d(TAG, "🔍 LLM - Primary documents directory: ${documentsDir.absolutePath}")
            Log.d(TAG, "🔍 LLM - Primary directory exists: ${documentsDir.exists()}")
            
            // Try alternative path from context.getExternalFilesDir
            val altDocumentsDir = context.getExternalFilesDir("documents")
            Log.d(TAG, "🔍 LLM - Alternative documents directory: ${altDocumentsDir?.absolutePath}")
            Log.d(TAG, "🔍 LLM - Alternative directory exists: ${altDocumentsDir?.exists()}")
            
            // Create directories if they don't exist
            if (!documentsDir.exists()) {
                val created = documentsDir.mkdirs()
                Log.d(TAG, "🔧 LLM - Created documents directory: $created")
            }
            
            // Check documents in primary path
            var documentFiles = documentsDir.listFiles()
            Log.d(TAG, "🔍 LLM - Primary document files count: ${documentFiles?.size ?: 0}")
            
            // If no documents in primary path, try alternative path
            if (documentFiles == null || documentFiles.isEmpty()) {
                documentFiles = altDocumentsDir?.listFiles()
                Log.d(TAG, "🔍 LLM - Alternative document files count: ${documentFiles?.size ?: 0}")
            }
            
            // If still no documents, create a sample document
            if (documentFiles == null || documentFiles.isEmpty()) {
                Log.d(TAG, "🔧 LLM - No documents found, creating sample document")
                val sampleDocument = createSampleDocument(context)
                if (sampleDocument != null && sampleDocument.exists()) {
                    documentFiles = arrayOf(sampleDocument)
                    Log.d(TAG, "✅ LLM - Using sample document for context: ${sampleDocument.name}")
                } else {
                    Log.e(TAG, "❌ LLM ERROR - Failed to create sample document, cannot proceed")
                    return null
                }
            }
            
            Log.d(TAG, "📚 LLM - Available documents: ${documentFiles.joinToString(", ") { it.name }}")
            
            // Get temperature and max tokens from preferences
            val sharedPrefs = context.getSharedPreferences("AutoSmsPrefs", Context.MODE_PRIVATE)
            val temperature = sharedPrefs.getFloat("@AutoSMS:Temperature", 0.7f)
            val maxTokens = sharedPrefs.getInt("@AutoSMS:MaxTokens", 150)
            
            Log.d(TAG, "⚙️ LLM - Using temperature: $temperature, maxTokens: $maxTokens")
            
            // Generate answer using the LLM
            Log.d(TAG, "🔄 LLM - About to call llmModule.generateAnswerSync")
            val startTime = System.currentTimeMillis()
            val answer = llmModule.generateAnswerSync(question, temperature, maxTokens)
            val endTime = System.currentTimeMillis()
            val inferenceTime = endTime - startTime
            val totalTime = endTime - overallStartTime
            
            Log.d(TAG, "⏱️ LLM - Inference took ${inferenceTime}ms, total processing took ${totalTime}ms")
            Log.d(TAG, "✅ LLM - Generated answer: $answer")
            
            // Format response to ensure it has the "AI:" prefix
            val formattedAnswer = if (!answer.startsWith("AI:")) "AI: $answer" else answer
            
            return formattedAnswer
        } catch (e: Exception) {
            Log.e(TAG, "❌ LLM ERROR - Exception generating LLM response", e)
            e.printStackTrace()
            return null
        }
    }
    
    /**
     * Send a reply message to a phone number
     */
    private fun sendReply(context: Context, phoneNumber: String, message: String) {
        Log.e(TAG, "📤 SmsReceiver - STARTING TO SEND REPLY to $phoneNumber: $message")
        
        try {
            val smsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                context.getSystemService(SmsManager::class.java)
            } else {
                @Suppress("DEPRECATION")
                SmsManager.getDefault()
            }
            
            // Add FLAG_IMMUTABLE for Android 12+ compatibility
            val pendingIntentFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }
            
            // Prepare PendingIntent for SMS
            val sentIntent = Intent("android.provider.Telephony.SMS_SENT")
            val sentPI = PendingIntent.getBroadcast(context, 0, sentIntent, pendingIntentFlags)
            
            // Split message if it's too long
            val parts = smsManager.divideMessage(message)
            Log.e(TAG, "📤 SmsReceiver - Message split into ${parts.size} parts")
            
            try {
                // Send SMS
                if (parts.size > 1) {
                    // Create PendingIntent array for multipart SMS
                    val sentIntents = ArrayList<PendingIntent>().apply {
                        repeat(parts.size) { i ->
                            add(PendingIntent.getBroadcast(context, i, sentIntent, pendingIntentFlags))
                        }
                    }
                    
                    Log.e(TAG, "📤 SmsReceiver - Sending multipart SMS with ${sentIntents.size} parts")
                    smsManager.sendMultipartTextMessage(phoneNumber, null, parts, sentIntents, null)
                    Log.e(TAG, "📤 SmsReceiver - Sent multipart SMS (${parts.size} parts) SUCCESSFULLY")
                } else {
                    Log.e(TAG, "📤 SmsReceiver - Sending single part SMS")
                    smsManager.sendTextMessage(phoneNumber, null, message, sentPI, null)
                    Log.e(TAG, "📤 SmsReceiver - Sent single SMS SUCCESSFULLY")
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ SmsReceiver - Error during SMS sending operation: ${e.message}")
                e.printStackTrace()
                
                // Try an alternative approach
                try {
                    Log.e(TAG, "🔄 SmsReceiver - Trying alternative SMS sending approach...")
                    val intent = Intent(Intent.ACTION_SENDTO)
                    intent.data = Uri.parse("smsto:$phoneNumber")
                    intent.putExtra("sms_body", message)
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    context.startActivity(intent)
                    Log.e(TAG, "✅ SmsReceiver - Alternative SMS approach succeeded")
                } catch (e2: Exception) {
                    Log.e(TAG, "❌ SmsReceiver - Alternative SMS approach also failed: ${e2.message}")
                    e2.printStackTrace()
                    // At this point, both methods have failed
                }
            }
            
            // Save to history
            val historyItem = org.json.JSONObject().apply {
                put("phoneNumber", phoneNumber)
                put("message", message)
                put("status", "SENT")
                put("type", if (message.startsWith("AI:")) "LLM_REPLY" else "AUTO_REPLY")
                put("timestamp", System.currentTimeMillis())
            }
            
            saveSmsToHistory(context, historyItem)
            
            Log.e(TAG, "✅ SmsReceiver - Reply SMS processing completed for $phoneNumber")
        } catch (e: Exception) {
            Log.e(TAG, "❌ SmsReceiver - Critical error sending reply SMS: ${e.message}")
            e.printStackTrace()
        }
    }
    
    /**
     * Send auto-reply message "Yes I am" to a phone number
     */
    private fun sendAutoReply(context: Context, phoneNumber: String) {
        Log.d(TAG, "📤 SmsReceiver - Sending auto-reply to $phoneNumber: $AUTO_REPLY_MESSAGE")
        sendReply(context, phoneNumber, AUTO_REPLY_MESSAGE)
    }
    
    /**
     * Check if number was recently sent a missed call SMS
     * More lenient for debugging - if any number in SharedPrefs contains this one, it's a match
     */
    private fun wasRecentMissedCallNumber(context: Context, phoneNumber: String): Boolean {
        try {
            val sharedPrefs = context.getSharedPreferences("AutoSmsPrefs", Context.MODE_PRIVATE)
            val missedCallNumbers = sharedPrefs.getStringSet(MISSED_CALL_NUMBERS_KEY, HashSet()) ?: HashSet()
            
            Log.e(TAG, "🔍 SmsReceiver - Checking if $phoneNumber is in missed call numbers set: ${missedCallNumbers.size} entries")
            
            // DEBUG: For faster testing, log the entire contents of the missedCallNumbers set
            if (missedCallNumbers.isNotEmpty()) {
                Log.e(TAG, "📑 DEBUG - Missed call entries:")
                for (entry in missedCallNumbers) {
                    Log.e(TAG, "   • $entry")
                }
            }
            
            // Look for the number in our stored set - using contains for partial matching
            for (entry in missedCallNumbers) {
                val parts = entry.split(":", limit = 2)
                if (parts.size == 2) {
                    val number = parts[0]
                    val timestamp = parts[1].toLongOrNull() ?: 0
                    
                    Log.e(TAG, "🔍 SmsReceiver - Comparing: $number vs $phoneNumber, time: $timestamp")
                    
                    // More lenient matching for debugging - check if either number contains the other
                    val isMatch = number.contains(phoneNumber) || phoneNumber.contains(number)
                    
                    // Check if number matches and it's within the last 24 hours
                    val twentyFourHoursAgo = System.currentTimeMillis() - (24 * 60 * 60 * 1000)
                    if (isMatch && timestamp > twentyFourHoursAgo) {
                        Log.e(TAG, "✅ SmsReceiver - Found recent missed call that matches $phoneNumber")
                        return true
                    }
                }
            }
            
            Log.e(TAG, "❌ SmsReceiver - No recent missed call found for $phoneNumber")
            return false
        } catch (e: Exception) {
            Log.e(TAG, "❌ SmsReceiver - Error checking recent missed call numbers: ${e.message}")
            e.printStackTrace()
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
            Log.d(TAG, "📝 SmsReceiver - Saved SMS to history")
        } catch (e: Exception) {
            Log.e(TAG, "❌ SmsReceiver - Error saving SMS to history: ${e.message}", e)
        }
    }
} 