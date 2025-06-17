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
import android.provider.Settings
import android.content.ComponentName
import com.facebook.react.ReactApplication
import com.facebook.react.bridge.ReactApplicationContext

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
    
    // Add at the beginning of the class after existing constant declarations
    private val ACTION_NOTIFICATION_LISTENER_SETTINGS = "android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"
    
    override fun onCreate() {
        super.onCreate()
        
        // Create RCS Auto-Reply Manager
        rcsManager = RcsAutoReplyManager(applicationContext)
        
        // Check if notification listener is enabled
        checkNotificationListenerEnabled()
        
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
            addAction("com.auto_sms.TEST_RCS_AUTO_REPLY")
        }
        
        applicationContext.registerReceiver(testCommandReceiver, filter)
        Log.e(TAG, "🔄 Registered test command receiver")
        
        // Register for notification listener changed broadcasts
        val notificationListenerFilter = IntentFilter()
        notificationListenerFilter.addAction("android.service.notification.NotificationListenerService")
        notificationListenerFilter.addAction("enabled_notification_listeners")
        applicationContext.registerReceiver(notificationListenerStatusReceiver, notificationListenerFilter)
        Log.e(TAG, "🔄 Registered notification listener status receiver")
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
                "com.auto_sms.TEST_RCS_AUTO_REPLY" -> {
                    Log.e(TAG, "🧪 Received test RCS auto-reply command")
                    val sender = intent.getStringExtra("sender") ?: "Test Sender"
                    val message = intent.getStringExtra("message") ?: "Test Message"
                    val forceDynamic = intent.getBooleanExtra("force_dynamic", false)
                    
                    Log.e(TAG, "🧪 Test parameters - Sender: $sender, Message: $message, Force Dynamic: $forceDynamic")
                    
                    if (forceDynamic) {
                        // Process with forced dynamic response
                        val dynamicResponse = rcsManager.forceDynamicMlcResponse(sender, message)
                        Log.e(TAG, "✅ Forced dynamic response: $dynamicResponse")
                        
                        // Log the response
                        rcsManager.addLogEntry(sender, message, dynamicResponse, true, true)
                    } else {
                        // Process the test message normally
                        processManualTestMessage(sender, message)
                    }
                }
            }
        }
    }
    
    // Add a broadcast receiver to monitor notification listener changes
    private val notificationListenerStatusReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            Log.e(TAG, "📣 Notification listener status change detected")
            checkNotificationListenerEnabled()
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
        
        // Unregister the notification listener status receiver
        try {
            applicationContext.unregisterReceiver(notificationListenerStatusReceiver)
            Log.e(TAG, "🔄 Unregistered notification listener status receiver")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error unregistering notification listener status receiver: ${e.message}")
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
        
        // Double check that we're actually enabled
        val enabled = checkNotificationListenerEnabled()
        if (enabled) {
            Log.e(TAG, "🔑 Notification listener permission confirmed")
        } else {
            Log.e(TAG, "⚠️ Warning: Listener connected but appears to be disabled in settings")
        }
        
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
            
            // Always dump debug data about the notification to help troubleshoot
            dumpNotificationInfo(sbn)
            
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
            val extras = notification.extras
            
            // CRITICAL FIX: For Google Messages, process ALL notifications regardless of category
            // This ensures we don't miss any RCS messages due to inconsistent notification formats
            if (packageName == "com.google.android.apps.messaging" || 
                packageName == "com.google.android.apps.messaging.debug") {
                
                Log.e(TAG, "🔍 Processing ALL notifications from Google Messages, category: ${notification.category}")
                processGoogleMessagesNotification(sbn)
                return
            }
            
            // For other messaging apps, continue with standard processing
            
            // IMPROVEMENT: Be more lenient with notification categories
            if (notification.category != Notification.CATEGORY_MESSAGE && 
                notification.category != Notification.CATEGORY_SOCIAL &&
                notification.category != null) { 
                
                Log.e(TAG, "❌ Ignoring non-message notification category: ${notification.category}")
                return
            }
            
            // Extract notification data
            val title = extras.getString(Notification.EXTRA_TITLE)
            val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
            
            // Enhanced logging with consistent tag for easier filtering
            Log.e(TAG, "LOGTAG_RCS_DETAILS: 📨📨📨 RCS MESSAGE DETAILS 📨📨📨")
            
            // CRITICAL FIX: Check for messaging-specific extras that might indicate an RCS message
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
     * Process ALL notifications from Google Messages app, using more aggressive detection
     * This ensures we don't miss RCS messages due to inconsistent notification formats
     */
    private fun processGoogleMessagesNotification(sbn: StatusBarNotification) {
        try {
            val notification = sbn.notification
            val extras = notification.extras
            
            Log.e(TAG, "LOGTAG_RCS_DETAILS: 📨📨📨 GOOGLE MESSAGES NOTIFICATION 📨📨📨")
            
            // Always assume Google Messages notifications are RCS-related unless proven otherwise
            var isRcsMessage = true
            
            // Try to extract sender and message from various possible locations
            var sender = extras.getString(Notification.EXTRA_TITLE) 
                      ?: extras.getString(Notification.EXTRA_CONVERSATION_TITLE)
                      ?: extras.getString(Notification.EXTRA_SUB_TEXT)
                      ?: "Unknown Sender"
            
            var message: String? = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
                        ?: extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()
                        ?: extras.getCharSequence(Notification.EXTRA_SUMMARY_TEXT)?.toString()
            
            Log.e(TAG, "LOGTAG_RCS_DETAILS: ↘️ Initial extraction - Sender: $sender, Message: $message")
            
            // If message is still null, try to extract from android.messages array
            if (message == null && extras.containsKey("android.messages")) {
                try {
                    val messages = extras.getParcelableArray("android.messages")
                    if (messages != null && messages.isNotEmpty()) {
                        // Try to get the last (most recent) message
                        val lastMessage = messages.last()
                        
                        // Try to get sender name if available
                        try {
                            val personField = lastMessage.javaClass.getDeclaredField("mPerson")
                            personField.isAccessible = true
                            val person = personField.get(lastMessage)
                            
                            if (person != null) {
                                val nameField = person.javaClass.getDeclaredField("mName")
                                nameField.isAccessible = true
                                val name = nameField.get(person) as? CharSequence
                                if (name != null && name.isNotEmpty()) {
                                    // If we already have a sender name, only replace if this one is better
                                    if (sender == "Unknown Sender" || name.toString().length > sender.length) {
                                        sender = name.toString()
                                        Log.e(TAG, "🔍 Extracted better sender name: $sender")
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "❌ Error extracting sender from message: ${e.message}")
                        }
                        
                        // Extract message text
                        try {
                            val textField = lastMessage.javaClass.getDeclaredField("mText")
                            textField.isAccessible = true
                            message = (textField.get(lastMessage) as? CharSequence)?.toString()
                            Log.e(TAG, "🔍 Extracted message text from android.messages: $message")
                        } catch (e: Exception) {
                            Log.e(TAG, "❌ Error extracting text from message: ${e.message}")
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Error processing messages array: ${e.message}")
                }
            }
            
            // If message is still null, try to use ticker text
            if (message == null) {
                notification.tickerText?.let {
                    message = it.toString()
                    Log.e(TAG, "🔍 Using ticker text as message: $message")
                }
            }
            
            // If message is STILL null, check if it's a summary notification
            if (message == null) {
                // Check if this might be a group summary notification
                val isGroupSummary = notification.flags and Notification.FLAG_GROUP_SUMMARY != 0
                
                if (isGroupSummary) {
                    Log.e(TAG, "ℹ️ This appears to be a group summary notification, not a message")
                    isRcsMessage = false
                } else {
                    // Last resort - use a placeholder
                    message = "New message notification (ID: ${sbn.id})"
                    Log.e(TAG, "⚠️ Using generic fallback text: $message")
                }
            }
            
            // Check for known non-message notifications from Google Messages
            val nonMessageTitles = listOf(
                "checking for new messages",
                "updating",
                "downloading",
                "chat features",
                "messages is running",
                "verifying",
                "connected"
            )
            
            val titleLower = sender.lowercase()
            if (nonMessageTitles.any { titleLower.contains(it) }) {
                Log.e(TAG, "ℹ️ This appears to be a status notification, not a message")
                isRcsMessage = false
            }
            
            Log.e(TAG, "LOGTAG_RCS_DETAILS: ↘️ Final extraction - Sender: $sender, Message: $message, Is RCS: $isRcsMessage")
            
            // If we have a message and it looks like an RCS message, process it
            if (message != null && isRcsMessage) {
                processMessageNotification(sbn, notification, extras, sender, message)
            } else {
                Log.e(TAG, "ℹ️ Google Messages notification doesn't appear to be an RCS message, ignoring")
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error processing Google Messages notification: ${e.message}")
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
            
            val hasText = extras.getCharSequence(Notification.EXTRA_TEXT) != null ||
                         extras.getCharSequence(Notification.EXTRA_BIG_TEXT) != null
            val hasConversationExtras = extras.containsKey("extra_im_notification_conversation_id") ||
                                       extras.containsKey("android.conversationId")
            val hasMessagingExtras = extras.containsKey("android.messages") ||
                                    extras.containsKey("android.messagingUser") ||
                                    extras.containsKey("android.messagingStyleUser")
            
            if (hasText || hasConversationExtras || hasMessagingExtras) {
                Log.e(TAG, "LOGTAG_RCS_DETAILS: ↘️ QUICK CHECK: Google Messages with content, treating as RCS")
                return true
            }
            
            // NEW: Check for remote inputs which typically indicate a message that can be replied to
            val hasReplyActions = notification.actions?.any { 
                it?.remoteInputs?.isNotEmpty() == true 
            } ?: false
            
            if (hasReplyActions) {
                Log.e(TAG, "LOGTAG_RCS_DETAILS: ↘️ QUICK CHECK: Message has reply actions, treating as RCS")
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
            
            // NEW: Check if this is a summary notification, which we can ignore
            val isGroupSummary = (notification.flags and Notification.FLAG_GROUP_SUMMARY) != 0
            if (isGroupSummary) {
                Log.e(TAG, "LOGTAG_RCS_DETAILS: ↘️ This appears to be a group summary notification")
                // Even for summaries, check if they have message content that should be processed
                if (!hasText && !hasMessages) {
                    return false
                }
            }
            
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
                action?.title?.toString()?.contains("Mark as read", ignoreCase = true) == true ||
                action?.title?.toString()?.contains("read", ignoreCase = true) == true
            }
            
            val hasReplyAction = actions.any { action ->
                action?.title?.toString()?.contains("Reply", ignoreCase = true) == true ||
                action?.title?.toString()?.contains("respond", ignoreCase = true) == true
            }
            
            Log.e(TAG, "LOGTAG_RCS_DETAILS: ↘️ Has Mark as read action: $hasMarkAsReadAction")
            Log.e(TAG, "LOGTAG_RCS_DETAILS: ↘️ Has Reply action: $hasReplyAction")
            
            if (hasMarkAsReadAction || hasReplyAction) {
                Log.e(TAG, "LOGTAG_RCS_DETAILS: ↘️ RESULT: Found typical RCS actions")
                return true
            }
            
            // FALLBACK: For Google Messages, assume it's RCS if it has any notification actions
            // that aren't just "dismiss" or standard system actions
            if (actions.isNotEmpty() && actions.size > 1) {
                Log.e(TAG, "LOGTAG_RCS_DETAILS: ↘️ FALLBACK: Google Messages with multiple actions, treating as RCS")
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
        
        // NEW: Check if the notification has a Category.MESSAGE or Category.SOCIAL
        if (notification.category == Notification.CATEGORY_MESSAGE || 
            notification.category == Notification.CATEGORY_SOCIAL) {
            Log.e(TAG, "✅ Message or social category found, treating as RCS")
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
    
    /**
     * More comprehensive check for static messages
     * This ensures we never send generic "unavailable" messages
     */
    private fun isStaticMessage(message: String): Boolean {
        // Common static messages to check for
        val staticPhrases = listOf(
            "I am busy",
            "I'm busy",
            "I'm currently unavailable",
            "I will respond as soon as possible",
            "I'll respond as soon as possible",
            "I will contact you",
            "I'll contact you",
            "please give me some time",
            "I'm not available",
            "I can't respond right now",
            "I will get back to you",
            "I'll get back to you"
        )
        
        // If message has no unique content (just generic phrases), consider it static
        val lowerMessage = message.lowercase()
        
        // Check if message contains any of the static phrases without specific context
        for (phrase in staticPhrases) {
            if (lowerMessage.contains(phrase.lowercase())) {
                // Look for unique content outside the static phrase
                val withoutPhrase = lowerMessage.replace(phrase.lowercase(), "")
                
                // If what remains is just generic text or punctuation, it's likely static
                val meaningfulContent = withoutPhrase.replace(Regex("[,.!?;:\\s]+"), "")
                if (meaningfulContent.length < 10) {
                    Log.e(TAG, "⚠️ Detected static message pattern: \"$phrase\" without sufficient unique content")
                    return true
                }
            }
        }
        
        // If message is very short and generic, consider it static
        if (message.length < 15 && !message.contains(Regex("[0-9]|\\?|\\\"")) && 
            !message.contains(Regex("\\b(you|your|we|our|I'll|I will|they|their)\\b", RegexOption.IGNORE_CASE))) {
            Log.e(TAG, "⚠️ Message too short and generic, likely static: \"$message\"")
            return true
        }
        
        return false
    }

    /**
     * Send auto-reply using MLC LLM with document context
     */
    private fun sendAutoReply(replyAction: Notification.Action, conversationId: String, sender: String, originalMessage: String, replyMessage: String) {
        try {
            Log.e(TAG, "🧠🧠🧠 START: SENDING MLC LLM AUTO-REPLY 🧠🧠🧠")
            Log.e(TAG, "   • To: $sender")
            Log.e(TAG, "   • Original: $originalMessage")
            Log.e(TAG, "   • Conversation ID: $conversationId")
            Log.e(TAG, "   • Reply: $replyMessage")
            
            // Create the remote input
            val remoteInputs = replyAction.remoteInputs
            if (remoteInputs.isEmpty()) {
                Log.e(TAG, "❌ No remote inputs found")
                rcsManager.addLogEntry(sender, originalMessage, "Failed: No remote inputs", false, false)
                Log.e(TAG, "🧠🧠🧠 END: MLC LLM AUTO-REPLY ABORTED - NO REMOTE INPUTS 🧠🧠🧠")
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
                Log.e(TAG, "📤 Sending MLC LLM auto-reply")
                replyAction.actionIntent.send(this, 0, resultIntent)
                Log.e(TAG, "✅✅✅ MLC LLM auto-reply sent successfully! ✅✅✅")
                
                // Log this auto-reply
                rcsManager.addLogEntry(sender, originalMessage, replyMessage, true, true) // Always mark as LLM
                Log.e(TAG, "🧠🧠🧠 END: MLC LLM AUTO-REPLY SENT SUCCESSFULLY 🧠🧠🧠")
            } catch (e: Exception) {
                Log.e(TAG, "❌❌❌ Failed to send MLC LLM auto-reply: ${e.message}", e)
                rcsManager.addLogEntry(sender, originalMessage, "Failed: ${e.message}", false, false)
                Log.e(TAG, "🧠🧠🧠 END: MLC LLM AUTO-REPLY FAILED 🧠🧠🧠")
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌❌❌ Error in sendAutoReply: ${e.message}", e)
            Log.e(TAG, "🧠🧠🧠 END: MLC LLM AUTO-REPLY ERROR 🧠🧠��")
        }
    }
    
    /**
     * Force a dynamic response from MLC LLM (used for testing)
     * This ensures we get a non-static response for RCS auto-replies
     */
    private fun forceDynamicResponse(sender: String, message: String): String {
        try {
            Log.e(TAG, "🧠🧠🧠 FORCE GENERATING DYNAMIC RCS RESPONSE 🧠🧠🧠")
            
            // Try to generate a document-based response from RcsAutoReplyManager first
            val documentResponse = rcsManager.forceDynamicMlcResponse(sender, message)
            if (documentResponse.isNotEmpty()) {
                Log.e(TAG, "✅ Successfully generated document-based response: $documentResponse")
                return documentResponse
            }
            
            // If that failed, try direct reflection approach
            try {
                // Try to use reflection directly rather than ReactApplication
                val callSmsModule = CallSmsModule(applicationContext as ReactApplicationContext)
                
                try {
                    Log.e(TAG, "📝 Using CallSmsModule.testLLM for direct LLM response")
                    
                    // Use reflection to call the testLLM method
                    val testLLMMethod = CallSmsModule::class.java.getDeclaredMethod(
                        "testLLM",
                        String::class.java
                    )
                    testLLMMethod.isAccessible = true
                    
                    // Craft a prompt that will force a dynamic document-based response
                    val prompt = "The following message may be asking about information in our documents: \"$message\". " +
                              "Please provide a helpful response that references any relevant document information. " +
                              "The response MUST mention something specific from the message. " +
                              "The message is from $sender."
                    
                    // Call the method directly
                    val result = testLLMMethod.invoke(callSmsModule, prompt)
                    if (result != null) {
                        val rawResponse = result as String
                        
                        // Clean the response to remove AI prefixes
                        val cleanedResponse = rawResponse.replace(Regex("^AI:\\s*", RegexOption.IGNORE_CASE), "")
                            .replace(Regex("Document \\d+: [^\n]+\n"), "")
                            .replace(Regex("Title: [^\n]+\n"), "")
                            .replace(Regex("Content: \\s*\n"), "")
                            .replace(Regex("\\s+"), " ")
                            .trim()
                        
                        if (cleanedResponse.isNotEmpty()) {
                            Log.e(TAG, "✅ Successfully generated dynamic response: $cleanedResponse")
                            return cleanedResponse
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Error calling testLLM: ${e.message}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error getting CallSmsModule: ${e.message}")
            }
            
            // If all LLM approaches failed, don't send a response
            Log.e(TAG, "❌❌❌ All LLM approaches failed, no response will be sent")
            return ""
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error generating forced dynamic response: ${e.message}")
            // Return empty string to prevent sending any message
            return ""
        }
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
    
    // Add a comprehensive debug method to dump all notification information
    /**
     * Dump complete notification information for debugging
     * This helps diagnose why some notifications aren't being properly detected
     */
    private fun dumpNotificationInfo(sbn: StatusBarNotification) {
        try {
            val notification = sbn.notification
            val extras = notification.extras
            
            Log.e(TAG, "📋📋📋 NOTIFICATION DEBUG INFO 📋📋📋")
            Log.e(TAG, "• Package: ${sbn.packageName}")
            Log.e(TAG, "• ID: ${sbn.id}")
            Log.e(TAG, "• Key: ${sbn.key}")
            Log.e(TAG, "• Tag: ${sbn.tag}")
            Log.e(TAG, "• Post Time: ${sbn.postTime}")
            Log.e(TAG, "• Category: ${notification.category}")
            Log.e(TAG, "• Group Key: ${sbn.groupKey}")
            Log.e(TAG, "• Flags: ${notification.flags}")
            Log.e(TAG, "• Is Group Summary: ${(notification.flags and Notification.FLAG_GROUP_SUMMARY) != 0}")
            
            // Log all extras for debugging
            Log.e(TAG, "• EXTRAS:")
            extras.keySet().forEach { key ->
                try {
                    val value = when (val v = extras.get(key)) {
                        null -> "null"
                        is CharSequence -> "\"${v}\""
                        is Bundle -> "Bundle with ${v.size()} items"
                        is Array<*> -> "Array with ${v.size} items"
                        else -> v.toString()
                    }
                    Log.e(TAG, "  - $key: $value")
                } catch (e: Exception) {
                    Log.e(TAG, "  - $key: [Error getting value: ${e.message}]")
                }
            }
            
            // Log all notification actions
            Log.e(TAG, "• ACTIONS:")
            notification.actions?.forEachIndexed { index, action ->
                Log.e(TAG, "  - Action $index: Title=\"${action?.title}\"")
                action?.remoteInputs?.forEachIndexed { inputIndex, remoteInput ->
                    Log.e(TAG, "    · RemoteInput $inputIndex: Key=\"${remoteInput.resultKey}\", Label=\"${remoteInput.label}\"")
                }
            } ?: Log.e(TAG, "  - No actions")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error dumping notification info: ${e.message}")
        }
    }
    
    /**
     * Check if this notification listener is enabled in system settings
     * This can help diagnose why notifications aren't being received
     */
    private fun checkNotificationListenerEnabled(): Boolean {
        val context = applicationContext
        val component = ComponentName(context, RcsNotificationListener::class.java)
        val packageManager = context.packageManager
        val packageName = context.packageName
        
        try {
            // Check if our notification listener service is enabled
            val enabledListeners = Settings.Secure.getString(
                context.contentResolver,
                "enabled_notification_listeners"
            )
            
            val enabled = enabledListeners?.contains(packageName) == true
            
            Log.e(TAG, "🔑🔑🔑 RCS NOTIFICATION LISTENER STATUS 🔑🔑🔑")
            Log.e(TAG, "   • Package: $packageName")
            Log.e(TAG, "   • Component: ${component.flattenToString()}")
            Log.e(TAG, "   • Enabled: $enabled")
            Log.e(TAG, "   • All enabled listeners: $enabledListeners")
            
            if (!enabled) {
                Log.e(TAG, "❌❌❌ CRITICAL: RcsNotificationListener is NOT enabled in system settings!")
                Log.e(TAG, "❌❌❌ This is why RCS auto-replies are not working!")
                Log.e(TAG, "ℹ️ User needs to enable notification access in Settings > Apps > Special access > Notification access")
                
                // Add code to request notification access if needed
            }
            
            return enabled
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error checking notification listener status: ${e.message}")
            return false
        }
    }

    // Add this method to the class for manual testing
    /**
     * For testing: Manually process a simulated RCS message
     * This can be called from LogTestModule to test RCS auto-replies
     */
    fun processManualTestMessage(sender: String, message: String): Boolean {
        try {
            Log.e(TAG, "🧪🧪🧪 MANUAL TEST: Processing simulated RCS message 🧪🧪🧪")
            Log.e(TAG, "   • Sender: $sender")
            Log.e(TAG, "   • Message: $message")
            
            // Check if RCS auto-reply is enabled
            if (!rcsManager.isEnabled()) {
                Log.e(TAG, "❌ RCS auto-reply is disabled, cannot process test message")
                return false
            }
            
            // Check if notification listener is enabled
            if (!checkNotificationListenerEnabled()) {
                Log.e(TAG, "⚠️ Warning: Processing test message but notification listener appears to be disabled")
                // Continue anyway for testing
            }
            
            // Process the message with rule engine
            val replyMessage = rcsManager.processMessage(sender, message)
            
            if (replyMessage != null) {
                Log.e(TAG, "LOGTAG_RCS_DETAILS: ↘️ Auto-reply generated: $replyMessage")
                Log.e(TAG, "✅ Test successful - would send: $replyMessage")
                return true
            } else {
                Log.e(TAG, "ℹ️ No auto-reply needed for this test message")
                return false
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error processing manual test message: ${e.message}")
            return false
        }
    }
} 