package com.auto_sms.callsms

import android.content.Context
import android.util.Log
import com.facebook.react.bridge.*

class AutoReplyModule(reactContext: ReactApplicationContext) : ReactContextBaseJavaModule(reactContext) {
    private val TAG = "AutoReplyModule"
    
    // Auto-reply feature keys
    private val AUTO_REPLY_ENABLED_KEY = "@AutoSMS:AutoReplyEnabled"
    // Document-based LLM auto-reply keys
    private val LLM_AUTO_REPLY_ENABLED_KEY = "@AutoSMS:LLMAutoReplyEnabled"
    private val LLM_CONTEXT_LENGTH_KEY = "@AutoSMS:LLMContextLength"
    // RCS auto-reply keys
    private val RCS_AUTO_REPLY_ENABLED_KEY = "@AutoSMS:RcsAutoReplyEnabled"
    private val RCS_AUTO_REPLY_MESSAGE_KEY = "@AutoSMS:RcsAutoReplyMessage"
    private val RCS_RATE_LIMIT_KEY = "@AutoSMS:RcsRateLimit"
    
    // Create an instance of RcsAutoReplyManager
    private val rcsManager by lazy { RcsAutoReplyManager(reactApplicationContext) }
    
    override fun getName(): String {
        return "AutoReplyModule"
    }
    
    @ReactMethod
    fun isAutoReplyEnabled(promise: Promise) {
        try {
            val sharedPrefs = reactApplicationContext.getSharedPreferences("AutoSmsPrefs", Context.MODE_PRIVATE)
            val enabled = sharedPrefs.getBoolean(AUTO_REPLY_ENABLED_KEY, false)
            Log.d(TAG, "Auto-reply enabled check: $enabled")
            promise.resolve(enabled)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting auto-reply enabled: ${e.message}")
            promise.reject("GET_AUTO_REPLY_ERROR", "Failed to get auto-reply enabled: ${e.message}")
        }
    }

    @ReactMethod
    fun setAutoReplyEnabled(enabled: Boolean, promise: Promise) {
        try {
            // Save setting to SharedPreferences for use when app is killed
            val sharedPrefs = reactApplicationContext.getSharedPreferences("AutoSmsPrefs", Context.MODE_PRIVATE)
            sharedPrefs.edit().putBoolean(AUTO_REPLY_ENABLED_KEY, enabled).apply()
            Log.d(TAG, "Auto-reply feature ${if (enabled) "enabled" else "disabled"}")
            promise.resolve(true)
        } catch (e: Exception) {
            Log.e(TAG, "Error setting auto-reply enabled: ${e.message}")
            promise.reject("SET_AUTO_REPLY_ERROR", "Failed to set auto-reply enabled: ${e.message}")
        }
    }
    
    @ReactMethod
    fun isLLMAutoReplyEnabled(promise: Promise) {
        try {
            val sharedPrefs = reactApplicationContext.getSharedPreferences("AutoSmsPrefs", Context.MODE_PRIVATE)
            val enabled = sharedPrefs.getBoolean(LLM_AUTO_REPLY_ENABLED_KEY, false)
            Log.d(TAG, "LLM auto-reply enabled check: $enabled")
            promise.resolve(enabled)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting LLM auto-reply enabled: ${e.message}")
            promise.reject("GET_LLM_AUTO_REPLY_ERROR", "Failed to get LLM auto-reply enabled: ${e.message}")
        }
    }
    
    @ReactMethod
    fun setLLMAutoReplyEnabled(enabled: Boolean, promise: Promise) {
        try {
            // Save setting to SharedPreferences for use when app is killed
            val sharedPrefs = reactApplicationContext.getSharedPreferences("AutoSmsPrefs", Context.MODE_PRIVATE)
            sharedPrefs.edit().putBoolean(LLM_AUTO_REPLY_ENABLED_KEY, enabled).apply()
            Log.d(TAG, "LLM auto-reply feature ${if (enabled) "enabled" else "disabled"}")
            promise.resolve(true)
        } catch (e: Exception) {
            Log.e(TAG, "Error setting LLM auto-reply enabled: ${e.message}")
            promise.reject("SET_LLM_AUTO_REPLY_ERROR", "Failed to set LLM auto-reply enabled: ${e.message}")
        }
    }
    
    @ReactMethod
    fun getLLMContextLength(promise: Promise) {
        try {
            val sharedPrefs = reactApplicationContext.getSharedPreferences("AutoSmsPrefs", Context.MODE_PRIVATE)
            val contextLength = sharedPrefs.getInt(LLM_CONTEXT_LENGTH_KEY, 2048)
            promise.resolve(contextLength)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting LLM context length: ${e.message}")
            promise.reject("GET_LLM_CONTEXT_LENGTH_ERROR", "Failed to get LLM context length: ${e.message}")
        }
    }
    
    @ReactMethod
    fun setLLMContextLength(length: Int, promise: Promise) {
        try {
            val sharedPrefs = reactApplicationContext.getSharedPreferences("AutoSmsPrefs", Context.MODE_PRIVATE)
            sharedPrefs.edit().putInt(LLM_CONTEXT_LENGTH_KEY, length).apply()
            Log.d(TAG, "Set LLM context length to $length")
            promise.resolve(true)
        } catch (e: Exception) {
            Log.e(TAG, "Error setting LLM context length: ${e.message}")
            promise.reject("SET_LLM_CONTEXT_LENGTH_ERROR", "Failed to set LLM context length: ${e.message}")
        }
    }
    
    @ReactMethod
    fun clearMissedCallNumbers(promise: Promise) {
        try {
            val sharedPrefs = reactApplicationContext.getSharedPreferences("AutoSmsPrefs", Context.MODE_PRIVATE)
            sharedPrefs.edit().putStringSet("missedCallNumbers", HashSet()).apply()
            Log.d(TAG, "Cleared missed call numbers")
            promise.resolve(true)
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing missed call numbers: ${e.message}")
            promise.reject("CLEAR_MISSED_CALLS_ERROR", "Failed to clear missed call numbers: ${e.message}")
        }
    }
    
    @ReactMethod
    fun getMissedCallNumbers(promise: Promise) {
        try {
            val sharedPrefs = reactApplicationContext.getSharedPreferences("AutoSmsPrefs", Context.MODE_PRIVATE)
            val missedCallNumbers = sharedPrefs.getStringSet("missedCallNumbers", HashSet()) ?: HashSet()
            
            val resultArray = Arguments.createArray()
            val currentTime = System.currentTimeMillis()
            val twentyFourHoursAgo = currentTime - (24 * 60 * 60 * 1000)
            
            for (entry in missedCallNumbers) {
                val parts = entry.split(":", limit = 2)
                if (parts.size == 2) {
                    val number = parts[0]
                    val timestamp = parts[1].toLongOrNull() ?: 0
                    
                    // Only include numbers from the last 24 hours
                    if (timestamp > twentyFourHoursAgo) {
                        val item = Arguments.createMap()
                        item.putString("phoneNumber", number)
                        item.putDouble("timestamp", timestamp.toDouble())
                        resultArray.pushMap(item)
                    }
                }
            }
            
            Log.d(TAG, "Got ${resultArray.size()} recent missed call numbers")
            promise.resolve(resultArray)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting missed call numbers: ${e.message}")
            promise.reject("GET_MISSED_CALLS_ERROR", "Failed to get missed call numbers: ${e.message}")
        }
    }
    
    @ReactMethod
    fun isRcsAutoReplyEnabled(promise: Promise) {
        try {
            val enabled = rcsManager.isEnabled()
            Log.d(TAG, "RCS auto-reply enabled check: $enabled")
            promise.resolve(enabled)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting RCS auto-reply enabled: ${e.message}")
            promise.reject("GET_RCS_AUTO_REPLY_ERROR", "Failed to get RCS auto-reply enabled: ${e.message}")
        }
    }
    
    @ReactMethod
    fun setRcsAutoReplyEnabled(enabled: Boolean, promise: Promise) {
        try {
            rcsManager.setEnabled(enabled)
            Log.d(TAG, "RCS auto-reply feature ${if (enabled) "enabled" else "disabled"}")
            promise.resolve(true)
        } catch (e: Exception) {
            Log.e(TAG, "Error setting RCS auto-reply enabled: ${e.message}")
            promise.reject("SET_RCS_AUTO_REPLY_ERROR", "Failed to set RCS auto-reply enabled: ${e.message}")
        }
    }
    
    @ReactMethod
    fun isRcsLLMEnabled(promise: Promise) {
        try {
            val enabled = rcsManager.isLLMEnabled()
            Log.d(TAG, "RCS LLM auto-reply enabled check: $enabled")
            promise.resolve(enabled)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting RCS LLM auto-reply enabled: ${e.message}")
            promise.reject("GET_RCS_LLM_ERROR", "Failed to get RCS LLM auto-reply enabled: ${e.message}")
        }
    }
    
    @ReactMethod
    fun setRcsLLMEnabled(enabled: Boolean, promise: Promise) {
        try {
            rcsManager.setLLMEnabled(enabled)
            Log.d(TAG, "RCS LLM auto-reply feature ${if (enabled) "enabled" else "disabled"}")
            promise.resolve(true)
        } catch (e: Exception) {
            Log.e(TAG, "Error setting RCS LLM auto-reply enabled: ${e.message}")
            promise.reject("SET_RCS_LLM_ERROR", "Failed to set RCS LLM auto-reply enabled: ${e.message}")
        }
    }
    
    @ReactMethod
    fun getRcsAutoReplyMessage(promise: Promise) {
        try {
            val message = rcsManager.getDefaultMessage("Test Sender", "Test Message")
            promise.resolve(message)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting RCS auto-reply message: ${e.message}")
            promise.reject("GET_RCS_AUTO_REPLY_MESSAGE_ERROR", "Failed to get RCS auto-reply message: ${e.message}")
        }
    }
    
    @ReactMethod
    fun setRcsAutoReplyMessage(message: String, promise: Promise) {
        try {
            rcsManager.setDefaultMessage(message)
            Log.d(TAG, "Set RCS auto-reply message to: $message")
            promise.resolve(true)
        } catch (e: Exception) {
            Log.e(TAG, "Error setting RCS auto-reply message: ${e.message}")
            promise.reject("SET_RCS_AUTO_REPLY_MESSAGE_ERROR", "Failed to set RCS auto-reply message: ${e.message}")
        }
    }
    
    @ReactMethod
    fun getRcsRateLimit(promise: Promise) {
        try {
            val rateLimit = rcsManager.getRateLimit()
            promise.resolve(rateLimit)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting RCS rate limit: ${e.message}")
            promise.reject("GET_RCS_RATE_LIMIT_ERROR", "Failed to get RCS rate limit: ${e.message}")
        }
    }
    
    @ReactMethod
    fun setRcsRateLimit(rateLimit: Double, promise: Promise) {
        try {
            rcsManager.setRateLimit(rateLimit.toLong())
            Log.d(TAG, "Set RCS rate limit to: $rateLimit ms")
            promise.resolve(true)
        } catch (e: Exception) {
            Log.e(TAG, "Error setting RCS rate limit: ${e.message}")
            promise.reject("SET_RCS_RATE_LIMIT_ERROR", "Failed to set RCS rate limit: ${e.message}")
        }
    }
    
    @ReactMethod
    fun clearRcsRepliedConversations(promise: Promise) {
        try {
            val sharedPrefs = reactApplicationContext.getSharedPreferences("AutoSmsPrefs", Context.MODE_PRIVATE)
            sharedPrefs.edit().putString(RcsAutoReplyManager.RCS_REPLIED_CONVERSATIONS_KEY, "{}").apply()
            Log.d(TAG, "Cleared RCS replied conversations")
            promise.resolve(true)
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing RCS replied conversations: ${e.message}")
            promise.reject("CLEAR_RCS_REPLIED_CONVERSATIONS_ERROR", "Failed to clear RCS replied conversations: ${e.message}")
        }
    }
    
    @ReactMethod
    fun getRcsAutoReplyLogs(promise: Promise) {
        try {
            val logs = rcsManager.getLogs()
            val resultArray = Arguments.createArray()
            
            for (i in 0 until logs.length()) {
                val log = logs.getJSONObject(i)
                val item = Arguments.createMap()
                
                item.putString("sender", log.getString("sender"))
                item.putString("received", log.getString("received"))
                item.putString("sent", log.getString("sent"))
                item.putBoolean("success", log.getBoolean("success"))
                item.putDouble("timestamp", log.getLong("timestamp").toDouble())
                item.putString("type", log.getString("type"))
                item.putBoolean("isLLM", log.optBoolean("isLLM", false))
                
                resultArray.pushMap(item)
            }
            
            Log.d(TAG, "Got ${resultArray.size()} RCS auto-reply logs")
            promise.resolve(resultArray)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting RCS auto-reply logs: ${e.message}")
            promise.reject("GET_RCS_AUTO_REPLY_LOGS_ERROR", "Failed to get RCS auto-reply logs: ${e.message}")
        }
    }
    
    @ReactMethod
    fun clearRcsAutoReplyLogs(promise: Promise) {
        try {
            rcsManager.clearLogs()
            Log.d(TAG, "Cleared RCS auto-reply logs")
            promise.resolve(true)
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing RCS auto-reply logs: ${e.message}")
            promise.reject("CLEAR_RCS_AUTO_REPLY_LOGS_ERROR", "Failed to clear RCS auto-reply logs: ${e.message}")
        }
    }
    
    @ReactMethod
    fun addListener(eventName: String) {
        // Keep track of listeners if needed
    }

    @ReactMethod
    fun removeListeners(count: Int) {
        // Remove listeners if needed
    }
} 