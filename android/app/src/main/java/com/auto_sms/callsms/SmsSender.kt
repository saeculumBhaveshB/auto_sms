package com.auto_sms.callsms

import android.app.Activity
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.provider.Telephony
import android.telephony.SmsManager
import android.util.Log

class SmsSender {
    companion object {
        private const val TAG = "SmsSender"
        
        // Normalize phone number to ensure consistent format
        fun normalizePhoneNumber(phoneNumber: String): String {
            // Remove all non-digit characters
            var normalized = phoneNumber.replace(Regex("[^\\d+]"), "")
            
            // If number starts with +, keep it, otherwise check for country code
            if (!normalized.startsWith("+")) {
                // If number is 10 digits (US format without country code), add +1
                if (normalized.length == 10) {
                    normalized = "+1$normalized"
                }
            }
            
            return normalized
        }
        
        fun sendSms(context: Context, phoneNumber: String, message: String) {
            Log.e(TAG, "🚀🚀🚀 SENDING SMS - START 🚀🚀🚀")
            Log.e(TAG, "📞 To: $phoneNumber")
            Log.e(TAG, "📝 Message: $message")

            try {
                val normalizedNumber = normalizePhoneNumber(phoneNumber)
                Log.e(TAG, "📱 Original number: $phoneNumber, Normalized: $normalizedNumber")
                
                // Check if the device has default SMS app permissions
                val defaultSmsPackage = Telephony.Sms.getDefaultSmsPackage(context)
                val ourPackage = context.packageName
                val isDefaultSmsApp = defaultSmsPackage == ourPackage
                
                Log.e(TAG, "📲 Default SMS package: $defaultSmsPackage")
                Log.e(TAG, "📲 Our package: $ourPackage")
                Log.e(TAG, "📲 Is default SMS app: $isDefaultSmsApp")
                
                // CRITICAL FIX: Check if number is likely RCS-enabled
                val isRcsEnabled = normalizedNumber.startsWith("+") || 
                                  normalizedNumber.length > 10
                Log.e(TAG, "📱 Number likely RCS-enabled: $isRcsEnabled")
                
                // If RCS is likely enabled and we're not the default SMS app,
                // try the alternative approach first for better compatibility
                if (isRcsEnabled && !isDefaultSmsApp) {
                    Log.e(TAG, "🔄 RCS number detected and not default SMS app, trying alternative approach first")
                    if (sendSmsViaIntent(context, normalizedNumber, message)) {
                        Log.e(TAG, "✅ Alternative SMS approach succeeded")
                        return
                    }
                    // If alternative approach fails, continue with standard approach
                    Log.e(TAG, "⚠️ Alternative approach failed, falling back to standard SMS")
                }
                
                val smsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    context.getSystemService(SmsManager::class.java)
                } else {
                    SmsManager.getDefault()
                }
                
                // Create sentIntent to track SMS delivery status
                val sentIntent = PendingIntent.getBroadcast(
                    context, 
                    0, 
                    Intent("SMS_SENT"), 
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
                )
                
                // Create deliveredIntent to track SMS delivery status
                val deliveredIntent = PendingIntent.getBroadcast(
                    context, 
                    0, 
                    Intent("SMS_DELIVERED"), 
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
                )
                
                // Track if SMS was sent successfully
                val sentSuccessfully = java.util.concurrent.atomic.AtomicBoolean(false)
                
                // Register BroadcastReceiver for sent SMS
                context.registerReceiver(object : BroadcastReceiver() {
                    override fun onReceive(context: Context, intent: Intent) {
                        when (resultCode) {
                            Activity.RESULT_OK -> {
                                Log.e(TAG, "✅ SMS sent successfully")
                                sentSuccessfully.set(true)
                            }
                            SmsManager.RESULT_ERROR_GENERIC_FAILURE -> {
                                Log.e(TAG, "❌ SMS sending failed: Generic failure")
                                // Try alternative method if standard method fails
                                if (isRcsEnabled) {
                                    Log.e(TAG, "🔄 Attempting alternative SMS method after failure")
                                    sendSmsViaIntent(context, normalizedNumber, message)
                                }
                            }
                            SmsManager.RESULT_ERROR_NO_SERVICE -> Log.e(TAG, "❌ SMS sending failed: No service")
                            SmsManager.RESULT_ERROR_NULL_PDU -> Log.e(TAG, "❌ SMS sending failed: Null PDU")
                            SmsManager.RESULT_ERROR_RADIO_OFF -> Log.e(TAG, "❌ SMS sending failed: Radio off")
                            else -> Log.e(TAG, "❌ SMS sending failed: Unknown error code $resultCode")
                        }
                        try {
                            context.unregisterReceiver(this)
                        } catch (e: Exception) {
                            Log.e(TAG, "⚠️ Error unregistering receiver: ${e.message}")
                        }
                    }
                }, IntentFilter("SMS_SENT"))
                
                // Register BroadcastReceiver for delivered SMS
                context.registerReceiver(object : BroadcastReceiver() {
                    override fun onReceive(context: Context, intent: Intent) {
                        when (resultCode) {
                            Activity.RESULT_OK -> Log.e(TAG, "✅ SMS delivered successfully")
                            Activity.RESULT_CANCELED -> {
                                Log.e(TAG, "❌ SMS not delivered")
                                // If SMS wasn't delivered and it's an RCS number, try alternative method
                                if (isRcsEnabled && !sentSuccessfully.get()) {
                                    Log.e(TAG, "🔄 Attempting alternative SMS method after delivery failure")
                                    sendSmsViaIntent(context, normalizedNumber, message)
                                }
                            }
                            else -> Log.e(TAG, "⚠️ SMS delivery status unknown: $resultCode")
                        }
                        try {
                            context.unregisterReceiver(this)
                        } catch (e: Exception) {
                            Log.e(TAG, "⚠️ Error unregistering receiver: ${e.message}")
                        }
                    }
                }, IntentFilter("SMS_DELIVERED"))
                
                // Try to send SMS
                try {
                    if (message.length <= 160) {
                        Log.e(TAG, "📤 Sending single SMS message")
                        smsManager.sendTextMessage(normalizedNumber, null, message, sentIntent, deliveredIntent)
                    } else {
                        Log.e(TAG, "📤 Sending multi-part SMS message")
                        val parts = smsManager.divideMessage(message)
                        val sentIntents = ArrayList<PendingIntent>()
                        val deliveredIntents = ArrayList<PendingIntent>()
                        
                        for (i in parts.indices) {
                            sentIntents.add(PendingIntent.getBroadcast(
                                context, 
                                i, 
                                Intent("SMS_SENT_PART_$i"), 
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
                            ))
                            deliveredIntents.add(PendingIntent.getBroadcast(
                                context, 
                                i, 
                                Intent("SMS_DELIVERED_PART_$i"), 
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
                            ))
                        }
                        
                        smsManager.sendMultipartTextMessage(normalizedNumber, null, parts, sentIntents, deliveredIntents)
                    }
                    Log.e(TAG, "🚀🚀🚀 SENDING SMS - COMPLETED 🚀🚀🚀")
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Error during SMS sending operation: ${e.message}")
                    e.printStackTrace()
                    
                    // If standard method fails, try alternative approach
                    Log.e(TAG, "🔄 Standard SMS sending failed, trying alternative approach")
                    sendSmsViaIntent(context, normalizedNumber, message)
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌❌❌ ERROR SENDING SMS: ${e.message}")
                Log.e(TAG, "Stack trace: ${e.stackTraceToString()}")
                
                // Try alternative method as last resort
                try {
                    Log.e(TAG, "🔄 Trying alternative SMS sending approach as last resort")
                    sendSmsViaIntent(context, phoneNumber, message)
                } catch (e2: Exception) {
                    Log.e(TAG, "❌❌❌ Alternative SMS approach also failed: ${e2.message}")
                    e2.printStackTrace()
                }
            }
        }
        
        /**
         * Alternative method to send SMS using ACTION_SENDTO intent
         * This can work better for RCS-enabled numbers in some cases
         * @return true if the intent was successfully started, false otherwise
         */
        fun sendSmsViaIntent(context: Context, phoneNumber: String, message: String): Boolean {
            return try {
                Log.e(TAG, "📱 Sending SMS via ACTION_SENDTO intent")
                val intent = Intent(Intent.ACTION_SENDTO)
                intent.data = Uri.parse("smsto:$phoneNumber")
                intent.putExtra("sms_body", message)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                context.startActivity(intent)
                Log.e(TAG, "✅ ACTION_SENDTO intent started successfully")
                true
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error sending SMS via intent: ${e.message}")
                e.printStackTrace()
                
                // Try another approach with ACTION_VIEW
                try {
                    Log.e(TAG, "🔄 Trying SMS via ACTION_VIEW intent")
                    val uri = Uri.parse("smsto:$phoneNumber")
                    val viewIntent = Intent(Intent.ACTION_VIEW, uri)
                    viewIntent.putExtra("sms_body", message)
                    viewIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    context.startActivity(viewIntent)
                    Log.e(TAG, "✅ ACTION_VIEW intent started successfully")
                    true
                } catch (e2: Exception) {
                    Log.e(TAG, "❌ Error sending SMS via ACTION_VIEW intent: ${e2.message}")
                    e2.printStackTrace()
                    false
                }
            }
        }
    }
} 