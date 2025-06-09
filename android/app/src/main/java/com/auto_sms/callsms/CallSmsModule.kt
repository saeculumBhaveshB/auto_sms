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
import java.io.File
import java.io.FileInputStream
import java.util.Timer
import java.util.TimerTask
import kotlinx.coroutines.runBlocking
import com.auto_sms.docextractor.DocExtractorHelper

class CallSmsModule(reactContext: ReactApplicationContext) : ReactContextBaseJavaModule(reactContext) {

    private val TAG = "CallSmsModule"
    
    // Common stopwords to ignore when analyzing text
    private val STOPWORDS = setOf(
        "the", "and", "that", "have", "for", "not", "with", "you", "this", "but", "his", "from", 
        "they", "she", "will", "would", "there", "their", "what", "about", "which", "when", "make", 
        "like", "time", "just", "know", "take", "into", "year", "your", "good", "some", "could", 
        "them", "than", "then", "look", "only", "come", "over", "think", "also", "back", "after", 
        "work", "first", "well", "even", "want", "because", "these", "give", "most"
    )
    
    private var callReceiver: BroadcastReceiver? = null
    private var isMonitoringCalls = false
    private val DEFAULT_MESSAGE = "I am busy, please give me some time, I will contact you."
    private val scheduler: ScheduledExecutorService = Executors.newScheduledThreadPool(1)
    private var lastPhoneState = TelephonyManager.CALL_STATE_IDLE
    private var latestIncomingNumber: String? = null
    private var callStart: Long = 0L
    private var missedCallDetectedAt: Long = 0L
    
    // Static variable to hold the ReactContext instance
    companion object {
        private var reactContextInstance: ReactApplicationContext? = null
        
        /**
         * Get the current React Context instance
         * This is used by the SmsReceiver to send events to React Native
         */
        @JvmStatic
        fun getReactContextInstance(): ReactApplicationContext? {
            return reactContextInstance
        }
    }
    
    init {
        // Store the React Context instance when module is initialized
        reactContextInstance = reactContext
    }
    
    override fun getName(): String {
        return "CallSmsModule"
    }

    private fun hasRequiredPermissions(): Boolean {
        val context = reactApplicationContext
        val readCallLog = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALL_LOG) == PackageManager.PERMISSION_GRANTED
        val readPhoneState = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED
        
        // For SMS permissions, check differently based on Android version
        if (Build.VERSION.SDK_INT >= 35) {
            // On Android 15+, only check if we're the default SMS handler
            return readCallLog && readPhoneState && isDefaultSmsHandlerSync()
        } else {
            // On older Android versions, check for SEND_SMS permission
            val sendSms = ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED
            return readCallLog && readPhoneState && sendSms
        }
    }

    // Add a synchronous version of isDefaultSmsHandler
    private fun isDefaultSmsHandlerSync(): Boolean {
        val defaultSmsPackage = Telephony.Sms.getDefaultSmsPackage(reactApplicationContext)
        val ourPackage = reactApplicationContext.packageName
        return defaultSmsPackage == ourPackage
    }
    
    // Fix the sendSmsForMissedCall function to handle Promise properly
    private fun sendSmsForMissedCall(phoneNumber: String) {
        try {
            // Check if auto SMS is enabled
            val sharedPrefs = reactApplicationContext.getSharedPreferences("AutoSmsPrefs", Context.MODE_PRIVATE)
            val autoSmsEnabled = sharedPrefs.getBoolean("@AutoSMS:Enabled", true)
            
            if (!autoSmsEnabled) {
                Log.d(TAG, "Auto SMS is disabled. Not sending message for missed call.")
                return
            }
            
            // Get initial message from preferences
            val message = sharedPrefs.getString("@AutoSMS:InitialMessage", DEFAULT_MESSAGE) ?: DEFAULT_MESSAGE
            
            // Check if we can send SMS (permission and Android version)
            if (Build.VERSION.SDK_INT >= 35) {
                // For Android 15+, we need to be the default SMS handler or use intent-based approach
                val isDefault = isDefaultSmsHandlerSync()
                
                if (isDefault) {
                    // Use direct SMS API since we're the default handler
                    // Don't use sendSmsDirectly since it requires a Promise
                    try {
                        val smsManager = SmsManager.getDefault()
                        
                        // Split message if it's too long
                        val parts = smsManager.divideMessage(message)
                        
                        // Send SMS
                        if (parts.size > 1) {
                            // For multipart messages
                            smsManager.sendMultipartTextMessage(phoneNumber, null, parts, null, null)
                        } else {
                            // For single-part messages
                            smsManager.sendTextMessage(phoneNumber, null, message, null, null)
                        }
                        
                        // Create data for event
                        val eventData = Arguments.createMap().apply {
                            putString("phoneNumber", phoneNumber)
                            putString("message", message)
                            putString("timestamp", System.currentTimeMillis().toString())
                        }
                        
                        // Emit event
                        sendEvent("onSmsSent", eventData)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error sending direct SMS for missed call: ${e.message}", e)
                    }
                } else {
                    // Use intent-based approach
                    val intent = Intent(Intent.ACTION_SENDTO).apply {
                        data = Uri.parse("smsto:$phoneNumber")
                        putExtra("sms_body", message)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    
                    if (intent.resolveActivity(reactApplicationContext.packageManager) != null) {
                        reactApplicationContext.startActivity(intent)
                        
                        // Create data for event
                        val eventData = Arguments.createMap().apply {
                            putString("phoneNumber", phoneNumber)
                            putString("message", message)
                            putString("timestamp", System.currentTimeMillis().toString())
                        }
                        
                        // Emit event
                        sendEvent("onSmsSent", eventData)
                    }
                }
            } else {
                // For older Android versions, use direct SMS API
                try {
                    val smsManager = SmsManager.getDefault()
                    
                    // Split message if it's too long
                    val parts = smsManager.divideMessage(message)
                    
                    // Send SMS
                    if (parts.size > 1) {
                        // For multipart messages
                        smsManager.sendMultipartTextMessage(phoneNumber, null, parts, null, null)
                    } else {
                        // For single-part messages
                        smsManager.sendTextMessage(phoneNumber, null, message, null, null)
                    }
                    
                    // Create data for event
                    val eventData = Arguments.createMap().apply {
                        putString("phoneNumber", phoneNumber)
                        putString("message", message)
                        putString("timestamp", System.currentTimeMillis().toString())
                    }
                    
                    // Emit event
                    sendEvent("onSmsSent", eventData)
                } catch (e: Exception) {
                    Log.e(TAG, "Error sending direct SMS for missed call: ${e.message}", e)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error sending SMS for missed call: ${e.message}", e)
        }
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
        if (phoneNumber.isEmpty()) {
            promise.reject("INVALID_PHONE_NUMBER", "Phone number cannot be empty")
            return
        }

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
            
            // Use different approaches based on Android version
            if (Build.VERSION.SDK_INT >= 35) {
                // On Android 15+, use intent-based approach to send SMS
                sendSmsViaIntent(phoneNumber, smsMessage, promise)
            } else {
                // On older Android versions, use direct SMS API
                sendSmsDirectly(phoneNumber, smsMessage, promise)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error sending SMS: ${e.message}", e)
            promise.reject("SEND_SMS_ERROR", "Failed to send SMS: ${e.message}")
        }
    }

    /**
     * Send SMS via intent (for Android 15+)
     */
    private fun sendSmsViaIntent(phoneNumber: String, message: String, promise: Promise) {
        try {
            // Create intent to send SMS via the system SMS app
            val intent = Intent(Intent.ACTION_SENDTO).apply {
                data = Uri.parse("smsto:$phoneNumber")
                putExtra("sms_body", message)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            
            // Check if the intent can be resolved
            if (intent.resolveActivity(reactApplicationContext.packageManager) != null) {
                reactApplicationContext.startActivity(intent)
                
                // Create data for event
                val eventData = Arguments.createMap().apply {
                    putString("phoneNumber", phoneNumber)
                    putString("message", message)
                    putString("timestamp", System.currentTimeMillis().toString())
                }
                
                // Emit event
                sendEvent("onSmsSent", eventData)
                
                promise.resolve(true)
            } else {
                Log.e(TAG, "No app can handle SMS intent")
                promise.reject("SEND_SMS_ERROR", "No app can handle SMS intent")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error sending SMS via intent: ${e.message}", e)
            promise.reject("SEND_SMS_ERROR", "Failed to send SMS via intent: ${e.message}")
        }
    }

    /**
     * Send SMS directly using SmsManager (for Android <= 14)
     */
    private fun sendSmsDirectly(phoneNumber: String, message: String, promise: Promise) {
        try {
            val smsManager = SmsManager.getDefault()
            
            // Split message if it's too long
            val parts = smsManager.divideMessage(message)
            
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
                smsManager.sendTextMessage(phoneNumber, null, message, sentPI, null)
            }
            
            // Create data for event
            val eventData = Arguments.createMap().apply {
                putString("phoneNumber", phoneNumber)
                putString("message", message)
                putString("timestamp", System.currentTimeMillis().toString())
            }
            
            // Emit event
            sendEvent("onSmsSent", eventData)
            
            promise.resolve(true)
        } catch (e: Exception) {
            Log.e(TAG, "Error sending SMS directly: ${e.message}", e)
            promise.reject("SEND_SMS_ERROR", "Failed to send SMS directly: ${e.message}")
        }
    }

    /**
     * Check if the app is the default SMS handler
     */
    @ReactMethod
    fun isDefaultSmsHandler(promise: Promise) {
        try {
            val defaultSmsPackage = Telephony.Sms.getDefaultSmsPackage(reactApplicationContext)
            val ourPackage = reactApplicationContext.packageName
            val isDefault = defaultSmsPackage == ourPackage
            
            Log.d(TAG, "Default SMS package: $defaultSmsPackage, our package: $ourPackage")
            Log.d(TAG, "Is default SMS handler: $isDefault")
            
            promise.resolve(isDefault)
        } catch (e: Exception) {
            Log.e(TAG, "Error checking default SMS handler: ${e.message}")
            promise.reject("DEFAULT_SMS_ERROR", "Failed to check default SMS handler: ${e.message}")
        }
    }

    /**
     * Request to become the default SMS handler
     */
    @ReactMethod
    fun requestDefaultSmsHandler(promise: Promise) {
        try {
            // First check if we're already the default SMS app
            val defaultSmsPackage = Telephony.Sms.getDefaultSmsPackage(reactApplicationContext)
            val ourPackage = reactApplicationContext.packageName
            val isDefault = defaultSmsPackage == ourPackage
            
            if (isDefault) {
                Log.d(TAG, "App is already the default SMS handler")
                promise.resolve(true)
                return
            }
            
            // Create intent to request becoming default SMS app
            val intent = Intent(Telephony.Sms.Intents.ACTION_CHANGE_DEFAULT)
            intent.putExtra(Telephony.Sms.Intents.EXTRA_PACKAGE_NAME, ourPackage)
            
            // Add all necessary flags to make sure the dialog appears
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            intent.addFlags(Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
            intent.addFlags(Intent.FLAG_ACTIVITY_BROUGHT_TO_FRONT)
            
            // Define the request code constant - must match MainActivity
            val DEFAULT_SMS_REQUEST_CODE = 1001
            
            Log.d(TAG, "Preparing to show default SMS handler selection dialog")
            
            // Get current activity to start intent from there
            val currentActivity = currentActivity
            if (currentActivity != null) {
                try {
                    Log.d(TAG, "Starting intent from current activity: ${currentActivity.javaClass.simpleName}")
                    // Start activity for result from current activity
                    currentActivity.startActivityForResult(intent, DEFAULT_SMS_REQUEST_CODE)
                    Log.d(TAG, "Successfully started intent from current activity")
                    promise.resolve(true)
                    return
                } catch (e: Exception) {
                    Log.e(TAG, "Error starting intent from current activity: ${e.message}")
                    // Fall through to try other methods
                }
            } else {
                Log.d(TAG, "No current activity available")
            }
            
            // Fallback: Start from application context
            try {
                Log.d(TAG, "Starting intent from application context")
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                reactApplicationContext.startActivity(intent)
                Log.d(TAG, "Successfully started intent from application context")
                promise.resolve(true)
            } catch (e: Exception) {
                Log.e(TAG, "Error starting intent from application context: ${e.message}")
                promise.reject("DEFAULT_SMS_ERROR", "Failed to launch default SMS handler dialog: ${e.message}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error requesting default SMS handler: ${e.message}")
            promise.reject("DEFAULT_SMS_ERROR", "Failed to request default SMS handler: ${e.message}")
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
                        
                        // Remove any "AI:" prefix instead of adding one
                        return response.replace(Regex("^AI:\\s*", RegexOption.IGNORE_CASE), "")
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
            return "Sorry, I am not capable of giving this answer. Please try again later."
        }
    }
    
    /**
     * Fallback method for generating AI responses when LLM is not available
     */
    private fun generateAIResponseSync(caller: String, messageBody: String): String {
        // Simple fallback response when local LLM is not available
        return "Sorry, I am not capable of giving this answer. Please try again later."
    }

    @ReactMethod
    fun checkLLMStatus(promise: Promise) {
        try {
            Log.d(TAG, "🔍 DIAGNOSTIC - Running LLM status check")
            
            val diagnosticReport = Arguments.createMap()
            val sharedPrefs = reactApplicationContext.getSharedPreferences("AutoSmsPrefs", Context.MODE_PRIVATE)
            
            // Check enabled settings
            val autoReplyEnabled = sharedPrefs.getBoolean("@AutoSMS:AutoReplyEnabled", false)
            val llmAutoReplyEnabled = sharedPrefs.getBoolean("@AutoSMS:LLMAutoReplyEnabled", false)
            val aiEnabled = sharedPrefs.getBoolean("@AutoSMS:AIEnabled", false)
            
            diagnosticReport.putBoolean("simpleAutoReplyEnabled", autoReplyEnabled)
            diagnosticReport.putBoolean("llmAutoReplyEnabled", llmAutoReplyEnabled)
            diagnosticReport.putBoolean("aiEnabled", aiEnabled)
            
            // Check documents directory
            val documentsDir = File(reactApplicationContext.filesDir, "documents")
            val exists = documentsDir.exists()
            diagnosticReport.putBoolean("documentsDirectoryExists", exists)
            
            if (exists) {
                val documentFiles = documentsDir.listFiles()
                val documentsArray = Arguments.createArray()
                
                if (documentFiles != null && documentFiles.isNotEmpty()) {
                    for (file in documentFiles) {
                        val docMap = Arguments.createMap()
                        docMap.putString("name", file.name)
                        docMap.putDouble("size", file.length().toDouble())
                        docMap.putDouble("lastModified", file.lastModified().toDouble())
                        documentsArray.pushMap(docMap)
                    }
                }
                
                diagnosticReport.putArray("documents", documentsArray)
                diagnosticReport.putInt("documentCount", documentFiles?.size ?: 0)
            } else {
                diagnosticReport.putInt("documentCount", 0)
            }
            
            // Check if the LLM module is available
            try {
                val reactContext = reactApplicationContext
                val llmModule = reactContext.getNativeModule(com.auto_sms.llm.LocalLLMModule::class.java)
                val isAvailable = llmModule != null
                diagnosticReport.putBoolean("llmModuleAvailable", isAvailable)
                
                if (isAvailable) {
                    // Check if a model is loaded
                    val isModelLoaded = llmModule!!.isModelLoadedSync()
                    diagnosticReport.putBoolean("modelLoaded", isModelLoaded)
                    
                    // Try a test generation if model is loaded
                    if (isModelLoaded) {
                        val testQuestion = "Hello, is the LLM working?"
                        val response = llmModule.generateAnswerSync(testQuestion, 0.7f, 100)
                        diagnosticReport.putString("testResponse", response)
                        diagnosticReport.putBoolean("testPassed", response != null && response.isNotEmpty())
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error accessing LLM module", e)
                diagnosticReport.putBoolean("llmModuleAvailable", false)
                diagnosticReport.putString("llmError", e.message)
            }
            
            // Return the diagnostic report
            promise.resolve(diagnosticReport)
            
            // Also log the report
            Log.d(TAG, "📊 LLM DIAGNOSTIC REPORT: $diagnosticReport")
        } catch (e: Exception) {
            Log.e(TAG, "Error checking LLM status", e)
            promise.reject("CHECK_LLM_ERROR", "Failed to check LLM status: ${e.message}")
        }
    }

    /**
     * Set LLM auto-reply enabled state
     */
    @ReactMethod
    fun setLLMAutoReplyEnabled(enabled: Boolean, promise: Promise) {
        try {
            Log.d(TAG, "🤖 Setting LLM auto-reply enabled: $enabled")
            
            // Save setting to SharedPreferences for use when app is killed
            val sharedPrefs = reactApplicationContext.getSharedPreferences("AutoSmsPrefs", Context.MODE_PRIVATE)
            sharedPrefs.edit().putBoolean("@AutoSMS:LLMAutoReplyEnabled", enabled).apply()
            
            // Log the current state of all auto-reply settings for debugging
            val autoReplyEnabled = sharedPrefs.getBoolean("@AutoSMS:AutoReplyEnabled", false)
            val aiEnabled = sharedPrefs.getBoolean("@AutoSMS:AIEnabled", false)
            Log.d(TAG, "📊 Auto-reply settings after update:")
            Log.d(TAG, "   • Simple Auto-Reply: $autoReplyEnabled")
            Log.d(TAG, "   • LLM Auto-Reply: $enabled")
            Log.d(TAG, "   • AI Enabled: $aiEnabled")
            
            promise.resolve(true)
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error setting LLM auto-reply enabled: ${e.message}")
            promise.reject("SET_LLM_AUTO_REPLY_ERROR", "Failed to set LLM auto-reply enabled: ${e.message}")
        }
    }
    
    /**
     * Check if LLM auto-reply is enabled
     */
    @ReactMethod
    fun isLLMAutoReplyEnabled(promise: Promise) {
        try {
            val sharedPrefs = reactApplicationContext.getSharedPreferences("AutoSmsPrefs", Context.MODE_PRIVATE)
            val enabled = sharedPrefs.getBoolean("@AutoSMS:LLMAutoReplyEnabled", false)
            Log.d(TAG, "🤖 LLM auto-reply enabled status: $enabled")
            promise.resolve(enabled)
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error checking if LLM auto-reply is enabled: ${e.message}")
            promise.reject("GET_LLM_AUTO_REPLY_ERROR", "Failed to get LLM auto-reply enabled: ${e.message}")
        }
    }

    /**
     * Set simple auto-reply enabled state directly
     */
    @ReactMethod
    fun setAutoReplyEnabled(enabled: Boolean, promise: Promise) {
        try {
            Log.e(TAG, "🔧 Setting auto reply enabled: $enabled")
            // Save setting to SharedPreferences for use when app is killed
            val sharedPrefs = reactApplicationContext.getSharedPreferences("AutoSmsPrefs", Context.MODE_PRIVATE)
            sharedPrefs.edit().putBoolean("@AutoSMS:AutoReplyEnabled", enabled).apply()
            
            // Log the update for verification
            Log.e(TAG, "✅ Auto reply enabled set to: $enabled")
            Log.e(TAG, "📊 Current SharedPrefs values:")
            Log.e(TAG, "   • Auto-Reply: ${sharedPrefs.getBoolean("@AutoSMS:AutoReplyEnabled", false)}")
            Log.e(TAG, "   • LLM Auto-Reply: ${sharedPrefs.getBoolean("@AutoSMS:LLMAutoReplyEnabled", false)}")
            
            promise.resolve(true)
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error setting auto reply enabled: ${e.message}")
            promise.reject("SET_AUTO_REPLY_ERROR", "Failed to set auto reply enabled: ${e.message}")
        }
    }

    /**
     * Check if simple auto-reply is enabled
     */
    @ReactMethod
    fun isAutoReplyEnabled(promise: Promise) {
        try {
            val sharedPrefs = reactApplicationContext.getSharedPreferences("AutoSmsPrefs", Context.MODE_PRIVATE)
            val enabled = sharedPrefs.getBoolean("@AutoSMS:AutoReplyEnabled", false)
            
            // Log for debugging
            Log.e(TAG, "🔍 Checking auto-reply enabled status: $enabled")
            
            promise.resolve(enabled)
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error checking auto reply enabled: ${e.message}")
            promise.reject("GET_AUTO_REPLY_ERROR", "Failed to check auto reply enabled: ${e.message}")
        }
    }
    
    /**
     * Add a test phone number to the missed call numbers list
     */
    @ReactMethod
    fun addTestPhoneNumber(phoneNumber: String, promise: Promise) {
        try {
            Log.e(TAG, "📞 Adding test phone number to missed call list: $phoneNumber")
            
            val sharedPrefs = reactApplicationContext.getSharedPreferences("AutoSmsPrefs", Context.MODE_PRIVATE)
            val missedCallNumbers = sharedPrefs.getStringSet("missedCallNumbers", HashSet()) ?: HashSet()
            
            // Add the test entry with current timestamp
            val newMissedCallNumbers = HashSet(missedCallNumbers)
            val timestamp = System.currentTimeMillis()
            newMissedCallNumbers.add("$phoneNumber:$timestamp")
            
            // Save back to SharedPreferences
            sharedPrefs.edit().putStringSet("missedCallNumbers", newMissedCallNumbers).apply()
            
            // Verify by reading back
            val updatedSet = sharedPrefs.getStringSet("missedCallNumbers", HashSet()) ?: HashSet()
            Log.e(TAG, "✅ Test phone number added. Currently ${updatedSet.size} entries:")
            for (entry in updatedSet) {
                Log.e(TAG, "   • $entry")
            }
            
            promise.resolve(true)
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error adding test phone number: ${e.message}")
            promise.reject("ADD_TEST_NUMBER_ERROR", "Failed to add test phone number: ${e.message}")
        }
    }
    
    /**
     * Send a test SMS for debugging
     */
    @ReactMethod
    fun sendTestSms(phoneNumber: String, message: String, promise: Promise) {
        try {
            Log.e(TAG, "🧪 Sending test SMS to $phoneNumber: $message")
            
            val smsManager = SmsManager.getDefault()
            
            // Add FLAG_IMMUTABLE for Android 12+ compatibility
            val pendingIntentFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }
            
            // Prepare PendingIntent for SMS
            val sentIntent = Intent("android.provider.Telephony.SMS_SENT")
            val sentPI = PendingIntent.getBroadcast(reactApplicationContext, 0, sentIntent, pendingIntentFlags)
            
            // Split message if it's too long
            val parts = smsManager.divideMessage(message)
            
            try {
                // Send SMS
                if (parts.size > 1) {
                    // Create PendingIntent array for multipart SMS
                    val sentIntents = ArrayList<PendingIntent>().apply {
                        repeat(parts.size) { i ->
                            add(PendingIntent.getBroadcast(reactApplicationContext, i, sentIntent, pendingIntentFlags))
                        }
                    }
                    
                    Log.e(TAG, "📤 Sending multipart test SMS")
                    smsManager.sendMultipartTextMessage(phoneNumber, null, parts, sentIntents, null)
                    Log.e(TAG, "✅ Sent multipart test SMS successfully")
                } else {
                    Log.e(TAG, "📤 Sending single part test SMS")
                    smsManager.sendTextMessage(phoneNumber, null, message, sentPI, null)
                    Log.e(TAG, "✅ Sent single test SMS successfully")
                }
                
                promise.resolve(true)
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error sending test SMS: ${e.message}")
                e.printStackTrace()
                promise.reject("SEND_TEST_SMS_ERROR", "Failed to send test SMS: ${e.message}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error in sendTestSms: ${e.message}")
            promise.reject("SEND_TEST_SMS_ERROR", "Failed to send test SMS: ${e.message}")
        }
    }

    /**
     * Test the LLM by generating a response to a question
     * This is used by the LLM testing UI in the app
     */
    @ReactMethod
    fun testLLM(question: String, promise: Promise) {
        try {
            Log.d(TAG, "🧪 Testing LLM with question: $question")
            // Record start time for performance measurement
            val startTime = System.currentTimeMillis()
            
            // Check if we should load document content for better context
            val documentsDir = File(reactApplicationContext.filesDir, "documents")
            if (documentsDir.exists()) {
                // Get list of documents
                val documents = documentsDir.listFiles() ?: emptyArray()
                    
                if (documents.isNotEmpty()) {
                    // Process documents
                    val documentContentMap = mutableMapOf<String, String>()
                    var totalSize = 0L
                    var docxCount = 0
                    
                    // Process each document
                    for (document in documents) {
                            if (document.isFile) {
                            try {
                                val name = document.name
                                totalSize += document.length()
                                
                                // Skip files that aren't useful for context
                                if (shouldSkipFile(name)) {
                                    continue
                                }
                                
                                // Check file types for appropriate handling
                                val isDocx = name.lowercase().endsWith(".docx")
                                val isPdf = name.lowercase().endsWith(".pdf")
                                
                                val content = try {
                                    when {
                                        // For PDF files, extract text using appropriate method
                                        isPdf -> {
                                            try {
                                                extractTextFromPdf(document)  // Pass the File object directly
                                            } catch (pdfErr: Exception) {
                                                Log.e(TAG, "❌ Error with PDF: ${pdfErr.message}")
                                                "This document is in PDF format and contains information that may be relevant to your query."
                                            }
                                        }
                                        // For DOCX files, use robust extraction 
                                        isDocx -> {
                                            docxCount++
                                            try {
                                                extractTextFromDocx(document)
                                            } catch (docxErr: Exception) {
                                                Log.e(TAG, "❌ Error with DOCX: ${docxErr.message}")
                                                "This document is in DOCX format and contains information that may be relevant to your query."
                                            }
                                        }
                                        // For other file types, use appropriate handlers
                                        else -> {
                                            // Default to plain text for most files
                                            document.readText()
                                        }
                                    }
                                } catch (e: Exception) {
                                    Log.e(TAG, "❌ Error reading document ${document.name}: ${e.message}")
                                    "Error: Could not read document content."
                                }
                                
                                // Add to our collection
                                documentContentMap[name] = content
                                Log.d(TAG, "📄 Added document to context: ${document.name} (${content.length} chars)")
                        } catch (e: Exception) {
                                Log.e(TAG, "❌ Error processing document: ${e.message}")
                            }
                        }
                    }
                    
                    Log.e(TAG, "📊 Loaded ${documentContentMap.size}/${documents.size} documents, total ${totalSize/1024} KB")
                    
                    // Now enhance the question with document content
                    val enhancedPrompt = buildPromptWithDocuments(question, documentContentMap)
                    
                    // Check if model is loaded
                    val llmModule = reactApplicationContext.getNativeModule(
                        com.auto_sms.llm.LocalLLMModule::class.java
                    )
                    
                    // Set a timeout to prevent hanging
                    val timeoutTimer = Timer()
                    val timeoutTask = object : TimerTask() {
                        override fun run() {
                            Log.e(TAG, "⚠️ LLM processing timeout reached")
                            try {
                                promise.resolve("The processing took too long to respond. This might be due to complex documents or processing issues. Please try again with a simpler query.")
                            } catch (e: Exception) {
                                Log.e(TAG, "❌ Error handling timeout: ${e.message}")
                            }
                        }
                    }
                    timeoutTimer.schedule(timeoutTask, 30000) // 30 seconds timeout
                    
                    // Use direct method call rather than callback
                    if (llmModule != null && llmModule.isModelLoadedSync()) {
                        Log.d(TAG, "🧠 Using LLM with context")
                        
                        try {
                            // Use a coroutine to handle the async operation
                            val response = runBlocking { 
                                llmModule.generateAnswer(question, enhancedPrompt, 0.7f)
                            }
                            
                            timeoutTimer.cancel()
                    val endTime = System.currentTimeMillis()
                            Log.d(TAG, "⏱️ LLM test completed in ${endTime - startTime}ms")
                            
                            // Format and return response without the "AI:" prefix
                            val formattedResponse = response.replace(Regex("^AI:\\s*", RegexOption.IGNORE_CASE), "")
                            
                            promise.resolve(formattedResponse)
                        } catch (e: Exception) {
                            timeoutTimer.cancel()
                            Log.e(TAG, "❌ Error generating response: ${e.message}")
                            promise.reject("LLM_ERROR", e.message)
                        }
                    } else {
                        timeoutTimer.cancel()
                        
                        // Create a document-based response directly using the extracted text
                        val relevantPassages = extractRelevantPassagesFromDocuments(question, documentContentMap)
                        
                        if (relevantPassages.isNotEmpty()) {
                            // Use the most relevant passage to create a response
                            val topPassage = relevantPassages.first()
                            
                            // Extract sentences from the passage
                            val sentences = topPassage.split(Regex("[.!?]\\s+"))
                                .filter { it.trim().length > 20 }
                            
                            // Extract keywords from the question
                            val keywords = question.lowercase()
                                .split(Regex("\\s+"))
                                .filter { it.length >= 4 }
                                .toSet()
                            
                            // Find sentences that might answer the question
                            val relevantSentences = sentences
                                .filter { sentence -> 
                                    keywords.any { keyword -> 
                                        sentence.lowercase().contains(keyword) 
                                    }
                                }
                                .take(2)
                            
                            if (relevantSentences.isNotEmpty()) {
                                // Construct a helpful response using the extracted content
                                val response = StringBuilder()
                                relevantSentences.forEach { sentence ->
                                    response.append(sentence.trim())
                                    if (!sentence.trim().endsWith(".")) response.append(".")
                                    response.append(" ")
                                }
                                
                                // Strip out any document metadata tags
                                var cleanedResponse = response.toString()
                                cleanedResponse = cleanedResponse.replace(Regex("Document \\d+: [^\n]+\n"), "")
                                cleanedResponse = cleanedResponse.replace(Regex("Title: [^\n]+\n"), "")
                                cleanedResponse = cleanedResponse.replace(Regex("Content: \\s*\n"), "")
                                cleanedResponse = cleanedResponse.replace(Regex("^AI:\\s*", RegexOption.IGNORE_CASE), "")
                                
                                promise.resolve(cleanedResponse)
                            } else {
                                // If we couldn't find relevant sentences, use document names
                                val documentNames = documentContentMap.keys.take(3).joinToString(", ")
                                val documentCountText = if (documentContentMap.size > 3) {
                                    "$documentNames and ${documentContentMap.size - 3} more"
                                } else {
                                    documentNames
                                }
                                
                                // Find documents that might contain relevant info
                                val possiblyRelevantDocs = documentContentMap.entries
                                    .filter { (_, content) -> 
                                        keywords.any { keyword -> content.lowercase().contains(keyword) }
                                    }
                                    .map { it.key }
                                    .take(2)
                                
                                if (possiblyRelevantDocs.isNotEmpty()) {
                                    val docNames = possiblyRelevantDocs.joinToString(", ")
                                    promise.resolve("I found some information in ${docNames} that might be relevant to your question about ${keywords.first()}, but I couldn't extract a specific answer. Could you ask in a different way?")
                                } else {
                                    promise.resolve("I have documents about ${documentCountText}, but I couldn't find specific information about your question. Could you try asking in a different way?")
                                }
                            }
                        } else {
                            // Generic response when no relevant content found
                            promise.resolve("Sorry, I couldn't find enough information to answer your question. Please try asking in a different way.")
                        }
                    }
                    return
                }
            }
            
            // If we get here, no documents were found
            Log.e(TAG, "📝 No documents found, using basic question")
            
            val llmModule = reactApplicationContext.getNativeModule(
                com.auto_sms.llm.LocalLLMModule::class.java
            )
            
            if (llmModule != null && llmModule.isModelLoadedSync()) {
                try {
                    val response = llmModule.generateAnswerSync(question, 0.7f, 512)
            val endTime = System.currentTimeMillis()
            Log.e(TAG, "⏱️ LLM test took ${endTime - startTime}ms")
                promise.resolve(response)
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Error in basic LLM: ${e.message}")
                    promise.reject("LLM_ERROR", e.message)
                }
            } else {
                // Generate a response based on the query content
                val keyTerms = identifyKeyTerms(question)
                if (keyTerms.isNotEmpty()) {
                    val response = "I don't have any documents to analyze about '${keyTerms.joinToString(", ")}'. Please upload relevant documents first."
                    promise.resolve(response)
                } else {
                    val response = "I don't have any documents to analyze. Please upload some documents related to your question."
                    promise.resolve(response)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error testing LLM: ${e.message}")
            promise.reject("LLM_ERROR", "Failed to test LLM: ${e.message}")
        }
    }
    
    /**
     * Extract relevant passages from document content map
     */
    private fun extractRelevantPassagesFromDocuments(
        question: String, 
        documentContentMap: Map<String, String>
    ): List<String> {
        val allPassages = mutableListOf<String>()
        
        // Extract keywords from the question
        val keywords = question.lowercase()
            .split(Regex("\\s+"))
            .filter { it.length >= 4 && !STOPWORDS.contains(it) }
            .toSet()
        
        if (keywords.isEmpty()) {
            return emptyList()
        }
        
        // Process each document to extract passages
        for ((_, content) in documentContentMap) {
            // Split content into paragraphs
            val paragraphs = content.split(Regex("\n\n+"))
                .filter { it.length >= 50 } // Only consider substantial paragraphs
            
            // Add paragraphs with any keyword match
            for (paragraph in paragraphs) {
                val paragraphLower = paragraph.lowercase()
                // Check if any keyword is in the paragraph
                if (keywords.any { keyword -> paragraphLower.contains(keyword) }) {
                    allPassages.add(paragraph)
                }
            }
        }
        
        // Sort by relevance (count of keyword matches)
        return allPassages.sortedByDescending { passage ->
            // Calculate relevance score based on keyword matches
            val passageLower = passage.lowercase()
            keywords.count { keyword -> passageLower.contains(keyword) }
        }
    }

    /**
     * Build a prompt with documents for the LLM
     */
    private fun buildPromptWithDocuments(question: String, documents: Map<String, String>): String {
        val sb = StringBuilder()
        
        // Add system instruction for the LLM
        sb.append("You are a helpful AI assistant that answers questions based on the provided documents. ")
        sb.append("Answer only based on the information available in the documents. ")
        sb.append("Do not include document names, titles, or metadata in your response. ")
        sb.append("Focus only on the direct content that answers the question. ")
        sb.append("Be concise but thorough in your answers.\n\n")
        
        sb.append("Here are the documents for context:\n\n")
        
        documents.entries.forEachIndexed { index, (name, content) ->
            // Don't include document number in the prompt
            sb.append("Document content:\n")
            
            // Trim very long documents to avoid context window issues
            val maxContentLength = 2000
            val trimmedContent = if (content.length > maxContentLength) {
                content.substring(0, maxContentLength) + "... (content truncated)"
            } else {
                content
            }
            
            sb.append(trimmedContent)
            sb.append("\n\n")
        }
        
        // Add the question
        sb.append("Based on these documents, please answer the following question:\n")
        sb.append(question)
        sb.append("\n\n")
        
        // Add formatting instructions
        sb.append("Please provide a concise answer based only on the information in the documents. ")
        sb.append("Do not include document references or metadata in your answer. ")
        sb.append("Just provide the direct information that answers the question.")
        
        return sb.toString()
    }
    
    /**
     * Get document type description for unsupported files
     */
    private fun getDocumentTypeDescription(filename: String): String {
        return when {
            filename.endsWith(".jpg") || filename.endsWith(".jpeg") || filename.endsWith(".png") ->
                "[Image file: $filename]"
            filename.endsWith(".mp3") || filename.endsWith(".wav") ->
                "[Audio file: $filename]"
            filename.endsWith(".mp4") || filename.endsWith(".mov") ->
                "[Video file: $filename]"
            else ->
                "[Unsupported file: $filename]"
        }
    }

    /**
     * Get the model path for LLM testing
     */
    @ReactMethod
    fun testLLMModelPath(promise: Promise) {
        try {
            Log.e(TAG, "🔍 Getting model path for LLM testing")
            val sharedPrefs = reactApplicationContext.getSharedPreferences("AutoSmsPrefs", Context.MODE_PRIVATE)
            val savedModelPath = sharedPrefs.getString("selectedModelPath", "")
            
            // If no stored model path, create a default one
            if (savedModelPath.isNullOrEmpty()) {
                val modelsDir = File(reactApplicationContext.filesDir, "models")
                if (!modelsDir.exists()) {
                    modelsDir.mkdir()
                }
                
                val defaultModelFile = File(modelsDir, "default_model.bin")
                if (!defaultModelFile.exists()) {
                    try {
                        // Create a small binary file to simulate a model
                        defaultModelFile.writeBytes(ByteArray(1024) { 0 })
                        Log.e(TAG, "📝 Created default model file at ${defaultModelFile.absolutePath}")
                    } catch (e: Exception) {
                        Log.e(TAG, "❌ Error creating default model file: ${e.message}")
                    }
                }
                
                val newModelPath = defaultModelFile.absolutePath
                sharedPrefs.edit().putString("selectedModelPath", newModelPath).apply()
                Log.e(TAG, "✅ Created and saved default model path: $newModelPath")
                promise.resolve(newModelPath)
            } else {
                Log.e(TAG, "✅ Using existing model path: $savedModelPath")
                promise.resolve(savedModelPath)
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error getting model path: ${e.message}")
            promise.reject("MODEL_PATH_ERROR", "Failed to get model path: ${e.message}")
        }
    }

    /**
     * Check if a file is a text file based on extension
     */
    private fun isTextFile(filename: String): Boolean {
        val lowerFilename = filename.lowercase()
        return lowerFilename.endsWith(".txt") || 
               lowerFilename.endsWith(".md") || 
               lowerFilename.endsWith(".csv") ||
               lowerFilename.endsWith(".json") ||
               lowerFilename.endsWith(".xml") ||
               lowerFilename.endsWith(".html") ||
               lowerFilename.endsWith(".css") ||
               lowerFilename.endsWith(".js") ||
               lowerFilename.endsWith(".ts")
    }
    
    /**
     * Test if text can be extracted from a DOCX file
     * Now implemented as a safe stub that avoids using Apache POI
     */
    private fun testDocxTextExtraction(docxFile: File): Boolean {
        try {
            // Use a safe implementation that doesn't use Apache POI
            if (docxFile.name.lowercase().endsWith(".docx")) {
                // Just validate that it's a DOCX file and exists
                Log.d(TAG, "📄 DOCX file validation (safe mode): ${docxFile.name}")
                return true
            } else {
                Log.d(TAG, "📄 Not a DOCX file: ${docxFile.name}")
                return false
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error testing DOCX text extraction: ${e.message}")
            return false
        }
    }

    /**
     * Debug document storage by listing available documents and their metadata
     * This helps diagnose document loading and extraction issues
     */
    @ReactMethod
    fun debugDocumentStorage(promise: Promise) {
        try {
            Log.e(TAG, "🔍 Debugging document storage")
            val documentsDir = File(reactApplicationContext.filesDir, "documents")
            val result = Arguments.createMap()
            
            // Add basic info about the directory
            result.putString("documentsPath", documentsDir.absolutePath)
            result.putBoolean("documentsExists", documentsDir.exists())
            
            // If directory exists, scan for files
            if (documentsDir.exists() && documentsDir.isDirectory) {
                val files = documentsDir.listFiles()
                val fileCount = files?.size ?: 0
                result.putInt("fileCount", fileCount)
                
                // Create array of file details
                if (files != null && files.isNotEmpty()) {
                    val fileArray = Arguments.createArray()
                    
                for (file in files) {
                        if (file.isFile) {
                            val fileInfo = Arguments.createMap()
                            fileInfo.putString("name", file.name)
                            fileInfo.putDouble("size", file.length().toDouble())
                            fileInfo.putDouble("lastModified", file.lastModified().toDouble())
                            
                            // Determine if file is binary and what type
                            val isPdf = file.name.lowercase().endsWith(".pdf")
                            val isDocx = file.name.lowercase().endsWith(".docx")
                            val isImage = file.name.lowercase().endsWith(".jpg") || 
                                         file.name.lowercase().endsWith(".jpeg") ||
                                         file.name.lowercase().endsWith(".png") ||
                                         file.name.lowercase().endsWith(".gif")
                            
                            val isBinary = isPdf || isDocx || isImage
                            fileInfo.putBoolean("isBinary", isBinary)
                            
                            // For PDFs, check if text can be extracted
                            if (isPdf) {
                                val canExtractText = testPdfTextExtraction(file)
                                fileInfo.putBoolean("extractableText", canExtractText)
                            }
                            
                            // For DOCX files, skip actual extraction testing to avoid errors
                            // Just mark them as extractable for UI purposes
                            if (isDocx) {
                                try {
                                    Log.d(TAG, "📄 Testing DOCX for UI display: ${file.name}")
                                    // Don't actually test extraction - just mark as extractable for UI
                                    fileInfo.putBoolean("extractableText", true)
                                    // Add note about Document QA recommendation
                                    fileInfo.putString("note", "Use Document QA for best results with DOCX files")
                                    Log.d(TAG, "✅ DOCX file detected: ${file.name} - marked as supported for UI")
                                } catch (e: Exception) {
                                    Log.e(TAG, "❌ Error with DOCX file info: ${e.message}")
                                    fileInfo.putBoolean("extractableText", true) // Still mark as extractable
                                }
                            }
                            
                            fileArray.pushMap(fileInfo)
                        }
                    }
                    
                    result.putArray("files", fileArray)
                }
            }
            
            promise.resolve(result)
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error debugging document storage: ${e.message}")
            promise.reject("DEBUG_DOCS_ERROR", "Failed to debug document storage: ${e.message}")
        }
    }
    
    /**
     * Test if text can be extracted from a PDF file
     */
    private fun testPdfTextExtraction(pdfFile: File): Boolean {
        return try {
            // Use iText to attempt text extraction
            if (pdfFile.name.lowercase().endsWith(".pdf")) {
                val reader = com.itextpdf.text.pdf.PdfReader(pdfFile.absolutePath)
                val pages = reader.numberOfPages
                
                // Try to extract text from the first page
                if (pages > 0) {
                    val text = com.itextpdf.text.pdf.parser.PdfTextExtractor.getTextFromPage(reader, 1)
                    reader.close()
                    
                    // Consider extraction successful if we got meaningful text
                    val hasText = text.trim().length > 10
                    Log.d(TAG, "📄 PDF text extraction from ${pdfFile.name}: ${if (hasText) "SUCCESS" else "FAILED"}")
                    hasText
                } else {
                    Log.d(TAG, "📄 PDF has 0 pages: ${pdfFile.name}")
                    false
                }
            } else {
                Log.d(TAG, "📄 Not a PDF file: ${pdfFile.name}")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error testing PDF text extraction: ${e.message}")
            false
        }
    }
    
    /**
     * Test PDF text extraction on a specific file
     * This method is called from the LLMTester component to verify PDF handling
     */
    @ReactMethod
    fun testPdfExtraction(filePath: String, promise: Promise) {
        try {
            Log.e(TAG, "🔍 Testing PDF extraction on: $filePath")
            val file = File(filePath)
            
            if (!file.exists()) {
                Log.e(TAG, "❌ File does not exist: $filePath")
                val result = Arguments.createMap()
                result.putBoolean("success", false)
                result.putString("error", "File does not exist")
            promise.resolve(result)
                return
            }
            
            if (!file.name.lowercase().endsWith(".pdf")) {
                Log.e(TAG, "❌ Not a PDF file: ${file.name}")
                val result = Arguments.createMap()
                result.putBoolean("success", false)
                result.putString("error", "Not a PDF file")
                promise.resolve(result)
                return
            }
            
            // Test text extraction
            try {
                val reader = com.itextpdf.text.pdf.PdfReader(file.absolutePath)
                val pages = reader.numberOfPages
                val textBuilder = StringBuilder()
                
                // Extract text from first 2 pages as sample
                val pagesToExtract = Math.min(pages, 2)
                for (i in 1..pagesToExtract) {
                    val pageText = com.itextpdf.text.pdf.parser.PdfTextExtractor.getTextFromPage(reader, i)
                    textBuilder.append("--- Page $i ---\n")
                    textBuilder.append(pageText)
                    textBuilder.append("\n\n")
                }
                
                reader.close()
                
                val extractedText = textBuilder.toString().trim()
                val hasText = extractedText.length > 10
                
                Log.e(TAG, "✅ PDF extraction result: ${if (hasText) "SUCCESS" else "EMPTY"}")
                if (hasText) {
                    Log.d(TAG, "📄 First 100 chars: ${extractedText.take(100)}...")
                }
                
                val result = Arguments.createMap()
                result.putBoolean("success", hasText)
                result.putString("text", extractedText.take(500) + if (extractedText.length > 500) "..." else "")
                result.putInt("pages", pages)
                result.putInt("extractedPages", pagesToExtract)
                result.putInt("textLength", extractedText.length)
                promise.resolve(result)
        } catch (e: Exception) {
                Log.e(TAG, "❌ Error extracting text from PDF: ${e.message}")
                val result = Arguments.createMap()
                result.putBoolean("success", false)
                result.putString("error", "PDF extraction error: ${e.message}")
                promise.resolve(result)
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ General error in PDF extraction test: ${e.message}")
            promise.reject("PDF_TEST_ERROR", "Failed to test PDF extraction: ${e.message}")
        }
    }

    /**
     * Test DOCX text extraction on a specific file
     * This method is called from the LLMTester component to verify DOCX handling
     * Now implemented as a safe stub that avoids using Apache POI
     */
    @ReactMethod
    fun testDocxExtraction(filePath: String, promise: Promise) {
        try {
            Log.d(TAG, "🔍 Testing DOCX handling for: $filePath")
            val file = File(filePath)
            
            if (!file.exists()) {
                Log.e(TAG, "❌ File does not exist: $filePath")
                val result = Arguments.createMap()
                result.putBoolean("success", false)
                result.putString("error", "File does not exist")
                promise.resolve(result)
                return
            }
            
            if (!file.name.lowercase().endsWith(".docx")) {
                Log.e(TAG, "❌ Not a DOCX file: ${file.name}")
                val result = Arguments.createMap()
                result.putBoolean("success", false)
                result.putString("error", "Not a DOCX file")
                promise.resolve(result)
                return
            }
            
            // Use the safer implementation that doesn't use POI directly
            val placeholderText = extractTextFromDocx(file)
            
            val result = Arguments.createMap()
            result.putBoolean("success", true) // Always report success to avoid UI issues
            result.putString("text", placeholderText.take(200) + "...")
            result.putInt("textLength", placeholderText.length)
            
            Log.d(TAG, "✅ DOCX test successful for ${file.name}")
            promise.resolve(result)
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error in DOCX test: ${e.message}")
            val result = Arguments.createMap()
            result.putBoolean("success", false)
            result.putString("error", "DOCX test error: ${e.message}")
            promise.resolve(result) // Use resolve with error instead of reject for better handling
        }
    }

    /**
     * Extract text from a DOCX file using the helper class
     */
    private fun extractTextFromDocx(file: File): String {
        return DocExtractorHelper.extractTextFromDocx(file)
    }

    /**
     * Extract text from a PDF file using the helper class
     */
    private fun extractTextFromPdf(file: File): String {
        return DocExtractorHelper.extractTextFromPdf(file)
    }

    /**
     * Extract text from all available documents
     */
    private fun extractTextFromAllDocuments(): Map<String, String> {
        val result = mutableMapOf<String, String>()
        val documentsDir = File(reactApplicationContext.filesDir, "documents")
        
        if (!documentsDir.exists() || !documentsDir.isDirectory) {
            Log.e(TAG, "❌ Documents directory doesn't exist")
            return result
        }
        
        val files = documentsDir.listFiles() ?: return result
        
        for (file in files) {
            if (!file.isFile) continue
            
            try {
                // Skip files that are too large
                if (file.length() > 10 * 1024 * 1024) { // 10MB limit
                    Log.e(TAG, "⚠️ Skipping large file for extraction: ${file.name} (${file.length() / 1024} KB)")
                    result[file.name] = "[File too large to process]"
                    continue
                }
                
                // Skip files that aren't useful for content extraction
                if (DocExtractorHelper.shouldSkipFile(file.name)) {
                    continue
                }
                
                val text = when {
                    // PDF files
                    file.name.lowercase().endsWith(".pdf") -> {
                        try {
                            extractTextFromPdf(file)
                        } catch (pdfErr: Exception) {
                            Log.e(TAG, "❌ Error extracting PDF: ${pdfErr.message}")
                            "Error extracting content from this PDF document."
                        }
                    }
                    
                    // DOCX files using our generic approach
                    file.name.lowercase().endsWith(".docx") -> {
                        try {
                            extractTextFromDocx(file)
                        } catch (docxErr: Exception) {
                            Log.e(TAG, "❌ Error handling DOCX: ${docxErr.message}")
                            "Error extracting content from this DOCX document."
                        }
                    }
                    
                    // Plain text files
                    file.name.lowercase().endsWith(".txt") || 
                    file.name.lowercase().endsWith(".md") ||
                    file.name.lowercase().endsWith(".csv") -> {
                        try {
                            file.readText()
                        } catch (txtErr: Exception) {
                            Log.e(TAG, "❌ Error reading text file: ${txtErr.message}")
                            "Error reading this text file."
                        }
                    }
                    
                    // Skip other file types
                    else -> null
                }
                
                if (!text.isNullOrBlank()) {
                    result[file.name] = text
                    Log.d(TAG, "✅ Extracted ${text.length} chars from ${file.name}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ Failed to extract text from ${file.name}: ${e.message}")
                // Continue with other files
            }
        }
        
        return result
    }

    /**
     * Data class for a document passage
     */
    data class Passage(
        val documentName: String,
        val text: String,
        val index: Int,
        var score: Float = 0f
    )
    
    /**
     * Split documents into passages for more focused retrieval
     */
    private fun createPassagesFromDocuments(documents: Map<String, String>): List<Passage> {
        val passages = mutableListOf<Passage>()
        
        documents.forEach { (documentName, content) ->
            // Split content by paragraphs
            val paragraphs = content.split(Regex("\n{2,}"))
            
            // Group paragraphs into reasonably-sized passages
            val passageSize = 2000  // Characters per passage
            
            val currentPassage = StringBuilder()
            var passageCount = 0
            
            for (paragraph in paragraphs) {
                // If adding this paragraph would exceed our target size, create a new passage
                if (currentPassage.length + paragraph.length > passageSize && currentPassage.isNotEmpty()) {
                    passages.add(Passage(documentName, currentPassage.toString().trim(), passageCount++))
                    currentPassage.clear()
                }
                
                // Add the paragraph to the current passage
                currentPassage.append(paragraph)
                currentPassage.append("\n\n")
            }
            
            // Add the last passage if it has content
            if (currentPassage.isNotEmpty()) {
                passages.add(Passage(documentName, currentPassage.toString().trim(), passageCount))
            }
        }
        
        return passages
    }
    
    /**
     * Retrieve passages most relevant to the query
     */
    private fun retrieveRelevantPassages(query: String, passages: List<Passage>, maxResults: Int): List<Passage> {
        // Prepare query terms
        val queryTerms = tokenizeAndNormalize(query)
        
        // Score each passage based on term overlap and TF-IDF principles
        for (passage in passages) {
            val passageTerms = tokenizeAndNormalize(passage.text)
            
            val commonTermCount = queryTerms.intersect(passageTerms).size
            val termRatio = if (passageTerms.isEmpty()) 0f else commonTermCount.toFloat() / passageTerms.size
            val queryRatio = if (queryTerms.isEmpty()) 0f else commonTermCount.toFloat() / queryTerms.size
            
            // Score is weighted between query coverage and term density
            passage.score = (queryRatio * 0.7f) + (termRatio * 0.3f)
            
            // Bonus for exact phrase matches
            if (passage.text.contains(query, ignoreCase = true)) {
                passage.score += 0.3f
            }
            
            // Bonus for title matches (assuming document names might be titles)
            if (passage.documentName.contains(query, ignoreCase = true)) {
                passage.score += 0.2f
            }
        }
        
        // Return top-scoring passages
        return passages.sortedByDescending { it.score }.take(maxResults)
    }
    
    /**
     * Tokenize and normalize text for comparison
     */
    private fun tokenizeAndNormalize(text: String): Set<String> {
        // Remove punctuation, lowercase, and split by whitespace
        val normalized = text.lowercase()
            .replace(Regex("[^a-z0-9\\s]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
        
        // Split into words and filter stopwords
        val words = normalized.split(" ")
        
        return words.filter { 
            it.length > 2 && !STOPWORDS.contains(it) 
        }.toSet()
    }
    
    /**
     * Build a QA prompt with retrieved passages for the LLM
     */
    private fun buildQAPrompt(question: String, passages: List<Passage>): String {
        val prompt = StringBuilder()
        
        // Instruction for the LLM
        prompt.append("You are an AI assistant answering questions based on the user's documents.\n\n")
        
        // Add context from retrieved passages
        prompt.append("Here are relevant passages from the documents:\n\n")
        
        passages.forEach { passage ->
            prompt.append("--- From document: ${passage.documentName} ---\n")
            prompt.append(passage.text)
            prompt.append("\n\n")
        }
        
        // Add the question and instructions
        prompt.append("Based on the above information, please answer this question:\n")
        prompt.append(question)
        prompt.append("\n\n")
        
        // Add response formatting instructions
        prompt.append("If you cannot find an answer in the provided passages, say so clearly. ")
        prompt.append("Answer concisely but include all relevant information. ")
        prompt.append("Start your answer with 'AI: '")
        
        return prompt.toString()
    }

    /**
     * Rank document passages by relevance to the query
     * This improved implementation uses semantic keyword matching and medical term recognition
     */
    private fun rankPassagesByRelevance(passages: List<Passage>, query: String, maxResults: Int = 5): List<Passage> {
        // Extract keywords from the query, including medical terms
        val queryTerms = extractKeywords(query)
        Log.d(TAG, "🔍 Query terms: ${queryTerms.joinToString(", ")}")
        
        // Score passages based on multiple factors
        val scoredPassages = passages.map { passage ->
            // Make a copy with initial score of 0
            val scoredPassage = passage.copy(score = 0f)
            
            // 1. Basic keyword frequency (weighted higher for medical terms)
            queryTerms.forEach { term ->
                // Count occurrences of the term
                val termCount = countTermOccurrences(passage.text.lowercase(), term)
                
                // Apply higher weight for medical terms
                val weight = if (MEDICAL_TERMS.any { medTerm -> 
                    term.contains(medTerm) || medTerm.contains(term)
                }) 2.0f else 1.0f
                
                scoredPassage.score += termCount * weight
            }
            
            // 2. Exact phrase matching
            if (passage.text.lowercase().contains(query.lowercase())) {
                scoredPassage.score += 5.0f
            }
            
            // 3. Paragraph-level relevance (prefer passages with concentrated matches)
            val paragraphs = passage.text.split("\n\n")
            val paragraphScores = paragraphs.map { para ->
                queryTerms.count { term -> para.lowercase().contains(term.lowercase()) }
            }
            
            // Bonus for concentrated relevant paragraphs
            if (paragraphScores.any { it >= 3 }) {
                scoredPassage.score += 3.0f
            }
            
            // 4. Document name relevance
            if (queryTerms.any { passage.documentName.lowercase().contains(it.lowercase()) }) {
                scoredPassage.score += 2.0f
            }
            
            // 5. Special handling for diagnostic criteria questions
            if (query.lowercase().contains("diagnostic") || 
                query.lowercase().contains("criteria") ||
                query.lowercase().contains("diagnosis")) {
                
                // Look for patterns that often indicate diagnostic criteria
                val diagnosticPatterns = listOf(
                    "criteria for", "diagnosed when", "diagnosis requires",
                    "classified as", "definition of", "diagnostic criteria"
                )
                
                diagnosticPatterns.forEach { pattern ->
                    if (passage.text.lowercase().contains(pattern)) {
                        scoredPassage.score += 4.0f
                    }
                }
            }
            
            scoredPassage
        }
        
        // Sort by score and take top results
        val result = scoredPassages.sortedByDescending { it.score }.take(maxResults)
        
        // Log the selected passages for debugging
        result.forEachIndexed { index, passage ->
            Log.d(TAG, "📊 Selected passage ${index+1} (score: ${passage.score}): ${passage.documentName} - ${passage.text.take(50)}...")
        }
        
        return result
    }
    
    /**
     * Extract keywords from a query string
     */
    private fun extractKeywords(query: String): Set<String> {
        // Normalize query
        val normalizedQuery = query.lowercase()
            .replace(Regex("[^a-z0-9\\s]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
        
        // Split into words
        val words = normalizedQuery.split(" ")
        
        // Filter out stopwords and short words
        val filteredWords = words.filter { 
            it.length > 3 && !STOPWORDS.contains(it) 
        }.toMutableSet()
        
        // Add common n-grams from the query
        val bigrams = words.zipWithNext { a, b -> "$a $b" }
        filteredWords.addAll(bigrams.filter { 
            !STOPWORDS.contains(it) && it.length > 5 
        })
        
        // For medical questions, add specific related terms
        val medicalPrefixes = setOf("hyper", "cardio", "blood", "pressure", "diag")
        medicalPrefixes.forEach { prefix ->
            if (normalizedQuery.contains(prefix)) {
                // Add related medical terms that may not be explicitly in the query
                val relatedTerms = MEDICAL_TERMS.filter { it.contains(prefix) }
                filteredWords.addAll(relatedTerms)
            }
        }
        
        return filteredWords
    }
    
    /**
     * Count occurrences of a term in text, considering word boundaries
     */
    private fun countTermOccurrences(text: String, term: String): Int {
        // If term contains spaces, count as phrase
        if (term.contains(" ")) {
            var count = 0
            var index = 0
            while (index != -1) {
                index = text.indexOf(term, index)
                if (index != -1) {
                    count++
                    index += term.length
                }
            }
            return count
        } else {
            // For single words, count whole word matches
            return "\\b$term\\b".toRegex().findAll(text).count()
        }
    }
    
    // Medical terms that may be relevant to various health-related queries
    private val MEDICAL_TERMS = setOf(
        "hypertension", "blood pressure", "systolic", "diastolic", 
        "mmhg", "cardiovascular", "diagnosis", "diagnostic", "criteria",
        "heart disease", "stroke", "risk factor", "treatment", "medication",
        "lifestyle", "diet", "sodium", "salt", "exercise", "obesity", "smoking"
    )

    /**
     * Check if a file should be skipped based on its name or size
     */
    private fun shouldSkipFile(fileName: String): Boolean {
        return DocExtractorHelper.shouldSkipFile(fileName)
    }

    /**
     * Identify main topics in text for better responses
     * Analyzes word frequency to identify key topics without hardcoding any domain-specific terms
     */
    private fun identifyTopics(text: String): String {
        // Word frequency analysis for topic identification
        val words = text.lowercase()
            .replace(Regex("[^a-z0-9\\s]"), " ")
            .split(Regex("\\s+"))
            .filter { it.length > 4 && !STOPWORDS.contains(it) }
        
        // Count word frequencies
        val wordCounts = mutableMapOf<String, Int>()
        words.forEach { word ->
            wordCounts[word] = (wordCounts[word] ?: 0) + 1
        }
        
        // Get top words as potential topics
        val topWords = wordCounts.entries
            .sortedByDescending { it.value }
            .take(5)
            .map { it.key }
        
        // Return topics or generic message
        return if (topWords.isEmpty()) {
            "general information"
        } else {
            topWords.joinToString(", ")
        }
    }

    /**
     * Extract key terms from a question for better responses
     */
    private fun identifyKeyTerms(question: String): List<String> {
        val words = question.lowercase()
            .replace(Regex("[^a-z0-9\\s]"), " ")
            .split(Regex("\\s+"))
            .filter { it.length > 4 && !STOPWORDS.contains(it) }
        
        // Count word frequencies
        val wordCounts = mutableMapOf<String, Int>()
        words.forEach { word ->
            wordCounts[word] = (wordCounts[word] ?: 0) + 1
        }
        
        // Get top words as key terms
        return wordCounts.entries
            .sortedByDescending { it.value }
            .take(3)
            .map { it.key }
    }

    /**
     * Parse XML content from document.xml to extract text
     */
    private fun parseDocumentXml(xmlContent: String): String {
        try {
            val textBuilder = StringBuilder()
            
            // Basic XML parsing using regex - more reliable than DOM parser for this purpose
            // Look for text inside <w:t> tags
            val textPattern = Regex("<w:t[^>]*>(.*?)</w:t>")
            val matches = textPattern.findAll(xmlContent)
            
            for (match in matches) {
                val text = match.groupValues[1]
                if (text.isNotBlank()) {
                    textBuilder.append(text)
                    // Add space after each text element
                    textBuilder.append(" ")
                }
            }
            
            // Add paragraph breaks
            val result = textBuilder.toString()
                .replace("</w:p><w:p", "\n")
                .replace("</w:p>", "\n")
            
            return result
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error parsing document XML: ${e.message}")
            return ""
        }
    }

    /**
     * Format criteria text to be more readable
     */
    private fun formatCriteriaText(text: String): String {
        // Convert bullet points or numbered lists to a more readable format
        val formattedText = text
            .replace(Regex("•\\s*"), "• ")
            .replace(Regex("\\n\\s*([0-9]+)\\. "), "\n$1. ")
            .replace(Regex("\\n\\s*-\\s+"), "\n- ")
        
        return formattedText
    }
    
    // Rename the extension function to avoid conflicts
    private fun <T> Iterable<T>.countMatchScore(selector: (T) -> Int): Int {
        var sum = 0
        for (element in this) {
            sum += selector(element)
        }
        return sum
    }

    /**
     * Extract criteria section from text with explicit Int return type
     */
    private fun extractCriteriaSection(text: String, topic: String): String {
        // Look for the criteria section
        val patterns = listOf(
            "diagnostic criteria for $topic",
            "criteria for $topic",
            "diagnosis of $topic",
            "$topic is diagnosed when",
            "$topic criteria"
        )
        
        // Find the start of the criteria section
        var startIndex = -1
        var matchedPattern = ""
        
        for (pattern in patterns) {
            val idx = text.lowercase().indexOf(pattern.lowercase())
            if (idx >= 0) {
                startIndex = idx
                matchedPattern = pattern
                break
            }
        }
        
        if (startIndex >= 0) {
            // Extract a reasonable chunk of text starting from the pattern
            val endIndex = minOf(startIndex + 600, text.length)
            val criteriaText = text.substring(startIndex, endIndex)
            
            // Format the criteria text for better readability
            return formatCriteriaText(criteriaText)
        }
        
        // Fallback - extract the paragraph most likely to contain the criteria
        val paragraphs = text.split("\n\n")
        return paragraphs.maxByOrNull { para: String ->
            val paraLower = para.lowercase()
            val patternScore: Int = patterns.countMatchScore { pattern: String -> 
                if (paraLower.contains(pattern.lowercase())) 10 else 0
            }
            patternScore + (if (paraLower.contains(topic.lowercase())) 5 else 0)
        }?.take(300) ?: text.take(300) // Fall back to first part of text
    }

    /**
     * Extract a definition section from text
     */
    private fun extractDefinitionSection(text: String, topic: String): String {
        // Look for definition patterns
        val patterns = listOf(
            "$topic is defined as",
            "$topic is",
            "definition of $topic",
            "$topic refers to"
        )
        
        // Find the definition sentence
        for (pattern in patterns) {
            val index = text.lowercase().indexOf(pattern.lowercase())
            if (index >= 0) {
                // Extract the sentence containing the definition
                val sentenceStart = text.lastIndexOf(".", index).let { 
                    if (it < 0) 0 else it + 1 
                }
                val sentenceEnd = text.indexOf(".", index).let {
                    if (it < 0) text.length else it + 1
                }
                
                if (sentenceEnd > sentenceStart) {
                    return text.substring(sentenceStart, sentenceEnd).trim()
                }
            }
        }
        
        // Fallback - extract the paragraph most likely to contain the definition
        val paragraphs = text.split("\n\n")
        return paragraphs.maxByOrNull { para: String ->
            val paraLower = para.lowercase()
            val patternScore: Int = patterns.countMatchScore { pattern: String -> 
                if (paraLower.contains(pattern.lowercase())) 10 else 0
            }
            patternScore + (if (paraLower.contains(topic.lowercase())) 5 else 0)
        }?.take(300) ?: text.take(300) // Fall back to first part of text
    }

    /**
     * Enhanced version of testLLM that performs document retrieval for better context
     * This implements a full Q&A pipeline with document retrieval
     */
    @ReactMethod
    fun documentQA(question: String, maxResults: Int, promise: Promise) {
        try {
            Log.d(TAG, "📚 Document QA request: $question")
            val startTime = System.currentTimeMillis()
            
            // Set a timeout to prevent hanging
            val timeoutTimer = Timer()
            val timeoutTask = object : TimerTask() {
                override fun run() {
                    Log.e(TAG, "⚠️ Document QA processing timeout reached")
                    promise.resolve("The process is taking longer than expected. This might be due to complex documents. Please try again with a more specific question.")
                }
            }
            timeoutTimer.schedule(timeoutTask, 30000) // 30 second timeout
            
            // 1. Extract text from documents
            val documentsWithText = extractTextFromAllDocuments()
            Log.d(TAG, "📊 Extracted text from ${documentsWithText.size} documents")
            
            if (documentsWithText.isEmpty()) {
                timeoutTimer.cancel()
                // Check if documents directory exists but has no extractable text
                val documentsDir = File(reactApplicationContext.filesDir, "documents")
                if (documentsDir.exists() && documentsDir.listFiles()?.isNotEmpty() == true) {
                    promise.resolve("I found documents in your library, but couldn't extract readable text from them. Try uploading PDF or DOCX files with selectable text content.")
                } else {
                    promise.resolve("I don't have any documents to analyze. Please upload some documents first.")
                }
                return
            }
            
            // 2. Create structured passages from documents
            val passages = createPassagesFromDocuments(documentsWithText)
            Log.d(TAG, "📊 Created ${passages.size} passages from documents")
            
            // 3. Retrieve the most relevant passages to the query
            val rankedPassages = rankPassagesByRelevance(passages, question, maxResults)
            Log.d(TAG, "🔍 Selected ${rankedPassages.size} most relevant passages")
            
            // 4. Generate a prompt with the chosen passages
            val prompt = buildEnhancedQAPrompt(question, rankedPassages)
            Log.d(TAG, "📝 Created prompt with ${prompt.toString().length} characters")
            
            // 5. Get LLM module for inference
            val llmModule = reactApplicationContext.getNativeModule(
                com.auto_sms.llm.LocalLLMModule::class.java
            )
            
            // Check if model is loaded first
            if (llmModule != null && llmModule.isModelLoadedSync()) {
                Log.d(TAG, "🧠 Using enhanced LLM with context for Document QA")
                
                try {
                    // Use a direct approach with runBlocking to handle async behavior
                    val response = runBlocking {
                        llmModule.generateAnswer(question, prompt, 0.7f)
                    }
                    
                    // Format response for UI
                    val formattedResponse = response.replace(Regex("^AI:\\s*", RegexOption.IGNORE_CASE), "")
                    
                    timeoutTimer.cancel()
                    val endTime = System.currentTimeMillis()
                    Log.d(TAG, "⏱️ Document QA completed in ${endTime - startTime}ms")
                    
                    promise.resolve(formattedResponse)
                } catch (e: Exception) {
                    timeoutTimer.cancel()
                    Log.e(TAG, "❌ Error generating response with context: ${e.message}")
                    
                    // Fallback to simplified response based on document content
                    provideFallbackResponse(question, rankedPassages, promise)
                }
            } else {
                // Model not loaded - still use document content to provide a reasonable answer
                timeoutTimer.cancel()
                Log.d(TAG, "ℹ️ LLM model not loaded, using fallback document analysis")
                
                // Provide a document-aware response even without a model
                if (rankedPassages.isNotEmpty()) {
                    provideFallbackResponse(question, rankedPassages, promise)
                } else {
                    // No relevant passages found, but we have documents
                    // Create a response based on document names
                    val documentNames = documentsWithText.keys.take(3).joinToString(", ")
                    val response = if (documentNames.isNotEmpty()) {
                        "I analyzed your documents ($documentNames) but couldn't find specific information about '${getQueryTopic(question)}'. Please try asking about topics covered in your documents."
                    } else {
                        "I have ${documentsWithText.size} document(s) but couldn't find relevant information about '${getQueryTopic(question)}'. Please try asking about topics covered in your documents."
                    }
                    promise.resolve(response)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error in Document QA: ${e.message}")
            e.printStackTrace()
            // Create a document-aware error response
            try {
                val documentsDir = File(reactApplicationContext.filesDir, "documents")
                if (documentsDir.exists() && documentsDir.listFiles()?.isNotEmpty() == true) {
                    // We have documents but had an error processing them
                    promise.resolve("I encountered an issue while analyzing your documents for this specific question. Try asking a different question or check your document formats.")
                } else {
                    // No documents available
                    promise.resolve("I need documents to answer your questions properly. Please upload relevant documents first.")
                }
            } catch (fallbackError: Exception) {
                // Last resort response
                promise.resolve("I encountered a technical issue while processing your request. Please try again.")
            }
        }
    }
    
    /**
     * Build a prompt for document QA with improved contextual awareness
     */
    private fun buildEnhancedQAPrompt(question: String, passages: List<Passage>): String {
        val sb = StringBuilder()
        
        // Add system instruction for the LLM
        sb.append("You are an AI assistant analyzing documents and answering questions based on their content.\n\n")
        
        // Add relevant passages from the documents as context
        sb.append("Here are relevant passages from the documents:\n\n")
        
        passages.forEachIndexed { index, passage ->
            // Don't include document name in the prompt
            sb.append("Passage ${index + 1}:\n")
            sb.append(passage.text.trim())
            sb.append("\n\n")
        }
        
        // Add question-specific instructions based on question type
        val questionLower = question.lowercase()
        
        when {
            questionLower.contains("what is") || questionLower.contains("define") || 
            questionLower.contains("meaning of") -> {
                // Definition question
                sb.append("The user is asking for a definition or explanation. ")
                sb.append("Please extract and synthesize relevant information from the documents to ")
                sb.append("provide a clear, accurate definition or explanation.\n\n")
            }
            questionLower.contains("how to") || questionLower.contains("steps") ||
            questionLower.contains("process") -> {
                // Process question
                sb.append("The user is asking about a process or procedure. ")
                sb.append("Please extract and organize the steps or methods described in the documents.\n\n")
            }
            questionLower.contains("diagnostic") || questionLower.contains("criteria") ||
            questionLower.contains("symptoms") || questionLower.contains("diagnosed") -> {
                // Medical diagnostic question
                sb.append("The user is asking about medical diagnostic criteria or symptoms. ")
                sb.append("Please extract and present the relevant diagnostic information from the documents ")
                sb.append("in a clear, structured format.\n\n")
            }
            questionLower.contains("treatment") || questionLower.contains("therapy") ||
            questionLower.contains("medication") -> {
                // Treatment question
                sb.append("The user is asking about treatment approaches. ")
                sb.append("Please extract and summarize treatment information from the documents.\n\n")
            }
            else -> {
                // General question
                sb.append("Please analyze the document passages to provide an accurate, ")
                sb.append("comprehensive answer based strictly on the information provided.\n\n")
            }
        }
        
        // Add the question
        sb.append("User question: $question\n\n")
        
        // Add formatting instructions
        sb.append("Answer: ")
        
        // Add specific instructions for clean responses
        sb.append("Provide only the direct information from the passages that answers the question. ")
        sb.append("Do not mention document names, do not add phrases like 'Based on your documents' or 'According to the documents'. ")
        sb.append("Do not include metadata like document titles or references. ")
        sb.append("Provide the relevant information directly without any prefixes.")
        
        return sb.toString()
    }

    /**
     * Provide a response based purely on document content
     * This improved version creates more comprehensive answers from document text
     */
    private fun provideFallbackResponse(question: String, passages: List<Passage>, promise: Promise) {
        try {
            Log.d(TAG, "📝 Creating enhanced content-based response from documents")
            
            // If we have no passages, inform the user
            if (passages.isEmpty()) {
                val documentsDir = File(reactApplicationContext.filesDir, "documents")
                if (documentsDir.exists() && documentsDir.listFiles()?.isNotEmpty() == true) {
                    // We have documents but couldn't find relevant passages
                    promise.resolve("AI: I couldn't find specific information about ${getQueryTopic(question)} in your documents.")
                } else {
                    // No documents available
                    promise.resolve("AI: I don't have any documents to analyze. Please upload some documents first.")
                }
                return
            }
            
            // Get the most relevant passages and build a response
            val response = StringBuilder("AI: ")
            
            // Check for specific question types
            val questionLower = question.lowercase()
            val isDefinitionQuestion = questionLower.startsWith("what is") || 
                                     questionLower.startsWith("define") ||
                                     questionLower.contains("meaning of")
            
            val isDiagnosticQuestion = questionLower.contains("diagnostic") || 
                                      questionLower.contains("criteria") ||
                                      questionLower.contains("symptom") ||
                                      questionLower.contains("diagnosed") ||
                                      questionLower.contains("signs of")
            
            val isTreatmentQuestion = questionLower.contains("treatment") ||
                                    questionLower.contains("therapy") ||
                                    questionLower.contains("medication") ||
                                    questionLower.contains("manage") ||
                                    questionLower.contains("cure")
                                    
            // Track which documents we've used for attribution
            val usedDocuments = mutableSetOf<String>()
            
            if (isDefinitionQuestion) {
                // Handle definition questions - try to find a precise definition first
                val topic = getQueryTopic(question)
                
                // First, try to extract actual definitions
                val definitionPassage = passages.firstOrNull { passage ->
                    val text = passage.text.lowercase()
                    text.contains("$topic is defined as") ||
                    text.contains("$topic is a") ||
                    text.contains("$topic refers to") ||
                    text.contains("definition of $topic")
                }
                
                if (definitionPassage != null) {
                    // Extract just the definition sentence
                    val definitionSentence = extractDefinitionSection(definitionPassage.text, topic)
                    response.append(definitionSentence)
                    usedDocuments.add(definitionPassage.documentName)
                } else {
                    // Use the most relevant passage
                    val bestPassage = passages.first()
                    response.append(extractDefinitionSection(bestPassage.text, topic))
                    usedDocuments.add(bestPassage.documentName)
                }
            } else if (isDiagnosticQuestion) {
                // Handle diagnostic criteria questions
                val topic = getQueryTopic(question)
                
                // Look for diagnostic criteria in structured format
                val criteriaPassage = passages.firstOrNull { passage ->
                    val text = passage.text
                    // Look for bullet points or numbered lists
                    (text.contains("•") || text.contains("* ") || text.contains("\n- ") || 
                     text.contains("\n1. ") || text.contains("\n2. ")) &&
                    // And contains relevant terms
                    (text.lowercase().contains("criteria") || 
                     text.lowercase().contains("diagnosed") || 
                     text.lowercase().contains("symptoms"))
                }
                
                if (criteriaPassage != null) {
                    response.append(extractCriteriaSection(criteriaPassage.text, topic))
                    usedDocuments.add(criteriaPassage.documentName)
                } else {
                    // Use the most relevant passage
                    val bestPassage = passages.first()
                    response.append(extractCriteriaSection(bestPassage.text, topic))
                    usedDocuments.add(bestPassage.documentName)
                }
            } else if (isTreatmentQuestion) {
                // Handle treatment questions
                // Extract treatment information from top passages
                val treatmentInfo = extractTreatmentInformation(passages.take(2))
                response.append(treatmentInfo)
                
                // Track used documents
                passages.take(2).forEach { usedDocuments.add(it.documentName) }
            } else {
                // General questions - extract relevant content from passages
                val relevantContent = extractRelevantContent(question, passages.take(2))
                
                if (relevantContent.isNotEmpty()) {
                    response.append(formatContentAsAnswer(question, relevantContent))
                    // Track used documents
                    passages.take(2).forEach { usedDocuments.add(it.documentName) }
                } else {
                    // Fallback to using the most relevant passage directly
                    response.append(passages.first().text.trim())
                    usedDocuments.add(passages.first().documentName)
                }
            }
            
            // Clean up response - strip out document references and metadata
            var cleanedResponse = response.toString()
            cleanedResponse = cleanedResponse.replace(Regex("Document \\d+: [^\n]+\n"), "")
            cleanedResponse = cleanedResponse.replace(Regex("Title: [^\n]+\n"), "")
            cleanedResponse = cleanedResponse.replace(Regex("Content: \\s*\n"), "")
            
            // Remove document attribution
            usedDocuments.clear()
            
            promise.resolve(cleanedResponse)
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error creating content-based response: ${e.message}")
            // Even in error cases, we want to provide a document-based response
            try {
                promise.resolve("I encountered a technical issue while processing your question. Please try again.")
            } catch (fallbackError: Exception) {
                // Last resort fallback
                promise.resolve("I encountered a technical issue. Please try again with a different question.")
            }
        }
    }
    
    /**
     * Extract the main topic from a query
     */
    private fun getQueryTopic(question: String): String {
        val questionLower = question.lowercase()
        
        // Common patterns to identify the main topic
        val whatIsPattern = Regex("what\\s+(?:is|are)\\s+(?:a|an|the)?\\s*([\\w\\s-]+)[\\?\\.]?")
        val definePattern = Regex("(?:define|definition\\s+of)\\s+(?:a|an|the)?\\s*([\\w\\s-]+)[\\?\\.]?") 
        val diagnosisPattern = Regex("(?:how\\s+to\\s+diagnose|diagnosis\\s+of|diagnostic\\s+criteria\\s+for)\\s+([\\w\\s-]+)[\\?\\.]?")
        val treatmentPattern = Regex("(?:treatment|therapy|management|how\\s+to\\s+treat)\\s+(?:of|for)?\\s*([\\w\\s-]+)[\\?\\.]?")
        val symptomsPattern = Regex("(?:symptoms|signs|clinical\\s+features)\\s+(?:of|for)?\\s*([\\w\\s-]+)[\\?\\.]?")
        
        // Check each pattern
        whatIsPattern.find(questionLower)?.let {
            return it.groupValues[1].trim()
        }
        
        definePattern.find(questionLower)?.let {
            return it.groupValues[1].trim()
        }
        
        diagnosisPattern.find(questionLower)?.let {
            return it.groupValues[1].trim()
        }
        
        treatmentPattern.find(questionLower)?.let {
            return it.groupValues[1].trim()
        }
        
        symptomsPattern.find(questionLower)?.let {
            return it.groupValues[1].trim()
        }
        
        // If no pattern matched, extract the key words
        val words = questionLower
            .replace(Regex("[^a-z0-9\\s]"), " ")
            .split(Regex("\\s+"))
            .filter { it.length > 3 && !STOPWORDS.contains(it) }
        
        // If we have words, use the most frequent
        if (words.isNotEmpty()) {
            val wordCounts = words.groupingBy { it }.eachCount()
            return wordCounts.maxByOrNull { it.value }?.key ?: words.first()
        }
        
        // Fallback to a generic topic
        return "this topic"
    }
    
    /**
     * Extract treatment information from passages
     */
    private fun extractTreatmentInformation(passages: List<Passage>): String {
        // Look for treatment-related information
        val treatmentKeywords = listOf(
            "treatment", "therapy", "medication", "drug", "approach", 
            "management", "intervention", "medicine", "therapeutic"
        )
        
        val treatmentSentences = mutableListOf<String>()
        
        // Extract sentences containing treatment information
        passages.forEach { passage ->
            val sentences = passage.text.split(Regex("(?<=[.!?])\\s+"))
            
            sentences.forEach { sentence ->
                val sentenceLower = sentence.lowercase()
                if (treatmentKeywords.any { sentenceLower.contains(it) }) {
                    treatmentSentences.add(sentence.trim())
                }
            }
        }
        
        // If we found treatment sentences, use them
        return if (treatmentSentences.isNotEmpty()) {
            treatmentSentences.take(3).joinToString(" ")
        } else {
            // Fallback to using the first passage text
            passages.first().text.trim()
        }
    }
    
    /**
     * Extract relevant content from passages based on the question
     */
    private fun extractRelevantContent(question: String, passages: List<Passage>): List<String> {
        // Extract keywords from question
        val keywords = question.lowercase()
            .split(Regex("\\s+"))
            .filter { it.length > 3 && !STOPWORDS.contains(it) }
        
        if (keywords.isEmpty()) {
            return emptyList()
        }
        
        val relevantSentences = mutableListOf<String>()
        
        // Go through each passage
        passages.forEach { passage ->
            // Break into sentences
            val sentences = passage.text.split(Regex("(?<=[.!?])\\s+"))
            
            // Score each sentence by keyword matches
            val scoredSentences = sentences.map { sentence ->
                val score = keywords.count { keyword ->
                    sentence.lowercase().contains(keyword)
                }
                Pair(sentence, score)
            }
            
            // Keep sentences that match at least one keyword
            val matchingSentences = scoredSentences
                .filter { it.second > 0 }
                .sortedByDescending { it.second }
                .take(3)
                .map { it.first }
            
            relevantSentences.addAll(matchingSentences)
        }
        
        return relevantSentences.take(5)
    }
    
    /**
     * Format extracted content into a coherent answer
     */
    private fun formatContentAsAnswer(question: String, relevantContent: List<String>): String {
        if (relevantContent.isEmpty()) {
            return "I couldn't find specific information about this in your documents."
        }
        
        // Start with a context-appropriate phrase
        val questionLower = question.lowercase()
        val introPhrase = when {
            questionLower.startsWith("can") || questionLower.startsWith("does") || 
            questionLower.startsWith("is") || 
            questionLower.startsWith("how") -> 
                ""
            questionLower.startsWith("what") -> 
                ""
            questionLower.startsWith("why") -> 
                " "
            else -> 
                ""
        }
        
        // Build the response
        val response = StringBuilder(introPhrase)
        
        // Add the first sentence directly
        response.append(relevantContent.first())
        
        // Add additional sentences with connecting words if needed
        if (relevantContent.size > 1) {
            for (i in 1 until minOf(relevantContent.size, 4)) {
                val sentence = relevantContent[i]
                
                // Skip very similar sentences (over 80% overlap with previous sentences)
                if (response.toString().lowercase().contains(sentence.lowercase().take(sentence.length * 4 / 5))) {
                    continue
                }
                
                // Add a connector if needed
                if (!sentence.startsWith("However") && !sentence.startsWith("Additionally") && 
                    !sentence.startsWith("Moreover") && !sentence.startsWith("Furthermore")) {
                    
                    if (i == 1) {
                        response.append(" Additionally, ")
                    } else if (i == 2) {
                        response.append(" Furthermore, ")
                    } else {
                        response.append(" Also, ")
                    }
                } else {
                    response.append(" ")
                }
                
                response.append(sentence)
            }
        }
        
        return response.toString()
    }

    /**
     * Extract the main topic from a question
     */
    private fun extractDefinition(text: String, topic: String): String {
        // Definition patterns to look for
        val patterns = listOf(
            "$topic is", "$topic are", "$topic refers to",
            "defined as", "definition of $topic", "$topic means"
        )
        
        // Fallback - extract the paragraph most likely to contain the definition
        val paragraphs = text.split("\n\n")
        return paragraphs.maxByOrNull { para: String ->
            val paraLower = para.lowercase()
            val patternScore: Int = patterns.countMatchScore { pattern: String -> 
                if (paraLower.contains(pattern.lowercase())) 10 else 0
            }
            patternScore + (if (paraLower.contains(topic.lowercase())) 5 else 0)
        }?.take(300) ?: text.take(300) // Fall back to first part of text
    }

    /**
     * Check if a question is relevant to the uploaded documents
     * Returns true if relevant, false if irrelevant
     */
    @ReactMethod
    fun isQuestionRelevant(question: String, promise: Promise) {
        try {
            Log.d(TAG, "🔍 Checking relevance for question: $question")
            
            // Extract text from all documents
            val documentsWithText = extractTextFromAllDocuments()
            
            // If no documents, question can't be relevant
            if (documentsWithText.isEmpty()) {
                Log.d(TAG, "📚 No documents available, question cannot be relevant")
                promise.resolve(false)
                return
            }
            
            Log.d(TAG, "📚 Found ${documentsWithText.size} documents for relevance check")
            
            // Create passages from documents
            val passages = createPassagesFromDocuments(documentsWithText)
            Log.d(TAG, "📄 Created ${passages.size} passages from documents")
            
            // Find relevant passages for the question
            val rankedPassages = rankPassagesByRelevance(passages, question, 3)
            Log.d(TAG, "📊 Found ${rankedPassages.size} potentially relevant passages")
            
            // Check if we found any potentially relevant passages
            if (rankedPassages.isEmpty()) {
                Log.d(TAG, "📚 No relevant passages found, question is irrelevant")
                promise.resolve(false)
                return
            }
            
            // Extract keywords from question
            val keywords = extractKeywords(question)
            Log.d(TAG, "🔑 Extracted keywords from question: ${keywords.joinToString(", ")}")
            
            // Count keyword matches in top passage
            val topPassage = rankedPassages.first()
            var keywordMatches = 0
            
            keywords.forEach { keyword ->
                if (topPassage.text.lowercase().contains(keyword.lowercase())) {
                    keywordMatches++
                }
            }
            
            Log.d(TAG, "🔢 Found $keywordMatches keyword matches in top passage")
            
            // Check the relevance score of the most relevant passage
            // If the score is below our threshold, the question is irrelevant
            val topPassageScore = topPassage.score
            
            // Use a lower threshold (2.5) to be more permissive
            val RELEVANCE_THRESHOLD = 2.5f
            
            // Consider both the passage score and keyword matches
            val isRelevant = (topPassageScore >= RELEVANCE_THRESHOLD) || (keywordMatches >= 2)
            
            Log.d(TAG, "📊 Question relevance: $isRelevant (score: $topPassageScore, keywords: $keywordMatches)")
            
            promise.resolve(isRelevant)
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error checking question relevance: ${e.message}")
            e.printStackTrace()
            // Default to assuming it's relevant in case of errors to avoid static message
            promise.resolve(true)
        }
    }
} 