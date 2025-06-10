package com.auto_sms

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Telephony
import android.util.Log
import com.facebook.react.ReactActivity
import com.facebook.react.ReactActivityDelegate
import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.WritableMap
import com.facebook.react.defaults.DefaultNewArchitectureEntryPoint.fabricEnabled
import com.facebook.react.defaults.DefaultReactActivityDelegate
import com.facebook.react.modules.core.DeviceEventManagerModule

class MainActivity : ReactActivity() {

  companion object {
    private const val TAG = "MainActivity"
    private const val DEFAULT_SMS_REQUEST_CODE = 1001
  }

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
      
  /**
   * Activity lifecycle hook - onCreate
   */
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    Log.d(TAG, "MainActivity onCreate")
    
    // Check current default SMS handler on startup
    try {
      val defaultSmsPackage = Telephony.Sms.getDefaultSmsPackage(this)
      val ourPackage = packageName
      val isDefault = defaultSmsPackage == ourPackage
      
      Log.d(TAG, "✅ Initial default SMS check - Default package: $defaultSmsPackage")
      Log.d(TAG, "✅ Our package: $ourPackage")
      Log.d(TAG, "✅ Is default SMS handler: $isDefault")
    } catch (e: Exception) {
      Log.e(TAG, "❌ Error checking default SMS handler on startup: ${e.message}")
    }
    
    // Register ourselves with the CallSmsModule
    val reactContext = this.reactNativeHost.reactInstanceManager.currentReactContext
    if (reactContext != null) {
      val eventData = Arguments.createMap().apply {
        putString("event", "activityCreated")
      }
      reactContext
        .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter::class.java)
        .emit("activityEvent", eventData)
    }
  }
  
  /**
   * Activity lifecycle hook - onResume
   */
  override fun onResume() {
    super.onResume()
    Log.d(TAG, "📱 MainActivity onResume")
    
    // Check if we're the default SMS handler on resume
    val defaultSmsPackage = Telephony.Sms.getDefaultSmsPackage(this)
    val ourPackage = packageName
    val isDefault = defaultSmsPackage == ourPackage
    
    Log.d(TAG, "📱 onResume check - Default SMS app: $defaultSmsPackage, our package: $ourPackage, is default: $isDefault")
    
    // Send the status to JS
    val reactContext = this.reactNativeHost.reactInstanceManager.currentReactContext
    if (reactContext != null) {
      val eventData = Arguments.createMap().apply {
        putBoolean("isDefault", isDefault)
      }
      reactContext
        .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter::class.java)
        .emit("defaultSmsHandlerChanged", eventData)
    }
  }
  
  /**
   * Helper method to start the default SMS request
   * This is called from the CallSmsModule
   */
  fun startDefaultSmsRequest(intent: Intent, requestCode: Int) {
    Log.d(TAG, "📱 Starting default SMS request from MainActivity")
    try {
      // Start activity for result
      startActivityForResult(intent, requestCode)
      Log.d(TAG, "📱 Default SMS request intent started successfully")
    } catch (e: Exception) {
      Log.e(TAG, "📱 Error starting default SMS request: ${e.message}")
      // Notify JS of the error
      val reactContext = this.reactNativeHost.reactInstanceManager.currentReactContext
      if (reactContext != null) {
        val eventData = Arguments.createMap().apply {
          putString("error", e.message)
        }
        reactContext
          .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter::class.java)
          .emit("defaultSmsHandlerRequestFailed", eventData)
      }
    }
  }
  
  /**
   * Handle activity result for default SMS handler request
   */
  override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
    super.onActivityResult(requestCode, resultCode, data)
    
    Log.d(TAG, "📱 Activity result received: requestCode=$requestCode, resultCode=$resultCode")
    
    if (requestCode == DEFAULT_SMS_REQUEST_CODE) {
      Log.d(TAG, "📱 Received result from default SMS handler request: $resultCode")
      
      // Check if we've become the default SMS handler - don't rely on resultCode
      // The actual state must be verified by checking the system setting
      Handler(Looper.getMainLooper()).postDelayed({
        val defaultSmsPackage = Telephony.Sms.getDefaultSmsPackage(this)
        val ourPackage = packageName
        val isDefault = defaultSmsPackage == ourPackage
        
        Log.d(TAG, "📱 Delayed check - Current default SMS app: $defaultSmsPackage, our package: $ourPackage, is default: $isDefault")
        
        // Send the result to JS
        val reactContext = this.reactNativeHost.reactInstanceManager.currentReactContext
        if (reactContext != null) {
          val eventData = Arguments.createMap().apply {
            putBoolean("isDefault", isDefault)
          }
          reactContext
            .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter::class.java)
            .emit("defaultSmsHandlerChanged", eventData)
          
          // If we've become the default handler, also emit the success event
          if (isDefault) {
            reactContext
              .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter::class.java)
              .emit("defaultSmsHandlerSet", null)
          }
        }
        
        // Check again after some time in case system is still updating
        if (!isDefault) {
          Handler(Looper.getMainLooper()).postDelayed({
            val latestDefault = Telephony.Sms.getDefaultSmsPackage(this)
            val isNowDefault = latestDefault == ourPackage
            
            Log.d(TAG, "📱 Final check - Current default SMS app: $latestDefault, our package: $ourPackage, is default: $isNowDefault")
            
            if (isNowDefault && reactContext != null) {
              val eventData = Arguments.createMap().apply {
                putBoolean("isDefault", true)
              }
              reactContext
                .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter::class.java)
                .emit("defaultSmsHandlerChanged", eventData)
              
              reactContext
                .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter::class.java)
                .emit("defaultSmsHandlerSet", null)
            }
          }, 5000) // Check again after 5 seconds
        }
      }, 1000) // Initial check after 1 second
    }
  }
  
  /**
   * Handle new intents (including SMS-related intents)
   */
  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    Log.d(TAG, "📱 onNewIntent called with action: ${intent.action}")
    
    // Handle SMS-related intents
    if (intent.action == Intent.ACTION_SENDTO && 
        (intent.data?.scheme == "sms" || intent.data?.scheme == "smsto" ||
         intent.data?.scheme == "mms" || intent.data?.scheme == "mmsto")) {
      
      Log.d(TAG, "📱 Received SMS-related intent: ${intent.data}")
      
      // Get the phone number from the intent data
      val phoneNumber = intent.data?.schemeSpecificPart
      Log.d(TAG, "📱 Phone number from intent: $phoneNumber")
      
      // Get the message body if present
      val messageBody = intent.getStringExtra("sms_body") ?: ""
      Log.d(TAG, "📱 Message body from intent: $messageBody")
      
      // You can handle this by passing it to React Native or handling it natively
      // For now, we'll just log it
      
      // Check if we're the default SMS handler
      try {
        val defaultSmsPackage = Telephony.Sms.getDefaultSmsPackage(this)
        val ourPackage = packageName
        val isDefault = defaultSmsPackage == ourPackage
        
        Log.d(TAG, "📱 Default SMS check during intent handling - Default package: $defaultSmsPackage")
        Log.d(TAG, "📱 Our package: $ourPackage")
        Log.d(TAG, "📱 Is default SMS handler: $isDefault")
        
        // If we aren't the default handler but received this intent, inform user
        if (!isDefault) {
          Log.d(TAG, "⚠️ Received SMS intent but we're not the default handler")
        }
      } catch (e: Exception) {
        Log.e(TAG, "❌ Error checking default SMS handler during intent handling: ${e.message}")
      }
    }
  }
}
