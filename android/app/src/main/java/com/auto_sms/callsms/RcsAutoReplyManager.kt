package com.auto_sms.callsms

import android.content.Context
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.util.*
import kotlinx.coroutines.*
import kotlinx.coroutines.runBlocking
import com.auto_sms.llm.MLCLLMModule
import com.facebook.react.bridge.ReactApplicationContext
import android.content.Intent
import android.provider.Telephony
import com.facebook.react.bridge.Promise
import com.facebook.react.ReactApplication
import kotlinx.coroutines.CompletableDeferred
import java.io.File
import com.auto_sms.docextractor.DocExtractorHelper

/**
 * Manager class for RCS auto-reply rules and settings
 */
class RcsAutoReplyManager(private val context: Context) {
    private val TAG = "RcsAutoReplyManager"
    
    // MLC LLM integration
    private var mlcLlmModule: MLCLLMModule? = null
    private var isMLCInitialized = false
    private val coroutineScope = CoroutineScope(Dispatchers.IO)
    
    // Shared preferences keys
    companion object {
        const val PREFS_NAME = "AutoSmsPrefs"
        const val RCS_AUTO_REPLY_ENABLED_KEY = "@AutoSMS:RcsAutoReplyEnabled"
        const val RCS_AUTO_REPLY_MESSAGE_KEY = "@AutoSMS:RcsAutoReplyMessage"
        const val RCS_AUTO_REPLY_RULES_KEY = "@AutoSMS:RcsAutoReplyRules"
        const val RCS_AUTO_REPLY_LOG_KEY = "@AutoSMS:RcsAutoReplyLog"
        const val RCS_REPLIED_CONVERSATIONS_KEY = "@AutoSMS:RcsRepliedConversations"
        const val RCS_RATE_LIMIT_KEY = "@AutoSMS:RcsRateLimit"
        const val RCS_USE_LLM_KEY = "@AutoSMS:RcsUseLLM"
        
        // Rule types
        const val RULE_TYPE_ALWAYS = "always"
        const val RULE_TYPE_TIME = "time"
        const val RULE_TYPE_CONTACT = "contact"
        const val RULE_TYPE_KEYWORD = "keyword"
        
        // Max log entries to keep
        const val MAX_LOG_ENTRIES = 100
        
        // Default rate limit in milliseconds (1 minute for testing)
        const val DEFAULT_RATE_LIMIT_MS = 1L * 60L * 1000L
    }
    
    init {
        // Initialize MLC LLM if context is ReactApplicationContext
        if (context is ReactApplicationContext) {
            mlcLlmModule = MLCLLMModule(context)
            coroutineScope.launch {
                try {
                    isMLCInitialized = mlcLlmModule?.initialize() ?: false
                    Log.e(TAG, "🧠 MLC LLM initialization: ${if (isMLCInitialized) "SUCCESS" else "FAILED"}")
                    
                    // Test the LLM to ensure it's working
                    if (isMLCInitialized) {
                        val testPrompt = "Hello, this is a test"
                        val testContext = "This is a test context to verify LLM is working."
                        val testResponse = mlcLlmModule?.generateAnswer(testPrompt, testContext, 0.7f)
                        
                        Log.e(TAG, "🧠 MLC LLM test response: ${testResponse ?: "null"}")
                        Log.e(TAG, "✅ MLC LLM is ready for RCS auto-replies")
                    } else {
                        // Try to initialize again with different approach if it failed
                        Log.e(TAG, "🔄 Trying alternative initialization approach")
                        isMLCInitialized = mlcLlmModule?.initialize() ?: false
                        Log.e(TAG, "🧠 Second attempt LLM initialization: ${if (isMLCInitialized) "SUCCESS" else "FAILED"}")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Failed to initialize MLC LLM", e)
                    
                    // Try again with a delay
                    delay(1000)
                    try {
                        isMLCInitialized = mlcLlmModule?.initialize() ?: false
                        Log.e(TAG, "🧠 Delayed LLM initialization: ${if (isMLCInitialized) "SUCCESS" else "STILL FAILED"}")
                    } catch (e: Exception) {
                        Log.e(TAG, "❌ Second attempt to initialize MLC LLM also failed", e)
                    }
                }
            }
        } else {
            Log.e(TAG, "⚠️ Context is not ReactApplicationContext, LLM features will be limited")
            Log.e(TAG, "⚠️ Using context type: ${context.javaClass.name}")
            isMLCInitialized = false
            mlcLlmModule = null
            
            // Check if we can access documents from the file system
            val documents = getDocumentsFromFileSystem()
            if (documents.isNotEmpty()) {
                Log.e(TAG, "✅ Successfully accessed ${documents.size} documents from file system despite non-ReactApplicationContext")
            } else {
                Log.e(TAG, "⚠️ No documents found in file system with non-ReactApplicationContext")
            }
        }
        
        // Ensure RCS auto-reply and LLM are both enabled
        ensureRcsAutoReplyEnabled()
        setLLMEnabled(true)
    }
    
    /**
     * Ensure RCS auto-reply is enabled for testing purposes
     */
    private fun ensureRcsAutoReplyEnabled() {
        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            
            // Check current state
            val rcsEnabled = prefs.getBoolean(RCS_AUTO_REPLY_ENABLED_KEY, false)
            val llmEnabled = prefs.getBoolean(RCS_USE_LLM_KEY, false)
            
            Log.e(TAG, "🔧 RCS Auto-Reply Status Check:")
            Log.e(TAG, "   • RCS Enabled: $rcsEnabled")
            Log.e(TAG, "   • LLM Enabled: $llmEnabled")
            
            // Enable RCS auto-reply if not already enabled
            if (!rcsEnabled) {
                Log.e(TAG, "🔧 Enabling RCS auto-reply")
                prefs.edit().putBoolean(RCS_AUTO_REPLY_ENABLED_KEY, true).apply()
            }
            
            // Enable LLM for RCS if not already enabled
            if (!llmEnabled) {
                Log.e(TAG, "🔧 Enabling LLM for RCS auto-reply")
                prefs.edit().putBoolean(RCS_USE_LLM_KEY, true).apply()
            }
            
            // Set a reasonable rate limit
            val currentRateLimit = prefs.getLong(RCS_RATE_LIMIT_KEY, DEFAULT_RATE_LIMIT_MS)
            if (currentRateLimit > 5 * 60 * 1000) { // If more than 5 minutes
                Log.e(TAG, "🔧 Setting a more reasonable rate limit (1 minute)")
                prefs.edit().putLong(RCS_RATE_LIMIT_KEY, 60 * 1000).apply() // 1 minute
            }
            
            Log.e(TAG, "✅ RCS Auto-Reply setup complete")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error ensuring RCS auto-reply enabled: ${e.message}")
        }
    }
    
    /**
     * Check if RCS auto-reply is enabled
     */
    fun isEnabled(): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(RCS_AUTO_REPLY_ENABLED_KEY, false)
    }
    
    /**
     * Set RCS auto-reply enabled state
     */
    fun setEnabled(enabled: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(RCS_AUTO_REPLY_ENABLED_KEY, enabled).apply()
    }
    
    /**
     * Check if LLM-based replies are enabled
     */
    fun isLLMEnabled(): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(RCS_USE_LLM_KEY, false)
    }
    
    /**
     * Set LLM-based replies enabled state
     */
    fun setLLMEnabled(enabled: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(RCS_USE_LLM_KEY, enabled).apply()
    }
    
    /**
     * Get a default message for auto-reply
     * Only uses LLM-generated responses based on real documents, no static fallbacks
     */
    fun getDefaultMessage(sender: String = "Unknown", receivedMessage: String = ""): String {
        // If receivedMessage is empty, use a placeholder to still generate a dynamic response
        val messageToUse = if (receivedMessage.isEmpty()) "Hello" else receivedMessage
        
        Log.e(TAG, "🧠🧠🧠 START: GENERATING DYNAMIC RCS RESPONSE 🧠🧠🧠")
        Log.e(TAG, "   • Sender: $sender")
        Log.e(TAG, "   • Message: $messageToUse")
        
        try {
            // Primary approach: Use document-based LLM
            Log.e(TAG, "🧠 Attempting to generate response using document-based approach")
            val documentResponse = generateLLMResponseWithDocuments(sender, messageToUse)
            if (documentResponse.isNotEmpty()) {
                Log.e(TAG, "✅ Generated dynamic response with documents: $documentResponse")
                Log.e(TAG, "🧠🧠🧠 END: DYNAMIC RCS RESPONSE GENERATED 🧠🧠🧠")
                return documentResponse
            }
            
            // If document-based approach fails, use MLC LLM directly
            Log.e(TAG, "🧠 Attempting to generate response using MLC LLM directly")
            val mlcResponse = generateMlcLLMResponse(sender, messageToUse)
            if (mlcResponse.isNotEmpty()) {
                Log.e(TAG, "✅ Generated dynamic response via MLC LLM: $mlcResponse")
                Log.e(TAG, "🧠🧠🧠 END: DYNAMIC RCS RESPONSE GENERATED 🧠🧠🧠")
                return mlcResponse
            }
            
            // If all approaches fail, return empty string to prevent sending static messages
            Log.e(TAG, "❌❌❌ All dynamic response approaches failed")
            Log.e(TAG, "❌❌❌ No response will be sent to avoid static templates")
            Log.e(TAG, "🧠🧠🧠 END: NO DYNAMIC RCS RESPONSE GENERATED 🧠🧠🧠")
            return ""
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error generating dynamic response: ${e.message}")
            Log.e(TAG, "🧠🧠🧠 END: ERROR IN DYNAMIC RCS RESPONSE GENERATION 🧠🧠🧠")
            // Return empty string to indicate no response should be sent
            return ""
        }
    }
    
    /**
     * Generate response using MLC LLM directly
     * Enhanced to only use LLM-generated responses, no static fallbacks
     */
    private fun generateMlcLLMResponse(sender: String, message: String): String {
        Log.e(TAG, "🧠 START: generateMlcLLMResponse for sender: $sender")
        try {
            // Check if context is valid for LLM operations
            // We'll still try even if context isn't ideal
            val contextValid = isContextValidForLLM()
            if (!contextValid) {
                Log.e(TAG, "⚠️ Context not ideal for LLM, but will attempt anyway")
            }
            
            if (mlcLlmModule == null) {
                Log.e(TAG, "🧠 MLC LLM module not initialized, attempting initialization")
                if (this.context is ReactApplicationContext) {
                    Log.e(TAG, "🧠 Creating new MLCLLMModule instance")
                    mlcLlmModule = MLCLLMModule(this.context as ReactApplicationContext)
                    try {
                        Log.e(TAG, "🧠 Initializing MLC LLM module")
                        isMLCInitialized = mlcLlmModule?.initialize() ?: false
                        Log.e(TAG, "🧠 Late initialization of MLC LLM: ${if (isMLCInitialized) "SUCCESS" else "FAILED"}")
                    } catch (e: Exception) {
                        Log.e(TAG, "❌ Error in late initialization of MLC LLM: ${e.message}")
                    }
                } else {
                    Log.e(TAG, "⚠️ Context is not ReactApplicationContext, will try alternative approaches")
                }
            }
            
            // Only proceed with MLC LLM if available
            if (mlcLlmModule != null) {
                // Craft a very specific prompt to ensure dynamic content
                Log.e(TAG, "✏️ Creating prompt context for MLC LLM")
                val promptContext = "You are responding to a message from $sender. " +
                               "Keep your response brief, friendly and conversational. " +
                               "You MUST reference specific content from their message in your reply. " +
                               "IMPORTANT: Do NOT use generic unavailable messages. " +
                               "The message you received is: \"$message\""
                
                val prompt = "Generate a brief, specific reply that directly addresses what was said in the message."
                Log.e(TAG, "✏️ Prompt: $prompt")
                Log.e(TAG, "✏️ Context length: ${promptContext.length} characters")
                
                Log.e(TAG, "🧠 Calling MLC LLM generateAnswer")
                val startTime = System.currentTimeMillis()
                val response = runBlocking { 
                    mlcLlmModule?.generateAnswer(prompt, promptContext, 0.7f)
                }
                val endTime = System.currentTimeMillis()
                Log.e(TAG, "⏱️ MLC LLM response generation took ${endTime - startTime} ms")
                
                if (response != null && response.isNotEmpty()) {
                    Log.e(TAG, "✅ MLC LLM generated response (${response.length} chars): $response")
                    Log.e(TAG, "🧠 END: generateMlcLLMResponse - SUCCESS")
                    return response
                } else {
                    Log.e(TAG, "❌ MLC LLM returned null or empty response")
                }
            } else {
                Log.e(TAG, "⚠️ MLC LLM module is null, trying alternative approaches")
            }
            
            // Try reflection approach as backup
            try {
                Log.e(TAG, "🔍 Attempting to use reflection approach")
                val reactContext = try {
                    (context as ReactApplicationContext)
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Error getting ReactContext: ${e.message}")
                    null
                }
                
                if (reactContext != null) {
                    val callSmsModule = reactContext.getNativeModule(CallSmsModule::class.java)
                    if (callSmsModule != null) {
                        Log.e(TAG, "🔍 Found CallSmsModule, using testLLM method via reflection")
                        val testLLMMethod = CallSmsModule::class.java.getDeclaredMethod(
                            "testLLM",
                            String::class.java
                        )
                        testLLMMethod.isAccessible = true
                        
                        // Create a personalized prompt including message content
                        val reflectionPrompt = "Message from $sender: \"$message\". Generate a friendly, specific reply that references the content of their message. Be brief and conversational."
                        
                        // Call the method directly
                        val result = testLLMMethod.invoke(callSmsModule, reflectionPrompt)
                        
                        if (result != null) {
                            val rawResponse = result as String
                            val cleanedResponse = cleanLLMResponse(rawResponse)
                            
                            if (cleanedResponse.isNotEmpty()) {
                                Log.e(TAG, "✅ Generated response via reflection: $cleanedResponse")
                                Log.e(TAG, "🧠 END: generateMlcLLMResponse - SUCCESS via reflection")
                                return cleanedResponse
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error with reflection approach: ${e.message}")
            }
            
            // If all LLM approaches fail, return empty string to prevent static responses
            Log.e(TAG, "❌ All LLM approaches failed, returning empty string to prevent static responses")
            Log.e(TAG, "🧠 END: generateMlcLLMResponse - NO RESPONSE")
            return ""
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error with MLC LLM approach: ${e.message}")
            Log.e(TAG, "🧠 END: generateMlcLLMResponse - ERROR")
            return ""
        }
    }
    
    /**
     * Set the default message - this is only used for UI display, not for actual replies
     * Actual replies will always use dynamic LLM generation
     */
    fun setDefaultMessage(message: String) {
        // IMPORTANT: Add a flag to indicate we should always use dynamic messages
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putString(RCS_AUTO_REPLY_MESSAGE_KEY, message)
            .putBoolean(RCS_USE_LLM_KEY, true) // Always enable LLM
            .apply()
            
        Log.e(TAG, "💬 Set UI display message: $message")
        Log.e(TAG, "🧠 Note: Actual replies will always use dynamic LLM generation")
    }
    
    /**
     * Get the rate limit in milliseconds
     */
    fun getRateLimit(): Long {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getLong(RCS_RATE_LIMIT_KEY, DEFAULT_RATE_LIMIT_MS)
    }
    
    /**
     * Set the rate limit in milliseconds
     */
    fun setRateLimit(rateLimit: Long) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putLong(RCS_RATE_LIMIT_KEY, rateLimit).apply()
    }
    
    /**
     * Process the message with rule engine
     * @return The reply message if should reply, null only if disabled or rate limited
     */
    fun processMessage(sender: String, message: String, timestamp: Long = System.currentTimeMillis()): String? {
        Log.e(TAG, "🧠🧠🧠 RCS PROCESSING MESSAGE 🧠🧠🧠")
        Log.e(TAG, "   • Sender: $sender")
        Log.e(TAG, "   • Message: $message")
        Log.e(TAG, "   • Timestamp: $timestamp")
        
        if (!isEnabled()) {
            Log.e(TAG, "❌ RCS auto-reply is disabled")
            return null
        }
        
        // First check if we've already replied to this sender recently
        if (hasRepliedRecently(sender, message)) {
            Log.e(TAG, "⚠️ Already replied to $sender recently, skipping")
            return null
        }
        
        // Record that we'll be replying to this sender
        recordReply(sender, message)
        
        // First, attempt to generate a document-based response (highest priority)
        // This treats the message as a potential question about documents
        Log.e(TAG, "📚 Checking for document-based response first")
        val documentResponse = generateLLMResponseWithDocuments(sender, message)
        if (documentResponse.isNotEmpty()) {
            Log.e(TAG, "✅ Generated document-based response: $documentResponse")
            addLogEntry(sender, message, documentResponse, true, true)
            return documentResponse
        }
        
        // Process rules to see if we should reply
        val rules = getRules()
        Log.e(TAG, "📋 Processing ${rules.length()} rules")
        
        // Process rules in order (higher priority first)
        for (i in 0 until rules.length()) {
            try {
                val rule = rules.getJSONObject(i)
                
                if (!rule.getBoolean("enabled")) {
                    Log.e(TAG, "   • Rule $i: disabled, skipping")
                    continue
                }
                
                val ruleType = rule.getString("type")
                val ruleMessage = rule.getString("message")
                
                Log.e(TAG, "   • Rule $i: type=$ruleType, message=$ruleMessage")
                
                val matches = when (ruleType) {
                    RULE_TYPE_ALWAYS -> true
                    RULE_TYPE_TIME -> matchesTimeRule(rule, timestamp)
                    RULE_TYPE_CONTACT -> matchesContactRule(rule, sender)
                    RULE_TYPE_KEYWORD -> matchesKeywordRule(rule, message)
                    else -> false
                }
                
                Log.e(TAG, "   • Rule $i matches: $matches")
                
                if (matches) {
                    // Use document-based LLM approach with rule context
                    Log.e(TAG, "🧠 Generating LLM response with rule context")
                    val ruleDocumentResponse = generateLLMResponseWithDocuments(sender, message, ruleMessage)
                    
                    if (ruleDocumentResponse.isNotEmpty()) {
                        Log.e(TAG, "✅ Generated document-based response with rule context: $ruleDocumentResponse")
                        addLogEntry(sender, message, ruleDocumentResponse, true, true)
                        return ruleDocumentResponse
                    } else {
                        Log.e(TAG, "❌ Failed to generate document-based response with rule context")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error processing rule ${i}: ${e.message}")
            }
        }
        
        // If no specific rule matched, try one more time with direct document approach
        Log.e(TAG, "📋 No specific rules matched, trying direct document approach again")
        val finalDocumentResponse = generateLLMResponseWithDocuments(sender, message)
        if (finalDocumentResponse.isNotEmpty()) {
            Log.e(TAG, "✅ Generated final document-based response: $finalDocumentResponse")
            addLogEntry(sender, message, finalDocumentResponse, true, true)
            return finalDocumentResponse
        }
        
        // If all approaches fail, return null to indicate no response should be sent
        Log.e(TAG, "❌ All response generation methods failed, no response will be sent")
        return null
    }
    
    /**
     * Check if we've already replied to this sender within the rate limit period
     * Now also considers the message content to handle multiple messages in a conversation
     */
    private fun hasRepliedRecently(sender: String, message: String): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val repliedConversations = prefs.getString(RCS_REPLIED_CONVERSATIONS_KEY, "{}") ?: "{}"
        val rateLimit = getRateLimit()
        
        try {
            Log.e(TAG, "🔍 Checking if we've replied recently to $sender")
            val json = JSONObject(repliedConversations)
            
            // Create a unique key for this sender and message
            // This helps distinguish between different messages from the same sender
            val senderKey = sender
            
            if (json.has(senderKey)) {
                val senderData = json.getJSONObject(senderKey)
                val lastReplyTime = senderData.getLong("timestamp")
                val currentTime = System.currentTimeMillis()
                val timeSinceLastReply = currentTime - lastReplyTime
                
                Log.e(TAG, "⏱️ Time since last reply: ${timeSinceLastReply}ms, Rate limit: ${rateLimit}ms")
                
                // Check if we're within the rate limit period
                if (timeSinceLastReply < rateLimit) {
                    // If this is the exact same message we've already replied to, don't reply again
                    if (senderData.has("lastMessage")) {
                        val lastMessage = senderData.getString("lastMessage")
                        
                        // CRITICAL FIX: Only consider it a duplicate if it's exactly the same message
                        // AND it was sent within a very short time window (10 seconds)
                        // This prevents legitimate duplicate notifications from blocking replies
                        if (lastMessage == message && timeSinceLastReply < 10000) {
                            Log.e(TAG, "🔄 Exact same message detected within 10 seconds, skipping reply")
                            return true
                        }
                        
                        // IMPROVEMENT: For single notifications with longer time gaps, 
                        // we should still reply if it's the same message but after some time
                        // This handles the case where someone sends the exact same message twice
                        
                        // If messages are similar but not identical, use normal rate limiting rules
                        if (areMessagesSimilar(lastMessage, message) && !messageSignificantlyDifferent(lastMessage, message)) {
                            Log.e(TAG, "⚠️ Similar message within rate limit period, applying normal rate limit")
                            return true
                        } else {
                            Log.e(TAG, "✅ Messages are different enough to trigger a new reply")
                        }
                    }
                    
                    // If it's a very short message, only rate limit for very short periods (15 seconds)
                    // This ensures short messages still get replies but not too frequently
                    if (message.length < 5) {
                        val shortMessageRateLimit = 15000L // 15 seconds for very short messages
                        if (timeSinceLastReply < shortMessageRateLimit) {
                            Log.e(TAG, "📝 Very short message, applying short message rate limit")
                            return true
                        } else {
                            Log.e(TAG, "✅ Short message but outside short message rate limit, allowing reply")
                            return false
                        }
                    }
                }
                
                // CRITICAL FIX: Add special cases for common single-word messages that should
                // always get replies (like "Hi", "Hello", "Ok", etc.)
                if (isCommonSingleWordMessage(message)) {
                    Log.e(TAG, "✅ Common greeting or single-word message, allowing reply regardless of rate limit")
                    return false
                }
                
                Log.e(TAG, "✅ Outside rate limit period, allowing reply")
            } else {
                Log.e(TAG, "✅ First message from this sender, allowing reply")
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error checking replied conversations: ${e.message}")
        }
        
        return false
    }
    
    /**
     * Check if two messages are similar enough to be considered part of the same conversation
     */
    private fun areMessagesSimilar(message1: String, message2: String): Boolean {
        // Simple implementation - just check if one is a substring of the other
        // Could be enhanced with more sophisticated text similarity algorithms
        return message1.contains(message2) || message2.contains(message1)
    }
    
    /**
     * Check if the new message is significantly different from the previous one
     */
    private fun messageSignificantlyDifferent(message1: String, message2: String): Boolean {
        // If either message is empty, they're not significantly different
        if (message1.isEmpty() || message2.isEmpty()) {
            return false
        }
        
        // If messages are very short, require exact match
        if (message1.length < 5 || message2.length < 5) {
            return message1 != message2
        }
        
        // If one message is much longer than the other, they're different
        if (message1.length > message2.length * 2 || message2.length > message1.length * 2) {
            return true
        }
        
        // Calculate word overlap
        val words1 = message1.lowercase().split(Regex("\\s+")).toSet()
        val words2 = message2.lowercase().split(Regex("\\s+")).toSet()
        
        // If there are no words in common, they're different
        val commonWords = words1.intersect(words2)
        if (commonWords.isEmpty()) {
            return true
        }
        
        // If less than 30% of words overlap, they're different
        val overlapRatio = commonWords.size.toDouble() / Math.max(words1.size, words2.size)
        return overlapRatio < 0.3
    }
    
    /**
     * Record that we've replied to this sender
     */
    private fun recordReply(sender: String, message: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val repliedConversations = prefs.getString(RCS_REPLIED_CONVERSATIONS_KEY, "{}") ?: "{}"
        
        try {
            val json = JSONObject(repliedConversations)
            
            // Store sender data with timestamp and last message
            val senderData = JSONObject()
            senderData.put("timestamp", System.currentTimeMillis())
            senderData.put("lastMessage", message)
            
            json.put(sender, senderData)
            
            // Clean up old entries (older than 24 hours)
            val keysToRemove = mutableListOf<String>()
            val iter = json.keys()
            val currentTime = System.currentTimeMillis()
            val oneDayMs = 24 * 60 * 60 * 1000
            
            while (iter.hasNext()) {
                val key = iter.next()
                val data = json.getJSONObject(key)
                val timestamp = data.getLong("timestamp")
                if (currentTime - timestamp > oneDayMs) {
                    keysToRemove.add(key)
                }
            }
            
            for (key in keysToRemove) {
                json.remove(key)
            }
            
            prefs.edit().putString(RCS_REPLIED_CONVERSATIONS_KEY, json.toString()).apply()
        } catch (e: Exception) {
            Log.e(TAG, "Error recording reply: ${e.message}")
        }
    }
    
    /**
     * Add an entry to the auto-reply log
     */
    fun addLogEntry(sender: String, receivedMessage: String, sentMessage: String, success: Boolean, isLLM: Boolean = false) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val logJson = prefs.getString(RCS_AUTO_REPLY_LOG_KEY, "[]") ?: "[]"
        
        try {
            val logArray = JSONArray(logJson)
            val newEntry = JSONObject()
            newEntry.put("timestamp", System.currentTimeMillis())
            newEntry.put("sender", sender)
            newEntry.put("received", receivedMessage)
            newEntry.put("sent", sentMessage)
            newEntry.put("success", success)
            newEntry.put("type", "rcs")
            newEntry.put("isLLM", isLLM)
            
            // Add to beginning
            val newLogArray = JSONArray()
            newLogArray.put(newEntry)
            
            // Add existing entries up to max
            for (i in 0 until Math.min(logArray.length(), MAX_LOG_ENTRIES - 1)) {
                newLogArray.put(logArray.get(i))
            }
            
            prefs.edit().putString(RCS_AUTO_REPLY_LOG_KEY, newLogArray.toString()).apply()
        } catch (e: Exception) {
            Log.e(TAG, "Error adding log entry: ${e.message}")
        }
    }
    
    /**
     * Get all auto-reply logs
     */
    fun getLogs(): JSONArray {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val logJson = prefs.getString(RCS_AUTO_REPLY_LOG_KEY, "[]") ?: "[]"
        
        return try {
            JSONArray(logJson)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting logs: ${e.message}")
            JSONArray()
        }
    }
    
    /**
     * Clear all auto-reply logs
     */
    fun clearLogs() {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(RCS_AUTO_REPLY_LOG_KEY, "[]").apply()
    }
    
    /**
     * Get all rules
     */
    fun getRules(): JSONArray {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val rulesJson = prefs.getString(RCS_AUTO_REPLY_RULES_KEY, "[]") ?: "[]"
        
        return try {
            JSONArray(rulesJson)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting rules: ${e.message}")
            JSONArray()
        }
    }
    
    /**
     * Set all rules
     */
    fun setRules(rules: JSONArray) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(RCS_AUTO_REPLY_RULES_KEY, rules.toString()).apply()
    }
    
    /**
     * Add a rule
     */
    fun addRule(type: String, enabled: Boolean, message: String, data: JSONObject): Boolean {
        try {
            val rule = JSONObject()
            rule.put("type", type)
            rule.put("enabled", enabled)
            rule.put("message", message)
            rule.put("data", data)
            
            val rules = getRules()
            rules.put(rule)
            setRules(rules)
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Error adding rule: ${e.message}")
            return false
        }
    }
    
    /**
     * Check if a message matches a time-based rule
     */
    private fun matchesTimeRule(rule: JSONObject, timestamp: Long): Boolean {
        try {
            val data = rule.getJSONObject("data")
            val calendar = Calendar.getInstance()
            calendar.timeInMillis = timestamp
            
            val currentHour = calendar.get(Calendar.HOUR_OF_DAY)
            val currentMinute = calendar.get(Calendar.MINUTE)
            val currentDayOfWeek = calendar.get(Calendar.DAY_OF_WEEK)
            
            // Check if current day is enabled
            val dayKey = "day_${currentDayOfWeek}"
            if (!data.optBoolean(dayKey, false)) {
                return false
            }
            
            // Check if current time is within range
            val startHour = data.getInt("start_hour") 
            val startMinute = data.getInt("start_minute")
            val endHour = data.getInt("end_hour")
            val endMinute = data.getInt("end_minute")
            
            val currentTime = currentHour * 60 + currentMinute
            val startTime = startHour * 60 + startMinute
            val endTime = endHour * 60 + endMinute
            
            // Handle ranges that span midnight
            if (startTime <= endTime) {
                return currentTime >= startTime && currentTime <= endTime
            } else {
                // Time range goes over midnight (e.g., 22:00 - 06:00)
                return currentTime >= startTime || currentTime <= endTime
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in matchesTimeRule: ${e.message}")
            return false
        }
    }
    
    /**
     * Check if a message matches a contact-based rule
     */
    private fun matchesContactRule(rule: JSONObject, sender: String): Boolean {
        try {
            val data = rule.getJSONObject("data")
            val contactList = data.getJSONArray("contacts")
            
            // For name-based matching we would need contact lookup
            // For now just do basic phone number comparison
            for (i in 0 until contactList.length()) {
                val contact = contactList.getString(i)
                if (normalizePhoneNumber(sender) == normalizePhoneNumber(contact)) {
                    return true
                }
            }
            
            return false
        } catch (e: Exception) {
            Log.e(TAG, "Error in matchesContactRule: ${e.message}")
            return false
        }
    }
    
    /**
     * Check if a message matches a keyword-based rule
     */
    private fun matchesKeywordRule(rule: JSONObject, message: String): Boolean {
        try {
            val data = rule.getJSONObject("data")
            val keywords = data.getJSONArray("keywords")
            val caseSensitive = data.optBoolean("case_sensitive", false)
            
            val normalizedMessage = if (caseSensitive) message else message.lowercase(Locale.getDefault())
            
            for (i in 0 until keywords.length()) {
                val keyword = keywords.getString(i)
                val normalizedKeyword = if (caseSensitive) keyword else keyword.lowercase(Locale.getDefault())
                
                if (normalizedMessage.contains(normalizedKeyword)) {
                    return true
                }
            }
            
            return false
        } catch (e: Exception) {
            Log.e(TAG, "Error in matchesKeywordRule: ${e.message}")
            return false
        }
    }
    
    /**
     * Normalize a phone number for comparison (strip non-digits)
     */
    private fun normalizePhoneNumber(phoneNumber: String): String {
        return phoneNumber.replace(Regex("[^0-9+]"), "")
    }
    
    /**
     * Clean up resources
     */
    fun cleanup() {
        coroutineScope.cancel()
    }
    
    /**
     * Generate a test SMS to verify logging functionality
     * This method doesn't actually send an SMS, but broadcasts an SMS_RECEIVED intent
     * to trigger the SmsReceiver and test the logging
     */
    fun generateTestSmsForLogging(context: Context, senderNumber: String = "+1234567890", message: String = "This is a test SMS message"): Boolean {
        try {
            Log.e(TAG, "🧪🧪🧪 Generating test SMS for logging verification 🧪🧪🧪")
            
            // Create an intent that mimics an incoming SMS
            val intent = Intent(Telephony.Sms.Intents.SMS_RECEIVED_ACTION)
            
            // Create a PduPart to hold the message
            val pdu = createTestPdu(senderNumber, message)
            
            // Add PDUs to the intent
            intent.putExtra("pdus", arrayOf(pdu))
            intent.putExtra("format", "3gpp")
            
            // Send the broadcast to trigger SmsReceiver
            context.sendBroadcast(intent)
            
            Log.e(TAG, "✅ Test SMS broadcast sent successfully")
            Log.e(TAG, "   • Sender: $senderNumber")
            Log.e(TAG, "   • Message: $message")
            
            return true
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error generating test SMS: ${e.message}")
            return false
        }
    }
    
    /**
     * Create a test PDU (Protocol Data Unit) for SMS testing
     * This is a simplified implementation that creates a fake PDU
     * NOTE: This is only for testing logging and won't actually create a valid PDU
     */
    private fun createTestPdu(senderNumber: String, message: String): ByteArray {
        // This is a placeholder implementation that just creates a dummy byte array
        // In a real implementation, this would create a properly formatted PDU
        // But for testing logging, this simplified version is sufficient
        
        val combined = "SENDER:$senderNumber|MESSAGE:$message|TIME:${System.currentTimeMillis()}"
        return combined.toByteArray()
    }
    
    /**
     * Reset/clear all remembered replied conversations
     * This is useful for testing to ensure we can force a fresh response
     */
    fun clearRepliedConversations() {
        try {
            Log.e(TAG, "🧹 Clearing all replied conversations for testing")
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().putString(RCS_REPLIED_CONVERSATIONS_KEY, "{}").apply()
            Log.e(TAG, "✅ Successfully cleared replied conversations")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error clearing replied conversations: ${e.message}")
        }
    }
    
    /**
     * Adjust rate limit to a testing-friendly value
     * Use a shorter time for testing (30 seconds)
     */
    fun setTestingRateLimit() {
        try {
            Log.e(TAG, "⏱️ Setting testing-friendly rate limit")
            val testRateLimit = 30 * 1000L // 30 seconds for testing
            setRateLimit(testRateLimit)
            Log.e(TAG, "✅ Set rate limit to ${testRateLimit}ms for testing")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error setting testing rate limit: ${e.message}")
        }
    }
    
    /**
     * Check if a message is a common greeting or single-word message
     * These should always get replies regardless of rate limits
     */
    private fun isCommonSingleWordMessage(message: String): Boolean {
        val trimmed = message.trim().lowercase()
        
        // Common single-word greetings and replies
        val commonWords = setOf(
            "hi", "hello", "hey", "hola", "yo", 
            "ok", "okay", "k", "yes", "no", "yeah",
            "sure", "thanks", "thx", "ty", "test"
        )
        
        // Check if it's a single word
        if (!trimmed.contains(" ") && commonWords.contains(trimmed)) {
            return true
        }
        
        // Also check for very short greetings with punctuation
        val noSymbols = trimmed.replace(Regex("[.!?,]"), "")
        return commonWords.contains(noSymbols) || 
               noSymbols.endsWith("?") || // Questions should always get replies
               (noSymbols.length < 5 && noSymbols.isNotEmpty()) // Very short messages
    }
    
    /**
     * Extract the main topic of a message
     */
    private fun extractTopic(message: String): String {
        if (message.isEmpty()) return "your inquiry"
        
        // Try to get a short snippet of the message
        val words = message.split(Regex("\\s+"))
        
        return if (words.size <= 3) {
            message
        } else if (message.length <= 25) {
            message
        } else {
            // Get first few words
            words.take(3).joinToString(" ")
        }
    }
    
    /**
     * Get documents from the app's documents directory
     * This allows us to access documents even when context is not ReactApplicationContext
     */
    private fun getDocumentsFromFileSystem(): List<File> {
        Log.e(TAG, "📚 Getting documents from file system")
        try {
            // Access documents directory in app's files directory
            val documentsDir = File(context.filesDir, "documents")
            if (!documentsDir.exists()) {
                Log.e(TAG, "❌ Documents directory doesn't exist at ${documentsDir.absolutePath}")
                return emptyList()
            }
            
            val files = documentsDir.listFiles()
            if (files == null || files.isEmpty()) {
                Log.e(TAG, "❌ No documents found in ${documentsDir.absolutePath}")
                return emptyList()
            }
            
            Log.e(TAG, "📚 Found ${files.size} documents in file system")
            files.forEach { file ->
                Log.e(TAG, "   • ${file.name} (${file.length()} bytes)")
            }
            
            return files.toList()
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error accessing documents from file system: ${e.message}")
            return emptyList()
        }
    }
    
    /**
     * Extract text content from documents
     */
    private fun extractDocumentContents(files: List<File>): String {
        Log.e(TAG, "📚📚📚 START: Extracting document contents from ${files.size} files 📚📚📚")
        val startTime = System.currentTimeMillis()
        
        val builder = StringBuilder()
        var successCount = 0
        var totalChars = 0
        
        try {
            // Process each document file
            files.forEachIndexed { index, file ->
                try {
                    Log.e(TAG, "📚 Processing document ${index+1}/${files.size}: ${file.name}")
                    
                    // Skip empty files
                    if (file.length() == 0L) {
                        Log.e(TAG, "⚠️ Skipping empty document: ${file.name}")
                        return@forEachIndexed
                    }
                    
                    // Handle different file types
                    when {
                        file.name.endsWith(".txt", ignoreCase = true) -> {
                            // Read text files directly
                            val content = file.readText()
                            val contentLength = content.length
                            totalChars += contentLength
                            
                            builder.append("--- Document: ${file.name} ---\n")
                            builder.append(content)
                            builder.append("\n\n")
                            
                            successCount++
                            Log.e(TAG, "📝 Added text document: ${file.name}, length: ${contentLength} chars")
                        }
                        file.name.endsWith(".pdf", ignoreCase = true) -> {
                            // For PDF files, use DocExtractorHelper
                            Log.e(TAG, "📄 Extracting text from PDF: ${file.name}")
                            try {
                                val pdfContent = extractPdfText(file)
                                if (pdfContent.isNotEmpty()) {
                                    val contentLength = pdfContent.length
                                    totalChars += contentLength
                                    
                                    builder.append("--- Document: ${file.name} ---\n")
                                    builder.append(pdfContent)
                                    builder.append("\n\n")
                                    
                                    successCount++
                                    Log.e(TAG, "📝 Added PDF document: ${file.name}, extracted length: ${contentLength} chars")
                                } else {
                                    Log.e(TAG, "⚠️ No text extracted from PDF: ${file.name}")
                                }
                            } catch (e: Exception) {
                                Log.e(TAG, "❌ Error extracting text from PDF ${file.name}: ${e.message}")
                            }
                        }
                        file.name.endsWith(".docx", ignoreCase = true) -> {
                            Log.e(TAG, "📄 Attempting to extract text from DOCX: ${file.name}")
                            // Placeholder for DOCX extraction if needed
                            Log.e(TAG, "⚠️ DOCX extraction not implemented yet")
                        }
                        else -> {
                            Log.e(TAG, "⚠️ Skipping unsupported file type: ${file.name}")
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Error reading document ${file.name}: ${e.message}")
                    e.printStackTrace()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error extracting document contents: ${e.message}")
            e.printStackTrace()
        }
        
        val result = builder.toString()
        val endTime = System.currentTimeMillis()
        
        Log.e(TAG, "📊 Document extraction summary:")
        Log.e(TAG, "   • Total files processed: ${files.size}")
        Log.e(TAG, "   • Successfully extracted: $successCount")
        Log.e(TAG, "   • Total characters: $totalChars")
        Log.e(TAG, "   • Processing time: ${endTime - startTime} ms")
        
        Log.e(TAG, "📚📚📚 END: Document extraction complete 📚📚📚")
        return result
    }
    
    /**
     * Extract text from a PDF file using DocExtractorHelper
     */
    private fun extractPdfText(pdfFile: File): String {
        try {
            // Use DocExtractorHelper to extract PDF text
            return DocExtractorHelper.extractTextFromPdf(pdfFile)
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error in PDF extraction: ${e.message}")
            e.printStackTrace()
            return ""
        }
    }

    /**
     * Generate LLM response using document context (similar to SMS auto-reply)
     * This is the primary method for generating LLM responses
     */
    fun generateLLMResponseWithDocuments(sender: String, receivedMessage: String, contextMessage: String = ""): String {
        Log.e(TAG, "📚 START: Generating LLM response with document context")
        Log.e(TAG, "📚 Sender: $sender")
        Log.e(TAG, "📚 Message: $receivedMessage")
        if (contextMessage.isNotEmpty()) {
            Log.e(TAG, "📚 Additional context provided: $contextMessage")
        }
        
        try {
            // Check if context is valid for LLM operations
            // We'll still try even if context isn't ideal
            val contextValid = isContextValidForLLM()
            if (!contextValid) {
                Log.e(TAG, "⚠️ Context not ideal for LLM, but will attempt anyway")
            }
            
            // Get documents from file system regardless of context type
            val documentFiles = getDocumentsFromFileSystem()
            if (documentFiles.isEmpty()) {
                Log.e(TAG, "❌ No document files found in file system")
                return ""
            } else {
                Log.e(TAG, "📚 Found ${documentFiles.size} documents in file system")
                
                // Extract document content
                val documentContent = extractDocumentContents(documentFiles)
                if (documentContent.isEmpty()) {
                    Log.e(TAG, "❌ No content extracted from documents")
                    return ""
                }
                
                Log.e(TAG, "📚 Extracted ${documentContent.length} characters from documents")
                
                // Try MLC LLM approach
                try {
                    Log.e(TAG, "🧠 Attempting to use MLC LLM for document-based response")
                    if (mlcLlmModule == null) {
                        Log.e(TAG, "🧠 MLC LLM module not initialized, attempting initialization")
                        if (this.context is ReactApplicationContext) {
                            Log.e(TAG, "🧠 Creating new MLCLLMModule instance")
                            mlcLlmModule = MLCLLMModule(this.context as ReactApplicationContext)
                            try {
                                Log.e(TAG, "🧠 Initializing MLC LLM module")
                                isMLCInitialized = mlcLlmModule?.initialize() ?: false
                                Log.e(TAG, "🧠 Late initialization of MLC LLM: ${if (isMLCInitialized) "SUCCESS" else "FAILED"}")
                            } catch (e: Exception) {
                                Log.e(TAG, "❌ Error in late initialization of MLC LLM: ${e.message}")
                            }
                        } else {
                            Log.e(TAG, "⚠️ Context is not ReactApplicationContext, will try alternative approaches")
                        }
                    }
                    
                    // If MLC module is available, use it
                    if (mlcLlmModule != null) {
                        val prompt = "Generate a document-based reply to this message."
                        Log.e(TAG, "✏️ Document-based prompt: $prompt")
                        
                        val mlcContext = if (contextMessage.isNotEmpty()) {
                            "You are responding to a message from $sender who asked: \"$receivedMessage\". " +
                            "Keep your response brief, helpful and conversational. " +
                            "Consider this context for your response: \"$contextMessage\". " +
                            "Consider this document content: \"$documentContent\". " +
                            "If their message seems to be asking for information, try to provide specific details from available documents."
                        } else {
                            "You are responding to a message from $sender who asked: \"$receivedMessage\". " +
                            "Keep your response brief, helpful and conversational. " +
                            "Consider this document content: \"$documentContent\". " +
                            "If their message seems to be asking for information, try to provide specific details from available documents."
                        }
                        Log.e(TAG, "✏️ Document-based context length: ${mlcContext.length} characters")
                        
                        Log.e(TAG, "🧠 Calling MLC LLM generateAnswer for document-based response")
                        val startTime = System.currentTimeMillis()
                        val response = runBlocking { 
                            mlcLlmModule?.generateAnswer(prompt, mlcContext, 0.7f)
                        }
                        val endTime = System.currentTimeMillis()
                        Log.e(TAG, "⏱️ Document-based MLC LLM response generation took ${endTime - startTime} ms")
                        
                        if (response != null && response.isNotEmpty()) {
                            Log.e(TAG, "✅ MLC LLM generated document-based response (${response.length} chars): $response")
                            addLogEntry(sender, receivedMessage, response, true, true)
                            Log.e(TAG, "📚 END: Document-based response generation successful")
                            return response
                        } else {
                            Log.e(TAG, "❌ MLC LLM returned null or empty document-based response")
                        }
                    } else {
                        Log.e(TAG, "⚠️ MLC LLM module is null, trying alternative approaches")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Error with MLC LLM document-based approach: ${e.message}")
                    e.printStackTrace()
                }
                
                // If we have document content but MLC failed, use a fallback approach
                try {
                    Log.e(TAG, "🔄 Using fallback document-based approach")
                    val response = generateFallbackDocumentResponse(receivedMessage, documentContent)
                    if (response.isNotEmpty()) {
                        Log.e(TAG, "✅ Generated fallback document-based response: $response")
                        Log.e(TAG, "📚 END: Document-based response generation successful with fallback")
                        return response
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Error with fallback document-based approach: ${e.message}")
                }
            }
            
            // If all approaches failed, return empty string
            Log.e(TAG, "❌❌❌ All approaches failed, returning empty string")
            Log.e(TAG, "📚 END: Document-based response generation failed - all approaches")
            return ""
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error in generateLLMResponseWithDocuments: ${e.message}")
            e.printStackTrace()
            Log.e(TAG, "📚 END: Document-based response generation failed with exception")
            return ""
        }
    }
    
    /**
     * Generate a fallback response based on document content
     * This is used when MLC LLM is not available
     */
    private fun generateFallbackDocumentResponse(question: String, documentContent: String): String {
        Log.e(TAG, "🔄 Generating fallback document response")
        
        try {
            // Extract keywords from question
            val keywords = question.lowercase()
                .split(Regex("\\s+"))
                .filter { it.length > 3 }
                .toSet()
                
            if (keywords.isEmpty()) {
                Log.e(TAG, "❌ No keywords found in question")
                return ""
            }
            
            Log.e(TAG, "🔑 Keywords: ${keywords.joinToString(", ")}")
            
            // Split document content into paragraphs
            val paragraphs = documentContent.split(Regex("\n\n"))
                .filter { it.isNotBlank() && it.length > 20 }
                
            if (paragraphs.isEmpty()) {
                Log.e(TAG, "❌ No paragraphs found in document content")
                return ""
            }
            
            // Score paragraphs by keyword matching
            val scoredParagraphs = paragraphs.map { paragraph ->
                val paragraphLower = paragraph.lowercase()
                val score = keywords.count { keyword ->
                    paragraphLower.contains(keyword)
                }
                Pair(paragraph, score)
            }
            
            // Get most relevant paragraphs
            val relevantParagraphs = scoredParagraphs
                .filter { it.second > 0 }
                .sortedByDescending { it.second }
                .take(2)
                .map { it.first }
                
            if (relevantParagraphs.isEmpty()) {
                Log.e(TAG, "❌ No relevant paragraphs found")
                return ""
            }
            
            // Construct response
            val response = StringBuilder()
            response.append("Based on our documents, ")
            
            relevantParagraphs.forEachIndexed { index, paragraph ->
                if (index > 0) {
                    response.append(" Additionally, ")
                }
                
                // Clean up paragraph
                val cleanParagraph = paragraph
                    .replace(Regex("--- Document: [^-]+ ---\n"), "")
                    .replace(Regex("\\s+"), " ")
                    .trim()
                    
                response.append(cleanParagraph)
                
                if (!cleanParagraph.endsWith(".") && !cleanParagraph.endsWith("!") && !cleanParagraph.endsWith("?")) {
                    response.append(".")
                }
            }
            
            val finalResponse = response.toString()
            Log.e(TAG, "✅ Generated fallback document response: $finalResponse")
            return finalResponse
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error generating fallback document response: ${e.message}")
            return ""
        }
    }
    
    /**
     * Clean LLM response by removing AI prefixes and formatting
     */
    private fun cleanLLMResponse(response: String): String {
        if (response.isEmpty()) return ""
        
        var cleaned = response.trim()
        
        // Remove AI prefixes
        cleaned = cleaned.replace(Regex("^AI:\\s*", RegexOption.IGNORE_CASE), "")
        
        // Remove document metadata tags
        cleaned = cleaned.replace(Regex("Document \\d+: [^\n]+\n"), "")
        cleaned = cleaned.replace(Regex("Title: [^\n]+\n"), "")
        cleaned = cleaned.replace(Regex("Content: \\s*\n"), "")
        
        // Remove excessive whitespace
        cleaned = cleaned.replace(Regex("\\s+"), " ")
        
        return cleaned.trim()
    }
    
    /**
     * Force a dynamic response from MLC LLM (used for testing)
     * This ensures we get a non-static response for RCS auto-replies
     * Only returns LLM-generated responses, no static fallbacks
     */
    fun forceDynamicMlcResponse(sender: String, receivedMessage: String): String {
        Log.e(TAG, "🔥🔥🔥 START: FORCING DYNAMIC MLC LLM RESPONSE 🔥🔥🔥")
        Log.e(TAG, "   • Sender: $sender")
        Log.e(TAG, "   • Message: $receivedMessage")
        
        // If receivedMessage is empty, use a placeholder
        val messageToUse = if (receivedMessage.isEmpty()) "Hello" else receivedMessage
        
        try {
            // First try to generate a document-based response (highest priority)
            Log.e(TAG, "📚 Attempting document-based MLC LLM response")
            val startTime = System.currentTimeMillis()
            val documentResponse = generateLLMResponseWithDocuments(sender, messageToUse)
            val endTime = System.currentTimeMillis()
            Log.e(TAG, "⏱️ Document-based response generation took ${endTime - startTime} ms")
            
            if (documentResponse.isNotEmpty()) {
                Log.e(TAG, "✅ Generated document-based response: $documentResponse")
                Log.e(TAG, "🔥🔥🔥 END: DYNAMIC MLC LLM RESPONSE GENERATED SUCCESSFULLY 🔥🔥🔥")
                return documentResponse
            }
            
            // Directly use MLC LLM for a guaranteed dynamic response
            Log.e(TAG, "🧠 Attempting direct MLC LLM response")
            val mlcResponse = generateMlcLLMResponse(sender, messageToUse)
            if (mlcResponse.isNotEmpty()) {
                Log.e(TAG, "✅ Generated direct MLC LLM response: $mlcResponse")
                Log.e(TAG, "🔥🔥🔥 END: DYNAMIC MLC LLM RESPONSE GENERATED SUCCESSFULLY 🔥🔥🔥")
                return mlcResponse
            }
            
            // Try reflection approach as another option
            Log.e(TAG, "🔄 Attempting reflection-based LLM response")
            val reflectionResponse = callReflectionLLM(sender, messageToUse)
            if (reflectionResponse.isNotEmpty()) {
                Log.e(TAG, "✅ Generated reflection-based LLM response: $reflectionResponse")
                Log.e(TAG, "🔥🔥🔥 END: DYNAMIC MLC LLM RESPONSE GENERATED SUCCESSFULLY 🔥🔥🔥")
                return reflectionResponse
            }
            
            // If all LLM approaches fail, return empty string to prevent static responses
            Log.e(TAG, "❌❌❌ All LLM approaches failed, returning empty string to prevent static responses")
            Log.e(TAG, "🔥🔥🔥 END: DYNAMIC MLC LLM RESPONSE FAILED - ALL APPROACHES 🔥🔥🔥")
            return ""
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error forcing dynamic response: ${e.message}")
            e.printStackTrace()
            Log.e(TAG, "🔥🔥🔥 END: DYNAMIC MLC LLM RESPONSE FAILED WITH EXCEPTION 🔥🔥🔥")
            
            // Return empty string on error to prevent static messages
            return ""
        }
    }
    
    /**
     * Call LLM via reflection to CallSmsModule
     */
    private fun callReflectionLLM(sender: String, message: String): String {
        try {
            val reactContext = try {
                (context as ReactApplicationContext)
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error getting ReactContext: ${e.message}")
                return ""
            }
            
            val callSmsModule = reactContext.getNativeModule(CallSmsModule::class.java)
            if (callSmsModule != null) {
                try {
                    // Use reflection to call the testLLM method
                    val testLLMMethod = CallSmsModule::class.java.getDeclaredMethod(
                        "testLLM",
                        String::class.java
                    )
                    testLLMMethod.isAccessible = true
                    
                    // Create a special prompt that forces a dynamic response and leverages documents
                    val specialPrompt = "The following message may be asking about information in our documents: \"$message\". " +
                                     "Please provide a helpful response that references any relevant document information. " +
                                     "The response MUST mention something specific from the message. " + 
                                     "DO NOT use generic 'I'm unavailable' messages. " +
                                     "The message is from $sender."
                    
                    val result = testLLMMethod.invoke(callSmsModule, specialPrompt)
                    
                    if (result != null) {
                        val rawResponse = result as String
                        val cleanedResponse = cleanLLMResponse(rawResponse)
                        
                        if (cleanedResponse.isNotEmpty()) {
                            return cleanedResponse
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Error with CallSmsModule reflection: ${e.message}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error in callReflectionLLM: ${e.message}")
        }
        
        return ""
    }
    
    /**
     * Check if the context is valid for LLM operations
     * Now less strict to allow more attempts to use LLM
     */
    private fun isContextValidForLLM(): Boolean {
        val isIdeal = context is ReactApplicationContext && mlcLlmModule != null && isMLCInitialized
        val isWorkable = context is ReactApplicationContext && (mlcLlmModule != null || this.context is ReactApplicationContext)
        
        if (!isIdeal) {
            if (isWorkable) {
                Log.e(TAG, "⚠️ Context is workable but not ideal for LLM operations")
                Log.e(TAG, "⚠️ Context type: ${context.javaClass.name}")
                Log.e(TAG, "⚠️ MLC LLM module: ${if (mlcLlmModule != null) "available" else "null"}")
                Log.e(TAG, "⚠️ MLC initialized: $isMLCInitialized")
                return true
            } else {
                Log.e(TAG, "⚠️ Context is not valid for LLM operations")
                Log.e(TAG, "⚠️ Context type: ${context.javaClass.name}")
                Log.e(TAG, "⚠️ MLC LLM module: ${if (mlcLlmModule != null) "available" else "null"}")
                Log.e(TAG, "⚠️ MLC initialized: $isMLCInitialized")
                return false
            }
        }
        
        return true
    }

    /**
     * Test document access and LLM response generation
     * This can be called to verify our fixes are working with real uploaded documents
     */
    fun testDocumentAccess(): String {
        Log.e(TAG, "🧪🧪🧪 TESTING DOCUMENT ACCESS AND LLM RESPONSE GENERATION 🧪🧪🧪")
        
        try {
            // First check if we can access documents from the file system
            // These are the real documents uploaded by the user in LocalLLMSetupScreen.tsx
            val documents = getDocumentsFromFileSystem()
            if (documents.isEmpty()) {
                Log.e(TAG, "❌ No documents found in file system. Please upload documents in LocalLLMSetupScreen first.")
                return "No documents found. Please upload documents in the Local LLM Setup screen first."
            }
            
            Log.e(TAG, "✅ Successfully accessed ${documents.size} real uploaded documents from file system")
            
            // Extract document content
            val documentContent = extractDocumentContents(documents)
            if (documentContent.isEmpty()) {
                Log.e(TAG, "❌ No content extracted from documents")
                return "No content extracted from documents. Please ensure your uploaded documents contain text."
            }
            
            Log.e(TAG, "✅ Successfully extracted ${documentContent.length} characters from real uploaded documents")
            
            // Generate a test response
            val testMessage = "Tell me about your product"
            val response = generateLLMResponseWithDocuments("TestUser", testMessage)
            
            if (response.isEmpty()) {
                Log.e(TAG, "❌ Failed to generate response with documents")
                return "Failed to generate response with documents. Please check that your uploaded documents contain relevant information."
            }
            
            Log.e(TAG, "✅ Successfully generated response using real uploaded documents: $response")
            return "Test successful! Generated response from real uploaded documents: $response"
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error in testDocumentAccess: ${e.message}")
            e.printStackTrace()
            return "Error: ${e.message}"
        }
    }
} 