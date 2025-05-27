package com.auto_sms.callsms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * SMS Delivered Receiver - required for default SMS app implementation
 * This is a minimal implementation to satisfy the requirements for becoming the default SMS app
 */
class SmsDeliveredReceiver : BroadcastReceiver() {
    
    private val TAG = "SmsDeliveredReceiver"
    
    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "SMS delivered: ${intent.action}")
        // Forward to the existing SMS receiver for consistent handling
        val forwardIntent = Intent(intent)
        forwardIntent.setClass(context, SmsReceiver::class.java)
        context.sendBroadcast(forwardIntent)
    }
} 