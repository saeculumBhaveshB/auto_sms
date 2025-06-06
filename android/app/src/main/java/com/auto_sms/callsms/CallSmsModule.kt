package com.auto_sms.callsms

import android.Manifest
import android.app.Activity
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.provider.CallLog
import android.provider.Telephony
import android.telephony.SmsManager
import android.telephony.TelephonyManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.facebook.react.bridge.*
import com.facebook.react.modules.core.DeviceEventManagerModule
import java.util.*
import java.text.SimpleDateFormat
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

class CallSmsModule(reactContext: ReactApplicationContext) :
    ReactContextBaseJavaModule(reactContext) {

    private val TAG = "CallSmsModule"
    private var callReceiver: BroadcastReceiver? = null
    private var isMonitoringCalls = false
    private val DEFAULT_MESSAGE = "I am busy, please give me some time, I will contact you."
    private val scheduler: ScheduledExecutorService = Executors.newScheduledThreadPool(1)
    private var lastPhoneState = TelephonyManager.CALL_STATE_IDLE
    private var latestIncomingNumber: String? = null
    private var callStart: Long = 0L
    private var missedCallDetectedAt: Long = 0L
    
    override fun getName(): String {
        return "CallSmsModule"
    }

    private fun hasRequiredPermissions(): Boolean {
        val context = reactApplicationContext
        val readCallLog = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALL_LOG) == PackageManager.PERMISSION_GRANTED
        val readPhoneState = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED
        val sendSms = ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED

        return readCallLog && readPhoneState && sendSms
    }

    @ReactMethod
    fun startMonitoringCalls(promise: Promise) {
        if (!hasRequiredPermissions()) {
            val missingPermissions = getMissingPermissions()
            Log.e(TAG, "Missing permissions for call monitoring: $missingPermissions")
            promise.reject(
                "PERMISSIONS_DENIED", 
                "Missing permissions for call monitoring. Please grant all required permissions (Call Log, Phone State, SMS) in the Permissions screen."
            )
            return
        }

        if (isMonitoringCalls) {
            promise.resolve(true)
            return
        }

        try {
            // Reset call state tracking variables to prevent false positives
            lastPhoneState = TelephonyManager.CALL_STATE_IDLE
            latestIncomingNumber = null
            callStart = 0L
            missedCallDetectedAt = System.currentTimeMillis()
            
            // Start our foreground service for persistent monitoring
            val serviceIntent = Intent(reactApplicationContext, CallLogCheckService::class.java)
            
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    reactApplicationContext.startForegroundService(serviceIntent)
                } else {
                    reactApplicationContext.startService(serviceIntent)
                }
                Log.d(TAG, "CallLogCheckService started successfully")
            } catch (e: Exception) {
                Log.e(TAG, "Error starting service: ${e.message}", e)
                // Continue with receiver registration even if service start fails
            }
            
            // Register broadcast receiver for immediate response
            val intentFilter = IntentFilter()
            intentFilter.addAction(TelephonyManager.ACTION_PHONE_STATE_CHANGED)
            intentFilter.addAction("android.provider.Telephony.SMS_DELIVERED")
            intentFilter.addAction("android.provider.Telephony.SMS_SENT")
            
            if (callReceiver == null) {
                callReceiver = object : BroadcastReceiver() {
                    override fun onReceive(context: Context, intent: Intent) {
                        if (intent.action == TelephonyManager.ACTION_PHONE_STATE_CHANGED) {
                            handleCallStateChanged(intent)
                        } else if (intent.action == "android.provider.Telephony.SMS_DELIVERED") {
                            handleSmsDelivered(intent)
                        } else if (intent.action == "android.provider.Telephony.SMS_SENT") {
                            handleSmsSent(intent)
                        }
                    }
                }
            }
            
            reactApplicationContext.registerReceiver(callReceiver, intentFilter)
            isMonitoringCalls = true
            
            // Register SMS receiver for AI responses
            registerSmsReceiver()
            
            // Save monitoring state to SharedPreferences
            saveMonitoringStatus(true)
            
            promise.resolve(true)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error starting call monitoring: ${e.message}")
            promise.reject("START_MONITORING_ERROR", "Failed to start monitoring calls: ${e.message}")
        }
    }

    @ReactMethod
    fun stopMonitoringCalls(promise: Promise) {
        if (!isMonitoringCalls) {
            promise.resolve(true)
            return
        }

        try {
            // Unregister receiver
            callReceiver?.let {
                try {
                    reactApplicationContext.unregisterReceiver(it)
                } catch (e: Exception) {
                    Log.e(TAG, "Error unregistering receiver: ${e.message}")
                }
                callReceiver = null
            }
            
            // Stop the service
            val serviceIntent = Intent(reactApplicationContext, CallLogCheckService::class.java)
            reactApplicationContext.stopService(serviceIntent)
            
            isMonitoringCalls = false
            
            // Save monitoring state to SharedPreferences
            saveMonitoringStatus(false)
            
            promise.resolve(true)
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping call monitoring: ${e.message}")
            promise.reject("STOP_MONITORING_ERROR", "Failed to stop monitoring calls: ${e.message}")
        }
    }
    
    /**
     * Save the monitoring status to SharedPreferences
     */
    private fun saveMonitoringStatus(isMonitoring: Boolean) {
        try {
            val sharedPrefs = reactApplicationContext.getSharedPreferences("AutoSmsPrefs", Context.MODE_PRIVATE)
            sharedPrefs.edit().putBoolean("isMonitoringActive", isMonitoring).apply()
        } catch (e: Exception) {
            Log.e(TAG, "Error saving monitoring status: ${e.message}")
        }
    }
    
    @ReactMethod
    fun setAutoSmsEnabled(enabled: Boolean, promise: Promise) {
        try {
            // Save setting to SharedPreferences for use when app is killed
            val sharedPrefs = reactApplicationContext.getSharedPreferences("AutoSmsPrefs", Context.MODE_PRIVATE)
            sharedPrefs.edit().putBoolean("@AutoSMS:Enabled", enabled).apply()
            
            if (enabled) {
                if (hasRequiredPermissions()) {
                    startMonitoringCalls(promise)
                } else {
                    promise.resolve(false)
                }
            } else {
                stopMonitoringCalls(promise)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error setting auto SMS enabled: ${e.message}")
            promise.reject("SET_AUTO_SMS_ERROR", "Failed to set auto SMS enabled: ${e.message}")
        }
    }
    
    @ReactMethod
    fun isAutoSmsEnabled(promise: Promise) {
        try {
            val sharedPrefs = reactApplicationContext.getSharedPreferences("AutoSmsPrefs", Context.MODE_PRIVATE)
            val enabled = sharedPrefs.getBoolean("@AutoSMS:Enabled", true)
            promise.resolve(enabled)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting auto SMS enabled: ${e.message}")
            promise.reject("GET_AUTO_SMS_ERROR", "Failed to get auto SMS enabled: ${e.message}")
        }
    }

    private fun handleCallStateChanged(intent: Intent) {
        val stateStr = intent.getStringExtra(TelephonyManager.EXTRA_STATE)
        val phoneNumber = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER)
        
        when (stateStr) {
            TelephonyManager.EXTRA_STATE_RINGING -> {
                if (phoneNumber != null) {
                    Log.d(TAG, "Incoming call from: $phoneNumber")
                    latestIncomingNumber = phoneNumber
                    callStart = System.currentTimeMillis()
                    lastPhoneState = TelephonyManager.CALL_STATE_RINGING
                }
            }
            TelephonyManager.EXTRA_STATE_OFFHOOK -> {
                lastPhoneState = TelephonyManager.CALL_STATE_OFFHOOK
            }
            TelephonyManager.EXTRA_STATE_IDLE -> {
                if (lastPhoneState == TelephonyManager.CALL_STATE_RINGING && latestIncomingNumber != null && callStart > 0) {
                    // This indicates a missed call
                    val missedCallDuration = System.currentTimeMillis() - callStart
                    Log.d(TAG, "Missed call detected from: $latestIncomingNumber, duration: $missedCallDuration ms")
                    
                    // For reliability, let's check the call log a bit later to confirm it was missed
                    missedCallDetectedAt = System.currentTimeMillis()
                    sendSmsForMissedCall(latestIncomingNumber!!)
                }
                lastPhoneState = TelephonyManager.CALL_STATE_IDLE
            }
        }
    }

    private fun startCheckingMissedCalls() {
        // Check call log every minute in case we missed the broadcast
        scheduler.scheduleAtFixedRate({
            checkForRecentMissedCalls()
        }, 1, 1, TimeUnit.MINUTES)
    }

    private fun checkForRecentMissedCalls() {
        if (!hasRequiredPermissions()) {
            return
        }

        try {
            val projection = arrayOf(
                CallLog.Calls.NUMBER,
                CallLog.Calls.TYPE,
                CallLog.Calls.DATE
            )
            
            val selection = "${CallLog.Calls.TYPE} = ${CallLog.Calls.MISSED_TYPE}"
            val sortOrder = "${CallLog.Calls.DATE} DESC"
            
            val uri = CallLog.Calls.CONTENT_URI
            val cursor = reactApplicationContext.contentResolver.query(
                uri,
                projection,
                selection,
                null,
                sortOrder
            )
            
            cursor?.use {
                if (it.moveToFirst()) {
                    val numberIndex = it.getColumnIndex(CallLog.Calls.NUMBER)
                    val dateIndex = it.getColumnIndex(CallLog.Calls.DATE)
                    
                    val number = it.getString(numberIndex)
                    val date = it.getLong(dateIndex)
                    
                    // Only consider missed calls from the last 5 minutes
                    val fiveMinutesAgo = System.currentTimeMillis() - 5 * 60 * 1000
                    
                    // Only process missed calls that:
                    // 1. Are recent (within last 5 minutes)
                    // 2. Happened after we started monitoring (missedCallDetectedAt is set when monitoring starts)
                    // 3. Are newer than any missed calls we've already processed
                    if (date > fiveMinutesAgo && date > missedCallDetectedAt && missedCallDetectedAt > 0) {
                        Log.d(TAG, "Found recent missed call from call log: $number at $date")
                        // Send SMS for this missed call
                        sendSmsForMissedCall(number)
                    } else {
                        Log.d(TAG, "Skipping missed call processing: call at $date, monitored since $missedCallDetectedAt")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking call log: ${e.message}", e)
        }
    }

    @ReactMethod
    fun sendSms(phoneNumber: String, message: String, promise: Promise) {
        if (!hasRequiredPermissions()) {
            val missingPermissions = getMissingPermissions()
            Log.e(TAG, "Missing permissions for sending SMS: $missingPermissions")
            promise.reject(
                "PERMISSIONS_DENIED", 
                "Cannot send SMS: Missing required permissions. Please grant all permissions first."
            )
            return
        }

        try {
            val smsMessage = if (message.isEmpty()) DEFAULT_MESSAGE else message
            val smsManager = SmsManager.getDefault()
            
            // Split message if it's too long
            val parts = smsManager.divideMessage(smsMessage)
            
            val sentIntent = Intent("android.provider.Telephony.SMS_SENT")
            
            // Add FLAG_IMMUTABLE for Android 12+ compatibility
            val pendingIntentFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }
            val sentPI = PendingIntent.getBroadcast(reactApplicationContext, 0, sentIntent, pendingIntentFlags)
            
            // Send SMS
            if (parts.size > 1) {
                // Create PendingIntent array for multipart SMS
                val sentIntents = ArrayList<PendingIntent>().apply {
                    repeat(parts.size) { i ->
                        add(PendingIntent.getBroadcast(reactApplicationContext, i, sentIntent, pendingIntentFlags))
                    }
                }
                smsManager.sendMultipartTextMessage(phoneNumber, null, parts, sentIntents, null)
            } else {
                smsManager.sendTextMessage(phoneNumber, null, smsMessage, sentPI, null)
            }
            
            // Create data for event
            val eventData = Arguments.createMap().apply {
                putString("phoneNumber", phoneNumber)
                putString("message", smsMessage)
                putString("status", "SENT")
                putDouble("timestamp", System.currentTimeMillis().toDouble())
            }
            
            // Emit event
            sendEvent("onSmsSent", eventData)
            
            promise.resolve(true)
        } catch (e: Exception) {
            Log.e(TAG, "Error sending SMS: ${e.message}", e)
            
            // Create data for event
            val eventData = Arguments.createMap().apply {
                putString("phoneNumber", phoneNumber)
                putString("message", message)
                putString("status", "FAILED")
                putString("error", e.message ?: "Unknown error")
                putDouble("timestamp", System.currentTimeMillis().toDouble())
            }
            
            // Emit event
            sendEvent("onSmsError", eventData)
            
            promise.reject("SEND_SMS_ERROR", "Failed to send SMS: ${e.message}")
        }
    }

    private fun sendSmsForMissedCall(phoneNumber: String) {
        // Only proceed if this is a genuine missed call (not during initialization)
        // Check if the missed call detection happened during normal operation
        if (lastPhoneState != TelephonyManager.CALL_STATE_RINGING || callStart == 0L) {
            Log.d(TAG, "Skipping auto-SMS for $phoneNumber - not a confirmed missed call")
            return
        }
        
        // We'll use the default message for missed calls
        try {
            val smsMessage = DEFAULT_MESSAGE
            val smsManager = SmsManager.getDefault()
            
            // Split message if it's too long
            val parts = smsManager.divideMessage(smsMessage)
            
            // Add FLAG_IMMUTABLE for Android 12+ compatibility
            val pendingIntentFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }
            
            // Prepare PendingIntent for SMS
            val sentIntent = Intent("android.provider.Telephony.SMS_SENT")
            val sentPI = PendingIntent.getBroadcast(reactApplicationContext, 0, sentIntent, pendingIntentFlags)
            
            Log.d(TAG, "Sending SMS to $phoneNumber for a missed call")
            
            // Send SMS
            if (parts.size > 1) {
                // Create PendingIntent array for multipart SMS
                val sentIntents = ArrayList<PendingIntent>().apply {
                    repeat(parts.size) { i ->
                        add(PendingIntent.getBroadcast(reactApplicationContext, i, sentIntent, pendingIntentFlags))
                    }
                }
                smsManager.sendMultipartTextMessage(phoneNumber, null, parts, sentIntents, null)
            } else {
                smsManager.sendTextMessage(phoneNumber, null, smsMessage, sentPI, null)
            }
            
            // Create data for event
            val eventData = Arguments.createMap().apply {
                putString("phoneNumber", phoneNumber)
                putString("message", smsMessage)
                putString("status", "SENT")
                putDouble("timestamp", System.currentTimeMillis().toDouble())
            }
            
            // Emit event
            sendEvent("onSmsSent", eventData)
            
            Log.d(TAG, "SMS sent successfully to missed call from $phoneNumber")
        } catch (e: Exception) {
            Log.e(TAG, "Error sending SMS for missed call: ${e.message}")
            
            // Create data for event
            val eventData = Arguments.createMap().apply {
                putString("phoneNumber", phoneNumber)
                putString("message", DEFAULT_MESSAGE)
                putString("status", "FAILED")
                putString("error", e.message ?: "Unknown error")
                putDouble("timestamp", System.currentTimeMillis().toDouble())
            }
            
            // Emit event
            sendEvent("onSmsError", eventData)
        }
    }

    private fun handleSmsSent(intent: Intent) {
        when (intent.extras?.getInt("resultCode", -1) ?: -1) {
            Activity.RESULT_OK -> {
                Log.d(TAG, "SMS sent successfully")
            }
            SmsManager.RESULT_ERROR_GENERIC_FAILURE -> {
                Log.e(TAG, "Generic failure")
            }
            SmsManager.RESULT_ERROR_NO_SERVICE -> {
                Log.e(TAG, "No service")
            }
            SmsManager.RESULT_ERROR_NULL_PDU -> {
                Log.e(TAG, "Null PDU")
            }
            SmsManager.RESULT_ERROR_RADIO_OFF -> {
                Log.e(TAG, "Radio off")
            }
        }
    }

    private fun handleSmsDelivered(intent: Intent) {
        when (intent.extras?.getInt("resultCode", -1) ?: -1) {
            Activity.RESULT_OK -> {
                Log.d(TAG, "SMS delivered successfully")
            }
            Activity.RESULT_CANCELED -> {
                Log.e(TAG, "SMS not delivered")
            }
        }
    }

    @ReactMethod
    fun getRecentCalls(days: Int, promise: Promise) {
        if (!hasRequiredPermissions()) {
            val missingPermissions = getMissingPermissions()
            Log.e(TAG, "Missing permissions for getting call log: $missingPermissions")
            promise.reject(
                "PERMISSIONS_DENIED", 
                "Cannot access call log: Missing required permissions. Please grant all permissions first."
            )
            return
        }

        try {
            val projection = arrayOf(
                CallLog.Calls.NUMBER,
                CallLog.Calls.TYPE,
                CallLog.Calls.DATE,
                CallLog.Calls.DURATION
            )
            
            val daysAgo = System.currentTimeMillis() - (days * 24 * 60 * 60 * 1000)
            val selection = "${CallLog.Calls.DATE} > ?"
            val selectionArgs = arrayOf(daysAgo.toString())
            val sortOrder = "${CallLog.Calls.DATE} DESC"
            
            val uri = CallLog.Calls.CONTENT_URI
            val cursor = reactApplicationContext.contentResolver.query(
                uri,
                projection,
                selection,
                selectionArgs,
                sortOrder
            )
            
            val calls = Arguments.createArray()
            
            cursor?.use {
                while (it.moveToNext()) {
                    val numberIndex = it.getColumnIndex(CallLog.Calls.NUMBER)
                    val typeIndex = it.getColumnIndex(CallLog.Calls.TYPE)
                    val dateIndex = it.getColumnIndex(CallLog.Calls.DATE)
                    val durationIndex = it.getColumnIndex(CallLog.Calls.DURATION)
                    
                    val call = Arguments.createMap().apply {
                        putString("phoneNumber", it.getString(numberIndex))
                        putInt("type", it.getInt(typeIndex))
                        putDouble("date", it.getLong(dateIndex).toDouble())
                        putInt("duration", it.getInt(durationIndex))
                    }
                    
                    calls.pushMap(call)
                }
            }
            
            promise.resolve(calls)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting recent calls: ${e.message}", e)
            promise.reject("GET_RECENT_CALLS_ERROR", "Failed to get recent calls: ${e.message}")
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

    private fun sendEvent(eventName: String, params: WritableMap?) {
        reactApplicationContext
            .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter::class.java)
            .emit(eventName, params)
    }

    private fun getMissingPermissions(): String {
        val context = reactApplicationContext
        val missingPermissions = mutableListOf<String>()
        
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALL_LOG) != PackageManager.PERMISSION_GRANTED) {
            missingPermissions.add("READ_CALL_LOG")
        }
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
            missingPermissions.add("READ_PHONE_STATE")
        }
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) {
            missingPermissions.add("SEND_SMS")
        }
        
        return missingPermissions.joinToString(", ")
    }

    @ReactMethod
    fun getSmsHistoryFromNative(promise: Promise) {
        try {
            val sharedPrefs = reactApplicationContext.getSharedPreferences("AutoSmsPrefs", Context.MODE_PRIVATE)
            val historyJson = sharedPrefs.getString("@AutoSMS:SmsHistory", "[]") ?: "[]"
            
            // If there's history data, parse it to a WritableArray
            if (historyJson != "[]") {
                try {
                    // Convert JSON string to WritableArray
                    val jsonArray = org.json.JSONArray(historyJson)
                    val resultArray = Arguments.createArray()
                    
                    for (i in 0 until jsonArray.length()) {
                        val jsonObject = jsonArray.getJSONObject(i)
                        val item = Arguments.createMap()
                        
                        // Copy all fields from JSON to WritableMap
                        val keys = jsonObject.keys()
                        while (keys.hasNext()) {
                            val key = keys.next()
                            val value = jsonObject.get(key)
                            
                            when (value) {
                                is String -> item.putString(key, value)
                                is Int -> item.putInt(key, value)
                                is Double -> item.putDouble(key, value)
                                is Long -> item.putDouble(key, value.toDouble())
                                is Boolean -> item.putBoolean(key, value)
                                else -> {
                                    // Handle case when the value is null or another type
                                    if (!jsonObject.isNull(key)) {
                                        item.putString(key, value.toString())
                                    }
                                }
                            }
                        }
                        
                        resultArray.pushMap(item)
                    }
                    
                    // Clear history in SharedPreferences after retrieving it
                    sharedPrefs.edit().putString("@AutoSMS:SmsHistory", "[]").apply()
                    
                    promise.resolve(resultArray)
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing SMS history JSON: ${e.message}", e)
                    promise.reject("PARSE_HISTORY_ERROR", "Failed to parse SMS history: ${e.message}")
                }
            } else {
                // No history, return empty array
                promise.resolve(Arguments.createArray())
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting SMS history: ${e.message}", e)
            promise.reject("GET_HISTORY_ERROR", "Failed to get SMS history: ${e.message}")
        }
    }

    @ReactMethod
    fun isAIEnabled(promise: Promise) {
        try {
            val sharedPrefs = reactApplicationContext.getSharedPreferences("AutoSmsPrefs", Context.MODE_PRIVATE)
            val enabled = sharedPrefs.getBoolean("@AutoSMS:AIEnabled", false)
            promise.resolve(enabled)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting AI SMS enabled: ${e.message}")
            promise.reject("GET_AI_SMS_ERROR", "Failed to get AI SMS enabled: ${e.message}")
        }
    }

    @ReactMethod
    fun setAIEnabled(enabled: Boolean, promise: Promise) {
        try {
            // Save setting to SharedPreferences for use when app is killed
            val sharedPrefs = reactApplicationContext.getSharedPreferences("AutoSmsPrefs", Context.MODE_PRIVATE)
            sharedPrefs.edit().putBoolean("@AutoSMS:AIEnabled", enabled).apply()
            promise.resolve(true)
        } catch (e: Exception) {
            Log.e(TAG, "Error setting AI SMS enabled: ${e.message}")
            promise.reject("SET_AI_SMS_ERROR", "Failed to set AI SMS enabled: ${e.message}")
        }
    }
    
    @ReactMethod
    fun getInitialSmsMessage(promise: Promise) {
        try {
            val sharedPrefs = reactApplicationContext.getSharedPreferences("AutoSmsPrefs", Context.MODE_PRIVATE)
            val message = sharedPrefs.getString("@AutoSMS:InitialMessage", "AI: I am busy, available only for chat. How may I help you?")
            promise.resolve(message)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting initial SMS message: ${e.message}")
            promise.reject("GET_INITIAL_SMS_ERROR", "Failed to get initial SMS message: ${e.message}")
        }
    }
    
    @ReactMethod
    fun setInitialSmsMessage(message: String, promise: Promise) {
        try {
            val sharedPrefs = reactApplicationContext.getSharedPreferences("AutoSmsPrefs", Context.MODE_PRIVATE)
            sharedPrefs.edit().putString("@AutoSMS:InitialMessage", message).apply()
            promise.resolve(true)
        } catch (e: Exception) {
            Log.e(TAG, "Error setting initial SMS message: ${e.message}")
            promise.reject("SET_INITIAL_SMS_ERROR", "Failed to set initial SMS message: ${e.message}")
        }
    }

    private fun registerSmsReceiver() {
        try {
            // Register for incoming SMS messages
            val smsIntentFilter = IntentFilter()
            smsIntentFilter.addAction(Telephony.Sms.Intents.SMS_RECEIVED_ACTION)
            
            val smsReceiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) {
                    if (intent.action == Telephony.Sms.Intents.SMS_RECEIVED_ACTION) {
                        // Check if AI SMS is enabled
                        val sharedPrefs = reactApplicationContext.getSharedPreferences("AutoSmsPrefs", Context.MODE_PRIVATE)
                        val aiEnabled = sharedPrefs.getBoolean("@AutoSMS:AIEnabled", false)
                        
                        if (!aiEnabled) {
                            Log.d(TAG, "AI SMS is disabled. Ignoring incoming message.")
                            return
                        }
                        
                        // Process incoming SMS
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                            for (smsMessage in Telephony.Sms.Intents.getMessagesFromIntent(intent)) {
                                val phoneNumber = smsMessage.originatingAddress
                                val messageBody = smsMessage.messageBody
                                
                                Log.d(TAG, "Received SMS from $phoneNumber: $messageBody")
                                
                                // Emit event to React Native
                                val eventData = Arguments.createMap().apply {
                                    putString("phoneNumber", phoneNumber)
                                    putString("message", messageBody)
                                    putDouble("timestamp", System.currentTimeMillis().toDouble())
                                }
                                
                                sendEvent("onSmsReceived", eventData)
                            }
                        }
                    }
                }
            }
            
            reactApplicationContext.registerReceiver(smsReceiver, smsIntentFilter)
            Log.d(TAG, "SMS receiver registered successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error registering SMS receiver: ${e.message}", e)
        }
    }

    @ReactMethod
    fun processPendingMessages(promise: Promise) {
        try {
            val sharedPrefs = reactApplicationContext.getSharedPreferences("AutoSmsPrefs", Context.MODE_PRIVATE)
            val pendingMessages = sharedPrefs.getStringSet("pendingIncomingSms", HashSet()) ?: HashSet()
            
            if (pendingMessages.isEmpty()) {
                promise.resolve(false)
                return
            }
            
            Log.d(TAG, "Processing ${pendingMessages.size} pending SMS messages")
            
            for (pendingMessage in pendingMessages) {
                try {
                    val parts = pendingMessage.split(":", limit = 3)
                    if (parts.size == 3) {
                        val phoneNumber = parts[0]
                        val message = parts[1]
                        
                        // Emit event to React Native
                        val eventData = Arguments.createMap().apply {
                            putString("phoneNumber", phoneNumber)
                            putString("message", message)
                            putDouble("timestamp", System.currentTimeMillis().toDouble())
                        }
                        
                        sendEvent("onSmsReceived", eventData)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error processing pending message: ${e.message}")
                }
            }
            
            // Clear pending messages
            sharedPrefs.edit().putStringSet("pendingIncomingSms", HashSet()).apply()
            
            promise.resolve(true)
        } catch (e: Exception) {
            Log.e(TAG, "Error processing pending messages: ${e.message}")
            promise.reject("PROCESS_PENDING_ERROR", "Failed to process pending messages: ${e.message}")
        }
    }

    /**
     * Process incoming SMS and generate response using local LLM if available, 
     * or fallback to online API
     */
    private fun processIncomingSmsWithLLM(caller: String, messageBody: String): String {
        try {
            // Check if Local LLM module is loaded
            val localLLMModule = reactApplicationContext.getNativeModule(com.auto_sms.llm.LocalLLMModule::class.java)
            
            if (localLLMModule != null) {
                // Try to generate response with local LLM
                try {
                    val isLoaded = localLLMModule.isModelLoadedSync()
                    
                    if (isLoaded) {
                        Log.d(TAG, "Using local LLM for response generation")
                        val response = localLLMModule.generateAnswerSync(messageBody, 0.7f, 200)
                        
                        // Ensure response starts with "AI:" prefix
                        return if (response.startsWith("AI:")) {
                            response
                        } else {
                            "AI: $response"
                        }
                    } else {
                        Log.d(TAG, "Local LLM model is not loaded, falling back to online API")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error using local LLM for response: ${e.message}")
                }
            }
            
            // Fallback to online AI API response
            return generateAIResponseSync(caller, messageBody)
        } catch (e: Exception) {
            Log.e(TAG, "Error in processIncomingSmsWithLLM: ${e.message}")
            return "AI: Sorry, I am not capable of giving this answer. Wait for a call or try with a different question."
        }
    }
    
    /**
     * Fallback method for generating AI responses when LLM is not available
     */
    private fun generateAIResponseSync(caller: String, message: String): String {
        // Simple fallback response when local LLM is not available
        return "AI: Sorry, I am not capable of giving this answer. Wait for a call or try with a different question."
    }
} 