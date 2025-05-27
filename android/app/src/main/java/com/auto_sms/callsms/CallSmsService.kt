package com.auto_sms.callsms

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.auto_sms.MainActivity
import com.auto_sms.R

/**
 * Foreground service for reliable SMS processing
 */
class CallSmsService : Service() {
    companion object {
        private const val TAG = "CallSmsService"
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "CallSmsServiceChannel"
        
        // Intent actions
        const val ACTION_START_SERVICE = "com.auto_sms.callsms.START_SERVICE"
        const val ACTION_STOP_SERVICE = "com.auto_sms.callsms.STOP_SERVICE"
        
        // Shared preferences keys
        private const val AI_SMS_ENABLED_KEY = "@AutoSMS:AIEnabled"
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "CallSmsService created")
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand called with action: ${intent?.action}")
        
        when (intent?.action) {
            ACTION_START_SERVICE -> startForegroundService()
            ACTION_STOP_SERVICE -> stopService()
        }
        
        // Restart if killed
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onDestroy() {
        Log.d(TAG, "CallSmsService destroyed")
        super.onDestroy()
    }
    
    /**
     * Create notification channel for Android 8.0+
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "SMS Auto-Reply Service"
            val descriptionText = "Background service for SMS auto-replies"
            val importance = NotificationManager.IMPORTANCE_LOW
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
                setShowBadge(false)
            }
            
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
            
            Log.d(TAG, "Notification channel created")
        }
    }
    
    /**
     * Start the service in foreground mode
     */
    private fun startForegroundService() {
        Log.d(TAG, "Starting foreground service")
        
        // Check if AI SMS is enabled
        val sharedPrefs = getSharedPreferences("AutoSmsPrefs", Context.MODE_PRIVATE)
        val aiEnabled = sharedPrefs.getBoolean(AI_SMS_ENABLED_KEY, false)
        
        // Create intent to open the app when notification is clicked
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        
        // Build the notification
        val notificationBuilder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("SMS Auto-Reply Active")
            .setContentText(if (aiEnabled) "AI responses are enabled" else "AI responses are disabled")
            .setSmallIcon(R.drawable.notification_icon)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
        
        // Start as foreground service
        startForeground(NOTIFICATION_ID, notificationBuilder.build())
        
        Log.d(TAG, "Foreground service started")
    }
    
    /**
     * Stop the service
     */
    private fun stopService() {
        Log.d(TAG, "Stopping service")
        stopForeground(true)
        stopSelf()
    }
} 