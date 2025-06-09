package com.auto_sms.callsms

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log

/**
 * HeadlessSmsSendService - required component for being a default SMS app
 * This is a placeholder implementation that is required for Android
 * to recognize the app as a valid SMS handler.
 */
class HeadlessSmsSendService : Service() {
    private val TAG = "HeadlessSmsSendService"

    override fun onBind(intent: Intent): IBinder? {
        Log.d(TAG, "onBind called: ${intent.action}")
        return null
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand called")
        return START_NOT_STICKY
    }
} 