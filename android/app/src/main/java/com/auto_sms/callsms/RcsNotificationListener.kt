package com.auto_sms.callsms

import android.app.Notification
import android.content.Context
import android.content.Intent
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import android.os.Bundle
import android.app.RemoteInput
import android.content.SharedPreferences
import android.os.Build
import java.util.regex.Pattern

class RcsNotificationListener : NotificationListenerService() {
    private val TAG = "RcsNotification"
    
    // Messaging app package IDs to monitor
    private val MESSAGING_APPS = listOf(
        "com.google.android.apps.messaging",      // Google Messages
        "com.samsung.android.messaging",          // Samsung Messages
        "com.oneplus.mms",                        // OnePlus Messages
        "com.motorola.messaging",                 // Motorola Messages
        "com.android.mms",                        // AOSP Messaging
        "com.verizon.messaging.vzmsgs",           // Verizon Messages
        "com.google.android.apps.messaging.debug" // Google Messages Debug
    )
    
    private lateinit var rcsManager: RcsAutoReplyManager
    
    override fun onCreate() {
        super.onCreate()
        rcsManager = RcsAutoReplyManager(this)
        Log.e(TAG, "🚀🚀🚀 RCS Notification Listener Service created and initialized 🚀🚀🚀")
    }

    override fun onDestroy() {
        super.onDestroy()
        // Clean up resources
        if (::rcsManager.isInitialized) {
            rcsManager.cleanup()
        }
        Log.e(TAG, "🛑 RCS Notification Listener Service destroyed")
    }
    
    override fun onListenerConnected() {
        super.onListenerConnected()
        Log.e(TAG, "✅✅✅ RCS Notification Listener connected to system ✅✅✅")
        
        // Check if RCS auto-reply is enabled
        val isEnabled = rcsManager.isEnabled()
        Log.e(TAG, "📊 RCS Auto-reply enabled: $isEnabled")
        
        if (isEnabled) {
            Log.e(TAG, "🎯 RCS Auto-reply is ready to process notifications")
        } else {
            Log.e(TAG, "⚠️ RCS Auto-reply is disabled - notifications will be ignored")
        }
    }
    
    override fun onNotificationPosted(sbn: StatusBarNotification) {
        try {
            Log.e(TAG, "📨📨📨 NOTIFICATION RECEIVED 📨📨📨")
            Log.e(TAG, "   • Package: ${sbn.packageName}")
            Log.e(TAG, "   • ID: ${sbn.id}")
            Log.e(TAG, "   • Key: ${sbn.key}")
            Log.e(TAG, "   • Post Time: ${sbn.postTime}")
            
            // Check if RCS auto-reply is enabled
            if (!rcsManager.isEnabled()) {
                Log.e(TAG, "❌ RCS auto-reply is disabled, ignoring notification")
                return
            }
            
            val packageName = sbn.packageName
            
            // Check if this is from a messaging app we care about
            if (!MESSAGING_APPS.contains(packageName)) {
                Log.e(TAG, "❌ Ignoring notification from non-messaging app: $packageName")
                return
            }
            
            Log.e(TAG, "✅ Notification is from supported messaging app: $packageName")
            
            val notification = sbn.notification
            
            // Check if this is a message notification
            if (notification.category != Notification.CATEGORY_MESSAGE) {
                Log.e(TAG, "❌ Ignoring non-message notification category: ${notification.category}")
                return
            }
            
            Log.e(TAG, "✅ Notification is a message notification")
            
            // Extract notification data
            val extras = notification.extras
            val title = extras.getString(Notification.EXTRA_TITLE)
            val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
            
            // Enhanced logging with consistent tag for easier filtering
            Log.e(TAG, "LOGTAG_RCS_DETAILS: 📨📨📨 RCS MESSAGE DETAILS 📨📨📨")
            Log.e(TAG, "LOGTAG_RCS_DETAILS: ↘️ Sender: $title")
            Log.e(TAG, "LOGTAG_RCS_DETAILS: ↘️ Message: $text")
            Log.e(TAG, "LOGTAG_RCS_DETAILS: ↘️ Package: $packageName")
            Log.e(TAG, "LOGTAG_RCS_DETAILS: ↘️ Timestamp: ${sbn.postTime}")
            Log.e(TAG, "LOGTAG_RCS_DETAILS: ↘️ Actions count: ${notification.actions?.size ?: 0}")
            Log.e(TAG, "LOGTAG_RCS_DETAILS: ↘️ Notification ID: ${sbn.id}")
            Log.e(TAG, "LOGTAG_RCS_DETAILS: ↘️ Notification Key: ${sbn.key}")
            
            if (title == null || text == null) {
                Log.e(TAG, "❌ Ignoring notification with missing title or text")
                return
            }
            
            // Get conversation ID if available (for tracking ongoing conversations)
            val conversationId = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                // Use notification key as conversation ID since EXTRA_CONVERSATION_ID isn't directly available
                sbn.key
            } else {
                sbn.key
            }
            
            // Check if this appears to be an RCS message
            val isRcsMessage = isRcsMessage(packageName, notification, extras)
            
            Log.e(TAG, "LOGTAG_RCS_DETAILS: ↘️ Is RCS: $isRcsMessage")
            Log.e(TAG, "LOGTAG_RCS_DETAILS: ↘️ Conversation ID: $conversationId")
            
            // Process the notification with rule engine
            Log.e(TAG, "🧠 Processing message with RCS auto-reply manager...")
            val replyMessage = rcsManager.processMessage(title, text)
            
            if (replyMessage != null) {
                Log.e(TAG, "LOGTAG_RCS_DETAILS: ↘️ Auto-reply: $replyMessage")
                
                // Get the reply action
                val replyAction = getReplyAction(notification)
                if (replyAction != null) {
                    Log.e(TAG, "✅ Found reply action, sending auto-reply...")
                    // Send auto-reply
                    sendAutoReply(replyAction, conversationId, title, text, replyMessage)
                } else {
                    Log.e(TAG, "❌ No reply action found in notification")
                    rcsManager.addLogEntry(title, text, "Failed: No reply action available", false)
                }
            } else {
                Log.e(TAG, "ℹ️ No auto-reply needed for this message")
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌❌❌ Error processing notification: ${e.message}", e)
        }
    }
    
    /**
     * Try to determine if this is an RCS message based on available information
     */
    private fun isRcsMessage(packageName: String, notification: Notification, extras: Bundle): Boolean {
        Log.e(TAG, "🔍 Analyzing if message is RCS...")
        
        // Google Messages typically has specific extras for RCS
        if (packageName == "com.google.android.apps.messaging" || 
            packageName == "com.google.android.apps.messaging.debug") {
            
            Log.e(TAG, "LOGTAG_RCS_DETAILS: ↘️ Checking Google Messages app for RCS indicators")
            
            // Check for RCS-specific extras
            val hasMessagingStyleUser = extras.containsKey("android.messagingStyleUser")
            val hasMessagingUser = extras.containsKey("android.messagingUser")
            val hasHiddenConversationTitle = extras.containsKey("android.hiddenConversationTitle")
            
            // Additional RCS indicators that might be present
            val hasPeopleList = extras.containsKey("android.people.list")
            val hasMessagingPerson = extras.containsKey("android.messagingPerson")
            val hasMessages = extras.containsKey("android.messages")
            
            Log.e(TAG, "LOGTAG_RCS_DETAILS: ↘️ Indicators - messagingStyleUser: $hasMessagingStyleUser, messagingUser: $hasMessagingUser")
            Log.e(TAG, "LOGTAG_RCS_DETAILS: ↘️ Indicators - hiddenConversationTitle: $hasHiddenConversationTitle")
            Log.e(TAG, "LOGTAG_RCS_DETAILS: ↘️ Indicators - peopleList: $hasPeopleList, messagingPerson: $hasMessagingPerson, messages: $hasMessages")
            
            // Log all extras for debugging
            extras.keySet().forEach { key ->
                val value = when (val v = extras.get(key)) {
                    null -> "null"
                    is CharSequence -> v.toString()
                    is Bundle -> "Bundle with ${v.size()} items"
                    is Array<*> -> "Array with ${v.size} items"
                    else -> v.toString()
                }
                Log.e(TAG, "LOGTAG_RCS_DETAILS: ↘️ Extra - $key: $value")
            }
            
            if (hasMessagingStyleUser || hasMessagingUser || hasHiddenConversationTitle || hasPeopleList || hasMessagingPerson || hasMessages) {
                Log.e(TAG, "LOGTAG_RCS_DETAILS: ↘️ RESULT: Found RCS indicators in Google Messages")
                return true
            }
            
            // Check for specific actions that are usually present in RCS messages
            val actions = notification.actions ?: emptyArray()
            Log.e(TAG, "LOGTAG_RCS_DETAILS: ↘️ Found ${actions.size} notification actions")
            
            // Log all action titles for debugging
            actions.forEachIndexed { index, action ->
                Log.e(TAG, "LOGTAG_RCS_DETAILS: ↘️ Action $index - ${action?.title}")
            }
            
            val hasMarkAsReadAction = actions.any { action -> 
                action?.title?.toString()?.contains("Mark as read", ignoreCase = true) == true
            }
            
            val hasReplyAction = actions.any { action ->
                action?.title?.toString()?.contains("Reply", ignoreCase = true) == true
            }
            
            Log.e(TAG, "LOGTAG_RCS_DETAILS: ↘️ Has Mark as read action: $hasMarkAsReadAction")
            Log.e(TAG, "LOGTAG_RCS_DETAILS: ↘️ Has Reply action: $hasReplyAction")
            
            if (hasMarkAsReadAction || hasReplyAction) {
                Log.e(TAG, "LOGTAG_RCS_DETAILS: ↘️ RESULT: Found typical RCS actions")
                return true
            }
        }
        
        // For other messaging apps, we have to make an educated guess
        // Most RCS implementations use MessagingStyle notifications
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val template = extras.getString(Notification.EXTRA_TEMPLATE)
            Log.e(TAG, "LOGTAG_RCS_DETAILS: ↘️ Notification template: $template")
            
            if (Notification.MessagingStyle::class.java.name == template) {
                Log.e(TAG, "LOGTAG_RCS_DETAILS: ↘️ RESULT: Found MessagingStyle template (likely RCS)")
                return true
            }
        }
        
        // Additional checks for RCS indicators
        val hasRemoteInputs = notification.actions?.any { action ->
            action?.remoteInputs?.isNotEmpty() == true
        } == true
        
        Log.e(TAG, "   • Has RemoteInputs: $hasRemoteInputs")
        
        // If we have remote inputs and it's from a messaging app, it's likely RCS
        if (hasRemoteInputs) {
            Log.e(TAG, "✅ RemoteInputs found, treating as RCS")
            return true
        }
        
        Log.e(TAG, "❌ No RCS indicators found, treating as regular SMS")
        return false
    }
    
    private fun getReplyAction(notification: Notification): Notification.Action? {
        Log.e(TAG, "🔍 Looking for reply action in notification...")
        
        // Check if we have actions
        if (notification.actions == null) {
            Log.e(TAG, "❌ No actions found in notification")
            return null
        }
        
        Log.e(TAG, "📋 Notification has ${notification.actions.size} actions:")
        
        // Look for the action with RemoteInput that has "reply" in the title or result key
        for (i in notification.actions.indices) {
            val action = notification.actions[i]
            val actionTitle = action?.title?.toString() ?: "null"
            val hasRemoteInputs = action?.remoteInputs?.isNotEmpty() == true
            
            Log.e(TAG, "   • Action $i: '$actionTitle' (has RemoteInputs: $hasRemoteInputs)")
            
            if (action?.remoteInputs != null && action.remoteInputs.isNotEmpty()) {
                // Check if this is a reply action by looking at the title
                val lowerTitle = actionTitle.lowercase()
                val isReplyAction = lowerTitle.contains("reply") || 
                    action.remoteInputs.any { it.resultKey.contains("reply", ignoreCase = true) }
                
                Log.e(TAG, "   • Is reply action: $isReplyAction")
                
                if (isReplyAction) {
                    Log.e(TAG, "✅ Found reply action: $actionTitle")
                    return action
                }
            }
        }
        
        // If we didn't find a specific reply action, try to find any action with remote inputs
        for (i in notification.actions.indices) {
            val action = notification.actions[i]
            if (action?.remoteInputs != null && action.remoteInputs.isNotEmpty()) {
                Log.e(TAG, "✅ Using fallback action with RemoteInput: ${action.title}")
                return action
            }
        }
        
        Log.e(TAG, "❌ No reply action found in notification actions")
        return null
    }
    
    private fun sendAutoReply(
        replyAction: Notification.Action,
        conversationId: String?,
        sender: String,
        originalMessage: String,
        replyMessage: String
    ) {
        try {
            Log.e(TAG, "📤📤📤 SENDING AUTO-REPLY 📤📤📤")
            Log.e(TAG, "   • To: $sender")
            Log.e(TAG, "   • Original: $originalMessage")
            Log.e(TAG, "   • Reply: $replyMessage")
            Log.e(TAG, "   • Conversation ID: $conversationId")
            
            // Create the remote input
            val remoteInputs = replyAction.remoteInputs
            if (remoteInputs.isEmpty()) {
                Log.e(TAG, "❌ No remote inputs found")
                rcsManager.addLogEntry(sender, originalMessage, "Failed: No remote inputs", false)
                return
            }
            
            Log.e(TAG, "📝 Remote inputs found: ${remoteInputs.size}")
            for (i in remoteInputs.indices) {
                val remoteInput = remoteInputs[i]
                Log.e(TAG, "   • RemoteInput $i: ${remoteInput.resultKey}")
            }
            
            val resultIntent = Intent()
            val resultBundle = Bundle()
            
            // Fill the bundle with the reply text
            for (remoteInput in remoteInputs) {
                resultBundle.putCharSequence(remoteInput.resultKey, replyMessage)
                Log.e(TAG, "📝 Adding reply to RemoteInput with key: ${remoteInput.resultKey}")
            }
            
            RemoteInput.addResultsToIntent(remoteInputs, resultIntent, resultBundle)
            
            // Execute the action
            try {
                replyAction.actionIntent.send(this, 0, resultIntent)
                Log.e(TAG, "✅✅✅ Auto-reply sent successfully! ✅✅✅")
                
                // Log this auto-reply
                rcsManager.addLogEntry(sender, originalMessage, replyMessage, true)
            } catch (e: Exception) {
                Log.e(TAG, "❌❌❌ Failed to send auto-reply: ${e.message}", e)
                rcsManager.addLogEntry(sender, originalMessage, "Failed: ${e.message}", false)
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌❌❌ Error in sendAutoReply: ${e.message}", e)
        }
    }
} 