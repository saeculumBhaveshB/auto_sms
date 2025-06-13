package com.auto_sms.callsms

import android.app.Notification
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import android.os.Bundle
import android.app.RemoteInput
import android.content.SharedPreferences
import android.os.Build
import java.util.regex.Pattern
import android.content.BroadcastReceiver

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
        
        // Create RCS Auto-Reply Manager
        rcsManager = RcsAutoReplyManager(applicationContext)
        
        // Check if RCS auto-reply is enabled
        val isEnabled = rcsManager.isEnabled()
        Log.e(TAG, "📊 RCS Auto-reply enabled: $isEnabled")
        
        if (isEnabled) {
            Log.e(TAG, "🎯 RCS Auto-reply is ready to process notifications")
        } else {
            Log.e(TAG, "⚠️ RCS Auto-reply is disabled - notifications will be ignored")
        }
        
        // Register broadcast receiver for test commands
        val filter = IntentFilter().apply {
            addAction("com.auto_sms.RESET_RCS_STATE")
            addAction("com.auto_sms.SET_TESTING_RATE_LIMIT")
        }
        
        applicationContext.registerReceiver(testCommandReceiver, filter)
        Log.e(TAG, "🔄 Registered test command receiver")
    }
    
    // BroadcastReceiver for test commands
    private val testCommandReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                "com.auto_sms.RESET_RCS_STATE" -> {
                    Log.e(TAG, "🧹 Received command to reset RCS state")
                    rcsManager.clearRepliedConversations()
                    
                    // Also make sure RCS auto-reply is enabled
                    if (!rcsManager.isEnabled()) {
                        Log.e(TAG, "🔄 Enabling RCS auto-reply for testing")
                        rcsManager.setEnabled(true)
                    }
                }
                "com.auto_sms.SET_TESTING_RATE_LIMIT" -> {
                    Log.e(TAG, "⏱️ Received command to set testing rate limit")
                    rcsManager.setTestingRateLimit()
                }
            }
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        
        // Unregister the test command receiver
        try {
            applicationContext.unregisterReceiver(testCommandReceiver)
            Log.e(TAG, "🔄 Unregistered test command receiver")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error unregistering test command receiver: ${e.message}")
        }
        
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
            Log.e(TAG, "   • Category: ${sbn.notification.category}")
            
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
            
            // CRITICAL FIX: Special handling for service category notifications from Google Messages
            // These often contain important messaging data despite not being standard message notifications
            if (notification.category == "service" && 
                (packageName == "com.google.android.apps.messaging" || 
                 packageName == "com.google.android.apps.messaging.debug")) {
                Log.e(TAG, "🔍 Processing service category notification from Google Messages")
                processServiceNotification(sbn)
                return
            }
            
            // IMPROVEMENT: Be more lenient with notification categories
            // Some messaging apps might not set CATEGORY_MESSAGE correctly
            if (notification.category != Notification.CATEGORY_MESSAGE && 
                notification.category != Notification.CATEGORY_SOCIAL &&
                notification.category != null) { // Allow null categories
                
                // CRITICAL FIX: Don't reject notifications from Google Messages regardless of category
                // Many important RCS notifications come with category "service" or other categories
                if (packageName == "com.google.android.apps.messaging" ||
                    packageName == "com.google.android.apps.messaging.debug") {
                    Log.e(TAG, "⚠️ Found non-message category (${notification.category}) but allowing because it's from Google Messages")
                } else {
                    Log.e(TAG, "❌ Ignoring non-message notification category: ${notification.category}")
                    return
                }
            }
            
            Log.e(TAG, "✅ Notification is being processed (category: ${notification.category ?: "null"})")
            
            // Extract notification data
            val extras = notification.extras
            val title = extras.getString(Notification.EXTRA_TITLE)
            val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
            
            // Enhanced logging with consistent tag for easier filtering
            Log.e(TAG, "LOGTAG_RCS_DETAILS: 📨📨📨 RCS MESSAGE DETAILS 📨📨📨")
            
            // CRITICAL FIX: Check for messaging-specific extras that might indicate an RCS message
            // This helps with notifications that don't have standard title/text fields
            val hasMessagingExtras = extras.containsKey("android.messages") || 
                                    extras.containsKey("android.messagingUser") ||
                                    extras.containsKey("extra_im_notification_message_ids") ||
                                    extras.containsKey("extra_im_notification_conversation_id")
            
            // Log all detected indicators to help debug
            Log.e(TAG, "LOGTAG_RCS_DETAILS: ↘️ Has Messaging Extras: $hasMessagingExtras")
            if (hasMessagingExtras) {
                Log.e(TAG, "LOGTAG_RCS_DETAILS: ↘️ Messages: ${extras.containsKey("android.messages")}")
                Log.e(TAG, "LOGTAG_RCS_DETAILS: ↘️ Messaging User: ${extras.containsKey("android.messagingUser")}")
                Log.e(TAG, "LOGTAG_RCS_DETAILS: ↘️ Message IDs: ${extras.containsKey("extra_im_notification_message_ids")}")
                Log.e(TAG, "LOGTAG_RCS_DETAILS: ↘️ Conversation ID: ${extras.containsKey("extra_im_notification_conversation_id")}")
            }
            
            Log.e(TAG, "LOGTAG_RCS_DETAILS: ↘️ Sender: $title")
            Log.e(TAG, "LOGTAG_RCS_DETAILS: ↘️ Message: $text")
            Log.e(TAG, "LOGTAG_RCS_DETAILS: ↘️ Package: $packageName")
            Log.e(TAG, "LOGTAG_RCS_DETAILS: ↘️ Timestamp: ${sbn.postTime}")
            Log.e(TAG, "LOGTAG_RCS_DETAILS: ↘️ Actions count: ${notification.actions?.size ?: 0}")
            Log.e(TAG, "LOGTAG_RCS_DETAILS: ↘️ Notification ID: ${sbn.id}")
            Log.e(TAG, "LOGTAG_RCS_DETAILS: ↘️ Notification Key: ${sbn.key}")
            
            // IMPROVEMENT: Handle notifications with missing title or text more gracefully
            if (title == null || text == null) {
                Log.e(TAG, "⚠️ Notification missing title or text")
                
                // Try to extract information from other notification fields
                val fallbackTitle = title ?: extras.getString(Notification.EXTRA_CONVERSATION_TITLE) ?: 
                                   extras.getString(Notification.EXTRA_SUB_TEXT) ?: "Unknown Sender"
                
                // Try to extract text from different sources
                var fallbackText = text
                
                // Check standard fallback fields first
                if (fallbackText == null) {
                    fallbackText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString() ?:
                                 extras.getCharSequence(Notification.EXTRA_SUMMARY_TEXT)?.toString()
                }
                
                // CRITICAL FIX: Try to extract text from android.messages array if present
                // This is often where Google Messages stores the actual message content
                if (fallbackText == null && extras.containsKey("android.messages")) {
                    try {
                        val messages = extras.getParcelableArray("android.messages")
                        if (messages != null && messages.isNotEmpty()) {
                            // Try to get the last (most recent) message
                            val lastMessage = messages.last()
                            
                            // Use reflection to access the text field of the message
                            val textField = lastMessage.javaClass.getDeclaredField("mText")
                            textField.isAccessible = true
                            fallbackText = (textField.get(lastMessage) as? CharSequence)?.toString()
                            
                            Log.e(TAG, "🔍 Extracted message text from android.messages: $fallbackText")
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "❌ Error extracting message from android.messages: ${e.message}")
                    }
                }
                
                // If we still don't have text, try to use a generic message based on the notification ID
                if (fallbackText == null) {
                    fallbackText = "New message notification (ID: ${sbn.id})"
                    Log.e(TAG, "⚠️ Using generic fallback text: $fallbackText")
                }
                
                Log.e(TAG, "🔍 Using fallback data - Title: $fallbackTitle, Text: $fallbackText")
                
                // Process with fallback data
                processMessageNotification(sbn, notification, extras, fallbackTitle, fallbackText)
            } else {
                // Process with standard data
                processMessageNotification(sbn, notification, extras, title, text)
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌❌❌ Error processing notification: ${e.message}", e)
        }
    }
    
    /**
     * Process a message notification with the extracted data
     * This separate method allows for easier handling of fallback data
     */
    private fun processMessageNotification(
        sbn: StatusBarNotification,
        notification: Notification,
        extras: Bundle,
        sender: String,
        message: String
    ) {
        // Get conversation ID if available (for tracking ongoing conversations)
        val conversationId = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Use notification key as conversation ID since EXTRA_CONVERSATION_ID isn't directly available
            sbn.key
        } else {
            sbn.key
        }
        
        // Check if this appears to be an RCS message
        // IMPROVEMENT: For notifications from Google Messages, assume it's RCS even if indicators are missing
        var isRcsMessage = isRcsMessage(sbn.packageName, notification, extras)
        
        // If this is from Google Messages but doesn't have RCS indicators, still treat as RCS
        // This helps with single notifications that might not have all the RCS indicators
        if (!isRcsMessage && sbn.packageName == "com.google.android.apps.messaging") {
            Log.e(TAG, "⚠️ Message from Google Messages without RCS indicators - treating as RCS anyway")
            isRcsMessage = true
        }
        
        Log.e(TAG, "LOGTAG_RCS_DETAILS: ↘️ Is RCS: $isRcsMessage")
        Log.e(TAG, "LOGTAG_RCS_DETAILS: ↘️ Conversation ID: $conversationId")
        
        // Only proceed if this is an RCS message
        if (!isRcsMessage) {
            Log.e(TAG, "❌ Not an RCS message, ignoring")
            return
        }
        
        // Process the notification with rule engine
        Log.e(TAG, "🧠 Processing message with RCS auto-reply manager...")
        val replyMessage = rcsManager.processMessage(sender, message)
        
        if (replyMessage != null) {
            Log.e(TAG, "LOGTAG_RCS_DETAILS: ↘️ Auto-reply: $replyMessage")
            
            // Get the reply action
            val replyAction = getReplyAction(notification)
            if (replyAction != null) {
                Log.e(TAG, "✅ Found reply action, sending auto-reply...")
                // Send auto-reply
                sendAutoReply(replyAction, conversationId, sender, message, replyMessage)
            } else {
                Log.e(TAG, "❌ No reply action found in notification")
                
                // IMPROVEMENT: Try to find alternative ways to reply if direct reply action is missing
                val alternativeAction = findAlternativeReplyAction(notification)
                if (alternativeAction != null) {
                    Log.e(TAG, "🔄 Found alternative reply action, attempting to use it...")
                    sendAlternativeReply(alternativeAction, conversationId, sender, message, replyMessage)
                } else {
                    rcsManager.addLogEntry(sender, message, "Failed: No reply action available", false)
                }
            }
        } else {
            Log.e(TAG, "ℹ️ No auto-reply needed for this message")
        }
    }
    
    /**
     * Try to find an alternative way to reply if the standard reply action is missing
     */
    private fun findAlternativeReplyAction(notification: Notification): Notification.Action? {
        // Look for any action that contains "reply" in its title
        val replyAction = notification.actions?.firstOrNull { action ->
            action?.title?.toString()?.lowercase()?.contains("reply") == true ||
            action?.title?.toString()?.lowercase()?.contains("respond") == true
        }
        
        if (replyAction != null) {
            Log.e(TAG, "✅ Found alternative reply action: ${replyAction.title}")
            return replyAction
        }
        
        // Look for any action that has remote inputs (typical for reply actions)
        val actionWithRemoteInputs = notification.actions?.firstOrNull { action ->
            action?.remoteInputs?.isNotEmpty() == true
        }
        
        if (actionWithRemoteInputs != null) {
            Log.e(TAG, "✅ Found action with remote inputs: ${actionWithRemoteInputs.title}")
            return actionWithRemoteInputs
        }
        
        return null
    }
    
    /**
     * Send a reply using an alternative action
     */
    private fun sendAlternativeReply(
        action: Notification.Action,
        conversationId: String,
        sender: String,
        receivedMessage: String,
        replyMessage: String
    ) {
        try {
            // Same implementation as sendAutoReply but logged as alternative method
            Log.e(TAG, "🔄 Sending alternative auto-reply to $sender: $replyMessage")
            
            // Similar implementation to sendAutoReply
            sendAutoReply(action, conversationId, sender, receivedMessage, replyMessage)
            
            // Add "using alternative action" to the log entry
            rcsManager.addLogEntry(
                sender,
                receivedMessage,
                "$replyMessage (using alternative action)",
                true
            )
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error sending alternative auto-reply: ${e.message}")
            rcsManager.addLogEntry(
                sender,
                receivedMessage,
                "Failed: Error with alternative reply: ${e.message}",
                false
            )
        }
    }
    
    /**
     * Try to determine if this is an RCS message based on available information
     */
    private fun isRcsMessage(packageName: String, notification: Notification, extras: Bundle): Boolean {
        Log.e(TAG, "🔍 Analyzing if message is RCS...")
        
        // CRITICAL FIX: For Google Messages, assume most notifications are RCS-related
        // This is the most reliable approach given the inconsistent notification formats
        if (packageName == "com.google.android.apps.messaging" || 
            packageName == "com.google.android.apps.messaging.debug") {
            
            Log.e(TAG, "LOGTAG_RCS_DETAILS: ↘️ Checking Google Messages app for RCS indicators")
            
            // Auto-treat as RCS if this is from Google Messages and:
            // 1. Has any message text content, OR
            // 2. Has message-specific extras, OR 
            // 3. Has conversation ID extras
            
            val hasText = extras.getCharSequence(Notification.EXTRA_TEXT) != null
            val hasConversationExtras = extras.containsKey("extra_im_notification_conversation_id")
            val hasMessagingExtras = extras.containsKey("android.messages") ||
                                    extras.containsKey("android.messagingUser") ||
                                    extras.containsKey("android.messagingStyleUser")
            
            if (hasText || hasConversationExtras || hasMessagingExtras) {
                Log.e(TAG, "LOGTAG_RCS_DETAILS: ↘️ QUICK CHECK: Google Messages with content, treating as RCS")
                return true
            }
            
            // Continue with standard checks for thoroughness
            
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
            
            // FALLBACK: For Google Messages, assume it's RCS if it has any notification actions
            if (actions.isNotEmpty()) {
                Log.e(TAG, "LOGTAG_RCS_DETAILS: ↘️ FALLBACK: Google Messages with actions, treating as RCS")
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
    
    /**
     * Process a service category notification from Google Messages
     * These notifications often contain RCS message data but in a different format
     */
    private fun processServiceNotification(sbn: StatusBarNotification) {
        try {
            val notification = sbn.notification
            val extras = notification.extras
            
            Log.e(TAG, "LOGTAG_RCS_DETAILS: 📨📨📨 SERVICE NOTIFICATION DETAILS 📨📨📨")
            
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
            
            // Extract any potential message info
            val conversationId = extras.getString("extra_im_notification_conversation_id") 
                              ?: extras.getString("android.conversationId")
                              ?: sbn.key
                              
            // Try to find a sender name from various sources
            var sender = extras.getString(Notification.EXTRA_TITLE) 
                      ?: extras.getString(Notification.EXTRA_CONVERSATION_TITLE)
                      ?: "Unknown Sender"
                      
            // Try to extract the actual message content
            var messageText: String? = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
                                     ?: extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()
            
            // Try to extract from messaging-specific extras
            if (messageText == null && extras.containsKey("android.messages")) {
                try {
                    val messages = extras.getParcelableArray("android.messages")
                    if (messages != null && messages.isNotEmpty()) {
                        // Get the last (most recent) message
                        val lastMessage = messages.last()
                        
                        // Try to get sender info if available
                        try {
                            val personField = lastMessage.javaClass.getDeclaredField("mPerson")
                            personField.isAccessible = true
                            val person = personField.get(lastMessage)
                            
                            if (person != null) {
                                val nameField = person.javaClass.getDeclaredField("mName")
                                nameField.isAccessible = true
                                val name = nameField.get(person) as? CharSequence
                                if (name != null && name.isNotEmpty()) {
                                    sender = name.toString()
                                }
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "❌ Error extracting sender from message: ${e.message}")
                        }
                        
                        // Extract the message text
                        try {
                            val textField = lastMessage.javaClass.getDeclaredField("mText")
                            textField.isAccessible = true
                            messageText = (textField.get(lastMessage) as? CharSequence)?.toString()
                        } catch (e: Exception) {
                            Log.e(TAG, "❌ Error extracting text from message: ${e.message}")
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Error processing messages array: ${e.message}")
                }
            }
            
            // If we still don't have message text, check for other indicators
            if (messageText == null) {
                // Look for any text content in the notification
                notification.tickerText?.let {
                    messageText = it.toString()
                    Log.e(TAG, "🔍 Using ticker text as message: $messageText")
                }
            }
            
            // If we found message text, process it
            if (!messageText.isNullOrEmpty()) {
                Log.e(TAG, "LOGTAG_RCS_DETAILS: ↘️ Extracted from service notification - Sender: $sender, Message: $messageText")
                
                // Process using standard message handling logic
                processMessageNotification(sbn, notification, extras, sender, messageText)
            } else {
                Log.e(TAG, "❌ Could not extract message text from service notification")
                
                // Even without message text, try to check if there's a conversation that needs a reply
                if (conversationId != null) {
                    Log.e(TAG, "🔍 Found conversation ID but no message text, using placeholder")
                    processMessageNotification(sbn, notification, extras, sender, "New message notification")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error processing service notification: ${e.message}")
        }
    }
} 