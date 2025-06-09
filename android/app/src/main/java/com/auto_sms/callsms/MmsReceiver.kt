package com.auto_sms.callsms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * MmsReceiver - required component for being a default SMS app
 * This is a placeholder implementation that doesn't actually handle MMS messages
 * but is required for Android to recognize the app as a valid SMS handler.
 */
class MmsReceiver : BroadcastReceiver() {
    private val TAG = "MmsReceiver"

    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "MMS received: ${intent.action}")
        // This is a placeholder implementation
        // In a real SMS app, you would handle MMS reception here
    }
} 