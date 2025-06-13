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
                    Log.d(TAG, "MLC LLM initialization: ${if (isMLCInitialized) "SUCCESS" else "FAILED"}")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to initialize MLC LLM", e)
                }
            }
        }
        
        // Ensure RCS auto-reply is enabled for testing
        ensureRcsAutoReplyEnabled()
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
                Log.e(TAG, "🔧 Enabling RCS auto-reply for testing")
                prefs.edit().putBoolean(RCS_AUTO_REPLY_ENABLED_KEY, true).apply()
            }
            
            // Enable LLM for RCS if not already enabled
            if (!llmEnabled) {
                Log.e(TAG, "🔧 Enabling LLM for RCS auto-reply for testing")
                prefs.edit().putBoolean(RCS_USE_LLM_KEY, true).apply()
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
     * Get the default auto-reply message
     */
    fun getDefaultMessage(): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(RCS_AUTO_REPLY_MESSAGE_KEY, 
            "I'm currently unavailable. I'll respond as soon as possible.") ?: 
            "I'm currently unavailable. I'll respond as soon as possible."
    }
    
    /**
     * Set the default auto-reply message
     */
    fun setDefaultMessage(message: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(RCS_AUTO_REPLY_MESSAGE_KEY, message).apply()
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
     * Check if we should reply to a message based on rules
     * @return The reply message if should reply, null otherwise
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
                    // If LLM is enabled, use it to generate a response
                    if (isLLMEnabled() && isMLCInitialized && mlcLlmModule != null) {
                        Log.e(TAG, "🧠 LLM is enabled, generating intelligent response")
                        
                        // Record that we've replied to this sender
                        recordReply(sender, message)
                        
                        // Generate LLM response synchronously for RCS
                        val llmResponse = generateLLMResponseSync(sender, message, ruleMessage)
                        if (llmResponse.isNotEmpty()) {
                            Log.e(TAG, "✅ Using LLM-generated response: $llmResponse")
                            return llmResponse
                        } else {
                            Log.e(TAG, "⚠️ LLM response was empty, using rule message")
                            return ruleMessage
                        }
                    }
                    
                    Log.e(TAG, "📝 Using rule-based response: $ruleMessage")
                    // Record that we've replied to this sender
                    recordReply(sender, message)
                    return ruleMessage
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error processing rule ${i}: ${e.message}")
            }
        }
        
        // If no specific rule matched but auto-reply is enabled, use default message
        Log.e(TAG, "📋 No specific rules matched, using default behavior")
        recordReply(sender, message)
        
        // If LLM is enabled, use it to generate a response
        if (isLLMEnabled() && isMLCInitialized && mlcLlmModule != null) {
            Log.e(TAG, "🧠 LLM is enabled for default response")
            val llmResponse = generateLLMResponseSync(sender, message, getDefaultMessage())
            if (llmResponse.isNotEmpty()) {
                Log.e(TAG, "✅ Using LLM-generated default response: $llmResponse")
                return llmResponse
            } else {
                Log.e(TAG, "⚠️ LLM response was empty, using default message")
                return getDefaultMessage()
            }
        }
        
        Log.e(TAG, "📝 Using default message: ${getDefaultMessage()}")
        return getDefaultMessage()
    }
    
    /**
     * Generate a response using the MLC LLM module synchronously
     */
    private fun generateLLMResponseSync(sender: String, receivedMessage: String, fallbackMessage: String): String {
        Log.e(TAG, "🧠🧠🧠 GENERATING LLM RESPONSE SYNC 🧠🧠🧠")
        Log.e(TAG, "   • Sender: $sender")
        Log.e(TAG, "   • Received: $receivedMessage")
        Log.e(TAG, "   • Fallback: $fallbackMessage")
        
        return try {
            // Try to use the same LLM approach as SMS auto-reply
            val response = generateLLMResponseWithDocuments(sender, receivedMessage)
            
            if (response.isNotEmpty()) {
                Log.e(TAG, "✅ LLM generated response: $response")
                addLogEntry(sender, receivedMessage, response, true, true)
                response
            } else {
                Log.e(TAG, "⚠️ LLM response was empty, using fallback")
                addLogEntry(sender, receivedMessage, fallbackMessage, true, false)
                fallbackMessage
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error generating LLM response: ${e.message}")
            addLogEntry(sender, receivedMessage, fallbackMessage, true, false)
            fallbackMessage
        }
    }
    
    /**
     * Generate LLM response using document context (similar to SMS auto-reply)
     */
    private fun generateLLMResponseWithDocuments(sender: String, receivedMessage: String): String {
        Log.e(TAG, "📚 Generating LLM response with document context")
        
        try {
            // Try to access CallSmsModule for document-based LLM
            val reactContext = try {
                (context as ReactApplicationContext)
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error getting ReactContext: ${e.message}")
                null
            }
            
            if (reactContext != null) {
                val callSmsModule = reactContext.getNativeModule(CallSmsModule::class.java)
                if (callSmsModule != null) {
                    try {
                        Log.e(TAG, "📝 Using CallSmsModule.testLLM for document-based response")
                        
                        // Use reflection to call the testLLM method
                        val testLLMMethod = CallSmsModule::class.java.getDeclaredMethod(
                            "testLLM",
                            String::class.java
                        )
                        testLLMMethod.isAccessible = true
                        
                        // Call the method directly
                        val result = testLLMMethod.invoke(callSmsModule, receivedMessage)
                        if (result != null) {
                            val rawResponse = result as String
                            val cleanedResponse = cleanLLMResponse(rawResponse)
                            
                            if (cleanedResponse.isNotEmpty()) {
                                Log.e(TAG, "✅ Successfully got document-based LLM response: $cleanedResponse")
                                return cleanedResponse
                            } else {
                                Log.e(TAG, "⚠️ Document-based LLM response was empty after cleaning")
                            }
                        } else {
                            Log.e(TAG, "❌ No response from document-based LLM")
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "❌ Error calling testLLM: ${e.message}", e)
                    }
                } else {
                    Log.e(TAG, "❌ CallSmsModule is null, cannot use document-based LLM")
                }
            } else {
                Log.e(TAG, "❌ ReactContext is null, cannot use document-based LLM")
            }
            
            // Fallback to MLC LLM if document-based approach fails
            Log.e(TAG, "🔄 Falling back to MLC LLM")
            val context = "You are responding to a message from $sender. " +
                          "Keep your response brief and conversational. " +
                          "The message you received is: \"$receivedMessage\""
            
            val prompt = "Generate a brief, friendly auto-reply to this message."
            
            // Use runBlocking to make the async call synchronous
            val response = runBlocking { 
                mlcLlmModule?.generateAnswer(prompt, context, 0.7f) ?: ""
            }
            
            if (response.isNotEmpty() && !response.startsWith("AI:")) {
                Log.e(TAG, "✅ MLC LLM generated response: $response")
                return response
            } else {
                Log.e(TAG, "⚠️ MLC LLM response was empty or invalid")
                return ""
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error in generateLLMResponseWithDocuments: ${e.message}", e)
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
     * Check if we've already replied to this sender within the rate limit period
     * Now also considers the message content to handle multiple messages in a conversation
     */
    private fun hasRepliedRecently(sender: String, message: String): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val repliedConversations = prefs.getString(RCS_REPLIED_CONVERSATIONS_KEY, "{}") ?: "{}"
        val rateLimit = getRateLimit()
        
        try {
            val json = JSONObject(repliedConversations)
            
            // Create a unique key for this sender and message
            // This helps distinguish between different messages from the same sender
            val senderKey = sender
            
            if (json.has(senderKey)) {
                val senderData = json.getJSONObject(senderKey)
                val lastReplyTime = senderData.getLong("timestamp")
                val currentTime = System.currentTimeMillis()
                
                // Check if we're within the rate limit period
                if ((currentTime - lastReplyTime) < rateLimit) {
                    // If this is the exact same message we've already replied to, don't reply again
                    if (senderData.has("lastMessage") && 
                        senderData.getString("lastMessage") == message) {
                        return true
                    }
                    
                    // If it's a different message but from same sender within rate limit,
                    // check message similarity to avoid replying to similar messages
                    val lastMessage = senderData.optString("lastMessage", "")
                    if (lastMessage.isNotEmpty() && areMessagesSimilar(lastMessage, message)) {
                        return true
                    }
                    
                    // If it's a very short message, apply stricter rate limiting
                    if (message.length < 5) {
                        return true
                    }
                    
                    // If the new message is significantly different, allow a reply
                    if (messageSignificantlyDifferent(lastMessage, message)) {
                        return false
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking replied conversations: ${e.message}")
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
} 