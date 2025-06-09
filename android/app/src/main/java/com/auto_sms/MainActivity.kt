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
   * Handle activity result for default SMS handler request
   */
  override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
    super.onActivityResult(requestCode, resultCode, data)
    
    Log.d(TAG, "Activity result received: requestCode=$requestCode, resultCode=$resultCode")
    
    if (requestCode == DEFAULT_SMS_REQUEST_CODE) {
      Log.d(TAG, "Received result from default SMS handler request: $resultCode")
      
      // Check if we've become the default SMS handler
      val defaultSmsPackage = Telephony.Sms.getDefaultSmsPackage(this)
      val isDefault = defaultSmsPackage == packageName
      
      Log.d(TAG, "Default SMS package is now: $defaultSmsPackage")
      Log.d(TAG, "Our package is: $packageName")
      Log.d(TAG, "Is default SMS handler: $isDefault")
      
      // Always send event to JS, regardless of the result
      sendDefaultSmsHandlerEvent(isDefault)
    }
  }
  
  /**
   * Helper method to send default SMS handler event to React Native
   */
  private fun sendDefaultSmsHandlerEvent(isDefault: Boolean) {
    try {
      // Try to get current React context
      val reactContext = this.reactNativeHost.reactInstanceManager.currentReactContext
      if (reactContext != null) {
        Log.d(TAG, "Sending defaultSmsHandlerChanged event to JS with isDefault=$isDefault")
        
        val eventData = Arguments.createMap().apply {
          putBoolean("isDefault", isDefault)
        }
        
        reactContext
          .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter::class.java)
          .emit("defaultSmsHandlerChanged", eventData)
      } else {
        Log.e(TAG, "Cannot send event to JS: React context is null")
        
        // If React context is null, retry after a delay
        Handler(Looper.getMainLooper()).postDelayed({
          Log.d(TAG, "Retrying to send defaultSmsHandlerChanged event")
          val retryContext = this.reactNativeHost.reactInstanceManager.currentReactContext
          if (retryContext != null) {
            val eventData = Arguments.createMap().apply {
              putBoolean("isDefault", isDefault)
            }
            retryContext
              .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter::class.java)
              .emit("defaultSmsHandlerChanged", eventData)
            Log.d(TAG, "Successfully sent delayed defaultSmsHandlerChanged event")
          } else {
            Log.e(TAG, "Still cannot send event: React context is null after delay")
          }
        }, 1000) // 1 second delay
      }
    } catch (e: Exception) {
      Log.e(TAG, "Error sending defaultSmsHandlerChanged event: ${e.message}")
    }
  }
}
