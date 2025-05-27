package com.auto_sms

import com.facebook.react.ReactActivity
import com.facebook.react.ReactActivityDelegate
import com.facebook.react.defaults.DefaultNewArchitectureEntryPoint.fabricEnabled
import com.facebook.react.defaults.DefaultReactActivityDelegate
import android.os.Bundle
import android.content.Intent
import android.provider.Telephony
import android.util.Log
import android.content.SharedPreferences
import android.content.Context
import android.widget.Toast
import android.app.Activity

class MainActivity : ReactActivity() {
    private val TAG = "MainActivity"
    private val REQUEST_DEFAULT_SMS = 1001
    private var pendingDefaultSmsRequest = false

    /**
     * Returns the name of the main component registered from JavaScript. This is used to schedule
     * rendering of the component.
     */
    override fun getMainComponentName(): String = "auto_sms"

    /**
     * Returns the instance of the [ReactActivityDelegate]. We use [DefaultReactActivityDelegate]
     * which allows you to enable New Architecture with a single boolean flags [fabricEnabled]
     */
    override fun createReactActivityDelegate(): ReactActivityDelegate =
        DefaultReactActivityDelegate(this, mainComponentName, fabricEnabled)
        
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Check if we need to request to be the default SMS app
        val sharedPrefs = getSharedPreferences("AutoSmsPrefs", Context.MODE_PRIVATE)
        val aiSmsEnabled = sharedPrefs.getBoolean("@AutoSMS:AIEnabled", false)
        val isDefaultRequested = sharedPrefs.getBoolean("DefaultSmsRequested", false)
        
        if (aiSmsEnabled && !isDefaultRequested) {
            requestDefaultSmsHandler()
        }
    }
    
    override fun onResume() {
        super.onResume()
        
        // Re-check if we should start the service when app comes to foreground
        startSmsService()
    }
    
    /**
     * Request to become the default SMS handler
     */
    private fun requestDefaultSmsHandler() {
        try {
            Log.d(TAG, "Requesting to become default SMS handler")
            val currentDefaultSmsPackage = Telephony.Sms.getDefaultSmsPackage(this)
            
            if (currentDefaultSmsPackage != packageName) {
                pendingDefaultSmsRequest = true
                val setSmsAppIntent = Intent(Telephony.Sms.Intents.ACTION_CHANGE_DEFAULT)
                setSmsAppIntent.putExtra(Telephony.Sms.Intents.EXTRA_PACKAGE_NAME, packageName)
                startActivityForResult(setSmsAppIntent, REQUEST_DEFAULT_SMS)
            } else {
                Log.d(TAG, "App is already the default SMS handler")
                saveDefaultSmsRequested()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error requesting default SMS handler: ${e.message}")
        }
    }
    
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        
        if (requestCode == REQUEST_DEFAULT_SMS) {
            if (resultCode == Activity.RESULT_OK) {
                Log.d(TAG, "User approved default SMS handler")
                Toast.makeText(this, "SMS auto-reply activated", Toast.LENGTH_SHORT).show()
                saveDefaultSmsRequested()
            } else {
                Log.d(TAG, "User rejected default SMS handler")
                Toast.makeText(this, "SMS auto-reply requires default SMS handler permission", Toast.LENGTH_LONG).show()
            }
            pendingDefaultSmsRequest = false
        }
    }
    
    /**
     * Save that we've requested to be the default SMS handler
     */
    private fun saveDefaultSmsRequested() {
        val sharedPrefs = getSharedPreferences("AutoSmsPrefs", Context.MODE_PRIVATE)
        sharedPrefs.edit().putBoolean("DefaultSmsRequested", true).apply()
    }
    
    /**
     * Start the SMS service for background operation
     */
    private fun startSmsService() {
        try {
            val sharedPrefs = getSharedPreferences("AutoSmsPrefs", Context.MODE_PRIVATE)
            val aiEnabled = sharedPrefs.getBoolean("@AutoSMS:AIEnabled", false)
            
            if (aiEnabled) {
                val serviceIntent = Intent(this, Class.forName("com.auto_sms.callsms.CallSmsService"))
                serviceIntent.action = "com.auto_sms.callsms.START_SERVICE"
                
                startService(serviceIntent)
                Log.d(TAG, "Started CallSmsService from MainActivity")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error starting service from MainActivity: ${e.message}")
        }
    }
}
