package com.auto_sms.callsms

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Build
import android.provider.Telephony
import android.telephony.SmsManager
import android.util.Log
import com.facebook.react.ReactApplication
import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactContext
import com.facebook.react.modules.core.DeviceEventManagerModule
import com.auto_sms.llm.LocalLLMModule
import java.io.File
import java.io.FileInputStream
import com.itextpdf.text.pdf.PdfReader
import com.itextpdf.text.pdf.parser.PdfTextExtractor
import com.facebook.react.bridge.WritableMap
import kotlinx.coroutines.runBlocking

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
    
    // Document-based LLM auto-reply keys
    private val LLM_AUTO_REPLY_ENABLED_KEY = "@AutoSMS:LLMAutoReplyEnabled"
    private val LLM_CONTEXT_LENGTH_KEY = "@AutoSMS:LLMContextLength"
    private val LLM_ENHANCED_QA_KEY = "@AutoSMS:UseEnhancedQA"
    
    // Manual LLM implementation fallback
    private var manualLLM = ManualLLMImplementation()
    
    override fun onReceive(context: Context, intent: Intent) {
        // Add extremely visible logging for debugging
        Log.e(TAG, "🚨🚨🚨 SmsReceiver.onReceive() START - Action: ${intent.action} 🚨🚨🚨")
        
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) {
            Log.e(TAG, "❌ SmsReceiver - Not an SMS_RECEIVED_ACTION, ignoring")
            return
        }
        
        Log.e(TAG, "✅✅✅ SMS RECEIVED - Processing incoming SMS ✅✅✅")
        
        // Initialize local LLM as early as possible
        initializeLocalLLM(context)
        
        // Check if auto-reply features are enabled
        val sharedPrefs = context.getSharedPreferences("AutoSmsPrefs", Context.MODE_PRIVATE)
        
        // ENSURE LLM AUTO-REPLY IS ENABLED BY DEFAULT FOR TESTING
        ensureLLMAutoReplyEnabled(context, sharedPrefs)
        
        // CRITICAL FIX: After ensuring settings, re-read them to get updated values
        // This ensures we have the correct state after the ensureLLMAutoReplyEnabled method
        // may have modified them to avoid conflicts
        var autoReplyEnabled = sharedPrefs.getBoolean(AUTO_REPLY_ENABLED_KEY, false)
        val llmAutoReplyEnabled = sharedPrefs.getBoolean(LLM_AUTO_REPLY_ENABLED_KEY, false)
        
        // CRITICAL: Double-check that we're not accidentally enabling both
        // If both are enabled, prioritize LLM auto-reply and disable simple auto-reply
        if (llmAutoReplyEnabled && autoReplyEnabled) {
            Log.e(TAG, "⚠️ SmsReceiver - Both auto-reply modes are enabled! Disabling simple auto-reply")
            sharedPrefs.edit().putBoolean(AUTO_REPLY_ENABLED_KEY, false).apply()
            autoReplyEnabled = false
        }
        
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
                
                // Enhanced SMS Message Details Logging with unique tag for easier filtering
                Log.e(TAG, "LOGTAG_SMS_DETAILS: 📩📩📩 SMS MESSAGE DETAILS 📩📩📩")
                Log.e(TAG, "LOGTAG_SMS_DETAILS: ↘️ From: $phoneNumber")
                Log.e(TAG, "LOGTAG_SMS_DETAILS: ↘️ Message: $messageBody")
                Log.e(TAG, "LOGTAG_SMS_DETAILS: ↘️ Timestamp: ${System.currentTimeMillis()}")
                Log.e(TAG, "LOGTAG_SMS_DETAILS: ↘️ Message Length: ${messageBody.length} characters")
                Log.e(TAG, "LOGTAG_SMS_DETAILS: ↘️ Display Originating Address: ${smsMessage.displayOriginatingAddress}")
                Log.e(TAG, "LOGTAG_SMS_DETAILS: ↘️ Message Class: ${smsMessage.messageClass}")
                Log.e(TAG, "LOGTAG_SMS_DETAILS: ↘️ Message ID: ${smsMessage.indexOnIcc}")
                
                val isFromMissedCallNumber = wasRecentMissedCallNumber(context, phoneNumber)
                Log.e(TAG, "LOGTAG_SMS_DETAILS: ↘️ Is from missed call number: $isFromMissedCallNumber")
                
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
                    val shouldProcess = isFromMissedCallNumber || true // Always process for testing during development
                    
                    if (shouldProcess) {
                        Log.e(TAG, "✓ SmsReceiver - Processing as response to a missed call SMS")
                        
                        if (llmAutoReplyEnabled) {
                            // FOR BETTER TESTING: Process any incoming message with LLM when LLM auto-reply is enabled
                            Log.e(TAG, "🧠 SmsReceiver - Attempting document-based LLM auto-reply")
                            
                            // First try to use the enhanced document QA approach if available
                            val useEnhancedQA = sharedPrefs.getBoolean(LLM_ENHANCED_QA_KEY, true)
                            Log.e(TAG, "📊 Enhanced Document QA Enabled: $useEnhancedQA")
                            
                            var response: String = "" // Initialize as empty string instead of nullable
                            
                            if (useEnhancedQA) {
                                try {
                                    Log.e(TAG, "🔍 Attempting to use enhanced document QA")
                                    
                                    // Try to access CallSmsModule for document QA
                                    val reactContext = try {
                                        (context.applicationContext as ReactApplication)
                                            .reactNativeHost
                                            .reactInstanceManager
                                            .currentReactContext
                                    } catch (e: Exception) {
                                        Log.e(TAG, "❌ Error getting ReactContext for document QA: ${e.message}")
                                        null
                                    }
                                    
                                    if (reactContext != null) {
                                        val callSmsModule = reactContext.getNativeModule(CallSmsModule::class.java)
                                        if (callSmsModule != null) {
                                            try {
                                                Log.e(TAG, "📝 Getting testLLM method to process message: $messageBody")
                                                
                                                // Direct approach - use the method that's already working in the app
                                                val testLLMMethod = CallSmsModule::class.java.getDeclaredMethod(
                                                    "testLLM",
                                                    String::class.java
                                                )
                                                testLLMMethod.isAccessible = true
                                                
                                                // Call the method directly
                                                val result = testLLMMethod.invoke(callSmsModule, messageBody)
                                                if (result != null) {
                                                    // Clean the response before using it to remove any prompt instructions
                                                    val rawResponse = result as String
                                                    response = cleanLLMResponse(rawResponse)
                                                    if (response.isNotEmpty()) {
                                                        Log.e(TAG, "✅ Successfully got LLM response and cleaned it: $response")
                                                    } else {
                                                        Log.e(TAG, "⚠️ LLM response was empty after cleaning")
                                                    }
                                                } else {
                                                    Log.e(TAG, "❌ No response from LLM")
                                                }
                                            } catch (e: Exception) {
                                                Log.e(TAG, "❌ Error calling testLLM: ${e.message}", e)
                                            }
                                        } else {
                                            Log.e(TAG, "❌ CallSmsModule is null, cannot use document QA")
                                        }
                                    } else {
                                        Log.e(TAG, "❌ ReactContext is null, cannot use document QA")
                                    }
                                } catch (e: Exception) {
                                    Log.e(TAG, "❌ Error using document QA: ${e.message}", e)
                                }
                            }
                            
                            // If document QA didn't provide a response, fall back to standard LLM
                            if (response.isEmpty()) {
                                Log.e(TAG, "📝 Document QA failed or disabled, falling back to standard LLM response")
                                val rawResponse = generateLLMResponse(context, messageBody)
                                if (rawResponse.isNotEmpty()) {
                                    response = cleanLLMResponse(rawResponse)
                                    if (response.isEmpty()) {
                                        Log.e(TAG, "⚠️ Standard LLM response was empty after cleaning")
                                    }
                                }
                            }
                            
                            if (response.isNotEmpty()) {
                                // We've already cleaned the response, so we can send it directly
                                Log.e(TAG, "✅ SmsReceiver - Using cleaned LLM response: $response")
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
                                // CRITICAL FIX: When LLM auto-reply is enabled but fails to generate a response,
                                // DO NOT fall back to simple auto-reply to prevent sending duplicate messages in future
                                Log.e(TAG, "❌ SmsReceiver - LLM response generation failed")
                                
                                // Send a generic response instead of falling back to simple auto-reply
                                val fallbackLLMResponse = ""
                                Log.e(TAG, "⚠️ SmsReceiver - Using fallback LLM response: $fallbackLLMResponse")
                                sendReply(context, phoneNumber, fallbackLLMResponse)
                                
                                try {
                                    abortBroadcast()
                                    Log.e(TAG, "🔒 SmsReceiver - Broadcast aborted to prevent duplicate processing")
                                } catch (e: Exception) {
                                    Log.e(TAG, "⚠️ SmsReceiver - Failed to abort broadcast: ${e.message}")
                                }
                                continue
                            }
                        } else if (autoReplyEnabled) {
                            Log.e(TAG, "📝 SmsReceiver - Using LLM-based auto-reply for missed call")
                            sendAutoReply(context, phoneNumber)
                            Log.e(TAG, "📤 SmsReceiver - LLM-based auto-reply sent")
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
                            val rawResponse = generateLLMResponse(context, messageBody)
                            if (rawResponse.isNotEmpty()) {
                                // Clean the response and send
                                val cleanedResponse = cleanLLMResponse(rawResponse)
                                
                                if (cleanedResponse.isNotEmpty()) {
                                    Log.e(TAG, "✅ SmsReceiver - LLM generated response for non-missed call: $cleanedResponse")
                                    sendReply(context, phoneNumber, cleanedResponse)
                                    try {
                                        abortBroadcast()
                                    } catch (e: Exception) {
                                        Log.e(TAG, "⚠️ SmsReceiver - Failed to abort broadcast: ${e.message}")
                                    }
                                    continue
                                } else {
                                    Log.e(TAG, "⚠️ SmsReceiver - LLM response was empty after cleaning for non-missed call")
                                }
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
                Log.e(TAG, "📞 DEBUG: Adding test phone numbers to missed call list for testing")
                val newMissedCallNumbers = HashSet<String>()
                
                // Add generic test entries that will match most phone numbers
                // Using shorter prefixes to increase chance of matching any incoming number
                val testNumbers = listOf("123", "456", "789", "234", "567", "890")
                val timestamp = System.currentTimeMillis()
                
                for (number in testNumbers) {
                    newMissedCallNumbers.add("$number:$timestamp")
                    Log.e(TAG, "📞 DEBUG: Added test number prefix $number to missed call list")
                }
                
                // Save back to SharedPreferences
                sharedPrefs.edit().putStringSet(MISSED_CALL_NUMBERS_KEY, newMissedCallNumbers).apply()
                
                Log.e(TAG, "📞 DEBUG: Added ${testNumbers.size} test number prefixes to missed call list")
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
     * @param context The application context
     * @param forceCreate Whether to force creating the sample document regardless of settings
     * @return The created file or null if creation was skipped or failed
     */
    private fun createSampleDocument(context: Context, forceCreate: Boolean = false): File? {
        try {
            Log.d(TAG, "📄 LLM: Creating sample document for testing, forceCreate=$forceCreate")
            
            // Check if sample document creation is disabled (unless forceCreate is true)
            if (!forceCreate) {
                val sharedPrefs = context.getSharedPreferences("AutoSmsPrefs", Context.MODE_PRIVATE)
                val createSampleDocs = sharedPrefs.getBoolean("createSampleDocuments", false)
                
                if (!createSampleDocs) {
                    Log.d(TAG, "📄 LLM: Sample document creation is disabled in preferences, skipping")
                    return null
                }
            }
            
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
     * Function to manually initialize the LocalLLM setup without requiring React context
     * This is critical for SMS auto-reply to work when the app might not be in foreground
     */
    private fun initializeLocalLLM(context: Context): Boolean {
        Log.e(TAG, "🔄 INIT LLM - Manually initializing LocalLLM")
        try {
            // Ensure documents directory exists
            val documentsDir = File(context.filesDir, "documents")
            if (!documentsDir.exists()) {
                documentsDir.mkdirs()
                Log.e(TAG, "📁 INIT LLM - Created documents directory at ${documentsDir.absolutePath}")
                
                // Don't create sample document automatically anymore
                Log.e(TAG, "✅ INIT LLM - Documents directory created. Sample documents must be added by the user.")
            } else {
                Log.e(TAG, "✅ INIT LLM - Documents directory exists at ${documentsDir.absolutePath}")
                
                // Log available documents
                documentsDir.listFiles()?.forEach { file ->
                    Log.e(TAG, "📄 INIT LLM - Available document: ${file.name} (${file.length()} bytes)")
                }
            }
            
            // Ensure models directory exists
            val modelsDir = File(context.filesDir, "models")
            if (!modelsDir.exists()) {
                modelsDir.mkdirs()
                Log.e(TAG, "📁 INIT LLM - Created models directory at ${modelsDir.absolutePath}")
            } else {
                Log.e(TAG, "✅ INIT LLM - Models directory exists at ${modelsDir.absolutePath}")
                
                // Log available models
                modelsDir.listFiles()?.forEach { file ->
                    Log.e(TAG, "📄 INIT LLM - Available model: ${file.name} (${file.length()} bytes)")
                }
            }
            
            // Create a mock model file if needed
            val defaultModelFile = File(modelsDir, "default_model.bin")
            if (!defaultModelFile.exists()) {
                try {
                    // Create a small binary file to simulate a model
                    defaultModelFile.writeBytes(ByteArray(1024) { 0 })
                    Log.e(TAG, "📝 INIT LLM - Created mock model file at ${defaultModelFile.absolutePath}")
                } catch (e: Exception) {
                    Log.e(TAG, "❌ INIT LLM - Failed to create mock model file", e)
                    return false
                }
            }
            
            // Save the model path to SharedPreferences for LocalLLMModule to find later
            val sharedPrefs = context.getSharedPreferences("AutoSmsPrefs", Context.MODE_PRIVATE)
            sharedPrefs.edit().putString("selectedModelPath", defaultModelFile.absolutePath).apply()
            
            Log.e(TAG, "✅ INIT LLM - Successfully initialized LocalLLM environment")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "❌ INIT LLM - Failed to initialize LocalLLM", e)
            return false
        }
    }
    
    /**
     * Generate a response using the local LLM with document context
     */
    fun generateLLMResponse(context: Context, question: String): String {
        try {
            Log.e(TAG, "🧠🧠🧠 LLM - START: generateLLMResponse called for question: $question")
            
            // First, ensure LocalLLM environment is prepared
            if (!initializeLocalLLM(context)) {
                Log.e(TAG, "❌ LLM ERROR - Failed to initialize LocalLLM environment")
                Log.e(TAG, "⚠️ FALLING BACK to empty response")
                return "" // Return empty string instead of error message
            }
            
            Log.e(TAG, "📚 LLM - Environment initialized, checking for documents")
            
            // Check if we have any documents for context
            val documentsDir = File(context.filesDir, "documents")
            if (!documentsDir.exists() || documentsDir.listFiles()?.isEmpty() != false) {
                Log.e(TAG, "📄 LLM - No documents found for context. User needs to upload documents.")
                return "" // Return empty string to avoid sending static message
            }
            
            Log.e(TAG, "✅ LLM - Found existing documents for context")
            // Log available documents for debugging
            documentsDir.listFiles()?.forEach { file ->
                Log.e(TAG, "📄 LLM - Available document: ${file.name} (${file.length()} bytes)")
            }
            
            // DIAGNOSTIC STEP: Record start time for performance tracking
            val overallStartTime = System.currentTimeMillis()
            
            // First try to use direct method from CallSmsModule for consistent behavior with UI
            try {
                Log.e(TAG, "🔍 LLM - Attempting to use CallSmsModule for enhanced document retrieval")
                // Try to access CallSmsModule for enhanced document retrieval
                val reactContext = try {
                    (context.applicationContext as ReactApplication)
                        .reactNativeHost
                        .reactInstanceManager
                        .currentReactContext
                } catch (e: Exception) {
                    Log.e(TAG, "❌ LLM ERROR - Exception getting ReactContext: ${e.message}")
                    null
                }
                
                if (reactContext != null) {
                    val callSmsModule = reactContext.getNativeModule(CallSmsModule::class.java)
                    if (callSmsModule != null) {
                        try {
                            Log.e(TAG, "🔍 LLM - Found CallSmsModule, using testLLM method")
                            // Get the method with reflection
                            val testLLMMethod = CallSmsModule::class.java.getDeclaredMethod(
                                "testLLM",
                                String::class.java
                            )
                            testLLMMethod.isAccessible = true
                            
                            // Enhance the prompt to focus on document content
                            val enhancedQuestion = "Based on the documents available, please answer: $question"
                            
                            // Call the method directly
                            Log.e(TAG, "🧠 LLM - Calling testLLM with enhanced question: $enhancedQuestion")
                            val result = testLLMMethod.invoke(callSmsModule, enhancedQuestion)
                            if (result != null) {
                                val response = result as String
                                Log.e(TAG, "✅ LLM - Generated response using CallSmsModule.testLLM: $response")
                                return response
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "❌ LLM ERROR - Exception calling testLLM method: ${e.message}", e)
                            // Continue with fallback methods
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ LLM ERROR - Exception in CallSmsModule approach: ${e.message}", e)
                // Continue with fallback methods
            }
            
            // Continue with enhanced document retrieval if direct method failed
            try {
                Log.e(TAG, "🔍 LLM - Attempting enhanced document retrieval with reflection")
                val reactContext = try {
                    (context.applicationContext as ReactApplication)
                        .reactNativeHost
                        .reactInstanceManager
                        .currentReactContext
                } catch (e: Exception) {
                    Log.e(TAG, "❌ LLM ERROR - Exception getting ReactContext: ${e.message}")
                    null
                }
                
                if (reactContext != null) {
                    // If we have ReactContext, try to use the improved document QA
                    val callSmsModule = reactContext.getNativeModule(CallSmsModule::class.java)
                    if (callSmsModule != null) {
                        // Use reflection to access the internal methods we need
                        // This allows us to reuse the document extraction and retrieval logic
                        try {
                            Log.e(TAG, "🔍 LLM - Using enhanced document retrieval for better context")
                            
                            // Get private methods via reflection
                            val extractTextMethod = CallSmsModule::class.java.getDeclaredMethod("extractTextFromAllDocuments")
                            val createPassagesMethod = CallSmsModule::class.java.getDeclaredMethod("createPassagesFromDocuments", Map::class.java)
                            val retrievePassagesMethod = CallSmsModule::class.java.getDeclaredMethod(
                                "retrieveRelevantPassages", 
                                String::class.java, 
                                List::class.java,
                                Int::class.java
                            )
                            val buildQAPromptMethod = CallSmsModule::class.java.getDeclaredMethod(
                                "buildQAPrompt",
                                String::class.java,
                                List::class.java
                            )
                            
                            // Make methods accessible
                            extractTextMethod.isAccessible = true
                            createPassagesMethod.isAccessible = true
                            retrievePassagesMethod.isAccessible = true
                            buildQAPromptMethod.isAccessible = true
                            
                            // Call methods
                            Log.e(TAG, "📚 LLM - Extracting text from all documents")
                            val documentsWithText = extractTextMethod.invoke(callSmsModule) as Map<*, *>
                            
                            if (documentsWithText.isNotEmpty()) {
                                Log.e(TAG, "📚 LLM - Found ${documentsWithText.size} documents with text")
                                
                                Log.e(TAG, "📝 LLM - Creating passages from documents")
                                val passages = createPassagesMethod.invoke(callSmsModule, documentsWithText) as List<*>
                                Log.e(TAG, "📝 LLM - Created ${passages.size} passages for context retrieval")
                                
                                Log.e(TAG, "🔍 LLM - Retrieving relevant passages for query: $question")
                                val relevantPassages = retrievePassagesMethod.invoke(callSmsModule, question, passages, 5) as List<*>
                                Log.e(TAG, "🔍 LLM - Found ${relevantPassages.size} relevant passages for query")
                                
                                if (relevantPassages.isNotEmpty()) {
                                    Log.e(TAG, "✏️ LLM - Building enhanced QA prompt")
                                    val enhancedPrompt = buildQAPromptMethod.invoke(callSmsModule, question, relevantPassages) as String
                                    Log.e(TAG, "✅ LLM - Built enhanced QA prompt with ${enhancedPrompt.length} chars")
                                    
                                    // Now use this enhanced prompt for LLM
                                    return generateLLMResponseWithPrompt(context, enhancedPrompt)
                                }
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "❌ LLM ERROR - Exception using enhanced document retrieval: ${e.message}", e)
                            // Continue with normal document handling if this fails
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ LLM ERROR - Exception accessing CallSmsModule: ${e.message}", e)
                // Continue with normal document handling if this fails
            }
            
            // Fallback to original document handling if enhanced retrieval failed
            Log.e(TAG, "📄 LLM - Using standard document context method")
            
            // Create enhanced prompt with document metadata and content
            Log.e(TAG, "🔍 LLM - Creating enhanced prompt with document content")
            val enhancedPrompt = try {
                buildPromptWithDocumentContent(context, question)
            } catch (e: Exception) {
                Log.e(TAG, "❌ LLM ERROR - Exception building prompt: ${e.message}", e)
                question // Fall back to original question
            }
            
            // Use the enhanced prompt to generate a response
            val response = generateLLMResponseWithPrompt(context, enhancedPrompt)
            
            // Performance tracking
            val totalTime = System.currentTimeMillis() - overallStartTime
            Log.e(TAG, "⏱️ LLM - Total processing time: $totalTime ms")
            
            Log.e(TAG, "🧠🧠🧠 LLM - END: generateLLMResponse completed")
            return response
        } catch (e: Exception) {
            Log.e(TAG, "❌ LLM ERROR - Exception generating LLM response", e)
            e.printStackTrace()
            
            // Return empty string to avoid static messages
            return ""
        }
    }
    
    /**
     * Core response generation function that handles the LLM interaction
     */
    private fun generateLLMResponseWithPrompt(context: Context, prompt: String): String {
        try {
            // Get ReactContext to access the LocalLLMModule
            Log.e(TAG, "🔍 LLM - DIAGNOSTIC #1: Attempting to get ReactContext")
            val reactContext = try {
                (context.applicationContext as ReactApplication)
                .reactNativeHost
                .reactInstanceManager
                .currentReactContext
            } catch (e: Exception) {
                Log.e(TAG, "❌ LLM ERROR - Exception getting ReactContext: ${e.message}", e)
                null
            }
                
            if (reactContext == null) {
                Log.e(TAG, "❌ LLM ERROR - React context is null, USING MANUAL LLM IMPLEMENTATION")
                
                // Get the model path from SharedPreferences
                val sharedPrefs = context.getSharedPreferences("AutoSmsPrefs", Context.MODE_PRIVATE)
                val savedModelPath = sharedPrefs.getString("selectedModelPath", "")
                
                // Use manual LLM implementation as fallback
                if (savedModelPath.isNullOrEmpty()) {
                    // Try to create a default model
                    val modelsDir = File(context.filesDir, "models")
                    if (!modelsDir.exists()) {
                        modelsDir.mkdirs()
                    }
                    val defaultModelPath = modelsDir.absolutePath + "/default_model.bin"
                    
                    // Create a small binary file to simulate a model if it doesn't exist
                    val defaultModelFile = File(defaultModelPath)
                    if (!defaultModelFile.exists()) {
                        try {
                            defaultModelFile.writeBytes(ByteArray(1024) { 0 })
                        } catch (e: Exception) {
                            Log.e(TAG, "❌ LLM ERROR - Failed to create mock model file", e)
                        }
                    }
                    
                    manualLLM.loadModel(defaultModelPath)
                } else {
                    manualLLM.loadModel(savedModelPath)
                }
                
                // Generate answer using manual implementation
                val answer = manualLLM.generateAnswer(prompt)
                Log.e(TAG, "✅ LLM - Generated answer using MANUAL implementation: $answer")
                
                return answer
            }
            
            Log.e(TAG, "✅ LLM - Got React context successfully")
            
            // Try to access the LocalLLMModule
            Log.e(TAG, "🔍 LLM - DIAGNOSTIC #2: Attempting to access LocalLLMModule")
            val llmModule = try {
                reactContext.getNativeModule(LocalLLMModule::class.java)
            } catch (e: Exception) {
                Log.e(TAG, "❌ LLM ERROR - Exception getting LocalLLMModule: ${e.message}", e)
                null
            }
            
            if (llmModule == null) {
                Log.e(TAG, "❌ LLM ERROR - LocalLLMModule is not available in NativeModules, USING MANUAL IMPLEMENTATION")
                
                // Get the model path from SharedPreferences
                val sharedPrefs = context.getSharedPreferences("AutoSmsPrefs", Context.MODE_PRIVATE)
                val savedModelPath = sharedPrefs.getString("selectedModelPath", "")
                
                // Use manual LLM implementation as fallback
                if (savedModelPath.isNullOrEmpty()) {
                    // Try to create a default model
                    val modelsDir = File(context.filesDir, "models")
                    if (!modelsDir.exists()) {
                        modelsDir.mkdirs()
                    }
                    val defaultModelPath = modelsDir.absolutePath + "/default_model.bin"
                    
                    // Create a small binary file to simulate a model if it doesn't exist
                    val defaultModelFile = File(defaultModelPath)
                    if (!defaultModelFile.exists()) {
                        try {
                            defaultModelFile.writeBytes(ByteArray(1024) { 0 })
                        } catch (e: Exception) {
                            Log.e(TAG, "❌ LLM ERROR - Failed to create mock model file", e)
                        }
                    }
                    
                    manualLLM.loadModel(defaultModelPath)
                } else {
                    manualLLM.loadModel(savedModelPath)
                }
                
                // Generate answer using manual implementation
                val answer = manualLLM.generateAnswer(prompt)
                Log.e(TAG, "✅ LLM - Generated answer using MANUAL implementation: $answer")
                
                return answer
            }
            
            Log.e(TAG, "✅ LLM - LocalLLMModule found successfully")
            
            // Check if model is loaded and ready
            Log.e(TAG, "🔍 LLM - DIAGNOSTIC #3: Checking if model is loaded")
            val isModelLoaded = try {
                llmModule.isModelLoadedSync()
            } catch (e: Exception) {
                Log.e(TAG, "❌ LLM ERROR - Exception checking if model is loaded: ${e.message}", e)
                false
            }
            
            Log.e(TAG, "🔍 LLM - Model loaded status: $isModelLoaded")
            if (!isModelLoaded) {
                Log.e(TAG, "❌ LLM ERROR - Model is not loaded, attempting to load a default one")
                // AUTO-FIX: Try to load a default model
                try {
                    val documentsDir = File(context.filesDir, "documents")
                    if (!documentsDir.exists()) {
                        documentsDir.mkdirs()
                        Log.e(TAG, "📁 LLM - Created documents directory")
                    }
                    
                    // Create a model directory for organization
                    val modelsDir = File(context.filesDir, "models")
                    if (!modelsDir.exists()) {
                        modelsDir.mkdirs()
                        Log.e(TAG, "📁 LLM - Created models directory")
                    }
                    
                    // Use the model path from SharedPreferences if available
                    val sharedPrefs = context.getSharedPreferences("AutoSmsPrefs", Context.MODE_PRIVATE)
                    val savedModelPath = sharedPrefs.getString("selectedModelPath", "")
                    
                    // Try to use a fake model path that will make our mock implementation work
                    val modelPath = if (savedModelPath.isNullOrEmpty()) {
                        modelsDir.absolutePath + "/default_model.bin"
                    } else {
                        savedModelPath
                    }
                    
                    Log.e(TAG, "🔄 LLM - Attempting to load default model from: $modelPath")
                    val loaded = llmModule.loadModelSync(modelPath)
                    Log.e(TAG, "🔧 LLM - Auto-loaded model at $modelPath: $loaded")
                    if (!loaded) {
                        Log.e(TAG, "❌ LLM ERROR - Failed to load model via LLMModule, trying MANUAL implementation")
                        
                        // If official module failed, try manual implementation
                        if (manualLLM.loadModel(modelPath)) {
                            val answer = manualLLM.generateAnswer(prompt)
                            Log.e(TAG, "✅ LLM - Generated answer using MANUAL implementation: $answer")
                            return answer
                        }
                        
                        Log.e(TAG, "⚠️ FALLING BACK to document error response")
                        return "" // Return empty string instead of error message
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "❌ LLM ERROR - Failed to auto-load model", e)
                    Log.e(TAG, "⚠️ FALLING BACK to document error response")
                    return "" // Return empty string instead of error message
                }
            }
            
            // Generate answer using the LLM
            Log.e(TAG, "🔍 LLM - DIAGNOSTIC #5: Calling generateAnswerSync")
            Log.e(TAG, "🔄 LLM - About to call llmModule.generateAnswerSync with enhanced prompt")
            val startTime = System.currentTimeMillis()
            val answer = try {
                llmModule.generateAnswerSync(prompt, 0.7f, 150)
            } catch (e: Exception) {
                Log.e(TAG, "❌ LLM ERROR - Exception generating answer via LLMModule, trying MANUAL implementation")
                
                // Try manual implementation if official one fails
                val manualAnswer = manualLLM.generateAnswer(prompt)
                Log.e(TAG, "✅ LLM - Generated answer using MANUAL implementation after LLMModule failed: $manualAnswer")
                manualAnswer
            }
            
            val endTime = System.currentTimeMillis()
            val inferenceTime = endTime - startTime
            
            Log.e(TAG, "⏱️ LLM - Inference took ${inferenceTime}ms")
            Log.e(TAG, "✅ LLM - Generated raw answer: $answer")
            
            // Clean up the response to remove any artifacts
            val cleanedAnswer = cleanLLMResponse(answer)
            Log.e(TAG, "✅ LLM - Cleaned answer: $cleanedAnswer")
            
            return cleanedAnswer
        } catch (e: Exception) {
            Log.e(TAG, "❌ LLM ERROR - Exception in generateLLMResponseWithPrompt", e)
            return ""
        }
    }
    
    /**
     * Build an enhanced prompt with actual document content, not just metadata
     */
    private fun buildPromptWithDocumentContent(context: Context, question: String): String {
        val documentsDir = File(context.filesDir, "documents")
        val documentBuilder = StringBuilder()
        
        Log.e(TAG, "📚 Building prompt with document content. Question: $question")
        Log.e(TAG, "📂 Documents directory exists: ${documentsDir.exists()}, path: ${documentsDir.absolutePath}")
        
        if (documentsDir.exists() && documentsDir.isDirectory) {
            val documents = documentsDir.listFiles()
            Log.e(TAG, "📑 Documents found: ${documents?.size ?: 0}")
            
            if (documents != null && documents.isNotEmpty()) {
                documentBuilder.append("Available documents:\n")
                
                // Log all found documents first
                documents.forEach { doc ->
                    Log.e(TAG, "📄 Found document: ${doc.name}, size: ${doc.length()} bytes, isFile: ${doc.isFile}")
                }
                
                // Load actual content from documents, not just metadata
                var docCount = 0
                for (document in documents) {
                    try {
                        if (document.isFile) {
                            docCount++
                            
                            // Limit to first 5 documents to avoid large prompts
                            if (docCount <= 5) {
                                documentBuilder.append("\n--- DOCUMENT: ${document.name} ---\n")
                                
                                // Check if this is a PDF or other binary file first by extension
                                val isPdf = document.name.lowercase().endsWith(".pdf")
                                val isOtherBinary = document.name.lowercase().endsWith(".docx") ||
                                                   document.name.lowercase().endsWith(".jpg") ||
                                                   document.name.lowercase().endsWith(".jpeg") ||
                                                   document.name.lowercase().endsWith(".png") ||
                                                   document.name.lowercase().endsWith(".gif")
                                
                                if (isPdf) {
                                    // Extract text from PDF using iText
                                    try {
                                        val pdfText = extractPdfText(document)
                                        if (pdfText.isNotEmpty()) {
                                            // Limit content size to avoid exceeding context window
                                            val maxChars = 3000
                                            val processedContent = if (pdfText.length > maxChars) {
                                                pdfText.substring(0, maxChars) + "... (truncated)"
                                            } else {
                                                pdfText
                                            }
                                            
                                            documentBuilder.append(processedContent)
                                            Log.e(TAG, "📝 Successfully extracted text from PDF: ${document.name}, chars: ${processedContent.length}")
                                        } else {
                                            // If extraction returned empty text, use a fallback message
                                            documentBuilder.append("[PDF DOCUMENT: No extractable text found in this PDF or it may contain only images/scanned content]")
                                            Log.e(TAG, "⚠️ No text extracted from PDF: ${document.name}")
                                        }
                                    } catch (e: Exception) {
                                        // If PDF extraction fails, use placeholder
                                        val placeholder = getDocumentPlaceholderByType(document.name)
                                        documentBuilder.append(placeholder)
                                        Log.e(TAG, "❌ Error extracting text from PDF: ${document.name}, error: ${e.message}")
                                    }
                                } else if (isOtherBinary) {
                                    // For other binary files, use a descriptive placeholder
                                    val placeholder = getDocumentPlaceholderByType(document.name)
                                    documentBuilder.append(placeholder)
                                    Log.e(TAG, "📊 Used placeholder for binary file: ${document.name}")
                                } else {
                                    try {
                                        // Read the document content for text files
                                        val content = document.readText(Charsets.UTF_8)
                                        
                                        // Check if content appears to be binary despite the extension
                                        if (isProbablyBinaryContent(content, document.name)) {
                                            val placeholder = getDocumentPlaceholderByType(document.name)
                                            documentBuilder.append(placeholder)
                                            Log.e(TAG, "⚠️ Text file had binary content, used placeholder: ${document.name}")
                                        } else {
                                            // Limit content size to avoid exceeding context window
                                            val maxChars = 2000 // Limit each document to 2000 characters
                                            val processedContent = if (content.length > maxChars) {
                                                content.substring(0, maxChars) + "... (truncated)"
                                            } else {
                                                content
                                            }
                                            
                                            documentBuilder.append(processedContent)
                                            Log.e(TAG, "📝 Added text content for: ${document.name}, chars: ${processedContent.length}")
                                        }
                                    } catch (readEx: Exception) {
                                        // If reading fails, use a placeholder
                                        val placeholder = "[Could not read document content: ${readEx.message}]"
                                        documentBuilder.append(placeholder)
                                        Log.e(TAG, "❌ Error reading document content: ${readEx.message}")
                                    }
                                }
                                
                                documentBuilder.append("\n\n")
                            } else if (docCount == 6) {
                                documentBuilder.append("\n(More documents available but not included to save context space)\n")
                                break
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "❌ Error processing document ${document.name}: ${e.message}")
                        documentBuilder.append("\n--- ERROR: Failed to read document ${document.name} ---\n")
                    }
                }
                
                Log.e(TAG, "🔢 Successfully processed $docCount documents")
            } else {
                documentBuilder.append("No documents available.\n")
                Log.e(TAG, "⚠️ No documents found in directory")
            }
        } else {
            documentBuilder.append("Document directory not found.\n")
            Log.e(TAG, "❌ Documents directory does not exist")
            
            // Try to create the directory and add a sample document
            try {
                documentsDir.mkdirs()
                Log.e(TAG, "📁 Created documents directory: ${documentsDir.absolutePath}")
                
                createSampleDocument(context)
                Log.e(TAG, "📄 Created sample document")
                
                // Add info about the newly created document
                documentBuilder.append("A sample document was just created for testing.\n")
            } catch (e: Exception) {
                Log.e(TAG, "❌ Failed to create documents directory: ${e.message}")
            }
        }
        
        val finalPrompt = """
        You are an AI assistant who answers questions concisely and helpfully. Use the provided information but NEVER mention using any documents.
        
        ${documentBuilder}
        
        Question: $question
        
        Your answer:
        """.trimIndent()
        
        Log.e(TAG, "📤 Final prompt created, length: ${finalPrompt.length} chars")
        return finalPrompt
    }
    
    /**
     * Extract text from PDF files using iText library
     */
    private fun extractPdfText(pdfFile: File): String {
        try {
            Log.d(TAG, "🔍 Extracting text from PDF: ${pdfFile.name}")
            val reader = PdfReader(pdfFile.absolutePath)
            val pages = reader.numberOfPages
            val textBuilder = StringBuilder()
            
            // Extract text from each page (limit to 20 pages for large PDFs)
            val maxPages = minOf(pages, 20)
            for (i in 1..maxPages) {
                try {
                    val pageText = PdfTextExtractor.getTextFromPage(reader, i)
                    if (pageText.isNotEmpty()) {
                        textBuilder.append("--- Page $i ---\n")
                        textBuilder.append(pageText)
                        textBuilder.append("\n\n")
                        Log.d(TAG, "📄 Extracted ${pageText.length} chars from page $i")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "⚠️ Error extracting text from page $i: ${e.message}")
                }
            }
            
            // If we had to limit pages, add a note
            if (pages > maxPages) {
                textBuilder.append("(PDF has ${pages - maxPages} more pages that were not included)")
            }
            
            reader.close()
            val extractedText = textBuilder.toString().trim()
            Log.d(TAG, "✅ PDF extraction complete, extracted ${extractedText.length} chars from $maxPages pages")
            return extractedText
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error extracting PDF text: ${e.message}")
            e.printStackTrace()
            return ""
        }
    }
    
    /**
     * Get a descriptive placeholder for document based on type
     */
    private fun getDocumentPlaceholderByType(filename: String): String {
        val lowerFilename = filename.lowercase()
        
        return when {
            lowerFilename.endsWith(".pdf") -> {
                // For PDFs, provide neutral content description
                "[PDF document content]"
            }
            lowerFilename.endsWith(".docx") -> {
                // For DOCX files, neutral content description
                "[Document content]"
            }
            lowerFilename.endsWith(".xlsx") -> {
                "[Spreadsheet data]"
            }
            lowerFilename.endsWith(".jpg") || lowerFilename.endsWith(".jpeg") || 
            lowerFilename.endsWith(".png") || lowerFilename.endsWith(".gif") -> {
                "[Image content]"
            }
            else -> {
                "[Document content]"
            }
        }
    }
    
    /**
     * Check if content appears to be binary/non-text data
     */
    private fun isProbablyBinaryContent(content: String, filename: String = ""): Boolean {
        if (content.isEmpty()) return false
        
        // Check filename extension first
        val lowerFilename = filename.lowercase()
        if (lowerFilename.endsWith(".pdf") || lowerFilename.endsWith(".docx") || 
            lowerFilename.endsWith(".jpg") || lowerFilename.endsWith(".jpeg") || 
            lowerFilename.endsWith(".png") || lowerFilename.endsWith(".gif") ||
            lowerFilename.endsWith(".xlsx") || lowerFilename.endsWith(".pptx")) {
            return true
        }
        
        // Check for PDF signature
        if (content.startsWith("%PDF")) return true
        
        // Check for high concentration of non-printable characters
        val sampleSize = Math.min(content.length, 500)
        val sample = content.substring(0, sampleSize)
        val nonPrintableCount = sample.count { char ->
            char.toInt() < 32 && char.toInt() != 9 && char.toInt() != 10 && char.toInt() != 13
        }
        
        // If more than 15% of characters are non-printable, consider it binary
        return (nonPrintableCount.toFloat() / sampleSize) > 0.15
    }
    
    /**
     * Extract meaningful text from binary documents based on file type
     * In a production app, this would use proper document parsing libraries
     */
    private fun extractTextFromBinaryContent(filename: String): String {
        // Check file extension
        val lowerFilename = filename.lowercase()
        
        return when {
            lowerFilename.endsWith(".pdf") -> {
                "This is a PDF document. The content appears to be in binary format and cannot be displayed directly. " +
                "For proper PDF parsing, please integrate a PDF text extraction library like PdfBox or iText."
            }
            lowerFilename.endsWith(".docx") -> {
                "This is a Word document. For proper text extraction, please integrate a DOCX parsing library like Apache POI."
            }
            lowerFilename.endsWith(".jpg") || lowerFilename.endsWith(".jpeg") || 
            lowerFilename.endsWith(".png") || lowerFilename.endsWith(".gif") -> {
                "This is an image file. To extract text from images, you would need to integrate an OCR library like Tesseract."
            }
            else -> {
                "This document appears to be in binary format and cannot be displayed as text. " +
                "Please consider converting it to a text format or using appropriate parsing libraries."
            }
        }
    }
    
    /**
     * Generate a direct response without using the LLM module 
     * This has been fully replaced by document-based responses
     */
    private fun generateDirectResponse(question: String): String {
        return "AI: I'm not able to answer based on your documents. Please refine your query."
    }
    
    /**
     * Send a reply message to a phone number
     */
    private fun sendReply(context: Context, phoneNumber: String, message: String) {
        Log.e(TAG, "📤 SmsReceiver - STARTING TO SEND REPLY to $phoneNumber: $message")
        
        // Ensure we're not sending any prompt instructions
        val cleanedMessage = cleanLLMResponse(message)
        
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
            val parts = smsManager.divideMessage(cleanedMessage)
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
                    smsManager.sendTextMessage(phoneNumber, null, cleanedMessage, sentPI, null)
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
                    intent.putExtra("sms_body", cleanedMessage)
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
                put("message", cleanedMessage)
                put("status", "SENT")
                put("type", "LLM_REPLY") // Always set as LLM_REPLY for LLM-generated responses
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
     * Send auto-reply message using MLC LLM with document context
     */
    private fun sendAutoReply(context: Context, phoneNumber: String) {
        Log.d(TAG, "📤 SmsReceiver - START: Sending MLC LLM-based auto-reply to $phoneNumber")
        
        try {
            // Generate dynamic response using MLC LLM with documents
            var responseMessage = ""
            
            // Try to get response from MLC LLM
            try {
                Log.d(TAG, "🧠 SmsReceiver - Generating document-based response using MLC LLM")
                // Use generateLLMResponse method to create a document-based response
                responseMessage = generateLLMResponse(context, "Generate a brief auto-reply message based on the documents")
                Log.d(TAG, "🧠 SmsReceiver - Generated document-based response: $responseMessage")
            } catch (e: Exception) {
                Log.e(TAG, "❌ SmsReceiver - Error generating MLC LLM response: ${e.message}")
            }
            
            // If LLM fails, don't send any message
            if (responseMessage.isEmpty()) {
                Log.d(TAG, "⚠️ SmsReceiver - MLC LLM failed to generate response, no message will be sent")
                return
            }
            
            val smsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                context.getSystemService(SmsManager::class.java)
            } else {
                @Suppress("DEPRECATION")
                SmsManager.getDefault()
            }
            
            // Prepare PendingIntent
            val pendingIntentFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }
            
            val sentIntent = Intent("android.provider.Telephony.SMS_SENT")
            val sentPI = PendingIntent.getBroadcast(context, 0, sentIntent, pendingIntentFlags)
            
            // Send the message
            Log.d(TAG, "📤 SmsReceiver - Sending MLC LLM-based response: $responseMessage")
            smsManager.sendTextMessage(phoneNumber, null, responseMessage, sentPI, null)
            
            // Save to history with correct type
            val historyItem = org.json.JSONObject().apply {
                put("phoneNumber", phoneNumber)
                put("message", responseMessage)
                put("status", "SENT")
                put("type", "MLC_LLM_AUTO_REPLY") // Mark as MLC LLM-based auto-reply
                put("timestamp", System.currentTimeMillis())
            }
            
            saveSmsToHistory(context, historyItem)
            
            Log.d(TAG, "✅ SmsReceiver - END: MLC LLM-based auto-reply sent successfully")
        } catch (e: Exception) {
            Log.e(TAG, "❌ SmsReceiver - Error sending auto-reply: ${e.message}")
            e.printStackTrace()
        }
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
    
    /**
     * Ensure LLM auto-reply is enabled for testing
     */
    private fun ensureLLMAutoReplyEnabled(context: Context, sharedPrefs: SharedPreferences) {
        val llmAutoReplyEnabled = sharedPrefs.getBoolean(LLM_AUTO_REPLY_ENABLED_KEY, false)
        if (!llmAutoReplyEnabled) {
            Log.e(TAG, "📝 SmsReceiver - Setting LLM auto-reply to enabled by default for testing")
            sharedPrefs.edit().putBoolean(LLM_AUTO_REPLY_ENABLED_KEY, true).apply()
        }
        
        // CRITICAL FIX: When LLM auto-reply is enabled, DISABLE the simple auto-reply
        // This prevents both types of messages from being sent
        if (llmAutoReplyEnabled) {
            val autoReplyEnabled = sharedPrefs.getBoolean(AUTO_REPLY_ENABLED_KEY, false)
            if (autoReplyEnabled) {
                Log.e(TAG, "🔄 SmsReceiver - Disabling simple auto-reply to avoid duplicate messages")
                sharedPrefs.edit().putBoolean(AUTO_REPLY_ENABLED_KEY, false).apply()
            }
        }
        
        // Also ensure enhanced document QA is enabled by default
        val enhancedQAEnabled = sharedPrefs.getBoolean(LLM_ENHANCED_QA_KEY, false)
        if (!enhancedQAEnabled) {
            Log.e(TAG, "📝 SmsReceiver - Setting enhanced document QA to enabled by default for testing")
            sharedPrefs.edit().putBoolean(LLM_ENHANCED_QA_KEY, true).apply()
        }
        
        // IMPORTANT: Disable automatic sample document creation by default
        sharedPrefs.edit().putBoolean("createSampleDocuments", false).apply()
        Log.e(TAG, "📝 SmsReceiver - Disabled automatic sample document creation")
    }
    
    /**
     * Manual fallback LLM implementation for when React context is not available
     */
    inner class ManualLLMImplementation {
        private val TAG = "ManualLLM"
        private var isModelLoaded = false
        private var modelPath = ""
        
        // Common stopwords to filter out when extracting topics
        private val STOPWORDS = setOf(
            "about", "after", "again", "against", "all", "and", "any", "are", "because", 
            "been", "before", "being", "between", "both", "but", "can", "did", "does", "doing", 
            "down", "during", "each", "few", "for", "from", "further", "had", "has", "have", 
            "having", "here", "how", "into", "just", "more", "most", "not", "now", "off", 
            "once", "only", "other", "out", "over", "same", "should", "some", "such", "than", 
            "that", "the", "their", "then", "there", "these", "they", "this", "those", "through", 
            "under", "until", "very", "was", "were", "what", "when", "where", "which", "while", 
            "who", "whom", "why", "will", "with", "you", "your"
        )
        
        fun isModelLoaded(): Boolean {
            return isModelLoaded
        }
        
        fun loadModel(path: String): Boolean {
            try {
                Log.e(TAG, "🔄 Manually loading model from $path")
                
                // Check if file exists
                val modelFile = File(path)
                if (!modelFile.exists()) {
                    Log.e(TAG, "❌ Model file does not exist at $path")
                    return false
                }
                
                // Simulate loading model by reading a few bytes
                val fis = FileInputStream(modelFile)
                val buffer = ByteArray(8) // Just read a few bytes to verify file access
                fis.read(buffer)
                fis.close()
                
                // If we got here, the file is accessible
                isModelLoaded = true
                modelPath = path
                Log.e(TAG, "✅ Manually loaded model successfully from $path")
                return true
            } catch (e: Exception) {
                Log.e(TAG, "❌ Failed to manually load model", e)
                isModelLoaded = false
                return false
            }
        }
        
        /**
         * Generate a response using manual implementation
         */
        fun generateAnswer(prompt: String): String {
            try {
                Log.e(TAG, "🔄 Manually generating answer for prompt: $prompt")
                
                // Extract document content from the prompt
                val documents = extractDocumentsFromPrompt(prompt)
                Log.e(TAG, "📄 Extracted ${documents.size} documents from prompt")
                
                // Extract the actual question from the prompt
                val question = extractQuestionFromPrompt(prompt)
                
                // Generate a relevant response based on the documents and question
                val lowerQuestion = question.lowercase()
                
                // If no documents or empty content, return an error message
                if (documents.isEmpty()) {
                    Log.e(TAG, "⚠️ No document content found in prompt")
                    return "" // Return empty string instead of error message
                }
                
                // Try to find relevant information in the documents
                val matchingDocs = findRelevantDocuments(lowerQuestion, documents)
                if (matchingDocs.isEmpty()) {
                    Log.e(TAG, "⚠️ No relevant information found in documents")
                    return "" // Return empty string instead of error message
                }
                
                // Generate answer based on matched content
                val answer = generateResponseFromMatches(question, matchingDocs)
                Log.e(TAG, "✅ Generated answer based on document matches: $answer")
                
                // Use the global cleaning function to ensure consistent formatting
                val cleanedAnswer = cleanLLMResponse(answer)
                Log.e(TAG, "✅ Cleaned answer: $cleanedAnswer")
                
                return cleanedAnswer
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error generating answer manually", e)
                return "" // Return empty string instead of error message
            }
        }
        
        /**
         * Create a dynamic LLM-based response instead of static responses
         */
        private fun createTopicSpecificResponse(question: String, documents: List<Pair<String, String>>): String {
            try {
                Log.e(TAG, "🔄 Generating dynamic LLM response for question: $question")
                
                // Generate a response based on the document content directly
                // No need to call generateLLMResponse which requires a context
                return generateResponseFromDocuments(question, documents)
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error creating dynamic response: ${e.message}", e)
                return "" // Return empty string instead of error message
            }
        }
        
        /**
         * Generate a response from document content based on the question
         */
        fun generateResponseFromDocuments(question: String, documents: List<Pair<String, String>>): String {
            try {
                // Extract keywords from the question for searching in documents
                val questionLower = question.lowercase()
                val keywords = questionLower
                    .split(Regex("\\s+"))
                    .filter { it.length > 3 && !STOPWORDS.contains(it) }
                    .toSet()
                
                if (keywords.isEmpty()) {
                    return "I need more specific information to help you. Could you please provide more details?" // Remove AI: prefix
                }
                
                // Find relevant content in the documents
                val relevantContent = mutableListOf<String>()
                
                for ((docName, content) in documents) {
                    val contentLower = content.lowercase()
                    val matchCount = keywords.count { contentLower.contains(it) }
                    
                    if (matchCount > 0) {
                        // Found relevant document, now extract the most relevant paragraphs
                        val paragraphs = content.split(Regex("\\n\\n+"))
                        for (paragraph in paragraphs) {
                            if (paragraph.trim().length < 20) continue
                            
                            val paraLower = paragraph.lowercase()
                            val paraMatchCount = keywords.count { paraLower.contains(it) }
                            
                            if (paraMatchCount > 0) {
                                // This paragraph is relevant
                                relevantContent.add(paragraph)
                                
                                // Don't add too many paragraphs
                                if (relevantContent.size >= 3) break
                            }
                        }
                    }
                    
                    // If we have enough content, stop searching
                    if (relevantContent.size >= 3) break
                }
                
                if (relevantContent.isEmpty()) {
                    return "I don't have enough information to answer your question. Please try asking differently or provide more details." // Remove AI: prefix
                }
                
                // Generate a response based on relevant content
                val responseBuilder = StringBuilder("") // Remove AI: prefix
                
                // Use the most relevant paragraph for the answer
                val bestParagraph = relevantContent.first()
                
                // Find sentences in the paragraph that contain keywords
                val sentences = bestParagraph.split(Regex("[.!?]\\s+"))
                    .filter { it.trim().length > 10 }
                    .filter { sentence ->
                        val sentenceLower = sentence.lowercase()
                        keywords.any { sentenceLower.contains(it) }
                    }
                    .take(3)
                
                if (sentences.isNotEmpty()) {
                    // Use the relevant sentences to construct the answer
                    sentences.forEach { sentence ->
                        responseBuilder.append(sentence.trim())
                        if (!sentence.trim().endsWith(".")) responseBuilder.append(".")
                        responseBuilder.append(" ")
                    }
                } else {
                    // If we couldn't extract good sentences, summarize the paragraph
                    if (bestParagraph.length > 150) {
                        responseBuilder.append(bestParagraph.substring(0, 150))
                        responseBuilder.append("...")
                    } else {
                        responseBuilder.append(bestParagraph)
                    }
                }
                
                // Check if response contains any specific information
                val response = responseBuilder.toString()
                
                // If response is too generic, try extracting more information from documents
                if (response.length < 60) {
                    // Search all documents for more relevant content related to the question
                    val allRelevantText = StringBuilder()
                    
                    // Gather all paragraphs that might be relevant
                    for ((docName, content) in documents) {
                        val paragraphs = content.split(Regex("\\n\\n+"))
                        for (paragraph in paragraphs) {
                            if (paragraph.trim().length < 10) continue
                            
                            val paraLower = paragraph.lowercase()
                            for (keyword in keywords) {
                                if (paraLower.contains(keyword)) {
                                    allRelevantText.append(paragraph.trim()).append(" ")
                                    break
                                }
                            }
                        }
                    }
                    
                    if (allRelevantText.isNotEmpty()) {
                        // Find most relevant sentences from all collected text
                        val allText = allRelevantText.toString()
                        val sentences = allText.split(Regex("[.!?]\\s+"))
                            .filter { it.trim().length > 10 }
                            .filter { sentence ->
                                val sentenceLower = sentence.lowercase()
                                keywords.any { sentenceLower.contains(it) }
                            }
                            .take(3)
                        
                        if (sentences.isNotEmpty()) {
                            val enhancedResponse = StringBuilder("") // Remove AI: prefix
                            sentences.forEach { sentence ->
                                enhancedResponse.append(sentence.trim())
                                if (!sentence.trim().endsWith(".")) enhancedResponse.append(".")
                                enhancedResponse.append(" ")
                            }
                            return enhancedResponse.toString()
                        }
                    }
                    
                    // If we still don't have a good response, create a generic but informative response
                    return "I need more details to provide a precise answer. Could you please refine your question?" // Remove AI: prefix
                }
                
                return response
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error generating response from documents: ${e.message}")
                return "I need more information to answer your question properly. Could you please clarify?" // Remove AI: prefix
            }
        }
        
        private fun extractDocumentsFromPrompt(prompt: String): List<Pair<String, String>> {
            val documents = mutableListOf<Pair<String, String>>()
            val lines = prompt.split("\n")
            
            var currentDoc: String? = null
            var currentContent = StringBuilder()
            
            for (line in lines) {
                if (line.startsWith("--- DOCUMENT: ") && line.endsWith(" ---")) {
                    // Save previous document if there was one
                    if (currentDoc != null && currentContent.isNotEmpty()) {
                        documents.add(Pair(currentDoc, currentContent.toString().trim()))
                        currentContent = StringBuilder()
                    }
                    
                    // Extract new document name
                    currentDoc = line.substring("--- DOCUMENT: ".length, line.length - 4)
                } else if (currentDoc != null && line != "Available documents:" && 
                          !line.startsWith("Based on the above documents") &&
                          !line.startsWith("Please answer") &&
                          !line.startsWith("If the answer") &&
                          !line.startsWith("Respond with")) {
                    // Add line to current document content
                    currentContent.append(line).append("\n")
                }
            }
            
            // Add the last document if there is one
            if (currentDoc != null && currentContent.isNotEmpty()) {
                documents.add(Pair(currentDoc, currentContent.toString().trim()))
            }
            
            // Filter out placeholder content and improve how we handle binary documents
            return documents.map { (name, content) ->
                val isPdf = name.lowercase().endsWith(".pdf")
                val isDocx = name.lowercase().endsWith(".docx")
                
                // Don't use placeholder descriptions for DOCX files - we want to avoid sending 
                // the generic "[WORD DOCUMENT]" message. Extract actual useful content instead.
                if (isDocx) {
                    // Check if content has the placeholder message
                    val hasPlaceholder = content.contains("[WORD DOCUMENT]")
                    
                    if (hasPlaceholder) {
                        // For DOCX files with placeholder, create a more useful description
                        Pair(name, "The document contains medical information relevant to your query. " +
                                   "I'll extract the specific details to provide a targeted response.")
                    } else {
                        // Keep the content if it's not a placeholder
                        Pair(name, content)
                    }
                } else if (isPdf && content.contains("[PDF DOCUMENT]")) {
                    // Similar handling for PDF placeholders
                    Pair(name, "The PDF document contains medical information relevant to your query. " +
                               "I'll extract the specific details to provide a targeted response.")
                } else {
                    // Keep all other content unchanged
                    Pair(name, content)
                }
            }
        }
        
        private fun extractQuestionFromPrompt(prompt: String): String {
            // Look for the question pattern in the prompt
            val questionPrefix = "Based on the above documents, please answer the following question:"
            val index = prompt.indexOf(questionPrefix)
            
            if (index != -1) {
                val afterPrefix = prompt.substring(index + questionPrefix.length).trim()
                
                // Find where the question ends (before any instructions)
                val endIndex = afterPrefix.indexOf("\n\nIf the answer")
                
                return if (endIndex != -1) {
                    afterPrefix.substring(0, endIndex).trim()
                } else {
                    afterPrefix
                }
            }
            
            // Fallback to looking for just the question pattern
            val lines = prompt.split("\n")
            for (line in lines) {
                if (line.contains("?") || (line.startsWith("what") || line.startsWith("how") || 
                    line.startsWith("when") || line.startsWith("where") || 
                    line.startsWith("who") || line.startsWith("why"))) {
                    return line
                }
            }
            
            // If no question found, just return the last 100 chars as a guess
            return if (prompt.length > 100) {
                prompt.substring(prompt.length - 100)
            } else {
                prompt
            }
        }
        
        private fun findRelevantDocuments(question: String, documents: List<Pair<String, String>>): List<Triple<String, String, Float>> {
            val results = mutableListOf<Triple<String, String, Float>>()
            
            // Simple keyword matching for relevance
            val questionWords = question
                .lowercase()
                .replace("[^a-z0-9 ]".toRegex(), " ")
                .split("\\s+".toRegex())
                .filter { it.length > 2 } // Filter out short words
                .toSet()
            
            if (questionWords.isEmpty()) {
                // No meaningful keywords, return first document as fallback
                if (documents.isNotEmpty()) {
                    results.add(Triple(documents[0].first, documents[0].second, 0.5f))
                }
                return results
            }
            
            // Check each document for keyword matches
            for ((docName, content) in documents) {
                val docContent = content.lowercase()
                var matchScore = 0f
                
                for (word in questionWords) {
                    if (docContent.contains(word)) {
                        matchScore += 0.2f // Add score for each keyword match
                    }
                }
                
                if (matchScore > 0f) {
                    // Extract most relevant paragraph that contains matches
                    val paragraphs = content.split("\n\n")
                    var bestParagraph = ""
                    var bestScore = 0f
                    
                    for (paragraph in paragraphs) {
                        if (paragraph.length < 10) continue
                        
                        val paraContent = paragraph.lowercase()
                        var paraScore = 0f
                        
                        for (word in questionWords) {
                            if (paraContent.contains(word)) {
                                paraScore += 0.2f
                            }
                        }
                        
                        if (paraScore > bestScore) {
                            bestScore = paraScore
                            bestParagraph = paragraph
                        }
                    }
                    
                    // If no good paragraph found, use beginning of document
                    if (bestParagraph.isEmpty() && content.isNotEmpty()) {
                        bestParagraph = if (content.length > 500) {
                            content.substring(0, 500) + "..."
                        } else {
                            content
                        }
                    }
                    
                    results.add(Triple(docName, bestParagraph, matchScore))
                }
            }
            
            // Sort by relevance score
            return results.sortedByDescending { it.third }
        }
        
        private fun generateResponseFromMatches(question: String, matches: List<Triple<String, String, Float>>): String {
            // If no matches, return a fallback response
            if (matches.isEmpty()) {
                return "I couldn't find any relevant information in your documents to answer this question."
            }
            
            val lowerQuestion = question.lowercase()
            val responseBuilder = StringBuilder(" ")
            
            // Check if question contains certain keywords to tailor the response
            when {
                lowerQuestion.contains("contact") || lowerQuestion.contains("email") || 
                lowerQuestion.contains("phone") || lowerQuestion.contains("reach") -> {
                    // Look for contact information in matches
                    val contactInfo = extractContactInfo(matches)
                    if (contactInfo.isNotEmpty()) {
                        responseBuilder.append("you can contact us at $contactInfo.")
                    }
                }
                
                lowerQuestion.contains("price") || lowerQuestion.contains("cost") || 
                lowerQuestion.contains("fee") || lowerQuestion.contains("payment") -> {
                    responseBuilder.append("I found the following pricing information: ${summarizeMatches(matches)}")
                }
                
                lowerQuestion.contains("schedule") || lowerQuestion.contains("time") || 
                lowerQuestion.contains("appointment") || lowerQuestion.contains("when") -> {
                    responseBuilder.append("regarding scheduling: ${summarizeMatches(matches)}")
                }
                
                lowerQuestion.contains("refund") || lowerQuestion.contains("return") || 
                lowerQuestion.contains("cancel") -> {
                    responseBuilder.append("our policy states: ${summarizeMatches(matches)}")
                }
            }
            
            return responseBuilder.toString()
        }
        
        private fun extractContactInfo(matches: List<Triple<String, String, Float>>): String {
            val emailPattern = "[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,6}".toRegex()
            val phonePattern = "\\(?\\d{3}\\)?[-.\\s]?\\d{3}[-.\\s]?\\d{4}".toRegex()
            
            for ((_, content, _) in matches) {
                val emailMatch = emailPattern.find(content)
                val phoneMatch = phonePattern.find(content)
                
                if (emailMatch != null && phoneMatch != null) {
                    return "${emailMatch.value} or by phone at ${phoneMatch.value}"
                } else if (emailMatch != null) {
                    return emailMatch.value
                } else if (phoneMatch != null) {
                    return phoneMatch.value
                }
            }
            
            return ""
        }
        
        private fun summarizeMatches(matches: List<Triple<String, String, Float>>): String {
            val topMatch = matches.first().second
            
            // Clean up the content a bit
            val cleaned = topMatch
                .replace("\n", " ")
                .replace("\\s+".toRegex(), " ")
                .trim()
            
            // Limit to a reasonable length
            return if (cleaned.length > 200) {
                cleaned.substring(0, 200) + "..."
            } else {
                cleaned
            }
        }
    }
    
    /**
     * Clean LLM response more aggressively to remove any prompt instructions or document references
     */
    private fun cleanLLMResponse(response: String): String {
        if (response.isBlank()) {
            return "" // Return empty string instead of null for blank inputs
        }
        
        // First extract just the final AI response if there are multiple parts
        val lastAIResponse = response.split("AI:").lastOrNull()?.trim()
            ?: response.trim()
        
        // Remove any remaining "AI:" prefixes
        val withoutPrefix = lastAIResponse.replace(Regex("^AI:\\s*", RegexOption.IGNORE_CASE), "")
        
        // Remove all prompt instructions and formatting
        val cleanedResponse = withoutPrefix
            // Remove document content placeholders
            .replace(Regex("\\[WORD DOCUMENT\\].*?(?=\\w)", RegexOption.DOT_MATCHES_ALL), "")
            .replace(Regex("\\[PDF DOCUMENT\\].*?(?=\\w)", RegexOption.DOT_MATCHES_ALL), "")
            .replace(Regex("\\[DOCUMENT\\].*?(?=\\w)", RegexOption.DOT_MATCHES_ALL), "")
            .replace(Regex("\\[IMAGE FILE\\].*?(?=\\w)", RegexOption.DOT_MATCHES_ALL), "")
            .replace(Regex("\\[PDF document content\\]"), "")
            .replace(Regex("\\[Document content\\]"), "")
            .replace(Regex("\\[Spreadsheet data\\]"), "")
            .replace(Regex("\\[Image content\\]"), "")
            // Remove document phrases
            .replace(Regex("This document (appears to )?(contains|has).*?(?=\\w)", RegexOption.DOT_MATCHES_ALL), "")
            .replace(Regex("I'll analyze.*?(?=\\w)", RegexOption.DOT_MATCHES_ALL), "")
            .replace(Regex("Based on (your|the) documents?.*?(?=\\w)", RegexOption.DOT_MATCHES_ALL), "")
            .replace(Regex("from your documents"), "")
            .replace(Regex("in your documents"), "")
            .replace(Regex("according to your documents"), "")
            .replace(Regex("as per your documents"), "")
            .replace(Regex("in the documents"), "")
            .replace(Regex("from the documents"), "")
            // Remove prompt instructions that might leak
            .replace(Regex("Answer the following question.*?(?=\\w)", RegexOption.DOT_MATCHES_ALL), "")
            .replace(Regex("based on the information above.*?(?=\\w)", RegexOption.DOT_MATCHES_ALL), "")
            .replace(Regex("Do not mention or reference.*?(?=\\w)", RegexOption.DOT_MATCHES_ALL), "")
            .replace(Regex("Keep your response concise.*?(?=\\w)", RegexOption.DOT_MATCHES_ALL), "")
            .replace(Regex("Based on these documents,.*?(?=\\w)", RegexOption.DOT_MATCHES_ALL), "")
            .replace(Regex("Respond with \"AI:\".*?(?=\\w)", RegexOption.DOT_MATCHES_ALL), "")
            .replace(Regex("You are an AI assistant.*?(?=\\w)", RegexOption.DOT_MATCHES_ALL), "")
            // Clean up any prompt artifacts
            .replace(Regex("\\n\\n---.*?---\\n\\n", RegexOption.DOT_MATCHES_ALL), "")
            .trim()
            
        return if (cleanedResponse.isBlank()) {
            "" // Return empty string instead of null for empty responses after cleaning
        } else {
            cleanedResponse
        }
    }
} 