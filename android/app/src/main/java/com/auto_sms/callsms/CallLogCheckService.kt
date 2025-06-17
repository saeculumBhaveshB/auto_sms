package com.auto_sms.callsms

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.os.Handler
import android.os.Looper
import android.provider.CallLog
import android.telephony.SmsManager
import android.util.Log
import android.content.Context
import android.content.SharedPreferences
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.os.Build
import kotlin.math.max

/**
 * Service to check for missed calls and send SMS automatically
 * This service will run as a foreground service to ensure it works even when the app is killed
 */
class CallLogCheckService : Service() {
    private val TAG = "CallLogCheckService"
    private val NOTIFICATION_ID = 1001
    
    private val handler = Handler(Looper.getMainLooper())
    private val checkInterval = 30000L // 30 seconds
    
    // Constants
    private val DEFAULT_MESSAGE = "Thanks for your call. I'll respond to your message as soon as possible. (ID: AUTO)"
    private val AUTO_SMS_ENABLED_KEY = "@AutoSMS:Enabled"
    private val AI_SMS_ENABLED_KEY = "@AutoSMS:AIEnabled"
    private val AUTO_REPLY_ENABLED_KEY = "@AutoSMS:AutoReplyEnabled"
    private val INITIAL_SMS_MESSAGE_KEY = "@AutoSMS:InitialMessage"
    private val SMS_HISTORY_STORAGE_KEY = "@AutoSMS:SmsHistory"
    private val LAST_CHECK_TIME_KEY = "last_call_log_check_time"
    private val MISSED_CALL_NUMBERS_KEY = "missedCallNumbers"
    
    // SharedPreferences to store state
    private lateinit var sharedPrefs: SharedPreferences
    
    override fun onCreate() {
        super.onCreate()
        sharedPrefs = getSharedPreferences("AutoSmsPrefs", Context.MODE_PRIVATE)
        startForeground()
        Log.d(TAG, "CallLogCheckService created")
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "Service started or restarted")
        
        if (!isAutoSmsEnabled()) {
            Log.d(TAG, "Auto SMS is disabled, stopping service")
            stopSelf()
            return START_NOT_STICKY
        }
        
        // Set last check time to current time on service start to avoid processing old missed calls
        // Only do this if last check time isn't already set (first run)
        if (sharedPrefs.getLong(LAST_CHECK_TIME_KEY, 0L) == 0L) {
            sharedPrefs.edit().putLong(LAST_CHECK_TIME_KEY, System.currentTimeMillis()).apply()
            Log.d(TAG, "Initialized last check time to current time to avoid processing old calls")
        }
        
        // Start periodic call log checking
        scheduleCallLogCheck()
        
        // Return sticky to restart service if killed
        return START_STICKY
    }
    
    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
    
    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
        Log.d(TAG, "CallLogCheckService destroyed")
    }
    
    /**
     * Start as a foreground service with notification
     */
    private fun startForeground() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channelId = createNotificationChannel()
            val notification = Notification.Builder(this, channelId)
                .setContentTitle("Auto SMS Service")
                .setContentText("Monitoring for missed calls")
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .build()
            startForeground(NOTIFICATION_ID, notification)
        } else {
            // For older Android versions
            @Suppress("DEPRECATION")
            val notification = Notification.Builder(this)
                .setContentTitle("Auto SMS Service")
                .setContentText("Monitoring for missed calls")
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .build()
            startForeground(NOTIFICATION_ID, notification)
        }
    }
    
    /**
     * Create notification channel for Android 8.0+
     */
    private fun createNotificationChannel(): String {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channelId = "auto_sms_channel"
            val channelName = "Auto SMS Service"
            val channel = NotificationChannel(
                channelId,
                channelName,
                NotificationManager.IMPORTANCE_LOW
            )
            channel.description = "Used to monitor missed calls"
            channel.setShowBadge(false)
            
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
            return channelId
        }
        return ""
    }
    
    /**
     * Schedule regular call log checks
     */
    private fun scheduleCallLogCheck() {
        // First check after a delay to avoid processing at startup
        handler.postDelayed({
            checkCallLog()
        }, 10000)  // 10 second delay before first check
        
        // Then schedule periodic checks
        handler.postDelayed(object : Runnable {
            override fun run() {
                if (isAutoSmsEnabled()) {
                    checkCallLog()
                    handler.postDelayed(this, checkInterval)
                } else {
                    Log.d(TAG, "Auto SMS disabled, stopping scheduled checks")
                }
            }
        }, checkInterval)
    }
    
    /**
     * Check call log for missed calls
     */
    private fun checkCallLog() {
        if (!isAutoSmsEnabled()) {
            return
        }
        
        try {
            val lastCheckTime = sharedPrefs.getLong(LAST_CHECK_TIME_KEY, 0L)
            val currentTime = System.currentTimeMillis()
            
            // Safety check: if this is the first check, don't process old calls
            if (lastCheckTime == 0L) {
                Log.d(TAG, "First call log check, setting initial timestamp and skipping processing")
                sharedPrefs.edit().putLong(LAST_CHECK_TIME_KEY, currentTime).apply()
                return
            }
            
            // Only check for very recent calls (last 5 minutes max)
            val fiveMinutesAgo = currentTime - (5 * 60 * 1000)
            val effectiveLastCheckTime = max(lastCheckTime, fiveMinutesAgo)
            
            Log.d(TAG, "Checking for missed calls since $effectiveLastCheckTime (last check time was $lastCheckTime)")
            
            // Query call log for missed calls since last check
            val projection = arrayOf(
                CallLog.Calls.NUMBER,
                CallLog.Calls.TYPE,
                CallLog.Calls.DATE
            )
            
            val selection = "${CallLog.Calls.TYPE} = ? AND ${CallLog.Calls.DATE} > ?"
            val selectionArgs = arrayOf(
                CallLog.Calls.MISSED_TYPE.toString(),
                effectiveLastCheckTime.toString()
            )
            
            val sortOrder = "${CallLog.Calls.DATE} DESC"
            
            contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                sortOrder
            )?.use { cursor ->
                val numberIndex = cursor.getColumnIndex(CallLog.Calls.NUMBER)
                val dateIndex = cursor.getColumnIndex(CallLog.Calls.DATE)
                
                val missedCallsCount = cursor.count
                Log.d(TAG, "Found $missedCallsCount missed calls since last check")
                
                while (cursor.moveToNext()) {
                    val number = cursor.getString(numberIndex)
                    val date = cursor.getLong(dateIndex)
                    
                    if (!hasBeenHandled(number, date)) {
                        Log.d(TAG, "Found unhandled missed call from $number at $date")
                        sendSmsForMissedCall(number)
                        markCallAsHandled(number, date)
                    } else {
                        Log.d(TAG, "Call from $number at $date already handled, skipping")
                    }
                }
            }
            
            // Update last check time
            sharedPrefs.edit().putLong(LAST_CHECK_TIME_KEY, currentTime).apply()
            
        } catch (e: Exception) {
            Log.e(TAG, "Error checking call log: ${e.message}", e)
        }
    }
    
    /**
     * Check if a call has already been handled
     */
    private fun hasBeenHandled(phoneNumber: String, timestamp: Long): Boolean {
        val handledCalls = sharedPrefs.getStringSet("handled_missed_calls", HashSet()) ?: HashSet()
        return handledCalls.contains("$phoneNumber:$timestamp")
    }
    
    /**
     * Mark a call as handled to avoid duplicate messages
     */
    private fun markCallAsHandled(phoneNumber: String, timestamp: Long) {
        val handledCalls = sharedPrefs.getStringSet("handled_missed_calls", HashSet()) ?: HashSet()
        val newHandledCalls = HashSet(handledCalls)
        newHandledCalls.add("$phoneNumber:$timestamp")
        
        sharedPrefs.edit().putStringSet("handled_missed_calls", newHandledCalls).apply()
    }
    
    /**
     * Send SMS for a missed call
     */
    private fun sendSmsForMissedCall(phoneNumber: String) {
        try {
            // Check if AI mode is enabled
            val aiEnabled = sharedPrefs.getBoolean(AI_SMS_ENABLED_KEY, false)
            
            // Get the appropriate message
            val message = 
                sharedPrefs.getString(INITIAL_SMS_MESSAGE_KEY, "Thanks for your call. I'll respond to your specific query as soon as possible. (ID: AUTO)") ?: DEFAULT_MESSAGE
            
            val smsManager = SmsManager.getDefault()
            
            // Add FLAG_IMMUTABLE for Android 12+ compatibility
            val pendingIntentFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }
            
            // Prepare PendingIntent for SMS
            val sentIntent = Intent("android.provider.Telephony.SMS_SENT")
            val sentPI = PendingIntent.getBroadcast(this, 0, sentIntent, pendingIntentFlags)
            
            // Split message if it's too long
            val parts = smsManager.divideMessage(message)
            
            // Send SMS
            if (parts.size > 1) {
                // Create PendingIntent array for multipart SMS
                val sentIntents = ArrayList<PendingIntent>().apply {
                    repeat(parts.size) { i ->
                        add(PendingIntent.getBroadcast(this@CallLogCheckService, i, sentIntent, pendingIntentFlags))
                    }
                }
                smsManager.sendMultipartTextMessage(phoneNumber, null, parts, sentIntents, null)
            } else {
                smsManager.sendTextMessage(phoneNumber, null, message, sentPI, null)
            }
            
            Log.d(TAG, "SMS sent successfully to missed call from $phoneNumber")
            
            // Store the number for potential auto-reply
            storeMissedCallNumber(phoneNumber)
            
            // Save to history
            saveSmsToHistory(phoneNumber, message, true)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error sending SMS for missed call: ${e.message}", e)
            saveSmsToHistory(phoneNumber, DEFAULT_MESSAGE, false, e.message)
        }
    }
    
    /**
     * Store phone number that we sent a missed call SMS to for later auto-reply
     */
    private fun storeMissedCallNumber(phoneNumber: String) {
        try {
            val missedCallNumbers = sharedPrefs.getStringSet(MISSED_CALL_NUMBERS_KEY, HashSet()) ?: HashSet()
            
            // Add timestamp to track when the missed call happened
            val newMissedCallNumbers = HashSet(missedCallNumbers)
            newMissedCallNumbers.add("$phoneNumber:${System.currentTimeMillis()}")
            
            sharedPrefs.edit().putStringSet(MISSED_CALL_NUMBERS_KEY, newMissedCallNumbers).apply()
            Log.d(TAG, "Stored missed call number for auto-reply: $phoneNumber")
        } catch (e: Exception) {
            Log.e(TAG, "Error storing missed call number: ${e.message}", e)
        }
    }
    
    /**
     * Save SMS to history
     */
    private fun saveSmsToHistory(
        phoneNumber: String, 
        message: String, 
        success: Boolean, 
        errorMsg: String? = null
    ) {
        try {
            val historyJson = sharedPrefs.getString(SMS_HISTORY_STORAGE_KEY, "[]") ?: "[]"
            
            // Create new history entry
            val timestamp = System.currentTimeMillis()
            val errorPart = if (errorMsg != null) ", \"error\": \"${errorMsg.replace("\"", "\\\"")}\"" else ""
            val newEntryJson = """
                {
                    "id": "$phoneNumber-$timestamp",
                    "phoneNumber": "$phoneNumber",
                    "message": "${message.replace("\"", "\\\"")}",
                    "status": "${if (success) "SENT" else "FAILED"}",
                    "timestamp": $timestamp$errorPart
                }
            """.trimIndent()
            
            // Add to existing history (simple approach - prepend)
            val updatedHistoryJson = if (historyJson == "[]") {
                "[$newEntryJson]"
            } else {
                "[${newEntryJson},${historyJson.substring(1)}"
            }
            
            // Save updated history
            sharedPrefs.edit().putString(SMS_HISTORY_STORAGE_KEY, updatedHistoryJson).apply()
            
        } catch (e: Exception) {
            Log.e(TAG, "Error saving SMS to history: ${e.message}", e)
        }
    }
    
    /**
     * Check if Auto SMS is enabled
     */
    private fun isAutoSmsEnabled(): Boolean {
        return sharedPrefs.getBoolean(AUTO_SMS_ENABLED_KEY, true)
    }
} 