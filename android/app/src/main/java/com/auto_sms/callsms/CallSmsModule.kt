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
            promise.reject("PERMISSIONS_DENIED", "The app doesn't have the required permissions")
            return
        }

        if (isMonitoringCalls) {
            promise.resolve(true)
            return
        }

        try {
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
            
            currentActivity?.registerReceiver(callReceiver, intentFilter)
            isMonitoringCalls = true
            startCheckingMissedCalls()
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
            callReceiver?.let {
                currentActivity?.unregisterReceiver(it)
                callReceiver = null
            }
            isMonitoringCalls = false
            promise.resolve(true)
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping call monitoring: ${e.message}")
            promise.reject("STOP_MONITORING_ERROR", "Failed to stop monitoring calls: ${e.message}")
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
                if (lastPhoneState == TelephonyManager.CALL_STATE_RINGING && latestIncomingNumber != null) {
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
                    
                    if (date > fiveMinutesAgo && date > missedCallDetectedAt) {
                        Log.d(TAG, "Found recent missed call from call log: $number at $date")
                        // Send SMS for this missed call
                        sendSmsForMissedCall(number)
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
            promise.reject("PERMISSIONS_DENIED", "The app doesn't have the required permissions")
            return
        }

        try {
            val smsMessage = if (message.isEmpty()) DEFAULT_MESSAGE else message
            val smsManager = SmsManager.getDefault()
            
            // Split message if it's too long
            val parts = smsManager.divideMessage(smsMessage)
            
            val sentIntent = Intent("android.provider.Telephony.SMS_SENT")
            val sentPI = PendingIntent.getBroadcast(reactApplicationContext, 0, sentIntent, PendingIntent.FLAG_UPDATE_CURRENT)
            
            // Send SMS
            if (parts.size > 1) {
                smsManager.sendMultipartTextMessage(phoneNumber, null, parts, null, null)
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
        // We'll use the default message for missed calls
        try {
            val smsMessage = DEFAULT_MESSAGE
            val smsManager = SmsManager.getDefault()
            
            // Split message if it's too long
            val parts = smsManager.divideMessage(smsMessage)
            
            // Send SMS
            if (parts.size > 1) {
                smsManager.sendMultipartTextMessage(phoneNumber, null, parts, null, null)
            } else {
                smsManager.sendTextMessage(phoneNumber, null, smsMessage, null, null)
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
            promise.reject("PERMISSIONS_DENIED", "The app doesn't have the required permissions")
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
} 