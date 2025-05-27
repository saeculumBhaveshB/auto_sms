package com.auto_sms.callsms

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log

/**
 * Headless SMS Send Service - required for default SMS app implementation
 * This is a minimal implementation to satisfy the requirements for becoming the default SMS app
 * It represents a service that can send SMS messages in the background
 */
class HeadlessSmsSendService : Service() {
    private val TAG = "HeadlessSmsSendService"
    
    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "Service started: ${intent?.action}")
        
        // Process the message - typically we would handle SMS sending here
        // But for our auto-reply functionality we handle it in CallSmsService
        
        // Stop the service as we don't need to do anything here
        stopSelf()
        return START_NOT_STICKY
    }
} 