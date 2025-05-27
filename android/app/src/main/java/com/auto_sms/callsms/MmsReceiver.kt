package com.auto_sms.callsms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * MMS Receiver - required for default SMS app implementation
 * This is a minimal implementation to satisfy the requirements for becoming the default SMS app
 */
class MmsReceiver : BroadcastReceiver() {
    
    private val TAG = "MmsReceiver"
    
    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "MMS received: ${intent.action}")
        // We're not implementing MMS functionality, just fulfilling the requirement
    }
} 